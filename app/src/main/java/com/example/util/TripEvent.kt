package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.repository.ActiveTripState

/**
 * Domain events that mutate the active trip session state ("La Pizarra").
 */
sealed interface TripEvent {

    /** User GPS or Network position update */
    data class LocationUpdated(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float? = null,
        val speedMps: Float? = null,
        val bearingDegrees: Float? = null,
        val timestampMs: Long = System.currentTimeMillis()
    ) : TripEvent

    /** Live operator telemetry updated from background reconciler */
    data class RealTimeStatusUpdated(
        val status: RealTimeTripStatus
    ) : TripEvent

    /** User or Sensor Fusion confirmed vehicle boarding */
    data class BoardingConfirmed(
        val legIndex: Int,
        val leg: PlannedLeg
    ) : TripEvent

    /** Trip leg completed or manually advanced */
    data class LegAdvanced(
        val newLegIndex: Int
    ) : TripEvent

    /** Periodic 4-second ticker for in-tunnel dead-reckoning and courtesy countdown */
    data class Tick(
        val timestampMs: Long = System.currentTimeMillis()
    ) : TripEvent

    /** Active trip model updated from repository */
    data class ActiveTripUpdated(
        val trip: ActiveTripState
    ) : TripEvent
}
