package com.example.data.repository.emt

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.network.NetworkModule
import com.example.ui.bus.BusMapper
import com.example.ui.bus.EmtBusTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EmtScheduledRepository(private val context: Context) {

    companion object {
        private const val TAG = "EmtScheduledRepo"
        private const val DEPARTURES_URL_BASE = "https://raw.githubusercontent.com/BananaManMS/EMT_valencia_schedule/data/departures"
        private val MADRID_ZONE = TimeZone.getTimeZone("Europe/Madrid")
        private val departuresMemoryCache = mutableMapOf<String, Pair<Long, String>>()
        private const val MEMORY_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour

        suspend fun fetchScheduledDepartures(
            stopId: String,
            stopName: String? = null,
            limitPerLine: Int = 5,
            context: Context? = null
        ): List<EmtBusTime> = withContext(Dispatchers.IO) {
            val cleanStop = stopId.trim()
            if (cleanStop.isBlank()) return@withContext emptyList()

            val appContext = context ?: com.example.MainApplication.instance
            val jsonContent = loadStopDeparturesJson(cleanStop, appContext)
            if (jsonContent.isNullOrBlank()) {
                return@withContext emptyList()
            }

            parseScheduledDepartures(jsonContent, limitPerLine)
        }

        private fun loadStopDeparturesJson(stopId: String, context: Context): String? {
            val now = System.currentTimeMillis()
            val cachedMem = departuresMemoryCache[stopId]
            if (cachedMem != null && (now - cachedMem.first < MEMORY_CACHE_TTL_MS)) {
                return cachedMem.second
            }

            val dir = File(context.filesDir, "emt_departures")
            if (!dir.exists()) dir.mkdirs()
            val localFile = File(dir, "$stopId.json")

            if (localFile.exists() && localFile.length() > 20 && (now - localFile.lastModified() < 7 * 24 * 60 * 60 * 1000L)) {
                try {
                    val content = localFile.readText(Charsets.UTF_8)
                    if (content.isNotBlank()) {
                        departuresMemoryCache[stopId] = Pair(now, content)
                        return content
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading local cached departures for $stopId: ${e.message}")
                }
            }

            // Download from official GTFS schedule repository
            try {
                val url = "$DEPARTURES_URL_BASE/$stopId.json"
                val request = Request.Builder().url(url).build()
                NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            try {
                                localFile.writeText(body, Charsets.UTF_8)
                            } catch (_: Exception) {}
                            departuresMemoryCache[stopId] = Pair(now, body)
                            return body
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching EMT schedule from GitHub for stop $stopId: ${e.message}")
            }

            if (localFile.exists() && localFile.length() > 20) {
                try {
                    val content = localFile.readText(Charsets.UTF_8)
                    departuresMemoryCache[stopId] = Pair(now, content)
                    return content
                } catch (_: Exception) {}
            }

            return null
        }

        private fun parseScheduledDepartures(jsonString: String, limitPerLine: Int): List<EmtBusTime> {
            val results = mutableListOf<EmtBusTime>()
            try {
                val root = JSONObject(jsonString)
                val schedules = root.optJSONArray("schedules") ?: JSONArray()

                val cal = Calendar.getInstance(MADRID_ZONE)
                val todayYmd = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                    timeZone = MADRID_ZONE
                }.format(cal.time)

                val isoDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }

                val currentMinuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val currentSecond = cal.get(Calendar.SECOND)

                for (i in 0 until schedules.length()) {
                    val schObj = schedules.optJSONObject(i) ?: continue
                    val line = schObj.optString("line", "").trim()
                    val dest = schObj.optString("dest", "").trim()

                    // Check date/day validity
                    val datesArr = schObj.optJSONArray("dates")
                    val daysArr = schObj.optJSONArray("days")

                    var isApplicable = false
                    if (datesArr != null && datesArr.length() > 0) {
                        for (d in 0 until datesArr.length()) {
                            if (datesArr.optString(d) == todayYmd) {
                                isApplicable = true
                                break
                            }
                        }
                    } else if (daysArr != null && daysArr.length() > 0) {
                        for (d in 0 until daysArr.length()) {
                            if (daysArr.optInt(d) == isoDay) {
                                isApplicable = true
                                break
                            }
                        }
                    }

                    if (!isApplicable) continue

                    val timesArr = schObj.optJSONArray("times") ?: JSONArray()
                    val upcomingTimes = mutableListOf<Int>()
                    for (t in 0 until timesArr.length()) {
                        val timeMin = timesArr.optInt(t, -1)
                        if (timeMin >= currentMinuteOfDay) {
                            upcomingTimes.add(timeMin)
                        }
                    }

                    upcomingTimes.take(limitPerLine).forEach { timeMin ->
                        val diffMinutes = timeMin - currentMinuteOfDay
                        val secondsRemaining = (diffMinutes * 60) - currentSecond
                        val hh = (timeMin / 60) % 24
                        val mm = timeMin % 60
                        val formattedTime = String.format(Locale.ROOT, "%02d:%02d", hh, mm)

                        results.add(
                            EmtBusTime(
                                linea = line,
                                destino = dest,
                                minutos = if (diffMinutes <= 0) "1" else diffMinutes.toString(),
                                horaLlegada = formattedTime,
                                secondsRemaining = secondsRemaining.coerceAtLeast(0),
                                isRealTime = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing EMT schedule JSON: ${e.message}", e)
            }

            return results.sortedBy { it.secondsRemaining }
        }
    }
}
