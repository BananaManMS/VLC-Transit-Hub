package com.example.ui.dashboard.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.metro.MetroLineBadge
import com.example.ui.metro.TransitLogoUtils
import com.example.util.LineColorResolver
import com.example.util.MetrobusLineColorResolver

fun formatHabitualKey(operatorKey: String, line: String): String {
    val cleanLine = line.trim()
    val type = when (operatorKey.uppercase()) {
        "METRO", "METROVALENCIA" -> "METRO"
        "CERCANÍAS", "CERCANIAS", "RENFE" -> "CERCANIAS"
        "EMT", "EMT VALÈNCIA" -> "EMT"
        "METROBÚS", "METROBUS" -> "METROBUS"
        else -> operatorKey.uppercase()
    }
    return "$type:$cleanLine"
}

fun isHabitualLineSelected(operatorKey: String, line: String, habitualSet: Set<String>): Boolean {
    val cleanLine = line.trim()
    if (cleanLine.isBlank() || habitualSet.isEmpty()) return false

    val type = when (operatorKey.uppercase()) {
        "METRO", "METROVALENCIA" -> "METRO"
        "CERCANÍAS", "CERCANIAS", "RENFE" -> "CERCANIAS"
        "EMT", "EMT VALÈNCIA" -> "EMT"
        "METROBÚS", "METROBUS" -> "METROBUS"
        else -> operatorKey.uppercase()
    }

    val scopedKey = "$type:$cleanLine"
    if (habitualSet.contains(scopedKey)) return true

    val strippedL = if (cleanLine.startsWith("L", ignoreCase = true)) cleanLine.substring(1) else cleanLine
    val prefixedL = if (cleanLine.startsWith("L", ignoreCase = true)) cleanLine else "L$cleanLine"

    if (habitualSet.contains("$type:$strippedL") || habitualSet.contains("$type:$prefixedL")) {
        return true
    }

    if (habitualSet.contains(cleanLine) || habitualSet.contains(strippedL) || habitualSet.contains(prefixedL)) {
        val hasConflict = habitualSet.any { key ->
            key.contains(":") && !key.startsWith("$type:") &&
            (key.substringAfter(":") == cleanLine || key.substringAfter(":") == strippedL || key.substringAfter(":") == prefixedL)
        }
        if (!hasConflict) return true
    }

    return false
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitualLinesSheet(
    habitualLines: Set<String>,
    availableLinesGrouped: Map<String, List<String>>,
    isDarkMode: Boolean,
    onToggleLine: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val normalizedQuery = remember(searchQuery) { searchQuery.trim().lowercase() }

    val filteredGrouped = remember(availableLinesGrouped, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            availableLinesGrouped
        } else {
            availableLinesGrouped.mapValues { (_, lines) ->
                lines.filter { line ->
                    line.lowercase().contains(normalizedQuery) ||
                    "l$line".lowercase().contains(normalizedQuery) ||
                    "c$line".lowercase().contains(normalizedQuery) ||
                    "línea $line".lowercase().contains(normalizedQuery) ||
                    "linea $line".lowercase().contains(normalizedQuery)
                }
            }.filterValues { it.isNotEmpty() }
        }
    }

    val sheetBg = if (isDarkMode) Color(0xFF1E212A) else MaterialTheme.colorScheme.surface
    val textColor = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface
    val subtextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("habitual_lines_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Líneas habituales",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = subtextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habitual_lines_search_input"),
                placeholder = {
                    Text(
                        text = "Buscar línea (ej. L3, 93, C1)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtextColor
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = subtextColor,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpiar",
                                tint = subtextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Continuous List of Sections
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf("Metro", "Cercanías", "EMT", "Metrobús").forEach { operatorKey ->
                    val lines = filteredGrouped[operatorKey] ?: emptyList()
                    if (lines.isNotEmpty()) {
                        item(key = operatorKey) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = when (operatorKey) {
                                        "Metro" -> "Metrovalencia"
                                        "Cercanías" -> "Renfe Cercanías"
                                        "EMT" -> "EMT València"
                                        "Metrobús" -> "Metrobús"
                                        else -> operatorKey
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    lines.forEach { line ->
                                        val isSelected = isHabitualLineSelected(operatorKey, line, habitualLines)
                                        val scopedKey = formatHabitualKey(operatorKey, line)

                                        HabitualLineChip(
                                            operatorKey = operatorKey,
                                            line = line,
                                            isSelected = isSelected,
                                            isDarkMode = isDarkMode,
                                            onClick = { onToggleLine(scopedKey) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habitual_lines_done_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Guardar",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun HabitualLineChip(
    operatorKey: String,
    line: String,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val cleanLine = line.trim()
    val isMetro = operatorKey == "Metro"
    val isCercanias = operatorKey == "Cercanías"
    val isEmt = operatorKey == "EMT"
    val isMetrobus = operatorKey == "Metrobús"

    val baseColor = when {
        isMetro -> Color(android.graphics.Color.parseColor(LineColorResolver.getMetroLineColorHex(cleanLine)))
        isCercanias -> Color(android.graphics.Color.parseColor(LineColorResolver.getCercaniasLineColorHex(cleanLine)))
        isEmt -> if (isDarkMode) Color(0xFFEF4444) else Color(0xFFDC2626)
        isMetrobus -> MetrobusLineColorResolver.BRAND_COLOR
        else -> MaterialTheme.colorScheme.primary
    }

    val chipBg = if (isSelected) {
        baseColor
    } else {
        if (isDarkMode) Color(0xFF272A34) else Color(0xFFF1F5F9)
    }

    val contentColor = if (isSelected) {
        Color.White
    } else {
        if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF334155)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = chipBg,
        border = if (isSelected) BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)) else BorderStroke(1.dp, if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)),
        modifier = Modifier.testTag("habitual_chip_${operatorKey}_$cleanLine")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (isMetro) {
                MetroLineBadge(lineId = cleanLine, size = 20.dp)
                Spacer(modifier = Modifier.width(6.dp))
            } else if (isCercanias) {
                val logoRes = TransitLogoUtils.getCercaniasLineLogoRes(cleanLine)
                if (logoRes != null) {
                    Image(
                        painter = painterResource(id = logoRes),
                        contentDescription = "Línea $cleanLine",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            Text(
                text = if (isMetro && !cleanLine.startsWith("L", ignoreCase = true)) "L$cleanLine" else cleanLine,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = contentColor
            )

            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
