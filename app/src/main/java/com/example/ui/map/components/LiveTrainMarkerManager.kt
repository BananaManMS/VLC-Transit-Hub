package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.RealTimeTransitRepository
import com.example.data.repository.routing.TransitIdMapper
import com.example.ui.cercanias.LiveVehicleInfo
import com.example.util.LineColorResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages the single real-time animated train marker for the active Cercanías leg in an itinerary.
 * Uses 20-second polling cadence with continuous smooth position/bearing interpolation along the rail line.
 */
object LiveTrainMarkerManager {

    private var activeTrainMarker: Marker? = null
    private var pollingJob: Job? = null
    private var animationRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var startPoint: GeoPoint? = null
    private var targetPoint: GeoPoint? = null
    private var startBearing: Float = 0f
    private var targetBearing: Float = 0f
    private var animStartTime: Long = 0L
    private const val ANIMATION_DURATION_MS = 20_000L // 20s transition matching API updates
    private val interpolator: Interpolator = LinearInterpolator()

    private var lastMatchedTripId: String? = null
    private var lastMatchedLine: String? = null

    /**
     * Updates or starts the live train tracking overlay for the given itinerary.
     */
    fun bindLiveTrain(
        context: Context,
        mapView: MapView,
        itinerary: PlannedItinerary?,
        coroutineScope: CoroutineScope,
        isDarkMode: Boolean
    ) {
        // Find if this itinerary has a RAIL leg
        val railLeg = itinerary?.legs?.firstOrNull { it.mode == TransitMode.RAIL }
        if (railLeg == null) {
            clearLiveTrain(mapView)
            return
        }

        val cercaniasLine = TransitIdMapper.extractCercaniasLine(
            railLeg.routeShortName,
            railLeg.routeLongName,
            railLeg.agencyName,
            railLeg.mode
        ) ?: railLeg.routeShortName ?: ""

        // Cancel previous polling if any
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val livePositions = RealTimeTransitRepository.getCercaniasLivePositions()
                    val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                    val stopIdDigits = railLeg.fromStopId?.filter { it.isDigit() }

                    // Find single matching train for this leg
                    val matchingVehicle = findMatchingTrain(livePositions, tripUpdates, cercaniasLine, stopIdDigits)

                    if (matchingVehicle != null && matchingVehicle.latitude != null && matchingVehicle.longitude != null) {
                        val rawLat = matchingVehicle.latitude
                        val rawLon = matchingVehicle.longitude
                        val rawPoint = GeoPoint(rawLat, rawLon)

                        // Snap point and bearing smoothly to the actual polyline geometry of this rail leg
                        val (snappedPoint, bearing) = projectOnLegGeometry(rawPoint, railLeg)

                        withContext(Dispatchers.Main) {
                            updateOrAnimateMarker(
                                context = context,
                                mapView = mapView,
                                lineId = cercaniasLine,
                                tripId = matchingVehicle.tripId,
                                newTarget = snappedPoint,
                                newBearing = bearing,
                                isDarkMode = isDarkMode,
                                status = matchingVehicle.status
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LiveTrainMarkerManager", "Error fetching live train: ${e.message}")
                }
                delay(20_000L) // 20s official Renfe GTFS-RT update cadence
            }
        }
    }

    private fun findMatchingTrain(
        livePositions: Map<String, LiveVehicleInfo>,
        tripUpdates: Map<String, com.example.data.repository.renfe.GtfsRtTripUpdate>,
        targetLine: String,
        targetStopDigits: String?
    ): LiveVehicleInfo? {
        val cleanLine = targetLine.replace("-", "").uppercase()

        // 1. Match vehicle by line in routeId or tripId
        val lineVehicles = livePositions.values.filter { veh ->
            val routeClean = veh.routeId.replace("-", "").uppercase()
            val tripClean = veh.tripId.replace("-", "").uppercase()
            (cleanLine.isNotBlank() && (routeClean.contains(cleanLine) || tripClean.contains(cleanLine)))
        }

        if (lineVehicles.isNotEmpty()) {
            // Pick the vehicle nearest or active for this trip
            return lineVehicles.first()
        }

        // 2. Fallback: match by tripUpdates containing target stop or line
        val matchingUpdate = tripUpdates.values.firstOrNull { update ->
            val tripId = update.tripId
            val lineMatches = cleanLine.isNotBlank() && tripId.contains(cleanLine, ignoreCase = true)
            val stopMatches = !targetStopDigits.isNullOrBlank() && (update.stopDelays.containsKey(targetStopDigits) || update.stopEstimatedTimes.containsKey(targetStopDigits))
            lineMatches || stopMatches
        }

        if (matchingUpdate != null) {
            return livePositions[matchingUpdate.tripId]
        }

        return null
    }

    /**
     * Snaps a GPS coordinate onto the leg's polyline geometry and computes the forward bearing angle.
     */
    private fun projectOnLegGeometry(rawPoint: GeoPoint, leg: PlannedLeg): Pair<GeoPoint, Float> {
        val geometry = if (leg.geometry.isNotEmpty()) {
            leg.geometry
        } else if (leg.intermediateStops.isNotEmpty()) {
            leg.intermediateStops.map { GeoPoint(it.lat, it.lon) }
        } else {
            val from = GeoPoint(leg.fromLat, leg.fromLon)
            val to = GeoPoint(leg.toLat, leg.toLon)
            if (from.latitude != 0.0 && to.latitude != 0.0) listOf(from, to) else emptyList()
        }

        if (geometry.size < 2) {
            return Pair(rawPoint, 0f)
        }

        var minDistance = Double.MAX_VALUE
        var bestPoint = rawPoint
        var bestBearing = 0f

        for (i in 0 until geometry.size - 1) {
            val p1 = geometry[i]
            val p2 = geometry[i + 1]
            val (proj, dist) = projectPointOnSegment(rawPoint, p1, p2)
            if (dist < minDistance) {
                minDistance = dist
                bestPoint = proj
                bestBearing = computeBearing(p1, p2)
            }
        }

        // If raw point is too far (> 1.5km off track), keep raw point with forward line bearing
        return if (minDistance > 1500.0) {
            Pair(rawPoint, computeBearing(geometry.first(), geometry.last()))
        } else {
            Pair(bestPoint, bestBearing)
        }
    }

    private fun projectPointOnSegment(p: GeoPoint, v: GeoPoint, w: GeoPoint): Pair<GeoPoint, Double> {
        val l2 = distSq(v, w)
        if (l2 == 0.0) return Pair(v, distanceMeters(p, v))
        val t = (((p.latitude - v.latitude) * (w.latitude - v.latitude) + (p.longitude - v.longitude) * (w.longitude - v.longitude)) / l2).coerceIn(0.0, 1.0)
        val proj = GeoPoint(v.latitude + t * (w.latitude - v.latitude), v.longitude + t * (w.longitude - v.longitude))
        return Pair(proj, distanceMeters(p, proj))
    }

    private fun distSq(p1: GeoPoint, p2: GeoPoint): Double {
        val dLat = p1.latitude - p2.latitude
        val dLon = p1.longitude - p2.longitude
        return dLat * dLat + dLon * dLon
    }

    private fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val latMid = (p1.latitude + p2.latitude) / 2.0 * Math.PI / 180.0
        val dLat = (p2.latitude - p1.latitude) * 111139.0
        val dLon = (p2.longitude - p1.longitude) * 111139.0 * cos(latMid)
        return sqrt(dLat * dLat + dLon * dLon)
    }

    private fun computeBearing(from: GeoPoint, to: GeoPoint): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)
        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var brng = Math.toDegrees(atan2(y, x)).toFloat()
        return (brng + 360f) % 360f
    }

    private fun updateOrAnimateMarker(
        context: Context,
        mapView: MapView,
        lineId: String,
        tripId: String,
        newTarget: GeoPoint,
        newBearing: Float,
        isDarkMode: Boolean,
        status: String
    ) {
        lastMatchedTripId = tripId
        lastMatchedLine = lineId

        var marker = activeTrainMarker
        if (marker == null) {
            marker = Marker(mapView).apply {
                infoWindow = null
                val trainIcon = createTrainMarkerBitmap(context, lineId, isDarkMode)
                icon = BitmapDrawable(context.resources, trainIcon)
                setAnchor(0.5f, 0.5f)
                title = "Tren Cercanías $lineId"
                snippet = if (status.isNotBlank()) "En trayecto • $status" else "En trayecto en tiempo real"
            }
            activeTrainMarker = marker
            if (!mapView.overlays.contains(marker)) {
                mapView.overlays.add(marker)
            }
            marker.position = newTarget
            marker.rotation = newBearing
            startPoint = newTarget
            targetPoint = newTarget
            startBearing = newBearing
            targetBearing = newBearing
            mapView.invalidate()
            return
        }

        // Ensure marker is in overlays list (e.g. if overlays were cleared)
        if (!mapView.overlays.contains(marker)) {
            mapView.overlays.add(marker)
        }

        // Setup smooth continuous interpolation animation
        animationRunnable?.let { mainHandler.removeCallbacks(it) }
        startPoint = marker.position
        targetPoint = newTarget
        startBearing = marker.rotation
        targetBearing = resolveShortestRotation(startBearing, newBearing)
        animStartTime = SystemClock.uptimeMillis()

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - animStartTime
                val fraction = (elapsed.toFloat() / ANIMATION_DURATION_MS).coerceIn(0f, 1f)
                val interpolatedFraction = interpolator.getInterpolation(fraction)

                val sP = startPoint ?: newTarget
                val tP = targetPoint ?: newTarget
                val currentLat = sP.latitude + (tP.latitude - sP.latitude) * interpolatedFraction
                val currentLon = sP.longitude + (tP.longitude - sP.longitude) * interpolatedFraction
                val currentRot = startBearing + (targetBearing - startBearing) * interpolatedFraction

                marker.position = GeoPoint(currentLat, currentLon)
                marker.rotation = currentRot
                mapView.invalidate()

                if (fraction < 1.0f) {
                    mainHandler.postDelayed(this, 30L) // ~33 FPS smooth animation
                }
            }
        }
        animationRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun resolveShortestRotation(fromAngle: Float, toAngle: Float): Float {
        var diff = (toAngle - fromAngle) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return fromAngle + diff
    }

    /**
     * Creates a high-definition, glowing animated train marker with the official Cercanías line color.
     */
    private fun createTrainMarkerBitmap(context: Context, lineId: String, isDarkMode: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (40 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hexColor = LineColorResolver.getCercaniasLineColorHex(lineId)
        val lineColorInt = try { Color.parseColor(hexColor) } catch (e: Exception) { Color.parseColor("#702B7B") }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Soft glowing outer pulse
        paint.color = lineColorInt
        paint.alpha = 50
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // 2. White/Dark Outer Ring
        paint.alpha = 255
        paint.color = if (isDarkMode) Color.parseColor("#0F172A") else Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.40f, paint)

        // 3. Line colored core
        paint.color = lineColorInt
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.33f, paint)

        // 4. Clean Train Silhouette / Icon in Center
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        val trainW = sizePx * 0.30f
        val trainH = sizePx * 0.38f
        val left = (sizePx - trainW) / 2f
        val top = (sizePx - trainH) / 2f
        val rect = RectF(left, top, left + trainW, top + trainH)
        val r = 4f * density
        canvas.drawRoundRect(rect, r, r, paint)

        // Train front windshield
        paint.color = lineColorInt
        val winW = trainW * 0.70f
        val winH = trainH * 0.30f
        val winLeft = (sizePx - winW) / 2f
        val winTop = top + 2.5f * density
        canvas.drawRoundRect(RectF(winLeft, winTop, winLeft + winW, winTop + winH), 2f * density, 2f * density, paint)

        // Headlights
        paint.color = Color.parseColor("#FEF08A") // Bright yellow lights
        val lightRadius = 1.6f * density
        canvas.drawCircle(left + 3f * density, top + trainH - 3.5f * density, lightRadius, paint)
        canvas.drawCircle(left + trainW - 3f * density, top + trainH - 3.5f * density, lightRadius, paint)

        return bitmap
    }

    /**
     * Cleans up marker and cancels polling jobs when the route preview is closed.
     */
    fun clearLiveTrain(mapView: MapView?) {
        pollingJob?.cancel()
        pollingJob = null
        animationRunnable?.let { mainHandler.removeCallbacks(it) }
        animationRunnable = null
        activeTrainMarker?.let { marker ->
            mapView?.overlays?.remove(marker)
        }
        activeTrainMarker = null
        lastMatchedTripId = null
        lastMatchedLine = null
    }
}
