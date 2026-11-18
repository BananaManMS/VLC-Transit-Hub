package com.example.data.repository.metrobus

import android.content.Context
import android.util.Log
import com.example.data.network.NetworkModule
import com.example.ui.bus.MetrobusDepartureUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.example.BuildConfig
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class MetrobusScheduledRepository(private val context: Context) {

    private data class CachedScheduleEntry(
        val hourInt: Int,
        val minuteInt: Int,
        val lineCode: String,
        val direction: String
    )

    companion object {
        private const val TAG = "MetrobusScheduledRepo"
        private val API_SECRET = BuildConfig.METROVALENCIA_API_SECRET
        private const val METROBUS_API_BASE = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/metrobus"
        private const val SCHEDULE_CACHE_TTL_MS = 30 * 60 * 1000L

        private val scheduleCache = ConcurrentHashMap<String, Pair<Long, List<CachedScheduleEntry>>>()
        private val stopMutexes = ConcurrentHashMap<String, Mutex>()

        fun clearCache() {
            scheduleCache.clear()
        }

        suspend fun fetchScheduledDepartures(
            stopId: String,
            limitPerLine: Int = 5,
            linesMap: Map<String, Any?> = emptyMap(),
            client: OkHttpClient? = null
        ): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
            val cleanStop = stopId.trim()
            if (cleanStop.isBlank()) return@withContext emptyList()

            val httpClient = client ?: NetworkModule.okHttpClient

            val entries = getOrFetchEntries(cleanStop, httpClient)
            val remoteResults = buildDeparturesFromEntries(entries)
            if (limitPerLine > 0 && remoteResults.isNotEmpty()) {
                val grouped = remoteResults.groupBy { it.lineCode }
                val limited = mutableListOf<MetrobusDepartureUiModel>()
                grouped.forEach { (_, departures) ->
                    limited.addAll(departures.take(limitPerLine))
                }
                return@withContext limited.sortedBy { it.minutesRemaining }
            }

            remoteResults.sortedBy { it.minutesRemaining }
        }

        private suspend fun getOrFetchEntries(
            stopId: String,
            client: OkHttpClient
        ): List<CachedScheduleEntry> {
            val now = System.currentTimeMillis()
            scheduleCache[stopId]?.let { (ts, cached) ->
                if (now - ts < SCHEDULE_CACHE_TTL_MS && cached.isNotEmpty()) {
                    return cached
                }
            }

            val stopMutex = stopMutexes.getOrPut(stopId) { Mutex() }
            return stopMutex.withLock {
                val lockNow = System.currentTimeMillis()
                scheduleCache[stopId]?.let { (ts, cached) ->
                    if (lockNow - ts < SCHEDULE_CACHE_TTL_MS && cached.isNotEmpty()) {
                        return@withLock cached
                    }
                }

                val parsed = fetchEntriesFromApi(stopId, client)
                if (parsed.isNotEmpty()) {
                    scheduleCache[stopId] = Pair(System.currentTimeMillis(), parsed)
                    parsed
                } else {
                    scheduleCache[stopId]?.second ?: emptyList()
                }
            }
        }

        private fun fetchEntriesFromApi(
            stopId: String,
            client: OkHttpClient
        ): List<CachedScheduleEntry> {
            val entries = mutableListOf<CachedScheduleEntry>()
            try {
                val url = "$METROBUS_API_BASE/paradas/$stopId/horario"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("X-App-Secret", API_SECRET)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val root = JSONObject(body)
                            val dataObj = root.optJSONObject("data")
                            if (dataObj != null) {
                                val keys = dataObj.keys()
                                while (keys.hasNext()) {
                                    val hourStr = keys.next()
                                    val hourInt = hourStr.toIntOrNull() ?: continue
                                    val depArray = dataObj.optJSONArray(hourStr) ?: continue

                                    for (i in 0 until depArray.length()) {
                                        val depObj = depArray.optJSONObject(i) ?: continue
                                        val minuteStr = depObj.optString("minute", "00")
                                        val minuteInt = minuteStr.toIntOrNull() ?: continue
                                        val lineCode = depObj.optString("line", "").ifBlank {
                                            depObj.optString("route_short_name", "")
                                        }.trim()
                                        if (lineCode.isBlank()) continue

                                        val direction = depObj.optString("direction", "València").trim()
                                        entries.add(
                                            CachedScheduleEntry(
                                                hourInt = hourInt,
                                                minuteInt = minuteInt,
                                                lineCode = lineCode,
                                                direction = direction
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching scheduled departures from remote API for stop $stopId: ${e.message}")
            }
            return entries
        }

        private fun buildDeparturesFromEntries(
            entries: List<CachedScheduleEntry>
        ): List<MetrobusDepartureUiModel> {
            if (entries.isEmpty()) return emptyList()
            val list = mutableListOf<MetrobusDepartureUiModel>()
            val madridZone = TimeZone.getTimeZone("Europe/Madrid")
            val calNow = Calendar.getInstance(madridZone)
            val currentHour = calNow.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calNow.get(Calendar.MINUTE)
            val currentSecond = calNow.get(Calendar.SECOND)
            val currentTotalMin = currentHour * 60 + currentMinute

            for (entry in entries) {
                val hourInt = entry.hourInt
                val minuteInt = entry.minuteInt
                val lineCode = entry.lineCode
                val direction = entry.direction

                val normalizedHour = hourInt % 24
                val formattedTime = String.format(Locale.ROOT, "%02d:%02d", normalizedHour, minuteInt)

                val addDeparture = { dMin: Int, isTom: Boolean, dLabel: String ->
                    if (dMin >= -2) {
                        val safeDiffMin = dMin.coerceAtLeast(0)
                        val secondsRemaining = (safeDiffMin * 60) - currentSecond
                        list.add(
                            MetrobusDepartureUiModel(
                                lineCode = lineCode,
                                lineName = "Línea $lineCode",
                                destination = direction,
                                minutesRemaining = safeDiffMin,
                                timeLabel = formattedTime,
                                departureTime = formattedTime,
                                routeColor = "#FBC02D",
                                isRealTime = false,
                                agencyName = "Metrobús",
                                secondsRemaining = secondsRemaining.coerceAtLeast(0),
                                isTomorrow = isTom,
                                dayLabel = dLabel
                            )
                        )
                    }
                }

                if (currentHour < 6) {
                    if (hourInt >= 24) {
                        val effTodayMin = (hourInt - 24) * 60 + minuteInt
                        addDeparture(effTodayMin - currentTotalMin, false, "Esta noche")
                    } else if (hourInt < 6) {
                        val effTodayMin = hourInt * 60 + minuteInt
                        addDeparture(effTodayMin - currentTotalMin, false, "Hoy")
                        addDeparture((1440 + effTodayMin) - currentTotalMin, true, "Mañana")
                    } else {
                        val effTodayMin = hourInt * 60 + minuteInt
                        addDeparture(effTodayMin - currentTotalMin, false, "Hoy")
                        addDeparture((1440 + effTodayMin) - currentTotalMin, true, "Mañana")
                    }
                } else {
                    if (hourInt >= 24) {
                        val depTotalMin = hourInt * 60 + minuteInt
                        addDeparture(depTotalMin - currentTotalMin, false, "Esta noche")
                    } else if (currentHour >= 20 && hourInt < 6) {
                        val depTotalMin = (hourInt + 24) * 60 + minuteInt
                        addDeparture(depTotalMin - currentTotalMin, false, "Esta noche")
                    } else {
                        val depTodayMin = hourInt * 60 + minuteInt
                        if (depTodayMin >= currentTotalMin - 2) {
                            addDeparture(depTodayMin - currentTotalMin, false, "Hoy")
                            addDeparture((1440 + depTodayMin) - currentTotalMin, true, "Mañana")
                        } else {
                            addDeparture((1440 + depTodayMin) - currentTotalMin, true, "Mañana")
                        }
                    }
                }
            }

            return list
                .distinctBy { "${it.lineCode}__${it.destination}__${it.departureTime}__${it.isTomorrow}" }
                .sortedBy { it.minutesRemaining }
        }
    }
}

