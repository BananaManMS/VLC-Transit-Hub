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
    context: Context,
    handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit
) {
    val isMetroActiveFilter = showMetroStations && (mapFilter.isFavorites || mapFilter.showMetro)
    if (isMetroActiveFilter) {
        metroStationsToRender.forEach { station ->
            val isSelected = (selectedItem as? GoogleMapTransitItem.Metro)?.station?.id == station.id ||
                             (selectedMapItem as? SelectedMapItem.Metro)?.station?.id == station.id
            if (!isSelected && !isZoomForMetro && !mapFilter.isFavorites) return@forEach

            key("METRO_${station.id}") {
                val isFav = favoriteMetroStations.contains(station.id)
                val isDimmedByOtherMode = isBusActive || isCercaniasActive
                val isDimmedByMetroHighlight = activeMetroLines.isNotEmpty() && !activeMetroLines.any { station.lines.contains(it) }
                val isDimmed = !isSelected && (isDimmedByOtherMode || isDimmedByMetroHighlight)
                val isConnected = !isSelected && activeMetroLines.isNotEmpty() && activeMetroLines.any { station.lines.contains(it) }

                val metroAlpha = if (isSelected) 1.0f else if (isDimmed) 0.20f else 1.0f
                val metroZIndex = if (isSelected) 45f else if (isDimmed) 2f else if (isConnected) 34f else 26f

                val metroLod = when {
                    settledZoom < 13.5f -> 0
                    settledZoom < 15.0f -> 1
                    settledZoom < 16.0f -> 2
                    else -> 3
                }

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
