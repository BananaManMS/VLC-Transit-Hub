package com.example.ui.bus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.Translation

@Composable
fun BusArrivalsList(
    availableLines: List<String>,
    selectedLineFilters: Set<String>,
    onToggleLineFilter: (String) -> Unit,
    onClearLineFilters: () -> Unit,
    busTimes: List<EmtBusTime>,
    filteredBusTimes: List<EmtBusTime>,
    busTimesLoading: Boolean,
    isDarkMode: Boolean,
    texts: Translation,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    onLoadMoreScheduled: (() -> Unit)? = null,
    isLoadingMoreScheduled: Boolean = false
) {
    val sheetSubtextColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
    val sheetTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val itemBg = if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)
    val context = LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    Column(modifier = modifier) {
        if (availableLines.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp)
            ) {
                val isAllSelected = selectedLineFilters.isEmpty()
                Surface(
                    onClick = onClearLineFilters,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllSelected) {
                        if (isDarkMode) Color(0xFF3B82F6) else MaterialTheme.colorScheme.primary
                    } else {
                        if (isDarkMode) Color(0xFF1E2538) else MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (isAllSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 26.dp)
                        .testTag("line_filter_chip_all")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.bus_line_filter_all),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAllSelected) Color.White else sheetSubtextColor
                        )
                    }
                }

                availableLines.forEach { line ->
                    val isSelected = selectedLineFilters.any { it.equals(line, ignoreCase = true) }
                    val isNoFilterActive = selectedLineFilters.isEmpty()

                    val bgColor = when {
                        isSelected -> Color(0xFFC62828)
                        isNoFilterActive -> Color(0xFFC62828)
                        else -> if (isDarkMode) Color(0xFF2C1A1A) else Color(0xFFFDE8E8)
                    }

                    val textColor = when {
                        isSelected -> Color.White
                        isNoFilterActive -> Color.White
                        else -> if (isDarkMode) Color(0xFFE57373) else Color(0xFFC62828)
                    }

                    val border = when {
                        isSelected && !isNoFilterActive -> BorderStroke(2.dp, if (isDarkMode) Color.White else Color(0xFFB71C1C))
                        else -> null
                    }

                    Surface(
                        onClick = { onToggleLineFilter(line) },
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor,
                        border = border,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 40.dp, minHeight = 26.dp)
                            .testTag("line_filter_chip_$line")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = texts.nextBusesLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (busTimesLoading) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                repeat(2) { SkeletonCardItem() }
            }
        } else if (busTimes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isOnline) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFFEF5350) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.offline_banner_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = sheetTextColor
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_check_connection_departures),
                            style = MaterialTheme.typography.bodySmall,
                            color = sheetSubtextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.no_arrival_estimates),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sheetSubtextColor
                        )
                        if (onLoadMoreScheduled != null) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = onLoadMoreScheduled,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (texts.headerAjustesTitle == "Ajusts") "Veure eixides programades" else "Ver salidas programadas",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        } else if (filteredBusTimes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                val filterText = selectedLineFilters.joinToString(", ")
                Text(
                    text = "No hay próximas llegadas para la(s) línea(s) $filterText.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sheetSubtextColor
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(scrollState)
            ) {
                filteredBusTimes.forEach { time ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = itemBg
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = time.linea,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = time.destino,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = sheetTextColor
                                    )
                                    val isAbsolute = time.minutos.contains(":") || time.minutos.length >= 5
                                    if (!isAbsolute && time.horaLlegada.isNotBlank()) {
                                        Text(
                                            text = "~${time.horaLlegada}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sheetSubtextColor
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val isAbsolute = time.minutos.contains(":") || time.minutos.length >= 5
                                val minutesInt = time.minutos.toIntOrNull() ?: 99
                                val isImmediate = !isAbsolute && minutesInt <= 0
                                val text = if (isAbsolute) time.minutos else {
                                    if (isImmediate) texts.immediateValue else "${time.minutos} min"
                                }
                                val color = when {
                                    !time.isRealTime -> if (isDarkMode) Color.White else Color.Black
                                    isAbsolute -> if (isDarkMode) Color.White else Color.Black
                                    isImmediate -> Color(0xFFE53935)
                                    minutesInt <= 2 -> Color(0xFFED8936)
                                    minutesInt <= 5 -> Color(0xFF48BB78)
                                    else -> if (isDarkMode) Color.White else Color.Black
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                    if (!isAbsolute && time.isRealTime) {
                                        com.example.ui.components.LiveRssFeedIcon(
                                            contentDescription = "En Vivo",
                                            tint = Color(0xFF2ECC71),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else if (!time.isRealTime) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Horario programado",
                                            tint = sheetSubtextColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                if (!time.isRealTime) {
                                    Text(
                                        text = "Programado",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sheetSubtextColor,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (isLoadingMoreScheduled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(2) {
                            SkeletonCardItem()
                        }
                    }
                } else if (onLoadMoreScheduled != null) {
                    val hasScheduled = filteredBusTimes.any { !it.isRealTime }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onLoadMoreScheduled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (texts.headerAjustesTitle == "Ajusts") {
                                if (hasScheduled) "Veure més eixides programades" else "Veure eixides programades"
                            } else {
                                if (hasScheduled) "Ver más salidas programadas" else "Ver salidas programadas"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
