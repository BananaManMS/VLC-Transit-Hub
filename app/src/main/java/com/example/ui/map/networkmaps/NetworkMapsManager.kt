package com.example.ui.map.networkmaps

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class MapFileStatus(
    val mapItem: MapPlanItem,
    val file: File,
    val exists: Boolean,
    val fileSizeBytes: Long = 0L,
    val formattedSize: String = "",
    val lastUpdatedTimestamp: Long = 0L
)

class NetworkMapsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("network_maps_prefs", Context.MODE_PRIVATE)

    private val mapsDir: File = File(context.filesDir, "network_maps").apply {
        if (!exists()) mkdirs()
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mapsStatus = MutableStateFlow<Map<String, MapFileStatus>>(emptyMap())
    val mapsStatus: StateFlow<Map<String, MapFileStatus>> = _mapsStatus.asStateFlow()

    init {
        cleanOrphanedFiles()
        refreshLocalFilesStatus()
    }

    fun getAllItems(): List<MapPlanItem> {
        return NetworkMapId.values().toList() + InterchangeMapId.values().toList()
    }

    fun getMapFile(mapItem: MapPlanItem): File {
        return File(mapsDir, mapItem.fileName)
    }

    fun areAllMapsDownloaded(): Boolean {
        return getAllItems().all { item ->
            val file = getMapFile(item)
            file.exists() && file.length() > 100
        }
    }

    /**
     * Deletes leftover temporary (.tmp) or obsolete/orphaned files to prevent
     * storage accumulation.
     */
    private fun cleanOrphanedFiles() {
        try {
            val validFileNames = getAllItems().map { it.fileName }.toSet()
            mapsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp") || (!file.isDirectory && file.name !in validFileNames)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun refreshLocalFilesStatus() {
        cleanOrphanedFiles()
        val map = mutableMapOf<String, MapFileStatus>()
        for (item in getAllItems()) {
            val file = getMapFile(item)
            val exists = file.exists() && file.length() > 100
            val size = if (exists) file.length() else 0L
            val sizeMb = if (exists) String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024.0)) else ""
            val lastMod = if (exists) file.lastModified() else 0L
            map[item.id] = MapFileStatus(
                mapItem = item,
                file = file,
                exists = exists,
                fileSizeBytes = size,
                formattedSize = sizeMb,
                lastUpdatedTimestamp = lastMod
            )
        }
        _mapsStatus.value = map
    }

    suspend fun checkAndSyncMaps(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
        val needsWeeklyCheck = (now - lastCheck) > sevenDaysMs
        val missingAny = !areAllMapsDownloaded()

        if (!forceRefresh && !needsWeeklyCheck && !missingAny) {
            refreshLocalFilesStatus()
            return@withContext
        }

        syncMapsInternal()
    }

    suspend fun forceRefreshAll() = withContext(Dispatchers.IO) {
        syncMapsInternal()
    }

    private suspend fun syncMapsInternal() = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext
        _isSyncing.value = true
        _errorMessage.value = null
        _syncProgress.value = 0f

        val allItems = getAllItems()
        val totalMaps = allItems.size
        var completedCount = 0

        try {
            for ((index, mapItem) in allItems.withIndex()) {
                val targetFile = getMapFile(mapItem)
                _statusText.value = "${mapItem.getTitle(com.example.ui.dashboard.AppLanguage.ES)} (${index + 1}/$totalMaps)"

                val savedEtag = prefs.getString("etag_${mapItem.id}", null)
                val savedLastMod = prefs.getString("lastmod_${mapItem.id}", null)

                var shouldDownload = !targetFile.exists() || targetFile.length() < 100

                // Quick HEAD check if file already exists
                if (!shouldDownload) {
                    try {
                        val headReqBuilder = Request.Builder()
                            .url(mapItem.url)
                            .head()
                            .header("User-Agent", NetworkModule.USER_AGENT)

                        if (!savedEtag.isNullOrBlank()) {
                            headReqBuilder.header("If-None-Match", savedEtag)
                        }
                        if (!savedLastMod.isNullOrBlank()) {
                            headReqBuilder.header("If-Modified-Since", savedLastMod)
                        }

                        val headResp = NetworkModule.okHttpClient.newCall(headReqBuilder.build()).execute()
                        headResp.use { resp ->
                            if (resp.code == 304) {
                                shouldDownload = false
                            } else if (resp.isSuccessful) {
                                val remoteEtag = resp.header("ETag")
                                val remoteLastMod = resp.header("Last-Modified")
                                val remoteLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L

                                if (remoteEtag != null && remoteEtag != savedEtag) {
                                    shouldDownload = true
                                } else if (remoteLength > 0 && remoteLength != targetFile.length()) {
                                    shouldDownload = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "HEAD request check failed for ${mapItem.id}, using existing file", e)
                        shouldDownload = false
                    }
                }

                if (shouldDownload) {
                    downloadMapFile(mapItem, targetFile)
                }

                completedCount++
                _syncProgress.value = completedCount.toFloat() / totalMaps
            }

            prefs.edit().putLong(KEY_LAST_CHECK_TIMESTAMP, System.currentTimeMillis()).apply()
            _statusText.value = ""
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing maps", e)
            _errorMessage.value = e.localizedMessage ?: "Error al descargar los planos"
        } finally {
            _isSyncing.value = false
            refreshLocalFilesStatus()
        }
    }

    private fun downloadMapFile(mapItem: MapPlanItem, targetFile: File) {
        val request = Request.Builder()
            .url(mapItem.url)
            .header("User-Agent", NetworkModule.USER_AGENT)
            .build()

        val response = NetworkModule.okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} al descargar ${mapItem.fileName}")
            }

            val body = resp.body ?: throw IllegalStateException("Cuerpo vacío para ${mapItem.fileName}")
            val tempFile = File(mapsDir, "${mapItem.fileName}.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 100) {
                // Replaces and deletes previous version completely
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)

                val etag = resp.header("ETag")
                val lastMod = resp.header("Last-Modified")
                prefs.edit()
                    .putString("etag_${mapItem.id}", etag)
                    .putString("lastmod_${mapItem.id}", lastMod)
                    .apply()
            } else {
                tempFile.delete()
                throw IllegalStateException("Descarga incompleta de ${mapItem.fileName}")
            }
        }
    }

    companion object {
        private const val TAG = "NetworkMapsManager"
        private const val KEY_LAST_CHECK_TIMESTAMP = "last_network_maps_check_timestamp"
    }
}
