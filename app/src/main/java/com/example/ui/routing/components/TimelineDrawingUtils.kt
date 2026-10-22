package com.example.ui.routing.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode

/**
 * Reusable Canvas to draw perfectly centered graphic lines and circles for the timeline.
 */
@Composable
fun TimelineNodeCanvas(
    modifier: Modifier = Modifier,
    topLineColor: Color? = null,
    bottomLineColor: Color? = null,
    topDotted: Boolean = false,
    bottomDotted: Boolean = false,
    nodeColor: Color? = null,
    innerColor: Color? = null,
    isPinIcon: Boolean = false,
    pinColor: Color = Color(0xFFE53935)
) {
    Box(
        modifier = modifier.width(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokeWidthPx = 5.dp.toPx()
            val outerRadiusPx = 8.dp.toPx()
            val innerRadiusPx = 3.5f.dp.toPx()
            val dotRadius = 2.5f.dp.toPx()
            val dotStep = 7.5f.dp.toPx()

            val hasCenterNode = nodeColor != null || isPinIcon

            if (!hasCenterNode) {
                // Continuous segment connecting upper and lower nodes (e.g. Walk or Transit ride segment)
                if (topDotted || bottomDotted) {
                    val lineColor = topLineColor ?: bottomLineColor ?: Color(0xFF9E9E9E)
                    var y = dotStep / 2f
                    while (y <= size.height) {
                        drawCircle(color = lineColor, radius = dotRadius, center = Offset(cx, y))
                        y += dotStep
                    }
                } else if (topLineColor != null || bottomLineColor != null) {
                    val lineColor = topLineColor ?: bottomLineColor ?: Color.Gray
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Square
                    )
                }
            } else {
                // Top Line / Dots leading into central node
                if (topLineColor != null) {
                    if (topDotted) {
                        val maxDotY = cy - outerRadiusPx - (dotRadius * 0.5f)
                        var y = dotStep / 2f
                        while (y <= maxDotY) {
                            drawCircle(color = topLineColor, radius = dotRadius, center = Offset(cx, y))
                            y += dotStep
                        }
                    } else {
                        drawLine(
                            color = topLineColor,
                            start = Offset(cx, 0f),
                            end = Offset(cx, cy),
                            strokeWidth = strokeWidthPx,
                            cap = StrokeCap.Square
                        )
                    }
                }

                // Bottom Line / Dots leading out from central node
                if (bottomLineColor != null) {
                    if (bottomDotted) {
                        val minDotY = cy + outerRadiusPx + (dotRadius * 0.5f)
                        var y = minDotY + (dotStep / 2f)
                        while (y <= size.height) {
                            drawCircle(color = bottomLineColor, radius = dotRadius, center = Offset(cx, y))
                            y += dotStep
                        }
                    } else {
                        drawLine(
                            color = bottomLineColor,
                            start = Offset(cx, cy),
                            end = Offset(cx, size.height),
                            strokeWidth = strokeWidthPx,
                            cap = StrokeCap.Square
                        )
                    }
                }
            }

            // Central Node Circle
            if (nodeColor != null) {
                drawCircle(color = nodeColor, radius = outerRadiusPx, center = Offset(cx, cy))
                if (innerColor != null) {
                    drawCircle(color = innerColor, radius = innerRadiusPx, center = Offset(cx, cy))
                }
            }
        }

        if (isPinIcon) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = pinColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Official Operator Logo & Line Capsule Badge.
 */
@Composable
fun OperatorLogoOrIcon(
    leg: PlannedLeg,
    lineColor: Color
) {
    val agency = leg.agencyName ?: ""
    val isEmt = agency.contains("EMT", ignoreCase = true) ||
            (agency.isBlank() && !leg.routeShortName.orEmpty().let {
                it.length >= 3 && (it.startsWith("1") || it.startsWith("2") || it.startsWith("3"))
            })

    val operatorLogoRes = when (leg.mode) {
        TransitMode.SUBWAY, TransitMode.TRAM -> R.drawable.logo_metrovalencia
        TransitMode.RAIL, TransitMode.CERCANIAS -> R.drawable.logo_cercanias
        TransitMode.BUS -> if (isEmt) R.drawable.logo_emt_valencia else R.drawable.logo_metrobus
        TransitMode.METROBUS -> R.drawable.logo_metrobus
        TransitMode.BICYCLE, TransitMode.VALENBISI -> R.drawable.ic_bike
        TransitMode.WALK -> null
        else -> null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (operatorLogoRes != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Image(
                    painter = painterResource(id = operatorLogoRes),
                    contentDescription = leg.mode.displayNameEs,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(3.dp)
                )
            }
        }

        // Line pill (e.g. 3, 32, C2)
        val lineLabel = when (leg.mode) {
            TransitMode.WALK -> "A pie"
            TransitMode.BUS -> leg.routeShortName ?: "Bus"
            TransitMode.METROBUS -> leg.routeShortName ?: "Metrobús"
            TransitMode.SUBWAY, TransitMode.TRAM -> {
                val short = leg.routeShortName ?: ""
                if (short.startsWith("L") || short.startsWith("T") || short.isEmpty()) short.ifEmpty { "Metro" } else "L$short"
            }
            TransitMode.RAIL, TransitMode.CERCANIAS -> leg.routeShortName ?: "Cercanías"
            TransitMode.BICYCLE, TransitMode.VALENBISI -> "Bici"
            else -> leg.routeShortName ?: ""
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = lineColor,
            contentColor = Color.White
        ) {
            Text(
                text = lineLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
