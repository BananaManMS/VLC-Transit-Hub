package com.example.ui.map

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.ui.routing.PlannerLocation
import com.example.data.repository.DashboardRepository
import com.example.data.repository.MetroRepository
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtBusTime
import com.example.ui.metro.RealTimeDeparture
import com.example.util.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.osmdroid.util.GeoPoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale
import com.example.ui.bus.computeSearchScore
import com.example.ui.bus.computeAddressSearchScore
import com.example.ui.metro.computeMetroSearchScore
import com.example.ui.cercanias.computeCercaniasSearchScore
import com.example.ui.map.components.NearbyTransitItem
import com.example.data.repository.UnifiedSearchEngine
import kotlinx.coroutines.flow.onEach

enum class MapFilterType {
    FAVORITES,
    BUS,
    METROBUS,
    METRO,
    CERCANIAS,
    VALENBISI
}

data class MapFilter(
    val isFavorites: Boolean = true,
    val showBus: Boolean = false,
    val showMetrobus: Boolean = false,
    val showMetro: Boolean = false,
    val showCercanias: Boolean = false,
    val showValenbisi: Boolean = false
) {
    companion object {
        val FAVORITES = MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
        val BUS_ONLY = MapFilter(isFavorites = false, showBus = true, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
        val METROBUS_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = true, showMetro = false, showCercanias = false, showValenbisi = false)
        val METRO_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = true, showCercanias = false, showValenbisi = false)
        val CERCANIAS_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = true, showValenbisi = false)
        val VALENBISI_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = true)
        val SHOW_ALL = MapFilter(isFavorites = false, showBus = true, showMetrobus = false, showMetro = true, showCercanias = true, showValenbisi = true)
    }
}

sealed class SelectedMapItem {
    data class BusStop(val stop: GeoportalStopEntity, val emtStopModel: EmtBusStop) : SelectedMapItem()
    data class MetrobusStopItem(val stop: com.example.data.database.MetrobusStopEntity, val metrobusModel: com.example.ui.bus.MetrobusStop) : SelectedMapItem()
    data class Metro(val station: MetroStation) : SelectedMapItem()
    data class Cercanias(val station: com.example.data.database.CercaniasStationEntity) : SelectedMapItem()
    data class Valenbisi(val station: com.example.ui.map.components.ValenbisiStation) : SelectedMapItem()
    data class Address(val result: com.example.data.model.NominatimResult) : SelectedMapItem()
}

sealed class MapSearchResult {
    abstract val score: Double
    data class BusStop(val stop: GeoportalStopEntity, val alias: String?, override val score: Double) : MapSearchResult()
    data class Metro(val station: MetroStation, override val score: Double) : MapSearchResult()
    data class Cercanias(val station: com.example.data.database.CercaniasStationEntity, override val score: Double) : MapSearchResult()
    data class Address(val result: com.example.data.model.NominatimResult, override val score: Double) : MapSearchResult()
}

enum class MapSelectionMode {
    NORMAL,
    SELECTING_LOCATION,
    SELECTING_HOME,
    SELECTING_WORK,
    SELECTING_FOR_PLANNER_ORIGIN,
    SELECTING_FOR_PLANNER_DESTINATION
}

data class RecentSearch(
    val type: String,     // "bus", "metro", "cercanias", "address", "valenbisi"
    val id: String,       // Unique ID or "lat_lon"
    val title: String,    // Visible name
    val subtitle: String, // Secondary description
    val latitude: Double,
    val longitude: Double,
    val extraData: String? = null // Any extra details (like lines)
)

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

    // Map Selection Mode
    private val _selectionMode = MutableStateFlow(MapSelectionMode.NORMAL)
    val selectionMode: StateFlow<MapSelectionMode> = _selectionMode.asStateFlow()

    fun setSelectionMode(mode: MapSelectionMode) {
        _selectionMode.value = mode
    }

    // Recent Searches
    val recentSearches: StateFlow<List<RecentSearch>> = dashboardRepository.getPreferenceFlow("recent_searches", "[]")
        .map { json ->
            try {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home Location
    val homeLocation: StateFlow<RecentSearch?> = dashboardRepository.getPreferenceFlow("home_location", "")
        .map { json ->
            if (json.isBlank()) null
            else {
                try {
                    gson.fromJson(json, RecentSearch::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Work Location
    val workLocation: StateFlow<RecentSearch?> = dashboardRepository.getPreferenceFlow("work_location", "")
        .map { json ->
            if (json.isBlank()) null
            else {
                try {
                    gson.fromJson(json, RecentSearch::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Custom Favorites
    val customFavorites: StateFlow<List<RecentSearch>> = dashboardRepository.getPreferenceFlow("custom_favorites", "[]")
        .map { json ->
            try {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val _mapFilter = MutableStateFlow(MapFilter.FAVORITES)
    val mapFilter: StateFlow<MapFilter> = _mapFilter.asStateFlow()

    private val _selectedMapItem = MutableStateFlow<SelectedMapItem?>(null)
    val selectedMapItem: StateFlow<SelectedMapItem?> = _selectedMapItem.asStateFlow()

    private val _busStops = MutableStateFlow<List<GeoportalStopEntity>>(emptyList())
    val busStops: StateFlow<List<GeoportalStopEntity>> = _busStops.asStateFlow()

    private val _metrobusStops = MutableStateFlow<List<com.example.data.database.MetrobusStopEntity>>(emptyList())
    val metrobusStops: StateFlow<List<com.example.data.database.MetrobusStopEntity>> = _metrobusStops.asStateFlow()

    private val _metroStations = MutableStateFlow<List<MetroStation>>(emptyList())
    val metroStations: StateFlow<List<MetroStation>> = _metroStations.asStateFlow()

    private val _favoriteBusStops = MutableStateFlow<Set<String>>(emptySet())
    val favoriteBusStops: StateFlow<Set<String>> = _favoriteBusStops.asStateFlow()

    private val _favoriteMetroStations = MutableStateFlow<Set<String>>(emptySet())
    val favoriteMetroStations: StateFlow<Set<String>> = _favoriteMetroStations.asStateFlow()

    private val renfeRepository = com.example.data.repository.renfe.RenfeRepository(application, database)

    private val valenbisiRepository = com.example.data.repository.ValenbisiRepository(httpClient)

    private val _valenbisiStations = MutableStateFlow<List<com.example.ui.map.components.ValenbisiStation>>(emptyList())
    val valenbisiStations: StateFlow<List<com.example.ui.map.components.ValenbisiStation>> = _valenbisiStations.asStateFlow()

    private val _valenbisiLoading = MutableStateFlow(false)
    val valenbisiLoading: StateFlow<Boolean> = _valenbisiLoading.asStateFlow()

    private val _cercaniasStations = MutableStateFlow<List<com.example.data.database.CercaniasStationEntity>>(emptyList())
    val cercaniasStations: StateFlow<List<com.example.data.database.CercaniasStationEntity>> = _cercaniasStations.asStateFlow()

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

    private val _busTimes = MutableStateFlow<List<EmtBusTime>>(emptyList())
    val busTimes: StateFlow<List<EmtBusTime>> = _busTimes.asStateFlow()

    // Unified transit favorites from Sets (declared after all dependent properties are initialized)
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
        val cercanias = array[5] as List<com.example.data.database.CercaniasStationEntity>

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

    private val _busTimesLoading = MutableStateFlow(false)
    val busTimesLoading: StateFlow<Boolean> = _busTimesLoading.asStateFlow()

    private val _metroDepartures = MutableStateFlow<List<RealTimeDeparture>>(emptyList())
    val metroDepartures: StateFlow<List<RealTimeDeparture>> = _metroDepartures.asStateFlow()

    private val _metroDeparturesLoading = MutableStateFlow(false)
    val metroDeparturesLoading: StateFlow<Boolean> = _metroDeparturesLoading.asStateFlow()

    private val _cercaniasDepartures = MutableStateFlow<List<com.example.ui.cercanias.CercaniasDeparture>>(emptyList())
    val cercaniasDepartures: StateFlow<List<com.example.ui.cercanias.CercaniasDeparture>> = _cercaniasDepartures.asStateFlow()

    private val _cercaniasDeparturesLoading = MutableStateFlow(false)
    val cercaniasDeparturesLoading: StateFlow<Boolean> = _cercaniasDeparturesLoading.asStateFlow()

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
        busStopAliases = _busStopAliases
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

    val visibleBusStops: StateFlow<List<GeoportalStopEntity>> = combine(
        _busStops,
        _favoriteBusStops,
        _mapFilter
    ) { stops, favs, filter ->
        if (filter.isFavorites) {
            val filtered = stops.filter { it.id_parada in favs }
            if (filtered.isNotEmpty()) filtered else stops.take(15)
        } else if (filter.showBus) {
            stops
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleMetrobusStops: StateFlow<List<com.example.data.database.MetrobusStopEntity>> = combine(
        _metrobusStops,
        _favoriteBusStops,
        _mapFilter
    ) { stops, favs, filter ->
        if (filter.isFavorites) {
            val filtered = stops.filter { it.id_parada in favs }
            if (filtered.isNotEmpty()) filtered else stops.take(15)
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
            val filtered = stations.filter { it.id in favs }
            if (filtered.isNotEmpty()) filtered else stations.filter { it.id in setOf("15", "16", "14", "1", "2") }
        } else if (filter.showMetro) {
            stations
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleCercaniasStations: StateFlow<List<com.example.data.database.CercaniasStationEntity>> = combine(
        _cercaniasStations,
        _favoriteCercaniasStations,
        _mapFilter
    ) { stations, favs, filter ->
        if (filter.isFavorites) {
            val filtered = stations.filter { it.stop_id in favs }
            if (filtered.isNotEmpty()) filtered else stations.filter { it.stop_id in setOf("65000", "65300", "65100") }
        } else if (filter.showCercanias) {
            stations
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userLocation = MutableStateFlow<GeoPoint?>(null)
    val userLocation: StateFlow<GeoPoint?> = _userLocation.asStateFlow()

    private val _cameraTarget = MutableStateFlow<GeoPoint>(MapConfig.VALENCIA_CENTER)
    val cameraTarget: StateFlow<GeoPoint> = _cameraTarget.asStateFlow()

    private val _cameraZoom = MutableStateFlow(MapConfig.DEFAULT_ZOOM)
    val cameraZoom: StateFlow<Double> = _cameraZoom.asStateFlow()

    private val _cameraAnimTrigger = MutableStateFlow(0)
    val cameraAnimTrigger: StateFlow<Int> = _cameraAnimTrigger.asStateFlow()

    private var hasInitiallyCenteredOnUser = false

    private val _isFollowingUser = MutableStateFlow(false)
    val isFollowingUser: StateFlow<Boolean> = _isFollowingUser.asStateFlow()

    private val _selectedNearbyTab = MutableStateFlow(0)
    val selectedNearbyTab: StateFlow<Int> = _selectedNearbyTab.asStateFlow()

    fun setSelectedNearbyTab(tabIndex: Int) {
        _selectedNearbyTab.value = tabIndex
    }

    private val _destinationLocation = MutableStateFlow<GeoPoint?>(null)
    val destinationLocation: StateFlow<GeoPoint?> = _destinationLocation.asStateFlow()

    private val _destinationTitle = MutableStateFlow<String?>(null)
    val destinationTitle: StateFlow<String?> = _destinationTitle.asStateFlow()

    fun setDestination(geoPoint: GeoPoint, title: String) {
        _destinationLocation.value = geoPoint
        _destinationTitle.value = title
        setCameraTarget(geoPoint, 17.0)
    }

    fun clearDestination() {
        _destinationLocation.value = null
        _destinationTitle.value = null
    }

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val unifiedSearchEngine = UnifiedSearchEngine(database, geocodingRepository)

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<MapSearchResult>> = _searchQuery
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { query: String ->
            val trimmed = query.trim()
            if (trimmed.length < 2) {
                _isSearching.value = false
                flowOf(emptyList<MapSearchResult>())
            } else {
                _isSearching.value = true
                val userLoc = _userLocation.value
                unifiedSearchEngine.performSearch(
                    query = trimmed,
                    userLat = userLoc?.latitude,
                    userLon = userLoc?.longitude,
                    busStops = _busStops.value,
                    metroStations = _metroStations.value,
                    cercaniasStations = _cercaniasStations.value,
                    busStopAliases = _busStopAliases.value,
                    customFavorites = customFavorites.value,
                    favoriteBusStops = _favoriteBusStops.value + _favoriteMetrobusStops.value,
                    favoriteMetroStations = _favoriteMetroStations.value,
                    favoriteCercaniasStations = _favoriteCercaniasStations.value
                ).onEach {
                    _isSearching.value = false
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val _debouncedCameraTarget = _cameraTarget
        .debounce(200L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MapConfig.VALENCIA_CENTER)

    val nearbyValenbisiStations: StateFlow<List<com.example.ui.map.components.ValenbisiStation>> = combine(
        _debouncedCameraTarget,
        _valenbisiStations
    ) { center, stations ->
        stations.map { station ->
            val dist = LocationUtils.calculateDistanceMeters(center.latitude, center.longitude, station.latitude, station.longitude)
            val distText = if (dist >= 1000) {
                String.format("%.1f km", dist / 1000.0)
            } else {
                "${dist.toInt()} m"
            }
            station.copy(distanceMeters = dist, distanceText = distText)
        }.sortedBy { it.distanceMeters }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val nearbyTransitItems: StateFlow<List<NearbyTransitItem>> = combine(
        _debouncedCameraTarget,
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
        val busStops = array[1] as List<GeoportalStopEntity>
        val metroStations = array[2] as List<MetroStation>
        val cercaniasStations = array[3] as List<com.example.data.database.CercaniasStationEntity>
        val metrobusStops = array[4] as List<com.example.data.database.MetrobusStopEntity>
        val filter = array[5] as MapFilter
        val favBuses = array[6] as Set<String>
        val favMetros = array[7] as Set<String>
        val favCercanias = array[8] as Set<String>
        val favMetrobus = array[9] as Set<String>

        val maxBusDistance = 600.0
        val maxMetroDistance = 1000.0
        val maxCercaniasDistance = 1750.0
        val maxMetrobusDistance = 1000.0

        val busCandidates = mutableListOf<NearbyTransitItem.Bus>()
        val metroCandidates = mutableListOf<NearbyTransitItem.Metro>()
        val cercaniasCandidates = mutableListOf<NearbyTransitItem.Cercanias>()
        val metrobusCandidates = mutableListOf<NearbyTransitItem.Metrobus>()

        // 1. Bus Stops
        val includeBus = filter.isFavorites || filter.showBus
        if (includeBus) {
            busStops.forEach { stop ->
                val isFav = stop.id_parada in favBuses
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(center.latitude, center.longitude, stop.lat, stop.lon)
                    if (dist <= maxBusDistance) {
                        val linesList = (stop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { com.example.ui.bus.EmtRoute(id_linea = it, SN = it) }

                        val emtModel = com.example.ui.bus.EmtBusStop(
                            t = stop.denominacion,
                            n = stop.denominacion,
                            me = stop.denominacion,
                            utes = linesList,
                            opId = stop.id_parada,
                            ica = stop.id_parada
                        )
                        busCandidates.add(NearbyTransitItem.Bus(stop, emtModel, dist, isFavorite = isFav))
                    }
                }
            }
        }

        // 2. Metro Stations
        val includeMetro = filter.isFavorites || filter.showMetro
        if (includeMetro) {
            metroStations.forEach { station ->
                val isFav = station.id in favMetros
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(center.latitude, center.longitude, station.latitude, station.longitude)
                    if (dist <= maxMetroDistance) {
                        metroCandidates.add(NearbyTransitItem.Metro(station, dist, isFavorite = isFav))
                    }
                }
            }
        }

        // 3. Cercanias Stations
        val includeCercanias = filter.isFavorites || filter.showCercanias
        if (includeCercanias) {
            cercaniasStations.forEach { station ->
                val isFav = station.stop_id in favCercanias
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(center.latitude, center.longitude, station.lat, station.lon)
                    if (dist <= maxCercaniasDistance) {
                        cercaniasCandidates.add(NearbyTransitItem.Cercanias(station, dist, isFavorite = isFav))
                    }
                }
            }
        }

        // 4. Metrobus Stops (On hold pending GTFS)
        val includeMetrobus = false
        if (includeMetrobus) {
            metrobusStops.forEach { stop ->
                val isFav = stop.id_parada in favMetrobus
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(center.latitude, center.longitude, stop.lat, stop.lon)
                    if (dist <= maxMetrobusDistance) {
                        val linesList = (stop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        val metrobusModel = com.example.ui.bus.MetrobusStop(
                            idParada = stop.id_parada,
                            denominacion = stop.denominacion,
                            lat = stop.lat,
                            lon = stop.lon,
                            lineas = linesList
                        )
                        metrobusCandidates.add(NearbyTransitItem.Metrobus(stop, metrobusModel, dist, isFavorite = isFav))
                    }
                }
            }
        }

        val finalBuses = busCandidates.sortedWith(compareByDescending<NearbyTransitItem.Bus> { it.isFavorite }.thenBy { it.distanceMeters }).take(6)
        val finalMetros = metroCandidates.sortedWith(compareByDescending<NearbyTransitItem.Metro> { it.isFavorite }.thenBy { it.distanceMeters }).take(3)
        val finalCercanias = cercaniasCandidates.sortedWith(compareByDescending<NearbyTransitItem.Cercanias> { it.isFavorite }.thenBy { it.distanceMeters }).take(2)
        val finalMetrobus = metrobusCandidates.sortedWith(compareByDescending<NearbyTransitItem.Metrobus> { it.isFavorite }.thenBy { it.distanceMeters }).take(4)

        val combined = finalBuses + finalMetrobus + finalMetros + finalCercanias
        combined.sortedWith(compareByDescending<NearbyTransitItem> { it.isFavorite }.thenBy { it.distanceMeters })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        if (query.trim().length >= 2) {
            _isSearching.value = true
        } else {
            _isSearching.value = false
        }
        _searchQuery.value = query
    }

    fun selectItemFromSearch(result: MapSearchResult) {
        setSearchQuery("") // Clear query
        when (result) {
            is MapSearchResult.BusStop -> {
                if (!mapFilter.value.isFavorites && !mapFilter.value.showBus) {
                    setFilter(mapFilter.value.copy(showBus = true))
                }
                
                setCameraTarget(GeoPoint(result.stop.lat, result.stop.lon), 17.5)
                
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
                
                selectItem(SelectedMapItem.BusStop(result.stop, emtModel), centerCamera = false)

                val alias = result.alias
                addRecentSearch(RecentSearch(
                    type = "bus",
                    id = result.stop.id_parada,
                    title = alias ?: result.stop.denominacion,
                    subtitle = if (!alias.isNullOrBlank()) "${result.stop.denominacion} • Parada ${result.stop.id_parada}" else "Parada ${result.stop.id_parada}",
                    latitude = result.stop.lat,
                    longitude = result.stop.lon,
                    extraData = result.stop.lineas
                ))
            }
            is MapSearchResult.Metro -> {
                if (!mapFilter.value.isFavorites && !mapFilter.value.showMetro) {
                    setFilter(mapFilter.value.copy(showMetro = true))
                }
                
                setCameraTarget(GeoPoint(result.station.latitude, result.station.longitude), 17.5)
                selectItem(SelectedMapItem.Metro(result.station), centerCamera = false)

                addRecentSearch(RecentSearch(
                    type = "metro",
                    id = result.station.id,
                    title = result.station.name,
                    subtitle = "Metrovalencia",
                    latitude = result.station.latitude,
                    longitude = result.station.longitude
                ))
            }
            is MapSearchResult.Cercanias -> {
                if (!mapFilter.value.isFavorites && !mapFilter.value.showCercanias) {
                    setFilter(mapFilter.value.copy(showCercanias = true))
                }
                
                setCameraTarget(GeoPoint(result.station.lat, result.station.lon), 17.5)
                selectItem(SelectedMapItem.Cercanias(result.station), centerCamera = false)

                addRecentSearch(RecentSearch(
                    type = "cercanias",
                    id = result.station.stop_id,
                    title = result.station.nombre,
                    subtitle = "Cercanías",
                    latitude = result.station.lat,
                    longitude = result.station.lon
                ))
            }
            is MapSearchResult.Address -> {
                val geoPoint = GeoPoint(result.result.latitude, result.result.longitude)
                setDestination(geoPoint, result.result.displayName)
                selectItem(SelectedMapItem.Address(result.result), centerCamera = true)

                val isFav = result.result.type == "favorite" || result.result.category == "favorite" ||
                        customFavorites.value.any { it.latitude == result.result.latitude && it.longitude == result.result.longitude }
                val favItem = customFavorites.value.find { it.latitude == result.result.latitude && it.longitude == result.result.longitude }

                val mainTitle = favItem?.title ?: (result.result.displayName.split(",").firstOrNull()?.trim() ?: result.result.displayName)
                val subTitle = favItem?.subtitle ?: (result.result.displayName.split(",").drop(1).take(2).joinToString(", ").trim().ifEmpty { "Ubicación" })

                addRecentSearch(RecentSearch(
                    type = if (isFav) "favorite" else "address",
                    id = if (isFav) (favItem?.id ?: "fav_${result.result.latitude}_${result.result.longitude}") else "addr_${result.result.latitude}_${result.result.longitude}",
                    title = mainTitle,
                    subtitle = subTitle,
                    latitude = result.result.latitude,
                    longitude = result.result.longitude
                ))
            }
        }
    }

    init {
        dataLoader.loadData()
        favoritesHandler.reloadFavorites()
        updateLocation()
    }

    private fun saveMapFilterPreference(filter: MapFilter) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonObj = org.json.JSONObject().apply {
                    put("isFavorites", filter.isFavorites)
                    put("showBus", filter.showBus)
                    put("showMetro", filter.showMetro)
                    put("showCercanias", filter.showCercanias)
                    put("showValenbisi", filter.showValenbisi)
                }
                dashboardRepository.savePreference("map_filter_preference", jsonObj.toString())
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error saving map filter preference", e)
            }
        }
    }

    fun toggleFilter(type: MapFilterType) {
        val current = _mapFilter.value
        val updated = when (type) {
            MapFilterType.FAVORITES -> {
                MapFilter(isFavorites = true, showBus = false, showMetro = false, showCercanias = false, showValenbisi = false)
            }
            MapFilterType.BUS -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = true, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                } else {
                    val newBus = !current.showBus
                    if (!newBus && !current.showMetrobus && !current.showMetro && !current.showCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showBus = newBus)
                    }
                }
            }
            MapFilterType.METROBUS -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = true, showMetro = false, showCercanias = false, showValenbisi = false)
                } else {
                    val newMetrobus = !current.showMetrobus
                    if (!current.showBus && !newMetrobus && !current.showMetro && !current.showCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showMetrobus = newMetrobus)
                    }
                }
            }
            MapFilterType.METRO -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = true, showCercanias = false, showValenbisi = false)
                } else {
                    val newMetro = !current.showMetro
                    if (!current.showBus && !current.showMetrobus && !newMetro && !current.showCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showMetro = newMetro)
                    }
                }
            }
            MapFilterType.CERCANIAS -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = true, showValenbisi = false)
                } else {
                    val newCercanias = !current.showCercanias
                    if (!current.showBus && !current.showMetrobus && !current.showMetro && !newCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showCercanias = newCercanias)
                    }
                }
            }
            MapFilterType.VALENBISI -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = true)
                } else {
                    val newValenbisi = !current.showValenbisi
                    if (!current.showBus && !current.showMetrobus && !current.showMetro && !current.showCercanias && !newValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showValenbisi = newValenbisi)
                    }
                }
            }
        }
        setFilter(updated)
    }

    fun setFilter(filter: MapFilter) {
        _mapFilter.value = filter
        saveMapFilterPreference(filter)
    }

    fun reloadFavorites() = favoritesHandler.reloadFavorites()

    fun toggleFavoriteBusStop(stopId: String) = favoritesHandler.toggleFavoriteBusStop(stopId)

    fun toggleFavoriteMetroStation(stationId: String) = favoritesHandler.toggleFavoriteMetroStation(stationId)

    fun toggleFavoriteCercaniasStation(stationId: String) = favoritesHandler.toggleFavoriteCercaniasStation(stationId)

    fun toggleFavoriteValenbisiStation(stationNumber: String) = favoritesHandler.toggleFavoriteValenbisiStation(stationNumber)

    fun saveValenbisiAlias(stationNumber: String, alias: String) = favoritesHandler.saveValenbisiAlias(stationNumber, alias)

    fun setBusStopAlias(stopId: String, alias: String) = favoritesHandler.setBusStopAlias(stopId, alias)

    fun selectItem(item: SelectedMapItem?, centerCamera: Boolean = true) {
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
            val targetZoom = if (_cameraZoom.value < 17.0) 17.0 else _cameraZoom.value
            setCameraTarget(GeoPoint(lat, lon), targetZoom)
        }
        if (item is SelectedMapItem.BusStop) {
            fetchBusTimes(item.stop.id_parada)
            _metroDepartures.value = emptyList()
            _cercaniasDepartures.value = emptyList()
        } else if (item is SelectedMapItem.Metro) {
            fetchMetroDepartures(item.station)
            _busTimes.value = emptyList()
            _cercaniasDepartures.value = emptyList()
        } else if (item is SelectedMapItem.Cercanias) {
            fetchCercaniasDepartures(item.station.stop_id)
            _busTimes.value = emptyList()
            _metroDepartures.value = emptyList()
        } else {
            _busTimes.value = emptyList()
            _metroDepartures.value = emptyList()
            _cercaniasDepartures.value = emptyList()
        }
    }

    fun refreshValenbisiStations() = dataLoader.refreshValenbisiStations()

    fun fetchBusTimes(stopId: String) = dataLoader.fetchBusTimes(stopId)

    fun fetchMetroDepartures(station: MetroStation) = dataLoader.fetchMetroDepartures(station)

    fun fetchCercaniasDepartures(stationId: String) = dataLoader.fetchCercaniasDepartures(stationId)

    private var locationTrackingJob: Job? = null

    /**
     * Starts continuous real-time GPS tracking stream.
     * When the user moves physically, updates _userLocation and keeps camera centered if following.
     */
    fun startLocationTracking(context: Context) {
        if (locationTrackingJob?.isActive == true) return
        locationTrackingJob = viewModelScope.launch(Dispatchers.IO) {
            LocationUtils.getLocationUpdates(
                context = context.applicationContext,
                intervalMs = 12000L,
                minDistanceMeters = 10.0f
            ).collect { location ->
                withContext(Dispatchers.Main) {
                    val geo = GeoPoint(location.latitude, location.longitude)
                    _userLocation.value = geo
                    if (!hasInitiallyCenteredOnUser) {
                        hasInitiallyCenteredOnUser = true
                        _isFollowingUser.value = true
                        _cameraTarget.value = geo
                        _cameraZoom.value = 16.0
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    } else if (_isFollowingUser.value) {
                        _cameraTarget.value = geo
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    }
                }
            }
        }
    }

    fun stopLocationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = null
    }

    fun updateLocation(lat: Double, lon: Double) {
        val geo = GeoPoint(lat, lon)
        _userLocation.value = geo
        if (!hasInitiallyCenteredOnUser) {
            hasInitiallyCenteredOnUser = true
            _isFollowingUser.value = true
            _cameraTarget.value = geo
            _cameraZoom.value = 16.0
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        } else if (_isFollowingUser.value) {
            _cameraTarget.value = geo
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        }
    }

    fun updateLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            val location = LocationUtils.getBestLastLocation(getApplication())
            if (location != null) {
                val geo = GeoPoint(location.latitude, location.longitude)
                withContext(Dispatchers.Main) {
                    _userLocation.value = geo
                    if (!hasInitiallyCenteredOnUser) {
                        hasInitiallyCenteredOnUser = true
                        _isFollowingUser.value = true
                        _cameraTarget.value = geo
                        _cameraZoom.value = 16.0
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    } else if (_isFollowingUser.value) {
                        _cameraTarget.value = geo
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    }
                }
            }
        }
    }

    fun centerOnUser() {
        _isFollowingUser.value = true
        val currentLoc = _userLocation.value
        if (currentLoc != null) {
            _cameraTarget.value = currentLoc
            _cameraZoom.value = 16.0
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val location = LocationUtils.getBestLastLocation(getApplication())
                val target = if (location != null) {
                    GeoPoint(location.latitude, location.longitude).also { 
                        withContext(Dispatchers.Main) { _userLocation.value = it }
                    }
                } else {
                    _userLocation.value
                }
                withContext(Dispatchers.Main) {
                    if (target != null) {
                        _cameraTarget.value = target
                        _cameraZoom.value = 16.0
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    } else {
                        LocationUtils.requestDeviceLocation(getApplication()) { lat, lon ->
                            val geo = GeoPoint(lat, lon)
                            _userLocation.value = geo
                            _cameraTarget.value = geo
                            _cameraZoom.value = 16.0
                            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                        }
                    }
                }
            }
        }
    }

    fun disableFollowUser() {
        _isFollowingUser.value = false
    }

    fun updateCameraPosition(center: GeoPoint, zoom: Double) {
        _cameraTarget.value = center
        _cameraZoom.value = zoom
    }

    fun setCameraZoom(zoom: Double) {
        _cameraZoom.value = zoom
    }

    fun setCameraTarget(geoPoint: GeoPoint, zoom: Double = _cameraZoom.value) {
        _isFollowingUser.value = false
        _cameraTarget.value = geoPoint
        _cameraZoom.value = zoom
        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
    }

    // --- Search History & Favorites Persistence Helpers ---

    fun addRecentSearch(search: RecentSearch) {
        viewModelScope.launch {
            val currentList = recentSearches.value.toMutableList()
            // Deduplicate
            currentList.removeAll { it.id == search.id || (it.latitude == search.latitude && it.longitude == search.longitude) }
            // Add to the top
            currentList.add(0, search)
            // Limit to 10
            val limitedList = currentList.take(10)
            val json = gson.toJson(limitedList)
            dashboardRepository.savePreference("recent_searches", json)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            dashboardRepository.savePreference("recent_searches", "[]")
        }
    }

    fun removeRecentSearch(searchId: String) {
        viewModelScope.launch {
            val currentList = recentSearches.value.toMutableList()
            currentList.removeAll { it.id == searchId }
            val json = gson.toJson(currentList)
            dashboardRepository.savePreference("recent_searches", json)
        }
    }

    fun saveHomeLocation(location: RecentSearch?) {
        viewModelScope.launch {
            val json = if (location == null) "" else gson.toJson(location)
            dashboardRepository.savePreference("home_location", json)
        }
    }

    fun saveWorkLocation(location: RecentSearch?) {
        viewModelScope.launch {
            val json = if (location == null) "" else gson.toJson(location)
            dashboardRepository.savePreference("work_location", json)
        }
    }

    fun toggleCustomFavorite(location: RecentSearch) {
        viewModelScope.launch {
            val currentList = customFavorites.value.toMutableList()
            val existing = currentList.find { it.title.equals(location.title, ignoreCase = true) || (it.latitude == location.latitude && it.longitude == location.longitude) }
            if (existing != null) {
                currentList.remove(existing)
            } else {
                currentList.add(location)
            }
            val json = gson.toJson(currentList)
            dashboardRepository.savePreference("custom_favorites", json)
        }
    }

    fun saveCustomFavorite(alias: String, subtitle: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val currentList = customFavorites.value.toMutableList()
            // Remove any existing one at the same coordinates to avoid duplicates
            currentList.removeAll { it.latitude == latitude && it.longitude == longitude }
            val item = RecentSearch(
                type = "favorite",
                id = "custom_${System.currentTimeMillis()}",
                title = alias,
                subtitle = subtitle,
                latitude = latitude,
                longitude = longitude
            )
            currentList.add(item)
            val json = gson.toJson(currentList)
            dashboardRepository.savePreference("custom_favorites", json)
        }
    }

    fun deleteCustomFavorite(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val currentList = customFavorites.value.toMutableList()
            currentList.removeAll { it.latitude == latitude && it.longitude == longitude }
            val json = gson.toJson(currentList)
            dashboardRepository.savePreference("custom_favorites", json)
        }
    }

    fun confirmSelectedLocationOnMap(
        mode: MapSelectionMode,
        lat: Double,
        lon: Double,
        onLocationSelected: ((PlannerLocation) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isSearching.value = true
            geocodingRepository.reverseGeocode(lat, lon).collect { addressText ->
                _isSearching.value = false
                val finalAddress = addressText ?: "Ubicación en el mapa (${String.format(Locale.US, "%.4f, %.4f", lat, lon)})"
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
                        val nominatimResult = com.example.data.model.NominatimResult(
                            displayName = finalAddress,
                            latitude = lat,
                            longitude = lon,
                            type = "address",
                            category = "place",
                            isLocalStop = false,
                            stopId = null,
                            stopType = null
                        )
                        selectItemFromSearch(MapSearchResult.Address(nominatimResult, 1.0))
                    }
                    MapSelectionMode.SELECTING_HOME -> {
                        val homeSearch = recentSearch.copy(title = "Casa", subtitle = finalAddress)
                        saveHomeLocation(homeSearch)
                    }
                    MapSelectionMode.SELECTING_WORK -> {
                        val workSearch = recentSearch.copy(title = "Trabajo", subtitle = finalAddress)
                        saveWorkLocation(workSearch)
                    }
                    MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN,
                    MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION -> {
                        val plannerLocation = PlannerLocation(
                            title = finalAddress,
                            subtitle = "Punto en el mapa",
                            latitude = lat,
                            longitude = lon
                        )
                        addRecentSearch(recentSearch)
                        onLocationSelected?.invoke(plannerLocation)
                    }
                    else -> {}
                }
                _selectionMode.value = MapSelectionMode.NORMAL
            }
        }
    }

    fun onMapLongClick(geoPoint: GeoPoint) {
        viewModelScope.launch {
            _isSearching.value = true
            geocodingRepository.reverseGeocode(geoPoint.latitude, geoPoint.longitude).collect { addressText ->
                _isSearching.value = false
                val finalAddress = addressText ?: "Punto en el mapa (${String.format(Locale.US, "%.4f, %.4f", geoPoint.latitude, geoPoint.longitude)})"
                val nominatimResult = com.example.data.model.NominatimResult(
                    displayName = finalAddress,
                    latitude = geoPoint.latitude,
                    longitude = geoPoint.longitude,
                    type = "address",
                    category = "place",
                    isLocalStop = false,
                    stopId = null,
                    stopType = null
                )
                setDestination(geoPoint, finalAddress)
                selectItem(SelectedMapItem.Address(nominatimResult), centerCamera = true)
            }
        }
    }
}
