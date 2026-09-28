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
    val seconds: Int,
    val estimatedTime: String? = null,
    val status: String? = null,
    val track: String? = null,
    val vehicleId: String? = null,
    val capacidad: Int? = null,
    val aforoBloqueado: com.example.ui.metro.AforoBloqueado? = null,
    val isRealTime: Boolean = true
)

/**
 * Unified Central Real-Time Repository (Single Source of Truth)
 * Serves live real-time information for EMT Bus, Metrovalencia, and Renfe Cercanías.
 * Shares cache between Map, Stop Details, Trip Navigation, and Route Planner.
 */
object RealTimeTransitRepository {

    private const val TAG = "RealTimeTransitRepo"
    private const val CACHE_TTL_MS = 30_000L // 30s TTL

    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private val emtArrivalsCache = ConcurrentHashMap<String, Pair<Long, List<EmtBusTime>>>()
    private val metroDeparturesCache = ConcurrentHashMap<String, Pair<Long, List<MetroArrival>>>()
    private val aforoBloqueadoCache = ConcurrentHashMap<String, com.example.ui.metro.AforoBloqueado>()

    fun getAforoBloqueadoForStation(stationId: String): com.example.ui.metro.AforoBloqueado? {
        val cleanStation = stationId.trimStart('0')
        return aforoBloqueadoCache[cleanStation] ?: aforoBloqueadoCache[stationId]
    }

    private val gtfsCacheManager by lazy {
        GtfsCacheManager(appContext ?: com.example.MainApplication.instance)
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
        stopName: String? = null,
        forceRefresh: Boolean = false,
        useFastTimeout: Boolean = false,
        limitPerLine: Int = 3,
        includeScheduled: Boolean = false
    ): List<EmtBusTime> = withContext(Dispatchers.IO) {
        val cleanStop = stopNumber.trim()
        if (cleanStop.isBlank()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        if (!forceRefresh && !includeScheduled) {
            emtArrivalsCache[cleanStop]?.let { (timestamp, data) ->
                if (now - timestamp < CACHE_TTL_MS) {
                    val elapsedSec = ((now - timestamp) / 1000L).toInt()
                    val adjusted = data.mapNotNull { bus ->
                        if (bus.secondsRemaining > 0) {
                            val remainingSec = bus.secondsRemaining - elapsedSec
                            if (remainingSec >= -30) {
                                val safeSec = remainingSec.coerceAtLeast(0)
                                val safeMins = safeSec / 60
                                val minStr = if (safeMins <= 0) "1" else safeMins.toString()
                                bus.copy(
                                    secondsRemaining = safeSec,
                                    minutos = minStr
                                )
                            } else null
                        } else {
                            bus
                        }
                    }
                    if (adjusted.isNotEmpty()) {
                        return@withContext adjusted
                    }
                }
            }
        }

        val url = "https://www.emtvalencia.es/EMT/mapfunctions/MapUtilsPetitions.php?sec=getSAE&parada=$cleanStop"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Accept" to "*/*",
            "Referer" to "https://www.emtvalencia.es"
        )

        val xml = executeGetRequest(url, headers, useFastTimeout)
        val liveList = if (xml != null) BusMapper.parseEmtXml(xml) else emptyList()
        if (liveList.isNotEmpty()) {
            emtArrivalsCache[cleanStop] = Pair(now, liveList)
        }

        if (liveList.isEmpty()) {
            val cached = emtArrivalsCache[cleanStop]?.second
            if (!cached.isNullOrEmpty()) {
                return@withContext cached
            }
            val scheduledList = com.example.data.repository.emt.EmtScheduledRepository.fetchScheduledDepartures(
                stopId = cleanStop,
                stopName = stopName,
                limitPerLine = limitPerLine,
                context = appContext
            )
            return@withContext scheduledList
        }

        if (!includeScheduled) {
            return@withContext liveList
        }

        val scheduledList = com.example.data.repository.emt.EmtScheduledRepository.fetchScheduledDepartures(
            stopId = cleanStop,
            stopName = stopName,
            limitPerLine = limitPerLine,
            context = appContext
        )

        val combined = liveList.toMutableList()
        val maxLiveSecByLine = liveList.groupBy { "${it.linea}__${it.destino}" }
            .mapValues { entry -> entry.value.maxOf { it.secondsRemaining } }

        scheduledList.forEach { sched ->
            val key = "${sched.linea}__${sched.destino}"
            val maxLiveSec = maxLiveSecByLine[key]
            if (maxLiveSec == null || sched.secondsRemaining > (maxLiveSec + 120)) {
                combined.add(sched)
            }
        }

        combined.sortedBy { if (it.secondsRemaining < 0) 99999 else it.secondsRemaining }
    }

    suspend fun getEmtScheduledDepartures(
        stopNumber: String,
        stopName: String? = null,
        limitPerLine: Int = 5
    ): List<EmtBusTime> = withContext(Dispatchers.IO) {
        val cleanStop = stopNumber.trim()
        if (cleanStop.isBlank()) return@withContext emptyList()
        com.example.data.repository.emt.EmtScheduledRepository.fetchScheduledDepartures(
            stopId = cleanStop,
            stopName = stopName,
            limitPerLine = limitPerLine
        )
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

        fun getValidCachedArrivals(): List<MetroArrival> {
            val cached = metroDeparturesCache[cleanStation] ?: return emptyList()
            val (timestamp, data) = cached
            if (now - timestamp < CACHE_TTL_MS) {
                val elapsedSec = ((now - timestamp) / 1000L).toInt()
                return data.mapNotNull { arrival ->
                    val remainingSec = arrival.seconds - elapsedSec
                    if (remainingSec >= 0) {
                        arrival.copy(
                            seconds = remainingSec,
                            minutes = remainingSec / 60
                        )
                    } else null
                }
            } else {
                metroDeparturesCache.remove(cleanStation)
                return emptyList()
            }
        }

        if (!forceRefresh) {
            val validCached = getValidCachedArrivals()
            if (validCached.isNotEmpty()) {
                return@withContext validCached
            }
        }

        val url = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/estaciones/$cleanStation/llegadas"
        val headers = mapOf("User-Agent" to com.example.data.network.NetworkModule.USER_AGENT)

        val bodyStr = executeGetRequest(url, headers, useFastTimeout)
        
        if (bodyStr == null) {
            val validCached = getValidCachedArrivals()
            if (validCached.isNotEmpty()) {
                return@withContext validCached
            }
        }

        val list = mutableListOf<MetroArrival>()
        if (!bodyStr.isNullOrBlank() && !bodyStr.trim().startsWith("<")) {
            try {
                val jsonObject = JSONObject(bodyStr)
                val success = jsonObject.optBoolean("success", false)
                val dataObj = jsonObject.optJSONObject("data")

                val aforoObj = dataObj?.optJSONObject("aforo_bloqueado") ?: jsonObject.optJSONObject("aforo_bloqueado")
                val parsedAforo = if (aforoObj != null) {
                    val desdeRaw = if (!aforoObj.isNull("desde")) {
                        aforoObj.optString("desde", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                    } else null
                    val hastaRaw = if (!aforoObj.isNull("hasta")) {
                        aforoObj.optString("hasta", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                    } else null
                    val activoBool = if (!aforoObj.isNull("activo")) aforoObj.optBoolean("activo", false) else false
                    if (activoBool || desdeRaw != null || hastaRaw != null) {
                        com.example.ui.metro.AforoBloqueado(desde = desdeRaw, hasta = hastaRaw, activo = activoBool)
                    } else null
                } else null

                if (parsedAforo != null) {
                    aforoBloqueadoCache[cleanStation] = parsedAforo
                } else {
                    aforoBloqueadoCache.remove(cleanStation)
                }

                val previsArray = if (success && dataObj != null) {
                    dataObj.optJSONArray("trenes") ?: dataObj.optJSONArray("destinos") ?: dataObj.optJSONArray("llegadas") ?: dataObj.optJSONArray("salidas") ?: dataObj.optJSONArray("previsiones") ?: org.json.JSONArray()
                } else {
                    jsonObject.optJSONArray("trenes") ?: jsonObject.optJSONArray("destinos") ?: jsonObject.optJSONArray("llegadas") ?: jsonObject.optJSONArray("salidas") ?: jsonObject.optJSONArray("previsiones") ?: jsonObject.optJSONArray("previsiion") ?: org.json.JSONArray()
                }

                for (i in 0 until previsArray.length()) {
                    val item = previsArray.optJSONObject(i) ?: continue
                    val lineNum = item.optInt("linea", item.optInt("line", 0))
                    val lineId = if (lineNum > 0) "L$lineNum" else {
                        val lineStr = item.optString("linea", "")
                        if (lineStr.isNotEmpty()) {
                            if (lineStr.startsWith("L")) lineStr else "L$lineStr"
                        } else "L"
                    }
                    val cleanLine = lineId.replace("L", "").trim()
                    if (com.example.util.MetroDepotFilterHelper.isDepotExcludedStationLine(cleanStation, null, cleanLine)) {
                        continue
                    }
                    val destino = item.optString("destino", "Desconocido")
                    val minutes = item.optInt("minutos", -1)
                    val seconds = item.optInt("segundos", item.optInt("seconds", -1))
                    val finalSeconds = if (seconds >= 0) seconds else if (minutes >= 0) minutes * 60 else 0
                    val finalMinutes = if (minutes >= 0) minutes else finalSeconds / 60
                    
                    val estTime = item.optString("hora_estimada", item.optString("horaEstimada", item.optString("hora", item.optString("time", "")))).ifEmpty { null }
                    val status = item.optString("estado", item.optString("status", "")).ifEmpty { null }
                    val trackStr = item.optString("via", item.optString("vía", item.optString("track", item.optString("anden", "")))).ifEmpty { null }
                    val rawVehicle = item.optString("vehiculo", item.optString("vehículo", item.optString("vehicle", item.optString("id_vehiculo", "")))).trim()
                    val vehicleIdStr = if (rawVehicle.isNotEmpty() && !rawVehicle.equals("null", ignoreCase = true)) rawVehicle else null
                    val rawCapacidad = item.optInt("capacidad", item.optInt("capacity", -1)).takeIf { it > 0 }
                    
                    list.add(
                        MetroArrival(
                            line = lineId,
                            destination = destino,
                            minutes = finalMinutes,
                            seconds = finalSeconds,
                            estimatedTime = estTime,
                            status = status,
                            track = trackStr,
                            vehicleId = vehicleIdStr,
                            capacidad = rawCapacidad,
                            aforoBloqueado = parsedAforo
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse live Metro arrivals JSON for station $cleanStation: ${e.message}")
            }
        }

        if (list.isNotEmpty()) {
            metroDeparturesCache[cleanStation] = Pair(now, list)
            return@withContext list
        }

        emptyList()
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
