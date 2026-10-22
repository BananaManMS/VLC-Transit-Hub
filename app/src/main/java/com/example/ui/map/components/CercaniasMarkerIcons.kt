package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.example.util.LineColorResolver

private val cercaniasStationIconCache = LruCache<String, MarkerIconResult>(64)

internal fun getCercaniasMarkerIcon(
    context: Context,
    stationName: String,
    lines: List<String> = emptyList(),
    isDarkMode: Boolean,
    showPill: Boolean
): MarkerIconResult {
    val cleanLinesKey = lines.joinToString(",")
    val key = "CERCANIAS_${stationName}_${cleanLinesKey}_${isDarkMode}_${showPill}"
    var cached = cercaniasStationIconCache.get(key)
    if (cached == null) {
        cached = createCercaniasMarkerIcon(context, stationName, lines, isDarkMode, showPill)
        cercaniasStationIconCache.put(key, cached)
    }
    return cached
}

internal fun createCercaniasMarkerIcon(
    context: Context,
    stationName: String,
    lines: List<String> = emptyList(),
    isDarkMode: Boolean,
    showPill: Boolean
): MarkerIconResult {
    val hasLines = showPill && lines.isNotEmpty()
    val stationTextSize = if (hasLines) 40f else 34f
    val stationStrokeWidth = if (hasLines) 7f else 6f

    val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textSize = stationTextSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        style = Paint.Style.FILL
    }

    val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.parseColor("#090D16") else Color.WHITE
        textSize = stationTextSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        style = Paint.Style.STROKE
        strokeWidth = stationStrokeWidth
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    val logoRadius = 32f
    val spacing = 12f

    val parsedLines = if (hasLines) {
        lines.flatMap { raw ->
            raw.split(",", "/", ";", " ").map { it.trim().uppercase() }
        }.filter { it.isNotBlank() }.distinct().sortedWith { a, b ->
            val numA = a.filter { it.isDigit() }.toIntOrNull() ?: 999
            val numB = b.filter { it.isDigit() }.toIntOrNull() ?: 999
            numA.compareTo(numB)
        }
    } else {
        emptyList()
    }

    val totalWidth: Int
    val totalHeight: Int
    val logoCenterX: Float
    val logoCenterY: Float
    val anchorU: Float
    val anchorV: Float

    val badgeH = 26f
    val cornerRad = 6f
    val badgeSpacingX = 6f
    val badgeSpacingY = 6f

    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    fun getBadgeWidth(lineId: String): Float {
        val clean = lineId.replace("C-", "C").replace(" ", "")
        val label = if (clean.startsWith("C") || clean.startsWith("ER")) clean else "C$clean"
        return maxOf(38f, badgeTextPaint.measureText(label) + 14f)
    }

    if (showPill) {
        val textWidth = textFillPaint.measureText(stationName)

        logoCenterX = logoRadius + 10f
        logoCenterY = logoRadius + 10f

        val textLeft = logoCenterX + logoRadius + spacing

        val totalWidthSingleRow = if (parsedLines.isNotEmpty()) {
            parsedLines.sumOf { getBadgeWidth(it).toDouble() }.toFloat() + (parsedLines.size - 1) * badgeSpacingX
        } else 0f

        val lineChunks: List<List<String>> = when {
            parsedLines.isEmpty() -> emptyList()
            parsedLines.size <= 3 || totalWidthSingleRow <= textWidth + 8f -> listOf(parsedLines)
            parsedLines.size == 4 -> listOf(parsedLines.take(2), parsedLines.drop(2))
            parsedLines.size == 5 -> listOf(parsedLines.take(3), parsedLines.drop(3))
            parsedLines.size == 6 -> listOf(parsedLines.take(3), parsedLines.drop(3))
            else -> {
                val numRows = (parsedLines.size + 2) / 3
                val perRow = (parsedLines.size + numRows - 1) / numRows
                parsedLines.chunked(perRow)
            }
        }

        val maxRowWidth = lineChunks.maxOfOrNull { row ->
            row.sumOf { getBadgeWidth(it).toDouble() }.toFloat() + ((row.size - 1) * badgeSpacingX)
        } ?: 0f

        val numRows = lineChunks.size
        val badgesHeight = if (numRows > 0) {
            (numRows * badgeH) + ((numRows - 1) * badgeSpacingY)
        } else 0f

        val contentWidth = maxOf(textWidth, maxRowWidth)
        totalWidth = (textLeft + contentWidth + 18f).toInt()

        val badgesStartY = 54f
        val calculatedHeight = if (numRows > 0) {
            badgesStartY + badgesHeight + 12f
        } else {
            logoCenterY + logoRadius + 10f
        }
        totalHeight = maxOf((logoCenterY + logoRadius + 10f), calculatedHeight).toInt()

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
        val fontMetrics = textFillPaint.fontMetrics
        val textLeft = logoCenterX + logoRadius + spacing
        
        val nameCenterY = if (parsedLines.isNotEmpty()) {
            26f
        } else {
            logoCenterY
        }
        val textY = nameCenterY - (fontMetrics.ascent + fontMetrics.descent) / 2f

        // Draw light/dark stroke outline (borde protector)
        canvas.drawText(stationName, textLeft, textY, textStrokePaint)
        // Draw bold fill text
        canvas.drawText(stationName, textLeft, textY, textFillPaint)

        // Draw line badges if present
        if (parsedLines.isNotEmpty()) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val badgeFontMetrics = badgeTextPaint.fontMetrics
            val badgesStartY = 54f

            val totalWidthSingleRow = parsedLines.sumOf { getBadgeWidth(it).toDouble() }.toFloat() + (parsedLines.size - 1) * badgeSpacingX
            val lineChunks: List<List<String>> = when {
                parsedLines.size <= 3 || totalWidthSingleRow <= textFillPaint.measureText(stationName) + 8f -> listOf(parsedLines)
                parsedLines.size == 4 -> listOf(parsedLines.take(2), parsedLines.drop(2))
                parsedLines.size == 5 -> listOf(parsedLines.take(3), parsedLines.drop(3))
                parsedLines.size == 6 -> listOf(parsedLines.take(3), parsedLines.drop(3))
                else -> {
                    val numRows = (parsedLines.size + 2) / 3
                    val perRow = (parsedLines.size + numRows - 1) / numRows
                    parsedLines.chunked(perRow)
                }
            }

            lineChunks.forEachIndexed { rowIndex, rowLines ->
                var currentX = textLeft
                val rowY = badgesStartY + (rowIndex * (badgeH + badgeSpacingY))

                rowLines.forEach { lineId ->
                    val clean = lineId.replace("C-", "C").replace(" ", "")
                    val label = if (clean.startsWith("C") || clean.startsWith("ER")) clean else "C$clean"
                    val bW = maxOf(38f, badgeTextPaint.measureText(label) + 14f)

                    val hexColor = LineColorResolver.getCercaniasLineColorHex(lineId)
                    val colorInt = Color.parseColor(hexColor)
                    val isYellowLine = lineId.contains("C2", ignoreCase = true) || hexColor.equals("#FAB700", ignoreCase = true)

                    val rect = RectF(currentX, rowY, currentX + bW, rowY + badgeH)

                    // Outer subtle dark halo / shadow
                    badgePaint.style = Paint.Style.FILL
                    badgePaint.color = if (isDarkMode) Color.argb(160, 0, 0, 0) else Color.argb(80, 0, 0, 0)
                    canvas.drawRoundRect(RectF(rect.left - 1f, rect.top, rect.right + 1f, rect.bottom + 2f), cornerRad + 1f, cornerRad + 1f, badgePaint)

                    // Fill color
                    badgePaint.color = colorInt
                    canvas.drawRoundRect(rect, cornerRad, cornerRad, badgePaint)

                    // Subtle white/dark ring border
                    badgePaint.style = Paint.Style.STROKE
                    badgePaint.strokeWidth = 1.2f
                    badgePaint.color = if (isYellowLine) Color.argb(140, 0, 0, 0) else Color.WHITE
                    canvas.drawRoundRect(rect, cornerRad, cornerRad, badgePaint)

                    // Text
                    badgeTextPaint.color = if (isYellowLine) Color.parseColor("#0F172A") else Color.WHITE
                    val bTextY = rect.centerY() - (badgeFontMetrics.ascent + badgeFontMetrics.descent) / 2f
                    canvas.drawText(label, rect.centerX(), bTextY, badgeTextPaint)

                    currentX += bW + badgeSpacingX
                }
            }
        }
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

internal fun getCercaniasTinyDotIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "CERCANIAS_TINY_DOT_SOLID_WHITE"
    var cached = cercaniasStationIconCache.get(key)
    if (cached == null) {
        val size = 14
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // Solid white station dot without outer circle or borders
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1f, paint)

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

