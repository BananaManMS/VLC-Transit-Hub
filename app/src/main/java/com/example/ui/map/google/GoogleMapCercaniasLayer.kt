package com.example.ui.map.google

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.example.data.database.CercaniasStationEntity
import com.example.ui.cercanias.LiveVehicleInfo
import com.example.ui.map.MapFilter
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.CercaniasMapOverlayLoader
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
    context: Context,
    handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit
) {
    val isCercaniasActiveFilter = showCercaniasStations && (mapFilter.isFavorites || mapFilter.showCercanias)

    // 1. Cercanías Stations
    if (isCercaniasActiveFilter) {
        cercaniasStationsToRender.forEach { station ->
            val isSelected = (selectedItem as? GoogleMapTransitItem.Cercanias)?.station?.stop_id == station.stop_id ||
                             (selectedMapItem as? SelectedMapItem.Cercanias)?.station?.stop_id == station.stop_id
            if (!isSelected && !isZoomForCercanias && !mapFilter.isFavorites) return@forEach

            key("CERCANIAS_${station.stop_id}") {
                val isFav = favoriteCercaniasStations.contains(station.stop_id)

                val isDimmedByOtherMode = isBusActive || isMetroActive
                val isDimmedByCercaniasHighlight = activeCercaniasLines.isNotEmpty() && !activeCercaniasLines.any { station.lineas.contains(it) }
                val isDimmed = !isSelected && (isDimmedByOtherMode || isDimmedByCercaniasHighlight)
                val isConnected = !isSelected && activeCercaniasLines.isNotEmpty() && activeCercaniasLines.any { station.lineas.contains(it) }

                val cercaniasAlpha = if (isSelected) 1.0f else if (isDimmed) 0.20f else 1.0f
                val cercaniasZIndex = if (isSelected) 45f else if (isDimmed) 2f else if (isConnected) 34f else 25f

                val cercaniasLod = when {
                    settledZoom < 11.5f -> 0
                    settledZoom < 13.5f -> 1
                    settledZoom < 15.0f -> 2
                    settledZoom < 16.0f -> 3
                    else -> 4
                }

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
                val canonicalNum = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(vehicle.routeId, vehicle.tripId)
                val lineCode = "C$canonicalNum"
                val lineColorHex = AndroidColor.parseColor(
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
