package com.example.data.network

import com.example.data.model.transitous.TransitousPlanResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TransitousApiService {

    /**
     * Plans multimodal routes using MOTIS 2 engine on Transitous.
     *
     * @param fromPlace Coordinates "lat,lon" or stop ID
     * @param toPlace Coordinates "lat,lon" or stop ID
     * @param time Desired time e.g. "16:30"
     * @param date Desired date e.g. "2026-08-14"
     * @param arriveBy false = leave at (default), true = arrive by
     * @param maxTransfers Maximum number of transfers allowed
     * @param modes Comma-separated list of allowed modes e.g. "WALK,BUS,SUBWAY,TRAM,RAIL"
     * @param pageCursor Cursor for previous/next itineraries
     */
    @GET("plan")
    suspend fun plan(
        @Query("fromPlace") fromPlace: String,
        @Query("toPlace") toPlace: String,
        @Query("time") time: String? = null,
        @Query("date") date: String? = null,
        @Query("arriveBy") arriveBy: Boolean? = null,
        @Query("maxTransfers") maxTransfers: Int? = null,
        @Query("mode") modes: String? = null,
        @Query("numItineraries") numItineraries: Int? = null,
        @Query("pageCursor") pageCursor: String? = null,
        @Query("max_walk_duration") maxWalkDuration: Int? = null,
        @Query("max_walk_dist") maxWalkDist: Int? = null
    ): TransitousPlanResponse
}
