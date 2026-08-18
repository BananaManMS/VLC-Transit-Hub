package com.example.ui.metro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.ui.bus.BusFilterChip

@Composable
fun MetroSummaryWidget(
    isTablet: Boolean,
    isDarkMode: Boolean,
    popularStations: List<Triple<String, String, List<String>>>,
    selectedStationId: String?,
    activeStation: MetroStation?,
    onStationSelect: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColors = remember {
        mapOf(
            "L1" to Color(0xFFFDCB06),
            "L2" to Color(0xFFEC008C),
            "L3" to Color(0xFFE2001A),
            "L4" to Color(0xFF003366),
            "L5" to Color(0xFF009639),
            "L6" to Color(0xFF7B3FE4),
            "L7" to Color(0xFFF97316),
            "L8" to Color(0xFF14B8A6),
            "L9" to Color(0xFF7A4A2A),
            "L10" to Color(0xFF84CC16)
        )
    }

    val boardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val boardSubtextColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "METROVALENCIA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Editar favoritas",
                    tint = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Popular Stations Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(popularStations, key = { it.first }) { (id, name, _) ->
                val isSelected = selectedStationId == id
                val displayName = if (id == "facultats" || id == "13") "Facultats" else name
                BusFilterChip(
                    selected = isSelected,
                    onClick = { onStationSelect(id) },
                    label = displayName,
                    modifier = Modifier.testTag("station_recuadro_$id")
                )
            }
        }

        // Departures Card
        Card(
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkMode) 0.3f else 0.6f)
            ),
            modifier = if (isTablet) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("metro_departures_board")
            } else {
                Modifier
                    .fillMaxWidth()
                    .testTag("metro_departures_board")
            },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            val displayStationName = if (activeStation?.id == "facultats" || activeStation?.id == "13") {
                "Facultats - Manuel Broseta"
            } else {
                activeStation?.name ?: "Colón"
            }

            if (isTablet) {
                // Tablet layout inside Card: Vertical layout with description
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Subway,
                        contentDescription = "Valencia Metro",
                        tint = Color(0xFF4F8CFF),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = displayStationName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = boardTextColor,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        activeStation?.lines?.forEach { lineId ->
                            val color = lineColors[lineId] ?: Color(0xFF4F8CFF)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lineId,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(
                                            includeFontPadding = false
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = activeStation?.description ?: "",
                        fontSize = 11.sp,
                        color = boardSubtextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Phone layout inside Card: Horizontal layout without description
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subway,
                            contentDescription = "Valencia Metro",
                            tint = Color(0xFF4F8CFF),
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = displayStationName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = boardTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                activeStation?.lines?.forEach { lineId ->
                                    val color = lineColors[lineId] ?: Color(0xFF4F8CFF)
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lineId,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(
                                                    includeFontPadding = false
                                                ),
                                                textAlign = TextAlign.Center
                                            )
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
}
