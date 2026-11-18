package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache

private val valenbisiIconCache = LruCache<String, MarkerIconResult>(256)

private val COLOR_VALENBISI_EMPTY = Color.parseColor("#94A3B8")
private val COLOR_VALENBISI_LOW = Color.parseColor("#F97316")
private val COLOR_VALENBISI_OK = Color.parseColor("#10B981")
private val COLOR_VALENBISI_LIGHT_TEXT = Color.parseColor("#0F172A")
private val COLOR_VALENBISI_DARK_BG = Color.parseColor("#1E293B")

internal fun getValenbisiCompactDotIcon(context: Context, available: Int): MarkerIconResult {
    val bikeBgColor = if (available == 0) {
        COLOR_VALENBISI_EMPTY // Slate gray if empty
    } else if (available < 3) {
        COLOR_VALENBISI_LOW // Amber if low
    } else {
        COLOR_VALENBISI_OK // Emerald green
    }
    val key = "VALENBISI_COMPACT_DOT_${bikeBgColor}"
    var cached = valenbisiIconCache.get(key)
    if (cached == null) {
        val size = 36 // Small colored dot
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Soft drop shadow
        paint.color = Color.argb(45, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f + 1f, size / 2f - 2f, paint)

        // 2. Outer white border
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)

        // 3. Inner filled color circle
        paint.color = bikeBgColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

        cached = MarkerIconResult(BitmapDrawable(context.resources, bitmap), 0.5f, 0.5f)
        valenbisiIconCache.put(key, cached)
    }
    return cached
}

internal fun getValenbisiMarkerIcon(context: Context, available: Int, free: Int, isDarkMode: Boolean, showPill: Boolean): MarkerIconResult {
    // Note: [free] does not affect visual output; omit it from key for ~90% higher cache hit rate
    val key = if (showPill) {
        "VALENBISI_${available}_${isDarkMode}_true"
    } else {
        val colorBucket = when {
            available == 0 -> 0
            available < 3 -> 1
            else -> 2
        }
        "VALENBISI_NO_PILL_${colorBucket}_${isDarkMode}"
    }
    var cached = valenbisiIconCache.get(key)
    if (cached == null) {
        cached = createValenbisiMarkerIcon(context, available, free, isDarkMode, showPill)
        valenbisiIconCache.put(key, cached)
    }
    return cached
}

internal fun createValenbisiMarkerIcon(context: Context, available: Int, free: Int, isDarkMode: Boolean, showPill: Boolean): MarkerIconResult {
    val bikeBgColor = if (available == 0) {
        COLOR_VALENBISI_EMPTY // Slate gray if empty
    } else if (available < 3) {
        COLOR_VALENBISI_LOW // Amber if low
    } else {
        COLOR_VALENBISI_OK // Emerald green
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.WHITE else COLOR_VALENBISI_LIGHT_TEXT
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    val logoRadius = 24f
    val spacing = 8f

    val totalWidth: Int
    val totalHeight: Int
    val logoCenterX: Float
    val logoCenterY: Float
    val anchorU: Float
    val anchorV: Float

    if (showPill) {
        val textValue = available.toString()
        val textWidth = textPaint.measureText(textValue)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val pillPaddingH = 14f
        val pillPaddingV = 6f
        val pillWidth = textWidth + (pillPaddingH * 2f)
        val pillHeight = textHeight + (pillPaddingV * 2f)

        logoCenterX = logoRadius + 10f
        logoCenterY = logoRadius + 10f

        totalWidth = (logoCenterX + logoRadius + spacing + pillWidth + 10f).toInt()
        totalHeight = (Math.max(logoRadius * 2f, pillHeight) + 20f).toInt()

        anchorU = logoCenterX / totalWidth.toFloat()
        anchorV = logoCenterY / totalHeight.toFloat()
    } else {
        logoCenterX = logoRadius + 10f
        logoCenterY = logoRadius + 10f
        totalWidth = (logoRadius * 2f + 20f).toInt()
        totalHeight = (logoRadius * 2f + 20f).toInt()
        anchorU = 0.5f
        anchorV = 0.5f
    }

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Draw pill background if requested
    if (showPill) {
        val pillLeft = logoCenterX + logoRadius - 10f
        val pillTop = logoCenterY - (logoRadius * 0.75f)
        val pillRight = totalWidth.toFloat() - 10f
        val pillBottom = logoCenterY + (logoRadius * 0.75f)

        // Drop shadow for pill
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawRoundRect(android.graphics.RectF(pillLeft, pillTop + 2f, pillRight, pillBottom + 2f), 16f, 16f, paint)

        // Pill body
        paint.color = if (isDarkMode) COLOR_VALENBISI_DARK_BG else Color.WHITE
        canvas.drawRoundRect(android.graphics.RectF(pillLeft, pillTop, pillRight, pillBottom), 16f, 16f, paint)

        // Border
        paint.color = bikeBgColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(android.graphics.RectF(pillLeft, pillTop, pillRight, pillBottom), 16f, 16f, paint)

        // Available bikes text
        val textX = pillLeft + (pillRight - pillLeft) / 2f
        val fontMetrics = textPaint.fontMetrics
        val textY = logoCenterY - (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText(available.toString(), textX, textY, textPaint)
    }

    // Draw main circle shadow
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(45, 0, 0, 0)
    canvas.drawCircle(logoCenterX, logoCenterY + 2f, logoRadius, paint)

    // Draw main circle background
    paint.color = bikeBgColor
    canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

    // Draw white border around main circle
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

    // Draw pre-rasterized tinted bike logo inside main circle
    val bikeResId = MarkerLogoBitmapCache.resolveBikeDrawableResId(context)
    if (bikeResId != 0) {
        val dSize = (logoRadius * 1.25f).toInt()
        val tintColor = if (isDarkMode) Color.WHITE else Color.BLACK
        MarkerLogoBitmapCache.drawSquareLogo(
            context = context,
            canvas = canvas,
            resId = bikeResId,
            centerX = logoCenterX,
            centerY = logoCenterY,
            sizePx = dSize,
            tintColor = tintColor
        )
    }

    return MarkerIconResult(BitmapDrawable(context.resources, bitmap), anchorU, anchorV)
}
