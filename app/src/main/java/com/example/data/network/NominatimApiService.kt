package com.example.data.network

import com.example.data.model.NominatimResult
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApiService {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("viewbox") viewbox: String = "-1.5,40.5,0.6,38.5",
        @Query("bounded") bounded: Int = 0,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("extratags") extraTags: Int = 1,
        @Query("namedetails") nameDetails: Int = 1,
        @Query("countrycodes") countryCodes: String = "es",
        @Query("limit") limit: Int = 10,
        @Query("accept-language") acceptLanguage: String = "es,ca,en"
    ): List<NominatimResultDto>

    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 10,
        @Query("viewbox") viewbox: String = "-1.5,40.5,0.6,38.5",
        @Query("bounded") bounded: Int = 0
    ): List<NominatimResult> = search(query = query, viewbox = viewbox, bounded = bounded, limit = limit, format = format).map { it.toNominatimResult() }

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("extratags") extraTags: Int = 1,
        @Query("namedetails") nameDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "es,ca,en"
    ): NominatimResultDto

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json"
    ): NominatimResult = reverse(lat = lat, lon = lon, format = format).toNominatimResult()
}
