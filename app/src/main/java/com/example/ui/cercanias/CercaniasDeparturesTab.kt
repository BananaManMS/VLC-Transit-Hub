package com.example.ui.cercanias

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.core.graphics.toColorInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.components.CercaniasDepartureSkeletonCard
import com.example.ui.components.TransitPullRefreshIndicator
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation

private val TIME_REGEX = Regex("""\b(\d{1,2})[:.](\d{2})\s*h?\b""")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CercaniasDeparturesTab(
    viewModel: CercaniasViewModel,
    selectedStationId: String,
    departures: List<CercaniasDeparture>,
    isLoading: Boolean,
    error: String?,
    favoriteStations: List<CercaniasStationEntity>,
    allCercaniasStations: List<CercaniasStationEntity>,
    cercaniasAlerts: List<CercaniasAlert>,
    accessibilityAlerts: List<CercaniasAlert>,
    isOnline: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    texts: Translation,
    activeTripBottomPadding: Dp = 0.dp,
    onOpenSearchDialog: () -> Unit,
    onSelectDeparture: (CercaniasDeparture) -> Unit
) {
    var showQuickPicker by remember { mutableStateOf(false) }

    val displayStations = remember(favoriteStations, selectedStationId, allCercaniasStations) {
        if (selectedStationId.isNotEmpty() && favoriteStations.none { it.id == selectedStationId || it.stop_id == selectedStationId }) {
            val tempStation = allCercaniasStations.find { it.id == selectedStationId || it.stop_id == selectedStationId }
            if (tempStation != null) {
                listOf(tempStation) + favoriteStations
            } else {
                favoriteStations
            }
        } else {
            favoriteStations
        }
    }

    val selectedStationEntity = remember(allCercaniasStations, selectedStationId) {
        allCercaniasStations.find { it.stop_id == selectedStationId || it.id == selectedStationId }
    }
    val stationDisplayName = selectedStationEntity?.displayName ?: selectedStationEntity?.nombre ?: "Estación"

    val textColor = MaterialTheme.colorScheme.onSurface
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    if (showQuickPicker) {
        CercaniasQuickStationPickerDialog(
            viewModel = viewModel,
            onDismiss = { showQuickPicker = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Favoritos Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_my_fav_stations),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = accentColor
            )
            IconButton(
                onClick = onOpenSearchDialog,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("cercanias_add_favorites_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.edit_favorites_desc),
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Horizontal Row of Favorite Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                val cardBgColor = MaterialTheme.colorScheme.surface
                Card(
                    modifier = Modifier
                        .width(48.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showQuickPicker = true }
                        .testTag("square_cercanias_picker_button"),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.search_station_label),
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (displayStations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showQuickPicker = true }
                            .testTag("empty_favorite_cercanias_chip"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = texts.searchPlaceholder,
                                fontSize = 12.sp,
                                color = subtextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                items(displayStations, key = { it.id }) { station ->
                    val isSelected = station.stop_id == selectedStationId || station.id == selectedStationId
                    val cardBgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    val borderStroke = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    val animatedCornerRadius by animateDpAsState(
                        targetValue = if (isSelected) 8.dp else 18.dp,
                        animationSpec = tween(durationMillis = 500),
                        label = "fav_cercanias_selector_corner"
                    )

                    Card(
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 160.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(animatedCornerRadius))
                            .clickable { viewModel.selectCercaniasStation(station.stop_id.ifBlank { station.id }) }
                            .testTag("favorite_cercanias_chip_${station.id}"),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = borderStroke,
                        shape = RoundedCornerShape(animatedCornerRadius)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = station.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else textColor,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                val distanceText = remember(station) { viewModel.getCercaniasStationDistanceText(station) }
                                if (distanceText != null) {
                                    Text(
                                        text = distanceText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else subtextColor,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val linesList = station.lines.filter { it.isNotBlank() }
                                linesList.forEach { line ->
                                    val colorHex = com.example.util.LineColorResolver.getCercaniasLineColorHex(line)
                                    Surface(
                                        color = Color(android.graphics.Color.parseColor(colorHex)),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(14.dp)
                                    ) {
                                        Text(
                                            text = line,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Line filter selector chips for Cercanías
        val availableLines = remember(departures, selectedStationEntity) {
            val stationLines = selectedStationEntity?.lines
                ?.map { it.uppercase().replace("-", "").trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val departureLines = departures
                .map { it.routeId.uppercase().replace("-", "").trim() }
                .filter { it.isNotBlank() }
            (stationLines + departureLines).distinct().sorted()
        }
        val selectedLineFilters by viewModel.selectedCercaniasLineFilters.collectAsState()

        if (availableLines.size > 1) {
            val horizontalScrollBoundaryLock = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset = Offset(x = available.x, y = 0f)
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(horizontalScrollBoundaryLock)
                    .padding(top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    val isAllSelected = selectedLineFilters.isEmpty()
                    Surface(
                        onClick = { viewModel.clearCercaniasLineFilters() },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isAllSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkMode) 0.85f else 0.9f)
                        } else {
                            if (isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (isAllSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("cercanias_filter_all")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRailway,
                                contentDescription = null,
                                tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.bus_line_filter_all),
                                fontSize = 12.5.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                items(availableLines, key = { it }) { line ->
                    val isSelected = selectedLineFilters.contains(line)
                    val isAnySelected = selectedLineFilters.isNotEmpty()
                    val lineHex = com.example.util.LineColorResolver.getCercaniasLineColorHex(line)
                    val lineColor = remember(lineHex) {
                        try {
                            Color(lineHex.toColorInt())
                        } catch (_: Exception) {
                            Color(0xFF702B7B)
                        }
                    }
                    val displayLine = com.example.ui.metro.TransitLogoUtils.getCercaniasDisplayName(line)

                    Surface(
                        onClick = { viewModel.toggleCercaniasLineFilter(line) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            lineColor.copy(alpha = if (isDarkMode) 0.28f else 0.15f)
                        } else {
                            if (isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, lineColor)
                        } else {
                            BorderStroke(1.dp, if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .alpha(if (isAnySelected && !isSelected) 0.55f else 1f)
                            .testTag("cercanias_filter_$line")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            com.example.ui.metro.CercaniasLineBadge(
                                routeId = line,
                                size = 24.dp
                            )
                        }
                    }
                }
            }
        }

        // Pull to refresh + List of Departures
        val pullToRefreshState = rememberPullToRefreshState()
        val (isPullRefreshing, triggerCercaniasRefresh) = com.example.ui.components.rememberPullRefreshFeedback(
            isLoading = isLoading,
            onRefresh = { viewModel.refreshCercaniasAll() }
        )
        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = triggerCercaniasRefresh,
            state = pullToRefreshState,
            indicator = {
                TransitPullRefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isPullRefreshing,
                    stationName = stationDisplayName,
                    operatorName = "Renfe Cercanías",
                    appLanguage = appLanguage,
                    isInitialLoad = departures.isEmpty()
                )
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (isLoading && departures.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(5) {
                        CercaniasDepartureSkeletonCard()
                    }
                }
            } else if (!isLoading && error != null && departures.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.fetchCercaniasDepartures() },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_retry))
                    }
                }
            } else {
                val filteredList = remember(departures, selectedLineFilters) {
                    if (selectedLineFilters.isEmpty()) {
                        departures
                    } else {
                        departures.filter { selectedLineFilters.contains(it.routeId.uppercase().replace("-", "").trim()) }
                    }
                }

                if (filteredList.isEmpty()) {
                    val matchingAlerts = remember(cercaniasAlerts, selectedLineFilters, selectedStationEntity) {
                        val stationLines = selectedStationEntity?.lines
                            ?.map { it.uppercase().replace("-", "").trim() }
                            ?.filter { it.isNotBlank() }
                            ?: emptyList()
                        cercaniasAlerts.filter { alert ->
                            if (selectedLineFilters.isNotEmpty()) {
                                alert.routeIds.any { alertRoute ->
                                    selectedLineFilters.contains(alertRoute.uppercase().replace("-", "").trim())
                                }
                            } else {
                                alert.stopIds.contains(selectedStationId) ||
                                selectedStationEntity?.let { alert.stopIds.contains(it.stop_id) || alert.stopIds.contains(it.id) } == true ||
                                alert.routeIds.any { alertRoute ->
                                    stationLines.any { sLine ->
                                        alertRoute.uppercase().replace("-", "").trim() == sLine
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 24.dp, bottom = 16.dp + activeTripBottomPadding)
                    ) {
                        item {
                            Icon(
                                imageVector = Icons.Default.DirectionsRailway,
                                contentDescription = null,
                                tint = subtextColor,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedLineFilters.isNotEmpty()) {
                                    if (selectedLineFilters.size == 1) {
                                        "No hay trenes programados para la línea ${selectedLineFilters.first()} en las próximas 24h."
                                    } else {
                                        "No hay trenes programados para las líneas seleccionadas en las próximas 24h."
                                    }
                                } else {
                                    "No hay trenes programados para esta estación en las próximas 24h."
                                },
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Revisa los avisos activos a continuación.",
                                color = subtextColor,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }

                        if (matchingAlerts.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedLineFilters.isNotEmpty()) "AVISOS DE LA LÍNEA" else "AVISOS DE LA ESTACIÓN",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            items(matchingAlerts, key = { "filtered_alert_${it.id}" }) { alert ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    CercaniasNoticeFilteredCard(
                                        alert = alert,
                                        isDarkMode = isDarkMode,
                                        appLanguage = appLanguage
                                    )
                                }
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = "No hay avisos activos en este momento.",
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center,
                                        fontSize = 13.sp,
                                        color = subtextColor
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val todayDepartures = remember(filteredList) { filteredList.filter { !it.isTomorrow } }
                    val tomorrowDepartures = remember(filteredList) { filteredList.filter { it.isTomorrow } }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 88.dp + activeTripBottomPadding)
                    ) {
                        if (todayDepartures.isNotEmpty()) {
                            items(todayDepartures, key = { "${it.tripId}_${it.routeId}_${it.departureTime}_today" }) { departure ->
                                CercaniasDepartureCard(
                                    departure = departure,
                                    alerts = cercaniasAlerts,
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onClick = { onSelectDeparture(departure) }
                                )
                            }
                        }

                        if (tomorrowDepartures.isNotEmpty()) {
                            item(key = "header_tomorrow_departures") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (todayDepartures.isNotEmpty()) 12.dp else 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(end = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(com.example.R.string.calendar_filter_tomorrow),
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant,
                                        thickness = 1.dp
                                    )
                                }
                            }

                            items(tomorrowDepartures, key = { "${it.tripId}_${it.routeId}_${it.departureTime}_tomorrow" }) { departure ->
                                CercaniasDepartureCard(
                                    departure = departure,
                                    alerts = cercaniasAlerts,
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onClick = { onSelectDeparture(departure) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CercaniasNoticeFilteredCard(
    alert: CercaniasAlert,
    isDarkMode: Boolean,
    appLanguage: AppLanguage
) {
    val catEnum = remember(alert.headerEs, alert.descriptionEs) {
        CercaniasNoticeCategory.resolveFromText(alert.headerEs, alert.descriptionEs)
    }
    val badgeCategoryName = catEnum.getDisplayName(appLanguage)
    val badgeColor = catEnum.getColor(isDarkMode)
    val badgeIcon = catEnum.icon
    val isUrgent = catEnum == CercaniasNoticeCategory.SUPRESION || catEnum == CercaniasNoticeCategory.INCIDENCIA

    val cleanHeader = remember(alert.headerEs) { sanitizeRenfeNoticeText(alert.headerEs) }
    val cleanDescription = remember(alert.descriptionEs) { sanitizeRenfeNoticeText(alert.descriptionEs) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cercanias_filtered_notice_card_${alert.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) {
                com.example.ui.theme.AppThemeColors.statusErrorContainer(isDarkMode)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUrgent) badgeColor.copy(alpha = 0.4f) else if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeCategoryName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                if (alert.routeIds.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        alert.routeIds.forEach { lineId ->
                            com.example.ui.metro.CercaniasLineBadge(routeId = lineId, size = 20.dp)
                        }
                    }
                }
            }

            if (cleanHeader.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = cleanHeader,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (cleanDescription.isNotBlank() && !cleanDescription.equals(cleanHeader, ignoreCase = true)) {
                Spacer(modifier = Modifier.height(6.dp))
                com.example.ui.components.LinkifiedText(
                    text = cleanDescription,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
