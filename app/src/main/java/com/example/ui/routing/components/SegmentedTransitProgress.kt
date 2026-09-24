package com.example.ui.routing.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.util.LineColorResolver

/**
 * Segmented Stepper Bar that divides the progress across multi-modal legs (Walk, Bus, Metro, Train).
 * Non-clickable: progress is automatically synchronized with GPS and the active step engine.
 */
@Composable
fun SegmentedTransitProgress(
    legs: List<PlannedLeg>,
    currentLegIndex: Int,
    currentLegProgressFraction: Float = 0.5f,
    isDark: Boolean
) {
    if (legs.isEmpty()) return

    val inactiveSegmentColor = com.example.ui.theme.AppThemeColors.subtleBorder(isDark)
    val completedColor = Color(0xFF00A86B)

    // Calculate balanced visual weights with minimum threshold so every leg has comfortable room
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

    val legWeights = remember(legs) { legs.map { getLegWeight(it) } }
    val totalWeight = remember(legWeights) { legWeights.sum().coerceAtLeast(1f) }

    val safeLegIndex = currentLegIndex.coerceIn(0, legs.size - 1)
    val completedWeight = legWeights.take(safeLegIndex).sum()
    val currentLegWeight = legWeights[safeLegIndex]
    val progressFraction = ((completedWeight + currentLegWeight * currentLegProgressFraction.coerceIn(0f, 1f)) / totalWeight).coerceIn(0.02f, 0.98f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            val totalWidthPx = maxWidth

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                legs.forEachIndexed { index, leg ->
                    val isCompleted = index < currentLegIndex
                    val isCurrent = index == currentLegIndex
                    val legColor = LineColorResolver.resolveRouteColor(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)
                    val weight = legWeights[index]

                    val segmentColor = when {
                        isCompleted -> completedColor
                        isCurrent -> legColor
                        else -> inactiveSegmentColor
                    }

                    val isWalk = leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE

                    if (isWalk) {
                        // Walking / cycling represented by symmetric continuous sequence of circles/dots
                        Canvas(
                            modifier = Modifier
                                .weight(weight)
                                .height(8.dp)
                        ) {
                            val dotRadius = if (isCurrent) 2.2.dp.toPx() else 1.8.dp.toPx()
                            val cy = size.height / 2f
                            val availableWidth = size.width
                            val desiredSpacing = 6.5.dp.toPx()

                            // Symmetrical margins on left and right borders of the canvas
                            val minMargin = dotRadius + 1.dp.toPx()
                            val usableWidth = availableWidth - (2 * minMargin)
                            if (usableWidth <= 0f) {
                                drawCircle(
                                    color = segmentColor,
                                    radius = dotRadius,
                                    center = Offset(availableWidth / 2f, cy)
                                )
                            } else {
                                val dotCount = (usableWidth / desiredSpacing).toInt().coerceAtLeast(1)
                                val actualSpacing = usableWidth / dotCount
                                for (i in 0..dotCount) {
                                    val cx = minMargin + i * actualSpacing
                                    drawCircle(
                                        color = segmentColor,
                                        radius = dotRadius,
                                        center = Offset(cx, cy)
                                    )
                                }
                            }
                        }
                    } else {
                        // Motorized Transit (Metro, Bus, Tram, Cercanías) represented by solid smooth bar
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(if (isCurrent) 8.dp else 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(segmentColor)
                        )
                    }
                }
            }

            // Real-Time Animated Pulse Dot cleanly bounded
            val dotSize = 12.dp
            val dotOffset = (totalWidthPx - dotSize) * progressFraction
            Box(
                modifier = Modifier
                    .offset(x = dotOffset, y = (-2).dp)
                    .size(dotSize)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color(0xFF00A86B), CircleShape)
            )
        }

        // Stepper Step Labels: perfectly aligned with identical weight & spacing as the bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            legs.forEachIndexed { index, leg ->
                val isCompleted = index < currentLegIndex
                val isCurrent = index == currentLegIndex
                val weight = legWeights[index]
                val legColor = LineColorResolver.resolveRouteColor(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)
                val isWalk = leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE

                val contentColor = when {
                    isCompleted -> completedColor
                    isCurrent -> if (isWalk) (if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)) else legColor
                    else -> if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                }

                Box(
                    modifier = Modifier.weight(weight),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val icon = when (leg.mode) {
                            TransitMode.WALK, TransitMode.BICYCLE -> Icons.AutoMirrored.Filled.DirectionsWalk
                            TransitMode.BUS -> Icons.Default.DirectionsBus
                            TransitMode.RAIL -> Icons.Default.DirectionsRailway
                            else -> Icons.Default.Subway
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(11.dp)
                        )

                        Spacer(modifier = Modifier.width(2.5.dp))

                        if (isWalk) {
                            val walkMinutes = ((leg.durationSeconds + 30) / 60).coerceAtLeast(1)
                            Text(
                                text = "${walkMinutes}m",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        } else {
                            val lineName = leg.routeShortName?.takeIf { it.isNotBlank() } ?: leg.headsign?.takeIf { it.isNotBlank() } ?: "Metro"
                            Text(
                                text = lineName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = contentColor
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }
    }
}
