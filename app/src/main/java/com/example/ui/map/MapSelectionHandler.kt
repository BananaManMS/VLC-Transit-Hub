package com.example.ui.map

import android.content.Context
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.NominatimResult
import com.example.ui.bus.MetrobusStop
import com.example.ui.routing.PlannerLocation
import com.example.util.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale

class MapSelectionHandler(
    private val scope: CoroutineScope,
    private val context: Context,
    private val searchManager: MapSearchManager,
    private val locationCoordinator: MapLocationCoordinator,
    private val onFetchBusTimes: (String) -> Unit,
    private val onFetchMetrobusTimes: (String) -> Unit,
    private val onFetchMetroDepartures: (com.example.data.model.MetroStation) -> Unit,
    private val onFetchCercaniasDepartures: (String) -> Unit,
    private val onClearBusTimes: () -> Unit,
    private val onClearMetrobusTimes: () -> Unit,
    private val onClearMetroDepartures: () -> Unit,
    private val onClearCercaniasDepartures: () -> Unit,
    private val onSetFilterIfHidden: (SelectedMapItem) -> Unit
) {
    private val _selectionMode = MutableStateFlow(MapSelectionMode.NORMAL)
    val selectionMode: StateFlow<MapSelectionMode> = _selectionMode.asStateFlow()

    private val _selectedMapItem = MutableStateFlow<SelectedMapItem?>(null)
    val selectedMapItem: StateFlow<SelectedMapItem?> = _selectedMapItem.asStateFlow()

    private val _destinationLocation = MutableStateFlow<GeoPoint?>(null)
    val destinationLocation: StateFlow<GeoPoint?> = _destinationLocation.asStateFlow()

    private val _destinationTitle = MutableStateFlow<String?>(null)
    val destinationTitle: StateFlow<String?> = _destinationTitle.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedBusLineFilters = MutableStateFlow<Set<String>>(emptySet())
    val selectedBusLineFilters: StateFlow<Set<String>> = _selectedBusLineFilters.asStateFlow()

    private var enrichmentJob: Job? = null

    fun setSelectionMode(mode: MapSelectionMode) {
        _selectionMode.value = mode
    }

    fun setBusLineFilters(filters: Set<String>) {
        _selectedBusLineFilters.value = filters
    }

    fun setIsSearching(searching: Boolean) {
        _isSearching.value = searching
    }

    fun setDestination(geoPoint: GeoPoint, title: String) {
        _destinationLocation.value = geoPoint
        _destinationTitle.value = title
        locationCoordinator.setCameraTarget(geoPoint, 17.0)
    }

    fun clearDestination() {
        enrichmentJob?.cancel()
        enrichmentJob = null
        _destinationLocation.value = null
        _destinationTitle.value = null
    }

    fun selectItem(item: SelectedMapItem?, centerCamera: Boolean = true) {
        _selectedBusLineFilters.value = emptySet()
        if (item == null || item !is SelectedMapItem.Address) {
            enrichmentJob?.cancel()
            enrichmentJob = null
        }
        _selectedMapItem.value = item
        if (item == null) {
            clearDestination()
        }
        if (item != null && centerCamera) {
            val (lat, lon) = when (item) {
                is SelectedMapItem.BusStop -> Pair(item.stop.lat, item.stop.lon)
                is SelectedMapItem.MetrobusStopItem -> Pair(item.stop.lat, item.stop.lon)
                is SelectedMapItem.Metro -> Pair(item.station.latitude, item.station.longitude)
                is SelectedMapItem.Cercanias -> Pair(item.station.lat, item.station.lon)
                is SelectedMapItem.Valenbisi -> Pair(item.station.latitude, item.station.longitude)
                is SelectedMapItem.Address -> Pair(item.result.latitude, item.result.longitude)
            }
            val currentZoom = locationCoordinator.cameraZoom.value
            val targetZoom = if (currentZoom < 17.0) 17.0 else currentZoom
            locationCoordinator.setCameraTarget(GeoPoint(lat, lon), targetZoom)
        }
        if (item is SelectedMapItem.BusStop) {
            onFetchBusTimes(item.stop.id_parada)
            onClearMetrobusTimes()
            onClearMetroDepartures()
            onClearCercaniasDepartures()
        } else if (item is SelectedMapItem.MetrobusStopItem) {
            onFetchMetrobusTimes(item.stop.id_parada)
            onClearBusTimes()
            onClearMetroDepartures()
            onClearCercaniasDepartures()
        } else if (item is SelectedMapItem.Metro) {
            onFetchMetroDepartures(item.station)
            onClearBusTimes()
            onClearMetrobusTimes()
            onClearCercaniasDepartures()
        } else if (item is SelectedMapItem.Cercanias) {
            onFetchCercaniasDepartures(item.station.stop_id)
            onClearBusTimes()
            onClearMetrobusTimes()
            onClearMetroDepartures()
        } else {
            onClearBusTimes()
            onClearMetrobusTimes()
            onClearMetroDepartures()
            onClearCercaniasDepartures()
        }
    }

    fun selectItemFromSearch(
        result: MapSearchResult,
        customFavorites: List<RecentSearch>,
        onClearSearchQuery: () -> Unit
    ) {
        onClearSearchQuery()
        when (result) {
            is MapSearchResult.BusStop -> {
                val linesList = (result.stop.lineas ?: "").split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { com.example.ui.bus.EmtRoute(id_linea = it, SN = it) }

                val emtModel = com.example.ui.bus.EmtBusStop(
                    t = result.stop.denominacion,
                    n = result.stop.denominacion,
                    me = result.stop.denominacion,
                    utes = linesList,
                    opId = result.stop.id_parada,
                    ica = result.stop.id_parada
                )

                val selectedItem = SelectedMapItem.BusStop(result.stop, emtModel)
                onSetFilterIfHidden(selectedItem)
                locationCoordinator.setCameraTarget(GeoPoint(result.stop.lat, result.stop.lon), 17.5)
                selectItem(selectedItem, centerCamera = false)

                val alias = result.alias
                searchManager.addRecentSearch(
                    RecentSearch(
                        type = "bus",
                        id = result.stop.id_parada,
                        title = alias ?: result.stop.denominacion,
                        subtitle = if (!alias.isNullOrBlank()) "${result.stop.denominacion} • Parada ${result.stop.id_parada}" else "Parada ${result.stop.id_parada}",
                        latitude = result.stop.lat,
                        longitude = result.stop.lon,
                        extraData = result.stop.lineas
                    )
                )
            }
            is MapSearchResult.MetrobusStop -> {
                val linesList = (result.stop.lineas ?: "").split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val metrobusStopModel = com.example.ui.bus.MetrobusStop(
                    idParada = result.stop.id_parada,
                    denominacion = result.stop.denominacion,
                    lat = result.stop.lat,
                    lon = result.stop.lon,
                    lineas = linesList
                )

                val selectedItem = SelectedMapItem.MetrobusStopItem(result.stop, metrobusStopModel)
                onSetFilterIfHidden(selectedItem)
                locationCoordinator.setCameraTarget(GeoPoint(result.stop.lat, result.stop.lon), 17.5)
                selectItem(selectedItem, centerCamera = false)

                val alias = result.alias
                searchManager.addRecentSearch(
                    RecentSearch(
                        type = "metrobus",
                        id = result.stop.id_parada,
                        title = alias ?: result.stop.denominacion,
                        subtitle = if (!alias.isNullOrBlank()) "${result.stop.denominacion} • Metrobús • Parada ${result.stop.id_parada}" else "Metrobús • Parada ${result.stop.id_parada}",
                        latitude = result.stop.lat,
                        longitude = result.stop.lon,
                        extraData = result.stop.lineas
                    )
                )
            }
            is MapSearchResult.Metro -> {
                val selectedItem = SelectedMapItem.Metro(result.station)
                onSetFilterIfHidden(selectedItem)
                locationCoordinator.setCameraTarget(GeoPoint(result.station.latitude, result.station.longitude), 17.5)
                selectItem(selectedItem, centerCamera = false)

                searchManager.addRecentSearch(
                    RecentSearch(
                        type = "metro",
                        id = result.station.id,
                        title = result.station.name,
                        subtitle = "Metrovalencia",
                        latitude = result.station.latitude,
                        longitude = result.station.longitude
                    )
                )
            }
            is MapSearchResult.Cercanias -> {
                val selectedItem = SelectedMapItem.Cercanias(result.station)
                onSetFilterIfHidden(selectedItem)
                locationCoordinator.setCameraTarget(GeoPoint(result.station.lat, result.station.lon), 17.5)
                selectItem(selectedItem, centerCamera = false)

                searchManager.addRecentSearch(
                    RecentSearch(
                        type = "cercanias",
                        id = result.station.stop_id,
                        title = result.station.nombre,
                        subtitle = "Cercanías",
                        latitude = result.station.lat,
                        longitude = result.station.lon
                    )
                )
            }
            is MapSearchResult.Address -> {
                val geoPoint = GeoPoint(result.result.latitude, result.result.longitude)
                setDestination(geoPoint, result.result.displayName)
                selectItem(SelectedMapItem.Address(result.result), centerCamera = true)

                val isFav = result.result.type == "favorite" || result.result.category == "favorite" ||
                        customFavorites.any { it.latitude == result.result.latitude && it.longitude == result.result.longitude }
                val favItem = customFavorites.find { it.latitude == result.result.latitude && it.longitude == result.result.longitude }

                val mainTitle = favItem?.title ?: (result.result.displayName.split(",").firstOrNull()?.trim() ?: result.result.displayName)
                val subTitle = favItem?.subtitle ?: (result.result.displayName.split(",").drop(1).take(2).joinToString(", ").trim().ifEmpty { "Ubicación" })

                val baseRecent = RecentSearch(
                    type = if (isFav) "favorite" else "address",
                    id = if (isFav) (favItem?.id ?: "fav_${result.result.latitude}_${result.result.longitude}") else "addr_${result.result.latitude}_${result.result.longitude}",
                    title = mainTitle,
                    subtitle = subTitle,
                    latitude = result.result.latitude,
                    longitude = result.result.longitude,
                    categoryName = result.result.placeCategory.name,
                    categoryType = "${result.result.category}:${result.result.type}",
                    placeName = result.result.placeName,
                    road = result.result.road,
                    houseNumber = result.result.houseNumber,
                    suburb = result.result.suburb,
                    city = result.result.city,
                    postcode = result.result.postcode,
                    openingHours = result.result.openingHours,
                    wheelchair = result.result.wheelchair,
                    brand = result.result.brand,
                    operator = result.result.operator,
                    phone = result.result.phone,
                    email = result.result.email,
                    website = result.result.website,
                    wikipedia = result.result.wikipedia,
                    wikidata = result.result.wikidata,
                    fee = result.result.fee,
                    charge = result.result.charge,
                    startDate = result.result.startDate,
                    historicType = result.result.historicType
                )
                searchManager.addRecentSearch(baseRecent)

                enrichmentJob?.cancel()
                if (isNetworkAvailable(context)) {
                    val targetLat = result.result.latitude
                    val targetLon = result.result.longitude
                    enrichmentJob = scope.launch {
                        searchManager.reverseGeocodeDetails(targetLat, targetLon).collect { detailed ->
                            if (detailed != null) {
                                val current = _selectedMapItem.value
                                if (current is SelectedMapItem.Address &&
                                    Math.abs(current.result.latitude - targetLat) < 0.0001 &&
                                    Math.abs(current.result.longitude - targetLon) < 0.0001
                                ) {
                                    val updatedResult = detailed.copy(
                                        displayName = if (isFav) result.result.displayName else detailed.displayName
                                    )
                                    _selectedMapItem.value = SelectedMapItem.Address(updatedResult)
                                    searchManager.addRecentSearch(baseRecent.copy(
                                        categoryName = updatedResult.placeCategory.name,
                                        categoryType = "${updatedResult.category}:${updatedResult.type}",
                                        placeName = updatedResult.placeName ?: baseRecent.placeName,
                                        road = updatedResult.road ?: baseRecent.road,
                                        houseNumber = updatedResult.houseNumber ?: baseRecent.houseNumber,
                                        suburb = updatedResult.suburb ?: baseRecent.suburb,
                                        city = updatedResult.city ?: baseRecent.city,
                                        postcode = updatedResult.postcode ?: baseRecent.postcode,
                                        openingHours = updatedResult.openingHours ?: baseRecent.openingHours,
                                        wheelchair = updatedResult.wheelchair ?: baseRecent.wheelchair,
                                        brand = updatedResult.brand ?: baseRecent.brand,
                                        operator = updatedResult.operator ?: baseRecent.operator,
                                        phone = updatedResult.phone ?: baseRecent.phone,
                                        email = updatedResult.email ?: baseRecent.email,
                                        website = updatedResult.website ?: baseRecent.website,
                                        wikipedia = updatedResult.wikipedia ?: baseRecent.wikipedia,
                                        wikidata = updatedResult.wikidata ?: baseRecent.wikidata,
                                        fee = updatedResult.fee ?: baseRecent.fee,
                                        charge = updatedResult.charge ?: baseRecent.charge,
                                        startDate = updatedResult.startDate ?: baseRecent.startDate,
                                        historicType = updatedResult.historicType ?: baseRecent.historicType
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun confirmSelectedLocationOnMap(
        mode: MapSelectionMode,
        lat: Double,
        lon: Double,
        customFavorites: List<RecentSearch>,
        onLocationSelected: ((PlannerLocation) -> Unit)? = null
    ) {
        scope.launch {
            _isSearching.value = true
            searchManager.reverseGeocode(lat, lon).collect { addressText ->
                _isSearching.value = false
                val finalAddress = addressText ?: "Ubicación en el mapa"
                val recentSearch = RecentSearch(
                    type = "address",
                    id = "map_${System.currentTimeMillis()}",
                    title = finalAddress,
                    subtitle = "Punto en el mapa",
                    latitude = lat,
                    longitude = lon
                )

                when (mode) {
                    MapSelectionMode.SELECTING_LOCATION -> {
                        val nominatimResult = NominatimResult(
                            displayName = finalAddress,
                            latitude = lat,
                            longitude = lon,
                            type = "address",
                            category = "place",
                            isLocalStop = false,
                            stopId = null,
                            stopType = null
                        )
                        selectItemFromSearch(
                            MapSearchResult.Address(nominatimResult, 1.0),
                            customFavorites,
                            onClearSearchQuery = {}
                        )
                    }
                    MapSelectionMode.SELECTING_HOME -> {
                        val homeSearch = recentSearch.copy(title = "Casa", subtitle = finalAddress)
                        searchManager.saveHomeLocation(homeSearch)
                    }
                    MapSelectionMode.SELECTING_WORK -> {
                        val workSearch = recentSearch.copy(title = "Trabajo", subtitle = finalAddress)
                        searchManager.saveWorkLocation(workSearch)
                    }
                    MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN,
                    MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION -> {
                        val plannerLocation = PlannerLocation(
                            title = finalAddress,
                            subtitle = "Punto en el mapa",
                            latitude = lat,
                            longitude = lon
                        )
                        searchManager.addRecentSearch(recentSearch)
                        onLocationSelected?.invoke(plannerLocation)
                    }
                    else -> {}
                }
                _selectionMode.value = MapSelectionMode.NORMAL
            }
        }
    }

    fun onMapLongClick(geoPoint: GeoPoint) {
        val lat = geoPoint.latitude
        val lon = geoPoint.longitude

        val initialResult = NominatimResult(
            displayName = "Ubicación seleccionada",
            latitude = lat,
            longitude = lon,
            type = "address",
            category = "place",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        setDestination(geoPoint, initialResult.displayName)
        selectItem(SelectedMapItem.Address(initialResult), centerCamera = false)

        enrichmentJob?.cancel()
        enrichmentJob = scope.launch {
            _isSearching.value = true
            try {
                searchManager.reverseGeocodeDetails(lat, lon).collect { detailedResult ->
                    _isSearching.value = false
                    if (detailedResult != null) {
                        val current = _selectedMapItem.value
                        if (current is SelectedMapItem.Address &&
                            Math.abs(current.result.latitude - lat) < 0.0001 &&
                            Math.abs(current.result.longitude - lon) < 0.0001
                        ) {
                            _selectedMapItem.value = SelectedMapItem.Address(detailedResult)
                            setDestination(geoPoint, detailedResult.displayName)
                        }
                    }
                }
            } catch (_: Exception) {
                _isSearching.value = false
            } finally {
                _isSearching.value = false
            }
        }
    }
}
