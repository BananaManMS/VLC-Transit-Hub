package com.example.ui.map.google

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
    onCameraPositionChanged: ((GeoPoint, Double) -> Unit)?,
    onZoomLevelChanged: ((Double) -> Unit)?,
    onMapTouch: (() -> Unit)?,
    onMapPan: (() -> Unit)?,
    onItemClick: ((GoogleMapTransitItem) -> Unit)?,
    onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)?
): GoogleMapCameraCoordinator {
    val initialLatLng = remember {
        if (cameraTarget != null) LatLng(cameraTarget.latitude, cameraTarget.longitude) else LatLng(39.4699, -0.3763)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, cameraZoom.toFloat())
    }

    // Active focal target lat/lng to preserve optical centering during sheet height changes
    var activeFocusLatLng by remember { mutableStateOf<LatLng?>(initialLatLng) }

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
            val now = System.currentTimeMillis()
            if (!isMoving || (now - lastReportTime >= 180L)) {
                lastReportTime = now
                val geoPoint = GeoPoint(target.latitude, target.longitude)
                val zoomVal = zoom.toDouble()
                onCameraPositionChanged?.invoke(geoPoint, zoomVal)
                onZoomLevelChanged?.invoke(zoomVal)
            }
        }
    }

    // 2. Animate camera ONCE when a new selectedItem is tapped
    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            val target = LatLng(selectedItem.lat, selectedItem.lon)
            activeFocusLatLng = target
            val currentZoom = cameraPositionState.position.zoom
            val targetZoom = if (currentZoom < 17.0f) 17.0f else currentZoom
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target, targetZoom),
                durationMs = 250
            )
        } else {
            activeFocusLatLng = cameraPositionState.position.target
        }
    }

    // 3. Detect user manual gesture interactions for touches/pans
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { Pair(cameraPositionState.isMoving, cameraPositionState.cameraMoveStartedReason) }
            .collect { (isMoving, reason) ->
                if (isMoving && reason == CameraMoveStartedReason.GESTURE) {
                    onMapTouch?.invoke()
                    onMapPan?.invoke()
                } else if (!isMoving && reason == CameraMoveStartedReason.GESTURE) {
                    activeFocusLatLng = cameraPositionState.position.target
                }
            }
    }

    // 4. External camera animations from MapScreen (recentering / location / search triggers)
    LaunchedEffect(cameraAnimTrigger) {
        if (cameraAnimTrigger > 0 && cameraTarget != null) {
            val target = LatLng(cameraTarget.latitude, cameraTarget.longitude)
            activeFocusLatLng = target
            val targetZoom = if (cameraZoom > 0.0) cameraZoom.toFloat() else 15.5f
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target, targetZoom),
                durationMs = 150
            )
        }
    }

    // 5. Instant 1:1 camera tracking of active focal target when bottom sheet height changes in real-time
    LaunchedEffect(bottomPaddingDp) {
        val focus = activeFocusLatLng
        if (focus != null) {
            val currentZoom = cameraPositionState.position.zoom
            cameraPositionState.move(
                update = CameraUpdateFactory.newLatLngZoom(focus, currentZoom)
            )
        }
    }

    // 5. External Zoom In / Out triggers
    LaunchedEffect(zoomInTrigger) {
        if (zoomInTrigger > 0) {
            cameraPositionState.animate(CameraUpdateFactory.zoomIn(), 250)
        }
    }

    LaunchedEffect(zoomOutTrigger) {
        if (zoomOutTrigger > 0) {
            cameraPositionState.animate(CameraUpdateFactory.zoomOut(), 250)
        }
    }

    // 6. Auto-fit camera to route bounding box when an itinerary is loaded or changed
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
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(bounds, 90),
                            durationMs = 500
                        )
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w("GoogleMapCameraCoord", "Could not animate to itinerary bounds", e)
                    }
                }
            }
        }
    }

    // Settled zoom state: updates ONLY when the zoom/camera gesture finishes (!isMoving)
    var settledZoom by remember { mutableFloatStateOf(cameraZoom.toFloat()) }
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { Pair(cameraPositionState.isMoving, cameraPositionState.position.zoom) }
            .collect { (isMoving, zoom) ->
                if (!isMoving) {
                    settledZoom = zoom
                }
            }
    }
    LaunchedEffect(cameraZoom) {
        if (!cameraPositionState.isMoving) {
            settledZoom = cameraZoom.toFloat()
        }
    }

    // Proximity tap disambiguation handler (matches OSMdroid DisambiguationDialog)
    val handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit = { item, lat, lon ->
        activeFocusLatLng = LatLng(lat, lon)
        if (onShowDisambiguationMenu != null) {
            val coincidentItems = mutableListOf<SelectedMapItem>()
            val thresholdSq = 0.00035 * 0.00035
            if (mapFilter.isFavorites || mapFilter.showMetro) {
                metroStations.forEach { s ->
                    val dLat = s.latitude - lat
                    val dLon = s.longitude - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) {
                        coincidentItems.add(SelectedMapItem.Metro(s))
                    }
                }
            }
            if (mapFilter.isFavorites || mapFilter.showCercanias) {
                cercaniasStations.forEach { s ->
                    val dLat = s.lat - lat
                    val dLon = s.lon - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) {
                        coincidentItems.add(SelectedMapItem.Cercanias(s))
                    }
                }
            }
            if (mapFilter.isFavorites || mapFilter.showBus) {
                busStops.forEach { s ->
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
            if (mapFilter.isFavorites || mapFilter.showMetrobus) {
                metrobusStops.forEach { s ->
                    val dLat = s.latitud - lat
                    val dLon = s.longitud - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) {
                        val linesList = (s.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val mbModel = MetrobusStop(idParada = s.id, denominacion = s.denominacion, lat = s.latitud, lon = s.longitud, lineas = linesList)
                        coincidentItems.add(SelectedMapItem.MetrobusStopItem(s, mbModel))
                    }
                }
            }
            if (showValenbisi && (mapFilter.isFavorites || mapFilter.showValenbisi)) {
                valenbisiStations.forEach { s ->
                    val dLat = s.latitude - lat
                    val dLon = s.longitude - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) {
                        coincidentItems.add(SelectedMapItem.Valenbisi(s))
                    }
                }
            }
            if (coincidentItems.size >= 2) {
                onShowDisambiguationMenu(coincidentItems)
            } else {
                onItemClick?.invoke(item)
            }
        } else {
            onItemClick?.invoke(item)
        }
    }

    return remember(cameraPositionState, settledZoom) {
        GoogleMapCameraCoordinator(
            cameraPositionState = cameraPositionState,
            settledZoom = settledZoom,
            handleTransitItemTap = handleTransitItemTap
        )
    }
}
