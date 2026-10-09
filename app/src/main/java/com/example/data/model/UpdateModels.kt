package com.example.data.model

import com.google.gson.annotations.SerializedName
import java.io.File

data class GitHubReleaseResponse(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("assets") val assets: List<GitHubAssetResponse>? = null
)

data class GitHubAssetResponse(
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("browser_download_url") val downloadUrl: String? = null,
    @SerializedName("content_type") val contentType: String? = null
)

data class AppUpdateInfo(
    val latestVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val isNewerVersion: Boolean
)

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data object Checking : UpdateDownloadState()
    data class UpToDate(val currentVersion: String) : UpdateDownloadState()
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateDownloadState()
    data class Downloading(
        val updateInfo: AppUpdateInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    data class ReadyToInstall(
        val updateInfo: AppUpdateInfo,
        val apkFile: File
    ) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}
