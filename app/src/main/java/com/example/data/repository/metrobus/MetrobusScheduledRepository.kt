package com.example.data.repository.metrobus

import android.content.Context
import android.util.Log
import com.example.data.network.NetworkModule
import com.example.ui.bus.MetrobusDepartureUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MetrobusScheduledRepository(private val context: Context) {

    companion object {
        private const val TAG = "MetrobusScheduledRepo"
        private const val API_SECRET = "ApiVLCTH2584"
        private const val METROBUS_API_BASE = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/metrobus"

        suspend fun fetchScheduledDepartures(
            stopId: String,
            limitPerLine: Int = 5,
            linesMap: Map<String, Any?> = emptyMap(),
            client: OkHttpClient? = null
        ): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
            val cleanStop = stopId.trim()
            if (cleanStop.isBlank()) return@withContext emptyList()

            val httpClient = client ?: NetworkModule.okHttpClient

            val remoteResults = tryFetchFromApi(cleanStop, httpClient)
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

        private fun tryFetchFromApi(
            stopId: String,
            client: OkHttpClient
        ): List<MetrobusDepartureUiModel> {
            val list = mutableListOf<MetrobusDepartureUiModel>()
            val madridZone = TimeZone.getTimeZone("Europe/Madrid")
            val calNow = Calendar.getInstance(madridZone)
            val currentHour = calNow.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calNow.get(Calendar.MINUTE)
            val currentSecond = calNow.get(Calendar.SECOND)
            val currentTotalMin = currentHour * 60 + currentMinute

            try {
                val url = "$METROBUS_API_BASE/paradas/$stopId/horario"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("X-App-Secret", API_SECRET)
                    .addHeader("X-API-Secret", API_SECRET)
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    response.close()
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

                                    val depTotalMin = hourInt * 60 + minuteInt
                                    val diffMin = depTotalMin - currentTotalMin

                                    if (diffMin >= 0) {
                                        val secondsRemaining = (diffMin * 60) - currentSecond
                                        val formattedTime = String.format(Locale.ROOT, "%02d:%02d", hourInt, minuteInt)

                                        list.add(
                                            MetrobusDepartureUiModel(
                                                lineCode = lineCode,
                                                lineName = "Línea $lineCode",
                                                destination = direction,
                                                minutesRemaining = diffMin,
                                                timeLabel = formattedTime,
                                                departureTime = formattedTime,
                                                routeColor = "#FBC02D",
                                                isRealTime = false,
                                                agencyName = "Metrobús",
                                                secondsRemaining = secondsRemaining.coerceAtLeast(0)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching scheduled departures from remote API for stop $stopId: ${e.message}")
            }
            return list
        }
    }
}

