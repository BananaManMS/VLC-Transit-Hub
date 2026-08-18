package com.example.ui.bus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.Translation
import com.example.ui.theme.appCardBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusTimesBottomSheet(
    stop: EmtBusStop,
    busTimes: List<EmtBusTime>,
    busTimesLoading: Boolean,
    isDarkMode: Boolean,
    texts: Translation,
    onDismissRequest: () -> Unit,
    alias: String? = null,
    onEditAliasClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onDirectionsClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetBg = if (isDarkMode) Color(0xFF0F131E) else MaterialTheme.colorScheme.surface
    val sheetTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val sheetSubtextColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
    val itemBg = MaterialTheme.colorScheme.surface

    var selectedLineFilters by remember(stop.opId) { mutableStateOf<Set<String>>(emptySet()) }

    val availableLines = remember(stop, busTimes) {
        val linesFromUtes = stop.utes.map { it.id_linea.trim() }.filter { it.isNotEmpty() }
        val linesFromTimes = busTimes.map { it.linea.trim() }.filter { it.isNotEmpty() }
        (linesFromUtes + linesFromTimes).distinct().sortedWith(compareBy { line ->
            line.toIntOrNull() ?: Int.MAX_VALUE
        })
    }

    val filteredBusTimes = remember(busTimes, selectedLineFilters) {
        if (selectedLineFilters.isEmpty()) {
            busTimes
        } else {
            busTimes.filter { time ->
                selectedLineFilters.any { filter -> filter.equals(time.linea.trim(), ignoreCase = true) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 340.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("bus_times_bottom_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val cleanSheetName = remember(stop.me) {
                        stop.me.replace(Regex("\\s*\\([^)]+\\)\\s*$"), "").trim()
                    }
                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = alias,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sheetTextColor
                        )
                        Text(
                            text = "$cleanSheetName • Parada ${stop.opId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = sheetSubtextColor
                        )
                    } else {
                        Text(
                            text = cleanSheetName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sheetTextColor
                        )
                        Text(
                            text = "Parada ${stop.opId} • ${stop.distanceText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = sheetSubtextColor
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDirectionsClick != null) {
                        IconButton(
                            onClick = onDirectionsClick,
                            modifier = Modifier.testTag("sheet_directions_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = "Cómo llegar",
                                tint = Color(0xFF0284C7)
                            )
                        }
                    }
                    if (onToggleFavorite != null) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag("sheet_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorito",
                                tint = if (isFavorite) Color(0xFFF59E0B) else (if (isDarkMode) Color.White else Color.Black)
                            )
                        }
                    }
                    if (onEditAliasClick != null) {
                        IconButton(
                            onClick = onEditAliasClick,
                            modifier = Modifier.testTag("sheet_edit_alias_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar nombre personalizado",
                                tint = if (!alias.isNullOrBlank()) Color(0xFF4F8CFF) else sheetSubtextColor
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val gmmIntentUri = Uri.parse("geo:0,0?q=${stop.t},${stop.n}(${Uri.encode(stop.me)})")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${stop.t},${stop.n}"))
                                context.startActivity(webIntent)
                            }
                        },
                        modifier = Modifier.testTag("maps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Ver en Google Maps",
                            tint = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        onClick = { selectedLineFilters = emptySet() },
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
                                text = "Todas",
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
                            onClick = {
                                selectedLineFilters = if (isSelected) {
                                    selectedLineFilters.filterNot { it.equals(line, ignoreCase = true) }.toSet()
                                } else {
                                    selectedLineFilters + line
                                }
                            },
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
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay estimaciones de llegada disponibles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = sheetSubtextColor
                    )
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
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    filteredBusTimes.forEach { time ->
                        Card(
                            border = appCardBorder(),
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
                                        color = Color(0xFFD32F2F), // EMT Corporate Red
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
                                        if (!isAbsolute) {
                                            Text(
                                                text = "Hora estimada: ${time.horaLlegada}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sheetSubtextColor
                                            )
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val isAbsolute = time.minutos.contains(":") || time.minutos.length >= 5
                                    val minutesInt = time.minutos.toIntOrNull() ?: 99
                                    val isImmediate = !isAbsolute && minutesInt <= 1
                                    val text = if (isAbsolute) time.minutos else {
                                        if (isImmediate) texts.immediateValue else "${time.minutos} min"
                                    }
                                    val color = when {
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
                                        if (!isAbsolute) {
                                            Icon(
                                                imageVector = Icons.Default.RssFeed,
                                                contentDescription = "En Vivo",
                                                tint = Color(0xFF2ECC71),
                                                modifier = Modifier.size(16.dp)
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
}
