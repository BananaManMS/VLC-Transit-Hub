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
    val gtfsCacheManager: GtfsCacheManager = GtfsCacheManager(context)

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

    init {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.delay(4000L)
                syncManager.ensureScheduleLoadedInMemory()
            } catch (_: Exception) {}
        }
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

        val updates = try {
            gtfsRtUpdates ?: withTimeoutOrNull(timeoutMs) { fetchGtfsRtTripUpdates() } ?: emptyMap()
        } catch (e: Exception) { emptyMap() }

        val vehicles = try {
            gtfsVehiclePositions ?: withTimeoutOrNull(timeoutMs) { fetchGtfsRtVehiclePositions() } ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
        
        val horarios = syncManager.getHorariosForStation(stopId)
        if (horarios.isEmpty()) return@withContext emptyList()
        
        val stationNameMap = syncManager.getStationNameMap()
        val currentStationName = stationNameMap[stopId] ?: ""
        val preNormalizedCurrent = com.example.data.mapper.CercaniasDepartureMapper.normalizeStationName(currentStationName)

        val madridCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        val nowSecondsOfDay = madridCal.get(Calendar.HOUR_OF_DAY) * 3600 +
                              madridCal.get(Calendar.MINUTE) * 60 +
                              madridCal.get(Calendar.SECOND)

        val updatesIndex = HashMap<String, GtfsRtTripUpdate>(updates.size * 15)
        for ((k, v) in updates) {
            updatesIndex[k] = v
            val keys = com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(k)
            for (key in keys) {
                updatesIndex[key] = v
            }
        }

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

            // O(1) Instant lookup for GTFS-RT trip update using full key variants
            var liveUpdate: GtfsRtTripUpdate? = null
            for (id in allTripIds) {
                liveUpdate = updatesIndex[id] ?: updatesIndex[id.uppercase(java.util.Locale.ROOT)]
                if (liveUpdate != null) break
            }
            if (liveUpdate == null && tripId.isNotBlank()) {
                val depKeys = com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(tripId)
                for (dk in depKeys) {
                    liveUpdate = updatesIndex[dk]
                    if (liveUpdate != null) break
                }
            }

            // O(1) Look up matching live vehicle position to extract track/platform ("vía")
            var vehicleForTrip: com.example.ui.cercanias.LiveVehicleInfo? = null
            for (id in allTripIds) {
                vehicleForTrip = vehicles[id] ?: vehicles[id.uppercase(java.util.Locale.ROOT)]
                if (vehicleForTrip != null) break
            }
            if (vehicleForTrip == null && tripId.isNotBlank()) {
                val depKeys = com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(tripId)
                for (dk in depKeys) {
                    vehicleForTrip = vehicles[dk] ?: vehicles[dk.uppercase(java.util.Locale.ROOT)]
                    if (vehicleForTrip != null) break
                }
            }
            val vehiclePlatform = if (vehicleForTrip != null && (vehicleForTrip.currentStopId == stopId || vehicleForTrip.currentStopId.isBlank())) {
                vehicleForTrip.platform
            } else ""

            val isLive = liveUpdate != null || vehicleForTrip != null
            val isCanceled = liveUpdate?.isCanceled ?: false
            val delaySeconds = (liveUpdate?.stopDelays?.get(stopId) ?: liveUpdate?.delaySeconds ?: 0L).toInt()
            val delayMinutes = delaySeconds / 60
            val livePlatform = when {
                !liveUpdate?.stopPlatforms?.get(stopId).isNullOrBlank() -> liveUpdate?.stopPlatforms?.get(stopId) ?: ""
                vehiclePlatform.isNotBlank() -> vehiclePlatform
                !liveUpdate?.vehicleLabel.isNullOrBlank() -> {
                    val platRegex = Regex("""PLATF\.\((\d+)\)|V[IÍ]A\s*(\d+)""", RegexOption.IGNORE_CASE)
                    val m = platRegex.find(liveUpdate!!.vehicleLabel)
                    m?.let { it.groupValues[1].ifBlank { it.groupValues[2] } } ?: ""
                }
                else -> ""
            }
            val platform = livePlatform.trim()

            val locationText = if (isLive) "En tiempo real" else ""

            val effectiveDepSeconds = depSeconds + delaySeconds
            val diffSecondsToday = effectiveDepSeconds - nowSecondsOfDay

            val timeHHmm = formatTimeHHmm(h.llegada)
            val estimatedTimeStr = if (isLive && !isCanceled) {
                formatSecondsToHHmm(effectiveDepSeconds)
            } else {
                ""
            }

            val isStoppedAt = false
            val isIncomingAt = false
            val vehStatus = ""

            val cleanDest = dest
                .replace("dirección", "", ignoreCase = true)
                .replace("direccion", "", ignoreCase = true)
                .trim()
            val formattedDestName = com.example.data.mapper.CercaniasDepartureMapper.formatStationDisplayName(cleanDest)
            val normRouteStr = line.uppercase().replace("-", "").trim()

            // 1. Departures for TODAY (from now until end of service today)
            val isUpcomingToday = diffSecondsToday >= -30 || (isLive && diffSecondsToday >= -180)
            if (isUpcomingToday) {
                val minutesRemaining = if (diffSecondsToday < 0 && isLive) {
                    0
                } else {
                    Math.round(diffSecondsToday / 60.0f).coerceAtLeast(0)
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
                        latitude = null,
                        longitude = null,
                        status = vehStatus,
                        locationText = locationText,
                        isCanceled = isCanceled,
                        isStoppedAt = false,
                        isIncomingAt = false,
                        isTomorrow = false,
                        allTripIds = allTripIds,
                        normalizedRoute = normRouteStr,
                        formattedDestination = formattedDestName
                    )
                )
            }

            // 2. Departures for TOMORROW (within 24 hours from current time)
            val diffSecondsTomorrow = (depSeconds + 86400) - nowSecondsOfDay
            if (diffSecondsTomorrow in 0..86400) {
                val minutesRemainingTomorrow = Math.round(diffSecondsTomorrow / 60.0f)
                val tomorrowCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid")).apply { add(Calendar.DAY_OF_YEAR, 1) }
                val tomorrowDateStr = String.format(java.util.Locale.ROOT, "%04d%02d%02d",
                    tomorrowCal.get(Calendar.YEAR),
                    tomorrowCal.get(Calendar.MONTH) + 1,
                    tomorrowCal.get(Calendar.DAY_OF_MONTH)
                )

                var isCanceledTomorrow = false
                for (k in allTripIds) {
                    val keyWithDate = "${tomorrowDateStr}_$k"
                    val keyWithDateDash = "$tomorrowDateStr-$k"
                    val tomorrowUpdate = updatesIndex[keyWithDate] ?: updatesIndex[keyWithDateDash]
                    if (tomorrowUpdate?.isCanceled == true) {
                        isCanceledTomorrow = true
                        break
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
