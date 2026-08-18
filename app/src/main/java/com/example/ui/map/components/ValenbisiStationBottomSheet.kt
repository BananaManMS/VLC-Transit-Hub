package com.example.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.dashboard.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValenbisiStationBottomSheet(
    station: ValenbisiStation,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean = false,
    alias: String? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onEditAlias: (() -> Unit)? = null,
    onDirectionsClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val textColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val cardBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val cardBorderColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
    val actionBtnBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = if (isDarkMode) Color(0xFF0F172A) else Color.White,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("valenbisi_station_bottom_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            text = if (appLanguage == AppLanguage.CA) "Estació de lloguer" else "Estación de alquiler",
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
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Directions,
                                    contentDescription = "Cómo llegar",
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
                                modifier = Modifier.fillMaxSize()
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
                                modifier = Modifier.fillMaxSize()
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
                            modifier = Modifier.fillMaxSize()
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

            Spacer(modifier = Modifier.height(20.dp))

            // Realtime Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bikes available
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = station.available.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = if (station.available == 0) subtextColor else Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Bicicletes" else "Bicicletas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Disponibles" else "Disponibles",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextColor
                        )
                    }
                }

                // Free slots
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = station.free.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Buits" else "Huecos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Lliures" else "Libres",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extra Info (Capacity, Distance)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (station.distanceText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = subtextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Distància: ${station.distanceText}" else "Distancia: ${station.distanceText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtextColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Capacitat: ${station.total}" else "Capacidad: ${station.total}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
