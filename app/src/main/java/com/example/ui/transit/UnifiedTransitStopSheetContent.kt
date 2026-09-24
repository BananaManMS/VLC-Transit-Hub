package com.example.ui.transit

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.LiveRssFeedIcon
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage

@Composable
fun UnifiedTransitStopSheetContent(
    stop: UnifiedTransitStop,
    liveDepartures: List<UnifiedTransitDeparture>,
    isLiveLoading: Boolean,
    scheduledDepartures: List<UnifiedTransitDeparture>,
    isScheduledLoaded: Boolean,
    isScheduledLoading: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier,
    headerDragModifier: Modifier = Modifier,
    onHeaderHeightChanged: ((Float) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    selectedLineFilters: Set<String>? = null,
    onLineFiltersChanged: ((Set<String>) -> Unit)? = null,
    onDirectionsClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onEditAliasClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onLoadScheduled: () -> Unit,
    onLoadMoreScheduled: (() -> Unit)? = null,
    onCollapseScheduled: (() -> Unit)? = null,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    val titleColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF64748B)

    var internalSelectedFilters by remember(stop.id) { mutableStateOf<Set<String>>(emptySet()) }
    val effectiveFilters = selectedLineFilters ?: internalSelectedFilters

    val updateFilters: (Set<String>) -> Unit = { newFilters ->
        if (onLineFiltersChanged != null) {
            onLineFiltersChanged(newFilters)
        } else {
            internalSelectedFilters = newFilters
        }
    }

    // Combine lines from stop model and departures for comprehensive filter chips
    val allAvailableLines = remember(stop, liveDepartures, scheduledDepartures) {
        val fromStop = stop.availableLines
        val fromLive = liveDepartures.map { it.lineCode }
        val fromSched = scheduledDepartures.map { it.lineCode }
        (fromStop + fromLive + fromSched).filter { it.isNotBlank() }.distinct().sortedWith(compareBy {
            it.toIntOrNull() ?: Int.MAX_VALUE
        })
    }

    // Filter live and scheduled departures based on selected chips
    val filteredLiveDepartures = remember(liveDepartures, effectiveFilters) {
        if (effectiveFilters.isEmpty()) {
            liveDepartures
        } else {
            liveDepartures.filter { dep ->
                effectiveFilters.any { it.equals(dep.lineCode, ignoreCase = true) }
            }
        }
    }

    val filteredScheduledDepartures = remember(scheduledDepartures, effectiveFilters) {
        if (effectiveFilters.isEmpty()) {
            scheduledDepartures
        } else {
            scheduledDepartures.filter { dep ->
                effectiveFilters.any { it.equals(dep.lineCode, ignoreCase = true) }
            }
        }
    }

    val (divertedDepartures, activeLiveDepartures) = remember(filteredLiveDepartures) {
        filteredLiveDepartures.partition { it.isDiverted }
    }

    val divertedLines = remember(divertedDepartures) {
        divertedDepartures.map { it.lineCode }.filter { it.isNotBlank() }.distinct()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Stop Header
        UnifiedTransitStopHeader(
            stop = stop,
            isDarkMode = isDarkMode,
            appLanguage = appLanguage,
            headerDragModifier = headerDragModifier,
            onDirectionsClick = onDirectionsClick,
            onToggleFavorite = onToggleFavorite,
            onEditAliasClick = onEditAliasClick,
            onDismiss = onDismiss,
            modifier = Modifier.onSizeChanged { size ->
                val newH = size.height.toFloat()
                if (newH > 0f) {
                    onHeaderHeightChanged?.invoke(newH)
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Line Filter Row (if > 1 line)
        if (allAvailableLines.size > 1) {
            UnifiedLineFilterRow(
                availableLines = allAvailableLines,
                selectedLineFilters = effectiveFilters,
                operator = stop.operator,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                onToggleLineFilter = { line ->
                    val next = if (effectiveFilters.contains(line)) {
                        effectiveFilters - line
                    } else {
                        effectiveFilters + line
                    }
                    updateFilters(next)
                },
                onClearLineFilters = {
                    updateFilters(emptySet())
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Scrollable departures area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Real-Time Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                LiveRssFeedIcon(
                    contentDescription = null,
                    tint = Color(0xFF2ECC71),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (appLanguage == AppLanguage.CA) "Pròximes eixides" else "Próximas salidas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            }

            // Real-Time Section Content
            if (isLiveLoading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    repeat(2) {
                        SkeletonCardItem()
                    }
                }
            } else if (divertedLines.isNotEmpty() || activeLiveDepartures.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (divertedLines.isNotEmpty()) {
                        UnifiedDivertedAlertBanner(
                            divertedLines = divertedLines,
                            operator = stop.operator,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage
                        )
                    }

                    activeLiveDepartures.forEach { departure ->
                        UnifiedTransitDepartureCard(
                            departure = departure,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage
                        )
                    }
                }
            } else if (liveDepartures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isOnline) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isDarkMode) Color(0xFFEF5350) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "No es poden consultar les eixides en temps real." else "No se pueden consultar las salidas en tiempo real.",
                                style = MaterialTheme.typography.bodySmall,
                                color = subtextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "No hi ha eixides en temps real en este moment." else "No hay salidas en tiempo real en este momento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val filterText = effectiveFilters.joinToString(", ")
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "No hi ha eixides en temps real per a la línia $filterText." else "No hay salidas en tiempo real para la(s) línea(s) $filterText.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtextColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Separated Scheduled Departures Section
            UnifiedScheduledSection(
                isLoaded = isScheduledLoaded,
                isLoading = isScheduledLoading,
                scheduledDepartures = filteredScheduledDepartures,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                scrollState = scrollState,
                onLoadScheduled = onLoadScheduled,
                onLoadMoreScheduled = onLoadMoreScheduled,
                onCollapse = onCollapseScheduled,
                isOnline = isOnline
            )

            Spacer(modifier = Modifier.height(16.dp + activeTripBottomPadding))
        }
    }
}

@Composable
fun UnifiedDivertedAlertBanner(
    divertedLines: List<String>,
    operator: TransitOperator,
    isDarkMode: Boolean,
    appLanguage: AppLanguage
) {
    val isDark = isDarkMode || isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF3E2723) else Color(0xFFFFEBEE)
    val textColor = if (isDark) Color(0xFFFFAB91) else Color(0xFFC62828)
    val linesStr = divertedLines.joinToString(", ")
    val text = if (appLanguage == AppLanguage.CA) {
        "Avís: Línia(es) desviada(es): $linesStr"
    } else {
        "Aviso: Línea(s) desviada(s): $linesStr"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
