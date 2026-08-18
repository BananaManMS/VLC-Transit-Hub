package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat

private val metroStationIconCache = LruCache<String, MarkerIconResult>(64)

internal fun getMetroCompactDotIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "METRO_COMPACT_DOT_${isDarkMode}"
    var cached = metroStationIconCache.get(key)
    if (cached == null) {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Soft drop shadow
        paint.color = Color.argb(45, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f + 2f, size / 2f - 4f, paint)

        // 2. Outer white border
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

        // 3. Inner filled Metro Red circle
        paint.color = Color.parseColor("#E2001A")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8.5f, paint)

        // 4. Elegant white "m" in the center representing Metrovalencia
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val y = (size / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("m", size / 2f, y - 2.5f, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cached = MarkerIconResult(drawable, 0.5f, 0.5f)
        metroStationIconCache.put(key, cached)
    }
    return cached
}

internal fun getMetroMarkerIcon(context: Context, stationName: String, isDarkMode: Boolean, showPill: Boolean): MarkerIconResult {
    val key = "METRO_${stationName}_${isDarkMode}_${showPill}"
    var cached = metroStationIconCache.get(key)
    if (cached == null) {
        cached = createMetroMarkerIcon(context, stationName, isDarkMode, showPill)
        metroStationIconCache.put(key, cached)
    }
    return cached
}

internal fun createMetroMarkerIcon(context: Context, stationName: String, isDarkMode: Boolean, showPill: Boolean): MarkerIconResult {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.WHITE else Color.parseColor("#0F172A")
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    val logoRadius = 32f
    val spacing = 8f

    val totalWidth: Int
    val totalHeight: Int
    val logoCenterX: Float
    val logoCenterY: Float
    val anchorU: Float
    val anchorV: Float

    if (showPill) {
        val textWidth = textPaint.measureText(stationName)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val pillPaddingH = 16f
        val pillPaddingV = 8f
        val pillWidth = textWidth + (pillPaddingH * 2f)
        val pillHeight = textHeight + (pillPaddingV * 2f)

        logoCenterX = logoRadius + 10f
        logoCenterY = logoRadius + 10f

        val pillLeft = logoCenterX + logoRadius + spacing

        totalWidth = (pillLeft + pillWidth + 10f).toInt()
        totalHeight = (logoCenterY + logoRadius + 10f).toInt()

        anchorU = logoCenterX / totalWidth
        anchorV = logoCenterY / totalHeight
    } else {
        totalWidth = (logoRadius * 2f + 20f).toInt()
        totalHeight = (logoRadius * 2f + 20f).toInt()

        logoCenterX = totalWidth / 2f
        logoCenterY = totalHeight / 2f

        anchorU = 0.5f
        anchorV = 0.5f
    }

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    if (showPill) {
        val textWidth = textPaint.measureText(stationName)
        val fontMetrics = textPaint.fontMetrics

        val pillPaddingH = 16f
        val pillPaddingV = 8f
        val pillWidth = textWidth + (pillPaddingH * 2f)
        val pillHeight = (fontMetrics.descent - fontMetrics.ascent) + (pillPaddingV * 2f)

        val pillLeft = logoCenterX + logoRadius + spacing
        val pillTop = logoCenterY - pillHeight / 2f
        val pillRight = pillLeft + pillWidth
        val pillBottom = pillTop + pillHeight

        // 1. Draw station name pill shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(30, 0, 0, 0)
        val shadowRect = android.graphics.RectF(
            pillLeft + 1f,
            pillTop + 2f,
            pillRight + 1f,
            pillBottom + 3f
        )
        canvas.drawRoundRect(shadowRect, pillHeight / 2f, pillHeight / 2f, paint)

        // 2. Draw station name pill background
        paint.color = if (isDarkMode) Color.parseColor("#1E293B") else Color.WHITE
        val pillRect = android.graphics.RectF(pillLeft, pillTop, pillRight, pillBottom)
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, paint)

        // 3. Stroke border (Red for Metrovalencia)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.parseColor("#E2001A")
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, paint)

        // 4. Text inside pill
        paint.style = Paint.Style.FILL
        val textY = pillTop + pillPaddingV - fontMetrics.ascent
        canvas.drawText(stationName, pillLeft + pillWidth / 2f, textY, textPaint)
    }

    // 2. Draw Metrovalencia Logo Image or Fallback Circle
    val logoDrawable = try {
        ResourcesCompat.getDrawable(context.resources, com.example.R.drawable.logo_metrovalencia, context.theme)
    } catch (e: Exception) {
        null
    }

    if (logoDrawable != null) {
        // Soft drop shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(
            logoCenterX - logoRadius + 2f,
            logoCenterY - logoRadius + 4f,
            logoCenterX + logoRadius + 2f,
            logoCenterY + logoRadius + 4f,
            paint
        )

        // Draw clean white circular background
        paint.color = Color.WHITE
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

        // Calculate aspect ratio so the logo isn't squished or stretched
        val intrinsicWidth = logoDrawable.intrinsicWidth.toFloat()
        val intrinsicHeight = logoDrawable.intrinsicHeight.toFloat()
        val maxLogoSize = logoRadius * 1.6f
        val w: Float
        val h: Float
        if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            val ratio = intrinsicWidth / intrinsicHeight
            if (ratio > 1f) {
                w = maxLogoSize
                h = maxLogoSize / ratio
            } else {
                h = maxLogoSize
                w = maxLogoSize * ratio
            }
        } else {
            w = maxLogoSize
            h = maxLogoSize
        }
        val left = logoCenterX - w / 2f
        val top = logoCenterY - h / 2f

        logoDrawable.setBounds(
            left.toInt(),
            top.toInt(),
            (left + w).toInt(),
            (top + h).toInt()
        )
        logoDrawable.draw(canvas)
    } else {
        // Fallback to classic custom m text red logo
        val metroRed = Color.parseColor("#E2001A")
        
        // Soft drop shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(logoCenterX - logoRadius + 2f, logoCenterY - logoRadius + 4f, logoCenterX + logoRadius + 2f, logoCenterY + logoRadius + 4f, paint)

        // Solid red circle
        paint.color = metroRed
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

        // Subtle white outer border stroke
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 1.25f, paint)

        // Draw Metrovalencia white lowercase 'm'
        val mPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val mMetrics = mPaint.fontMetrics
        val mY = logoCenterY - (mMetrics.ascent + mMetrics.descent) / 2f
        canvas.drawText("m", logoCenterX + logoRadius - 3f, mY, mPaint)
    }

    return MarkerIconResult(BitmapDrawable(context.resources, bitmap), anchorU, anchorV)
}

internal fun getMetroWhiteDotIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "METRO_WHITE_DOT_${isDarkMode}"
    var cached = metroStationIconCache.get(key)
    if (cached == null) {
        val size = 24
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Soft dark border / shadow for high contrast on light backgrounds
        paint.color = Color.argb(120, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // 2. Pure white inner circle
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2.5f, paint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cached = MarkerIconResult(drawable, 0.5f, 0.5f)
        metroStationIconCache.put(key, cached)
    }
    return cached
}

internal fun getMetroTinyDotIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "METRO_TINY_DOT_${isDarkMode}"
    var cached = metroStationIconCache.get(key)
    if (cached == null) {
        val size = 20
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Outer dark stroke / shadow
        paint.color = Color.argb(100, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // 2. White outer circle
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1.5f, paint)

        // 3. Inner filled Red dot
        paint.color = Color.parseColor("#E2001A")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3.5f, paint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cached = MarkerIconResult(drawable, 0.5f, 0.5f)
        metroStationIconCache.put(key, cached)
    }
    return cached
}


