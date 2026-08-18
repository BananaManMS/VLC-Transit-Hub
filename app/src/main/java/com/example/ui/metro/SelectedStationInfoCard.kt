package com.example.ui.metro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    selectedStation: MetroStation?
) {
                            AnimatedVisibility(
                                visible = isStationInfoExpanded && selectedStation != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                selectedStation?.let { station ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("station_info_expanded_card"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "LÍNEAS Y DESTINOS:",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                    letterSpacing = 0.5.sp
                                                )

                                                val cleanZone = com.example.data.model.cleanZoneCode(station.zone)
                                                val zoneText = "ZONA $cleanZone"

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = zoneText,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }

                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                station.lines.forEach { lineId ->
                                                    val lineInfo = ValenciaMetroData.getLine(lineId)
                                                    if (lineInfo != null) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
