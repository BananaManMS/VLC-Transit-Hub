package com.example.data.network

import com.example.data.model.transitous.TransitousResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface TransitousApiService {
    @GET("plan")
    suspend fun plan(
        @Query("fromPlace") fromPlace: String,
        @Query("toPlace") toPlace: String,
        @Query("time") time: String? = null,
        @Query("date") date: String? = null,
        @Query("arriveBy") arriveBy: Boolean? = null,
        @Query("maxTransfers") maxTransfers: Int? = null,
        @Query("modes") modes: String? = null,
        @Query("numItineraries") numItineraries: Int? = null,
        @Query("pageCursor") pageCursor: String? = null,
        @Query("maxWalkDuration") maxWalkDuration: Int? = null,
        @Query("maxWalkDist") maxWalkDist: Int? = null
    ): TransitousResponseDto
}

suspend fun TransitousApiService.getPlan(
    fromPlace: String,
    toPlace: String,
    date: String? = null,
    time: String? = null,
    mode: String = "TRANSIT,WALK"
): TransitousResponseDto = plan(fromPlace = fromPlace, toPlace = toPlace, date = date, time = time, modes = mode)


