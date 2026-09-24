package com.example.data.repository

import android.content.Context
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import org.json.JSONArray
import org.json.JSONObject

data class LineStationInfo(
    val id: String = "",
    val name: String = "",
    val stationName: String = name,
    val line: String = "",
    val zone: String = "A",
    val timeMinutes: Int = 0,
    val timeFormatted: String = "",
    val isCurrentStation: Boolean = false,
    val isOrigin: Boolean = false,
    val isDestination: Boolean = false,
    val order: Int = 0,
    val stops: List<LineStationInfo> = emptyList()
) : Comparable<LineStationInfo> {
    override fun compareTo(other: LineStationInfo): Int {
        return timeMinutes.compareTo(other.timeMinutes)
    }
}

class MetroRepository(private val context: Context) {

    @Volatile
    private var cachedStations: List<MetroStation>? = null

    @Volatile
    private var cachedLineMap: Map<String, List<LineStationInfo>>? = null

    fun loadMetroStations(forceReload: Boolean = false): List<MetroStation> {
        if (!forceReload && cachedStations != null) {
            return cachedStations!!
        }

        val list = mutableListOf<MetroStation>()
        try {
            val jsonString = context.assets.open("stations_coords.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val name = obj.optString("name", "").trim()
                val zone = obj.optString("zone", "A").trim()
                val lat = obj.optDouble("latitude", 0.0)
                val lon = obj.optDouble("longitude", 0.0)

                val linesList = mutableListOf<String>()
                val linesArr = obj.optJSONArray("lines")
                if (linesArr != null) {
                    for (k in 0 until linesArr.length()) {
                        val lineStr = linesArr.optString(k, "").replace("L", "", ignoreCase = true).trim()
                        if (lineStr.isNotBlank()) {
                            linesList.add(lineStr)
                        }
                    }
                }

                if (id.isNotBlank() && name.isNotBlank()) {
                    list.add(
                        MetroStation(
                            id = id,
                            name = name,
                            lines = linesList,
                            zone = zone,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MetroRepository", "Error loading stations_coords.json: ${e.message}")
        }

        if (list.isEmpty()) {
            list.addAll(ValenciaMetroData.mainMetroStations)
        }

        cachedStations = list
        return list
    }

    suspend fun loadMetroLineStations(): Map<String, List<LineStationInfo>> {
        return getLineStationsMap()
    }

    suspend fun getLineStationsMap(): Map<String, List<LineStationInfo>> {
        cachedLineMap?.let { return it }

        val map = mutableMapOf<String, List<LineStationInfo>>()
        try {
            val jsonString = context.assets.open("metro_line_stations.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val keys = root.keys()
            while (keys.hasNext()) {
                val rawLineKey = keys.next()
                val lineKey = rawLineKey.replace("L", "", ignoreCase = true).trim()
                val arr = root.optJSONArray(rawLineKey) ?: continue
                val stationList = mutableListOf<LineStationInfo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").trim()
                    val name = obj.optString("name", "").trim()
                    val zone = obj.optString("zone", "A").trim()
                    if (id.isNotBlank() && name.isNotBlank()) {
                        stationList.add(
                            LineStationInfo(
                                id = id,
                                name = name,
                                stationName = name,
                                line = lineKey,
                                zone = zone,
                                order = i
                            )
                        )
                    }
                }
                map[lineKey] = stationList
                map["L$lineKey"] = stationList
            }
        } catch (e: Exception) {
            android.util.Log.e("MetroRepository", "Error loading metro_line_stations.json: ${e.message}")
        }

        cachedLineMap = map
        return map
    }
}
