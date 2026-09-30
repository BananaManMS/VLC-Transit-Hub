package com.example.data.repository.renfe

import android.content.Context
import android.util.Log
import com.example.data.network.NetworkModule
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    @Volatile
    private var lastFlotaEmissionTimestamp: String = ""

    @Volatile
    private var lastTripUpdatesEmissionTimestamp: Long = 0L

    @Volatile
    private var lastVehiclePositionsEmissionTimestamp: Long = 0L

    private val cacheTtlMs = 15_000L // 15s official update cadence

    private val fastClient = NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    suspend fun getLiveTripUpdates(): Map<String, GtfsRtTripUpdate> = withContext(Dispatchers.IO) {
        ensureCacheUpdated()
        cachedTripUpdates
    }

    suspend fun getLiveVehiclePositions(): Map<String, LiveVehicleInfo> = withContext(Dispatchers.IO) {
        ensureCacheUpdated()
        cachedVehiclePositions
    }

    suspend fun getUniqueLiveVehicles(): List<LiveVehicleInfo> = withContext(Dispatchers.IO) {
        ensureCacheUpdated()
        cachedVehiclePositions.values
            .filter { it.latitude != null && it.longitude != null && !it.latitude.isNaN() && !it.longitude.isNaN() && it.latitude != 0.0 && it.longitude != 0.0 }
            .distinctBy { it.tripId.ifBlank { it.trainNum } }
    }

    suspend fun getLiveVehiclesWithFreshness(forceFetch: Boolean = false): Pair<List<LiveVehicleInfo>, Boolean> = withContext(Dispatchers.IO) {
        val hasNewData = ensureCacheUpdated(force = forceFetch)
        val vehicles = cachedVehiclePositions.values
            .filter { it.latitude != null && it.longitude != null && !it.latitude.isNaN() && !it.longitude.isNaN() && it.latitude != 0.0 && it.longitude != 0.0 }
            .distinctBy { it.tripId.ifBlank { it.trainNum } }
        Pair(vehicles, hasNewData)
    }

    private suspend fun ensureCacheUpdated(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchTimeMs < cacheTtlMs && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
            return false
        }

        return mutex.withLock {
            if (!force && now - lastFetchTimeMs < cacheTtlMs && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
                return@withLock false
            }

            try {
                val hasNewData = fetchRealTimeFeeds()
                lastFetchTimeMs = System.currentTimeMillis()
                hasNewData
            } catch (e: Exception) {
                Log.w("GtfsCacheManager", "Error fetching Renfe GTFS-RT: ${e.message}")
                false
            }
        }
    }

    private suspend fun fetchRealTimeFeeds(): Boolean = kotlinx.coroutines.coroutineScope {
        val tripUpdatesMap = java.util.concurrent.ConcurrentHashMap<String, GtfsRtTripUpdate>()
        val vehiclePositionsMap = java.util.concurrent.ConcurrentHashMap<String, LiveVehicleInfo>()
        val anyFeedUpdated = java.util.concurrent.atomic.AtomicBoolean(false)

        val jobFlota = launch(Dispatchers.IO) {
            try {
                val unixTime = System.currentTimeMillis()
                val req = Request.Builder()
                    .url("https://tiempo-real.renfe.com/renfe-visor/flota.json?v=$unixTime")
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .header("Referer", "https://tiempo-real.renfe.com/")
                    .build()

                fastClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val tempTu = mutableMapOf<String, GtfsRtTripUpdate>()
                            val tempVp = mutableMapOf<String, LiveVehicleInfo>()
                            val isFresh = parseFlotaJson(body, tempTu, tempVp)
                            if (isFresh) {
                                anyFeedUpdated.set(true)
                                tripUpdatesMap.putAll(tempTu)
                                mergeVehiclePositions(vehiclePositionsMap, tempVp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("GtfsCacheManager", "Network call to flota.json failed: ${e.message}")
            }
        }

        val jobTripUpdates = launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://gtfsrt.renfe.com/trip_updates.json")
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .build()

                fastClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val tempTu = mutableMapOf<String, GtfsRtTripUpdate>()
                            val isFresh = parseTripUpdatesJson(body, tempTu)
                            if (isFresh) {
                                anyFeedUpdated.set(true)
                                tripUpdatesMap.putAll(tempTu)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("GtfsCacheManager", "Network call to trip_updates.json failed: ${e.message}")
            }
        }

        val jobVehiclePositions = launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://gtfsrt.renfe.com/vehicle_positions.json")
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .build()

                fastClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val tempVp = mutableMapOf<String, LiveVehicleInfo>()
                            val isFresh = parseVehiclePositionsJson(body, tempVp)
                            if (isFresh) {
                                anyFeedUpdated.set(true)
                                mergeVehiclePositions(vehiclePositionsMap, tempVp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("GtfsCacheManager", "Network call to vehicle_positions.json failed: ${e.message}")
            }
        }

        kotlinx.coroutines.joinAll(jobFlota, jobTripUpdates, jobVehiclePositions)

        if (anyFeedUpdated.get()) {
            val nowSec = System.currentTimeMillis() / 1000L
            val graceCutoffSec = nowSec - 30L // 30s retention (exactly 2 API polling cycles of 15s)

            // Merge newly fetched data with still-valid cached data to bridge single-cycle Renfe feed dropouts
            val mergedTripUpdates = cachedTripUpdates.filter { it.value.timestamp >= graceCutoffSec }.toMutableMap()
            mergedTripUpdates.putAll(tripUpdatesMap)

            val mergedVehiclePositions = cachedVehiclePositions.filter { it.value.timestamp >= graceCutoffSec }.toMutableMap()
            mergeVehiclePositions(mergedVehiclePositions, vehiclePositionsMap)

            cachedTripUpdates = mergedTripUpdates
            cachedVehiclePositions = mergedVehiclePositions
            true
        } else {
            false
        }
    }

    private fun mergeVehiclePositions(
        target: MutableMap<String, LiveVehicleInfo>,
        source: Map<String, LiveVehicleInfo>
    ) {
        for ((key, newInfo) in source) {
            val existing = target[key]
            if (existing != null) {
                val resolvedRouteId = if (existing.routeId.isNotBlank() && existing.routeId != "1" && existing.routeId != "0" && existing.routeId != "C1") {
                    existing.routeId
                } else if (newInfo.routeId.isNotBlank() && newInfo.routeId != "1" && newInfo.routeId != "0" && newInfo.routeId != "C1") {
                    newInfo.routeId
                } else {
                    existing.routeId.ifBlank { newInfo.routeId }
                }

                target[key] = newInfo.copy(
                    routeId = resolvedRouteId,
                    trainNum = newInfo.trainNum.ifBlank { existing.trainNum },
                    originName = newInfo.originName.ifBlank { existing.originName },
                    destinationName = newInfo.destinationName.ifBlank { existing.destinationName },
                    currentStopId = newInfo.currentStopId.ifBlank { existing.currentStopId },
                    nextStopId = newInfo.nextStopId.ifBlank { existing.nextStopId },
                    nextPlatform = newInfo.nextPlatform.ifBlank { existing.nextPlatform },
                    nextArrivalTime = newInfo.nextArrivalTime.ifBlank { existing.nextArrivalTime },
                    platform = newInfo.platform.ifBlank { existing.platform },
                    delayMinutes = if (newInfo.delayMinutes != 0) newInfo.delayMinutes else existing.delayMinutes,
                    bearing = newInfo.bearing ?: existing.bearing
                )
            } else {
                target[key] = newInfo
            }
        }
    }

    private fun parseFlotaJson(
        jsonStr: String,
        tripUpdatesMap: MutableMap<String, GtfsRtTripUpdate>,
        vehiclePositionsMap: MutableMap<String, LiveVehicleInfo>
    ): Boolean {
        val root = JSONObject(jsonStr)
        val fecha = root.optString("fecha", "").trim()
        val hora = root.optString("hora", "").trim()
        val fechaHora = root.optString("fechaHora", "").trim()
        val timestamp = root.optLong("timestamp", 0L)
        val headerTs = root.optJSONObject("header")?.optLong("timestamp", 0L) ?: 0L
        val emissionKey = when {
            fechaHora.isNotBlank() -> fechaHora
            hora.isNotBlank() -> "$fecha $hora".trim()
            headerTs > 0L -> "$headerTs"
            timestamp > 0L -> "$timestamp"
            else -> ""
        }
        if (emissionKey.isNotBlank() && emissionKey == lastFlotaEmissionTimestamp) {
            Log.d("GtfsCacheManager", "Flota JSON emission timestamp ($emissionKey) unchanged. Skipping body parse.")
            return false
        }
        val trenes = root.optJSONArray("trenes") ?: return false
        if (emissionKey.isNotBlank()) {
            lastFlotaEmissionTimestamp = emissionKey
        }

        for (i in 0 until trenes.length()) {
            val t = trenes.optJSONObject(i) ?: continue

            // Filter strictly by Valencia nucleus (Nucleo 40)
            val nucleo = t.optString("nucleo", "").trim()
            val codNucleo = t.optString("codNucleo", "").trim()
            val idNucleo = t.optString("idNucleo", "").trim()
            val effectiveNucleo = when {
                nucleo.isNotBlank() -> nucleo
                codNucleo.isNotBlank() -> codNucleo
                idNucleo.isNotBlank() -> idNucleo
                else -> ""
            }
            val codLinea = t.optString("codLinea", t.optString("linea", "")).trim()
            val isValenciaNucleo = effectiveNucleo == "40" || effectiveNucleo == "040" || effectiveNucleo == "40.0"
            val isValenciaLine = codLinea.matches(Regex("(?i)^(?:40|C-?)[1-6].*")) || codLinea.matches(Regex("^[1-6]$"))

            if (effectiveNucleo.isNotBlank() && !isValenciaNucleo) {
                continue
            }
            if (effectiveNucleo.isBlank() && !isValenciaLine) {
                continue
            }

            val tripId = t.optString("tripId", t.optString("idViaje", t.optString("idTren", ""))).trim()
            val codTren = t.optString("codTren", t.optString("numTren", t.optString("tren", ""))).trim()
            val delayMin = t.optString("retrasoMin", "0").toIntOrNull() ?: 0
            val delaySeconds = delayMin * 60L
            val codEstAct = t.optString("codEstAct", "").trim()
            val via = t.optString("via", "").trim()
            val codEstSig = t.optString("codEstSig", "").trim()
            val nextVia = t.optString("nextVia", "").trim()
            val porAvanc = t.optString("porAvanc", "").trim()
            val horaLlegadaSigEst = t.optString("horaLlegadaSigEst", "").trim()

            val lat = t.optDouble("latitud", Double.NaN).takeIf { !it.isNaN() }
                ?: t.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
            val lon = t.optDouble("longitud", Double.NaN).takeIf { !it.isNaN() }
                ?: t.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }

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

            val rawCodLinea = t.optString("codLinea", "").trim()
            val canonicalRouteId = when {
                rawCodLinea.startsWith("C", ignoreCase = true) -> rawCodLinea.uppercase()
                rawCodLinea.isNotBlank() -> "C$rawCodLinea"
                else -> "C${com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber("", tripId)}"
            }
            val effectiveTrainNum = if (codTren.isNotBlank()) codTren else com.example.ui.cercanias.CercaniasRouteUtils.getEffectiveTrainNumber("", tripId)

            val tu = GtfsRtTripUpdate(
                tripId = tripId,
                routeId = canonicalRouteId,
                delaySeconds = delaySeconds,
                stopDelays = stopDelays,
                stopPlatforms = platforms,
                vehicleId = effectiveTrainNum,
                vehicleLabel = if (via.isNotBlank()) "$canonicalRouteId-$effectiveTrainNum-PLATF.($via)" else "$canonicalRouteId-$effectiveTrainNum",
                timestamp = System.currentTimeMillis() / 1000L
            )

            val speed = t.optDouble("velocidad", Double.NaN).takeIf { !it.isNaN() }
                ?: t.optDouble("speed", Double.NaN).takeIf { !it.isNaN() }
            val bearing = t.optDouble("rumbo", Double.NaN).takeIf { !it.isNaN() }?.toFloat()
                ?: t.optDouble("bearing", Double.NaN).takeIf { !it.isNaN() }?.toFloat()
                ?: t.optDouble("orientacion", Double.NaN).takeIf { !it.isNaN() }?.toFloat()

            val codEstOrig = t.optString("codEstOrig", "").trim()
            val codEstDest = t.optString("codEstDest", "").trim()
            val nomEstOrigen = t.optString("nomEstOrigen", t.optString("origen", "")).trim()
            val nomEstDestino = t.optString("nomEstDestino", t.optString("destino", "")).trim()
            val origen = if (nomEstOrigen.isNotBlank()) nomEstOrigen else codEstOrig
            val destino = if (nomEstDestino.isNotBlank()) nomEstDestino else codEstDest

            val vp = LiveVehicleInfo(
                tripId = tripId,
                routeId = canonicalRouteId,
                latitude = lat,
                longitude = lon,
                status = status,
                platform = via,
                speed = speed,
                bearing = bearing,
                currentStopId = codEstAct,
                timestamp = System.currentTimeMillis() / 1000L,
                nextStopId = codEstSig,
                nextPlatform = nextVia,
                nextArrivalTime = horaLlegadaSigEst,
                delayMinutes = delayMin,
                trainNum = effectiveTrainNum,
                originName = origen,
                destinationName = destino
            )

            val keys = (
                com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(tripId) +
                com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(codTren)
            ).distinct().filter { it.isNotBlank() }

            for (k in keys) {
                tripUpdatesMap[k] = tu
                vehiclePositionsMap[k] = vp
            }
        }
        return true
    }

    private fun parseTripUpdatesJson(jsonStr: String, outMap: MutableMap<String, GtfsRtTripUpdate>): Boolean {
        val root = JSONObject(jsonStr)
        val headerObj = root.optJSONObject("header")
        val headerTs = headerObj?.optLong("timestamp", 0L) ?: 0L
        if (headerTs > 0L && headerTs == lastTripUpdatesEmissionTimestamp) {
            Log.d("GtfsCacheManager", "Trip updates header timestamp ($headerTs) unchanged. Skipping body parse.")
            return false
        }
        val entities = root.optJSONArray("entity") ?: root.optJSONArray("entities") ?: return false
        if (headerTs > 0L) {
            lastTripUpdatesEmissionTimestamp = headerTs
        }

        for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            val tripUpdateObj = entity.optJSONObject("trip_update") ?: entity.optJSONObject("tripUpdate") ?: continue
            val tripObj = tripUpdateObj.optJSONObject("trip") ?: continue

            val tripId = tripObj.optString("trip_id", "").ifBlank { tripObj.optString("tripId", "") }
            if (tripId.isBlank()) continue

            val routeId = tripObj.optString("route_id", "").ifBlank { tripObj.optString("routeId", "") }
            if (routeId.isNotBlank() && routeId.first().isDigit() && !routeId.startsWith("40")) {
                continue
            }
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

            val keys = (
                com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(tripId) +
                com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(vehicleId) +
                com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(vehicleLabel)
            ).distinct().filter { it.isNotBlank() }

            for (k in keys) {
                outMap[k] = update
            }
        }
        return true
    }

    private fun parseVehiclePositionsJson(jsonStr: String, outMap: MutableMap<String, LiveVehicleInfo>): Boolean {
        val root = JSONObject(jsonStr)
        val headerObj = root.optJSONObject("header")
        val headerTs = headerObj?.optLong("timestamp", 0L) ?: 0L
        if (headerTs > 0L && headerTs == lastVehiclePositionsEmissionTimestamp) {
            Log.d("GtfsCacheManager", "Vehicle positions header timestamp ($headerTs) unchanged. Skipping body parse.")
            return false
        }
        val entities = root.optJSONArray("entity") ?: root.optJSONArray("entities") ?: return false
        if (headerTs > 0L) {
            lastVehiclePositionsEmissionTimestamp = headerTs
        }

        for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            val vehEntity = entity.optJSONObject("vehicle") ?: continue
            val tripObj = vehEntity.optJSONObject("trip")

            val tripId = tripObj?.optString("trip_id", "")?.ifBlank { tripObj.optString("tripId", "") } ?: ""
            val routeId = tripObj?.optString("route_id", "")?.ifBlank { tripObj.optString("routeId", "") } ?: ""
            if (routeId.isNotBlank() && routeId.first().isDigit() && !routeId.startsWith("40")) {
                continue
            }

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
                val resolvedRouteId = if (existing != null && existing.routeId.isNotBlank() && existing.routeId != "1" && existing.routeId != "0" && existing.routeId != "C1") {
                    existing.routeId
                } else if (routeId.isNotBlank() && routeId != "1" && routeId != "0" && routeId != "C1") {
                    routeId
                } else {
                    existing?.routeId ?: routeId
                }
                val info = LiveVehicleInfo(
                    tripId = tripId.ifBlank { existing?.tripId ?: "" },
                    routeId = resolvedRouteId,
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

                val keys = (
                    com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(tripId) +
                    com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(vehicleId) +
                    com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(vehicleLabel)
                ).distinct().filter { it.isNotBlank() }

                for (k in keys) {
                    outMap[k] = info
                }
            }
        }
        return true
    }
}
