package com.example.ui.map

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.database.CercaniasStationEntity
import com.example.data.model.MetroStation
import com.example.ui.routing.PlannerLocation
import com.example.data.repository.DashboardRepository
import com.example.data.repository.MetroRepository
import com.example.data.repository.renfe.RenfeRepository
import com.example.data.repository.ValenbisiRepository
import com.example.ui.bus.EmtBusTime
import com.example.ui.cercanias.CercaniasDeparture
import com.example.ui.metro.RealTimeDeparture
import com.example.ui.map.components.NearbyTransitItem
import com.example.ui.map.components.ValenbisiStation
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dashboardRepository = DashboardRepository(application, database)
    private val metroRepository = MetroRepository(application)
    private val geocodingRepository = com.example.data.repository.GeocodingRepository(application, database)
    private val httpClient = com.example.data.network.NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Location & Camera Coordinator
    private val locationCoordinator = MapLocationCoordinator(
        scope = viewModelScope,
        context = application
    )

    // Search and Recent Searches Manager
    private val searchManager = MapSearchManager(viewModelScope, dashboardRepository, geocodingRepository, database, gson, context = application)

    // Filter Manager
    private val _mapFilter = MutableStateFlow(MapFilter.DEFAULT)
    private val filterManager = MapFilterManager(viewModelScope, dashboardRepository, _mapFilter)
    val mapFilter: StateFlow<MapFilter> = filterManager.mapFilter

    // Satellite Map Layer State
    private val _isSatelliteMode = MutableStateFlow(false)
    val isSatelliteMode: StateFlow<Boolean> = _isSatelliteMode.asStateFlow()

    fun toggleSatelliteMode() {
        _isSatelliteMode.value = !_isSatelliteMode.value
    }

    fun setSatelliteMode(enabled: Boolean) {
        _isSatelliteMode.value = enabled
    }

    // Data States
    private val _busStops = MutableStateFlow<List<GeoportalStopEntity>>(emptyList())
    val busStops: StateFlow<List<GeoportalStopEntity>> = _busStops.asStateFlow()

    private val _metrobusStops = MutableStateFlow<List<MetrobusStopEntity>>(emptyList())
    val metrobusStops: StateFlow<List<MetrobusStopEntity>> = _metrobusStops.asStateFlow()

    private val _metroStations = MutableStateFlow<List<MetroStation>>(emptyList())
    val metroStations: StateFlow<List<MetroStation>> = _metroStations.asStateFlow()

    private val _cercaniasStations = MutableStateFlow<List<CercaniasStationEntity>>(emptyList())
    val cercaniasStations: StateFlow<List<CercaniasStationEntity>> = _cercaniasStations.asStateFlow()

    private val _valenbisiStations = MutableStateFlow<List<ValenbisiStation>>(emptyList())
    val valenbisiStations: StateFlow<List<ValenbisiStation>> = _valenbisiStations.asStateFlow()

    private val _valenbisiLoading = MutableStateFlow(false)
    val valenbisiLoading: StateFlow<Boolean> = _valenbisiLoading.asStateFlow()

    private val _busTimes = MutableStateFlow<List<EmtBusTime>>(emptyList())
    val busTimes: StateFlow<List<EmtBusTime>> = _busTimes.asStateFlow()

    private val _busTimesLoading = MutableStateFlow(false)
    val busTimesLoading: StateFlow<Boolean> = _busTimesLoading.asStateFlow()

    val busTimesLoadingMore: StateFlow<Boolean> get() = dataLoader.busTimesLoadingMore.asStateFlow()

    private val _metrobusTimes = MutableStateFlow<List<com.example.ui.bus.MetrobusDepartureUiModel>>(emptyList())
    val metrobusTimes: StateFlow<List<com.example.ui.bus.MetrobusDepartureUiModel>> = _metrobusTimes.asStateFlow()

    private val _metrobusTimesLoading = MutableStateFlow(false)
    val metrobusTimesLoading: StateFlow<Boolean> = _metrobusTimesLoading.asStateFlow()
    val metrobusTimesLoadingMore: StateFlow<Boolean> get() = dataLoader.metrobusTimesLoadingMore.asStateFlow()
    val selectedMetrobusShapes: StateFlow<Map<String, List<GeoPoint>>> get() = dataLoader.selectedMetrobusShapes.asStateFlow()

    private val _metroDepartures = MutableStateFlow<List<RealTimeDeparture>>(emptyList())
    val metroDepartures: StateFlow<List<RealTimeDeparture>> = _metroDepartures.asStateFlow()

    private val _metroDeparturesLoading = MutableStateFlow(false)
    val metroDeparturesLoading: StateFlow<Boolean> = _metroDeparturesLoading.asStateFlow()

    private val _cercaniasDepartures = MutableStateFlow<List<CercaniasDeparture>>(emptyList())
    val cercaniasDepartures: StateFlow<List<CercaniasDeparture>> = _cercaniasDepartures.asStateFlow()

    private val _cercaniasDeparturesLoading = MutableStateFlow(false)
    val cercaniasDeparturesLoading: StateFlow<Boolean> = _cercaniasDeparturesLoading.asStateFlow()

    private val _liveCercaniasVehicles = MutableStateFlow<List<com.example.ui.cercanias.LiveVehicleInfo>>(emptyList())
    val liveCercaniasVehicles: StateFlow<List<com.example.ui.cercanias.LiveVehicleInfo>> = _liveCercaniasVehicles.asStateFlow()

    // Favorites States
    private val _favoriteBusStops = MutableStateFlow<Set<String>>(emptySet())
    val favoriteBusStops: StateFlow<Set<String>> = _favoriteBusStops.asStateFlow()

    private val _favoriteMetroStations = MutableStateFlow<Set<String>>(emptySet())
    val favoriteMetroStations: StateFlow<Set<String>> = _favoriteMetroStations.asStateFlow()

    private val _favoriteCercaniasStations = MutableStateFlow<Set<String>>(emptySet())
    val favoriteCercaniasStations: StateFlow<Set<String>> = _favoriteCercaniasStations.asStateFlow()

    private val _favoriteMetrobusStops = MutableStateFlow<Set<String>>(emptySet())
    val favoriteMetrobusStops: StateFlow<Set<String>> = _favoriteMetrobusStops.asStateFlow()

    private val _favoriteValenbisi = MutableStateFlow<Set<String>>(emptySet())
    val favoriteValenbisi: StateFlow<Set<String>> = _favoriteValenbisi.asStateFlow()

    private val _valenbisiAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val valenbisiAliases: StateFlow<Map<String, String>> = _valenbisiAliases.asStateFlow()

    private val _busStopAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val busStopAliases: StateFlow<Map<String, String>> = _busStopAliases.asStateFlow()

    private val _metrobusStopAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val metrobusStopAliases: StateFlow<Map<String, String>> = _metrobusStopAliases.asStateFlow()

    // Repositories
    private val renfeRepository = RenfeRepository(application, database)
    private val valenbisiRepository = ValenbisiRepository(httpClient)

    // Favorites & Data Handlers
    private val favoritesHandler = MapFavoritesHandler(
        scope = viewModelScope,
        dashboardRepository = dashboardRepository,
        database = database,
        mapFilter = _mapFilter,
        favoriteBusStops = _favoriteBusStops,
        favoriteMetroStations = _favoriteMetroStations,
        favoriteCercaniasStations = _favoriteCercaniasStations,
        favoriteMetrobusStops = _favoriteMetrobusStops,
        favoriteValenbisi = _favoriteValenbisi,
        valenbisiAliases = _valenbisiAliases,
        busStopAliases = _busStopAliases,
        metrobusStopAliases = _metrobusStopAliases
    )

    private val dataLoader = MapDataLoader(
        application = application,
        database = database,
        metroRepository = metroRepository,
        renfeRepository = renfeRepository,
        valenbisiRepository = valenbisiRepository,
        httpClient = httpClient,
        scope = viewModelScope,
        metroStations = _metroStations,
        busStops = _busStops,
        metrobusStops = _metrobusStops,
        cercaniasStations = _cercaniasStations,
        valenbisiStations = _valenbisiStations,
        valenbisiLoading = _valenbisiLoading,
        busTimes = _busTimes,
        busTimesLoading = _busTimesLoading,
        metroDepartures = _metroDepartures,
        metroDeparturesLoading = _metroDeparturesLoading,
        cercaniasDepartures = _cercaniasDepartures,
        cercaniasDeparturesLoading = _cercaniasDeparturesLoading
    )

    // Selection & Destinations Handler
    private val selectionHandler = MapSelectionHandler(
        scope = viewModelScope,
        context = application,
        searchManager = searchManager,
        locationCoordinator = locationCoordinator,
        onFetchBusTimes = { 
            dataLoader.resetEmtScheduledState()
            fetchBusTimes(it) 
        },
        onFetchMetrobusTimes = { 
            dataLoader.resetMetrobusScheduledState()
            fetchMetrobusTimes(it) 
        },
        onFetchMetroDepartures = { fetchMetroDepartures(it) },
        onFetchCercaniasDepartures = { fetchCercaniasDepartures(it) },
        onClearBusTimes = { dataLoader.clearBusTimes() },
        onClearMetrobusTimes = { 
            dataLoader.clearMetrobusTimes()
            _metrobusTimes.value = emptyList()
        },
        onClearMetroDepartures = { dataLoader.clearMetroDepartures() },
        onClearCercaniasDepartures = { dataLoader.clearCercaniasDepartures() },
        onSetFilterIfHidden = { item ->
            when (item) {
                is SelectedMapItem.BusStop -> {
                    if (!mapFilter.value.isFavorites && !mapFilter.value.showBus) {
                        setFilter(mapFilter.value.copy(showBus = true))
                    }
                }
                is SelectedMapItem.MetrobusStopItem -> {
                    if (!mapFilter.value.isFavorites && !mapFilter.value.showMetrobus) {
                        setFilter(mapFilter.value.copy(showMetrobus = true))
                    }
                }
                is SelectedMapItem.Metro -> {
                    if (!mapFilter.value.isFavorites && !mapFilter.value.showMetro) {
                        setFilter(mapFilter.value.copy(showMetro = true))
                    }
                }
                is SelectedMapItem.Cercanias -> {
                    if (!mapFilter.value.isFavorites && !mapFilter.value.showCercanias) {
                        setFilter(mapFilter.value.copy(showCercanias = true))
                    }
                }
                else -> {}
            }
        }
    )

    // Selection Mode
    val selectionMode: StateFlow<MapSelectionMode> = selectionHandler.selectionMode
    fun setSelectionMode(mode: MapSelectionMode) = selectionHandler.setSelectionMode(mode)

    // Selected Map Item & Destination
    val selectedMapItem: StateFlow<SelectedMapItem?> = selectionHandler.selectedMapItem
    val selectedBusLineFilters: StateFlow<Set<String>> = selectionHandler.selectedBusLineFilters
    val selectedDirectionFilter: StateFlow<String?> = selectionHandler.selectedDirectionFilter
    val destinationLocation: StateFlow<GeoPoint?> = selectionHandler.destinationLocation
    val destinationTitle: StateFlow<String?> = selectionHandler.destinationTitle
    fun setDestination(geoPoint: GeoPoint, title: String) = selectionHandler.setDestination(geoPoint, title)
    fun clearDestination() = selectionHandler.clearDestination()
    fun selectItem(item: SelectedMapItem?, centerCamera: Boolean = true) = selectionHandler.selectItem(item, centerCamera)
    fun setBusLineFilters(filters: Set<String>) = selectionHandler.setBusLineFilters(filters)
    fun setSelectedDirectionFilter(direction: String?) = selectionHandler.setSelectedDirectionFilter(direction)
    fun onMapLongClick(geoPoint: GeoPoint) = selectionHandler.onMapLongClick(geoPoint)

    // Location & Camera Delegations
    val userLocation: StateFlow<GeoPoint?> = locationCoordinator.userLocation
    val cameraTarget: StateFlow<GeoPoint> = locationCoordinator.cameraTarget
    val cameraZoom: StateFlow<Double> = locationCoordinator.cameraZoom
    val cameraAnimTrigger: StateFlow<Int> = locationCoordinator.cameraAnimTrigger
    val isFollowingUser: StateFlow<Boolean> = locationCoordinator.isFollowingUser

    fun startLocationTracking(context: Context) = locationCoordinator.startLocationTracking(context)
    fun stopLocationTracking() = locationCoordinator.stopLocationTracking()
    fun updateLocation(lat: Double, lon: Double) = locationCoordinator.updateLocation(lat, lon)
    fun updateLocation() = locationCoordinator.updateLocation()
    fun centerOnUser() = locationCoordinator.centerOnUser()
    fun disableFollowUser() = locationCoordinator.disableFollowUser()
    fun updateCameraPosition(center: GeoPoint, zoom: Double) = locationCoordinator.updateCameraPosition(center, zoom)
    fun setCameraZoom(zoom: Double) = locationCoordinator.setCameraZoom(zoom)
    fun setCameraTarget(geoPoint: GeoPoint, zoom: Double = locationCoordinator.cameraZoom.value) =
        locationCoordinator.setCameraTarget(geoPoint, zoom)

    // Recent Searches & Locations
    val recentSearches: StateFlow<List<RecentSearch>> = searchManager.recentSearches
    val homeLocation: StateFlow<RecentSearch?> = searchManager.homeLocation
    val workLocation: StateFlow<RecentSearch?> = searchManager.workLocation
    val customFavorites: StateFlow<List<RecentSearch>> = searchManager.customFavorites

    init {
        // Disabling live Cercanias train polling in the main map to prevent performance degradation and memory overhead.
        // Live train tracking has been migrated to the dedicated, on-demand Cercanias Live Map Dialog.
    }

    fun addRecentSearch(search: RecentSearch) = searchManager.addRecentSearch(search)
    fun clearRecentSearches() = searchManager.clearRecentSearches()
    fun removeRecentSearch(searchId: String) = searchManager.removeRecentSearch(searchId)
    fun saveHomeLocation(location: RecentSearch?) = searchManager.saveHomeLocation(location)
    fun saveWorkLocation(location: RecentSearch?) = searchManager.saveWorkLocation(location)
    fun saveCustomFavorite(
        alias: String,
        subtitle: String,
        latitude: Double,
        longitude: Double,
        showOnMap: Boolean = true,
        colorHex: String? = null,
        nominatimResult: com.example.data.model.NominatimResult? = null
    ) = searchManager.saveCustomFavorite(alias, subtitle, latitude, longitude, showOnMap, colorHex, nominatimResult)

    fun deleteCustomFavorite(latitude: Double, longitude: Double) =
        searchManager.deleteCustomFavorite(latitude, longitude)

    fun confirmSelectedLocationOnMap(
        mode: MapSelectionMode,
        lat: Double,
        lon: Double,
        onLocationSelected: ((PlannerLocation) -> Unit)? = null
    ) = selectionHandler.confirmSelectedLocationOnMap(mode, lat, lon, customFavorites.value, onLocationSelected)

    // Unified transit favorites from Sets
    val unifiedTransitFavorites: StateFlow<List<RecentSearch>> = combine(
        favoriteBusStops,
        busStops,
        favoriteMetroStations,
        metroStations,
        favoriteCercaniasStations,
        cercaniasStations
    ) { array ->
        val favBuses = array[0] as Set<String>
        val buses = array[1] as List<GeoportalStopEntity>
        val favMetros = array[2] as Set<String>
        val metros = array[3] as List<MetroStation>
        val favCercanias = array[4] as Set<String>
        val cercanias = array[5] as List<CercaniasStationEntity>

        val list = mutableListOf<RecentSearch>()

        favBuses.forEach { id ->
            buses.find { it.id_parada == id }?.let { stop ->
                val alias = _busStopAliases.value[stop.id_parada]
                list.add(RecentSearch(
                    type = "bus",
                    id = stop.id_parada,
                    title = alias ?: stop.denominacion,
                    subtitle = if (!alias.isNullOrBlank()) "${stop.denominacion} • Parada ${stop.id_parada}" else "Parada ${stop.id_parada}",
                    latitude = stop.lat,
                    longitude = stop.lon,
                    extraData = stop.lineas
                ))
            }
        }

        favMetros.forEach { id ->
            metros.find { it.id == id }?.let { station ->
                list.add(RecentSearch(
                    type = "metro",
                    id = station.id,
                    title = station.name,
                    subtitle = "Metrovalencia",
                    latitude = station.latitude,
                    longitude = station.longitude,
                    extraData = station.lines.joinToString(",")
                ))
            }
        }

        favCercanias.forEach { id ->
            cercanias.find { it.stop_id == id }?.let { station ->
                list.add(RecentSearch(
                    type = "cercanias",
                    id = station.stop_id,
                    title = station.nombre,
                    subtitle = "Cercanías",
                    latitude = station.lat,
                    longitude = station.lon
                ))
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Visible items according to filters
    val visibleBusStops: StateFlow<List<GeoportalStopEntity>> = combine(
        _busStops,
        _favoriteBusStops,
        _mapFilter
    ) { stops, favs, filter ->
        if (filter.isFavorites) {
            stops.filter { it.id_parada in favs }
        } else if (filter.showBus || filter.showMetrobus) {
            stops
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleMetrobusStops: StateFlow<List<MetrobusStopEntity>> = combine(
        _metrobusStops,
        _favoriteBusStops,
        _mapFilter
    ) { stops, favs, filter ->
        if (filter.isFavorites) {
            stops.filter { it.id_parada in favs }
        } else if (filter.showMetrobus) {
            stops
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleMetroStations: StateFlow<List<MetroStation>> = combine(
        _metroStations,
        _favoriteMetroStations,
        _mapFilter
    ) { stations, favs, filter ->
        if (filter.isFavorites) {
            stations.filter { it.id in favs }
        } else if (filter.showMetro) {
            stations
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleCercaniasStations: StateFlow<List<CercaniasStationEntity>> = combine(
        _cercaniasStations,
        _favoriteCercaniasStations,
        _mapFilter
    ) { stations, favs, filter ->
        if (filter.isFavorites) {
            stations.filter { it.stop_id in favs }
        } else if (filter.showCercanias) {
            stations
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Nearby Tab
    private val _selectedNearbyTab = MutableStateFlow(0)
    val selectedNearbyTab: StateFlow<Int> = _selectedNearbyTab.asStateFlow()

    fun setSelectedNearbyTab(tabIndex: Int) {
        _selectedNearbyTab.value = tabIndex
    }

    // Search Query and Results
    val isSearching: StateFlow<Boolean> = selectionHandler.isSearching

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<MapSearchResult>> = _searchQuery
        .debounce(250L)
        .distinctUntilChanged()
        .flatMapLatest { query: String ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                selectionHandler.setIsSearching(false)
                flowOf(emptyList<MapSearchResult>())
            } else {
                selectionHandler.setIsSearching(true)
                val userLoc = locationCoordinator.userLocation.value
                searchManager.performSearch(
                    query = trimmed,
                    userLat = userLoc?.latitude,
                    userLon = userLoc?.longitude,
                    busStops = _busStops.value,
                    metroStations = _metroStations.value,
                    cercaniasStations = _cercaniasStations.value,
                    metrobusStops = _metrobusStops.value,
                    busStopAliases = _busStopAliases.value,
                    metrobusStopAliases = _metrobusStopAliases.value,
                    customFavorites = customFavorites.value,
                    homeLocation = homeLocation.value,
                    workLocation = workLocation.value,
                    favoriteBusStops = _favoriteBusStops.value,
                    favoriteMetroStations = _favoriteMetroStations.value,
                    favoriteCercaniasStations = _favoriteCercaniasStations.value,
                    favoriteMetrobusStops = _favoriteMetrobusStops.value
                ).onCompletion {
                    selectionHandler.setIsSearching(false)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val debouncedCameraTarget: StateFlow<GeoPoint> = cameraTarget
        .debounce(200L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MapConfig.VALENCIA_CENTER)

    val nearbyValenbisiStations: StateFlow<List<ValenbisiStation>> = combine(
        debouncedCameraTarget,
        _valenbisiStations
    ) { center, stations ->
        MapNearbyCalculator.calculateNearbyValenbisi(center, stations)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val nearbyTransitItems: StateFlow<List<NearbyTransitItem>> = combine(
        debouncedCameraTarget,
        _busStops,
        _metroStations,
        _cercaniasStations,
        _metrobusStops,
        _mapFilter,
        _favoriteBusStops,
        _favoriteMetroStations,
        _favoriteCercaniasStations,
        _favoriteMetrobusStops
    ) { array ->
        val center = array[0] as GeoPoint
        val buses = array[1] as List<GeoportalStopEntity>
        val metros = array[2] as List<MetroStation>
        val cercanias = array[3] as List<CercaniasStationEntity>
        val metrobus = array[4] as List<MetrobusStopEntity>
        val filter = array[5] as MapFilter
        val favBuses = array[6] as Set<String>
        val favMetros = array[7] as Set<String>
        val favCercanias = array[8] as Set<String>
        val favMetrobus = array[9] as Set<String>

        MapNearbyCalculator.calculateNearbyTransitItems(
            center = center,
            busStops = buses,
            metroStations = metros,
            cercaniasStations = cercanias,
            metrobusStops = metrobus,
            filter = filter,
            favBuses = favBuses,
            favMetros = favMetros,
            favCercanias = favCercanias,
            favMetrobus = favMetrobus
        )
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        if (query.trim().length >= 2) {
            selectionHandler.setIsSearching(true)
        } else {
            selectionHandler.setIsSearching(false)
        }
        _searchQuery.value = query
    }

    fun selectItemFromSearch(result: MapSearchResult) {
        selectionHandler.selectItemFromSearch(
            result = result,
            customFavorites = customFavorites.value,
            onClearSearchQuery = { setSearchQuery("") }
        )
    }

    init {
        dataLoader.loadData()
        favoritesHandler.reloadFavorites()
        updateLocation()

        viewModelScope.launch {
            _mapFilter.collect { filter ->
                if (filter.showValenbisi) {
                    dataLoader.refreshValenbisiStations()
                    dataLoader.startValenbisiPeriodicRefresh()
                } else {
                    dataLoader.stopValenbisiPeriodicRefresh()
                }
            }
        }

        viewModelScope.launch {
            combine(selectedMapItem, selectedBusLineFilters) { item, filters ->
                Pair(item, filters)
            }.collect { (item, filters) ->
                if (item is SelectedMapItem.MetrobusStopItem) {
                    val lineCodes = if (filters.isNotEmpty()) {
                        filters.toList()
                    } else {
                        val dbLines = item.stop.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                        if (dbLines.isEmpty()) {
                            item.metrobusModel.lineas
                        } else {
                            dbLines
                        }
                    }
                    dataLoader.fetchMetrobusShapes(lineCodes)
                } else {
                    dataLoader.selectedMetrobusShapes.value = emptyMap()
                }
            }
        }
    }

    fun toggleFilter(type: MapFilterType) = filterManager.toggleFilter(type)
    fun setFilter(filter: MapFilter) = filterManager.setFilter(filter)

    fun reloadFavorites() = favoritesHandler.reloadFavorites()
    fun toggleFavoriteBusStop(stopId: String) = favoritesHandler.toggleFavoriteBusStop(stopId)
    fun toggleFavoriteMetroStation(stationId: String) = favoritesHandler.toggleFavoriteMetroStation(stationId)
    fun toggleFavoriteCercaniasStation(stationId: String) = favoritesHandler.toggleFavoriteCercaniasStation(stationId)
    fun toggleFavoriteValenbisiStation(stationNumber: String) = favoritesHandler.toggleFavoriteValenbisiStation(stationNumber)
    fun saveValenbisiAlias(stationNumber: String, alias: String) = favoritesHandler.saveValenbisiAlias(stationNumber, alias)
    fun setBusStopAlias(stopId: String, alias: String) = favoritesHandler.setBusStopAlias(stopId, alias)
    fun setMetrobusStopAlias(stopId: String, alias: String) = favoritesHandler.setMetrobusStopAlias(stopId, alias)

    fun refreshValenbisiStations(force: Boolean = false) = dataLoader.refreshValenbisiStations(force)
    fun startValenbisiPeriodicRefresh() = dataLoader.startValenbisiPeriodicRefresh()
    fun stopValenbisiPeriodicRefresh() = dataLoader.stopValenbisiPeriodicRefresh()
    fun fetchBusTimes(stopId: String, stopName: String? = null) = dataLoader.fetchBusTimes(stopId, stopName)
    fun loadMoreScheduledBusTimes(stopId: String) = dataLoader.loadMoreScheduledBusTimes(stopId)
    fun fetchMetroDepartures(station: MetroStation) = dataLoader.fetchMetroDepartures(station)
    fun fetchCercaniasDepartures(stationId: String) = dataLoader.fetchCercaniasDepartures(stationId)
    fun fetchMetrobusTimes(stopId: String) = dataLoader.fetchMetrobusTimes(stopId, _metrobusTimes, _metrobusTimesLoading)
    fun loadMoreScheduledMetrobusTimes(stopId: String) = dataLoader.loadMoreScheduledMetrobusTimes(stopId, _metrobusTimes)

    // Scheduled departures flows and triggers
    val emtScheduledDepartures = dataLoader.emtScheduledDepartures
    val emtScheduledLoading = dataLoader.emtScheduledLoading
    val isEmtScheduledLoaded = dataLoader.isEmtScheduledLoaded
    fun fetchEmtScheduledDepartures(stopId: String, stopName: String? = null) = dataLoader.fetchEmtScheduledDepartures(stopId, stopName)
    fun loadMoreEmtScheduledDepartures(stopId: String, stopName: String? = null) = dataLoader.fetchEmtScheduledDepartures(stopId, stopName, isLoadMore = true)

    val metrobusScheduledDepartures = dataLoader.metrobusScheduledDepartures
    val metrobusScheduledLoading = dataLoader.metrobusScheduledLoading
    val isMetrobusScheduledLoaded = dataLoader.isMetrobusScheduledLoaded
    fun fetchMetrobusScheduledDepartures(stopId: String) = dataLoader.fetchMetrobusScheduledDepartures(stopId)
    fun loadMoreMetrobusScheduledDepartures(stopId: String) = dataLoader.fetchMetrobusScheduledDepartures(stopId, isLoadMore = true)
}
