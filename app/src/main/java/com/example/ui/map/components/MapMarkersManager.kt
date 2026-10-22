package com.example.ui.map.components

import android.content.Context
import android.graphics.drawable.Drawable
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.MapConfig
import com.example.ui.map.MapFilter
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

object MapMarkersManager {

    data class MarkerIconResult(
        val drawable: Drawable,
        val anchorU: Float,
        val anchorV: Float
    )

    // Recycled instances to prevent GC allocations during pan/zoom
    internal var lastMapView: MapView? = null
    private var mapEventsOverlay: MapEventsOverlay? = null
    internal var userMarker: Marker? = null
    internal var destinationMarker: Marker? = null
    private val recycledMetroMarkers = mutableListOf<Marker>()
    private val recycledCercaniasMarkers = mutableListOf<Marker>()
    private val recycledBusMarkers = mutableListOf<Marker>()
    private val recycledMetrobusMarkers = mutableListOf<Marker>()
    private val recycledMergedBusMarkers = mutableListOf<Marker>()
    private val recycledMetrobusClusterMarkers = mutableListOf<Marker>()
    private val recycledClusterMarkers = mutableListOf<Marker>()
    private val recycledValenbisiMarkers = mutableListOf<Marker>()
    private val recycledValenbisiClusterMarkers = mutableListOf<Marker>()
    private val recycledItineraryMarkers = mutableListOf<Marker>()
    private val recycledCustomFavoriteMarkers = mutableListOf<Marker>()

    private var currentOnMapClick: (() -> Unit)? = null
    private var currentOnMapLongClick: ((GeoPoint) -> Unit)? = null
    private var currentOnSelectItem: ((SelectedMapItem) -> Unit)? = null
    private var currentOnShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null
    private var currentMetroStations = emptyList<MetroStation>()
    private var currentCercaniasStations = emptyList<com.example.data.database.CercaniasStationEntity>()
    private var currentBusStopsInViewport = emptyList<GeoportalStopEntity>()
    private var currentMetrobusStopsInViewport = emptyList<com.example.data.database.MetrobusStopEntity>()
    private var currentValenbisiStations = emptyList<com.example.ui.map.components.ValenbisiStation>()
    private var currentMetroPositions = emptyMap<String, GeoPoint>()
    private var currentCercaniasPositions = emptyMap<String, GeoPoint>()
    private var showMetro: Boolean = false
    private var showCercanias: Boolean = false
    private var showMetrobus: Boolean = false
    private var showBus: Boolean = false
    private var showValenbisi: Boolean = false
    private var currentZoomLevel: Double = 16.0
    private var currentCustomFavorites = emptyList<RecentSearch>()
    private var currentHomeLocation: RecentSearch? = null
    private var currentWorkLocation: RecentSearch? = null
    private var isOnlyMetroSelected: Boolean = false
    private var isOnlyCercaniasSelected: Boolean = false
    private var currentDestinationLocation: GeoPoint? = null
    private var currentDestinationTitle: String? = null
    private var currentSelectedItinerary: PlannedItinerary? = null
    private var currentSelectedMapItem: SelectedMapItem? = null

    private var lastRawMetroStations: List<MetroStation>? = null
    private var cachedValidMetroStations: List<MetroStation> = emptyList()
    private var cachedMetroPositions: Map<String, GeoPoint> = emptyMap()

    private var lastRawCercaniasStations: List<com.example.data.database.CercaniasStationEntity>? = null
    private var cachedValidCercaniasStations: List<com.example.data.database.CercaniasStationEntity> = emptyList()
    private var cachedCercaniasPositions: Map<String, GeoPoint> = emptyMap()

    private var lastZoomedItineraryId: String? = null

    private fun clearRecycledMarkers() {
        recycledMetroMarkers.clear()
        recycledCercaniasMarkers.clear()
        recycledBusMarkers.clear()
        recycledMetrobusMarkers.clear()
        recycledMergedBusMarkers.clear()
        recycledMetrobusClusterMarkers.clear()
        recycledClusterMarkers.clear()
        recycledValenbisiMarkers.clear()
        recycledValenbisiClusterMarkers.clear()
        recycledItineraryMarkers.clear()
        recycledCustomFavoriteMarkers.clear()
    }

    fun notifyGesture() {
        MapEventsDelegate.notifyGesture()
    }

    fun isGestureActive(): Boolean {
        return MapEventsDelegate.isGestureActive()
    }

    private fun handleTapAtGeoPoint(context: Context, mapView: MapView, p: GeoPoint): Boolean {
        if (isGestureActive()) {
            return true
        }

        // When viewing route preview / itinerary, disable all tap selection on stops, stations, favorites, etc.
        if (currentSelectedItinerary != null) {
            currentOnMapClick?.invoke()
            return true
        }

        val tapContext = MapTapHandler.TapContext(
            showMetro = showMetro,
            showCercanias = showCercanias,
            showBus = showBus,
            showMetrobus = showMetrobus,
            showValenbisi = showValenbisi,
            currentZoomLevel = currentZoomLevel,
            isOnlyMetroSelected = isOnlyMetroSelected,
            isOnlyCercaniasSelected = isOnlyCercaniasSelected,
            currentMetroStations = currentMetroStations,
            currentCercaniasStations = currentCercaniasStations,
            currentBusStopsInViewport = currentBusStopsInViewport,
            currentMetrobusStopsInViewport = currentMetrobusStopsInViewport,
            currentValenbisiStations = currentValenbisiStations,
            currentMetroPositions = currentMetroPositions,
            currentCercaniasPositions = currentCercaniasPositions,
            currentDestinationLocation = currentDestinationLocation,
            currentDestinationTitle = currentDestinationTitle,
            currentCustomFavorites = currentCustomFavorites,
            currentHomeLocation = currentHomeLocation,
            currentWorkLocation = currentWorkLocation,
            currentOnSelectItem = currentOnSelectItem,
            currentOnMapClick = currentOnMapClick,
            currentOnShowDisambiguationMenu = currentOnShowDisambiguationMenu
        )
        return MapTapHandler.handleTapAtGeoPoint(context, mapView, p, tapContext)
    }

    fun updateMarkers(
        context: Context,
        mapView: MapView,
        cameraZoom: Double = MapConfig.DEFAULT_ZOOM,
        busStops: List<GeoportalStopEntity>,
        metrobusStops: List<com.example.data.database.MetrobusStopEntity> = emptyList(),
        metroStations: List<MetroStation>,
        cercaniasStations: List<com.example.data.database.CercaniasStationEntity>,
        valenbisiStations: List<com.example.ui.map.components.ValenbisiStation> = emptyList(),
        customFavorites: List<RecentSearch> = emptyList(),
        homeLocation: RecentSearch? = null,
        workLocation: RecentSearch? = null,
        mapFilter: MapFilter,
        userLocation: GeoPoint?,
        destinationLocation: GeoPoint? = null,
        destinationTitle: String? = null,
        isDarkMode: Boolean,
        busStopAliases: Map<String, String> = emptyMap(),
        appLanguage: AppLanguage = AppLanguage.CA,
        selectedItinerary: PlannedItinerary? = null,
        selectedMapItem: SelectedMapItem? = currentSelectedMapItem,
        selectedBusLineFilters: Set<String> = emptySet(),
        selectedMetrobusShapes: Map<String, List<GeoPoint>> = emptyMap(),
        selectedDirectionFilter: String? = null,
        onSelectItem: (SelectedMapItem) -> Unit,
        onMapClick: () -> Unit,
        onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null,
        onMapLongClick: ((GeoPoint) -> Unit)? = null,
        isCellTowerLocation: Boolean = false
    ) {
        if (!mapView.isAttachedToWindow && mapView.parent == null) {
            mapView.post {
                if (mapView.isAttachedToWindow || mapView.parent != null) {
                    updateMarkers(
                        context, mapView, cameraZoom, busStops, metrobusStops, metroStations, cercaniasStations,
                        valenbisiStations, customFavorites, homeLocation, workLocation, mapFilter,
                        userLocation, destinationLocation, destinationTitle, isDarkMode, busStopAliases,
                        appLanguage, selectedItinerary, selectedMapItem, selectedBusLineFilters, selectedMetrobusShapes,
                        selectedDirectionFilter, onSelectItem, onMapClick, onShowDisambiguationMenu, onMapLongClick,
                        isCellTowerLocation
                    )
                }
            }
            return
        }

        currentOnMapClick = onMapClick
        currentOnMapLongClick = onMapLongClick
        currentOnSelectItem = onSelectItem
        currentOnShowDisambiguationMenu = onShowDisambiguationMenu
        currentDestinationLocation = destinationLocation
        currentDestinationTitle = destinationTitle
        currentCustomFavorites = customFavorites
        currentHomeLocation = homeLocation
        currentWorkLocation = workLocation
        currentSelectedItinerary = selectedItinerary
        currentSelectedMapItem = selectedMapItem

        // Reset recycling cache if MapView instance changes
        if (lastMapView != mapView) {
            stopLiveLocationUpdates()
            lastMapView?.let {
                try {
                    MetroStationFootprintsOverlayManager.clearFromMap(it)
                } catch (_: Exception) {}
            }
            lastMapView = mapView
            mapEventsOverlay = null
            userMarker = null
            destinationMarker = null
            clearRecycledMarkers()
            try {
                mapView.overlays.clear()
            } catch (_: Exception) {}
        }

        // 1. Map Events Receiver (Click on map empty space)
        if (mapEventsOverlay == null) {
            val overlay = MapEventsDelegate.createMapEventsOverlay(
                context = context,
                mapView = mapView,
                onSingleTap = { p ->
                    if (currentSelectedItinerary != null) {
                        currentOnMapClick?.invoke()
                        true
                    } else if (p == null) {
                        currentOnMapClick?.invoke()
                        true
                    } else {
                        handleTapAtGeoPoint(context, mapView, p)
                    }
                },
                onLongPress = { _ ->
                    // Long press is handled with 1.0s delay in MapGestureTouchHandler
                    false
                }
            )
            mapEventsOverlay = overlay
            mapView.overlays.add(0, overlay)
        }

        val currentZoom = if (mapView.zoomLevelDouble >= MapConfig.MIN_ZOOM) mapView.zoomLevelDouble else cameraZoom

        lastMapView = mapView

        // 2. User Location Marker
        userMarker = UserAndDestinationMarkersRenderer.updateUserLocationMarker(
            context = context,
            mapView = mapView,
            userLocation = userLocation,
            existingUserMarker = userMarker,
            isCellTowerLocation = isCellTowerLocation
        )

        // 2b. Destination Location Marker (Only when not previewing an active route)
        destinationMarker = UserAndDestinationMarkersRenderer.updateDestinationMarker(
            context = context,
            mapView = mapView,
            destinationLocation = destinationLocation,
            destinationTitle = destinationTitle,
            selectedItinerary = selectedItinerary,
            isDarkMode = isDarkMode,
            existingDestinationMarker = destinationMarker,
            onSelectItem = { item ->
                if (currentSelectedItinerary == null) {
                    currentOnSelectItem?.invoke(item)
                }
            },
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )
        // 3. Metro Stations & Line Shapes (Draw if filter is FAVORITES, METRO_ONLY, or SHOW_ALL)
        val showMetro = mapFilter.isFavorites || mapFilter.showMetro
        val showCercanias = mapFilter.isFavorites || mapFilter.showCercanias

        // Dynamically toggle high-res / low-res (LOD) based on the zoom level threshold (12.0)
        MetroMapOverlayLoader.setUseHighRes(currentZoom >= 12.0)

        // Dynamically toggle precomputed ZoomCategory (Far/Medium/Close) based on zoom range
        val category = MetroMapOverlayLoader.getZoomCategoryForLevel(currentZoom)
        if (MetroMapOverlayLoader.getZoomCategory() != category) {
            MetroMapOverlayLoader.setZoomCategory(category)
        }

        if (lastRawMetroStations !== metroStations) {
            lastRawMetroStations = metroStations
            cachedValidMetroStations = metroStations.filter { station ->
                val lat = station.latitude
                val lon = station.longitude
                lat != null && lon != null && lat != 0.0 && lon != 0.0
            }
            cachedMetroPositions = cachedValidMetroStations.associate { s ->
                val fallback = GeoPoint(s.latitude!!, s.longitude!!)
                s.name to com.example.data.repository.StaticTransitDataCache.getVisualPositionForMetro(s.name, fallback)
            }
        }

        if (lastRawCercaniasStations !== cercaniasStations) {
            lastRawCercaniasStations = cercaniasStations
            cachedValidCercaniasStations = cercaniasStations.filter { station ->
                station.lat != 0.0 && station.lon != 0.0
            }
            cachedCercaniasPositions = cachedValidCercaniasStations.associate { s ->
                val fallback = GeoPoint(s.lat, s.lon)
                s.stop_id to com.example.data.repository.StaticTransitDataCache.getVisualPositionForCercanias(s.stop_id, fallback)
            }
        }

        val validMetroStations = if (showMetro) cachedValidMetroStations else emptyList()
        val validCercaniasStations = if (showCercanias) cachedValidCercaniasStations else emptyList()

        // Fast visual positions lookup using StaticTransitDataCache
        if (!com.example.data.repository.StaticTransitDataCache.isOffsetsReady() && metroStations.isNotEmpty() && cercaniasStations.isNotEmpty()) {
            val mList = metroStations.mapNotNull { s ->
                val lat = s.latitude
                val lon = s.longitude
                if (lat != null && lon != null && lat != 0.0 && lon != 0.0) s.name to GeoPoint(lat, lon) else null
            }
            val cList = cercaniasStations.mapNotNull { s ->
                if (s.lat != 0.0 && s.lon != 0.0) s.stop_id to GeoPoint(s.lat, s.lon) else null
            }
            com.example.data.repository.StaticTransitDataCache.precomputeStationVisualOffsets(mList, cList)
        }

        val metroPositions = cachedMetroPositions
        val cercaniasPositions = cachedCercaniasPositions

        this.currentMetroStations = validMetroStations
        this.currentCercaniasStations = validCercaniasStations
        this.currentMetroPositions = metroPositions
        this.currentCercaniasPositions = cercaniasPositions
        this.showMetro = showMetro
        this.showCercanias = showCercanias
        this.currentZoomLevel = currentZoom

        val isOnlyMetroSelected = mapFilter.showMetro &&
                !mapFilter.showBus &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        val isOnlyCercaniasSelected = mapFilter.showCercanias &&
                !mapFilter.showBus &&
                !mapFilter.showMetro &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        this.isOnlyMetroSelected = isOnlyMetroSelected
        this.isOnlyCercaniasSelected = isOnlyCercaniasSelected

        // Option 3: Zoom level of detail (hide pills/text at lower zoom levels, show only pins)
        val showPill = currentZoom >= 15.0

        // Delegate Metro & Cercanías markers rendering
        MetroMarkersRenderer.renderMetroMarkers(
            context = context,
            mapView = mapView,
            validMetroStations = validMetroStations,
            metroPositions = metroPositions,
            currentZoom = currentZoom,
            isDarkMode = isDarkMode,
            isOnlyMetroSelected = isOnlyMetroSelected,
            showPill = showPill,
            selectedMapItem = selectedMapItem,
            recycledMetroMarkers = recycledMetroMarkers,
            onSelectItem = currentOnSelectItem,
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )

        MetroMarkersRenderer.renderCercaniasMarkers(
            context = context,
            mapView = mapView,
            validCercaniasStations = validCercaniasStations,
            cercaniasPositions = cercaniasPositions,
            currentZoom = currentZoom,
            isDarkMode = isDarkMode,
            isOnlyCercaniasSelected = isOnlyCercaniasSelected,
            showPill = showPill,
            selectedMapItem = selectedMapItem,
            appLanguage = appLanguage,
            recycledCercaniasMarkers = recycledCercaniasMarkers,
            onSelectItem = currentOnSelectItem,
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )

        // 4. Viewport Level of Detail (LOD) & Spatial Filtering for Bus, Metrobus, and Valenbisi
        val filtered = ViewportTransitFilter.filterForViewport(
            boundingBox = mapView.boundingBox,
            busStops = busStops,
            metrobusStops = metrobusStops,
            valenbisiStations = valenbisiStations,
            mapFilter = mapFilter,
            currentZoom = currentZoom
        )

        this.currentBusStopsInViewport = filtered.busStopsInViewport
        this.showBus = filtered.showBus
        this.currentMetrobusStopsInViewport = filtered.metrobusStopsInViewport
        this.showMetrobus = filtered.showMetrobus
        this.currentValenbisiStations = filtered.valenbisiStationsInViewport
        this.showValenbisi = filtered.showValenbisi
        this.currentZoomLevel = currentZoom

        val isOnlyBusSelected = mapFilter.showBus &&
                !mapFilter.showMetro &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        val isOnlyMetrobusSelected = mapFilter.showMetrobus &&
                !mapFilter.showMetro &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        // Find and render merged EMT + Metrobús stops (same name & nearby coordinates)
        val mergedGroups = BusMarkersRenderer.findMergedStopGroups(
            busStopsInViewport = filtered.busStopsInViewport,
            metrobusStopsInViewport = filtered.metrobusStopsInViewport,
            selectedMapItem = selectedMapItem,
            showBus = filtered.showBus,
            showMetrobus = filtered.showMetrobus,
            isFavoritesMode = filtered.isFavoritesMode,
            currentZoom = currentZoom
        )

        val activeMergedBusCount = BusMarkersRenderer.renderMergedBusStops(
            context = context,
            mapView = mapView,
            mergedGroups = mergedGroups,
            isDarkMode = isDarkMode,
            currentZoom = currentZoom,
            showBus = filtered.showBus,
            showMetrobus = filtered.showMetrobus,
            isOnlyBusOrMbSelected = isOnlyBusSelected || isOnlyMetrobusSelected,
            isFavoritesMode = filtered.isFavoritesMode,
            recycledMergedMarkers = recycledMergedBusMarkers,
            selectedMapItem = selectedMapItem,
            selectedBusLineFilters = selectedBusLineFilters,
            onSelectItem = currentOnSelectItem,
            onShowDisambiguationMenu = currentOnShowDisambiguationMenu,
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )

        val isZoomedOut = currentZoom < 13.5 && !filtered.isFavoritesMode

        val (busStopsForSingleRender, mbStopsForSingleRender) = if (isZoomedOut) {
            Pair(filtered.busStopsInViewport, filtered.metrobusStopsInViewport)
        } else {
            val mergedBusIds = mergedGroups.map { it.busStop.id_parada }.toSet()
            val mergedMbIds = mergedGroups.map { it.mbStop.id_parada }.toSet()

            val busStops = if (mergedBusIds.isEmpty()) filtered.busStopsInViewport else filtered.busStopsInViewport.filter { !mergedBusIds.contains(it.id_parada) }
            val mbStops = if (mergedMbIds.isEmpty()) filtered.metrobusStopsInViewport else filtered.metrobusStopsInViewport.filter { !mergedMbIds.contains(it.id_parada) }
            Pair(busStops, mbStops)
        }

        val busRenderResult = BusMarkersRenderer.renderBusStops(
            context = context,
            mapView = mapView,
            busStopsInViewport = busStopsForSingleRender,
            isFavoritesMode = filtered.isFavoritesMode,
            isOnlyBusSelected = isOnlyBusSelected,
            isDarkMode = isDarkMode,
            currentZoom = currentZoom,
            showBus = filtered.showBus,
            busStopAliases = busStopAliases,
            selectedMapItem = selectedMapItem,
            selectedBusLineFilters = selectedBusLineFilters,
            recycledBusMarkers = recycledBusMarkers,
            recycledClusterMarkers = recycledClusterMarkers,
            onSelectItem = currentOnSelectItem,
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )
        val activeBusCount = busRenderResult.activeMarkerCount
        val activeClusterCount = busRenderResult.activeClusterCount

        val metrobusRenderResult = BusMarkersRenderer.renderMetrobusStops(
            context = context,
            mapView = mapView,
            metrobusStopsInViewport = mbStopsForSingleRender,
            isMetrobusFavoritesMode = filtered.isMetrobusFavoritesMode,
            isOnlyMetrobusSelected = isOnlyMetrobusSelected,
            isDarkMode = isDarkMode,
            currentZoom = currentZoom,
            showMetrobus = filtered.showMetrobus,
            selectedMapItem = selectedMapItem,
            selectedBusLineFilters = selectedBusLineFilters,
            recycledMetrobusMarkers = recycledMetrobusMarkers,
            recycledMetrobusClusterMarkers = recycledMetrobusClusterMarkers,
            onSelectItem = currentOnSelectItem,
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )
        val activeMetrobusCount = metrobusRenderResult.activeMarkerCount
        val activeMetrobusClusterCount = metrobusRenderResult.activeClusterCount

        val isOnlyValenbisi = mapFilter.showValenbisi && !mapFilter.showBus && !mapFilter.showMetrobus && !mapFilter.showMetro && !mapFilter.showCercanias && !mapFilter.isFavorites
        val valenbisiRenderResult = ValenbisiMarkersRenderer.renderValenbisi(
            context = context,
            mapView = mapView,
            valenbisiStations = filtered.valenbisiStationsInViewport,
            showValenbisi = filtered.showValenbisi,
            isOnlyValenbisi = isOnlyValenbisi,
            currentZoom = currentZoom,
            isDarkMode = isDarkMode,
            recycledValenbisiMarkers = recycledValenbisiMarkers,
            recycledValenbisiClusterMarkers = recycledValenbisiClusterMarkers,
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )
        val activeValenbisiCount = valenbisiRenderResult.activeMarkerCount
        val activeValenbisiClusterCount = valenbisiRenderResult.activeClusterCount

        // Paint Custom Places (Home, Work, and Favorites) with Zoom LOD
        val activeCustomFavCount = CustomPlacesMarkersRenderer.renderCustomPlaces(
            context = context,
            mapView = mapView,
            homeLocation = homeLocation,
            workLocation = workLocation,
            customFavorites = customFavorites,
            currentZoom = currentZoom,
            isDarkMode = isDarkMode,
            recycledCustomFavoriteMarkers = recycledCustomFavoriteMarkers,
            onSelectItem = currentOnSelectItem,
            onTapHandler = { ctx, map, pos -> handleTapAtGeoPoint(ctx, map, pos) }
        )

        // Re-order overlays to respect the layer sequence via MapOverlaysComposer
        val composeParams = MapOverlaysComposer.ComposeParams(
            context = context,
            mapView = mapView,
            selectedItinerary = selectedItinerary,
            showMetro = showMetro,
            showCercanias = showCercanias,
            showMetrobus = showMetrobus,
            showValenbisi = showValenbisi,
            currentZoom = currentZoom,
            isDarkMode = isDarkMode,
            mapEventsOverlay = mapEventsOverlay,
            destinationMarker = destinationMarker,
            destinationLocation = destinationLocation,
            userMarker = userMarker,
            userLocation = userLocation,
            activeBusCount = activeBusCount,
            activeClusterCount = activeClusterCount,
            recycledBusMarkers = recycledBusMarkers,
            recycledClusterMarkers = recycledClusterMarkers,
            activeMergedBusCount = activeMergedBusCount,
            recycledMergedBusMarkers = recycledMergedBusMarkers,
            activeMetrobusCount = activeMetrobusCount,
            activeMetrobusClusterCount = activeMetrobusClusterCount,
            recycledMetrobusMarkers = recycledMetrobusMarkers,
            recycledMetrobusClusterMarkers = recycledMetrobusClusterMarkers,
            activeValenbisiCount = activeValenbisiCount,
            activeValenbisiClusterCount = activeValenbisiClusterCount,
            recycledValenbisiMarkers = recycledValenbisiMarkers,
            recycledValenbisiClusterMarkers = recycledValenbisiClusterMarkers,
            validMetroStationsCount = validMetroStations.size,
            recycledMetroMarkers = recycledMetroMarkers,
            validCercaniasStationsCount = validCercaniasStations.size,
            recycledCercaniasMarkers = recycledCercaniasMarkers,
            activeCustomFavCount = activeCustomFavCount,
            recycledCustomFavoriteMarkers = recycledCustomFavoriteMarkers,
            lastZoomedItineraryId = lastZoomedItineraryId,
            selectedMapItem = selectedMapItem,
            validMetroStations = validMetroStations,
            selectedBusLineFilters = selectedBusLineFilters,
            selectedMetrobusShapes = selectedMetrobusShapes,
            selectedDirectionFilter = selectedDirectionFilter,
            onSelectItem = onSelectItem
        )
        lastZoomedItineraryId = MapOverlaysComposer.composeOverlays(composeParams)
    }

    /**
     * Cleans up static map references and stops all running animation loops when the map screen is disposed.
     */
    fun clearReferences(mapView: MapView? = null) {
        stopLiveLocationUpdates()
        if (mapView == null || lastMapView == mapView) {
            lastMapView?.let {
                try {
                    MetroStationFootprintsOverlayManager.clearFromMap(it)
                    LiveTrainMarkerManager.clearLiveTrain(it)
                    MapFadeTransitionManager.reset()
                } catch (_: Exception) {}
            }
            lastMapView = null
            userMarker = null
            destinationMarker = null
            mapEventsOverlay = null
            clearRecycledMarkers()
            ViewportTransitFilter.clearCache()
            currentOnMapClick = null
            currentOnMapLongClick = null
            currentOnSelectItem = null
            currentOnShowDisambiguationMenu = null
            currentSelectedMapItem = null
        }
    }
}
