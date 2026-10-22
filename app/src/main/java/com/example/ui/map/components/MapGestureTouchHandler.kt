package com.example.ui.map.components

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Handles custom map touch gestures:
 * 1. One-finger double-tap-and-drag zoom with mathematical anchor preservation (zero drift).
 * 2. 1-second long-press for placing custom destination pin with haptic feedback.
 * 3. Quick double-tap-to-zoom (centered on tapped point without premature zoom jump).
 */
object MapGestureTouchHandler {

    private const val LONG_PRESS_HOLD_MS = 1000L // Requires 1.0s continuous hold
    private const val DOUBLE_TAP_MAX_DELAY_MS = 350L
    private const val DOUBLE_TAP_MAX_SLOP_PX = 100f
    private const val DRAG_ZOOM_THRESHOLD_PX = 8f
    private const val MOVE_SLOP_PX = 15f
    private const val SENSITIVITY_PX = 300.0

    fun createTouchListener(
        mapView: MapView,
        getMapCenterOffsetY: () -> Int,
        onMapTouch: (() -> Unit)?,
        onMapPan: (() -> Unit)?,
        onZoomLevelChanged: ((Double) -> Unit)?,
        onCameraPositionChanged: ((GeoPoint, Double) -> Unit)? = null,
        onMapLongClick: ((GeoPoint) -> Unit)?,
        isItinerarySelected: () -> Boolean
    ): View.OnTouchListener {
        val mainHandler = Handler(Looper.getMainLooper())
        var longPressRunnable: Runnable? = null

        fun cancelLongPress() {
            longPressRunnable?.let {
                mainHandler.removeCallbacks(it)
                longPressRunnable = null
            }
        }

        var lastTapTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var isDoubleTapCandidate = false
        var doubleTapStartY = 0f
        var startZoom = 0.0
        var focalGeo: GeoPoint? = null
        var hasDraggedForZoom = false

        return View.OnTouchListener { _, event ->
            onMapTouch?.invoke()
            val now = System.currentTimeMillis()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelLongPress()
                    val dx = abs(event.x - lastTapX)
                    val dy = abs(event.y - lastTapY)
                    downX = event.x
                    downY = event.y
                    downTime = now

                    if (now - lastTapTime < DOUBLE_TAP_MAX_DELAY_MS && dx < DOUBLE_TAP_MAX_SLOP_PX && dy < DOUBLE_TAP_MAX_SLOP_PX) {
                        // Candidate for double-tap drag zoom or quick double tap
                        isDoubleTapCandidate = true
                        doubleTapStartY = event.y
                        startZoom = mapView.zoomLevelDouble
                        val curCenter = mapView.projection?.currentCenter ?: mapView.mapCenter
                        focalGeo = if (curCenter != null) {
                            GeoPoint(curCenter.latitude, curCenter.longitude)
                        } else {
                            null
                        }
                        hasDraggedForZoom = false
                        onMapPan?.invoke()
                        MapMarkersManager.notifyGesture()
                        // Consume ACTION_DOWN to prevent Osmdroid's built-in GestureDetector from firing an immediate zoom
                        return@OnTouchListener true
                    } else {
                        isDoubleTapCandidate = false
                        focalGeo = null
                        if (!isItinerarySelected()) {
                            val tapX = event.x
                            val tapY = event.y
                            val runnable = Runnable {
                                longPressRunnable = null
                                if (!isDoubleTapCandidate && !isItinerarySelected()) {
                                    val igp = mapView.projection?.fromPixels(tapX.toInt(), tapY.toInt())
                                    if (igp != null) {
                                        val geoPoint = GeoPoint(igp.latitude, igp.longitude)
                                        mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onMapLongClick?.invoke(geoPoint)
                                    }
                                }
                            }
                            longPressRunnable = runnable
                            mainHandler.postDelayed(runnable, LONG_PRESS_HOLD_MS)
                        }
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDoubleTapCandidate) {
                        val deltaY = event.y - doubleTapStartY
                        if (!hasDraggedForZoom && abs(deltaY) > DRAG_ZOOM_THRESHOLD_PX) {
                            hasDraggedForZoom = true
                        }
                        if (hasDraggedForZoom) {
                            onMapPan?.invoke()
                            MapMarkersManager.notifyGesture()
                            // Drag DOWN (deltaY > 0) -> Zoom IN
                            // Drag UP (deltaY < 0) -> Zoom OUT
                            val dragDistance = deltaY
                            val absDist = abs(dragDistance.toDouble())
                            val scaleFactor = if (dragDistance >= 0f) {
                                1.0 + (absDist / SENSITIVITY_PX)
                            } else {
                                1.0 / (1.0 + (absDist / SENSITIVITY_PX))
                            }
                            val zoomDelta = ln(scaleFactor) / ln(2.0)
                            val targetZoom = (startZoom + zoomDelta).coerceIn(mapView.minZoomLevel, mapView.maxZoomLevel)

                            val center = focalGeo
                            if (center != null) {
                                mapView.setExpectedCenter(center)
                                mapView.controller.setZoom(targetZoom)
                                mapView.setExpectedCenter(center)
                            } else {
                                mapView.controller.setZoom(targetZoom)
                            }
                            mapView.invalidate()
                        }
                        return@OnTouchListener true
                    } else {
                        val moveDist = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
                        if (moveDist > MOVE_SLOP_PX) {
                            cancelLongPress()
                            onMapPan?.invoke()
                            MapMarkersManager.notifyGesture()
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    cancelLongPress()
                    if (isDoubleTapCandidate) {
                        isDoubleTapCandidate = false
                        lastTapTime = 0L
                        val lockedCenter = focalGeo
                        focalGeo = null
                        if (hasDraggedForZoom) {
                            lockedCenter?.let { mapView.setExpectedCenter(it) }
                            onZoomLevelChanged?.invoke(mapView.zoomLevelDouble)
                            lockedCenter?.let { onCameraPositionChanged?.invoke(it, mapView.zoomLevelDouble) }
                            return@OnTouchListener true
                        } else {
                            // Quick double tap without drag -> perform single step zoom-in centered on tapped point
                            val tapX = downX.toInt().coerceIn(0, (mapView.width - 1).coerceAtLeast(0))
                            val tapY = downY.toInt().coerceIn(0, (mapView.height - 1).coerceAtLeast(0))
                            mapView.controller.zoomInFixing(tapX, tapY)
                            val newZoom = (mapView.zoomLevelDouble + 1.0).coerceAtMost(mapView.maxZoomLevel)
                            onZoomLevelChanged?.invoke(newZoom)
                            return@OnTouchListener true
                        }
                    } else {
                        val moveDist = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
                        if (now - downTime < 250L && moveDist < 25.0) {
                            lastTapTime = now
                            lastTapX = event.x
                            lastTapY = event.y
                        } else {
                            lastTapTime = 0L
                        }
                    }
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelLongPress()
                    isDoubleTapCandidate = false
                    focalGeo = null
                    lastTapTime = 0L
                    onMapPan?.invoke()
                    MapMarkersManager.notifyGesture()
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    isDoubleTapCandidate = false
                    focalGeo = null
                    lastTapTime = 0L
                }
            }
            false
        }
    }
}
