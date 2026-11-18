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
import com.example.util.LineColorResolver

private val metroStationIconCache = LruCache<String, MarkerIconResult>(256)

private val COLOR_TEXT_LIGHT_BG = Color.parseColor("#0F172A")
private val COLOR_TEXT_DARK_BG = Color.parseColor("#F8FAFC")
private val COLOR_STROKE_DARK = Color.parseColor("#090D16")
private val COLOR_METRO_RED = Color.parseColor("#E2001A")

internal fun getMetroMarkerIcon(
    context: Context,
    stationName: String,
    lines: List<String> = emptyList(),
    isDarkMode: Boolean,
    showPill: Boolean
): MarkerIconResult {
    val cleanLinesKey = lines.joinToString(",")
    val key = "METRO_${stationName}_${cleanLinesKey}_${isDarkMode}_${showPill}"
    var cached = metroStationIconCache.get(key)
    if (cached == null) {
        cached = createMetroMarkerIcon(context, stationName, lines, isDarkMode, showPill)
        metroStationIconCache.put(key, cached)
    }
    return cached
}

internal fun createMetroMarkerIcon(
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
        color = if (isDarkMode) COLOR_TEXT_DARK_BG else COLOR_TEXT_LIGHT_BG
        textSize = stationTextSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        style = Paint.Style.FILL
    }

    val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) COLOR_STROKE_DARK else Color.WHITE
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
        lines.mapNotNull { raw ->
            val clean = raw.trim().uppercase()
            if (clean.isBlank()) null else clean
        }.distinct().sortedWith { a, b ->
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

    val badgeRadius = 16f
    val badgeSpacingX = 6f
    val badgeSpacingY = 6f

    var precomputedChunks: List<List<String>> = emptyList()

    if (showPill) {
        val textWidth = textFillPaint.measureText(stationName)

        logoCenterX = logoRadius + 10f
        logoCenterY = logoRadius + 10f

        val textLeft = logoCenterX + logoRadius + spacing

        val singleRowWidth = if (parsedLines.isNotEmpty()) {
            (parsedLines.size * (badgeRadius * 2f)) + ((parsedLines.size - 1) * badgeSpacingX)
        } else 0f

        precomputedChunks = when {
            parsedLines.isEmpty() -> emptyList()
            parsedLines.size <= 3 || singleRowWidth <= textWidth + 8f -> listOf(parsedLines)
            parsedLines.size == 4 -> listOf(parsedLines.take(2), parsedLines.drop(2))
            parsedLines.size == 5 -> listOf(parsedLines.take(3), parsedLines.drop(3))
            parsedLines.size == 6 -> listOf(parsedLines.take(3), parsedLines.drop(3))
            else -> {
                val numRows = (parsedLines.size + 2) / 3
                val perRow = (parsedLines.size + numRows - 1) / numRows
                parsedLines.chunked(perRow)
            }
        }

        val maxRowWidth = precomputedChunks.maxOfOrNull { row ->
            (row.size * (badgeRadius * 2f)) + ((row.size - 1) * badgeSpacingX)
        } ?: 0f

        val numRows = precomputedChunks.size
        val badgesHeight = if (numRows > 0) {
            (numRows * (badgeRadius * 2f)) + ((numRows - 1) * badgeSpacingY)
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
        if (precomputedChunks.isNotEmpty()) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val badgeFontMetrics = badgeTextPaint.fontMetrics
            val badgesStartY = 54f
            val haloColor = if (isDarkMode) Color.argb(160, 0, 0, 0) else Color.argb(80, 0, 0, 0)

            precomputedChunks.forEachIndexed { rowIndex, rowLines ->
                val rowY = badgesStartY + badgeRadius + (rowIndex * (badgeRadius * 2f + badgeSpacingY))
                rowLines.forEachIndexed { colIndex, lineId ->
                    val cx = textLeft + badgeRadius + (colIndex * (badgeRadius * 2f + badgeSpacingX))
                    val cy = rowY

                    val hexColor = LineColorResolver.getMetroLineColorHex(lineId)
                    val colorInt = MarkerLogoBitmapCache.parseColorCached(hexColor)

                    // Outer subtle dark halo / shadow
                    badgePaint.style = Paint.Style.FILL
                    badgePaint.color = haloColor
                    canvas.drawCircle(cx, cy + 1f, badgeRadius + 1f, badgePaint)

                    // Fill color
                    badgePaint.color = colorInt
                    canvas.drawCircle(cx, cy, badgeRadius, badgePaint)

                    // Subtle white ring border
                    badgePaint.style = Paint.Style.STROKE
                    badgePaint.strokeWidth = 1.2f
                    badgePaint.color = Color.WHITE
                    canvas.drawCircle(cx, cy, badgeRadius - 0.6f, badgePaint)

                    // Text (clean line digits e.g. "3", "5", "10")
                    val digits = lineId.filter { it.isDigit() }
                    val label = if (digits.isNotEmpty()) digits else lineId.removePrefix("L")
                    val bTextY = cy - (badgeFontMetrics.ascent + badgeFontMetrics.descent) / 2f
                    canvas.drawText(label, cx, bTextY, badgeTextPaint)
                }
            }
        }
    }

    // 2. Draw Metrovalencia Logo Image or Fallback Circle (pre-rasterized logo bitmap)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(40, 0, 0, 0)
    canvas.drawOval(
        logoCenterX - logoRadius + 2f,
        logoCenterY - logoRadius + 4f,
        logoCenterX + logoRadius + 2f,
        logoCenterY + logoRadius + 4f,
        paint
    )
    paint.color = Color.WHITE
    canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

    val drewLogo = MarkerLogoBitmapCache.drawAspectFittedLogo(
        context = context,
        canvas = canvas,
        resId = com.example.R.drawable.logo_metrovalencia,
        centerX = logoCenterX,
        centerY = logoCenterY,
        maxSize = logoRadius * 1.6f
    )

    if (!drewLogo) {
        // Fallback to classic custom m text red logo
        paint.color = COLOR_METRO_RED
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 1.25f, paint)

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
        paint.color = COLOR_METRO_RED
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3.5f, paint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cached = MarkerIconResult(drawable, 0.5f, 0.5f)
        metroStationIconCache.put(key, cached)
    }
    return cached
}

internal fun getMetroWhiteDotIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "METRO_WHITE_DOT_SMALL"
    var cached = metroStationIconCache.get(key)
    if (cached == null) {
        val density = context.resources.displayMetrics.density
        // Transparent hit area for tap responsiveness
        val hitSize = (36f * density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(hitSize, hitSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = hitSize / 2f
        val dotRadius = 2.2f * density // Delicate small white dot (~4.5dp)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(center, center, dotRadius, fillPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cached = MarkerIconResult(drawable, 0.5f, 0.5f)
        metroStationIconCache.put(key, cached)
    }
    return cached
}

internal fun getMetroLogoSmallIcon(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val key = "METRO_LOGO_SMALL_${isDarkMode}"
    var cached = metroStationIconCache.get(key)
    if (cached == null) {
        cached = createMetroMarkerIconSmall(context, isDarkMode)
        metroStationIconCache.put(key, cached)
    }
    return cached
}

internal fun createMetroMarkerIconSmall(context: Context, isDarkMode: Boolean): MarkerIconResult {
    val logoRadius = 20f
    val totalWidth = (logoRadius * 2f + 10f).toInt()
    val totalHeight = (logoRadius * 2f + 10f).toInt()

    val logoCenterX = totalWidth / 2f
    val logoCenterY = totalHeight / 2f

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.style = Paint.Style.FILL
    paint.color = Color.argb(40, 0, 0, 0)
    canvas.drawOval(
        logoCenterX - logoRadius + 1f,
        logoCenterY - logoRadius + 2f,
        logoCenterX + logoRadius + 1f,
        logoCenterY + logoRadius + 2f,
        paint
    )
    paint.color = Color.WHITE
    canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

    val drewLogo = MarkerLogoBitmapCache.drawAspectFittedLogo(
        context = context,
        canvas = canvas,
        resId = com.example.R.drawable.logo_metrovalencia,
        centerX = logoCenterX,
        centerY = logoCenterY,
        maxSize = logoRadius * 1.6f
    )

    if (!drewLogo) {
        paint.color = COLOR_METRO_RED
        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val y = logoCenterY - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("m", logoCenterX, y - 1f, textPaint)
    }

    return MarkerIconResult(BitmapDrawable(context.resources, bitmap), 0.5f, 0.5f)
}



