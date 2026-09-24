package com.example.data.repository

import android.content.Context
import com.example.ui.map.components.ValenbisiStation
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class ValenbisiRepository {
    private val client: OkHttpClient
    private val context: Context?

    constructor(context: Context) {
        this.context = context
        this.client = com.example.data.network.NetworkModule.okHttpClient
    }

    constructor(client: OkHttpClient) {
        this.context = null
        this.client = client
    }

    constructor(context: Context, client: OkHttpClient) {
        this.context = context
        this.client = client
    }

    suspend fun fetchStations(force: Boolean = false): List<ValenbisiStation> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ValenbisiStation>()
        try {
            // Fetch real Valenbisi live station data from Valencia Open Data API
            val url = "https://valencia.opendatasoft.com/api/explore/v2.1/catalog/datasets/valenbisi-disponibilitat-valenbisi-dsponibilidad/records?limit=100"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val records = root.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until records.length()) {
                        val rec = records.optJSONObject(i) ?: continue
                        val number = rec.optInt("number", 0)
                            .let { if (it == 0) rec.optInt("numstation", 0) else it }
                            .let { if (it == 0) rec.optInt("gid", 0) else it }

                        var name = rec.optString("name", "")
                        var address = rec.optString("address", "")
                        if (name.isBlank() && address.isNotBlank()) name = address
                        if (address.isBlank() && name.isNotBlank()) address = name
                        if (name.isBlank()) name = "Estación Valenbisi $number"

                        val status = rec.optString("status", "OPEN")
                        val available = rec.optInt("available", rec.optInt("available_bikes", rec.optInt("bikes", 0)))
                        val free = rec.optInt("free", rec.optInt("available_slots", rec.optInt("slots", 0)))
                        val total = rec.optInt("total", rec.optInt("capacity", available + free))
                        val ticket = rec.optInt("ticket", 0) == 1 || rec.optBoolean("ticket", false)

                        var lat = rec.optDouble("latitude", 0.0)
                        var lon = rec.optDouble("longitude", 0.0)

                        if (lat == 0.0 || lon == 0.0) {
                            val geoPointObj = rec.optJSONObject("geo_point_2d")
                            if (geoPointObj != null) {
                                if (lat == 0.0) lat = geoPointObj.optDouble("lat", 0.0)
                                if (lon == 0.0) lon = geoPointObj.optDouble("lon", 0.0)
                            } else {
                                val geoPointArr = rec.optJSONArray("geo_point_2d")
                                if (geoPointArr != null && geoPointArr.length() >= 2) {
                                    if (lat == 0.0) lat = geoPointArr.optDouble(0, 0.0)
                                    if (lon == 0.0) lon = geoPointArr.optDouble(1, 0.0)
                                }
                            }
                        }

                        list.add(
                            ValenbisiStation(
                                gid = number,
                                name = name,
                                number = number,
                                address = address,
                                open = status.equals("OPEN", ignoreCase = true) || status.equals("ACTIVO", ignoreCase = true),
                                available = available,
                                free = free,
                                total = total,
                                ticket = ticket,
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            Log.e("ValenbisiRepository", "Error fetching Valenbisi stations", e)
        }
        
        // If empty, generate fallback dummy stations in Valencia center to keep map usable
        if (list.isEmpty()) {
            val centerLat = 39.46975
            val centerLon = -0.37639
            for (i in 1..20) {
                list.add(
                    ValenbisiStation(
                        gid = i,
                        name = "Valenbisi Estación $i",
                        number = i,
                        address = "Calle de Valencia, $i",
                        open = true,
                        available = (3..15).random(),
                        free = (2..12).random(),
                        total = 20,
                        ticket = i % 3 == 0,
                        latitude = centerLat + (i * 0.0015 - 0.015),
                        longitude = centerLon + (i * 0.0015 - 0.015) * 0.8
                    )
                )
            }
        }
        if (list.isNotEmpty()) {
            cachedStationsList = list
        }
        list
    }

    suspend fun getStations(): List<String> {
        return fetchStations().map { it.name }
    }

    fun getCachedStations(): List<ValenbisiStation> {
        return cachedStationsList
    }

    companion object {
        @Volatile
        private var cachedStationsList: List<ValenbisiStation> = emptyList()

        fun getCachedStations(): List<ValenbisiStation> {
            return cachedStationsList
        }
    }
}
