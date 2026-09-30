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
import kotlinx.coroutines.withContext

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

    suspend fun getDeparturesForStation(
        stopId: String,
        gtfsRtUpdates: Map<String, GtfsRtTripUpdate>? = null,
        gtfsVehiclePositions: Map<String, LiveVehicleInfo>? = null
    ): List<CercaniasDeparture> = withContext(Dispatchers.IO) {
        val updates = try { gtfsRtUpdates ?: fetchGtfsRtTripUpdates() } catch (e: Exception) { emptyMap() }
        val positions = try { gtfsVehiclePositions ?: fetchGtfsRtVehiclePositions() } catch (e: Exception) { emptyMap() }
        
        var horarios = syncManager.getHorariosForStation(stopId)
        if (horarios.isEmpty()) {
            syncManager.initDatabaseFromAssetsIfNeeded()
            horarios = syncManager.getHorariosForStation(stopId)
        }
        if (horarios.isEmpty()) return@withContext emptyList()
        
        val stationNameMap = syncManager.getStationNameMap()
        val currentStationName = stationNameMap[stopId] ?: ""
        val preNormalizedCurrent = com.example.data.mapper.CercaniasDepartureMapper.normalizeStationName(currentStationName)

        val madridCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        val nowSecondsOfDay = madridCal.get(Calendar.HOUR_OF_DAY) * 3600 +
                              madridCal.get(Calendar.MINUTE) * 60 +
                              madridCal.get(Calendar.SECOND)
        val nowHour = madridCal.get(Calendar.HOUR_OF_DAY)

        val list = mutableListOf<CercaniasDeparture>()
        for (h in horarios) {
            val depSeconds = parseTimeToSeconds(h.llegada)
            if (depSeconds < 0) continue

            // 1. Safe filter to window [-1 hour, +4 hours] around current time
            var timeWindowDiff = depSeconds - nowSecondsOfDay
            // Safe midnight wrap-around check
            if (timeWindowDiff < -43200) {
                timeWindowDiff += 86400
            } else if (timeWindowDiff > 43200) {
                timeWindowDiff -= 86400
            }

            if (timeWindowDiff < -3600 || timeWindowDiff > 4 * 3600) {
                continue
            }

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

            // Match against any trip_id key variants across allTripIds
            var liveUpdate: GtfsRtTripUpdate? = null
            var liveVehicle: LiveVehicleInfo? = null

            val searchKeys = allTripIds
                .flatMap { com.example.ui.cercanias.CercaniasRouteUtils.getTripKeys(it) }
                .distinct()

            for (k in searchKeys) {
                if (liveUpdate == null && updates.containsKey(k)) {
                    liveUpdate = updates[k]
                }
                if (liveVehicle == null && positions.containsKey(k)) {
                    liveVehicle = positions[k]
                }
                if (liveUpdate != null && liveVehicle != null) break
            }

            // Case-insensitive or prefix-based fallback only for specific full-length keys (>= 5 chars)
            if (liveUpdate == null || liveVehicle == null) {
                for (k in searchKeys) {
                    if (k.length < 5) continue
                    if (liveUpdate == null) {
                        liveUpdate = updates.entries.firstOrNull { (uKey, _) ->
                            uKey.equals(k, ignoreCase = true) || 
                            (uKey.length >= 8 && k.length >= 8 && (uKey.startsWith(k, ignoreCase = true) || k.startsWith(uKey, ignoreCase = true)))
                        }?.value
                    }
                    if (liveVehicle == null) {
                        liveVehicle = positions.entries.firstOrNull { (vKey, _) ->
                            vKey.equals(k, ignoreCase = true) || 
                            (vKey.length >= 8 && k.length >= 8 && (vKey.startsWith(k, ignoreCase = true) || k.startsWith(vKey, ignoreCase = true)))
                        }?.value
                    }
                    if (liveUpdate != null && liveVehicle != null) break
                }
            }

            // A train is strictly considered live if present in the Vehicle Positions / Flota telemetry feed
            val isLive = liveVehicle != null
            val isCanceled = liveUpdate?.isCanceled ?: false
            val delaySeconds = (liveUpdate?.stopDelays?.get(stopId) ?: liveUpdate?.delaySeconds ?: 0L).toInt()
            val delayMinutes = delaySeconds / 60
            val livePlatform = when {
                liveVehicle?.currentStopId == stopId && !liveVehicle.platform.isNullOrBlank() -> {
                    liveVehicle.platform
                }
                liveVehicle?.nextStopId == stopId && !liveVehicle.nextPlatform.isNullOrBlank() -> {
                    liveVehicle.nextPlatform
                }
                !liveUpdate?.stopPlatforms?.get(stopId).isNullOrBlank() -> {
                    liveUpdate?.stopPlatforms?.get(stopId) ?: ""
                }
                else -> ""
            }
            val platform = livePlatform.trim()

            val vehStatus = liveVehicle?.status ?: ""
            val currentStopId = liveVehicle?.currentStopId ?: ""
            val nextStopId = liveVehicle?.nextStopId ?: ""
            val currentStopName = if (currentStopId.isNotBlank()) {
                val rawName = stationNameMap[currentStopId] ?: ""
                CercaniasDepartureMapper.formatStationDisplayName(rawName)
            } else ""
            val nextStopName = if (nextStopId.isNotBlank()) {
                val rawName = stationNameMap[nextStopId] ?: ""
                CercaniasDepartureMapper.formatStationDisplayName(rawName)
            } else ""

            val isStoppedAt = (vehStatus == "STOPPED_AT" && currentStopId == stopId)
            val isIncomingAt = (vehStatus == "INCOMING_AT" && (currentStopId == stopId || nextStopId == stopId))

            val locationText = when {
                isStoppedAt -> "Parado en la estación"
                isIncomingAt -> "Llegando a la estación"
                vehStatus == "STOPPED_AT" && currentStopName.isNotBlank() -> "Parado en $currentStopName"
                vehStatus == "INCOMING_AT" && nextStopName.isNotBlank() -> "Llegando a $nextStopName"
                vehStatus == "INCOMING_AT" && currentStopName.isNotBlank() -> "Llegando a $currentStopName"
                vehStatus == "IN_TRANSIT_TO" && nextStopName.isNotBlank() -> "En trayecto hacia $nextStopName"
                vehStatus == "IN_TRANSIT_TO" && currentStopName.isNotBlank() -> "En trayecto hacia $currentStopName"
                isLive -> "En circulación"
                else -> ""
            }

            val effectiveDepSeconds = depSeconds + delaySeconds
            var diffSeconds = effectiveDepSeconds - nowSecondsOfDay
            var isTomorrow = false

            // Handle midnight wrap-around:
            if (diffSeconds < -72000 && nowHour >= 20) {
                diffSeconds += 86400
                isTomorrow = true
            }

            var minutesRemaining = Math.round(diffSeconds / 60.0f)

            // A departure is past if effective departure time has passed by more than 30 seconds,
            // UNLESS the train has active vehicle telemetry at/arriving at this station (or within 3 mins max)
            if (diffSeconds < -30) {
                if (isStoppedAt || isIncomingAt || (isLive && diffSeconds >= -180)) {
                    minutesRemaining = 0
                } else {
                    continue
                }
            } else if (minutesRemaining <= 0 && (isStoppedAt || isIncomingAt || isLive)) {
                minutesRemaining = 0
            }

            val timeHHmm = formatTimeHHmm(h.llegada)
            val estimatedTimeStr = if (isLive && !isCanceled) {
                formatSecondsToHHmm(effectiveDepSeconds)
            } else {
                ""
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
                    latitude = liveVehicle?.latitude,
                    longitude = liveVehicle?.longitude,
                    status = vehStatus,
                    locationText = locationText,
                    isCanceled = isCanceled,
                    isStoppedAt = isStoppedAt,
                    isIncomingAt = isIncomingAt,
                    isTomorrow = isTomorrow,
                    allTripIds = allTripIds
                )
            )
        }

        // If all departures for today have already passed (late night after last train),
        // fallback to tomorrow's morning departures
        if (list.isEmpty() && horarios.isNotEmpty()) {
            for (h in horarios) {
                val line = h.linea
                val allTripIds = if (h.trip_ids.isNotEmpty()) h.trip_ids else h.tripIds
                val tripId = allTripIds.firstOrNull() ?: ""
                val formattedDest = syncManager.formatDestinationName(h.destino)
                val dest = if (formattedDest.isNotBlank() && !formattedDest.equals("Civis", ignoreCase = true)) {
                    formattedDest
                } else {
                    syncManager.getTripDestination(tripId, line, currentStationName)
                }
                if (CercaniasDepartureMapper.isTerminalArrival(currentStationName, dest)) continue
                val depSeconds = parseTimeToSeconds(h.llegada)
                if (depSeconds < 0) continue

                val diffSeconds = (depSeconds + 86400) - nowSecondsOfDay
                val minutesRemaining = Math.round(diffSeconds / 60.0f)
                val timeHHmm = formatTimeHHmm(h.llegada)

                list.add(
                    CercaniasDeparture(
                        routeId = line,
                        destination = dest,
                        minutesRemaining = minutesRemaining,
                        delayMinutes = 0,
                        tripId = tripId,
                        departureTime = timeHHmm,
                        estimatedTime = "",
                        isLive = false,
                        isTomorrow = true,
                        allTripIds = allTripIds
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
