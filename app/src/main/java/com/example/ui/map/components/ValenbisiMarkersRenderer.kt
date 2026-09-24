package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Pure rendering delegate for Valenbisi bike stations and clusters.
 * Handles zoom LOD clustering, compact dots, and detailed station pins with marker recycling.
 */
object ValenbisiMarkersRenderer {

    data class RenderResult(
        val activeMarkerCount: Int,
        val activeClusterCount: Int
    )

    fun renderValenbisi(
        context: Context,
        mapView: MapView,
        valenbisiStations: List<ValenbisiStation>,
        showValenbisi: Boolean,
        isOnlyValenbisi: Boolean,
        currentZoom: Double,
        isDarkMode: Boolean,
        recycledValenbisiMarkers: MutableList<Marker>,
        recycledValenbisiClusterMarkers: MutableList<Marker>,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): RenderResult {
        var activeValenbisiCount = 0
        var activeValenbisiClusterCount = 0

        if (showValenbisi && valenbisiStations.isNotEmpty()) {
            if (isOnlyValenbisi) {
                when {
                    // 1. Zoom < 13.5 -> Group/cluster them
                    currentZoom < 13.5 -> {
                        val gridSize = if (currentZoom < 11.0) 0.06 else 0.025
                        val clusters = valenbisiStations.groupBy { station ->
                            val gridX = (station.latitude / gridSize).toInt()
                            val gridY = (station.longitude / gridSize).toInt()
                            Pair(gridX, gridY)
                        }

                        clusters.values.forEach { group ->
                            val avgLat = group.map { it.latitude }.average()
                            val avgLon = group.map { it.longitude }.average()
                            val count = group.size

                            val clusterMarker = if (activeValenbisiClusterCount < recycledValenbisiClusterMarkers.size) {
                                recycledValenbisiClusterMarkers[activeValenbisiClusterCount]
                            } else {
                                Marker(mapView).also {
                                    recycledValenbisiClusterMarkers.add(it)
                                }
                            }
                            activeValenbisiClusterCount++

                            clusterMarker.infoWindow = null
                            clusterMarker.closeInfoWindow()
                            clusterMarker.position = GeoPoint(avgLat, avgLon)
                            clusterMarker.icon = getClusterIcon(context, count, Color.parseColor("#10B981"))
                            clusterMarker.title = "$count Estaciones de Valenbisi"
                            clusterMarker.snippet = "Toca para ampliar área"
                            clusterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            clusterMarker.isEnabled = true
                            clusterMarker.setVisible(true)
                            clusterMarker.setOnMarkerClickListener { _, _ ->
                                if (showValenbisi && clusterMarker.isEnabled) {
                                    mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                                    mapView.controller.zoomTo(currentZoom + 2.2)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    }

                    // 2. Zoom in 13.5..15.2 -> Show color dots
                    currentZoom in 13.5..15.2 -> {
                        valenbisiStations.forEach { station ->
                            val marker = if (activeValenbisiCount < recycledValenbisiMarkers.size) {
                                recycledValenbisiMarkers[activeValenbisiCount]
                            } else {
                                Marker(mapView).also {
                                    recycledValenbisiMarkers.add(it)
                                }
                            }
                            activeValenbisiCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            marker.position = GeoPoint(station.latitude, station.longitude)

                            val dotResult = getValenbisiCompactDotIcon(context, station.available)
                            marker.icon = dotResult.drawable
                            marker.setAnchor(dotResult.anchorU, dotResult.anchorV)

                            marker.title = station.name
                            marker.snippet = "Bicis: ${station.available} • Huecos: ${station.free}"
                            marker.isEnabled = true
                            marker.setVisible(true)

                            if (station.available == 0) {
                                marker.alpha = 0.55f
                            } else {
                                marker.alpha = 1.0f
                            }

                            marker.setOnMarkerClickListener { m, _ ->
                                onTapHandler(context, mapView, m.position)
                            }
                        }
                    }

                    // 3. Zoom >= 15.2 -> Show full detailed pins
                    else -> {
                        val showPill = currentZoom >= 14.5
                        valenbisiStations.forEach { station ->
                            val marker = if (activeValenbisiCount < recycledValenbisiMarkers.size) {
                                recycledValenbisiMarkers[activeValenbisiCount]
                            } else {
                                Marker(mapView).also {
                                    recycledValenbisiMarkers.add(it)
                                }
                            }
                            activeValenbisiCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            marker.position = GeoPoint(station.latitude, station.longitude)

                            val iconResult = getValenbisiMarkerIcon(context, station.available, station.free, isDarkMode, showPill)
                            marker.icon = iconResult.drawable
                            marker.setAnchor(iconResult.anchorU, iconResult.anchorV)

                            marker.title = station.name
                            marker.snippet = "Disponibles: ${station.available} • Huecos: ${station.free}"
                            marker.isEnabled = true
                            marker.setVisible(true)

                            if (station.available == 0) {
                                marker.alpha = 0.55f
                            } else {
                                marker.alpha = 1.0f
                            }

                            marker.setOnMarkerClickListener { m, _ ->
                                onTapHandler(context, mapView, m.position)
                            }
                        }
                    }
                }
            } else {
                // If not "only Valenbisi", hide completely below zoom 15.2
                if (currentZoom >= 15.2) {
                    val showPill = currentZoom >= 14.5
                    valenbisiStations.forEach { station ->
                        val marker = if (activeValenbisiCount < recycledValenbisiMarkers.size) {
                            recycledValenbisiMarkers[activeValenbisiCount]
                        } else {
                            Marker(mapView).also {
                                recycledValenbisiMarkers.add(it)
                            }
                        }
                        activeValenbisiCount++

                        marker.infoWindow = null
                        marker.closeInfoWindow()
                        marker.position = GeoPoint(station.latitude, station.longitude)

                        val iconResult = getValenbisiMarkerIcon(context, station.available, station.free, isDarkMode, showPill)
                        marker.icon = iconResult.drawable
                        marker.setAnchor(iconResult.anchorU, iconResult.anchorV)

                        marker.title = station.name
                        marker.snippet = "Disponibles: ${station.available} • Huecos: ${station.free}"
                        marker.isEnabled = true
                        marker.setVisible(true)

                        if (station.available == 0) {
                            marker.alpha = 0.55f
                        } else {
                            marker.alpha = 1.0f
                        }

                        marker.setOnMarkerClickListener { m, _ ->
                            onTapHandler(context, mapView, m.position)
                        }
                    }
                }
            }
        }

        // Hide unused recycled valenbisi markers
        for (i in activeValenbisiCount until recycledValenbisiMarkers.size) {
            recycledValenbisiMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Hide unused recycled valenbisi cluster markers
        for (i in activeValenbisiClusterCount until recycledValenbisiClusterMarkers.size) {
            recycledValenbisiClusterMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        return RenderResult(activeValenbisiCount, activeValenbisiClusterCount)
    }
}
