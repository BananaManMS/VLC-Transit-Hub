@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import com.example.ui.bus.*
import com.example.ui.cercanias.*
import com.example.util.LocationUtils

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.example.ui.routing.components.ActiveTripOverlay
import com.example.ui.routing.components.RouteDetailBottomSheet
import com.example.data.repository.ActiveTripState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Switch
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn

import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.metro.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.MetroRepository
import android.app.Application
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.database.CalendarItemEntity
import com.example.data.database.CercaniasStationEntity
import com.example.data.model.Departure
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherData
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import com.example.ui.theme.VlcMetroTheme
import com.example.ui.theme.*
import com.example.ui.metro.MetroSummaryWidget
import com.example.ui.map.MapScreen
import com.example.ui.routing.RoutePlannerScreen
import com.example.ui.routing.PlannerLocation
import com.example.data.model.routing.PlannedItinerary

enum class DashboardTab {
    Inicio,
    Mapa,
    Bus,
    Metro,
    Cercanias,
    Ajustes
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    cercaniasViewModel: CercaniasViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    metroViewModel: com.example.ui.metro.MetroViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapViewModel: com.example.ui.map.MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val routePlannerViewModel: com.example.ui.routing.RoutePlannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val metroViewModel = metroViewModel ?: androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.ui.metro.MetroViewModel.Companion.Factory(
            application = context.applicationContext as android.app.Application,
            metroRepository = com.example.data.repository.MetroRepository(context)
        )
    )
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isFahrenheit by viewModel.isFahrenheit.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }

    val currentTime by viewModel.currentTime.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()

    val selectedStationId by metroViewModel.selectedStationId.collectAsState()
    val departures by metroViewModel.departures.collectAsState()

    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val sortedFavoriteStations by metroViewModel.sortedFavoriteStations.collectAsState()
    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
    val popularStations = remember(sortedFavoriteStations, allNetworkStations) {
        sortedFavoriteStations.mapNotNull { id ->
            allNetworkStations.find { it.id == id }?.let { station ->
                Triple(station.id, station.name, station.lines)
            }
        }
    }

    val weatherCity by viewModel.weatherCity.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val useGpsOnOpen by viewModel.useGpsOnOpen.collectAsState()

    val calendarItems by viewModel.calendarItems.collectAsState()

    // Real-time trains state collection
    val realTimeSelectedStationId by metroViewModel.realTimeSelectedStationId.collectAsState()
    val realTimeDepartures by metroViewModel.realTimeDepartures.collectAsState()
    val realTimeLoading by metroViewModel.realTimeLoading.collectAsState()
    val realTimeError by metroViewModel.realTimeError.collectAsState()

    val metroSearchQuery by metroViewModel.metroSearchQuery.collectAsState()
    val filteredStations by metroViewModel.searchedStations.collectAsState()

    val metroIncidents by metroViewModel.activeIncidents.collectAsState()
    val isMetroAlertsLoading by metroViewModel.isMetroAlertsLoading.collectAsState()
    val cercaniasAlerts by cercaniasViewModel.activeCercaniasAlerts.collectAsState()
    val isCercaniasAlertsLoading by cercaniasViewModel.isCercaniasAlertsLoading.collectAsState()

    var activeTab by remember { mutableStateOf(DashboardTab.Inicio) }
    var previousTabForBack by remember { mutableStateOf<DashboardTab?>(null) }
    var metroInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var cercaniasInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    var plannerInitialDestination by remember { mutableStateOf<PlannerLocation?>(null) }
    var isPlannerVisible by remember { mutableStateOf(false) }
    var activeMapItinerary by remember { mutableStateOf<PlannedItinerary?>(null) }

    val activeTrip by viewModel.activeTripState.collectAsState()
    val realTimeTripStatus by viewModel.realTimeTripStatus.collectAsState()
    val isRecalculatingTransfer by viewModel.isRecalculatingTransfer.collectAsState()
    val recalculateError by viewModel.recalculateError.collectAsState()
    val showTransferRiskDialog by viewModel.showTransferRiskDialog.collectAsState()
    var showActiveTripDetails by remember { mutableStateOf(false) }
    var pendingTripToStart by remember { mutableStateOf<Triple<PlannedItinerary, String, String>?>(null) }

    // Sync activeMapItinerary when an active trip exists or is restored from persistence
    LaunchedEffect(activeTrip) {
        if (activeTrip != null) {
            activeMapItinerary = activeTrip?.itinerary
        }
    }

    // Auto-prompt dialog when real-time status detects transfer risk during active trip
    LaunchedEffect(realTimeTripStatus.isTransferAtRisk) {
        if (realTimeTripStatus.isTransferAtRisk && activeTrip != null) {
            viewModel.triggerTransferRiskDialog()
        }
    }


    LaunchedEffect(sortedFavoriteStations) {
        if (sortedFavoriteStations.isNotEmpty() && !sortedFavoriteStations.contains(realTimeSelectedStationId)) {
            metroViewModel.selectRealTimeStation(sortedFavoriteStations.first())
        }
    }

    // Auto-select nearest metro station on first load once favorites are populated
    LaunchedEffect(favoriteStations) {
        if (favoriteStations.isNotEmpty()) {
            metroViewModel.autoSelectNearestMetroStationIfNeeded()
        }
    }

    LaunchedEffect(activeTab, realTimeSelectedStationId) {
        if (activeTab == DashboardTab.Metro && realTimeSelectedStationId != null) {
            metroViewModel.fetchRealTimeDepartures(realTimeSelectedStationId!!)
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showStationConfigDialog by remember { mutableStateOf(false) }
    var showCercaniasStationConfigDialog by remember { mutableStateOf(false) }
    var showMetroSearchDialog by remember { mutableStateOf(false) }


    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.syncGoogleCalendarEvents()
            android.widget.Toast.makeText(context, if (appLanguage == AppLanguage.ES) "Sincronizando eventos..." else "Sincronitzant esdeveniments...", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, if (appLanguage == AppLanguage.ES) "Permiso de calendario denegado." else "Permís de calendari denegat.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val handleLocationResult: (Double, Double) -> Unit = { lat, lng ->
        viewModel.updateLocation(lat, lng)
        viewModel.updateWeatherByLocation(lat, lng, context)
        metroViewModel.setLocation(android.location.Location("GPS").apply {
            latitude = lat
            longitude = lng
        })
        cercaniasViewModel.updateLocation(lat, lng)
        mapViewModel.updateLocation(lat, lng)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            LocationUtils.requestDeviceLocation(context, handleLocationResult)
            if (activeTrip != null) {
                com.example.service.ActiveTripTrackingService.start(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (LocationUtils.hasLocationPermission(context)) {
            LocationUtils.requestDeviceLocation(context, handleLocationResult)
        }
    }

    var isAppInForeground by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAppInForeground = true
                viewModel.onAppForegrounded()
                viewModel.refreshRealTimeTripStatus()
                if (LocationUtils.hasLocationPermission(context)) {
                    LocationUtils.requestDeviceLocation(context, handleLocationResult)
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                isAppInForeground = false
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAppInForeground) {
        if (isAppInForeground) {
            viewModel.onAppForegrounded()
            while (true) {
                if (LocationUtils.hasLocationPermission(context)) {
                    if (viewModel.shouldRequestLocationUpdate()) {
                        LocationUtils.requestDeviceLocation(context, handleLocationResult)
                    }
                }
                delay(600_000) // 10 minutes frequency
            }
        }
    }

    VlcMetroTheme(darkTheme = isDarkMode) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val shouldShowOnboarding by viewModel.shouldShowOnboarding.collectAsState()
            if (shouldShowOnboarding) {
                OnboardingScreen(
                    cercaniasViewModel = cercaniasViewModel,
                    viewModel = viewModel,
                    onConfigureStations = { showStationConfigDialog = true },
                    onConfigureCercaniasStations = { showCercaniasStationConfigDialog = true },
                    onLaunchLocationPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
            } else {
                Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.testTag("bottom_nav_bar"),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        val navColors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.Inicio,
                            onClick = { previousTabForBack = null; activeTab = DashboardTab.Inicio },
                            label = { Text(texts.tabInicio, style = MaterialTheme.typography.labelMedium, fontWeight = if (activeTab == DashboardTab.Inicio) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == DashboardTab.Inicio) Icons.Default.Home else Icons.Outlined.Home,
                                    contentDescription = texts.tabInicio
                                )
                            },
                            colors = navColors,
                            modifier = Modifier.testTag("tab_inicio")
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.Mapa,
                            onClick = { previousTabForBack = null; activeTab = DashboardTab.Mapa },
                            label = { Text(texts.tabMapa, style = MaterialTheme.typography.labelMedium, fontWeight = if (activeTab == DashboardTab.Mapa) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == DashboardTab.Mapa) Icons.Default.Map else Icons.Outlined.Map,
                                    contentDescription = texts.tabMapa
                                )
                            },
                            colors = navColors,
                            modifier = Modifier.testTag("tab_mapa")
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.Bus,
                            onClick = { previousTabForBack = null; activeTab = DashboardTab.Bus },
                            label = { Text(texts.tabBus, style = MaterialTheme.typography.labelMedium, fontWeight = if (activeTab == DashboardTab.Bus) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == DashboardTab.Bus) Icons.Default.DirectionsBus else Icons.Outlined.DirectionsBus,
                                    contentDescription = texts.tabBus
                                )
                            },
                            colors = navColors,
                            modifier = Modifier.testTag("tab_bus")
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.Metro,
                            onClick = { previousTabForBack = null; metroInitialPage = 0; activeTab = DashboardTab.Metro },
                            label = { Text(texts.tabMetro, style = MaterialTheme.typography.labelMedium, fontWeight = if (activeTab == DashboardTab.Metro) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == DashboardTab.Metro) Icons.Default.Subway else Icons.Outlined.Subway,
                                    contentDescription = texts.tabMetro
                                )
                            },
                            colors = navColors,
                            modifier = Modifier.testTag("tab_metro")
                        )
                        NavigationBarItem(
                            selected = activeTab == DashboardTab.Cercanias,
                            onClick = { previousTabForBack = null; cercaniasInitialPage = 0; activeTab = DashboardTab.Cercanias },
                            label = { Text(texts.tabCercanias, style = MaterialTheme.typography.labelMedium, fontWeight = if (activeTab == DashboardTab.Cercanias) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == DashboardTab.Cercanias) Icons.Default.DirectionsRailway else Icons.Outlined.DirectionsRailway,
                                    contentDescription = texts.tabCercanias
                                )
                            },
                            colors = navColors,
                            modifier = Modifier.testTag("tab_cercanias")
                        )
                    }
                }
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val localMaxHeight = maxHeight
                    val localMaxWidth = maxWidth
                    val isTablet = localMaxWidth >= 768.dp

                    Crossfade(
                        targetState = activeTab,
                        animationSpec = tween(durationMillis = 350),
                        label = "tab_fade_transition"
                    ) { currentTab ->
                        when (currentTab) {
                        DashboardTab.Inicio -> {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    if (LocationUtils.hasLocationPermission(context)) {
                                        LocationUtils.requestDeviceLocation(context) { lat, lng ->
                                            viewModel.updateWeatherByLocation(lat, lng, context)
                                        }
                                    }
                                    viewModel.refreshAll()
                                },
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("inicio_pull_to_refresh")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("clock_widget")
                                        ) {
                                            val (hoursMinutes, seconds) = remember(currentTime) {
                                                if (currentTime.length >= 8 && currentTime.count { it == ':' } == 2) {
                                                    val parts = currentTime.split(":")
                                                    Pair("${parts[0]}:${parts[1]}", parts[2])
                                                } else {
                                                    val clean = currentTime.ifEmpty { "09:12:00" }
                                                    if (clean.count { it == ':' } == 2) {
                                                        val parts = clean.split(":")
                                                        Pair("${parts[0]}:${parts[1]}", parts[2])
                                                    } else {
                                                        Pair(clean, "00")
                                                    }
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                Text(
                                                    text = hoursMinutes,
                                                    fontSize = if (isTablet) 54.sp else 44.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    letterSpacing = (-1.5).sp,
                                                    modifier = Modifier.alignByBaseline()
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = seconds,
                                                    fontSize = if (isTablet) 24.sp else 20.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                    modifier = Modifier.alignByBaseline()
                                                )
                                            }

                                            val formattedDate = remember(currentTime, appLanguage) {
                                                try {
                                                    val locale = if (appLanguage == AppLanguage.CA) java.util.Locale.forLanguageTag("ca-ES") else java.util.Locale.forLanguageTag("es-ES")
                                                    java.text.SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", locale).format(java.util.Date())
                                                } catch (e: Exception) {
                                                    if (appLanguage == AppLanguage.CA) "diumenge, 19 de juliol de 2026" else "domingo, 19 de julio de 2026"
                                                }
                                            }
                                            Text(
                                                text = formattedDate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                                                fontSize = if (isTablet) 14.sp else 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                letterSpacing = 0.1.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { activeTab = DashboardTab.Ajustes },
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .testTag("settings_button_top")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = texts.headerAjustesTitle,
                                                tint = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    IncidenciasWidget(
                                        isDarkMode = isDarkMode,
                                        appLanguage = appLanguage,
                                        metroIncidents = metroIncidents,
                                        cercaniasAlerts = cercaniasAlerts,
                                        isMetroLoading = isMetroAlertsLoading,
                                        isCercaniasLoading = isCercaniasAlertsLoading,
                                        onOpenMetroAvisos = {
                                            previousTabForBack = DashboardTab.Inicio
                                            metroInitialPage = 1
                                            activeTab = DashboardTab.Metro
                                        },
                                        onOpenCercaniasAvisos = {
                                            previousTabForBack = DashboardTab.Inicio
                                            cercaniasInitialPage = 1
                                            activeTab = DashboardTab.Cercanias
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    )

                                    if (isTablet) {
                                        Row(
                                            modifier = Modifier
                                                .height(if (localMaxHeight - 120.dp > 450.dp) localMaxHeight - 120.dp else 450.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            ) {
                                                WeatherCard(
                                                    currentTime = currentTime,
                                                    data = weatherData,
                                                    isFahrenheit = isFahrenheit,
                                                    isDarkMode = isDarkMode,
                                                    appLanguage = appLanguage
                                                )
                                            }

                                            CalendarSummaryWidget(
                                                isTablet = isTablet,
                                                isDarkMode = isDarkMode,
                                                calendarTitle = texts.calendarTitle,
                                                noEventsTodayText = texts.noEventsToday,
                                                events = remember(calendarItems) { calendarItems.filter { it.itemType == "EVENT" } },
                                                onSyncClick = {
                                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                        viewModel.syncGoogleCalendarEvents(force = true)
                                                        android.widget.Toast.makeText(context, if (appLanguage == AppLanguage.ES) "Sincronizando eventos..." else "Sincronitzant esdeveniments...", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                                    }
                                                },
                                                onAddClick = {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                                                            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        showAddDialog = true
                                                    }
                                                },
                                                onEventDelete = { viewModel.deleteItem(it) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                WeatherCard(
                                                    currentTime = currentTime,
                                                    data = weatherData,
                                                    isFahrenheit = isFahrenheit,
                                                    isDarkMode = isDarkMode,
                                                    appLanguage = appLanguage
                                                )
                                            }

                                            CalendarSummaryWidget(
                                                isTablet = isTablet,
                                                isDarkMode = isDarkMode,
                                                calendarTitle = texts.calendarTitle,
                                                noEventsTodayText = texts.noEventsToday,
                                                events = remember(calendarItems) { calendarItems.filter { it.itemType == "EVENT" } },
                                                onSyncClick = {
                                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                        viewModel.syncGoogleCalendarEvents(force = true)
                                                        android.widget.Toast.makeText(context, if (appLanguage == AppLanguage.ES) "Sincronizando eventos..." else "Sincronitzant esdeveniments...", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                                    }
                                                },
                                                onAddClick = {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                                                            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        showAddDialog = true
                                                    }
                                                },
                                                onEventDelete = { viewModel.deleteItem(it) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        DashboardTab.Mapa -> {
                            MapScreen(
                                mapViewModel = mapViewModel,
                                dashboardViewModel = viewModel,
                                metroViewModel = metroViewModel,
                                cercaniasViewModel = cercaniasViewModel,
                                isDarkMode = isDarkMode,
                                appLanguage = appLanguage,
                                onNavigateToMetro = { stationId ->
                                    metroViewModel.selectRealTimeStation(stationId)
                                    previousTabForBack = DashboardTab.Mapa
                                    activeTab = DashboardTab.Metro
                                },
                                onNavigateToCercanias = { stationId ->
                                    cercaniasViewModel.selectCercaniasStation(stationId)
                                    previousTabForBack = DashboardTab.Mapa
                                    activeTab = DashboardTab.Cercanias
                                },
                                onNavigateToRoutePlanner = { location ->
                                    plannerInitialDestination = location
                                    routePlannerViewModel.setDestination(location)
                                    isPlannerVisible = true
                                },
                                onPlannerLocationPicked = { loc, isOrigin ->
                                    plannerInitialDestination = null
                                    if (isOrigin) {
                                        routePlannerViewModel.setOrigin(loc)
                                    } else {
                                        routePlannerViewModel.setDestination(loc)
                                    }
                                    isPlannerVisible = true
                                },
                                onCancelPlannerLocationPicking = {
                                    isPlannerVisible = true
                                },
                                selectedItinerary = activeMapItinerary,
                                onClearItinerary = {
                                    activeMapItinerary = null
                                    plannerInitialDestination = null
                                    isPlannerVisible = true
                                },
                                onOpenRouteDetail = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                DashboardTab.Bus -> {
                    EmtBusScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 0.dp),
                        isDarkMode = isDarkMode
                    )
                }
                DashboardTab.Metro -> {
                    MetroScreen(
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 0.dp),
                        appLanguage = appLanguage,
                        metroViewModel = metroViewModel,
                        isDarkMode = isDarkMode,
                        initialPage = metroInitialPage,
                        onBackClick = if (previousTabForBack != null) {
                            {
                                val backTo = previousTabForBack ?: DashboardTab.Inicio
                                previousTabForBack = null
                                activeTab = backTo
                            }
                        } else null,
                        onBackGesture = if (previousTabForBack != null) {
                            {
                                val backTo = previousTabForBack ?: DashboardTab.Inicio
                                previousTabForBack = null
                                activeTab = backTo
                            }
                        } else null
                    )
                }
                DashboardTab.Cercanias -> {
                    CercaniasScreen(
                        viewModel = cercaniasViewModel,
                        isDarkMode = isDarkMode,
                        initialPage = cercaniasInitialPage,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 0.dp),
                        onBackClick = if (previousTabForBack != null) {
                            {
                                val backTo = previousTabForBack ?: DashboardTab.Inicio
                                previousTabForBack = null
                                activeTab = backTo
                            }
                        } else null,
                        onBackGesture = if (previousTabForBack != null) {
                            {
                                val backTo = previousTabForBack ?: DashboardTab.Inicio
                                previousTabForBack = null
                                activeTab = backTo
                            }
                        } else null
                    )
                }
                DashboardTab.Ajustes -> {
                    AjustesScreen(
                        viewModel = viewModel,
                        cercaniasViewModel = cercaniasViewModel,
                        onBackClick = { activeTab = DashboardTab.Inicio },
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 0.dp)
                    )
                }
                    } // closes when (currentTab)
                    } // closes Crossfade

                    // Active Trip Floating Overlay (Transit GO)
                    AnimatedVisibility(
                        visible = activeTrip != null && !isPlannerVisible,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 600.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        activeTrip?.let { currentActiveTrip ->
                            ActiveTripOverlay(
                                activeTrip = currentActiveTrip,
                                onExpandDetails = { showActiveTripDetails = true },
                                onCancelTrip = { viewModel.cancelActiveTrip() },
                                onAdvanceLeg = { newIdx -> viewModel.advanceActiveTripLeg(newIdx) },
                                onRecalculateTransfer = { viewModel.recalculateMissedTransfer() },
                                isRecalculating = isRecalculatingTransfer,
                                recalculateError = recalculateError,
                                onDismissRecalculateError = { viewModel.dismissRecalculateError() },
                                realTimeStatus = realTimeTripStatus,
                                appLanguage = appLanguage
                            )
                        }
                    }
                } // closes BoxWithConstraints
            } // closes Scaffold
            } // closes else (onboarding conditional)

        // Back navigation handling for dialogs
        if (showAddDialog) {
            BackHandler { showAddDialog = false }
        }
        if (showStationConfigDialog) {
            BackHandler { showStationConfigDialog = false }
        }
        if (showCercaniasStationConfigDialog) {
            BackHandler { showCercaniasStationConfigDialog = false }
        }
        if (showMetroSearchDialog) {
            BackHandler { showMetroSearchDialog = false }
        }

        // Back navigation for tabs when no overlay/dialog/planner is active
        val isTabBackEnabled = !isPlannerVisible &&
            activeMapItinerary == null &&
            !showAddDialog &&
            !showStationConfigDialog &&
            !showCercaniasStationConfigDialog &&
            !showMetroSearchDialog &&
            activeTab != DashboardTab.Inicio

        BackHandler(enabled = isTabBackEnabled) {
            val backTo = previousTabForBack ?: DashboardTab.Inicio
            previousTabForBack = null
            activeTab = backTo
        }

        // 5. DIALOG FOR ADDING CALENDAR ITEM
        if (showAddDialog) {
            AddCalendarItemDialog(
                onDismiss = { showAddDialog = false },
                onAddEvent = { title, desc, offset, dur, color ->
                    viewModel.addEvent(title, desc, offset, dur, color)
                    showAddDialog = false
                },
                onAddTask = { title, desc, offset, color ->
                    viewModel.addTask(title, desc, offset, color)
                    showAddDialog = false
                }
            )
        }

        // 5b. DIALOG FOR CONFIGURING FAVOURITE STATIONS
        if (showStationConfigDialog) {
            StationConfigDialog(
                allStations = allNetworkStations,
                currentFavorites = favoriteStations,
                onDismiss = { showStationConfigDialog = false },
                onSave = { selectedIds ->
                    metroViewModel.updateFavoriteStations(selectedIds)
                    showStationConfigDialog = false
                }
            )
        }

        if (showCercaniasStationConfigDialog) {
            CercaniasStationSelectionDialog(
                viewModel = cercaniasViewModel,
                onDismiss = { showCercaniasStationConfigDialog = false }
            )
        }

        if (showMetroSearchDialog) {
            MetroSearchDialog(
                searchQuery = metroSearchQuery,
                filteredStations = filteredStations,
                favoriteStationIds = favoriteStations,
                isDarkMode = isDarkMode,
                onQueryChange = { metroViewModel.setMetroSearchQuery(it) },
                onSelectStation = { stationId ->
                    metroViewModel.selectStation(stationId)
                    showMetroSearchDialog = false
                },
                onToggleFavorite = { stationId ->
                    metroViewModel.toggleFavoriteStation(stationId)
                },
                onDismiss = { showMetroSearchDialog = false }
            )
        }

        if (isPlannerVisible) {
            RoutePlannerScreen(
                initialDestination = plannerInitialDestination,
                onInitialDestinationConsumed = {
                    plannerInitialDestination = null
                },
                onNavigateBack = {
                    isPlannerVisible = false
                    plannerInitialDestination = null
                },
                onSelectItineraryForMap = { itinerary ->
                    activeMapItinerary = itinerary
                    isPlannerVisible = false
                    activeTab = DashboardTab.Mapa
                },
                onStartTrip = { itinerary, originName, destName ->
                    if (activeTrip != null) {
                        pendingTripToStart = Triple(itinerary, originName, destName)
                    } else {
                        viewModel.startActiveTrip(itinerary, originName, destName)
                        activeMapItinerary = itinerary
                        isPlannerVisible = false
                        activeTab = DashboardTab.Mapa
                    }
                },
                onPickOnMapClick = { isOrigin ->
                    isPlannerVisible = false
                    activeTab = DashboardTab.Mapa
                    mapViewModel.selectItem(null)
                    mapViewModel.setSelectionMode(
                        if (isOrigin) com.example.ui.map.MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN
                        else com.example.ui.map.MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION
                    )
                },
                viewModel = routePlannerViewModel,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Active Trip Detailed Modal
        if (showActiveTripDetails) {
            activeTrip?.itinerary?.let { tripItinerary ->
                RouteDetailBottomSheet(
                    itinerary = tripItinerary,
                    onDismiss = { showActiveTripDetails = false },
                    onViewOnMap = {
                        showActiveTripDetails = false
                        activeMapItinerary = tripItinerary
                        activeTab = DashboardTab.Mapa
                    },
                    onStartTrip = null,
                    currentLegIndex = activeTrip?.currentLegIndex ?: -1,
                    realTimeStatus = realTimeTripStatus,
                    appLanguage = appLanguage
                )
            }
        }

        // Confirmation Dialog when replacing an existing Active Trip
        pendingTripToStart?.let { pending ->
            AlertDialog(
                onDismissRequest = { pendingTripToStart = null },
                title = {
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "¿Iniciar nuevo viaje?" else "Iniciar nou viatge?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = if (appLanguage == AppLanguage.ES)
                            "Ya tienes un viaje activo hacia ${activeTrip?.destinationName ?: "tu destino"}. ¿Deseas sustituirlo por el nuevo trayecto hacia ${pending.third}?"
                        else
                            "Ja tens un viatge actiu cap a ${activeTrip?.destinationName ?: "la teua destinació"}. Vols substituir-lo per este nou trajecte cap a ${pending.third}?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.startActiveTrip(pending.first, pending.second, pending.third)
                            activeMapItinerary = pending.first
                            isPlannerVisible = false
                            activeTab = DashboardTab.Mapa
                            pendingTripToStart = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A86B)
                        )
                    ) {
                        Text(if (appLanguage == AppLanguage.ES) "Sustituir e Iniciar" else "Substituir i Iniciar")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { pendingTripToStart = null }) {
                        Text(if (appLanguage == AppLanguage.ES) "Cancelar" else "Cancel·lar")
                    }
                }
            )
        }

        // Floating Dialog for Transfer Risk Recalculation Confirmation
        if (showTransferRiskDialog && activeTrip != null) {
            val currentLeg = activeTrip?.itinerary?.legs?.getOrNull(activeTrip?.currentLegIndex ?: 0)
            val transferStopName = currentLeg?.toName?.ifBlank { "la estación de transbordo" } ?: "el transbordo"
            val destName = activeTrip?.destinationName?.ifBlank { "tu destino" } ?: "tu destino"
            val isDark = isSystemInDarkTheme()

            AlertDialog(
                onDismissRequest = { viewModel.dismissTransferRiskDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "Posible transbordo perdido" else "Possible transbordament perdut",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)
                        )
                    )
                },
                text = {
                    Column {
                        Text(
                            text = if (appLanguage == AppLanguage.ES)
                                "Debido a un retraso acumulado en tu vehículo, es probable que no llegues a tiempo a tu conexión en $transferStopName.\n\n¿Quieres buscar rutas alternativas desde $transferStopName hasta $destName sin caminar más?"
                            else
                                "A causa d'un retràs acumulat en el teu transport, és probable que no arribes a temps a la connexió en $transferStopName.\n\nVols buscar rutes alternatives des de $transferStopName fins a $destName sense caminar més?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (recalculateError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = recalculateError ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.recalculateMissedTransfer() },
                        enabled = !isRecalculatingTransfer,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                    ) {
                        if (isRecalculatingTransfer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (appLanguage == AppLanguage.ES) "Buscando..." else "Buscant...")
                        } else {
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "Buscar alternativas (sin caminar más)" else "Buscar alternatives (sense caminar més)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.dismissTransferRiskDialog() }
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Mantener ruta actual" else "Mantindre ruta actual"
                        )
                    }
                }
            )
        }

    } // closes Box
} // closes VlcMetroTheme
} // closes DashboardScreen







