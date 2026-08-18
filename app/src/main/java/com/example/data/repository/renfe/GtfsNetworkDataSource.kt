package com.example.data.repository.renfe

import com.example.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class GtfsNetworkDataSource {

    suspend fun fetchVehiclePositionsJson(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://gtfsrt.renfe.com/vehicle_positions.json")
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchTripUpdatesJson(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://gtfsrt.renfe.com/trip_updates.json")
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}