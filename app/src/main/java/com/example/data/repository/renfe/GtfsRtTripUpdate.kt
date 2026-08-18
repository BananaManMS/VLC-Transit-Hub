package com.example.data.repository.renfe

data class GtfsRtTripUpdate(
    val tripId: String,
    val delaySeconds: Int,
    val scheduleRelationship: String = "SCHEDULED",
    val stopDelays: Map<String, Int> = emptyMap(),
    val stopEstimatedTimes: Map<String, Long> = emptyMap(),
    val skippedStops: Set<String> = emptySet(),
    val firstActiveStopId: String = ""
)

enum class TripDirection { INBOUND, OUTBOUND }
