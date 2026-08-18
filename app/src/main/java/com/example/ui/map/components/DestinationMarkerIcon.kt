package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

private val destinationIconCache = LruCache<String, Drawable>(16)

internal fun getDestinationMarkerIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "DEST_MARKER_$isDarkMode"
    var cached = destinationIconCache.get(key)
    if (cached == null) {
        cached = createDestinationMarkerDrawable(context)
        destinationIconCache.put(key, cached)
    }
    // Bottom center anchor (pin tip)
    return MarkerIconResult(
        drawable = cached,
        anchorU = 0.5f,
        anchorV = 1.0f
    )
}

private fun createDestinationMarkerDrawable(context: Context): Drawable {
    val width = 72
    val height = 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    // Ground shadow oval
    canvas.drawOval(
        width / 2f - 18f,
        height - 12f,
        width / 2f + 18f,
        height - 2f,
        shadowPaint
    )

    // Pin shape path
    val pinPath = Path().apply {
        val centerX = width / 2f
        val headRadius = 26f
        val headCenterY = 32f

        moveTo(centerX, height - 10f)
        // Left curve from bottom tip to circle
        quadTo(centerX - headRadius * 1.05f, headCenterY + headRadius * 0.9f, centerX - headRadius, headCenterY)
        // Top circle arc
        arcTo(
            centerX - headRadius,
            headCenterY - headRadius,
            centerX + headRadius,
            headCenterY + headRadius,
            180f,
            180f,
            false
        )
        // Right curve back to bottom tip
        quadTo(centerX + headRadius * 1.05f, headCenterY + headRadius * 0.9f, centerX, height - 10f)
        close()
    }

    // Outer white stroke/glow for contrast
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(pinPath, strokePaint)

    // Pin body fill - Vibrant Red/Coral
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444") // Coral Red
        style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, fillPaint)

    // Inner White Circle
    val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 32f, 13f, innerCirclePaint)

    // Inner Red Dot
    val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 32f, 6.5f, innerDotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

enum class CustomPlaceType {
    FAVORITE,
    HOME,
    WORK
}

internal fun getCustomPlaceMarkerIcon(
    context: Context,
    type: CustomPlaceType = CustomPlaceType.FAVORITE,
    currentZoom: Double = 16.0,
    isDarkMode: Boolean = false
): MarkerIconResult {
    val zoomTier = when {
        currentZoom >= 15.5 -> "PIN"
        currentZoom >= 13.0 -> "BADGE"
        else -> "DOT"
    }
    val key = "CUSTOM_PLACE_${type.name}_${zoomTier}_${if (isDarkMode) "dark" else "light"}"
    var cached = destinationIconCache.get(key)
    if (cached == null) {
        cached = when (zoomTier) {
            "PIN" -> createCustomPlacePinDrawable(context, type)
            "BADGE" -> createCustomPlaceBadgeDrawable(context, type)
            else -> createCustomPlaceDotDrawable(context, type)
        }
        destinationIconCache.put(key, cached)
    }

    return if (zoomTier == "PIN") {
        MarkerIconResult(
            drawable = cached,
            anchorU = 0.5f,
            anchorV = 1.0f
        )
    } else {
        MarkerIconResult(
            drawable = cached,
            anchorU = 0.5f,
            anchorV = 0.5f
        )
    }
}

// Backward compatibility helper
internal fun getFavoritePlaceMarkerIcon(context: Context, currentZoom: Double = 16.0): MarkerIconResult {
    return getCustomPlaceMarkerIcon(context, CustomPlaceType.FAVORITE, currentZoom, false)
}

private fun createCustomPlacePinDrawable(context: Context, type: CustomPlaceType): Drawable {
    val width = 68
    val height = 90
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val (mainColor, iconColor) = when (type) {
        CustomPlaceType.FAVORITE -> Pair(Color.parseColor("#F59E0B"), Color.parseColor("#D97706")) // Amber Gold
        CustomPlaceType.HOME -> Pair(Color.parseColor("#10B981"), Color.parseColor("#047857"))     // Emerald Teal
        CustomPlaceType.WORK -> Pair(Color.parseColor("#6366F1"), Color.parseColor("#4338CA"))     // Indigo
    }

    // Base contact shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawOval(width / 2f - 16f, height - 10f, width / 2f + 16f, height - 2f, shadowPaint)

    // Pin Body Path
    val pinPath = Path().apply {
        val centerX = width / 2f
        val headRadius = 24f
        val headCenterY = 30f

        moveTo(centerX, height - 8f)
        quadTo(centerX - headRadius * 1.05f, headCenterY + headRadius * 0.9f, centerX - headRadius, headCenterY)
        arcTo(centerX - headRadius, headCenterY - headRadius, centerX + headRadius, headCenterY + headRadius, 180f, 180f, false)
        quadTo(centerX + headRadius * 1.05f, headCenterY + headRadius * 0.9f, centerX, height - 8f)
        close()
    }

    // Outer White Stroke
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(pinPath, strokePaint)

    // Pin Fill
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mainColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, fillPaint)

    // Center White Disc
    val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 30f, 12.5f, innerCirclePaint)

    // Draw Inner Icon
    drawPlaceVectorIcon(canvas, width / 2f, 30f, type, iconColor, scale = 1.0f)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createCustomPlaceBadgeDrawable(context: Context, type: CustomPlaceType): Drawable {
    val size = 42
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val mainColor = when (type) {
        CustomPlaceType.FAVORITE -> Color.parseColor("#F59E0B")
        CustomPlaceType.HOME -> Color.parseColor("#10B981")
        CustomPlaceType.WORK -> Color.parseColor("#6366F1")
    }

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 1.5f, 16f, shadowPaint)

    // White outer border
    val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 15f, whiteBorderPaint)

    // Main colored core
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mainColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 12f, fillPaint)

    // White inner vector icon
    drawPlaceVectorIcon(canvas, cx, cy, type, Color.WHITE, scale = 0.85f)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createCustomPlaceDotDrawable(context: Context, type: CustomPlaceType): Drawable {
    val size = 22
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val mainColor = when (type) {
        CustomPlaceType.FAVORITE -> Color.parseColor("#F59E0B")
        CustomPlaceType.HOME -> Color.parseColor("#10B981")
        CustomPlaceType.WORK -> Color.parseColor("#6366F1")
    }

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 1f, 8.5f, shadowPaint)

    // White outer border
    val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 7.5f, whiteBorderPaint)

    // Colored center dot
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mainColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 5f, fillPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun drawPlaceVectorIcon(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    type: CustomPlaceType,
    color: Int,
    scale: Float = 1.0f
) {
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    when (type) {
        CustomPlaceType.FAVORITE -> {
            val starPath = Path().apply {
                val numPoints = 5
                val rOuter = 8.0f * scale
                val rInner = 3.5f * scale
                var currentAngle = -Math.PI / 2.0
                val angleStep = Math.PI / numPoints
                for (i in 0 until (numPoints * 2)) {
                    val r = if (i % 2 == 0) rOuter else rInner
                    val x = (cx + Math.cos(currentAngle) * r).toFloat()
                    val y = (cy + Math.sin(currentAngle) * r).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                    currentAngle += angleStep
                }
                close()
            }
            iconPaint.pathEffect = android.graphics.CornerPathEffect(0.6f * scale)
            canvas.drawPath(starPath, iconPaint)
        }
        CustomPlaceType.HOME -> {
            val housePath = Path().apply {
                // Roof triangle
                moveTo(cx, cy - 7.5f * scale)
                lineTo(cx - 7.5f * scale, cy - 0.5f * scale)
                lineTo(cx - 5.5f * scale, cy - 0.5f * scale)
                // Base walls
                lineTo(cx - 5.5f * scale, cy + 6.5f * scale)
                lineTo(cx + 5.5f * scale, cy + 6.5f * scale)
                lineTo(cx + 5.5f * scale, cy - 0.5f * scale)
                lineTo(cx + 7.5f * scale, cy - 0.5f * scale)
                close()
            }
            iconPaint.pathEffect = android.graphics.CornerPathEffect(0.6f * scale)
            canvas.drawPath(housePath, iconPaint)

            // Doorway cutout
            val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = if (color == Color.WHITE) Color.TRANSPARENT else Color.WHITE
                style = Paint.Style.FILL
            }
            if (color != Color.WHITE) {
                canvas.drawRect(cx - 1.8f * scale, cy + 1.5f * scale, cx + 1.8f * scale, cy + 6.5f * scale, doorPaint)
            }
        }
        CustomPlaceType.WORK -> {
            // Briefcase Body
            val bodyRect = RectF(
                cx - 7.0f * scale,
                cy - 2.5f * scale,
                cx + 7.0f * scale,
                cy + 6.0f * scale
            )
            canvas.drawRoundRect(bodyRect, 1.8f * scale, 1.8f * scale, iconPaint)

            // Briefcase Handle
            val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * scale
                strokeCap = Paint.Cap.ROUND
            }
            val handlePath = Path().apply {
                moveTo(cx - 3.2f * scale, cy - 2.5f * scale)
                lineTo(cx - 3.2f * scale, cy - 5.5f * scale)
                lineTo(cx + 3.2f * scale, cy - 5.5f * scale)
                lineTo(cx + 3.2f * scale, cy - 2.5f * scale)
            }
            canvas.drawPath(handlePath, handlePaint)

            // Center clasp / divider
            if (color != Color.WHITE) {
                val claspPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawRect(cx - 1.2f * scale, cy + 0.5f * scale, cx + 1.2f * scale, cy + 3.0f * scale, claspPaint)
            }
        }
    }
}

internal fun getOriginMarkerIcon(context: Context): MarkerIconResult {
    val key = "ORIGIN_MARKER"
    var cached = destinationIconCache.get(key)
    if (cached == null) {
        cached = createOriginMarkerDrawable(context)
        destinationIconCache.put(key, cached)
    }
    return MarkerIconResult(
        drawable = cached,
        anchorU = 0.5f,
        anchorV = 1.0f
    )
}

private fun createOriginMarkerDrawable(context: Context): Drawable {
    val width = 72
    val height = 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawOval(width / 2f - 18f, height - 12f, width / 2f + 18f, height - 2f, shadowPaint)

    val pinPath = Path().apply {
        val centerX = width / 2f
        val headRadius = 26f
        val headCenterY = 32f

        moveTo(centerX, height - 10f)
        quadTo(centerX - headRadius * 1.05f, headCenterY + headRadius * 0.9f, centerX - headRadius, headCenterY)
        arcTo(centerX - headRadius, headCenterY - headRadius, centerX + headRadius, headCenterY + headRadius, 180f, 180f, false)
        quadTo(centerX + headRadius * 1.05f, headCenterY + headRadius * 0.9f, centerX, height - 10f)
        close()
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(pinPath, strokePaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // Emerald Green
        style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, fillPaint)

    val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 32f, 13f, innerCirclePaint)

    val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 32f, 6.5f, innerDotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

internal fun getTransferMarkerIcon(context: Context, colorHex: String, label: String): MarkerIconResult {
    val key = "TRANSFER_${colorHex}_$label"
    var cached = destinationIconCache.get(key)
    if (cached == null) {
        cached = createTransferMarkerDrawable(context, colorHex, label)
        destinationIconCache.put(key, cached)
    }
    return MarkerIconResult(
        drawable = cached,
        anchorU = 0.5f,
        anchorV = 0.5f
    )
}

private fun createTransferMarkerDrawable(context: Context, colorHex: String, label: String): Drawable {
    val cleanHex = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
    val bgColor = try { Color.parseColor(cleanHex) } catch (e: Exception) { Color.parseColor("#0284C7") }

    val density = context.resources.displayMetrics.density
    val cleanLabel = label.trim().take(8)
    
    // Calculate size adaptively
    val isShort = cleanLabel.length <= 2
    val baseHeight = (24f * density).toInt()
    val baseWidth = if (isShort) baseHeight else ((cleanLabel.length * 8f + 16f) * density).toInt()

    val width = baseWidth.coerceAtLeast((24f * density).toInt())
    val height = baseHeight

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Shadow paint
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
    }
    // Draw a subtle drop shadow
    val shadowOffset = 1.5f * density
    canvas.drawRoundRect(
        shadowOffset, 
        shadowOffset, 
        width.toFloat() - shadowOffset, 
        height.toFloat() - shadowOffset + shadowOffset, 
        height / 2f, 
        height / 2f, 
        shadowPaint
    )

    // Outer white border (like Google Maps badge)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(
        0f, 
        0f, 
        width.toFloat() - shadowOffset, 
        height.toFloat() - shadowOffset, 
        (height - shadowOffset) / 2f, 
        (height - shadowOffset) / 2f, 
        borderPaint
    )

    // Inner background pill/circle
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    val inset = 1.5f * density
    canvas.drawRoundRect(
        inset, 
        inset, 
        width.toFloat() - shadowOffset - inset, 
        height.toFloat() - shadowOffset - inset, 
        (height - shadowOffset - 2f * inset) / 2f, 
        (height - shadowOffset - 2f * inset) / 2f, 
        bgPaint
    )

    // Label Text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10.5f * density
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = ((height - shadowOffset) / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(cleanLabel, (width - shadowOffset) / 2f, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

internal fun createStopDotWithTextIcon(context: Context, stopName: String, zoom: Double, isDarkMode: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    
    // Dot configuration
    val dotRadius = 4f * density
    val borderSize = 1.2f * density
    
    // Determine whether to show text based on zoom
    val showText = zoom >= 14.5
    
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        color = if (isDarkMode) Color.WHITE else Color.BLACK
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    val textWidth = if (showText) textPaint.measureText(stopName) else 0f
    val textHeight = if (showText) textPaint.fontMetrics.bottom - textPaint.fontMetrics.top else 0f
    
    // Calculate layout spacing
    // Google Maps places the label offset to the right. Let's add 12dp spacing from the dot center.
    val spacing = 12f * density
    val textOffset = (dotRadius + borderSize) + spacing
    
    val width = if (showText) {
        (textOffset + textWidth + 8f * density).toInt().coerceAtLeast(1)
    } else {
        ((dotRadius + borderSize) * 2f).toInt().coerceAtLeast(1)
    }
    
    val height = if (showText) {
        maxOf((dotRadius + borderSize) * 2f, textHeight + 6f * density).toInt().coerceAtLeast(1)
    } else {
        ((dotRadius + borderSize) * 2f).toInt().coerceAtLeast(1)
    }
    
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Y center alignment
    val centerY = height / 2f
    val centerXOfDot = dotRadius + borderSize
    
    // 1. Draw dot (White background circle)
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    canvas.drawCircle(centerXOfDot, centerY, dotRadius, dotPaint)
    
    // 2. Draw dot border (Slate-800 gray color for contrast)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderSize
        color = if (isDarkMode) Color.parseColor("#475569") else Color.parseColor("#1E293B")
    }
    canvas.drawCircle(centerXOfDot, centerY, dotRadius, borderPaint)
    
    // 3. Draw text (if zoom >= 14.5)
    if (showText) {
        // Draw outline halo to ensure readability over any background/line color
        val haloPaint = Paint(textPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = if (isDarkMode) Color.parseColor("#0F172A") else Color.WHITE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        
        // Vertical baseline centering
        val textBaseline = centerY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
        
        // Render halo outline
        canvas.drawText(stopName, textOffset, textBaseline, haloPaint)
        // Render main text
        canvas.drawText(stopName, textOffset, textBaseline, textPaint)
    }
    
    return bitmap
}

internal fun createTransferLabelIcon(context: Context, labelText: String, zoom: Double, isDarkMode: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    
    // Dot configuration (slightly larger than regular stop dot)
    val dotRadius = 5.5f * density
    val borderSize = 1.5f * density
    val innerDotRadius = 2.5f * density
    
    // Text configuration (larger and bolder than regular stop text)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f * density
        color = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A") // high contrast slate colors
        typeface = android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.BOLD)
    }

    val textWidth = textPaint.measureText(labelText)
    val textHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
    
    val spacing = 14f * density
    val textOffset = (dotRadius + borderSize) + spacing
    
    val width = (textOffset + textWidth + 8f * density).toInt().coerceAtLeast(1)
    val height = maxOf((dotRadius + borderSize) * 2f, textHeight + 8f * density).toInt().coerceAtLeast(1)
    
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val centerY = height / 2f
    val centerXOfDot = dotRadius + borderSize
    
    // 1. Draw outer white dot
    val outerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    canvas.drawCircle(centerXOfDot, centerY, dotRadius, outerDotPaint)
    
    // 2. Draw border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderSize
        color = if (isDarkMode) Color.parseColor("#64748B") else Color.parseColor("#334155")
    }
    canvas.drawCircle(centerXOfDot, centerY, dotRadius, borderPaint)
    
    // 3. Draw inner amber dot (signaling transfer hub)
    val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F59E0B") // Amber transfer hub color
    }
    canvas.drawCircle(centerXOfDot, centerY, innerDotRadius, innerDotPaint)
    
    // 4. Draw text with thick high-contrast halo
    val haloPaint = Paint(textPaint).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = if (isDarkMode) Color.parseColor("#090D16") else Color.WHITE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    
    val textBaseline = centerY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
    
    canvas.drawText(labelText, textOffset, textBaseline, haloPaint)
    canvas.drawText(labelText, textOffset, textBaseline, textPaint)
    
    return bitmap
}


