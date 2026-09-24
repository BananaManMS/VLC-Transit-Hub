package com.example.ui.map.components

import android.content.Context
import com.example.data.database.CercaniasStationEntity
import com.example.data.model.MetroStation
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.SelectedMapItem
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Pure rendering delegate for Metrovalencia and Cercanías Renfe station markers.
 * Uses recycled markers and centralized bitmap icon caches.
 */
object MetroMarkersRenderer {

    /**
     * Renders Metrovalencia station markers onto the MapView.
     * Returns the count of valid stations rendered.
     */
    fun renderMetroMarkers(
        context: Context,
        mapView: MapView,
        validMetroStations: List<MetroStation>,
        metroPositions: Map<String, GeoPoint>,
        currentZoom: Double,
        isDarkMode: Boolean,
        isOnlyMetroSelected: Boolean,
        showPill: Boolean,
        selectedMapItem: SelectedMapItem? = null,
        recycledMetroMarkers: MutableList<Marker>,
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): Int {
        val selectedMetro = (selectedMapItem as? SelectedMapItem.Metro)?.station
        val isMetroVisible = currentZoom >= 11.5

        val activeMetroKeys = HashSet<String>()

        validMetroStations.forEach { station ->
            val markerKey = "METRO_${station.id}"
            activeMetroKeys.add(markerKey)

            var marker = recycledMetroMarkers.find { it.id == markerKey }
            if (marker == null) {
                marker = recycledMetroMarkers.find { !it.isEnabled }
                if (marker == null) {
                    marker = Marker(mapView).also {
                        recycledMetroMarkers.add(it)
                        mapView.overlays.add(it)
                    }
                }
                marker.id = markerKey
            }

            marker.infoWindow = null
            marker.closeInfoWindow()
            val adjustedPos = metroPositions[station.name] ?: GeoPoint(station.latitude!!, station.longitude!!)
            val isSelected = selectedMetro != null && (station.id == selectedMetro.id || station.name.equals(selectedMetro.name, ignoreCase = true))

            val metroResult = if (isSelected) {
                getMetroMarkerIcon(context, station.name, lines = station.lines, isDarkMode = isDarkMode, showPill = true)
            } else if (currentZoom < 13.5) {
                getMetroLogoSmallIcon(context, isDarkMode)
            } else if (currentZoom < 16.0) {
                getMetroMarkerIcon(context, station.name, lines = emptyList(), isDarkMode = isDarkMode, showPill = showPill)
            } else {
                getMetroMarkerIcon(context, station.name, lines = station.lines, isDarkMode = isDarkMode, showPill = showPill)
            }
            marker.position = adjustedPos
            marker.icon = metroResult.drawable
            marker.title = station.name
            marker.snippet = "Metrovalencia • ${station.lines.joinToString(", ")}"
            marker.setAnchor(metroResult.anchorU, metroResult.anchorV)

            if (isMetroVisible || isSelected) {
                marker.isEnabled = true
                marker.setVisible(true)
            } else {
                marker.isEnabled = false
                marker.setVisible(false)
            }

            marker.setOnMarkerClickListener { m, _ ->
                onTapHandler(context, mapView, m.position)
                true
            }
        }

        recycledMetroMarkers.forEach { m ->
            if (m.id != null && m.id.startsWith("METRO_") && m.id !in activeMetroKeys) {
                m.setVisible(false)
                m.isEnabled = false
                m.setOnMarkerClickListener(null)
            }
        }

        return validMetroStations.size
    }

    /**
     * Renders Cercanías Renfe station markers onto the MapView.
     * Returns the count of valid stations rendered.
     */
    fun renderCercaniasMarkers(
        context: Context,
        mapView: MapView,
        validCercaniasStations: List<CercaniasStationEntity>,
        cercaniasPositions: Map<String, GeoPoint>,
        currentZoom: Double,
        isDarkMode: Boolean,
        isOnlyCercaniasSelected: Boolean,
        showPill: Boolean,
        selectedMapItem: SelectedMapItem? = null,
        appLanguage: AppLanguage,
        recycledCercaniasMarkers: MutableList<Marker>,
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): Int {
        val selectedCercanias = (selectedMapItem as? SelectedMapItem.Cercanias)?.station
        val isCercaniasVisible = currentZoom >= 9.0 || isOnlyCercaniasSelected

        val activeCercaniasKeys = HashSet<String>()

        validCercaniasStations.forEach { station ->
            val markerKey = "CERCANIAS_${station.stop_id}"
            activeCercaniasKeys.add(markerKey)

            var marker = recycledCercaniasMarkers.find { it.id == markerKey }
            if (marker == null) {
                marker = recycledCercaniasMarkers.find { !it.isEnabled }
                if (marker == null) {
                    marker = Marker(mapView).also {
                        recycledCercaniasMarkers.add(it)
                        mapView.overlays.add(it)
                    }
                }
                marker.id = markerKey
            }

            marker.infoWindow = null
            marker.closeInfoWindow()
            marker.relatedObject = station
            val adjustedPos = cercaniasPositions[station.stop_id] ?: GeoPoint(station.lat, station.lon)
            val isSelected = selectedCercanias != null && (station.stop_id == selectedCercanias.stop_id || station.displayName.equals(selectedCercanias.displayName, ignoreCase = true))

            val cercaniasResult = if (isSelected) {
                getCercaniasMarkerIcon(context, station.displayName, lines = station.lineas, isDarkMode = isDarkMode, showPill = true)
            } else if (currentZoom < 11.5) {
                getCercaniasTinyDotIcon(context, isDarkMode)
            } else if (currentZoom < 13.5) {
                getCercaniasLogoSmallIcon(context, isDarkMode)
            } else if (currentZoom < 16.0) {
                getCercaniasMarkerIcon(context, station.displayName, lines = emptyList(), isDarkMode = isDarkMode, showPill = showPill)
            } else {
                getCercaniasMarkerIcon(context, station.displayName, lines = station.lineas, isDarkMode = isDarkMode, showPill = showPill)
            }
            marker.position = adjustedPos
            marker.icon = cercaniasResult.drawable
            marker.title = station.displayName
            marker.snippet = if (appLanguage == AppLanguage.CA) "Rodalia Renfe • ${station.lines}" else "Cercanías Renfe • ${station.lines}"
            marker.setAnchor(cercaniasResult.anchorU, cercaniasResult.anchorV)

            if (isCercaniasVisible || isSelected) {
                marker.isEnabled = true
                marker.setVisible(true)
            } else {
                marker.isEnabled = false
                marker.setVisible(false)
            }

            marker.setOnMarkerClickListener { m, _ ->
                onTapHandler(context, mapView, m.position)
                true
            }
        }

        recycledCercaniasMarkers.forEach { m ->
            if (m.id != null && m.id.startsWith("CERCANIAS_") && m.id !in activeCercaniasKeys) {
                m.setVisible(false)
                m.isEnabled = false
                m.setOnMarkerClickListener(null)
            }
        }

        return validCercaniasStations.size
    }
}
