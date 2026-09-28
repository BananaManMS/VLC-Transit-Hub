package com.example.ui.bus

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@Composable
fun MetrobusSheetHeader(
    stop: MetrobusStop,
    alias: String?,
    isDarkMode: Boolean,
    isFavorite: Boolean,
    sheetTextColor: Color,
    sheetSubtextColor: Color,
    onRefresh: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onEditAliasClick: (() -> Unit)?,
    onDismiss: () -> Unit,
    onDirectionsClick: (() -> Unit)?,
    dragModifier: Modifier = Modifier,
    onCloseClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragModifier),
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
                        text = buildFormattedStopName(
                            rawName = stop.denominacion,
                            primaryColor = sheetTextColor,
                            secondaryColor = sheetSubtextColor
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "Parada ${stop.idParada}${if (!stop.distanceText.isNullOrEmpty()) " • ${stop.distanceText}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = sheetSubtextColor
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleFavorite != null) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("metrobus_sheet_fav_btn")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) Color(0xFFFFB300) else (if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF475569))
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

                if (onCloseClick != null) {
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier.testTag("metrobus_sheet_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = if (isDarkMode) Color.White else Color.Black
                        )
                    }
                }
            }
        }

        if (onDirectionsClick != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDirectionsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("metrobus_directions_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cómo llegar",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MetrobusDepartureCard(
    dep: MetrobusDepartureUiModel,
    isDarkMode: Boolean,
    sheetTextColor: Color,
    sheetSubtextColor: Color
) {
    val badgeBg = parseHexColor(dep.routeColor, Color(0xFFD97706))
    val badgeText = Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)
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
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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

                    if (dep.isRealTime && dep.ocupacion.isNotBlank() && !dep.ocupacion.equals("SIN DATOS", ignoreCase = true) && !dep.ocupacion.contains("HORARIO", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-3).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val level = dep.ocupacion.uppercase()
                            val activeCount = when (level) {
                                "BAJA" -> 1
                                "MEDIA" -> 2
                                "ALTA" -> 3
                                else -> 0
                            }
                            val activeColor = when (level) {
                                "BAJA" -> Color(0xFF137333) // Green
                                "MEDIA" -> Color(0xFFD97706) // Orange
                                "ALTA" -> Color(0xFFC5221F) // Red
                                else -> Color.Gray
                            }
                            val inactiveColor = if (isDarkMode) Color(0xFF475569) else Color(0xFFCBD5E1)

                            for (i in 0 until 3) {
                                val isActive = i < activeCount
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isActive) activeColor else inactiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (level) {
                                    "BAJA" -> "Baja"
                                    "MEDIA" -> "Media"
                                    "ALTA" -> "Alta"
                                    else -> dep.ocupacion
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeCount > 0) activeColor else sheetSubtextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeColor = if (!dep.isRealTime) {
                        if (isDarkMode) Color.White else Color.Black
                    } else if (dep.minutesRemaining <= 5) {
                        MetrobusGreen
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = dep.timeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = timeColor
                    )
                    if (dep.isRealTime) {
                        com.example.ui.components.LiveRssFeedIcon(
                            contentDescription = "En vivo",
                            tint = Color(0xFF2ECC71),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (dep.departureTime.isNotEmpty()) {
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
