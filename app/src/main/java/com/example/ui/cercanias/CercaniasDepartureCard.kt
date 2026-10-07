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
private val TIME_REGEX = Regex("""\b(\d{1,2})[:.](\d{2})\s*h?\b""")
private val alertTimesCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

@Composable
fun CercaniasDepartureCard(
    departure: CercaniasDeparture,
    alerts: List<CercaniasAlert>,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onClick: () -> Unit
) {
    val normalizedRoute = departure.normalizedRoute.ifBlank {
        departure.routeId.uppercase().replace("-", "").trim()
    }
    val routeColor = when (normalizedRoute) {
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
                val timesInText = alertTimesCache.getOrPut(alert.id) {
                    val text = "${alert.headerEs} ${alert.descriptionEs}".lowercase()
                    TIME_REGEX.findAll(text).map { it.groupValues[1].padStart(2, '0') + ":" + it.groupValues[2] }.toList()
                }
                if (timesInText.isNotEmpty()) {
                    val depTime = departure.departureTime.take(5)
                    timesInText.contains(depTime)
                } else {
                    true
                }
            }
        }
    }
    val totalAvisos = affectedAlerts.size
    val hasAviso = totalAvisos > 0
    val singleNotice = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_notice_single)
    val noNotices = androidx.compose.ui.res.stringResource(com.example.R.string.no_avisos)
    val avisoText = if (totalAvisos > 0) {
        if (totalAvisos == 1) singleNotice else androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_notice_plural, totalAvisos)
    } else {
        noNotices
    }
    val avisoColor = if (hasAviso) Color(0xFFE53935) else if (isDarkMode) Color(0xFF8791A6) else Color.Gray

    UnifiedAppCard(
        onClick = onClick,
        startContent = {
            com.example.ui.metro.CercaniasLineBadge(
                routeId = departure.routeId,
                modifier = Modifier.fillMaxSize()
            )
        },
        centerContent = {
            Column {
                val destinationText = departure.formattedDestination.ifBlank {
                    com.example.data.mapper.CercaniasDepartureMapper.formatStationDisplayName(
                        departure.destination
                            .replace("dirección", "", ignoreCase = true)
                            .replace("direccion", "", ignoreCase = true)
                            .trim()
                    )
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
                    val canceledLabel = androidx.compose.ui.res.stringResource(com.example.R.string.validation_entry).let { "CANCELADO" } // or custom
                    val (statusLabel, statusColor) = when {
                        departure.isCanceled -> Pair(androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_canceled_service).take(10).uppercase(), Color(0xFFB91C1C))
                        departure.isSkippedAtStop -> Pair(androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_status_no_service), Color(0xFFB91C1C))
                        departure.isLive -> {
                            val delay = departure.delayMinutes
                            when {
                                delay < 0 -> Pair("Adelantado ${delay} min", Color(0xFF0284C7))
                                delay in 0..3 -> Pair(if (delay == 0) androidx.compose.ui.res.stringResource(com.example.R.string.status_normal) else "+${delay} min", Color(0xFF2ECC71))
                                delay in 4..5 -> Pair("+$delay min", Color(0xFFF97316))
                                else -> Pair("+$delay min", Color(0xFFE53935))
                            }
                        }
                        else -> Pair(androidx.compose.ui.res.stringResource(com.example.R.string.metro_status_programado), if (isDarkMode) Color(0xFF8791A6) else Color.Gray)
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
            val isNow = departure.minutesRemaining in -1..1 || (departure.isStoppedAt && departure.minutesRemaining <= 1)
            val exceeds60 = departure.minutesRemaining > 60

            val bigTextStr = if (departure.isCanceled) {
                "CANCELADO"
            } else if (isNow) {
                androidx.compose.ui.res.stringResource(com.example.R.string.immediate_value)
            } else if (departure.isRecoveredStopped) {
                "Detenido"
            } else if (departure.isTomorrow) {
                departure.departureTime
            } else if (exceeds60) {
                departure.departureTime
            } else {
                "${departure.minutesRemaining} min"
            }

            val bigTextColor = when {
                departure.isCanceled || departure.isSkippedAtStop -> Color(0xFFB91C1C)
                departure.isRecoveredStopped -> Color(0xFFF97316)
                departure.isLive -> {
                    val delay = departure.delayMinutes
                    when {
                        delay <= 3 -> Color(0xFF2ECC71)
                        delay in 4..5 -> Color(0xFFF97316)
                        else -> Color(0xFFE53935)
                    }
                }
                departure.isStoppedAt -> Color(0xFF2ECC71)
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
                        com.example.ui.components.LiveRssFeedIcon(
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.live_indicator_desc),
                            tint = bigTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (departure.platform.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_platform_format, departure.platform),
                        style = MaterialTheme.typography.bodySmall,
                        color = subtextColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
