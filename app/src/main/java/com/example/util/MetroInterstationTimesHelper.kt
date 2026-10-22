package com.example.util

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Utility helper that loads and queries scheduled inter-station travel times
 * from `assets/metro_interstation_times.json`.
 */
object MetroInterstationTimesHelper {
    private const val TAG = "MetroInterstationTimes"
    private const val ASSET_NAME = "metro_interstation_times.json"

    // Map of Pair(fromStationId, toStationId) -> travelTimeMinutes
    @Volatile
    private var hopTimesMap: Map<Pair<String, String>, Int>? = null

    /**
     * Ensures inter-station travel times are loaded into memory from assets.
     */
    fun ensureLoaded(context: Context) {
        if (hopTimesMap != null) return
        synchronized(this) {
            if (hopTimesMap != null) return
            val map = mutableMapOf<Pair<String, String>, Int>()
            try {
                context.assets.open(ASSET_NAME).use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(jsonString)

                    // Priority 1: Read exact directional hop map saltos_directos
                    val saltosObj = root.optJSONObject("saltos_directos")
                    if (saltosObj != null) {
                        val keys = saltosObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val parts = key.split("-")
                            if (parts.size == 2) {
                                val idA = parts[0].trim()
                                val idB = parts[1].trim()
                                val min = saltosObj.getInt(key)
                                map[Pair(idA, idB)] = min
                            }
                        }
                    }

                    // Priority 2: Fallback to segment tramos
                    val tramosArr = root.optJSONArray("tramos")
                    if (tramosArr != null) {
                        for (i in 0 until tramosArr.length()) {
                            val tramoObj = tramosArr.getJSONObject(i)
                            val nodosArr = tramoObj.optJSONArray("nodos")
                            val tiemposArr = tramoObj.optJSONArray("tiempos")
                            if (nodosArr != null && tiemposArr != null) {
                                for (j in 0 until minOf(nodosArr.length() - 1, tiemposArr.length())) {
                                    val idA = nodosArr.get(j).toString().trim()
                                    val idB = nodosArr.get(j + 1).toString().trim()
                                    val minutes = tiemposArr.getInt(j)
                                    if (idA.isNotEmpty() && idB.isNotEmpty()) {
                                        map.putIfAbsent(Pair(idA, idB), minutes)
                                        map.putIfAbsent(Pair(idB, idA), minutes)
                                    }
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "Loaded ${map.size} directional inter-station hop times from $ASSET_NAME")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $ASSET_NAME: ${e.message}", e)
            }
            hopTimesMap = map
        }
    }

    /**
     * Gets travel time in minutes between two adjacent stations.
     * Returns 2 min fallback if not found in table.
     */
    fun getHopTime(context: Context, fromStationId: String, toStationId: String): Int {
        ensureLoaded(context)
        val idA = fromStationId.trim()
        val idB = toStationId.trim()
        return hopTimesMap?.get(Pair(idA, idB))
            ?: hopTimesMap?.get(Pair(idB, idA))
            ?: 2
    }
}
