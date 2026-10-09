package com.example.ui.map.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker
import java.lang.ref.WeakReference

/**
 * Modern, subtle User Location Marker on the osmdroid map:
 * 1. Slow, subtle, compact semi-transparent breathing pulse circle in the background.
 * 2. Clean, compact vibrant blue ball with crisp white border.
 * 3. Directional pointer triangle indicating forward heading/bearing.
 * 4. Lightweight orientation sensor listener for real-time bearing updates without battery drain.
 * 5. Smooth sliding animation (Point A to Point B) when user location updates.
 */
class UserLocationMarker(mapView: MapView) : Marker(mapView), SensorEventListener {

    private val mapRef = WeakReference(mapView)
    private val density: Float = mapView.context.resources.displayMetrics.density

    // Smooth heading bearing (in degrees, 0 = North, clockwise)
    var userHeading: Float = 0f
    private var smoothedBearing: Float = 0f
    private var gpsBearing: Float? = null

    // Cell tower subterranean localization mode (green dot, no directional arrow)
    var isCellTowerLocation: Boolean = false

    // Smooth position sliding animation (Point A to Point B)
    private var currentAnimatedLocation: GeoPoint? = null
    private var positionAnimator: ValueAnimator? = null

    fun animateToPosition(targetLocation: GeoPoint, durationMs: Long = 450L) {
        val startLoc = currentAnimatedLocation ?: mPosition
        if (startLoc == null) {
            currentAnimatedLocation = GeoPoint(targetLocation.latitude, targetLocation.longitude)
            super.setPosition(currentAnimatedLocation)
            mapRef.get()?.postInvalidate()
            return
        }

        val distanceMeters = startLoc.distanceToAsDouble(targetLocation)
        if (distanceMeters < 0.1) {
            currentAnimatedLocation = GeoPoint(targetLocation.latitude, targetLocation.longitude)
            super.setPosition(currentAnimatedLocation)
            return
        }

        if (distanceMeters > 800.0) {
            positionAnimator?.cancel()
            positionAnimator = null
            currentAnimatedLocation = GeoPoint(targetLocation.latitude, targetLocation.longitude)
            super.setPosition(currentAnimatedLocation)
            mapRef.get()?.postInvalidate()
            return
        }

        positionAnimator?.cancel()

        val startLat = startLoc.latitude
        val startLon = startLoc.longitude
        val targetLat = targetLocation.latitude
        val targetLon = targetLocation.longitude

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val lat = startLat + (targetLat - startLat) * fraction
                val lon = startLon + (targetLon - startLon) * fraction
                val interpolated = GeoPoint(lat, lon)
                currentAnimatedLocation = interpolated
                super.setPosition(interpolated)
                mapRef.get()?.postInvalidate()
            }
        }
        positionAnimator = animator
        animator.start()
    }

    override fun setPosition(aPosition: GeoPoint?) {
        if (aPosition == null) {
            positionAnimator?.cancel()
            positionAnimator = null
            currentAnimatedLocation = null
            super.setPosition(null)
            return
        }
        animateToPosition(aPosition)
    }

    fun updateCellTowerMode(isCellTower: Boolean) {
        if (isCellTowerLocation != isCellTower) {
            isCellTowerLocation = isCellTower
        }
    }

    // Pre-allocated drawing objects to prevent GC allocations per frame
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pulseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
    }
    private val whiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val blueCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1D4ED8") // Material vibrant blue
    }
    private val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1D4ED8")
    }
    private val arrowWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val outerArrowPath = Path()

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    init {
        title = "Tu ubicación"
        infoWindow = null
        setOnMarkerClickListener { _, _ -> true }
        setAnchor(ANCHOR_CENTER, ANCHOR_CENTER)

        try {
            sensorManager = mapView.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
            rotationSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {}
    }

    fun updateGpsBearing(bearing: Float?) {
        gpsBearing = bearing
    }

    override fun draw(canvas: Canvas, pj: Projection) {
        val pos = mPosition
        if (!isEnabled || pos == null) return

        pj.toPixels(pos, mPositionPixels)
        val cx = mPositionPixels.x.toFloat()
        val cy = mPositionPixels.y.toFloat()

        val coreColor = if (isCellTowerLocation) Color.parseColor("#059669") else Color.parseColor("#1D4ED8")
        val pulseColor = if (isCellTowerLocation) Color.parseColor("#10B981") else Color.parseColor("#2563EB")
        val strokeColor = if (isCellTowerLocation) Color.parseColor("#34D399") else Color.parseColor("#3B82F6")

        // 1. Slow, subtle breathing pulse animation (sinusoidal 4.0-second cycle)
        val timeMs = SystemClock.uptimeMillis() % 4000L
        val phase = (timeMs / 4000.0) * 2.0 * Math.PI - (Math.PI / 2.0)
        val breath = ((Math.sin(phase) + 1.0) / 2.0).toFloat() // 0.0 -> 1.0 -> 0.0

        val minPulseRadius = 11.5f * density
        val maxPulseRadius = 22f * density
        val currentPulseRadius = minPulseRadius + (maxPulseRadius - minPulseRadius) * breath

        // Subtle, gentle alpha (between 10 and 26)
        val currentPulseAlpha = (10 + 16 * (1f - breath * 0.7f)).toInt().coerceIn(0, 255)
        pulsePaint.color = pulseColor
        pulsePaint.alpha = currentPulseAlpha
        canvas.drawCircle(cx, cy, currentPulseRadius, pulsePaint)

        pulseStrokePaint.color = strokeColor
        pulseStrokePaint.alpha = (currentPulseAlpha * 0.4f).toInt()
        canvas.drawCircle(cx, cy, currentPulseRadius, pulseStrokePaint)

        val dynamicBallRadius = (6.0f + 0.9f * breath) * density
        val whiteHaloRadius = 9.5f * density

        canvas.save()

        // 2. Directional pointer triangle attached in heading direction (only when GNSS satellite has bearing, hidden for cellular tower)
        if (!isCellTowerLocation) {
            // Target angle: GPS bearing if available and moving, otherwise compass heading
            val targetBearing = gpsBearing ?: userHeading
            // Smoothly interpolate angle to avoid jitter
            val angleDiff = ((targetBearing - smoothedBearing + 540f) % 360f) - 180f
            smoothedBearing = (smoothedBearing + angleDiff * 0.25f) % 360f
            if (smoothedBearing < 0f) smoothedBearing += 360f

            canvas.rotate(smoothedBearing, cx, cy)

            // Equilateral triangle (side width = 9.0dp, height = 7.8dp), positioned slightly outer to the white halo (gap of 1.5dp)
            val baseDistance = 11f * density
            val tipDistance = 18.8f * density
            val baseHalfWidth = 4.5f * density

            outerArrowPath.reset()
            outerArrowPath.moveTo(cx, cy - tipDistance)
            outerArrowPath.lineTo(cx + baseHalfWidth, cy - baseDistance)
            outerArrowPath.lineTo(cx - baseHalfWidth, cy - baseDistance)
            outerArrowPath.close()

            arrowFillPaint.color = coreColor
            // White outline on the directional arrow
            canvas.drawPath(outerArrowPath, arrowWhitePaint)
            // Fill on the directional arrow
            canvas.drawPath(outerArrowPath, arrowFillPaint)
        }

        // 3. Crisp white outer halo circle
        canvas.drawCircle(cx, cy, whiteHaloRadius, whiteHaloPaint)

        // 4. Vibrant inner core circle (emerald green for cellular tower, vibrant blue for satellite GNSS)
        blueCorePaint.color = coreColor
        canvas.drawCircle(cx, cy, dynamicBallRadius, blueCorePaint)

        canvas.restore()

        // Schedule next frame for smooth breathing pulse animation
        if (isEnabled) {
            val mv = mapRef.get()
            if (mv != null && mv.isAttachedToWindow) {
                mv.postInvalidateDelayed(33)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            if (azimuthDeg < 0f) azimuthDeg += 360f
            userHeading = azimuthDeg
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            var azimuthDeg = event.values[0]
            if (azimuthDeg < 0f) azimuthDeg += 360f
            userHeading = azimuthDeg
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDetach(mapView: MapView) {
        super.onDetach(mapView)
        try {
            positionAnimator?.cancel()
            positionAnimator = null
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }
}
