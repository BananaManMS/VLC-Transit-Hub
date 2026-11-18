package com.example.data.repository.emt

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.PreferenceEntity
import com.example.data.network.NetworkModule
import com.example.data.repository.StaticTransitDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages 24-hour periodic synchronization and caching of EMT Valencia static data:
 * - EMT Stops (stops.json)
 * - EMT Route Shapes (shapes.json)
 */
class EmtDataSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "EmtDataSyncManager"
        private const val PREF_LAST_SYNC = "last_emt_data_sync"
        private const val PREFS_SYNC = "emt_sync_prefs"
        private const val KEY_STOPS_ETAG = "emt_stops_etag"
        private const val KEY_SHAPES_ETAG = "emt_shapes_etag"
        private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

        private const val STOPS_URL = "https://raw.githubusercontent.com/BananaManMS/EMT_valencia_schedule/refs/heads/data/stops.json"
        private const val SHAPES_URL = "https://raw.githubusercontent.com/BananaManMS/EMT_valencia_schedule/refs/heads/data/shapes.json"

        private val syncMutex = Mutex()

        fun getLocalStopsFile(context: Context): File {
            return File(context.filesDir, "emt_stops.json")
        }

        fun getLocalShapesFile(context: Context): File {
            return File(context.filesDir, "emt_shapes.json")
        }

        suspend fun syncIfNeeded(context: Context, force: Boolean = false) {
            EmtDataSyncManager(context).syncEmtData(force)
        }
    }

    suspend fun syncEmtData(force: Boolean = false) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val db = AppDatabase.getDatabase(context)
            val prefDao = db.preferenceDao()
            val syncPrefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            val lastSyncStr = try { prefDao.getPreference(PREF_LAST_SYNC)?.value } catch (_: Exception) { null }
            val lastSync = lastSyncStr?.toLongOrNull() ?: 0L

            val stopsFile = getLocalStopsFile(context)
            val shapesFile = getLocalShapesFile(context)

            // If files exist, DB has stops, and less than 24h passed, skip remote sync
            val stopCount = try { db.geoportalStopDao().getStopCount() } catch (_: Exception) { 0 }
            if (!force && (now - lastSync < SYNC_INTERVAL_MS) && stopsFile.exists() && stopsFile.length() > 1000 && stopCount > 50) {
                return@withLock
            }

            var stopsUpdated = false

            coroutineScope {
                val stopsDeferred = async {
                    try {
                        val reqBuilder = Request.Builder().url(STOPS_URL)
                        val cachedEtag = syncPrefs.getString(KEY_STOPS_ETAG, null)
                        if (!force && stopsFile.exists() && stopsFile.length() > 1000 && !cachedEtag.isNullOrBlank()) {
                            reqBuilder.header("If-None-Match", cachedEtag)
                        }
                        NetworkModule.okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                            when {
                                response.code == 304 -> {
                                    Log.d(TAG, "EMT stops unchanged on GitHub (304 Not Modified).")
                                }
                                response.isSuccessful -> {
                                    val newEtag = response.header("ETag")
                                    val body = response.body?.string() ?: ""
                                    val parsedStops = parseAndValidateStops(body)
                                    if (parsedStops.isNotEmpty()) {
                                        writeAtomic(stopsFile, body)
                                        db.geoportalStopDao().insertAll(parsedStops)
                                        if (!newEtag.isNullOrBlank()) {
                                            syncPrefs.edit().putString(KEY_STOPS_ETAG, newEtag).apply()
                                        }
                                        stopsUpdated = true
                                        Log.i(TAG, "Successfully synced ${parsedStops.size} EMT stops from GitHub.")
                                    } else {
                                        Log.w(TAG, "Downloaded EMT stops failed validation (empty or corrupt).")
                                    }
                                }
                                else -> {
                                    Log.w(TAG, "EMT stops HTTP request returned code ${response.code}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error downloading EMT stops from GitHub: ${e.message}")
                    }
                }

                val shapesDeferred = async {
                    try {
                        val reqBuilder = Request.Builder().url(SHAPES_URL)
                        val cachedEtag = syncPrefs.getString(KEY_SHAPES_ETAG, null)
                        if (!force && shapesFile.exists() && shapesFile.length() > 500 && !cachedEtag.isNullOrBlank()) {
                            reqBuilder.header("If-None-Match", cachedEtag)
                        }
                        NetworkModule.okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                            when {
                                response.code == 304 -> {
                                    Log.d(TAG, "EMT shapes unchanged on GitHub (304 Not Modified).")
                                }
                                response.isSuccessful -> {
                                    val newEtag = response.header("ETag")
                                    val body = response.body?.string() ?: ""
                                    if (isValidShapesJson(body)) {
                                        writeAtomic(shapesFile, body)
                                        if (!newEtag.isNullOrBlank()) {
                                            syncPrefs.edit().putString(KEY_SHAPES_ETAG, newEtag).apply()
                                        }
                                        Log.i(TAG, "Successfully synced EMT shapes from GitHub.")
                                    } else {
                                        Log.w(TAG, "Downloaded EMT shapes failed validation.")
                                    }
                                }
                                else -> {
                                    Log.w(TAG, "EMT shapes HTTP request returned code ${response.code}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error downloading EMT shapes from GitHub: ${e.message}")
                    }
                }

                stopsDeferred.await()
                shapesDeferred.await()
            }

            // 3. Update Last Sync timestamp in preferences
            try {
                prefDao.insertPreference(PreferenceEntity(PREF_LAST_SYNC, now.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving last EMT sync preference: ${e.message}")
            }

            // 4. If stops were updated, notify memory cache
            if (stopsUpdated) {
                StaticTransitDataCache.getOrLoadEmtStops(context)
            }
        }
    }

    private fun parseAndValidateStops(jsonString: String): List<GeoportalStopEntity> {
        val list = mutableListOf<GeoportalStopEntity>()
        if (jsonString.isBlank()) return list
        try {
            val jsonArray = try {
                JSONArray(jsonString)
            } catch (_: Exception) {
                try {
                    JSONObject(jsonString).optJSONArray("stops") ?: JSONArray()
                } catch (_: Exception) {
                    JSONArray()
                }
            }

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val idParada = obj.optString("id_parada", "")
                    .ifBlank { obj.optString("stop_id", "") }
                    .ifBlank { obj.optString("id", "") }
                    .trim()
                val denominacion = obj.optString("denominacion", "")
                    .ifBlank { obj.optString("nombre", "") }
                    .ifBlank { obj.optString("name", "") }
                    .trim()
                val lat = obj.optDouble("lat", 0.0)
                val lon = obj.optDouble("lon", 0.0)
                val suprimida = obj.optInt("suprimida", 0)

                val lineas = if (obj.has("lineas") || obj.has("lines")) {
                    val l = if (obj.has("lineas")) obj.get("lineas") else obj.get("lines")
                    if (l is JSONArray) {
                        (0 until l.length()).joinToString(", ") { l.getString(it) }
                    } else {
                        l.toString()
                    }
                } else ""

                if (idParada.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                    list.add(
                        GeoportalStopEntity(
                            id_parada = idParada,
                            denominacion = denominacion,
                            suprimida = suprimida,
                            lat = lat,
                            lon = lon,
                            lineas = lineas
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed for EMT stops JSON: ${e.message}")
        }
        return if (list.size >= 50) list else emptyList()
    }

    private fun isValidShapesJson(jsonString: String): Boolean {
        if (jsonString.isBlank()) return false
        return try {
            val trimmed = jsonString.trim()
            trimmed.startsWith("{") || trimmed.startsWith("[")
        } catch (_: Exception) {
            false
        }
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
