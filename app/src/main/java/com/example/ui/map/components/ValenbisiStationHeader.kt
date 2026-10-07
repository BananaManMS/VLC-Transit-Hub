package com.example.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import com.example.ui.dashboard.AppLanguage

@Composable
fun ValenbisiStationHeader(
    station: ValenbisiStation,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean,
    alias: String?,
    onToggleFavorite: (() -> Unit)?,
    onEditAlias: (() -> Unit)?,
    onDirectionsClick: (() -> Unit)?,
    onDismiss: () -> Unit,
    headerDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val textColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val actionBtnBg = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFF1F5F9)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(headerDragModifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF10B981), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBike,
                        contentDescription = "Valenbisi",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Valenbisi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.valenbisi_rental_station_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = subtextColor
                    )
                }
            }

            // Action Buttons Row (Directions, Edit Alias, Favorite Star, Close)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDirectionsClick != null) {
                    Surface(
                        shape = CircleShape,
                        color = actionBtnBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconButton(
                            onClick = onDirectionsClick,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("sheet_directions_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.bus_directions_btn),
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (onEditAlias != null) {
                    Surface(
                        shape = CircleShape,
                        color = actionBtnBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconButton(
                            onClick = onEditAlias,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("sheet_edit_alias_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar nombre",
                                tint = if (!alias.isNullOrBlank()) Color(0xFF3B82F6) else subtextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (onToggleFavorite != null) {
                    Surface(
                        shape = CircleShape,
                        color = actionBtnBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("sheet_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorito",
                                tint = if (isFavorite) Color(0xFFFFB300) else subtextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = actionBtnBg,
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("sheet_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Station Display Name & Info
        val displayName = alias.takeIf { !it.isNullOrBlank() } ?: station.name
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (station.open) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ) {
                Text(
                    text = if (station.open)
                        (if (appLanguage == AppLanguage.CA) "OBERTA" else "ABIERTA")
                    else (if (appLanguage == AppLanguage.CA) "TANCADA" else "CERRADA"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (station.open) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(
                text = "Nº ${station.number}" + if (!alias.isNullOrBlank()) " • ${station.name}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = subtextColor
            )
        }

        if (station.address.isNotBlank() && station.address != station.name) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = station.address,
                style = MaterialTheme.typography.bodySmall,
                color = subtextColor
            )
        }
    }
}
