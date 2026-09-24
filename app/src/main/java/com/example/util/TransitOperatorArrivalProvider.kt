package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.RealTimeTransitRepository
import java.util.Calendar

/**
 * Encapsulates fetching and adapting raw transit telemetry into normalized candidates
 * for each operator (EMT Valencia, Metrovalencia, Renfe Cercanías).
 */
object TransitOperatorArrivalProvider {

    /**
     * Fetches and normalizes arrivals for EMT Bus stop.
     */
    suspend fun fetchEmtArrivals(stopNumber: String): List<TransitArrivalCandidate> {
        val liveArrivals = RealTimeTransitRepository.getEmtLiveArrivals(stopNumber, useFastTimeout = true)
        return liveArrivals.mapNotNull { time ->
            val mins = when {
                time.minutos.contains(":") -> {
                    val parts = time.minutos.split(":")
                    val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
                    cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                    cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                    cal.set(Calendar.SECOND, 0)
                    var diffMs = cal.timeInMillis - System.currentTimeMillis()
                    if (diffMs < -12 * 3600 * 1000L) diffMs += 24 * 3600 * 1000L
                    ((diffMs / 60000L).toInt()).coerceAtLeast(1)
                }
                time.minutos.startsWith("pr", ignoreCase = true) -> 1
                else -> time.minutos.filter { it.isDigit() }.toIntOrNull() ?: (if (time.secondsRemaining > 0) time.secondsRemaining / 60 else null)
            }

            if (mins != null && time.linea.isNotBlank()) {
                TransitArrivalCandidate(
                    line = time.linea,
                    destination = time.destino,
                    minutes = mins,
                    seconds = mins * 60,
                    vehicleId = null,
                    rawStopId = stopNumber,
                    isRealTime = time.isRealTime
                )
            } else null
        }
    }

    /**
     * Fetches and normalizes departures for Metrovalencia station.
     */
    suspend fun fetchMetroDepartures(stationId: String): List<TransitArrivalCandidate> {
        val departures = RealTimeTransitRepository.getMetroLiveArrivals(stationId)
        return departures.map { dep ->
            val hasLiveTelemetry = !dep.vehicleId.isNullOrBlank() ||
                    (dep.status != null && !dep.status.equals("programado", ignoreCase = true))
            TransitArrivalCandidate(
                line = dep.line,
                destination = dep.destination,
                minutes = dep.minutes,
                seconds = dep.seconds,
                vehicleId = dep.vehicleId,
                rawStopId = stationId,
                isRealTime = hasLiveTelemetry
            )
        }.sortedBy { it.seconds }
    }
}
