package com.example.ui.cercanias

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.database.CercaniasStationEntity
import com.example.ui.map.components.CercaniasMapOverlayLoader
import com.example.ui.map.google.GoogleMapPolyline
import com.example.ui.map.google.GoogleMapsMarkerCache
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Dedicated Google Maps view for CercaniasLiveMapDialog.
 * Provides complete visual and functional parity with OSMdroid:
 * 1. C1 to C6 track polylines with line-specific official colors.
 * 2. Cercanías station markers with Renfe roundels and transfer pills.
 * 3. Real-time moving train markers with delay status rings and bearing indicators.
 * 4. Interactive station and train selection bottom sheet triggers.
 */
@Composable
fun CercaniasGoogleMapView(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    stations: List<CercaniasStationEntity> = emptyList(),
    favoriteStationIds: Set<String> = emptySet(),
    liveTrains: List<LiveVehicleInfo> = emptyList(),
    selectedStation: CercaniasStationEntity? = null,
    selectedTrain: LiveVehicleInfo? = null,
    cameraAnimTrigger: Int = 0,
    cameraTarget: LatLng? = null,
    cameraZoom: Float = 14.5f,
    bottomPaddingDp: androidx.compose.ui.unit.Dp = 0.dp,
    onStationClick: (CercaniasStationEntity) -> Unit,
    onTrainClick: (LiveVehicleInfo) -> Unit,
    onMapClick: () -> Unit
) {
    val context = LocalContext.current
    var isMapLoaded by remember { mutableStateOf(false) }

    val valenciaCenter = remember { LatLng(39.46975, -0.37739) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(valenciaCenter, 10.2f)
    }

    // Camera animation trigger from Recenter GPS or station click
    LaunchedEffect(cameraAnimTrigger) {
        if (cameraAnimTrigger > 0 && cameraTarget != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(cameraTarget, cameraZoom),
                durationMs = 200
            )
        }
    }

    // Camera focus on selected station/train and instant optical recentering (0ms zero-delay jump)
    LaunchedEffect(selectedStation, selectedTrain, bottomPaddingDp) {
        val target = when {
            selectedStation != null -> LatLng(selectedStation.lat, selectedStation.lon)
            selectedTrain?.latitude != null && selectedTrain.longitude != null -> LatLng(selectedTrain.latitude!!, selectedTrain.longitude!!)
            else -> null
        }
        if (target != null) {
            val currentZoom = cameraPositionState.position.zoom
            val targetZoom = if (currentZoom < 14.0f) 14.8f else currentZoom
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(target, targetZoom)
            )
        }
    }

    // Raw Map Styles
    val lightStyleOptions = remember(context) {
        try {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_clean_light)
        } catch (_: Exception) { null }
    }
    val darkStyleOptions = remember(context) {
        try {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_clean_dark)
        } catch (_: Exception) { null }
    }

    val mapProperties = remember(isDarkMode) {
        MapProperties(
            mapType = MapType.NORMAL,
            mapStyleOptions = if (isDarkMode) darkStyleOptions else lightStyleOptions,
            isMyLocationEnabled = false
        )
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        )
    }

    // Load Cercanías track polylines
    var cercaniasPolylineList by remember { mutableStateOf<List<GoogleMapPolyline>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                CercaniasMapOverlayLoader.ensureLoaded(context)
                if (!CercaniasMapOverlayLoader.isLoaded) {
                    CercaniasMapOverlayLoader.isLoadedState.first { it }
                }
                val sets = CercaniasMapOverlayLoader.getPolylineSets()
                val rawSets = sets.highResMedium.ifEmpty { sets.highResClose }
                val converted = rawSets.map { raw ->
                    GoogleMapPolyline(
                        points = raw.points.map { LatLng(it.latitude, it.longitude) },
                        color = Color(raw.color),
                        width = 15f,
                        lineRef = raw.lineRef
                    )
                }
                withContext(Dispatchers.Main) {
                    cercaniasPolylineList = converted
                }
            } catch (e: Exception) {
                android.util.Log.e("CercaniasGoogleMapView", "Error loading cercanias polylines", e)
            }
        }
    }

    val currentZoom = cameraPositionState.position.zoom
    val isZoomForStations by remember { derivedStateOf { cameraPositionState.position.zoom >= 11.5f } }

    val activeCercaniasLines = remember(selectedStation) {
        selectedStation?.lineas?.map { it.trim().uppercase() }?.toSet() ?: emptySet()
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPaddingDp),
            onMapClick = { onMapClick() },
            onMapLoaded = { isMapLoaded = true }
        ) {
            // 1. Cercanías Track Polylines
            cercaniasPolylineList.forEachIndexed { index, line ->
                key("TRACK_${index}_${line.lineRef}") {
                    val isHighlighted = activeCercaniasLines.isNotEmpty() &&
                            activeCercaniasLines.contains(line.lineRef.trim().uppercase())
                    val isDimmed = activeCercaniasLines.isNotEmpty() && !isHighlighted

                    val effectiveColor = if (isDimmed) line.color.copy(alpha = 0.22f) else line.color
                    val effectiveWidth = if (isHighlighted) 17f else if (isDimmed) 10f else line.width
                    val effectiveZIndex = if (isHighlighted) 12f else 8f

                    Polyline(
                        points = line.points,
                        color = effectiveColor,
                        width = effectiveWidth,
                        zIndex = effectiveZIndex,
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap()
                    )
                }
            }

            // 2. Cercanías Stations (Exact OSMdroid Zoom LOD: Red dot < 13.5, Roundel 13.5..15.5, Full pill >= 15.5)
            if (isZoomForStations) {
                stations.forEach { station ->
                    key("STATION_${station.stop_id}") {
                        val isFav = favoriteStationIds.contains(station.stop_id)
                        val isSelected = selectedStation?.stop_id == station.stop_id

                        val iconInfo = remember(station.nombre, station.lineas, isDarkMode, isFav, currentZoom, isSelected) {
                            if (isSelected) {
                                GoogleMapsMarkerCache.getOrCreateCercaniasStationIcon(
                                    context = context,
                                    stationName = station.nombre,
                                    lines = station.lineas,
                                    isDarkMode = isDarkMode,
                                    isFavorite = isFav,
                                    showPill = true
                                )
                            } else if (currentZoom < 13.5f) {
                                GoogleMapsMarkerCache.getOrCreateCercaniasRedDotIcon(context, isDarkMode)
                            } else if (currentZoom < 15.5f) {
                                GoogleMapsMarkerCache.getOrCreateCercaniasStationIcon(
                                    context = context,
                                    stationName = station.nombre,
                                    lines = emptyList(),
                                    isDarkMode = isDarkMode,
                                    isFavorite = isFav,
                                    showPill = false
                                )
                            } else {
                                GoogleMapsMarkerCache.getOrCreateCercaniasStationIcon(
                                    context = context,
                                    stationName = station.nombre,
                                    lines = station.lineas,
                                    isDarkMode = isDarkMode,
                                    isFavorite = isFav,
                                    showPill = true
                                )
                            }
                        }

                        val markerState = com.google.maps.android.compose.rememberMarkerState(
                            key = "STATION_${station.stop_id}",
                            position = LatLng(station.lat, station.lon)
                        )

                        Marker(
                            state = markerState,
                            icon = iconInfo.descriptor,
                            anchor = iconInfo.anchor,
                            title = station.nombre,
                            zIndex = if (isSelected) 45f else 20f,
                            onClick = {
                                onStationClick(station)
                                true
                            }
                        )
                    }
                }
            }

            // 3. Live Moving Trains
            if (liveTrains.isNotEmpty()) {
                liveTrains.forEach { vehicle ->
                    val vLat = vehicle.latitude
                    val vLon = vehicle.longitude
                    if (vLat != null && vLon != null && !vLat.isNaN() && !vLon.isNaN()) {
                        val canonicalNum = CercaniasRouteUtils.getCanonicalLineNumber(vehicle.routeId, vehicle.tripId)
                        val lineCode = "C$canonicalNum"
                        val lineColorHex = AndroidColor.parseColor(
                            com.example.util.LineColorResolver.getCercaniasLineColorHex(lineCode)
                        )
                        val trackPoints = remember(lineCode) {
                            CercaniasMapOverlayLoader.getLinePoints(lineCode)
                        }
                        val snappedPos = remember(vLat, vLon, trackPoints) {
                            snapToTrackPolyline(vLat, vLon, trackPoints)
                        }

                        key("TRAIN_${vehicle.tripId}_${vehicle.trainNum}") {
                            val trainIcon = remember(lineCode, lineColorHex, vehicle.delayMinutes, vehicle.bearing, currentZoom) {
                                GoogleMapsMarkerCache.getOrCreateLiveTrainIcon(
                                    context = context,
                                    lineCode = lineCode,
                                    lineColorHex = lineColorHex,
                                    delayMinutes = vehicle.delayMinutes,
                                    bearing = vehicle.bearing ?: 0f,
                                    zoom = currentZoom.toDouble(),
                                    showArrow = (vehicle.bearing != null && vehicle.bearing != 0f)
                                )
                            }
                            val markerState = com.google.maps.android.compose.rememberMarkerState(
                                key = "TRAIN_${vehicle.tripId}_${vehicle.trainNum}",
                                position = snappedPos
                            )
                            LaunchedEffect(snappedPos) {
                                markerState.position = snappedPos
                            }

                            Marker(
                                state = markerState,
                                icon = trainIcon.descriptor,
                                anchor = trainIcon.anchor,
                                title = "Tren ${vehicle.trainNum}",
                                snippet = "Línea $lineCode • Retraso: ${vehicle.delayMinutes} min",
                                zIndex = 60f,
                                onClick = {
                                    onTrainClick(vehicle)
                                    true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Anti-flash loading scrim
        AnimatedVisibility(
            visible = !isMapLoaded,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

private fun snapToTrackPolyline(
    lat: Double,
    lon: Double,
    trackPoints: List<List<org.osmdroid.util.GeoPoint>>
): LatLng {
    if (trackPoints.isEmpty()) return LatLng(lat, lon)
    var closestLat = lat
    var closestLon = lon
    var minDistanceSq = Double.MAX_VALUE

    for (segment in trackPoints) {
        for (p in segment) {
            val dLat = p.latitude - lat
            val dLon = p.longitude - lon
            val distSq = dLat * dLat + dLon * dLon
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestLat = p.latitude
                closestLon = p.longitude
            }
        }
    }
    return if (minDistanceSq < 0.000100) LatLng(closestLat, closestLon) else LatLng(lat, lon)
}
