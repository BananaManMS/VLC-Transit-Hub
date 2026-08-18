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
import androidx.compose.material.icons.filled.CheckCircle
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
    val cardBg = if (isDarkMode) Color(0xFF1B2234) else Color(0xFFF0F4F8)

    val affectedAlerts = remember(alerts, departure) {
        alerts.filter { alert ->
            if (alert.isAccessibility) return@filter false
            val matchesRoute = alert.routeIds.any { rId ->
                rId.equals(departure.routeId, ignoreCase = true) || 
                rId.replace("-", "").equals(departure.routeId.replace("-", ""), ignoreCase = true)
            }
            val matchesTrip = alert.tripIds.any { tId ->
                tId.equals(departure.tripId, ignoreCase = true) || 
                (departure.tripId.isNotBlank() && tId.contains(departure.tripId)) || 
                (tId.isNotBlank() && departure.tripId.contains(tId))
            }
            matchesRoute || matchesTrip
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
        departure.destination
            .replace("dirección", "", ignoreCase = true)
            .replace("direccion", "", ignoreCase = true)
            .trim()
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(routeColor)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = routeText,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }

            Text(
                text = if (appLanguage == AppLanguage.CA) "Destí: $destinationText" else "Destino: $destinationText",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // --- FIRST CARD: HORAS Y RETRASO ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Hora programada:" else "Hora programada:",
                        fontSize = 15.sp,
                        color = subtextColor
                    )
                    Text(
                        text = departure.departureTime.ifBlank { "--:--" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                if (departure.isLive || departure.isCanceled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Hora estimada:" else "Hora estimada:",
                            fontSize = 15.sp,
                            color = subtextColor
                        )
                        val estTimeStr = if (departure.estimatedTime.isNotBlank()) departure.estimatedTime else departure.departureTime
                        val delay = departure.delayMinutes
                        val estColor = when {
                            departure.isCanceled -> Color(0xFFB91C1C)
                            delay < 0 -> Color(0xFF0284C7)
                            delay in 0..3 -> Color(0xFF2ECC71)
                            delay in 4..5 -> Color(0xFFF97316)
                            else -> Color(0xFFE53935)
                        }
                        Text(
                            text = if (departure.isCanceled) (if (appLanguage == AppLanguage.CA) "CANCEL·LAT" else "CANCELADO") else estTimeStr.ifBlank { "--:--" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = estColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Retard en temps real:" else "Retraso en tiempo real:",
                            fontSize = 15.sp,
                            color = subtextColor
                        )

                        val delay = departure.delayMinutes
                        val (delayText, delayColor) = when {
                            departure.isCanceled -> Pair(if (appLanguage == AppLanguage.CA) "CANCEL·LAT" else "CANCELADO", Color(0xFFB91C1C))
                            departure.isSkippedAtStop -> Pair(if (appLanguage == AppLanguage.CA) "Sense servei" else "Sin servicio", Color(0xFFB91C1C))
                            delay < 0 -> Pair("$delay min", Color(0xFF0284C7))
                            delay in 0..3 -> Pair(if (delay == 0) (if (appLanguage == AppLanguage.CA) "En hora" else "En hora") else "+$delay min", Color(0xFF2ECC71))
                            delay in 4..5 -> Pair("+$delay min", Color(0xFFF97316))
                            else -> Pair("+$delay min", Color(0xFFE53935))
                        }

                        Text(
                            text = delayText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = delayColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SECOND CARD: UBICACIÓN DEL TREN (SOLO TRENES EN VIVO) ---
        if (departure.isLive && !departure.isCanceled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val rawLoc = departure.locationText.ifBlank { "En trayecto hacia $destinationText" }
                    val locText = if (appLanguage == AppLanguage.CA) {
                        when {
                            rawLoc == "No ha iniciado el trayecto" -> "No ha iniciat el trajecte"
                            rawLoc.startsWith("En trayecto hacia ") -> {
                                val dest = rawLoc.substringAfter("En trayecto hacia ")
                                "En trajecte cap a $dest"
                            }
                            rawLoc.startsWith("Llegando a ") -> {
                                val stationName = rawLoc.substringAfter("Llegando a ")
                                "Arribant a $stationName"
                            }
                            rawLoc.startsWith("Parado en ") -> {
                                val stationName = rawLoc.substringAfter("Parado en ")
                                "Aturat a $stationName"
                            }
                            rawLoc.startsWith("Última info: ") -> {
                                val sub = rawLoc.substringAfter("Última info: ")
                                val translatedSub = when {
                                    sub.startsWith("En trayecto hacia ") -> "En trajecte cap a " + sub.substringAfter("En trayecto hacia ")
                                    sub.startsWith("Llegando a ") -> "Arribant a " + sub.substringAfter("Llegando a ")
                                    sub.startsWith("Parado en ") -> "Aturat a " + sub.substringAfter("Parado en ")
                                    else -> sub
                                }
                                "Última info: $translatedSub"
                            }
                            rawLoc.startsWith("Última info (hace ") -> {
                                val minutesPart = rawLoc.substringAfter("Última info (hace ").substringBefore("):").trim()
                                val sub = rawLoc.substringAfter("): ").trim()
                                val translatedSub = when {
                                    sub.startsWith("En trayecto hacia ") -> "En trajecte cap a " + sub.substringAfter("En trayecto hacia ")
                                    sub.startsWith("Llegando a ") -> "Arribant a " + sub.substringAfter("Llegando a ")
                                    sub.startsWith("Parado en ") -> "Aturat a " + sub.substringAfter("Parado en ")
                                    else -> sub
                                }
                                val minutesVal = minutesPart.removeSuffix(" min").trim()
                                "Última info (fa $minutesVal min): $translatedSub"
                            }
                            else -> rawLoc
                        }
                    } else {
                        rawLoc
                    }
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Ubicació: $locText" else "Ubicación: $locText",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // --- THIRD CARD: INCIDENCIAS O ESTADO ---
        val hasIncidences = departure.isCanceled || departure.isSkippedAtStop || affectedAlerts.isNotEmpty()

        if (!hasIncidences) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) Color(0xFF132219) else Color(0xFFE8F5E9)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2ECC71),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Funcionant amb normalitat - Línia operant sense incidències." else "Funcionando con normalidad - Línea operando sin incidencias.",
                        color = if (isDarkMode) Color(0xFFC8E6C9) else Color(0xFF1B5E20),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (departure.isCanceled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
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
                                text = if (appLanguage == AppLanguage.CA) "Servei cancel·lat - Aquest tren ha sigut suprimit." else "Servicio cancelado - Este tren ha sido suprimido.",
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
                        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
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
                                val prefix = if (appLanguage == AppLanguage.CA) {
                                    if (alert.tripIds.isNotEmpty()) "Avís Tren ${departure.tripId}: " else "Avís Línia ${departure.routeId}: "
                                } else {
                                    if (alert.tripIds.isNotEmpty()) "Aviso Tren ${departure.tripId}: " else "Aviso Línea ${departure.routeId}: "
                                }
                                Text(
                                    text = prefix + alert.headerEs.ifBlank { if (appLanguage == AppLanguage.CA) "Incidència activa" else "Incidencia activa" },
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
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    text = if (appLanguage == AppLanguage.CA) "RECORREGUT I PRÒXIMES ESTACIONS" else "RECORRIDO Y PRÓXIMAS ESTACIONES",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = subtextColor.copy(alpha = 0.8f)
                )

                // Current station highlight card (Metro style)
                val currentStationName = originInfo?.name ?: originStationName.ifBlank {
                    if (appLanguage == AppLanguage.CA) "Estació actual" else "Estación actual"
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
                                text = if (appLanguage == AppLanguage.CA) "Estació actual" else "Estación actual",
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
                                    text = if (appLanguage == AppLanguage.CA) "Cancel·lat" else "Cancelado",
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
                                text = if (appLanguage == AppLanguage.CA) "Destí" else "Destino",
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
                        text = if (appLanguage == AppLanguage.CA) "Cancel·lat" else "Cancelado",
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
