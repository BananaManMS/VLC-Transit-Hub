package com.example.data.repository.metrobus

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.MetrobusStopEntity
import com.example.data.database.PreferenceEntity
import com.example.data.network.NetworkModule
import com.example.data.repository.StaticTransitDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages 24-hour periodic synchronization and caching of Metrobús Valencia data:
 * - Metrobús Stops (metrobus_stops.json)
 * - Metrobús Lines (metrobus_lines.json)
 * - Metrobús Shapes (metrobus_shapes.json)
 */
object MetrobusDataSyncManager {

    private const val TAG = "MetrobusDataSyncManager"
    private const val PREF_LAST_SYNC = "last_metrobus_data_sync"
    private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private const val STOPS_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_stops.json"
    private const val LINES_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_lines.json"
    private const val SHAPES_URL = "https://raw.githubusercontent.com/BananaManMS/metrobus_valencia_schedule/refs/heads/main/data/metrobus_shapes.json"

    fun getLocalStopsFile(context: Context): File {
        return File(context.filesDir, "metrobus_stops.json")
    }

    fun getLocalLinesFile(context: Context): File {
        return File(context.filesDir, "metrobus_lines.json")
    }

    fun getLocalShapesFile(context: Context): File {
        return File(context.filesDir, "metrobus_shapes.json")
    }

    fun loadShapesIndex(context: Context): JSONObject? {
        return try {
            val file = getLocalShapesFile(context)
            if (file.exists() && file.length() > 0) {
                JSONObject(file.readText())
            } else {
                val assetNames = context.assets.list("") ?: emptyArray()
                if (assetNames.contains("metrobus_shapes.json")) {
                    context.assets.open("metrobus_shapes.json").bufferedReader().use {
                        JSONObject(it.readText())
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading Metrobús shapes index: ${e.message}")
            null
        }
    }

    suspend fun syncIfNeeded(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefDao = db.preferenceDao()
        val now = System.currentTimeMillis()

        val lastSyncStr = try { prefDao.getPreference(PREF_LAST_SYNC)?.value } catch (_: Exception) { null }
        val lastSync = lastSyncStr?.toLongOrNull() ?: 0L

        val stopsFile = getLocalStopsFile(context)
        val linesFile = getLocalLinesFile(context)
        val shapesFile = getLocalShapesFile(context)

        val stopCount = try { db.metrobusStopDao().getStopCount() } catch (_: Exception) { 0 }

        // If local files exist, DB is seeded, and last sync is within 24h, skip
        if (!force && (now - lastSync < SYNC_INTERVAL_MS) && stopsFile.exists() && linesFile.exists() && stopCount > 50) {
            return@withContext
        }

        var stopsUpdated = false

        // 1. Sync Stops
        try {
            val request = Request.Builder().url(STOPS_URL).build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val stops = parseStopsJson(body)
                    if (stops.isNotEmpty()) {
                        writeAtomic(stopsFile, body)
                        db.metrobusStopDao().insertAll(stops)
                        stopsUpdated = true
                        Log.i(TAG, "Successfully synced ${stops.size} Metrobús stops from GitHub.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Metrobús stops from GitHub: ${e.message}")
        }

        // 2. Sync Lines
        try {
            val request = Request.Builder().url(LINES_URL).build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (isValidJson(body)) {
                        writeAtomic(linesFile, body)
                        Log.i(TAG, "Successfully synced Metrobús lines from GitHub.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Metrobús lines from GitHub: ${e.message}")
        }

        // 3. Sync Shapes
        try {
            val request = Request.Builder().url(SHAPES_URL).build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (isValidJson(body)) {
                        writeAtomic(shapesFile, body)
                        Log.i(TAG, "Successfully synced Metrobús shapes from GitHub.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Metrobús shapes from GitHub: ${e.message}")
        }

        // 4. Update last sync timestamp
        try {
            prefDao.insertPreference(PreferenceEntity(PREF_LAST_SYNC, now.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last Metrobús sync preference: ${e.message}")
        }

        if (stopsUpdated) {
            StaticTransitDataCache.getOrLoadMetrobusStops(context)
        }
    }

    private fun parseStopsJson(jsonStr: String): List<MetrobusStopEntity> {
        val list = mutableListOf<MetrobusStopEntity>()
        if (jsonStr.isBlank()) return list
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

                if (id.isNotEmpty() && name.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                    list.add(
                        MetrobusStopEntity(
                            id = id,
                            denominacion = name,
                            municipio = mun,
                            latitud = lat,
                            longitud = lon,
                            lineas = lineas,
                            codigoParada = id,
                            direccion = ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Metrobús stops JSON: ${e.message}")
        }
        return list
    }

    private fun isValidJson(text: String): Boolean {
        if (text.isBlank()) return false
        val trimmed = text.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private fun writeAtomic(targetFile: File, content: String) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeText(content, Charsets.UTF_8)
        if (tempFile.exists() && tempFile.length() > 0) {
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }
    }
}
