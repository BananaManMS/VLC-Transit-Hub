package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat
import com.example.ui.cercanias.LiveVehicleInfo
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.atan2

class CercaniasTrainOverlayManager(
    private val context: Context,
    private val mapView: MapView,
    private val onTrainSelected: (LiveVehicleInfo) -> Unit
) {

    private val recycledMarkers = mutableListOf<Marker>()
    private val iconCache = mutableMapOf<String, BitmapDrawable>()

    // Last known coordinates and update time for smooth 20s interpolation
    private class TrainAnimState(
        var startLat: Double,
        var startLon: Double,
        var targetLat: Double,
        var targetLon: Double,
        var lastUpdateMs: Long,
        var calculatedBearing: Float
    )

    private val trainAnimStates = mutableMapOf<String, TrainAnimState>()

    fun updateLiveTrains(
        trains: List<LiveVehicleInfo>,
        isCercaniasOnlyFilter: Boolean,
        selectedLines: List<String>? = null
    ) {
        val zoom = mapView.zoomLevelDouble
        val bounds = mapView.boundingBox

        val nowMs = System.currentTimeMillis()

        // Filter trains by line selection or Cercanías-only filter
        val filteredTrains = trains.filter { vehicle ->
            val lat = vehicle.latitude
            val lon = vehicle.longitude
            if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) return@filter false

            // Line matching
            val matchesLine = selectedLines.isNullOrEmpty() ||
                selectedLines.any { sel ->
                    vehicle.routeId.equals(sel, ignoreCase = true) ||
                    vehicle.routeId.contains(sel, ignoreCase = true)
                }

            isCercaniasOnlyFilter || matchesLine
        }

        val activeCount = filteredTrains.size
        var renderedCount = 0

        // Recycling strategy (index-based)
        for (i in 0 until activeCount) {
            val vehicle = filteredTrains[i]
            val lat = vehicle.latitude!!
            val lon = vehicle.longitude!!
            val trainKey = vehicle.tripId.ifBlank { vehicle.trainNum }

            val canonicalNum = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(vehicle.routeId, vehicle.tripId)
            val lineCode = "C$canonicalNum"
            val lineColorHex = getCercaniasLineColorHex(lineCode)
            val delayMinutes = vehicle.delayMinutes

            // Smooth position interpolation across 20-second Renfe cadence
            val animState = trainAnimStates[trainKey]
            
            // Get track polyline coordinates for snapping!
            val trackPoints = com.example.ui.map.components.CercaniasMapOverlayLoader.getLinePoints(lineCode)

            val (currentPos, effectiveBearing, showArrow) = if (animState == null) {
                // Initial snap: snap to line polyline or fall back to actual GPS coordinates
                val snapped = snapToPolyline(lat, lon, trackPoints) ?: GeoPoint(lat, lon)
                val initialBearing = vehicle.bearing ?: 0f
                val directionKnown = (vehicle.bearing != null && vehicle.bearing != 0f)
                trainAnimStates[trainKey] = TrainAnimState(snapped.latitude, snapped.longitude, snapped.latitude, snapped.longitude, nowMs, initialBearing)
                Triple(snapped, initialBearing, directionKnown)
            } else {
                val snappedTarget = snapToPolyline(lat, lon, trackPoints) ?: GeoPoint(lat, lon)
                val latDiff = snappedTarget.latitude - animState.targetLat
                    val lonDiff = snappedTarget.longitude - animState.targetLon
                    val distSq = latDiff * latDiff + lonDiff * lonDiff
                    
                    val timeGapMs = nowMs - animState.lastUpdateMs
                    
                    // Teleport instantly if the jump is > 1.0 km (distSq > 0.00008) to prevent flying at impossible speeds!
                    val shouldTeleport = distSq > 0.00008

                    if (shouldTeleport) {
                        animState.startLat = snappedTarget.latitude
                        animState.startLon = snappedTarget.longitude
                        animState.targetLat = snappedTarget.latitude
                        animState.targetLon = snappedTarget.longitude
                        animState.lastUpdateMs = nowMs
                        if (vehicle.bearing == null || vehicle.bearing == 0f) {
                            // Keep previous bearing or recalculate
                        } else {
                            animState.calculatedBearing = vehicle.bearing
                        }
                        Triple(snappedTarget, animState.calculatedBearing ?: 0f, true)
                    } else {
                        val hasMoved = distSq > 0.0000001 // ~30 meters epsilon
                        if (hasMoved) {
                            // Compute where the train is visually rendering right now to start next segment from here
                            val elapsed = timeGapMs.coerceIn(0L, 26_000L)
                            val fraction = elapsed.toFloat() / 26_000f
                            val currentVisualLat = animState.startLat + (animState.targetLat - animState.startLat) * fraction
                            val currentVisualLon = animState.startLon + (animState.targetLon - animState.startLon) * fraction

                            animState.startLat = currentVisualLat
                            animState.startLon = currentVisualLon
                            animState.targetLat = snappedTarget.latitude
                            animState.targetLon = snappedTarget.longitude
                            animState.lastUpdateMs = nowMs

                            if (vehicle.bearing == null || vehicle.bearing == 0f) {
                                animState.calculatedBearing = calculateBearing(animState.startLat, animState.startLon, snappedTarget.latitude, snappedTarget.longitude)
                            } else {
                                animState.calculatedBearing = vehicle.bearing
                            }
                        }

                        // Interpolate position across 26,000 ms along the snapped track line
                        val elapsed = (nowMs - animState.lastUpdateMs).coerceIn(0L, 26_000L)
                        val fraction = elapsed.toFloat() / 26_000f
                        
                        val interpLat = animState.startLat + (animState.targetLat - animState.startLat) * fraction
                        val interpLon = animState.startLon + (animState.targetLon - animState.startLon) * fraction
                        val currentPoint = GeoPoint(interpLat, interpLon)

                        // Advanced straight line position for tracking direction
                        val nextFraction = (fraction + 0.05f).coerceAtMost(1.0f)
                        val nextInterpLat = animState.startLat + (animState.targetLat - animState.startLat) * nextFraction
                        val nextInterpLon = animState.startLon + (animState.targetLon - animState.startLon) * nextFraction

                        val movementOccurred = (interpLat != nextInterpLat || interpLon != nextInterpLon)
                        val calculatedBearing = if (movementOccurred) {
                            calculateBearing(interpLat, interpLon, nextInterpLat, nextInterpLon)
                        } else {
                            vehicle.bearing ?: animState.calculatedBearing ?: 0f
                        }

                        val directionKnown = (vehicle.bearing != null && vehicle.bearing != 0f) || movementOccurred
                        Triple(currentPoint, calculatedBearing, directionKnown)
                    }
                }

            val marker = if (renderedCount < recycledMarkers.size) {
                recycledMarkers[renderedCount]
            } else {
                val m = Marker(mapView)
                mapView.overlays.add(m)
                recycledMarkers.add(m)
                m
            }

            val iconDrawable = getOrCreateTrainIcon(lineCode, lineColorHex, delayMinutes, effectiveBearing, zoom, showArrow)

            marker.apply {
                position = currentPos
                icon = iconDrawable
                // Disable default info window bubble so only our beautiful bottom sheet is shown
                infoWindow = null
                title = "Cercanías $lineCode (${vehicle.trainNum})"
                snippet = if (vehicle.destinationName.isNotBlank()) "Destino: ${vehicle.destinationName}" else ""
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setVisible(true)
                isEnabled = true
                setOnMarkerClickListener { _, _ ->
                    onTrainSelected(vehicle)
                    true
                }
            }

            renderedCount++
        }

        // Hide leftover recycled markers
        for (i in renderedCount until recycledMarkers.size) {
            recycledMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Ensure all active train markers are on top of everything (stations, dots, and track overlays)
        val totalOverlays = mapView.overlays.size
        for (i in 0 until renderedCount) {
            val m = recycledMarkers[i]
            if (m.isEnabled) {
                val idx = mapView.overlays.indexOf(m)
                if (idx != -1 && idx < totalOverlays - 1) {
                    mapView.overlays.removeAt(idx)
                    mapView.overlays.add(m)
                }
            }
        }

        mapView.invalidate()
    }

    fun bringTrainsToTop() {
        val totalOverlays = mapView.overlays.size
        for (m in recycledMarkers) {
            if (m.isEnabled) {
                val idx = mapView.overlays.indexOf(m)
                if (idx != -1 && idx < totalOverlays - 1) {
                    mapView.overlays.removeAt(idx)
                    mapView.overlays.add(m)
                }
            }
        }
        mapView.invalidate()
    }

    private fun isPointInExpandedBounds(lat: Double, lon: Double, bounds: BoundingBox?): Boolean {
        if (bounds == null) return true
        val latMargin = 0.05
        val lonMargin = 0.05
        return lat in (bounds.latSouth - latMargin)..(bounds.latNorth + latMargin) &&
                lon in (bounds.lonWest - lonMargin)..(bounds.lonEast + lonMargin)
    }

    private fun calculateBearing(startLat: Double, startLon: Double, endLat: Double, endLon: Double): Float {
        val dLon = Math.toRadians(endLon - startLon)
        val lat1 = Math.toRadians(startLat)
        val lat2 = Math.toRadians(endLat)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        val rad = atan2(y, x)
        return ((Math.toDegrees(rad) + 360) % 360).toFloat()
    }

    private fun getOrCreateTrainIcon(
        lineCode: String,
        lineColorHex: Int,
        delayMinutes: Int,
        bearing: Float,
        zoom: Double,
        showArrow: Boolean
    ): BitmapDrawable {
        // Visual sizing depending on zoom
        val visualSizeDp = when {
            zoom < 11.0 -> 24
            zoom < 14.0 -> 30
            else -> 36
        }
        val touchSizeDp = 48 // Comfortable tap target matching accessibility guidelines

        // Color-coded status ring: Green (<=1m), Orange (2-4m), Red (>=5m)
        val ringColorHex = when {
            delayMinutes <= 1 -> Color.parseColor("#34C759") // Green
            delayMinutes <= 4 -> Color.parseColor("#FF9500") // Orange
            else -> Color.parseColor("#FF3B30")              // Red
        }

        // Quantize bearing to 15-degree steps for cache efficiency
        val quantizedBearing = ((bearing / 15f).toInt() * 15) % 360
        val cacheKey = "$lineCode-$lineColorHex-$ringColorHex-$quantizedBearing-$visualSizeDp-$showArrow"

        return iconCache.getOrPut(cacheKey) {
            val metrics = context.resources.displayMetrics
            val px = (touchSizeDp * metrics.density).toInt().coerceAtLeast(48)
            val visualPx = (visualSizeDp * metrics.density).toInt().coerceAtLeast(22)
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val center = px / 2f
            val radius = visualPx * 0.35f

            // 1. Draw Directional Pointer protruding from the edge if direction is known
            if (showArrow) {
                canvas.save()
                canvas.rotate(quantizedBearing.toFloat(), center, center)
                
                // Draw a beautiful white border path for the pointer to give it elevation
                val pointerPath = Path().apply {
                    val arrowW = radius * 0.45f
                    val arrowH = radius * 0.40f
                    moveTo(center, center - radius - arrowH)
                    lineTo(center - arrowW, center - radius + 2f)
                    lineTo(center + arrowW, center - radius + 2f)
                    close()
                }

                val pointerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * metrics.density
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(pointerPath, pointerBorderPaint)

                val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = lineColorHex
                    style = Paint.Style.FILL
                }
                canvas.drawPath(pointerPath, pointerPaint)

                canvas.restore()
            }

            // 2. Main Circle with white border (shadow/elevation effect)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, radius + (1.5f * metrics.density), borderPaint)

            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColorHex
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, radius, circlePaint)

            // 3. Official Line Text Label (e.g. "C1", "C6") in the center
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = radius * 0.9f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = textPaint.fontMetrics
            val textY = center - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(lineCode, center, textY, textPaint)

            // 4. Subtle, official Renfe-style small top-right status dot indicating delay
            val statusX = center + radius * 0.73f
            val statusY = center - radius * 0.73f
            val statusDotRadius = radius * 0.32f

            // White crisp border for the status dot
            val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(statusX, statusY, statusDotRadius + (1.2f * metrics.density), dotBorderPaint)

            // Colored status core
            val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColorHex
                style = Paint.Style.FILL
            }
            canvas.drawCircle(statusX, statusY, statusDotRadius, dotCorePaint)

            BitmapDrawable(context.resources, bitmap)
        }
    }

    private fun getCercaniasLineColorHex(lineCode: String): Int {
        val clean = lineCode.uppercase().replace("C-", "").replace("C_", "").replace("C", "").trim()
        return when (clean) {
            "1", "10" -> Color.parseColor("#00A3E0") // Cyan (C1)
            "2", "20" -> Color.parseColor("#FF6A00") // Orange (C2)
            "3", "30" -> Color.parseColor("#7A287B") // Purple (C3)
            "4", "40" -> Color.parseColor("#E52321") // Red (C4)
            "5", "50" -> Color.parseColor("#009639") // Green (C5)
            "6", "60" -> Color.parseColor("#002F6C") // Dark Blue (C6)
            else -> Color.parseColor("#00A3E0")
        }
    }

    private fun snapToPolyline(lat: Double, lon: Double, linePoints: List<List<GeoPoint>>): GeoPoint? {
        if (linePoints.isEmpty()) return null
        var closestPoint: GeoPoint? = null
        var minDistance = Double.MAX_VALUE
        
        for (segment in linePoints) {
            for (p in segment) {
                val dLat = p.latitude - lat
                val dLon = p.longitude - lon
                val distSq = dLat * dLat + dLon * dLon
                if (distSq < minDistance) {
                    minDistance = distSq
                    closestPoint = p
                }
            }
        }
        // Limit snap distance to ~1.0 km (0.000100 sq deg) to prevent snapping across distant lines
        return if (minDistance < 0.000100) closestPoint else null
    }

    private fun getCanonicalLineNum(routeId: String): String {
        return com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(routeId)
    }

    fun clear() {
        for (m in recycledMarkers) {
            mapView.overlays.remove(m)
        }
        recycledMarkers.clear()
        iconCache.clear()
        trainAnimStates.clear()
    }
}
