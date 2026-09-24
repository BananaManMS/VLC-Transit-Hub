package com.example.data.repository

import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.MetrobusStopEntity
import com.example.data.database.PreferenceEntity
import com.example.data.network.NetworkModule
import com.example.ui.bus.MetrobusDepartureUiModel
import com.example.ui.bus.MetrobusLineInfo
import com.example.ui.bus.MetrobusShapeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.content.Context
import com.example.data.repository.metrobus.MetrobusDataSyncManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class MetrobusRepository(
    private val database: AppDatabase,
    private val client: OkHttpClient = NetworkModule.okHttpClient,
    private val context: Context? = null
) {

    private var cachedLinesMap: Map<String, String>? = null
    private var cachedLinesInfo: List<MetrobusLineInfo>? = null
    private val shapeCache = ConcurrentHashMap<String, Map<String, String>>()
    private var cachedShapesIndex: JSONObject? = null

    @Volatile
    private var isStopsCacheValid: Boolean = false

    companion object {
        private const val METROBUS_API_BASE = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/metrobus"
        private const val API_SECRET = "ApiVLCTH2584"

        private const val LINES_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_lines.json"
        private const val STOPS_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_stops.json"
        private const val SHAPES_INDEX_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_shapes.json"
        private const val SHAPE_BASE_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/shapes"

        private const val KEY_STOPS_ETAG = "metrobus_stops_etag"
        private const val KEY_LINES_ETAG = "metrobus_lines_etag"
        private const val KEY_LINES_JSON = "metrobus_lines_json"
    }

    suspend fun getLinesMap(forceRefresh: Boolean = false): Map<String, String> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedLinesMap != null && cachedLinesMap!!.isNotEmpty()) {
            return@withContext cachedLinesMap!!
        }
        val storedJson = database.preferenceDao().getPreference(KEY_LINES_JSON)?.value
        if (!storedJson.isNullOrBlank()) {
            val parsed = parseLinesMap(storedJson)
            if (parsed.isNotEmpty()) {
                cachedLinesMap = parsed
                return@withContext parsed
            }
        }
        syncLines()
        cachedLinesMap ?: emptyMap()
    }

    suspend fun getLinesInfo(): List<MetrobusLineInfo> = withContext(Dispatchers.IO) {
        if (cachedLinesInfo != null && cachedLinesInfo!!.isNotEmpty()) {
            return@withContext cachedLinesInfo!!
        }
        val storedJson = database.preferenceDao().getPreference(KEY_LINES_JSON)?.value
        if (!storedJson.isNullOrBlank()) {
            val parsedInfo = parseLinesInfoList(storedJson)
            if (parsedInfo.isNotEmpty()) {
                cachedLinesInfo = parsedInfo
                return@withContext parsedInfo
            }
        }
        syncLines()
        cachedLinesInfo ?: emptyList()
    }

    private suspend fun syncLines(): Boolean = withContext(Dispatchers.IO) {
        try {
            val storedEtag = database.preferenceDao().getPreference(KEY_LINES_ETAG)?.value ?: ""
            val requestBuilder = Request.Builder().url(LINES_URL)
            if (storedEtag.isNotBlank()) {
                requestBuilder.header("If-None-Match", storedEtag)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.code == 304) {
                Log.d("MetrobusRepository", "Lines JSON not modified (304)")
                response.close()
                return@withContext true
            }

            if (response.isSuccessful) {
                val newEtag = response.header("ETag") ?: ""
                val bodyStr = response.body?.string() ?: ""
                response.close()

                if (bodyStr.isNotBlank()) {
                    database.preferenceDao().insertPreference(PreferenceEntity(KEY_LINES_JSON, bodyStr))
                    if (newEtag.isNotBlank()) {
                        database.preferenceDao().insertPreference(PreferenceEntity(KEY_LINES_ETAG, newEtag))
                    }
                    cachedLinesMap = parseLinesMap(bodyStr)
                    cachedLinesInfo = parseLinesInfoList(bodyStr)
                    return@withContext true
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error syncing lines JSON from GitHub", e)
        }
        false
    }

    suspend fun ensureStopsCached(forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!forceRefresh && isStopsCacheValid) {
            return@withContext true
        }
        val count = database.metrobusStopDao().getStopCount()
        if (count == 0 || forceRefresh) {
            val success = syncStops(forceRefresh = forceRefresh)
            if (success) {
                isStopsCacheValid = true
            }
            return@withContext success
        }
        isStopsCacheValid = true
        true
    }

    suspend fun syncStops(forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            syncLines()

            val storedEtag = if (forceRefresh) "" else (database.preferenceDao().getPreference(KEY_STOPS_ETAG)?.value ?: "")
            val requestBuilder = Request.Builder().url(STOPS_URL)
            if (storedEtag.isNotBlank()) {
                requestBuilder.header("If-None-Match", storedEtag)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.code == 304) {
                Log.d("MetrobusRepository", "Stops JSON not modified (304)")
                response.close()
                return@withContext true
            }

            if (response.isSuccessful) {
                val newEtag = response.header("ETag") ?: ""
                val bodyStr = response.body?.string() ?: ""
                response.close()

                if (bodyStr.isNotBlank()) {
                    val stopsEntities = parseStopsJson(bodyStr)
                    if (stopsEntities.isNotEmpty()) {
                        database.metrobusStopDao().insertAll(stopsEntities)
                        if (newEtag.isNotBlank()) {
                            database.preferenceDao().insertPreference(PreferenceEntity(KEY_STOPS_ETAG, newEtag))
                        }
                        Log.d("MetrobusRepository", "Successfully synced ${stopsEntities.size} Metrobus stops")
                        return@withContext true
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error syncing stops from GitHub", e)
        }
        false
    }

    suspend fun fetchRealTimeEstimations(stopId: String): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
        val cleanStopId = stopId.trim()
        val url = "$METROBUS_API_BASE/estimacion/$cleanStopId"
        val request = Request.Builder()
            .url(url)
            .header("X-App-Secret", API_SECRET)
            .build()

        val results = mutableListOf<MetrobusDepartureUiModel>()
        val linesMap = getLinesMap()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                response.close()

                if (bodyStr.isNotBlank()) {
                    val root = JSONObject(bodyStr)
                    if (root.optBoolean("success", false)) {
                        val dataArray = root.optJSONArray("data") ?: JSONArray()
                        for (i in 0 until dataArray.length()) {
                            val lineGroup = dataArray.optJSONObject(i) ?: continue
                            val lineCode = lineGroup.optString("line", "").ifBlank { "MB" }
                            val destination = lineGroup.optString("route", "Metrobús")
                            val estimations = lineGroup.optJSONArray("estimations") ?: JSONArray()

                            val lineName = linesMap[lineCode] ?: linesMap["L$lineCode"]

                            for (j in 0 until estimations.length()) {
                                val estObj = estimations.optJSONObject(j) ?: continue
                                val mins = estObj.optInt("minutesToArrival", -1)
                                val ocupacion = estObj.optString("ocupacion", "SIN DATOS")
                                val vehicleId = if (estObj.isNull("vehicleId")) null else estObj.optInt("vehicleId")

                                var lat: Double? = null
                                var lon: Double? = null
                                var tripId: String? = null

                                val almex = estObj.optJSONObject("almex")
                                if (almex != null) {
                                    lat = almex.optString("latitude", "").toDoubleOrNull()
                                    lon = almex.optString("longitude", "").toDoubleOrNull()
                                    tripId = almex.optString("trip-id", null)
                                }

                                val timeLabel = when {
                                    mins <= 0 -> "Ahora"
                                    mins < 60 -> "$mins min"
                                    else -> "${mins / 60}h ${mins % 60}m"
                                }

                                results.add(
                                    MetrobusDepartureUiModel(
                                        lineCode = lineCode,
                                        destination = destination,
                                        departureTime = "",
                                        minutesRemaining = mins,
                                        timeLabel = timeLabel,
                                        agencyName = "Metrobús",
                                        routeColor = "#D97706",
                                        lineName = lineName,
                                        ocupacion = ocupacion,
                                        vehicleId = vehicleId,
                                        isRealTime = true,
                                        latitude = lat,
                                        longitude = lon,
                                        tripId = tripId
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error fetching real-time estimations for stop $stopId", e)
        }

        results.sortedBy { if (it.minutesRemaining < 0) 999 else it.minutesRemaining }
    }

    suspend fun getMetrobusArrivals(
        stopId: String,
        stopName: String = "",
        limitPerLine: Int = 3,
        includeScheduled: Boolean = false
    ): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
        val realTime = fetchRealTimeEstimations(stopId)
        val linesMap = getLinesMap()

        if (!includeScheduled) {
            return@withContext realTime
        }

        val scheduled = com.example.data.repository.metrobus.MetrobusScheduledRepository.fetchScheduledDepartures(
            stopId = stopId,
            limitPerLine = limitPerLine,
            linesMap = linesMap,
            client = client
        )

        if (realTime.isEmpty()) {
            return@withContext scheduled
        }

        val combined = realTime.toMutableList()
        val maxLiveMinsByLine = realTime.groupBy { "${it.lineCode}__${it.destination}" }
            .mapValues { entry -> entry.value.maxOf { it.minutesRemaining } }

        scheduled.forEach { sched ->
            val key = "${sched.lineCode}__${sched.destination}"
            val maxLive = maxLiveMinsByLine[key]
            if (maxLive == null || sched.minutesRemaining > (maxLive + 3)) {
                combined.add(sched)
            }
        }

        combined.sortedBy { if (it.minutesRemaining < 0) 999 else it.minutesRemaining }
    }

    suspend fun getMetrobusScheduledDepartures(
        stopId: String,
        limitPerLine: Int = 5
    ): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
        val cleanStop = stopId.trim()
        if (cleanStop.isBlank()) return@withContext emptyList()
        val linesMap = getLinesMap()
        com.example.data.repository.metrobus.MetrobusScheduledRepository.fetchScheduledDepartures(
            stopId = cleanStop,
            limitPerLine = limitPerLine,
            linesMap = linesMap,
            client = client
        )
    }

    suspend fun fetchLineShape(lineCode: String): Map<String, String> = withContext(Dispatchers.IO) {
        val cleanCode = lineCode.trim()
        val normalizedCode = if (cleanCode.startsWith("L", ignoreCase = true) && cleanCode.length > 1 && cleanCode[1].isDigit()) {
            cleanCode.substring(1)
        } else {
            cleanCode
        }
        val lCode = if (normalizedCode.startsWith("L", ignoreCase = true)) normalizedCode else "L$normalizedCode"

        val candidates = listOf(cleanCode, normalizedCode, lCode).distinct()

        // 1. Check in-memory cache
        for (candidate in candidates) {
            if (shapeCache.containsKey(candidate)) {
                return@withContext shapeCache[candidate]!!
            }
        }

        // 2. Fast resolution from consolidated shapes index (local storage or synced index)
        val index = getShapesIndex()
        if (index != null) {
            for (candidate in candidates) {
                if (index.has(candidate)) {
                    val lineShapeObj = index.optJSONObject(candidate)
                    if (lineShapeObj != null) {
                        val map = mutableMapOf<String, String>()
                        val keys = lineShapeObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = lineShapeObj.optString(key, "")
                        }
                        if (map.isNotEmpty()) {
                            shapeCache[candidate] = map
                            shapeCache[cleanCode] = map
                            return@withContext map
                        }
                    }
                }
            }
        }

        // 3. Fallback to individual shape endpoint if not present in consolidated index
        for (candidate in candidates) {
            val url = "$SHAPE_BASE_URL/$candidate.json"
            try {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    response.close()
                    if (bodyStr.isNotBlank() && bodyStr.startsWith("{")) {
                        val jsonObj = JSONObject(bodyStr)
                        val map = mutableMapOf<String, String>()
                        val keys = jsonObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = jsonObj.optString(key, "")
                        }
                        if (map.isNotEmpty()) {
                            shapeCache[candidate] = map
                            shapeCache[cleanCode] = map // Cache under requested code too
                            return@withContext map
                        }
                    }
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                Log.d("MetrobusRepository", "Individual shape for $candidate unavailable", e)
            }
        }

        emptyMap()
    }

    private suspend fun getShapesIndex(): JSONObject? = withContext(Dispatchers.IO) {
        if (cachedShapesIndex != null) return@withContext cachedShapesIndex

        // Check local storage via MetrobusDataSyncManager if context is available
        if (context != null) {
            val local = MetrobusDataSyncManager.loadShapesIndex(context)
            if (local != null) {
                cachedShapesIndex = local
                return@withContext local
            }
            // Trigger sync if local file does not exist yet
            MetrobusDataSyncManager.syncIfNeeded(context)
            val synced = MetrobusDataSyncManager.loadShapesIndex(context)
            if (synced != null) {
                cachedShapesIndex = synced
                return@withContext synced
            }
        }

        try {
            val response = client.newCall(Request.Builder().url(SHAPES_INDEX_URL).build()).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                response.close()
                if (bodyStr.isNotBlank()) {
                    cachedShapesIndex = JSONObject(bodyStr)
                    return@withContext cachedShapesIndex
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error fetching shapes index JSON", e)
        }
        null
    }

    private fun parseLinesMap(jsonStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val root = JSONObject(jsonStr)
            val linesArray = root.optJSONArray("lines") ?: JSONArray()
            for (i in 0 until linesArray.length()) {
                val obj = linesArray.optJSONObject(i) ?: continue
                val code = obj.optString("line_code", obj.optString("route_short_name", "")).trim()
                val longName = obj.optString("route_long_name", "").trim()
                if (code.isNotBlank() && longName.isNotBlank()) {
                    result[code] = longName
                }
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error parsing lines map JSON", e)
        }
        return result
    }

    private fun parseLinesInfoList(jsonStr: String): List<MetrobusLineInfo> {
        val list = mutableListOf<MetrobusLineInfo>()
        try {
            val root = JSONObject(jsonStr)
            val linesArray = root.optJSONArray("lines") ?: JSONArray()
            for (i in 0 until linesArray.length()) {
                val obj = linesArray.optJSONObject(i) ?: continue
                val lineCode = obj.optString("line_code", obj.optString("route_short_name", "")).trim()
                val routeId = obj.optString("route_id", lineCode).trim()
                val routeShortName = obj.optString("route_short_name", lineCode).trim()
                val routeLongName = obj.optString("route_long_name", "").trim()
                val concesion = obj.optString("concesion", "").trim()

                if (lineCode.isNotBlank()) {
                    list.add(
                        MetrobusLineInfo(
                            lineCode = lineCode,
                            routeId = routeId,
                            routeShortName = routeShortName,
                            routeLongName = routeLongName,
                            concesion = concesion
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error parsing lines info list JSON", e)
        }
        return list
    }

    private fun parseStopsJson(jsonStr: String): List<MetrobusStopEntity> {
        val list = mutableListOf<MetrobusStopEntity>()
        try {
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
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error parsing stops JSON", e)
        }
        return list
    }
}

