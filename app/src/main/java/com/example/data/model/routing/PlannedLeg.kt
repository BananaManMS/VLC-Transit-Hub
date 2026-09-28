package com.example.data.model.routing

import org.osmdroid.util.GeoPoint

data class PlannedLeg(
    val mode: TransitMode,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val formattedDuration: String,
    val startTime: String,
    val endTime: String,
    val formattedStartTime: String,
    val formattedEndTime: String,
    val agencyName: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val headsign: String? = null,
    val routeColorHex: String,
    val fromName: String,
    val toName: String,
    val fromStopId: String? = null,
    val toStopId: String? = null,
    val fromLat: Double = 0.0,
    val fromLon: Double = 0.0,
    val toLat: Double = 0.0,
    val toLon: Double = 0.0,
    val intermediateStops: List<PlannedStop> = emptyList(),
    val geometry: List<GeoPoint> = emptyList(),
    val realTimeDelayMinutes: Int? = null,
    val isRealTimeVerified: Boolean = false,
    val schedulePhase: SchedulePhase = SchedulePhase.THEORETICAL_AWAITING_RADAR,
    val scheduledStartTime: String? = null,
    val scheduledEndTime: String? = null,
    val hasActiveAlert: Boolean = false,
    val alertMessage: String? = null
)
