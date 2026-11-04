package com.example.ui.map.google

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.ui.map.MapFilter
import com.example.ui.map.components.BusMarkersRenderer
import com.example.ui.map.components.ValenbisiStation
import com.example.ui.map.components.ViewportTransitFilter
import com.example.ui.map.spatial.SpatialGridSampler
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox

data class GoogleMapViewportTransitState(
    val currentBoundingBox: BoundingBox,
    val currentMergedGroups: List<BusMarkersRenderer.MergedBusStopGroup>,
    val busClusters: List<GoogleMapTransitItem.Cluster>,
    val metrobusClusters: List<GoogleMapTransitItem.Cluster>,
    val standaloneBusDetailed: List<GeoportalStopEntity>,
    val standaloneMbDetailed: List<MetrobusStopEntity>,
    val sampledDotsBus: List<GeoportalStopEntity>,
    val sampledDotsMb: List<MetrobusStopEntity>,
    val showBusLayer: Boolean,
    val showMetrobusLayer: Boolean,
    val isZoomForClusters: Boolean,
    val isZoomForBusDots: Boolean,
    val isZoomForBusBadges: Boolean,
    val isZoomForBusDetailed: Boolean,
    val isOnlyBusesSelected: Boolean,
    val isOnlyValenbisi: Boolean
)
 
private data class ViewportQuery(
    val isMoving: Boolean,
    val zoom: Float,
    val filter: MapFilter,
    val bounds: LatLngBounds?
)

/**
 * Manages debounced viewport transit calculations, including stop clustering,
 * spatial grid sampling, and stop group merging for bus and metrobús.
 */
@Composable
fun rememberGoogleMapViewportTransit(
    cameraPositionState: CameraPositionState,
    settledZoom: Float,
    mapFilter: MapFilter,
    busStops: List<GeoportalStopEntity>,
    metrobusStops: List<MetrobusStopEntity>,
    valenbisiStations: List<ValenbisiStation>
): GoogleMapViewportTransitState {
    var currentBoundingBox by remember { mutableStateOf(BoundingBox(39.55, -0.25, 39.35, -0.50)) }

    val isOnlyBusesSelected = remember(mapFilter) {
        (mapFilter.showBus || mapFilter.showMetrobus) &&
                !mapFilter.showMetro &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites
    }

    val isOnlyValenbisi = remember(mapFilter) {
        mapFilter.showValenbisi && !mapFilter.showBus && !mapFilter.showMetrobus && !mapFilter.showMetro && !mapFilter.showCercanias && !mapFilter.isFavorites
    }

    // Instant synchronous visibility flags matching OSMdroid rules
    val showBusLayer by remember(mapFilter, isOnlyBusesSelected, settledZoom) {
        derivedStateOf {
            (mapFilter.isFavorites || mapFilter.showBus) && (mapFilter.isFavorites || isOnlyBusesSelected || settledZoom >= 15.2f)
        }
    }
    val showMetrobusLayer by remember(mapFilter, isOnlyBusesSelected, settledZoom) {
        derivedStateOf {
            (mapFilter.isFavorites || mapFilter.showMetrobus) && (mapFilter.isFavorites || isOnlyBusesSelected || settledZoom >= 15.2f)
        }
    }

    var currentMergedGroups by remember { mutableStateOf<List<BusMarkersRenderer.MergedBusStopGroup>>(emptyList()) }
    var busClusters by remember { mutableStateOf<List<GoogleMapTransitItem.Cluster>>(emptyList()) }
    var metrobusClusters by remember { mutableStateOf<List<GoogleMapTransitItem.Cluster>>(emptyList()) }
    var standaloneBusDetailed by remember { mutableStateOf<List<GeoportalStopEntity>>(emptyList()) }
    var standaloneMbDetailed by remember { mutableStateOf<List<MetrobusStopEntity>>(emptyList()) }
    var sampledDotsBus by remember { mutableStateOf<List<GeoportalStopEntity>>(emptyList()) }
    var sampledDotsMb by remember { mutableStateOf<List<MetrobusStopEntity>>(emptyList()) }

    val isZoomForClusters by remember(settledZoom, isOnlyBusesSelected, showBusLayer, showMetrobusLayer) {
        derivedStateOf {
            settledZoom < 13.5f && isOnlyBusesSelected && (showBusLayer || showMetrobusLayer)
        }
    }
    val isZoomForBusDots by remember(settledZoom, isZoomForClusters, showBusLayer, showMetrobusLayer) {
        derivedStateOf {
            settledZoom < 15.2f && !isZoomForClusters && (showBusLayer || showMetrobusLayer)
        }
    }
    val isZoomForBusBadges by remember(settledZoom, showBusLayer, showMetrobusLayer) {
        derivedStateOf {
            settledZoom in 15.2f..<17.8f && (showBusLayer || showMetrobusLayer)
        }
    }
    val isZoomForBusDetailed by remember(settledZoom, showBusLayer, showMetrobusLayer) {
        derivedStateOf {
            settledZoom >= 17.8f && (showBusLayer || showMetrobusLayer)
        }
    }

    // Viewport Transit Filter execution
    LaunchedEffect(
        cameraPositionState,
        mapFilter,
        busStops.size,
        metrobusStops.size,
        valenbisiStations.size
    ) {
        var lastRunTime = 0L
        snapshotFlow {
            val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
            ViewportQuery(
                isMoving = cameraPositionState.isMoving,
                zoom = cameraPositionState.position.zoom,
                filter = mapFilter,
                bounds = bounds
            )
        }.collect { query ->
            // ONLY execute heavy spatial filtering and LOD updates when gesture finishes
            if (query.isMoving) {
                return@collect
            }
            delay(50)

            val bounds = query.bounds ?: cameraPositionState.projection?.visibleRegion?.latLngBounds
            val curZoom = query.zoom.toDouble()
            val bBox = if (bounds != null) {
                BoundingBox(
                    bounds.northeast.latitude,
                    bounds.northeast.longitude,
                    bounds.southwest.latitude,
                    bounds.southwest.longitude
                )
            } else {
                BoundingBox(39.55, -0.25, 39.35, -0.50)
            }
            currentBoundingBox = bBox

            val currentFilter = query.filter

            withContext(Dispatchers.Default) {
                val filtered = ViewportTransitFilter.filterForViewport(
                    boundingBox = bBox,
                    busStops = busStops,
                    metrobusStops = metrobusStops,
                    valenbisiStations = valenbisiStations,
                    mapFilter = currentFilter,
                    currentZoom = curZoom
                )

                if (!filtered.showBus && !filtered.showMetrobus) {
                    withContext(Dispatchers.Main) {
                        currentMergedGroups = emptyList()
                        busClusters = emptyList()
                        metrobusClusters = emptyList()
                        sampledDotsBus = emptyList()
                        sampledDotsMb = emptyList()
                        standaloneBusDetailed = emptyList()
                        standaloneMbDetailed = emptyList()
                    }
                } else {
                    val merged = if (filtered.showBus && filtered.showMetrobus) {
                        BusMarkersRenderer.findMergedStopGroups(
                            busStopsInViewport = filtered.busStopsInViewport,
                            metrobusStopsInViewport = filtered.metrobusStopsInViewport,
                            selectedMapItem = null,
                            showBus = filtered.showBus,
                            showMetrobus = filtered.showMetrobus,
                            isFavoritesMode = filtered.isFavoritesMode,
                            currentZoom = curZoom
                        )
                    } else emptyList()

                    val mergedBusIds = merged.map { it.busStop.id_parada }.toSet()
                    val mergedMbIds = merged.map { it.mbStop.id_parada }.toSet()
                    val bStopsSingle = if (mergedBusIds.isEmpty()) filtered.busStopsInViewport else filtered.busStopsInViewport.filter { !mergedBusIds.contains(it.id_parada) }
                    val mbStopsSingle = if (mergedMbIds.isEmpty()) filtered.metrobusStopsInViewport else filtered.metrobusStopsInViewport.filter { !mergedMbIds.contains(it.id_parada) }

                    val isOnlyBus = (currentFilter.showBus || currentFilter.showMetrobus) &&
                            !currentFilter.showMetro &&
                            !currentFilter.showCercanias &&
                            !currentFilter.showValenbisi &&
                            !currentFilter.isFavorites

                    // Precompute all LOD representations from the current viewport stops so zoom transitions are instant
                    var bClusters = emptyList<GoogleMapTransitItem.Cluster>()
                    var mbClusters = emptyList<GoogleMapTransitItem.Cluster>()
                    if (isOnlyBus) {
                        val gridSize = if (curZoom < 11.0) 0.06 else 0.025
                        val allBusLocations = (bStopsSingle + merged.map { it.busStop })
                        if (currentFilter.showBus && allBusLocations.isNotEmpty()) {
                            bClusters = allBusLocations.groupBy { stop ->
                                Pair((stop.lat / gridSize).toInt(), (stop.lon / gridSize).toInt())
                            }.values.map { group ->
                                GoogleMapTransitItem.Cluster(
                                    count = group.size,
                                    centerLat = group.map { it.lat }.average(),
                                    centerLon = group.map { it.lon }.average(),
                                    isMetrobus = false
                                )
                            }
                        }
                        if (currentFilter.showMetrobus && mbStopsSingle.isNotEmpty()) {
                            mbClusters = mbStopsSingle.groupBy { stop ->
                                Pair((stop.lat / gridSize).toInt(), (stop.lon / gridSize).toInt())
                            }.values.map { group ->
                                GoogleMapTransitItem.Cluster(
                                    count = group.size,
                                    centerLat = group.map { it.lat }.average(),
                                    centerLon = group.map { it.lon }.average(),
                                    isMetrobus = true
                                )
                            }
                        }
                    }

                    val sDotsBus = if (currentFilter.showBus && bStopsSingle.isNotEmpty()) {
                        if (currentFilter.isFavorites) bStopsSingle
                        else SpatialGridSampler.sampleUniformlyByGrid(
                            items = bStopsSingle,
                            maxCount = 200,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        )
                    } else emptyList()

                    val sDotsMb = if (currentFilter.showMetrobus && mbStopsSingle.isNotEmpty()) {
                        if (currentFilter.isFavorites) mbStopsSingle
                        else SpatialGridSampler.sampleUniformlyByGrid(
                            items = mbStopsSingle,
                            maxCount = 100,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        )
                    } else emptyList()

                    val sDetailedBus = if (currentFilter.showBus && bStopsSingle.isNotEmpty()) {
                        if (currentFilter.isFavorites) bStopsSingle
                        else SpatialGridSampler.sampleUniformlyByGrid(
                            items = bStopsSingle,
                            maxCount = 300,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        )
                    } else emptyList()

                    val sDetailedMb = if (currentFilter.showMetrobus && mbStopsSingle.isNotEmpty()) {
                        if (currentFilter.isFavorites) mbStopsSingle
                        else SpatialGridSampler.sampleUniformlyByGrid(
                            items = mbStopsSingle,
                            maxCount = 150,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        )
                    } else emptyList()

                    withContext(Dispatchers.Main) {
                        currentMergedGroups = merged
                        busClusters = bClusters
                        metrobusClusters = mbClusters
                        sampledDotsBus = sDotsBus
                        sampledDotsMb = sDotsMb
                        standaloneBusDetailed = sDetailedBus
                        standaloneMbDetailed = sDetailedMb
                    }
                }
            }
        }
    }

    return remember(
        currentBoundingBox,
        currentMergedGroups,
        busClusters,
        metrobusClusters,
        standaloneBusDetailed,
        standaloneMbDetailed,
        sampledDotsBus,
        sampledDotsMb,
        showBusLayer,
        showMetrobusLayer,
        isZoomForClusters,
        isZoomForBusDots,
        isZoomForBusBadges,
        isZoomForBusDetailed,
        isOnlyBusesSelected,
        isOnlyValenbisi
    ) {
        GoogleMapViewportTransitState(
            currentBoundingBox = currentBoundingBox,
            currentMergedGroups = currentMergedGroups,
            busClusters = busClusters,
            metrobusClusters = metrobusClusters,
            standaloneBusDetailed = standaloneBusDetailed,
            standaloneMbDetailed = standaloneMbDetailed,
            sampledDotsBus = sampledDotsBus,
            sampledDotsMb = sampledDotsMb,
            showBusLayer = showBusLayer,
            showMetrobusLayer = showMetrobusLayer,
            isZoomForClusters = isZoomForClusters,
            isZoomForBusDots = isZoomForBusDots,
            isZoomForBusBadges = isZoomForBusBadges,
            isZoomForBusDetailed = isZoomForBusDetailed,
            isOnlyBusesSelected = isOnlyBusesSelected,
            isOnlyValenbisi = isOnlyValenbisi
        )
    }
}
