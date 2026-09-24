package com.example.ui.map.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.RecentSearch
import com.example.ui.map.MapConfig
import com.example.ui.map.MapFilter
import com.example.ui.map.SelectedMapItem
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

private val darkContrastFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            1.4f, 0f, 0f, 0f, 10f,
            0f, 1.4f, 0f, 0f, 10f,
            0f, 0f, 1.4f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

@Composable
fun OsmdroidMapView(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    isSatelliteMode: Boolean = false,
    cameraTarget: GeoPoint = MapConfig.VALENCIA_CENTER,
    cameraZoom: Double = MapConfig.DEFAULT_ZOOM,
    cameraAnimTrigger: Int = 0,
    zoomInTrigger: Int = 0,
    zoomOutTrigger: Int = 0,
    userLocation: GeoPoint? = null,
    destinationLocation: GeoPoint? = null,
    destinationTitle: String? = null,
    busStops: List<GeoportalStopEntity> = emptyList(),
    metrobusStops: List<com.example.data.database.MetrobusStopEntity> = emptyList(),
    metroStations: List<MetroStation> = emptyList(),
    cercaniasStations: List<com.example.data.database.CercaniasStationEntity> = emptyList(),
    valenbisiStations: List<com.example.ui.map.components.ValenbisiStation> = emptyList(),
    customFavorites: List<RecentSearch> = emptyList(),
    homeLocation: RecentSearch? = null,
    workLocation: RecentSearch? = null,
    mapFilter: MapFilter = MapFilter.DEFAULT,
    busStopAliases: Map<String, String> = emptyMap(),
    appLanguage: AppLanguage = AppLanguage.CA,
    selectedItinerary: com.example.data.model.routing.PlannedItinerary? = null,
    selectedMapItem: SelectedMapItem? = null,
    selectedBusLineFilters: Set<String> = emptySet(),
    selectedMetrobusShapes: Map<String, List<GeoPoint>> = emptyMap(),
    bottomPanelOffsetPx: Float = 0f,
    onSelectItem: (SelectedMapItem) -> Unit,
    onMapClick: () -> Unit,
    onMapTouch: (() -> Unit)? = null,
    onMapPan: (() -> Unit)? = null,
    onCameraPositionChanged: ((GeoPoint, Double) -> Unit)? = null,
    onZoomLevelChanged: ((Double) -> Unit)? = null,
    onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null,
    onMapLongClick: ((GeoPoint) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapTouch by rememberUpdatedState(onMapTouch)
    val currentOnMapPan by rememberUpdatedState(onMapPan)
    val currentOnZoomLevelChanged by rememberUpdatedState(onZoomLevelChanged)
    val currentOnCameraPositionChanged by rememberUpdatedState(onCameraPositionChanged)
    val currentOnMapLongClick by rememberUpdatedState(onMapLongClick)
    val currentSelectedItinerary by rememberUpdatedState(selectedItinerary)

    // Initialize MapConfig User Agent & Preferences
    remember {
        MapConfig.initialize(context)
        true
    }

    val mapView = remember {
        MapView(context).apply {
            val initialTileSource = if (isDarkMode) MapConfig.CARTO_DARK_SOURCE else MapConfig.CARTO_LIGHT_SOURCE
            setTileSource(initialTileSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = MapConfig.MIN_ZOOM
            maxZoomLevel = MapConfig.MAX_ZOOM
            resetScrollableAreaLimitLatitude()
            resetScrollableAreaLimitLongitude()

            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            isTilesScaledToDpi = true

            // Match background color of the map container with the tile source to eliminate bright grey gaps during loads
            setBackgroundColor(if (isDarkMode) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#F1F5F9"))

            // Optimize tileProvider and tileCache for adjacent tile preloading and memory retention
            tileProvider.apply {
                tileCache.apply {
                    ensureCapacity(200)
                    setAutoEnsureCapacity(true)
                    protectedTileComputers.apply {
                        clear()
                        // Pre-load and retain 2 rings of adjacent surrounding tiles (sufficient for smooth pan without RAM pressure)
                        add(org.osmdroid.util.MapTileAreaBorderComputer(2))
                        // Retain multi-level zoom tiles for instant pinch transitions
                        add(org.osmdroid.util.MapTileAreaZoomComputer(-1))
                        add(org.osmdroid.util.MapTileAreaZoomComputer(1))
                    }
                }
            }

            // Optimize tile overlay rendering to eliminate visible square grid lines & enable background fetching
            overlayManager.tilesOverlay.apply {
                setLoadingBackgroundColor(if (isDarkMode) android.graphics.Color.parseColor("#121826") else android.graphics.Color.parseColor("#E2E8F0"))
                setLoadingLineColor(android.graphics.Color.TRANSPARENT)
                setUseDataConnection(true)
                isHorizontalWrapEnabled = false
                isVerticalWrapEnabled = false
                if (isDarkMode) {
                    setColorFilter(darkContrastFilter)
                }
            }

            controller.setZoom(cameraZoom)
            controller.setCenter(cameraTarget)

            setOnTouchListener(
                MapGestureTouchHandler.createTouchListener(
                    mapView = this,
                    getMapCenterOffsetY = { mapCenterOffsetY },
                    onMapTouch = { currentOnMapTouch?.invoke() },
                    onMapPan = { currentOnMapPan?.invoke() },
                    onZoomLevelChanged = { currentOnZoomLevelChanged?.invoke(it) },
                    onCameraPositionChanged = { center, zoom -> currentOnCameraPositionChanged?.invoke(center, zoom) },
                    onMapLongClick = { currentOnMapLongClick?.invoke(it) },
                    isItinerarySelected = { currentSelectedItinerary != null }
                )
            )
        }
    }

    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val coroutineScope = rememberCoroutineScope()

    // Lifecycle handling for mapView and sensors
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        mapView.onResume()
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        mapView.onPause()
                        stopLiveLocationUpdates()
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        mapView.onDetach()
                        MapMarkersManager.clearReferences(mapView)
                    } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
            } catch (_: Exception) {}
            stopLiveLocationUpdates()
            handler.removeCallbacksAndMessages(null)
            LiveTrainMarkerManager.clearLiveTrain(mapView)
            MapMarkersManager.clearReferences(mapView)
            MetroStationFootprintsOverlayManager.clearFromMap(mapView)
        }
    }

    // Dynamic tile source switching based on dark mode theme and satellite mode
    LaunchedEffect(isDarkMode, isSatelliteMode) {
        if (isSatelliteMode) {
            mapView.setBackgroundColor(android.graphics.Color.parseColor("#0B131F"))
            mapView.setTileSource(MapConfig.ESRI_SATELLITE_SOURCE)
            mapView.overlayManager.tilesOverlay.apply {
                setLoadingBackgroundColor(android.graphics.Color.parseColor("#0B131F"))
                setColorFilter(null)
            }
        } else {
            mapView.setBackgroundColor(if (isDarkMode) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#F1F5F9"))
            mapView.setTileSource(if (isDarkMode) MapConfig.CARTO_DARK_SOURCE else MapConfig.CARTO_LIGHT_SOURCE)
            mapView.overlayManager.tilesOverlay.apply {
                setLoadingBackgroundColor(
                    if (isDarkMode) android.graphics.Color.parseColor("#121826") else android.graphics.Color.parseColor("#E2E8F0")
                )
                setColorFilter(if (isDarkMode) darkContrastFilter else null)
            }
        }
        mapView.invalidate()
    }

    val isSettingOffsetRef = remember { AtomicBoolean(false) }
    val targetOffsetY = if (bottomPanelOffsetPx > 0f) {
        -(bottomPanelOffsetPx / 2f).toInt()
    } else {
        0
    }

    fun getFocalCenterGeoPoint(): GeoPoint {
        val proj = mapView.projection
        val width = mapView.width
        val height = mapView.height
        if (proj != null && width > 0 && height > 0) {
            val focalX = width / 2
            val focalY = height / 2 + targetOffsetY
            val igp = proj.fromPixels(focalX, focalY)
            if (igp != null) {
                return GeoPoint(igp.latitude, igp.longitude)
            }
        }
        val center = mapView.mapCenter
        return if (center != null) GeoPoint(center.latitude, center.longitude) else MapConfig.VALENCIA_CENTER
    }

    // Animate camera when target changes programmatically
    LaunchedEffect(cameraAnimTrigger) {
        if (cameraAnimTrigger > 0) {
            mapView.controller.animateTo(cameraTarget, cameraZoom, 500L)
        }
    }

    // Direct zoom in action
    LaunchedEffect(zoomInTrigger) {
        if (zoomInTrigger > 0) {
            val fx = mapView.width / 2
            val fy = if (mapView.height > 0) mapView.height / 2 + targetOffsetY else mapView.height / 2
            mapView.controller.zoomInFixing(fx, fy)
            onZoomLevelChanged?.invoke(mapView.zoomLevelDouble)
        }
    }

    // Direct zoom out action
    LaunchedEffect(zoomOutTrigger) {
        if (zoomOutTrigger > 0) {
            val fx = mapView.width / 2
            val fy = if (mapView.height > 0) mapView.height / 2 + targetOffsetY else mapView.height / 2
            mapView.controller.zoomOutFixing(fx, fy)
            onZoomLevelChanged?.invoke(mapView.zoomLevelDouble)
        }
    }

    SideEffect {
        if (mapView.mapCenterOffsetY != targetOffsetY) {
            isSettingOffsetRef.set(true)
            try {
                android.util.Log.d("MapOffsetCheck", "Applying setMapCenterOffset(0, $targetOffsetY) [previous: ${mapView.mapCenterOffsetY}]")
                mapView.setMapCenterOffset(0, targetOffsetY)
                mapView.invalidate()
            } finally {
                isSettingOffsetRef.set(false)
            }
        }
    }

    val currentBusStops by rememberUpdatedState(busStops)
    val currentMetrobusStops by rememberUpdatedState(metrobusStops)
    val currentMetroStations by rememberUpdatedState(metroStations)
    val currentCercaniasStations by rememberUpdatedState(cercaniasStations)
    val currentValenbisiStations by rememberUpdatedState(valenbisiStations)
    val currentCustomFavorites by rememberUpdatedState(customFavorites)
    val currentHomeLocation by rememberUpdatedState(homeLocation)
    val currentWorkLocation by rememberUpdatedState(workLocation)
    val currentMapFilter by rememberUpdatedState(mapFilter)
    val currentUserLocation by rememberUpdatedState(userLocation)
    val currentDestinationLocation by rememberUpdatedState(destinationLocation)
    val currentDestinationTitle by rememberUpdatedState(destinationTitle)
    val currentIsDarkMode by rememberUpdatedState(isDarkMode)
    val currentBusStopAliases by rememberUpdatedState(busStopAliases)
    val currentSelectedMapItem by rememberUpdatedState(selectedMapItem)
    val currentOnSelectItem by rememberUpdatedState(onSelectItem)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnShowDisambiguationMenu by rememberUpdatedState(onShowDisambiguationMenu)
    val currentSelectedBusLineFilters by rememberUpdatedState(selectedBusLineFilters)
    val currentSelectedMetrobusShapes by rememberUpdatedState(selectedMetrobusShapes)

    val isCercaniasLoaded by CercaniasMapOverlayLoader.isLoadedState.collectAsState()
    val isEmtLoaded by EmtMapOverlayLoader.isLoadedState.collectAsState()

    // Trigger background lazy loading of Cercanías GeoJSON as soon as the layer is needed
    LaunchedEffect(mapFilter) {
        if (mapFilter.isFavorites || mapFilter.showCercanias) {
            CercaniasMapOverlayLoader.ensureLoaded(context)
        }
    }

    // Trigger background lazy loading of EMT GTFS routes and shapes
    LaunchedEffect(mapFilter, selectedMapItem) {
        if (mapFilter.isFavorites || mapFilter.showBus || selectedMapItem is SelectedMapItem.BusStop) {
            com.example.data.repository.emt.EmtDataSyncManager.syncIfNeeded(context)
            EmtMapOverlayLoader.ensureLoaded(context)
        }
    }

    // Bind real-time animated Cercanías train marker during route preview
    LaunchedEffect(selectedItinerary, isDarkMode) {
        LiveTrainMarkerManager.bindLiveTrain(
            context = context,
            mapView = mapView,
            itinerary = selectedItinerary,
            coroutineScope = coroutineScope,
            isDarkMode = isDarkMode
        )
    }

    // Update Markers on state change or when transit line overlays finish lazy loading
    LaunchedEffect(
        busStops, metrobusStops, metroStations, cercaniasStations, valenbisiStations,
        customFavorites, homeLocation, workLocation, mapFilter, userLocation,
        destinationLocation, destinationTitle, isDarkMode, busStopAliases,
        appLanguage, selectedItinerary, onShowDisambiguationMenu, onMapLongClick,
        isCercaniasLoaded, isEmtLoaded, selectedMapItem, selectedBusLineFilters, selectedMetrobusShapes
    ) {
        MapMarkersManager.updateMarkers(
            context = context,
            mapView = mapView,
            cameraZoom = cameraZoom,
            busStops = busStops,
            metrobusStops = metrobusStops,
            metroStations = metroStations,
            cercaniasStations = cercaniasStations,
            valenbisiStations = valenbisiStations,
            customFavorites = customFavorites,
            homeLocation = homeLocation,
            workLocation = workLocation,
            mapFilter = mapFilter,
            userLocation = userLocation,
            destinationLocation = destinationLocation,
            destinationTitle = destinationTitle,
            isDarkMode = isDarkMode,
            busStopAliases = busStopAliases,
            appLanguage = appLanguage,
            selectedItinerary = selectedItinerary,
            selectedMapItem = selectedMapItem,
            selectedBusLineFilters = selectedBusLineFilters,
            selectedMetrobusShapes = selectedMetrobusShapes,
            onSelectItem = onSelectItem,
            onMapClick = onMapClick,
            onShowDisambiguationMenu = onShowDisambiguationMenu,
            onMapLongClick = onMapLongClick
        )
    }

    // Attach MapListener with 120ms debounce for smooth pan and zoom
    DisposableEffect(mapView) {
        var pendingUpdateRunnable: Runnable? = null

        val debouncedUpdate = {
            pendingUpdateRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                MapMarkersManager.updateMarkers(
                    context = context,
                    mapView = mapView,
                    cameraZoom = cameraZoom,
                    busStops = currentBusStops,
                    metrobusStops = currentMetrobusStops,
                    metroStations = currentMetroStations,
                    cercaniasStations = currentCercaniasStations,
                    valenbisiStations = currentValenbisiStations,
                    customFavorites = currentCustomFavorites,
                    homeLocation = currentHomeLocation,
                    workLocation = currentWorkLocation,
                    mapFilter = currentMapFilter,
                    userLocation = currentUserLocation,
                    destinationLocation = currentDestinationLocation,
                    destinationTitle = currentDestinationTitle,
                    isDarkMode = currentIsDarkMode,
                    busStopAliases = currentBusStopAliases,
                    appLanguage = appLanguage,
                    selectedItinerary = currentSelectedItinerary,
                    selectedMapItem = currentSelectedMapItem,
                    selectedBusLineFilters = currentSelectedBusLineFilters,
                    selectedMetrobusShapes = currentSelectedMetrobusShapes,
                    onSelectItem = currentOnSelectItem,
                    onMapClick = currentOnMapClick,
                    onShowDisambiguationMenu = currentOnShowDisambiguationMenu,
                    onMapLongClick = currentOnMapLongClick
                )
            }
            pendingUpdateRunnable = runnable
            handler.postDelayed(runnable, 120L)
        }

        var lastCameraUpdateTime = 0L
        var pendingCameraRunnable: Runnable? = null

        fun dispatchCameraPosition(zoom: Double) {
            pendingCameraRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                val center = getFocalCenterGeoPoint()
                currentOnCameraPositionChanged?.invoke(center, zoom)
            }
            pendingCameraRunnable = runnable
            val now = System.currentTimeMillis()
            if (now - lastCameraUpdateTime >= 160L) {
                lastCameraUpdateTime = now
                runnable.run()
            } else {
                handler.postDelayed(runnable, 160L)
            }
        }

        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (isSettingOffsetRef.get()) {
                    android.util.Log.d("MapOffsetCheck", "Filtered onScroll event during setMapCenterOffset: $event")
                    return false
                }
                debouncedUpdate()
                dispatchCameraPosition(mapView.zoomLevelDouble)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val zoom = mapView.zoomLevelDouble
                val shouldBeHighRes = zoom >= 12.0
                MetroMapOverlayLoader.setUseHighRes(shouldBeHighRes)
                val newCategory = MetroMapOverlayLoader.getZoomCategoryForLevel(zoom)
                MetroMapOverlayLoader.setZoomCategory(newCategory)
                
                // Debounced update ensures markers & line overlays update smoothly on zoom
                debouncedUpdate()
                
                currentOnZoomLevelChanged?.invoke(zoom)
                dispatchCameraPosition(zoom)
                return false
            }
        }
        mapView.addMapListener(listener)

        val firstLayoutListener = MapView.OnFirstLayoutListener { _, _, _, _, _ ->
            debouncedUpdate()
        }
        mapView.addOnFirstLayoutListener(firstLayoutListener)

        if (mapView.isLaidOut) {
            debouncedUpdate()
        } else {
            mapView.post { debouncedUpdate() }
        }

        onDispose {
            pendingUpdateRunnable?.let { handler.removeCallbacks(it) }
            pendingCameraRunnable?.let { handler.removeCallbacks(it) }
            try {
                mapView.removeOnFirstLayoutListener(firstLayoutListener)
            } catch (_: Exception) {}
            try {
                mapView.removeMapListener(listener)
            } catch (_: Exception) {}
            try {
                mapView.setMapCenterOffset(0, 0)
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            update = { view ->
                if (view.mapCenterOffsetY != targetOffsetY) {
                    isSettingOffsetRef.set(true)
                    try {
                        view.setMapCenterOffset(0, targetOffsetY)
                        view.invalidate()
                    } finally {
                        isSettingOffsetRef.set(false)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isDarkMode) Color(0xD90F172A) else Color(0xD9F8FAFC),
            contentColor = if (isDarkMode) Color(0xCCCBD5E1) else Color(0xCC334155),
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Attribution",
                    modifier = Modifier.size(11.dp),
                    tint = if (isDarkMode) Color(0xAA94A3B8) else Color(0xAA64748B)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "© CARTO, © OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp
                    )
                )
            }
        }
    }
}
