package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe rasterized logo & color cache shared across all marker icon generators.
 * Eliminates repeated XML VectorDrawable inflation, mutating/tinting, and vector path rasterization
 * across hundreds of transit stations and stops.
 */
internal object MarkerLogoBitmapCache {

    private data class RasterLogoKey(
        val resId: Int,
        val widthPx: Int,
        val heightPx: Int,
        val tintColor: Int?
    )

    private val logoBitmapCache = ConcurrentHashMap<RasterLogoKey, Bitmap>(16)
    private val parsedHexColorCache = ConcurrentHashMap<String, Int>(32)
    private val bitmapDrawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    @Volatile
    private var cachedBikeResId: Int = -1

    fun parseColorCached(hex: String): Int {
        parsedHexColorCache[hex]?.let { return it }
        val parsed = Color.parseColor(hex)
        parsedHexColorCache[hex] = parsed
        return parsed
    }

    fun resolveBikeDrawableResId(context: Context): Int {
        val current = cachedBikeResId
        if (current != -1) return current
        val resolved = context.resources.getIdentifier("ic_bike", "drawable", context.packageName)
        cachedBikeResId = resolved
        return resolved
    }

    /**
     * Draws a drawable resource fitted within maxSize (preserving intrinsic aspect ratio) onto [canvas]
     * using a pre-rasterized cached Bitmap. Returns false if the drawable could not be loaded.
     */
    fun drawAspectFittedLogo(
        context: Context,
        canvas: Canvas,
        resId: Int,
        centerX: Float,
        centerY: Float,
        maxSize: Float,
        fallbackPaddingInset: Float = 0f,
        tintColor: Int? = null
    ): Boolean {
        if (resId == 0) return false

        val drawable = try {
            ResourcesCompat.getDrawable(context.resources, resId, context.theme)
        } catch (_: Exception) {
            null
        } ?: return false

        val intrinsicWidth = drawable.intrinsicWidth.toFloat()
        val intrinsicHeight = drawable.intrinsicHeight.toFloat()

        val leftInt: Int
        val topInt: Int
        val rightInt: Int
        val bottomInt: Int

        if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            val ratio = intrinsicWidth / intrinsicHeight
            val w: Float
            val h: Float
            if (ratio > 1f) {
                w = maxSize
                h = maxSize / ratio
            } else {
                h = maxSize
                w = maxSize * ratio
            }
            val left = centerX - w / 2f
            val top = centerY - h / 2f
            leftInt = left.toInt()
            topInt = top.toInt()
            rightInt = (left + w).toInt()
            bottomInt = (top + h).toInt()
        } else {
            val half = (maxSize / 2f)
            leftInt = (centerX - half + fallbackPaddingInset).toInt()
            topInt = (centerY - half + fallbackPaddingInset).toInt()
            rightInt = (centerX + half - fallbackPaddingInset).toInt()
            bottomInt = (centerY + half - fallbackPaddingInset).toInt()
        }

        val wPx = (rightInt - leftInt).coerceAtLeast(1)
        val hPx = (bottomInt - topInt).coerceAtLeast(1)

        val key = RasterLogoKey(resId, wPx, hPx, tintColor)
        var bitmap = logoBitmapCache[key]
        if (bitmap == null) {
            val raster = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            val rasterCanvas = Canvas(raster)
            val boundDrawable = if (tintColor != null) {
                DrawableCompat.wrap(drawable).mutate().also {
                    DrawableCompat.setTint(it, tintColor)
                }
            } else {
                drawable
            }
            boundDrawable.setBounds(0, 0, wPx, hPx)
            boundDrawable.draw(rasterCanvas)
            logoBitmapCache[key] = raster
            bitmap = raster
        }

        canvas.drawBitmap(bitmap, null, Rect(leftInt, topInt, rightInt, bottomInt), bitmapDrawPaint)
        return true
    }

    /**
     * Draws an exact square icon of [sizePx] x [sizePx] centered at ([centerX], [centerY]) using a cached raster Bitmap.
     */
    fun drawSquareLogo(
        context: Context,
        canvas: Canvas,
        resId: Int,
        centerX: Float,
        centerY: Float,
        sizePx: Int,
        tintColor: Int? = null
    ): Boolean {
        if (resId == 0 || sizePx <= 0) return false
        val key = RasterLogoKey(resId, sizePx, sizePx, tintColor)
        var bitmap = logoBitmapCache[key]
        if (bitmap == null) {
            val drawable = try {
                ResourcesCompat.getDrawable(context.resources, resId, context.theme)
            } catch (_: Exception) {
                null
            } ?: return false
            val raster = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val rasterCanvas = Canvas(raster)
            val boundDrawable = if (tintColor != null) {
                DrawableCompat.wrap(drawable).mutate().also {
                    DrawableCompat.setTint(it, tintColor)
                }
            } else {
                drawable
            }
            boundDrawable.setBounds(0, 0, sizePx, sizePx)
            boundDrawable.draw(rasterCanvas)
            logoBitmapCache[key] = raster
            bitmap = raster
        }

        val left = (centerX - sizePx / 2f).toInt().toFloat()
        val top = (centerY - sizePx / 2f).toInt().toFloat()
        canvas.drawBitmap(bitmap, left, top, bitmapDrawPaint)
        return true
    }

    fun clear() {
        logoBitmapCache.clear()
        parsedHexColorCache.clear()
    }
}
