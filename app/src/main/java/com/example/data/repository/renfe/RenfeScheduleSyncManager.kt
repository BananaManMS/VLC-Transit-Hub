package com.example.data.repository.renfe

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.RenfeScheduleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.URL
import java.util.Locale

class RenfeScheduleSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val stationDao = database.cercaniasStationDao()
    private val localScheduleFile: File = File(context.filesDir, "cercanias_valencia_schedule.json")
    private val tempScheduleFile: File = File(context.filesDir, "cercanias_valencia_schedule_temp.json")

    @Volatile
    private var tripDestinationMap: Map<String, String>? = null
    
    private var stationNameMap: Map<String, String>? = null

    private fun isValidScheduleJson(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed == "[]" || trimmed == "{}") {
            Log.w("RenfeScheduleSyncManager", "Validation failed: JSON text is blank or empty container ('$trimmed').")
            return false
        }

        if (trimmed.startsWith("{")) {
            try {
                val jsonObj = org.json.JSONObject(trimmed)
                if (jsonObj.has("error") || jsonObj.has("message") || jsonObj.has("code") || jsonObj.has("status") || jsonObj.has("detail")) {
                    val errMsg = jsonObj.optString("error", jsonObj.optString("message", "Error response"))
                    Log.w("RenfeScheduleSyncManager", "Validation failed: Server returned error JSON object ($errMsg).")
                    return false
                }
            } catch (e: Exception) {
                Log.w("RenfeScheduleSyncManager", "Validation failed: JSON object could not be parsed: ${e.message}")
                return false
            }
            Log.w("RenfeScheduleSyncManager", "Validation failed: Expected JSON Array of stations but received JSON Object.")
            return false
        }

        if (!trimmed.startsWith("[")) {
            Log.w("RenfeScheduleSyncManager", "Validation failed: JSON text does not start with '['.")
            return false
        }

        return try {
            val jsonArray = JSONArray(trimmed)
            if (jsonArray.length() < 10) {
                Log.w("RenfeScheduleSyncManager", "Validation failed: Schedule array has only ${jsonArray.length()} items (expected at least 10).")
                return false
            }

            var validStationsCount = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue

                if (obj.has("error") || obj.has("message")) {
                    Log.w("RenfeScheduleSyncManager", "Validation failed: Station object contains error response key.")
                    return false
                }

                val stopId = obj.optString("stop_id", "")
                val nombre = obj.optString("nombre", "")
                val lineas = obj.optJSONArray("lineas")
                val horarios = obj.optJSONArray("horarios")

                if (stopId.isNotBlank() && nombre.isNotBlank() && lineas != null && horarios != null && horarios.length() > 0) {
                    val firstHorario = horarios.optJSONObject(0)
                    if (firstHorario != null && (firstHorario.has("linea") || firstHorario.has("llegada") || firstHorario.has("trip_ids"))) {
                        validStationsCount++
                    }
                }
            }

            if (validStationsCount >= 5) {
                true
            } else {
                Log.w("RenfeScheduleSyncManager", "Validation failed: Only $validStationsCount valid stations with complete schedules found (expected at least 5).")
                false
            }
        } catch (e: Exception) {
            Log.w("RenfeScheduleSyncManager", "Validation failed with exception during JSON parsing: ${e.message}")
            false
        }
    }

    private fun readAssetSchedule(): String {
        return try {
            val assetStr = context.assets.open("cercanias_valencia_schedule.json").bufferedReader().use { it.readText() }
            if (isValidScheduleJson(assetStr)) {
                try {
                    localScheduleFile.writeText(assetStr)
                } catch (e: Exception) {}
                assetStr
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getScheduleJsonString(): String {
        return try {
            if (localScheduleFile.exists() && localScheduleFile.length() > 100) {
                val text = localScheduleFile.readText()
                if (isValidScheduleJson(text)) {
                    text
                } else {
                    Log.w("RenfeScheduleSyncManager", "Local schedule file invalid or corrupted, deleting and falling back to assets.")
                    try { localScheduleFile.delete() } catch (e: Exception) {}
                    readAssetSchedule()
                }
            } else {
                readAssetSchedule()
            }
        } catch (e: Exception) {
            readAssetSchedule()
        }
    }

    suspend fun syncScheduleFromRemoteIfNeeded() = withContext(Dispatchers.IO) {
        var currentDbCount = try { stationDao.getStationCount() } catch (e: Exception) { 0 }
        if (currentDbCount < 10) {
            initDatabaseFromAssetsIfNeeded()
            currentDbCount = try { stationDao.getStationCount() } catch (e: Exception) { 0 }
        }

        val lastSyncStr = database.preferenceDao().getPreference("last_schedule_sync")?.value
        val now = System.currentTimeMillis()
        val lastSync = lastSyncStr?.toLongOrNull() ?: 0L
        
        val isCacheValid = localScheduleFile.exists() && isValidScheduleJson(
            try { localScheduleFile.readText() } catch (e: Exception) { "" }
        )

        // Only skip remote sync if DB is complete, local cache is valid, and last sync was less than 12h ago
        if (now - lastSync < 12 * 60 * 60 * 1000L && currentDbCount >= 10 && isCacheValid) {
            return@withContext
        }

        try {
            val request = okhttp3.Request.Builder()
                .url("https://raw.githubusercontent.com/BananaManMS/cercanias-vlc-schedule/refs/heads/main/cercanias_valencia_schedule.json")
                .build()

            com.example.data.network.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val text = response.body?.string() ?: ""
                    if (isValidScheduleJson(text)) {
                        tempScheduleFile.writeText(text)
                        if (tempScheduleFile.exists() && tempScheduleFile.length() > 100) {
                            tempScheduleFile.copyTo(localScheduleFile, overwrite = true)
                            try { tempScheduleFile.delete() } catch (e: Exception) {}
                            database.preferenceDao().insertPreference(
                                com.example.data.database.PreferenceEntity("last_schedule_sync", now.toString())
                            )
                            reloadFromAssets()
                            Log.i("RenfeScheduleSyncManager", "Remote schedule updated and validated successfully.")
                        }
                    } else {
                        Log.w("RenfeScheduleSyncManager", "Downloaded remote schedule failed validation. Retaining local asset/cache.")
                    }
                } else {
                    Log.w("RenfeScheduleSyncManager", "Remote schedule returned HTTP ${response.code}")
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            Log.w("RenfeScheduleSyncManager", "Remote schedule file not found (404), keeping local schedule.")
        } catch (e: Exception) {
            Log.e("RenfeScheduleSyncManager", "Error syncing schedule: ${e.message}")
        }

        // Final safeguard: if DB is still empty after attempt, force asset load
        if (stationDao.getStationCount() == 0) {
            initDatabaseFromAssetsIfNeeded()
        }
    }

    suspend fun forceSyncScheduleFromRemote(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        reloadFromAssets()

        try {
            val request = okhttp3.Request.Builder()
                .url("https://raw.githubusercontent.com/BananaManMS/cercanias-vlc-schedule/refs/heads/main/cercanias_valencia_schedule.json")
                .build()

            com.example.data.network.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val text = response.body?.string() ?: ""
                    if (isValidScheduleJson(text)) {
                        tempScheduleFile.writeText(text)
                        if (tempScheduleFile.exists() && tempScheduleFile.length() > 100) {
                            tempScheduleFile.copyTo(localScheduleFile, overwrite = true)
                            try { tempScheduleFile.delete() } catch (e: Exception) {}
                            database.preferenceDao().insertPreference(
                                com.example.data.database.PreferenceEntity("last_schedule_sync", now.toString())
                            )
                            reloadFromAssets()
                            Log.i("RenfeScheduleSyncManager", "Force sync remote schedule succeeded.")
                            return@withContext true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RenfeScheduleSyncManager", "Error force syncing schedule: ${e.message}")
        }

        return@withContext true
    }

    suspend fun initDatabaseFromAssetsIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val currentAssetsVersion = database.preferenceDao().getPreference("cercanias_assets_version")?.value ?: "0"
            if (currentAssetsVersion != "13") {
                try {
                    if (localScheduleFile.exists()) {
                        localScheduleFile.delete()
                    }
                } catch (e: Exception) {}
                try {
                    stationDao.deleteAllStations()
                } catch (e: Exception) {}
                database.preferenceDao().insertPreference(
                    com.example.data.database.PreferenceEntity("cercanias_assets_version", "13")
                )
            }
        } catch (e: Exception) {
            Log.e("RenfeScheduleSyncManager", "Error checking/updating cercanias assets version preference", e)
        }

        val jsonString = getScheduleJsonString()
        if (jsonString.isBlank()) {
            Log.e("RenfeScheduleSyncManager", "JSON file is empty")
            return@withContext
        }
        try {
            val jsonArray = JSONArray(jsonString)
            val count = stationDao.getStationCount()
            val existingStations = try { stationDao.getAllStations() } catch (e: Exception) { emptyList() }
            val hasMojibake = existingStations.any { it.nombre.contains("ã") }
            val hasEmptyHorarios = existingStations.isEmpty() || existingStations.any { it.horarios.isEmpty() }
            if (count != jsonArray.length() || count == 0 || hasMojibake || hasEmptyHorarios) {
                loadAndSaveScheduleJson(jsonArray)
                buildTripDestinationMapFromAssets()
            }
        } catch (e: Exception) {
            Log.e("RenfeScheduleSyncManager", "Error initializing Cercanías database", e)
        }
    }

    suspend fun reloadFromAssets() = withContext(Dispatchers.IO) {
        try {
            val jsonString = getScheduleJsonString()
            val jsonArray = JSONArray(jsonString)
            loadAndSaveScheduleJson(jsonArray)
        } catch (e: Exception) {}
    }

    private suspend fun loadAndSaveScheduleJson(jsonArray: JSONArray) = withContext(Dispatchers.IO) {
        try {
            val savedFavsPref = try {
                database.preferenceDao().getPreference("favorite_cercanias_stations")?.value ?: ""
            } catch(e: Exception) { "" }
            val savedFavSet = savedFavsPref.split(",").filter { it.isNotBlank() }.toSet()

            val existingFavorites = try {
                stationDao.getAllStations().associate { it.stop_id to it.isFavorite }
            } catch(e: Exception) {
                emptyMap()
            }

            // Load precise coordinates from cercanias_stations_coords.json to override general ones
            val coordsMap = mutableMapOf<String, Pair<Double, Double>>()
            try {
                val coordsString = context.assets.open("cercanias_stations_coords.json").bufferedReader().use { it.readText() }
                val coordsArray = JSONArray(coordsString)
                for (cIdx in 0 until coordsArray.length()) {
                    val cObj = coordsArray.optJSONObject(cIdx) ?: continue
                    val stopId = cObj.optString("stop_id", "")
                    val stopLat = cObj.optDouble("stop_lat", 0.0)
                    val stopLon = cObj.optDouble("stop_lon", 0.0)
                    if (stopId.isNotEmpty() && stopLat != 0.0 && stopLon != 0.0) {
                        coordsMap[stopId] = Pair(stopLat, stopLon)
                    }
                }
            } catch (e: Exception) {
                Log.e("RenfeScheduleSyncManager", "Error parsing cercanias_stations_coords.json", e)
            }

            val stationsToInsert = mutableListOf<CercaniasStationEntity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val stopId = obj.optString("stop_id", "")
                if (stopId.isEmpty()) continue
                val rawNombre = obj.optString("nombre", "")
                val nombre = formatDestinationName(rawNombre)

                val overrideCoords = coordsMap[stopId]
                val lat = overrideCoords?.first ?: obj.optDouble("lat", 0.0)
                val lon = overrideCoords?.second ?: obj.optDouble("lon", 0.0)
                
                val lineasArr = obj.optJSONArray("lineas")
                val lineas = mutableListOf<String>()
                if (lineasArr != null) {
                    for (j in 0 until lineasArr.length()) {
                        val line = lineasArr.getString(j)
                        if (line.isNotBlank()) lineas.add(line)
                    }
                }
                
                val horariosArr = obj.optJSONArray("horarios")
                val horarios = mutableListOf<RenfeScheduleItem>()
                if (horariosArr != null) {
                    for (j in 0 until horariosArr.length()) {
                        val hObj = horariosArr.optJSONObject(j) ?: continue
                        val linea = hObj.optString("linea", "")
                        val destino = hObj.optString("destino", "")
                        val llegada = hObj.optString("llegada", "")
                        val tripIdsArr = hObj.optJSONArray("trip_ids")
                        val tripIds = mutableListOf<String>()
                        if (tripIdsArr != null) {
                            for (k in 0 until tripIdsArr.length()) {
                                tripIds.add(tripIdsArr.getString(k))
                            }
                        }
                        horarios.add(RenfeScheduleItem(linea, tripIds, llegada))
                    }
                }
                
                val isFav = savedFavSet.contains(stopId) || (existingFavorites[stopId] ?: false)
                stationsToInsert.add(CercaniasStationEntity(stopId, nombre, lat, lon, lineas, horarios, isFav))
            }
            
            tripDestinationMap = processTripDestinationMap(jsonArray)
            if (stationsToInsert.size >= 10) {
                stationDao.deleteAllStations()
                stationDao.insertAll(stationsToInsert)
                Log.i("RenfeScheduleSyncManager", "Successfully saved ${stationsToInsert.size} Cercanías stations to database.")
            } else {
                Log.w("RenfeScheduleSyncManager", "Parsed only ${stationsToInsert.size} stations (expected >= 10), skipping database update.")
            }
        } catch(e: Exception) {}
    }

    private fun parseTimeToSeconds(timeStr: String): Int {
        if (timeStr.isBlank()) return -1
        val parts = timeStr.trim().split(":")
        if (parts.size < 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        val s = if (parts.size >= 3) parts[2].toIntOrNull() ?: 0 else 0
        return h * 3600 + m * 60 + s
    }

    private fun processTripDestinationMap(jsonArray: JSONArray): Map<String, String> {
        val tripMap = mutableMapOf<String, Pair<String, Int>>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val nombre = obj.optString("nombre", "")
            val horariosArr = obj.optJSONArray("horarios") ?: continue
            for (j in 0 until horariosArr.length()) {
                val hObj = horariosArr.optJSONObject(j) ?: continue
                val llegada = hObj.optString("llegada", "")
                val timeSec = parseTimeToSeconds(llegada)
                if (timeSec < 0) continue
                val tripsArr = hObj.optJSONArray("trip_ids") ?: continue
                for (k in 0 until tripsArr.length()) {
                    val tid = tripsArr.getString(k).trim()
                    if (tid.isNotEmpty()) {
                        val prev = tripMap[tid]
                        if (prev == null || timeSec > prev.second) {
                            tripMap[tid] = Pair(nombre, timeSec)
                        }
                    }
                }
            }
        }
        return tripMap.mapValues { it.value.first }
    }

    fun buildTripDestinationMapFromAssets() {
        if (tripDestinationMap != null) return
        try {
            val jsonString = getScheduleJsonString()
            if (jsonString.isBlank()) return
            val jsonArray = JSONArray(jsonString)
            tripDestinationMap = processTripDestinationMap(jsonArray)
        } catch (e: Exception) {
            Log.e("RenfeScheduleSyncManager", "Error building trip destination map: ${e.message}")
        }
    }

    fun getTripDestination(tripId: String, line: String, currentStationName: String): String {
        if (tripDestinationMap == null) {
            buildTripDestinationMapFromAssets()
        }
        val rawDest = tripDestinationMap?.get(tripId)
        val destName = if (!rawDest.isNullOrBlank()) {
            formatDestinationName(rawDest)
        } else {
            ""
        }
        if (destName.isNotEmpty()) {
            return destName
        }
        val fallback = when (line) {
            "C1" -> "Gandia"
            "C2" -> "Moixent"
            "C3" -> "Utiel"
            "C5" -> "Caudiel"
            "C6" -> "Castelló de la Plana"
            else -> "València Nord"
        }
        return formatDestinationName(fallback)
    }

    private fun formatDestinationName(dest: String): String {
        val lower = dest.lowercase(Locale.ROOT)
        return when {
            dest.equals("Estacio del Nord", ignoreCase = true) || dest.equals("Valencia Nord", ignoreCase = true) || dest.equals("Valencia-Estacio del Nord", ignoreCase = true) -> "València Nord"
            dest.equals("Valencia-La Font de Sant Lluis", ignoreCase = true) || dest.contains("Font de Sant Lluis", ignoreCase = true) -> "Valencia F. S. Lluís"
            dest.equals("València Sant Isidre", ignoreCase = true) || dest.equals("Valencia Sant Isidre", ignoreCase = true) -> "València Sant Isidre"
            dest.contains("Vinaros", ignoreCase = true) || dest.contains("Vinaròs", ignoreCase = true) -> "Vinaròs"
            dest.contains("Benicarlo", ignoreCase = true) -> "Benicarló-Peníscola"
            dest.contains("Castello", ignoreCase = true) -> "Castelló de la Plana"
            dest.equals("Xativa", ignoreCase = true) || dest.equals("Xàtiva", ignoreCase = true) -> "Xàtiva"
            dest.equals("L'Alcudia", ignoreCase = true) || dest.equals("L'Alcudia de Crespins", ignoreCase = true) -> "L'Alcúdia de Crespins"
            lower.contains("bunol") || lower.contains("buã") || lower.contains("buñ") || (lower.contains("bu") && lower.contains("ol")) -> "Buñol"
            lower.contains("puc") || lower.contains("puç") || lower.contains("puã") || lower.contains("pua") || lower.contains("pu") && lower.contains("ol") -> "Puçol"
            lower.contains("burriana") || lower.contains("alquerias") || lower.contains("niã") || lower.contains("niño") -> "Burriana - Alquerías del Niño Perdido"
            dest.equals("Platja i Grau de Gandia", ignoreCase = true) -> "Platja de Gandia"
            else -> dest
        }
    }

    suspend fun getStationNameMap(): Map<String, String> = withContext(Dispatchers.IO) {
        stationNameMap?.let { return@withContext it }
        val map = mutableMapOf<String, String>()
        val stations = stationDao.getAllStations()
        stations.forEach { map[it.stop_id] = it.nombre }
        stationNameMap = map
        return@withContext map
    }
}
