package com.example.data.repository

import android.util.Log
import com.example.data.network.NetworkModule
import com.example.data.repository.renfe.GtfsCacheManager
import com.example.data.repository.renfe.GtfsNetworkDataSource
import com.example.data.repository.renfe.GtfsParser
import com.example.data.repository.renfe.GtfsRtTripUpdate
import com.example.ui.bus.BusMapper
import com.example.ui.bus.EmtBusTime
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class MetroArrival(
    val line: String,
    val destination: String,
    val minutes: Int,
    val seconds: Int
)

/**
 * Unified Central Real-Time Repository (Single Source of Truth)
 * Serves live real-time information for EMT Bus, Metrovalencia, and Renfe Cercanías.
 * Shares cache between Map, Stop Details, Trip Navigation, and Route Planner.
 */
object RealTimeTransitRepository {

    private const val TAG = "RealTimeTransitRepo"
    private const val CACHE_TTL_MS = 30_000L // 30s TTL

    private val emtArrivalsCache = ConcurrentHashMap<String, Pair<Long, List<EmtBusTime>>>()
    private val metroDeparturesCache = ConcurrentHashMap<String, Pair<Long, List<MetroArrival>>>()

    private val gtfsCacheManager by lazy {
        GtfsCacheManager(
            networkDataSource = GtfsNetworkDataSource(),
            gtfsParser = GtfsParser()
        )
    }

    private val standardHttpClient: OkHttpClient by lazy {
        NetworkModule.okHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val fastHttpClient: OkHttpClient by lazy {
        NetworkModule.okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun executeGetRequest(
        url: String,
        headers: Map<String, String> = emptyMap(),
        useFastTimeout: Boolean = false
    ): String? = suspendCancellableCoroutine { continuation ->
        val client = if (useFastTimeout) fastHttpClient else standardHttpClient
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val call = client.newCall(requestBuilder.build())

        continuation.invokeOnCancellation {
            try {
                call.cancel()
            } catch (_: Throwable) {}
        }

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    try {
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            continuation.resume(body)
                        } else {
                            continuation.resume(null)
                        }
                    } catch (e: Exception) {
                        continuation.resume(null)
                    } finally {
                        response.close()
                    }
                } else {
                    response.close()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        })
    }

    // ==========================================
    // 1. EMT BUS REAL-TIME ARRIVALS
    // ==========================================

    suspend fun getEmtLiveArrivals(
        stopNumber: String,
        forceRefresh: Boolean = false,
        useFastTimeout: Boolean = false
    ): List<EmtBusTime> = withContext(Dispatchers.IO) {
        val cleanStop = stopNumber.trim()
        if (cleanStop.isBlank()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            emtArrivalsCache[cleanStop]?.let { (timestamp, data) ->
                if (now - timestamp < CACHE_TTL_MS) {
                    return@withContext data
                }
            }
        }

        val url = "https://www.emtvalencia.es/EMT/mapfunctions/MapUtilsPetitions.php?sec=getSAE&parada=$cleanStop"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Accept" to "*/*",
            "Referer" to "https://www.emtvalencia.es"
        )

        val xml = executeGetRequest(url, headers, useFastTimeout) ?: return@withContext emtArrivalsCache[cleanStop]?.second ?: emptyList()
        val list = BusMapper.parseEmtXml(xml)
        if (list.isNotEmpty()) {
            emtArrivalsCache[cleanStop] = Pair(now, list)
        }
        list
    }

    // ==========================================
    // 2. METROVALENCIA REAL-TIME ARRIVALS
    // ==========================================

    suspend fun getMetroLiveArrivals(
        stationId: String,
        forceRefresh: Boolean = false,
        useFastTimeout: Boolean = false
    ): List<MetroArrival> = withContext(Dispatchers.IO) {
        val cleanStation = stationId.trim()
        if (cleanStation.isBlank()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            metroDeparturesCache[cleanStation]?.let { (timestamp, data) ->
                if (now - timestamp < CACHE_TTL_MS) {
                    return@withContext data
                }
            }
        }

        val url = "https://metroapi.alexbadi.es/prevision/$cleanStation/parse"
        val headers = mapOf("User-Agent" to com.example.data.network.NetworkModule.USER_AGENT)

        val bodyStr = executeGetRequest(url, headers, useFastTimeout) ?: return@withContext metroDeparturesCache[cleanStation]?.second ?: emptyList()

            try {
                val jsonObject = JSONObject(bodyStr)
                val previsArray = jsonObject.optJSONArray("previsiones") ?: jsonObject.optJSONArray("previsiion") ?: return@withContext emptyList()

                val list = mutableListOf<MetroArrival>()
                for (i in 0 until previsArray.length()) {
                    val item = previsArray.optJSONObject(i) ?: continue
                    val lineNum = item.optInt("line", 0)
                    val lineId = if (lineNum > 0) "L$lineNum" else "L"
                    val destino = item.optString("destino", "Desconocido")
                    val seconds = item.optInt("seconds", 0)
                    if (seconds < 0) continue
                    list.add(
                        MetroArrival(
                            line = lineId,
                            destination = destino,
                            minutes = seconds / 60,
                            seconds = seconds
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    metroDeparturesCache[cleanStation] = Pair(now, list)
                }
                list
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse live Metro arrivals for station $cleanStation: ${e.message}")
                metroDeparturesCache[cleanStation]?.second ?: emptyList()
            }
        }

    // ==========================================
    // 3. RENFE CERCANÍAS GTFS-RT REAL-TIME
    // ==========================================

    suspend fun getCercaniasLivePositions(): Map<String, LiveVehicleInfo> = withContext(Dispatchers.IO) {
        gtfsCacheManager.getLiveVehiclePositions()
    }

    suspend fun getCercaniasTripUpdates(): Map<String, GtfsRtTripUpdate> = withContext(Dispatchers.IO) {
        gtfsCacheManager.getLiveTripUpdates()
    }

    fun clearAllCaches() {
        emtArrivalsCache.clear()
        metroDeparturesCache.clear()
    }
}
