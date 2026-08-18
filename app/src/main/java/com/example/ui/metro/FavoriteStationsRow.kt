package com.example.ui.metro

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.model.ValenciaMetroData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation
import com.example.ui.theme.appCardBorder

@Composable
fun FavoriteStationsRow(
    favoriteStations: List<String>,
    selectedStationId: String,
    metroViewModel: MetroViewModel,
    isDarkMode: Boolean,
    accentColor: Color,
    subtextColor: Color,
    texts: Translation,
    onSearchClick: () -> Unit
) {
    val displayStations = androidx.compose.runtime.remember(favoriteStations, selectedStationId) {
        if (selectedStationId.isNotEmpty() && !favoriteStations.contains(selectedStationId)) {
            listOf(selectedStationId) + favoriteStations
        } else {
            favoriteStations
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag("favorite_stations_selector"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Botón cuadrado de búsqueda rápida / selector de estación
        item {
            val cardBgColor = if (isDarkMode) Color(0xFF171D2C) else MaterialTheme.colorScheme.surface
            val expressiveShape = RoundedCornerShape(12.dp)
            Card(
                modifier = Modifier
                    .size(width = 48.dp, height = 52.dp)
                    .clickable { onSearchClick() }
                    .testTag("square_station_picker_button"),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = appCardBorder(),
                shape = expressiveShape
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = texts.searchPlaceholder,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Chips de estaciones favoritas (y estación seleccionada temporalmente si procede)
        if (displayStations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .clickable { onSearchClick() }
                        .testTag("empty_favorite_stations_chip"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = texts.searchPlaceholder,
                            fontSize = 12.sp,
                            color = subtextColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            items(displayStations, key = { it }) { stationId ->
                val station = metroViewModel.getStationInfo(stationId)
                if (station != null) {
                    val isSelected = stationId == selectedStationId
                    val cardBgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    val borderStroke = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        appCardBorder()
                    }
                    val animatedCornerRadius by animateDpAsState(
                        targetValue = if (isSelected) 8.dp else 18.dp,
                        animationSpec = tween(durationMillis = 500),
                        label = "fav_stations_selector_corner"
                    )
                    val expressiveShape = RoundedCornerShape(animatedCornerRadius)

                    Card(
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 160.dp)
                            .clickable { metroViewModel.selectRealTimeStation(stationId) }
                            .testTag("favorite_station_chip_$stationId"),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = borderStroke,
                        shape = expressiveShape
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = station.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) {
                                        if (isDarkMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                val distanceText = metroViewModel.getStationDistanceText(station)
                                if (distanceText != null) {
                                    Text(
                                        text = distanceText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) {
                                            if (isDarkMode) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                station.lines.forEach { lineId ->
                                    val lineObj = ValenciaMetroData.lines.find { it.id == lineId }
                                    val colorHex = lineObj?.colorHex ?: "#7F8C8D"
                                    Box(
                                        modifier = Modifier
                                            .size(width = 24.dp, height = 6.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
