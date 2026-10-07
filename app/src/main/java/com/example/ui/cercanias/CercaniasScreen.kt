package com.example.ui.cercanias

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.theme.UnifiedTabRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CercaniasScreen(
    viewModel: CercaniasViewModel,
    isDarkMode: Boolean,
    initialPage: Int = 0,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onBackGesture: (() -> Unit)? = null,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val backHandlerAction = onBackClick ?: onBackGesture
    if (backHandlerAction != null) {
        BackHandler(enabled = true) {
            backHandlerAction()
        }
    }

    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val selectedStationId by viewModel.cercaniasSelectedStationId.collectAsState()
    val departures by viewModel.cercaniasDepartures.collectAsState()
    val isLoading by viewModel.cercaniasLoading.collectAsState()
    val error by viewModel.cercaniasError.collectAsState()
    val favoriteStations by viewModel.cercaniasFavoriteStations.collectAsState()
    val isBottomSheetVisible by viewModel.isCercaniasBottomSheetVisible.collectAsState()
    val selectedDeparture by viewModel.selectedCercaniasDeparture.collectAsState()
    val cercaniasAlerts by viewModel.cercaniasAlerts.collectAsState()
    val isCercaniasAlertsLoading by viewModel.isCercaniasAlertsLoading.collectAsState()
    val hasCercaniasAlertsError by viewModel.hasCercaniasAlertsError.collectAsState()
    val activeAlerts by viewModel.activeCercaniasAlerts.collectAsState()
    val generalNotices by viewModel.generalCercaniasNotices.collectAsState()
    val accessibilityAlerts by viewModel.accessibilityCercaniasAlerts.collectAsState()
    val groupedAccessibilityAlerts by viewModel.groupedAccessibilityAlerts.collectAsState()
    
    var showSearchDialog by remember { mutableStateOf(false) }
    val allCercaniasStations by viewModel.allCercaniasStations.collectAsState()

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    // Auto-select nearest station on load if no station is selected
    LaunchedEffect(selectedStationId, favoriteStations, allCercaniasStations) {
        if (selectedStationId.isBlank()) {
            viewModel.autoSelectNearestCercaniasStationIfNeeded()
        }
    }

    // Ciclo de vida: refresco adaptativo activo solo cuando la pantalla es visible (evita drenaje de batería en segundo plano)
    val context = androidx.compose.ui.platform.LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (departures.isEmpty()) {
                    viewModel.fetchCercaniasDepartures()
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopCercaniasPolling()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopCercaniasPolling()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // TabRow Unificado (Material 3 con tipografía titleSmall y Capitalizado)
            UnifiedTabRow(
                selectedTabIndex = pagerState.currentPage,
                tabs = listOf(
                    androidx.compose.ui.res.stringResource(com.example.R.string.metro_tab_salidas),
                    androidx.compose.ui.res.stringResource(com.example.R.string.metro_tab_avisos)
                ),
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> {
                        CercaniasDeparturesTab(
                            viewModel = viewModel,
                            selectedStationId = selectedStationId,
                            departures = departures,
                            isLoading = isLoading,
                            error = error,
                            favoriteStations = favoriteStations,
                            allCercaniasStations = allCercaniasStations,
                            cercaniasAlerts = cercaniasAlerts,
                            accessibilityAlerts = accessibilityAlerts,
                            isOnline = isOnline,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            texts = texts,
                            activeTripBottomPadding = activeTripBottomPadding,
                            onOpenSearchDialog = { showSearchDialog = true },
                            onSelectDeparture = { viewModel.selectCercaniasDepartureDetails(it) }
                        )
                    }
                    1 -> {
                        CercaniasAlertsTab(
                            activeAlerts = activeAlerts,
                            generalNotices = generalNotices,
                            groupedAccessibilityAlerts = groupedAccessibilityAlerts,
                            allCercaniasStations = allCercaniasStations,
                            isCercaniasAlertsLoading = isCercaniasAlertsLoading,
                            isOnline = isOnline,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            activeTripBottomPadding = activeTripBottomPadding,
                            hasCercaniasAlertsError = hasCercaniasAlertsError,
                            onRetry = { viewModel.fetchCercaniasRealTimeAlerts() },
                            onNavigateToStation = { stationId ->
                                viewModel.selectCercaniasStation(stationId)
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Dedicated Live Train Tracking Floating Action Button (FAB)
        FloatingActionButton(
            onClick = { viewModel.setShowLiveMap(true) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = activeTripBottomPadding + 24.dp, end = 8.dp)
                .testTag("cercanias_live_map_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_view_live_map)
            )
        }
    }

    // Search Dialog for selecting any station and managing favorites
    if (showSearchDialog) {
        CercaniasStationSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showSearchDialog = false }
        )
    }
    
    // Bottom Sheet for Departure Details
    if (isBottomSheetVisible && selectedDeparture != null) {
        val sheetState = rememberModalBottomSheetState()
        val currentStationEntity = remember(allCercaniasStations, selectedStationId) {
            allCercaniasStations.find { it.stop_id.equals(selectedStationId, ignoreCase = true) || it.id.equals(selectedStationId, ignoreCase = true) }
        }
        val currentStationName = currentStationEntity?.displayName ?: currentStationEntity?.nombre ?: ""

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissCercaniasDepartureDetails() },
            sheetState = sheetState,
            containerColor = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA),
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            CercaniasDepartureDetails(
                departure = selectedDeparture!!,
                alerts = cercaniasAlerts,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                originStationId = selectedStationId,
                originStationName = currentStationName
            )
        }
    }

    // Live Map Dialog overlay
    val showLiveMap by viewModel.showLiveMap.collectAsState()
    if (showLiveMap) {
        CercaniasLiveMapDialog(
            viewModel = viewModel,
            isDarkMode = isDarkMode,
            bottomPadding = activeTripBottomPadding + 80.dp,
            onDismiss = { viewModel.setShowLiveMap(false) }
        )
    }
}
