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

            val localMatches = try {
                val db = database
                if (db != null) {
                    db.geoportalStopDao().searchActiveStops(trimmedQuery).map { stop ->
                        NominatimResult(
                            displayName = stop.denominacion,
                            latitude = stop.lat,
                            longitude = stop.lon,
                            type = "stop",
                            category = "highway",
                            isLocalStop = true,
                            stopId = stop.id_parada,
                            stopType = "EMT"
                        )
                    }
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            // Query remote Nominatim OpenStreetMap API
            val dtoList = try {
                api.search(query = trimmedQuery)
            } catch (_: Exception) {
                emptyList()
            }

            // Keep results within reasonable bounds of Valencia province & Spain (excluding other provinces)
            val filtered = dtoList.filter { item ->
                val lat = item.latitude
                val lon = item.longitude
                val display = item.displayName ?: ""
                // Valencia province bounds, and explicitly exclude external provinces
                lat in 38.7..40.2 && lon in -1.45..0.35 &&
                    !display.contains("Albacete", ignoreCase = true) &&
                    !display.contains("Alpera", ignoreCase = true) &&
                    !display.contains("Alicante", ignoreCase = true) &&
                    !display.contains("Castellón", ignoreCase = true) &&
                    !display.contains("Castelló", ignoreCase = true)
            }.map { it.toNominatimResult() }

            // Proximity sorting if user coordinates are provided
            val sortedResults = if (userLat != null && userLon != null) {
                filtered.sortedBy { result ->
                    LocationUtils.calculateDistanceMeters(userLat, userLon, result.latitude, result.longitude)
                }
            } else {
                filtered
            }

            val combined = (localMatches + sortedResults).distinctBy { "${it.displayName}_${it.latitude}_${it.longitude}" }
            Result.success(combined)
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
