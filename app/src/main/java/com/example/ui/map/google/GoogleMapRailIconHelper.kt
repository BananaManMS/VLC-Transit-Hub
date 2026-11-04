package com.example.ui.map.google

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.collection.LruCache
import androidx.compose.ui.geometry.Offset
import com.example.ui.map.components.createCercaniasMarkerIcon
import com.example.ui.map.components.createMetroMarkerIcon
import com.example.ui.map.components.getCercaniasTinyDotIcon
import com.example.ui.map.components.getMetroWhiteDotIcon
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Dedicated cache and icon generator for Metrovalencia and Cercanías Renfe.
 * Isolated LruCache prevents rail icons from being evicted by high-volume bus stops.
 */
internal object GoogleMapRailIconHelper {
    private val railCache = LruCache<String, CachedMarkerIcon>(128)

    fun getOrCreateMetroStationIcon(
        context: Context,
        stationName: String,
        lines: List<String>,
        isDarkMode: Boolean,
        showPill: Boolean = true,
        isFavorite: Boolean = false
    ): CachedMarkerIcon {
        val favTag = if (isFavorite) "FAV_" else ""
        val cleanKey = "METRO_${favTag}${stationName}_${lines.sorted().joinToString(",")}_${isDarkMode}_$showPill"
        val cached = railCache.get(cleanKey)
        if (cached != null) return cached

        val displayName = if (isFavorite) "★ $stationName" else stationName

        val icon = try {
            val result = createMetroMarkerIcon(
                context = context,
                stationName = displayName,
                lines = lines,
                isDarkMode = isDarkMode,
                showPill = showPill
            )
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(result.anchorU, result.anchorV)
                )
            } else {
                createMetroStationFallbackIcon(context, displayName, lines, isDarkMode)
            }
        } catch (e: Throwable) {
            Log.e("GoogleMapRailIconHelper", "Error generating authentic metro icon for $stationName", e)
            createMetroStationFallbackIcon(context, displayName, lines, isDarkMode)
        }

        railCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateMetroWhiteDotIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon {
        val key = "METRO_WHITE_DOT_$isDarkMode"
        val cached = railCache.get(key)
        if (cached != null) return cached

        val result = getMetroWhiteDotIcon(context, isDarkMode)
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
        val icon = if (bitmap != null) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                anchor = Offset(result.anchorU, result.anchorV)
            )
        } else {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                anchor = Offset(0.5f, 0.5f)
            )
        }
        railCache.put(key, icon)
        return icon
    }

    private fun createMetroStationFallbackIcon(
        context: Context,
        stationName: String,
        lines: List<String>,
        isDarkMode: Boolean
    ): CachedMarkerIcon {
        return try {
            val density = context.resources.displayMetrics.density
            val logoRadius = 13f * density
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDarkMode) AndroidColor.WHITE else AndroidColor.parseColor("#0F172A")
                textSize = 12f * density
                typeface = Typeface.DEFAULT_BOLD
            }
            val textWidth = textPaint.measureText(stationName)
            val spacing = 6f * density
            val width = (logoRadius * 2f + spacing + textWidth + 8f * density).toInt()
            val height = (logoRadius * 2f + 4f * density).toInt()

            val bitmap = Bitmap.createBitmap(maxOf(width, 1), maxOf(height, 1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Metro Logo Circle
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = AndroidColor.parseColor("#E2001A")
            canvas.drawCircle(logoRadius + 2f * density, height / 2f, logoRadius, paint)

            // "m" letter
            val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 12f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = letterPaint.fontMetrics
            val yOffset = (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText("m", logoRadius + 2f * density, height / 2f - yOffset, letterPaint)

            // Station Name with outline for high legibility
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDarkMode) AndroidColor.parseColor("#090D16") else AndroidColor.WHITE
                textSize = 12f * density
                typeface = Typeface.DEFAULT_BOLD
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeJoin = Paint.Join.ROUND
            }
            val textX = logoRadius * 2f + spacing
            val textY = height / 2f - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
            canvas.drawText(stationName, textX, textY, strokePaint)
            canvas.drawText(stationName, textX, textY, textPaint)

            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                anchor = Offset((logoRadius + 2f * density) / width.toFloat(), 0.5f)
            )
        } catch (e: Throwable) {
            Log.e("GoogleMapRailIconHelper", "Error generating metro fallback icon for $stationName", e)
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                anchor = Offset(0.5f, 1.0f)
            )
        }
    }

    fun getOrCreateCercaniasStationIcon(
        context: Context,
        stationName: String,
        lines: List<String>,
        isDarkMode: Boolean,
        showPill: Boolean = true,
        isFavorite: Boolean = false
    ): CachedMarkerIcon {
        val favTag = if (isFavorite) "FAV_" else ""
        val cleanKey = "CERCANIAS_${favTag}${stationName}_${lines.sorted().joinToString(",")}_${isDarkMode}_$showPill"
        val cached = railCache.get(cleanKey)
        if (cached != null) return cached

        val displayName = if (isFavorite) "★ $stationName" else stationName

        val icon = try {
            val result = createCercaniasMarkerIcon(
                context = context,
                stationName = displayName,
                lines = lines,
                isDarkMode = isDarkMode,
                showPill = showPill
            )
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(result.anchorU, result.anchorV)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    anchor = Offset(0.5f, 1.0f)
                )
            }
        } catch (e: Throwable) {
            Log.e("GoogleMapRailIconHelper", "Error generating cercanias icon for $stationName", e)
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                anchor = Offset(0.5f, 1.0f)
            )
        }

        railCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateCercaniasRedDotIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon {
        val key = "CERCANIAS_RED_DOT_$isDarkMode"
        val cached = railCache.get(key)
        if (cached != null) return cached

        val result = getCercaniasTinyDotIcon(context, isDarkMode)
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
        val icon = if (bitmap != null) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                anchor = Offset(result.anchorU, result.anchorV)
            )
        } else {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                anchor = Offset(0.5f, 0.5f)
            )
        }
        railCache.put(key, icon)
        return icon
    }

    fun getOrCreateCercaniasLogoSmallIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon {
        val key = "CERCANIAS_LOGO_SMALL_$isDarkMode"
        val cached = railCache.get(key)
        if (cached != null) return cached

        val result = com.example.ui.map.components.getCercaniasLogoSmallIcon(context, isDarkMode)
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
        val icon = if (bitmap != null) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                anchor = Offset(result.anchorU, result.anchorV)
            )
        } else {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                anchor = Offset(0.5f, 0.5f)
            )
        }
        railCache.put(key, icon)
        return icon
    }

    fun clear() {
        railCache.evictAll()
    }
}
