package com.example.data.repository

import android.content.Context
import com.example.data.model.MetroStation
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

data class LineStationInfo(
    val id: String,
    val name: String,
    val zone: String
)

class MetroRepository(private val context: Context) {

    private var cachedStations: List<MetroStation>? = null
    private var cachedLineStations: Map<String, List<LineStationInfo>>? = null

    suspend fun loadMetroLineStations(): Map<String, List<LineStationInfo>> = withContext(Dispatchers.IO) {
        if (cachedLineStations != null) return@withContext cachedLineStations!!
        val resultMap = mutableMapOf<String, List<LineStationInfo>>()
        try {
            val jsonString = context.assets.open("metro_line_stations.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val lineKey = keys.next()
                val arr = jsonObject.getJSONArray(lineKey)
                val list = mutableListOf<LineStationInfo>()
                for (i in 0 until arr.length()) {
                    val stObj = arr.getJSONObject(i)
                    list.add(
                        LineStationInfo(
                            id = stObj.optString("id", ""),
                            name = stObj.optString("name", ""),
                            zone = stObj.optString("zone", "A")
                        )
                    )
                }
                resultMap[lineKey] = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedLineStations = resultMap
        resultMap
    }

    suspend fun loadMetroStations(forceReload: Boolean = false): List<MetroStation> = withContext(Dispatchers.IO) {
        if (!forceReload && cachedStations != null) {
            return@withContext cachedStations!!
        }
        val lineStationsMap = loadMetroLineStations()
        val stLinesMap = mutableMapOf<String, MutableSet<String>>()
        val stZonesMap = mutableMapOf<String, String>()

        for ((lineId, stList) in lineStationsMap) {
            for (st in stList) {
                val cleanStZone = com.example.data.model.cleanZoneCode(st.zone)
                if (st.id.isNotBlank()) {
                    stLinesMap.getOrPut("id:${st.id}") { mutableSetOf() }.add(lineId)
                    val existing = stZonesMap["id:${st.id}"]
                    stZonesMap["id:${st.id}"] = combineZoneStrings(existing, cleanStZone)
                }
                if (st.name.isNotBlank()) {
                    val keyName = "name:${st.name.lowercase().trim()}"
                    stLinesMap.getOrPut(keyName) { mutableSetOf() }.add(lineId)
                    val existing = stZonesMap[keyName]
                    stZonesMap[keyName] = combineZoneStrings(existing, cleanStZone)
                }
            }
        }

        val resultList = mutableListOf<MetroStation>()
        try {
            val jsonString = context.assets.open("stations_coords.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val coordsZone = obj.optString("zone", "A")
                val lat = obj.optDouble("latitude", 39.4697)
                val lon = obj.optDouble("longitude", -0.3734)
                
                val linesArray = obj.optJSONArray("lines")
                val lines = mutableListOf<String>()
                if (linesArray != null) {
                    for (j in 0 until linesArray.length()) {
                        lines.add(linesArray.getString(j))
                    }
                }

                val computedLines = mutableSetOf<String>()
                stLinesMap["id:$id"]?.let { computedLines.addAll(it) }
                stLinesMap["name:${name.lowercase().trim()}"]?.let { computedLines.addAll(it) }

                val finalLines = if (computedLines.isNotEmpty()) {
                    computedLines.sortedBy { l -> l.replace("L", "").toIntOrNull() ?: 99 }
                } else {
                    lines.ifEmpty { listOf("L3") }
                }

                val mappedZone = stZonesMap["id:$id"] ?: stZonesMap["name:${name.lowercase().trim()}"] ?: coordsZone
                val finalZone = com.example.data.model.cleanZoneCode(mappedZone)
                
                if (id.isNotBlank() && name.isNotBlank()) {
                    resultList.add(
                        MetroStation(
                            id = id,
                            name = name,
                            lines = finalLines,
                            description = "Zona $finalZone",
                            latitude = lat,
                            longitude = lon,
                            zone = finalZone
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedStations = resultList
        resultList
    }

    private fun combineZoneStrings(existing: String?, newZone: String): String {
        if (existing.isNullOrBlank()) return newZone
        if (newZone.isBlank() || existing == newZone) return existing
        if (existing.contains(newZone)) return existing
        if (newZone.contains(existing)) return newZone
        val combined = (existing + newZone).toCharArray().distinct().sorted().joinToString("")
        return com.example.data.model.cleanZoneCode(combined)
    }

    fun generateStationSlug(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutAccents.lowercase().replace(" ", "_").replace("'", "").replace(".", "").replace("-", "_")
    }
}
