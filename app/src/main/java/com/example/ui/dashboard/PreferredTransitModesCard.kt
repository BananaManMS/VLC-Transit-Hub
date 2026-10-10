package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.appCardBorder

data class TransitModeOption(
    val code: String,
    val labelEs: String,
    val labelCa: String,
    val icon: ImageVector,
    val activeColor: Color
)

private val ALL_TRANSIT_MODES = listOf(
    TransitModeOption("METRO", "Metrovalencia", "Metrovalencia", Icons.Default.Subway, Color(0xFFE53935)),
    TransitModeOption("EMT", "EMT Bus", "EMT Bus", Icons.Default.DirectionsBus, Color(0xFFD32F2F)),
    TransitModeOption("CERCANIAS", "Cercanías", "Rodalies", Icons.Default.DirectionsRailway, Color(0xFF702B7B)),
    TransitModeOption("VALENBISI", "Valenbisi", "Valenbisi", Icons.Default.DirectionsBike, Color(0xFF00897B)),
    TransitModeOption("METROBUS", "Metrobús", "Metrobús", Icons.Default.DirectionsBus, Color(0xFFF57C00))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferredTransitModesCard(
    preferredModes: Set<String>,
    onToggleMode: (String) -> Unit,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val isCa = appLanguage == AppLanguage.CA
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preferred_transit_modes_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = appCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF1976D2),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCa) "Mitjans de transport actius" else "Medios de transporte activos",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isCa) "Personalitza les eixides d'Inici i les capes del mapa" else "Personaliza las salidas de Inicio y las capas del mapa",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ALL_TRANSIT_MODES.forEach { option ->
                    val isSelected = preferredModes.contains(option.code)
                    val chipBg = if (isSelected) {
                        option.activeColor.copy(alpha = if (isDarkMode) 0.22f else 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                    val chipBorder = if (isSelected) {
                        option.activeColor
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    }
                    val chipContentColor = if (isSelected) {
                        option.activeColor
                    } else {
                        subtextColor
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(10.dp))
                            .clickable { onToggleMode(option.code) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("chip_mode_${option.code.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = chipContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isCa) option.labelCa else option.labelEs,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = chipContentColor
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = chipContentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
