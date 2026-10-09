package com.example.ui.update

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppUpdateInfo
import com.example.data.model.UpdateDownloadState
import com.example.ui.theme.UnifiedAppCard
import com.example.util.AppUpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class AppUpdateViewModel : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateState: StateFlow<UpdateDownloadState> = _updateState.asStateFlow()

    private var downloadedApkFile: File? = null

    fun checkForUpdate(silentIfUpToDate: Boolean = false) {
        viewModelScope.launch {
            if (!silentIfUpToDate) {
                _updateState.value = UpdateDownloadState.Checking
            }
            val result = AppUpdateManager.checkForUpdate()
            result.onSuccess { info ->
                if (info.isNewerVersion) {
                    _updateState.value = UpdateDownloadState.UpdateAvailable(info)
                } else {
                    _updateState.value = if (silentIfUpToDate) {
                        UpdateDownloadState.Idle
                    } else {
                        UpdateDownloadState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                }
            }.onFailure { error ->
                _updateState.value = if (silentIfUpToDate) {
                    UpdateDownloadState.Idle
                } else {
                    UpdateDownloadState.Error(
                        error.message ?: "No se pudo conectar con GitHub"
                    )
                }
            }
        }
    }

    fun startDownload(context: Context, info: AppUpdateInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateDownloadState.Downloading(
                updateInfo = info,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = info.apkSizeBytes
            )

            val result = AppUpdateManager.downloadApk(context, info) { downloaded, total, progress ->
                _updateState.value = UpdateDownloadState.Downloading(
                    updateInfo = info,
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }

            result.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                _updateState.value = UpdateDownloadState.ReadyToInstall(info, apkFile)
                // Proactively attempt install
                val installResult = AppUpdateManager.installApk(context, apkFile)
                installResult.onFailure { err ->
                    Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                _updateState.value = UpdateDownloadState.Error(
                    error.message ?: "Fallo durante la descarga"
                )
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val result = AppUpdateManager.installApk(context, apkFile)
        result.onFailure { err ->
            Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
        }
    }

    fun cancelDownload(context: Context) {
        AppUpdateManager.cancelActiveDownload(context)
        _updateState.value = UpdateDownloadState.Idle
    }

    fun resetState() {
        _updateState.value = UpdateDownloadState.Idle
    }
}

@Composable
fun AppUpdateSettingCard(
    viewModel: AppUpdateViewModel,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary

    val statusSubtitle = when (val state = updateState) {
        is UpdateDownloadState.Checking -> stringResource(R.string.update_status_checking)
        is UpdateDownloadState.Downloading -> {
            val pct = (state.progress * 100).toInt()
            "${stringResource(R.string.update_downloading_title)} $pct%"
        }
        is UpdateDownloadState.ReadyToInstall -> stringResource(R.string.update_ready_title)
        is UpdateDownloadState.UpdateAvailable -> {
            stringResource(R.string.update_status_available, state.updateInfo.latestVersionName)
        }
        is UpdateDownloadState.UpToDate -> {
            stringResource(R.string.update_status_up_to_date, state.currentVersion)
        }
        is UpdateDownloadState.Error -> state.message
        UpdateDownloadState.Idle -> {
            stringResource(R.string.update_check_subtitle, BuildConfig.VERSION_NAME)
        }
    }

    UnifiedAppCard(
        modifier = modifier.testTag("app_update_setting_card"),
        onClick = {
            when (updateState) {
                is UpdateDownloadState.Idle, is UpdateDownloadState.UpToDate, is UpdateDownloadState.Error -> {
                    viewModel.checkForUpdate()
                    showDialog = true
                }
                else -> {
                    showDialog = true
                }
            }
        },
        startContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) Color(0xFF1E283F) else Color(0xFFE8EAF6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        centerContent = {
            Column {
                Text(
                    text = stringResource(R.string.update_check_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (updateState is UpdateDownloadState.UpdateAvailable) accentColor else subtextColor
                )

                if (updateState is UpdateDownloadState.Downloading) {
                    val progress = (updateState as UpdateDownloadState.Downloading).progress
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)
                    )
                }
            }
        },
        endContent = {
            if (updateState is UpdateDownloadState.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            } else {
                IconButton(
                    onClick = {
                        viewModel.checkForUpdate()
                        showDialog = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.update_btn_check),
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )

    if (showDialog) {
        AppUpdateDialog(
            viewModel = viewModel,
            isDarkMode = isDarkMode,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun AppUpdateDialog(
    viewModel: AppUpdateViewModel,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.updateState.collectAsState()

    val surfaceColor = if (isDarkMode) Color(0xFF1E2433) else MaterialTheme.colorScheme.surface
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = surfaceColor,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.update_check_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (val current = state) {
                    is UpdateDownloadState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.update_status_checking),
                                color = textColor,
                                fontSize = 14.sp
                            )
                        }
                    }

                    is UpdateDownloadState.UpToDate -> {
                        Text(
                            text = stringResource(R.string.update_status_up_to_date, current.currentVersion),
                            color = textColor,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.btn_close), color = accentColor)
                            }
                        }
                    }

                    is UpdateDownloadState.UpdateAvailable -> {
                        val info = current.updateInfo
                        Text(
                            text = stringResource(R.string.update_status_available, info.latestVersionName),
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )

                        if (info.apkSizeBytes > 0) {
                            val mb = String.format(Locale.US, "%.1f MB", info.apkSizeBytes / (1024.0 * 1024.0))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.update_size_format, mb),
                                color = subtextColor,
                                fontSize = 12.sp
                            )
                        }

                        if (info.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.update_release_notes_title),
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(
                                        if (isDarkMode) Color(0xFF141822) else Color(0xFFF1F3F5),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = info.releaseNotes,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.btn_cancel), color = subtextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.startDownload(context, info) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text(stringResource(R.string.update_btn_download_install), color = Color.White)
                            }
                        }
                    }

                    is UpdateDownloadState.Downloading -> {
                        val pct = (current.progress * 100).toInt()
                        val downloadedMb = String.format(Locale.US, "%.1f", current.downloadedBytes / (1024.0 * 1024.0))
                        val totalMb = String.format(Locale.US, "%.1f MB", current.totalBytes / (1024.0 * 1024.0))

                        Text(
                            text = stringResource(R.string.update_downloading_progress, pct, downloadedMb, totalMb),
                            color = textColor,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { current.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accentColor,
                            trackColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.cancelDownload(context) }) {
                                Text(stringResource(R.string.btn_cancel), color = subtextColor)
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Text(
                            text = stringResource(R.string.update_ready_desc),
                            color = textColor,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.btn_cancel), color = subtextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.installApk(context, current.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text(stringResource(R.string.update_btn_install), color = Color.White)
                            }
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Text(
                            text = current.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.btn_close), color = subtextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.checkForUpdate() },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text(stringResource(R.string.update_btn_check), color = Color.White)
                            }
                        }
                    }

                    UpdateDownloadState.Idle -> {
                        Text(
                            text = stringResource(R.string.update_check_subtitle, BuildConfig.VERSION_NAME),
                            color = textColor,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.btn_close), color = subtextColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.checkForUpdate() },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text(stringResource(R.string.update_btn_check), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
