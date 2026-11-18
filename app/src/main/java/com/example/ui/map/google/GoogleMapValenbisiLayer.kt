package com.example.ui.map.google

import android.content.Context
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

private const val COLOR_VALENBISI_GREEN = 0xFF10B981.toInt()

private class ValenbisiClusterBucket(
    var latSum: Double = 0.0,
    var lonSum: Double = 0.0,
    var count: Int = 0
)

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
        val isClusterMode = settledZoom < 13.5f && !mapFilter.isFavorites
        val filteredValenbisi = remember(valenbisiToRender, minLat, maxLat, minLon, maxLon, isClusterMode) {
            if (isClusterMode) emptyList()
            else valenbisiToRender.filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
        }

        if (isClusterMode) {
            if (isOnlyValenbisi) {
                val isFarCluster = settledZoom < 11.0f
                val valenbisiClusters = remember(valenbisiToRender, isFarCluster) {
                    val gridSize = if (isFarCluster) 0.06 else 0.025
                    val buckets = HashMap<Long, ValenbisiClusterBucket>(64)
                    for (i in 0 until valenbisiToRender.size) {
                        val station = valenbisiToRender[i]
                        val cellLat = (station.latitude / gridSize).toInt()
                        val cellLon = (station.longitude / gridSize).toInt()
                        val key = (cellLat.toLong() shl 32) or (cellLon.toLong() and 0xFFFFFFFFL)
                        val bucket = buckets[key]
                        if (bucket == null) {
                            buckets[key] = ValenbisiClusterBucket(station.latitude, station.longitude, 1)
                        } else {
                            bucket.latSum += station.latitude
                            bucket.lonSum += station.longitude
                            bucket.count++
                        }
                    }
                    val result = ArrayList<GoogleMapTransitItem.Cluster>(buckets.size)
                    for (bucket in buckets.values) {
                        result.add(
                            GoogleMapTransitItem.Cluster(
                                count = bucket.count,
                                centerLat = bucket.latSum / bucket.count,
                                centerLon = bucket.lonSum / bucket.count,
                                isMetrobus = false
                            )
                        )
                    }
                    result
                }

                valenbisiClusters.forEachIndexed { idx, cluster ->
                    val clusterKey = "VALENBISI_CLUSTER_${idx}_${cluster.centerLat}_${cluster.centerLon}"
                    key(clusterKey) {
                        val iconInfo = remember(cluster.count) {
                            GoogleMapsMarkerCache.getOrCreateClusterIcon(context, cluster.count, COLOR_VALENBISI_GREEN)
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
        } else {
            // Unified marker key across Dot (<= 14.8), No-Pill (14.8..15.5), and Pill (>= 15.5)
            // updates icon/anchor in-place instead of destroying and re-adding native markers.
            val isDotMode = settledZoom <= 14.8f && !mapFilter.isFavorites
            val showPill = !isDotMode && (settledZoom >= 15.5f || mapFilter.isFavorites)
            val isDimmedOther = isBusActive || isMetroActive || isCercaniasActive
            val selectedGid = (selectedItem as? GoogleMapTransitItem.Valenbisi)?.station?.gid
                ?: (selectedMapItem as? SelectedMapItem.Valenbisi)?.station?.gid

            filteredValenbisi.forEach { station ->
                val unifiedKey = "VALENBISI_${station.gid}"
                key(unifiedKey) {
                    val isValenbisiSelected = station.gid == selectedGid
                    val isDimmedValenbisi = !isValenbisiSelected && isDimmedOther
                    val valenbisiAlpha = if (isValenbisiSelected) 1.0f else if (isDimmedValenbisi) 0.25f else 1.0f
                    val valenbisiZIndex = if (isValenbisiSelected) 45f else if (isDimmedValenbisi) 4f else 22f

                    val iconInfo = if (isDotMode) {
                        val dotBucket = when {
                            station.available == 0 -> 0
                            station.available < 3 -> 1
                            else -> 2
                        }
                        remember(dotBucket) {
                            GoogleMapsMarkerCache.getOrCreateValenbisiCompactDotIcon(context, station.available)
                        }
                    } else if (showPill) {
                        remember(station.available, isDarkMode, true) {
                            GoogleMapsMarkerCache.getOrCreateValenbisiMarkerIcon(
                                context = context,
                                available = station.available,
                                free = station.free,
                                isDarkMode = isDarkMode,
                                showPill = true
                            )
                        }
                    } else {
                        val noPillBucket = when {
                            station.available == 0 -> 0
                            station.available < 3 -> 1
                            else -> 2
                        }
                        remember(noPillBucket, isDarkMode, false) {
                            GoogleMapsMarkerCache.getOrCreateValenbisiMarkerIcon(
                                context = context,
                                available = station.available,
                                free = station.free,
                                isDarkMode = isDarkMode,
                                showPill = false
                            )
                        }
                    }

                    val snippet = if (isDotMode) {
                        "Bicis: ${station.available} • Huecos: ${station.free}"
                    } else {
                        "Disponibles: ${station.available} • Huecos: ${station.free}"
                    }

                    Marker(
                        state = rememberMarkerState(key = unifiedKey, position = LatLng(station.latitude, station.longitude)),
                        icon = iconInfo.descriptor,
                        anchor = iconInfo.anchor,
                        alpha = valenbisiAlpha,
                        title = station.name,
                        snippet = snippet,
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
                val badgeInfo = remember(station.available, isDarkMode) {
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
