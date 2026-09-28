package com.example.data.model.transitous

data class TransitousPolylineDto(
    val points: String? = null,
    val precision: Int? = 6
)

data class TransitousStepDto(
    val polyline: TransitousPolylineDto? = null
)

data class TransitousStopDto(
    val name: String? = null,
    val id: String? = null,
    val stopId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val departure: String? = null,
    val arrival: String? = null,
    val scheduledDeparture: String? = null,
    val scheduledArrival: String? = null
)

data class TransitousLegDto(
    val mode: String? = null,
    val agencyName: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val displayName: String? = null,
    val headsign: String? = null,
    val routeColor: String? = null,
    val from: TransitousStopDto? = null,
    val to: TransitousStopDto? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: Long? = null,
    val distance: Double? = null,
    val intermediateStops: List<TransitousStopDto>? = null,
    val legGeometry: TransitousPolylineDto? = null,
    val polyline: String? = null,
    val steps: List<TransitousStepDto>? = null
)

data class TransitousItineraryDto(
    val id: String? = null,
    val legs: List<TransitousLegDto> = emptyList(),
    val duration: Long = 0L,
    val transfers: Int = 0,
    val startTime: String? = null,
    val endTime: String? = null
)

data class TransitousPlanDto(
    val itineraries: List<TransitousItineraryDto>? = null
)

data class TransitousResponseDto(
    val plan: TransitousPlanDto? = null,
    val itineraries: List<TransitousItineraryDto>? = null,
    val message: String? = null,
    val error: String? = null
) {
    val effectiveItineraries: List<TransitousItineraryDto>
        get() = itineraries ?: plan?.itineraries ?: emptyList()
}

typealias TransitousPlanResponse = TransitousResponseDto
typealias TransitousPlaceDto = TransitousStopDto


