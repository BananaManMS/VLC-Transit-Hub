package com.example.ui.cercanias

data class CercaniasDeparture(
    val routeId: String,
    val destination: String,
    val minutesRemaining: Int,
    val delayMinutes: Int,
    val tripId: String,
    val departureTime: String = "",
    val estimatedTime: String = "",
    val isLive: Boolean = false,
    val platform: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "",
    val locationText: String = "",
    val isCanceled: Boolean = false,
    val isAdded: Boolean = false,
    val isSkippedAtStop: Boolean = false,
    val isRecoveredStopped: Boolean = false,
    val isStoppedAt: Boolean = false,
    val isIncomingAt: Boolean = false,
    val isTomorrow: Boolean = false
)

data class CercaniasAlert(
    val id: String,
    val headerEs: String,
    val descriptionEs: String,
    val routeIds: List<String>,
    val tripIds: List<String>,
    val stopIds: List<String>,
    val isAccessibility: Boolean,
    val timestamp: Long
)

data class LiveVehicleInfo(
    val tripId: String,
    val routeId: String,
    val latitude: Double?,
    val longitude: Double?,
    val status: String,
    val platform: String,
    val speed: Double? = null,
    val currentStopId: String = "",
    val timestamp: Long = 0L
)

data class ScheduledDeparture(
    val line: String,
    val destination: String,
    val time: String,
    val days: List<Int>
)
