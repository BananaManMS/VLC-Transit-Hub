package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import com.example.util.LineColorResolver
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Pure rendering delegate and bitmap creator for real-time animated train markers.
 * Keeps an LRU bitmap cache for Cercanías line icons to minimize memory allocations.
 */
object LiveTrainMarkerRenderer {

    private val trainBitmapCache = LruCache<String, Bitmap>(16)

    /**
     * Creates or retrieves a cached train marker bitmap.
     */
    fun getOrCreateTrainMarkerBitmap(context: Context, lineId: String, isDarkMode: Boolean): Bitmap {
        val cacheKey = "LIVE_TRAIN_${lineId}_$isDarkMode"
        var cached = trainBitmapCache.get(cacheKey)
        if (cached == null || cached.isRecycled) {
            cached = createTrainMarkerBitmap(context, lineId, isDarkMode)
            trainBitmapCache.put(cacheKey, cached)
        }
        return cached
    }

    /**
     * Instantiates a new osmdroid Marker for the live train on the map.
     */
    fun createLiveTrainMarker(
        context: Context,
        mapView: MapView,
        lineId: String,
        isDarkMode: Boolean,
        status: String
    ): Marker {
        return Marker(mapView).apply {
            infoWindow = null
            setOnMarkerClickListener { _, _ -> true }
            val trainIcon = getOrCreateTrainMarkerBitmap(context, lineId, isDarkMode)
            icon = BitmapDrawable(context.resources, trainIcon)
            setAnchor(0.5f, 0.5f)
            title = "Tren Cercanías $lineId"
            snippet = if (status.isNotBlank()) "En trayecto • $status" else "En trayecto en tiempo real"
        }
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
}
