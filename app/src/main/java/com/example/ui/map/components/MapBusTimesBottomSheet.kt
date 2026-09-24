package com.example.ui.map.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtBusTime
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import com.example.ui.transit.MapUnifiedTransitBottomSheet
import com.example.ui.transit.toUnifiedDeparture
import com.example.ui.transit.toUnifiedStop

@Composable
fun MapBusTimesBottomSheet(
    stop: EmtBusStop,
    busTimes: List<EmtBusTime>,
    busTimesLoading: Boolean,
    isDarkMode: Boolean,
    texts: Translation,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alias: String? = null,
    onEditAliasClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onDirectionsClick: (() -> Unit)? = null,
    maxExpandedHeight: Dp = 560.dp,
    sheetState: DetailSheetState = DetailSheetState.HALF_EXPANDED,
    onSheetStateChanged: (DetailSheetState) -> Unit = {},
    onHeightPxChanged: ((Float) -> Unit)? = null,
    selectedLineFilters: Set<String> = emptySet(),
    onLineFiltersChanged: ((Set<String>) -> Unit)? = null,
    scheduledDepartures: List<EmtBusTime> = emptyList(),
    isScheduledLoaded: Boolean = false,
    isScheduledLoading: Boolean = false,
    onLoadScheduled: () -> Unit = {},
    onLoadMoreScheduled: (() -> Unit)? = null,
    isLoadingMoreScheduled: Boolean = false,
    activeTripBottomPadding: Dp = 0.dp
) {
    val unifiedStop = remember(stop, alias, isFavorite) {
        stop.toUnifiedStop(alias = alias, isFavorite = isFavorite)
    }

    val liveDepartures = remember(busTimes) {
        busTimes.filter { it.isRealTime }.mapIndexed { index: Int, item: EmtBusTime -> item.toUnifiedDeparture(index) }
    }

    val scheduledList = remember(busTimes, scheduledDepartures) {
        if (scheduledDepartures.isNotEmpty()) {
            scheduledDepartures.mapIndexed { index: Int, item: EmtBusTime -> item.toUnifiedDeparture(index) }
        } else {
            busTimes.filter { !it.isRealTime }.mapIndexed { index: Int, item: EmtBusTime -> item.toUnifiedDeparture(index) }
        }
    }

    val appLang = if (texts.aboutUnderstood == "Entès") AppLanguage.CA else AppLanguage.ES

    MapUnifiedTransitBottomSheet(
        stop = unifiedStop,
        liveDepartures = liveDepartures,
        isLiveLoading = busTimesLoading,
        scheduledDepartures = scheduledList,
        isScheduledLoaded = isScheduledLoaded || scheduledList.isNotEmpty(),
        isScheduledLoading = isScheduledLoading || isLoadingMoreScheduled,
        isDarkMode = isDarkMode,
        appLanguage = appLang,
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
