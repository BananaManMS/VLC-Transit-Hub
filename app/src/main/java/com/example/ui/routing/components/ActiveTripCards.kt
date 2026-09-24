package com.example.ui.routing.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.ui.dashboard.AppLanguage
import com.example.util.RealTimeTripStatus
import kotlinx.coroutines.delay

/**
 * Interactive Onboard Confirmation Prompt Widget
 */
@Composable
fun BoardingConfirmationContent(
    candidateTransitLeg: PlannedLeg,
    candidateLegIndex: Int,
    isDark: Boolean,
    appLanguage: AppLanguage,
    onAdvanceLeg: (Int) -> Unit
) {
    val modeLabel = when (candidateTransitLeg.mode) {
        TransitMode.TRAM -> if (appLanguage == AppLanguage.ES) "Tranvía" else "Tramvia"
        TransitMode.SUBWAY -> "Metro"
        TransitMode.BUS -> "Bus"
        TransitMode.RAIL -> if (appLanguage == AppLanguage.ES) "Tren" else "Tren"
        else -> if (appLanguage == AppLanguage.ES) "transporte" else "transport"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF00A86B).copy(alpha = if (isDark) 0.25f else 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onAdvanceLeg(candidateLegIndex) }
            .testTag("active_trip_board_confirm_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (candidateTransitLeg.mode) {
                        TransitMode.TRAM -> Icons.Default.Tram
                        TransitMode.BUS -> Icons.Default.DirectionsBus
                        TransitMode.RAIL -> Icons.Default.DirectionsRailway
                        else -> Icons.Default.Subway
                    },
                    contentDescription = null,
                    tint = Color(0xFF00A86B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == AppLanguage.ES) "¿A bordo del $modeLabel?" else "A bord del $modeLabel?",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF00A86B),
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "Confirmar" else "Confirmar",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Transfer Risk Warning & Recalculate Prompt
 */
@Composable
fun TransferRiskPromptContent(
    isDark: Boolean,
    appLanguage: AppLanguage,
    realTimeStatus: RealTimeTripStatus?,
    isRecalculating: Boolean,
    secondaryTextColor: Color,
    onRecalculateTransfer: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val cardColor = Color(0xFFFF9800)
    val cardContentColor = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor.copy(alpha = if (isDark) 0.22f else 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = cardContentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == AppLanguage.ES) "Posible transbordo perdido" else "Possible transbordament perdut",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = cardContentColor,
                        fontSize = 12.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (appLanguage == AppLanguage.ES) {
                    realTimeStatus?.transferWarningEs ?: "El transporte actual lleva retraso y la conexión es inviable."
                } else {
                    realTimeStatus?.transferWarningCa ?: "El transport actual porta retard i la connexió és inviable."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "No, mantener" else "No, mantindre",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = secondaryTextColor,
                            fontSize = 11.sp
                        )
                    )
                }
                Button(
                    onClick = { onRecalculateTransfer?.invoke() },
                    enabled = !isRecalculating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    if (isRecalculating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Recalculando..." else "Recalculant...",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Sí, recalcular" else "Sí, recalcular",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recalculation Error Banner
 */
@Composable
fun RecalculateErrorBanner(
    error: String,
    isDark: Boolean,
    onDismiss: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFD32F2F).copy(alpha = if (isDark) 0.25f else 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                    fontSize = 11.sp
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onDismiss?.invoke() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Arrival and Trip Completed Banner
 */
@Composable
fun ArrivalCompletedContent(
    destinationName: String,
    isDark: Boolean,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit
) {
    val totalSeconds = 45
    var progress by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        val stepMs = 250L
        val totalMs = totalSeconds * 1000L
        var elapsedMs = 0L
        while (elapsedMs < totalMs) {
            delay(stepMs)
            elapsedMs += stepMs
            progress = (1f - (elapsedMs.toFloat() / totalMs)).coerceIn(0f, 1f)
        }
        onDismiss()
    }

    val isEs = appLanguage == AppLanguage.ES
    val titleText = if (isEs) "¡Has llegado a tu destino!" else "¡Has arribat al teu destí!"
    val subtitleText = if (isEs) "Viaje completado · $destinationName" else "Viatge completat · $destinationName"
    val doneButtonText = if (isEs) "Listo" else "Fet"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2E7D32).copy(alpha = if (isDark) 0.35f else 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20),
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDark) Color(0xFFC8E6C9) else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF2E7D32) else Color(0xFF388E3C),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = doneButtonText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Subtle countdown progress indicator
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (isDark) Color(0xFF81C784).copy(alpha = 0.7f) else Color(0xFF4CAF50).copy(alpha = 0.7f),
            trackColor = if (isDark) Color(0xFF1B3828) else Color(0xFFC8E6C9).copy(alpha = 0.4f)
        )
    }
}
