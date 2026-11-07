package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.PreferenceEntity
import com.example.data.model.announcement.RemoteAnnouncement
import com.example.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
        private const val PREF_LAST_FETCH = "last_announcements_fetch"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private val fetchMutex = Mutex()
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

    private val localCacheFile: File by lazy {
        File(context.filesDir, "cached_remote_announcements.json")
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

    private suspend fun getOrFetchRawAnnouncementsJson(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        fetchMutex.withLock {
            val db = AppDatabase.getDatabase(context)
            val now = System.currentTimeMillis()
            val lastFetchStr = try {
                db.preferenceDao().getPreference(PREF_LAST_FETCH)?.value
            } catch (_: Exception) { null }
            val lastFetch = lastFetchStr?.toLongOrNull() ?: 0L

            val hasValidFile = localCacheFile.exists() && localCacheFile.length() > 0
            if (!force && (now - lastFetch < CACHE_TTL_MS) && hasValidFile) {
                return@withLock try { localCacheFile.readText() } catch (_: Exception) { null }
            }

            var downloadedBody: String? = null
            try {
                val request = Request.Builder()
                    .url(ANNOUNCEMENTS_URL)
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .header("Cache-Control", "no-cache")
                    .build()

                val responseBody = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "HTTP error fetching announcements: ${response.code}")
                        null
                    } else {
                        response.body?.string()
                    }
                }

                if (!responseBody.isNullOrBlank()) {
                    downloadedBody = responseBody
                    try {
                        localCacheFile.writeText(responseBody)
                        db.preferenceDao().insertPreference(PreferenceEntity(PREF_LAST_FETCH, now.toString()))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing cached announcements to disk: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network failure fetching announcements: ${e.message}")
            }

            if (!downloadedBody.isNullOrBlank()) {
                downloadedBody
            } else if (hasValidFile) {
                Log.i(TAG, "Using fallback cached announcements JSON after network failure.")
                try { localCacheFile.readText() } catch (_: Exception) { null }
            } else {
                null
            }
        }
    }

    private fun parseAnnouncementsFromJson(jsonString: String, activeOnly: Boolean): List<RemoteAnnouncement> {
        val resultList = mutableListOf<RemoteAnnouncement>()
        try {
            val jsonTrimmed = jsonString.trim()
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

            val madridTz = TimeZone.getTimeZone("Europe/Madrid")
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = madridTz
            }.format(Date())

            for (i in 0 until rawArray.length()) {
                val item = rawArray.optJSONObject(i) ?: continue

                val id = item.optString("id").ifBlank { "announcement_$i" }
                allRemoteIdsInJson.add(id)

                if (activeOnly && dismissedSet.contains(id)) {
                    continue
                }

                val enabled = item.optBoolean("activo", true)
                if (!enabled) continue

                val endDate = item.optString("hasta_fecha", "").trim()
                if (endDate.isNotBlank()) {
                    val cleanEndDate = if (endDate.contains("T")) endDate.split("T")[0] else endDate
                    if (cleanEndDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        if (todayStr > cleanEndDate) {
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

                if (!activeOnly && oculto) {
                    continue
                }

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

            if (activeOnly && dismissedSet.isNotEmpty()) {
                val updatedDismissed = dismissedSet.filter { allRemoteIdsInJson.contains(it) }.toSet()
                if (updatedDismissed.size != dismissedSet.size) {
                    prefs.edit().putStringSet(KEY_DISMISSED_IDS, updatedDismissed).apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing remote announcements: ${e.message}")
        }
        return resultList
    }

    suspend fun fetchActiveAnnouncements(force: Boolean = false): List<RemoteAnnouncement> = withContext(Dispatchers.IO) {
        val rawJson = getOrFetchRawAnnouncementsJson(force) ?: return@withContext emptyList()
        parseAnnouncementsFromJson(rawJson, activeOnly = true)
    }

    suspend fun fetchAllAnnouncements(force: Boolean = false): List<RemoteAnnouncement> = withContext(Dispatchers.IO) {
        val rawJson = getOrFetchRawAnnouncementsJson(force) ?: return@withContext emptyList()
        parseAnnouncementsFromJson(rawJson, activeOnly = false)
    }
}
