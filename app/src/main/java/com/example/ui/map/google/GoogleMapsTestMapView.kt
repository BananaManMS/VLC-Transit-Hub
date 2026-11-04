package com.example.ui.map.google

import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.cercanias.CercaniasRouteUtils
import com.example.ui.cercanias.LiveVehicleInfo
import com.example.ui.map.MapFilter
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.CercaniasMapOverlayLoader
import com.example.ui.map.components.EmtMapOverlayLoader
import com.example.ui.map.components.MetroMapOverlayLoader
import com.example.ui.map.components.MetroStationFootprintsOverlayManager
import com.example.ui.map.components.MetroStationHighlightManager
import com.example.ui.map.components.ValenbisiStation
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

/**
 * High-Performance Google Maps View configured for full transit parity with OSMdroid:
 * 1. Offline local MapStyleOptions JSON (No Cloud Map ID / completely free tier).
 * 2. Precomputed Metro & Cercanías Polylines with dynamic route highlighting.
 * 3. Exact zoom LOD hierarchy for Bus/Metrobús stops (Clusters < 13.5, Dots 13.5..15.2, Badges >= 15.2).
 * 4. Metro Station Footprints and entrance access markers (escaleras/ascensores).
 * 5. Coordinated native Map Padding and smooth target centering.
 * 6. Isolated sub-composable layers to eliminate unnecessary recompositions during map interactions.
 */
@Composable
fun GoogleMapsTestMapView(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    isSatelliteMode: Boolean = false,
    mapFilter: MapFilter = MapFilter.DEFAULT,
    showMetroLines: Boolean = true,
    showCercaniasLines: Boolean = true,
    showMetroFootprints: Boolean = true,
    showMetroStations: Boolean = true,
    showCercaniasStations: Boolean = true,
    showBusStops: Boolean = true,
    showMetrobusStops: Boolean = true,
    showValenbisi: Boolean = false,
    metroStations: List<MetroStation> = emptyList(),
    cercaniasStations: List<CercaniasStationEntity> = emptyList(),
    busStops: List<GeoportalStopEntity> = emptyList(),
    metrobusStops: List<MetrobusStopEntity> = emptyList(),
    valenbisiStations: List<ValenbisiStation> = emptyList(),
    liveCercaniasVehicles: List<LiveVehicleInfo> = emptyList(),
    selectedItinerary: PlannedItinerary? = null,
    homeLocation: RecentSearch? = null,
    workLocation: RecentSearch? = null,
    customFavorites: List<RecentSearch> = emptyList(),
    destinationLocation: GeoPoint? = null,
    destinationTitle: String? = null,
    userLocation: GeoPoint? = null,
    isCellTowerLocation: Boolean = false,
    userBearing: Float? = null,
    selectedItem: GoogleMapTransitItem? = null,
    selectedMapItem: SelectedMapItem? = null,
    selectedBusLineFilters: Set<String> = emptySet(),
    selectedDirectionFilter: String? = null,
    selectedMetrobusShapes: Map<String, List<GeoPoint>> = emptyMap(),
    bottomPaddingDp: Dp = 0.dp,
    showOpticalCenterGuide: Boolean = false,
    cameraTarget: GeoPoint? = null,
    cameraZoom: Double = 13.5,
    cameraAnimTrigger: Int = 0,
    zoomInTrigger: Int = 0,
    zoomOutTrigger: Int = 0,
    busStopAliases: Map<String, String> = emptyMap(),
    favoriteBusStops: Set<String> = emptySet(),
    favoriteMetroStations: Set<String> = emptySet(),
    favoriteCercaniasStations: Set<String> = emptySet(),
    favoriteValenbisiStations: Set<String> = emptySet(),
    onItemClick: ((GoogleMapTransitItem) -> Unit)? = null,
    onMapClick: (() -> Unit)? = null,
    onMapLongClick: ((GeoPoint) -> Unit)? = null,
    onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null,
    onMapTouch: (() -> Unit)? = null,
    onMapPan: (() -> Unit)? = null,
    onCameraPositionChanged: ((GeoPoint, Double) -> Unit)? = null,
    onZoomLevelChanged: ((Double) -> Unit)? = null,
    onMapReady: (() -> Unit)? = null,
    onMapError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    // Pre-initialize Maps SDK safely
    LaunchedEffect(Unit) {
        try {
            MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
                Log.d("GoogleMapsTestMapView", "Maps initialized with renderer: $renderer")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            try {
                MapsInitializer.initialize(context.applicationContext)
            } catch (fallbackError: Exception) {
                if (fallbackError is kotlinx.coroutines.CancellationException) throw fallbackError
                onMapError?.invoke("Error al inicializar Maps SDK: ${fallbackError.localizedMessage}")
            }
        }
    }

    // Unified active mode filters matching OSMdroid rules
    val isMetroActiveMode = showMetroStations && (mapFilter.isFavorites || mapFilter.showMetro)
    val isCercaniasActiveMode = showCercaniasStations && (mapFilter.isFavorites || mapFilter.showCercanias)
    val isBusActiveMode = showBusStops && (mapFilter.isFavorites || mapFilter.showBus)
    val isMetrobusActiveMode = showMetrobusStops && (mapFilter.isFavorites || mapFilter.showMetrobus)
    val isValenbisiActiveMode = showValenbisi && (mapFilter.isFavorites || mapFilter.showValenbisi)

    val effectiveFilter = remember(
        mapFilter,
        isMetroActiveMode,
        isCercaniasActiveMode,
        isBusActiveMode,
        isMetrobusActiveMode,
        isValenbisiActiveMode
    ) {
        MapFilter(
            isFavorites = mapFilter.isFavorites,
            showMetro = isMetroActiveMode,
            showCercanias = isCercaniasActiveMode,
            showBus = isBusActiveMode,
            showMetrobus = isMetrobusActiveMode,
            showValenbisi = isValenbisiActiveMode
        )
    }

    val isOnlyMetroSelected = isMetroActiveMode && !isBusActiveMode && !isCercaniasActiveMode && !isMetrobusActiveMode && !isValenbisiActiveMode && !mapFilter.isFavorites
    val isOnlyCercaniasSelected = isCercaniasActiveMode && !isBusActiveMode && !isMetroActiveMode && !isMetrobusActiveMode && !isValenbisiActiveMode && !mapFilter.isFavorites

    // Camera and optical center coordinator
    val cameraCoordinator = rememberGoogleMapCameraCoordinator(
        cameraTarget = cameraTarget,
        cameraZoom = cameraZoom,
        cameraAnimTrigger = cameraAnimTrigger,
        zoomInTrigger = zoomInTrigger,
        zoomOutTrigger = zoomOutTrigger,
        selectedItem = selectedItem,
        bottomPaddingDp = bottomPaddingDp,
        selectedItinerary = selectedItinerary,
        metroStations = metroStations,
        cercaniasStations = cercaniasStations,
        busStops = busStops,
        metrobusStops = metrobusStops,
        valenbisiStations = valenbisiStations,
        showValenbisi = isValenbisiActiveMode,
        mapFilter = effectiveFilter,
        onCameraPositionChanged = onCameraPositionChanged,
        onZoomLevelChanged = onZoomLevelChanged,
        onMapTouch = onMapTouch,
        onMapPan = onMapPan,
        onItemClick = onItemClick,
        onShowDisambiguationMenu = onShowDisambiguationMenu
    )
    val (cameraPositionState, settledZoom, handleTransitItemTap) = cameraCoordinator

    // Parallel pre-warming of all transit data overlays on Dispatchers.IO
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val metroJob = async { try { MetroMapOverlayLoader.ensureLoaded(context) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
            val cercaniasJob = async { try { CercaniasMapOverlayLoader.ensureLoaded(context) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
            val emtJob = async { try { EmtMapOverlayLoader.ensureLoaded(context) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
            val footprintsJob = async { try { MetroStationFootprintsOverlayManager.ensureLoaded(context) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
            metroJob.await()
            cercaniasJob.await()
            emtJob.await()
            footprintsJob.await()
        }
    }

    // Map style & properties
    val currentStyle = remember(isDarkMode, context) {
        if (isDarkMode) GoogleMapStyleCache.getDarkStyle(context) else GoogleMapStyleCache.getLightStyle(context)
    }

    val comValencianaBounds = remember {
        LatLngBounds(
            LatLng(37.80, -1.65),
            LatLng(40.85, 0.85)
        )
    }

    val mapProperties = remember(isSatelliteMode, currentStyle, comValencianaBounds) {
        MapProperties(
            mapType = if (isSatelliteMode) MapType.SATELLITE else MapType.NORMAL,
            mapStyleOptions = if (isSatelliteMode) null else currentStyle,
            isMyLocationEnabled = false,
            isBuildingEnabled = false,
            isIndoorEnabled = false,
            latLngBoundsForCameraTarget = comValencianaBounds
        )
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            indoorLevelPickerEnabled = false
        )
    }

    // Modular state managers
    val footprintsState = rememberGoogleMapFootprints(
        context = context,
        showMetroFootprints = showMetroFootprints
    )

    val routesState = rememberGoogleMapTransitRoutes(
        context = context,
        showMetroLines = showMetroLines,
        showCercaniasLines = showCercaniasLines,
        cameraPositionState = cameraPositionState,
        settledZoom = settledZoom,
        selectedItem = selectedItem,
        selectedMapItem = selectedMapItem,
        selectedBusLineFilters = selectedBusLineFilters,
        selectedDirectionFilter = selectedDirectionFilter,
        selectedMetrobusShapes = selectedMetrobusShapes
    )

    val viewportState = rememberGoogleMapViewportTransit(
        cameraPositionState = cameraPositionState,
        settledZoom = settledZoom,
        mapFilter = effectiveFilter,
        busStops = busStops,
        metrobusStops = metrobusStops,
        valenbisiStations = valenbisiStations
    )

    // Zoom LOD thresholds and active layer filters matching OSMdroid rules
    val currentZoom = settledZoom
    val isZoomForCercanias = settledZoom >= 9.0f || isOnlyCercaniasSelected
    val isZoomForMetro = settledZoom >= 11.5f || isOnlyMetroSelected

    val minLat = viewportState.currentBoundingBox.latSouth
    val maxLat = viewportState.currentBoundingBox.latNorth
    val minLon = viewportState.currentBoundingBox.lonWest
    val maxLon = viewportState.currentBoundingBox.lonEast

    // Active transit line highlights
    val selectedMetroStation = (selectedItem as? GoogleMapTransitItem.Metro)?.station
    val activeMetroLines = remember(selectedMetroStation) {
        selectedMetroStation?.lines?.map { MetroStationHighlightManager.normalizeLineRef(it) }?.toSet() ?: emptySet()
    }

    val selectedCercaniasStation = (selectedItem as? GoogleMapTransitItem.Cercanias)?.station
        ?: (selectedMapItem as? SelectedMapItem.Cercanias)?.station
    val activeCercaniasLines = remember(selectedCercaniasStation) {
        selectedCercaniasStation?.lineas?.map { it.trim().uppercase() }?.toSet() ?: emptySet()
    }

    // Filtered stations taking into account favorites filter
    val metroStationsToRender = remember(metroStations, favoriteMetroStations, mapFilter.isFavorites) {
        if (mapFilter.isFavorites) metroStations.filter { favoriteMetroStations.contains(it.id) }
        else metroStations
    }

    val cercaniasStationsToRender = remember(cercaniasStations, favoriteCercaniasStations, mapFilter.isFavorites) {
        if (mapFilter.isFavorites) cercaniasStations.filter { favoriteCercaniasStations.contains(it.stop_id) }
        else cercaniasStations
    }

    val valenbisiToRender = remember(valenbisiStations, favoriteValenbisiStations, mapFilter.isFavorites) {
        if (mapFilter.isFavorites) valenbisiStations.filter { favoriteValenbisiStations.contains(it.number.toString()) }
        else valenbisiStations
    }

    val isCercaniasOnlyFilter = mapFilter.showCercanias && !mapFilter.showBus && !mapFilter.showMetrobus && !mapFilter.showMetro && !mapFilter.showValenbisi && !mapFilter.isFavorites

    val trainsToRender = remember(liveCercaniasVehicles, isCercaniasOnlyFilter, selectedCercaniasStation) {
        if (isCercaniasOnlyFilter) {
            liveCercaniasVehicles
        } else if (selectedCercaniasStation != null) {
            liveCercaniasVehicles.filter { vehicle ->
                CercaniasRouteUtils.getStationScheduledTime(
                    tripId = vehicle.tripId,
                    stationId = selectedCercaniasStation.stop_id,
                    stationName = selectedCercaniasStation.nombre
                ) != null
            }
        } else {
            emptyList()
        }
    }

    val isMetroActive = activeMetroLines.isNotEmpty() || selectedMetroStation != null
    val isCercaniasActive = activeCercaniasLines.isNotEmpty() || selectedCercaniasStation != null
    val isBusActive = routesState.isBusActive

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            googleMapOptionsFactory = {
                val bg = if (isDarkMode) AndroidColor.parseColor("#0F172A") else AndroidColor.parseColor("#F1F5F9")
                GoogleMapOptions().backgroundColor(bg)
            },
            contentPadding = PaddingValues(bottom = bottomPaddingDp),
            onMapClick = {
                onMapClick?.invoke()
            },
            onMapLongClick = { latLng ->
                onMapLongClick?.invoke(GeoPoint(latLng.latitude, latLng.longitude))
            },
            onMapLoaded = {
                onMapReady?.invoke()
            }
        ) {
            if (selectedItinerary == null) {
                // 1. Metro station footprints & access points
                GoogleMapFootprintsAndAccessLayer(
                    showMetroFootprints = showMetroFootprints,
                    metroStationFootprints = footprintsState.metroStationFootprints,
                    metroAccessPoints = footprintsState.metroAccessPoints,
                    selectedMetroStation = selectedMetroStation,
                    currentZoom = currentZoom,
                    minLat = minLat,
                    maxLat = maxLat,
                    minLon = minLon,
                    maxLon = maxLon,
                    context = context,
                    onItemClick = onItemClick
                )

                // 2. Track & route polylines (Metro, Cercanias, Bus, Chevrons)
                GoogleMapPolylinesLayer(
                    showCercaniasLines = showCercaniasLines && isCercaniasActiveMode,
                    showMetroLines = showMetroLines && isMetroActiveMode,
                    cercaniasPolylineList = routesState.cercaniasPolylineList,
                    metroPolylineList = routesState.metroPolylineList,
                    busPolylineList = routesState.busPolylineList,
                    metrobusPolylineList = routesState.metrobusPolylineList,
                    routeChevronArrows = routesState.routeChevronArrows,
                    activeCercaniasLines = activeCercaniasLines,
                    activeMetroLines = activeMetroLines,
                    isCercaniasActive = isCercaniasActive,
                    isMetroActive = isMetroActive,
                    isBusActive = isBusActive,
                    context = context
                )

                // 3. Metrovalencia Stations
                GoogleMapMetroLayer(
                    showMetroStations = isMetroActiveMode,
                    isZoomForMetro = isZoomForMetro,
                    mapFilter = mapFilter,
                    metroStationsToRender = metroStationsToRender,
                    favoriteMetroStations = favoriteMetroStations,
                    selectedItem = selectedItem,
                    selectedMapItem = selectedMapItem,
                    isBusActive = isBusActive,
                    isCercaniasActive = isCercaniasActive,
                    activeMetroLines = activeMetroLines,
                    isDarkMode = isDarkMode,
                    settledZoom = settledZoom,
                    context = context,
                    handleTransitItemTap = handleTransitItemTap
                )

                // 4. Cercanías Renfe Stations and Live Trains
                GoogleMapCercaniasLayer(
                    showCercaniasStations = isCercaniasActiveMode,
                    isZoomForCercanias = isZoomForCercanias,
                    mapFilter = mapFilter,
                    cercaniasStationsToRender = cercaniasStationsToRender,
                    favoriteCercaniasStations = favoriteCercaniasStations,
                    selectedItem = selectedItem,
                    selectedMapItem = selectedMapItem,
                    isBusActive = isBusActive,
                    isMetroActive = isMetroActive,
                    activeCercaniasLines = activeCercaniasLines,
                    trainsToRender = trainsToRender,
                    isDarkMode = isDarkMode,
                    settledZoom = settledZoom,
                    context = context,
                    handleTransitItemTap = handleTransitItemTap
                )

                // 5. EMT & Metrobús Stops (Clusters, Dots, Badges)
                GoogleMapBusLayer(
                    isZoomForClusters = viewportState.isZoomForClusters,
                    isZoomForBusDots = viewportState.isZoomForBusDots,
                    isZoomForBusBadges = viewportState.isZoomForBusBadges,
                    isZoomForBusDetailed = viewportState.isZoomForBusDetailed,
                    showBusStops = isBusActiveMode && viewportState.showBusLayer,
                    showMetrobusStops = isMetrobusActiveMode && viewportState.showMetrobusLayer,
                    isMetroActive = isMetroActive,
                    isCercaniasActive = isCercaniasActive,
                    activeIsolatedBusLines = routesState.activeIsolatedBusLines,
                    busClusters = viewportState.busClusters,
                    metrobusClusters = viewportState.metrobusClusters,
                    currentMergedGroups = viewportState.currentMergedGroups,
                    sampledDotsBus = viewportState.sampledDotsBus,
                    sampledDotsMb = viewportState.sampledDotsMb,
                    standaloneBusDetailed = viewportState.standaloneBusDetailed,
                    standaloneMbDetailed = viewportState.standaloneMbDetailed,
                    favoriteBusStops = favoriteBusStops,
                    busStopAliases = busStopAliases,
                    selectedItem = selectedItem,
                    selectedMapItem = selectedMapItem,
                    isDarkMode = isDarkMode,
                    currentZoom = currentZoom,
                    cameraPositionState = cameraPositionState,
                    context = context,
                    handleTransitItemTap = handleTransitItemTap
                )

                // 6. Valenbisi Stations
                GoogleMapValenbisiLayer(
                    showValenbisi = isValenbisiActiveMode,
                    valenbisiToRender = valenbisiToRender,
                    minLat = minLat,
                    maxLat = maxLat,
                    minLon = minLon,
                    maxLon = maxLon,
                    settledZoom = settledZoom,
                    mapFilter = mapFilter,
                    isOnlyValenbisi = viewportState.isOnlyValenbisi,
                    selectedItem = selectedItem,
                    selectedMapItem = selectedMapItem,
                    isBusActive = isBusActive,
                    isMetroActive = isMetroActive,
                    isCercaniasActive = isCercaniasActive,
                    isDarkMode = isDarkMode,
                    cameraPositionState = cameraPositionState,
                    context = context,
                    handleTransitItemTap = handleTransitItemTap
                )

                // 7. Custom Places (Destination, Home, Work, Favorites)
                GoogleMapPlacesLayer(
                    destinationLocation = destinationLocation,
                    destinationTitle = destinationTitle,
                    homeLocation = homeLocation,
                    workLocation = workLocation,
                    customFavorites = customFavorites,
                    isBusActive = isBusActive,
                    isMetroActive = isMetroActive,
                    isCercaniasActive = isCercaniasActive,
                    isDarkMode = isDarkMode,
                    settledZoom = settledZoom,
                    context = context,
                    onItemClick = onItemClick
                )
            } else {
                // Multimodal Route / Itinerary Layer
                GoogleMapItineraryLayer(
                    itinerary = selectedItinerary,
                    settledZoom = settledZoom,
                    isDarkMode = isDarkMode,
                    context = context
                )
            }

            // User Location Marker with real-time compass & sliding animation
            userLocation?.let { uLoc ->
                key("USER_LOCATION_MARKER") {
                    GoogleMapUserLocationMarker(
                        userLocation = uLoc,
                        userBearing = userBearing,
                        isCellTowerLocation = isCellTowerLocation,
                        context = context,
                        cameraBearing = cameraPositionState.position.bearing
                    )
                }
            }
        }

        // Adaptive Emerald Green Optical Center Circle
        if (showOpticalCenterGuide) {
            GoogleMapOpticalCenterGuide(
                maxHeight = maxHeight,
                bottomPaddingDp = bottomPaddingDp
            )
        }

        // Status badge: Shows active layers count and zoom LOD rule
        GoogleMapStatusBadge(
            bottomPaddingDp = bottomPaddingDp,
            settledZoom = settledZoom,
            metroLinesCount = routesState.metroPolylineList.size,
            cercaniasLinesCount = routesState.cercaniasPolylineList.size,
            showBusStops = showBusStops,
            showMetrobusStops = showMetrobusStops,
            viewportState = viewportState
        )

        // Loading indicator overlay while polylines or footprints load
        GoogleMapLoadingOverlay(
            bottomPaddingDp = bottomPaddingDp,
            isLinesLoading = routesState.isLinesLoading,
            isFootprintsLoading = footprintsState.isFootprintsLoading
        )
    }
}
