package com.example.ui.bus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.components.ValenbisiStation

// Official Valenbisi Dark Blue Branding Color
val ValenbisiDarkBlue = Color(0xFF1B365D)

@Composable
fun ValenbisiStationCard(
    station: ValenbisiStation,
    alias: String?,
    isFavorite: Boolean,
    appLanguage: AppLanguage,
    isDarkMode: Boolean = false,
    onToggleFavorite: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditAlias: (() -> Unit)? = null
) {
    val cardBg = if (isFavorite) {
        if (isDarkMode) Color(0xFF111E2E) else Color(0xFFEEF3F8)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val cardBorder = if (isFavorite) {
        BorderStroke(1.dp, if (isDarkMode) Color(0xFF4A90E2).copy(alpha = 0.5f) else ValenbisiDarkBlue.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, if (isDarkMode) Color(0xFF2E3545) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onCardClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("valenbisi_station_card_${station.number}"),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title on left, Favorite Star on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    val displayName = alias.takeIf { !it.isNullOrBlank() } ?: station.name
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cardTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = cardTextSecondaryColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isDarkMode) Color(0xFF0F131E) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .testTag("valenbisi_fav_btn_${station.number}")
                            .fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) Color(0xFFFFB300) else cardTextSecondaryColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                color = if (isDarkMode) Color(0x1F8791A6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Compact bottom chips: Bikes available & Free docks, and distance on the bottom right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bikes chip
                val hasBikes = station.available > 0
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasBikes) {
                        if (isDarkMode) Color(0xFF142B1F) else Color(0xFFE8F5E9)
                    } else {
                        if (isDarkMode) Color(0xFF2E1515) else Color(0xFFFFEBEE)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBike,
                            contentDescription = null,
                            tint = if (hasBikes) {
                                if (isDarkMode) Color(0xFF81C784) else Color(0xFF2E7D32)
                            } else {
                                if (isDarkMode) Color(0xFFE57373) else Color(0xFFC62828)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.valenbisi_bikes_count_format, station.available),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hasBikes) {
                                if (isDarkMode) Color(0xFF81C784) else Color(0xFF1B5E20)
                            } else {
                                if (isDarkMode) Color(0xFFE57373) else Color(0xFFB71C1C)
                            }
                        )
                    }
                }

                // Free docks chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkMode) Color(0xFF1A2230) else Color(0xFFF1F5F9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalParking,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.valenbisi_docks_free_suffix_format, station.free),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF334155)
                        )
                    }
                }

                // Station status if closed
                if (!station.open) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDarkMode) Color(0xFF2E1515) else Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.status_out_of_service),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFFE57373) else Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Distance indicator on the bottom right
                if (station.distanceText.isNotBlank()) {
                    Text(
                        text = station.distanceText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cardTextSecondaryColor
                    )
                }
            }
        }
    }
}
