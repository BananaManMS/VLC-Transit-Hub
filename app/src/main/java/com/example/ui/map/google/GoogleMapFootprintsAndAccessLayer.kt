package com.example.ui.map.google

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.data.model.MetroStation
import com.example.ui.map.components.MetroStationAccessMatcher
import com.example.ui.map.components.MetroStationFootprintsOverlayManager
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberMarkerState

private val SELECTED_FP_FILL = Color(0x4DE2001A)
private val SELECTED_FP_STROKE = Color(0xFFE2001A)
private val UNSELECTED_FP_FILL = Color(0x33EF4444)
private val UNSELECTED_FP_STROKE = Color(0x99DC2626)

@Composable
internal fun GoogleMapFootprintsAndAccessLayer(
    showMetroFootprints: Boolean,
    metroStationFootprints: List<GoogleMapStationFootprint>,
    metroAccessPoints: List<MetroStationFootprintsOverlayManager.AccessPointData>,
    selectedMetroStation: MetroStation?,
    currentZoom: Float,
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
    context: Context,
    onItemClick: ((GoogleMapTransitItem) -> Unit)?
) {
    // 1. Draw Station Footprints (Polygons) matching OSMdroid rules
    if (showMetroFootprints && metroStationFootprints.isNotEmpty()) {
        if (selectedMetroStation != null && currentZoom >= 15.2f) {
            val selectedFootprints = remember(metroStationFootprints, selectedMetroStation) {
                metroStationFootprints.filter {
                    MetroStationAccessMatcher.matchesStation(it.stationName, selectedMetroStation)
                }
            }
            selectedFootprints.forEachIndexed { idx, fp ->
                key("FOOTPRINT_SEL_${fp.stationName}_$idx") {
                    Polygon(
                        points = fp.points,
                        fillColor = SELECTED_FP_FILL,
                        strokeColor = SELECTED_FP_STROKE,
                        strokeWidth = 3.5f,
                        zIndex = 8f
                    )
                }
            }
        } else if (selectedMetroStation == null && currentZoom >= 15.8f) {
            val visibleFootprints = remember(metroStationFootprints, minLat, maxLat, minLon, maxLon) {
                metroStationFootprints.filter {
                    it.centerLat in minLat..maxLat && it.centerLon in minLon..maxLon
                }
            }
            visibleFootprints.forEachIndexed { idx, fp ->
                key("FOOTPRINT_UNSEL_${fp.stationName}_$idx") {
                    Polygon(
                        points = fp.points,
                        fillColor = UNSELECTED_FP_FILL,
                        strokeColor = UNSELECTED_FP_STROKE,
                        strokeWidth = 2.5f,
                        zIndex = 1f
                    )
                }
            }
        }
    }

    // 2. Draw Metro Access Points (Bocas de metro / Escaleras / Ascensor)
    val shouldShowAccesses = showMetroFootprints && (
        (selectedMetroStation != null && currentZoom >= 15.2f) ||
        (selectedMetroStation == null && currentZoom >= 15.8f)
    )
    if (shouldShowAccesses && metroAccessPoints.isNotEmpty()) {
        val filteredAccesses = remember(metroAccessPoints, selectedMetroStation, minLat, maxLat, minLon, maxLon) {
            if (selectedMetroStation != null) {
                metroAccessPoints.filter { MetroStationAccessMatcher.matchesStation(it.stationName, selectedMetroStation) }
            } else {
                metroAccessPoints.filter { it.geoPoint.latitude in minLat..maxLat && it.geoPoint.longitude in minLon..maxLon }
            }
        }
        val accessZIndex = if (selectedMetroStation != null) 36f else 24f

        filteredAccesses.forEach { access ->
            val accessKey = "ACCESS_${access.stationName}_${access.geoPoint.latitude}_${access.geoPoint.longitude}"
            key(accessKey) {
                val iconInfo = remember(access.typeCas) {
                    GoogleMapsMarkerCache.getOrCreateAccessIcon(context, access.typeCas)
                }

                Marker(
                    state = rememberMarkerState(
                        key = accessKey,
                        position = LatLng(access.geoPoint.latitude, access.geoPoint.longitude)
                    ),
                    icon = iconInfo.descriptor,
                    anchor = iconInfo.anchor,
                    title = access.labelText,
                    zIndex = accessZIndex,
                    onClick = {
                        val item = GoogleMapTransitItem.AccessPoint(access)
                        onItemClick?.invoke(item)
                        true
                    }
                )
            }
        }
    }
}
