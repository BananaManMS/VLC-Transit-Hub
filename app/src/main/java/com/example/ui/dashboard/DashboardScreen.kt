@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.routing.PlannerLocation
import com.example.ui.routing.RoutePlannerScreen
import com.example.ui.theme.VlcMetroTheme
import com.example.util.LocationUtils
import kotlinx.coroutines.delay

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
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val mapViewModel: com.example.ui.map.MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val mapUserLocation by mapViewModel.userLocation.collectAsState()
    val userAndroidLocation = remember(mapUserLocation) {
        mapUserLocation?.let { geo ->
            android.location.Location("gps").apply {
                latitude = geo.latitude
                longitude = geo.longitude
            }
        }
    }
    val routePlannerViewModel: com.example.ui.routing.RoutePlannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val busViewModel: com.example.ui.bus.BusViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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
    val shouldShowOnboarding by viewModel.shouldShowOnboarding.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }

    val selectedStationId by metroViewModel.selectedStationId.collectAsState()

    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val sortedFavoriteStations by metroViewModel.sortedFavoriteStations.collectAsState()

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
    val metroSpecialNotices by metroViewModel.specialNotices.collectAsState()
    val isMetroAlertsLoading by metroViewModel.isMetroAlertsLoading.collectAsState()
    val cercaniasAlerts by cercaniasViewModel.activeCercaniasAlerts.collectAsState()
    val isCercaniasAlertsLoading by cercaniasViewModel.isCercaniasAlertsLoading.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var activeTab by remember { mutableStateOf(DashboardTab.Inicio) }
    var previousTabForBack by remember { mutableStateOf<DashboardTab?>(null) }
    val lastBusTabMemory by busViewModel.selectedBusTabIndex.collectAsState()
    var busInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(busViewModel.selectedBusTabIndex.value) }
    var metroInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var cercaniasInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    var plannerInitialDestination by remember { mutableStateOf<PlannerLocation?>(null) }
    var isPlannerVisible by remember { mutableStateOf(false) }
    var isViewingPlannerItineraryOnMap by remember { mutableStateOf(false) }
    var activeMapItinerary by remember { mutableStateOf<PlannedItinerary?>(null) }
    var userDismissedActiveMapItinerary by remember { mutableStateOf(false) }
    var lastTrackedActiveTripStartMs by remember { mutableStateOf<Long?>(null) }

    val activeTrip by viewModel.activeTripState.collectAsState()
    val unifiedTripSnapshot by viewModel.unifiedTripSnapshot.collectAsState()
    val realTimeTripStatus by viewModel.realTimeTripStatus.collectAsState()
    val isRecalculatingTransfer by viewModel.isRecalculatingTransfer.collectAsState()
    val recalculateError by viewModel.recalculateError.collectAsState()
    val showTransferRiskDialog by viewModel.showTransferRiskDialog.collectAsState()
    var showActiveTripDetails by remember { mutableStateOf(false) }
    var pendingTripToStart by remember { mutableStateOf<Triple<PlannedItinerary, String, String>?>(null) }

    // Sync activeMapItinerary ONLY when a new trip starts or if user hasn't explicitly dismissed it from the map
    LaunchedEffect(activeTrip?.startTimestamp) {
        val currentTrip = activeTrip
        if (currentTrip != null) {
            if (lastTrackedActiveTripStartMs != currentTrip.startTimestamp) {
                lastTrackedActiveTripStartMs = currentTrip.startTimestamp
                userDismissedActiveMapItinerary = false
                activeMapItinerary = currentTrip.itinerary
            } else if (!userDismissedActiveMapItinerary) {
                activeMapItinerary = currentTrip.itinerary
            }
        } else {
            lastTrackedActiveTripStartMs = null
            userDismissedActiveMapItinerary = false
            if (activeMapItinerary != null && !isViewingPlannerItineraryOnMap) {
                activeMapItinerary = null
            }
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


    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

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
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val hasNotifPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!hasNotifPerm) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            if (shouldShowOnboarding) {
                OnboardingScreen(
                    cercaniasViewModel = cercaniasViewModel,
                    metroViewModel = metroViewModel,
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
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    DashboardBottomNavBar(
                        activeTab = activeTab,
                        isDarkMode = isDarkMode,
                        texts = texts,
                        onTabSelected = { selectedTab ->
                            when (selectedTab) {
                                DashboardTab.Inicio -> {
                                    previousTabForBack = null
                                    if (activeTrip == null) {
                                        activeMapItinerary = null
                                        isViewingPlannerItineraryOnMap = false
                                    }
                                    activeTab = DashboardTab.Inicio
                                }
                                DashboardTab.Mapa -> {
                                    previousTabForBack = null
                                    val currentTrip = activeTrip
                                    if (currentTrip != null && !userDismissedActiveMapItinerary) {
                                        activeMapItinerary = currentTrip.itinerary
                                    }
                                    activeTab = DashboardTab.Mapa
                                }
                                DashboardTab.Bus -> {
                                    previousTabForBack = null
                                    busInitialPage = busViewModel.selectedBusTabIndex.value
                                    if (activeTrip == null) {
                                        activeMapItinerary = null
                                        isViewingPlannerItineraryOnMap = false
                                    }
                                    activeTab = DashboardTab.Bus
                                }
                                DashboardTab.Metro -> {
                                    previousTabForBack = null
                                    metroInitialPage = 0
                                    if (activeTrip == null) {
                                        activeMapItinerary = null
                                        isViewingPlannerItineraryOnMap = false
                                    }
                                    activeTab = DashboardTab.Metro
                                }
                                DashboardTab.Cercanias -> {
                                    previousTabForBack = null
                                    cercaniasInitialPage = 0
                                    if (activeTrip == null) {
                                        activeMapItinerary = null
                                        isViewingPlannerItineraryOnMap = false
                                    }
                                    activeTab = DashboardTab.Cercanias
                                }
                                DashboardTab.Ajustes -> {
                                    previousTabForBack = null
                                    activeTab = DashboardTab.Ajustes
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val localMaxHeight = maxHeight
                    val localMaxWidth = maxWidth
                    val isTablet = localMaxWidth >= 768.dp

                    val isViewingActiveRouteOnMap = (activeTab == DashboardTab.Mapa && activeMapItinerary != null)
                    var activeTripOverlayMeasuredHeight by remember { mutableStateOf(104.dp) }
                    val dynamicBottomTripPadding by animateDpAsState(
                        targetValue = if (activeTrip != null && !isPlannerVisible) (activeTripOverlayMeasuredHeight + 8.dp) else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dynamicBottomTripPadding"
                    )

                    Crossfade(
                        targetState = activeTab,
                        animationSpec = tween(durationMillis = 350),
                        label = "tab_fade_transition"
                    ) { currentTab ->
                        when (currentTab) {
                        DashboardTab.Inicio -> {
                            DashboardHomeTab(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    if (LocationUtils.hasLocationPermission(context)) {
                                        LocationUtils.requestDeviceLocation(context) { lat, lng ->
                                            viewModel.updateWeatherByLocation(lat, lng, context)
                                        }
                                    }
                                    viewModel.refreshAll()
                                    metroViewModel.fetchAllAlerts()
                                    cercaniasViewModel.fetchCercaniasRealTimeAlerts()
                                },
                                currentTimeFlow = viewModel.currentTime,
                                appLanguage = appLanguage,
                                texts = texts,
                                isDarkMode = isDarkMode,
                                isFahrenheit = isFahrenheit,
                                isTablet = isTablet,
                                localMaxHeight = localMaxHeight,
                                dynamicBottomTripPadding = dynamicBottomTripPadding,
                                metroIncidents = metroIncidents,
                                metroSpecialNotices = metroSpecialNotices,
                                cercaniasAlerts = cercaniasAlerts,
                                isMetroAlertsLoading = isMetroAlertsLoading,
                                isCercaniasAlertsLoading = isCercaniasAlertsLoading,
                                weatherData = weatherData,
                                calendarItems = calendarItems,
                                isOnline = isOnline,
                                onOpenSettings = { activeTab = DashboardTab.Ajustes },
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
                                onSyncCalendarClick = {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        viewModel.syncGoogleCalendarEvents(force = true)
                                        android.widget.Toast.makeText(context, if (appLanguage == AppLanguage.ES) "Sincronizando eventos..." else "Sincronitzant esdeveniments...", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                    }
                                },
                                onAddCalendarClick = {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                                            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        showAddDialog = true
                                    }
                                },
                                onDeleteCalendarItem = { viewModel.deleteItem(it) },
                                dashboardViewModel = viewModel,
                                metroViewModel = metroViewModel,
                                cercaniasViewModel = cercaniasViewModel,
                                busViewModel = busViewModel,
                                onNavigateToTab = { targetTab, page ->
                                    previousTabForBack = DashboardTab.Inicio
                                    when (targetTab) {
                                        DashboardTab.Bus -> busInitialPage = page
                                        DashboardTab.Metro -> metroInitialPage = page
                                        DashboardTab.Cercanias -> cercaniasInitialPage = page
                                        else -> {}
                                    }
                                    activeTab = targetTab
                                },
                                onOpenRoutePlanner = { dest ->
                                    plannerInitialDestination = dest
                                    isPlannerVisible = true
                                },
                                onRequestLocationPermission = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onConfigureLocationOnMap = { isHome ->
                                    previousTabForBack = DashboardTab.Inicio
                                    activeTab = DashboardTab.Mapa
                                    mapViewModel.selectItem(null)
                                    mapViewModel.setSelectionMode(
                                        if (isHome) com.example.ui.map.MapSelectionMode.SELECTING_HOME
                                        else com.example.ui.map.MapSelectionMode.SELECTING_WORK
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                        DashboardTab.Mapa -> {
                            DashboardMapTab(
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
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    plannerInitialDestination = location
                                    val userLoc = mapViewModel.userLocation.value
                                    if (userLoc != null) {
                                        routePlannerViewModel.setUserLocationAsOrigin(android.location.Location("gps").apply {
                                            latitude = userLoc.latitude
                                            longitude = userLoc.longitude
                                        })
                                    } else {
                                        routePlannerViewModel.useCurrentLocationAsOrigin(context)
                                    }
                                    routePlannerViewModel.setDestination(location)
                                    routePlannerViewModel.dismissSearchPanel()
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
                                onCommuteLocationConfigured = {
                                    activeTab = DashboardTab.Inicio
                                },
                                selectedItinerary = activeMapItinerary,
                                onClearItinerary = {
                                    userDismissedActiveMapItinerary = true
                                    activeMapItinerary = null
                                    plannerInitialDestination = null
                                    if (isViewingPlannerItineraryOnMap && activeTrip == null) {
                                        isViewingPlannerItineraryOnMap = false
                                        isPlannerVisible = true
                                    } else {
                                        isViewingPlannerItineraryOnMap = false
                                    }
                                },
                                onStartTrip = { itinerary ->
                                    val origName = itinerary.legs.firstOrNull()?.fromName
                                        ?: if (appLanguage == AppLanguage.ES) "Tu ubicación" else "La teua ubicació"
                                    val destName = itinerary.legs.lastOrNull()?.toName
                                        ?: if (appLanguage == AppLanguage.ES) "Destino" else "Destinació"
                                    isViewingPlannerItineraryOnMap = false
                                    userDismissedActiveMapItinerary = false
                                    if (activeTrip != null) {
                                        pendingTripToStart = Triple(itinerary, origName, destName)
                                    } else {
                                        viewModel.startActiveTrip(itinerary, origName, destName)
                                        activeMapItinerary = itinerary
                                        isPlannerVisible = false
                                        activeTab = DashboardTab.Mapa
                                    }
                                },
                                activeTripBottomPadding = dynamicBottomTripPadding,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                DashboardTab.Bus -> {
                    DashboardBusTab(
                        viewModel = viewModel,
                        metroViewModel = metroViewModel,
                        isDarkMode = isDarkMode,
                        initialPage = busInitialPage,
                        activeTripBottomPadding = dynamicBottomTripPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
                DashboardTab.Metro -> {
                    DashboardMetroTab(
                        metroViewModel = metroViewModel,
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode,
                        initialPage = metroInitialPage,
                        activeTripBottomPadding = dynamicBottomTripPadding,
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
                        } else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
                DashboardTab.Cercanias -> {
                    DashboardCercaniasTab(
                        cercaniasViewModel = cercaniasViewModel,
                        isDarkMode = isDarkMode,
                        initialPage = cercaniasInitialPage,
                        activeTripBottomPadding = dynamicBottomTripPadding,
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
                        } else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
                DashboardTab.Ajustes -> {
                    AjustesScreen(
                        viewModel = viewModel,
                        cercaniasViewModel = cercaniasViewModel,
                        metroViewModel = metroViewModel,
                        onBackClick = { activeTab = DashboardTab.Inicio },
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        activeTripBottomPadding = dynamicBottomTripPadding
                    )
                }
                    } // closes when (currentTab)
                    } // closes Crossfade

                    // Active Trip Floating Overlay (Transit GO)
                    ActiveTripBannerHost(
                        activeTrip = activeTrip,
                        isPlannerVisible = isPlannerVisible,
                        isViewingActiveRouteOnMap = isViewingActiveRouteOnMap,
                        appLanguage = appLanguage,
                        realTimeTripStatus = realTimeTripStatus,
                        isRecalculatingTransfer = isRecalculatingTransfer,
                        recalculateError = recalculateError,
                        onExpandDetails = { showActiveTripDetails = true },
                        onCancelTrip = {
                            viewModel.cancelActiveTrip()
                            if (activeMapItinerary != null) {
                                activeMapItinerary = null
                            }
                        },
                        onAdvanceLeg = { newIdx -> viewModel.confirmBoarding(newIdx) },
                        onRecalculateTransfer = { viewModel.recalculateMissedTransfer() },
                        onDismissRecalculateError = { viewModel.dismissRecalculateError() },
                        onOpenRouteOnMap = {
                            activeTrip?.let { currentActiveTrip ->
                                userDismissedActiveMapItinerary = false
                                activeMapItinerary = currentActiveTrip.itinerary
                                activeTab = DashboardTab.Mapa
                            }
                        },
                        onHeightChanged = { h ->
                            if (h > 0.dp) {
                                activeTripOverlayMeasuredHeight = h
                            }
                        },
                        unifiedTripSnapshot = unifiedTripSnapshot,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )

                    // Discreet Persistent Offline Banner
                    OfflineBanner(
                        isOnline = isOnline,
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode,
                        bottomPadding = dynamicBottomTripPadding,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                } // closes BoxWithConstraints
            } // closes Scaffold
            } // closes else (onboarding conditional)

        // Back navigation handling for Onboarding
        if (shouldShowOnboarding) {
            BackHandler {
                viewModel.completeOnboarding()
            }
        }

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

        if (isPlannerVisible) {
            RoutePlannerScreen(
                userLocation = userAndroidLocation,
                initialDestination = plannerInitialDestination,
                onInitialDestinationConsumed = {
                    plannerInitialDestination = null
                },
                onNavigateBack = {
                    isPlannerVisible = false
                    isViewingPlannerItineraryOnMap = false
                    plannerInitialDestination = null
                },
                onSelectItineraryForMap = { itinerary ->
                    activeMapItinerary = itinerary
                    isViewingPlannerItineraryOnMap = true
                    isPlannerVisible = false
                    activeTab = DashboardTab.Mapa
                },
                onStartTrip = { itinerary, originName, destName ->
                    isViewingPlannerItineraryOnMap = false
                    userDismissedActiveMapItinerary = false
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

        // Dialogs, Sheets and Modals Host
        DashboardDialogsHost(
            showAddDialog = showAddDialog,
            onDismissAddDialog = { showAddDialog = false },
            onAddCalendarEvent = { title, desc, offset, dur, color ->
                viewModel.addEvent(title, desc, offset, dur, color)
                showAddDialog = false
            },
            onAddCalendarTask = { title, desc, offset, color ->
                viewModel.addTask(title, desc, offset, color)
                showAddDialog = false
            },
            showStationConfigDialog = showStationConfigDialog,
            onDismissStationConfigDialog = { showStationConfigDialog = false },
            showCercaniasStationConfigDialog = showCercaniasStationConfigDialog,
            onDismissCercaniasStationConfigDialog = { showCercaniasStationConfigDialog = false },
            showMetroSearchDialog = showMetroSearchDialog,
            onDismissMetroSearchDialog = { showMetroSearchDialog = false },
            metroSearchQuery = metroSearchQuery,
            filteredStations = filteredStations,
            favoriteStations = favoriteStations.toList(),
            onMetroQueryChange = { metroViewModel.setMetroSearchQuery(it) },
            onSelectMetroSearchStation = { stationId ->
                metroViewModel.selectStation(stationId)
                showMetroSearchDialog = false
            },
            onToggleFavoriteMetroStation = { stationId ->
                metroViewModel.toggleFavoriteStation(stationId)
            },
            metroViewModel = metroViewModel,
            cercaniasViewModel = cercaniasViewModel,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            showActiveTripDetails = showActiveTripDetails,
            onDismissActiveTripDetails = { showActiveTripDetails = false },
            onViewActiveTripOnMap = { tripItinerary ->
                showActiveTripDetails = false
                userDismissedActiveMapItinerary = false
                activeMapItinerary = tripItinerary
                activeTab = DashboardTab.Mapa
            },
            activeTrip = activeTrip,
            realTimeTripStatus = realTimeTripStatus,
            pendingTripToStart = pendingTripToStart,
            onConfirmReplaceActiveTrip = { itinerary, originName, destName ->
                viewModel.startActiveTrip(itinerary, originName, destName)
                userDismissedActiveMapItinerary = false
                activeMapItinerary = itinerary
                isPlannerVisible = false
                activeTab = DashboardTab.Mapa
                pendingTripToStart = null
            },
            onDismissReplaceActiveTrip = { pendingTripToStart = null },
            showTransferRiskDialog = showTransferRiskDialog,
            isRecalculatingTransfer = isRecalculatingTransfer,
            recalculateError = recalculateError,
            onRecalculateTransfer = { viewModel.recalculateMissedTransfer() },
            onDismissTransferRiskDialog = { viewModel.dismissTransferRiskDialog() }
        )

    } // closes Box
    } // closes Surface
} // closes VlcMetroTheme
} // closes DashboardScreen







