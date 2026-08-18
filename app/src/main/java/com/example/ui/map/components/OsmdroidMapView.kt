package com.example.ui.map.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
    mapFilter: MapFilter = MapFilter.FAVORITES,
    busStopAliases: Map<String, String> = emptyMap(),
    appLanguage: AppLanguage = AppLanguage.CA,
    selectedItinerary: com.example.data.model.routing.PlannedItinerary? = null,
    onSelectItem: (SelectedMapItem) -> Unit,
    onMapClick: () -> Unit,
    onMapTouch: (() -> Unit)? = null,
    onCameraPositionChanged: ((GeoPoint, Double) -> Unit)? = null,
    onZoomLevelChanged: ((Double) -> Unit)? = null,
    onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null,
    onMapLongClick: ((GeoPoint) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapTouch by rememberUpdatedState(onMapTouch)

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
            setScrollableAreaLimitDouble(MapConfig.VALENCIA_BOUNDS)

            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            isTilesScaledToDpi = true

            // Match background color of the map container with the tile source to eliminate bright grey gaps during loads
            setBackgroundColor(if (isDarkMode) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#F1F5F9"))

            // Optimize tileProvider and tileCache for adjacent tile preloading and memory retention
            tileProvider.apply {
                tileCache.apply {
                    ensureCapacity(1000)
                    setAutoEnsureCapacity(true)
                    protectedTileComputers.apply {
                        clear()
                        // Pre-load and retain 8 rings of adjacent surrounding tiles beyond visible screen viewport
                        add(org.osmdroid.util.MapTileAreaBorderComputer(8))
                        // Retain and stretch multi-level zoom tiles to eliminate blank gray gaps while zooming/panning
                        add(org.osmdroid.util.MapTileAreaZoomComputer(-1))
                        add(org.osmdroid.util.MapTileAreaZoomComputer(-2))
                        add(org.osmdroid.util.MapTileAreaZoomComputer(-3))
                        add(org.osmdroid.util.MapTileAreaZoomComputer(1))
                        add(org.osmdroid.util.MapTileAreaZoomComputer(2))
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

            var firstTapTime = 0L
            var firstTapX = 0f
            var firstTapY = 0f
            var down1Time = 0L
            var down1X = 0f
            var down1Y = 0f
            var isDoubleTapDragging = false
            var doubleTapStartY = 0f
            var startZoom = 0.0
            var doubleTapCenter: GeoPoint? = null
            var hasDragged = false

            var userHasInteractedWithMap = false

            setOnTouchListener { _, event ->
                currentOnMapTouch?.invoke()
                if (event.pointerCount > 1) {
                    userHasInteractedWithMap = true
                    MapMarkersManager.notifyGesture()
                    isDoubleTapDragging = false
                    return@setOnTouchListener false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val now = System.currentTimeMillis()
                        val dx = Math.abs(event.x - firstTapX)
                        val dy = Math.abs(event.y - firstTapY)

                        down1Time = now
                        down1X = event.x
                        down1Y = event.y

                        if (now - firstTapTime < 350L && dx < 100f && dy < 100f) {
                            userHasInteractedWithMap = true
                            MapMarkersManager.notifyGesture()
                            isDoubleTapDragging = true
                            doubleTapStartY = event.y
                            startZoom = zoomLevelDouble
                            val center = mapCenter
                            doubleTapCenter = if (center != null) GeoPoint(center.latitude, center.longitude) else null
                            hasDragged = false
                            true
                        } else {
                            isDoubleTapDragging = false
                            false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveX = Math.abs(event.x - down1X)
                        val moveY = Math.abs(event.y - down1Y)
                        if (moveX > 20f || moveY > 20f) {
                            userHasInteractedWithMap = true
                            MapMarkersManager.notifyGesture()
                        }
                        if (isDoubleTapDragging) {
                            MapMarkersManager.notifyGesture()
                            val deltaY = event.y - doubleTapStartY
                            if (Math.abs(deltaY) > 8f) {
                                hasDragged = true
                            }
                            // Moving down (positive deltaY) -> zoom in
                            // Moving up (negative deltaY) -> zoom out
                            val zoomSensitivity = 180.0
                            val targetZoom = (startZoom + (deltaY / zoomSensitivity)).coerceIn(minZoomLevel, maxZoomLevel)
                            controller.setZoom(targetZoom)
                            doubleTapCenter?.let { controller.setCenter(it) }
                            invalidate()
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDoubleTapDragging) {
                            MapMarkersManager.notifyGesture()
                            isDoubleTapDragging = false
                            firstTapTime = 0L
                            doubleTapCenter = null
                            if (hasDragged) {
                                onZoomLevelChanged?.invoke(zoomLevelDouble)
                                true
                            } else {
                                controller.zoomIn()
                                onZoomLevelChanged?.invoke(zoomLevelDouble)
                                true
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            val moveX = Math.abs(event.x - down1X)
                            val moveY = Math.abs(event.y - down1Y)
                            if (now - down1Time < 250L && moveX < 30f && moveY < 30f) {
                                firstTapTime = now
                                firstTapX = event.x
                                firstTapY = event.y
                            } else {
                                MapMarkersManager.notifyGesture()
                                firstTapTime = 0L
                            }
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isDoubleTapDragging = false
                        firstTapTime = 0L
                        doubleTapCenter = null
                        false
                    }
                    else -> false
                }
            }
        }
    }

    // Lifecycle handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Dynamic tile source switching based on dark mode theme
    LaunchedEffect(isDarkMode) {
        mapView.setBackgroundColor(if (isDarkMode) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#F1F5F9"))
        mapView.setTileSource(if (isDarkMode) MapConfig.CARTO_DARK_SOURCE else MapConfig.CARTO_LIGHT_SOURCE)
        mapView.overlayManager.tilesOverlay.apply {
            setLoadingBackgroundColor(
                if (isDarkMode) android.graphics.Color.parseColor("#121826") else android.graphics.Color.parseColor("#E2E8F0")
            )
            setColorFilter(if (isDarkMode) darkContrastFilter else null)
        }
        mapView.invalidate()
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
            mapView.controller.zoomIn()
            onZoomLevelChanged?.invoke(mapView.zoomLevelDouble)
        }
    }

    // Direct zoom out action
    LaunchedEffect(zoomOutTrigger) {
        if (zoomOutTrigger > 0) {
            mapView.controller.zoomOut()
            onZoomLevelChanged?.invoke(mapView.zoomLevelDouble)
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
    val currentSelectedItinerary by rememberUpdatedState(selectedItinerary)
    val currentOnSelectItem by rememberUpdatedState(onSelectItem)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnCameraPositionChanged by rememberUpdatedState(onCameraPositionChanged)
    val currentOnZoomLevelChanged by rememberUpdatedState(onZoomLevelChanged)
    val currentOnShowDisambiguationMenu by rememberUpdatedState(onShowDisambiguationMenu)
    val currentOnMapLongClick by rememberUpdatedState(onMapLongClick)

    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val coroutineScope = rememberCoroutineScope()

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

    DisposableEffect(Unit) {
        onDispose {
            LiveTrainMarkerManager.clearLiveTrain(mapView)
        }
    }

    // Update Markers on state change
    LaunchedEffect(busStops, metrobusStops, metroStations, cercaniasStations, valenbisiStations, customFavorites, homeLocation, workLocation, mapFilter, userLocation, destinationLocation, destinationTitle, isDarkMode, busStopAliases, appLanguage, selectedItinerary, onShowDisambiguationMenu, onMapLongClick) {
        MapMarkersManager.updateMarkers(
            context = context,
            mapView = mapView,
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
                    onSelectItem = currentOnSelectItem,
                    onMapClick = currentOnMapClick,
                    onShowDisambiguationMenu = currentOnShowDisambiguationMenu,
                    onMapLongClick = currentOnMapLongClick
                )
            }
            pendingUpdateRunnable = runnable
            handler.postDelayed(runnable, 120L)
        }

        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                debouncedUpdate()
                val center = mapView.mapCenter
                if (center != null) {
                    currentOnCameraPositionChanged?.invoke(GeoPoint(center.latitude, center.longitude), mapView.zoomLevelDouble)
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val zoom = mapView.zoomLevelDouble
                val shouldBeHighRes = zoom >= 12.0
                val highResChanged = MetroMapOverlayLoader.isUseHighRes() != shouldBeHighRes
                if (highResChanged) {
                    MetroMapOverlayLoader.setUseHighRes(shouldBeHighRes)
                }
                val newCategory = MetroMapOverlayLoader.getZoomCategoryForLevel(zoom)
                val categoryChanged = MetroMapOverlayLoader.getZoomCategory() != newCategory
                if (categoryChanged) {
                    MetroMapOverlayLoader.setZoomCategory(newCategory)
                }
                
                // PERFORMANCE: Recalculate and redraw overlays ONLY when camera crosses thresholds
                if (highResChanged || categoryChanged) {
                    debouncedUpdate()
                }
                
                currentOnZoomLevelChanged?.invoke(zoom)
                val center = mapView.mapCenter
                if (center != null) {
                    currentOnCameraPositionChanged?.invoke(GeoPoint(center.latitude, center.longitude), zoom)
                }
                return false
            }
        }
        mapView.addMapListener(listener)
        onDispose {
            pendingUpdateRunnable?.let { handler.removeCallbacks(it) }
            mapView.removeMapListener(listener)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
