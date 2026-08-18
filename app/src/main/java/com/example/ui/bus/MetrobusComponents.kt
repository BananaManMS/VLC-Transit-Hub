package com.example.ui.bus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.appCardBorder

// Metrobus Branding Colors
val MetrobusYellow = Color(0xFFFFD54F)
val MetrobusGreen = Color(0xFF2E7D32)
val MetrobusDarkText = Color(0xFF2E2400)

fun parseHexColor(hex: String?, default: Color): Color {
    if (hex.isNullOrBlank()) return default
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        val colorInt = cleanHex.toLong(16).toInt()
        if (cleanHex.length == 6) {
            Color(colorInt or 0xFF000000.toInt())
        } else {
            Color(colorInt)
        }
    } catch (e: Exception) {
        default
    }
}

// Calculate appropriate text color (white or black) based on color luminance
fun getContrastColor(backgroundColor: Color): Color {
    val luminance = 0.2126 * backgroundColor.red + 0.7152 * backgroundColor.green + 0.0722 * backgroundColor.blue
    return if (luminance > 0.5) Color(0xFF212121) else Color.White
}

@Composable
fun MetrobusStopCard(
    stop: MetrobusStop,
    isFav: Boolean,
    isDarkMode: Boolean,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    alias: String? = null,
    onEditAliasClick: (() -> Unit)? = null
) {
    // Elegant warm yellowish-green tint if favorite
    val cardBg = if (isFav) {
        if (isDarkMode) Color(0x1B2E7D32) else Color(0xFFF1F8E9)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("metrobus_stop_card_${stop.idParada}")
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        border = if (isFav) {
            BorderStroke(1.5.dp, MetrobusGreen)
        } else {
            appCardBorder()
        },
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    // Stop Name or Custom Alias
                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = alias,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = stop.denominacion,
                            style = MaterialTheme.typography.bodySmall,
                            color = cardTextSecondaryColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = stop.denominacion,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (stop.distanceText.isNotEmpty()) "Metrobús • ${stop.distanceText}" else "Metrobús",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardTextSecondaryColor
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFav && onEditAliasClick != null) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkMode) Color(0xFF0F131E) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(
                                onClick = onEditAliasClick,
                                modifier = Modifier.testTag("edit_metrobus_alias_btn_${stop.idParada}").fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar nombre personalizado",
                                    tint = if (!alias.isNullOrBlank()) MetrobusGreen else cardTextSecondaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isDarkMode) Color(0xFF0F131E) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag("metrobus_fav_btn_${stop.idParada}").fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorito",
                                tint = if (isFav) MetrobusYellow else cardTextSecondaryColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                color = if (isDarkMode) Color(0x1F8791A6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Line Badges Flow
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                val sortedLines = remember(stop.lineas) {
                    stop.lineas.sortedWith { l1, l2 ->
                        val num1 = l1.filter { it.isDigit() }.toIntOrNull()
                        val num2 = l2.filter { it.isDigit() }.toIntOrNull()
                        if (num1 != null && num2 != null) {
                            if (num1 != num2) num1.compareTo(num2) else l1.compareTo(l2)
                        } else if (num1 != null) {
                            -1
                        } else if (num2 != null) {
                            1
                        } else {
                            l1.compareTo(l2)
                        }
                    }
                }

                if (sortedLines.isEmpty()) {
                    Surface(
                        color = if (isDarkMode) Color(0xFF1E2638) else Color(0xFFEEEEEE),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Consultar líneas",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = cardTextSecondaryColor
                        )
                    }
                } else {
                    sortedLines.forEach { lineCode ->
                        Surface(
                            color = MetrobusYellow,
                            contentColor = MetrobusDarkText,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 26.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = lineCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetrobusTimesBottomSheet(
    stop: MetrobusStop,
    times: List<MetrobusDepartureUiModel>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    onDismissRequest: () -> Unit,
    alias: String? = null,
    onEditAliasClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onRefresh: () -> Unit
) {
    val sheetBg = if (isDarkMode) Color(0xFF0F131E) else MaterialTheme.colorScheme.surface
    val sheetTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val sheetSubtextColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 360.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("metrobus_times_bottom_sheet")
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = alias,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sheetTextColor
                        )
                        Text(
                            text = stop.denominacion,
                            style = MaterialTheme.typography.bodySmall,
                            color = sheetSubtextColor
                        )
                    } else {
                        Text(
                            text = stop.denominacion,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sheetTextColor
                        )
                        Text(
                            text = if (stop.distanceText.isNotEmpty()) "Metrobús • ${stop.distanceText}" else "Metrobús",
                            style = MaterialTheme.typography.bodySmall,
                            color = sheetSubtextColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refrescar horarios",
                            tint = if (isDarkMode) Color.White else Color.Black
                        )
                    }

                    if (onToggleFavorite != null) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag("metrobus_sheet_fav_btn")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorito",
                                tint = if (isFavorite) MetrobusYellow else (if (isDarkMode) Color.White else Color.Black)
                            )
                        }
                    }

                    if (onEditAliasClick != null) {
                        IconButton(
                            onClick = onEditAliasClick,
                            modifier = Modifier.testTag("metrobus_sheet_edit_alias_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar alias",
                                tint = if (isDarkMode) Color.White else Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = if (isDarkMode) Color(0x1F8791A6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Body List Section
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MetrobusGreen)
                }
            } else if (times.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron más salidas programadas para hoy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = sheetSubtextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    times.forEach { dep ->
                        val badgeBg = parseHexColor(dep.routeColor, MetrobusYellow)
                        val badgeText = getContrastColor(badgeBg)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkMode) Color(0xFF1E2638) else Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Line Badge with GTFS color if present, else Metrobús Yellow
                                    Surface(
                                        color = badgeBg,
                                        contentColor = badgeText,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 50.dp, minHeight = 32.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = dep.lineCode,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dep.destination,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = sheetTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val agencyAndLineText = if (!dep.lineName.isNullOrBlank() && !dep.destination.equals(dep.lineName, ignoreCase = true)) {
                                            "${dep.agencyName} • ${dep.lineName}"
                                        } else {
                                            dep.agencyName
                                        }
                                        Text(
                                            text = agencyAndLineText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sheetSubtextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Time Info
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dep.timeLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (dep.minutesRemaining <= 5) MetrobusGreen else MaterialTheme.colorScheme.primary
                                    )
                                    if (dep.minutesRemaining < 60) {
                                        Text(
                                            text = "Hora: ${dep.departureTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = sheetSubtextColor
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
