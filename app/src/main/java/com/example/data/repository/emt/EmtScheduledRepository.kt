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

                val todayIsoDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }

                val calYesterday = Calendar.getInstance(MADRID_ZONE).apply {
                    timeInMillis = cal.timeInMillis
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                val yesterdayYmd = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                    timeZone = MADRID_ZONE
                }.format(calYesterday.time)

                val yesterdayIsoDay = when (calYesterday.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }

                val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                val currentMinuteOfDay = currentHour * 60 + cal.get(Calendar.MINUTE)
                val currentSecond = cal.get(Calendar.SECOND)

                fun isApplicable(schObj: JSONObject, ymd: String, isoDay: Int): Boolean {
                    val datesArr = schObj.optJSONArray("dates")
                    val daysArr = schObj.optJSONArray("days")
                    if (datesArr != null && datesArr.length() > 0) {
                        for (d in 0 until datesArr.length()) {
                            if (datesArr.optString(d) == ymd) return true
                        }
                        return false
                    }
                    if (daysArr != null && daysArr.length() > 0) {
                        for (d in 0 until daysArr.length()) {
                            if (daysArr.optInt(d) == isoDay) return true
                        }
                        return false
                    }
                    return false
                }

                data class EmtScheduledCandidate(
                    val line: String,
                    val dest: String,
                    val diffMinutes: Int,
                    val secondsRemaining: Int,
                    val formattedTime: String
                )
                val candidates = mutableListOf<EmtScheduledCandidate>()

                for (i in 0 until schedules.length()) {
                    val schObj = schedules.optJSONObject(i) ?: continue
                    val line = schObj.optString("line", "").trim()
                    val dest = schObj.optString("dest", "").trim()
                    if (line.isBlank()) continue

                    val isYesterday = isApplicable(schObj, yesterdayYmd, yesterdayIsoDay)
                    val isToday = isApplicable(schObj, todayYmd, todayIsoDay)
                    if (!isYesterday && !isToday) continue

                    val timesArr = schObj.optJSONArray("times") ?: JSONArray()

                    if (currentHour < 6) {
                        // Early morning query (00:00 - 05:59):
                        // 1) Late night departures from yesterday's service day (e.g. 24:30, 25:15 -> 00:30, 01:15 today)
                        if (isYesterday) {
                            for (t in 0 until timesArr.length()) {
                                val rawMin = timesArr.optInt(t, -1)
                                if (rawMin >= 1440) {
                                    val effTodayMin = rawMin - 1440
                                    if (effTodayMin >= currentMinuteOfDay - 2) {
                                        val diffMin = effTodayMin - currentMinuteOfDay
                                        val sec = (diffMin * 60) - currentSecond
                                        val hh = (effTodayMin / 60) % 24
                                        val mm = effTodayMin % 60
                                        val formatted = String.format(Locale.ROOT, "%02d:%02d", hh, mm)
                                        candidates.add(
                                            EmtScheduledCandidate(
                                                line = line,
                                                dest = dest,
                                                diffMinutes = diffMin,
                                                secondsRemaining = sec.coerceAtLeast(0),
                                                formattedTime = formatted
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        // 2) Today's morning departures starting today (rawMin < 1440)
                        if (isToday) {
                            for (t in 0 until timesArr.length()) {
                                val rawMin = timesArr.optInt(t, -1)
                                if (rawMin in 0 until 1440) {
                                    if (rawMin >= currentMinuteOfDay - 2) {
                                        val diffMin = rawMin - currentMinuteOfDay
                                        val sec = (diffMin * 60) - currentSecond
                                        val hh = (rawMin / 60) % 24
                                        val mm = rawMin % 60
                                        val formatted = String.format(Locale.ROOT, "%02d:%02d", hh, mm)
                                        candidates.add(
                                            EmtScheduledCandidate(
                                                line = line,
                                                dest = dest,
                                                diffMinutes = diffMin,
                                                secondsRemaining = sec.coerceAtLeast(0),
                                                formattedTime = formatted
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Daytime / evening query (06:00 - 23:59):
                        if (isToday) {
                            for (t in 0 until timesArr.length()) {
                                val rawMin = timesArr.optInt(t, -1)
                                if (rawMin >= currentMinuteOfDay - 2) {
                                    val diffMin = rawMin - currentMinuteOfDay
                                    val sec = (diffMin * 60) - currentSecond
                                    val effMin = if (rawMin >= 1440) rawMin - 1440 else rawMin
                                    val hh = (effMin / 60) % 24
                                    val mm = effMin % 60
                                    val formatted = String.format(Locale.ROOT, "%02d:%02d", hh, mm)
                                    candidates.add(
                                        EmtScheduledCandidate(
                                            line = line,
                                            dest = dest,
                                            diffMinutes = diffMin,
                                            secondsRemaining = sec.coerceAtLeast(0),
                                            formattedTime = formatted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                val uniqueCandidates = candidates
                    .distinctBy { "${it.line}__${it.dest}__${it.formattedTime}" }
                    .sortedWith(compareBy({ it.diffMinutes }, { it.line }))

                val grouped = uniqueCandidates.groupBy { "${it.line}__${it.dest}" }
                grouped.forEach { (_, depList) ->
                    depList.take(limitPerLine).forEach { cand ->
                        results.add(
                            EmtBusTime(
                                linea = cand.line,
                                destino = cand.dest,
                                minutos = if (cand.diffMinutes <= 0) "1" else cand.diffMinutes.toString(),
                                horaLlegada = cand.formattedTime,
                                secondsRemaining = cand.secondsRemaining,
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
