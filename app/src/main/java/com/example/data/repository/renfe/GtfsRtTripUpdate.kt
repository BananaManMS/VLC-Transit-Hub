package com.example.data.repository.renfe

data class GtfsRtStopTime(
    val stopId: String,
    val stopSequence: Int = 0,
    val arrivalDelay: Long? = null,
    val departureDelay: Long? = null,
    val estimatedDepartureTime: Long? = null,
    val platform: String? = null,
    val isIndeterminate: Boolean = false
)

data class GtfsRtTripUpdate(
    val tripId: String,
    val routeId: String = "",
    val startDate: String = "",
    val startTime: String = "",
    val scheduleRelationship: String = "SCHEDULED",
    val delaySeconds: Long = 0L,
    val stopDelays: Map<String, Long> = emptyMap(),
    val stopEstimatedTimes: Map<String, Long> = emptyMap(),
    val stopPlatforms: Map<String, String> = emptyMap(),
    val stopTimes: List<GtfsRtStopTime> = emptyList(),
    val vehicleId: String = "",
    val vehicleLabel: String = "",
    val isIndeterminateDeparture: Boolean = false,
    val isCanceled: Boolean = false,
    val timestamp: Long = 0L
)
