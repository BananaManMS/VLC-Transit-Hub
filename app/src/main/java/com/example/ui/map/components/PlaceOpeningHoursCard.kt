package com.example.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.util.OpeningHoursParser
import com.example.ui.dashboard.AppLanguage

@Composable
fun ExpandableOpeningHoursCard(
    rawOpeningHours: String,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val isValencian = appLanguage == AppLanguage.CA
    val schedule = remember(rawOpeningHours) {
        OpeningHoursParser.parse(rawOpeningHours)
    } ?: return

    val isOpen = schedule.isOpenNow ?: false
    val statusText = if (isValencian) schedule.statusTextCa else schedule.statusTextEs
    val summaryText = if (isValencian) schedule.weeklySummaryCa else schedule.weeklySummaryEs

    val cardBg = if (isDarkMode) Color(0xFF262626) else Color(0xFFF8FAFC)
    val borderCol = if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = if (isOpen) Color(0xFF10B981) else Color(0xFFEF4444)
                Surface(
                    shape = CircleShape,
                    color = dotColor,
                    modifier = Modifier.size(8.dp)
                ) {}

                Spacer(modifier = Modifier.width(8.dp))

                val statusColor = if (isOpen) Color(0xFF10B981) else Color(0xFFEF4444)

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = statusColor
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = textSecondary
                        )
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = textPrimary,
                        maxLines = if (expanded) 3 else 1
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer horarios" else "Desplegar horarios",
                    tint = textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(
                        color = borderCol,
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    schedule.dailySchedules.forEach { day ->
                        val dayName = if (isValencian) day.dayNameCa else day.dayNameEs
                        val dayHours = if (isValencian) day.scheduleCa else day.scheduleEs

                        val rowTextColor = if (day.isToday) {
                            if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        } else {
                            textPrimary
                        }
                        val rowWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = rowWeight,
                                    fontSize = 12.sp
                                ),
                                color = rowTextColor
                            )
                            Text(
                                text = dayHours,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = rowWeight,
                                    fontSize = 12.sp
                                ),
                                color = rowTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
