package com.example.ui.map.google

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.map.MapFilter
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.ValenbisiStation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

@Composable
internal fun GoogleMapValenbisiLayer(
    showValenbisi: Boolean,
    valenbisiToRender: List<ValenbisiStation>,
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
    settledZoom: Float,
    mapFilter: MapFilter,
    isOnlyValenbisi: Boolean,
    selectedItem: GoogleMapTransitItem?,
    selectedMapItem: SelectedMapItem?,
    isBusActive: Boolean,
    isMetroActive: Boolean,
    isCercaniasActive: Boolean,
    isDarkMode: Boolean,
    cameraPositionState: CameraPositionState,
    context: Context,
    handleTransitItemTap: (GoogleMapTransitItem, Double, Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    val isValenbisiActiveFilter = showValenbisi && (mapFilter.isFavorites || mapFilter.showValenbisi)
    if (isValenbisiActiveFilter && valenbisiToRender.isNotEmpty()) {
        val filteredValenbisi = remember(valenbisiToRender, minLat, maxLat, minLon, maxLon) {
            valenbisiToRender.filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
        }

        if (settledZoom < 13.5f && !mapFilter.isFavorites) {
            if (isOnlyValenbisi) {
                val isFarCluster = settledZoom < 11.0f
                val valenbisiClusters = remember(valenbisiToRender, isFarCluster) {
                    val gridSize = if (isFarCluster) 0.06 else 0.025
                    valenbisiToRender.groupBy { station ->
                        Pair((station.latitude / gridSize).toInt(), (station.longitude / gridSize).toInt())
                    }.values.map { group ->
                        GoogleMapTransitItem.Cluster(
                            count = group.size,
                            centerLat = group.map { it.latitude }.average(),
                            centerLon = group.map { it.longitude }.average(),
                            isMetrobus = false
                        )
                    }
                }

                valenbisiClusters.forEachIndexed { idx, cluster ->
                    val clusterKey = "VALENBISI_CLUSTER_${idx}_${cluster.centerLat}_${cluster.centerLon}"
                    key(clusterKey) {
                        val iconInfo = remember(cluster.count) {
                            GoogleMapsMarkerCache.getOrCreateClusterIcon(context, cluster.count, AndroidColor.parseColor("#10B981"))
                        }
                        Marker(
                            state = rememberMarkerState(key = clusterKey, position = LatLng(cluster.centerLat, cluster.centerLon)),
                            icon = iconInfo.descriptor,
                            anchor = iconInfo.anchor,
                            title = "${cluster.count} Estaciones de Valenbisi",
                            zIndex = 16f,
                            onClick = {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(cluster.centerLat, cluster.centerLon),
                                            settledZoom + 2.2f
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
        } else if (settledZoom <= 14.8f && !mapFilter.isFavorites) {
            filteredValenbisi.forEach { station ->
                val dotKey = "VALENBISI_DOT_${station.gid}"
                key(dotKey) {
                    val isValenbisiSelected = (selectedItem as? GoogleMapTransitItem.Valenbisi)?.station?.gid == station.gid ||
                                              (selectedMapItem as? SelectedMapItem.Valenbisi)?.station?.gid == station.gid
                    val isDimmedValenbisi = !isValenbisiSelected && (isBusActive || isMetroActive || isCercaniasActive)
                    val valenbisiAlpha = if (isValenbisiSelected) 1.0f else if (isDimmedValenbisi) 0.25f else 1.0f
                    val valenbisiZIndex = if (isValenbisiSelected) 45f else if (isDimmedValenbisi) 4f else 22f

                    val dotInfo = remember(station.available) {
                        GoogleMapsMarkerCache.getOrCreateValenbisiCompactDotIcon(context, station.available)
                    }
                    Marker(
                        state = rememberMarkerState(key = dotKey, position = LatLng(station.latitude, station.longitude)),
                        icon = dotInfo.descriptor,
                        anchor = dotInfo.anchor,
                        alpha = valenbisiAlpha,
                        title = station.name,
                        snippet = "Bicis: ${station.available} • Huecos: ${station.free}",
                        zIndex = valenbisiZIndex,
                        onClick = {
                            handleTransitItemTap(GoogleMapTransitItem.Valenbisi(station), station.latitude, station.longitude)
                            true
                        }
                    )
                }
            }
        } else {
            val showPill = settledZoom >= 15.5f || mapFilter.isFavorites
            filteredValenbisi.forEach { station ->
                val badgeKey = "VALENBISI_BADGE_${station.gid}_$showPill"
                key(badgeKey) {
                    val isValenbisiSelected = (selectedItem as? GoogleMapTransitItem.Valenbisi)?.station?.gid == station.gid ||
                                              (selectedMapItem as? SelectedMapItem.Valenbisi)?.station?.gid == station.gid
                    val isDimmedValenbisi = !isValenbisiSelected && (isBusActive || isMetroActive || isCercaniasActive)
                    val valenbisiAlpha = if (isValenbisiSelected) 1.0f else if (isDimmedValenbisi) 0.25f else 1.0f
                    val valenbisiZIndex = if (isValenbisiSelected) 45f else if (isDimmedValenbisi) 4f else 22f

                    val badgeInfo = remember(station.available, station.free, isDarkMode, showPill) {
                        GoogleMapsMarkerCache.getOrCreateValenbisiMarkerIcon(
                            context = context,
                            available = station.available,
                            free = station.free,
                            isDarkMode = isDarkMode,
                            showPill = showPill
                        )
                    }
                    Marker(
                        state = rememberMarkerState(key = badgeKey, position = LatLng(station.latitude, station.longitude)),
                        icon = badgeInfo.descriptor,
                        anchor = badgeInfo.anchor,
                        alpha = valenbisiAlpha,
                        title = station.name,
                        snippet = "Disponibles: ${station.available} • Huecos: ${station.free}",
                        zIndex = valenbisiZIndex,
                        onClick = {
                            handleTransitItemTap(GoogleMapTransitItem.Valenbisi(station), station.latitude, station.longitude)
                            true
                        }
                    )
                }
            }
        }

        // Always render selected Valenbisi station prominently on top
        val selectedValenbisi: ValenbisiStation? = (selectedItem as? GoogleMapTransitItem.Valenbisi)?.station
            ?: (selectedMapItem as? SelectedMapItem.Valenbisi)?.station
        if (selectedValenbisi != null) {
            val station = selectedValenbisi
            val selectedKey = "SELECTED_VALENBISI_${station.gid}"
            key(selectedKey) {
                val badgeInfo = remember(station.available, station.free, isDarkMode) {
                    GoogleMapsMarkerCache.getOrCreateValenbisiMarkerIcon(
                        context = context,
                        available = station.available,
                        free = station.free,
                        isDarkMode = isDarkMode,
                        showPill = true
                    )
                }
                Marker(
                    state = rememberMarkerState(key = selectedKey, position = LatLng(station.latitude, station.longitude)),
                    icon = badgeInfo.descriptor,
                    anchor = badgeInfo.anchor,
                    title = station.name,
                    snippet = "Disponibles: ${station.available} • Huecos: ${station.free}",
                    zIndex = 45f
                )
            }
        }
    }
}
