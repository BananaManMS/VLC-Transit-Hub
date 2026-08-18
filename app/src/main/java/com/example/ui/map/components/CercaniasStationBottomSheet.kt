package com.example.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.cercanias.CercaniasDeparture
import com.example.ui.cercanias.CercaniasDepartureCard
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.SelectedMapItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CercaniasStationBottomSheet(
    station: CercaniasStationEntity,
    departures: List<CercaniasDeparture>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    alerts: List<CercaniasAlert> = emptyList(),
    onNavigateToCercanias: ((String) -> Unit)? = null,
    onDirectionsClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
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
                .defaultMinSize(minHeight = 340.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Header: Icon + Name + Favorite/Close buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OperatorLogo(
                        item = SelectedMapItem.Cercanias(station),
                        modifier = Modifier.size(44.dp)
                    )
                    Column {
                        Text(
                            text = station.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Rodalia Renfe • València" else "Cercanías Renfe • València",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDirectionsClick != null) {
                        IconButton(onClick = onDirectionsClick) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = "Cómo llegar",
                                tint = Color(0xFF0284C7)
                            )
                        }
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) Color(0xFFF59E0B) else (if (isDarkMode) Color.White else Color.Black)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = if (isDarkMode) Color.White else Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lines badges & Full screen button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    station.lines.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { lineId ->
                        val routeColor = when(lineId) {
                            "C1" -> Color(0xFF00A3E0)
                            "C2" -> Color(0xFFFF6A00)
                            "C3" -> Color(0xFF7A287B)
                            "C4" -> Color(0xFFE52321)
                            "C5" -> Color(0xFF009639)
                            "C6" -> Color(0xFF002F6C)
                            else -> Color(0xFF702B7B)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = routeColor
                        ) {
                            Text(
                                text = lineId,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    onClick = { onNavigateToCercanias?.invoke(station.stop_id) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Pantalla completa" else "Ver pantalla completa",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (appLanguage == AppLanguage.CA) "Pròximes Salides" else "Próximas Salidas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDarkMode) Color.White else Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF702B7B))
                }
            } else if (departures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "No hi ha eixides programades en aquest moment" else "No hay salidas programadas en este momento",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(departures.size) { index ->
                        val departure = departures[index]
                        CercaniasDepartureCard(
                            departure = departure,
                            alerts = alerts,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            onClick = {}
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
