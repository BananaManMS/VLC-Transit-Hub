package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.NominatimResult
import com.example.data.network.NetworkModule
import com.example.data.network.NominatimApiService
import com.example.util.LocationUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GeocodingRepository(
    private val context: Context? = null,
    private val database: AppDatabase? = null,
    private val apiServiceOverride: NominatimApiService? = null
) {
    constructor(context: Context) : this(context, AppDatabase.getDatabase(context), null)
    constructor(context: Context, database: AppDatabase) : this(context, database, null)

    private val api: NominatimApiService
        get() = apiServiceOverride ?: NetworkModule.nominatimApiService

    suspend fun searchAddress(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null
    ): Result<List<NominatimResult>> {
        return try {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isEmpty()) {
                return Result.success(emptyList())
            }

            // Query remote Nominatim OpenStreetMap API
            val dtoList = api.search(query = trimmedQuery)

            // Keep results within reasonable bounds of Comunitat Valenciana & Spain
            val filtered = dtoList.filter { item ->
                val lat = item.latitude
                val lon = item.longitude
                // Comunitat Valenciana and connecting rail corridors
                lat in 37.5..41.5 && lon in -2.5..1.5
            }.map { it.toNominatimResult() }

            // Proximity sorting if user coordinates are provided
            val sortedResults = if (userLat != null && userLon != null) {
                filtered.sortedBy { result ->
                    LocationUtils.calculateDistanceMeters(userLat, userLon, result.latitude, result.longitude)
                }
            } else {
                filtered
            }

            Result.success(sortedResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchPlaces(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null
    ): List<NominatimResult> {
        val result = searchAddress(query, userLat, userLon)
        return result.getOrDefault(emptyList())
    }

    suspend fun performSearch(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null
    ): List<NominatimResult> {
        return searchPlaces(query, userLat, userLon)
    }

    fun reverseGeocode(lat: Double, lon: Double): Flow<String?> = flow {
        try {
            val res = api.reverse(lat = lat, lon = lon)
            emit(res.displayName)
        } catch (e: Exception) {
            emit(null)
        }
    }

    fun reverseGeocodeDetails(lat: Double, lon: Double): Flow<NominatimResult?> = flow {
        try {
            val res = api.reverse(lat = lat, lon = lon)
            emit(res.toNominatimResult())
        } catch (e: Exception) {
            emit(null)
        }
    }
}
