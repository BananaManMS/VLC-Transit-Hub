package com.example.ui.map.google

import android.content.Context
import android.graphics.Color as AndroidColor
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
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

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

    // 1. LOD 1 (Zoom < 13.5): Draw EMT & Metrobús Clusters
    if (isZoomForClusters && activeIsolatedBusLines.isEmpty()) {
        if (showBusStops) {
            busClusters.forEachIndexed { idx, cluster ->
                val clusterKey = "BUS_CLUSTER_${idx}_${cluster.centerLat}_${cluster.centerLon}"
                key(clusterKey) {
                    val iconInfo = remember(cluster.count) {
                        GoogleMapsMarkerCache.getOrCreateClusterIcon(context, cluster.count, AndroidColor.parseColor("#E53935"))
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
                        GoogleMapsMarkerCache.getOrCreateClusterIcon(context, cluster.count, AndroidColor.parseColor("#FFB300"))
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

    // 2. LOD 2 (Zoom 13.5..15.2): Draw Compact Dots
    if (isZoomForBusDots) {
        val emtDot = remember { GoogleMapsMarkerCache.getOrCreateCompactDotIcon(context, AndroidColor.parseColor("#E53935")) }
        val mbDot = remember { GoogleMapsMarkerCache.getOrCreateCompactDotIcon(context, AndroidColor.parseColor("#FFB300")) }
        val mergedDot = remember { GoogleMapsMarkerCache.getOrCreateMergedDotIcon(context) }
        val isDimmedByOtherMode = selectedMapItem is SelectedMapItem.Metro || selectedMapItem is SelectedMapItem.Cercanias

        // Merged dots (only if both EMT and Metrobús are visible)
        if (showBusStops && showMetrobusStops) {
            currentMergedGroups.forEach { group ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Merged)?.busStop?.id_parada == group.busStop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada == group.busStop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id == group.mbStop.id
                if (isSelected) return@forEach

                val stopLines = remember(group) {
                    ((group.busStop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() } +
                     (group.mbStop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }).filter { it.isNotEmpty() }.toSet()
                }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("MERGED_DOT_${group.busStop.id_parada}_${group.mbStop.id}") {
                    Marker(
                        state = rememberMarkerState(key = "MERGED_DOT_${group.busStop.id_parada}_${group.mbStop.id}", position = LatLng(group.busStop.lat, group.busStop.lon)),
                        icon = mergedDot.descriptor,
                        anchor = mergedDot.anchor,
                        alpha = markerAlpha,
                        title = "${group.busStop.denominacion} (EMT + Metrobús)",
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

        // Standalone EMT dots
        if (showBusStops) {
            sampledDotsBus.forEach { stop ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Bus)?.stop?.id_parada == stop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada == stop.id_parada
                if (isSelected) return@forEach

                val stopLines = remember(stop.lineas) { (stop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }.filter { it.isNotEmpty() } }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("BUS_DOT_${stop.id_parada}") {
                    Marker(
                        state = rememberMarkerState(key = "BUS_DOT_${stop.id_parada}", position = LatLng(stop.lat, stop.lon)),
                        icon = emtDot.descriptor,
                        anchor = emtDot.anchor,
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

        // Standalone Metrobús dots
        if (showMetrobusStops) {
            sampledDotsMb.forEach { stop ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Metrobus)?.stop?.id == stop.id ||
                                 (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id == stop.id
                if (isSelected) return@forEach

                val stopLines = remember(stop.lineas) { (stop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }.filter { it.isNotEmpty() } }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("MB_DOT_${stop.id}") {
                    Marker(
                        state = rememberMarkerState(key = "MB_DOT_${stop.id}", position = LatLng(stop.lat, stop.lon)),
                        icon = mbDot.descriptor,
                        anchor = mbDot.anchor,
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

    // 3. LOD 3A (Zoom 15.2..17.8): Draw Clean Icon Badges (WITHOUT text name and WITHOUT line pills)
    if (isZoomForBusBadges) {
        val isDimmedByOtherMode = selectedMapItem is SelectedMapItem.Metro || selectedMapItem is SelectedMapItem.Cercanias

        // Merged stops (only if both EMT and Metrobús are visible)
        if (showBusStops && showMetrobusStops) {
            currentMergedGroups.forEach { group ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Merged)?.busStop?.id_parada == group.busStop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada == group.busStop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id == group.mbStop.id
                if (isSelected) return@forEach

                val stopLines = remember(group) {
                    ((group.busStop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() } +
                     (group.mbStop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }).filter { it.isNotEmpty() }.toSet()
                }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("MERGED_BADGE_CLEAN_${group.busStop.id_parada}_${group.mbStop.id}") {
                    val iconInfo = remember(isDarkMode) {
                        GoogleMapsMarkerCache.getOrCreateMergedCompactIconBadge(context, isDarkMode)
                    }
                    Marker(
                        state = rememberMarkerState(key = "MERGED_BADGE_CLEAN_${group.busStop.id_parada}_${group.mbStop.id}", position = LatLng(group.busStop.lat, group.busStop.lon)),
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        alpha = markerAlpha,
                        title = "${group.busStop.denominacion} (EMT + Metrobús)",
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

        // Standalone EMT Bus stops
        if (showBusStops) {
            val emtBadge = remember { GoogleMapsMarkerCache.getOrCreateBusIconBadge(context, isMetrobus = false) }
            standaloneBusDetailed.forEach { stop ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Bus)?.stop?.id_parada == stop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada == stop.id_parada
                if (isSelected) return@forEach

                val stopLines = remember(stop.lineas) { (stop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }.filter { it.isNotEmpty() } }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("BUS_BADGE_CLEAN_${stop.id_parada}") {
                    Marker(
                        state = rememberMarkerState(key = "BUS_BADGE_CLEAN_${stop.id_parada}", position = LatLng(stop.lat, stop.lon)),
                        icon = emtBadge.descriptor,
                        anchor = emtBadge.anchor,
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
            val mbBadge = remember { GoogleMapsMarkerCache.getOrCreateBusIconBadge(context, isMetrobus = true) }
            standaloneMbDetailed.forEach { stop ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Metrobus)?.stop?.id == stop.id ||
                                 (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id == stop.id
                if (isSelected) return@forEach

                val stopLines = remember(stop.lineas) { (stop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }.filter { it.isNotEmpty() } }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("MB_BADGE_CLEAN_${stop.id}") {
                    Marker(
                        state = rememberMarkerState(key = "MB_BADGE_CLEAN_${stop.id}", position = LatLng(stop.lat, stop.lon)),
                        icon = mbBadge.descriptor,
                        anchor = mbBadge.anchor,
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

    // 4. LOD 3B (Zoom >= 17.8): Draw Full Detailed Pins WITH Text Station Name and Line Pills
    if (isZoomForBusDetailed) {
        val isDimmedByOtherMode = selectedMapItem is SelectedMapItem.Metro || selectedMapItem is SelectedMapItem.Cercanias

        // Merged stops (only if both EMT and Metrobús are visible)
        if (showBusStops && showMetrobusStops) {
            currentMergedGroups.forEach { group ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Merged)?.busStop?.id_parada == group.busStop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada == group.busStop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id == group.mbStop.id
                if (isSelected) return@forEach

                val stopLines = remember(group) {
                    ((group.busStop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() } +
                     (group.mbStop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }).filter { it.isNotEmpty() }.toSet()
                }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("MERGED_BADGE_${group.busStop.id_parada}_${group.mbStop.id}") {
                    val isFav = favoriteBusStops.contains(group.busStop.id_parada) || favoriteBusStops.contains(group.mbStop.id)
                    val alias = busStopAliases[group.busStop.id_parada] ?: busStopAliases[group.mbStop.id]
                    val iconInfo = remember(group.busStop.denominacion, group.busStop.lineas, group.mbStop.lineas, isDarkMode, alias, isFav, isDimmed) {
                        if (isDimmed) {
                            GoogleMapsMarkerCache.getOrCreateMergedDotIcon(context)
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

                    Marker(
                        state = rememberMarkerState(key = "MERGED_BADGE_${group.busStop.id_parada}_${group.mbStop.id}", position = LatLng(group.busStop.lat, group.busStop.lon)),
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        alpha = markerAlpha,
                        title = group.busStop.denominacion,
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

        // Standalone EMT Bus stops
        if (showBusStops) {
            standaloneBusDetailed.forEach { stop ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Bus)?.stop?.id_parada == stop.id_parada ||
                                 (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada == stop.id_parada
                if (isSelected) return@forEach

                val stopLines = remember(stop.lineas) { (stop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }.filter { it.isNotEmpty() } }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("BUS_BADGE_${stop.id_parada}") {
                    val isFav = favoriteBusStops.contains(stop.id_parada)
                    val alias = busStopAliases[stop.id_parada]
                    val iconInfo = remember(stop.denominacion, stop.lineas, isDarkMode, alias, isFav, isDimmed) {
                        if (isDimmed) {
                            GoogleMapsMarkerCache.getOrCreateCompactDotIcon(context, AndroidColor.parseColor("#E53935"))
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

                    Marker(
                        state = rememberMarkerState(key = "BUS_BADGE_${stop.id_parada}", position = LatLng(stop.lat, stop.lon)),
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
            standaloneMbDetailed.forEach { stop ->
                val isSelected = (selectedItem as? GoogleMapTransitItem.Metrobus)?.stop?.id == stop.id ||
                                 (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id == stop.id
                if (isSelected) return@forEach

                val stopLines = remember(stop.lineas) { (stop.lineas ?: "").split(",").map { MetrobusStationHighlightManager.normalizeLine(it).uppercase() }.filter { it.isNotEmpty() } }
                val sharesLine = stopLines.any { activeIsolatedBusLines.contains(it) }
                if (activeIsolatedBusLines.isNotEmpty() && !sharesLine) return@forEach

                val isDimmedByBusHighlight = activeIsolatedBusLines.isNotEmpty() && !sharesLine
                val isDimmed = isDimmedByBusHighlight || isDimmedByOtherMode
                val isHighlightedRoute = activeIsolatedBusLines.isNotEmpty() && sharesLine

                val markerAlpha = if (isDimmed) 0.25f else 1.0f
                val markerZIndex = if (isDimmed) 2f else if (isHighlightedRoute) 32f else 24f

                key("MB_BADGE_${stop.id}") {
                    val isFav = favoriteBusStops.contains(stop.id)
                    val alias = busStopAliases[stop.id]
                    val iconInfo = remember(stop.denominacion, stop.lineas, isDarkMode, alias, isFav, isDimmed) {
                        if (isDimmed) {
                            GoogleMapsMarkerCache.getOrCreateCompactDotIcon(context, AndroidColor.parseColor("#FFB300"))
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

                    Marker(
                        state = rememberMarkerState(key = "MB_BADGE_${stop.id}", position = LatLng(stop.lat, stop.lon)),
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
