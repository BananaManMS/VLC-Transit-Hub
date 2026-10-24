package com.example.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.appCardBorder

data class TransitModeOption(
    val code: String,
    val labelEs: String,
    val labelCa: String,
    val icon: ImageVector,
    val brandColor: Color
)

private val ALL_TRANSIT_MODES = listOf(
    TransitModeOption(
        code = "METRO",
        labelEs = "Metrovalencia",
        labelCa = "Metrovalencia",
        icon = Icons.Default.Subway,
        brandColor = Color(0xFFE53935)
    ),
    TransitModeOption(
        code = "EMT",
        labelEs = "EMT Valencia",
        labelCa = "EMT València",
        icon = Icons.Default.DirectionsBus,
        brandColor = Color(0xFFD32F2F)
    ),
    TransitModeOption(
        code = "CERCANIAS",
        labelEs = "Cercanías Renfe",
        labelCa = "Rodalies Renfe",
        icon = Icons.Default.DirectionsRailway,
        brandColor = Color(0xFF702B7B)
    ),
    TransitModeOption(
        code = "VALENBISI",
        labelEs = "Valenbisi",
        labelCa = "Valenbisi",
        icon = Icons.Default.DirectionsBike,
        brandColor = Color(0xFF00897B)
    ),
    TransitModeOption(
        code = "METROBUS",
        labelEs = "Metrobús",
        labelCa = "Metrobús",
        icon = Icons.Default.DirectionsBus,
        brandColor = Color(0xFFF57C00)
    )
)

@Composable
fun PreferredTransitModesCard(
    preferredModes: Set<String>,
    onToggleMode: (String) -> Unit,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCa = appLanguage == AppLanguage.CA
    val cardBg = if (isDarkMode) Color(0xFF1E222B) else MaterialTheme.colorScheme.surface
    val dividerColor = if (isDarkMode) Color(0xFF282F3E) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    val activeCount = preferredModes.size
    val totalCount = ALL_TRANSIT_MODES.size

    fun handleToggle(code: String) {
        val isCurrentlySelected = preferredModes.contains(code)
        if (isCurrentlySelected && activeCount <= 1) {
            val warningMsg = if (isCa) {
                "Has de mantindre almenys un mitjà de transport actiu"
            } else {
                "Debes mantener al menos un medio de transporte activo"
            }
            Toast.makeText(context, warningMsg, Toast.LENGTH_SHORT).show()
            return
        }
        onToggleMode(code)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preferred_transit_modes_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkMode) 0.dp else 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCa) "Mitjans de transport actius" else "Medios de transporte activos",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (isCa) "Filtra les eixides d'Inici i les capes del mapa" else "Filtra las salidas de Inicio y las capas del mapa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Active counter badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkMode) Color(0xFF282F3E) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$activeCount/$totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.8.dp,
                color = dividerColor
            )

            // Compact, Indented List of Transit Modes (Hierarchy established by start padding)
            ALL_TRANSIT_MODES.forEachIndexed { index, option ->
                val isSelected = preferredModes.contains(option.code)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { handleToggle(option.code) }
                        .padding(start = 28.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                        .testTag("chip_mode_${option.code.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Operator Icon Badge
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) option.brandColor.copy(alpha = if (isDarkMode) 0.20f else 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = if (isSelected) option.brandColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Operator Name (single line, space saving)
                    Text(
                        text = if (isCa) option.labelCa else option.labelEs,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Switch control
                    Switch(
                        checked = isSelected,
                        onCheckedChange = { handleToggle(option.code) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = option.brandColor
                        )
                    )
                }

                if (index < ALL_TRANSIT_MODES.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 70.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = dividerColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
