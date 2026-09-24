package com.example.data.network

import com.example.data.model.transitous.TransitousResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface TransitousApiService {
    @GET("otp/routers/default/plan")
    suspend fun plan(
        @Query("fromPlace") fromPlace: String,
        @Query("toPlace") toPlace: String,
        @Query("time") time: String? = null,
        @Query("date") date: String? = null,
        @Query("arriveBy") arriveBy: Boolean? = null,
        @Query("maxTransfers") maxTransfers: Int? = null,
        @Query("modes") modes: String? = null,
        @Query("numItineraries") numItineraries: Int? = null,
        @Query("maxWalkDuration") maxWalkDuration: Int? = null,
        @Query("maxWalkDist") maxWalkDist: Int? = null
    ): TransitousResponseDto

    @GET("otp/routers/default/plan")
    suspend fun getPlan(
        @Query("fromPlace") fromPlace: String,
        @Query("toPlace") toPlace: String,
        @Query("date") date: String? = null,
        @Query("time") time: String? = null,
        @Query("mode") mode: String = "TRANSIT,WALK"
    ): TransitousResponseDto
}
