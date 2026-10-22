package com.example.service

import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.util.ActiveTripProgressTracker
import com.example.util.RealTimeTripStatus

/**
 * Calculates adaptive GPS location update intervals based on transit mode,
 * progress within the current leg, and real-time arrival estimates.
 * Also evaluates fail-safe triggers for TTFF (Time-To-First-Fix) guarantee windows.
 */
object TripLocationIntervalPolicy {

    private const val CRITICAL_APPROACH_WINDOW_SECONDS = 90L

    fun computeLocationInterval(
        trip: ActiveTripState,
        latestRealTimeStatus: RealTimeTripStatus?,
        isGeofenceGateOpen: Boolean,
        onOpenHighAccuracyGate: (reason: String) -> Unit
    ): Long {
        val legs = trip.itinerary.legs
        val currentLegIndex = trip.currentLegIndex
        val currentLeg = legs.getOrNull(currentLegIndex) ?: return 12000L
        val progressInfo = ActiveTripProgressTracker.progressState.value
        val realTime = latestRealTimeStatus

        val remainingLegSeconds = (currentLeg.durationSeconds * (1.0f - progressInfo.progressWithinLeg)).toLong()
        val realTimeRemainingSeconds = realTime?.vehicleSecondsRemaining?.toLong()
        val effectiveRemainingSeconds = realTimeRemainingSeconds ?: remainingLegSeconds

        // Critical approach window: penultimate stop reached (remainingStops <= 1), remaining ETA <= 90s, or progress >= 80%
        val isCriticalPenultimateOrArrivalApproach = (progressInfo.remainingStopsCount != null && progressInfo.remainingStopsCount!! <= 1) ||
                effectiveRemainingSeconds <= CRITICAL_APPROACH_WINDOW_SECONDS ||
                progressInfo.progressWithinLeg >= 0.80f

        if (isCriticalPenultimateOrArrivalApproach && !isGeofenceGateOpen) {
            onOpenHighAccuracyGate("Critical approach window: penultimate stop or ETA <= ${CRITICAL_APPROACH_WINDOW_SECONDS}s (stops remaining: ${progressInfo.remainingStopsCount})")
        }

        // OPTIMIZATION 1: If the user is standing at a station/stop waiting for transit (unboarded public transit leg)
        val isTransitLeg = currentLeg.mode != TransitMode.WALK && currentLeg.mode != TransitMode.BICYCLE
        if (isTransitLeg && !progressInfo.isBoarded) {
            val vehicleArrivalMins = realTime?.vehicleArrivalMinutes ?: progressInfo.lastSeenArrivalMins ?: 99
            return if (vehicleArrivalMins <= 2) {
                // Vehicle is arriving in <= 2 mins, increase frequency to 10s to detect boarding event
                10000L // 10s
            } else {
                // Stationary at station waiting for metro/bus/train, drop frequency to 30s to save battery
                30000L // 30s
            }
        }

        return when (currentLeg.mode) {
            TransitMode.SUBWAY -> {
                if (isCriticalPenultimateOrArrivalApproach) {
                    5000L // 5s accelerated frequency during approach to penultimate/destination platform
                } else {
                    // Boarded subway tunnel cruise: GNSS sleeping, listening to cell towers/Wi-Fi every 30s
                    30000L // 30s
                }
            }
            TransitMode.WALK, TransitMode.BICYCLE -> {
                val isLongSegment = currentLeg.durationSeconds > 180 // > 3 minutes
                val isInTheMiddleOfWalk = progressInfo.progressWithinLeg in 0.15f..0.85f
                
                if (isLongSegment && isInTheMiddleOfWalk) {
                    12000L // 12s interval for long walks on straight roads
                } else {
                    6000L // 6s high-frequency for junctions, starting point, and final destination approaches
                }
            }
            TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL, TransitMode.METROBUS, TransitMode.CERCANIAS -> {
                if (isCriticalPenultimateOrArrivalApproach) {
                    5000L // 5s frequency during approach to penultimate stop, transfer point or final stop
                } else {
                    // Surface transit cruise: 20s interval during steady ride
                    20000L // 20s
                }
            }
            else -> 20000L
        }
    }
}
