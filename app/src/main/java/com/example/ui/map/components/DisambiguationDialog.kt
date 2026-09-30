package com.example.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.ui.components.OperatorLogo
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.SelectedMapItem

@Composable
fun DisambiguationDialog(
    items: List<SelectedMapItem>,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    busStopAliases: Map<String, String>,
    onSelectItem: (SelectedMapItem) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val titleColor = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val subtitleColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val closeBtnBg = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFF1F5F9)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = dialogBg,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Parades properes" else "Paradas cercanas",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Tria la parada que vols consultar:" else "Elige la parada que deseas consultar:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = subtitleColor
                            )
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = closeBtnBg,
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("disambiguation_dialog_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = subtitleColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable list of selectable items
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(
                        items = items,
                        key = { item ->
                            when (item) {
                                is SelectedMapItem.BusStop -> "disambig_bus_${item.stop.id_parada}"
                                is SelectedMapItem.MetrobusStopItem -> "disambig_metrobus_${item.stop.id_parada}"
                                is SelectedMapItem.Metro -> "disambig_metro_${item.station.id}"
                                is SelectedMapItem.Cercanias -> "disambig_cercanias_${item.station.stop_id}"
                                is SelectedMapItem.Valenbisi -> "disambig_valenbisi_${item.station.number}"
                                is SelectedMapItem.Address -> "disambig_addr_${item.result.latitude}_${item.result.longitude}"
                                is SelectedMapItem.LiveTrain -> "disambig_train_${item.vehicle.tripId.ifBlank { item.vehicle.trainNum }}"
                            }
                        }
                    ) { item ->
                        val title: String
                        val subtitle: String
                        val badge: String

                        when (item) {
                            is SelectedMapItem.BusStop -> {
                                val alias = busStopAliases[item.stop.id_parada]
                                title = if (!alias.isNullOrBlank()) alias else item.stop.denominacion
                                val lines = item.stop.lineas ?: ""
                                subtitle = if (appLanguage == AppLanguage.CA) "EMT Autobús • Línies: $lines" else "EMT Autobús • Líneas: $lines"
                                badge = item.stop.id_parada
                            }
                            is SelectedMapItem.MetrobusStopItem -> {
                                val alias = busStopAliases[item.stop.id_parada]
                                title = if (!alias.isNullOrBlank()) alias else item.stop.denominacion
                                val lines = item.stop.lineas ?: ""
                                subtitle = if (appLanguage == AppLanguage.CA) "Metrobús • Línies: $lines" else "Metrobús • Líneas: $lines"
                                badge = "MB"
                            }
                            is SelectedMapItem.Metro -> {
                                title = item.station.name
                                val lines = item.station.lines.joinToString(", ")
                                subtitle = if (appLanguage == AppLanguage.CA) "Metrovalencia • Línies: $lines" else "Metrovalencia • Líneas: $lines"
                                badge = "Metro"
                            }
                            is SelectedMapItem.Cercanias -> {
                                title = item.station.displayName
                                val lines = item.station.lines
                                subtitle = if (appLanguage == AppLanguage.CA) "Rodalia Renfe • Línies: $lines" else "Rodalia Renfe • Líneas: $lines"
                                badge = "Rodalia"
                            }
                            is SelectedMapItem.Valenbisi -> {
                                val count = item.station.available
                                title = item.station.name
                                subtitle = if (appLanguage == AppLanguage.CA) "Estació de Valenbisi • Bicis: $count" else "Estación de Valenbisi • Bicis: $count"
                                badge = "Bici"
                            }
                            is SelectedMapItem.Address -> {
                                val isHome = item.result.type.equals("home", ignoreCase = true) || item.result.displayName.startsWith("Casa", ignoreCase = true)
                                val isWork = item.result.type.equals("work", ignoreCase = true) || item.result.displayName.startsWith("Trabajo", ignoreCase = true) || item.result.displayName.startsWith("Feina", ignoreCase = true)
                                val isFav = item.result.category == "favorite" || item.result.type == "favorite" || isHome || isWork

                                title = if (!item.result.placeName.isNullOrBlank()) {
                                    item.result.placeName
                                } else {
                                    item.result.displayName.split(",").firstOrNull()?.trim() ?: item.result.displayName
                                }
                                subtitle = item.result.displayName
                                badge = when {
                                    isHome -> if (appLanguage == AppLanguage.CA) "Casa" else "Casa"
                                    isWork -> if (appLanguage == AppLanguage.CA) "Feina" else "Trabajo"
                                    isFav -> if (appLanguage == AppLanguage.CA) "Preferit" else "Favorito"
                                    else -> if (appLanguage == AppLanguage.CA) "Destí" else "Destino"
                                }
                            }
                            is SelectedMapItem.LiveTrain -> {
                                title = "Tren ${item.vehicle.trainNum.ifBlank { item.vehicle.routeId }}"
                                subtitle = "Cercanías ${item.vehicle.routeId} • Destino: ${item.vehicle.destinationName.ifBlank { "En circulación" }}"
                                badge = item.vehicle.routeId.ifBlank { "RENFE" }
                            }
                        }

                        Card(
                            onClick = {
                                onSelectItem(item)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("disambiguation_item_${title}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkMode) Color(0xFF282828) else Color(0xFFF8FAFC)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OperatorLogo(item = item, modifier = Modifier.size(38.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = titleColor
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = subtitleColor
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDarkMode) Color(0xFF334155) else Color(0xFFEDF2F7)
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF4A5568)
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
