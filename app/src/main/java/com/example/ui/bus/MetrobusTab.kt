package com.example.ui.bus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage

@Composable
fun MetrobusTab(
    appLanguage: AppLanguage,
    metrobusStopsList: List<MetrobusStop>,
    favoriteMetrobusStops: Set<String>,
    metrobusStopAliases: Map<String, String>,
    filterSource: BusFilterSource,
    searchQuery: String,
    isLoading: Boolean,
    selectedMetroStationId: String?,
    isDarkMode: Boolean,
    favoriteMetroStations: List<String> = emptyList(),
    allMetroStations: List<MetroStation> = emptyList(),
    listState: LazyListState,
    onFilterSourceSelected: (BusFilterSource) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSelectMetroStation: (String) -> Unit,
    onSelectStop: (MetrobusStop) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditAliasClick: (MetrobusStop) -> Unit,
    activeTripBottomPadding: Dp
) {
    val metroStationsList = remember(favoriteMetroStations, allMetroStations) {
        val all = if (allMetroStations.isNotEmpty()) allMetroStations else ValenciaMetroData.mainMetroStations
        val sortedAll = all.sortedBy { it.name }
        if (favoriteMetroStations.isNotEmpty()) {
            val favs = favoriteMetroStations.mapNotNull { id -> sortedAll.find { it.id == id } }.sortedBy { it.name }
            val others = sortedAll.filterNot { station -> favoriteMetroStations.contains(station.id) }
            favs + others
        } else {
            sortedAll
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("metrobus_tab_screen")
    ) {
        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BusFilterChip(
                selected = filterSource == BusFilterSource.FAVORITES_BUS,
                onClick = { onFilterSourceSelected(BusFilterSource.FAVORITES_BUS) },
                label = if (appLanguage == AppLanguage.CA) "Preferides" else "Favoritas",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Mis Paradas Metrobús",
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("chip_metrobus_favorites")
            )

            BusFilterChip(
                selected = filterSource == BusFilterSource.GPS_USER,
                onClick = { onFilterSourceSelected(BusFilterSource.GPS_USER) },
                label = if (appLanguage == AppLanguage.CA) "Prop de mi" else "Cerca de mí",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("chip_metrobus_gps")
            )

            BusFilterChip(
                selected = filterSource == BusFilterSource.METRO_STATION,
                onClick = { onFilterSourceSelected(BusFilterSource.METRO_STATION) },
                label = "Metro",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Subway,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("chip_metrobus_metro")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filterSource == BusFilterSource.METRO_STATION) {
            if (metroStationsList.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Selecciona l'estació de metro:" else "Selecciona estación de metro:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        metroStationsList.forEach { station ->
                            val isSelected = selectedMetroStationId == station.id
                            val isFav = favoriteMetroStations.contains(station.id)
                            BusFilterChip(
                                selected = isSelected,
                                onClick = { onSelectMetroStation(station.id) },
                                label = station.name,
                                leadingIcon = if (isFav) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("metrobus_station_chip_${station.id}")
                            )
                        }
                    }
                }
            }
        }

        if (filterSource == BusFilterSource.FAVORITES_BUS) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("metrobus_search_bar"),
                placeholder = { Text(if (appLanguage == AppLanguage.CA) "Cercar parada o línia Metrobús" else "Buscar parada o línea Metrobús") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading && metrobusStopsList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(4) {
                        SkeletonCardItem()
                    }
                }
            } else if (metrobusStopsList.isEmpty()) {
                val emptyMsg = when (filterSource) {
                    BusFilterSource.FAVORITES_BUS -> if (searchQuery.isNotEmpty()) {
                        if (appLanguage == AppLanguage.CA) "No s'han trobat parades de Metrobús actives" else "No se encontraron paradas de Metrobús activas"
                    } else {
                        if (appLanguage == AppLanguage.CA) "No tens parades de Metrobús guardades a preferides." else "No tienes paradas de Metrobús guardadas en favoritas."
                    }
                    BusFilterSource.GPS_USER -> if (appLanguage == AppLanguage.CA) "No s'han trobat parades de Metrobús en un radi proper" else "No se encontraron paradas de Metrobús en un radio cercano"
                    BusFilterSource.METRO_STATION -> if (appLanguage == AppLanguage.CA) "No s'han trobat parades de Metrobús prop de l'estació seleccionada" else "No se encontraron paradas de Metrobús cerca de la estación seleccionada"
                }
                EmptyStateCard(
                    title = if (appLanguage == AppLanguage.CA) "Sense Parades Metrobús" else "Sin Paradas Metrobús",
                    message = emptyMsg,
                    icon = Icons.Default.DirectionsBus,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + activeTripBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = listState
                ) {
                    items(metrobusStopsList, key = { it.idParada }) { stop ->
                        val isFav = favoriteMetrobusStops.contains(stop.idParada)
                        val alias = metrobusStopAliases[stop.idParada]
                        MetrobusStopCard(
                            stop = stop,
                            isFav = isFav,
                            alias = alias,
                            isDarkMode = isDarkMode,
                            onCardClick = { onSelectStop(stop) },
                            onToggleFavorite = { onToggleFavorite(stop.idParada) },
                            onEditAliasClick = { onEditAliasClick(stop) }
                        )
                    }
                }
            }
        }
    }
}

val MetrobusAmber = com.example.util.MetrobusLineColorResolver.BRAND_COLOR

@Composable
fun MetrobusStopCard(
    stop: MetrobusStop,
    isFav: Boolean,
    alias: String?,
    isDarkMode: Boolean,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditAliasClick: (() -> Unit)? = null
) {
    val cardBg = if (isFav) {
        if (isDarkMode) Color(0xFF2B1C0B) else Color(0xFFFEF3C7)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorder = if (isFav) {
        BorderStroke(1.dp, MetrobusAmber.copy(alpha = if (isDarkMode) 0.5f else 0.4f))
    } else {
        BorderStroke(1.dp, if (isDarkMode) Color(0xFF2E3545) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metrobus_stop_card_${stop.idParada}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        shape = RoundedCornerShape(16.dp)
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
                            text = buildFormattedStopName(
                                rawName = stop.denominacion,
                                primaryColor = cardTextColor,
                                secondaryColor = cardTextSecondaryColor
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Parada ${stop.idParada}${if (!stop.distanceText.isNullOrEmpty()) " • ${stop.distanceText}" else ""}",
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
                        modifier = Modifier.testTag("fav_metrobus_${stop.idParada}").fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFav) Color(0xFFFFB300) else cardTextSecondaryColor
                        )
                    }
                }
            }

            if (stop.lineas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    color = if (isDarkMode) Color(0x1F8791A6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    stop.lineas.forEach { lineCode ->
                        Surface(
                            color = MetrobusAmber,
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
                                    text = lineCode,
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
}
