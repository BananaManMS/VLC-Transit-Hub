package com.example.data.repository

import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.MetrobusStopEntity
import com.example.ui.bus.MetrobusDepartureUiModel
import com.example.ui.bus.MetrobusLineInfo
import com.example.ui.bus.MetrobusShapeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class MetrobusRepository(
    private val database: AppDatabase,
    private val client: OkHttpClient,
    private val context: android.content.Context? = null
) {
    constructor(database: AppDatabase, client: OkHttpClient) : this(database, client, null)
    private var cachedShapesIndex: JSONObject? = null
    private var cachedLinesMap: Map<String, List<String>>? = null
    private var cachedLinesInfo: List<MetrobusLineInfo>? = null
    private var isStopsCacheValid: Boolean = false

    companion object {
        private const val TAG = "MetrobusRepository"
        private const val API_SECRET = "ApiVLCTH2584"
        private const val KEY_LINES_ETAG = "metrobus_lines_etag"
        private const val KEY_LINES_JSON = "metrobus_lines_json"
        private const val KEY_STOPS_ETAG = "metrobus_stops_etag"
        private const val LINES_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_lines.json"
        private const val METROBUS_API_BASE = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/metrobus"
        private const val SHAPES_INDEX_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_shapes.json"
        private const val SHAPE_BASE_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/shapes"
        private const val STOPS_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_stops.json"
    }

    suspend fun getLinesMap(forceRefresh: Boolean = false): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedLinesMap != null) {
            return@withContext cachedLinesMap!!
        }
        val linesInfo = getLinesInfo()
        val map = mutableMapOf<String, MutableList<String>>()
        for (info in linesInfo) {
            val list = map.getOrPut(info.lineCode) { mutableListOf() }
            if (!list.contains(info.routeId)) {
                list.add(info.routeId)
            }
        }
        cachedLinesMap = map
        map
    }

    suspend fun getLinesInfo(): List<MetrobusLineInfo> = withContext(Dispatchers.IO) {
        if (cachedLinesInfo != null) {
            return@withContext cachedLinesInfo!!
        }
        // Try local asset cache first
        if (context != null) {
            try {
                context.assets.open("metrobus_lines.json").use { stream ->
                    val jsonStr = stream.bufferedReader().use { it.readText() }
                    if (jsonStr.isNotBlank()) {
                        val parsed = parseLinesInfoList(jsonStr)
                        if (parsed.isNotEmpty()) {
                            cachedLinesInfo = parsed
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load local metrobus_lines.json asset: ${e.message}")
            }
        }
        try {
            val request = Request.Builder().url(LINES_URL).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                response.close()
                if (body.isNotBlank()) {
                    val parsed = parseLinesInfoList(body)
                    if (parsed.isNotEmpty()) {
                        cachedLinesInfo = parsed
                        return@withContext parsed
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching metrobus lines info", e)
        }
        cachedLinesInfo ?: emptyList()
    }

    private fun parseLinesInfoList(jsonStr: String): List<MetrobusLineInfo> {
        val list = mutableListOf<MetrobusLineInfo>()
        try {
            val root = JSONObject(jsonStr)
            val linesArray = root.optJSONArray("lines") ?: JSONArray()
            for (i in 0 until linesArray.length()) {
                val obj = linesArray.optJSONObject(i) ?: continue
                val lineCode = obj.optString("line_code", "").trim()
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
            Log.e(TAG, "Error parsing lines JSON", e)
        }
        return list
    }

    suspend fun ensureStopsCached(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (!forceRefresh && isStopsCacheValid) return@withContext
        val count = database.metrobusStopDao().getStopCount()
        if (count == 0 || forceRefresh) {
            syncStops(forceRefresh)
        }
        isStopsCacheValid = true
    }

    suspend fun syncStops(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val currentCount = database.metrobusStopDao().getStopCount()
        // If database is empty, seed immediately from assets
        if (currentCount == 0 && context != null) {
            try {
                context.assets.open("metrobus_stops.json").use { stream ->
                    val jsonStr = stream.bufferedReader().use { it.readText() }
                    if (jsonStr.isNotBlank()) {
                        val stops = parseStopsJson(jsonStr)
                        if (stops.isNotEmpty()) {
                            database.metrobusStopDao().insertAll(stops)
                            isStopsCacheValid = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load local metrobus_stops.json asset: ${e.message}")
            }
        }

        try {
            val request = Request.Builder().url(STOPS_URL).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                response.close()
                if (body.isNotBlank()) {
                    val stops = parseStopsJson(body)
                    if (stops.isNotEmpty()) {
                        database.metrobusStopDao().insertAll(stops)
                        isStopsCacheValid = true
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing metrobus stops from remote", e)
        }
    }

    fun parseStopsJson(jsonStr: String): List<MetrobusStopEntity> {
        val list = mutableListOf<MetrobusStopEntity>()
        try {
            val root = JSONObject(jsonStr)
            val stopsArray = root.optJSONArray("stops") ?: JSONArray()
            for (i in 0 until stopsArray.length()) {
                val obj = stopsArray.optJSONObject(i) ?: continue
                val id = obj.optString("stop_id", obj.optString("id_parada", obj.optString("id", ""))).trim()
                val name = obj.optString("stop_name", obj.optString("denominacion", obj.optString("name", ""))).trim()
                val mun = obj.optString("municipio", "").trim()
                val lat = obj.optDouble("stop_lat", obj.optDouble("latitud", obj.optDouble("lat", 0.0)))
                val lon = obj.optDouble("stop_lon", obj.optDouble("longitud", obj.optDouble("lon", 0.0)))

                val linesArray = obj.optJSONArray("lines")
                val lineas = if (linesArray != null) {
                    val sb = StringBuilder()
                    for (j in 0 until linesArray.length()) {
                        if (j > 0) sb.append(",")
                        sb.append(linesArray.optString(j))
                    }
                    sb.toString()
                } else {
                    obj.optString("lineas", "")
                }

                val codigo = obj.optString("codigo_parada", id)
                val dir = obj.optString("direccion", name)
                if (id.isNotBlank() && (lat != 0.0 || lon != 0.0)) {
                    list.add(
                        MetrobusStopEntity(
                            id = id,
                            denominacion = name,
                            municipio = mun,
                            latitud = lat,
                            longitud = lon,
                            lineas = lineas,
                            codigoParada = codigo,
                            direccion = dir
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing stops JSON", e)
        }
        return list
    }

    suspend fun fetchRealTimeEstimations(stopId: String): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MetrobusDepartureUiModel>()
        val cleanStop = stopId.trim()
        if (cleanStop.isBlank()) return@withContext emptyList()

        val madridZone = java.util.TimeZone.getTimeZone("Europe/Madrid")
        val calNow = java.util.Calendar.getInstance(madridZone)
        val currentSecond = calNow.get(java.util.Calendar.SECOND)

        try {
            val url = "$METROBUS_API_BASE/estimacion/$cleanStop"
            val request = Request.Builder()
                .url(url)
                .addHeader("X-App-Secret", API_SECRET)
                .addHeader("X-API-Secret", API_SECRET)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                response.close()
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    
                    // Format 1: Cloudflare Worker Metrobus format with "data" array
                    val dataArray = root.optJSONArray("data")
                    if (dataArray != null && dataArray.length() > 0) {
                        for (i in 0 until dataArray.length()) {
                            val lineObj = dataArray.optJSONObject(i) ?: continue
                            val lineCode = lineObj.optString("line", "").trim()
                            val routeName = lineObj.optString("route", "").trim()
                            val estArray = lineObj.optJSONArray("estimations") ?: JSONArray()

                            for (j in 0 until estArray.length()) {
                                val est = estArray.optJSONObject(j) ?: continue
                                val vehicleId = est.optString("vehicleId").ifBlank { null }
                                val minutes = est.optInt("minutesToArrival", 0)
                                val ocupacion = est.optString("ocupacion", "")
                                val almex = est.optJSONObject("almex")

                                val destName = routeName.ifBlank {
                                    almex?.optString("DestStopName", "")?.ifBlank { null }
                                } ?: "Línea $lineCode"
                                val tripId = almex?.optString("trip-id", "") ?: ""
                                val lat = almex?.optString("latitude", "")?.toDoubleOrNull()
                                val lon = almex?.optString("longitude", "")?.toDoubleOrNull()

                                val targetCal = java.util.Calendar.getInstance(madridZone).apply {
                                    add(java.util.Calendar.MINUTE, minutes)
                                }
                                val depTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).apply {
                                    timeZone = madridZone
                                }.format(targetCal.time)

                                val timeLabel = if (minutes <= 0) "Llegando" else "$minutes min"
                                val secondsRemaining = (minutes * 60) - currentSecond

                                list.add(
                                    MetrobusDepartureUiModel(
                                        lineCode = lineCode,
                                        lineName = "Línea $lineCode",
                                        destination = destName,
                                        minutesRemaining = minutes,
                                        timeLabel = timeLabel,
                                        departureTime = depTime,
                                        routeColor = "#FBC02D",
                                        tripId = tripId,
                                        ocupacion = ocupacion,
                                        vehicleId = vehicleId,
                                        isRealTime = true,
                                        latitude = lat,
                                        longitude = lon,
                                        agencyName = "Metrobús",
                                        secondsRemaining = secondsRemaining.coerceAtLeast(0)
                                    )
                                )
                            }
                        }
                    }

                    // Format 2: Flat estimations array fallback
                    if (list.isEmpty()) {
                        val estArray = root.optJSONArray("estimations") ?: JSONArray()
                        for (i in 0 until estArray.length()) {
                            val obj = estArray.optJSONObject(i) ?: continue
                            val lineCode = obj.optString("line", "").trim()
                            val lineName = obj.optString("line_name", lineCode).trim()
                            val destination = obj.optString("destination", "").trim()
                            val minutes = obj.optInt("minutes", 0)
                            val seconds = obj.optInt("seconds", minutes * 60)
                            val timeLabel = if (minutes <= 0) "Llegando" else "$minutes min"
                            val depTime = obj.optString("departure_time", "").trim()
                            val color = obj.optString("color", "#FBC02D")
                            val tripId = obj.optString("trip_id", "")
                            val ocupacion = obj.optString("occupancy", "")
                            val vehicleId = if (obj.has("vehicle_id")) obj.optString("vehicle_id").ifBlank { null } else null
                            val lat = if (obj.has("lat")) obj.optDouble("lat") else null
                            val lon = if (obj.has("lon")) obj.optDouble("lon") else null
                            val agency = obj.optString("agency", "Metrobús")

                            list.add(
                                MetrobusDepartureUiModel(
                                    lineCode = lineCode,
                                    lineName = lineName,
                                    destination = destination,
                                    minutesRemaining = minutes,
                                    timeLabel = timeLabel,
                                    departureTime = depTime,
                                    routeColor = color,
                                    tripId = tripId,
                                    ocupacion = ocupacion,
                                    vehicleId = vehicleId,
                                    isRealTime = true,
                                    latitude = lat,
                                    longitude = lon,
                                    agencyName = agency,
                                    secondsRemaining = seconds
                                )
                            )
                        }
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching real-time estimations for stop $stopId", e)
        }
        list.sortedBy { it.minutesRemaining }
    }

    suspend fun getMetrobusArrivals(
        stopId: String,
        limitPerLine: Int = 3,
        includeScheduled: Boolean = true
    ): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
        val realTime = fetchRealTimeEstimations(stopId).filter { it.isRealTime }
        if (realTime.isNotEmpty()) {
            if (!includeScheduled) {
                return@withContext realTime.take(limitPerLine * 5)
            }
            val scheduled = getMetrobusScheduledDepartures(stopId, limitPerLine)
            val combined = realTime.toMutableList()
            val maxLiveMinByLine = realTime.groupBy { it.lineCode }
                .mapValues { entry -> entry.value.maxOf { it.minutesRemaining } }

            scheduled.forEach { sched ->
                val maxLive = maxLiveMinByLine[sched.lineCode]
                if (maxLive == null || sched.minutesRemaining > (maxLive + 2)) {
                    combined.add(sched)
                }
            }
            return@withContext combined.sortedBy { it.minutesRemaining }
        }
        if (!includeScheduled) {
            return@withContext emptyList()
        }
        getMetrobusScheduledDepartures(stopId, limitPerLine)
    }

    suspend fun getMetrobusScheduledDepartures(
        stopId: String,
        limitPerLine: Int = 3
    ): List<MetrobusDepartureUiModel> = withContext(Dispatchers.IO) {
        com.example.data.repository.metrobus.MetrobusScheduledRepository.fetchScheduledDepartures(
            stopId = stopId,
            limitPerLine = limitPerLine,
            client = client
        )
    }

    suspend fun fetchLineShape(lineCode: String): MetrobusShapeData? = withContext(Dispatchers.IO) {
        val cleanLine = lineCode.trim().removePrefix("L").removePrefix("l")

        var indexObj: JSONObject? = if (context != null) {
            com.example.data.repository.metrobus.MetrobusDataSyncManager.loadShapesIndex(context)
        } else cachedShapesIndex

        if (indexObj == null) {
            try {
                val request = Request.Builder().url(SHAPES_INDEX_URL).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    response.close()
                    if (body.isNotBlank() && body.trim().startsWith("{")) {
                        indexObj = JSONObject(body)
                        cachedShapesIndex = indexObj
                        if (context != null) {
                            val shapesFile = com.example.data.repository.metrobus.MetrobusDataSyncManager.getLocalShapesFile(context)
                            try { shapesFile.writeText(body) } catch (_: Exception) {}
                        }
                    }
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching shapes index from GitHub: ${e.message}", e)
            }
        }

        if (indexObj == null) return@withContext null

        var lineObj: JSONObject? = indexObj.optJSONObject(lineCode.trim())
            ?: indexObj.optJSONObject(cleanLine)
            ?: indexObj.optJSONObject("L$cleanLine")
            ?: indexObj.optJSONObject("l$cleanLine")

        if (lineObj == null) {
            val keys = indexObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.equals(lineCode.trim(), ignoreCase = true) || k.equals(cleanLine, ignoreCase = true)) {
                    lineObj = indexObj.optJSONObject(k)
                    break
                }
            }
        }

        if (lineObj == null) return@withContext null

        val map = mutableMapOf<String, List<List<Double>>>()
        val dirKeys = lineObj.keys()
        while (dirKeys.hasNext()) {
            val dirKey = dirKeys.next()
            val coordsList = mutableListOf<List<Double>>()

            val polyString = lineObj.optString(dirKey, "")
            if (polyString.isNotBlank() && !polyString.startsWith("[")) {
                val decoded = com.example.util.PolylineDecoder.decodeToCoordinates(polyString, precision = 5)
                val finalDecoded = if (decoded.isEmpty()) {
                    com.example.util.PolylineDecoder.decodeToCoordinates(polyString, precision = 6)
                } else decoded
                finalDecoded.forEach { (lat, lon) ->
                    coordsList.add(listOf(lat, lon))
                }
            } else {
                val arr = lineObj.optJSONArray(dirKey)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val cArr = arr.optJSONArray(i) ?: continue
                        if (cArr.length() >= 2) {
                            coordsList.add(listOf(cArr.getDouble(0), cArr.getDouble(1)))
                        }
                    }
                }
            }

            if (coordsList.isNotEmpty()) {
                map[dirKey] = coordsList
            }
        }

        if (map.isNotEmpty()) {
            MetrobusShapeData(cleanLine, map)
        } else null
    }

    suspend fun getShapesIndex(): JSONObject? = withContext(Dispatchers.IO) {
        if (cachedShapesIndex != null) return@withContext cachedShapesIndex
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
            Log.e(TAG, "Error fetching shapes index JSON", e)
        }
        null
    }
}