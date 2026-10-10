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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.appCardBorder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val EmtRed = Color(0xFFE53935)

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
        if (isDarkMode) Color(0xFF2A1212) else Color(0xFFFFEBEE)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorder = if (isFav) {
        BorderStroke(1.dp, EmtRed.copy(alpha = if (isDarkMode) 0.5f else 0.35f))
    } else {
        BorderStroke(1.dp, if (isDarkMode) Color(0xFF2E3545) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = { onCardClick() },
        modifier = modifier
            .fillMaxWidth()
            .testTag("bus_stop_card_${stop.opId}"),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
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
                    // Title and notes or custom alias
                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = alias,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = stop.me,
                            style = MaterialTheme.typography.bodySmall,
                            color = cardTextSecondaryColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = buildFormattedStopName(
                                rawName = stop.me,
                                primaryColor = cardTextColor,
                                secondaryColor = cardTextSecondaryColor
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = SpaceGroteskFontFamily,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Parada ${stop.opId} • ${stop.distanceText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardTextSecondaryColor
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isDarkMode) Color(0xFF0F131E) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("fav_btn_${stop.opId}").fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFav) Color(0xFFFFB300) else cardTextSecondaryColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                color = if (isDarkMode) Color(0x1F8791A6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 26.dp)
                            .height(26.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = line.id_linea,
                                style = MaterialTheme.typography.labelLarge,
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
