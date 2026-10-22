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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.components.ValenbisiStation
import com.example.ui.theme.SpaceGroteskFontFamily

// Valenbisi Brand Colors
val ValenbisiBrandColor = Color(0xFF0284C7)
val ValenbisiBrandColorDark = Color(0xFF38BDF8)
fun getValenbisiBrandColor(isDarkMode: Boolean) = if (isDarkMode) ValenbisiBrandColorDark else ValenbisiBrandColor

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
    val brandColor = getValenbisiBrandColor(isDarkMode)

    val cardBg = if (isFavorite) {
        if (isDarkMode) Color(0xFF111E2E) else Color(0xFFEEF3F8)
    } else {
        if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface
    }

    val cardBorder = if (isFavorite) {
        BorderStroke(1.dp, brandColor.copy(alpha = if (isDarkMode) 0.65f else 0.45f))
    } else {
        BorderStroke(1.dp, if (isDarkMode) Color(0xFF262C3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
    val cleanDist = if (station.distanceText.isNotEmpty()) station.distanceText.removePrefix("a ").trim() else ""

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
                .padding(14.dp)
        ) {
            // Header Row: Station Name (+ optional Alias pill) + Favorite Star toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayName = alias.takeIf { !it.isNullOrBlank() } ?: station.name
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cardTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!alias.isNullOrBlank()) {
                        Surface(
                            color = if (isDarkMode) Color(0xFF16253B) else Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, brandColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = station.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = brandColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("valenbisi_fav_btn_${station.number}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) Color(0xFFFFB300) else cardTextSecondaryColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Subtitle: Address and distance
            val subtitleText = buildString {
                if (station.address.isNotBlank()) append(station.address)
                if (cleanDist.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append("a $cleanDist")
                }
            }
            if (subtitleText.isNotEmpty()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = cardTextSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Info Row: Balanced two-column module for Bikes & Free Docks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bikes Block
                val hasBikes = station.available > 0
                val bikeColor = if (hasBikes) (if (isDarkMode) Color(0xFF81C784) else Color(0xFF2E7D32)) else (if (isDarkMode) Color(0xFFE57373) else Color(0xFFC62828))
                val bikeBg = if (hasBikes) (if (isDarkMode) Color(0xFF142B1F) else Color(0xFFE8F5E9)) else (if (isDarkMode) Color(0xFF2E1515) else Color(0xFFFFEBEE))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bikeBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBike,
                                contentDescription = null,
                                tint = bikeColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Bicicletes" else "Bicis",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = bikeColor
                            )
                        }
                        Text(
                            text = "${station.available}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = bikeColor
                        )
                    }
                }

                // Docks Block
                val hasDocks = station.free > 0
                val dockColor = if (hasDocks) (if (isDarkMode) Color(0xFF93C5FD) else Color(0xFF1D4ED8)) else (if (isDarkMode) Color(0xFFE57373) else Color(0xFFC62828))
                val dockBg = if (hasDocks) (if (isDarkMode) Color(0xFF16253B) else Color(0xFFEFF6FF)) else (if (isDarkMode) Color(0xFF2E1515) else Color(0xFFFFEBEE))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = dockBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalParking,
                                contentDescription = null,
                                tint = dockColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Buits" else "Huecos",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = dockColor
                            )
                        }
                        Text(
                            text = "${station.free}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = dockColor
                        )
                    }
                }
            }

            // Status warning if out of service
            if (!station.open) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDarkMode) Color(0xFF2E1515) else Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.status_out_of_service),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFFE57373) else Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteValenbisiStationCard(
    station: ValenbisiStation,
    alias: String?,
    isDarkMode: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brandColor = getValenbisiBrandColor(isDarkMode)
    val cardBg = if (isDarkMode) Color(0xFF111E2E) else Color(0xFFEEF3F8)
    val cardBorder = BorderStroke(1.dp, brandColor.copy(alpha = if (isDarkMode) 0.65f else 0.45f))
    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    val cleanDist = if (station.distanceText.isNotEmpty()) {
        station.distanceText.removePrefix("a ").trim()
    } else {
        ""
    }

    val displayName = alias.takeIf { !it.isNullOrBlank() } ?: station.name

    Card(
        onClick = onCardClick,
        modifier = modifier
            .width(170.dp)
            .height(92.dp)
            .testTag("fav_pill_valenbisi_${station.number}"),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = cardTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    )

                    if (cleanDist.isNotEmpty()) {
                        Text(
                            text = cleanDist,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cardTextSecondaryColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }

            // Bottom chips row: Bikes and Docks badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasBikes = station.available > 0
                val bikeColor = if (hasBikes) (if (isDarkMode) Color(0xFF81C784) else Color(0xFF1B5E20)) else (if (isDarkMode) Color(0xFFE57373) else Color(0xFFB71C1C))
                val bikeBg = if (hasBikes) (if (isDarkMode) Color(0xFF142B1F) else Color(0xFFE8F5E9)) else (if (isDarkMode) Color(0xFF2E1515) else Color(0xFFFFEBEE))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = bikeBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBike,
                            contentDescription = null,
                            tint = bikeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${station.available}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = bikeColor
                        )
                    }
                }

                val hasDocks = station.free > 0
                val dockColor = if (hasDocks) (if (isDarkMode) Color(0xFF93C5FD) else Color(0xFF1D4ED8)) else (if (isDarkMode) Color(0xFFE57373) else Color(0xFFB71C1C))
                val dockBg = if (hasDocks) (if (isDarkMode) Color(0xFF16253B) else Color(0xFFEFF6FF)) else (if (isDarkMode) Color(0xFF2E1515) else Color(0xFFFFEBEE))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = dockBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalParking,
                            contentDescription = null,
                            tint = dockColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${station.free}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = dockColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFavoriteValenbisiCard(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val brandColor = getValenbisiBrandColor(isDarkMode)

    Card(
        modifier = modifier
            .width(250.dp)
            .height(92.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF262C3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.StarBorder,
                contentDescription = null,
                tint = brandColor,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.valenbisi_empty_favorites_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.valenbisi_empty_favorites_hint),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
