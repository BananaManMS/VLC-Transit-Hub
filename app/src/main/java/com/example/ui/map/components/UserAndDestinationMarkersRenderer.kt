package com.example.ui.map.components

import android.content.Context
import com.example.data.model.NominatimResult
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.map.SelectedMapItem
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Handles initialization, iconography, and state updates for User Location and Destination Pin markers.
 */
object UserAndDestinationMarkersRenderer {

    fun updateUserLocationMarker(
        context: Context,
        mapView: MapView,
        userLocation: GeoPoint?,
        existingUserMarker: Marker?,
        userBearing: Float? = null,
        isCellTowerLocation: Boolean = false
    ): Marker? {
        if (userLocation != null) {
            val marker = (existingUserMarker as? UserLocationMarker) ?: UserLocationMarker(mapView).also {
                mapView.overlays.add(it)
            }
            marker.infoWindow = null
            marker.setOnMarkerClickListener { _, _ -> true }
            marker.closeInfoWindow()
            marker.position = userLocation
            marker.updateGpsBearing(userBearing)
            marker.updateCellTowerMode(isCellTowerLocation)
            marker.setVisible(true)
            return marker
        } else {
            existingUserMarker?.setVisible(false)
            return existingUserMarker
        }
    }

    fun updateDestinationMarker(
        context: Context,
        mapView: MapView,
        destinationLocation: GeoPoint?,
        destinationTitle: String?,
        selectedItinerary: PlannedItinerary?,
        isDarkMode: Boolean,
        existingDestinationMarker: Marker?,
        onSelectItem: ((SelectedMapItem) -> Unit)?,
        onTapHandler: ((Context, MapView, GeoPoint) -> Boolean)? = null
    ): Marker? {
        if (destinationLocation != null && selectedItinerary == null) {
            val destIconResult = getDestinationMarkerIcon(context, isDarkMode)
            val marker = existingDestinationMarker ?: Marker(mapView).also {
                it.infoWindow = null
                mapView.overlays.add(it)
            }
            marker.infoWindow = null
            marker.closeInfoWindow()
            marker.position = destinationLocation
            marker.title = destinationTitle ?: "Destino"
            marker.snippet = "Destino seleccionado"
            marker.icon = destIconResult.drawable
            marker.setAnchor(destIconResult.anchorU, destIconResult.anchorV)
            marker.setVisible(true)
            marker.isEnabled = true
            marker.setOnMarkerClickListener { m, _ ->
                if (onTapHandler != null) {
                    onTapHandler(context, mapView, m.position)
                } else {
                    val destItem = SelectedMapItem.Address(
                        NominatimResult(
                            displayName = destinationTitle ?: "Destino",
                            latitude = m.position.latitude,
                            longitude = m.position.longitude,
                            type = "address",
                            category = "place",
                            isLocalStop = false,
                            stopId = null,
                            stopType = null
                        )
                    )
                    onSelectItem?.invoke(destItem)
                }
                true
            }
            return marker
        } else {
            existingDestinationMarker?.setVisible(false)
            existingDestinationMarker?.isEnabled = false
            existingDestinationMarker?.setOnMarkerClickListener(null)
            return existingDestinationMarker
        }
    }
}
