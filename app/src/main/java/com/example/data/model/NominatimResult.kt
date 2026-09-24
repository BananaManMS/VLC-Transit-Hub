package com.example.data.model

import com.example.data.model.PlaceCategory

data class NominatimResult(
    val place_id: Long = 0,
    val display_name: String = "",
    val lat: String = "0.0",
    val lon: String = "0.0",
    val type: String = "",
    val category: String = "",
    val placeName: String? = null,
    val road: String? = null,
    val houseNumber: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val openingHours: String? = null,
    val wheelchair: String? = null,
    val brand: String? = null,
    val operator: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val wikipedia: String? = null,
    val wikidata: String? = null,
    val fee: String? = null,
    val charge: String? = null,
    val startDate: String? = null,
    val historicType: String? = null,
    val colorHex: String? = null,
    val showOnMap: Boolean = true,
    val placeCategory: PlaceCategory = PlaceCategory.GENERAL,
    val displayName: String = display_name,
    val isLocalStop: Boolean = false,
    val stopId: String? = null,
    val stopType: String? = null
) {
    val latitude: Double get() = lat.toDoubleOrNull() ?: 0.0
    val longitude: Double get() = lon.toDoubleOrNull() ?: 0.0

    constructor(
        displayName: String,
        latitude: Double,
        longitude: Double,
        type: String = "",
        category: String = "",
        isLocalStop: Boolean = false,
        stopId: String? = null,
        stopType: String? = null
    ) : this(
        place_id = 0,
        display_name = displayName,
        lat = latitude.toString(),
        lon = longitude.toString(),
        type = type,
        category = category,
        placeName = displayName.split(",").firstOrNull()?.trim() ?: displayName,
        placeCategory = PlaceCategory.GENERAL,
        displayName = displayName,
        isLocalStop = isLocalStop,
        stopId = stopId,
        stopType = stopType
    )
}
