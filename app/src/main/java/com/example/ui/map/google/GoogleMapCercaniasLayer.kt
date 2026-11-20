package com.example.ui.map.google

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.example.data.database.CercaniasStationEntity
import com.example.ui.cercanias.LiveVehicleInfo
import com.example.ui.map.MapFilter
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.CercaniasMapOverlayLoader
import com.example.ui.map.components.MarkerLogoBitmapCache
import com.example.util.LineColorResolver
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState

@Composable
internal fun GoogleMapCercaniasLayer(
    showCercaniasStations: Boolean,
    isZoomForCercanias: Boolean,
    mapFilter: MapFilter,
    cercaniasStationsToRender: List<CercaniasStationEntity>,
    favoriteCercaniasStations: Set<String>,
    selectedItem: GoogleMapTransitItem?,
    selectedMapItem: SelectedMapItem?,
    isBusActive: Boolean,
    isMetroActive: Boolean,
    activeCercaniasLines: Set<String>,
    trainsToRender: List<LiveVehicleInfo>,
    isDarkMode: Boolean,
    settledZoom: Float,
    minLat: Double = -90.0,
    maxLat: Double = 90.0,
    minLon: Double = -180.0,
    maxLon: Double = 180.0,
    context: Context,
    handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit
) {
    val isCercaniasActiveFilter = showCercaniasStations && (mapFilter.isFavorites || mapFilter.showCercanias)

    // 1. Cercanías Stations
    if (isCercaniasActiveFilter) {
        val selectedCercaniasId = (selectedItem as? GoogleMapTransitItem.Cercanias)?.station?.stop_id
            ?: (selectedMapItem as? SelectedMapItem.Cercanias)?.station?.stop_id
        val hasBounds = maxLat > minLat && maxLon > minLon && minLat > -89.0
        val latPad = if (hasBounds) ((maxLat - minLat) * 0.5).coerceAtLeast(0.03) else 0.0
        val lonPad = if (hasBounds) ((maxLon - minLon) * 0.5).coerceAtLeast(0.03) else 0.0
        val pMinLat = minLat - latPad
        val pMaxLat = maxLat + latPad
        val pMinLon = minLon - lonPad
        val pMaxLon = maxLon + lonPad

        val visibleStations = remember(
            cercaniasStationsToRender,
            isZoomForCercanias,
            mapFilter.isFavorites,
            selectedCercaniasId,
            hasBounds,
            pMinLat,
            pMaxLat,
            pMinLon,
            pMaxLon
        ) {
            if (!isZoomForCercanias && !mapFilter.isFavorites) {
                if (selectedCercaniasId == null) emptyList()
                else cercaniasStationsToRender.filter { it.stop_id == selectedCercaniasId }
            } else if (!hasBounds || mapFilter.isFavorites) {
                cercaniasStationsToRender
            } else {
                cercaniasStationsToRender.filter { station ->
                    station.stop_id == selectedCercaniasId ||
                        (station.lat in pMinLat..pMaxLat && station.lon in pMinLon..pMaxLon)
                }
            }
        }

        val isDimmedByOtherMode = isBusActive || isMetroActive
        val cercaniasLod = when {
            settledZoom < 11.5f -> 0
            settledZoom < 13.5f -> 1
            settledZoom < 15.0f -> 2
            settledZoom < 16.0f -> 3
            else -> 4
        }
        val hasActiveHighlight = activeCercaniasLines.isNotEmpty()

        visibleStations.forEach { station ->
            val isSelected = station.stop_id == selectedCercaniasId

            key("CERCANIAS_${station.stop_id}") {
                val isFav = favoriteCercaniasStations.contains(station.stop_id)
                val matchesHighlight = hasActiveHighlight && station.lineas.any { activeCercaniasLines.contains(it) }
                val isDimmedByCercaniasHighlight = hasActiveHighlight && !matchesHighlight
                val isDimmed = !isSelected && (isDimmedByOtherMode || isDimmedByCercaniasHighlight)
                val isConnected = !isSelected && matchesHighlight

                val cercaniasAlpha = if (isSelected) 1.0f else if (isDimmed) 0.20f else 1.0f
                val cercaniasZIndex = if (isSelected) 45f else if (isDimmed) 2f else if (isConnected) 34f else 25f

                val iconInfo = remember(station.nombre, station.lineas, isDarkMode, isFav, cercaniasLod, isSelected) {
                    if (isSelected) {
                        GoogleMapsMarkerCache.getOrCreateCercaniasStationIcon(
                            context = context,
                            stationName = station.nombre,
                            lines = station.lineas,
                            isDarkMode = isDarkMode,
                            isFavorite = isFav,
                            showPill = true
                        )
                    } else if (cercaniasLod == 0) {
                        GoogleMapsMarkerCache.getOrCreateCercaniasRedDotIcon(context, isDarkMode)
                    } else if (cercaniasLod == 1) {
                        GoogleMapsMarkerCache.getOrCreateCercaniasLogoSmallIcon(context, isDarkMode)
                    } else if (cercaniasLod == 2) {
                        GoogleMapsMarkerCache.getOrCreateCercaniasStationIcon(
                            context = context,
                            stationName = station.nombre,
                            lines = emptyList(),
                            isDarkMode = isDarkMode,
                            isFavorite = isFav,
                            showPill = false
                        )
                    } else if (cercaniasLod == 3) {
                        GoogleMapsMarkerCache.getOrCreateCercaniasStationIcon(
                            context = context,
                            stationName = station.nombre,
                            lines = emptyList(),
                            isDarkMode = isDarkMode,
                            isFavorite = isFav,
                            showPill = true
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

                val markerState = rememberMarkerState(key = "CERCANIAS_${station.stop_id}", position = LatLng(station.lat, station.lon))
                Marker(
                    state = markerState,
                    icon = iconInfo.descriptor,
                    anchor = iconInfo.anchor,
                    alpha = cercaniasAlpha,
                    title = station.nombre,
                    zIndex = cercaniasZIndex,
                    onClick = {
                        val item = GoogleMapTransitItem.Cercanias(station)
                        handleTransitItemTap(item, station.lat, station.lon)
                        true
                    }
                )
            }
        }
    }

    // 2. Live Cercanías Trains (zIndex = 60f)
    if (isCercaniasActiveFilter && trainsToRender.isNotEmpty()) {
        trainsToRender.forEach { vehicle ->
            val vLat = vehicle.latitude
            val vLon = vehicle.longitude
            if (vLat != null && vLon != null && !vLat.isNaN() && !vLon.isNaN()) {
                val lineCode = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineCode(vehicle.routeId, vehicle.tripId, "CR")
                val lineColorHex = MarkerLogoBitmapCache.parseColorCached(
                    LineColorResolver.getCercaniasLineColorHex(lineCode)
                )
                val trackPoints = remember(lineCode) {
                    CercaniasMapOverlayLoader.getLinePoints(lineCode)
                }
                val snappedPos = remember(vLat, vLon, trackPoints) {
                    snapToTrackPolyline(vLat, vLon, trackPoints)
                }

                key("LIVE_TRAIN_${vehicle.tripId}_${vehicle.trainNum}") {
                    val trainIcon = remember(lineCode, lineColorHex, vehicle.delayMinutes, vehicle.bearing, settledZoom) {
                        GoogleMapsMarkerCache.getOrCreateLiveTrainIcon(
                            context = context,
                            lineCode = lineCode,
                            lineColorHex = lineColorHex,
                            delayMinutes = vehicle.delayMinutes,
                            bearing = vehicle.bearing ?: 0f,
                            zoom = settledZoom.toDouble(),
                            showArrow = (vehicle.bearing != null && vehicle.bearing != 0f)
                        )
                    }
                    val markerState = rememberMarkerState(
                        key = "LIVE_TRAIN_${vehicle.tripId}_${vehicle.trainNum}",
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
                            handleTransitItemTap(GoogleMapTransitItem.Train(vehicle), snappedPos.latitude, snappedPos.longitude)
                            true
                        }
                    )
                }
            }
        }
    }
}
