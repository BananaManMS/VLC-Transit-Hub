package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.LruCache

private val clusterAndUserIconCache = LruCache<String, Drawable>(64)

private var lastContext: Context? = null
private var sensorManager: SensorManager? = null
private var targetHeading: Float = 0f
private var smoothHeading: Float = 0f
private var sensorListenerRegistered = false
private var isAnimRunning = false
private var pulsePhase = 0f

private val handler = android.os.Handler(android.os.Looper.getMainLooper())

private val animRunnable = object : Runnable {
    override fun run() {
        if (!isAnimRunning) return
        pulsePhase += 0.08f
        if (pulsePhase > (2 * Math.PI)) {
            pulsePhase -= (2 * Math.PI).toFloat()
        }

        var diff = targetHeading - smoothHeading
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        smoothHeading = (smoothHeading + diff * 0.12f + 360f) % 360f

        val context = lastContext
        val marker = MapMarkersManager.userMarker
        if (context != null && marker != null) {
            marker.icon = createUserLiveIcon(context)
            MapMarkersManager.lastMapView?.invalidate()
        }
        handler.postDelayed(this, 33L)
    }
}

private val sensorListener = object : SensorEventListener {
    private val rotationMatrix = FloatArray(9)
    private val orientationVals = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationVals)
            val azimuthInRadians = orientationVals[0]
            val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
            targetHeading = (azimuthInDegrees + 360) % 360
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            targetHeading = event.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

internal fun getClusterIcon(context: Context, count: Int, primaryColor: Int): Drawable {
    val key = "CLUSTER_${count}_${primaryColor}"
    var cached = clusterAndUserIconCache.get(key)
    if (cached == null) {
        cached = createClusterIcon(context, count, primaryColor)
        clusterAndUserIconCache.put(key, cached)
    }
    return cached
}

internal fun getUserLocationIcon(context: Context): Drawable {
    val key = "USER_LOCATION"
    var cached = clusterAndUserIconCache.get(key)
    if (cached == null) {
        cached = createUserLocationIcon(context)
        clusterAndUserIconCache.put(key, cached)
    }
    return cached
}

internal fun createClusterIcon(context: Context, count: Int, primaryColor: Int): Drawable {
    val size = 68
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer semi-transparent aura
    paint.color = primaryColor
    paint.alpha = 60
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    // Solid primary circle
    paint.alpha = 255
    paint.color = primaryColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, paint)

    // Inner white circle
    paint.color = Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 9f, paint)

    // Primary color center circle
    paint.color = primaryColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 11f, paint)

    // Count Text
    paint.color = Color.WHITE
    paint.textSize = if (count > 99) 19f else 22f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER

    val text = if (count > 999) "999+" else count.toString()
    val fontMetrics = paint.fontMetrics
    val yOffset = (fontMetrics.descent + fontMetrics.ascent) / 2
    canvas.drawText(text, size / 2f, size / 2f - yOffset, paint)

    return BitmapDrawable(context.resources, bitmap)
}

internal fun createUserLocationIcon(context: Context): Drawable {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer pulse glow circle (Soft blue)
    paint.color = Color.parseColor("#333B82F6")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    // Halo border ring
    paint.color = Color.parseColor("#80FFFFFF")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3.5f
    canvas.drawCircle(size / 2f, size / 2f, size / 3.2f, paint)

    // Inner white halo circle
    paint.style = Paint.Style.FILL
    paint.color = Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 3.8f, paint)

    // Blue center dot
    paint.color = Color.parseColor("#3B82F6")
    canvas.drawCircle(size / 2f, size / 2f, size / 5f, paint)

    // Core lighter center gleam
    paint.color = Color.parseColor("#93C5FD")
    canvas.drawCircle(size / 2f - 3f, size / 2f - 3f, size / 14f, paint)

    return BitmapDrawable(context.resources, bitmap)
}

internal fun createUserLiveIcon(context: Context): Drawable {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val center = size / 2f
    val baseRadius = 16f
    val outerRadius = baseRadius + 4f

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3322C55E")
        style = Paint.Style.FILL
    }
    val glowRadius = outerRadius + 8f + 6f * kotlin.math.sin(pulsePhase).toFloat().coerceAtLeast(0f)
    canvas.drawCircle(center, center, glowRadius, glowPaint)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center + 2f, outerRadius, shadowPaint)

    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, outerRadius, whitePaint)

    val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22C55E")
        style = Paint.Style.FILL
    }
    val innerRadius = (outerRadius - 5f) * (1.0f + 0.08f * kotlin.math.sin(pulsePhase).toFloat())
    canvas.drawCircle(center, center, innerRadius, greenPaint)

    val gleamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#86EFAC")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center - innerRadius * 0.3f, center - innerRadius * 0.3f, innerRadius * 0.25f, gleamPaint)

    canvas.save()
    canvas.rotate(smoothHeading, center, center)

    val arrowPath = android.graphics.Path().apply {
        moveTo(center, center - outerRadius - 16f)
        lineTo(center - 9f, center - outerRadius + 1f)
        lineTo(center + 9f, center - outerRadius + 1f)
        close()
    }

    val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawPath(arrowPath, arrowStrokePaint)

    val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }
    canvas.drawPath(arrowPath, arrowFillPaint)

    canvas.restore()

    return BitmapDrawable(context.resources, bitmap)
}

internal fun startLiveLocationUpdates(context: Context) {
    lastContext = context
    if (sensorManager == null) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    if (!sensorListenerRegistered && sensorManager != null) {
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val orientationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        if (rotationSensor != null) {
            sensorManager?.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (orientationSensor != null) {
            sensorManager?.registerListener(sensorListener, orientationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        sensorListenerRegistered = true
    }

    if (!isAnimRunning) {
        isAnimRunning = true
        handler.post(animRunnable)
    }
}

internal fun stopLiveLocationUpdates() {
    if (sensorListenerRegistered && sensorManager != null) {
        sensorManager?.unregisterListener(sensorListener)
        sensorListenerRegistered = false
    }
    if (isAnimRunning) {
        isAnimRunning = false
        handler.removeCallbacks(animRunnable)
    }
}
