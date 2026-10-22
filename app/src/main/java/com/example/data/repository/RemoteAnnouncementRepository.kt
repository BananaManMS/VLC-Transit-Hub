package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.announcement.RemoteAnnouncement
import com.example.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class RemoteAnnouncementRepository(private val context: Context) {

    companion object {
        private const val TAG = "RemoteAnnouncementRepo"
        const val ANNOUNCEMENTS_URL = "https://raw.githubusercontent.com/BananaManMS/metro_valencia_schedule/refs/heads/mapas/Avisos/text.json"
        private const val PREFS_NAME = "remote_announcements_prefs"
        private const val KEY_DISMISSED_IDS = "dismissed_ids"
    }

    private val httpClient: OkHttpClient by lazy {
        NetworkModule.okHttpClient.newBuilder()
            .connectTimeout(4000, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .callTimeout(5000, TimeUnit.MILLISECONDS)
            .build()
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDismissedIds(): Set<String> {
        return prefs.getStringSet(KEY_DISMISSED_IDS, emptySet()) ?: emptySet()
    }

    fun markAnnouncementDismissed(id: String) {
        if (id.isBlank()) return
        val current = getDismissedIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet(KEY_DISMISSED_IDS, current).apply()
    }

    suspend fun fetchActiveAnnouncements(): List<RemoteAnnouncement> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<RemoteAnnouncement>()
        try {
            val request = Request.Builder()
                .url(ANNOUNCEMENTS_URL)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP error fetching announcements: ${response.code}")
                    return@use null
                }
                response.body?.string()
            }

            if (responseBody.isNullOrBlank()) {
                return@withContext emptyList()
            }

            val jsonTrimmed = responseBody.trim()
            val rawArray: JSONArray = when {
                jsonTrimmed.startsWith("[") -> JSONArray(jsonTrimmed)
                jsonTrimmed.startsWith("{") -> {
                    val rootObj = JSONObject(jsonTrimmed)
                    rootObj.optJSONArray("announcements")
                        ?: rootObj.optJSONArray("avisos")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }

            val allRemoteIdsInJson = mutableSetOf<String>()
            val dismissedSet = getDismissedIds()
            Log.d(TAG, "Fetched JSON (${jsonTrimmed.length} chars). Current dismissed IDs: $dismissedSet")

            val madridTz = TimeZone.getTimeZone("Europe/Madrid")
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = madridTz
            }.format(Date())

            for (i in 0 until rawArray.length()) {
                val item = rawArray.optJSONObject(i) ?: continue

                val id = item.optString("id").ifBlank { "announcement_$i" }
                allRemoteIdsInJson.add(id)

                // Skip if marked as "No mostrar de nuevo"
                if (dismissedSet.contains(id)) {
                    Log.d(TAG, "Announcement '$id' is in dismissed set, skipping.")
                    continue
                }

                val enabled = item.optBoolean("activo", true)
                if (!enabled) continue

                val endDate = item.optString("hasta_fecha", "").trim()

                // Check expiration
                if (endDate.isNotBlank()) {
                    val cleanEndDate = if (endDate.contains("T")) endDate.split("T")[0] else endDate
                    if (cleanEndDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        if (todayStr > cleanEndDate) {
                            // Expired
                            continue
                        }
                    }
                }

                val tipo = item.optString("tipo", "azul").lowercase().trim()

                val titleEs = item.optString("titulo_es", "")
                val titleCa = item.optString("titulo_ca", "").ifBlank { titleEs }

                val msgEs = item.optString("mensaje_es", "")
                val msgCa = item.optString("mensaje_ca", "").ifBlank { msgEs }

                if (msgEs.isBlank() && titleEs.isBlank()) {
                    continue
                }

                val image = item.optString("imagen", "").trim()
                val actionUrl = item.optString("enlace", "").trim()
                val actionTextEs = item.optString("texto_enlace_es", "").trim().takeIf { it.isNotBlank() }
                val actionTextCa = item.optString("texto_enlace_ca", "").trim().takeIf { it.isNotBlank() }
                val repetir = item.optBoolean("repetir", true)
                val oculto = item.optBoolean("oculto", false)

                resultList.add(
                    RemoteAnnouncement(
                        id = id,
                        enabled = enabled,
                        type = tipo,
                        endDate = endDate.ifBlank { null },
                        titleEs = titleEs.ifBlank { "Aviso" },
                        titleCa = titleCa.ifBlank { "Avís" },
                        messageEs = msgEs,
                        messageCa = msgCa,
                        imageUrl = image.ifBlank { null },
                        actionUrl = actionUrl.ifBlank { null },
                        actionTextEs = actionTextEs,
                        actionTextCa = actionTextCa,
                        repetir = repetir,
                        oculto = oculto
                    )
                )
            }

            Log.d(TAG, "Parsed ${resultList.size} active announcements to display")

            // Automatic Garbage Collection: Prune local dismissed IDs that no longer exist in the GitHub JSON
            if (dismissedSet.isNotEmpty()) {
                val updatedDismissed = dismissedSet.filter { allRemoteIdsInJson.contains(it) }.toSet()
                if (updatedDismissed.size != dismissedSet.size) {
                    prefs.edit().putStringSet(KEY_DISMISSED_IDS, updatedDismissed).apply()
                    Log.d(TAG, "Pruned ${dismissedSet.size - updatedDismissed.size} stale dismissed announcement IDs")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote announcements: ${e.message}")
        }
        resultList
    }

    suspend fun fetchAllAnnouncements(): List<RemoteAnnouncement> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<RemoteAnnouncement>()
        try {
            val request = Request.Builder()
                .url(ANNOUNCEMENTS_URL)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }

            if (responseBody.isNullOrBlank()) return@withContext emptyList()

            val jsonTrimmed = responseBody.trim()
            val rawArray: JSONArray = when {
                jsonTrimmed.startsWith("[") -> JSONArray(jsonTrimmed)
                jsonTrimmed.startsWith("{") -> {
                    val rootObj = JSONObject(jsonTrimmed)
                    rootObj.optJSONArray("announcements")
                        ?: rootObj.optJSONArray("avisos")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }

            val madridTz = TimeZone.getTimeZone("Europe/Madrid")
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = madridTz
            }.format(Date())

            for (i in 0 until rawArray.length()) {
                val item = rawArray.optJSONObject(i) ?: continue

                val id = item.optString("id").ifBlank { "announcement_$i" }
                val enabled = item.optBoolean("activo", true)
                if (!enabled) continue

                val endDate = item.optString("hasta_fecha", "").trim()
                if (endDate.isNotBlank()) {
                    val cleanEndDate = if (endDate.contains("T")) endDate.split("T")[0] else endDate
                    if (cleanEndDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        if (todayStr > cleanEndDate) continue
                    }
                }

                val tipo = item.optString("tipo", "azul").lowercase().trim()
                val titleEs = item.optString("titulo_es", "")
                val titleCa = item.optString("titulo_ca", "").ifBlank { titleEs }
                val msgEs = item.optString("mensaje_es", "")
                val msgCa = item.optString("mensaje_ca", "").ifBlank { msgEs }

                if (msgEs.isBlank() && titleEs.isBlank()) continue

                val image = item.optString("imagen", "").trim()
                val actionUrl = item.optString("enlace", "").trim()
                val actionTextEs = item.optString("texto_enlace_es", "").trim().takeIf { it.isNotBlank() }
                val actionTextCa = item.optString("texto_enlace_ca", "").trim().takeIf { it.isNotBlank() }
                val repetir = item.optBoolean("repetir", true)
                val oculto = item.optBoolean("oculto", false)
                if (oculto) continue // Do not show in settings bell feed if oculto is true

                resultList.add(
                    RemoteAnnouncement(
                        id = id,
                        enabled = enabled,
                        type = tipo,
                        endDate = endDate.ifBlank { null },
                        titleEs = titleEs.ifBlank { "Aviso" },
                        titleCa = titleCa.ifBlank { "Avís" },
                        messageEs = msgEs,
                        messageCa = msgCa,
                        imageUrl = image.ifBlank { null },
                        actionUrl = actionUrl.ifBlank { null },
                        actionTextEs = actionTextEs,
                        actionTextCa = actionTextCa,
                        repetir = repetir,
                        oculto = oculto
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all announcements: ${e.message}")
        }
        resultList
    }

    private fun extractString(item: JSONObject, keyFlatEs: String, keyFlatAlt: String, keyObj: String, lang: String): String {
        if (item.has(keyFlatEs)) {
            val v = item.optString(keyFlatEs)
            if (v.isNotBlank()) return v
        }
        if (item.has(keyFlatAlt)) {
            val v = item.optString(keyFlatAlt)
            if (v.isNotBlank()) return v
        }
        if (item.has(keyObj)) {
            val nested = item.optJSONObject(keyObj)
            if (nested != null) {
                val v = nested.optString(lang, nested.optString("es", ""))
                if (v.isNotBlank()) return v
            } else {
                val v = item.optString(keyObj)
                if (v.isNotBlank()) return v
            }
        }
        return ""
    }
}
