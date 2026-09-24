package com.example.data.repository.renfe

data class GtfsRtTripUpdate(
    val tripId: String,
    val routeId: String = "",
    val delaySeconds: Long = 0L,
    val stopDelays: Map<String, Long> = emptyMap(),
    val stopEstimatedTimes: Map<String, Long> = emptyMap()
)
