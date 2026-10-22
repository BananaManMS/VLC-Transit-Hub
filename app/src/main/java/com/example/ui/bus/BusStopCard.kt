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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SpaceGroteskFontFamily

val EmtRed = Color(0xFFE53935)

/**
 * Modernized bus stop card displayed in the "PARADAS CERCANAS" list.
 * Styled matching the screenshot:
 * - Title + optional alias tag pill + gold star toggle button on the right
 * - Subtitle: "Parada #2277 • a 6.5 km"
 * - Red pill badges for routes at the bottom
 * - Subtle red border when favorited
 */
@Composable
fun BusStopCard(
    stop: EmtBusStop,
    isFav: Boolean,
    isDarkMode: Boolean,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    alias: String? = null,
    onEditAliasClick: (() -> Unit)? = null
) {
    val cardBg = if (isFav) {
        if (isDarkMode) Color(0xFF1E1414) else Color(0xFFFFF5F5)
    } else {
        if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface
    }
    val cardBorder = if (isFav) {
        BorderStroke(1.dp, EmtRed.copy(alpha = if (isDarkMode) 0.65f else 0.45f))
    } else {
        BorderStroke(1.dp, if (isDarkMode) Color(0xFF262C3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    val distFormatted = if (stop.distanceText.isNotEmpty()) {
        val clean = stop.distanceText.removePrefix("a ").trim()
        " • a $clean"
    } else {
        ""
    }

    Card(
        onClick = { onCardClick() },
        modifier = modifier
            .fillMaxWidth()
            .testTag("bus_stop_card_${stop.opId}"),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header row: Stop Name (+ optional Alias pill) + Star toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = buildFormattedStopName(stop.me, cardTextColor, cardTextSecondaryColor),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!alias.isNullOrBlank()) {
                        Surface(
                            color = if (isDarkMode) Color(0xFF2E1C20) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, EmtRed.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = alias,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmtRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("fav_btn_${stop.opId}")
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (isFav) Color(0xFFFFB300) else cardTextSecondaryColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Subtitle: "Parada #2277 • a 6.5 km"
            Text(
                text = "Parada #${stop.opId}$distFormatted",
                style = MaterialTheme.typography.bodyMedium,
                color = cardTextSecondaryColor,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bus Line pills row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                val sortedLines = remember(stop.utes) {
                    stop.utes.sortedWith { route1, route2 ->
                        val l1 = route1.id_linea
                        val l2 = route2.id_linea

                        val num1 = l1.filter { it.isDigit() }.toIntOrNull()
                        val num2 = l2.filter { it.isDigit() }.toIntOrNull()

                        if (num1 != null && num2 != null) {
                            if (num1 != num2) {
                                num1.compareTo(num2)
                            } else {
                                l1.compareTo(l2)
                            }
                        } else if (num1 != null) {
                            -1
                        } else if (num2 != null) {
                            1
                        } else {
                            l1.compareTo(l2)
                        }
                    }
                }

                sortedLines.forEach { line ->
                    Surface(
                        color = EmtRed,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 32.dp, minHeight = 22.dp)
                            .height(22.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = line.id_linea,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Taller favorite bus stop pill card for the top horizontal carousel ("FAVORITAS GUARDADAS").
 * As requested:
 * - "pastillas un poquito más altas en donde están tus paradas de bus favoritas guardadas, con la distancia y todo eso"
 * - "no tiene que tener botón la pastilla de fuera, pero sí que debe tener el botón de favorito en el panel desplegable"
 */
@Composable
fun FavoriteBusStopCard(
    stop: EmtBusStop,
    alias: String?,
    isDarkMode: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDarkMode) Color(0xFF1E1414) else Color(0xFFFFF5F5)
    val cardBorder = BorderStroke(1.dp, EmtRed.copy(alpha = if (isDarkMode) 0.65f else 0.45f))
    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    val distFormatted = if (stop.distanceText.isNotEmpty()) {
        val clean = stop.distanceText.removePrefix("a ").trim()
        " • a $clean"
    } else {
        ""
    }

    Card(
        onClick = onCardClick,
        modifier = modifier
            .width(170.dp)
            .height(92.dp)
            .testTag("fav_pill_${stop.opId}"),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                if (!alias.isNullOrBlank()) {
                    Text(
                        text = alias,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = SpaceGroteskFontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = buildFormattedStopName(stop.me, cardTextColor, cardTextSecondaryColor),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "Parada #${stop.opId}$distFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = cardTextSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                val sortedLines = remember(stop.utes) {
                    stop.utes.sortedWith { route1, route2 ->
                        val l1 = route1.id_linea
                        val l2 = route2.id_linea

                        val num1 = l1.filter { it.isDigit() }.toIntOrNull()
                        val num2 = l2.filter { it.isDigit() }.toIntOrNull()

                        if (num1 != null && num2 != null) {
                            if (num1 != num2) {
                                num1.compareTo(num2)
                            } else {
                                l1.compareTo(l2)
                            }
                        } else if (num1 != null) {
                            -1
                        } else if (num2 != null) {
                            1
                        } else {
                            l1.compareTo(l2)
                        }
                    }
                }

                sortedLines.forEach { line ->
                    Surface(
                        color = EmtRed,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 24.dp, minHeight = 18.dp)
                            .height(18.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = line.id_linea,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Placeholder card displayed in the top carousel when user has not yet saved any favorite stops.
 */
@Composable
fun EmptyFavoriteBusCard(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(250.dp)
            .height(92.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF262C3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.StarBorder,
                contentDescription = null,
                tint = EmtRed,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.bus_empty_favorites_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.bus_empty_favorites_hint),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
