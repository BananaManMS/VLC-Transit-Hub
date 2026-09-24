package com.example.ui.transit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.dashboard.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedTransitModalBottomSheet(
    stop: UnifiedTransitStop,
    liveDepartures: List<UnifiedTransitDeparture>,
    isLiveLoading: Boolean,
    scheduledDepartures: List<UnifiedTransitDeparture>,
    isScheduledLoaded: Boolean,
    isScheduledLoading: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onDirectionsClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onEditAliasClick: (() -> Unit)? = null,
    onLoadScheduled: () -> Unit,
    onLoadMoreScheduled: (() -> Unit)? = null,
    onCollapseScheduled: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sheetBg = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
            .statusBarsPadding()
            .testTag("unified_transit_modal_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            UnifiedTransitStopSheetContent(
                stop = stop,
                liveDepartures = liveDepartures,
                isLiveLoading = isLiveLoading,
                scheduledDepartures = scheduledDepartures,
                isScheduledLoaded = isScheduledLoaded,
                isScheduledLoading = isScheduledLoading,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                onDirectionsClick = onDirectionsClick,
                onToggleFavorite = onToggleFavorite,
                onEditAliasClick = onEditAliasClick,
                onDismiss = onDismissRequest,
                onLoadScheduled = onLoadScheduled,
                onLoadMoreScheduled = onLoadMoreScheduled,
                onCollapseScheduled = onCollapseScheduled
            )
        }
    }
}
