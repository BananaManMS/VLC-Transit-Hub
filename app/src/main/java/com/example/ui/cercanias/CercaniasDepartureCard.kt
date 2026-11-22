package com.example.ui.cercanias

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.AppThemeColors
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
    val routeColor = com.example.ui.metro.TransitLogoUtils.getCercaniasLineColor(normalizedRoute)
    
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant

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
    val avisoColor = if (hasAviso) AppThemeColors.statusError(isDarkMode) else subtextColor
    val cardBgColor = routeColor
        .copy(alpha = if (isDarkMode) 0.14f else 0.07f)
        .compositeOver(MaterialTheme.colorScheme.surface)
    val cardBorder = BorderStroke(
        width = 1.dp,
        color = if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    UnifiedAppCard(
        onClick = onClick,
        border = cardBorder,
        containerColor = cardBgColor,
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
                    fontWeight = FontWeight.Bold,
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
                        departure.isCanceled -> Pair(androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_canceled_service).take(10).uppercase(), AppThemeColors.statusError(isDarkMode))
                        departure.isSkippedAtStop -> Pair(androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_status_no_service), AppThemeColors.statusError(isDarkMode))
                        departure.isLive -> {
                            val delay = departure.delayMinutes
                            when {
                                delay < 0 -> Pair("Adelantado ${delay} min", AppThemeColors.statusAccessibility(isDarkMode))
                                delay in 0..3 -> Pair(if (delay == 0) androidx.compose.ui.res.stringResource(com.example.R.string.status_normal) else "+${delay} min", AppThemeColors.statusOk(isDarkMode))
                                delay in 4..5 -> Pair("+$delay min", AppThemeColors.statusWarning(isDarkMode))
                                else -> Pair("+$delay min", AppThemeColors.statusError(isDarkMode))
                            }
                        }
                        else -> Pair(androidx.compose.ui.res.stringResource(com.example.R.string.metro_status_programado), subtextColor)
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
                        color = subtextColor.copy(alpha = 0.5f),
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
                departure.isCanceled || departure.isSkippedAtStop -> AppThemeColors.statusError(isDarkMode)
                departure.isRecoveredStopped -> AppThemeColors.statusWarning(isDarkMode)
                departure.isLive -> {
                    val delay = departure.delayMinutes
                    when {
                        delay <= 3 -> AppThemeColors.statusOk(isDarkMode)
                        delay in 4..5 -> AppThemeColors.statusWarning(isDarkMode)
                        else -> AppThemeColors.statusError(isDarkMode)
                    }
                }
                departure.isStoppedAt -> AppThemeColors.statusOk(isDarkMode)
                else -> textColor
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
