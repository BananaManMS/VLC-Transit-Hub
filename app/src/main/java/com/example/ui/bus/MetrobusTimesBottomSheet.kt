package com.example.ui.bus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ui.dashboard.AppLanguage
import com.example.ui.transit.UnifiedTransitModalBottomSheet
import com.example.ui.transit.toUnifiedDeparture
import com.example.ui.transit.toUnifiedStop

@Composable
fun MetrobusTimesBottomSheet(
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
    onRefresh: () -> Unit = {},
    scheduledDepartures: List<MetrobusDepartureUiModel> = emptyList(),
    isScheduledLoaded: Boolean = false,
    isScheduledLoading: Boolean = false,
    onLoadScheduled: () -> Unit = {},
    onLoadMoreScheduled: (() -> Unit)? = null,
    isLoadingMoreScheduled: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.ES
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

    UnifiedTransitModalBottomSheet(
        stop = unifiedStop,
        liveDepartures = liveDepartures,
        isLiveLoading = isLoading,
        scheduledDepartures = scheduledList,
        isScheduledLoaded = isScheduledLoaded || scheduledList.isNotEmpty(),
        isScheduledLoading = isScheduledLoading || isLoadingMoreScheduled,
        isDarkMode = isDarkMode,
        appLanguage = appLanguage,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onToggleFavorite = onToggleFavorite,
        onEditAliasClick = onEditAliasClick,
        onLoadScheduled = onLoadScheduled,
        onLoadMoreScheduled = onLoadMoreScheduled
    )
}
