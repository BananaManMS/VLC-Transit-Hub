package com.example.data.repository.renfe

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationDao
import com.example.data.database.CercaniasStationEntity
import com.example.ui.cercanias.CercaniasDeparture
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import com.example.data.mapper.CercaniasDepartureMapper
import java.util.Calendar
import java.util.TimeZone

class RenfeRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val stationDao: CercaniasStationDao = database.cercaniasStationDao()
    val syncManager: RenfeScheduleSyncManager = RenfeScheduleSyncManager(context, database)
    val gtfsCacheManager: GtfsCacheManager = GtfsCacheManager.getInstance(context)

    suspend fun syncScheduleFromRemoteIfNeeded() {
        syncManager.syncScheduleFromRemoteIfNeeded()
    }

    suspend fun forceSyncScheduleFromRemote(): Boolean {
        return syncManager.forceSyncScheduleFromRemote()
    }

    suspend fun initDatabaseFromAssetsIfNeeded() {
        syncManager.initDatabaseFromAssetsIfNeeded()
    }

    suspend fun reloadFromAssets(): Any {
        return syncManager.reloadFromAssets()
    }

    suspend fun fetchGtfsRtVehiclePositions(): Map<String, LiveVehicleInfo> {
        return gtfsCacheManager.getLiveVehiclePositions()
    }

    suspend fun getUniqueLiveVehicles(): List<LiveVehicleInfo> {
        return gtfsCacheManager.getUniqueLiveVehicles()
    }

    suspend fun getLiveVehiclesWithFreshness(forceFetch: Boolean = false): Pair<List<LiveVehicleInfo>, Boolean> {
        return gtfsCacheManager.getLiveVehiclesWithFreshness(forceFetch)
    }

    suspend fun fetchGtfsRtTripUpdates(): Map<String, GtfsRtTripUpdate> {
        return gtfsCacheManager.getLiveTripUpdates()
    }

    suspend fun getDeparturesForStation(
        stopId: String,
        gtfsRtUpdates: Map<String, GtfsRtTripUpdate>? = null,
        gtfsVehiclePositions: Map<String, LiveVehicleInfo>? = null
    ): List<CercaniasDeparture> = withContext(Dispatchers.IO) {
        syncManager.ensureScheduleLoadedInMemory()
        val timeoutMs = try {
            com.example.util.getTransitFetchTimeoutMs(context.applicationContext)
        } catch (_: Exception) { 5000L }

        val (updates, vehicles) = if (gtfsRtUpdates == null && gtfsVehiclePositions == null) {
            try {
                withTimeoutOrNull(timeoutMs) { gtfsCacheManager.getDeparturesFeedData() }
                    ?: Pair(emptyMap(), emptyMap())
            } catch (_: Exception) {
                Pair(emptyMap(), emptyMap())
            }
        } else {
            val u = gtfsRtUpdates ?: try {
                withTimeoutOrNull(timeoutMs) { fetchGtfsRtTripUpdates() } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            val v = gtfsVehiclePositions ?: try {
                withTimeoutOrNull(timeoutMs) { fetchGtfsRtVehiclePositions() } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            Pair(u, v)
        }

        val horarios = syncManager.getHorariosForStation(stopId)
        if (horarios.isEmpty()) return@withContext emptyList()

        val stationNameMap = syncManager.getStationNameMap()
        val currentStationName = stationNameMap[stopId] ?: ""
        val preNormalizedCurrent = CercaniasDepartureMapper.normalizeStationName(currentStationName)

        val madridTz = TimeZone.getTimeZone("Europe/Madrid")
        val madridCal = Calendar.getInstance(madridTz)
        val nowSecondsOfDay = madridCal.get(Calendar.HOUR_OF_DAY) * 3600 +
                              madridCal.get(Calendar.MINUTE) * 60 +
                              madridCal.get(Calendar.SECOND)
        val nowEpochSec = System.currentTimeMillis() / 1000L

        val tomorrowCal = Calendar.getInstance(madridTz).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowDateStr = String.format(
            java.util.Locale.ROOT, "%04d%02d%02d",
            tomorrowCal.get(Calendar.YEAR),
            tomorrowCal.get(Calendar.MONTH) + 1,
            tomorrowCal.get(Calendar.DAY_OF_MONTH)
        )

        // Build compact secondary lookup indexes only when caller supplied an un-indexed raw map
        val updatesIndex = HashMap<String, GtfsRtTripUpdate>(updates.size * 3 + 16)
        for ((k, v) in updates) {
            updatesIndex[k] = v
            val base = com.example.ui.cercanias.CercaniasRouteUtils.getBaseTripKey(k)
            if (base.isNotBlank() && !updatesIndex.containsKey(base)) {
                updatesIndex[base] = v
            }
            val vLineCode = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineCode(v.routeId, v.tripId)
            val vTrain = com.example.ui.cercanias.CercaniasRouteUtils.getEffectiveTrainNumber(v.vehicleId, v.tripId)
            if (vLineCode.isNotBlank() && vTrain.isNotBlank()) {
                val scopedKey = "${vLineCode}_$vTrain"
                if (!updatesIndex.containsKey(scopedKey)) {
                    updatesIndex[scopedKey] = v
                }
            }
        }

        val vehiclesIndex = HashMap<String, LiveVehicleInfo>(vehicles.size * 3 + 16)
        for ((k, v) in vehicles) {
            vehiclesIndex[k] = v
            val base = com.example.ui.cercanias.CercaniasRouteUtils.getBaseTripKey(k)
            if (base.isNotBlank() && !vehiclesIndex.containsKey(base)) {
                vehiclesIndex[base] = v
            }
            val vLineCode = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineCode(v.routeId, v.tripId)
            val vTrain = com.example.ui.cercanias.CercaniasRouteUtils.getEffectiveTrainNumber(v.trainNum, v.tripId)
            if (vLineCode.isNotBlank() && vTrain.isNotBlank()) {
                val scopedKey = "${vLineCode}_$vTrain"
                if (!vehiclesIndex.containsKey(scopedKey)) {
                    vehiclesIndex[scopedKey] = v
                }
            }
        }

        val platRegex = Regex("""PLATF\.\((\d+)\)|V[IÍ]A\s*(\d+)""", RegexOption.IGNORE_CASE)
        val list = mutableListOf<CercaniasDeparture>()

        for (h in horarios) {
            val depSeconds = parseTimeToSeconds(h.llegada)
            if (depSeconds < 0) continue

            val line = h.linea
            val allTripIds = if (h.trip_ids.isNotEmpty()) h.trip_ids else h.tripIds
            val tripId = allTripIds.firstOrNull() ?: ""
            val formattedDest = syncManager.formatDestinationName(h.destino)
            val dest = if (formattedDest.isNotBlank() && !formattedDest.equals("Civis", ignoreCase = true)) {
                formattedDest
            } else {
                syncManager.getTripDestination(tripId, line, currentStationName)
            }

            // Exclude terminal arrivals: trains terminating at this station cannot be boarded for departure
            if (CercaniasDepartureMapper.isTerminalArrival(currentStationName, dest, preNormalizedCurrent)) {
                continue
            }

            val depLineNum = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(line, tripId)
            val depLineCode = if (depLineNum.isNotBlank()) "C$depLineNum" else ""

            // Handle post-midnight continuous service wrap cleanly
            val scheduledDiffToday = when {
                nowSecondsOfDay >= 22 * 3600 && depSeconds < 4 * 3600 -> (depSeconds + 86400) - nowSecondsOfDay
                nowSecondsOfDay < 4 * 3600 && depSeconds >= 22 * 3600 -> (depSeconds - 86400) - nowSecondsOfDay
                else -> depSeconds - nowSecondsOfDay
            }

            // Strict +2h live matching window: departures beyond +2h stay O(1) pure scheduled
            val isInLiveMatchingWindow = scheduledDiffToday in -3600..7200

            var liveUpdate: GtfsRtTripUpdate? = null
            var vehicleForTrip: LiveVehicleInfo? = null

            if (isInLiveMatchingWindow && (updatesIndex.isNotEmpty() || vehiclesIndex.isNotEmpty())) {
                // Priority 1: Exact / base tripId / day-letter variants (zero cross-line collision)
                for (id in allTripIds) {
                    val exactKeys = com.example.ui.cercanias.CercaniasRouteUtils.getExactAndBaseTripKeys(id)
                    for (ek in exactKeys) {
                        if (liveUpdate == null) liveUpdate = updatesIndex[ek]
                        if (vehicleForTrip == null) vehicleForTrip = vehiclesIndex[ek]
                        if (liveUpdate != null && vehicleForTrip != null) break
                    }
                    if (liveUpdate != null && vehicleForTrip != null) break
                }

                // Priority 2: Line-scoped train number (e.g. "C1_24007" vs "C2_24007")
                if ((liveUpdate == null || vehicleForTrip == null) && depLineCode.isNotBlank()) {
                    for (id in allTripIds) {
                        val tNum = com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(id)
                        if (!tNum.isNullOrBlank()) {
                            val scoped = "${depLineCode}_$tNum"
                            if (liveUpdate == null) liveUpdate = updatesIndex[scoped]
                            if (vehicleForTrip == null) vehicleForTrip = vehiclesIndex[scoped]
                            if (liveUpdate != null && vehicleForTrip != null) break
                        }
                    }
                }

                // Priority 3: Bare train number ONLY if the candidate shares the same canonical line
                if (liveUpdate == null || vehicleForTrip == null) {
                    for (id in allTripIds) {
                        val tNum = com.example.ui.cercanias.CercaniasRouteUtils.extractTrainNumber(id) ?: continue
                        if (liveUpdate == null) {
                            val cand = updatesIndex[tNum]
                            if (cand != null) {
                                val candLine = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(cand.routeId, cand.tripId)
                                if (candLine.isBlank() || depLineNum.isBlank() || candLine == depLineNum) {
                                    liveUpdate = cand
                                }
                            }
                        }
                        if (vehicleForTrip == null) {
                            val candV = vehiclesIndex[tNum]
                            if (candV != null) {
                                val candLine = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(candV.routeId, candV.tripId)
                                if (candLine.isBlank() || depLineNum.isBlank() || candLine == depLineNum) {
                                    vehicleForTrip = candV
                                }
                            }
                        }
                        if (liveUpdate != null && vehicleForTrip != null) break
                    }
                }
            }

            val isLive = liveUpdate != null || vehicleForTrip != null
            val isCanceled = liveUpdate?.isCanceled ?: false

            // Delay resolution prioritizing per-stop GTFS-RT > trip delay > vehicle delay
            val stopDelaySec = liveUpdate?.stopDelays?.get(stopId)
            val delaySeconds = when {
                stopDelaySec != null -> stopDelaySec.toInt()
                liveUpdate != null && (liveUpdate.delaySeconds != 0L || liveUpdate.hasExplicitDelay) -> liveUpdate.delaySeconds.toInt()
                vehicleForTrip != null -> vehicleForTrip.delayMinutes * 60
                else -> 0
            }
            val delayMinutes = delaySeconds / 60

            // Strict station-matched platform resolution: never display a platform from another station
            val platform = when {
                !liveUpdate?.stopPlatforms?.get(stopId).isNullOrBlank() -> liveUpdate!!.stopPlatforms[stopId]!!.trim()
                vehicleForTrip != null && vehicleForTrip.currentStopId == stopId && vehicleForTrip.platform.isNotBlank() ->
                    vehicleForTrip.platform.trim()
                vehicleForTrip != null && vehicleForTrip.nextStopId == stopId && vehicleForTrip.nextPlatform.isNotBlank() ->
                    vehicleForTrip.nextPlatform.trim()
                liveUpdate != null && !liveUpdate.vehicleLabel.isBlank() &&
                    (vehicleForTrip?.currentStopId == stopId || liveUpdate.stopTimes.firstOrNull()?.stopId == stopId) -> {
                    val m = platRegex.find(liveUpdate.vehicleLabel)
                    m?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }?.trim() ?: ""
                }
                else -> ""
            }

            val locationText = if (isLive) "En tiempo real" else ""

            val stopEstEpoch = liveUpdate?.stopEstimatedTimes?.get(stopId)
            val useStopEpoch = stopEstEpoch != null && stopEstEpoch > 0L && kotlin.math.abs(stopEstEpoch - nowEpochSec) < 12 * 3600

            val effectiveDiffToday = if (useStopEpoch) {
                (stopEstEpoch!! - nowEpochSec).toInt()
            } else {
                scheduledDiffToday + delaySeconds
            }

            val timeHHmm = formatTimeHHmm(h.llegada)
            val estimatedTimeStr = if (isLive && !isCanceled) {
                if (useStopEpoch) {
                    formatEpochSecondsToHHmm(stopEstEpoch!!, madridTz)
                } else {
                    formatSecondsToHHmm(depSeconds + delaySeconds)
                }
            } else {
                ""
            }

            val vehStatus = vehicleForTrip?.status ?: ""
            val isStoppedAt = vehStatus == "STOPPED_AT" && vehicleForTrip?.currentStopId == stopId
            val isIncomingAt = vehStatus == "INCOMING_AT" && (vehicleForTrip?.currentStopId == stopId || vehicleForTrip?.nextStopId == stopId)
            val isIndeterminateDelay = !isCanceled && (
                liveUpdate?.isIndeterminateDeparture == true ||
                (liveUpdate?.stopTimes?.any { it.stopId == stopId && it.isIndeterminate } == true)
            )

            val cleanDest = dest
                .replace("dirección", "", ignoreCase = true)
                .replace("direccion", "", ignoreCase = true)
                .trim()
            val formattedDestName = CercaniasDepartureMapper.formatStationDisplayName(cleanDest)
            val normRouteStr = line.uppercase().replace("-", "").trim()

            // 1. Departures for TODAY (from now until end of service today)
            val isUpcomingToday = effectiveDiffToday >= -30 || (isLive && effectiveDiffToday >= -180)
            if (isUpcomingToday) {
                val minutesRemaining = if (effectiveDiffToday < 0 && isLive) {
                    0
                } else {
                    Math.round(effectiveDiffToday / 60.0f).coerceAtLeast(0)
                }

                list.add(
                    CercaniasDeparture(
                        routeId = line,
                        destination = dest,
                        minutesRemaining = minutesRemaining,
                        delayMinutes = if (isLive) delayMinutes else 0,
                        tripId = tripId,
                        departureTime = timeHHmm,
                        estimatedTime = estimatedTimeStr,
                        isLive = isLive,
                        platform = platform,
                        latitude = vehicleForTrip?.latitude,
                        longitude = vehicleForTrip?.longitude,
                        status = vehStatus,
                        locationText = locationText,
                        isCanceled = isCanceled,
                        isStoppedAt = isStoppedAt,
                        isIncomingAt = isIncomingAt,
                        isTomorrow = false,
                        isIndeterminateDelay = isIndeterminateDelay,
                        allTripIds = allTripIds,
                        normalizedRoute = normRouteStr,
                        formattedDestination = formattedDestName
                    )
                )
            }

            // 2. Departures for TOMORROW (within 24 hours from current time - scheduled only, except advance cancellations)
            val diffSecondsTomorrow = (depSeconds + 86400) - nowSecondsOfDay
            if (diffSecondsTomorrow in 0..86400) {
                val minutesRemainingTomorrow = Math.round(diffSecondsTomorrow / 60.0f)

                var isCanceledTomorrow = false
                if (updatesIndex.isNotEmpty()) {
                    for (k in allTripIds) {
                        val keyWithDate = "${tomorrowDateStr}_$k"
                        val keyWithDateDash = "$tomorrowDateStr-$k"
                        val tomorrowUpdate = updatesIndex[keyWithDate] ?: updatesIndex[keyWithDateDash]
                        if (tomorrowUpdate?.isCanceled == true) {
                            isCanceledTomorrow = true
                            break
                        }
                    }
                }

                list.add(
                    CercaniasDeparture(
                        routeId = line,
                        destination = dest,
                        minutesRemaining = minutesRemainingTomorrow,
                        delayMinutes = 0,
                        tripId = tripId,
                        departureTime = timeHHmm,
                        estimatedTime = "",
                        isLive = false,
                        platform = "",
                        status = "",
                        locationText = "",
                        isCanceled = isCanceledTomorrow,
                        isStoppedAt = false,
                        isIncomingAt = false,
                        isTomorrow = true,
                        allTripIds = allTripIds,
                        normalizedRoute = normRouteStr,
                        formattedDestination = formattedDestName
                    )
                )
            }
        }

        CercaniasDepartureMapper.sortDeparturesChronologically(list)
    }

    private fun parseTimeToSeconds(timeStr: String): Int {
        if (timeStr.isBlank()) return -1
        val parts = timeStr.trim().split(":")
        if (parts.size < 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        val s = if (parts.size >= 3) parts[2].toIntOrNull() ?: 0 else 0
        return h * 3600 + m * 60 + s
    }

    private fun formatTimeHHmm(timeStr: String): String {
        if (timeStr.isBlank()) return "--:--"
        val parts = timeStr.trim().split(":")
        if (parts.size >= 2) {
            val h = parts[0].padStart(2, '0')
            val m = parts[1].padStart(2, '0')
            return "$h:$m"
        }
        return timeStr
    }

    private fun formatSecondsToHHmm(totalSeconds: Int): String {
        val normalized = (totalSeconds % 86400 + 86400) % 86400
        val h = (normalized / 3600).toString().padStart(2, '0')
        val m = ((normalized % 3600) / 60).toString().padStart(2, '0')
        return "$h:$m"
    }

    private fun formatEpochSecondsToHHmm(epochSec: Long, tz: TimeZone): String {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = epochSec * 1000L }
        val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$h:$m"
    }

    suspend fun getAllStations(): List<CercaniasStationEntity> = withContext(Dispatchers.IO) {
        stationDao.getAllStations()
    }

    fun getAllStationsFlow(): Flow<List<CercaniasStationEntity>> {
        return stationDao.getAllStationsFlow().map { list ->
            list.filter { !it.nombre.contains("Torreblanca del Sol", ignoreCase = true) }
        }
    }

    fun getFavoriteStationsFlow(): Flow<List<CercaniasStationEntity>> {
        return stationDao.getFavoriteStationsFlow().map { list ->
            list.filter { !it.nombre.contains("Torreblanca del Sol", ignoreCase = true) }
        }
    }

    suspend fun getStationById(stopId: String): CercaniasStationEntity? = withContext(Dispatchers.IO) {
        stationDao.getStationById(stopId)
    }

    suspend fun updateStation(station: CercaniasStationEntity) = withContext(Dispatchers.IO) {
        stationDao.updateStation(station)
    }

    suspend fun updateAllStations(stations: List<CercaniasStationEntity>) = withContext(Dispatchers.IO) {
        stationDao.updateAll(stations)
    }
}
