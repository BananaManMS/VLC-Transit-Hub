package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.PlannedStop

object PenultimateStopArrivalMatcher {
    suspend fun fetchLiveArrivalMinutes(
        leg: PlannedLeg,
        stop: PlannedStop,
        nowMs: Long,
        boardedVehicleId: String?,
        lastKnownDrift: Int,
        progressFraction: Float
    ): Int? {
        val totalMins = (leg.durationSeconds / 60).toInt()
        val remaining = (totalMins * (1.0f - progressFraction)).toInt()
        return (remaining + lastKnownDrift).coerceAtLeast(0)
    }
}
