package com.example.ui.cercanias

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.LiveTimerStyle
import com.example.ui.theme.UnifiedAppCard

/**
 * Tarjeta de salidas de Cercanías adaptada estrictamente a UnifiedAppCard.
 */
@Composable
fun CercaniasDepartureCard(
    departure: CercaniasDeparture,
    alerts: List<CercaniasAlert>,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onClick: () -> Unit
) {
    val routeColor = when(departure.routeId) {
        "C1" -> Color(0xFF00A3E0)
        "C2" -> Color(0xFFFF6A00)
        "C3" -> Color(0xFF7A287B)
        "C4" -> Color(0xFFE52321)
        "C5" -> Color(0xFF009639)
        "C6" -> Color(0xFF002F6C)
        else -> Color.Gray
    }
    
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)

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
    val totalAvisos = affectedAlerts.size
    val hasAviso = totalAvisos > 0
    val avisoText = if (totalAvisos > 0) {
        if (appLanguage == AppLanguage.CA) {
            if (totalAvisos == 1) "1 avís" else "$totalAvisos avisos"
        } else {
            if (totalAvisos == 1) "1 aviso" else "$totalAvisos avisos"
        }
    } else {
        if (appLanguage == AppLanguage.CA) "Sense avisos" else "Sin avisos"
    }
    val avisoColor = if (hasAviso) Color(0xFFE53935) else if (isDarkMode) Color(0xFF8791A6) else Color.Gray

    UnifiedAppCard(
        onClick = onClick,
        startContent = {
            val routeText = if (departure.routeId.matches(Regex("C\\d"))) "C-${departure.routeId.substring(1)}" else departure.routeId
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(routeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = routeText,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        centerContent = {
            Column {
                val destinationText = remember(departure.destination) {
                    departure.destination
                        .replace("dirección", "", ignoreCase = true)
                        .replace("direccion", "", ignoreCase = true)
                        .trim()
                }
                Text(
                    text = destinationText,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val (statusLabel, statusColor) = when {
                        departure.isCanceled -> Pair(if (appLanguage == AppLanguage.CA) "CANCEL·LAT" else "CANCELADO", Color(0xFFB91C1C))
                        departure.isSkippedAtStop -> Pair(if (appLanguage == AppLanguage.CA) "Sense servei" else "Sin servicio", Color(0xFFB91C1C))
                        departure.isLive -> {
                            val delay = departure.delayMinutes
                            when {
                                delay < 0 -> Pair(if (appLanguage == AppLanguage.CA) "Avançat ${delay} min" else "Adelantado ${delay} min", Color(0xFF0284C7))
                                delay in 0..3 -> Pair(if (delay == 0) (if (appLanguage == AppLanguage.CA) "En hora" else "En hora") else "+${delay} min", Color(0xFF2ECC71))
                                delay in 4..5 -> Pair("+$delay min", Color(0xFFF97316))
                                else -> Pair("+$delay min", Color(0xFFE53935))
                            }
                        }
                        else -> Pair(if (appLanguage == AppLanguage.CA) "Programat" else "Programado", if (isDarkMode) Color(0xFF8791A6) else Color.Gray)
                    }

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (departure.isLive || departure.isCanceled) FontWeight.Bold else FontWeight.Normal,
                        color = statusColor
                    )

                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDarkMode) Color(0xFF8791A6).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = avisoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = avisoColor,
                        fontWeight = if (hasAviso) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        },
        endContent = {
            val isNow = departure.minutesRemaining <= 0
            val exceeds60 = departure.minutesRemaining > 60

            val isStopped = departure.isStoppedAt
            val infiniteTransition = rememberInfiniteTransition(label = "stopped_pulse")

            val textSwitchPhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "text_switch"
            )

            val bigTextStr = if (departure.isCanceled) {
                if (appLanguage == AppLanguage.CA) "CANCEL·LAT" else "CANCELADO"
            } else if (isStopped) {
                if (textSwitchPhase < 1.0f) {
                    if (appLanguage == AppLanguage.CA) "Aturat" else "Parado"
                } else {
                    if (appLanguage == AppLanguage.CA) "Immediat" else "Inmediato"
                }
            } else if (departure.isRecoveredStopped) {
                if (appLanguage == AppLanguage.CA) "Aturat" else "Detenido"
            } else if (exceeds60) {
                departure.departureTime
            } else {
                if (isNow) (if (appLanguage == AppLanguage.CA) "Immediat" else "Inmediato") else "${departure.minutesRemaining} min"
            }

            val bigTextColor = when {
                departure.isCanceled || departure.isSkippedAtStop -> Color(0xFFB91C1C)
                isStopped -> Color(0xFFF97316)
                departure.isRecoveredStopped -> Color(0xFFF97316)
                departure.isLive -> {
                    val delay = departure.delayMinutes
                    when {
                        delay < 0 -> if (isDarkMode) Color.White else Color.Black
                        delay in 0..3 -> Color(0xFF2ECC71)
                        delay in 4..5 -> Color(0xFFF97316)
                        else -> Color(0xFFE53935)
                    }
                }
                else -> if (isDarkMode) Color.White else Color.Black
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = bigTextStr,
                        style = LiveTimerStyle,
                        color = bigTextColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (departure.isLive && !departure.isCanceled) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = if (appLanguage == AppLanguage.CA) "En Directe" else "En Vivo",
                            tint = bigTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (departure.platform.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Via ${departure.platform}" else "Vía ${departure.platform}",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtextColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
