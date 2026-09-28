package com.example.ui.routing.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage

@Composable
fun OriginTimelineRow(item: TimelineItem.Origin) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            bottomLineColor = item.nextColor,
            bottomDotted = item.nextDotted,
            nodeColor = Color(0xFF1976D2),
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.time,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BoardingTimelineRow(item: TimelineItem.Boarding) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    val hasShift = !item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time
    val isDelayed = (item.delayMins != null && item.delayMins > 0) || (hasShift && item.time > (item.scheduledTime ?: ""))
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val timeColor = when {
        item.isLive && isDelayed -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        item.isLive -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        item.delayMins != null && item.delayMins != 0 -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.prevColor,
            topDotted = item.prevDotted,
            bottomLineColor = item.lineColor,
            nodeColor = item.lineColor,
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.stationName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time) {
                    Text(
                        text = item.scheduledTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = timeColor
                )
            }
        }
    }
}

@Composable
fun TransitRideTimelineRow(
    item: TimelineItem.TransitRide,
    appLanguage: AppLanguage,
    globalAlerts: List<String>
) {
    var expandedStops by remember { mutableStateOf(false) }
    val leg = item.leg
    val lineColor = item.lineColor
    val rowAlpha = if (item.isPast) 0.35f else 1.0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha)
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = lineColor,
            bottomLineColor = lineColor
        )
        Spacer(modifier = Modifier.width(10.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OperatorLogoOrIcon(leg = leg, lineColor = lineColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    val headsign = leg.headsign ?: leg.routeLongName
                    if (!headsign.isNullOrBlank()) {
                        Text(
                            text = "➔ $headsign",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isLive || leg.isRealTimeVerified) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00A86B).copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                        ) {
                            com.example.ui.components.LiveRssFeedIcon(
                                contentDescription = "En vivo",
                                tint = Color(0xFF00A86B),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "En vivo" else "En viu",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF00A86B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "Programado" else "Programat",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                val relevantAlert = when {
                    !leg.alertMessage.isNullOrBlank() -> leg.alertMessage
                    leg.hasActiveAlert -> "Aviso de servicio activo en esta línea"
                    else -> {
                        val line = leg.routeShortName ?: ""
                        val agency = leg.agencyName ?: ""
                        globalAlerts.firstOrNull { alert ->
                            (line.isNotEmpty() && alert.contains(line, ignoreCase = true)) ||
                            (agency.isNotEmpty() && alert.contains(agency, ignoreCase = true))
                        }
                    }
                }

                if (relevantAlert != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF3E2C1E) else Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.ES) "Aviso de servicio" else "Avís de servei",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)
                                    )
                                )
                                Text(
                                    text = relevantAlert,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isDark) Color(0xFFFFF3E0) else Color(0xFF5D4037),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                if (leg.intermediateStops.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { expandedStops = !expandedStops }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${leg.intermediateStops.size + 1} ${if (appLanguage == AppLanguage.ES) "paradas" else "parades"} (${leg.formattedDuration})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expandedStops) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = expandedStops,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 4.dp, top = 6.dp)
                                .fillMaxWidth()
                        ) {
                            leg.intermediateStops.forEach { stop ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(MaterialTheme.colorScheme.outline, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stop.name,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    if (stop.formattedTime != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!stop.scheduledTime.isNullOrBlank() && stop.scheduledTime != stop.formattedTime) {
                                                Text(
                                                    text = stop.scheduledTime,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textDecoration = TextDecoration.LineThrough
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = stop.formattedTime,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (!stop.scheduledTime.isNullOrBlank() && stop.scheduledTime != stop.formattedTime) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = if (!stop.scheduledTime.isNullOrBlank() && stop.scheduledTime != stop.formattedTime) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferTimelineRow(
    item: TimelineItem.Transfer,
    appLanguage: AppLanguage,
    onRecalculateTransfer: ((stationName: String, lat: Double, lon: Double, arrivalTime: String) -> Unit)? = null,
    isRecalculating: Boolean = false
) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.Top
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.incomingColor,
            bottomLineColor = item.outgoingColor,
            nodeColor = if (item.isRisk) Color(0xFFE65100) else item.outgoingColor,
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.stationName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${item.arrivalTime} ➔ ${item.departureTime}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (item.isRisk) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (item.isRisk) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (appLanguage == AppLanguage.ES) "Transbordo en ${item.stationName} (${item.durationStr})" else "Transbord a ${item.stationName} (${item.durationStr})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (item.isRisk) Color(0xFFE65100) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (item.isRisk) {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF3E2C1E) else Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "Transbordo en riesgo" else "Transbord en risc",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)
                                )
                            )
                        }

                        if (!item.nextScheduledDepartureTime.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val lineLabel = item.outgoingLine ?: ""
                            val destLabel = item.destination ?: ""
                            val lineDisp = if (lineLabel.startsWith("L") || lineLabel.isBlank()) lineLabel else "L$lineLabel"
                            val nextText = if (appLanguage == AppLanguage.ES) {
                                "Siguiente $lineDisp a $destLabel: ${item.nextScheduledDepartureTime} (Horario programado)"
                            } else {
                                "Següent $lineDisp a $destLabel: ${item.nextScheduledDepartureTime} (Horari programat)"
                            }

                            Text(
                                text = nextText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF37474F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        if (onRecalculateTransfer != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    onRecalculateTransfer(
                                        item.stationName,
                                        item.transferLat,
                                        item.transferLon,
                                        item.arrivalTime
                                    )
                                },
                                enabled = !isRecalculating,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isRecalculating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ES) "Buscando alternativas..." else "Buscant alternatives...",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.SyncAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ES) "Buscar alternativas" else "Buscar alternatives",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlightingTimelineRow(item: TimelineItem.Alighting) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    val hasShift = !item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time
    val isDelayed = (item.delayMins != null && item.delayMins > 0) || (hasShift && item.time > (item.scheduledTime ?: ""))
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val timeColor = when {
        item.isLive && isDelayed -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        item.isLive -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        item.delayMins != null && item.delayMins != 0 -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.lineColor,
            bottomLineColor = item.nextColor,
            bottomDotted = item.nextDotted,
            nodeColor = item.lineColor,
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.stationName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time) {
                    Text(
                        text = item.scheduledTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = timeColor
                )
            }
        }
    }
}

@Composable
fun WalkTimelineRow(
    item: TimelineItem.Walk,
    appLanguage: AppLanguage
) {
    val leg = item.leg
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha)
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.prevColor,
            bottomLineColor = item.nextColor,
            topDotted = true,
            bottomDotted = true
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isTransfer) Icons.Default.SyncAlt else Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            val walkLabel = if (item.isTransfer) {
                if (appLanguage == AppLanguage.ES) "Transbordo (${leg.formattedDuration})" else "Transbord (${leg.formattedDuration})"
            } else {
                "${if (appLanguage == AppLanguage.ES) "Camina" else "Camina"} ${leg.formattedDuration} (${leg.distanceMeters.toInt()} m)"
            }
            Text(
                text = walkLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun DestinationTimelineRow(item: TimelineItem.Destination) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    val hasShift = !item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time
    val isDelayed = (item.delayMins != null && item.delayMins > 0) || (hasShift && item.time > (item.scheduledTime ?: ""))
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val timeColor = when {
        item.isLive && isDelayed -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        item.isLive -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        item.delayMins != null && item.delayMins != 0 -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.prevColor,
            topDotted = item.prevDotted,
            isPinIcon = true
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time) {
                    Text(
                        text = item.scheduledTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = timeColor
                )
            }
        }
    }
}
