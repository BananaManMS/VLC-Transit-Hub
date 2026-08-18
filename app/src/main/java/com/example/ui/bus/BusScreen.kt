package com.example.ui.bus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.SkeletonCardItem
import com.example.ui.components.EmptyStateCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.theme.UnifiedTabRow
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.theme.ScreenHeader
import com.example.util.LocationUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmtBusScreen(
    viewModel: DashboardViewModel,
    busViewModel: BusViewModel = viewModel(),
    metroViewModel: com.example.ui.metro.MetroViewModel = viewModel(),
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true
) {
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val context = LocalContext.current
    
    val currentFilter by busViewModel.currentBusFilterSource.collectAsState()
    val searchQuery by busViewModel.busSearchQuery.collectAsState()
    val selectedMetroStationId by busViewModel.selectedMetroStationIdForBus.collectAsState()
    val favoriteMetroStations by metroViewModel.sortedFavoriteStations.collectAsState()
    val allMetroStations by metroViewModel.allNetworkStations.collectAsState()
    
    val busStopsList by busViewModel.busStopsList.collectAsState()
    val busStopsLoading by busViewModel.busStopsLoading.collectAsState()
    val favoriteBusStops by busViewModel.favoriteBusStops.collectAsState()
    val busStopAliases by busViewModel.busStopAliases.collectAsState()
    
    val selectedBusStop by busViewModel.selectedBusStop.collectAsState()
    val busTimes by busViewModel.busTimes.collectAsState()
    val busTimesLoading by busViewModel.busTimesLoading.collectAsState()

    // Metrobus State
    val favoriteMetrobusStops by busViewModel.favoriteMetrobusStops.collectAsState()
    val metrobusStopAliases by busViewModel.metrobusStopAliases.collectAsState()
    val metrobusSearchQuery by busViewModel.metrobusSearchQuery.collectAsState()
    val metrobusStopsList by busViewModel.metrobusStopsList.collectAsState()
    val metrobusStopsLoading by busViewModel.metrobusStopsLoading.collectAsState()
    val selectedMetrobusStop by busViewModel.selectedMetrobusStop.collectAsState()
    val metrobusTimes by busViewModel.metrobusTimes.collectAsState()
    val metrobusTimesLoading by busViewModel.metrobusTimesLoading.collectAsState()

    // Valenbisi State
    val favoriteValenbisi by busViewModel.favoriteValenbisi.collectAsState()
    val valenbisiAliases by busViewModel.valenbisiAliases.collectAsState()
    val valenbisiFilterSource by busViewModel.currentValenbisiFilterSource.collectAsState()
    val valenbisiSearchQuery by busViewModel.valenbisiSearchQuery.collectAsState()
    val valenbisiStations by busViewModel.valenbisiStations.collectAsState()
    val valenbisiLoading by busViewModel.valenbisiLoading.collectAsState()
    val selectedMetroStationIdForValenbisi by busViewModel.selectedMetroStationIdForValenbisi.collectAsState()
    
    var showTimesSheet by remember { mutableStateOf(false) }
    var showMetrobusTimesSheet by remember { mutableStateOf(false) }
    var editingStopForAlias by remember { mutableStateOf<EmtBusStop?>(null) }
    var editingMetrobusStopForAlias by remember { mutableStateOf<MetrobusStop?>(null) }

    val isBusBackHandlerEnabled = showTimesSheet || showMetrobusTimesSheet || searchQuery.isNotEmpty() || metrobusSearchQuery.isNotEmpty() || valenbisiSearchQuery.isNotEmpty()

    BackHandler(enabled = isBusBackHandlerEnabled) {
        when {
            showTimesSheet -> {
                showTimesSheet = false
                busViewModel.selectBusStop(null)
            }
            showMetrobusTimesSheet -> {
                showMetrobusTimesSheet = false
                busViewModel.selectMetrobusStop(null)
            }
            searchQuery.isNotEmpty() -> {
                busViewModel.setBusSearchQuery("")
            }
            metrobusSearchQuery.isNotEmpty() -> {
                busViewModel.setMetrobusSearchQuery("")
            }
            valenbisiSearchQuery.isNotEmpty() -> {
                busViewModel.setValenbisiSearchQuery("")
            }
        }
    }
    
    val dashboardLocation by viewModel.lastLocation.collectAsState()
    val busLocation by busViewModel.lastLocation.collectAsState()
    val userLocation = busLocation ?: dashboardLocation

    LaunchedEffect(Unit) {
        if (LocationUtils.hasLocationPermission(context)) {
            LocationUtils.requestDeviceLocation(context) { lat, lng ->
                viewModel.updateLocation(lat, lng)
                busViewModel.updateLocation(lat, lng)
            }
        }
    }

    LaunchedEffect(dashboardLocation) {
        dashboardLocation?.let { (lat, lon) ->
            busViewModel.updateLocation(lat, lon)
            busViewModel.loadBusStops()
            busViewModel.loadMetrobusStops()
        }
    }

    LaunchedEffect(currentFilter) {
        if (currentFilter == BusFilterSource.GPS_USER) {
            LocationUtils.requestDeviceLocation(context) { lat, lng ->
                viewModel.updateLocation(lat, lng)
                busViewModel.updateLocation(lat, lng)
                busViewModel.loadBusStops()
                busViewModel.loadMetrobusStops()
            }
        }
    }

    val busListState = rememberLazyListState()
    val metrobusListState = rememberLazyListState()

    LaunchedEffect(currentFilter, searchQuery, selectedMetroStationId) {
        busViewModel.loadBusStops()
        busListState.scrollToItem(0)
    }

    LaunchedEffect(currentFilter, metrobusSearchQuery, selectedMetroStationId) {
        busViewModel.loadMetrobusStops()
        try {
            metrobusListState.scrollToItem(0)
        } catch (e: Exception) {}
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val busTimesState by busViewModel.busTimes.collectAsState()
    LaunchedEffect(selectedBusStop) {
        if (selectedBusStop != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                val stopId = selectedBusStop!!.opId
                while (true) {
                    busViewModel.fetchBusTimes(stopId)

                    // Adaptive polling delay based on closest bus arrival
                    val minMins = busTimesState.mapNotNull { it.minutos.toIntOrNull() }.minOfOrNull { it } ?: 999
                    val nextDelayMs = when {
                        minMins < 3 -> 20000L   // <3 min -> 20s
                        minMins <= 10 -> 30000L // 3 to 10 min -> 30s
                        else -> 60000L          // >10 min -> 60s
                    }
                    kotlinx.coroutines.delay(nextDelayMs)
                }
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1 && valenbisiStations.isEmpty()) {
            busViewModel.fetchValenbisiStations()
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("emt_bus_screen")
    ) {
        ScreenHeader(
            title = texts.headerBusTitle,
            subtitle = texts.headerBusSubtitle
        )

        UnifiedTabRow(
            selectedTabIndex = pagerState.currentPage,
            tabs = listOf("EMT", "Valenbisi"),
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BusFilterChip(
                                selected = currentFilter == BusFilterSource.FAVORITES_BUS,
                                onClick = { busViewModel.setBusFilterSource(BusFilterSource.FAVORITES_BUS) },
                                label = if (appLanguage == AppLanguage.CA) "Preferides" else "Favoritas",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Mis Paradas",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("chip_filter_favorites")
                            )
                            
                            BusFilterChip(
                                selected = currentFilter == BusFilterSource.GPS_USER,
                                onClick = { 
                                    busViewModel.setBusFilterSource(BusFilterSource.GPS_USER)
                                    LocationUtils.requestDeviceLocation(context) { lat, lng ->
                                        viewModel.updateLocation(lat, lng)
                                        busViewModel.updateLocation(lat, lng)
                                        busViewModel.loadBusStops()
                                    }
                                },
                                label = if (appLanguage == AppLanguage.CA) "Prop de mi" else "Cerca de mí",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("chip_filter_gps")
                            )
                            
                            BusFilterChip(
                                selected = currentFilter == BusFilterSource.METRO_STATION,
                                onClick = { busViewModel.setBusFilterSource(BusFilterSource.METRO_STATION) },
                                label = "Metro",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Subway,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("chip_filter_metro")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (currentFilter == BusFilterSource.METRO_STATION) {
                            val favStationsList = remember(favoriteMetroStations, allMetroStations) {
                                favoriteMetroStations.mapNotNull { id -> allMetroStations.find { it.id == id } }
                            }
                            if (favStationsList.isNotEmpty()) {
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
                                        favStationsList.forEach { station ->
                                            val isSelected = selectedMetroStationId == station.id
                                            BusFilterChip(
                                                selected = isSelected,
                                                onClick = { busViewModel.selectMetroStationForBus(station.id) },
                                                label = station.name,
                                                modifier = Modifier.testTag("metro_station_chip_${station.id}")
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = texts.addMetroFavDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        
                        if (currentFilter == BusFilterSource.FAVORITES_BUS) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { busViewModel.setBusSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .testTag("bus_search_bar"),
                                placeholder = { Text(if (appLanguage == AppLanguage.CA) "Cercar parada" else "Buscar parada") },
                                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = if (appLanguage == AppLanguage.CA) "Cercar" else "Buscar") },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { busViewModel.setBusSearchQuery("") }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = if (appLanguage == AppLanguage.CA) "Netejar" else "Limpiar")
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
                            if (busStopsLoading && busStopsList.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    repeat(4) {
                                        SkeletonCardItem()
                                    }
                                }
                            } else if (busStopsList.isEmpty()) {
                                val emptyMsg = when (currentFilter) {
                                    BusFilterSource.FAVORITES_BUS -> if (searchQuery.isNotEmpty()) {
                                        if (appLanguage == AppLanguage.CA) "No s'han trobat parades actives" else "No se encontraron paradas activas"
                                    } else {
                                        texts.noFavStopsSaved
                                    }
                                    BusFilterSource.GPS_USER -> if (appLanguage == AppLanguage.CA) "No s'han trobat parades en un radi de 500m" else "No se encontraron paradas en un radio de 500m"
                                    BusFilterSource.METRO_STATION -> if (appLanguage == AppLanguage.CA) "No s'han trobat parades prop de l'estació seleccionada" else "No se encontraron paradas cerca de la estación seleccionada"
                                }
                                EmptyStateCard(
                                    title = if (appLanguage == AppLanguage.CA) "Sense Parades" else "Sin Paradas",
                                    message = emptyMsg,
                                    icon = Icons.Default.DirectionsBus,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    state = busListState
                                ) {
                                    items(busStopsList, key = { it.opId }) { stop ->
                                        val isFav = favoriteBusStops.contains(stop.opId)
                                        val alias = busStopAliases[stop.opId]
                                        BusStopCard(
                                            stop = stop,
                                            isFav = isFav,
                                            alias = alias,
                                            isDarkMode = isDarkMode,
                                            onCardClick = {
                                                busViewModel.selectBusStop(stop)
                                                showTimesSheet = true
                                            },
                                            onToggleFavorite = {
                                                busViewModel.toggleFavoriteBusStop(stop.opId)
                                            },
                                            onEditAliasClick = {
                                                editingStopForAlias = stop
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    ValenbisiTab(
                        appLanguage = appLanguage,
                        valenbisiStations = valenbisiStations,
                        favoriteValenbisi = favoriteValenbisi,
                        valenbisiAliases = valenbisiAliases,
                        filterSource = valenbisiFilterSource,
                        searchQuery = valenbisiSearchQuery,
                        isLoading = valenbisiLoading,
                        selectedMetroStationId = selectedMetroStationIdForValenbisi,
                        userLocation = userLocation,
                        isDarkMode = isDarkMode,
                        favoriteMetroStations = favoriteMetroStations,
                        allMetroStations = allMetroStations,
                        onFilterSourceSelected = { busViewModel.setValenbisiFilterSource(it) },
                        onSearchQueryChanged = { busViewModel.setValenbisiSearchQuery(it) },
                        onSelectMetroStation = { busViewModel.selectMetroStationForValenbisi(it) },
                        onToggleFavorite = { busViewModel.toggleValenbisiFavorite(it) },
                        onSaveAlias = { stationNumber, alias -> busViewModel.saveValenbisiAlias(stationNumber, alias) },
                        onRefresh = { busViewModel.fetchValenbisiStations() },
                        onUpdateLocation = { lat, lng ->
                            viewModel.updateLocation(lat, lng)
                            busViewModel.updateLocation(lat, lng)
                        }
                    )
                }
            }
        }
    }
    
    if (showTimesSheet && selectedBusStop != null) {
        BusTimesBottomSheet(
            stop = selectedBusStop!!,
            busTimes = busTimes,
            busTimesLoading = busTimesLoading,
            isDarkMode = isDarkMode,
            texts = texts,
            alias = busStopAliases[selectedBusStop!!.opId],
            isFavorite = favoriteBusStops.contains(selectedBusStop!!.opId),
            onToggleFavorite = {
                busViewModel.toggleFavoriteBusStop(selectedBusStop!!.opId)
            },
            onDismissRequest = {
                showTimesSheet = false
                busViewModel.selectBusStop(null)
            },
            onEditAliasClick = {
                editingStopForAlias = selectedBusStop
            }
        )
    }

    if (showMetrobusTimesSheet && selectedMetrobusStop != null) {
        MetrobusTimesBottomSheet(
            stop = selectedMetrobusStop!!,
            times = metrobusTimes,
            isLoading = metrobusTimesLoading,
            isDarkMode = isDarkMode,
            alias = metrobusStopAliases[selectedMetrobusStop!!.idParada],
            isFavorite = favoriteMetrobusStops.contains(selectedMetrobusStop!!.idParada),
            onToggleFavorite = {
                busViewModel.toggleFavoriteMetrobusStop(selectedMetrobusStop!!.idParada)
            },
            onDismissRequest = {
                showMetrobusTimesSheet = false
                busViewModel.selectMetrobusStop(null)
            },
            onEditAliasClick = {
                editingMetrobusStopForAlias = selectedMetrobusStop
            },
            onRefresh = {
                busViewModel.fetchMetrobusTimes(selectedMetrobusStop!!.idParada)
            }
        )
    }

    if (editingStopForAlias != null) {
        val stopToEdit = editingStopForAlias!!
        val existingAlias = busStopAliases[stopToEdit.opId] ?: ""
        var aliasInput by remember(editingStopForAlias) { mutableStateOf(existingAlias) }

        AlertDialog(
            onDismissRequest = { editingStopForAlias = null },
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
                            "Assigna un nom per identificar la Parada ${stopToEdit.opId} més fàcilment:" 
                            else "Asigna un nombre para identificar la Parada ${stopToEdit.opId} más fácilmente:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { if (it.length <= 32) aliasInput = it },
                        label = { Text(if (appLanguage == AppLanguage.CA) "Nom/Alias (màx. 32 lletres)" else "Nombre/Alias (máx. 32 letras)") },
                        placeholder = { Text("Ej: Casa, Trabajo, Universidad...") },
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
                            .testTag("alias_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        busViewModel.setBusStopAlias(stopToEdit.opId, aliasInput)
                        editingStopForAlias = null
                    },
                    modifier = Modifier.testTag("save_alias_button")
                ) {
                    Text(if (appLanguage == AppLanguage.CA) "Desar" else "Guardar")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existingAlias.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                busViewModel.setBusStopAlias(stopToEdit.opId, "")
                                editingStopForAlias = null
                            }
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Esborrar nom" else "Eliminar nombre",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = { editingStopForAlias = null }) {
                        Text(if (appLanguage == AppLanguage.CA) "Cancel·lar" else "Cancelar")
                    }
                }
            }
        )
    }

    if (editingMetrobusStopForAlias != null) {
        val stopToEdit = editingMetrobusStopForAlias!!
        val existingAlias = metrobusStopAliases[stopToEdit.idParada] ?: ""
        var aliasInput by remember(editingMetrobusStopForAlias) { mutableStateOf(existingAlias) }

        AlertDialog(
            onDismissRequest = { editingMetrobusStopForAlias = null },
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
                            "Assigna un nom per identificar la parada ${stopToEdit.denominacion} de Metrobús més fàcilment:" 
                            else "Asigna un nombre para identificar la parada ${stopToEdit.denominacion} de Metrobús más fácilmente:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { if (it.length <= 32) aliasInput = it },
                        label = { Text(if (appLanguage == AppLanguage.CA) "Nom/Alias (màx. 32 lletres)" else "Nombre/Alias (máx. 32 letras)") },
                        placeholder = { Text("Ej: Casa, Trabajo, Universidad...") },
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
                            .testTag("metrobus_alias_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        busViewModel.setMetrobusStopAlias(stopToEdit.idParada, aliasInput)
                        editingMetrobusStopForAlias = null
                    },
                    modifier = Modifier.testTag("save_metrobus_alias_button")
                ) {
                    Text(if (appLanguage == AppLanguage.CA) "Desar" else "Guardar")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existingAlias.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                busViewModel.setMetrobusStopAlias(stopToEdit.idParada, "")
                                editingMetrobusStopForAlias = null
                            }
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Esborrar nom" else "Eliminar nombre",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = { editingMetrobusStopForAlias = null }) {
                        Text(if (appLanguage == AppLanguage.CA) "Cancel·lar" else "Cancelar")
                    }
                }
            }
        )
    }
}



