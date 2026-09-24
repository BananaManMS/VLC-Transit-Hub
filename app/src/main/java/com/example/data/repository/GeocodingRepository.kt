package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.NominatimResult
import com.example.data.network.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GeocodingRepository(
    private val context: Context? = null,
    private val database: AppDatabase? = null
) {
    constructor(context: Context) : this(context, AppDatabase.getDatabase(context))

    private val api = NetworkModule.nominatimApiService

    suspend fun searchPlaces(query: String): List<NominatimResult> {
        return try {
            api.searchPlaces(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun performSearch(query: String): List<NominatimResult> {
        return searchPlaces(query)
    }

    fun reverseGeocode(lat: Double, lon: Double): Flow<String?> = flow {
        emit(null)
    }

    fun reverseGeocodeDetails(lat: Double, lon: Double): Flow<NominatimResult?> = flow {
        emit(null)
    }
}
