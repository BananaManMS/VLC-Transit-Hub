package com.example.ui.cercanias

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.database.CercaniasStationEntity
import com.example.data.mapper.CercaniasDepartureMapper
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.map.CercaniasTrainOverlayManager
import com.example.ui.map.MapConfig
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.CercaniasMapOverlayLoader
import com.example.ui.map.components.CercaniasStationBottomSheet
import com.example.ui.map.components.DetailSheetState
import com.example.ui.map.components.MetroMarkersRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CercaniasLiveMapDialog(
    viewModel: CercaniasViewModel,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    
    val liveTrains by viewModel.liveCercaniasVehicles.collectAsState()
    val isMapLoading by viewModel.isLiveMapLoading.collectAsState()
    val favoriteStations by viewModel.cercaniasFavoriteStations.collectAsState()
    val allStations by viewModel.allCercaniasStations.collectAsState()

    // Setup local state for bottom sheet selections
    var selectedStationEntity by remember { mutableStateOf<CercaniasStationEntity?>(null) }
    var stationDepartures by remember { mutableStateOf<List<CercaniasDeparture>>(emptyList()) }
    var isStationLoading by remember { mutableStateOf(false) }
    
    var selectedTrainVehicle by remember { mutableStateOf<LiveVehicleInfo?>(null) }
    var trainSheetState by remember { mutableStateOf(DetailSheetState.HALF_EXPANDED) }

    LaunchedEffect(selectedTrainVehicle) {
        if (selectedTrainVehicle != null) {
            trainSheetState = DetailSheetState.HALF_EXPANDED
        }
    }

    val renfeRepository = remember { RenfeRepository(context.applicationContext as Application, com.example.data.database.AppDatabase.getDatabase(context)) }

    var redrawTrigger by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Load departures for clicked station in a separate coroutine
    LaunchedEffect(selectedStationEntity) {
        val station = selectedStationEntity
        if (station != null) {
            isStationLoading = true
            try {
                val rawDeps = renfeRepository.getDeparturesForStation(station.stop_id)
                stationDepartures = CercaniasDepartureMapper.sortDeparturesChronologically(rawDeps)
            } catch (e: Exception) {
                stationDepartures = emptyList()
            } finally {
                isStationLoading = false
            }
        } else {
            stationDepartures = emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                .testTag("cercanias_live_map_dialog")
        ) {
            // Force the dialog's window to draw edge-to-edge behind system bars with zero offsets
            val view = androidx.compose.ui.platform.LocalView.current
            DisposableEffect(view) {
                fun findDialogWindow(): android.view.Window? {
                    var ctx: android.content.Context? = view.context
                    while (ctx is android.content.ContextWrapper) {
                        if (ctx is androidx.compose.ui.window.DialogWindowProvider) {
                            return ctx.window
                        }
                        ctx = ctx.baseContext
                    }
                    var p = view.parent
                    while (p != null) {
                        if (p is androidx.compose.ui.window.DialogWindowProvider) {
                            return p.window
                        }
                        p = p.parent
                    }
                    return null
                }

                val window = findDialogWindow()
                if (window != null) {
                    window.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    window.setBackgroundDrawableResource(android.R.color.transparent)
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT

                    // Set light/dark icons on the status bar and navigation bar to be perfectly legible
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isDarkMode
                    insetsController.isAppearanceLightNavigationBars = !isDarkMode
                }
                onDispose {}
            }

            // Keep persistent lists of recycled markers for the lifecycle of this Dialog to avoid memory leaks
            val recycledCercaniasMarkers = remember { mutableListOf<Marker>() }
            var currentZoom by remember { mutableStateOf(10.5) }
            val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
                
            val mapView = remember {
                MapView(context).apply {
                    setTileSource(if (isDarkMode) MapConfig.CARTO_DARK_SOURCE else MapConfig.CARTO_LIGHT_SOURCE)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 8.5
                    maxZoomLevel = MapConfig.MAX_ZOOM
                    setScrollableAreaLimitDouble(MapConfig.VALENCIA_BOUNDS)
                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    isTilesScaledToDpi = true
                    setBackgroundColor(if (isDarkMode) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#F1F5F9"))
                    
                    controller.setZoom(10.5)
                    controller.setCenter(MapConfig.VALENCIA_CENTER)

                    setOnTouchListener(
                        com.example.ui.map.components.MapGestureTouchHandler.createTouchListener(
                            mapView = this,
                            getMapCenterOffsetY = { 0 },
                            onMapTouch = { },
                            onMapPan = { },
                            onZoomLevelChanged = { newZoom -> currentZoom = newZoom },
                            onCameraPositionChanged = { _, zoom -> currentZoom = zoom },
                            onMapLongClick = { },
                            isItinerarySelected = { false }
                        )
                    )
                }
            }

            val trainOverlayManager = remember(mapView) {
                CercaniasTrainOverlayManager(context, mapView) { vehicle ->
                    selectedStationEntity = null
                    selectedTrainVehicle = vehicle
                }
            }

            // Start polling, stop polling, and bind MapView to Android lifecycle with full cleanup
            DisposableEffect(lifecycleOwner, mapView) {
                try {
                    mapView.onResume()
                    viewModel.startLiveTrainsPolling()
                } catch (_: Exception) {}

                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                            try {
                                mapView.onResume()
                                viewModel.startLiveTrainsPolling()
                                redrawTrigger++
                            } catch (_: Exception) {}
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                            try {
                                mapView.onPause()
                                viewModel.stopLiveTrainsPolling()
                            } catch (_: Exception) {}
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                            try {
                                mapView.onDetach()
                                trainOverlayManager.clear()
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
                        mapView.onDetach()
                        trainOverlayManager.clear()
                        viewModel.stopLiveTrainsPolling()
                    } catch (_: Exception) {}
                }
            }

            // Attach MapListener with 120ms debounce for smooth pan and zoom
            DisposableEffect(mapView) {
                var pendingUpdateRunnable: Runnable? = null
                val debouncedUpdate = {
                    pendingUpdateRunnable?.let { handler.removeCallbacks(it) }
                    val runnable = Runnable {
                        currentZoom = mapView.zoomLevelDouble
                        trainOverlayManager.updateLiveTrains(
                            trains = liveTrains,
                            isCercaniasOnlyFilter = true
                        )
                    }
                    pendingUpdateRunnable = runnable
                    handler.postDelayed(runnable, 120L)
                }

                val listener = object : org.osmdroid.events.MapListener {
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                        debouncedUpdate()
                        return false
                    }
                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                        debouncedUpdate()
                        return false
                    }
                }
                mapView.addMapListener(listener)
                onDispose {
                    pendingUpdateRunnable?.let { handler.removeCallbacks(it) }
                    try {
                        mapView.removeMapListener(listener)
                    } catch (_: Exception) {}
                }
            }

            // Draw Cercanías line track polyline overlays (C-1 to C-6 lines)
            val isCercaniasLoaded by CercaniasMapOverlayLoader.isLoadedState.collectAsState()
            LaunchedEffect(isCercaniasLoaded) {
                CercaniasMapOverlayLoader.ensureLoaded(context)
            }

            LaunchedEffect(isCercaniasLoaded, isDarkMode, redrawTrigger) {
                if (isCercaniasLoaded) {
                    val polylines = CercaniasMapOverlayLoader.getLoadedPolylines(mapView, context)
                    mapView.overlays.removeAll { it is Polyline }
                    mapView.overlays.addAll(0, polylines)
                    trainOverlayManager.bringTrainsToTop()
                    mapView.invalidate()
                }
            }

            val isStaticRendered = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
            val markerLOD = remember(currentZoom) {
                when {
                    currentZoom < 11.5 -> 0 // Tiny dot
                    currentZoom < 13.5 -> 1 // Logo small
                    else -> 2               // Pill
                }
            }
            val showPill = markerLOD >= 2

            // Draw station markers - ONLY when load state, theme, stations, or LOD threshold changes
            LaunchedEffect(isCercaniasLoaded, allStations, isDarkMode, redrawTrigger, markerLOD) {
                if (allStations.isNotEmpty() && isCercaniasLoaded) {
                    val cercaniasPositions = allStations.associate { s ->
                        s.stop_id to com.example.data.repository.StaticTransitDataCache.getVisualPositionForCercanias(
                            s.stop_id,
                            GeoPoint(s.lat, s.lon)
                        )
                    }

                    MetroMarkersRenderer.renderCercaniasMarkers(
                        context = context,
                        mapView = mapView,
                        validCercaniasStations = allStations,
                        cercaniasPositions = cercaniasPositions,
                        currentZoom = currentZoom,
                        isDarkMode = isDarkMode,
                        isOnlyCercaniasSelected = true,
                        showPill = showPill,
                        appLanguage = appLanguage,
                        recycledCercaniasMarkers = recycledCercaniasMarkers,
                        onTapHandler = { _, _, position ->
                            val clickedStation = allStations.find { s ->
                                val pos = cercaniasPositions[s.stop_id] ?: GeoPoint(s.lat, s.lon)
                                pos.latitude == position.latitude && pos.longitude == position.longitude
                            }
                            if (clickedStation != null) {
                                selectedTrainVehicle = null
                                selectedStationEntity = clickedStation
                            }
                            true
                        }
                    )
                    trainOverlayManager.bringTrainsToTop()
                    mapView.invalidate()
                    isStaticRendered.set(true)
                }
            }

            // Update live trains with smooth real-time interpolation animations while dialog is active
            LaunchedEffect(liveTrains, isStaticRendered.get()) {
                if (isStaticRendered.get() && liveTrains.isNotEmpty()) {
                    while (isActive) {
                        trainOverlayManager.updateLiveTrains(
                            trains = liveTrains,
                            isCercaniasOnlyFilter = true
                        )
                        delay(250L) // 4 updates/sec for silky-smooth train movement along track overlays
                    }
                }
            }

            // Bind native osmdroid MapView to lifecycle with empty update block to prevent recomposition overhead
            AndroidView(
                factory = { mapView },
                update = { /* Empty to completely prevent lag-inducing recompositions */ },
                modifier = Modifier.fillMaxSize()
            )

            // Top gradient scrim extending to physical top behind status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                if (isDarkMode) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )

            // Sleek Floating Header Card at Top Start
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) Color(0xEC1E293B) else Color(0xECFFFFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp, end = 76.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                    Column {
                        Text(
                            text = "Trenes en Vivo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color(0xFF1E293B)
                        )
                        Text(
                            text = "Telemetría en tiempo real de Cercanías Valencia",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Close Button in Top Right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
                    .align(Alignment.TopEnd)
                    .testTag("close_live_map_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar Mapa",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Right-side Floating Action Controls & Attribution Bar (Only shown when no bottom sheet is active to prevent collision)
            if (selectedTrainVehicle == null && selectedStationEntity == null) {
                // Right-side Floating Action Controls (Manual Refresh and Recenter GPS)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 44.dp)
                ) {
                    // Manual Refresh Button
                    IconButton(
                        onClick = {
                            viewModel.startLiveTrainsPolling()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = CircleShape
                            )
                            .testTag("refresh_live_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar Trenes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Recenter GPS Button
                    IconButton(
                        onClick = {
                            mapView.controller.animateTo(MapConfig.VALENCIA_CENTER)
                            mapView.controller.setZoom(10.5)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .testTag("recenter_live_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Centrar Valencia",
                            tint = Color.White
                        )
                    }
                }

                // Map Footer Copyright Attribution Bar
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkMode) Color(0xD90F172A) else Color(0xD9F8FAFC),
                    contentColor = if (isDarkMode) Color(0xCCCBD5E1) else Color(0xCC334155),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, bottom = 12.dp)
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
                            text = "osmdroid, © OpenStreetMap contributors, © CARTO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Loading Indicator (Sleek Linear Indicator at the top of the map)
            if (isMapLoading) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .align(Alignment.TopCenter)
                )
            }

            // --- BOTTOM SHEETS DISPLAY FOR SELECTIONS ---

            // 1. Station Selected Bottom Sheet
            selectedStationEntity?.let { station ->
                val isFavorite = favoriteStations.any { it.stop_id == station.stop_id }
                CercaniasStationBottomSheet(
                    station = station,
                    departures = stationDepartures,
                    isLoading = isStationLoading,
                    isDarkMode = isDarkMode,
                    appLanguage = appLanguage,
                    isFavorite = isFavorite,
                    onToggleFavorite = { viewModel.toggleFavoriteCercaniasStation(station.stop_id) },
                    onDismiss = { selectedStationEntity = null },
                    maxExpandedHeight = 440.dp,
                    sheetState = DetailSheetState.HALF_EXPANDED,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 2. Train Selected Bottom Sheet
            selectedTrainVehicle?.let { vehicle ->
                val stationNameMap = remember(allStations) { allStations.associate { it.stop_id to it.nombre } }
                LiveTrainBottomSheet(
                    vehicle = vehicle,
                    stationNameMap = stationNameMap,
                    isDarkMode = isDarkMode,
                    onDismiss = { selectedTrainVehicle = null },
                    sheetState = trainSheetState,
                    onSheetStateChanged = { trainSheetState = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
