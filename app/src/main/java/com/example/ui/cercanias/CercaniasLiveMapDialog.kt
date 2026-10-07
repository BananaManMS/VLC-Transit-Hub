package com.example.ui.cercanias

import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
    bottomPadding: Dp = 0.dp,
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
        var displayedStationEntity by remember { mutableStateOf<CercaniasStationEntity?>(null) }
        var stationSheetState by remember { mutableStateOf(DetailSheetState.HALF_EXPANDED) }
        var stationDepartures by remember { mutableStateOf<List<CercaniasDeparture>>(emptyList()) }
        var isStationLoading by remember { mutableStateOf(false) }
        
        var selectedTrainVehicle by remember { mutableStateOf<LiveVehicleInfo?>(null) }
        var displayedTrainVehicle by remember { mutableStateOf<LiveVehicleInfo?>(null) }
        var trainSheetState by remember { mutableStateOf(DetailSheetState.HALF_EXPANDED) }

        LaunchedEffect(selectedStationEntity) {
            if (selectedStationEntity != null) {
                displayedStationEntity = selectedStationEntity
                stationSheetState = DetailSheetState.HALF_EXPANDED
            }
        }

        LaunchedEffect(selectedTrainVehicle) {
            if (selectedTrainVehicle != null) {
                displayedTrainVehicle = selectedTrainVehicle
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                .testTag("cercanias_live_map_dialog")
        ) {
        BackHandler {
            if (selectedStationEntity != null) {
                selectedStationEntity = null
            } else if (selectedTrainVehicle != null) {
                selectedTrainVehicle = null
            } else {
                onDismiss()
            }
        }

            // Default center showing almost the entire Cercanías network, ignoring user GPS for initial view
            val initialCenter = MapConfig.VALENCIA_CENTER
            val initialZoom = 10.2

            // Keep persistent lists of recycled markers for the lifecycle of this Dialog to avoid memory leaks
            val recycledCercaniasMarkers = remember { mutableListOf<Marker>() }
            var currentZoom by remember { mutableStateOf(initialZoom) }
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
                    
                    controller.setZoom(initialZoom)
                    controller.setCenter(initialCenter)

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

            var stationsList by remember { mutableStateOf(allStations) }
            LaunchedEffect(allStations) {
                if (allStations.isNotEmpty()) {
                    stationsList = allStations
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val loaded = renfeRepository.getAllStations()
                        if (loaded.isNotEmpty()) {
                            stationsList = loaded
                        }
                    }
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
            LaunchedEffect(isCercaniasLoaded, stationsList, isDarkMode, redrawTrigger, markerLOD) {
                if (stationsList.isNotEmpty() && isCercaniasLoaded) {
                    val cercaniasPositions = stationsList.associate { s ->
                        s.stop_id to com.example.data.repository.StaticTransitDataCache.getVisualPositionForCercanias(
                            s.stop_id,
                            GeoPoint(s.lat, s.lon)
                        )
                    }

                    MetroMarkersRenderer.renderCercaniasMarkers(
                        context = context,
                        mapView = mapView,
                        validCercaniasStations = stationsList,
                        cercaniasPositions = cercaniasPositions,
                        currentZoom = currentZoom,
                        isDarkMode = isDarkMode,
                        isOnlyCercaniasSelected = true,
                        showPill = showPill,
                        appLanguage = appLanguage,
                        recycledCercaniasMarkers = recycledCercaniasMarkers,
                        onTapHandler = { _, _, position ->
                            val clickedStation = stationsList.find { s ->
                                val pos = cercaniasPositions[s.stop_id] ?: GeoPoint(s.lat, s.lon)
                                Math.abs(pos.latitude - position.latitude) < 0.0001 && Math.abs(pos.longitude - position.longitude) < 0.0001
                            }
                            if (clickedStation != null) {
                                selectedTrainVehicle = null
                                selectedStationEntity = clickedStation
                            }
                            true
                        }
                    )

                    // Add all rendered Cercanías station markers to osmdroid mapView overlays
                    recycledCercaniasMarkers.forEach { marker ->
                        if (!mapView.overlays.contains(marker)) {
                            mapView.overlays.add(marker)
                        }
                    }

                    trainOverlayManager.bringTrainsToTop()
                    mapView.invalidate()
                    isStaticRendered.set(true)
                }
            }

            // Update live trains with smooth real-time interpolation animations only while app is in foreground
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(liveTrains, isStaticRendered.get()) {
                if (isStaticRendered.get() && liveTrains.isNotEmpty()) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            trainOverlayManager.updateLiveTrains(
                                trains = liveTrains,
                                isCercaniasOnlyFilter = true
                            )
                            delay(250L) // 4 updates/sec for silky-smooth train movement along track overlays
                        }
                    }
                }
            }

            // Bind native osmdroid MapView to lifecycle with empty update block to prevent recomposition overhead
            AndroidView(
                factory = { mapView },
                update = { /* Empty to completely prevent lag-inducing recompositions */ },
                modifier = Modifier.fillMaxSize()
            )

            // Top status-bar gradient scrim matching MapScreen.kt EXACTLY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isDarkMode) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )

            // Right-side Floating Action Controls & Attribution Bar (Only shown when no bottom sheet is active)
            if (selectedTrainVehicle == null && selectedStationEntity == null) {
                // Right-side Floating Action Controls (Manual Refresh and Recenter GPS)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = bottomPadding + 52.dp)
                ) {
                    // Manual Refresh Button
                    IconButton(
                        onClick = {
                            viewModel.startLiveTrainsPolling()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = CircleShape
                            )
                            .testTag("refresh_live_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_reload_trains),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Recenter GPS Button
                    IconButton(
                        onClick = {
                            val loc = viewModel.lastLocation.value
                            if (loc != null) {
                                mapView.controller.animateTo(GeoPoint(loc.first, loc.second))
                                mapView.controller.setZoom(14.5)
                            } else {
                                mapView.controller.animateTo(MapConfig.VALENCIA_CENTER)
                                mapView.controller.setZoom(10.5)
                            }
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
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_center_valencia),
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
                        .padding(start = 16.dp, bottom = bottomPadding + 40.dp)
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

            // Bottom Bar / Franja inferior elevated safely above bottom navigation bar
            val barBg = if (isDarkMode) Color(0xFF171717) else Color.White
            val barBorder = if (isDarkMode) Color(0xFF262C38) else Color(0xFFE2E8F0)
            val textPrimary = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)

            AnimatedVisibility(
                visible = selectedTrainVehicle == null && selectedStationEntity == null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(180)),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = barBg,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, barBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Back button on the left
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkMode) Color(0xFF262C38) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF333E50) else Color(0xFFE2E8F0)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("cercanias_live_map_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.btn_back),
                                    tint = textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 2. Logo + Title + Subtitle in center
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDarkMode) Color(0xFF262C38) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF333E50) else Color(0xFFE2E8F0)),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = com.example.R.drawable.logo_cercanias),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_live_map_title),
                                    fontFamily = com.example.ui.theme.SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (liveTrains.isNotEmpty()) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_live_trains_count, liveTrains.size),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = Color(0xFF10B981),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (!isMapLoading) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_live_map_no_gps),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Loading Card Overlay matching Network Plans style
            AnimatedVisibility(
                visible = isMapLoading,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .testTag("cercanias_live_map_loading_card")
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF222222) else Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Cargando mapa en vivo de Cercanías...",
                                    fontFamily = com.example.ui.theme.SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // --- BOTTOM SHEETS DISPLAY FOR SELECTIONS ---

            // 1. Station Selected Bottom Sheet (Animated entrance and exit)
            AnimatedVisibility(
                visible = selectedStationEntity != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(animationSpec = tween(180)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                displayedStationEntity?.let { station ->
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
                        sheetState = stationSheetState,
                        onSheetStateChanged = { stationSheetState = it },
                        dismissOnCollapse = true,
                        activeTripBottomPadding = bottomPadding + 20.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // 2. Train Selected Bottom Sheet (Animated entrance and exit)
            AnimatedVisibility(
                visible = selectedTrainVehicle != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(animationSpec = tween(180)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                displayedTrainVehicle?.let { vehicle ->
                    val stationNameMap = remember(allStations) { allStations.associate { it.stop_id to it.nombre } }
                    LiveTrainBottomSheet(
                        vehicle = vehicle,
                        stationNameMap = stationNameMap,
                        isDarkMode = isDarkMode,
                        onDismiss = { selectedTrainVehicle = null },
                        sheetState = trainSheetState,
                        onSheetStateChanged = { trainSheetState = it },
                        bottomPadding = bottomPadding + 20.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
