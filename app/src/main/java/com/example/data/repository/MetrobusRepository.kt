package com.example.data.repository

import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.MetrobusStopEntity
import com.example.data.network.NetworkModule
import com.example.ui.bus.MetrobusStopDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.data.database.PreferenceEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MetrobusRepository(
    private val database: AppDatabase,
    private val client: OkHttpClient = NetworkModule.okHttpClient
) {

    private var cachedLinesMap: Map<String, String>? = null

    suspend fun getLinesMap(forceRefresh: Boolean = false): Map<String, String> = withContext(Dispatchers.IO) {
        // Metrobús features on hold pending GTFS updates
        emptyMap()
    }

    private fun sanitizeLineName(key: String, rawValue: String): String {
        var cleaned = rawValue.trim()
        if (cleaned.startsWith("${key}_", ignoreCase = true)) {
            cleaned = cleaned.substring(key.length + 1).trim()
        } else if (cleaned.contains("_")) {
            val parts = cleaned.split("_", limit = 2)
            if (parts.size == 2 && parts[0].matches(Regex("(?i)^[A-Z0-9]+$"))) {
                cleaned = parts[1].trim()
            }
        }
        return cleaned
    }

    private fun parseAndSanitizeLines(jsonStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val jsonObj = JSONObject(jsonStr)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rawVal = jsonObj.optString(key, "")
                if (rawVal.isNotBlank()) {
                    val cleanVal = sanitizeLineName(key, rawVal)
                    result[key] = cleanVal
                }
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error parsing lines JSON", e)
        }
        return result
    }

    private fun parseLinesJson(jsonStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val jsonObj = JSONObject(jsonStr)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val valStr = jsonObj.optString(key, "")
                if (valStr.isNotBlank()) {
                    result[key] = valStr
                }
            }
        } catch (e: Exception) {
            Log.e("MetrobusRepository", "Error parsing cached lines JSON", e)
        }
        return result
    }

    suspend fun ensureStopsCached(forceRefresh: Boolean = false): Boolean = false

    suspend fun syncStops(forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        // Metrobús requests on hold
        false
    }

    suspend fun fetchStopDetail(stopId: String): MetrobusStopDetail? = withContext(Dispatchers.IO) {
        // Metrobús requests on hold
        null
    }
}
