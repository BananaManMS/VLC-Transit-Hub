package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.GitHubReleaseResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    private const val GITHUB_OWNER = "BananaManMS"
    private const val GITHUB_REPO = "VLC-Transit-Hub"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()
    private var activeDownloadCall: Call? = null

    /**
     * Deletes any old update APKs and temp files in the updates cache directory.
     * Safe to run on every application startup.
     */
    fun cleanOldUpdateApks(context: Context) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists() && updatesDir.isDirectory) {
                updatesDir.listFiles()?.forEach { file ->
                    try {
                        if (file.name.endsWith(".apk") || file.name.endsWith(".tmp")) {
                            file.delete()
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks GitHub API for the latest published release.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "VLC-Transit-Hub-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Error del servidor GitHub (${response.code})")
                )
            }

            val bodyString = response.body?.string()
                ?: return@withContext Result.failure(Exception("Respuesta vacía de GitHub"))

            val release = gson.fromJson(bodyString, GitHubReleaseResponse::class.java)
            val tagName = release.tagName?.trim() ?: ""
            val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V")

            // Look for an APK asset in the release
            val apkAsset = release.assets?.firstOrNull { asset ->
                asset.name?.endsWith(".apk", ignoreCase = true) == true
            }

            if (apkAsset == null || apkAsset.downloadUrl.isNullOrBlank()) {
                return@withContext Result.failure(
                    Exception("La versión $tagName existe pero no contiene archivo APK disponible.")
                )
            }

            val isNewer = isVersionNewer(cleanRemoteVersion, currentVersion)

            val updateInfo = AppUpdateInfo(
                latestVersionName = cleanRemoteVersion.ifBlank { tagName },
                releaseTitle = release.name ?: "Versión $tagName",
                releaseNotes = release.body ?: "",
                apkDownloadUrl = apkAsset.downloadUrl,
                apkFileName = apkAsset.name ?: "app-update.apk",
                apkSizeBytes = apkAsset.size,
                isNewerVersion = isNewer
            )

            Result.success(updateInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file to cache with real-time byte progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long, progress: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val targetApk = File(updatesDir, "update_v${updateInfo.latestVersionName}.apk")
        val tempApk = File(updatesDir, "update_v${updateInfo.latestVersionName}.apk.tmp")

        // Clean previous partial attempts
        if (tempApk.exists()) tempApk.delete()

        try {
            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "VLC-Transit-Hub-App")
                .build()

            val call = httpClient.newCall(request)
            activeDownloadCall = call
            val response = call.execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Fallo al descargar archivo APK (HTTP ${response.code})")
                )
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(Exception("Cuerpo de descarga vacío"))

            val totalBytes = if (responseBody.contentLength() > 0) {
                responseBody.contentLength()
            } else {
                updateInfo.apkSizeBytes
            }

            responseBody.byteStream().use { input ->
                FileOutputStream(tempApk).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val progress = if (totalBytes > 0) {
                            (totalDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        onProgress(totalDownloaded, totalBytes, progress)
                    }
                    output.flush()
                }
            }

            // Atomic rename of completed download
            if (targetApk.exists()) targetApk.delete()
            if (!tempApk.renameTo(targetApk)) {
                tempApk.copyTo(targetApk, overwrite = true)
                tempApk.delete()
            }

            activeDownloadCall = null
            Result.success(targetApk)
        } catch (e: Exception) {
            activeDownloadCall = null
            if (tempApk.exists()) tempApk.delete()
            Result.failure(e)
        }
    }

    /**
     * Cancels any active download in progress and cleans temporary files.
     */
    fun cancelActiveDownload(context: Context) {
        try {
            activeDownloadCall?.cancel()
            activeDownloadCall = null
            val updatesDir = File(context.cacheDir, "updates")
            updatesDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    /**
     * Triggers the Android package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("El archivo APK no existe o está incompleto"))
            }

            // Check if permission to install unknown apps is granted (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return Result.failure(
                        Exception("Por favor, concede permiso para instalar desde esta aplicación y pulsa de nuevo en Instalar.")
                    )
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Semantic version comparator. Handles versions like "v2.0.1" vs "2.0.0" or "2.1" vs "2.0.0".
     */
    internal fun isVersionNewer(remote: String, current: String): Boolean {
        try {
            val cleanRemote = remote.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")

            val remoteParts = cleanRemote.split("-", "+")[0].split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split("-", "+")[0].split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (_: Exception) {
            return remote != current
        }
    }
}
