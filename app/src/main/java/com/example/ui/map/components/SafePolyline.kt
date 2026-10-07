package com.example.ui.map.components

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * A crash-safe wrapper around osmdroid's Polyline that prevents UnsupportedOperationException
 * during onDetach() by avoiding super.onDetach() and supplying mutable point lists.
 */
class SafePolyline(mapView: MapView) : Polyline(mapView) {

    override fun setPoints(points: List<GeoPoint>?) {
        super.setPoints(if (points.isNullOrEmpty()) ArrayList() else ArrayList(points))
    }

    override fun onDetach(mapView: MapView?) {
        setRelatedObject(null)
        closeInfoWindow()
    }
}
