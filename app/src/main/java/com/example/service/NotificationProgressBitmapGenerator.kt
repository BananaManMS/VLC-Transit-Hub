package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode

/**
 * High-performance graphical generator for notification progress bars.
 * Accurately replicates Transit App's signature notification layout:
 * - Avatar/mode badge moves dynamically along the track to current user position
 * - Completed legs rendered in muted gray dots/pills
 * - Active/upcoming transit lines rendered as thick rounded colored pills
 * - Real-time glowing location marker around active avatar
 */
object NotificationProgressBitmapGenerator {

    fun generateProgressBarBitmap(
        context: Context,
        legs: List<PlannedLeg>,
        currentLegIndex: Int,
        progressFractionInLeg: Float = 0.5f,
        width: Int = 1000,
        height: Int = 70
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (legs.isEmpty()) return bitmap

        val safeLegIndex = currentLegIndex.coerceIn(0, legs.size - 1)

        val startX = 10f
        val trackWidth = width - 20f
        val centerY = height / 2f

        // Compute leg weights using the same balanced formula as in ActiveTripOverlay
        fun getLegWeight(leg: PlannedLeg): Float {
            val durationMin = (leg.durationSeconds / 60f).coerceAtLeast(1f)
            return when (leg.mode) {
                TransitMode.WALK, TransitMode.BICYCLE -> {
                    1.0f + (durationMin.coerceIn(1f, 15f) - 1f) * 0.08f
                }
                else -> {
                    1.25f + (durationMin.coerceIn(1f, 30f) - 1f) * 0.12f
                }
            }
        }

        val weights = legs.map { getLegWeight(it) }
        val totalWeight = weights.sum().coerceAtLeast(1f)

        val completedWeight = weights.take(safeLegIndex).sum()
        val currentLegWeight = weights[safeLegIndex]
        val overallProgressFraction = ((completedWeight + currentLegWeight * progressFractionInLeg.coerceIn(0f, 1f)) / totalWeight).coerceIn(0.01f, 0.99f)

        val spacingPx = 8f
        val totalSpacing = spacingPx * (legs.size - 1).coerceAtLeast(0)
        val availableTrackWidth = (trackWidth - totalSpacing).coerceAtLeast(80f)

        val barHeight = 20f
        val barTop = centerY - barHeight / 2f
        val barBottom = centerY + barHeight / 2f

        val completedMutedColor = Color.parseColor("#475569") // Muted slate gray for completed legs
        val upcomingInactiveColor = Color.parseColor("#334155") // Dark slate gray for future inactive legs

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        var currentX = startX

        // 1. Draw Track Segments (Completed = muted gray, Active/Upcoming = vibrant colors)
        legs.forEachIndexed { index, leg ->
            val segmentWidth = (weights[index] / totalWeight) * availableTrackWidth
            val isCompleted = index < currentLegIndex
            val isCurrent = index == currentLegIndex

            val routeColor = resolveRouteColorInt(leg.mode, leg.routeShortName, leg.routeColorHex)
            val segmentColor = when {
                isCompleted -> completedMutedColor
                isCurrent -> routeColor
                else -> upcomingInactiveColor
            }

            barPaint.color = segmentColor

            if (leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE) {
                // Uniform constant-pitch dots for walk segments
                val dotRadius = 5f
                val dotSpacing = 18f // constant pitch
                if (segmentWidth >= dotRadius * 2) {
                    val count = ((segmentWidth - dotRadius * 2) / dotSpacing).toInt() + 1
                    val actualCount = count.coerceAtLeast(1)
                    val totalSpan = (actualCount - 1) * dotSpacing
                    val segStartX = currentX + (segmentWidth - totalSpan) / 2f

                    for (d in 0 until actualCount) {
                        val dotCx = segStartX + d * dotSpacing
                        canvas.drawCircle(dotCx, centerY, dotRadius, barPaint)
                    }
                }
            } else {
                // Thick rounded pill bar for transit lines
                val rect = RectF(currentX, barTop, currentX + segmentWidth, barBottom)
                canvas.drawRoundRect(rect, 10f, 10f, barPaint)
            }

            currentX += segmentWidth + spacingPx
        }

        // 2. Draw Clean & Elegant Green Progress Marker Circle (High visibility, no clutter)
        val markerCenterX = (startX + trackWidth * overallProgressFraction).coerceIn(startX + 16f, startX + trackWidth - 16f)

        // Outer subtle translucent green pulse halo
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#4D00E676") // Soft emerald green halo
        }
        canvas.drawCircle(markerCenterX, centerY, 18f, haloPaint)

        // Solid Emerald Green Circle
        val greenCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#00E676") // Vibrant primary green
        }
        canvas.drawCircle(markerCenterX, centerY, 12f, greenCirclePaint)

        // Crisp White Inner Core Dot
        val innerCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(markerCenterX, centerY, 5.5f, innerCorePaint)

        return bitmap
    }

    private fun resolveRouteColorInt(mode: TransitMode, routeName: String?, hex: String?): Int {
        if (!hex.isNullOrBlank()) {
            return try {
                Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
            } catch (e: Exception) {
                fallbackColorForMode(mode)
            }
        }
        val cleanName = (routeName ?: "").trim().uppercase()
        return when (mode) {
            TransitMode.SUBWAY, TransitMode.TRAM -> when {
                cleanName == "1" || cleanName == "L1" -> Color.parseColor("#E53935") // L1 Red
                cleanName == "2" || cleanName == "L2" -> Color.parseColor("#E91E63") // L2 Pink
                cleanName == "3" || cleanName == "L3" -> Color.parseColor("#D32F2F") // L3 Red
                cleanName == "4" || cleanName == "L4" -> Color.parseColor("#1565C0") // L4 Blue
                cleanName == "5" || cleanName == "L5" -> Color.parseColor("#00A86B") // L5 Green
                cleanName == "6" || cleanName == "L6" -> Color.parseColor("#8E24AA") // L6 Purple
                cleanName == "7" || cleanName == "L7" -> Color.parseColor("#FB8C00") // L7 Orange
                cleanName == "8" || cleanName == "L8" -> Color.parseColor("#00ACC1") // L8 Cyan
                cleanName == "9" || cleanName == "L9" -> Color.parseColor("#7CB342") // L9 Light Green
                cleanName == "10" || cleanName == "L10" -> Color.parseColor("#00838F") // L10 Teal
                else -> Color.parseColor("#D32F2F")
            }
            TransitMode.BUS -> Color.parseColor("#0D47A1") // EMT Blue
            TransitMode.RAIL -> Color.parseColor("#C2185B") // Cercanías Red
            TransitMode.WALK -> Color.parseColor("#00A86B") // Walk Green
            TransitMode.BICYCLE -> Color.parseColor("#00897B")
        }
    }

    private fun fallbackColorForMode(mode: TransitMode): Int = when (mode) {
        TransitMode.BUS -> Color.parseColor("#0D47A1")
        TransitMode.SUBWAY, TransitMode.TRAM -> Color.parseColor("#D32F2F")
        TransitMode.RAIL -> Color.parseColor("#C2185B")
        TransitMode.WALK -> Color.parseColor("#00A86B")
        TransitMode.BICYCLE -> Color.parseColor("#00897B")
    }

    fun generateModeIconBitmap(context: Context, mode: TransitMode, isSalYa: Boolean = false): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val iconRes = when (mode) {
            TransitMode.BUS -> R.drawable.ic_notif_bus
            TransitMode.SUBWAY, TransitMode.TRAM -> R.drawable.ic_notif_subway
            TransitMode.RAIL -> R.drawable.ic_notif_train
            TransitMode.WALK -> R.drawable.ic_notif_walk
            TransitMode.BICYCLE -> R.drawable.ic_bike
        }

        val iconColor = if (isSalYa) Color.parseColor("#FF9800") else when (mode) {
            TransitMode.BUS -> Color.parseColor("#38BDF8")
            TransitMode.SUBWAY, TransitMode.TRAM -> Color.parseColor("#F87171")
            TransitMode.RAIL -> Color.parseColor("#F472B6")
            TransitMode.WALK -> Color.parseColor("#4ADE80")
            TransitMode.BICYCLE -> Color.parseColor("#2DD4BF")
        }

        drawVectorDrawable(
            context = context,
            canvas = canvas,
            drawableResId = iconRes,
            left = 4,
            top = 4,
            right = size - 4,
            bottom = size - 4,
            tintColor = iconColor
        )

        return bitmap
    }

    private fun drawVectorDrawable(
        context: Context,
        canvas: Canvas,
        drawableResId: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        tintColor: Int? = null
    ) {
        val drawable = ContextCompat.getDrawable(context, drawableResId)?.mutate() ?: return
        if (tintColor != null) {
            drawable.setTint(tintColor)
        }
        drawable.setBounds(left, top, right, bottom)
        drawable.draw(canvas)
    }
}
