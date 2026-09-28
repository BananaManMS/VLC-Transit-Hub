package com.example.data.repository.renfe

import android.content.Context
import android.util.Log
import com.example.data.network.NetworkModule
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GtfsCacheManager(private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var cachedTripUpdates: Map<String, GtfsRtTripUpdate> = emptyMap()

    @Volatile
    private var cachedVehiclePositions: Map<String, LiveVehicleInfo> = emptyMap()

    @Volatile
    private var lastFetchTimeMs: Long = 0L

    private val cacheTtlMs = 15_000L // 15s official update cadence

    suspend fun getLiveTripUpdates(): Map<String, GtfsRtTripUpdate> = withContext(Dispatchers.IO) {
        ensureCacheUpdated()
        cachedTripUpdates
    }

    suspend fun getLiveVehiclePositions(): Map<String, LiveVehicleInfo> = withContext(Dispatchers.IO) {
        ensureCacheUpdated()
        cachedVehiclePositions
    }

    private suspend fun ensureCacheUpdated() {
        val now = System.currentTimeMillis()
        if (now - lastFetchTimeMs < cacheTtlMs && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
            return
        }

        mutex.withLock {
            if (now - lastFetchTimeMs < cacheTtlMs && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
                return
            }

            try {
                fetchRealTimeFeeds()
                lastFetchTimeMs = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.w("GtfsCacheManager", "Error fetching Renfe GTFS-RT: ${e.message}")
            }
        }
    }

    private fun fetchRealTimeFeeds() {
        val tripUpdatesMap = mutableMapOf<String, GtfsRtTripUpdate>()
        val vehiclePositionsMap = mutableMapOf<String, LiveVehicleInfo>()

        // 1. Primary: Renfe Flota Real-Time JSON (rich telemetries with current track & next track)
        try {
            val unixTime = System.currentTimeMillis()
            val req = Request.Builder()
                .url("https://tiempo-real.renfe.com/renfe-visor/flota.json?v=$unixTime")
                .header("User-Agent", NetworkModule.USER_AGENT)
                .header("Referer", "https://tiempo-real.renfe.com/")
                .build()

            NetworkModule.okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        parseFlotaJson(body, tripUpdatesMap, vehiclePositionsMap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("GtfsCacheManager", "Network call to flota.json failed: ${e.message}")
        }

        // 2. Renfe Trip Updates JSON (for cancellation status and detailed stop delays)
        try {
            val req = Request.Builder()
                .url("https://gtfsrt.renfe.com/trip_updates.json")
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            NetworkModule.okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        parseTripUpdatesJson(body, tripUpdatesMap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("GtfsCacheManager", "Network call to trip_updates.json failed: ${e.message}")
        }

        // 3. Renfe Vehicle Positions JSON (complementary/fallback)
        try {
            val req = Request.Builder()
                .url("https://gtfsrt.renfe.com/vehicle_positions.json")
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            NetworkModule.okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        parseVehiclePositionsJson(body, vehiclePositionsMap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("GtfsCacheManager", "Network call to vehicle_positions.json failed: ${e.message}")
        }

        val nowSec = System.currentTimeMillis() / 1000L
        val graceCutoffSec = nowSec - 30L // 30s retention (exactly 2 API polling cycles of 15s)

        // Merge newly fetched data with still-valid cached data to bridge single-cycle Renfe feed dropouts
        val mergedTripUpdates = cachedTripUpdates.filter { it.value.timestamp >= graceCutoffSec }.toMutableMap()
        mergedTripUpdates.putAll(tripUpdatesMap)

        val mergedVehiclePositions = cachedVehiclePositions.filter { it.value.timestamp >= graceCutoffSec }.toMutableMap()
        mergedVehiclePositions.putAll(vehiclePositionsMap)

        cachedTripUpdates = mergedTripUpdates
        cachedVehiclePositions = mergedVehiclePositions
    }

    private fun parseFlotaJson(
        jsonStr: String,
        tripUpdatesMap: MutableMap<String, GtfsRtTripUpdate>,
        vehiclePositionsMap: MutableMap<String, LiveVehicleInfo>
    ) {
        val root = JSONObject(jsonStr)
        val trenes = root.optJSONArray("trenes") ?: return

        for (i in 0 until trenes.length()) {
            val t = trenes.optJSONObject(i) ?: continue
            val tripId = t.optString("tripId", "").trim()
            val codTren = t.optString("codTren", "").trim()
            val codLinea = t.optString("codLinea", "").trim()
            val delayMin = t.optString("retrasoMin", "0").toIntOrNull() ?: 0
            val delaySeconds = delayMin * 60L
            val codEstAct = t.optString("codEstAct", "").trim()
            val via = t.optString("via", "").trim()
            val codEstSig = t.optString("codEstSig", "").trim()
            val nextVia = t.optString("nextVia", "").trim()
            val porAvanc = t.optString("porAvanc", "").trim()
            val horaLlegadaSigEst = t.optString("horaLlegadaSigEst", "").trim()

            val lat = t.optDouble("latitud", Double.NaN).takeIf { !it.isNaN() }
            val lon = t.optDouble("longitud", Double.NaN).takeIf { !it.isNaN() }

            val status = when (porAvanc) {
                "E" -> "STOPPED_AT"
                "A" -> "INCOMING_AT"
                else -> "IN_TRANSIT_TO"
            }

            // Platforms map: current station -> via, next station -> nextVia
            val platforms = mutableMapOf<String, String>()
            if (codEstAct.isNotBlank() && via.isNotBlank()) {
                platforms[codEstAct] = via
            }
            if (codEstSig.isNotBlank() && nextVia.isNotBlank()) {
                platforms[codEstSig] = nextVia
            }

            val stopDelays = mutableMapOf<String, Long>()
            if (codEstAct.isNotBlank()) stopDelays[codEstAct] = delaySeconds
            if (codEstSig.isNotBlank()) stopDelays[codEstSig] = delaySeconds

            val tu = GtfsRtTripUpdate(
                tripId = tripId,
                routeId = codLinea,
                delaySeconds = delaySeconds,
                stopDelays = stopDelays,
                stopPlatforms = platforms,
                vehicleId = codTren,
                vehicleLabel = if (via.isNotBlank()) "$codLinea-$codTren-PLATF.($via)" else "$codLinea-$codTren",
                timestamp = System.currentTimeMillis() / 1000L
            )

            val vp = LiveVehicleInfo(
                tripId = tripId,
                routeId = codLinea,
                latitude = lat,
                longitude = lon,
                status = status,
                platform = via,
                currentStopId = codEstAct,
                timestamp = System.currentTimeMillis() / 1000L,
                nextStopId = codEstSig,
                nextPlatform = nextVia,
                nextArrivalTime = horaLlegadaSigEst,
                delayMinutes = delayMin
            )

            val keys = mutableListOf<String>()
            if (tripId.isNotBlank()) keys.add(tripId)
            if (codTren.isNotBlank()) keys.add(codTren)
            com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(tripId)?.let { keys.add(it) }
            com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(codTren)?.let { keys.add(it) }

            for (k in keys.distinct()) {
                if (k.isNotBlank()) {
                    tripUpdatesMap[k] = tu
                    vehiclePositionsMap[k] = vp
                }
            }
        }
    }

    private fun parseTripUpdatesJson(jsonStr: String, outMap: MutableMap<String, GtfsRtTripUpdate>) {
        val root = JSONObject(jsonStr)
        val entities = root.optJSONArray("entity") ?: root.optJSONArray("entities") ?: return

        for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            val tripUpdateObj = entity.optJSONObject("trip_update") ?: entity.optJSONObject("tripUpdate") ?: continue
            val tripObj = tripUpdateObj.optJSONObject("trip") ?: continue

            val tripId = tripObj.optString("trip_id", "").ifBlank { tripObj.optString("tripId", "") }
            if (tripId.isBlank()) continue

            val routeId = tripObj.optString("route_id", "").ifBlank { tripObj.optString("routeId", "") }
            val delaySec = tripUpdateObj.optLong("delay", 0L)
            val scheduleRel = tripObj.optString("schedule_relationship", tripObj.optString("scheduleRelationship", "SCHEDULED"))
            val isCanceled = scheduleRel.equals("CANCELED", ignoreCase = true)

            val vehObj = tripUpdateObj.optJSONObject("vehicle")
            val vehicleId = vehObj?.optString("id", "") ?: ""
            val vehicleLabel = vehObj?.optString("label", "") ?: ""

            val stopDelays = mutableMapOf<String, Long>()
            val stopEstTimes = mutableMapOf<String, Long>()
            val stopPlatforms = mutableMapOf<String, String>()
            val stopTimesList = mutableListOf<GtfsRtStopTime>()
            var isIndeterminate = false

            val stopUpdates = tripUpdateObj.optJSONArray("stop_time_update") ?: tripUpdateObj.optJSONArray("stopTimeUpdate")
            if (stopUpdates != null) {
                for (j in 0 until stopUpdates.length()) {
                    val stu = stopUpdates.optJSONObject(j) ?: continue
                    val stopId = stu.optString("stop_id", "").ifBlank { stu.optString("stopId", "") }
                    val stopSeq = stu.optInt("stop_sequence", stu.optInt("stopSequence", j + 1))
                    
                    val depObj = stu.optJSONObject("departure")
                    val arrObj = stu.optJSONObject("arrival")

                    val depDelay = depObj?.optLong("delay") ?: arrObj?.optLong("delay")
                    val depTime = depObj?.optLong("time") ?: arrObj?.optLong("time")
                    val arrDelay = arrObj?.optLong("delay")

                    val platform = stu.optString("platform", depObj?.optString("platform", "") ?: "")
                    if (platform.isNotBlank() && stopId.isNotBlank()) {
                        stopPlatforms[stopId] = platform
                    }

                    if (depDelay != null && stopId.isNotBlank()) {
                        stopDelays[stopId] = depDelay
                    }
                    if (depTime != null && depTime > 0L && stopId.isNotBlank()) {
                        stopEstTimes[stopId] = depTime
                    }

                    // Detect indeterminate departure (e.g. Renfe 24294 at Platja i Grau de Gandia)
                    val stopIndeterminate = (depObj != null && (depTime == null || depTime <= 0L) && !isCanceled)
                    if (stopIndeterminate) {
                        isIndeterminate = true
                    }

                    stopTimesList.add(
                        GtfsRtStopTime(
                            stopId = stopId,
                            stopSequence = stopSeq,
                            arrivalDelay = arrDelay,
                            departureDelay = depDelay,
                            estimatedDepartureTime = depTime,
                            platform = platform.ifBlank { null },
                            isIndeterminate = stopIndeterminate
                        )
                    )
                }
            }

            val existingUpdate = outMap[tripId]
            val mergedPlatforms = (existingUpdate?.stopPlatforms ?: emptyMap()) + stopPlatforms
            val mergedDelays = (existingUpdate?.stopDelays ?: emptyMap()) + stopDelays

            val update = GtfsRtTripUpdate(
                tripId = tripId,
                routeId = routeId.ifBlank { existingUpdate?.routeId ?: "" },
                delaySeconds = if (delaySec != 0L) delaySec else (existingUpdate?.delaySeconds ?: 0L),
                stopDelays = mergedDelays,
                stopEstimatedTimes = stopEstTimes,
                stopPlatforms = mergedPlatforms,
                stopTimes = stopTimesList,
                vehicleId = vehicleId.ifBlank { existingUpdate?.vehicleId ?: "" },
                vehicleLabel = vehicleLabel.ifBlank { existingUpdate?.vehicleLabel ?: "" },
                isIndeterminateDeparture = isIndeterminate,
                isCanceled = isCanceled || (existingUpdate?.isCanceled == true),
                timestamp = tripUpdateObj.optLong("timestamp", System.currentTimeMillis() / 1000L)
            )

            outMap[tripId] = update
            if (vehicleId.isNotBlank()) {
                outMap[vehicleId] = update
            }

            // Index by train numbers extracted from tripId, vehicleId, and vehicleLabel
            val trainNumbers = mutableListOf<String>()
            com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(tripId)?.let { trainNumbers.add(it) }
            com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(vehicleId)?.let { trainNumbers.add(it) }
            com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(vehicleLabel)?.let { trainNumbers.add(it) }
            for (tn in trainNumbers.distinct()) {
                if (tn.isNotBlank()) {
                    outMap[tn] = update
                }
            }
        }
    }

    private fun parseVehiclePositionsJson(jsonStr: String, outMap: MutableMap<String, LiveVehicleInfo>) {
        val root = JSONObject(jsonStr)
        val entities = root.optJSONArray("entity") ?: root.optJSONArray("entities") ?: return

        for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            val vehEntity = entity.optJSONObject("vehicle") ?: continue
            val tripObj = vehEntity.optJSONObject("trip")

            val tripId = tripObj?.optString("trip_id", "")?.ifBlank { tripObj.optString("tripId", "") } ?: ""
            val routeId = tripObj?.optString("route_id", "")?.ifBlank { tripObj.optString("routeId", "") } ?: ""

            val posObj = vehEntity.optJSONObject("position")
            val lat = posObj?.optDouble("latitude", Double.NaN)?.takeIf { !it.isNaN() }
            val lon = posObj?.optDouble("longitude", Double.NaN)?.takeIf { !it.isNaN() }
            val speed = posObj?.optDouble("speed", Double.NaN)?.takeIf { !it.isNaN() }

            val status = vehEntity.optString("current_status", vehEntity.optString("currentStatus", "IN_TRANSIT_TO"))
            val stopId = vehEntity.optString("stop_id", vehEntity.optString("stopId", ""))
            val timestamp = vehEntity.optLong("timestamp", System.currentTimeMillis() / 1000L)

            val vehicleObj = vehEntity.optJSONObject("vehicle")
            val vehicleId = vehicleObj?.optString("id", "") ?: ""
            val vehicleLabel = vehicleObj?.optString("label", "") ?: ""

            // Platform extraction from vehicle platform field or from label (e.g. "C1-24225-PLATF.(1)")
            var platform = vehicleObj?.optString("platform", "") ?: ""
            if (platform.isBlank() && vehicleLabel.isNotBlank()) {
                val platRegex = Regex("""PLATF\.\((\d+)\)|V[IÍ]A\s*(\d+)""", RegexOption.IGNORE_CASE)
                val m = platRegex.find(vehicleLabel)
                if (m != null) {
                    platform = m.groupValues[1].ifBlank { m.groupValues[2] }
                }
            }

            if (tripId.isNotBlank() || vehicleId.isNotBlank()) {
                val existing = outMap[tripId] ?: outMap[vehicleId]
                val info = LiveVehicleInfo(
                    tripId = tripId.ifBlank { existing?.tripId ?: "" },
                    routeId = routeId.ifBlank { existing?.routeId ?: "" },
                    latitude = lat ?: existing?.latitude,
                    longitude = lon ?: existing?.longitude,
                    status = if (status.isNotBlank()) status else (existing?.status ?: "IN_TRANSIT_TO"),
                    platform = platform.ifBlank { existing?.platform ?: "" },
                    speed = speed ?: existing?.speed,
                    currentStopId = stopId.ifBlank { existing?.currentStopId ?: "" },
                    timestamp = timestamp,
                    nextStopId = existing?.nextStopId ?: "",
                    nextPlatform = existing?.nextPlatform ?: "",
                    nextArrivalTime = existing?.nextArrivalTime ?: "",
                    delayMinutes = existing?.delayMinutes ?: 0
                )
                if (tripId.isNotBlank()) {
                    outMap[tripId] = info
                }
                if (vehicleId.isNotBlank()) {
                    outMap[vehicleId] = info
                }

                // Index by train numbers extracted from tripId, vehicleId, and vehicleLabel
                val trainNumbers = mutableListOf<String>()
                com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(tripId)?.let { trainNumbers.add(it) }
                com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(vehicleId)?.let { trainNumbers.add(it) }
                com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(vehicleLabel)?.let { trainNumbers.add(it) }
                for (tn in trainNumbers.distinct()) {
                    if (tn.isNotBlank()) {
                        outMap[tn] = info
                    }
                }
            }
        }
    }
}
