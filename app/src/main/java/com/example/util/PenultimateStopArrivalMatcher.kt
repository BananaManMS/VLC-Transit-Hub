package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.PlannedStop
import com.example.data.model.routing.TransitMode
import com.example.data.repository.routing.TransitIdMapper

object PenultimateStopArrivalMatcher {
    suspend fun fetchLiveArrivalMinutes(
        leg: PlannedLeg,
        stop: PlannedStop,
        nowMs: Long,
        boardedVehicleId: String?,
        lastKnownDrift: Int,
        progressFraction: Float
    ): Int? {
        try {
            when (leg.mode) {
                TransitMode.SUBWAY, TransitMode.TRAM -> {
                    val stationId = TransitIdMapper.extractMetroStationId(stop.stopId, stop.name)?.toString()
                    if (!stationId.isNullOrBlank()) {
                        val departures = TransitOperatorArrivalProvider.fetchMetroDepartures(stationId)
                        val lineDigits = leg.routeShortName?.filter { it.isDigit() } ?: ""
                        val matching = departures.filter { dep ->
                            val depDigits = dep.line.filter { it.isDigit() }
                            (depDigits.isNotBlank() && depDigits == lineDigits) ||
                                    dep.line.equals(leg.routeShortName, ignoreCase = true)
                        }.filter { dep ->
                            TripVehicleMatcher.isDestinationMatch(dep.destination, leg, dep.line)
                        }

                        // Match vehicle by ID or closest realistic arrival
                        val vehicleMatch = if (!boardedVehicleId.isNullOrBlank()) {
                            matching.find { it.vehicleId == boardedVehicleId }
                        } else null

                        val chosen = vehicleMatch ?: matching.minByOrNull { it.minutes }
                        if (chosen != null) {
                            return chosen.minutes
                        }
                    }
                }
                TransitMode.BUS -> {
                    val stopNum = TransitIdMapper.extractEmtStopNumber(stop.stopId, stop.name)
                    if (stopNum != null) {
                        val arrivals = TransitOperatorArrivalProvider.fetchEmtArrivals(stopNum)
                        val rawLine = leg.routeShortName ?: ""
                        val matching = arrivals.filter { arr ->
                            TransitIdMapper.isSameEmtLine(arr.line, rawLine) &&
                                    TripVehicleMatcher.isDestinationMatch(arr.destination, leg)
                        }
                        val chosen = matching.minByOrNull { it.minutes }
                        if (chosen != null) {
                            return chosen.minutes
                        }
                    }
                }
                else -> {}
            }
        } catch (_: Exception) {}

        // Fallback: inercial progress-based remaining minutes to stop
        val totalMins = (leg.durationSeconds / 60).toInt()
        val remaining = (totalMins * (1.0f - progressFraction)).toInt()
        return (remaining + lastKnownDrift).coerceAtLeast(0)
    }
}
