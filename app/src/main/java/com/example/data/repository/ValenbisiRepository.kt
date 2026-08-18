package com.example.data.repository

import android.util.Log
import com.example.data.network.NetworkModule
import com.example.ui.map.components.ValenbisiStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ValenbisiRepository(private val client: OkHttpClient = NetworkModule.okHttpClient) {

    private var lastFetchTime = 0L
    private var cachedStations: List<ValenbisiStation> = emptyList()

    suspend fun fetchStations(): List<ValenbisiStation> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        synchronized(this@ValenbisiRepository) {
            if (cachedStations.isNotEmpty() && (currentTime - lastFetchTime) < 10 * 60 * 1000) {
                Log.d("ValenbisiRepository", "Returning cached Valenbisi stations (${(currentTime - lastFetchTime) / 1000}s since last fetch)")
                return@withContext cachedStations
            }
        }

        try {
            val url = "https://geoportal.valencia.es/server/rest/services/OPENDATA/Trafico/MapServer/228/query?where=1=1&outFields=*&f=json&outSR=4326"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            val fetchedList = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ValenbisiRepository", "HTTP Error fetching Valenbisi: ${response.code}")
                    return@withContext synchronized(this@ValenbisiRepository) { cachedStations }
                }

                val body = response.body?.string() ?: return@withContext synchronized(this@ValenbisiRepository) { cachedStations }
                val json = JSONObject(body)
                val features = json.optJSONArray("features") ?: return@withContext synchronized(this@ValenbisiRepository) { cachedStations }
                val stationsList = mutableListOf<ValenbisiStation>()

                for (i in 0 until features.length()) {
                    val feature = features.optJSONObject(i) ?: continue
                    val attributes = feature.optJSONObject("attributes") ?: continue
                    val geometry = feature.optJSONObject("geometry") ?: continue

                    val gid = attributes.optInt("gid", i)
                    val rawName = attributes.optString("name", "Estación $i")
                    val nameWithoutNumbers = rawName.replaceFirst(Regex("^\\d+\\s*[\\-_]?\\s*"), "")
                    val name = nameWithoutNumbers.replace("_", " ").trim()
                    val number = attributes.optInt("number", 0)
                    val address = attributes.optString("address", "")
                    val open = attributes.optString("open", "T") == "T"
                    val available = attributes.optInt("available", 0)
                    val free = attributes.optInt("free", 0)
                    val total = attributes.optInt("total", 0)
                    val ticket = attributes.optString("ticket", "F") == "T"

                    val longitude = geometry.optDouble("x", 0.0)
                    val latitude = geometry.optDouble("y", 0.0)

                    if (latitude != 0.0 && longitude != 0.0) {
                        stationsList.add(
                            ValenbisiStation(
                                gid = gid,
                                name = name,
                                number = number,
                                address = address,
                                open = open,
                                available = available,
                                free = free,
                                total = total,
                                ticket = ticket,
                                latitude = latitude,
                                longitude = longitude
                            )
                        )
                    }
                }
                stationsList
            }

            if (fetchedList.isNotEmpty()) {
                synchronized(this@ValenbisiRepository) {
                    cachedStations = fetchedList
                    lastFetchTime = System.currentTimeMillis()
                }
            }
            fetchedList
        } catch (e: Exception) {
            Log.e("ValenbisiRepository", "Error fetching Valenbisi stations", e)
            synchronized(this@ValenbisiRepository) { cachedStations }
        }
    }
}
