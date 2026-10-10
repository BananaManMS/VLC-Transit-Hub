package com.example.ui.cercanias

import com.example.ui.components.LinkifiedText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun CercaniasDepartureDetails(
    departure: CercaniasDeparture,
    alerts: List<CercaniasAlert>,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    originStationId: String = "",
    originStationName: String = ""
) {
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val cardBg = if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)

    val affectedAlerts = remember(alerts, departure) {
        val depRouteNorm = departure.routeId.uppercase().replace("-", "").replace(" ", "").trim()
        alerts.filter { alert ->
            if (alert.isAccessibility) return@filter false

            val matchesRoute = alert.routeIds.isEmpty() || alert.routeIds.any { rId ->
                val alertRouteNorm = rId.uppercase().replace("-", "").replace(" ", "").trim()
                alertRouteNorm == depRouteNorm || alertRouteNorm.contains(depRouteNorm) || depRouteNorm.contains(alertRouteNorm)
            }

            if (!matchesRoute) return@filter false

            if (alert.tripIds.isNotEmpty()) {
                CercaniasRouteUtils.isTripMatchedByAlert(departure.tripId, departure.allTripIds, alert.tripIds)
            } else {
                val text = "${alert.headerEs} ${alert.descriptionEs}".lowercase()
                val timeRegex = Regex("""\b(\d{1,2})[:.](\d{2})\s*h?\b""")
                val timesInText = timeRegex.findAll(text).map { it.groupValues[1].padStart(2, '0') + ":" + it.groupValues[2] }.toList()
                if (timesInText.isNotEmpty()) {
                    val depTime = departure.departureTime.take(5)
                    timesInText.contains(depTime)
                } else {
                    true
                }
            }
        }
    }

    val routeColor = when(departure.routeId.uppercase().replace("-", "").trim()) {
        "C1" -> Color(0xFF00A3E0)
        "C2" -> Color(0xFFFF6A00)
        "C3" -> Color(0xFF7A287B)
        "C4" -> Color(0xFFE52321)
        "C5" -> Color(0xFF009639)
        "C6" -> Color(0xFF002F6C)
        else -> Color.Gray
    }
    
    val routeText = if (departure.routeId.matches(Regex("C\\d"))) "C-${departure.routeId.substring(1)}" else departure.routeId
    val destinationText = remember(departure.destination) {
        com.example.data.mapper.CercaniasDepartureMapper.formatStationDisplayName(
            departure.destination
                .replace("dirección", "", ignoreCase = true)
                .replace("direccion", "", ignoreCase = true)
                .trim()
        )
    }

    val originInfo = remember(originStationId, originStationName, departure.routeId) {
        CercaniasRouteUtils.getOriginStationInfo(originStationId, originStationName, departure.routeId)
    }

    val remainingStops = remember(departure, originStationId, originStationName) {
        CercaniasRouteUtils.getRemainingStops(
            originStationId = originStationId,
            originStationName = originStationName,
            destinationName = departure.destination,
            routeId = departure.routeId
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // --- HEADER ROW (Badge + Destino) ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            com.example.ui.metro.CercaniasLineBadge(
                routeId = departure.routeId,
                size = 42.dp
            )

            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.dir_prefix, destinationText),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // --- OPTION A COMPACT STATUS HEADER (Hora, Vía, Estado y Ubicación integrados) ---
        CercaniasDepartureStatusHeader(
            departure = departure,
            isDarkMode = isDarkMode,
            appLanguage = appLanguage,
            destinationText = destinationText
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- THIRD CARD: INCIDENCIAS (SOLO SI HAY INCIDENCIAS ACTIVAS) ---
        val hasIncidences = departure.isCanceled || departure.isSkippedAtStop || affectedAlerts.isNotEmpty()

        if (hasIncidences) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (departure.isCanceled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF331818) else Color(0xFFFFEBEE)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_canceled_service),
                                color = Color(0xFFE53935),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                affectedAlerts.forEach { alert ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF331818) else Color(0xFFFFEBEE)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val noticeLabel = androidx.compose.ui.res.stringResource(com.example.R.string.alert_title)
                                val activeNoticeLabel = androidx.compose.ui.res.stringResource(com.example.R.string.metro_active_incident_label)
                                val prefix = if (alert.tripIds.isNotEmpty()) "$noticeLabel Tren ${departure.tripId}: " else "$noticeLabel L$routeText: "
                                Text(
                                    text = prefix + alert.headerEs.ifBlank { activeNoticeLabel },
                                    color = Color(0xFFE53935),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinkifiedText(
                                    text = alert.descriptionEs,
                                    textColor = textColor,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- FOURTH CARD: RECORRIDO Y PRÓXIMAS ESTACIONES (TIMELINE - METRO STYLE UI) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_route_timeline_header),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = subtextColor.copy(alpha = 0.8f)
                )

                // Current station highlight card (Metro style)
                val currentStationLabel = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_current_station)
                val currentStationName = originInfo?.name ?: originStationName.ifBlank {
                    currentStationLabel
                }

                val originSchedTime = remember(departure.tripId, originInfo?.id, currentStationName) {
                    CercaniasRouteUtils.getStationScheduledTime(
                        departure.tripId,
                        originInfo?.id ?: originStationId,
                        currentStationName
                    ) ?: departure.departureTime.ifBlank { null }
                }
                val originEstTime = remember(originSchedTime, departure.delayMinutes, departure.estimatedTime) {
                    if (originSchedTime != null) {
                        CercaniasRouteUtils.addMinutesToTime(originSchedTime, departure.delayMinutes)
                    } else {
                        departure.estimatedTime.ifBlank { null }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = routeColor.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.5.dp, routeColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(routeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRailway,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentStationName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_current_station_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = routeColor
                            )
                        }

                        val displayEst = originEstTime ?: originSchedTime ?: "--:--"
                        val displaySched = originSchedTime ?: ""
                        val hasDelay = departure.delayMinutes != 0 && displaySched.isNotBlank() && displaySched != displayEst

                        Column(horizontalAlignment = Alignment.End) {
                            if (departure.isCanceled) {
                                Text(
                                    text = displaySched.ifBlank { "--:--" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935),
                                    style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                )
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_cancelled_status),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935)
                                )
                            } else {
                                val estColor = when {
                                    departure.delayMinutes < 0 -> Color(0xFF0284C7)
                                    departure.delayMinutes in 0..3 -> Color(0xFF2ECC71)
                                    departure.delayMinutes in 4..5 -> Color(0xFFF97316)
                                    else -> Color(0xFFE53935)
                                }
                                Text(
                                    text = displayEst,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (departure.isLive || departure.delayMinutes != 0) estColor else textColor
                                )
                                if (hasDelay) {
                                    Text(
                                        text = "Prog. $displaySched",
                                        fontSize = 10.sp,
                                        color = subtextColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Intermediate and destination station rows (Metro style)
                if (remainingStops.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                    ) {
                        remainingStops.forEachIndexed { index, stop ->
                            val isDest = (index == remainingStops.size - 1)
                            val stopSchedTime = remember(departure.tripId, stop.id, stop.name) {
                                CercaniasRouteUtils.getStationScheduledTime(departure.tripId, stop.id, stop.name)
                            }
                            val stopEstTime = remember(stopSchedTime, departure.delayMinutes) {
                                if (stopSchedTime != null) {
                                    CercaniasRouteUtils.addMinutesToTime(stopSchedTime, departure.delayMinutes)
                                } else null
                            }

                            CercaniasTimelineRow(
                                stationName = stop.name,
                                transferLines = stop.transferLines,
                                currentLineId = departure.routeId,
                                lineColor = routeColor,
                                isDestination = isDest,
                                isFirst = false,
                                isLast = isDest,
                                scheduledTime = stopSchedTime,
                                estimatedTime = stopEstTime,
                                delayMinutes = departure.delayMinutes,
                                isLive = departure.isLive,
                                isCanceled = departure.isCanceled,
                                appLanguage = appLanguage,
                                textColor = textColor,
                                subtextColor = subtextColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CercaniasTimelineRow(
    stationName: String,
    transferLines: List<String>,
    currentLineId: String,
    lineColor: Color,
    isDestination: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    scheduledTime: String? = null,
    estimatedTime: String? = null,
    delayMinutes: Int = 0,
    isLive: Boolean = false,
    isCanceled: Boolean = false,
    appLanguage: AppLanguage,
    textColor: Color,
    subtextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Track + Dot Node
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cX = size.width / 2f
                val cY = size.height / 2f
                val strokeWidthPx = 3.dp.toPx()

                if (!isFirst) {
                    drawLine(
                        color = lineColor,
                        start = Offset(cX, 0f),
                        end = Offset(cX, cY),
                        strokeWidth = strokeWidthPx
                    )
                }

                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(cX, cY),
                        end = Offset(cX, size.height),
                        strokeWidth = strokeWidthPx
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(if (isDestination) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        width = if (isDestination) 3.dp else 2.5.dp,
                        color = lineColor,
                        shape = CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Station Info Content
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stationName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isDestination) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = textColor
                    )
                    if (isDestination) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = lineColor
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_destination_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (transferLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        transferLines.forEach { tLineId ->
                            val tColor = getCercaniasLineBadgeColor(tLineId)
                            val tText = formatCercaniasLineBadgeText(tLineId)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = tColor
                            ) {
                                Text(
                                    text = tText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Arrival Time Column
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (isCanceled) {
                    Text(
                        text = scheduledTime ?: "--:--",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935),
                        style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_cancelled_status),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                } else if (isLive || delayMinutes != 0) {
                    val displayEst = estimatedTime ?: scheduledTime ?: "--:--"
                    val displaySched = scheduledTime ?: ""
                    val hasDelay = delayMinutes != 0 && displaySched.isNotBlank() && displaySched != displayEst

                    val estColor = when {
                        delayMinutes < 0 -> Color(0xFF0284C7)
                        delayMinutes in 0..3 -> Color(0xFF2ECC71)
                        delayMinutes in 4..5 -> Color(0xFFF97316)
                        else -> Color(0xFFE53935)
                    }

                    Text(
                        text = displayEst,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = estColor
                    )
                    if (hasDelay) {
                        Text(
                            text = "Prog. $displaySched",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = subtextColor
                        )
                    }
                } else if (!scheduledTime.isNullOrBlank()) {
                    Text(
                        text = scheduledTime,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun getCercaniasLineBadgeColor(routeId: String): Color {
    val clean = routeId.uppercase().replace("-", "").trim()
    return when (clean) {
        "C1" -> Color(0xFF00A3E0)
        "C2" -> Color(0xFFFF6A00)
        "C3" -> Color(0xFF7A287B)
        "C4" -> Color(0xFFE52321)
        "C5" -> Color(0xFF009639)
        "C6" -> Color(0xFF002F6C)
        else -> Color(0xFF666666)
    }
}

private fun formatCercaniasLineBadgeText(routeId: String): String {
    val clean = routeId.uppercase().replace("-", "").trim()
    return if (clean.startsWith("C") && clean.length > 1) {
        "C-${clean.substring(1)}"
    } else {
        clean
    }
}
