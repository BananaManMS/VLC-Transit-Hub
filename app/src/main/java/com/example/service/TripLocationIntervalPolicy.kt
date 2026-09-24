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

        return when (currentLeg.mode) {
            TransitMode.SUBWAY -> {
                val isNearEndOrTransfer = progressInfo.progressWithinLeg >= 0.80f ||
                        (realTime != null && ((realTime.vehicleArrivalMinutes ?: 99) <= 1 || (realTime.vehicleSecondsRemaining ?: 999) <= 60)) ||
                        (currentLegIndex == legs.size - 1 && progressInfo.progressWithinLeg >= 0.70f)

                if (isNearEndOrTransfer) {
                    5000L // 4-5s interval near station/transfer to catch GPS fix during cut&cover platform stop
                } else {
                    12000L // 10-15s (12s) interval in subway tunnel relying on cell towers and dead-reckoning
                }
            }
            TransitMode.WALK, TransitMode.BICYCLE -> 6000L // 5-7s (6s) interval for walking/cycling
            TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL -> {
                val isNearTransferOrDest = (realTime != null && (realTime.vehicleArrivalMinutes ?: 99) <= 1) ||
                        progressInfo.progressWithinLeg >= 0.85f
                if (isNearTransferOrDest) 5000L else 12000L // 10-15s (12s) on surface, 5s near destination/transfer
            }
            else -> 12000L
        }
    }
}
