package com.example.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.dashboard.AppLanguage

@Composable
fun CercaniasStationHeader(
    station: CercaniasStationEntity,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean,
    onDirectionsClick: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToCercanias: ((String) -> Unit)?,
    selectedLineFilters: Set<String> = emptySet(),
    onToggleLineFilter: (String) -> Unit = {},
    onClearLineFilters: () -> Unit = {},
    headerDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val titleColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF64748B)
    val iconTint = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF475569)
    val renfeColor = Color(0xFFE52321)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(headerDragModifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Operator Badge + Subtitle Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = renfeColor,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Cercanías",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "València",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = subtextColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Large Station Title
                Text(
                    text = station.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Top-right action icons (Directions, Favorite, Fullscreen, Close)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onDirectionsClick != null) {
                    IconButton(
                        onClick = onDirectionsClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("sheet_directions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Com arribar" else "Cómo llegar",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("sheet_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) Color(0xFFFFB300) else iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (onNavigateToCercanias != null) {
                    IconButton(
                        onClick = { onNavigateToCercanias(station.stop_id) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("sheet_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Pantalla completa" else "Pantalla completa",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("sheet_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Line Badges Row with filtering
        val lineList = station.lines.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (lineList.isNotEmpty()) {
            val isAllSelected = selectedLineFilters.isEmpty()
            val showAllChip = lineList.size > 1
            val inactiveBgColor = if (isDarkMode) Color(0xFF242424) else Color(0xFFF1F5F9)
            val inactiveBorderColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFE2E8F0)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAllChip) {
                    Surface(
                        onClick = onClearLineFilters,
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAllSelected) renfeColor else inactiveBgColor,
                        border = if (isAllSelected) null else BorderStroke(1.dp, inactiveBorderColor),
                        modifier = Modifier.testTag("cercanias_line_filter_all")
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Totes" else "Todas",
                            color = if (isAllSelected) Color.White else subtextColor,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                lineList.forEach { rawLineId ->
                    val lineId = CercaniasStationHighlightManager.normalizeLineRef(rawLineId)
                    val routeColor = when (lineId) {
                        "C1" -> Color(0xFF00A3E0)
                        "C2" -> Color(0xFFFF6A00)
                        "C3" -> Color(0xFF7A287B)
                        "C4" -> Color(0xFFE52321)
                        "C5" -> Color(0xFF009639)
                        "C6" -> Color(0xFF002F6C)
                        else -> Color(0xFF702B7B)
                    }

                    val isSelected = selectedLineFilters.any {
                        CercaniasStationHighlightManager.normalizeLineRef(it).equals(lineId, ignoreCase = true)
                    }
                    val isNoFilterActive = selectedLineFilters.isEmpty()

                    val chipColor = when {
                        isSelected -> routeColor
                        isNoFilterActive -> routeColor
                        else -> inactiveBgColor
                    }

                    val textColor = when {
                        isSelected || isNoFilterActive -> Color.White
                        else -> subtextColor
                    }

                    val border = when {
                        isSelected && !isNoFilterActive -> BorderStroke(2.dp, if (isDarkMode) Color.White else routeColor)
                        !isSelected && !isNoFilterActive -> BorderStroke(1.dp, inactiveBorderColor)
                        else -> null
                    }

                    Surface(
                        onClick = { onToggleLineFilter(lineId) },
                        shape = RoundedCornerShape(8.dp),
                        color = chipColor,
                        border = border,
                        modifier = Modifier.testTag("cercanias_line_filter_$lineId")
                    ) {
                        Text(
                            text = lineId,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
