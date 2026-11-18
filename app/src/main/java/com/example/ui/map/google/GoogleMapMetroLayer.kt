package com.example.ui.map.google

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.example.data.model.MetroStation
import com.example.ui.map.MapFilter
import com.example.ui.map.SelectedMapItem
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState

@Composable
internal fun GoogleMapMetroLayer(
    showMetroStations: Boolean,
    isZoomForMetro: Boolean,
    mapFilter: MapFilter,
    metroStationsToRender: List<MetroStation>,
    favoriteMetroStations: Set<String>,
    selectedItem: GoogleMapTransitItem?,
    selectedMapItem: SelectedMapItem?,
    isBusActive: Boolean,
    isCercaniasActive: Boolean,
    activeMetroLines: Set<String>,
    isDarkMode: Boolean,
    settledZoom: Float,
    minLat: Double = -90.0,
    maxLat: Double = 90.0,
    minLon: Double = -180.0,
    maxLon: Double = 180.0,
    context: Context,
    handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit
) {
    val isMetroActiveFilter = showMetroStations && (mapFilter.isFavorites || mapFilter.showMetro)
    if (isMetroActiveFilter) {
        val selectedMetroId = (selectedItem as? GoogleMapTransitItem.Metro)?.station?.id
            ?: (selectedMapItem as? SelectedMapItem.Metro)?.station?.id
        val hasBounds = maxLat > minLat && maxLon > minLon && minLat > -89.0
        val latPad = if (hasBounds) ((maxLat - minLat) * 0.5).coerceAtLeast(0.025) else 0.0
        val lonPad = if (hasBounds) ((maxLon - minLon) * 0.5).coerceAtLeast(0.025) else 0.0
        val pMinLat = minLat - latPad
        val pMaxLat = maxLat + latPad
        val pMinLon = minLon - lonPad
        val pMaxLon = maxLon + lonPad

        val visibleStations = remember(
            metroStationsToRender,
            isZoomForMetro,
            mapFilter.isFavorites,
            selectedMetroId,
            hasBounds,
            pMinLat,
            pMaxLat,
            pMinLon,
            pMaxLon
        ) {
            if (!isZoomForMetro && !mapFilter.isFavorites) {
                if (selectedMetroId == null) emptyList()
                else metroStationsToRender.filter { it.id == selectedMetroId }
            } else if (!hasBounds || mapFilter.isFavorites) {
                metroStationsToRender
            } else {
                metroStationsToRender.filter { station ->
                    station.id == selectedMetroId ||
                        (station.latitude in pMinLat..pMaxLat && station.longitude in pMinLon..pMaxLon)
                }
            }
        }

        val isDimmedByOtherMode = isBusActive || isCercaniasActive
        val metroLod = when {
            settledZoom < 13.5f -> 0
            settledZoom < 15.0f -> 1
            settledZoom < 16.0f -> 2
            else -> 3
        }
        val hasActiveHighlight = activeMetroLines.isNotEmpty()

        visibleStations.forEach { station ->
            val isSelected = station.id == selectedMetroId

            key("METRO_${station.id}") {
                val isFav = favoriteMetroStations.contains(station.id)
                val matchesHighlight = hasActiveHighlight && station.lines.any { activeMetroLines.contains(it) }
                val isDimmedByMetroHighlight = hasActiveHighlight && !matchesHighlight
                val isDimmed = !isSelected && (isDimmedByOtherMode || isDimmedByMetroHighlight)
                val isConnected = !isSelected && matchesHighlight

                val metroAlpha = if (isSelected) 1.0f else if (isDimmed) 0.20f else 1.0f
                val metroZIndex = if (isSelected) 45f else if (isDimmed) 2f else if (isConnected) 34f else 26f

                val iconInfo = remember(station.name, station.lines, isDarkMode, isFav, metroLod, isSelected) {
                    if (isSelected) {
                        GoogleMapsMarkerCache.getOrCreateMetroStationIcon(
                            context = context,
                            stationName = station.name,
                            lines = station.lines,
                            isDarkMode = isDarkMode,
                            isFavorite = isFav,
                            showPill = true
                        )
                    } else if (metroLod == 0) {
                        GoogleMapsMarkerCache.getOrCreateMetroWhiteDotIcon(context, isDarkMode)
                    } else if (metroLod == 1) {
                        GoogleMapsMarkerCache.getOrCreateMetroStationIcon(
                            context = context,
                            stationName = station.name,
                            lines = emptyList(),
                            isDarkMode = isDarkMode,
                            isFavorite = isFav,
                            showPill = false
                        )
                    } else if (metroLod == 2) {
                        GoogleMapsMarkerCache.getOrCreateMetroStationIcon(
                            context = context,
                            stationName = station.name,
                            lines = emptyList(),
                            isDarkMode = isDarkMode,
                            isFavorite = isFav,
                            showPill = true
                        )
                    } else {
                        GoogleMapsMarkerCache.getOrCreateMetroStationIcon(
                            context = context,
                            stationName = station.name,
                            lines = station.lines,
                            isDarkMode = isDarkMode,
                            isFavorite = isFav,
                            showPill = true
                        )
                    }
                }

                val markerState = rememberMarkerState(key = "METRO_${station.id}", position = LatLng(station.latitude, station.longitude))
                Marker(
                    state = markerState,
                    icon = iconInfo.descriptor,
                    anchor = iconInfo.anchor,
                    alpha = metroAlpha,
                    title = station.name,
                    zIndex = metroZIndex,
                    onClick = {
                        val item = GoogleMapTransitItem.Metro(station)
                        handleTransitItemTap(item, station.latitude, station.longitude)
                        true
                    }
                )
            }
        }
    }
}
