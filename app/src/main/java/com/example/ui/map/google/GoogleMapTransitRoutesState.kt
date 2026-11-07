package com.example.ui.map.google

import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.BusRouteDirectionManager
import com.example.ui.map.components.CercaniasMapOverlayLoader
import com.example.ui.map.components.EmtMapOverlayLoader
import com.example.ui.map.components.MetroMapOverlayLoader
import com.example.ui.map.components.MetrobusStationHighlightManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

data class GoogleMapTransitRoutesState(
    val metroPolylineList: List<GoogleMapPolyline> = emptyList(),
    val cercaniasPolylineList: List<GoogleMapPolyline> = emptyList(),
    val busPolylineList: List<GoogleMapPolyline> = emptyList(),
    val metrobusPolylineList: List<GoogleMapPolyline> = emptyList(),
    val routeChevronArrows: List<RouteChevronArrow> = emptyList(),
    val isLinesLoading: Boolean = false,
    val activeBusTarget: Pair<Set<String>, GeoPoint?> = Pair(emptySet(), null),
    val activeIsolatedBusLines: Set<String> = emptySet(),
    val isBusActive: Boolean = false
)

/**
 * Manages loading and caching of track and route polylines for Metro, Cercanías, EMT Bus,
 * and Metrobús, as well as directional chevron arrows for isolated bus routes.
 */
@Composable
fun rememberGoogleMapTransitRoutes(
    context: Context,
    showMetroLines: Boolean,
    showCercaniasLines: Boolean,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    settledZoom: Float,
    selectedItem: GoogleMapTransitItem?,
    selectedMapItem: SelectedMapItem?,
    selectedBusLineFilters: Set<String>,
    selectedDirectionFilter: String?,
    selectedMetrobusShapes: Map<String, List<GeoPoint>>
): GoogleMapTransitRoutesState {
    var metroPolylineList by remember(showMetroLines) { mutableStateOf<List<GoogleMapPolyline>>(emptyList()) }
    var cercaniasPolylineList by remember(showCercaniasLines) { mutableStateOf<List<GoogleMapPolyline>>(emptyList()) }
    var busPolylineList by remember { mutableStateOf<List<GoogleMapPolyline>>(emptyList()) }
    var metrobusPolylineList by remember { mutableStateOf<List<GoogleMapPolyline>>(emptyList()) }
    var isLinesLoading by remember { mutableStateOf(!MetroMapOverlayLoader.isLoaded && showMetroLines) }

    // Adaptive zoom categories for parallel track offsets (driven live by cameraPositionState.position.zoom)
    val currentMetroZoomCategory by remember(cameraPositionState) {
        derivedStateOf {
            val zoom = cameraPositionState.position.zoom
            when {
                zoom < 13.5f -> MetroMapOverlayLoader.ZoomCategory.FAR
                zoom < 16.0f -> MetroMapOverlayLoader.ZoomCategory.MEDIUM
                else -> MetroMapOverlayLoader.ZoomCategory.CLOSE
            }
        }
    }

    val currentCercaniasZoomCategory by remember(cameraPositionState) {
        derivedStateOf {
            val zoom = cameraPositionState.position.zoom
            when {
                zoom < 13.5f -> MetroMapOverlayLoader.ZoomCategory.FAR
                zoom < 16.0f -> MetroMapOverlayLoader.ZoomCategory.MEDIUM
                else -> MetroMapOverlayLoader.ZoomCategory.CLOSE
            }
        }
    }

    // 1. Asynchronously load Metro Polylines
    LaunchedEffect(showMetroLines, currentMetroZoomCategory) {
        if (showMetroLines) {
            withContext(Dispatchers.IO) {
                try {
                    MetroMapOverlayLoader.ensureLoaded(context)
                    if (!MetroMapOverlayLoader.isLoaded) {
                        MetroMapOverlayLoader.isLoadedState.first { it }
                    }
                    val sets = MetroMapOverlayLoader.getPolylineSets()
                    val rawSets = when (currentMetroZoomCategory) {
                        MetroMapOverlayLoader.ZoomCategory.FAR -> sets.highResFar.ifEmpty { sets.lowResFar }
                        MetroMapOverlayLoader.ZoomCategory.MEDIUM -> sets.highResMedium.ifEmpty { sets.lowResMedium }
                        MetroMapOverlayLoader.ZoomCategory.CLOSE -> sets.highResClose.ifEmpty { sets.lowResClose }
                    }
                    val converted = rawSets.map { raw ->
                        GoogleMapPolyline(
                            points = raw.points.map { LatLng(it.latitude, it.longitude) },
                            color = Color(raw.color),
                            width = raw.strokeWidth,
                            lineRef = raw.lineRef
                        )
                    }
                    withContext(Dispatchers.Main) {
                        metroPolylineList = converted
                        isLinesLoading = false
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("GoogleMapTransitRoutes", "Error loading metro polylines", e)
                    withContext(Dispatchers.Main) { isLinesLoading = false }
                }
            }
        } else {
            metroPolylineList = emptyList()
        }
    }

    // 2. Asynchronously load Cercanías Polylines
    LaunchedEffect(showCercaniasLines, currentCercaniasZoomCategory) {
        if (showCercaniasLines) {
            withContext(Dispatchers.IO) {
                try {
                    CercaniasMapOverlayLoader.ensureLoaded(context)
                    if (!CercaniasMapOverlayLoader.isLoaded) {
                        CercaniasMapOverlayLoader.isLoadedState.first { it }
                    }
                    val sets = CercaniasMapOverlayLoader.getPolylineSets()
                    val rawSets = when (currentCercaniasZoomCategory) {
                        MetroMapOverlayLoader.ZoomCategory.FAR -> sets.highResFar.ifEmpty { sets.lowResFar }
                        MetroMapOverlayLoader.ZoomCategory.MEDIUM -> sets.highResMedium.ifEmpty { sets.lowResMedium }
                        MetroMapOverlayLoader.ZoomCategory.CLOSE -> sets.highResClose.ifEmpty { sets.lowResClose }
                    }
                    val converted = rawSets.map { raw ->
                        GoogleMapPolyline(
                            points = raw.points.map { LatLng(it.latitude, it.longitude) },
                            color = Color(raw.color),
                            width = raw.strokeWidth,
                            lineRef = raw.lineRef
                        )
                    }
                    withContext(Dispatchers.Main) {
                        cercaniasPolylineList = converted
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("GoogleMapTransitRoutes", "Error loading cercanias polylines", e)
                }
            }
        } else {
            cercaniasPolylineList = emptyList()
        }
    }

    // 3. Dynamic EMT Bus Route Polylines
    val activeBusTarget = remember(selectedItem, selectedMapItem, selectedBusLineFilters) {
        val lines = mutableSetOf<String>()
        var stopGeo: GeoPoint? = null

        if (selectedBusLineFilters.isNotEmpty()) {
            selectedBusLineFilters.forEach { lines.add(it.trim()) }
        }

        when (selectedItem) {
            is GoogleMapTransitItem.Bus -> {
                stopGeo = GeoPoint(selectedItem.stop.lat, selectedItem.stop.lon)
                if (lines.isEmpty()) {
                    (selectedItem.stop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
                }
            }
            is GoogleMapTransitItem.Metrobus -> {
                stopGeo = GeoPoint(selectedItem.stop.latitud, selectedItem.stop.longitud)
                if (lines.isEmpty()) {
                    (selectedItem.stop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
                }
            }
            is GoogleMapTransitItem.Merged -> {
                stopGeo = GeoPoint(selectedItem.busStop.lat, selectedItem.busStop.lon)
                if (lines.isEmpty()) {
                    (selectedItem.busStop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
                    (selectedItem.mbStop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
                }
            }
            else -> {}
        }

        if (selectedMapItem is SelectedMapItem.BusStop) {
            if (stopGeo == null) {
                stopGeo = GeoPoint(selectedMapItem.stop.lat, selectedMapItem.stop.lon)
            }
            if (lines.isEmpty()) {
                (selectedMapItem.stop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
                selectedMapItem.emtStopModel.utes.forEach { lines.add(it.id_linea.trim()) }
            }
        } else if (selectedMapItem is SelectedMapItem.MetrobusStopItem) {
            if (stopGeo == null) {
                stopGeo = GeoPoint(selectedMapItem.stop.latitud, selectedMapItem.stop.longitud)
            }
            if (lines.isEmpty()) {
                (selectedMapItem.stop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
                selectedMapItem.metrobusModel.lineas.forEach { lines.add(it.trim()) }
            }
        }

        Pair(lines.filter { it.isNotEmpty() }.toSet(), stopGeo)
    }

    val busPolyCategory = when {
        settledZoom < 13.5f -> 0
        settledZoom < 16.0f -> 1
        else -> 2
    }

    LaunchedEffect(activeBusTarget, selectedDirectionFilter, busPolyCategory) {
        val (linesToDraw, stopGeo) = activeBusTarget
        val emtLines = linesToDraw.filter { line ->
            val norm = MetrobusStationHighlightManager.normalizeLine(line)
            norm.toIntOrNull() == null || norm.toInt() < 100
        }
        if (emtLines.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    EmtMapOverlayLoader.ensureLoaded(context)
                    val polylines = mutableListOf<GoogleMapPolyline>()
                    val polyWidth = when (busPolyCategory) {
                        0 -> 6f
                        1 -> 9f
                        else -> 12f
                    }
                    emtLines.forEach { lineRef ->
                        val shapes = EmtMapOverlayLoader.getShapesForLine(
                            rawLine = lineRef,
                            stopLocation = stopGeo,
                            targetHeadsign = selectedDirectionFilter
                        )
                        shapes.forEach { shape ->
                            polylines.add(
                                GoogleMapPolyline(
                                    points = shape.points.map { LatLng(it.latitude, it.longitude) },
                                    color = Color(AndroidColor.parseColor("#E52320")),
                                    width = polyWidth,
                                    lineRef = lineRef
                                )
                            )
                        }
                    }
                    withContext(Dispatchers.Main) {
                        busPolylineList = polylines
                    }
                } catch (e: Exception) {
                    Log.e("GoogleMapTransitRoutes", "Error loading EMT bus polylines", e)
                }
            }
        } else {
            busPolylineList = emptyList()
        }
    }

    // 4. Dynamic Metrobús Route Polylines
    LaunchedEffect(selectedMetrobusShapes, activeBusTarget, selectedDirectionFilter, settledZoom) {
        val (linesToDraw, stopGeo) = activeBusTarget
        val mbLines = linesToDraw.filter { line ->
            val norm = MetrobusStationHighlightManager.normalizeLine(line)
            norm.toIntOrNull()?.let { it >= 100 } ?: (line.startsWith("L", ignoreCase = true) || line.startsWith("Metro", ignoreCase = true))
        }

        if (linesToDraw.isEmpty() && selectedBusLineFilters.isEmpty()) {
            metrobusPolylineList = emptyList()
        } else if (selectedMetrobusShapes.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val filteredShapes = when {
                        !selectedDirectionFilter.isNullOrBlank() -> {
                            val match = selectedMetrobusShapes.filterKeys { it.endsWith("_$selectedDirectionFilter") || it.contains(selectedDirectionFilter) }
                            if (match.isNotEmpty()) match else selectedMetrobusShapes
                        }
                        stopGeo != null -> {
                            BusRouteDirectionManager.findMatchingMetrobusDirectionForStop(stopGeo, selectedMetrobusShapes)
                        }
                        else -> selectedMetrobusShapes
                    }
                    val polyWidth = when (busPolyCategory) {
                        0 -> 6f
                        1 -> 9f
                        else -> 12f
                    }
                    val mbColor = Color(AndroidColor.parseColor("#F97316"))
                    val polylines = filteredShapes.map { (key, points) ->
                        GoogleMapPolyline(
                            points = points.map { LatLng(it.latitude, it.longitude) },
                            color = mbColor,
                            width = polyWidth,
                            lineRef = key
                        )
                    }
                    withContext(Dispatchers.Main) {
                        metrobusPolylineList = polylines
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("GoogleMapTransitRoutes", "Error loading Metrobús polylines", e)
                }
            }
        } else if (mbLines.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val db = com.example.data.database.AppDatabase.getDatabase(context)
                    val mbRepo = com.example.data.repository.MetrobusRepository(db, okhttp3.OkHttpClient(), context)
                    val shapesMap = mutableMapOf<String, List<GeoPoint>>()
                    mbLines.forEach { lineCode ->
                        val shapeData = mbRepo.fetchLineShape(lineCode)
                        shapeData?.polylinesByDirection?.forEach { (dir, coordList) ->
                            val points = coordList.mapNotNull { pt ->
                                if (pt.size >= 2) GeoPoint(pt[0], pt[1]) else null
                            }
                            if (points.isNotEmpty()) {
                                shapesMap["${lineCode}_$dir"] = points
                            }
                        }
                    }
                    val filteredShapes = when {
                        !selectedDirectionFilter.isNullOrBlank() -> {
                            val match = shapesMap.filterKeys { it.endsWith("_$selectedDirectionFilter") || it.contains(selectedDirectionFilter) }
                            if (match.isNotEmpty()) match else shapesMap
                        }
                        stopGeo != null -> {
                            BusRouteDirectionManager.findMatchingMetrobusDirectionForStop(stopGeo, shapesMap)
                        }
                        else -> shapesMap
                    }
                    val polyWidth = when (busPolyCategory) {
                        0 -> 6f
                        1 -> 9f
                        else -> 12f
                    }
                    val mbColor = Color(AndroidColor.parseColor("#F97316"))
                    val polylines = filteredShapes.map { (key, points) ->
                        GoogleMapPolyline(
                            points = points.map { LatLng(it.latitude, it.longitude) },
                            color = mbColor,
                            width = polyWidth,
                            lineRef = key
                        )
                    }
                    withContext(Dispatchers.Main) {
                        metrobusPolylineList = polylines
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("GoogleMapTransitRoutes", "Error fetching direct Metrobús shapes", e)
                }
            }
        } else {
            metrobusPolylineList = emptyList()
        }
    }

    // 5. Chevron Directional Arrows
    val activeIsolatedBusLines = remember(activeBusTarget) {
        activeBusTarget.first.map { EmtMapOverlayLoader.normalizeLine(it).uppercase() }.filter { it.isNotEmpty() }.toSet()
    }

    val isSingleBusLineIsolated = (selectedBusLineFilters.size == 1) || (selectedBusLineFilters.isEmpty() && activeIsolatedBusLines.size == 1)
    val showDirectionalArrows = isSingleBusLineIsolated && (busPolylineList.isNotEmpty() || metrobusPolylineList.isNotEmpty()) && settledZoom >= 11.5f

    val chevronTier = when {
        settledZoom < 13.0f -> 0
        settledZoom < 15.0f -> 1
        settledZoom < 17.0f -> 2
        else -> 3
    }

    val routeChevronArrows by remember(busPolylineList, metrobusPolylineList, chevronTier, showDirectionalArrows) {
        derivedStateOf {
            if (!showDirectionalArrows) {
                emptyList()
            } else {
                val interval = when (chevronTier) {
                    0 -> 650.0
                    1 -> 500.0
                    2 -> 380.0
                    else -> 260.0
                }
                val emtArrows = samplePolylineDirectionArrows(busPolylineList, intervalMeters = interval, isMetrobus = false)
                val mbArrows = samplePolylineDirectionArrows(metrobusPolylineList, intervalMeters = interval, isMetrobus = true)
                emtArrows + mbArrows
            }
        }
    }

    val isBusActive = busPolylineList.isNotEmpty() || metrobusPolylineList.isNotEmpty() || activeBusTarget.first.isNotEmpty()

    return remember(
        metroPolylineList,
        cercaniasPolylineList,
        busPolylineList,
        metrobusPolylineList,
        routeChevronArrows,
        isLinesLoading,
        activeBusTarget,
        activeIsolatedBusLines,
        isBusActive
    ) {
        GoogleMapTransitRoutesState(
            metroPolylineList = metroPolylineList,
            cercaniasPolylineList = cercaniasPolylineList,
            busPolylineList = busPolylineList,
            metrobusPolylineList = metrobusPolylineList,
            routeChevronArrows = routeChevronArrows,
            isLinesLoading = isLinesLoading,
            activeBusTarget = activeBusTarget,
            activeIsolatedBusLines = activeIsolatedBusLines,
            isBusActive = isBusActive
        )
    }
}
