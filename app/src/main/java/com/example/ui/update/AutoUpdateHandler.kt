package com.example.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.UpdateDownloadState
import com.example.util.AppUpdateManager
import kotlinx.coroutines.delay

/**
 * Coordinates automatic background checking for updates on app launch.
 * Guarantees zero UI collision with:
 * - Startup Announcements ("Avisos" / Remote Announcements)
 * - First-launch Onboarding screens
 *
 * If active unread Avisos exist, the update prompt waits silently until the user
 * finishes reviewing and closes the Avisos dialog.
 */
@Composable
fun DashboardAutoUpdateHandler(
    appUpdateViewModel: AppUpdateViewModel,
    hasActiveRemoteAnnouncements: Boolean,
    isShowingOnboarding: Boolean,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val updateState by appUpdateViewModel.updateState.collectAsState()

    // Session dismissal state so dismissing the update prompt does not annoy the user repeatedly
    var dismissedInSession by rememberSaveable { mutableStateOf(false) }

    // On app startup, clean old cache APKs and run a background update check
    LaunchedEffect(Unit) {
        AppUpdateManager.cleanOldUpdateApks(context)
        // Short pause to allow initial startup network and repository loads to stabilize
        delay(1500L)
        appUpdateViewModel.checkForUpdate(silentIfUpToDate = true)
    }

    // Anti-collision logic:
    // Only display the update dialog when:
    // 1. Not in onboarding
    // 2. No active un-dismissed Avisos (Remote Announcements) dialog is showing
    // 3. User has not dismissed the prompt during this app session
    val canShowDialog = !isShowingOnboarding &&
        !hasActiveRemoteAnnouncements &&
        !dismissedInSession

    val shouldShow = canShowDialog && when (updateState) {
        is UpdateDownloadState.UpdateAvailable -> true
        is UpdateDownloadState.Downloading -> true
        is UpdateDownloadState.ReadyToInstall -> true
        else -> false
    }

    if (shouldShow) {
        AppUpdateDialog(
            viewModel = appUpdateViewModel,
            isDarkMode = isDarkMode,
            onDismiss = {
                dismissedInSession = true
                if (updateState is UpdateDownloadState.Downloading) {
                    appUpdateViewModel.cancelDownload(context)
                }
            }
        )
    }
}
