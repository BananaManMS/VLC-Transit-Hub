package com.example.ui.metro
import com.example.data.model.MetroScheduledDeparture
import com.example.ui.components.MetroDepartureSkeletonCard
import com.example.ui.components.SkeletonCardItem
import com.example.ui.components.TransitPullRefreshIndicator
import com.example.ui.components.StationAccessibilityBadge
import com.example.ui.components.computeMetroStationAccessibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt
import com.example.data.model.ValenciaMetroData
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

import com.example.service.QuickVehicleTrackerManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MetroDeparturesTab(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    favoriteStations: List<String>,
    selectedStationId: String,
    departures: List<RealTimeDeparture>,
    isLoading: Boolean,
    error: String?,
    isDarkMode: Boolean,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    ProximosTrenesScreen(
        appLanguage = appLanguage,
        metroViewModel = metroViewModel,
        favoriteStations = favoriteStations,
        selectedStationId = selectedStationId,
        departures = departures,
        isLoading = isLoading,
        error = error,
        isDarkMode = isDarkMode,
        activeTripBottomPadding = activeTripBottomPadding
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProximosTrenesScreen(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    favoriteStations: List<String>,
    selectedStationId: String,
    departures: List<RealTimeDeparture>,
    isLoading: Boolean,
    error: String?,
    isDarkMode: Boolean,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val isBottomSheetVisible by metroViewModel.isBottomSheetVisible.collectAsState()
    val selectedDepartureDetails by metroViewModel.selectedDepartureForDetails.collectAsState()
    val lineStationsMap by metroViewModel.lineStationsState.collectAsState()
    val activeIncidents by metroViewModel.activeIncidents.collectAsState()
    val stationAforoBloqueado by metroViewModel.stationAforoBloqueado.collectAsState()
    val accessibilityIncidents by metroViewModel.accessibilityIncidents.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    val metroScheduleViewModel: MetroScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scheduledDepartures by metroScheduleViewModel.scheduledDepartures.collectAsState()
    val isLoadingScheduled by metroScheduleViewModel.isLoadingScheduled.collectAsState()
    val isScheduledSheetVisible by metroScheduleViewModel.isScheduledSheetVisible.collectAsState()
    val inlineTheoreticalDepartures by metroScheduleViewModel.inlineTheoreticalDepartures.collectAsState()
    val isLoadingInlineTheoretical by metroScheduleViewModel.isLoadingInlineTheoretical.collectAsState()
    val isInlineTheoreticalLoaded by metroScheduleViewModel.isInlineTheoreticalLoaded.collectAsState()
    val selectedLineFilter by metroScheduleViewModel.selectedLineFilter.collectAsState()
    val availableLines by metroScheduleViewModel.availableLines.collectAsState()

    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
    val selectedStation = remember(allNetworkStations, selectedStationId) {
        allNetworkStations.find { it.id == selectedStationId }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(selectedStationId) {
        scrollState.scrollTo(0)
        metroScheduleViewModel.resetInlineTheoreticalDepartures()
        metroScheduleViewModel.dismissScheduledDepartures()
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickPicker by remember { mutableStateOf(false) }
    var quickTrackDeparture by remember { mutableStateOf<RealTimeDeparture?>(null) }
    var showQuickTrackSheet by remember { mutableStateOf(false) }
    var expiredDepartureIds by remember(departures) { mutableStateOf(setOf<String>()) }
    val visibleDepartures = remember(departures, expiredDepartureIds) {
        departures.filter { it.id !in expiredDepartureIds }
    }

    // If all tracked departures have expired naturally over time, trigger auto-refresh for new incoming trains
    LaunchedEffect(departures, visibleDepartures.isEmpty()) {
        if (departures.isNotEmpty() && visibleDepartures.isEmpty()) {
            metroViewModel.fetchRealTimeDepartures(selectedStationId, isAutoRefresh = true)
        }
    }

    val hasCompletedInitialLiveFetch by metroViewModel.hasCompletedInitialLiveFetch.collectAsState()

    // Auto-load scheduled departures only after live departures API has actually completed and returned empty or failed
    LaunchedEffect(selectedStationId, isLoading, hasCompletedInitialLiveFetch, visibleDepartures.isEmpty(), error, isInlineTheoreticalLoaded, isLoadingInlineTheoretical) {
        if (!isLoading && hasCompletedInitialLiveFetch && (visibleDepartures.isEmpty() || error != null) && !isInlineTheoreticalLoaded && !isLoadingInlineTheoretical) {
            val stId = selectedStation?.id ?: selectedStationId
            val stName = selectedStation?.name ?: "Estación"
            metroScheduleViewModel.loadInlineTheoreticalDepartures(stId, stName)
        }
    }

    // Dynamic minute ticker to prune scheduled departures continuously as time passes (active only if scheduled trains are displayed)
    var currentTimeMinutes by remember {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
        mutableIntStateOf(cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE))
    }
    LaunchedEffect(isInlineTheoreticalLoaded, isScheduledSheetVisible) {
        if (isInlineTheoreticalLoaded || isScheduledSheetVisible) {
            while (isActive) {
                delay(30_000L)
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
                val newMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                if (newMin != currentTimeMinutes) {
                    currentTimeMinutes = newMin
                }
            }
        }
    }

    // Deduplicate scheduled departures against active live departures dynamically and prune past departures
    val deduplicatedScheduledDepartures = remember(inlineTheoreticalDepartures, visibleDepartures, selectedLineFilter, currentTimeMinutes) {
        MetroScheduleDeduplicator.deduplicate(
            scheduledDepartures = inlineTheoreticalDepartures,
            liveDepartures = visibleDepartures,
            referenceMinutesOfDay = currentTimeMinutes,
            lineFilter = selectedLineFilter
        )
    }

    // Natural pull-up stretch state attached to list scrolling
    val pullUpStretchState = rememberMetroPullUpStretchState(
        thresholdDp = 72.dp,
        isEnabled = !isInlineTheoreticalLoaded && !isLoadingInlineTheoretical,
        onTrigger = {
            val stId = selectedStation?.id ?: selectedStationId
            val stName = selectedStation?.name ?: "Estación"
            metroScheduleViewModel.loadInlineTheoreticalDepartures(stId, stName)
        }
    )

    val accentColor = MaterialTheme.colorScheme.primary
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val stationIncidents = remember(selectedStation, activeIncidents) {
        if (selectedStation == null) emptyList()
        else {
            selectedStation.lines.flatMap { lineId ->
                metroViewModel.getIncidentsForLine(lineId)
            }.distinctBy { it.id }
        }
    }
    val stationIncidentLines = remember(selectedStation, stationIncidents) {
        if (selectedStation == null || stationIncidents.isEmpty()) emptyList<String>()
        else {
            val set = mutableSetOf<String>()
            for (lineId in selectedStation.lines) {
                if (metroViewModel.getIncidentsForLine(lineId).isNotEmpty()) {
                    set.add(lineId.replace("L", "", ignoreCase = true).trim())
                }
            }
            set.toList().sorted()
        }
    }
    val stationDisplayName = selectedStation?.name ?: "Selecciona una estación"
    val isStationInfoExpanded by metroViewModel.isStationInfoExpanded.collectAsState()

    val isSingleLineStation = remember(selectedStation) {
        selectedStation != null && selectedStation.lines.size == 1
    }

    val isMixedMetroTramStation = remember(selectedStation) {
        if (selectedStation == null) false
        else {
            val lines = selectedStation.lines
            val hasMetro = lines.any { l ->
                val digit = l.filter { it.isDigit() }
                digit in setOf("1", "2", "3", "5", "7", "9")
            }
            val hasTram = lines.any { l ->
                val digit = l.filter { it.isDigit() }
                digit in setOf("4", "6", "8", "10", "11", "12")
            }
            hasMetro && hasTram
        }
    }

    val onScheduledDepartureClick: (MetroScheduledDeparture) -> Unit = remember(appLanguage) {
        { scheduledItem ->
            val realTimeDep = scheduledItem.toRealTimeDeparture(appLanguage, forQuickTrack = false)
            metroViewModel.selectDepartureDetails(realTimeDep)
        }
    }

    val onScheduledDepartureLongClick: (MetroScheduledDeparture) -> Unit = remember(appLanguage, context) {
        { scheduledItem ->
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
            val currentMinOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val diffMin = (scheduledItem.timeMinutes - currentMinOfDay).coerceAtLeast(0)
            if (diffMin > 60) {
                val msg = context.getString(com.example.R.string.quick_track_limit_one_hour)
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val realTimeDep = scheduledItem.toRealTimeDeparture(appLanguage, forQuickTrack = true)
                quickTrackDeparture = realTimeDep
                showQuickTrackSheet = true
            }
        }
    }

    // Agrupamos las salidas en tiempo real por línea y dirección (cada línea+dirección en un mismo cuadro)
    val lineDepartureGroups = remember(
        visibleDepartures,
        selectedStationId,
        stationDisplayName,
        lineStationsMap,
        appLanguage,
        texts,
        isDarkMode
    ) {
        MetroMapper.groupDeparturesByLineAndDirection(
            departures = visibleDepartures,
            currentStationId = selectedStationId,
            currentStationName = stationDisplayName,
            lineStationsMap = lineStationsMap,
            appLanguage = appLanguage,
            texts = texts,
            isDarkMode = isDarkMode,
            sharedLineDigitsGetter = { digit -> metroViewModel.getSharedLineDigits(digit) }
        )
    }

    val (metroLineGroups, tramLineGroups) = remember<Pair<List<LineDeparturesGroupUiModel>, List<LineDeparturesGroupUiModel>>>(
        lineDepartureGroups,
        isMixedMetroTramStation
    ) {
        if (!isMixedMetroTramStation) {
            Pair(lineDepartureGroups, emptyList())
        } else {
            val metro = mutableListOf<LineDeparturesGroupUiModel>()
            val tram = mutableListOf<LineDeparturesGroupUiModel>()
            lineDepartureGroups.forEach { group ->
                val digit = group.lineId.filter { it.isDigit() }
                if (digit in setOf("4", "6", "8", "10", "11", "12")) {
                    tram.add(group)
                } else {
                    metro.add(group)
                }
            }
            Pair(metro.toList(), tram.toList())
        }
    }

    if (showSearchDialog) {
        MetroStationSelectionDialog(
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            metroViewModel = metroViewModel,
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showQuickPicker) {
        MetroQuickStationPickerDialog(
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            metroViewModel = metroViewModel,
            onDismiss = { showQuickPicker = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("proximos_trenes_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Mis estaciones favoritas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = texts.favoritesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { showSearchDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.edit_favorites_desc),
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            FavoriteStationsRow(
                favoriteStations = favoriteStations,
                selectedStationId = selectedStationId,
                metroViewModel = metroViewModel,
                isDarkMode = isDarkMode,
                accentColor = accentColor,
                subtextColor = subtextColor,
                texts = texts,
                onSearchClick = { showQuickPicker = true }
            )

            // Cabecera de la estación activa
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stationDisplayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("proximas_salidas_header_text")
                    )
                    val accInfo = remember(selectedStationId, stationDisplayName, accessibilityIncidents) {
                        computeMetroStationAccessibility(selectedStationId, stationDisplayName, accessibilityIncidents)
                    }
                    StationAccessibilityBadge(
                        accessibilityInfo = accInfo,
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode
                    )

                    if (stationIncidentLines.isNotEmpty()) {
                        StationCirculationAlertBadge(
                            stationName = stationDisplayName,
                            affectedLines = stationIncidentLines,
                            incidents = stationIncidents,
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode
                        )
                    }
                }
                IconButton(
                    onClick = {
                        metroViewModel.toggleStationInfoExpanded()
                        scope.launch {
                            scrollState.animateScrollTo(0)
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("toggle_station_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.station_info_desc),
                        tint = if (isStationInfoExpanded) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }



            val pullToRefreshState = rememberPullToRefreshState()
            val isInitialDeparturesLoad = visibleDepartures.isEmpty() || lineDepartureGroups.isEmpty()
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { metroViewModel.fetchRealTimeDepartures(selectedStationId) },
                state = pullToRefreshState,
                indicator = {
                    TransitPullRefreshIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isLoading,
                        stationName = stationDisplayName,
                        operatorName = "Metrovalencia",
                        appLanguage = appLanguage,
                        isInitialLoad = isInitialDeparturesLoad
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val isShowingLoadingSkeletons = (isLoading && isInitialDeparturesLoad && error == null) || (isLoadingInlineTheoretical && !isInlineTheoreticalLoaded && isInitialDeparturesLoad && error == null)
                if (isShowingLoadingSkeletons) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(5) {
                            MetroDepartureSkeletonCard()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pullUpStretchState.nestedScrollConnection)
                            .verticalScroll(scrollState)
                            .padding(
                                top = 8.dp,
                                bottom = 16.dp + activeTripBottomPadding
                            )
                            .testTag("realtime_departures_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SelectedStationInfoCard(
                            isStationInfoExpanded = isStationInfoExpanded,
                            selectedStation = selectedStation,
                            isDarkMode = isDarkMode
                        )

                        val currentAforo = stationAforoBloqueado
                        if (currentAforo != null && (currentAforo.activo || !currentAforo.getFormattedTimeSpan().isNullOrEmpty())) {
                            AforoBloqueadoCard(
                                aforo = currentAforo,
                                appLanguage = appLanguage,
                                isDarkMode = isDarkMode
                            )
                        }

                        if (error != null && !isInlineTheoreticalLoaded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val isOfflineError = !isOnline || error.contains("conexión", ignoreCase = true) || error.contains("connexió", ignoreCase = true) || error.contains("network", ignoreCase = true)
                                Icon(
                                    imageVector = if (isOfflineError) Icons.Default.WifiOff else Icons.Default.Cloud,
                                    contentDescription = "Error",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isOfflineError) {
                                        if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet"
                                    } else error,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                if (isOfflineError) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA)
                                            "Comprova la teua connexió per a consultar les eixides en temps real."
                                        else
                                            "Comprueba tu conexión para consultar las salidas en tiempo real.",
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { metroViewModel.fetchRealTimeDepartures(selectedStationId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("retry_realtime_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (appLanguage == AppLanguage.CA) "Reintentar" else "Reintentar",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val stId = selectedStation?.id ?: selectedStationId
                                            val stName = selectedStation?.name ?: "Estación"
                                            metroScheduleViewModel.loadInlineTheoreticalDepartures(stId, stName)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("open_scheduled_departures_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_view_scheduled), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        } else if (!isLoading && (visibleDepartures.isEmpty() || lineDepartureGroups.isEmpty())) {
                            if (isInlineTheoreticalLoaded) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_no_live_showing_scheduled_notice),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp
                                    ),
                                    color = if (isDarkMode) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            } else {
                                MetroDeparturesEmptyState(
                                    isOnline = isOnline,
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    texts = texts,
                                    stationIncidents = stationIncidents,
                                    selectedStationId = selectedStationId,
                                    selectedStation = selectedStation,
                                    onRetry = { metroViewModel.fetchRealTimeDepartures(selectedStationId) },
                                    onShowScheduled = { stId, stName ->
                                        metroScheduleViewModel.loadInlineTheoreticalDepartures(stId, stName)
                                    }
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (isMixedMetroTramStation) {
                                    // Bloque 1: Metro (Arriba)
                                    if (metroLineGroups.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, top = 4.dp, bottom = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Subway,
                                                contentDescription = null,
                                                tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Metro",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
                                            )
                                        }

                                        metroLineGroups.forEach { lineGroup ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 10.dp)
                                            ) {
                                                MetroLineDepartureCard(
                                                    group = lineGroup,
                                                    metroViewModel = metroViewModel,
                                                    appLanguage = appLanguage,
                                                    texts = texts,
                                                    isDarkMode = isDarkMode,
                                                    onExpired = { expiredId ->
                                                        expiredDepartureIds = expiredDepartureIds + expiredId
                                                    },
                                                    onLongClickDeparture = { dep ->
                                                        if (dep.minutesRemaining > 60) {
                                                            val msg = context.getString(com.example.R.string.quick_track_limit_one_hour)
                                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            quickTrackDeparture = dep
                                                            showQuickTrackSheet = true
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Bloque 2: Tranvía (Abajo)
                                    if (tramLineGroups.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, top = 10.dp, bottom = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tram,
                                                contentDescription = null,
                                                tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (appLanguage == AppLanguage.CA) "Tramvia" else "Tranvía",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
                                            )
                                        }

                                        tramLineGroups.forEach { lineGroup ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 10.dp)
                                             ) {
                                                MetroLineDepartureCard(
                                                    group = lineGroup,
                                                    metroViewModel = metroViewModel,
                                                    appLanguage = appLanguage,
                                                    texts = texts,
                                                    isDarkMode = isDarkMode,
                                                    onExpired = { expiredId ->
                                                        expiredDepartureIds = expiredDepartureIds + expiredId
                                                    },
                                                    onLongClickDeparture = { dep ->
                                                        if (dep.minutesRemaining > 60) {
                                                            val msg = context.getString(com.example.R.string.quick_track_limit_one_hour)
                                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            quickTrackDeparture = dep
                                                            showQuickTrackSheet = true
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    lineDepartureGroups.forEach { lineGroup ->
                                        if (isSingleLineStation) {
                                            val dirIcon = if (lineGroup.direction == 0) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward
                                            val dirTerminus = lineGroup.directionTerminusName ?: lineGroup.primaryDestination
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = dirIcon,
                                                    contentDescription = null,
                                                    tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Dir. $dirTerminus",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp)
                                        ) {
                                            MetroLineDepartureCard(
                                                group = lineGroup,
                                                metroViewModel = metroViewModel,
                                                appLanguage = appLanguage,
                                                texts = texts,
                                                isDarkMode = isDarkMode,
                                                onExpired = { expiredId ->
                                                    expiredDepartureIds = expiredDepartureIds + expiredId
                                                },
                                                onLongClickDeparture = { dep ->
                                                    if (dep.minutesRemaining > 60) {
                                                        val msg = context.getString(com.example.R.string.quick_track_limit_one_hour)
                                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        quickTrackDeparture = dep
                                                        showQuickTrackSheet = true
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Elastic stretch pull-up footer for theoretical scheduled departures (integrated across all states: offline, empty, and live)
                        MetroStretchPullUpFooter(
                            scheduledDepartures = deduplicatedScheduledDepartures,
                            isLoading = isLoadingInlineTheoretical,
                            isLoaded = isInlineTheoreticalLoaded,
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode,
                            stretchState = pullUpStretchState,
                            availableLines = availableLines,
                            selectedLineFilter = selectedLineFilter,
                            scrollState = scrollState,
                            onLineFilterSelected = { line ->
                                metroScheduleViewModel.setLineFilter(line)
                            },
                            onTriggerLoad = {
                                val stId = selectedStation?.id ?: selectedStationId
                                val stName = selectedStation?.name ?: "Estación"
                                metroScheduleViewModel.loadInlineTheoreticalDepartures(stId, stName)
                            },
                            onReset = {
                                metroScheduleViewModel.resetInlineTheoreticalDepartures()
                            },
                            onDepartureClick = onScheduledDepartureClick,
                            onDepartureLongClick = onScheduledDepartureLongClick
                        )
                    }
                }
            }
        }

    if (isScheduledSheetVisible) {
        MetroScheduledDeparturesSheet(
            stationName = selectedStation?.name ?: "Estación",
            scheduledDepartures = scheduledDepartures,
            isLoading = isLoadingScheduled,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            onDismiss = { metroScheduleViewModel.dismissScheduledDepartures() },
            onDepartureClick = { scheduledItem ->
                metroScheduleViewModel.dismissScheduledDepartures()
                onScheduledDepartureClick(scheduledItem)
            },
            onDepartureLongClick = { scheduledItem ->
                metroScheduleViewModel.dismissScheduledDepartures()
                onScheduledDepartureLongClick(scheduledItem)
            }
        )
    }

    var pendingTrackVehicle by remember { mutableStateOf<com.example.data.model.quicktrack.QuickTrackedVehicle?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingTrackVehicle?.let { vehicle ->
                QuickVehicleTrackerManager.startTracking(context, vehicle)
                val msg = if (vehicle.isDestinationAlert) {
                    context.getString(com.example.R.string.quick_track_toast_dest_pinned, vehicle.targetStationName ?: "")
                } else {
                    context.getString(com.example.R.string.quick_track_toast_pinned, vehicle.cleanLineNumber)
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            val warn = context.getString(com.example.R.string.quick_track_permission_warn)
            android.widget.Toast.makeText(context, warn, android.widget.Toast.LENGTH_LONG).show()
        }
        pendingTrackVehicle = null
    }

    QuickTrainTrackerBottomSheet(
        isVisible = showQuickTrackSheet,
        departure = quickTrackDeparture,
        originStationName = stationDisplayName,
        originStationId = selectedStationId,
        lineStations = lineStationsMap[quickTrackDeparture?.lineId] ?: emptyList(),
        appLanguage = appLanguage,
        isDarkMode = isDarkMode,
        onDismiss = {
            showQuickTrackSheet = false
            quickTrackDeparture = null
        },
        onTrackSelected = { vehicle ->
            showQuickTrackSheet = false
            quickTrackDeparture = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    pendingTrackVehicle = vehicle
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@QuickTrainTrackerBottomSheet
                }
            }
            QuickVehicleTrackerManager.startTracking(context, vehicle)
            val msg = if (vehicle.isDestinationAlert) {
                context.getString(com.example.R.string.quick_track_toast_dest_pinned, vehicle.targetStationName ?: "")
            } else {
                context.getString(com.example.R.string.quick_track_toast_pinned, vehicle.cleanLineNumber)
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    )

    DepartureDetailsBottomSheet(
        isBottomSheetVisible = isBottomSheetVisible,
        selectedDepartureDetails = selectedDepartureDetails,
        metroViewModel = metroViewModel,
        appLanguage = appLanguage,
        texts = texts,
        isDarkMode = isDarkMode,
        sheetState = sheetState,
        onDismiss = { metroViewModel.dismissDepartureDetails() },
        metroScheduleViewModel = metroScheduleViewModel,
        onStartQuickTrack = { dep ->
            metroViewModel.dismissDepartureDetails()
            quickTrackDeparture = dep
            showQuickTrackSheet = true
        }
    )
}
}

private fun MetroScheduledDeparture.toRealTimeDeparture(
    appLanguage: AppLanguage,
    forQuickTrack: Boolean = false
): RealTimeDeparture {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
    val currentMinOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    val diffMin = (this.timeMinutes - currentMinOfDay).coerceAtLeast(0)
    val cleanLine = this.line.replace("L", "", ignoreCase = true).trim()
    val lineHex = com.example.util.LineColorResolver.getMetroLineColorHex("L$cleanLine")
    return RealTimeDeparture(
        lineId = "L$cleanLine",
        destination = this.destinationName,
        minutesRemaining = diffMin,
        secondsRemaining = diffMin * 60,
        colorHex = lineHex,
        estimatedTime = this.timeFormatted,
        status = if (appLanguage == AppLanguage.CA) "Programat" else "Programado",
        track = null,
        capacidad = null,
        aforoBloqueado = null,
        vehicleId = null,
        trainServiceId = this.trainServiceId,
        originStationName = this.originName,
        originWebId = this.originWebId,
        destinationWebId = this.destinationWebId,
        targetArrivalEpochMs = System.currentTimeMillis() + (diffMin * 60_000L),
        id = "sched_${this.trainServiceId}_${this.timeMinutes}_${this.line}",
        isRealTime = false
    )
}


