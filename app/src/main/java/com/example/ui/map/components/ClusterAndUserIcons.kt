package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

private val clusterAndUserIconCache = LruCache<String, Drawable>(64)

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
    return getUserLocationIcon(context)
}

// Zero-overhead no-op functions for battery efficiency (gyroscope and animation loops eliminated)
internal fun startLiveLocationUpdates(context: Context) {}
internal fun stopLiveLocationUpdates() {}
