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

private val busDetailedIconCache = LruCache<String, MarkerIconResult>(512)
private val mergedDetailedIconCache = LruCache<String, MarkerIconResult>(512)
private val mergedCompactIconCache = LruCache<String, BitmapDrawable>(64)

internal fun getMergedCompactMarkerIcon(
    context: Context,
    isDarkMode: Boolean
): BitmapDrawable {
    val key = "MERGED_COMPACT_$isDarkMode"
    var cached = mergedCompactIconCache.get(key)
    if (cached == null) {
        cached = createMergedCompactMarkerIcon(context, isDarkMode)
        mergedCompactIconCache.put(key, cached)
    }
    return cached
}

internal fun getMergedDotMarkerIcon(context: Context): BitmapDrawable {
    val key = "MERGED_DOT"
    var cached = mergedCompactIconCache.get(key)
    if (cached == null) {
        cached = createMergedDotMarkerIcon(context)
        mergedCompactIconCache.put(key, cached)
    }
    return cached
}

private fun createMergedDotMarkerIcon(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (14 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val radius = sizePx / 2f
    val rectF = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())

    paint.color = Color.parseColor("#E53935")
    paint.style = Paint.Style.FILL
    canvas.drawArc(rectF, 90f, 180f, true, paint)

    paint.color = Color.parseColor("#FF9800")
    paint.style = Paint.Style.FILL
    canvas.drawArc(rectF, 270f, 180f, true, paint)

    paint.style = Paint.Style.STROKE
    paint.color = Color.WHITE
    paint.strokeWidth = 1.5f * density
    canvas.drawCircle(radius, radius, radius - (0.75f * density), paint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createMergedCompactMarkerIcon(
    context: Context,
    isDarkMode: Boolean
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (54 * density).toInt()
    val height = (28 * density).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer shadow
    paint.color = Color.argb(40, 0, 0, 0)
    val shadowRect = RectF(1.5f * density, 2f * density, width - 1.5f * density, height - 1f * density)
    canvas.drawRoundRect(shadowRect, 6f * density, 6f * density, paint)

    // Background pill
    paint.color = if (isDarkMode) Color.parseColor("#1E293B") else Color.WHITE
    paint.style = Paint.Style.FILL
    val bgRect = RectF(1.5f * density, 1.5f * density, width - 1.5f * density, height - 2f * density)
    canvas.drawRoundRect(bgRect, 6f * density, 6f * density, paint)

    // Border
    paint.color = if (isDarkMode) Color.parseColor("#475569") else Color.parseColor("#CBD5E1")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.2f * density
    canvas.drawRoundRect(bgRect, 6f * density, 6f * density, paint)

    // EMT Icon on Left, Metrobús Icon on Right (Height 28dp matching single compact icon)
    val iconSize = 20f * density
    val centerY = bgRect.centerY()
    val emtCenterX = bgRect.left + (iconSize / 2f) + (3f * density)
    val mbCenterX = bgRect.right - (iconSize / 2f) - (3f * density)

    drawBusIconAt(context, canvas, emtCenterX, centerY, iconSize, "BUS")
    drawBusIconAt(context, canvas, mbCenterX, centerY, iconSize, "MB")

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Generates high-detail bus stop markers for Zoom >= 18.0.
 * Layout:
 *   [ Bus Icon (50x50) ]
 *   [ Stop Name (Transparent background with high-contrast text halo) ]
 *   [ Line Badges (Red pill badges, max 10, plus "..." if more) ]
 */
internal fun getBusDetailedMarkerIcon(
    context: Context,
    stopName: String,
    linesString: String?,
    isDarkMode: Boolean,
    busType: String = "BUS",
    labelPosition: String = "BELOW"
): MarkerIconResult {
    val cleanLines = linesString?.trim().orEmpty()
    val key = "BUS_DETAILED_${stopName}_${cleanLines}_${isDarkMode}_${busType}_${labelPosition}"
    var cached = busDetailedIconCache.get(key)
    if (cached == null) {
        cached = createBusDetailedMarkerIcon(context, stopName, cleanLines, isDarkMode, busType, labelPosition)
        busDetailedIconCache.put(key, cached)
    }
    return cached
}

private fun createBusDetailedMarkerIcon(
    context: Context,
    stopName: String,
    linesString: String,
    isDarkMode: Boolean,
    busType: String,
    labelPosition: String
): MarkerIconResult {
    val stationTextSize = 34f
    val stationStrokeWidth = 6f

    // 1. Text Paints matching Metro and Cercanías formatting
    val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textSize = stationTextSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.parseColor("#090D16") else Color.WHITE
        textSize = stationTextSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = stationStrokeWidth
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 25f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // 2. Parse lines with smart natural ordering
    val rawParsedLines = if (linesString.isNotBlank()) {
        linesString.split(",", "/", ";", " ").map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith { a, b ->
                val numA = a.filter { it.isDigit() }.toIntOrNull()
                val numB = b.filter { it.isDigit() }.toIntOrNull()
                when {
                    numA != null && numB != null -> numA.compareTo(numB)
                    numA != null -> -1
                    numB != null -> 1
                    else -> a.compareTo(b)
                }
            }
    } else {
        emptyList()
    }

    // Max 10 lines + ellipsis badge if more
    val hasOverflow = rawParsedLines.size > 10
    val displayLines: List<String> = if (hasOverflow) {
        rawParsedLines.take(10) + "..."
    } else {
        rawParsedLines
    }

    val badgeHeight = 34f
    val standardBadgeWidth = 46f
    val badgeRadius = 8f
    val badgeSpacingX = 8f
    val badgeSpacingY = 6f

    fun getBadgeWidth(label: String): Float {
        val measured = badgeTextPaint.measureText(label)
        return maxOf(standardBadgeWidth, measured + 14f)
    }

    // 3. Line badge row distribution (max 5-6 per row)
    val lineChunks: List<List<String>> = when {
        displayLines.isEmpty() -> emptyList()
        displayLines.size <= 5 -> listOf(displayLines)
        displayLines.size == 6 -> listOf(displayLines.take(3), displayLines.drop(3))
        displayLines.size in 7..8 -> listOf(displayLines.take(4), displayLines.drop(4))
        else -> {
            val perRow = (displayLines.size + 1) / 2
            displayLines.chunked(perRow)
        }
    }

    val maxRowWidth = lineChunks.maxOfOrNull { row ->
        row.sumOf { getBadgeWidth(it).toDouble() }.toFloat() + ((row.size - 1) * badgeSpacingX)
    } ?: 0f

    val nameLines = formatStopNameLines(stopName, textFillPaint, maxWidth = 280f)
    val fontMetrics = textFillPaint.fontMetrics
    val singleLineHeight = fontMetrics.descent - fontMetrics.ascent
    val lineSpacing = 4f
    val totalNameHeight = (nameLines.size * singleLineHeight) + ((nameLines.size - 1) * lineSpacing)

    val maxNameLineWidth = nameLines.maxOf { textFillPaint.measureText(it) }

    val iconSize = 54f
    val contentWidth = maxOf(iconSize, maxOf(maxNameLineWidth, maxRowWidth))
    val numRows = lineChunks.size
    val badgesHeight = if (numRows > 0) {
        (numRows * badgeHeight) + ((numRows - 1) * badgeSpacingY)
    } else 0f

    val iconTop: Float
    val iconCenterX: Float
    val iconCenterY: Float
    val firstLineY: Float
    val badgesStartY: Float
    val labelCenterX: Float
    val totalWidth: Int
    val totalHeight: Int
    val anchorU: Float
    val anchorV: Float

    totalWidth = (contentWidth + 32f).toInt()
    labelCenterX = totalWidth / 2f
    iconCenterX = totalWidth / 2f

    iconTop = 4f
    iconCenterY = iconTop + iconSize / 2f
    val iconBottom = iconTop + iconSize
    firstLineY = iconBottom + 8f - fontMetrics.ascent
    val nameBottom = firstLineY + totalNameHeight - singleLineHeight + fontMetrics.descent
    badgesStartY = nameBottom + 8f
    totalHeight = if (numRows > 0) {
        (badgesStartY + badgesHeight + 12f).toInt()
    } else {
        (nameBottom + 12f).toInt()
    }

    anchorU = 0.5f
    anchorV = iconCenterY / totalHeight.toFloat()

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 4. Draw Bus Icon
    drawBusIconAt(context, canvas, iconCenterX, iconCenterY, iconSize, busType)

    // 5. Draw Stop Name with outer contrast halo
    var currentNameY = firstLineY
    nameLines.forEach { line ->
        canvas.drawText(line, labelCenterX, currentNameY, textStrokePaint)
        canvas.drawText(line, labelCenterX, currentNameY, textFillPaint)
        currentNameY += singleLineHeight + lineSpacing
    }

    // 6. Draw Line Badges
    if (lineChunks.isNotEmpty()) {
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (busType == "MB") Color.parseColor("#F59E0B") else Color.parseColor("#E53935")
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = if (busType == "MB") Color.parseColor("#D97706") else Color.parseColor("#B91C1C")
        }

        val badgeFontMetrics = badgeTextPaint.fontMetrics
        var rowTop = badgesStartY

        lineChunks.forEach { row ->
            val rowWidth = row.sumOf { getBadgeWidth(it).toDouble() }.toFloat() + ((row.size - 1) * badgeSpacingX)
            var currentX = labelCenterX - (rowWidth / 2f)

            row.forEach { lineLabel ->
                val bWidth = getBadgeWidth(lineLabel)
                val badgeRect = RectF(currentX, rowTop, currentX + bWidth, rowTop + badgeHeight)

                // Fill badge
                canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBgPaint)
                canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBorderPaint)

                // Text centered
                val badgeTextCenterY = rowTop + (badgeHeight / 2f) - ((badgeFontMetrics.ascent + badgeFontMetrics.descent) / 2f)
                canvas.drawText(lineLabel, currentX + (bWidth / 2f), badgeTextCenterY, badgeTextPaint)

                currentX += bWidth + badgeSpacingX
            }

            rowTop += badgeHeight + badgeSpacingY
        }
    }

    val drawable = BitmapDrawable(context.resources, bitmap)
    return MarkerIconResult(drawable, anchorU, anchorV)
}

private fun drawBusIconAt(
    context: Context,
    canvas: Canvas,
    centerX: Float,
    centerY: Float,
    size: Float,
    busType: String
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val halfSize = size / 2f
    val left = centerX - halfSize
    val top = centerY - halfSize
    val right = centerX + halfSize
    val bottom = centerY + halfSize

    // Soft drop shadow
    paint.color = Color.argb(45, 0, 0, 0)
    paint.style = Paint.Style.FILL
    val shadowRect = RectF(left + 1.5f, top + 3f, right + 1.5f, bottom + 3f)
    canvas.drawRoundRect(shadowRect, 10f, 10f, paint)

    if (busType == "MB") {
        // Metrobús background (White square with orange border)
        paint.color = Color.WHITE
        val squareRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

        // Amber border
        paint.color = Color.parseColor("#D97706")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

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
                val w = if (ratio > 1f) targetMax else targetMax * ratio
                val h = if (ratio > 1f) targetMax / ratio else targetMax
                val l = centerX - w / 2f
                val t = centerY - h / 2f
                logoDrawable.setBounds(l.toInt(), t.toInt(), (l + w).toInt(), (t + h).toInt())
            } else {
                logoDrawable.setBounds((left + 6).toInt(), (top + 6).toInt(), (right - 6).toInt(), (bottom - 6).toInt())
            }
            logoDrawable.draw(canvas)
        }
    } else {
        // EMT Bus background (White square with blue border and EMT logo)
        paint.color = Color.WHITE
        val squareRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

        paint.color = Color.parseColor("#2563EB")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(squareRect, 10f, 10f, paint)

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
                val targetMax = size - 14f
                val w = if (ratio > 1f) targetMax else targetMax * ratio
                val h = if (ratio > 1f) targetMax / ratio else targetMax
                val l = centerX - w / 2f
                val t = centerY - h / 2f
                logoDrawable.setBounds(l.toInt(), t.toInt(), (l + w).toInt(), (t + h).toInt())
            } else {
                logoDrawable.setBounds((left + 8).toInt(), (top + 8).toInt(), (right - 8).toInt(), (bottom - 8).toInt())
            }
            logoDrawable.draw(canvas)
        } else {
            // Fallback red bus silhouette
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#E53935")
            val busLeft = centerX - 12f
            val busTop = centerY - 12f
            val busRight = centerX + 12f
            val busBottom = centerY + 10f
            canvas.drawRoundRect(RectF(busLeft, busTop, busRight, busBottom), 4f, 4f, paint)

            paint.color = Color.WHITE
            canvas.drawRect(busLeft + 2.5f, busTop + 2.5f, busRight - 2.5f, busTop + 8f, paint)

            val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBBF24") }
            canvas.drawCircle(busLeft + 4f, busBottom - 3.5f, 1.8f, lightPaint)
            canvas.drawCircle(busRight - 4f, busBottom - 3.5f, 1.8f, lightPaint)
        }
    }
}

private fun formatStopNameLines(stopName: String, paint: Paint, maxWidth: Float = 280f): List<String> {
    val trimmed = stopName.trim()
    if (paint.measureText(trimmed) <= maxWidth) {
        return listOf(trimmed)
    }

    // Check for " - " or " / "
    val dashIdx = trimmed.indexOf(" - ")
    if (dashIdx in 3..(trimmed.length - 4)) {
        val line1 = trimmed.substring(0, dashIdx).trim()
        val line2 = trimmed.substring(dashIdx + 3).trim()
        if (line1.isNotBlank() && line2.isNotBlank()) {
            return listOf(line1, line2)
        }
    }

    val slashIdx = trimmed.indexOf(" / ")
    if (slashIdx in 3..(trimmed.length - 4)) {
        val line1 = trimmed.substring(0, slashIdx).trim()
        val line2 = trimmed.substring(slashIdx + 3).trim()
        if (line1.isNotBlank() && line2.isNotBlank()) {
            return listOf(line1, line2)
        }
    }

    // Find space closest to string midpoint
    val spaces = mutableListOf<Int>()
    for (i in trimmed.indices) {
        if (trimmed[i] == ' ') spaces.add(i)
    }

    if (spaces.isNotEmpty()) {
        val mid = trimmed.length / 2
        val bestSpace = spaces.minByOrNull { Math.abs(it - mid) } ?: spaces[0]
        val line1 = trimmed.substring(0, bestSpace).trim()
        val line2 = trimmed.substring(bestSpace + 1).trim()
        if (line1.isNotBlank() && line2.isNotBlank()) {
            return listOf(line1, line2)
        }
    }

    return listOf(trimmed)
}

/**
 * Generates merged EMT + Metrobús detailed landscape marker icons.
 * Layout:
 *   [ EMT Icon ]  [ Metrobús Icon ]
 *   [ Stop Name (halo) ]
 *   [ EMT Red Badges ... ] [ Metrobús Amber Badges ... ]
 */
internal fun getMergedDetailedMarkerIcon(
    context: Context,
    stopName: String,
    emtLinesString: String?,
    metrobusLinesString: String?,
    isDarkMode: Boolean,
    labelPosition: String = "BELOW"
): MarkerIconResult {
    val cleanEmt = emtLinesString?.trim().orEmpty()
    val cleanMb = metrobusLinesString?.trim().orEmpty()
    val key = "MERGED_DETAILED_${stopName}_${cleanEmt}_${cleanMb}_${isDarkMode}_${labelPosition}"
    var cached = mergedDetailedIconCache.get(key)
    if (cached == null) {
        cached = createMergedDetailedMarkerIcon(context, stopName, cleanEmt, cleanMb, isDarkMode, labelPosition)
        mergedDetailedIconCache.put(key, cached)
    }
    return cached
}

private fun createMergedDetailedMarkerIcon(
    context: Context,
    stopName: String,
    emtLinesString: String,
    metrobusLinesString: String,
    isDarkMode: Boolean,
    labelPosition: String
): MarkerIconResult {
    val stationTextSize = 34f
    val stationStrokeWidth = 6f

    // 1. Text Paints
    val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textSize = stationTextSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode) Color.parseColor("#090D16") else Color.WHITE
        textSize = stationTextSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = stationStrokeWidth
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 25f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // 2. Parse EMT lines & Metrobús lines
    data class MergedBadge(val label: String, val isMetrobus: Boolean)

    fun parseLineString(s: String, isMb: Boolean): List<MergedBadge> {
        if (s.isBlank()) return emptyList()
        return s.split(",", "/", ";", " ").map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith { a, b ->
                val numA = a.filter { it.isDigit() }.toIntOrNull()
                val numB = b.filter { it.isDigit() }.toIntOrNull()
                when {
                    numA != null && numB != null -> numA.compareTo(numB)
                    numA != null -> -1
                    numB != null -> 1
                    else -> a.compareTo(b)
                }
            }
            .map { MergedBadge(it, isMb) }
    }

    val emtBadges = parseLineString(emtLinesString, false)
    val mbBadges = parseLineString(metrobusLinesString, true)

    val allBadges = mutableListOf<MergedBadge>()
    allBadges.addAll(emtBadges)
    allBadges.addAll(mbBadges)

    val maxBadges = 12
    val displayBadges: List<MergedBadge> = if (allBadges.size > maxBadges) {
        allBadges.take(maxBadges) + MergedBadge("...", false)
    } else {
        allBadges
    }

    val badgeHeight = 34f
    val standardBadgeWidth = 46f
    val badgeRadius = 8f
    val badgeSpacingX = 8f
    val badgeSpacingY = 6f

    fun getBadgeWidth(badge: MergedBadge): Float {
        val measured = badgeTextPaint.measureText(badge.label)
        return maxOf(standardBadgeWidth, measured + 14f)
    }

    // Line badge rows distribution (max 5-6 per row)
    val lineChunks: List<List<MergedBadge>> = when {
        displayBadges.isEmpty() -> emptyList()
        displayBadges.size <= 5 -> listOf(displayBadges)
        displayBadges.size == 6 -> listOf(displayBadges.take(3), displayBadges.drop(3))
        displayBadges.size in 7..8 -> listOf(displayBadges.take(4), displayBadges.drop(4))
        else -> {
            val perRow = (displayBadges.size + 1) / 2
            displayBadges.chunked(perRow)
        }
    }

    val maxRowWidth = lineChunks.maxOfOrNull { row ->
        row.sumOf { getBadgeWidth(it).toDouble() }.toFloat() + ((row.size - 1) * badgeSpacingX)
    } ?: 0f

    val nameLines = formatStopNameLines(stopName, textFillPaint, maxWidth = 320f)
    val fontMetrics = textFillPaint.fontMetrics
    val singleLineHeight = fontMetrics.descent - fontMetrics.ascent
    val lineSpacing = 4f
    val totalNameHeight = (nameLines.size * singleLineHeight) + ((nameLines.size - 1) * lineSpacing)
    val maxNameLineWidth = nameLines.maxOf { textFillPaint.measureText(it) }

    // Dual Icon Header: EMT Icon (48f) + Space (4f) + Metrobús Icon (48f) = Total width 100f, Height 48f
    val singleIconSize = 48f
    val iconGap = 4f
    val dualHeaderWidth = (singleIconSize * 2) + iconGap
    val dualHeaderHeight = singleIconSize

    val contentWidth = maxOf(dualHeaderWidth, maxOf(maxNameLineWidth, maxRowWidth))
    val numRows = lineChunks.size
    val badgesHeight = if (numRows > 0) {
        (numRows * badgeHeight) + ((numRows - 1) * badgeSpacingY)
    } else 0f

    val totalWidth: Int
    val totalHeight: Int
    val labelCenterX: Float
    val headerCenterX: Float
    val iconTop: Float
    val iconCenterY: Float
    val firstLineY: Float
    val badgesStartY: Float
    val anchorU: Float
    val anchorV: Float

    totalWidth = (contentWidth + 32f).toInt()
    labelCenterX = totalWidth / 2f
    headerCenterX = totalWidth / 2f

    iconTop = 6f
    iconCenterY = iconTop + dualHeaderHeight / 2f
    val iconBottom = iconTop + dualHeaderHeight
    firstLineY = iconBottom + 8f - fontMetrics.ascent
    val nameBottom = firstLineY + totalNameHeight - singleLineHeight + fontMetrics.descent
    badgesStartY = nameBottom + 8f
    totalHeight = if (numRows > 0) {
        (badgesStartY + badgesHeight + 12f).toInt()
    } else {
        (nameBottom + 12f).toInt()
    }

    anchorU = 0.5f
    anchorV = iconCenterY / totalHeight.toFloat()

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw Dual Header Icons (Left: EMT, Right: Metrobús)
    val emtIconCenterX = headerCenterX - (iconGap / 2f) - (singleIconSize / 2f)
    val mbIconCenterX = headerCenterX + (iconGap / 2f) + (singleIconSize / 2f)

    drawBusIconAt(context, canvas, emtIconCenterX, iconCenterY, singleIconSize, "BUS")
    drawBusIconAt(context, canvas, mbIconCenterX, iconCenterY, singleIconSize, "MB")

    // Draw Stop Name with halo
    var currentNameY = firstLineY
    nameLines.forEach { line ->
        canvas.drawText(line, labelCenterX, currentNameY, textStrokePaint)
        canvas.drawText(line, labelCenterX, currentNameY, textFillPaint)
        currentNameY += singleLineHeight + lineSpacing
    }

    // Draw Line Badges (EMT Red, Metrobús Amber)
    if (lineChunks.isNotEmpty()) {
        val emtBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E53935")
        }
        val emtBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.parseColor("#B91C1C")
        }

        val mbBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F59E0B")
        }
        val mbBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.parseColor("#D97706")
        }

        val badgeFontMetrics = badgeTextPaint.fontMetrics
        var rowTop = badgesStartY

        lineChunks.forEach { row ->
            val rowWidth = row.sumOf { getBadgeWidth(it).toDouble() }.toFloat() + ((row.size - 1) * badgeSpacingX)
            var currentX = labelCenterX - (rowWidth / 2f)

            row.forEach { badge ->
                val bWidth = getBadgeWidth(badge)
                val badgeRect = RectF(currentX, rowTop, currentX + bWidth, rowTop + badgeHeight)

                val bgPaint = if (badge.isMetrobus) mbBadgeBgPaint else emtBadgeBgPaint
                val borderPaint = if (badge.isMetrobus) mbBadgeBorderPaint else emtBadgeBorderPaint

                canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, bgPaint)
                canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, borderPaint)

                val badgeTextCenterY = rowTop + (badgeHeight / 2f) - ((badgeFontMetrics.ascent + badgeFontMetrics.descent) / 2f)
                canvas.drawText(badge.label, currentX + (bWidth / 2f), badgeTextCenterY, badgeTextPaint)

                currentX += bWidth + badgeSpacingX
            }

            rowTop += badgeHeight + badgeSpacingY
        }
    }

    val drawable = BitmapDrawable(context.resources, bitmap)
    return MarkerIconResult(drawable, anchorU, anchorV)
}

