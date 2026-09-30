package com.example.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.NominatimResult
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.ActiveItineraryBanner
import com.example.ui.map.components.DetailSheetState
import com.example.ui.map.components.DisambiguationDialog
import com.example.ui.map.components.EditBusStopAliasDialog
import com.example.ui.map.components.MapControlsOverlay
import com.example.ui.map.components.MapDetailBottomSheetsHost
import com.example.ui.map.components.MapLocationSelectionOverlay
import com.example.ui.map.components.NearbyStopsBottomSheet
import com.example.ui.map.components.OsmdroidMapView
import com.example.ui.map.components.SaveFavoriteDialog
import com.example.ui.map.components.SheetState
import com.example.ui.metro.MetroViewModel
import com.example.ui.routing.PlannerLocation
import com.example.ui.routing.components.RouteDetailBottomSheet
import kotlinx.coroutines.delay

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    dashboardViewModel: DashboardViewModel,
    metroViewModel: MetroViewModel? = null,
    cercaniasViewModel: CercaniasViewModel? = null,
    mapViewModel: MapViewModel = viewModel(),
    isDarkMode: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.CA,
    onNavigateToMetro: ((String) -> Unit)? = null,
    onNavigateToCercanias: ((String) -> Unit)? = null,
    onNavigateToRoutePlanner: ((PlannerLocation) -> Unit)? = null,
    onPlannerLocationPicked: ((PlannerLocation, Boolean) -> Unit)? = null,
    onCancelPlannerLocationPicking: (() -> Unit)? = null,
    onCommuteLocationConfigured: (() -> Unit)? = null,
    selectedItinerary: PlannedItinerary? = null,
    onClearItinerary: (() -> Unit)? = null,
    onOpenRouteDetail: (() -> Unit)? = null,
    onStartTrip: ((PlannedItinerary) -> Unit)? = null,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val mapFilter by mapViewModel.mapFilter.collectAsState()
    val visibleBusStops by mapViewModel.visibleBusStops.collectAsState()
    val visibleMetrobusStops by mapViewModel.visibleMetrobusStops.collectAsState()
    val visibleMetroStations by mapViewModel.visibleMetroStations.collectAsState()
    val visibleCercaniasStations by mapViewModel.visibleCercaniasStations.collectAsState()
    val liveCercaniasVehicles by mapViewModel.liveCercaniasVehicles.collectAsState()
    val selectedItem by mapViewModel.selectedMapItem.collectAsState()
    val selectedBusLineFilters by mapViewModel.selectedBusLineFilters.collectAsState()
    val selectedDirectionFilter by mapViewModel.selectedDirectionFilter.collectAsState()
    val userLocation by mapViewModel.userLocation.collectAsState()
    val isFollowingUser by mapViewModel.isFollowingUser.collectAsState()
    val cameraTarget by mapViewModel.cameraTarget.collectAsState()
    val debouncedCameraTarget by mapViewModel.debouncedCameraTarget.collectAsState()
    val cameraZoom by mapViewModel.cameraZoom.collectAsState()
    val cameraAnimTrigger by mapViewModel.cameraAnimTrigger.collectAsState()
    val busStopAliases by mapViewModel.busStopAliases.collectAsState()
    val favoriteBusStops by mapViewModel.favoriteBusStops.collectAsState()
    val favoriteMetroStations by mapViewModel.favoriteMetroStations.collectAsState()
    val favoriteCercaniasStations by mapViewModel.favoriteCercaniasStations.collectAsState()
    val favoriteValenbisiSet by mapViewModel.favoriteValenbisi.collectAsState()
    val valenbisiAliasesMap by mapViewModel.valenbisiAliases.collectAsState()

    val searchQuery by mapViewModel.searchQuery.collectAsState()
    val selectedNearbyTab by mapViewModel.selectedNearbyTab.collectAsState()
    val nearbyTransitItems by mapViewModel.nearbyTransitItems.collectAsState()
    val valenbisiStations by mapViewModel.valenbisiStations.collectAsState()
    val nearbyValenbisiStations by mapViewModel.nearbyValenbisiStations.collectAsState()
    val searchResults by mapViewModel.searchResults.collectAsState()
    val isSearching by mapViewModel.isSearching.collectAsState()
    val destinationLocation by mapViewModel.destinationLocation.collectAsState()
    val destinationTitle by mapViewModel.destinationTitle.collectAsState()
    val isSatelliteMode by mapViewModel.isSatelliteMode.collectAsState()

    // Phase 2 State collections
    val recentSearches by mapViewModel.recentSearches.collectAsState()
    val homeLocation by mapViewModel.homeLocation.collectAsState()
    val workLocation by mapViewModel.workLocation.collectAsState()
    val customFavorites by mapViewModel.customFavorites.collectAsState()
    val unifiedTransitFavorites by mapViewModel.unifiedTransitFavorites.collectAsState()
    val selectionMode by mapViewModel.selectionMode.collectAsState()
    val selectedMetrobusShapes by mapViewModel.selectedMetrobusShapes.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var isSearchFocused by remember { mutableStateOf(false) }
    var isMapMoving by remember { mutableStateOf(false) }

    var editingStopForAlias by remember { mutableStateOf<GeoportalStopEntity?>(null) }
    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var locationToSave by remember { mutableStateOf<NominatimResult?>(null) }
    var showRouteDetailSheet by remember { mutableStateOf(false) }
    var zoomInTrigger by remember { mutableStateOf(0) }
    var zoomOutTrigger by remember { mutableStateOf(0) }
    var disambiguationItems by remember { mutableStateOf<List<SelectedMapItem>?>(null) }
    val density = LocalDensity.current
    val defaultCollapsedHeightPx = with(density) { 240.dp.toPx() }
    val defaultDetailHalfHeightPx = with(density) { 340.dp.toPx() }
    val defaultAddressHalfHeightPx = with(density) { 210.dp.toPx() }

    var currentNearbySheetHeight by remember { mutableStateOf(240.dp) }
    var nearbySheetHeightPx by remember { mutableFloatStateOf(defaultCollapsedHeightPx) }
    var busStopDetailSheetHeightPx by remember { mutableFloatStateOf(defaultDetailHalfHeightPx) }
    var metroStationDetailSheetHeightPx by remember { mutableFloatStateOf(defaultDetailHalfHeightPx) }
    var cercaniasStationDetailSheetHeightPx by remember { mutableFloatStateOf(defaultDetailHalfHeightPx) }
    var valenbisiStationDetailSheetHeightPx by remember { mutableFloatStateOf(defaultDetailHalfHeightPx) }
    var metrobusStopDetailSheetHeightPx by remember { mutableFloatStateOf(defaultDetailHalfHeightPx) }
    var addressDetailSheetHeightPx by remember { mutableFloatStateOf(defaultAddressHalfHeightPx) }
    var nearbySheetState by remember { mutableStateOf(SheetState.COLLAPSED) }
    var detailSheetState by remember { mutableStateOf(DetailSheetState.HALF_EXPANDED) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    mapViewModel.startLocationTracking(context)
                    if (mapFilter.showValenbisi) {
                        mapViewModel.startValenbisiPeriodicRefresh()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    mapViewModel.stopLocationTracking()
                    mapViewModel.stopValenbisiPeriodicRefresh()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewModel.stopLocationTracking()
            mapViewModel.stopValenbisiPeriodicRefresh()
        }
    }

    LaunchedEffect(Unit) {
        mapViewModel.reloadFavorites()
    }

    LaunchedEffect(selectedItinerary) {
        if (selectedItinerary != null) {
            mapViewModel.selectItem(null)
            disambiguationItems = null
            mapViewModel.clearDestination()
        }
    }

    LaunchedEffect(cameraTarget, cameraZoom, selectionMode) {
        if (selectionMode != MapSelectionMode.NORMAL) {
            isMapMoving = true
            delay(200)
            isMapMoving = false
        }
    }

    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            detailSheetState = DetailSheetState.HALF_EXPANDED
        }
    }

    val isMapBackHandlerEnabled = !disambiguationItems.isNullOrEmpty() ||
        showRouteDetailSheet ||
        (selectedItem != null && (detailSheetState == DetailSheetState.FULLY_EXPANDED || detailSheetState == DetailSheetState.HALF_EXPANDED)) ||
        selectedItem != null ||
        selectedItinerary != null ||
        selectionMode != MapSelectionMode.NORMAL ||
        nearbySheetState == SheetState.EXPANDED ||
        isSearchFocused ||
        searchQuery.isNotEmpty()

    BackHandler(enabled = isMapBackHandlerEnabled) {
        when {
            isSearchFocused || searchQuery.isNotEmpty() -> {
                isSearchFocused = false
                mapViewModel.setSearchQuery("")
                focusManager.clearFocus()
                keyboardController?.hide()
            }
            !disambiguationItems.isNullOrEmpty() -> {
                disambiguationItems = null
            }
            showRouteDetailSheet -> {
                showRouteDetailSheet = false
            }
            selectedItem != null -> {
                mapViewModel.selectItem(null)
            }
            selectedItinerary != null -> {
                onClearItinerary?.invoke()
            }
            selectionMode != MapSelectionMode.NORMAL -> {
                val wasPlannerPicking = (selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN || 
                                        selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION)
                mapViewModel.setSelectionMode(MapSelectionMode.NORMAL)
                if (wasPlannerPicking) {
                    onCancelPlannerLocationPicking?.invoke()
                }
            }
            nearbySheetState == SheetState.EXPANDED -> {
                nearbySheetState = SheetState.COLLAPSED
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Cap bottom sheets expansion to 148.dp from top to leave space below the search bar and avoid status bar overlap
        val maxExpandedSheetHeight = (maxHeight - 148.dp).coerceAtLeast(300.dp)

        // Real-time bottom panel offset: active for BusStop/Metro/Cercanias/Valenbisi/Metrobus/Address detail sheet OR NearbyStopsBottomSheet
        val isBusStopDetailActive = (selectedItem is SelectedMapItem.BusStop && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL)
        val isMetroStationDetailActive = (selectedItem is SelectedMapItem.Metro && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL)
        val isCercaniasStationDetailActive = (selectedItem is SelectedMapItem.Cercanias && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL)
        val isValenbisiStationDetailActive = (selectedItem is SelectedMapItem.Valenbisi && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL)
        val isMetrobusStopDetailActive = (selectedItem is SelectedMapItem.MetrobusStopItem && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL)
        val isAddressDetailActive = (selectedItem is SelectedMapItem.Address && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL)
        val isNearbySheetActive = (selectedItem == null && searchQuery.isEmpty() && !isSearchFocused && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL)
        val rawBottomOffsetPx = when {
            isBusStopDetailActive -> busStopDetailSheetHeightPx
            isMetroStationDetailActive -> metroStationDetailSheetHeightPx
            isCercaniasStationDetailActive -> cercaniasStationDetailSheetHeightPx
            isValenbisiStationDetailActive -> valenbisiStationDetailSheetHeightPx
            isMetrobusStopDetailActive -> metrobusStopDetailSheetHeightPx
            isAddressDetailActive -> addressDetailSheetHeightPx
            isNearbySheetActive -> nearbySheetHeightPx
            else -> 0f
        }

        val animatedBottomOffsetPx by animateFloatAsState(
            targetValue = rawBottomOffsetPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "mapBottomOffsetAnim"
        )

        // Base Osmdroid Map
        OsmdroidMapView(
            modifier = Modifier.fillMaxSize(),
            isDarkMode = isDarkMode,
            isSatelliteMode = isSatelliteMode,
            cameraTarget = cameraTarget,
            cameraZoom = cameraZoom,
            cameraAnimTrigger = cameraAnimTrigger,
            zoomInTrigger = zoomInTrigger,
            zoomOutTrigger = zoomOutTrigger,
            userLocation = userLocation,
            destinationLocation = destinationLocation,
            destinationTitle = destinationTitle,
            busStops = visibleBusStops,
            metrobusStops = visibleMetrobusStops,
            metroStations = visibleMetroStations,
            cercaniasStations = visibleCercaniasStations,
            liveCercaniasVehicles = liveCercaniasVehicles,
            valenbisiStations = valenbisiStations,
            customFavorites = customFavorites,
            homeLocation = homeLocation,
            workLocation = workLocation,
            mapFilter = mapFilter,
            busStopAliases = busStopAliases,
            appLanguage = appLanguage,
            selectedItinerary = selectedItinerary,
            selectedMapItem = selectedItem,
            selectedBusLineFilters = selectedBusLineFilters,
            selectedMetrobusShapes = selectedMetrobusShapes,
            selectedDirectionFilter = selectedDirectionFilter,
            bottomPanelOffsetPx = animatedBottomOffsetPx,
            onSelectItem = { item ->
                if (selectedItinerary == null) {
                    mapViewModel.selectItem(item)
                }
            },
            onMapClick = {
                if (isSearchFocused || searchQuery.isNotEmpty()) {
                    isSearchFocused = false
                    mapViewModel.setSearchQuery("")
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                mapViewModel.clearDestination()
                if (selectedItem !is SelectedMapItem.BusStop &&
                    selectedItem !is SelectedMapItem.Metro &&
                    selectedItem !is SelectedMapItem.Cercanias &&
                    selectedItem !is SelectedMapItem.Valenbisi &&
                    selectedItem !is SelectedMapItem.MetrobusStopItem) {
                    mapViewModel.selectItem(null)
                }
                if (nearbySheetState == SheetState.EXPANDED) {
                    nearbySheetState = SheetState.COLLAPSED
                }
                if (detailSheetState != DetailSheetState.COLLAPSED) {
                    detailSheetState = DetailSheetState.COLLAPSED
                }
            },
            onMapTouch = {
                if (isSearchFocused || searchQuery.isNotEmpty()) {
                    isSearchFocused = false
                    mapViewModel.setSearchQuery("")
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                mapViewModel.disableFollowUser()
            },
            onMapPan = {
                if (nearbySheetState == SheetState.EXPANDED) {
                    nearbySheetState = SheetState.COLLAPSED
                }
                if (detailSheetState != DetailSheetState.COLLAPSED) {
                    detailSheetState = DetailSheetState.COLLAPSED
                }
            },
            onCameraPositionChanged = { center, zoom ->
                if (selectionMode != MapSelectionMode.NORMAL) {
                    isMapMoving = true
                }
                mapViewModel.updateCameraPosition(center, zoom)
            },
            onZoomLevelChanged = { newZoom ->
                mapViewModel.setCameraZoom(newZoom)
            },
            onShowDisambiguationMenu = { items ->
                if (selectedItinerary == null) {
                    disambiguationItems = items
                }
            },
            onMapLongClick = {
                if (selectedItinerary == null) {
                    mapViewModel.onMapLongClick(it)
                }
            }
        )

        // Subtle top gradient scrim to protect status bar indicators (clock, battery) and provide a clean transition
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (isDarkMode || isSatelliteMode) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        val isAtUserLocation = userLocation != null && cameraTarget.distanceToAsDouble(userLocation) < 15.0
        // Central focal crosshair indicator when no individual item is selected, not centered on user, and NOT in route/itinerary preview
        if (selectedItem == null && !isAtUserLocation && selectedItinerary == null) {
            val density = LocalDensity.current
            val crosshairOffsetY = with(density) { (animatedBottomOffsetPx / 2f).toDp() }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
                    .offset(y = -crosshairOffsetY)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x3310B981), // semi-transparent emerald
                    border = BorderStroke(1.5.dp, if (isDarkMode) Color.White else Color(0xFF10B981)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkMode) Color.White else Color(0xFF10B981),
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                }
            }
        }

        // Active Itinerary Banner Overlay
        if (selectedItinerary != null) {
            ActiveItineraryBanner(
                modifier = Modifier.align(Alignment.TopCenter),
                itinerary = selectedItinerary,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                onClearItinerary = onClearItinerary,
                onOpenRouteDetail = {
                    showRouteDetailSheet = true
                    onOpenRouteDetail?.invoke()
                },
                onStartTrip = if (onStartTrip != null) {
                    { onStartTrip(selectedItinerary) }
                } else null
            )
        }

        // Map Controls & Filters Overlay
        if (selectionMode == MapSelectionMode.NORMAL) {
            MapControlsOverlay(
                activeFilter = mapFilter,
                busCount = visibleBusStops.size,
                metroCount = visibleMetroStations.size,
                cameraZoom = cameraZoom,
                isDarkMode = isDarkMode,
                onFilterToggle = { filterType ->
                    mapViewModel.toggleFilter(filterType)
                    if (nearbySheetState == SheetState.EXPANDED) {
                        nearbySheetState = SheetState.COLLAPSED
                    }
                },
                onRecenterUser = { mapViewModel.centerOnUser() },
                onZoomIn = { zoomInTrigger++ },
                onZoomOut = { zoomOutTrigger++ },
                onSearchClick = {
                    if (nearbySheetState == SheetState.EXPANDED) {
                        nearbySheetState = SheetState.COLLAPSED
                    }
                },
                searchQuery = searchQuery,
                onSearchQueryChange = { mapViewModel.setSearchQuery(it) },
                searchResults = searchResults,
                onSearchResultClick = { result ->
                    isSearchFocused = false
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    mapViewModel.selectItemFromSearch(result)
                },
                isSearching = isSearching,
                appLanguage = appLanguage,
                isSatelliteMode = isSatelliteMode,
                onToggleSatelliteMode = { mapViewModel.toggleSatelliteMode() },
                hasPersistentBottomPanel = isNearbySheetActive,
                currentNearbySheetHeight = currentNearbySheetHeight,
                bottomPanelHeightPx = animatedBottomOffsetPx,
                isFollowingUser = isFollowingUser,
                selectedItem = selectedItem,
                onDirectionsClick = { item ->
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    isSearchFocused = false
                    mapViewModel.setSearchQuery("")
                    mapViewModel.clearDestination()
                    if (item != null) {
                        val (lat, lon, title) = when (item) {
                            is SelectedMapItem.BusStop -> Triple(item.stop.lat, item.stop.lon, "Parada ${item.stop.id_parada} - ${item.stop.denominacion}")
                            is SelectedMapItem.MetrobusStopItem -> Triple(item.stop.lat, item.stop.lon, "Parada ${item.stop.id_parada} - ${item.stop.denominacion}")
                            is SelectedMapItem.Metro -> Triple(item.station.latitude ?: 0.0, item.station.longitude ?: 0.0, "Metro ${item.station.name}")
                            is SelectedMapItem.Cercanias -> Triple(item.station.lat, item.station.lon, "Estación ${item.station.displayName}")
                            is SelectedMapItem.Valenbisi -> Triple(item.station.latitude, item.station.longitude, "Valenbisi ${item.station.name}")
                            is SelectedMapItem.Address -> Triple(item.result.latitude, item.result.longitude, item.result.displayName.split(",").firstOrNull()?.trim() ?: item.result.displayName)
                            is SelectedMapItem.LiveTrain -> Triple(item.vehicle.latitude ?: 0.0, item.vehicle.longitude ?: 0.0, "Tren ${item.vehicle.trainNum.ifBlank { item.vehicle.routeId }}")
                        }
                        mapViewModel.selectItem(null)
                        onNavigateToRoutePlanner?.invoke(
                            PlannerLocation(title = title, latitude = lat, longitude = lon)
                        )
                    } else if (destinationLocation != null) {
                        val lat = destinationLocation!!.latitude
                        val lon = destinationLocation!!.longitude
                        val title = destinationTitle ?: "Ubicación seleccionada"
                        onNavigateToRoutePlanner?.invoke(
                            PlannerLocation(
                                title = title,
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    } else {
                        onNavigateToRoutePlanner?.invoke(
                            PlannerLocation(
                                title = "Ubicación actual",
                                latitude = userLocation?.latitude ?: 0.0,
                                longitude = userLocation?.longitude ?: 0.0,
                                isUserGps = true
                            )
                        )
                    }
                },
                isItineraryActive = (selectedItinerary != null),
                activeTripBottomPadding = activeTripBottomPadding,
                recentSearches = recentSearches,
                homeLocation = homeLocation,
                workLocation = workLocation,
                customFavorites = customFavorites,
                unifiedTransitFavorites = unifiedTransitFavorites,
                isSearchFocused = isSearchFocused,
                onSearchFocusChange = { isSearchFocused = it },
                onClearRecentSearches = { mapViewModel.clearRecentSearches() },
                onRemoveRecentSearch = { mapViewModel.removeRecentSearch(it) },
                onElegirEnMapaClick = {
                    isSearchFocused = false
                    mapViewModel.selectItem(null)
                    mapViewModel.setSelectionMode(MapSelectionMode.SELECTING_LOCATION)
                },
                onSaveLocationShortcutClick = { isHome ->
                    isSearchFocused = false
                    mapViewModel.selectItem(null)
                    mapViewModel.setSelectionMode(
                        if (isHome) MapSelectionMode.SELECTING_HOME else MapSelectionMode.SELECTING_WORK
                    )
                }
            )
        }

        // Persistent nearby transit bottom sheet when no stop/station is selected and no active route
        if (isNearbySheetActive) {
            NearbyStopsBottomSheet(
                nearbyItems = nearbyTransitItems,
                nearbyValenbisiStations = nearbyValenbisiStations,
                cameraCenterLat = debouncedCameraTarget.latitude,
                cameraCenterLon = debouncedCameraTarget.longitude,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                busStopAliases = busStopAliases,
                selectedTab = selectedNearbyTab,
                onTabSelected = { mapViewModel.setSelectedNearbyTab(it) },
                onSelectItem = { item ->
                    mapViewModel.selectItem(item)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                activeTripBottomPadding = activeTripBottomPadding,
                sheetState = nearbySheetState,
                onSheetStateChanged = { nearbySheetState = it },
                onHeightChanged = { currentNearbySheetHeight = it },
                onHeightPxChanged = { nearbySheetHeightPx = it },
                maxExpandedHeight = maxExpandedSheetHeight,
                valenbisiEnabled = mapFilter.showValenbisi
            )
        }

        // Phase 2 Map Selection Overlays
        if (selectionMode != MapSelectionMode.NORMAL) {
            MapLocationSelectionOverlay(
                selectionMode = selectionMode,
                isMapMoving = isMapMoving,
                isSearching = isSearching,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                cameraTarget = cameraTarget,
                onCancelSelection = {
                    val wasPlannerPicking = (selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN || 
                                            selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION)
                    val wasCommutePicking = (selectionMode == MapSelectionMode.SELECTING_HOME ||
                                            selectionMode == MapSelectionMode.SELECTING_WORK)
                    mapViewModel.setSelectionMode(MapSelectionMode.NORMAL)
                    if (wasPlannerPicking) {
                        onCancelPlannerLocationPicking?.invoke()
                    } else if (wasCommutePicking) {
                        onCommuteLocationConfigured?.invoke()
                    }
                },
                onConfirmSelection = { target, isOrigin ->
                    val wasCommutePicking = (selectionMode == MapSelectionMode.SELECTING_HOME ||
                                            selectionMode == MapSelectionMode.SELECTING_WORK)
                    mapViewModel.confirmSelectedLocationOnMap(
                        mode = selectionMode,
                        lat = target.latitude,
                        lon = target.longitude,
                        onLocationSelected = { loc ->
                            onPlannerLocationPicked?.invoke(loc, isOrigin)
                        }
                    )
                    if (wasCommutePicking) {
                        onCommuteLocationConfigured?.invoke()
                    }
                }
            )
        }

        // Bottom Sheets for details
        if (selectionMode == MapSelectionMode.NORMAL && selectedItinerary == null) {
            MapDetailBottomSheetsHost(
                selectedItem = selectedItem,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                busStopAliases = busStopAliases,
                favoriteBusStops = favoriteBusStops,
                favoriteMetroStations = favoriteMetroStations,
                favoriteCercaniasStations = favoriteCercaniasStations,
                favoriteValenbisiSet = favoriteValenbisiSet,
                valenbisiAliasesMap = valenbisiAliasesMap,
                customFavorites = customFavorites,
                mapViewModel = mapViewModel,
                metroViewModel = metroViewModel,
                cercaniasViewModel = cercaniasViewModel,
                onNavigateToMetro = onNavigateToMetro,
                onNavigateToCercanias = onNavigateToCercanias,
                onNavigateToRoutePlanner = onNavigateToRoutePlanner,
                onEditBusStopAlias = { stop -> editingStopForAlias = stop },
                onSaveFavoriteAddress = { res ->
                    locationToSave = res
                    showSaveFavoriteDialog = true
                },
                onDismissItem = { mapViewModel.selectItem(null) },
                onBusStopDetailHeightPxChanged = { busStopDetailSheetHeightPx = it },
                onMetroStationDetailHeightPxChanged = { metroStationDetailSheetHeightPx = it },
                onCercaniasStationDetailHeightPxChanged = { cercaniasStationDetailSheetHeightPx = it },
                onValenbisiStationDetailHeightPxChanged = { valenbisiStationDetailSheetHeightPx = it },
                onMetrobusStopDetailHeightPxChanged = { metrobusStopDetailSheetHeightPx = it },
                onAddressDetailHeightPxChanged = { addressDetailSheetHeightPx = it },
                maxExpandedSheetHeight = maxExpandedSheetHeight,
                detailSheetState = detailSheetState,
                onDetailSheetStateChanged = { detailSheetState = it },
                activeTripBottomPadding = activeTripBottomPadding
            )
        }

        // Disambiguation Menu
        val itemsList = if (selectedItinerary == null) disambiguationItems else null
        if (itemsList != null) {
            val sortedItemsList = remember(itemsList, favoriteBusStops, favoriteMetroStations, favoriteCercaniasStations) {
                itemsList.sortedByDescending { item: SelectedMapItem ->
                    when (item) {
                        is SelectedMapItem.BusStop -> favoriteBusStops.contains(item.stop.id_parada)
                        is SelectedMapItem.MetrobusStopItem -> favoriteBusStops.contains(item.stop.id_parada)
                        is SelectedMapItem.Metro -> favoriteMetroStations.contains(item.station.id)
                        is SelectedMapItem.Cercanias -> favoriteCercaniasStations.contains(item.station.stop_id)
                        is SelectedMapItem.Valenbisi -> false
                        is SelectedMapItem.Address -> false
                        is SelectedMapItem.LiveTrain -> false
                    }
                }
            }

            DisambiguationDialog(
                items = sortedItemsList,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                busStopAliases = busStopAliases,
                onSelectItem = { item: SelectedMapItem ->
                    mapViewModel.selectItem(item)
                },
                onDismiss = { disambiguationItems = null }
            )
        }

        // Custom Bus Stop Alias Dialog
        val stopToEdit = editingStopForAlias
        if (stopToEdit != null) {
            EditBusStopAliasDialog(
                stopToEdit = stopToEdit,
                currentAlias = busStopAliases[stopToEdit.id_parada] ?: "",
                appLanguage = appLanguage,
                onSaveAlias = { aliasInput ->
                    mapViewModel.setBusStopAlias(stopToEdit.id_parada, aliasInput)
                },
                onDismiss = { editingStopForAlias = null }
            )
        }

        // Custom Save Favorite Dialog
        val favToSave = locationToSave
        if (showSaveFavoriteDialog && favToSave != null) {
            val existingFav = customFavorites.find {
                Math.abs(it.latitude - favToSave.latitude) < 0.0001 &&
                Math.abs(it.longitude - favToSave.longitude) < 0.0001
            }
            val initialAlias = existingFav?.title ?: favToSave.placeName ?: favToSave.displayName.split(",").firstOrNull()?.trim() ?: ""
            val initialShowOnMap = existingFav?.showOnMap ?: true
            val initialColorHex = existingFav?.colorHex ?: "#F59E0B"
            SaveFavoriteDialog(
                initialAlias = initialAlias,
                initialShowOnMap = initialShowOnMap,
                initialColorHex = initialColorHex,
                appLanguage = appLanguage,
                onSave = { aliasInput, showOnMap, colorHex ->
                    mapViewModel.saveCustomFavorite(
                        alias = aliasInput,
                        subtitle = favToSave.displayName,
                        latitude = favToSave.latitude,
                        longitude = favToSave.longitude,
                        showOnMap = showOnMap,
                        colorHex = colorHex,
                        nominatimResult = favToSave
                    )
                    showSaveFavoriteDialog = false
                    locationToSave = null
                },
                onDelete = if (existingFav != null) {
                    {
                        mapViewModel.deleteCustomFavorite(favToSave.latitude, favToSave.longitude)
                        showSaveFavoriteDialog = false
                        locationToSave = null
                    }
                } else null,
                onDismiss = {
                    showSaveFavoriteDialog = false
                    locationToSave = null
                }
            )
        }

        // Active Route Detail Bottom Sheet
        if (selectedItinerary != null && showRouteDetailSheet) {
            RouteDetailBottomSheet(
                itinerary = selectedItinerary,
                userLocation = userLocation?.let { gp ->
                    android.location.Location("gps").apply {
                        latitude = gp.latitude
                        longitude = gp.longitude
                    }
                },
                onDismiss = { showRouteDetailSheet = false },
                onViewOnMap = { showRouteDetailSheet = false },
                onStartTrip = if (onStartTrip != null) {
                    {
                        showRouteDetailSheet = false
                        onStartTrip(selectedItinerary)
                    }
                } else null,
                appLanguage = appLanguage
            )
        }
    }
}
