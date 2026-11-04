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
    var isFootprintsLoading by remember { mutableStateOf(showMetroFootprints) }

    LaunchedEffect(showMetroFootprints) {
        if (showMetroFootprints) {
            if (metroStationFootprints.isEmpty()) {
                isFootprintsLoading = true
                withContext(Dispatchers.IO) {
                    try {
                        val polygons = MetroStationFootprintsOverlayManager.ensureLoaded(context)
                        val convertedFootprints = polygons.map { data ->
                            GoogleMapStationFootprint(
                                points = data.points.map { LatLng(it.latitude, it.longitude) },
                                stationName = data.stationName,
                                centerLat = data.centerLat,
                                centerLon = data.centerLon
                            )
                        }
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
