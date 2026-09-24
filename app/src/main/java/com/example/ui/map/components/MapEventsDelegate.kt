package com.example.ui.map.components

import android.content.Context
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

object MapEventsDelegate {
    private var lastGestureTime = 0L

    fun notifyGesture() {
        lastGestureTime = System.currentTimeMillis()
    }

    fun isGestureActive(): Boolean {
        return System.currentTimeMillis() - lastGestureTime < 450L
    }

    fun createMapEventsOverlay(
        context: Context,
        mapView: MapView,
        onSingleTap: (GeoPoint?) -> Boolean,
        onLongPress: (GeoPoint?) -> Boolean
    ): MapEventsOverlay {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return onSingleTap(p)
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                return onLongPress(p)
            }
        }
        return MapEventsOverlay(receiver)
    }
}
