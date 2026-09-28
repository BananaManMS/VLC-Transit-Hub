package com.example.data.network

import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.google.gson.annotations.SerializedName

data class NominatimAddressDto(
    @SerializedName("state_district") val stateDistrict: String? = null,
    @SerializedName("ISO3166-2-lvl6") val isoProvince: String? = null,
    val road: String? = null,
    val house_number: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val postcode: String? = null,
    val country: String? = null
)

data class NominatimResultDto(
    @SerializedName("place_id") val placeId: Long = 0,
    @SerializedName("display_name") val displayName: String = "",
    val lat: String = "0.0",
    val lon: String = "0.0",
    val type: String = "",
    @SerializedName("class") val clazz: String = "",
    val category: String = "",
    val address: NominatimAddressDto? = null
) {
    val latitude: Double get() = lat.toDoubleOrNull() ?: 0.0
    val longitude: Double get() = lon.toDoubleOrNull() ?: 0.0

    fun toNominatimResult(): NominatimResult {
        return NominatimResult(
            place_id = placeId,
            display_name = displayName,
            lat = lat,
            lon = lon,
            type = type,
            category = category.ifEmpty { clazz },
            placeName = displayName.split(",").firstOrNull()?.trim() ?: displayName,
            road = address?.road,
            houseNumber = address?.house_number,
            suburb = address?.suburb,
            city = address?.city ?: address?.town ?: address?.village,
            postcode = address?.postcode,
            displayName = displayName,
            isLocalStop = false
        )
    }
}
