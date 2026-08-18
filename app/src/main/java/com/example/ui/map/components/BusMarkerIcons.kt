package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat

data class MarkerIconResult(
    val drawable: Drawable,
    val anchorU: Float,
    val anchorV: Float
)

private val busIconCache = LruCache<String, Drawable>(64)

internal fun getMarkerIcon(context: Context, label: String, primaryColor: Int): Drawable {
    val key = "MARKER_${label}_${primaryColor}"
    var cached = busIconCache.get(key)
    if (cached == null) {
        cached = createMarkerIcon(context, label, primaryColor)
        busIconCache.put(key, cached)
    }
    return cached
}

internal fun getCompactDotIcon(context: Context, primaryColor: Int): Drawable {
    val key = "COMPACT_DOT_${primaryColor}"
    var cached = busIconCache.get(key)
    if (cached == null) {
        cached = createCompactDotIcon(context, primaryColor)
        busIconCache.put(key, cached)
    }
    return cached
}

internal fun createCompactDotIcon(context: Context, primaryColor: Int): Drawable {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Soft drop shadow
    paint.color = Color.argb(40, 0, 0, 0)
    canvas.drawCircle(size / 2f, size / 2f + 2f, size / 2f - 4f, paint)

    // 2. Outer white border
    paint.color = Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

    // 3. Inner filled colored dot
    paint.color = primaryColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8.5f, paint)

    return BitmapDrawable(context.resources, bitmap)
}

internal fun createMarkerIcon(context: Context, label: String, primaryColor: Int): Drawable {
    if (label == "MB" || label == "METROBUS") {
        // Metrobús icon with amber background and logo_metrobus inside
        val size = 50
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val centerX = size / 2f
        val centerY = size / 2f

        // Soft drop shadow
        paint.color = Color.argb(45, 0, 0, 0)
        paint.style = Paint.Style.FILL
        val shadowRect = android.graphics.RectF(3f, 5f, size - 1f, size - 1f)
        canvas.drawRoundRect(shadowRect, 10f, 10f, paint)

        // Amber/Yellow rounded square background
        paint.color = Color.parseColor("#FFB300")
        val squareRect = android.graphics.RectF(2f, 2f, size - 4f, size - 4f)
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

        // Dark border stroke around square
        paint.color = Color.parseColor("#D97706")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

        // Draw logo_metrobus inside
        val logoDrawable = try {
            ResourcesCompat.getDrawable(context.resources, com.example.R.drawable.logo_metrobus, context.theme)
        } catch (e: Exception) {
            null
        }

        if (logoDrawable != null) {
            val intrinsicWidth = logoDrawable.intrinsicWidth.toFloat()
            val intrinsicHeight = logoDrawable.intrinsicHeight.toFloat()
            if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                val ratio = intrinsicWidth / intrinsicHeight
                val targetMax = size - 12f
                val w: Float
                val h: Float
                if (ratio > 1f) {
                    w = targetMax
                    h = targetMax / ratio
                } else {
                    h = targetMax
                    w = targetMax * ratio
                }
                val left = centerX - w / 2f
                val top = centerY - h / 2f
                logoDrawable.setBounds(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt())
            } else {
                logoDrawable.setBounds(6, 6, size - 6, size - 6)
            }
            logoDrawable.draw(canvas)
        } else {
            // Fallback: white "m" letter inside
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 24f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            val fontMetrics = paint.fontMetrics
            val baseline = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText("m", centerX, baseline, paint)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    if (label == "BUS") {
        // Clean rounded square with EMT logo inside on a white background
        val size = 50
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val centerX = size / 2f
        val centerY = size / 2f

        // Soft drop shadow
        paint.color = Color.argb(45, 0, 0, 0)
        paint.style = Paint.Style.FILL
        val shadowRect = android.graphics.RectF(3f, 5f, size - 1f, size - 1f)
        canvas.drawRoundRect(shadowRect, 10f, 10f, paint)

        // White rounded square background
        paint.color = Color.WHITE
        val squareRect = android.graphics.RectF(2f, 2f, size - 4f, size - 4f)
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

        // Draw thin EMT blue border stroke around square
        paint.color = Color.parseColor("#2563EB") // Nice bus/EMT blue
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

        // Draw the EMT logo inside
        val logoDrawable = try {
            ResourcesCompat.getDrawable(context.resources, com.example.R.drawable.logo_emt_valencia, context.theme)
        } catch (e: Exception) {
            null
        }

        if (logoDrawable != null) {
            val intrinsicWidth = logoDrawable.intrinsicWidth.toFloat()
            val intrinsicHeight = logoDrawable.intrinsicHeight.toFloat()
            if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                val ratio = intrinsicWidth / intrinsicHeight
                val targetMax = size - 14f // leave padding
                val w: Float
                val h: Float
                if (ratio > 1f) {
                    w = targetMax
                    h = targetMax / ratio
                } else {
                    h = targetMax
                    w = targetMax * ratio
                }
                val left = centerX - w / 2f
                val top = centerY - h / 2f
                logoDrawable.setBounds(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt())
            } else {
                logoDrawable.setBounds(8, 8, size - 8, size - 8)
            }
            logoDrawable.draw(canvas)
        } else {
            // Fallback inside square: draw bus silhouette in primaryColor (Red)
            paint.style = Paint.Style.FILL
            paint.color = primaryColor
            val busLeft = centerX - 12f
            val busTop = centerY - 12f
            val busRight = centerX + 12f
            val busBottom = centerY + 10f
            canvas.drawRoundRect(android.graphics.RectF(busLeft, busTop, busRight, busBottom), 4f, 4f, paint)

            // White bus windshield cutout
            paint.color = Color.WHITE
            canvas.drawRect(busLeft + 2.5f, busTop + 2.5f, busRight - 2.5f, busTop + 8f, paint)

            // Yellow headlights
            val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBBF24") }
            canvas.drawCircle(busLeft + 4f, busBottom - 3.5f, 1.8f, lightPaint)
            canvas.drawCircle(busRight - 4f, busBottom - 3.5f, 1.8f, lightPaint)

            // Wheels
            paint.color = Color.parseColor("#1E293B")
            canvas.drawRoundRect(android.graphics.RectF(busLeft + 2.5f, busBottom, busLeft + 6f, busBottom + 2.5f), 1f, 1f, paint)
            canvas.drawRoundRect(android.graphics.RectF(busRight - 6f, busBottom, busRight - 2.5f, busBottom + 2.5f), 1f, 1f, paint)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    val size = 68
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Drop shadow
    paint.color = Color.argb(45, 0, 0, 0)
    canvas.drawOval(2f, 4f, size - 2f, size.toFloat(), paint)

    // Circle Pin body
    paint.color = primaryColor
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

    // White border ring around the body
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3.5f
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

    // White inner circle
    paint.style = Paint.Style.FILL
    paint.color = Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 12f, paint)

    // Draw custom detailed Bus / Train icon inside the white circle
    val centerX = size / 2f
    val centerY = size / 2f

    if (label == "BUS") {
        // Draw a detailed red bus front view
        paint.color = primaryColor
        paint.style = Paint.Style.FILL
        val busLeft = centerX - 14f
        val busTop = centerY - 15f
        val busRight = centerX + 14f
        val busBottom = centerY + 12f
        canvas.drawRoundRect(android.graphics.RectF(busLeft, busTop, busRight, busBottom), 5f, 5f, paint)

        // Windshield window
        paint.color = Color.WHITE
        canvas.drawRect(busLeft + 3f, busTop + 3f, busRight - 3f, busTop + 10f, paint)

        // Tires
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(android.graphics.RectF(busLeft + 3f, busBottom, busLeft + 7f, busBottom + 3.5f), 1.5f, 1.5f, paint)
        canvas.drawRoundRect(android.graphics.RectF(busRight - 7f, busBottom, busRight - 3f, busBottom + 3.5f), 1.5f, 1.5f, paint)

        // Headlights
        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBBF24") }
        canvas.drawCircle(busLeft + 5.5f, busBottom - 4.5f, 2.5f, lightPaint)
        canvas.drawCircle(busRight - 5.5f, busBottom - 4.5f, 2.5f, lightPaint)

        // License plate
        paint.color = Color.WHITE
        canvas.drawRect(centerX - 4.5f, busBottom - 6f, centerX + 4.5f, busBottom - 4f, paint)
    } else {
        // Generic modern train front view
        paint.color = primaryColor
        paint.style = Paint.Style.FILL
        val trainLeft = centerX - 14f
        val trainTop = centerY - 15f
        val trainRight = centerX + 14f
        val trainBottom = centerY + 12f
        canvas.drawRoundRect(android.graphics.RectF(trainLeft, trainTop, trainRight, trainBottom), 6f, 6f, paint)

        // Window
        paint.color = Color.WHITE
        canvas.drawRect(trainLeft + 3f, trainTop + 3f, trainRight - 3f, trainTop + 10f, paint)

        // Headlights
        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBBF24") }
        canvas.drawCircle(trainLeft + 5.5f, trainBottom - 4.5f, 2.5f, lightPaint)
        canvas.drawCircle(trainRight - 5.5f, trainBottom - 4.5f, 2.5f, lightPaint)
    }

    return BitmapDrawable(context.resources, bitmap)
}
