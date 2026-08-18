package com.example.data.repository.renfe

import com.example.data.database.CercaniasStationEntity
import com.example.data.mapper.CercaniasDepartureMapper

import com.example.ui.cercanias.CercaniasDeparture
import com.example.ui.cercanias.LiveVehicleInfo
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CercaniasScheduleProcessor(
    private val gtfsCacheManager: GtfsCacheManager,
    private val getTripDestination: (String, String, String) -> String,

) {

    fun processDepartures(
        station: CercaniasStationEntity,
        liveUpdatesMap: Map<String, GtfsRtTripUpdate>,
        vehicleMap: Map<String, LiveVehicleInfo>,
        stationNames: Map<String, String>
    ): List<CercaniasDeparture> {
        val spainTimeZone = TimeZone.getTimeZone("Europe/Madrid")
        val currentCal = Calendar.getInstance(spainTimeZone)
        val todayLetter = getDayLetter(currentCal.get(Calendar.DAY_OF_WEEK))
        
        val tomorrowCal = Calendar.getInstance(spainTimeZone)
        tomorrowCal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowLetter = getDayLetter(tomorrowCal.get(Calendar.DAY_OF_WEEK))
        
        val currentSec = currentCal.get(Calendar.HOUR_OF_DAY) * 3600 +
                currentCal.get(Calendar.MINUTE) * 60 +
                currentCal.get(Calendar.SECOND)
        val currentEpochSec = System.currentTimeMillis() / 1000
        val departures = mutableListOf<CercaniasDeparture>()
        
        val liveUpdatesBySuffix = HashMap<String, GtfsRtTripUpdate>(liveUpdatesMap.size)
        for ((id, update) in liveUpdatesMap) {
            if (id.length > 4) {
                liveUpdatesBySuffix[id.substring(4)] = update
            }
        }
        val vehicleBySuffix = HashMap<String, LiveVehicleInfo>(vehicleMap.size)
        for ((id, veh) in vehicleMap) {
            if (id.length > 4) {
                vehicleBySuffix[id.substring(4)] = veh
            }
        }

        for (dayOffsetSec in listOf(0, 86400)) {
            val isTomorrow = dayOffsetSec == 86400
            val targetDayLetter = if (isTomorrow) tomorrowLetter else todayLetter
            for (item in station.horarios) {
                val matchedTripId = item.trip_ids.firstOrNull { it.length > 4 && it[4] == targetDayLetter } 
                    ?: continue
                val destination = getTripDestination(matchedTripId, item.linea, station.nombre)
                
                if (!CercaniasDepartureMapper.isValidDeparture(item, station.nombre, destination)) {
                    continue
                }
                
                val normDest = CercaniasDepartureMapper.normalizeStationName(destination)
                val normCur = CercaniasDepartureMapper.normalizeStationName(station.nombre)
                if (normDest.isNotEmpty() && normDest == normCur) {
                    continue
                }
                
                var llegadaStr = item.llegada.trim()
                if (llegadaStr.length < 5) continue
                val timeParts = llegadaStr.split(":")
                if (timeParts.size < 2) continue
                val schedHour = timeParts[0].toIntOrNull() ?: continue
                val schedMin = timeParts[1].toIntOrNull() ?: continue
                val schedSec = if (timeParts.size >= 3) timeParts[2].toIntOrNull() ?: 0 else 0
                
                val totalSchedSec = schedHour * 3600 + schedMin * 60 + schedSec + dayOffsetSec
                
                val suffixKey = if (matchedTripId.length > 4) matchedTripId.substring(4) else null
                var matchedUpdate: GtfsRtTripUpdate? = liveUpdatesMap[matchedTripId]
                    ?: (if (suffixKey != null) liveUpdatesBySuffix[suffixKey] else null)
                var matchedVehicle: LiveVehicleInfo? = vehicleMap[matchedTripId]
                    ?: (if (suffixKey != null) vehicleBySuffix[suffixKey] else null)
                
                val activeTripId = matchedUpdate?.tripId ?: matchedVehicle?.tripId ?: matchedTripId
                if (matchedUpdate != null && matchedVehicle != null) {
                    if (matchedUpdate.firstActiveStopId.isNotEmpty() && 
                        matchedVehicle.status == "STOPPED_AT" && 
                        matchedVehicle.currentStopId.isNotEmpty() &&
                        matchedUpdate.firstActiveStopId != matchedVehicle.currentStopId) {
                        matchedVehicle = matchedVehicle.copy(
                            status = "IN_TRANSIT_TO",
                            currentStopId = matchedUpdate.firstActiveStopId
                        )
                    }
                }
                val isLive = matchedUpdate != null || matchedVehicle != null
                val isCanceled = matchedUpdate?.scheduleRelationship?.equals("CANCELED", ignoreCase = true) == true
                val isAdded = matchedUpdate?.scheduleRelationship?.equals("ADDED", ignoreCase = true) == true
                val isSkippedAtStop = matchedUpdate?.skippedStops?.contains(station.stop_id) == true
                
                val delaySec = if (matchedUpdate != null) {
                    matchedUpdate.stopDelays[station.stop_id] ?: matchedUpdate.delaySeconds
                } else 0
                
                val delayMin = if (delaySec != 0) {
                    if (delaySec > 0) (delaySec + 30) / 60 else -((-delaySec + 30) / 60)
                } else 0
                val estTotalSec = totalSchedSec + delaySec
                val timeRemainingSec = estTotalSec - currentSec
                if (timeRemainingSec > 86400) {
                    continue
                }
                if (isTomorrow && timeRemainingSec > 30 * 60) {
                    matchedUpdate = null
                    matchedVehicle = null
                }
                val isStoppedAtThisStation = matchedVehicle != null &&
                        matchedVehicle.currentStopId == station.stop_id &&
                        matchedVehicle.status == "STOPPED_AT" &&
                        currentSec >= totalSchedSec
                val isIncomingAtThisStation = matchedVehicle != null &&
                        matchedVehicle.status == "INCOMING_AT" && 
                        matchedVehicle.currentStopId == station.stop_id
                if (isLive) {
                    if (timeRemainingSec < -90) {
                        val stillAtStation = matchedVehicle != null && matchedVehicle.currentStopId == station.stop_id
                        if (!stillAtStation) {
                            continue
                        }
                    }
                } else {
                    if (timeRemainingSec < -3600) {
                        continue
                    }
                    if (timeRemainingSec < -30) {
                        continue
                    }
                }
                val isRecoveredStopped = timeRemainingSec < -30 &&  
                        matchedVehicle != null && matchedVehicle.currentStopId == station.stop_id && matchedVehicle.status == "STOPPED_AT"
                val timeRemainingMin = (timeRemainingSec + 30) / 60
                
                val schedTimeFormatted = String.format(Locale.getDefault(), "%02d:%02d", schedHour, schedMin)
                
                val estSecNormalized = ((estTotalSec % 86400) + 86400) % 86400
                val estHour = estSecNormalized / 3600
                val estMin = (estSecNormalized % 3600) / 60
                val estTimeFormatted = String.format(Locale.getDefault(), "%02d:%02d", estHour, estMin)
                val platformForUserStation = if (matchedVehicle != null &&
                    matchedVehicle.currentStopId == station.stop_id &&
                    matchedVehicle.status in listOf("INCOMING_AT", "IN_TRANSIT_TO", "STOPPED_AT")
                ) {
                    matchedVehicle.platform
                } else ""
                val locationText = if (matchedVehicle != null && matchedVehicle.currentStopId.isNotEmpty()) {
                    val vehStopId = matchedVehicle.currentStopId
                    val vehStationName = stationNames[vehStopId] ?: "estación"
                    when (matchedVehicle.status) {
                        "INCOMING_AT" -> "Llegando a $vehStationName"
                        "IN_TRANSIT_TO" -> "En trayecto hacia $vehStationName"
                        "STOPPED_AT" -> "Parado en $vehStationName"
                        else -> "En trayecto hacia $vehStationName"
                    }
                } else if (matchedUpdate != null && matchedUpdate.firstActiveStopId.isNotEmpty()) {
                    val nextStationName = stationNames[matchedUpdate.firstActiveStopId] ?: "estación"
                    "Próxima parada: $nextStationName"
                } else {
                    val cachedVeh = vehicleMap[activeTripId] ?: (if (activeTripId.length > 4) vehicleBySuffix[activeTripId.substring(4)] else null)
                    if (cachedVeh != null && cachedVeh.currentStopId.isNotEmpty()) {
                        val vehStopId = cachedVeh.currentStopId
                        val vehStationName = stationNames[vehStopId] ?: "estación"
                        val cachedTime = gtfsCacheManager.getVehicleCacheTime(activeTripId) ?: cachedVeh.timestamp
                        val ageSec = currentEpochSec - cachedTime
                        val ageMin = (ageSec / 60).coerceAtLeast(0)
                        
                        if (ageSec < 600L) {
                            val baseLoc = when (cachedVeh.status) {
                                "INCOMING_AT" -> "Llegando a $vehStationName"
                                "IN_TRANSIT_TO" -> "En trayecto hacia $vehStationName"
                                "STOPPED_AT" -> "Parado en $vehStationName"
                                else -> "En trayecto hacia $vehStationName"
                            }
                            if (ageMin <= 1) {
                                "Última info: $baseLoc"
                            } else {
                                "Última info (hace $ageMin min): $baseLoc"
                            }
                        } else {
                            if (currentSec < estTotalSec) {
                                "No ha iniciado el trayecto"
                            } else {
                                "En trayecto hacia $destination"
                            }
                        }
                    } else {
                        if (currentSec < estTotalSec) {
                            "No ha iniciado el trayecto"
                        } else {
                            "En trayecto hacia $destination"
                        }
                    }
                }
                val statusText = when {
                    isCanceled -> "Cancelado"
                    isSkippedAtStop -> "Estación sin servicio"
                    isStoppedAtThisStation -> "Parado en estación"
                    isIncomingAtThisStation -> "Llegando a estación"
                    isRecoveredStopped -> "Detenido en estación"
                    isLive && delayMin > 0 -> "Retraso +$delayMin min"
                    isLive && delayMin < 0 -> "Adelantado ${-delayMin} min"
                    isLive -> "A tiempo"
                    else -> "Programado"
                }
                departures.add(
                    CercaniasDeparture(
                        routeId = item.linea,
                        destination = destination,
                        minutesRemaining = timeRemainingMin.coerceAtLeast(0),
                        delayMinutes = delayMin,
                        tripId = activeTripId,
                        departureTime = schedTimeFormatted,
                        estimatedTime = estTimeFormatted,
                        isLive = isLive,
                        platform = platformForUserStation,
                        latitude = matchedVehicle?.latitude,
                        longitude = matchedVehicle?.longitude,
                        status = statusText,
                        locationText = locationText,
                        isCanceled = isCanceled,
                        isAdded = isAdded,
                        isSkippedAtStop = isSkippedAtStop,
                        isRecoveredStopped = isRecoveredStopped,
                        isStoppedAt = isStoppedAtThisStation,
                        isIncomingAt = isIncomingAtThisStation,
                        isTomorrow = isTomorrow
                    )
                )
            }
        }
        
        return CercaniasDepartureMapper.sortDeparturesChronologically(departures)
    }

    private fun getDayLetter(calendarDayOfWeek: Int): Char {
        return when (calendarDayOfWeek) {
            Calendar.MONDAY -> 'L'
            Calendar.TUESDAY -> 'M'
            Calendar.WEDNESDAY -> 'X'
            Calendar.THURSDAY -> 'J'
            Calendar.FRIDAY -> 'V'
            Calendar.SATURDAY -> 'S'
            Calendar.SUNDAY -> 'D'
            else -> 'L'
        }
    }
}
