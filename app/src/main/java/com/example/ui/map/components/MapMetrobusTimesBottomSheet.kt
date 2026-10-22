package com.example.ui.map.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.bus.MetrobusDepartureUiModel
import com.example.ui.bus.MetrobusStop
import com.example.ui.dashboard.AppLanguage
import com.example.ui.transit.MapUnifiedTransitBottomSheet
import com.example.ui.transit.toUnifiedDeparture
import com.example.ui.transit.toUnifiedStop

@Composable
fun MapMetrobusTimesBottomSheet(
    stop: MetrobusStop,
    times: List<MetrobusDepartureUiModel>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alias: String? = null,
    onEditAliasClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onDirectionsClick: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    maxExpandedHeight: Dp = 560.dp,
    sheetState: DetailSheetState = DetailSheetState.HALF_EXPANDED,
    onSheetStateChanged: (DetailSheetState) -> Unit = {},
    onHeightPxChanged: ((Float) -> Unit)? = null,
    selectedLineFilters: Set<String> = emptySet(),
    onLineFiltersChanged: ((Set<String>) -> Unit)? = null,
    scheduledDepartures: List<MetrobusDepartureUiModel> = emptyList(),
    isScheduledLoaded: Boolean = false,
    isScheduledLoading: Boolean = false,
    onLoadScheduled: () -> Unit = {},
    onLoadMoreScheduled: (() -> Unit)? = null,
    isLoadingMoreScheduled: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.ES,
    activeTripBottomPadding: Dp = 0.dp
) {
    val unifiedStop = remember(stop, alias, isFavorite) {
        stop.toUnifiedStop(alias = alias, isFavorite = isFavorite)
    }

    val liveDepartures = remember(times) {
        times.filter { it.isRealTime }.mapIndexed { index: Int, item: MetrobusDepartureUiModel -> item.toUnifiedDeparture(index) }
    }

    val scheduledList = remember(times, scheduledDepartures) {
        if (scheduledDepartures.isNotEmpty()) {
            scheduledDepartures.mapIndexed { index: Int, item: MetrobusDepartureUiModel -> item.toUnifiedDeparture(index) }
        } else {
            times.filter { !it.isRealTime }.mapIndexed { index: Int, item: MetrobusDepartureUiModel -> item.toUnifiedDeparture(index) }
        }
    }

    MapUnifiedTransitBottomSheet(
        stop = unifiedStop,
        liveDepartures = liveDepartures,
        isLiveLoading = isLoading,
        scheduledDepartures = scheduledList,
        isScheduledLoaded = isScheduledLoaded || scheduledList.isNotEmpty(),
        isScheduledLoading = isScheduledLoading || isLoadingMoreScheduled,
        isDarkMode = isDarkMode,
        appLanguage = appLanguage,
        modifier = modifier,
        onDirectionsClick = onDirectionsClick,
        onToggleFavorite = onToggleFavorite,
        onEditAliasClick = onEditAliasClick,
        onDismiss = onDismissRequest,
        maxExpandedHeight = maxExpandedHeight,
        sheetState = sheetState,
        onSheetStateChanged = onSheetStateChanged,
        onHeightPxChanged = onHeightPxChanged,
        selectedLineFilters = selectedLineFilters,
        onLineFiltersChanged = onLineFiltersChanged,
        onLoadScheduled = onLoadScheduled,
        onLoadMoreScheduled = onLoadMoreScheduled,
        activeTripBottomPadding = activeTripBottomPadding
    )
}
