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

private val cercaniasStationIconCache = LruCache<String, MarkerIconResult>(64)

internal fun getCercaniasMarkerIcon(context: Context, stationName: String, isDarkMode: Boolean, showPill: Boolean): MarkerIconResult {
    val key = "CERCANIAS_${stationName}_${isDarkMode}_${showPill}"
    var cached = cercaniasStationIconCache.get(key)
    if (cached == null) {
        cached = createCercaniasMarkerIcon(context, stationName, isDarkMode, showPill)
        cercaniasStationIconCache.put(key, cached)
    }
    return cached
}

internal fun createCercaniasMarkerIcon(context: Context, stationName: String, isDarkMode: Boolean, showPill: Boolean): MarkerIconResult {
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

        // 3. Stroke border (Red/Orange for Cercanias)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.parseColor("#E2001A")
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, paint)

        // 4. Text inside pill
        paint.style = Paint.Style.FILL
        val textY = pillTop + pillPaddingV - fontMetrics.ascent
        canvas.drawText(stationName, pillLeft + pillWidth / 2f, textY, textPaint)
    }

    // 2. Draw Cercanias Logo Image or Fallback Circle
    val logoDrawable = try {
        ResourcesCompat.getDrawable(context.resources, com.example.R.drawable.logo_cercanias, context.theme)
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
        // Fallback to classic Cercanias purple logo circle & letter C
        val cercaniasPurple = Color.parseColor("#702B7B")
        
        // Soft drop shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(logoCenterX - logoRadius + 2f, logoCenterY - logoRadius + 4f, logoCenterX + logoRadius + 2f, logoCenterY + logoRadius + 4f, paint)

        // Solid purple circle
        paint.color = cercaniasPurple
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

        // White inner circle ring
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 2.5f, paint)

        // White inner circle background
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 3.5f, paint)

        // Draw classic Cercanias "C" logo letter
        val cPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cercaniasPurple
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cMetrics = cPaint.fontMetrics
        val cY = logoCenterY - (cMetrics.ascent + cMetrics.descent) / 2f
        canvas.drawText("C", logoCenterX, cY, cPaint)
    }

    return MarkerIconResult(BitmapDrawable(context.resources, bitmap), anchorU, anchorV)
}

internal fun getCercaniasCompactDotIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "CERCANIAS_COMPACT_DOT_${isDarkMode}"
    var cached = cercaniasStationIconCache.get(key)
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

        // 3. Inner filled Cercanías Purple circle
        paint.color = Color.parseColor("#702B7B")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8.5f, paint)

        // 4. Elegant white "c" in the center representing Cercanías
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val y = (size / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("c", size / 2f, y - 2.5f, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cached = MarkerIconResult(drawable, 0.5f, 0.5f)
        cercaniasStationIconCache.put(key, cached)
    }
    return cached
}

internal fun getCercaniasTinyDotIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "CERCANIAS_TINY_DOT_${isDarkMode}"
    var cached = cercaniasStationIconCache.get(key)
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

        // 3. Inner filled Purple dot
        paint.color = Color.parseColor("#702B7B")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3.5f, paint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cached = MarkerIconResult(drawable, 0.5f, 0.5f)
        cercaniasStationIconCache.put(key, cached)
    }
    return cached
}

internal fun getCercaniasLogoSmallIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "CERCANIAS_LOGO_SMALL_${isDarkMode}"
    var cached = cercaniasStationIconCache.get(key)
    if (cached == null) {
        cached = createCercaniasMarkerIconSmall(context, isDarkMode)
        cercaniasStationIconCache.put(key, cached)
    }
    return cached
}

internal fun createCercaniasMarkerIconSmall(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val logoRadius = 18f
    val totalWidth = (logoRadius * 2f + 10f).toInt()
    val totalHeight = (logoRadius * 2f + 10f).toInt()

    val logoCenterX = totalWidth / 2f
    val logoCenterY = totalHeight / 2f

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val logoDrawable = try {
        ResourcesCompat.getDrawable(context.resources, com.example.R.drawable.logo_cercanias, context.theme)
    } catch (e: Exception) {
        null
    }

    if (logoDrawable != null) {
        // Soft drop shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(
            logoCenterX - logoRadius + 1f,
            logoCenterY - logoRadius + 2f,
            logoCenterX + logoRadius + 1f,
            logoCenterY + logoRadius + 2f,
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
        val cercaniasPurple = Color.parseColor("#702B7B")
        
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(logoCenterX - logoRadius + 1f, logoCenterY - logoRadius + 2f, logoCenterX + logoRadius + 1f, logoCenterY + logoRadius + 2f, paint)

        paint.color = cercaniasPurple
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 1.5f, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 2.5f, paint)

        val cPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cercaniasPurple
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cMetrics = cPaint.fontMetrics
        val cY = logoCenterY - (cMetrics.ascent + cMetrics.descent) / 2f
        canvas.drawText("C", logoCenterX, cY, cPaint)
    }

    return MarkerIconResult(BitmapDrawable(context.resources, bitmap), 0.5f, 0.5f)
}

