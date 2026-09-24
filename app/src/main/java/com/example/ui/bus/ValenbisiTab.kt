package com.example.ui.bus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.components.ValenbisiStation
import com.example.ui.theme.appCardBorder
import com.example.util.LocationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValenbisiTab(
    appLanguage: AppLanguage,
    valenbisiStations: List<ValenbisiStation>,
    favoriteValenbisi: List<String>,
    valenbisiAliases: Map<String, String>,
    filterSource: ValenbisiFilterSource,
    searchQuery: String,
    isLoading: Boolean,
    selectedMetroStationId: String?,
    userLocation: Pair<Double, Double>? = null,
    isDarkMode: Boolean = false,
    favoriteMetroStations: List<String> = emptyList(),
    allMetroStations: List<MetroStation> = emptyList(),
    onFilterSourceSelected: (ValenbisiFilterSource) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSelectMetroStation: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSaveAlias: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onUpdateLocation: ((Double, Double) -> Unit)? = null,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var stationToEditAlias by remember { mutableStateOf<ValenbisiStation?>(null) }
    var selectedStationForSheet by remember { mutableStateOf<ValenbisiStation?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    val metroStationsList = remember(favoriteMetroStations, allMetroStations) {
        val favs = favoriteMetroStations.mapNotNull { id -> allMetroStations.find { it.id == id } }
        if (favs.isNotEmpty()) favs else ValenciaMetroData.mainMetroStations
    }

    LaunchedEffect(filterSource, metroStationsList) {
        if (filterSource == ValenbisiFilterSource.METRO_STATION && metroStationsList.isNotEmpty()) {
            if (selectedMetroStationId == null || metroStationsList.none { it.id == selectedMetroStationId }) {
                onSelectMetroStation(metroStationsList.first().id)
            }
        }
    }

    LaunchedEffect(filterSource) {
        if (filterSource == ValenbisiFilterSource.NEARBY) {
            if (LocationUtils.hasLocationPermission(context)) {
                LocationUtils.requestDeviceLocation(context) { lat, lng ->
                    onUpdateLocation?.invoke(lat, lng)
                }
            }
        }
    }

    LaunchedEffect(filterSource, selectedMetroStationId, searchQuery) {
        listState.scrollToItem(0)
    }

    if (stationToEditAlias != null) {
        val st = stationToEditAlias!!
        EditValenbisiAliasDialog(
            stationNumber = st.number.toString(),
            stationDefaultName = st.name,
            currentAlias = valenbisiAliases[st.number.toString()] ?: "",
            appLanguage = appLanguage,
            onSaveAlias = { alias ->
                onSaveAlias(st.number.toString(), alias)
            },
            onDismiss = { stationToEditAlias = null }
        )
    }

    if (selectedStationForSheet != null) {
        val st = selectedStationForSheet!!
        val isFav = favoriteValenbisi.contains(st.number.toString())
        val alias = valenbisiAliases[st.number.toString()]

        ValenbisiModalBottomSheet(
            station = st,
            isDarkMode = isDarkMode,
            appLanguage = appLanguage,
            isFavorite = isFav,
            alias = alias,
            onToggleFavorite = { onToggleFavorite(st.number.toString()) },
            onEditAlias = { stationToEditAlias = st },
            onDismissRequest = { selectedStationForSheet = null }
        )
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Filter Chips (Favoritos, Cerca de mí, Metro) matching EMT style exactly
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BusFilterChip(
                    selected = filterSource == ValenbisiFilterSource.FAVORITES,
                    onClick = { onFilterSourceSelected(ValenbisiFilterSource.FAVORITES) },
                    label = if (appLanguage == AppLanguage.CA) "Preferides" else "Favoritas",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favoritas",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("valenbisi_chip_favorites")
                )

                BusFilterChip(
                    selected = filterSource == ValenbisiFilterSource.NEARBY,
                    onClick = { onFilterSourceSelected(ValenbisiFilterSource.NEARBY) },
                    label = if (appLanguage == AppLanguage.CA) "Prop de mi" else "Cerca de mí",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("valenbisi_chip_nearby")
                )

                BusFilterChip(
                    selected = filterSource == ValenbisiFilterSource.METRO_STATION,
                    onClick = { onFilterSourceSelected(ValenbisiFilterSource.METRO_STATION) },
                    label = "Metro",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Subway,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("valenbisi_chip_metro")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metro station selector if METRO_STATION filter is active
            if (filterSource == ValenbisiFilterSource.METRO_STATION) {
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
                        metroStationsList.forEach { mStation ->
                            val isSelected = selectedMetroStationId == mStation.id
                            BusFilterChip(
                                selected = isSelected,
                                onClick = { onSelectMetroStation(mStation.id) },
                                label = mStation.name,
                                modifier = Modifier.testTag("valenbisi_metro_chip_${mStation.id}")
                            )
                        }
                    }
                }
            }

            // Search TextField matching EMT style (only visible in FAVORITES mode)
            if (filterSource == ValenbisiFilterSource.FAVORITES) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("valenbisi_search_bar"),
                    placeholder = {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Cercar estació de Valenbisi" else "Buscar estación de Valenbisi"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Cercar" else "Buscar"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (appLanguage == AppLanguage.CA) "Netejar" else "Limpiar"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Filtered stations list logic
            val filteredList = remember(valenbisiStations, favoriteValenbisi, filterSource, searchQuery, selectedMetroStationId, userLocation, valenbisiAliases) {
                var list = if (userLocation != null) {
                    valenbisiStations.map { st ->
                        val dist = LocationUtils.calculateDistanceMeters(
                            st.latitude, st.longitude,
                            userLocation.first, userLocation.second
                        )
                        st.copy(
                            distanceMeters = dist,
                            distanceText = LocationUtils.formatDistance(dist)
                        )
                    }
                } else {
                    valenbisiStations
                }

                if (searchQuery.isNotBlank()) {
                    list.mapNotNull { st ->
                        val alias = valenbisiAliases[st.number.toString()]
                        val score = computeSearchScore(st.number.toString(), "${st.name} ${st.address}", searchQuery, alias)
                        if (score > 0.0) Pair(st, score) else null
                    }.sortedWith(
                        compareByDescending<Pair<ValenbisiStation, Double>> { favoriteValenbisi.contains(it.first.number.toString()) }
                            .thenByDescending { it.second }
                            .thenBy { it.first.distanceMeters }
                    ).map { it.first }
                } else {
                    when (filterSource) {
                        ValenbisiFilterSource.FAVORITES -> {
                            list.filter { favoriteValenbisi.contains(it.number.toString()) }
                                .sortedBy { it.distanceMeters }
                        }
                        ValenbisiFilterSource.NEARBY -> {
                            list.sortedWith(
                                compareByDescending<ValenbisiStation> { favoriteValenbisi.contains(it.number.toString()) }
                                    .thenBy { it.distanceMeters }
                            ).take(25)
                        }
                        ValenbisiFilterSource.METRO_STATION -> {
                            val mStation = metroStationsList.find { it.id == selectedMetroStationId }
                                ?: allMetroStations.find { it.id == selectedMetroStationId }
                                ?: ValenciaMetroData.mainMetroStations.find { it.id == selectedMetroStationId }
                            if (mStation != null) {
                                list.map { st ->
                                    val dist = LocationUtils.calculateDistanceMeters(
                                        st.latitude, st.longitude,
                                        mStation.latitude, mStation.longitude
                                    )
                                    st.copy(
                                        distanceMeters = dist,
                                        distanceText = LocationUtils.formatDistance(dist)
                                    )
                                }
                                .filter { it.distanceMeters <= 600.0 }
                                .sortedWith(
                                    compareByDescending<ValenbisiStation> { favoriteValenbisi.contains(it.number.toString()) }
                                        .thenBy { it.distanceMeters }
                                )
                            } else {
                                list
                            }
                        }
                    }
                }
            }

            if (isLoading && valenbisiStations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (valenbisiStations.isEmpty() && !isOnline) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = if (isDarkMode) Color(0xFFEF5350) else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) 
                                    "La informació de les estacions de Valenbisi requereix connexió a internet." 
                                    else "La información de las estaciones de Valenbisi requiere conexión a internet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DirectionsBike,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (filterSource) {
                                    ValenbisiFilterSource.FAVORITES -> if (appLanguage == AppLanguage.CA) "No tens cap estació de Valenbisi als teus favorits" else "No tienes ninguna estación de Valenbisi en tus favoritos"
                                    else -> if (appLanguage == AppLanguage.CA) "No s'han trobat estacions" else "No se encontraron estaciones"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + activeTripBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredList,
                        key = { it.number }
                    ) { station ->
                        val isFav = favoriteValenbisi.contains(station.number.toString())
                        val alias = valenbisiAliases[station.number.toString()]

                        ValenbisiStationCard(
                            station = station,
                            alias = alias,
                            isFavorite = isFav,
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode,
                            onToggleFavorite = { onToggleFavorite(station.number.toString()) },
                            onCardClick = { selectedStationForSheet = station }
                        )
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean {
    return this == null || this.trim().isEmpty()
}

@Composable
fun EditValenbisiAliasDialog(
    stationNumber: String,
    stationDefaultName: String,
    currentAlias: String,
    appLanguage: AppLanguage,
    onSaveAlias: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var aliasInput by remember(stationNumber, currentAlias) { mutableStateOf(currentAlias) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (appLanguage == AppLanguage.CA) "Nom personalitzat" else "Nombre personalizado",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (appLanguage == AppLanguage.CA)
                        "Assigna un nom per identificar l'estació $stationNumber ($stationDefaultName) més fàcilment:"
                    else "Asigna un nombre para identificar la estación $stationNumber ($stationDefaultName) más fácilmente:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { if (it.length <= 32) aliasInput = it },
                    label = { Text(if (appLanguage == AppLanguage.CA) "Nom/Alias (màx. 32 lletres)" else "Nombre/Alias (máx. 32 letras)") },
                    placeholder = { Text("Ej: Casa, Trabajo, Facultad...") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "${aliasInput.length}/32 ${if (appLanguage == AppLanguage.CA) "lletres" else "letras"}",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("valenbisi_alias_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAlias(aliasInput)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_valenbisi_alias_button")
            ) {
                Text(if (appLanguage == AppLanguage.CA) "Desar" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(if (appLanguage == AppLanguage.CA) "Cancel·lar" else "Cancelar")
            }
        }
    )
}
