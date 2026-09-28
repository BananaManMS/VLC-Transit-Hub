package com.example.data.model.routing

import org.osmdroid.util.GeoPoint



/**
 * Transport mode representation with localized labels and visual indicators.
 */
enum class TransitMode(
    val code: String,
    val displayNameEs: String,
    val displayNameCa: String,
    val defaultColorHex: String
) {
    WALK("WALK", "A pie", "A peu", "9E9E9E"),
    BUS("BUS", "Autobús", "Autobús", "E52320"),
    SUBWAY("SUBWAY", "Metro", "Metro", "005BBB"),
    TRAM("TRAM", "Tranvía", "Tramvia", "6E2585"),
    RAIL("RAIL", "Cercanías", "Rodalia", "BF1E24"),
    BICYCLE("BICYCLE", "Bicicleta", "Bicicleta", "10B981"),
    METROBUS("METROBUS", "Metrobús", "Metrobús", "0D47A1"),
    CERCANIAS("CERCANIAS", "Rodalia", "Rodalia", "BF1E24"),
    VALENBISI("VALENBISI", "Valenbisi", "Valenbisi", "10B981");

    companion object {
        fun fromString(modeStr: String?): TransitMode {
            val upper = modeStr?.uppercase()?.trim() ?: return WALK
            return when {
                upper == "WALK" || upper == "FOOT" || upper.contains("WALK") -> WALK
                upper == "BUS" -> BUS
                upper == "SUBWAY" || upper == "METRO" -> SUBWAY
                upper == "TRAM" -> TRAM
                upper == "RAIL" || upper == "TRAIN" || upper.contains("RAIL") || upper.contains("TRAIN") ||
                        upper.contains("SUBURBAN") || upper.contains("COMMUTER") || upper.contains("CERCAN") ||
                        upper.contains("RODALIA") -> RAIL
                upper == "BICYCLE" || upper == "BIKE" -> BICYCLE
                upper.contains("BUS") -> BUS
                upper.contains("SUBWAY") || upper.contains("METRO") -> SUBWAY
                upper.contains("TRAM") -> TRAM
                else -> WALK
            }
        }
    }
}

/**
 * Real-time viability status of an itinerary.
 */
enum class ItineraryViability {
    /** Real-time GPS confirms connection is viable with healthy slack */
    VIABLE_ON_TIME,
    
    /** Real-time bus/train would be missed; departure automatically rescheduled to next vehicle */
    ADJUSTED_NEXT_DEPARTURE,
    
    /** Scheduled static timetable (no live GPS feed available for this operator/leg) */
    THEORETICAL_SCHEDULE,
    
    /** Active service disruption or accessibility issue reported on route lines/stations */
    SERVICE_ALERT,

    /** Calculating or awaiting cross-checked real-time data */
    CHECKING_REAL_TIME
}

/**
 * Clean domain model for a planned multimodal route alternative.
 */
data class PlannedItinerary(
    val id: String,
    val totalDurationSeconds: Long,
    val startTime: String, // ISO-8601 or formatted time
    val endTime: String,
    val recommendedStartTime: String, // Adjusted time to leave home based on real-time traffic
    val formattedDuration: String, // e.g. "24 min"
    val formattedDepartureTime: String, // e.g. "16:32"
    val formattedArrivalTime: String, // e.g. "16:56"
    val transfersCount: Int,
    val legs: List<PlannedLeg>,
    val viability: ItineraryViability,
    val viabilityNotice: String? = null,
    val activeAlerts: List<String> = emptyList(),
    val totalWalkDistanceMeters: Double = 0.0,
    val totalWalkDurationSeconds: Long = 0L,
    val allRoutePolyline: List<GeoPoint> = emptyList(),
    val alternativeConnections: List<AlternativeConnectingOption> = emptyList()
)

/**
 * Alternative connecting transit option for a transfer leg within tolerance.
 */
data class AlternativeConnectingOption(
    val lineName: String,
    val mode: TransitMode = TransitMode.BUS,
    val routeColorHex: String = "#DA291C",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val durationMinutes: Long = 0L,
    val deltaDurationMinutes: Int = 0,
    val fromStopName: String = "",
    val toStopName: String = "",
    val headsign: String? = null
)



/**
 * Stop along a transit leg.
 */
data class PlannedStop(
    val name: String,
    val stopId: String?,
    val lat: Double,
    val lon: Double,
    val scheduledTime: String? = null,
    val formattedTime: String? = null
)

/**
 * Result of the reconciliation between theoretical route and real-time live data.
 */
data class ReconciliationResult(
    val isReconciled: Boolean,
    val viability: ItineraryViability,
    val adjustedStartTime: String?,
    val notice: String?,
    val activeAlerts: List<String>
)
