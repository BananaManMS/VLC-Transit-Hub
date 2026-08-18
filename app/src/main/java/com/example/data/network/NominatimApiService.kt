package com.example.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApiService {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("viewbox") viewbox: String,
        @Query("bounded") bounded: Int = 1,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("countrycodes") countryCodes: String = "es",
        @Query("limit") limit: Int = 10,
        @Query("accept-language") acceptLanguage: String = "es,ca"
    ): List<NominatimResultDto>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "es,ca"
    ): NominatimResultDto
}

data class NominatimAddressDto(
    @SerializedName("province") val province: String? = null,
    @SerializedName("state_district") val stateDistrict: String? = null,
    @SerializedName("county") val county: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("ISO3166-2-lvl6") val isoProvince: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("town") val town: String? = null,
    @SerializedName("village") val village: String? = null,
    @SerializedName("postcode") val postcode: String? = null,
    @SerializedName("road") val road: String? = null,
    @SerializedName("house_number") val houseNumber: String? = null,
    @SerializedName("suburb") val suburb: String? = null,
    @SerializedName("neighbourhood") val neighbourhood: String? = null,
    @SerializedName("amenity") val amenity: String? = null
)

data class NominatimResultDto(
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("lat") val lat: String?,
    @SerializedName("lon") val lon: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("class") val clazz: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("address") val address: NominatimAddressDto? = null
)

