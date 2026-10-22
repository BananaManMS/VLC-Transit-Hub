package com.example.ui.map.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.bus.EditValenbisiAliasDialog
import com.example.ui.map.components.MapBusTimesBottomSheet
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.map.MapViewModel
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import com.example.ui.metro.MetroViewModel
import com.example.ui.routing.PlannerLocation
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.MapDetailBottomSheetsHost(
    selectedItem: SelectedMapItem?,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    busStopAliases: Map<String, String>,
    favoriteBusStops: Set<String>,
    favoriteMetroStations: Set<String>,
    favoriteCercaniasStations: Set<String>,
    favoriteValenbisiSet: Set<String>,
    valenbisiAliasesMap: Map<String, String>,
    customFavorites: List<RecentSearch>,
    mapViewModel: MapViewModel,
    metroViewModel: MetroViewModel?,
    cercaniasViewModel: CercaniasViewModel?,
    onNavigateToMetro: ((String) -> Unit)?,
    onNavigateToCercanias: ((String) -> Unit)?,
    onNavigateToRoutePlanner: ((PlannerLocation) -> Unit)?,
    onEditBusStopAlias: (GeoportalStopEntity) -> Unit,
    onSaveFavoriteAddress: (NominatimResult) -> Unit,
    onDismissItem: () -> Unit,
    onBusStopDetailHeightPxChanged: ((Float) -> Unit)? = null,
    onMetroStationDetailHeightPxChanged: ((Float) -> Unit)? = null,
    onCercaniasStationDetailHeightPxChanged: ((Float) -> Unit)? = null,
    onValenbisiStationDetailHeightPxChanged: ((Float) -> Unit)? = null,
    onMetrobusStopDetailHeightPxChanged: ((Float) -> Unit)? = null,
    onAddressDetailHeightPxChanged: ((Float) -> Unit)? = null,
    maxExpandedSheetHeight: Dp = 560.dp,
    detailSheetState: DetailSheetState = DetailSheetState.HALF_EXPANDED,
    onDetailSheetStateChanged: (DetailSheetState) -> Unit = {},
    activeTripBottomPadding: Dp = 0.dp
) {
    val busTimes by mapViewModel.busTimes.collectAsState()
    val busTimesLoading by mapViewModel.busTimesLoading.collectAsState()
    val busTimesLoadingMore by mapViewModel.busTimesLoadingMore.collectAsState()
    val selectedBusLineFilters by mapViewModel.selectedBusLineFilters.collectAsState()

    val cercaniasDepartures by mapViewModel.cercaniasDepartures.collectAsState()
    val cercaniasDeparturesLoading by mapViewModel.cercaniasDeparturesLoading.collectAsState()
    val cercaniasAlerts by (cercaniasViewModel?.cercaniasAlerts ?: MutableStateFlow(emptyList())).collectAsState()

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (val item = selectedItem) {
            is SelectedMapItem.BusStop -> {
            val emtScheduledDeps by mapViewModel.emtScheduledDepartures.collectAsState()
            val isEmtScheduledLoaded by mapViewModel.isEmtScheduledLoaded.collectAsState()
            val emtScheduledLoading by mapViewModel.emtScheduledLoading.collectAsState()

            MapBusTimesBottomSheet(
                stop = item.emtStopModel,
                busTimes = busTimes,
                busTimesLoading = busTimesLoading,
                isDarkMode = isDarkMode,
                texts = AppTexts.get(appLanguage),
                alias = busStopAliases[item.stop.id_parada],
                onEditAliasClick = {
                    onEditBusStopAlias(item.stop)
                },
                isFavorite = favoriteBusStops.contains(item.stop.id_parada),
                onToggleFavorite = {
                    mapViewModel.toggleFavoriteBusStop(item.stop.id_parada)
                },
                onDirectionsClick = {
                    onDismissItem()
                    onNavigateToRoutePlanner?.invoke(
                        PlannerLocation(
                            title = "Parada ${item.stop.id_parada} - ${item.stop.denominacion}",
                            latitude = item.stop.lat,
                            longitude = item.stop.lon
                        )
                    )
                },
                onDismissRequest = onDismissItem,
                maxExpandedHeight = maxExpandedSheetHeight,
                sheetState = detailSheetState,
                onSheetStateChanged = onDetailSheetStateChanged,
                onHeightPxChanged = onBusStopDetailHeightPxChanged,
                selectedLineFilters = selectedBusLineFilters,
                onLineFiltersChanged = { filters ->
                    mapViewModel.setBusLineFilters(filters)
                },
                scheduledDepartures = emtScheduledDeps,
                isScheduledLoaded = isEmtScheduledLoaded,
                isScheduledLoading = emtScheduledLoading,
                onLoadScheduled = {
                    mapViewModel.fetchEmtScheduledDepartures(item.stop.id_parada, item.stop.denominacion)
                },
                onLoadMoreScheduled = {
                    mapViewModel.loadMoreEmtScheduledDepartures(item.stop.id_parada, item.stop.denominacion)
                },
                isLoadingMoreScheduled = busTimesLoadingMore,
                activeTripBottomPadding = activeTripBottomPadding,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        is SelectedMapItem.Metro -> {
            LaunchedEffect(item.station.id) {
                if (metroViewModel != null) {
                    metroViewModel.selectRealTimeStation(item.station.id)
                } else {
                    mapViewModel.fetchMetroDepartures(item.station)
                }
            }

            val sharedDepartures by (metroViewModel?.realTimeDepartures ?: mapViewModel.metroDepartures).collectAsState()
            val sharedLoading by (metroViewModel?.realTimeLoading ?: mapViewModel.metroDeparturesLoading).collectAsState()

            MetroStationBottomSheet(
                station = item.station,
                departures = sharedDepartures,
                isLoading = sharedLoading,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                texts = AppTexts.get(appLanguage),
                isFavorite = favoriteMetroStations.contains(item.station.id),
                onToggleFavorite = { mapViewModel.toggleFavoriteMetroStation(item.station.id) },
                onNavigateToMetro = { stationId ->
                    metroViewModel?.selectRealTimeStation(stationId)
                    onDismissItem()
                    onNavigateToMetro?.invoke(stationId)
                },
                metroViewModel = metroViewModel,
                onDirectionsClick = {
                    val lat = item.station.latitude ?: 0.0
                    val lon = item.station.longitude ?: 0.0
                    metroViewModel?.stopRealTimeRefreshTicker()
                    onDismissItem()
                    onNavigateToRoutePlanner?.invoke(
                        PlannerLocation(
                            title = "Metro ${item.station.name}",
                            latitude = lat,
                            longitude = lon
                        )
                    )
                },
                onDismiss = {
                    metroViewModel?.stopRealTimeRefreshTicker()
                    onDismissItem()
                },
                maxExpandedHeight = maxExpandedSheetHeight,
                sheetState = detailSheetState,
                onSheetStateChanged = onDetailSheetStateChanged,
                onHeightPxChanged = onMetroStationDetailHeightPxChanged,
                activeTripBottomPadding = activeTripBottomPadding,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        is SelectedMapItem.Cercanias -> {
            CercaniasStationBottomSheet(
                station = item.station,
                departures = cercaniasDepartures,
                isLoading = cercaniasDeparturesLoading,
                alerts = cercaniasAlerts,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                isFavorite = favoriteCercaniasStations.contains(item.station.stop_id),
                onToggleFavorite = { mapViewModel.toggleFavoriteCercaniasStation(item.station.stop_id) },
                selectedLineFilters = selectedBusLineFilters,
                onToggleLineFilter = { line ->
                    val norm = CercaniasStationHighlightManager.normalizeLineRef(line)
                    val exists = selectedBusLineFilters.any {
                        CercaniasStationHighlightManager.normalizeLineRef(it).equals(norm, ignoreCase = true)
                    }
                    val newFilters = if (exists) {
                        selectedBusLineFilters.filterNot {
                            CercaniasStationHighlightManager.normalizeLineRef(it).equals(norm, ignoreCase = true)
                        }.toSet()
                    } else {
                        selectedBusLineFilters + norm
                    }
                    mapViewModel.setBusLineFilters(newFilters)
                },
                onClearLineFilters = {
                    mapViewModel.setBusLineFilters(emptySet())
                },
                onNavigateToCercanias = { stationId ->
                    cercaniasViewModel?.selectCercaniasStation(stationId)
                    onDismissItem()
                    onNavigateToCercanias?.invoke(stationId)
                },
                onDirectionsClick = {
                    onDismissItem()
                    onNavigateToRoutePlanner?.invoke(
                        PlannerLocation(
                            title = "Estación ${item.station.displayName}",
                            latitude = item.station.lat,
                            longitude = item.station.lon
                        )
                    )
                },
                onDismiss = onDismissItem,
                maxExpandedHeight = maxExpandedSheetHeight,
                sheetState = detailSheetState,
                onSheetStateChanged = onDetailSheetStateChanged,
                onHeightPxChanged = onCercaniasStationDetailHeightPxChanged,
                activeTripBottomPadding = activeTripBottomPadding,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        is SelectedMapItem.Valenbisi -> {
            var showEditValenbisiAliasDialog by remember { mutableStateOf(false) }
            val stationNum = item.station.number.toString()
            val isFav = favoriteValenbisiSet.contains(stationNum)
            val alias = valenbisiAliasesMap[stationNum]

            if (showEditValenbisiAliasDialog) {
                EditValenbisiAliasDialog(
                    stationNumber = stationNum,
                    stationDefaultName = item.station.name,
                    currentAlias = alias ?: "",
                    appLanguage = appLanguage,
                    onSaveAlias = { newAlias ->
                        mapViewModel.saveValenbisiAlias(stationNum, newAlias)
                    },
                    onDismiss = { showEditValenbisiAliasDialog = false }
                )
            }

            ValenbisiStationBottomSheet(
                station = item.station,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                isFavorite = isFav,
                alias = alias,
                onToggleFavorite = {
                    mapViewModel.toggleFavoriteValenbisiStation(stationNum)
                },
                onEditAlias = {
                    showEditValenbisiAliasDialog = true
                },
                onDirectionsClick = {
                    onDismissItem()
                    onNavigateToRoutePlanner?.invoke(
                        PlannerLocation(
                            title = "Valenbisi ${item.station.name}",
                            latitude = item.station.latitude,
                            longitude = item.station.longitude
                        )
                    )
                },
                onDismiss = onDismissItem,
                maxExpandedHeight = maxExpandedSheetHeight,
                sheetState = detailSheetState,
                onSheetStateChanged = onDetailSheetStateChanged,
                onHeightPxChanged = onValenbisiStationDetailHeightPxChanged,
                activeTripBottomPadding = activeTripBottomPadding,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        is SelectedMapItem.MetrobusStopItem -> {
            val metrobusTimes by mapViewModel.metrobusTimes.collectAsState()
            val metrobusTimesLoading by mapViewModel.metrobusTimesLoading.collectAsState()
            val metrobusTimesLoadingMore by mapViewModel.metrobusTimesLoadingMore.collectAsState()
            val metrobusScheduledDeps by mapViewModel.metrobusScheduledDepartures.collectAsState()
            val isMetrobusScheduledLoaded by mapViewModel.isMetrobusScheduledLoaded.collectAsState()
            val metrobusScheduledLoading by mapViewModel.metrobusScheduledLoading.collectAsState()

            MapMetrobusTimesBottomSheet(
                stop = item.metrobusModel,
                times = metrobusTimes,
                isLoading = metrobusTimesLoading,
                isDarkMode = isDarkMode,
                onDismissRequest = onDismissItem,
                alias = busStopAliases[item.stop.id_parada],
                onEditAliasClick = {
                    onEditBusStopAlias(
                        GeoportalStopEntity(
                            id_parada = item.stop.id_parada,
                            denominacion = item.stop.denominacion,
                            suprimida = item.stop.suprimida,
                            lat = item.stop.lat,
                            lon = item.stop.lon,
                            lineas = item.stop.lineas
                        )
                    )
                },
                isFavorite = favoriteBusStops.contains(item.stop.id_parada),
                onToggleFavorite = { mapViewModel.toggleFavoriteBusStop(item.stop.id_parada) },
                onDirectionsClick = {
                    onDismissItem()
                    onNavigateToRoutePlanner?.invoke(
                        PlannerLocation(
                            title = "Metrobús ${item.stop.denominacion}",
                            latitude = item.stop.lat,
                            longitude = item.stop.lon
                        )
                    )
                },
                onRefresh = { mapViewModel.fetchMetrobusTimes(item.stop.id_parada) },
                maxExpandedHeight = maxExpandedSheetHeight,
                sheetState = detailSheetState,
                onSheetStateChanged = onDetailSheetStateChanged,
                onHeightPxChanged = onMetrobusStopDetailHeightPxChanged,
                selectedLineFilters = selectedBusLineFilters,
                onLineFiltersChanged = { filters ->
                    mapViewModel.setBusLineFilters(filters)
                },
                scheduledDepartures = metrobusScheduledDeps,
                isScheduledLoaded = isMetrobusScheduledLoaded,
                isScheduledLoading = metrobusScheduledLoading,
                onLoadScheduled = {
                    mapViewModel.fetchMetrobusScheduledDepartures(item.stop.id_parada)
                },
                onLoadMoreScheduled = {
                    mapViewModel.loadMoreMetrobusScheduledDepartures(item.stop.id_parada)
                },
                isLoadingMoreScheduled = metrobusTimesLoadingMore,
                appLanguage = appLanguage,
                activeTripBottomPadding = activeTripBottomPadding,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        is SelectedMapItem.Address -> {
            val homeLoc by mapViewModel.homeLocation.collectAsState()
            val workLoc by mapViewModel.workLocation.collectAsState()

            val isHome = (homeLoc != null &&
                    Math.abs(homeLoc!!.latitude - item.result.latitude) < 0.0001 &&
                    Math.abs(homeLoc!!.longitude - item.result.longitude) < 0.0001) ||
                    item.result.type.equals("home", ignoreCase = true) ||
                    item.result.placeName?.equals("Casa", ignoreCase = true) == true

            val isWork = (workLoc != null &&
                    Math.abs(workLoc!!.latitude - item.result.latitude) < 0.0001 &&
                    Math.abs(workLoc!!.longitude - item.result.longitude) < 0.0001) ||
                    item.result.type.equals("work", ignoreCase = true) ||
                    item.result.placeName?.equals("Trabajo", ignoreCase = true) == true ||
                    item.result.placeName?.equals("Feina", ignoreCase = true) == true

            val matchingFav = customFavorites.find {
                Math.abs(it.latitude - item.result.latitude) < 0.0001 &&
                Math.abs(it.longitude - item.result.longitude) < 0.0001
            }
            val isFav = matchingFav != null || isHome || isWork

            val displayAddress = when {
                isHome -> {
                    val title = homeLoc?.title?.ifBlank { "Casa" } ?: "Casa"
                    val sub = homeLoc?.subtitle ?: item.result.displayName
                    item.result.copy(
                        displayName = if (sub.isNotEmpty() && !sub.equals(title, ignoreCase = true)) "$title, $sub" else title,
                        category = "favorite",
                        type = "home",
                        placeCategory = PlaceCategory.FAVORITE,
                        placeName = title
                    )
                }
                isWork -> {
                    val title = workLoc?.title?.ifBlank { if (appLanguage == AppLanguage.CA) "Feina" else "Trabajo" } ?: if (appLanguage == AppLanguage.CA) "Feina" else "Trabajo"
                    val sub = workLoc?.subtitle ?: item.result.displayName
                    item.result.copy(
                        displayName = if (sub.isNotEmpty() && !sub.equals(title, ignoreCase = true)) "$title, $sub" else title,
                        category = "favorite",
                        type = "work",
                        placeCategory = PlaceCategory.FAVORITE,
                        placeName = title
                    )
                }
                matchingFav != null -> {
                    item.result.copy(
                        displayName = if (matchingFav.subtitle.isNotEmpty()) "${matchingFav.title}, ${matchingFav.subtitle}" else matchingFav.title,
                        category = "favorite",
                        type = "favorite",
                        placeCategory = PlaceCategory.FAVORITE,
                        placeName = matchingFav.title
                    )
                }
                else -> item.result
            }

            AddressDestinationBottomSheet(
                address = displayAddress,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                isFavorite = isFav,
                favoriteColorHex = if (isHome) (homeLoc?.colorHex ?: "#4F8CFF") else if (isWork) (workLoc?.colorHex ?: "#F59E0B") else matchingFav?.colorHex,
                onSaveFavorite = {
                    onSaveFavoriteAddress(item.result)
                },
                onNavigate = { lat, lon, title ->
                    mapViewModel.clearDestination()
                    onDismissItem()
                    onNavigateToRoutePlanner?.invoke(
                        PlannerLocation(
                            title = title,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                },
                onDismiss = {
                    mapViewModel.clearDestination()
                    onDismissItem()
                },
                maxExpandedHeight = maxExpandedSheetHeight,
                sheetState = detailSheetState,
                onSheetStateChanged = onDetailSheetStateChanged,
                onHeightPxChanged = onAddressDetailHeightPxChanged,
                activeTripBottomPadding = activeTripBottomPadding,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        is SelectedMapItem.LiveTrain -> {
            val allCercaniasStations = cercaniasViewModel?.allCercaniasStations?.collectAsState()?.value ?: emptyList()
            val stationNameMap = remember(allCercaniasStations) { allCercaniasStations.associate { it.stop_id to it.nombre } }
            com.example.ui.cercanias.LiveTrainBottomSheet(
                vehicle = item.vehicle,
                stationNameMap = stationNameMap,
                isDarkMode = isDarkMode,
                onDismiss = {
                    onDismissItem()
                },
                sheetState = detailSheetState,
                onSheetStateChanged = onDetailSheetStateChanged,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        null -> {}
    }
    }

    if (metroViewModel != null) {
        val selectedDepartureDetails by metroViewModel.selectedDepartureForDetails.collectAsState()
        val departureDetailsSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        com.example.ui.metro.DepartureDetailsBottomSheet(
            isBottomSheetVisible = selectedDepartureDetails != null,
            selectedDepartureDetails = selectedDepartureDetails,
            metroViewModel = metroViewModel,
            appLanguage = appLanguage,
            texts = AppTexts.get(appLanguage),
            isDarkMode = isDarkMode,
            sheetState = departureDetailsSheetState,
            onDismiss = { metroViewModel.dismissDepartureDetails() }
        )
    }
}
