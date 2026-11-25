package com.example.data.repository.renfe

import android.content.Context
import android.util.Log
import com.example.data.network.NetworkModule
import com.example.ui.cercanias.CercaniasRouteUtils
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GtfsCacheManager(private val context: Context) {

    companion object {
        @Volatile
        private var instance: GtfsCacheManager? = null

        fun getInstance(context: Context): GtfsCacheManager {
            return instance ?: synchronized(this) {
                instance ?: GtfsCacheManager(context.applicationContext).also { instance = it }
            }
        }

        private val mutex = Mutex()

        @Volatile
        private var cachedTripUpdates: Map<String, GtfsRtTripUpdate> = emptyMap()

        @Volatile
        private var cachedVehiclePositions: Map<String, LiveVehicleInfo> = emptyMap()

        @Volatile
        private var lastFetchTimeMs: Long = 0L

        @Volatile
        private var lastDeparturesFetchTimeMs: Long = 0L

        @Volatile
        private var lastTripUpdatesFetchTimeMs: Long = 0L

        @Volatile
        private var lastFlotaEmissionTimestamp: String = ""

        @Volatile
        private var lastTripUpdatesEmissionTimestamp: Long = 0L

        @Volatile
        private var lastVehiclePositionsEmissionTimestamp: Long = 0L

        @Volatile
        private var cachedFlotaCoveredKeys: Set<String> = emptySet()

        @Volatile
        private var lastFlotaSuccessTimeMs: Long = 0L

        @Volatile
        private var lastFlotaEtag: String = ""

        @Volatile
        private var lastTripUpdatesEtag: String = ""

        @Volatile
        private var lastVehiclePositionsEtag: String = ""

        internal const val CACHE_TTL_MS = 30_000L // 30s cooldown matching unified real-time cadence
        private val CANONICAL_LINE_REGEX = Regex("""^C[1-6]$""")
        private val OTHER_NUCLEUS_PREFIXES = listOf("10", "20", "30", "31", "32", "41", "50", "51", "60", "61", "62", "70", "80", "90")
        private val PLATF_REGEX = Regex("""PLATF\.\((\d+)\)|V[IÍ]A\s*(\d+)""", RegexOption.IGNORE_CASE)

        private val fastClient by lazy {
            NetworkModule.okHttpClient.newBuilder()
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .callTimeout(3000, TimeUnit.MILLISECONDS)
                .build()
        }

        fun getDeparturesCacheRemainingMs(): Long {
            if (cachedTripUpdates.isEmpty() && cachedVehiclePositions.isEmpty()) return 0L
            val latest = maxOf(lastDeparturesFetchTimeMs, lastFetchTimeMs)
            if (latest <= 0L) return 0L
            val remaining = CACHE_TTL_MS - (System.currentTimeMillis() - latest)
            return if (remaining > 0L) remaining else 0L
        }
    }

    /**
     * Mode 1 (Routing / ETA estimation): fetches only trip_updates.json.
     */
    suspend fun getLiveTripUpdates(): Map<String, GtfsRtTripUpdate> = withContext(Dispatchers.IO) {
        ensureTripUpdatesOnlyUpdated()
        cachedTripUpdates
    }

    /**
     * Mode 2 (Station Departures Board): fetches ONLY trip_updates.json + flota.json in parallel
     * (skips vehicle_positions.json because flota already carries exact via/nextVia + GPS + status
     * for running trains and trip_updates already covers 100% of N40 trains including headers).
     */
    suspend fun getDeparturesFeedData(force: Boolean = false): Pair<Map<String, GtfsRtTripUpdate>, Map<String, LiveVehicleInfo>> =
        withContext(Dispatchers.IO) {
            ensureDeparturesFeedsUpdated(force = force)
            Pair(cachedTripUpdates, cachedVehiclePositions)
        }

    /**
     * Mode 3 (Live Map): fetches all 3 feeds (flota.json + trip_updates.json + vehicle_positions.json)
     * in parallel, merging field-by-field without duplicating trains already parsed from flota.json.
     */
    suspend fun getLiveVehiclePositions(): Map<String, LiveVehicleInfo> = withContext(Dispatchers.IO) {
        ensureCacheUpdated()
        cachedVehiclePositions
    }

    /**
     * Strictly verifies that a live vehicle belongs to Núcleo 40 (València).
     * Discards trains from Madrid, Barcelona, Sevilla, etc. from nationwide GTFS-RT feeds.
     */
    fun isValenciaNucleus40(vehicle: LiveVehicleInfo): Boolean {
        val lat = vehicle.latitude
        val lon = vehicle.longitude
        val hasValidCoords = lat != null && lon != null && !lat.isNaN() && !lon.isNaN() && lat != 0.0 && lon != 0.0
        if (hasValidCoords && (lat!! !in 38.65..40.65 || lon!! !in -1.50..0.65)) {
            return false
        }

        val r = vehicle.routeId.trim()
        if (r.isNotBlank()) {
            if (r.startsWith("40")) return true
            if (OTHER_NUCLEUS_PREFIXES.any { r.startsWith(it) }) {
                return false
            }
        }
        val trip = vehicle.tripId.trim()
        if (trip.isNotBlank()) {
            if (trip.startsWith("40") && (trip.length == 2 || !trip[2].isDigit() || trip.startsWith("40_") || trip.startsWith("40-"))) return true
            if (OTHER_NUCLEUS_PREFIXES.any { p -> trip.startsWith("${p}_") || trip.startsWith("${p}-") }) {
                return false
            }
        }
        if (r.isNotBlank() && CANONICAL_LINE_REGEX.matches(r.uppercase(Locale.ROOT))) {
            return true
        }

        // Check València station ID if known
        val stopIds = CercaniasRouteUtils.getValenciaStopIds()
        if (stopIds.isNotEmpty() &&
            ((vehicle.currentStopId.isNotBlank() && vehicle.currentStopId in stopIds) ||
             (vehicle.nextStopId.isNotBlank() && vehicle.nextStopId in stopIds))
        ) {
            return true
        }

        return hasValidCoords
    }

    suspend fun getUniqueLiveVehicles(): List<LiveVehicleInfo> = withContext(Dispatchers.IO) {
        ensureCacheUpdated()
        cachedVehiclePositions.values
            .filter { isValenciaNucleus40(it) }
            .distinctBy { it.tripId.ifBlank { "${it.routeId}_${it.trainNum}" } }
    }

    suspend fun getLiveVehiclesWithFreshness(forceFetch: Boolean = false): Pair<List<LiveVehicleInfo>, Boolean> =
        withContext(Dispatchers.IO) {
            val hasNewData = ensureCacheUpdated(force = forceFetch)
            val vehicles = cachedVehiclePositions.values
                .filter { isValenciaNucleus40(it) }
                .distinctBy { it.tripId.ifBlank { "${it.routeId}_${it.trainNum}" } }
            Pair(vehicles, hasNewData)
        }

    private suspend fun ensureTripUpdatesOnlyUpdated(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val latestTuFetch = maxOf(lastTripUpdatesFetchTimeMs, maxOf(lastDeparturesFetchTimeMs, lastFetchTimeMs))
        if (!force && now - latestTuFetch < CACHE_TTL_MS && cachedTripUpdates.isNotEmpty()) {
            return false
        }
        return mutex.withLock {
            val lockNow = System.currentTimeMillis()
            val lockedLatest = maxOf(lastTripUpdatesFetchTimeMs, maxOf(lastDeparturesFetchTimeMs, lastFetchTimeMs))
            if (!force && lockNow - lockedLatest < CACHE_TTL_MS && cachedTripUpdates.isNotEmpty()) {
                return@withLock false
            }
            try {
                val tripUpdatesMap = mutableMapOf<String, GtfsRtTripUpdate>()
                val serverResponded = AtomicBoolean(false)
                val isFresh = fetchTripUpdatesFeed(tripUpdatesMap, serverResponded)
                if (isFresh && tripUpdatesMap.isNotEmpty()) {
                    val pruned = pruneOldTripUpdates(cachedTripUpdates).toMutableMap()
                    mergeTripUpdates(pruned, tripUpdatesMap, overwriteWithFlota = false)
                    cachedTripUpdates = pruned
                }
                if (serverResponded.get()) {
                    lastTripUpdatesFetchTimeMs = System.currentTimeMillis()
                }
                isFresh
            } catch (e: Exception) {
                Log.w("GtfsCacheManager", "Error fetching trip_updates.json: ${e.message}")
                false
            }
        }
    }

    private suspend fun ensureDeparturesFeedsUpdated(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val latestDepFetch = maxOf(lastDeparturesFetchTimeMs, lastFetchTimeMs)
        if (!force && now - latestDepFetch < CACHE_TTL_MS && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
            return false
        }
        return mutex.withLock {
            val lockNow = System.currentTimeMillis()
            val lockedLatest = maxOf(lastDeparturesFetchTimeMs, lastFetchTimeMs)
            if (!force && lockNow - lockedLatest < CACHE_TTL_MS && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
                return@withLock false
            }
            try {
                val serverResponded = AtomicBoolean(false)
                val hasNewData = fetchRealTimeFeeds(includeVehiclePositions = false, anyServerContact = serverResponded)
                if (serverResponded.get()) {
                    val finishNow = System.currentTimeMillis()
                    lastDeparturesFetchTimeMs = finishNow
                    lastTripUpdatesFetchTimeMs = finishNow
                }
                hasNewData
            } catch (e: Exception) {
                Log.w("GtfsCacheManager", "Error fetching departures feeds: ${e.message}")
                false
            }
        }
    }

    private suspend fun ensureCacheUpdated(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchTimeMs < CACHE_TTL_MS && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
            return false
        }

        return mutex.withLock {
            val lockNow = System.currentTimeMillis()
            if (!force && lockNow - lastFetchTimeMs < CACHE_TTL_MS && (cachedTripUpdates.isNotEmpty() || cachedVehiclePositions.isNotEmpty())) {
                return@withLock false
            }

            try {
                val serverResponded = AtomicBoolean(false)
                val hasNewData = fetchRealTimeFeeds(includeVehiclePositions = true, anyServerContact = serverResponded)
                if (serverResponded.get()) {
                    val finishNow = System.currentTimeMillis()
                    lastFetchTimeMs = finishNow
                    lastDeparturesFetchTimeMs = finishNow
                    lastTripUpdatesFetchTimeMs = finishNow
                }
                hasNewData
            } catch (e: Exception) {
                Log.w("GtfsCacheManager", "Error fetching Renfe GTFS-RT: ${e.message}")
                false
            }
        }
    }

    private fun fetchTripUpdatesFeed(
        outMap: MutableMap<String, GtfsRtTripUpdate>,
        serverContact: AtomicBoolean? = null
    ): Boolean {
        val reqBuilder = Request.Builder()
            .url("https://gtfsrt.renfe.com/trip_updates.json")
            .header("User-Agent", NetworkModule.USER_AGENT)
        if (lastTripUpdatesEtag.isNotBlank()) {
            reqBuilder.header("If-None-Match", lastTripUpdatesEtag)
        }
        fastClient.newCall(reqBuilder.build()).execute().use { response ->
            if (response.code == 304) {
                serverContact?.set(true)
                return false
            }
            if (response.isSuccessful) {
                serverContact?.set(true)
                response.header("ETag")?.takeIf { it.isNotBlank() }?.let { lastTripUpdatesEtag = it }
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    return parseTripUpdatesJson(body, outMap)
                }
            }
        }
        return false
    }

    private suspend fun fetchRealTimeFeeds(
        includeVehiclePositions: Boolean,
        anyServerContact: AtomicBoolean? = null
    ): Boolean = coroutineScope {
        val flotaTuMap = ConcurrentHashMap<String, GtfsRtTripUpdate>()
        val flotaVpMap = ConcurrentHashMap<String, LiveVehicleInfo>()
        val flotaCoveredKeys = ConcurrentHashMap.newKeySet<String>()
        val tuMap = ConcurrentHashMap<String, GtfsRtTripUpdate>()
        var vpBody: String? = null
        val anyFeedUpdated = AtomicBoolean(false)

        val jobFlota = launch(Dispatchers.IO) {
            try {
                val unixTime = System.currentTimeMillis()
                val reqBuilder = Request.Builder()
                    .url("https://tiempo-real.renfe.com/renfe-visor/flota.json?v=$unixTime")
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .header("Referer", "https://tiempo-real.renfe.com/")
                if (lastFlotaEtag.isNotBlank()) {
                    reqBuilder.header("If-None-Match", lastFlotaEtag)
                }

                fastClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 304) {
                        anyServerContact?.set(true)
                        return@launch
                    }
                    if (response.isSuccessful) {
                        anyServerContact?.set(true)
                        response.header("ETag")?.takeIf { it.isNotBlank() }?.let { lastFlotaEtag = it }
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val tempTu = mutableMapOf<String, GtfsRtTripUpdate>()
                            val tempVp = mutableMapOf<String, LiveVehicleInfo>()
                            val isFresh = parseFlotaJson(body, tempTu, tempVp, flotaCoveredKeys)
                            if (isFresh) {
                                anyFeedUpdated.set(true)
                                flotaTuMap.putAll(tempTu)
                                flotaVpMap.putAll(tempVp)
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
                val tempTu = mutableMapOf<String, GtfsRtTripUpdate>()
                if (fetchTripUpdatesFeed(tempTu, anyServerContact)) {
                    anyFeedUpdated.set(true)
                    tuMap.putAll(tempTu)
                }
            } catch (e: Exception) {
                Log.d("GtfsCacheManager", "Network call to trip_updates.json failed: ${e.message}")
            }
        }

        val jobVehiclePositions = if (includeVehiclePositions) {
            launch(Dispatchers.IO) {
                try {
                    val reqBuilder = Request.Builder()
                        .url("https://gtfsrt.renfe.com/vehicle_positions.json")
                        .header("User-Agent", NetworkModule.USER_AGENT)
                    if (lastVehiclePositionsEtag.isNotBlank()) {
                        reqBuilder.header("If-None-Match", lastVehiclePositionsEtag)
                    }

                    fastClient.newCall(reqBuilder.build()).execute().use { response ->
                        if (response.code == 304) {
                            anyServerContact?.set(true)
                            return@launch
                        }
                        if (response.isSuccessful) {
                            anyServerContact?.set(true)
                            response.header("ETag")?.takeIf { it.isNotBlank() }?.let { lastVehiclePositionsEtag = it }
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                vpBody = body
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("GtfsCacheManager", "Network call to vehicle_positions.json failed: ${e.message}")
                }
            }
        } else null

        if (jobVehiclePositions != null) {
            joinAll(jobFlota, jobTripUpdates, jobVehiclePositions)
        } else {
            joinAll(jobFlota, jobTripUpdates)
        }

        if (flotaCoveredKeys.isNotEmpty()) {
            cachedFlotaCoveredKeys = flotaCoveredKeys.toSet()
            lastFlotaSuccessTimeMs = System.currentTimeMillis()
        }

        val nowMs = System.currentTimeMillis()
        val effectiveFlotaCoveredKeys: Set<String> = when {
            flotaCoveredKeys.isNotEmpty() -> flotaCoveredKeys
            cachedFlotaCoveredKeys.isNotEmpty() && (nowMs - lastFlotaSuccessTimeMs) < 180_000L -> cachedFlotaCoveredKeys
            else -> emptySet()
        }

        val vpExtraMap = mutableMapOf<String, LiveVehicleInfo>()
        val cachedVpForEnrichment = if (flotaVpMap.isEmpty() && effectiveFlotaCoveredKeys.isNotEmpty()) {
            val cutoff = (nowMs / 1000L) - 180L
            cachedVehiclePositions.filterValues { it.timestamp >= cutoff }.toMutableMap()
        } else null

        if (includeVehiclePositions && !vpBody.isNullOrBlank()) {
            // Merge trip updates so header trains from VP can read delayMinutes/routeId in one pass
            val combinedTuForEnrichment = cachedTripUpdates.toMutableMap().apply { putAll(tuMap) }
            val isVpFresh = parseVehiclePositionsJson(
                jsonStr = vpBody!!,
                outMap = vpExtraMap,
                flotaCoveredKeys = effectiveFlotaCoveredKeys,
                flotaVpMap = cachedVpForEnrichment ?: flotaVpMap,
                tripUpdatesForEnrichment = combinedTuForEnrichment
            )
            if (isVpFresh) {
                anyFeedUpdated.set(true)
                if (cachedVpForEnrichment != null && cachedVpForEnrichment.isNotEmpty()) {
                    flotaVpMap.putAll(cachedVpForEnrichment)
                }
            }
        }

        if (anyFeedUpdated.get()) {
            val nowSec = System.currentTimeMillis() / 1000L

            // Merge TripUpdates: TripUpdates official first (schedule/cancellations/per-stop ETAs),
            // then Flota non-destructively (adds missing via/nextVia platforms or fallback entries).
            val mergedTripUpdates = pruneOldTripUpdates(cachedTripUpdates).toMutableMap()
            if (tuMap.isNotEmpty()) {
                mergeTripUpdates(mergedTripUpdates, tuMap, overwriteWithFlota = false)
            }
            if (flotaTuMap.isNotEmpty()) {
                mergeTripUpdates(mergedTripUpdates, flotaTuMap, overwriteWithFlota = true)
            }

            // Merge VehiclePositions: keep active recent vehicles (180s grace for transient blanks),
            // apply VP extra header trains first, then apply Flota as authoritative for running trains.
            val graceCutoffSec = nowSec - 180L
            val mergedVehiclePositions = cachedVehiclePositions.filterValues { it.timestamp >= graceCutoffSec }.toMutableMap()
            if (vpExtraMap.isNotEmpty()) {
                mergeVehiclePositions(mergedVehiclePositions, vpExtraMap)
            }
            if (flotaVpMap.isNotEmpty()) {
                mergeVehiclePositions(mergedVehiclePositions, flotaVpMap)
            }

            cachedTripUpdates = mergedTripUpdates
            cachedVehiclePositions = mergedVehiclePositions
            true
        } else {
            false
        }
    }

    private fun pruneOldTripUpdates(source: Map<String, GtfsRtTripUpdate>): Map<String, GtfsRtTripUpdate> {
        val nowSec = System.currentTimeMillis() / 1000L
        val madridCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
        val todayDateStr = String.format(
            Locale.ROOT, "%04d%02d%02d",
            madridCal.get(java.util.Calendar.YEAR),
            madridCal.get(java.util.Calendar.MONTH) + 1,
            madridCal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        return source.filterValues { update ->
            when {
                update.startDate.isNotBlank() && update.startDate >= todayDateStr -> true
                update.isCanceled -> update.timestamp >= (nowSec - 86400L)
                update.stopEstimatedTimes.isNotEmpty() -> {
                    val maxEst = update.stopEstimatedTimes.values.maxOrNull() ?: 0L
                    maxEst > (nowSec - 3600L)
                }
                else -> update.timestamp >= (nowSec - 3600L)
            }
        }
    }

    private fun isValidRouteCode(r: String): Boolean {
        return r.isNotBlank() && r != "0" && r != "1"
    }

    private fun resolveBestRouteId(first: String, second: String): String {
        val fUpper = first.trim().uppercase(Locale.ROOT)
        val sUpper = second.trim().uppercase(Locale.ROOT)
        return when {
            CANONICAL_LINE_REGEX.matches(fUpper) -> fUpper
            CANONICAL_LINE_REGEX.matches(sUpper) -> sUpper
            isValidRouteCode(first.trim()) -> first.trim()
            isValidRouteCode(second.trim()) -> second.trim()
            else -> first.ifBlank { second }
        }
    }

    internal fun mergeTripUpdates(
        target: MutableMap<String, GtfsRtTripUpdate>,
        source: Map<String, GtfsRtTripUpdate>,
        overwriteWithFlota: Boolean
    ) {
        for ((key, incoming) in source) {
            val existing = target[key]
            if (existing == null) {
                target[key] = incoming
                continue
            }

            // Safeguard against cross-line collisions on shared bare keys
            val existingLine = CercaniasRouteUtils.getCanonicalLineNumber(existing.routeId, existing.tripId)
            val incomingLine = CercaniasRouteUtils.getCanonicalLineNumber(incoming.routeId, incoming.tripId)
            if (existingLine.isNotBlank() && incomingLine.isNotBlank() && existingLine != incomingLine) {
                continue
            }

            if (overwriteWithFlota) {
                // Flota NEVER overwrites trip_updates cancellations, stopEstimatedTimes, stopTimes, or explicit delays
                val mergedPlatforms = existing.stopPlatforms + incoming.stopPlatforms
                val mergedDelays = if (existing.stopDelays.isNotEmpty()) existing.stopDelays else incoming.stopDelays
                val effectiveDelay = if (existing.hasExplicitDelay || existing.isCanceled) existing.delaySeconds else incoming.delaySeconds
                target[key] = existing.copy(
                    routeId = resolveBestRouteId(incoming.routeId, existing.routeId),
                    stopPlatforms = mergedPlatforms,
                    stopDelays = mergedDelays,
                    delaySeconds = effectiveDelay,
                    hasExplicitDelay = existing.hasExplicitDelay || incoming.hasExplicitDelay,
                    vehicleId = existing.vehicleId.ifBlank { incoming.vehicleId },
                    vehicleLabel = existing.vehicleLabel.ifBlank { incoming.vehicleLabel },
                    timestamp = maxOf(existing.timestamp, incoming.timestamp)
                )
            } else {
                // Official trip_updates replaces live delay/cancel/stopTimes while preserving richer stopPlatforms
                val resolvedDelay = if (incoming.hasExplicitDelay) incoming.delaySeconds else existing.delaySeconds
                target[key] = incoming.copy(
                    routeId = resolveBestRouteId(incoming.routeId, existing.routeId),
                    startDate = incoming.startDate.ifBlank { existing.startDate },
                    startTime = incoming.startTime.ifBlank { existing.startTime },
                    stopDelays = existing.stopDelays + incoming.stopDelays,
                    stopEstimatedTimes = existing.stopEstimatedTimes + incoming.stopEstimatedTimes,
                    stopPlatforms = existing.stopPlatforms + incoming.stopPlatforms,
                    stopTimes = if (incoming.stopTimes.isNotEmpty()) incoming.stopTimes else existing.stopTimes,
                    vehicleId = incoming.vehicleId.ifBlank { existing.vehicleId },
                    vehicleLabel = incoming.vehicleLabel.ifBlank { existing.vehicleLabel },
                    delaySeconds = resolvedDelay,
                    hasExplicitDelay = incoming.hasExplicitDelay || existing.hasExplicitDelay,
                    timestamp = maxOf(existing.timestamp, incoming.timestamp)
                )
            }
        }
    }

    internal fun mergeVehiclePositions(
        target: MutableMap<String, LiveVehicleInfo>,
        source: Map<String, LiveVehicleInfo>
    ) {
        for ((key, newInfo) in source) {
            val existing = target[key]
            if (existing != null) {
                // Do not let a different canonical line overwrite a collision-prone bare train number
                val existingLine = CercaniasRouteUtils.getCanonicalLineNumber(existing.routeId, existing.tripId)
                val newLine = CercaniasRouteUtils.getCanonicalLineNumber(newInfo.routeId, newInfo.tripId)
                if (existingLine.isNotBlank() && newLine.isNotBlank() && existingLine != newLine) {
                    continue
                }

                val resolvedRouteId = resolveBestRouteId(existing.routeId, newInfo.routeId)
                val resolvedDelay = if (newInfo.hasExplicitDelay) {
                    newInfo.delayMinutes
                } else {
                    if (newInfo.delayMinutes != 0) newInfo.delayMinutes else existing.delayMinutes
                }

                target[key] = newInfo.copy(
                    tripId = newInfo.tripId.ifBlank { existing.tripId },
                    routeId = resolvedRouteId,
                    latitude = newInfo.latitude ?: existing.latitude,
                    longitude = newInfo.longitude ?: existing.longitude,
                    status = newInfo.status.ifBlank { existing.status },
                    trainNum = newInfo.trainNum.ifBlank { existing.trainNum },
                    originName = newInfo.originName.ifBlank { existing.originName },
                    destinationName = newInfo.destinationName.ifBlank { existing.destinationName },
                    currentStopId = newInfo.currentStopId.ifBlank { existing.currentStopId },
                    nextStopId = newInfo.nextStopId.ifBlank { existing.nextStopId },
                    nextPlatform = newInfo.nextPlatform.ifBlank { existing.nextPlatform },
                    nextArrivalTime = newInfo.nextArrivalTime.ifBlank { existing.nextArrivalTime },
                    platform = newInfo.platform.ifBlank { existing.platform },
                    delayMinutes = resolvedDelay,
                    hasExplicitDelay = newInfo.hasExplicitDelay || existing.hasExplicitDelay,
                    speed = newInfo.speed ?: existing.speed,
                    bearing = newInfo.bearing ?: existing.bearing
                )
            } else {
                target[key] = newInfo
            }
        }
    }

    private fun isLikelyNucleus40Entity(
        tripId: String,
        routeId: String,
        entityId: String,
        firstStopId: String = "",
        valenciaStopIds: Set<String> = emptySet()
    ): Boolean {
        if (tripId.startsWith("40") || routeId.startsWith("40") || entityId.contains("_40")) return true
        if (valenciaStopIds.isNotEmpty() && firstStopId.isNotBlank() && firstStopId in valenciaStopIds) return true
        if (routeId.isNotBlank() && OTHER_NUCLEUS_PREFIXES.any { routeId.startsWith(it) }) return false
        if (tripId.isNotBlank() && OTHER_NUCLEUS_PREFIXES.any { p -> tripId.startsWith(p) && (tripId.length <= 2 || !tripId[2].isDigit()) }) return false
        if (entityId.contains("_")) {
            val nucleusPart = entityId.substringAfter("_").take(2)
            if (nucleusPart.length == 2 && nucleusPart.all { it.isDigit() } && nucleusPart != "40") return false
        }
        return true
    }

    private fun indexSafely(
        outTu: MutableMap<String, GtfsRtTripUpdate>?,
        outVp: MutableMap<String, LiveVehicleInfo>?,
        tu: GtfsRtTripUpdate?,
        vp: LiveVehicleInfo?,
        tripId: String,
        lineCode: String,
        effectiveTrainNum: String,
        extraKeys: List<String> = emptyList()
    ) {
        val exactAndBase = CercaniasRouteUtils.getExactAndBaseTripKeys(tripId).toMutableList()
        if (lineCode.isNotBlank() && effectiveTrainNum.isNotBlank()) {
            exactAndBase.add("${lineCode}_$effectiveTrainNum")
        }
        exactAndBase.addAll(extraKeys)

        for (k in exactAndBase.distinct()) {
            if (k.isBlank()) continue
            if (outTu != null && tu != null) outTu[k] = tu
            if (outVp != null && vp != null) outVp[k] = vp
        }

        // Bare train number ONLY if not yet claimed by another line
        if (effectiveTrainNum.isNotBlank()) {
            if (outTu != null && tu != null) {
                val existing = outTu[effectiveTrainNum]
                if (existing == null || CercaniasRouteUtils.getCanonicalLineNumber(existing.routeId, existing.tripId) == lineCode.removePrefix("C")) {
                    outTu[effectiveTrainNum] = tu
                }
            }
            if (outVp != null && vp != null) {
                val existing = outVp[effectiveTrainNum]
                if (existing == null || CercaniasRouteUtils.getCanonicalLineNumber(existing.routeId, existing.tripId) == lineCode.removePrefix("C")) {
                    outVp[effectiveTrainNum] = vp
                }
            }
        }
    }

    internal fun parseFlotaJson(
        jsonStr: String,
        tripUpdatesMap: MutableMap<String, GtfsRtTripUpdate>,
        vehiclePositionsMap: MutableMap<String, LiveVehicleInfo>,
        flotaCoveredKeys: MutableSet<String> = mutableSetOf()
    ): Boolean {
        val root = JSONObject(jsonStr)
        val fechaActualizacion = root.optString("fechaActualizacion", "").trim()
        val fecha = root.optString("fecha", "").trim()
        val hora = root.optString("hora", "").trim()
        val fechaHora = root.optString("fechaHora", "").trim()
        val timestamp = root.optLong("timestamp", 0L)
        val headerTs = root.optJSONObject("header")?.optLong("timestamp", 0L) ?: 0L
        val emissionKey = when {
            fechaActualizacion.isNotBlank() -> fechaActualizacion
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

        val nowSec = System.currentTimeMillis() / 1000L

        for (i in 0 until trenes.length()) {
            val t = trenes.optJSONObject(i) ?: continue

            val nucleo = t.optString("nucleo", "").trim().ifBlank {
                t.optString("codNucleo", "").trim().ifBlank {
                    t.optInt("nucleo", 0).takeIf { it > 0 }?.toString() ?: ""
                }
            }
            if (nucleo != "40" && nucleo != "040") {
                continue
            }

            val tripId = t.optString("tripId", t.optString("idViaje", t.optString("idTren", ""))).trim()
            val codTren = t.optString("codTren", t.optString("numTren", t.optString("tren", ""))).trim()
            val delayMin = t.optString("retrasoMin", "0").toIntOrNull()
                ?: t.optInt("retrasoMin", 0)
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

            // Blank porAvanc in flota.json indicates stopped at current header station
            val status = when {
                porAvanc == "E" || porAvanc.isBlank() -> "STOPPED_AT"
                porAvanc == "A" -> "INCOMING_AT"
                else -> "IN_TRANSIT_TO"
            }

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
                rawCodLinea.startsWith("C", ignoreCase = true) -> rawCodLinea.uppercase(Locale.ROOT).replace("-", "")
                rawCodLinea.isNotBlank() -> "C${rawCodLinea.replace("-", "")}"
                else -> CercaniasRouteUtils.getCanonicalLineCode("", tripId)
            }
            val effectiveTrainNum = if (codTren.isNotBlank()) codTren else CercaniasRouteUtils.getEffectiveTrainNumber("", tripId)

            val labelPrefix = canonicalRouteId.ifBlank { "CR" }
            val tu = GtfsRtTripUpdate(
                tripId = tripId,
                routeId = canonicalRouteId,
                delaySeconds = delaySeconds,
                stopDelays = stopDelays,
                stopPlatforms = platforms,
                vehicleId = effectiveTrainNum,
                vehicleLabel = if (via.isNotBlank()) "$labelPrefix-$effectiveTrainNum-PLATF.($via)" else "$labelPrefix-$effectiveTrainNum",
                timestamp = nowSec,
                hasExplicitDelay = true
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
            val origen = nomEstOrigen.ifBlank { codEstOrig }
            val destino = nomEstDestino.ifBlank { codEstDest }

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
                timestamp = nowSec,
                nextStopId = codEstSig,
                nextPlatform = nextVia,
                nextArrivalTime = horaLlegadaSigEst,
                delayMinutes = delayMin,
                trainNum = effectiveTrainNum,
                originName = origen,
                destinationName = destino,
                hasExplicitDelay = true
            )

            val exactAndBase = CercaniasRouteUtils.getExactAndBaseTripKeys(tripId)
            flotaCoveredKeys.addAll(exactAndBase)
            if (canonicalRouteId.isNotBlank() && effectiveTrainNum.isNotBlank()) {
                flotaCoveredKeys.add("${canonicalRouteId}_$effectiveTrainNum")
            }

            indexSafely(
                outTu = tripUpdatesMap,
                outVp = vehiclePositionsMap,
                tu = tu,
                vp = vp,
                tripId = tripId,
                lineCode = canonicalRouteId,
                effectiveTrainNum = effectiveTrainNum
            )
        }
        return true
    }

    internal fun parseTripUpdatesJson(jsonStr: String, outMap: MutableMap<String, GtfsRtTripUpdate>): Boolean {
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

        val valenciaStopIds = CercaniasRouteUtils.getValenciaStopIds()
        val nowSec = System.currentTimeMillis() / 1000L

        for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            val entityId = entity.optString("id", "").trim()
            val tripUpdateObj = entity.optJSONObject("trip_update") ?: entity.optJSONObject("tripUpdate") ?: continue
            val tripObj = tripUpdateObj.optJSONObject("trip") ?: continue

            val tripId = tripObj.optString("trip_id", "").ifBlank { tripObj.optString("tripId", "") }.trim()
            if (tripId.isBlank()) continue

            val routeId = tripObj.optString("route_id", "").ifBlank { tripObj.optString("routeId", "") }.trim()
            val stopUpdates = tripUpdateObj.optJSONArray("stop_time_update") ?: tripUpdateObj.optJSONArray("stopTimeUpdate")
            val firstStopId = stopUpdates?.optJSONObject(0)?.let { stu ->
                stu.optString("stop_id", "").ifBlank { stu.optString("stopId", "") }.trim()
            } ?: ""

            if (!isLikelyNucleus40Entity(tripId, routeId, entityId, firstStopId, valenciaStopIds)) {
                continue
            }

            val startDate = tripObj.optString("start_date", tripObj.optString("startDate", "")).trim()
            val startTime = tripObj.optString("start_time", tripObj.optString("startTime", "")).trim()
            val hasTopDelay = tripUpdateObj.has("delay") && !tripUpdateObj.isNull("delay")
            val delaySec = if (hasTopDelay) tripUpdateObj.optLong("delay", 0L) else 0L
            val scheduleRel = tripObj.optString("schedule_relationship", tripObj.optString("scheduleRelationship", "SCHEDULED"))
            val isCanceled = scheduleRel.equals("CANCELED", ignoreCase = true)

            val vehObj = tripUpdateObj.optJSONObject("vehicle")
            val vehicleId = vehObj?.optString("id", "")?.trim() ?: ""
            val vehicleLabel = vehObj?.optString("label", "")?.trim() ?: ""
            val labelPlatform = if (vehicleLabel.isNotBlank()) {
                val m = PLATF_REGEX.find(vehicleLabel)
                if (m != null) m.groupValues[1].ifBlank { m.groupValues[2] } else ""
            } else ""

            val stopDelays = mutableMapOf<String, Long>()
            val stopEstTimes = mutableMapOf<String, Long>()
            val stopPlatforms = mutableMapOf<String, String>()
            val stopTimesList = mutableListOf<GtfsRtStopTime>()
            var isIndeterminate = false
            var hasStopDelay = false

            if (stopUpdates != null) {
                for (j in 0 until stopUpdates.length()) {
                    val stu = stopUpdates.optJSONObject(j) ?: continue
                    val stopId = stu.optString("stop_id", "").ifBlank { stu.optString("stopId", "") }.trim()
                    val stopSeq = stu.optInt("stop_sequence", stu.optInt("stopSequence", j + 1))

                    val depObj = stu.optJSONObject("departure")
                    val arrObj = stu.optJSONObject("arrival")

                    val depDelay = when {
                        depObj != null && depObj.has("delay") && !depObj.isNull("delay") -> depObj.optLong("delay")
                        arrObj != null && arrObj.has("delay") && !arrObj.isNull("delay") -> arrObj.optLong("delay")
                        else -> null
                    }
                    if (depDelay != null) hasStopDelay = true

                    val depTime = depObj?.optLong("time", 0L)?.takeIf { it > 0L }
                        ?: arrObj?.optLong("time", 0L)?.takeIf { it > 0L }
                    val arrDelay = if (arrObj != null && arrObj.has("delay") && !arrObj.isNull("delay")) arrObj.optLong("delay") else null

                    val rawPlatform = stu.optString("platform", depObj?.optString("platform", "") ?: "").trim()
                    // Only apply vehicleLabel platform fallback if explicit per-stop platform is missing on first pending stop
                    val platform = if (rawPlatform.isNotBlank()) rawPlatform else if (j == 0) labelPlatform else ""
                    if (platform.isNotBlank() && stopId.isNotBlank()) {
                        stopPlatforms[stopId] = platform
                    }

                    if (depDelay != null && stopId.isNotBlank()) {
                        stopDelays[stopId] = depDelay
                    }
                    if (depTime != null && depTime > 0L && stopId.isNotBlank()) {
                        stopEstTimes[stopId] = depTime
                    }

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

            val canonicalLine = CercaniasRouteUtils.getCanonicalLineCode(routeId, tripId)
            val resolvedRoute = resolveBestRouteId(canonicalLine, routeId)
            val effectiveTrainNum = CercaniasRouteUtils.getEffectiveTrainNumber(vehicleId, tripId)

            val existingUpdate = outMap[tripId]
            val mergedPlatforms = (existingUpdate?.stopPlatforms ?: emptyMap()) + stopPlatforms
            val mergedDelays = (existingUpdate?.stopDelays ?: emptyMap()) + stopDelays
            val mergedEstimatedTimes = (existingUpdate?.stopEstimatedTimes ?: emptyMap()) + stopEstTimes

            val update = GtfsRtTripUpdate(
                tripId = tripId,
                routeId = resolveBestRouteId(resolvedRoute, existingUpdate?.routeId ?: ""),
                startDate = startDate.ifBlank { existingUpdate?.startDate ?: "" },
                startTime = startTime.ifBlank { existingUpdate?.startTime ?: "" },
                scheduleRelationship = scheduleRel,
                delaySeconds = if (hasTopDelay) delaySec else (existingUpdate?.delaySeconds ?: 0L),
                stopDelays = mergedDelays,
                stopEstimatedTimes = mergedEstimatedTimes,
                stopPlatforms = mergedPlatforms,
                stopTimes = if (stopTimesList.isNotEmpty()) stopTimesList else (existingUpdate?.stopTimes ?: emptyList()),
                vehicleId = vehicleId.ifBlank { existingUpdate?.vehicleId ?: "" },
                vehicleLabel = vehicleLabel.ifBlank { existingUpdate?.vehicleLabel ?: "" },
                isIndeterminateDeparture = isIndeterminate || (existingUpdate?.isIndeterminateDeparture == true),
                isCanceled = isCanceled || (existingUpdate?.isCanceled == true),
                timestamp = tripUpdateObj.optLong("timestamp", nowSec),
                hasExplicitDelay = hasTopDelay || hasStopDelay || (existingUpdate?.hasExplicitDelay == true)
            )

            val dateKeys = if (startDate.isNotBlank()) listOf("${startDate}_$tripId", "$startDate-$tripId") else emptyList()
            indexSafely(
                outTu = outMap,
                outVp = null,
                tu = update,
                vp = null,
                tripId = tripId,
                lineCode = canonicalLine,
                effectiveTrainNum = effectiveTrainNum,
                extraKeys = dateKeys
            )
        }
        return true
    }

    internal fun parseVehiclePositionsJson(
        jsonStr: String,
        outMap: MutableMap<String, LiveVehicleInfo>,
        flotaCoveredKeys: Set<String> = emptySet(),
        flotaVpMap: MutableMap<String, LiveVehicleInfo> = mutableMapOf(),
        tripUpdatesForEnrichment: Map<String, GtfsRtTripUpdate> = emptyMap()
    ): Boolean {
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

        val valenciaStopIds = CercaniasRouteUtils.getValenciaStopIds()
        val nowSec = System.currentTimeMillis() / 1000L

        for (i in 0 until entities.length()) {
            val entity = entities.optJSONObject(i) ?: continue
            val entityId = entity.optString("id", "").trim()
            val vehEntity = entity.optJSONObject("vehicle") ?: continue
            val tripObj = vehEntity.optJSONObject("trip")

            val tripId = tripObj?.optString("trip_id", "")?.ifBlank { tripObj.optString("tripId", "") }?.trim() ?: ""
            val routeId = tripObj?.optString("route_id", "")?.ifBlank { tripObj.optString("routeId", "") }?.trim() ?: ""
            val stopId = vehEntity.optString("stop_id", vehEntity.optString("stopId", "")).trim()

            if (!isLikelyNucleus40Entity(tripId, routeId, entityId, stopId, valenciaStopIds)) {
                continue
            }

            val posObj = vehEntity.optJSONObject("position")
            val lat = posObj?.optDouble("latitude", Double.NaN)?.takeIf { !it.isNaN() }
            val lon = posObj?.optDouble("longitude", Double.NaN)?.takeIf { !it.isNaN() }
            if (lat != null && lon != null && (lat !in 38.65..40.65 || lon !in -1.50..0.65)) {
                continue
            }
            val speed = posObj?.optDouble("speed", Double.NaN)?.takeIf { !it.isNaN() }
            val bearing = posObj?.optDouble("bearing", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()

            val vehicleObj = vehEntity.optJSONObject("vehicle")
            val vehicleId = vehicleObj?.optString("id", "")?.trim() ?: ""
            val vehicleLabel = vehicleObj?.optString("label", "")?.trim() ?: ""

            val canonicalLine = CercaniasRouteUtils.getCanonicalLineCode(routeId, tripId)
            val effectiveTrainNum = CercaniasRouteUtils.getEffectiveTrainNumber(vehicleId, tripId)

            // If flota.json already parsed this active train with complete via/nextVia/origin/dest/delay,
            // skip reconstructing LiveVehicleInfo and only supplement missing speed/bearing if present.
            val candidateKeys = CercaniasRouteUtils.getExactAndBaseTripKeys(tripId).toMutableList()
            if (canonicalLine.isNotBlank() && effectiveTrainNum.isNotBlank()) {
                candidateKeys.add("${canonicalLine}_$effectiveTrainNum")
            }
            val coveredKey = candidateKeys.firstOrNull { it in flotaCoveredKeys }
            if (coveredKey != null) {
                if (speed != null || bearing != null) {
                    val existingFlota = flotaVpMap[coveredKey]
                    if (existingFlota != null) {
                        val updated = existingFlota.copy(
                            speed = existingFlota.speed ?: speed,
                            bearing = existingFlota.bearing ?: bearing
                        )
                        for (k in candidateKeys) {
                            if (flotaVpMap.containsKey(k)) flotaVpMap[k] = updated
                        }
                    }
                }
                continue
            }

            val status = vehEntity.optString("current_status", vehEntity.optString("currentStatus", "IN_TRANSIT_TO")).trim()
            val timestamp = vehEntity.optLong("timestamp", nowSec)

            var platform = vehicleObj?.optString("platform", "")?.trim() ?: ""
            if (platform.isBlank() && vehicleLabel.isNotBlank()) {
                val m = PLATF_REGEX.find(vehicleLabel)
                if (m != null) {
                    platform = m.groupValues[1].ifBlank { m.groupValues[2] }
                }
            }

            if (tripId.isNotBlank() || vehicleId.isNotBlank()) {

                // Enrich header/pre-departure train from trip_updates.json if available
                val tuMatch = candidateKeys.firstNotNullOfOrNull { tripUpdatesForEnrichment[it] }
                    ?: (if (canonicalLine.isNotBlank() && effectiveTrainNum.isNotBlank()) tripUpdatesForEnrichment["${canonicalLine}_$effectiveTrainNum"] else null)

                val delayMinFromTu = if (tuMatch?.hasExplicitDelay == true) (tuMatch.delaySeconds / 60L).toInt() else 0
                val resolvedRoute = resolveBestRouteId(canonicalLine, resolveBestRouteId(tuMatch?.routeId ?: "", routeId))
                val originDestPair = if (tripId.isNotBlank()) {
                    CercaniasRouteUtils.getTripOriginAndDestination(tripId, resolvedRoute, effectiveTrainNum)
                } else null

                val info = LiveVehicleInfo(
                    tripId = tripId,
                    routeId = resolvedRoute,
                    latitude = lat,
                    longitude = lon,
                    status = status.ifBlank { "STOPPED_AT" },
                    platform = platform,
                    speed = speed,
                    bearing = bearing,
                    currentStopId = stopId,
                    timestamp = timestamp,
                    nextStopId = "",
                    nextPlatform = "",
                    nextArrivalTime = "",
                    delayMinutes = delayMinFromTu,
                    trainNum = effectiveTrainNum,
                    originName = originDestPair?.first ?: "",
                    destinationName = originDestPair?.second ?: "",
                    hasExplicitDelay = tuMatch?.hasExplicitDelay == true
                )

                indexSafely(
                    outTu = null,
                    outVp = outMap,
                    tu = null,
                    vp = info,
                    tripId = tripId,
                    lineCode = canonicalLine,
                    effectiveTrainNum = effectiveTrainNum
                )
            }
        }
        return true
    }
}

