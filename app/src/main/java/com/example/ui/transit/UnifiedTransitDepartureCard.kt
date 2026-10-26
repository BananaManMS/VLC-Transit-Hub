package com.example.ui.transit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import com.example.ui.components.shimmerEffect
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LiveRssFeedIcon
import com.example.ui.dashboard.AppLanguage

@Composable
fun UnifiedTransitDepartureCard(
    departure: UnifiedTransitDeparture,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    // Pure neutral backgrounds (No blue/navy tint)
    val cardBg = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF64748B)
    val opColor = Color(departure.operator.colorHex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("unified_dep_${departure.operator}_${departure.lineCode}_${departure.minutesRemaining}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = if (isDarkMode) BorderStroke(1.dp, Color(0xFF2C2C2E)) else BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Fixed-width Line Badge + Destination Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Fixed-width badge for clean tabular alignment
                Surface(
                    color = opColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(50.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Text(
                            text = departure.lineCode,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = departure.destination,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val showEstimatedTime = departure.isRealTime && departure.formattedEstimatedTime.isNotBlank()
                    val showOccupancy = departure.occupancy.isNotBlank()

                    if (showEstimatedTime || showOccupancy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showEstimatedTime) {
                                Text(
                                    text = "~${departure.formattedEstimatedTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (showOccupancy) {
                                OccupancyIndicator(
                                    occupancy = departure.occupancy,
                                    isDarkMode = isDarkMode
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Time Remaining / Live Indicator / Scheduled Label
            Column(horizontalAlignment = Alignment.End) {
                if (departure.isRealTime) {
                    val mins = departure.minutesRemaining
                    val isImmediate = mins <= 0
                    val immediateText = androidx.compose.ui.res.stringResource(com.example.R.string.departure_immediate)
                    val timeText = when {
                        mins < 0 -> if (departure.formattedEstimatedTime.isNotBlank()) departure.formattedEstimatedTime else immediateText
                        isImmediate -> immediateText
                        else -> "$mins min"
                    }

                    val timeColor = when {
                        isImmediate || mins <= 0 -> Color(0xFFE53935)
                        mins <= 2 -> Color(0xFFED8936)
                        mins <= 5 -> Color(0xFF38A169)
                        else -> if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = timeColor
                        )

                        LiveRssFeedIcon(
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.status_live),
                            tint = Color(0xFF2ECC71),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    // Scheduled Departure
                    val timeText = if (departure.formattedEstimatedTime.isNotBlank()) {
                        departure.formattedEstimatedTime
                    } else if (departure.minutesRemaining >= 0) {
                        "${departure.minutesRemaining} min"
                    } else {
                        "--:--"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.status_scheduled),
                            tint = subtextColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.departure_scheduled_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = subtextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Loading skeleton matching the EXACT dimensions, padding and structure of UnifiedTransitDepartureCard
 * to eliminate height jumps during transitions and stabilize bottom sheet anchoring.
 */
@Composable
fun UnifiedDepartureCardSkeleton(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = if (isDarkMode) BorderStroke(1.dp, Color(0xFF2C2C2E)) else BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Line badge placeholder
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(30.dp)
                        .shimmerEffect(shape = RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(16.dp)
                            .shimmerEffect(shape = RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                            .shimmerEffect(shape = RoundedCornerShape(4.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Time placeholder
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(20.dp)
                    .shimmerEffect(shape = RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun OccupancyIndicator(
    occupancy: String,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val clean = occupancy.trim().lowercase()
    val level = when {
        clean.contains("baja") || clean.contains("low") || clean.contains("1") -> 1
        clean.contains("alta") || clean.contains("high") || clean.contains("3") -> 3
        clean.contains("media") || clean.contains("medium") || clean.contains("2") -> 2
        else -> 1
    }

    val activeColor = when (level) {
        1 -> if (isDarkMode) Color(0xFF34D399) else Color(0xFF059669)
        2 -> if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706)
        else -> if (isDarkMode) Color(0xFFF87171) else Color(0xFFDC2626)
    }

    val dimmedColor = if (isDarkMode) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.16f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        for (i in 1..3) {
            val tint = if (i <= level) activeColor else dimmedColor
            PassengerSilhouette(tint = tint)
        }
    }
}

/**
 * Sleek, delicately proportioned passenger silhouette drawn directly via Canvas.
 * Eliminates standard Material icon internal padding to enable crisp, tight spacing without enclosing surfaces.
 */
@Composable
private fun PassengerSilhouette(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 8.dp, height = 12.dp)) {
        val w = size.width
        val h = size.height

        // Head circle
        val headRadius = w * 0.28f
        drawCircle(
            color = tint,
            radius = headRadius,
            center = Offset(w / 2f, headRadius)
        )

        // Torso with soft curved shoulders and rounded base
        val bodyTop = (headRadius * 2f) + 1.2.dp.toPx()
        val left = 0.5.dp.toPx()
        val right = w - 0.5.dp.toPx()
        val shoulderRadius = (right - left) * 0.35f
        val bottomRadius = 0.8.dp.toPx()

        val path = Path().apply {
            moveTo(left, h - bottomRadius)
            lineTo(left, bodyTop + shoulderRadius)
            quadraticBezierTo(left, bodyTop, left + shoulderRadius, bodyTop)
            lineTo(right - shoulderRadius, bodyTop)
            quadraticBezierTo(right, bodyTop, right, bodyTop + shoulderRadius)
            lineTo(right, h - bottomRadius)
            quadraticBezierTo(right, h, right - bottomRadius, h)
            lineTo(left + bottomRadius, h)
            quadraticBezierTo(left, h, left, h - bottomRadius)
            close()
        }
        drawPath(path = path, color = tint)
    }
}



