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
        // Safe evaluation of penultimate stop ETA
        return null
    }
}
