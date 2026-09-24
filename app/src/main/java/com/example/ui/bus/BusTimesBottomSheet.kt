package com.example.ui.bus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import com.example.ui.transit.UnifiedTransitModalBottomSheet
import com.example.ui.transit.toUnifiedDeparture
import com.example.ui.transit.toUnifiedStop

@Composable
fun BusTimesBottomSheet(
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
    scheduledDepartures: List<EmtBusTime> = emptyList(),
    isScheduledLoaded: Boolean = false,
    isScheduledLoading: Boolean = false,
    onLoadScheduled: () -> Unit = {},
    onLoadMoreScheduled: (() -> Unit)? = null,
    isLoadingMoreScheduled: Boolean = false
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

    UnifiedTransitModalBottomSheet(
        stop = unifiedStop,
        liveDepartures = liveDepartures,
        isLiveLoading = busTimesLoading,
        scheduledDepartures = scheduledList,
        isScheduledLoaded = isScheduledLoaded || scheduledList.isNotEmpty(),
        isScheduledLoading = isScheduledLoading || isLoadingMoreScheduled,
        isDarkMode = isDarkMode,
        appLanguage = appLang,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onToggleFavorite = onToggleFavorite,
        onEditAliasClick = onEditAliasClick,
        onLoadScheduled = onLoadScheduled,
        onLoadMoreScheduled = onLoadMoreScheduled
    )
}
