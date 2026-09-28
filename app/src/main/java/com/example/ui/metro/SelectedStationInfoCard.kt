package com.example.ui.metro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData

@Composable
fun SelectedStationInfoCard(
    isStationInfoExpanded: Boolean,
    selectedStation: MetroStation?,
    isDarkMode: Boolean = false
) {
    AnimatedVisibility(
        visible = isStationInfoExpanded && selectedStation != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        selectedStation?.let { station ->
            val cardBg = if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)
            val borderStroke = if (isDarkMode) BorderStroke(1.dp, Color(0xFF333333)) else BorderStroke(1.dp, Color(0xFFE2E8F0))
            val headerColor = if (isDarkMode) Color(0xFFA3A3A3) else Color(0xFF64748B)
            val destinationTextColor = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("station_info_expanded_card"),
                colors = CardDefaults.cardColors(
                    containerColor = cardBg
                ),
                shape = RoundedCornerShape(14.dp),
                border = borderStroke,
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkMode) 0.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LÍNEAS Y DESTINOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = headerColor,
                            letterSpacing = 0.5.sp
                        )

                        val cleanZone = com.example.data.model.cleanZoneCode(station.zone)
                        val zoneText = "ZONA $cleanZone"

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDarkMode) Color(0xFF1E3A8A) else Color(0xFFEFF6FF)
                        ) {
                            Text(
                                text = zoneText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        station.lines.forEach { lineId ->
                            val lineInfo = ValenciaMetroData.getLine(lineId)
                            if (lineInfo != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(android.graphics.Color.parseColor(lineInfo.colorHex))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lineId,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    val destinationsText = lineInfo.destinations.take(2).joinToString(" / ")
                                    val isTram = lineId in listOf("L4", "L6", "L8", "L10")
                                    val displayText = if (isTram) "(Tranvía) $destinationsText" else destinationsText
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = destinationTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
