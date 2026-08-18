package com.example.data.model.transitous

import com.google.gson.annotations.SerializedName

/**
 * Root response for Transitous/MOTIS 2 /api/v2/plan endpoint.
 */
data class TransitousPlanResponse(
    @SerializedName("itineraries")
    val itineraries: List<TransitousItineraryDto>? = null,
    
    @SerializedName("previousPageCursor")
    val previousPageCursor: String? = null,
    
    @SerializedName("nextPageCursor")
    val nextPageCursor: String? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("message")
    val message: String? = null
)

/**
 * Represents a single travel itinerary / alternative.
 */
data class TransitousItineraryDto(
    @SerializedName("duration")
    val duration: Long = 0L, // Duration in seconds
    
    @SerializedName("startTime")
    val startTime: String? = null, // ISO-8601 timestamp e.g. "2026-08-14T15:53:00Z"
    
    @SerializedName("endTime")
    val endTime: String? = null,
    
    @SerializedName("transfers")
    val transfers: Int = 0,
    
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("legs")
    val legs: List<TransitousLegDto> = emptyList()
)

/**
 * Represents a leg / segment within an itinerary.
 */
data class TransitousLegDto(
    @SerializedName("mode")
    val mode: String = "WALK", // "WALK", "BUS", "SUBWAY", "TRAM", "RAIL", "BICYCLE", etc.
    
    @SerializedName("duration")
    val duration: Long = 0L, // Duration in seconds
    
    @SerializedName("distance")
    val distance: Double? = null, // In meters
    
    @SerializedName("startTime")
    val startTime: String? = null,
    
    @SerializedName("endTime")
    val endTime: String? = null,
    
    @SerializedName("scheduledStartTime")
    val scheduledStartTime: String? = null,
    
    @SerializedName("scheduledEndTime")
    val scheduledEndTime: String? = null,
    
    @SerializedName("realTime")
    val realTime: Boolean = false,
    
    @SerializedName("scheduled")
    val scheduled: Boolean = true,
    
    @SerializedName("routeId")
    val routeId: String? = null,
    
    @SerializedName("routeShortName")
    val routeShortName: String? = null,
    
    @SerializedName("routeLongName")
    val routeLongName: String? = null,
    
    @SerializedName("displayName")
    val displayName: String? = null,
    
    @SerializedName("headsign")
    val headsign: String? = null,
    
    @SerializedName("agencyName")
    val agencyName: String? = null,
    
    @SerializedName("agencyUrl")
    val agencyUrl: String? = null,
    
    @SerializedName("routeColor")
    val routeColor: String? = null, // Hex color without # (e.g. "b7dd79", "E52320")
    
    @SerializedName("routeTextColor")
    val routeTextColor: String? = null,
    
    @SerializedName("routeType")
    val routeType: Int? = null,
    
    @SerializedName("from")
    val from: TransitousPlaceDto? = null,
    
    @SerializedName("to")
    val to: TransitousPlaceDto? = null,
    
    @SerializedName("intermediateStops")
    val intermediateStops: List<TransitousPlaceDto>? = null,
    
    @SerializedName("legGeometry")
    val legGeometry: TransitousLegGeometryDto? = null,
    
    @SerializedName("steps")
    val steps: List<TransitousStepDto>? = null
)

/**
 * Origin, destination, or intermediate stop in a leg.
 */
data class TransitousPlaceDto(
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("stopId")
    val stopId: String? = null, // e.g. "es-Metro-de-Valencia_71", "es-EMT-Valencia_1234"
    
    @SerializedName("lat")
    val lat: Double = 0.0,
    
    @SerializedName("lon")
    val lon: Double = 0.0,
    
    @SerializedName("departure")
    val departure: String? = null,
    
    @SerializedName("arrival")
    val arrival: String? = null,
    
    @SerializedName("scheduledDeparture")
    val scheduledDeparture: String? = null,
    
    @SerializedName("scheduledArrival")
    val scheduledArrival: String? = null,
    
    @SerializedName("vertexType")
    val vertexType: String? = null, // "NORMAL", "TRANSIT"
    
    @SerializedName("track")
    val track: String? = null, // Platform / Andén if available
    
    @SerializedName("modes")
    val modes: List<String>? = null
)

/**
 * Encoded polyline geometry for rendering the path on maps.
 */
data class TransitousLegGeometryDto(
    @SerializedName("points")
    val points: String = "",
    
    @SerializedName("precision")
    val precision: Int = 6, // MOTIS 2 defaults to precision 6
    
    @SerializedName("length")
    val length: Int? = null
)

/**
 * Turn-by-turn walking / transit step.
 */
data class TransitousStepDto(
    @SerializedName("relativeDirection")
    val relativeDirection: String? = null,
    
    @SerializedName("distance")
    val distance: Double? = null,
    
    @SerializedName("streetName")
    val streetName: String? = null,
    
    @SerializedName("polyline")
    val polyline: TransitousLegGeometryDto? = null
)
