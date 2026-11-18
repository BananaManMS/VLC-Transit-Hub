package com.example.ui.map.google

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.map.components.MetroStationFootprintsOverlayManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GoogleMapFootprintsState(
    val metroStationFootprints: List<GoogleMapStationFootprint> = emptyList(),
    val metroAccessPoints: List<MetroStationFootprintsOverlayManager.AccessPointData> = emptyList(),
    val isFootprintsLoading: Boolean = false
)

private object GoogleMapFootprintsConversionCache {
    @Volatile
    var cachedPolygonsRef: List<MetroStationFootprintsOverlayManager.StationPolygonData>? = null
    @Volatile
    var cachedFootprints: List<GoogleMapStationFootprint> = emptyList()

    fun getOrConvert(
        polygons: List<MetroStationFootprintsOverlayManager.StationPolygonData>
    ): List<GoogleMapStationFootprint> {
        if (cachedPolygonsRef === polygons) return cachedFootprints
        synchronized(this) {
            if (cachedPolygonsRef === polygons) return cachedFootprints
            val converted = polygons.map { data ->
                GoogleMapStationFootprint(
                    points = data.points.map { LatLng(it.latitude, it.longitude) },
                    stationName = data.stationName,
                    centerLat = data.centerLat,
                    centerLon = data.centerLon
                )
            }
            cachedFootprints = converted
            cachedPolygonsRef = polygons
            return converted
        }
    }
}

/**
 * Manages asynchronous loading and caching of Metro station footprints (polygons)
 * and station access points (escaleras, ascensores).
 */
@Composable
fun rememberGoogleMapFootprints(
    context: Context,
    showMetroFootprints: Boolean
): GoogleMapFootprintsState {
    var metroStationFootprints by remember(showMetroFootprints) {
        mutableStateOf<List<GoogleMapStationFootprint>>(emptyList())
    }
    var metroAccessPoints by remember(showMetroFootprints) {
        mutableStateOf<List<MetroStationFootprintsOverlayManager.AccessPointData>>(emptyList())
    }
    var isFootprintsLoading by remember {
        mutableStateOf(showMetroFootprints && !MetroStationFootprintsOverlayManager.isLoadedState.value)
    }

    LaunchedEffect(showMetroFootprints) {
        if (showMetroFootprints) {
            if (metroStationFootprints.isEmpty()) {
                if (MetroStationFootprintsOverlayManager.isLoadedState.value) {
                    val polygons = MetroStationFootprintsOverlayManager.getLoadedPolygonsData()
                    metroStationFootprints = GoogleMapFootprintsConversionCache.getOrConvert(polygons)
                    metroAccessPoints = MetroStationFootprintsOverlayManager.getLoadedAccessPointsData()
                    isFootprintsLoading = false
                } else {
                    isFootprintsLoading = true
                    withContext(Dispatchers.IO) {
                        try {
                            val polygons = MetroStationFootprintsOverlayManager.ensureLoaded(context)
                            val convertedFootprints = GoogleMapFootprintsConversionCache.getOrConvert(polygons)
                            val accesses = MetroStationFootprintsOverlayManager.getLoadedAccessPointsData()
                            withContext(Dispatchers.Main) {
                                metroStationFootprints = convertedFootprints
                                metroAccessPoints = accesses
                                isFootprintsLoading = false
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Log.e("GoogleMapFootprints", "Error loading footprints", e)
                            withContext(Dispatchers.Main) { isFootprintsLoading = false }
                        }
                    }
                }
            }
        } else {
            metroStationFootprints = emptyList()
            metroAccessPoints = emptyList()
            isFootprintsLoading = false
        }
    }

    return remember(metroStationFootprints, metroAccessPoints, isFootprintsLoading) {
        GoogleMapFootprintsState(
            metroStationFootprints = metroStationFootprints,
            metroAccessPoints = metroAccessPoints,
            isFootprintsLoading = isFootprintsLoading
        )
    }
}
