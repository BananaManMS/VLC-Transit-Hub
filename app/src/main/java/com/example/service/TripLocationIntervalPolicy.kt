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

    private const val TTFF_GUARANTEE_WINDOW_SECONDS = 30L

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

        if (effectiveRemainingSeconds <= TTFF_GUARANTEE_WINDOW_SECONDS && !isGeofenceGateOpen) {
            onOpenHighAccuracyGate("TTFF Guarantee Dynamic Fail-safe: Remaining ETA <= ${TTFF_GUARANTEE_WINDOW_SECONDS}s")
        }

        // OPTIMIZATION 1: If the user is standing at a station/stop waiting for transit (unboarded public transit leg)
        val isTransitLeg = currentLeg.mode != TransitMode.WALK && currentLeg.mode != TransitMode.BICYCLE
        if (isTransitLeg && !progressInfo.isBoarded) {
            val vehicleArrivalMins = realTime?.vehicleArrivalMinutes ?: progressInfo.lastSeenArrivalMins ?: 99
            return if (vehicleArrivalMins <= 2) {
                // Vehicle is arriving in <= 2 mins, increase GPS frequency to detect boarding event
                10000L // 10s
            } else {
                // Stationary at station waiting for metro/bus/train, drop GPS frequency to 30s to save immense battery!
                30000L // 30s
            }
        }

        return when (currentLeg.mode) {
            TransitMode.SUBWAY -> {
                val isNearEndOrTransfer = progressInfo.progressWithinLeg >= 0.80f ||
                        (realTime != null && ((realTime.vehicleArrivalMinutes ?: 99) <= 1 || (realTime.vehicleSecondsRemaining ?: 999) <= 60)) ||
                        (currentLegIndex == legs.size - 1 && progressInfo.progressWithinLeg >= 0.70f)

                if (isNearEndOrTransfer) {
                    5000L // 5s interval near station/transfer to catch GPS fix during platform stop / exit
                } else {
                    // Boarded subway tunnel: zero GPS reception, waste no battery, drop frequency to 20s
                    20000L // 20s
                }
            }
            TransitMode.WALK, TransitMode.BICYCLE -> {
                // OPTIMIZATION 2: If walking/cycling on a long straight path, relax GPS interval in the middle
                val isLongSegment = currentLeg.durationSeconds > 180 // > 3 minutes
                val isInTheMiddleOfWalk = progressInfo.progressWithinLeg in 0.15f..0.85f
                
                if (isLongSegment && isInTheMiddleOfWalk) {
                    12000L // 12s interval for long walks on straight roads
                } else {
                    6000L // 6s high-frequency for junctions, starting point, and final destination approaches
                }
            }
            TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL -> {
                val isNearTransferOrDest = (realTime != null && (realTime.vehicleArrivalMinutes ?: 99) <= 1) ||
                        progressInfo.progressWithinLeg >= 0.85f
                
                if (isNearTransferOrDest) {
                    6000L // 6s frequency during approach to transfer point or final stop
                } else {
                    // Superficial transit: 20s interval during steady ride
                    20000L // 20s
                }
            }
            else -> 20000L
        }
    }
}
