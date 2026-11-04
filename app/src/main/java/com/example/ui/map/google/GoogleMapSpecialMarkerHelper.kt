package com.example.ui.map.google

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.collection.LruCache
import androidx.compose.ui.geometry.Offset
import com.example.ui.map.components.CustomPlaceType
import com.example.ui.map.components.createStopDotWithTextIcon
import com.example.ui.map.components.createTransferLabelIcon
import com.example.ui.map.components.getCustomPlaceMarkerIcon
import com.example.ui.map.components.getDestinationMarkerIcon
import com.example.ui.map.components.getOriginMarkerIcon
import com.example.ui.map.components.getTransferMarkerIcon
import com.example.ui.map.components.getValenbisiCompactDotIcon
import com.example.ui.map.components.getValenbisiMarkerIcon
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Dedicated cache and icon generator for user location, live trains, itinerary markers,
 * custom places, Valenbisi, station accesses, and route chevrons.
 */
internal object GoogleMapSpecialMarkerHelper {
    private val specialCache = LruCache<String, CachedMarkerIcon>(128)

    fun drawableToCachedMarkerIcon(
        drawable: Drawable,
        anchorU: Float,
        anchorV: Float,
        fallbackHue: Float = BitmapDescriptorFactory.HUE_RED
    ): CachedMarkerIcon {
        return try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bmp
            }
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                anchor = Offset(anchorU, anchorV)
            )
        } catch (e: Throwable) {
            Log.e("GoogleMapSpecialMarkerHelper", "Error converting drawable to BitmapDescriptor", e)
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(fallbackHue),
                anchor = Offset(anchorU, anchorV)
            )
        }
    }

    fun getOrCreateAccessIcon(
        context: Context,
        typeCas: String
    ): CachedMarkerIcon {
        val cleanKey = "ACCESS_$typeCas"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val drawable = com.example.ui.map.components.MetroAccessPointRenderer.getAccessIcon(context, typeCas)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(0.5f, 0.5f)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    anchor = Offset(0.5f, 0.5f)
                )
            }
        } catch (_: Throwable) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                anchor = Offset(0.5f, 0.5f)
            )
        }

        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateOriginMarkerIcon(context: Context): CachedMarkerIcon {
        val cleanKey = "ORIGIN_PIN"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val iconResult = getOriginMarkerIcon(context)
        val icon = drawableToCachedMarkerIcon(iconResult.drawable, iconResult.anchorU, iconResult.anchorV, BitmapDescriptorFactory.HUE_GREEN)
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateDestinationMarkerIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon {
        val cleanKey = "DEST_PIN_$isDarkMode"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val iconResult = getDestinationMarkerIcon(context, isDarkMode)
        val icon = drawableToCachedMarkerIcon(iconResult.drawable, iconResult.anchorU, iconResult.anchorV, BitmapDescriptorFactory.HUE_RED)
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateTransferMarkerIcon(context: Context, colorHex: String, label: String): CachedMarkerIcon {
        val cleanKey = "TRANSFER_PIN_${colorHex}_$label"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val iconResult = getTransferMarkerIcon(context, colorHex, label)
        val icon = drawableToCachedMarkerIcon(iconResult.drawable, iconResult.anchorU, iconResult.anchorV, BitmapDescriptorFactory.HUE_AZURE)
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateStopDotWithTextIcon(context: Context, stopName: String, zoom: Double, isDarkMode: Boolean): CachedMarkerIcon {
        val showText = zoom >= 14.5
        val cleanKey = "STOP_DOT_${stopName}_${showText}_$isDarkMode"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val bitmap = createStopDotWithTextIcon(context, stopName, zoom, isDarkMode)
        val density = context.resources.displayMetrics.density
        val dotRadius = 4f * density
        val borderSize = 1.2f * density
        val anchorU = (dotRadius + borderSize) / bitmap.width.toFloat()
        val anchorV = 0.5f

        val icon = CachedMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchor = Offset(anchorU, anchorV)
        )
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateTransferLabelIcon(context: Context, labelText: String, zoom: Double, isDarkMode: Boolean): CachedMarkerIcon {
        val cleanKey = "TRANSFER_LABEL_${labelText}_$isDarkMode"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val bitmap = createTransferLabelIcon(context, labelText, zoom, isDarkMode)
        val density = context.resources.displayMetrics.density
        val dotRadius = 5.5f * density
        val borderSize = 1.5f * density
        val anchorU = (dotRadius + borderSize) / bitmap.width.toFloat()
        val anchorV = 0.5f

        val icon = CachedMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchor = Offset(anchorU, anchorV)
        )
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateCustomPlaceMarkerIcon(
        context: Context,
        type: CustomPlaceType,
        currentZoom: Double = 16.0,
        isDarkMode: Boolean = false,
        customColorHex: String? = null
    ): CachedMarkerIcon {
        val cleanKey = "CUSTOM_PLACE_${type.name}_${customColorHex.orEmpty()}_${(currentZoom * 2).toInt()}_$isDarkMode"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val iconResult = getCustomPlaceMarkerIcon(context, type, currentZoom, isDarkMode, customColorHex)
        val icon = drawableToCachedMarkerIcon(iconResult.drawable, iconResult.anchorU, iconResult.anchorV, BitmapDescriptorFactory.HUE_YELLOW)
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateValenbisiMarkerIcon(
        context: Context,
        available: Int,
        free: Int,
        isDarkMode: Boolean,
        showPill: Boolean
    ): CachedMarkerIcon {
        val cleanKey = "VALENBISI_BADGE_${available}_${free}_${isDarkMode}_$showPill"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val iconResult = getValenbisiMarkerIcon(context, available, free, isDarkMode, showPill)
        val icon = drawableToCachedMarkerIcon(iconResult.drawable, iconResult.anchorU, iconResult.anchorV, BitmapDescriptorFactory.HUE_GREEN)
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateValenbisiCompactDotIcon(
        context: Context,
        available: Int
    ): CachedMarkerIcon {
        val cleanKey = "VALENBISI_DOT_$available"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val iconResult = getValenbisiCompactDotIcon(context, available)
        val icon = drawableToCachedMarkerIcon(iconResult.drawable, iconResult.anchorU, iconResult.anchorV, BitmapDescriptorFactory.HUE_GREEN)
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateLiveTrainIcon(
        context: Context,
        lineCode: String,
        lineColorHex: Int,
        delayMinutes: Int,
        bearing: Float,
        zoom: Double,
        showArrow: Boolean
    ): CachedMarkerIcon {
        val quantizedBearing = ((bearing / 15f).toInt() * 15) % 360
        val visualSizeDp = when {
            zoom < 11.0 -> 24
            zoom < 14.0 -> 30
            else -> 36
        }
        val touchSizeDp = 48
        val cleanKey = "LIVE_TRAIN_${lineCode}_${lineColorHex}_${delayMinutes}_${quantizedBearing}_${visualSizeDp}_$showArrow"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val ringColorHex = when {
            delayMinutes <= 1 -> AndroidColor.parseColor("#34C759")
            delayMinutes <= 4 -> AndroidColor.parseColor("#FF9500")
            else -> AndroidColor.parseColor("#FF3B30")
        }

        val density = context.resources.displayMetrics.density
        val px = (touchSizeDp * density).toInt().coerceAtLeast(48)
        val visualPx = (visualSizeDp * density).toInt().coerceAtLeast(22)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = px / 2f
        val radius = visualPx * 0.35f

        // 1. Directional Pointer protruding from edge if direction is known
        if (showArrow) {
            canvas.save()
            canvas.rotate(quantizedBearing.toFloat(), center, center)

            val pointerPath = android.graphics.Path().apply {
                val arrowW = radius * 0.45f
                val arrowH = radius * 0.40f
                moveTo(center, center - radius - arrowH)
                lineTo(center - arrowW, center - radius + 2f)
                lineTo(center + arrowW, center - radius + 2f)
                close()
            }

            val pointerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(pointerPath, pointerBorderPaint)

            val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColorHex
                style = Paint.Style.FILL
            }
            canvas.drawPath(pointerPath, pointerPaint)

            canvas.restore()
        }

        // 2. Main Circle with white border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius + (1.5f * density), borderPaint)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColorHex
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, circlePaint)

        // 3. Official Line Text Label
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = radius * 0.9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = center - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(lineCode, center, textY, textPaint)

        // 4. Status dot indicating delay
        val statusX = center + radius * 0.73f
        val statusY = center - radius * 0.73f
        val statusDotRadius = radius * 0.32f

        val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(statusX, statusY, statusDotRadius + (1.2f * density), dotBorderPaint)

        val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColorHex
            style = Paint.Style.FILL
        }
        canvas.drawCircle(statusX, statusY, statusDotRadius, dotCorePaint)

        val icon = CachedMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchor = Offset(0.5f, 0.5f)
        )
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateUserLocationIcon(
        context: Context,
        isCellTowerLocation: Boolean = false,
        hasDirectionalArrow: Boolean = true
    ): CachedMarkerIcon {
        val cleanKey = "USER_LOCATION_${isCellTowerLocation}_$hasDirectionalArrow"
        val cached = specialCache.get(cleanKey)
        if (cached != null) return cached

        val density = context.resources.displayMetrics.density
        val sizePx = (48f * density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = sizePx / 2f
        val cy = sizePx / 2f

        val coreColor = if (isCellTowerLocation) AndroidColor.parseColor("#059669") else AndroidColor.parseColor("#1D4ED8")

        // 1. Directional pointer triangle pointing UP (North / 0 degrees)
        if (!isCellTowerLocation && hasDirectionalArrow) {
            val baseDistance = 11f * density
            val tipDistance = 18.8f * density
            val baseHalfWidth = 4.5f * density

            val outerArrowPath = android.graphics.Path().apply {
                moveTo(cx, cy - tipDistance)
                lineTo(cx + baseHalfWidth, cy - baseDistance)
                lineTo(cx - baseHalfWidth, cy - baseDistance)
                close()
            }

            val arrowWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.8f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = AndroidColor.WHITE
            }
            val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = coreColor
            }

            canvas.drawPath(outerArrowPath, arrowWhitePaint)
            canvas.drawPath(outerArrowPath, arrowFillPaint)
        }

        // 2. Crisp white outer halo circle
        val whiteHaloRadius = 9.5f * density
        val whiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.WHITE
        }
        canvas.drawCircle(cx, cy, whiteHaloRadius, whiteHaloPaint)

        // 3. Vibrant inner core circle
        val coreRadius = 6.5f * density
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = coreColor
        }
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        val icon = CachedMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchor = Offset(0.5f, 0.5f)
        )
        specialCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateChevronArrowIcon(
        context: Context,
        borderColor: Int
    ): CachedMarkerIcon {
        val key = "CHEVRON_ARROW_COMPACT_$borderColor"
        val cached = specialCache.get(key)
        if (cached != null) return cached

        val density = context.resources.displayMetrics.density
        val sizePx = (16f * density).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = sizePx / 2f
        val cy = sizePx / 2f

        val length = 8.5f * density
        val halfWidth = 4.2f * density
        val indent = 2.6f * density

        val path = android.graphics.Path().apply {
            moveTo(cx, cy - length * 0.5f)
            lineTo(cx + halfWidth, cy + length * 0.5f)
            lineTo(cx, cy + length * 0.5f - indent)
            lineTo(cx - halfWidth, cy + length * 0.5f)
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.WHITE
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = 1.3f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = borderColor
        }

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, borderPaint)

        val icon = CachedMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchor = Offset(0.5f, 0.5f)
        )
        specialCache.put(key, icon)
        return icon
    }

    fun clear() {
        specialCache.evictAll()
    }
}
