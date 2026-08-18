package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CalendarItemEntity
import com.example.ui.theme.appCardBorder

@Composable
fun CalendarSummaryWidget(
    isTablet: Boolean,
    isDarkMode: Boolean,
    calendarTitle: String,
    noEventsTodayText: String,
    events: List<CalendarItemEntity>,
    onSyncClick: () -> Unit,
    onAddClick: () -> Unit,
    onEventDelete: (CalendarItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // Adaptive styling values based on tablet mode
    val titleFontSize = if (isTablet) 14.sp else 13.sp
    val sectionPadding = if (isTablet) 16.dp else 12.dp
    val itemSpacing = if (isTablet) 12.dp else 10.dp
    
    var selectedFilter by remember { mutableStateOf(0) } // 0: Todos, 1: Hoy, 2: Mañana, 3: Próximos
    val filteredEvents = remember(events, selectedFilter) {
        when (selectedFilter) {
            1 -> events.filter { event ->
                val label = getRelativeDateLabel(event.startMillis).replace("(", "").replace(")", "").trim()
                label.equals("Hoy", ignoreCase = true) || label.isEmpty()
            }
            2 -> events.filter { event ->
                val label = getRelativeDateLabel(event.startMillis).replace("(", "").replace(")", "").trim()
                label.equals("Mañana", ignoreCase = true)
            }
            3 -> events.filter { event ->
                val label = getRelativeDateLabel(event.startMillis).replace("(", "").replace(")", "").trim()
                !label.equals("Hoy", ignoreCase = true) && !label.equals("Mañana", ignoreCase = true) && label.isNotEmpty()
            }
            else -> events
        }
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp)
    ) {
        if (isTablet) {
            // TABLET LAYOUT: Styled inside an OutlinedCard with primary container background, and a scrollable list
            OutlinedCard(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("google_calendar_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = appCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(sectionPadding)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Calendar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "EVENTOS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = 1.sp
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onSyncClick,
                                modifier = Modifier.size(28.dp).testTag("sync_google_calendar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = onAddClick,
                                modifier = Modifier.size(28.dp).testTag("add_calendar_item_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val filterOptions = listOf("Todos", "Hoy", "Mañana", "Próximos")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("calendar_day_filter_pills_tablet")
                    ) {
                        items(filterOptions.size) { index ->
                            val label = filterOptions[index]
                            val isSelected = selectedFilter == index
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = index },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(itemSpacing)
                    ) {
                        if (filteredEvents.isEmpty()) {
                            item {
                                Text(
                                    text = noEventsTodayText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            items(filteredEvents, key = { it.id }) { event ->
                                EventCard(
                                    event = event,
                                    onDelete = { onEventDelete(event) },
                                    isDarkMode = isDarkMode
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // PHONE LAYOUT: Static vertical list without card wrapping, just a column as in the original layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_calendar_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Calendar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "EVENTOS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onSyncClick,
                                modifier = Modifier.size(28.dp).testTag("sync_google_calendar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onAddClick,
                                modifier = Modifier.size(28.dp).testTag("add_calendar_item_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val filterOptionsPhone = listOf("Todos", "Hoy", "Mañana", "Próximos")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("calendar_day_filter_pills_phone")
                    ) {
                        items(filterOptionsPhone.size) { index ->
                            val label = filterOptionsPhone[index]
                            val isSelected = selectedFilter == index
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = index },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(itemSpacing)
                    ) {
                        if (filteredEvents.isEmpty()) {
                            Text(
                                text = noEventsTodayText,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        } else {
                            filteredEvents.forEach { event ->
                                EventCard(
                                    event = event,
                                    onDelete = { onEventDelete(event) },
                                    isDarkMode = isDarkMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
