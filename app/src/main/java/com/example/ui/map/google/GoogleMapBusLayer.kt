package com.example.ui.map.google

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.BusMarkersRenderer
import com.example.ui.map.components.MetrobusStationHighlightManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

private const val COLOR_EMT_RED = 0xFFE53935.toInt()
private const val COLOR_MB_AMBER = 0xFFFFB300.toInt()

private val normalizedStopLinesCache = LruCache<String, List<String>>(512)

internal fun getNormalizedStopLines(lineas: String?): List<String> {
    if (lineas.isNullOrBlank()) return emptyList()
    normalizedStopLinesCache.get(lineas)?.let { return it }
    val parsed = lineas.split(",")
        .map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }
        .filter { it.isNotEmpty() }
    normalizedStopLinesCache.put(lineas, parsed)
    return parsed
}

@Composable
internal fun GoogleMapBusLayer(
    isZoomForClusters: Boolean,
    isZoomForBusDots: Boolean,
    isZoomForBusBadges: Boolean,
    isZoomForBusDetailed: Boolean,
    showBusStops: Boolean,
    showMetrobusStops: Boolean,
    isMetroActive: Boolean,
    isCercaniasActive: Boolean,
    activeIsolatedBusLines: Set<String>,
    busClusters: List<GoogleMapTransitItem.Cluster>,
    metrobusClusters: List<GoogleMapTransitItem.Cluster>,
    currentMergedGroups: List<BusMarkersRenderer.MergedBusStopGroup>,
    sampledDotsBus: List<GeoportalStopEntity>,
    sampledDotsMb: List<MetrobusStopEntity>,
    standaloneBusDetailed: List<GeoportalStopEntity>,
    standaloneMbDetailed: List<MetrobusStopEntity>,
    favoriteBusStops: Set<String>,
    busStopAliases: Map<String, String>,
    selectedItem: GoogleMapTransitItem?,
    selectedMapItem: SelectedMapItem?,
    isDarkMode: Boolean,
    currentZoom: Float,
    cameraPositionState: CameraPositionState,
    context: Context,
    handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    val hasActiveLineIsolation = activeIsolatedBusLines.isNotEmpty()

    // 1. LOD 1 (Zoom < 13.5): Draw EMT & Metrobús Clusters
    if (isZoomForClusters && !hasActiveLineIsolation) {
        if (showBusStops) {
            busClusters.forEachIndexed { idx, cluster ->
                val clusterKey = "BUS_CLUSTER_${idx}_${cluster.centerLat}_${cluster.centerLon}"
                key(clusterKey) {
                    val iconInfo = remember(cluster.count) {
                        GoogleMapsMarkerCache.getOrCreateClusterIcon(context, cluster.count, COLOR_EMT_RED)
                    }
                    val markerState = rememberMarkerState(
                        key = clusterKey,
                        position = LatLng(cluster.centerLat, cluster.centerLon)
                    )
                    Marker(
                        state = markerState,
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        title = cluster.title,
                        zIndex = 22f,
                        onClick = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(cluster.centerLat, cluster.centerLon),
                                        currentZoom + 2.2f
                                    ),
                                    durationMs = 400
                                )
                            }
                            true
                        }
                    )
                }
            }
        }

        if (showMetrobusStops) {
            metrobusClusters.forEachIndexed { idx, cluster ->
                val clusterKey = "MB_CLUSTER_${idx}_${cluster.centerLat}_${cluster.centerLon}"
                key(clusterKey) {
                    val iconInfo = remember(cluster.count) {
                        GoogleMapsMarkerCache.getOrCreateClusterIcon(context, cluster.count, COLOR_MB_AMBER)
                    }
                    val markerState = rememberMarkerState(
                        key = clusterKey,
                        position = LatLng(cluster.centerLat, cluster.centerLon)
                    )
                    Marker(
                        state = markerState,
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        title = cluster.title,
                        zIndex = 22f,
                        onClick = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(cluster.centerLat, cluster.centerLon),
                                        currentZoom + 2.2f
                                    ),
                                    durationMs = 400
                                )
                            }
                            true
                        }
                    )
                }
            }
        }
    }

    // 2..4. Unified LOD tier (1 = Dots, 2 = Clean Badges, 3 = Full Detailed Pins).
    // Sharing canonical keys across tiers updates icons/anchors in-place instead of destroying & recreating native Markers.
    val busLodTier = when {
        isZoomForBusDetailed -> 3
        isZoomForBusBadges -> 2
        isZoomForBusDots -> 1
        else -> 0
    }

    if (busLodTier > 0) {
        val isDimmedByOtherMode = selectedMapItem is SelectedMapItem.Metro || selectedMapItem is SelectedMapItem.Cercanias
        val emtDot = remember { GoogleMapsMarkerCache.getOrCreateCompactDotIcon(context, COLOR_EMT_RED) }
        val mbDot = remember { GoogleMapsMarkerCache.getOrCreateCompactDotIcon(context, COLOR_MB_AMBER) }
        val mergedDot = remember { GoogleMapsMarkerCache.getOrCreateMergedDotIcon(context) }
        val emtBadge = remember { GoogleMapsMarkerCache.getOrCreateBusIconBadge(context, isMetrobus = false) }
        val mbBadge = remember { GoogleMapsMarkerCache.getOrCreateBusIconBadge(context, isMetrobus = true) }
        val mergedCleanBadge = remember(isDarkMode) {
            GoogleMapsMarkerCache.getOrCreateMergedCompactIconBadge(context, isDarkMode)
        }

        val selectedBusId = (selectedItem as? GoogleMapTransitItem.Bus)?.stop?.id_parada
            ?: (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada
        val selectedMbId = (selectedItem as? GoogleMapTransitItem.Metrobus)?.stop?.id
            ?: (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id
        val selectedMergedBusId = (selectedItem as? GoogleMapTransitItem.Merged)?.busStop?.id_parada

        // Merged stops (only if both EMT and Metrobús are visible)
        if (showBusStops && showMetrobusStops) {
            currentMergedGroups.forEach { group ->
                val isSelected = selectedMergedBusId == group.busStop.id_parada ||
                    selectedBusId == group.busStop.id_parada ||
                    selectedMbId == group.mbStop.id
                if (isSelected) return@forEach

                val sharesLine = if (hasActiveLineIsolation) {
                    getNormalizedStopLines(group.busStop.lineas).any { activeIsolatedBusLines.contains(it) } ||
                        getNormalizedStopLines(group.mbStop.lineas).any { activeIsolatedBusLines.contains(it) }
                } else {
                    false
                }
                if (hasActiveLineIsolation && !sharesLine) return@forEach

                val isDimmed = isDimmedByOtherMode
                val isHighlightedRoute = hasActiveLineIsolation && sharesLine
                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                val markerKey = "MERGED_${group.busStop.id_parada}_${group.mbStop.id}"
                key(markerKey) {
                    val isFav = if (busLodTier == 3) {
                        favoriteBusStops.contains(group.busStop.id_parada) || favoriteBusStops.contains(group.mbStop.id)
                    } else false
                    val alias = if (busLodTier == 3) {
                        busStopAliases[group.busStop.id_parada] ?: busStopAliases[group.mbStop.id]
                    } else null

                    val iconInfo = when (busLodTier) {
                        1 -> mergedDot
                        2 -> mergedCleanBadge
                        else -> remember(
                            group.busStop.denominacion,
                            group.busStop.lineas,
                            group.mbStop.lineas,
                            isDarkMode,
                            alias,
                            isFav,
                            isDimmed
                        ) {
                            if (isDimmed) {
                                mergedDot
                            } else {
                                GoogleMapsMarkerCache.getOrCreateMergedStopIcon(
                                    context = context,
                                    stopName = group.busStop.denominacion,
                                    emtLinesString = group.busStop.lineas,
                                    metrobusLinesString = group.mbStop.lineas,
                                    isDarkMode = isDarkMode,
                                    alias = alias,
                                    isFavorite = isFav
                                )
                            }
                        }
                    }

                    val title = if (busLodTier == 3) group.busStop.denominacion else "${group.busStop.denominacion} (EMT + Metrobús)"

                    Marker(
                        state = rememberMarkerState(key = markerKey, position = LatLng(group.busStop.lat, group.busStop.lon)),
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        alpha = markerAlpha,
                        title = title,
                        zIndex = markerZIndex,
                        onClick = {
                            val item = GoogleMapTransitItem.Merged(group.busStop, group.mbStop)
                            handleTransitItemTap(item, group.busStop.lat, group.busStop.lon)
                            true
                        }
                    )
                }
            }
        }

        val busList = if (busLodTier == 1) sampledDotsBus else standaloneBusDetailed
        val mbList = if (busLodTier == 1) sampledDotsMb else standaloneMbDetailed

        // Standalone EMT Bus stops
        if (showBusStops) {
            busList.forEach { stop ->
                if (selectedBusId == stop.id_parada) return@forEach

                val sharesLine = if (hasActiveLineIsolation) {
                    getNormalizedStopLines(stop.lineas).any { activeIsolatedBusLines.contains(it) }
                } else {
                    false
                }
                if (hasActiveLineIsolation && !sharesLine) return@forEach

                val isDimmed = isDimmedByOtherMode
                val isHighlightedRoute = hasActiveLineIsolation && sharesLine
                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                val markerKey = "BUS_${stop.id_parada}"
                key(markerKey) {
                    val isFav = if (busLodTier == 3) favoriteBusStops.contains(stop.id_parada) else false
                    val alias = if (busLodTier == 3) busStopAliases[stop.id_parada] else null

                    val iconInfo = when (busLodTier) {
                        1 -> emtDot
                        2 -> emtBadge
                        else -> remember(stop.denominacion, stop.lineas, isDarkMode, alias, isFav, isDimmed) {
                            if (isDimmed) {
                                emtDot
                            } else {
                                GoogleMapsMarkerCache.getOrCreateBusStopIcon(
                                    context = context,
                                    stopName = stop.denominacion,
                                    linesString = stop.lineas,
                                    isDarkMode = isDarkMode,
                                    isMetrobus = false,
                                    alias = alias,
                                    isFavorite = isFav
                                )
                            }
                        }
                    }

                    Marker(
                        state = rememberMarkerState(key = markerKey, position = LatLng(stop.lat, stop.lon)),
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        alpha = markerAlpha,
                        title = stop.denominacion,
                        zIndex = markerZIndex,
                        onClick = {
                            val item = GoogleMapTransitItem.Bus(stop)
                            handleTransitItemTap(item, stop.lat, stop.lon)
                            true
                        }
                    )
                }
            }
        }

        // Standalone Metrobús stops
        if (showMetrobusStops) {
            mbList.forEach { stop ->
                if (selectedMbId == stop.id) return@forEach

                val sharesLine = if (hasActiveLineIsolation) {
                    getNormalizedStopLines(stop.lineas).any { activeIsolatedBusLines.contains(it) }
                } else {
                    false
                }
                if (hasActiveLineIsolation && !sharesLine) return@forEach

                val isDimmed = isDimmedByOtherMode
                val isHighlightedRoute = hasActiveLineIsolation && sharesLine
                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                val markerKey = "MB_${stop.id}"
                key(markerKey) {
                    val isFav = if (busLodTier == 3) favoriteBusStops.contains(stop.id) else false
                    val alias = if (busLodTier == 3) busStopAliases[stop.id] else null

                    val iconInfo = when (busLodTier) {
                        1 -> mbDot
                        2 -> mbBadge
                        else -> remember(stop.denominacion, stop.lineas, isDarkMode, alias, isFav, isDimmed) {
                            if (isDimmed) {
                                mbDot
                            } else {
                                GoogleMapsMarkerCache.getOrCreateBusStopIcon(
                                    context = context,
                                    stopName = stop.denominacion,
                                    linesString = stop.lineas,
                                    isDarkMode = isDarkMode,
                                    isMetrobus = true,
                                    alias = alias,
                                    isFavorite = isFav
                                )
                            }
                        }
                    }

                    Marker(
                        state = rememberMarkerState(key = markerKey, position = LatLng(stop.lat, stop.lon)),
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        alpha = markerAlpha,
                        title = stop.denominacion,
                        zIndex = markerZIndex,
                        onClick = {
                            val item = GoogleMapTransitItem.Metrobus(stop)
                            handleTransitItemTap(item, stop.lat, stop.lon)
                            true
                        }
                    )
                }
            }
        }
    }

    // 5. Selected Stop Override (Rendered on top at ANY zoom)
    val selectedBusStop = (selectedItem as? GoogleMapTransitItem.Bus)?.stop
        ?: (selectedMapItem as? SelectedMapItem.BusStop)?.stop
    val selectedMbStop = (selectedItem as? GoogleMapTransitItem.Metrobus)?.stop
        ?: (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop
    val selectedMergedItem = selectedItem as? GoogleMapTransitItem.Merged

    if (selectedBusStop != null) {
        val selectedKey = "SELECTED_BUS_${selectedBusStop.id_parada}"
        key(selectedKey) {
            val iconInfo = remember(selectedBusStop.denominacion, selectedBusStop.lineas, isDarkMode) {
                GoogleMapsMarkerCache.getOrCreateBusStopIcon(
                    context = context,
                    stopName = selectedBusStop.denominacion,
                    linesString = selectedBusStop.lineas,
                    isDarkMode = isDarkMode,
                    isMetrobus = false
                )
            }
            Marker(
                state = rememberMarkerState(key = selectedKey, position = LatLng(selectedBusStop.lat, selectedBusStop.lon)),
                icon = iconInfo.descriptor,
                anchor = iconInfo.anchor,
                title = selectedBusStop.denominacion,
                zIndex = 45f,
                onClick = {
                    handleTransitItemTap(GoogleMapTransitItem.Bus(selectedBusStop), selectedBusStop.lat, selectedBusStop.lon)
                    true
                }
            )
        }
    } else if (selectedMbStop != null) {
        val selectedKey = "SELECTED_MB_${selectedMbStop.id}"
        key(selectedKey) {
            val iconInfo = remember(selectedMbStop.denominacion, selectedMbStop.lineas, isDarkMode) {
                GoogleMapsMarkerCache.getOrCreateBusStopIcon(
                    context = context,
                    stopName = selectedMbStop.denominacion,
                    linesString = selectedMbStop.lineas,
                    isDarkMode = isDarkMode,
                    isMetrobus = true
                )
            }
            Marker(
                state = rememberMarkerState(key = selectedKey, position = LatLng(selectedMbStop.lat, selectedMbStop.lon)),
                icon = iconInfo.descriptor,
                anchor = iconInfo.anchor,
                title = selectedMbStop.denominacion,
                zIndex = 45f,
                onClick = {
                    handleTransitItemTap(GoogleMapTransitItem.Metrobus(selectedMbStop), selectedMbStop.lat, selectedMbStop.lon)
                    true
                }
            )
        }
    } else if (selectedMergedItem != null) {
        val selectedKey = "SELECTED_MERGED_${selectedMergedItem.busStop.id_parada}_${selectedMergedItem.mbStop.id}"
        key(selectedKey) {
            val iconInfo = remember(selectedMergedItem.busStop.denominacion, selectedMergedItem.busStop.lineas, selectedMergedItem.mbStop.lineas, isDarkMode) {
                GoogleMapsMarkerCache.getOrCreateMergedStopIcon(
                    context = context,
                    stopName = selectedMergedItem.busStop.denominacion,
                    emtLinesString = selectedMergedItem.busStop.lineas,
                    metrobusLinesString = selectedMergedItem.mbStop.lineas,
                    isDarkMode = isDarkMode
                )
            }
            Marker(
                state = rememberMarkerState(key = selectedKey, position = LatLng(selectedMergedItem.busStop.lat, selectedMergedItem.busStop.lon)),
                icon = iconInfo.descriptor,
                anchor = iconInfo.anchor,
                title = selectedMergedItem.busStop.denominacion,
                zIndex = 45f,
                onClick = {
                    handleTransitItemTap(selectedMergedItem, selectedMergedItem.busStop.lat, selectedMergedItem.busStop.lon)
                    true
                }
            )
        }
    }
}
