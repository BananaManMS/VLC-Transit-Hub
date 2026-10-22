package com.example.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.ui.dashboard.AppLanguage

@Composable
fun MetroStationHeader(
    station: MetroStation,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean,
    onDirectionsClick: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToMetro: ((String) -> Unit)?,
    headerDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val titleColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF64748B)
    val iconTint = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF475569)
    val metroColor = Color(0xFFED1C24)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(headerDragModifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Operator Badge + Zone Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = metroColor,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Metrovalencia",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val displayZone = com.example.data.model.cleanZoneCode(station.zone)
                    if (displayZone.isNotBlank()) {
                        Text(
                            text = "Zona $displayZone",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = subtextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Large Station Title
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Top-right action icons (Directions, Favorite, Fullscreen, Close)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onDirectionsClick != null) {
                    IconButton(
                        onClick = onDirectionsClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("sheet_directions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Com arribar" else "Cómo llegar",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("sheet_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) Color(0xFFFFB300) else iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (onNavigateToMetro != null) {
                    IconButton(
                        onClick = { onNavigateToMetro(station.id) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("sheet_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Pantalla completa" else "Pantalla completa",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("sheet_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Line Badges Row
        if (station.lines.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                station.lines.forEach { lineId ->
                    com.example.ui.metro.MetroLineBadge(
                        lineId = lineId,
                        size = 22.dp
                    )
                }
            }
        }
    }
}
