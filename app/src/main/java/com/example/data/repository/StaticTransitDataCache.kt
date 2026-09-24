package com.example.data.repository

import android.content.Context
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.ui.bus.BusMapper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object StaticTransitDataCache {
    private var cachedEmtStops: List<GeoportalStopEntity>? = null
    private var cachedMetrobusStops: List<MetrobusStopEntity>? = null
    private var offsetsPrecomputed = false
    private val metroPositions = mutableMapOf<String, org.osmdroid.util.GeoPoint>()
    private val cercaniasPositions = mutableMapOf<String, org.osmdroid.util.GeoPoint>()

    fun isOffsetsReady(): Boolean = offsetsPrecomputed

    fun precomputeStationVisualOffsets(
        metro: List<Pair<String, org.osmdroid.util.GeoPoint>>,
        cercanias: List<Pair<String, org.osmdroid.util.GeoPoint>>
    ) {
        metroPositions.clear()
        metro.forEach { (name, pos) -> metroPositions[name] = pos }
        cercaniasPositions.clear()
        cercanias.forEach { (id, pos) -> cercaniasPositions[id] = pos }
        offsetsPrecomputed = true
    }

    fun getVisualPositionForMetro(name: String, fallback: org.osmdroid.util.GeoPoint): org.osmdroid.util.GeoPoint {
        return metroPositions[name] ?: fallback
    }

    fun getVisualPositionForCercanias(id: String, fallback: org.osmdroid.util.GeoPoint): org.osmdroid.util.GeoPoint {
        return cercaniasPositions[id] ?: fallback
    }

    fun initialize(context: Context) {
        getOrLoadEmtStops(context)
        getOrLoadMetrobusStops(context)
    }

    @Synchronized
    fun getOrLoadEmtStops(context: Context): List<GeoportalStopEntity> {
        cachedEmtStops?.let { return it }
        val stops = BusMapper.parseStopsFromJsonDirect(context)
        cachedEmtStops = stops
        return stops
    }

    @Synchronized
    fun getOrLoadMetrobusStops(context: Context): List<MetrobusStopEntity> {
        cachedMetrobusStops?.let { return it }
        val stops = parseMetrobusStops(context)
        cachedMetrobusStops = stops
        return stops
    }

    private fun parseMetrobusStops(context: Context): List<MetrobusStopEntity> {
        val list = mutableListOf<MetrobusStopEntity>()
        try {
            val jsonStr = try {
                val localFile = File(context.filesDir, "metrobus_stops.json")
                if (localFile.exists() && localFile.length() > 0) {
                    localFile.readText()
                } else {
                    context.assets.open("metrobus_stops.json").bufferedReader().use { it.readText() }
                }
            } catch (_: Exception) {
                null
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val stopsArray = root.optJSONArray("stops") ?: JSONArray()
                for (i in 0 until stopsArray.length()) {
                    val obj = stopsArray.optJSONObject(i) ?: continue
                    val id = obj.optString("stop_id", "").trim()
                    val name = obj.optString("stop_name", "").trim()
                    val lat = obj.optDouble("stop_lat", 0.0)
                    val lon = obj.optDouble("stop_lon", 0.0)
                    if (id.isNotBlank() && name.isNotBlank() && lat != 0.0 && lon != 0.0) {
                        val linesArr = obj.optJSONArray("lines")
                        val linesList = mutableListOf<String>()
                        if (linesArr != null) {
                            for (k in 0 until linesArr.length()) {
                                val lCode = linesArr.optString(k, "").trim()
                                if (lCode.isNotBlank()) linesList.add(lCode)
                            }
                        }
                        list.add(
                            MetrobusStopEntity(
                                id_parada = id,
                                denominacion = name,
                                lat = lat,
                                lon = lon,
                                lineas = linesList.joinToString(","),
                                suprimida = 0
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StaticTransitDataCache", "Error loading metrobus stops", e)
        }
        return list
    }
}
