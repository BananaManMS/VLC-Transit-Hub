@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.example.data.model.MetroStation
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
    cercaniasViewModel: CercaniasViewModel? = null,
    metroViewModel: com.example.ui.metro.MetroViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val handleLocationResult: (Double, Double) -> Unit = { lat, lng ->
        viewModel.updateLocation(lat, lng)
    }

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isFahrenheit by viewModel.isFahrenheit.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val shouldShowOnboarding by viewModel.shouldShowOnboarding.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }

    val weatherCity by viewModel.weatherCity.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val useGpsOnOpen by viewModel.useGpsOnOpen.collectAsState()

    val calendarItems by viewModel.calendarItems.collectAsState()

    val metroIncidents by viewModel.activeMetroIncidents.collectAsState()
    val metroSpecialNotices by viewModel.metroSpecialNotices.collectAsState()
    val isMetroAlertsLoading by viewModel.isMetroAlertsLoading.collectAsState()
    val cercaniasAlerts by viewModel.activeCercaniasAlerts.collectAsState()
    val isCercaniasAlertsLoading by viewModel.isCercaniasAlertsLoading.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val lastLocation by viewModel.lastLocation.collectAsState()

    val appUpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.update.AppUpdateViewModel>()

    var activeTab by remember { mutableStateOf(DashboardTab.Inicio) }
    var previousTabForBack by remember { mutableStateOf<DashboardTab?>(null) }
    var busInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var metroInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var cercaniasInitialPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    var plannerInitialDestination by remember { mutableStateOf<PlannerLocation?>(null) }
    var pendingSelectedMetroStationId by remember { mutableStateOf<String?>(null) }
    var pendingSelectedCercaniasStationId by remember { mutableStateOf<String?>(null) }
    var pendingSelectedBusStopId by remember { mutableStateOf<String?>(null) }
    var isPlannerVisible by remember { mutableStateOf(false) }
    var isNetworkPlansVisible by remember { mutableStateOf(false) }
    var isViewingPlannerItineraryOnMap by remember { mutableStateOf(false) }
    var activeMapItinerary by remember { mutableStateOf<PlannedItinerary?>(null) }
    var userDismissedActiveMapItinerary by remember { mutableStateOf(false) }
    var lastTrackedActiveTripStartMs by remember { mutableStateOf<Long?>(null) }
    var instantTabSwitch by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) {
        if (instantTabSwitch) {
            instantTabSwitch = false
        }
    }

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
            android.widget.Toast.makeText(context, context.getString(com.example.R.string.toast_syncing_events), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, context.getString(com.example.R.string.toast_calendar_permission_denied), android.widget.Toast.LENGTH_LONG).show()
        }
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
            if (LocationUtils.hasLocationPermission(context)) {
                LocationUtils.requestDeviceLocation(context) { lat, lng ->
                    viewModel.updateLocation(lat, lng)
                    viewModel.updateWeatherByLocation(lat, lng, context)
                }
                LocationUtils.getLocationUpdates(context, intervalMs = 15000L, minDistanceMeters = 10.0f)
                    .collect { loc ->
                        viewModel.updateLocation(loc.latitude, loc.longitude)
                    }
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
                val metroVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.metro.MetroViewModel>()
                val cercaniasVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.cercanias.CercaniasViewModel>()
                OnboardingScreen(
                    cercaniasViewModel = cercaniasVm,
                    metroViewModel = metroVm,
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
                                    busInitialPage = 0
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
                    var offlineBannerMeasuredHeight by remember { mutableStateOf(52.dp) }
                    var isOfflineBannerDismissed by remember { mutableStateOf(false) }

                    LaunchedEffect(isOnline) {
                        if (isOnline) {
                            isOfflineBannerDismissed = false
                        }
                    }

                    val activeTripHeightNeeded = if (activeTrip != null && !isPlannerVisible) (activeTripOverlayMeasuredHeight + 8.dp) else 0.dp
                    val offlineBannerHeightNeeded = if (!isOnline && !isOfflineBannerDismissed) (offlineBannerMeasuredHeight + 8.dp) else 0.dp

                    val dynamicBottomTripPadding by animateDpAsState(
                        targetValue = activeTripHeightNeeded + offlineBannerHeightNeeded,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dynamicBottomTripPadding"
                    )

                    Crossfade(
                        targetState = activeTab,
                        animationSpec = if (instantTabSwitch) snap() else tween(durationMillis = 280),
                        label = "tab_fade_transition"
                    ) { currentTab ->
                        when (currentTab) {
                        DashboardTab.Inicio -> {
                            DashboardHomeTab(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    if (LocationUtils.hasLocationPermission(context)) {
                                        LocationUtils.requestDeviceLocation(context, handleLocationResult)
                                    }
                                    viewModel.refreshAll()
                                    viewModel.fetchAllDashboardAlerts(force = true)
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
                                        android.widget.Toast.makeText(context, context.getString(com.example.R.string.toast_syncing_events), android.widget.Toast.LENGTH_SHORT).show()
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
                                busViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.bus.BusViewModel>(),
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
                                onSelectMetroStation = { stationId ->
                                    pendingSelectedMetroStationId = stationId
                                },
                                onSelectCercaniasStation = { stationId ->
                                    pendingSelectedCercaniasStationId = stationId
                                },
                                onOpenRoutePlanner = { dest ->
                                    previousTabForBack = DashboardTab.Inicio
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
                                onConfigureLocationOnMap = { commuteType ->
                                    previousTabForBack = DashboardTab.Inicio
                                    activeTab = DashboardTab.Mapa
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                        DashboardTab.Mapa -> {
                            val mapVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.map.MapViewModel>()
                            val metroVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.metro.MetroViewModel>()
                            val cercaniasVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.cercanias.CercaniasViewModel>()
                            val plannerVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.routing.RoutePlannerViewModel>()
                            
                            val currentLoc = lastLocation ?: com.example.util.LocationUtils.lastKnownLocationCache?.let { Pair(it.latitude, it.longitude) }
                            if (currentLoc != null) {
                                mapVm.updateLocation(currentLoc.first, currentLoc.second)
                            }

                            LaunchedEffect(lastLocation) {
                                lastLocation?.let { (lat, lon) ->
                                    mapVm.updateLocation(lat, lon)
                                    metroVm.setLocation(android.location.Location("gps").apply {
                                        latitude = lat
                                        longitude = lon
                                    })
                                    cercaniasVm.updateLocation(lat, lon)
                                }
                            }
                            DashboardMapTab(
                                mapViewModel = mapVm,
                                dashboardViewModel = viewModel,
                                metroViewModel = metroVm,
                                cercaniasViewModel = cercaniasVm,
                                isDarkMode = isDarkMode,
                                appLanguage = appLanguage,
                                onNavigateToMetro = { stationId ->
                                    metroVm.selectRealTimeStation(stationId)
                                    previousTabForBack = DashboardTab.Mapa
                                    activeTab = DashboardTab.Metro
                                },
                                onNavigateToCercanias = { stationId ->
                                    cercaniasVm.selectCercaniasStation(stationId)
                                    previousTabForBack = DashboardTab.Mapa
                                    activeTab = DashboardTab.Cercanias
                                },
                                onNavigateToRoutePlanner = { location ->
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    plannerInitialDestination = location
                                    val userLoc = mapVm.userLocation.value
                                    if (userLoc != null) {
                                        plannerVm.setUserLocationAsOrigin(android.location.Location("gps").apply {
                                            latitude = userLoc.latitude
                                            longitude = userLoc.longitude
                                        })
                                    } else {
                                        plannerVm.useCurrentLocationAsOrigin(context)
                                    }
                                    plannerVm.setDestination(location)
                                    plannerVm.dismissSearchPanel()
                                    isPlannerVisible = true
                                },
                                onPlannerLocationPicked = { loc, isOrigin ->
                                    plannerInitialDestination = null
                                    if (isOrigin) {
                                        plannerVm.setOrigin(loc)
                                    } else {
                                        plannerVm.setDestination(loc)
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
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        val hasNotifPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (!hasNotifPerm) {
                                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    val origName = itinerary.legs.firstOrNull()?.fromName
                                        ?: if (appLanguage == AppLanguage.ES) "Tu ubicación" else "La teua ubicació"
                                    val destName = itinerary.legs.lastOrNull()?.toName
                                        ?: if (appLanguage == AppLanguage.ES) "Destino" else "Destinació"
                                    isViewingPlannerItineraryOnMap = false
                                    userDismissedActiveMapItinerary = false
                                    if (activeTrip != null) {
                                        pendingTripToStart = Triple(itinerary, origName, destName)
                                    } else {
                                        mapVm.disableFollowUser()
                                        viewModel.startActiveTrip(itinerary, origName, destName)
                                        activeMapItinerary = itinerary
                                        isPlannerVisible = false
                                        activeTab = DashboardTab.Mapa
                                    }
                                },
                                onOpenNetworkPlans = {
                                    isNetworkPlansVisible = true
                                },
                                activeTripBottomPadding = dynamicBottomTripPadding,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                DashboardTab.Bus -> {
                    val metroVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.metro.MetroViewModel>()
                    LaunchedEffect(lastLocation) {
                        lastLocation?.let { (lat, lon) ->
                            metroVm.setLocation(android.location.Location("gps").apply {
                                latitude = lat
                                longitude = lon
                            })
                        }
                    }
                    val busVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.bus.BusViewModel>()
                    LaunchedEffect(pendingSelectedBusStopId) {
                        pendingSelectedBusStopId?.let { stopId ->
                            busVm.selectBusStopById(stopId)
                            pendingSelectedBusStopId = null
                        }
                    }
                    DashboardBusTab(
                        viewModel = viewModel,
                        metroViewModel = metroVm,
                        busViewModel = busVm,
                        isDarkMode = isDarkMode,
                        initialPage = busInitialPage,
                        activeTripBottomPadding = dynamicBottomTripPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
                DashboardTab.Metro -> {
                    val metroVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.metro.MetroViewModel>()
                    LaunchedEffect(lastLocation) {
                        lastLocation?.let { (lat, lon) ->
                            metroVm.setLocation(android.location.Location("gps").apply {
                                latitude = lat
                                longitude = lon
                            })
                        }
                    }
                    LaunchedEffect(pendingSelectedMetroStationId) {
                        pendingSelectedMetroStationId?.let { stId ->
                            metroVm.selectRealTimeStation(stId, isUserAction = true)
                            pendingSelectedMetroStationId = null
                        }
                    }
                    DashboardMetroTab(
                        metroViewModel = metroVm,
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
                    val cercaniasVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.cercanias.CercaniasViewModel>()
                    LaunchedEffect(lastLocation) {
                        lastLocation?.let { (lat, lon) ->
                            cercaniasVm.updateLocation(lat, lon)
                        }
                    }
                    LaunchedEffect(pendingSelectedCercaniasStationId) {
                        pendingSelectedCercaniasStationId?.let { stId ->
                            cercaniasVm.selectCercaniasStation(stId)
                            pendingSelectedCercaniasStationId = null
                        }
                    }
                    DashboardCercaniasTab(
                        cercaniasViewModel = cercaniasVm,
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
                        onOpenMap = { activeTab = DashboardTab.Mapa },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
                DashboardTab.Ajustes -> {
                    AjustesScreen(
                        viewModel = viewModel,
                        appUpdateViewModel = appUpdateViewModel,
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
                        bottomPadding = activeTripHeightNeeded,
                        onDismiss = { isOfflineBannerDismissed = true },
                        onHeightChanged = { h -> if (h > 0.dp) offlineBannerMeasuredHeight = h },
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
            !isNetworkPlansVisible &&
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

        AnimatedVisibility(
            visible = isPlannerVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(180)),
            exit = if (instantTabSwitch) {
                ExitTransition.None
            } else {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(180))
            }
        ) {
            val plannerVm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.routing.RoutePlannerViewModel>()
            val userLocation by viewModel.lastLocation.collectAsState()
            val userAndroidLocation = remember(userLocation) {
                userLocation?.let { (lat, lon) ->
                    android.location.Location("gps").apply {
                        latitude = lat
                        longitude = lon
                    }
                }
            }
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
                    instantTabSwitch = true
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
                        instantTabSwitch = true
                        isPlannerVisible = false
                        activeTab = DashboardTab.Mapa
                    }
                },
                onPickOnMapClick = { isOrigin ->
                    instantTabSwitch = true
                    isPlannerVisible = false
                    activeTab = DashboardTab.Mapa
                },
                viewModel = plannerVm,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = isNetworkPlansVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(280, easing = FastOutLinearInEasing)
            ) + fadeOut(tween(200))
        ) {
            com.example.ui.map.networkmaps.NetworkPlansScreen(
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onBack = { isNetworkPlansVisible = false }
            )
        }

        val metroSearchVm = if (showMetroSearchDialog || showStationConfigDialog) {
            androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.metro.MetroViewModel>()
        } else null
        val cercaniasDialogVm = if (showCercaniasStationConfigDialog) {
            androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.cercanias.CercaniasViewModel>()
        } else null

        val metroSearchQuery by (metroSearchVm?.metroSearchQuery ?: remember { kotlinx.coroutines.flow.MutableStateFlow("") }).collectAsState()
        val filteredStations by (metroSearchVm?.searchedStations ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<MetroStation>()) }).collectAsState()
        val favoriteStations by (metroSearchVm?.favoriteStations ?: viewModel.favoriteMetroStations).collectAsState()

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
            onMetroQueryChange = { metroSearchVm?.setMetroSearchQuery(it) },
            onSelectMetroSearchStation = { stationId ->
                metroSearchVm?.selectStation(stationId)
                showMetroSearchDialog = false
            },
            onToggleFavoriteMetroStation = { stationId ->
                metroSearchVm?.toggleFavoriteStation(stationId)
            },
            metroViewModel = metroSearchVm,
            cercaniasViewModel = cercaniasDialogVm,
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
                instantTabSwitch = true
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

        val remoteAnnouncements by viewModel.remoteAnnouncements.collectAsState()
        if (remoteAnnouncements.isNotEmpty() && !shouldShowOnboarding) {
            com.example.ui.components.RemoteAnnouncementDialog(
                announcements = remoteAnnouncements,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onDismissSingle = { id -> viewModel.dismissRemoteAnnouncement(id) },
                onCloseAll = { viewModel.dismissAllRemoteAnnouncements() }
            )
        }

        com.example.ui.update.DashboardAutoUpdateHandler(
            appUpdateViewModel = appUpdateViewModel,
            hasActiveRemoteAnnouncements = remoteAnnouncements.isNotEmpty(),
            isShowingOnboarding = shouldShowOnboarding,
            isDarkMode = isDarkMode
        )

    } // closes Box
    } // closes Surface
} // closes VlcMetroTheme
} // closes DashboardScreen







