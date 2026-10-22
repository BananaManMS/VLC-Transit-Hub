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
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.View
import android.widget.TextView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * Handles icon generation and tooltips for Metro station access markers (entrances, elevators, stairs).
 */
object MetroAccessPointRenderer {

    private val iconCache = LruCache<String, Drawable>(32)

    fun getAccessIcon(context: Context, typeCas: String): Drawable {
        iconCache.get(typeCas)?.let { return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (18 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Semi-transparent Metro red background (~70% opacity)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3DC2626")
            style = Paint.Style.FILL
        }

        // Crisp subtle border (~80% white opacity)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }

        val cornerRadius = 4f * density
        val rect = RectF(1f * density, 1f * density, sizePx - 1f * density, sizePx - 1f * density)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f

        when {
            typeCas.contains("Ascensor", ignoreCase = true) -> {
                // Elevator icon: frame with up/down arrows
                val boxWidth = 6.5f * density
                val boxHeight = 8f * density
                val boxRect = RectF(cx - boxWidth / 2f, cy - boxHeight / 2f, cx + boxWidth / 2f, cy + boxHeight / 2f)
                canvas.drawRoundRect(boxRect, 1.5f * density, 1.5f * density, iconPaint)

                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                val pathUp = Path().apply {
                    moveTo(cx, cy - 2f * density)
                    lineTo(cx - 1.5f * density, cy + 0.2f * density)
                    lineTo(cx + 1.5f * density, cy + 0.2f * density)
                    close()
                }
                val pathDown = Path().apply {
                    moveTo(cx, cy + 2f * density)
                    lineTo(cx - 1.5f * density, cy - 0.2f * density)
                    lineTo(cx + 1.5f * density, cy - 0.2f * density)
                    close()
                }
                canvas.drawPath(pathUp, arrowPaint)
                canvas.drawPath(pathDown, arrowPaint)
            }
            else -> {
                // Boca metro / Acceso subterráneo / Acceso estación / Tranvía -> Stairs icon
                val stairsPath = Path().apply {
                    moveTo(cx - 3.5f * density, cy + 3.5f * density)
                    lineTo(cx - 1f * density, cy + 3.5f * density)
                    lineTo(cx - 1f * density, cy + 1f * density)
                    lineTo(cx + 1f * density, cy + 1f * density)
                    lineTo(cx + 1f * density, cy - 1.5f * density)
                    lineTo(cx + 3.5f * density, cy - 1.5f * density)
                    lineTo(cx + 3.5f * density, cy - 3.5f * density)
                }
                canvas.drawPath(stairsPath, iconPaint)
            }
        }

        val drawable = BitmapDrawable(context.resources, bitmap)
        iconCache.put(typeCas, drawable)
        return drawable
    }

    class AccessToolTipInfoWindow(
        titleText: String,
        mapView: MapView
    ) : InfoWindow(createView(mapView.context, titleText), mapView) {

        override fun onOpen(item: Any?) {
            closeAllInfoWindowsOn(mMapView)
            val density = mMapView.context.resources.displayMetrics.density
            mView.translationY = -6f * density
            mView.removeCallbacks(dismissRunnable)
            mView.postDelayed(dismissRunnable, 2200)
        }

        override fun onClose() {
            mView.removeCallbacks(dismissRunnable)
        }

        private val dismissRunnable = Runnable {
            if (isOpen) {
                close()
                mMapView.invalidate()
            }
        }

        companion object {
            private fun createView(context: Context, text: String): View {
                val density = context.resources.displayMetrics.density
                return TextView(context).apply {
                    setText(text)
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(
                        (8 * density).toInt(),
                        (4 * density).toInt(),
                        (8 * density).toInt(),
                        (4 * density).toInt()
                    )
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F00F172A")) // Modern dark slate tooltip badge
                        cornerRadius = 6f * density
                        setStroke(
                            (1f * density).toInt(),
                            Color.parseColor("#475569")
                        )
                    }
                    elevation = 4f * density
                }
            }
        }
    }
}
