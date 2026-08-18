package com.example.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import com.example.data.database.GeoportalStopEntity
import com.example.ui.bus.BusTimesBottomSheet
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.bus.EditValenbisiAliasDialog
import com.example.ui.map.components.AddressDestinationBottomSheet
import com.example.ui.map.components.CercaniasStationBottomSheet
import com.example.ui.map.components.DisambiguationMenuSheet
import com.example.ui.map.components.EditBusStopAliasDialog
import com.example.ui.map.components.SaveFavoriteDialog
import com.example.ui.map.components.MapControlsOverlay
import com.example.ui.map.components.MetroStationBottomSheet
import com.example.ui.map.components.OsmdroidMapView
import com.example.ui.map.components.NearbyStopsBottomSheet
import com.example.ui.map.components.SheetState
import com.example.ui.map.components.ValenbisiStationBottomSheet
import com.example.ui.metro.MetroViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

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
    onNavigateToRoutePlanner: ((com.example.ui.routing.PlannerLocation) -> Unit)? = null,
    onPlannerLocationPicked: ((com.example.ui.routing.PlannerLocation, Boolean) -> Unit)? = null,
    onCancelPlannerLocationPicking: (() -> Unit)? = null,
    selectedItinerary: com.example.data.model.routing.PlannedItinerary? = null,
    onClearItinerary: (() -> Unit)? = null,
    onOpenRouteDetail: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val mapFilter by mapViewModel.mapFilter.collectAsState()
    val visibleBusStops by mapViewModel.visibleBusStops.collectAsState()
    val visibleMetrobusStops by mapViewModel.visibleMetrobusStops.collectAsState()
    val visibleMetroStations by mapViewModel.visibleMetroStations.collectAsState()
    val visibleCercaniasStations by mapViewModel.visibleCercaniasStations.collectAsState()
    val selectedItem by mapViewModel.selectedMapItem.collectAsState()
    val userLocation by mapViewModel.userLocation.collectAsState()
    val isFollowingUser by mapViewModel.isFollowingUser.collectAsState()
    val cameraTarget by mapViewModel.cameraTarget.collectAsState()
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

    // Phase 2 State collections
    val recentSearches by mapViewModel.recentSearches.collectAsState()
    val homeLocation by mapViewModel.homeLocation.collectAsState()
    val workLocation by mapViewModel.workLocation.collectAsState()
    val customFavorites by mapViewModel.customFavorites.collectAsState()
    val unifiedTransitFavorites by mapViewModel.unifiedTransitFavorites.collectAsState()
    val selectionMode by mapViewModel.selectionMode.collectAsState()

    var isSearchFocused by remember { mutableStateOf(false) }
    var isMapMoving by remember { mutableStateOf(false) }

    val pinOffset by animateDpAsState(
        targetValue = if (isMapMoving) (-42).dp else (-24).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pinOffset"
    )

    val shadowScale by animateFloatAsState(
        targetValue = if (isMapMoving) 0.5f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "shadowScale"
    )

    val shadowAlpha by animateFloatAsState(
        targetValue = if (isMapMoving) 0.2f else 0.45f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "shadowAlpha"
    )

    val pinRotation by animateFloatAsState(
        targetValue = if (isMapMoving) -6f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pinRotation"
    )

    var editingStopForAlias by remember { mutableStateOf<GeoportalStopEntity?>(null) }
    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var locationToSave by remember { mutableStateOf<com.example.data.model.NominatimResult?>(null) }
    var showRouteDetailSheet by remember { mutableStateOf(false) }
    var zoomInTrigger by remember { mutableStateOf(0) }
    var zoomOutTrigger by remember { mutableStateOf(0) }
    var disambiguationItems by remember { mutableStateOf<List<SelectedMapItem>?>(null) }
    var currentNearbySheetHeight by remember { mutableStateOf(240.dp) }
    var nearbySheetState by remember { mutableStateOf(SheetState.COLLAPSED) }

    DisposableEffect(context) {
        mapViewModel.startLocationTracking(context)
        onDispose {
            mapViewModel.stopLocationTracking()
        }
    }

    LaunchedEffect(Unit) {
        mapViewModel.reloadFavorites()
    }

    LaunchedEffect(cameraTarget, cameraZoom, selectionMode) {
        if (selectionMode != MapSelectionMode.NORMAL) {
            isMapMoving = true
            delay(200)
            isMapMoving = false
        }
    }

    val busTimes by mapViewModel.busTimes.collectAsState()
    val busTimesLoading by mapViewModel.busTimesLoading.collectAsState()

    val metroDepartures by mapViewModel.metroDepartures.collectAsState()
    val metroDeparturesLoading by mapViewModel.metroDeparturesLoading.collectAsState()

    val cercaniasDepartures by mapViewModel.cercaniasDepartures.collectAsState()
    val cercaniasDeparturesLoading by mapViewModel.cercaniasDeparturesLoading.collectAsState()
    val cercaniasAlerts by (cercaniasViewModel?.cercaniasAlerts ?: MutableStateFlow(emptyList())).collectAsState()

    val isMapBackHandlerEnabled = selectionMode != MapSelectionMode.NORMAL ||
        showRouteDetailSheet ||
        selectedItinerary != null ||
        selectedItem != null ||
        searchQuery.isNotEmpty() ||
        nearbySheetState == SheetState.EXPANDED ||
        !disambiguationItems.isNullOrEmpty()

    BackHandler(enabled = isMapBackHandlerEnabled) {
        when {
            selectionMode != MapSelectionMode.NORMAL -> {
                val wasPlannerPicking = (selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN || 
                                        selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION)
                mapViewModel.setSelectionMode(MapSelectionMode.NORMAL)
                if (wasPlannerPicking) {
                    onCancelPlannerLocationPicking?.invoke()
                }
            }
            showRouteDetailSheet -> {
                showRouteDetailSheet = false
            }
            selectedItinerary != null -> {
                onClearItinerary?.invoke()
            }
            searchQuery.isNotEmpty() -> {
                mapViewModel.setSearchQuery("")
            }
            selectedItem != null -> {
                mapViewModel.selectItem(null)
            }
            nearbySheetState == SheetState.EXPANDED -> {
                nearbySheetState = SheetState.COLLAPSED
            }
            !disambiguationItems.isNullOrEmpty() -> {
                disambiguationItems = null
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxExpandedSheetHeight = (maxHeight - 85.dp).coerceAtLeast(300.dp)

        // Base Osmdroid Map
        OsmdroidMapView(
            modifier = Modifier.fillMaxSize(),
            isDarkMode = isDarkMode,
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
            valenbisiStations = valenbisiStations,
            customFavorites = customFavorites,
            homeLocation = homeLocation,
            workLocation = workLocation,
            mapFilter = mapFilter,
            busStopAliases = busStopAliases,
            appLanguage = appLanguage,
            selectedItinerary = selectedItinerary,
            onSelectItem = { item ->
                mapViewModel.selectItem(item)
            },
            onMapClick = {
                mapViewModel.clearDestination()
                mapViewModel.selectItem(null)
                if (nearbySheetState == SheetState.EXPANDED) {
                    nearbySheetState = SheetState.COLLAPSED
                }
            },
            onMapTouch = {
                mapViewModel.disableFollowUser()
                if (nearbySheetState == SheetState.EXPANDED) {
                    nearbySheetState = SheetState.COLLAPSED
                }
                if (selectionMode != MapSelectionMode.NORMAL) {
                    isMapMoving = true
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
                disambiguationItems = items
            },
            onMapLongClick = {
                mapViewModel.onMapLongClick(it)
            }
        )

        val isAtUserLocation = userLocation != null && cameraTarget.distanceToAsDouble(userLocation) < 15.0
        // Central focal crosshair indicator when no individual item is selected and not centered on user location
        if (selectedItem == null && !isAtUserLocation) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
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
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 14.dp, end = 14.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (isDarkMode) Color(0xFF1E293B) else Color.White,
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedItinerary.formattedDuration} • " + (if (appLanguage == AppLanguage.CA) "Arribada " else "Llegada ") + selectedItinerary.formattedArrivalTime,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = "${selectedItinerary.transfersCount} " + (if (appLanguage == AppLanguage.CA) "transbords" else "transbordos"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                showRouteDetailSheet = true
                                onOpenRouteDetail?.invoke()
                            }
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Detalls" else "Detalles",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }
                        if (onClearItinerary != null) {
                            IconButton(onClick = onClearItinerary) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar ruta",
                                    tint = if (isDarkMode) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            }
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
                    mapViewModel.selectItemFromSearch(result)
                },
                isSearching = isSearching,
                appLanguage = appLanguage,
                hasPersistentBottomPanel = (selectedItem == null && searchQuery.isEmpty() && selectedItinerary == null),
                currentNearbySheetHeight = currentNearbySheetHeight,
                isFollowingUser = isFollowingUser,
                selectedItem = selectedItem,
                onDirectionsClick = { item ->
                    mapViewModel.clearDestination()
                    if (item != null) {
                        val (lat, lon, title) = when (item) {
                            is SelectedMapItem.BusStop -> Triple(item.stop.lat, item.stop.lon, "Parada ${item.stop.id_parada} - ${item.stop.denominacion}")
                            is SelectedMapItem.MetrobusStopItem -> Triple(item.stop.lat, item.stop.lon, "Parada ${item.stop.id_parada} - ${item.stop.denominacion}")
                            is SelectedMapItem.Metro -> Triple(item.station.latitude ?: 0.0, item.station.longitude ?: 0.0, "Metro ${item.station.name}")
                            is SelectedMapItem.Cercanias -> Triple(item.station.lat, item.station.lon, "Estación ${item.station.displayName}")
                            is SelectedMapItem.Valenbisi -> Triple(item.station.latitude, item.station.longitude, "Valenbisi ${item.station.name}")
                            is SelectedMapItem.Address -> Triple(item.result.latitude, item.result.longitude, item.result.displayName.split(",").firstOrNull()?.trim() ?: item.result.displayName)
                        }
                        mapViewModel.selectItem(null)
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(title = title, latitude = lat, longitude = lon)
                        )
                    } else if (destinationLocation != null) {
                        val lat = destinationLocation!!.latitude
                        val lon = destinationLocation!!.longitude
                        val title = destinationTitle ?: "Ubicación seleccionada"
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(
                                title = title,
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    } else {
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(
                                title = "Ubicación actual",
                                latitude = userLocation?.latitude ?: 0.0,
                                longitude = userLocation?.longitude ?: 0.0,
                                isUserGps = true
                            )
                        )
                    }
                },
                isItineraryActive = (selectedItinerary != null),
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
        if (selectedItem == null && searchQuery.isEmpty() && !isSearchFocused && selectedItinerary == null && selectionMode == MapSelectionMode.NORMAL) {
            NearbyStopsBottomSheet(
                nearbyItems = nearbyTransitItems,
                nearbyValenbisiStations = nearbyValenbisiStations,
                cameraCenterLat = cameraTarget.latitude,
                cameraCenterLon = cameraTarget.longitude,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                busStopAliases = busStopAliases,
                selectedTab = selectedNearbyTab,
                onTabSelected = { mapViewModel.setSelectedNearbyTab(it) },
                onSelectItem = { item ->
                    mapViewModel.selectItem(item)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                sheetState = nearbySheetState,
                onSheetStateChanged = { nearbySheetState = it },
                onHeightChanged = { currentNearbySheetHeight = it },
                maxExpandedHeight = maxExpandedSheetHeight
            )
        }

        // Phase 2 Map Selection Overlays
        if (selectionMode != MapSelectionMode.NORMAL) {
            // Shadow dot exactly at the center with dynamic scale & alpha
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 4.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = shadowScale
                        scaleY = shadowScale
                        alpha = shadowAlpha
                    }
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            )
            // Bouncing/floating Pin above center with realistic sway (balanceo)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = pinOffset)
                    .graphicsLayer {
                        rotationZ = pinRotation
                        transformOrigin = TransformOrigin(0.5f, 1f) // bottom center pivot
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Selection Center Pin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Top Floating Instructions Card
            val titleText = when (selectionMode) {
                MapSelectionMode.SELECTING_LOCATION -> if (appLanguage == AppLanguage.CA) "Triar ubicació al mapa" else "Elegir ubicación en el mapa"
                MapSelectionMode.SELECTING_HOME -> if (appLanguage == AppLanguage.CA) "Establir ubicació de Casa" else "Establecer ubicación de Casa"
                MapSelectionMode.SELECTING_WORK -> if (appLanguage == AppLanguage.CA) "Establir ubicació de Feina" else "Establecer ubicación de Trabajo"
                MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN -> if (appLanguage == AppLanguage.CA) "Triar origen al mapa" else "Elegir origen en el mapa"
                MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION -> if (appLanguage == AppLanguage.CA) "Triar destí al mapa" else "Elegir destino en el mapa"
                else -> ""
            }
            val subtitleText = if (appLanguage == AppLanguage.CA) "Mou el mapa per a situar el marcador al centre" else "Arrastra el mapa para situar el marcador en el centro"
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDarkMode) Color(0xFF1E293B) else Color.White,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .statusBarsPadding()
                    .testTag("selection_mode_instruction_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            val wasPlannerPicking = (selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN || 
                                                    selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION)
                            mapViewModel.setSelectionMode(MapSelectionMode.NORMAL)
                            if (wasPlannerPicking) {
                                onCancelPlannerLocationPicking?.invoke()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar selección",
                            tint = if (isDarkMode) Color.White else Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Confirm Selection FAB
            ExtendedFloatingActionButton(
                onClick = {
                    val currentCenter = cameraTarget
                    val isOrigin = (selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN)
                    mapViewModel.confirmSelectedLocationOnMap(
                        mode = selectionMode,
                        lat = currentCenter.latitude,
                        lon = currentCenter.longitude,
                        onLocationSelected = { loc ->
                            onPlannerLocationPicked?.invoke(loc, isOrigin)
                        }
                    )
                },
                icon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                text = {
                    Text(
                        text = if (isSearching) {
                            if (appLanguage == AppLanguage.CA) "Processant..." else "Procesando..."
                        } else {
                            if (appLanguage == AppLanguage.CA) "Confirmar ubicació" else "Confirmar ubicación"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .testTag("confirm_selection_fab")
            )
        }

        // Bottom Sheets for details
        if (selectionMode == MapSelectionMode.NORMAL) {
            when (val item = selectedItem) {
            is SelectedMapItem.BusStop -> {
                BusTimesBottomSheet(
                    stop = item.emtStopModel,
                    busTimes = busTimes,
                    busTimesLoading = busTimesLoading,
                    isDarkMode = isDarkMode,
                    texts = AppTexts.get(appLanguage),
                    alias = busStopAliases[item.stop.id_parada],
                    onEditAliasClick = {
                        editingStopForAlias = item.stop
                    },
                    isFavorite = favoriteBusStops.contains(item.stop.id_parada),
                    onToggleFavorite = {
                        mapViewModel.toggleFavoriteBusStop(item.stop.id_parada)
                    },
                    onDirectionsClick = {
                        mapViewModel.selectItem(null)
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(
                                title = "Parada ${item.stop.id_parada} - ${item.stop.denominacion}",
                                latitude = item.stop.lat,
                                longitude = item.stop.lon
                            )
                        )
                    },
                    onDismissRequest = {
                        mapViewModel.selectItem(null)
                    }
                )
            }
            is SelectedMapItem.Metro -> {
                LaunchedEffect(item.station.id) {
                    metroViewModel?.selectRealTimeStation(item.station.id)
                }

                val sharedDepartures by (metroViewModel?.realTimeDepartures ?: mapViewModel.metroDepartures).collectAsState()
                val sharedLoading by (metroViewModel?.realTimeLoading ?: mapViewModel.metroDeparturesLoading).collectAsState()

                MetroStationBottomSheet(
                    station = item.station,
                    departures = sharedDepartures,
                    isLoading = sharedLoading,
                    isDarkMode = isDarkMode,
                    appLanguage = appLanguage,
                    texts = AppTexts.get(appLanguage),
                    isFavorite = favoriteMetroStations.contains(item.station.id),
                    onToggleFavorite = { mapViewModel.toggleFavoriteMetroStation(item.station.id) },
                    onNavigateToMetro = { stationId ->
                        metroViewModel?.selectRealTimeStation(stationId)
                        mapViewModel.selectItem(null)
                        onNavigateToMetro?.invoke(stationId)
                    },
                    metroViewModel = metroViewModel,
                    onDirectionsClick = {
                        val lat = item.station.latitude ?: 0.0
                        val lon = item.station.longitude ?: 0.0
                        mapViewModel.selectItem(null)
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(
                                title = "Metro ${item.station.name}",
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    },
                    onDismiss = {
                        mapViewModel.selectItem(null)
                    }
                )
            }
            is SelectedMapItem.Cercanias -> {
                CercaniasStationBottomSheet(
                    station = item.station,
                    departures = cercaniasDepartures,
                    isLoading = cercaniasDeparturesLoading,
                    alerts = cercaniasAlerts,
                    isDarkMode = isDarkMode,
                    appLanguage = appLanguage,
                    isFavorite = favoriteCercaniasStations.contains(item.station.stop_id),
                    onToggleFavorite = { mapViewModel.toggleFavoriteCercaniasStation(item.station.stop_id) },
                    onNavigateToCercanias = { stationId ->
                        cercaniasViewModel?.selectCercaniasStation(stationId)
                        mapViewModel.selectItem(null)
                        onNavigateToCercanias?.invoke(stationId)
                    },
                    onDirectionsClick = {
                        mapViewModel.selectItem(null)
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(
                                title = "Estación ${item.station.displayName}",
                                latitude = item.station.lat,
                                longitude = item.station.lon
                            )
                        )
                    },
                    onDismiss = {
                        mapViewModel.selectItem(null)
                    }
                )
            }
            is SelectedMapItem.Valenbisi -> {
                var showEditValenbisiAliasDialog by remember { mutableStateOf(false) }
                val stationNum = item.station.number.toString()
                val isFav = favoriteValenbisiSet.contains(stationNum)
                val alias = valenbisiAliasesMap[stationNum]

                if (showEditValenbisiAliasDialog) {
                    EditValenbisiAliasDialog(
                        stationNumber = stationNum,
                        stationDefaultName = item.station.name,
                        currentAlias = alias ?: "",
                        appLanguage = appLanguage,
                        onSaveAlias = { newAlias ->
                            mapViewModel.saveValenbisiAlias(stationNum, newAlias)
                        },
                        onDismiss = { showEditValenbisiAliasDialog = false }
                    )
                }

                ValenbisiStationBottomSheet(
                    station = item.station,
                    isDarkMode = isDarkMode,
                    appLanguage = appLanguage,
                    isFavorite = isFav,
                    alias = alias,
                    onToggleFavorite = {
                        mapViewModel.toggleFavoriteValenbisiStation(stationNum)
                    },
                    onEditAlias = {
                        showEditValenbisiAliasDialog = true
                    },
                    onDirectionsClick = {
                        mapViewModel.selectItem(null)
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(
                                title = "Valenbisi ${item.station.name}",
                                latitude = item.station.latitude,
                                longitude = item.station.longitude
                            )
                        )
                    },
                    onDismiss = {
                        mapViewModel.selectItem(null)
                    }
                )
            }
            is SelectedMapItem.MetrobusStopItem -> {
                com.example.ui.bus.MetrobusTimesBottomSheet(
                    stop = item.metrobusModel,
                    times = emptyList(),
                    isLoading = false,
                    isDarkMode = isDarkMode,
                    onDismissRequest = { mapViewModel.selectItem(null) },
                    alias = busStopAliases[item.stop.id_parada],
                    onEditAliasClick = {
                        editingStopForAlias = GeoportalStopEntity(
                            id_parada = item.stop.id_parada,
                            denominacion = item.stop.denominacion,
                            suprimida = item.stop.suprimida,
                            lat = item.stop.lat,
                            lon = item.stop.lon,
                            lineas = item.stop.lineas
                        )
                    },
                    isFavorite = favoriteBusStops.contains(item.stop.id_parada),
                    onToggleFavorite = { mapViewModel.toggleFavoriteBusStop(item.stop.id_parada) },
                    onRefresh = {}
                )
            }
            is SelectedMapItem.Address -> {
                val matchingFav = customFavorites.find {
                    Math.abs(it.latitude - item.result.latitude) < 0.0001 &&
                    Math.abs(it.longitude - item.result.longitude) < 0.0001
                }
                val isFav = matchingFav != null
                val displayAddress = if (matchingFav != null) {
                    item.result.copy(
                        displayName = if (matchingFav.subtitle.isNotEmpty()) "${matchingFav.title}, ${matchingFav.subtitle}" else matchingFav.title,
                        category = "favorite",
                        type = "favorite"
                    )
                } else item.result

                AddressDestinationBottomSheet(
                    address = displayAddress,
                    isDarkMode = isDarkMode,
                    appLanguage = appLanguage,
                    isFavorite = isFav,
                    onSaveFavorite = {
                        locationToSave = item.result
                        showSaveFavoriteDialog = true
                    },
                    onNavigate = { lat, lon, title ->
                        mapViewModel.clearDestination()
                        mapViewModel.selectItem(null)
                        onNavigateToRoutePlanner?.invoke(
                            com.example.ui.routing.PlannerLocation(
                                title = title,
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    },
                    onDismiss = {
                        mapViewModel.clearDestination()
                        mapViewModel.selectItem(null)
                    }
                )
            }
            null -> {}
        }
    }

        // Disambiguation Menu
        val itemsList = disambiguationItems
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
                    }
                }
            }

            DisambiguationMenuSheet(
                items = sortedItemsList,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                busStopAliases = busStopAliases,
                onSelectItem = { item ->
                    mapViewModel.selectItem(item)
                },
                onDismiss = { disambiguationItems = null }
            )
        }

        // Custom Alias Dialog
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
            val existingFav = customFavorites.find { it.latitude == favToSave.latitude && it.longitude == favToSave.longitude }
            val initialAlias = existingFav?.title ?: favToSave.displayName.split(",").firstOrNull()?.trim() ?: ""
            SaveFavoriteDialog(
                initialAlias = initialAlias,
                appLanguage = appLanguage,
                onSave = { aliasInput ->
                    mapViewModel.saveCustomFavorite(
                        alias = aliasInput,
                        subtitle = favToSave.displayName,
                        latitude = favToSave.latitude,
                        longitude = favToSave.longitude
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
            com.example.ui.routing.components.RouteDetailBottomSheet(
                itinerary = selectedItinerary,
                onDismiss = { showRouteDetailSheet = false },
                onViewOnMap = { showRouteDetailSheet = false },
                appLanguage = appLanguage
            )
        }
    }
}
