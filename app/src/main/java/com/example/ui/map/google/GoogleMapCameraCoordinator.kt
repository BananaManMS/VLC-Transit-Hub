package com.example.ui.map.google

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.example.ui.map.MapConfig
import androidx.compose.ui.unit.Dp
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtRoute
import com.example.ui.bus.MetrobusStop
import com.example.ui.map.MapFilter
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.ValenbisiStation
import com.example.ui.map.components.ViewportTransitFilter
import com.example.ui.map.spatial.BoundingBox2D
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint

data class GoogleMapCameraCoordinator(
    val cameraPositionState: CameraPositionState,
    val settledZoom: Float,
    val handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit
)

internal fun resolveInitialCameraPosition(
    cameraTarget: GeoPoint?,
    cameraZoom: Double
): Pair<LatLng, Float> {
    val latLng = if (cameraTarget != null &&
        cameraTarget.latitude.isFinite() &&
        cameraTarget.longitude.isFinite() &&
        (cameraTarget.latitude != 0.0 || cameraTarget.longitude != 0.0)
    ) {
        LatLng(cameraTarget.latitude, cameraTarget.longitude)
    } else {
        LatLng(MapConfig.DEFAULT_LATITUDE, MapConfig.DEFAULT_LONGITUDE)
    }
    val minZoom = MapConfig.MIN_ZOOM.toFloat()
    val maxZoom = MapConfig.MAX_ZOOM.toFloat()
    val zoom = if (cameraZoom > 0.0 && cameraZoom.isFinite()) {
        cameraZoom.toFloat().coerceIn(minZoom, maxZoom)
    } else {
        15.5f
    }
    return Pair(latLng, zoom)
}

internal fun isValidMapZoom(zoom: Float): Boolean {
    return zoom.isFinite() && zoom >= (MapConfig.MIN_ZOOM.toFloat() - 0.5f)
}

internal fun isValidMapTarget(target: LatLng): Boolean {
    return target.latitude.isFinite() &&
        target.longitude.isFinite() &&
        (target.latitude != 0.0 || target.longitude != 0.0)
}

internal fun shouldSkipRedundantAnimation(
    currentLat: Double,
    currentLon: Double,
    currentZoom: Float,
    targetLat: Double,
    targetLon: Double,
    targetZoom: Float
): Boolean {
    return kotlin.math.abs(currentLat - targetLat) < 1e-5 &&
        kotlin.math.abs(currentLon - targetLon) < 1e-5 &&
        kotlin.math.abs(currentZoom - targetZoom) < 0.05f
}

internal fun shouldSyncFocusAfterProgrammaticAnimation(
    pendingPaddingSync: Boolean,
    isProgrammaticAnimating: Boolean,
    isUserInteracting: Boolean
): Boolean {
    return pendingPaddingSync && !isProgrammaticAnimating && !isUserInteracting
}

internal fun shouldRetargetInFlightCamera(
    isProgrammaticAnimating: Boolean,
    hasActiveFlightTarget: Boolean,
    paddingDeltaDp: Float,
    remainingFlightMs: Long
): Boolean {
    return isProgrammaticAnimating &&
        hasActiveFlightTarget &&
        kotlin.math.abs(paddingDeltaDp) >= 1.5f &&
        remainingFlightMs > 0L
}

private class BottomPaddingSyncRef(
    var initialized: Boolean = false,
    var lastSyncedDp: Dp = Dp.Unspecified
)

/**
 * Manages camera positioning, gestures, animation triggers, zoom settlement,
 * and proximity tap disambiguation for Google Maps.
 */
@Composable
fun rememberGoogleMapCameraCoordinator(
    cameraTarget: GeoPoint?,
    cameraZoom: Double,
    cameraAnimTrigger: Int,
    zoomInTrigger: Int,
    zoomOutTrigger: Int,
    selectedItem: GoogleMapTransitItem?,
    bottomPaddingDp: Dp,
    selectedItinerary: PlannedItinerary?,
    metroStations: List<MetroStation>,
    cercaniasStations: List<CercaniasStationEntity>,
    busStops: List<GeoportalStopEntity>,
    metrobusStops: List<MetrobusStopEntity>,
    valenbisiStations: List<ValenbisiStation>,
    showValenbisi: Boolean,
    mapFilter: MapFilter = MapFilter.DEFAULT,
    isMapLoaded: Boolean = true,
    onCameraPositionChanged: ((GeoPoint, Double) -> Unit)?,
    onZoomLevelChanged: ((Double) -> Unit)?,
    onMapTouch: (() -> Unit)?,
    onMapPan: (() -> Unit)?,
    onItemClick: ((GoogleMapTransitItem) -> Unit)?,
    onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)?
): GoogleMapCameraCoordinator {
    val (initialLatLng, initialZoom) = remember {
        resolveInitialCameraPosition(cameraTarget, cameraZoom)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, initialZoom)
    }

    // Active focal target lat/lng to preserve optical centering during sheet height changes
    var activeFocusLatLng by remember { mutableStateOf<LatLng?>(initialLatLng) }

    // Flag to guard programmatic animations (selection flight, zoom, bounds) against interruption by bottom sheet padding moves
    var isProgrammaticAnimating by remember { mutableStateOf(false) }

    // Track user drag/pan interaction so sheet collapses triggered by onMapPan don't fight the user's finger
    var isUserInteracting by remember { mutableStateOf(false) }

    // Remember if bottomPaddingDp changed while a programmatic animation was running so we settle cleanly once it ends
    var pendingPaddingSync by remember { mutableStateOf(false) }

    // Latest references for callbacks & datasets used inside stable lambdas/effects
    val latestOnCameraChanged by rememberUpdatedState(onCameraPositionChanged)
    val latestOnZoomChanged by rememberUpdatedState(onZoomLevelChanged)
    val latestOnMapTouch by rememberUpdatedState(onMapTouch)
    val latestOnMapPan by rememberUpdatedState(onMapPan)
    val latestOnItemClick by rememberUpdatedState(onItemClick)
    val latestOnShowDisambiguation by rememberUpdatedState(onShowDisambiguationMenu)
    val latestMapFilter by rememberUpdatedState(mapFilter)
    val latestShowValenbisi by rememberUpdatedState(showValenbisi)
    val latestMetroStations by rememberUpdatedState(metroStations)
    val latestCercaniasStations by rememberUpdatedState(cercaniasStations)
    val latestBusStops by rememberUpdatedState(busStops)
    val latestMetrobusStops by rememberUpdatedState(metrobusStops)
    val latestValenbisiStations by rememberUpdatedState(valenbisiStations)

    val latestIsMapLoaded by rememberUpdatedState(isMapLoaded)

    // Settled zoom state: updates ONLY when the zoom/camera gesture finishes (!isMoving) with valid zoom
    var settledZoom by remember { mutableFloatStateOf(initialZoom) }

    // 1. Report camera position and zoom changes (throttled during gestures, instant on settle)
    LaunchedEffect(cameraPositionState) {
        var lastReportTime = 0L
        snapshotFlow {
            Triple(
                cameraPositionState.position.target,
                cameraPositionState.position.zoom,
                cameraPositionState.isMoving
            )
        }.collect { (target, zoom, isMoving) ->
            if (!isValidMapZoom(zoom) || !isValidMapTarget(target)) {
                return@collect
            }
            val now = System.currentTimeMillis()
            if (!isMoving || (now - lastReportTime >= 180L)) {
                lastReportTime = now
                val geoPoint = GeoPoint(target.latitude, target.longitude)
                val zoomVal = zoom.toDouble()
                latestOnCameraChanged?.invoke(geoPoint, zoomVal)
                latestOnZoomChanged?.invoke(zoomVal)
            }
        }
    }

    // 2. Keep active focal target synced with selectedItem without clobbering it with mid-transition setPadding targets on dismiss
    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            activeFocusLatLng = LatLng(selectedItem.lat, selectedItem.lon)
        } else if (activeFocusLatLng == null) {
            val currentTarget = cameraPositionState.position.target
            if (isValidMapTarget(currentTarget)) {
                activeFocusLatLng = currentTarget
            }
        }
    }

    // 3. Detect user manual gesture interactions for touches/pans
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { Pair(cameraPositionState.isMoving, cameraPositionState.cameraMoveStartedReason) }
            .collect { (isMoving, reason) ->
                if (isMoving && reason == CameraMoveStartedReason.GESTURE) {
                    isUserInteracting = true
                    isProgrammaticAnimating = false
                    pendingPaddingSync = false
                    latestOnMapTouch?.invoke()
                    latestOnMapPan?.invoke()
                } else if (!isMoving && (isUserInteracting || reason == CameraMoveStartedReason.GESTURE)) {
                    isUserInteracting = false
                    val currentTarget = cameraPositionState.position.target
                    if (isValidMapTarget(currentTarget)) {
                        activeFocusLatLng = currentTarget
                    }
                }
            }
    }

    // 4. External camera animations from MapScreen (station selection flight, recentering, search)
    // Supports single-curve mid-flight retargeting when sheet height updates dynamically during launch.
    var lastHandledAnimTrigger by remember { mutableIntStateOf(cameraAnimTrigger) }
    var activeFlightTarget by remember { mutableStateOf<LatLng?>(null) }
    var activeFlightZoom by remember { mutableFloatStateOf(initialZoom) }
    var activeFlightDurationMs by remember { mutableIntStateOf(500) }
    var activeFlightDeadlineUptimeMs by remember { mutableLongStateOf(0L) }
    var flightRevision by remember { mutableIntStateOf(0) }
    val paddingSyncRef = remember { BottomPaddingSyncRef() }

    LaunchedEffect(cameraAnimTrigger) {
        if (cameraAnimTrigger <= lastHandledAnimTrigger || cameraTarget == null) {
            return@LaunchedEffect
        }
        lastHandledAnimTrigger = cameraAnimTrigger
        val target = LatLng(cameraTarget.latitude, cameraTarget.longitude)
        activeFocusLatLng = target
        val targetZoom = (if (cameraZoom > 0.0 && cameraZoom.isFinite()) cameraZoom.toFloat() else 17.0f)
            .coerceIn(MapConfig.MIN_ZOOM.toFloat(), MapConfig.MAX_ZOOM.toFloat())

        val curPos = cameraPositionState.position
        val paddingAlreadySynced = paddingSyncRef.initialized &&
            kotlin.math.abs(bottomPaddingDp.value - paddingSyncRef.lastSyncedDp.value) < 0.5f
        if (paddingAlreadySynced && shouldSkipRedundantAnimation(
                currentLat = curPos.target.latitude,
                currentLon = curPos.target.longitude,
                currentZoom = curPos.zoom,
                targetLat = target.latitude,
                targetLon = target.longitude,
                targetZoom = targetZoom
            )
        ) {
            return@LaunchedEffect
        }

        paddingSyncRef.initialized = true
        paddingSyncRef.lastSyncedDp = bottomPaddingDp

        if (!latestIsMapLoaded) {
            settledZoom = targetZoom
            try {
                cameraPositionState.move(
                    update = CameraUpdateFactory.newLatLngZoom(target, targetZoom)
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("GoogleMapCameraCoord", "Pre-load camera snap failed", e)
            }
            return@LaunchedEffect
        }

        activeFlightTarget = target
        activeFlightZoom = targetZoom
        activeFlightDurationMs = 500
        activeFlightDeadlineUptimeMs = android.os.SystemClock.uptimeMillis() + 500L
        flightRevision++
    }

    LaunchedEffect(flightRevision) {
        val target = activeFlightTarget ?: return@LaunchedEffect
        val targetZoom = activeFlightZoom
        val durationMs = activeFlightDurationMs
        isProgrammaticAnimating = true
        var cancelled = false
        try {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target, targetZoom),
                durationMs = durationMs
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
            Log.w("GoogleMapCameraCoord", "Camera animation interrupted", e)
        } finally {
            if (!cancelled) {
                activeFlightTarget = null
                isProgrammaticAnimating = false
            }
        }
    }

    // 5. Zero-lag synchronous 1:1 camera tracking during bottom sheet drag & height animations.
    // Executes inside SideEffect right after MapUpdater applies setPadding in the same frame (no coroutine lag).
    val hasPendingCameraFlight = cameraAnimTrigger > lastHandledAnimTrigger
    SideEffect {
        if (!paddingSyncRef.initialized) {
            paddingSyncRef.initialized = true
            paddingSyncRef.lastSyncedDp = bottomPaddingDp
            return@SideEffect
        }
        val deltaDp = kotlin.math.abs(bottomPaddingDp.value - paddingSyncRef.lastSyncedDp.value)
        if (deltaDp < 0.05f) {
            return@SideEffect
        }
        if (hasPendingCameraFlight || isProgrammaticAnimating || isUserInteracting) {
            return@SideEffect
        }
        paddingSyncRef.lastSyncedDp = bottomPaddingDp
        val focus = activeFocusLatLng
        if (focus != null && isValidMapTarget(focus)) {
            val currentZoom = cameraPositionState.position.zoom
            val safeZoom = if (isValidMapZoom(currentZoom)) currentZoom else initialZoom
            try {
                cameraPositionState.move(
                    update = CameraUpdateFactory.newLatLngZoom(focus, safeZoom)
                )
            } catch (_: Exception) {
                // Ignore if native GoogleMap is not attached yet
            }
        }
    }

    // 5b. Retarget active flight immediately if sheet height changes mid-flight (or queue post-flight settle for zoom/bounds)
    LaunchedEffect(bottomPaddingDp) {
        if (!paddingSyncRef.initialized) return@LaunchedEffect
        val deltaDp = bottomPaddingDp.value - paddingSyncRef.lastSyncedDp.value
        if (isProgrammaticAnimating) {
            val remainingMs = activeFlightDeadlineUptimeMs - android.os.SystemClock.uptimeMillis()
            if (shouldRetargetInFlightCamera(
                    isProgrammaticAnimating = true,
                    hasActiveFlightTarget = activeFlightTarget != null,
                    paddingDeltaDp = deltaDp,
                    remainingFlightMs = remainingMs
                )
            ) {
                paddingSyncRef.lastSyncedDp = bottomPaddingDp
                activeFlightDurationMs = remainingMs.coerceIn(180L, 480L).toInt()
                flightRevision++
            } else if (activeFlightTarget == null && kotlin.math.abs(deltaDp) >= 0.5f) {
                pendingPaddingSync = true
            }
        }
    }

    LaunchedEffect(isProgrammaticAnimating, isUserInteracting) {
        if (shouldSyncFocusAfterProgrammaticAnimation(pendingPaddingSync, isProgrammaticAnimating, isUserInteracting)) {
            pendingPaddingSync = false
            paddingSyncRef.lastSyncedDp = bottomPaddingDp
            val focus = activeFocusLatLng
            if (focus != null && isValidMapTarget(focus)) {
                val currentZoom = cameraPositionState.position.zoom
                val safeZoom = if (isValidMapZoom(currentZoom)) currentZoom else initialZoom
                try {
                    if (latestIsMapLoaded) {
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(focus, safeZoom),
                            durationMs = 150
                        )
                    } else {
                        cameraPositionState.move(
                            update = CameraUpdateFactory.newLatLngZoom(focus, safeZoom)
                        )
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("GoogleMapCameraCoord", "Post-flight padding settle interrupted", e)
                }
            }
        }
    }

    // 6. External Zoom In / Out triggers
    LaunchedEffect(zoomInTrigger) {
        if (zoomInTrigger > 0) {
            isProgrammaticAnimating = true
            try {
                cameraPositionState.animate(CameraUpdateFactory.zoomIn(), 250)
                delay(100L)
            } finally {
                isProgrammaticAnimating = false
            }
        }
    }

    LaunchedEffect(zoomOutTrigger) {
        if (zoomOutTrigger > 0) {
            isProgrammaticAnimating = true
            try {
                cameraPositionState.animate(CameraUpdateFactory.zoomOut(), 250)
                delay(100L)
            } finally {
                isProgrammaticAnimating = false
            }
        }
    }

    // 7. Auto-fit camera to route bounding box when an itinerary is loaded or changed
    var lastZoomedItineraryId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedItinerary?.id) {
        val itinerary = selectedItinerary
        if (itinerary != null && itinerary.id != lastZoomedItineraryId) {
            lastZoomedItineraryId = itinerary.id
            val allPoints = mutableListOf<LatLng>()
            itinerary.legs.forEach { leg ->
                if (leg.geometry.isNotEmpty()) {
                    leg.geometry.forEach { allPoints.add(LatLng(it.latitude, it.longitude)) }
                } else if (leg.intermediateStops.isNotEmpty()) {
                    leg.intermediateStops.forEach { allPoints.add(LatLng(it.lat, it.lon)) }
                } else if (leg.fromLat != 0.0 && leg.toLat != 0.0) {
                    allPoints.add(LatLng(leg.fromLat, leg.fromLon))
                    allPoints.add(LatLng(leg.toLat, leg.toLon))
                }
            }
            val valid = allPoints.filter { it.latitude in 38.0..41.0 && it.longitude in -2.0..1.0 }
            if (valid.isNotEmpty()) {
                val minLat = valid.minOf { it.latitude }
                val maxLat = valid.maxOf { it.latitude }
                val minLon = valid.minOf { it.longitude }
                val maxLon = valid.maxOf { it.longitude }
                if (maxLat - minLat > 0.0001 && maxLon - minLon > 0.0001) {
                    val bounds = LatLngBounds(LatLng(minLat - 0.002, minLon - 0.002), LatLng(maxLat + 0.002, maxLon + 0.002))
                    try {
                        isProgrammaticAnimating = true
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(bounds, 90),
                            durationMs = 500
                        )
                        delay(100L)
                        val settledCenter = cameraPositionState.position.target
                        if (isValidMapTarget(settledCenter)) {
                            activeFocusLatLng = settledCenter
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w("GoogleMapCameraCoord", "Could not animate to itinerary bounds", e)
                    } finally {
                        isProgrammaticAnimating = false
                    }
                }
            }
        }
    }

    // Keep settledZoom synchronized when gestures stop or valid external zoom changes occur
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { Pair(cameraPositionState.isMoving, cameraPositionState.position.zoom) }
            .collect { (isMoving, zoom) ->
                if (!isMoving && isValidMapZoom(zoom)) {
                    settledZoom = zoom
                }
            }
    }
    LaunchedEffect(cameraZoom) {
        val candidate = cameraZoom.toFloat()
        if (!cameraPositionState.isMoving && isValidMapZoom(candidate)) {
            settledZoom = candidate
        }
    }

    // Proximity tap disambiguation handler using cached O(log N) QuadTree range queries
    val handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit = remember(cameraPositionState) {
        { item, lat, lon ->
            activeFocusLatLng = LatLng(lat, lon)
            val disambigCallback = latestOnShowDisambiguation
            if (disambigCallback != null) {
                val curFilter = latestMapFilter
                val coincidentItems = ArrayList<SelectedMapItem>(4)
                val radius = 0.00035
                val thresholdSq = radius * radius
                val searchBox = BoundingBox2D(lat - radius, lat + radius, lon - radius, lon + radius)

                if (curFilter.isFavorites || curFilter.showMetro) {
                    val metros = latestMetroStations
                    for (i in 0 until metros.size) {
                        val s = metros[i]
                        val dLat = s.latitude - lat
                        val dLon = s.longitude - lon
                        if (dLat * dLat + dLon * dLon < thresholdSq) {
                            coincidentItems.add(SelectedMapItem.Metro(s))
                        }
                    }
                }
                if (curFilter.isFavorites || curFilter.showCercanias) {
                    val cercs = latestCercaniasStations
                    for (i in 0 until cercs.size) {
                        val s = cercs[i]
                        val dLat = s.lat - lat
                        val dLon = s.lon - lon
                        if (dLat * dLat + dLon * dLon < thresholdSq) {
                            coincidentItems.add(SelectedMapItem.Cercanias(s))
                        }
                    }
                }
                if (curFilter.isFavorites || curFilter.showBus) {
                    val busCandidates = ViewportTransitFilter.queryBusStopsInBox(latestBusStops, searchBox)
                    for (i in 0 until busCandidates.size) {
                        val s = busCandidates[i]
                        val dLat = s.lat - lat
                        val dLon = s.lon - lon
                        if (dLat * dLat + dLon * dLon < thresholdSq) {
                            val linesList = (s.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                .map { EmtRoute(id_linea = it, SN = it) }
                            val emtModel = EmtBusStop(t = s.denominacion, n = s.denominacion, me = s.denominacion, utes = linesList, opId = s.id_parada, ica = s.id_parada)
                            coincidentItems.add(SelectedMapItem.BusStop(s, emtModel))
                        }
                    }
                }
                if (curFilter.isFavorites || curFilter.showMetrobus) {
                    val mbCandidates = ViewportTransitFilter.queryMetrobusStopsInBox(latestMetrobusStops, searchBox)
                    for (i in 0 until mbCandidates.size) {
                        val s = mbCandidates[i]
                        val dLat = s.latitud - lat
                        val dLon = s.longitud - lon
                        if (dLat * dLat + dLon * dLon < thresholdSq) {
                            val linesList = (s.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val mbModel = MetrobusStop(idParada = s.id, denominacion = s.denominacion, lat = s.latitud, lon = s.longitud, lineas = linesList)
                            coincidentItems.add(SelectedMapItem.MetrobusStopItem(s, mbModel))
                        }
                    }
                }
                if (latestShowValenbisi && (curFilter.isFavorites || curFilter.showValenbisi)) {
                    val valenCandidates = ViewportTransitFilter.queryValenbisiInBox(latestValenbisiStations, searchBox)
                    for (i in 0 until valenCandidates.size) {
                        val s = valenCandidates[i]
                        val dLat = s.latitude - lat
                        val dLon = s.longitude - lon
                        if (dLat * dLat + dLon * dLon < thresholdSq) {
                            coincidentItems.add(SelectedMapItem.Valenbisi(s))
                        }
                    }
                }
                if (coincidentItems.size >= 2) {
                    disambigCallback(coincidentItems)
                } else {
                    latestOnItemClick?.invoke(item)
                }
            } else {
                latestOnItemClick?.invoke(item)
            }
        }
    }

    return remember(cameraPositionState, settledZoom, handleTransitItemTap) {
        GoogleMapCameraCoordinator(
            cameraPositionState = cameraPositionState,
            settledZoom = settledZoom,
            handleTransitItemTap = handleTransitItemTap
        )
    }
}
