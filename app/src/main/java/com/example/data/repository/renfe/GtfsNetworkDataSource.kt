package com.example.data.repository.renfe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GtfsNetworkDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchGtfsData(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://valencia-gtfs.renfe.com/gtfs-rt-valencia.pb")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchTripUpdates(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://valencia-gtfs.renfe.com/gtfs-rt-tripupdates.pb")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
