package com.example.ui.metro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import com.example.ui.dashboard.TransitTripUiModel
import com.example.ui.theme.appCardBorder

@Composable
fun CardDetailTripHistory(
    viajesList: List<TransitTripUiModel>,
    appLanguage: AppLanguage,
    texts: Translation
) {
    Text(
        text = texts.historyLabel,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface
    )

    if (viajesList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = texts.historyNotAvailableDesc,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (viaje in viajesList) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = viaje.estacion,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (viaje.zona.isNotEmpty()) {
                                    Text(
                                        text = viaje.zona,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(
                                                color = when (viaje.zona.uppercase()) {
                                                    "A" -> Color(0xFF1976D2)
                                                    "B" -> Color(0xFF388E3C)
                                                    "C" -> Color(0xFFF57C00)
                                                    "D" -> Color(0xFFD32F2F)
                                                    else -> MaterialTheme.colorScheme.secondary
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = viaje.fecha,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            if (viaje.tipoValidacion.isNotEmpty()) {
                                val tipoLower = viaje.tipoValidacion.lowercase()
                                val badgeBg = when {
                                    tipoLower.contains("entrada") -> Color(0xFFE8F5E9)
                                    tipoLower.contains("salida") -> Color(0xFFEEEEEE)
                                    tipoLower.contains("transbordo") -> Color(0xFFE3F2FD)
                                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                }
                                val badgeText = when {
                                    tipoLower.contains("entrada") -> Color(0xFF2E7D32)
                                    tipoLower.contains("salida") -> Color(0xFF616161)
                                    tipoLower.contains("transbordo") -> Color(0xFF1565C0)
                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                }
                                val badgeLabel = when {
                                    tipoLower.contains("entrada") -> if (appLanguage == AppLanguage.CA) "Entrada" else "Entrada"
                                    tipoLower.contains("salida") -> if (appLanguage == AppLanguage.CA) "Eixida" else "Salida"
                                    tipoLower.contains("transbordo") -> if (appLanguage == AppLanguage.CA) "Transbord" else "Transbordo"
                                    else -> viaje.tipoValidacion
                                }
                                Text(
                                    text = badgeLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeText,
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
