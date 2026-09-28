package com.example.ui.cercanias

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.LiveTimerStyle

/**
 * Compact Glanceable Header Card for Cercanías Departure Details.
 * Displays:
 * [Big Departure Time + Remaining Min] | [Platform Chip + Status Badge]
 */
@Composable
fun CercaniasDepartureStatusHeader(
    departure: CercaniasDeparture,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    destinationText: String,
    modifier: Modifier = Modifier
) {
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF64748B)
    val cardBg = if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)

    val delay = departure.delayMinutes
    val isCanceled = departure.isCanceled
    val hasDelay = departure.isLive && delay != 0
    val estTimeStr = if (departure.estimatedTime.isNotBlank()) departure.estimatedTime else departure.departureTime
    val scheduledTimeStr = departure.departureTime.ifBlank { "--:--" }

    val statusColor = when {
        isCanceled -> Color(0xFFDC2626)
        !departure.isLive -> subtextColor
        delay <= 3 -> Color(0xFF16A34A)
        delay in 4..5 -> Color(0xFFEA580C)
        else -> Color(0xFFDC2626)
    }

    val statusBgColor = when {
        isCanceled -> Color(0xFFDC2626).copy(alpha = if (isDarkMode) 0.2f else 0.12f)
        !departure.isLive -> (if (isDarkMode) Color(0xFF333333) else Color(0xFFE2E8F0))
        delay <= 3 -> Color(0xFF16A34A).copy(alpha = if (isDarkMode) 0.2f else 0.12f)
        delay in 4..5 -> Color(0xFFEA580C).copy(alpha = if (isDarkMode) 0.2f else 0.12f)
        else -> Color(0xFFDC2626).copy(alpha = if (isDarkMode) 0.2f else 0.12f)
    }

    val statusLabel = when {
        isCanceled -> if (appLanguage == AppLanguage.CA) "CANCEL·LAT" else "CANCELADO"
        departure.isSkippedAtStop -> if (appLanguage == AppLanguage.CA) "Sense servei" else "Sin servicio"
        !departure.isLive -> if (appLanguage == AppLanguage.CA) "Programat" else "Programado"
        delay < 0 -> "$delay min"
        delay == 0 -> "En hora"
        else -> "+$delay min"
    }

    val remainingLabel = when {
        isCanceled -> if (appLanguage == AppLanguage.CA) "Servei cancel·lat" else "Servicio cancelado"
        departure.isStoppedAt || departure.minutesRemaining in -1..1 -> if (appLanguage == AppLanguage.CA) "Immediat" else "Inmediato"
        departure.minutesRemaining > 60 -> if (appLanguage == AppLanguage.CA) "Més d'1 hora" else "Más de 1 hora"
        departure.minutesRemaining > 1 -> "En ${departure.minutesRemaining} min"
        else -> ""
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Time + Remaining minutes subtitle
            Column {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCanceled) {
                        Text(
                            text = scheduledTimeStr,
                            style = LiveTimerStyle,
                            fontSize = 28.sp,
                            color = Color(0xFFDC2626),
                            textDecoration = TextDecoration.LineThrough
                        )
                    } else if (hasDelay) {
                        Text(
                            text = estTimeStr,
                            style = LiveTimerStyle,
                            fontSize = 28.sp,
                            color = statusColor
                        )
                        Text(
                            text = scheduledTimeStr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = subtextColor,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    } else {
                        Text(
                            text = scheduledTimeStr,
                            style = LiveTimerStyle,
                            fontSize = 28.sp,
                            color = textColor
                        )
                    }
                }

                if (remainingLabel.isNotBlank()) {
                    Text(
                        text = remainingLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isCanceled) Color(0xFFDC2626) else subtextColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Right: Chips for Platform and Status
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (departure.platform.isNotBlank()) {
                    val platformText = departure.platform.let {
                        if (it.startsWith("Vía", ignoreCase = true) || it.startsWith("Via", ignoreCase = true)) it
                        else "Vía $it"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFF1F5F9))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = platformText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBgColor)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}
