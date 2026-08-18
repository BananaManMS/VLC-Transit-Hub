package com.example.ui.routing

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.NominatimResult
import com.example.data.model.routing.PlannedItinerary
import com.example.data.repository.DashboardRepository
import com.example.data.repository.GeocodingRepository
import com.example.data.repository.MetroAlertsRepository
import com.example.data.repository.UnifiedSearchEngine
import com.example.data.repository.routing.HybridRoutingRepository
import com.example.ui.common.search.mapSearchResultToPlannerLocation
import com.example.ui.common.search.recentSearchToSearchResult
import com.example.ui.map.MapSearchResult
import com.example.ui.map.RecentSearch
import com.example.util.LocationUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PlannerSearchField {
    NONE,
    ORIGIN,
    DESTINATION
}

class RoutePlannerViewModel @JvmOverloads constructor(
    application: Application,
    private val hybridRoutingRepository: HybridRoutingRepository = HybridRoutingRepository(
        metroAlertsRepository = MetroAlertsRepository(),
        context = application.applicationContext
    ),
    private val geocodingRepository: GeocodingRepository = GeocodingRepository(
        application.applicationContext,
        AppDatabase.getDatabase(application.applicationContext)
    )
) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dashboardRepository = DashboardRepository(application, database)
    private val unifiedSearchEngine = UnifiedSearchEngine(database, geocodingRepository)
    private val gson = Gson()

    private val _origin = MutableStateFlow<PlannerLocation?>(null)
    val origin: StateFlow<PlannerLocation?> = _origin.asStateFlow()

    private val _destination = MutableStateFlow<PlannerLocation?>(null)
    val destination: StateFlow<PlannerLocation?> = _destination.asStateFlow()

    private val _originQuery = MutableStateFlow("")
    val originQuery: StateFlow<String> = _originQuery.asStateFlow()

    private val _destinationQuery = MutableStateFlow("")
    val destinationQuery: StateFlow<String> = _destinationQuery.asStateFlow()

    private val _activeSearchField = MutableStateFlow(PlannerSearchField.NONE)
    val activeSearchField: StateFlow<PlannerSearchField> = _activeSearchField.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MapSearchResult>>(emptyList())
    val searchResults: StateFlow<List<MapSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Backward compatibility flows
    val isSearchingOrigin: StateFlow<Boolean> = _isSearching.map { it && _activeSearchField.value == PlannerSearchField.ORIGIN }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isSearchingDestination: StateFlow<Boolean> = _isSearching.map { it && _activeSearchField.value == PlannerSearchField.DESTINATION }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Search History and Favorites from unified dashboard preferences
    val recentSearches: StateFlow<List<RecentSearch>> = dashboardRepository.getPreferenceFlow("recent_searches", "[]")
        .map { json ->
            try {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeLocation: StateFlow<RecentSearch?> = dashboardRepository.getPreferenceFlow("home_location", "")
        .map { json ->
            if (json.isBlank()) null
            else try {
                gson.fromJson(json, RecentSearch::class.java)
            } catch (e: Exception) {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workLocation: StateFlow<RecentSearch?> = dashboardRepository.getPreferenceFlow("work_location", "")
        .map { json ->
            if (json.isBlank()) null
            else try {
                gson.fromJson(json, RecentSearch::class.java)
            } catch (e: Exception) {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customFavorites: StateFlow<List<RecentSearch>> = dashboardRepository.getPreferenceFlow("custom_favorites", "[]")
        .map { json ->
            try {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transit Favorites flow
    val favoriteBusStopsSet = dashboardRepository.getPreferenceFlow("favorite_bus_stops", "")
        .map { favs -> if (favs.isNotEmpty()) favs.split(",").map { it.trim() }.toSet() else setOf("1001", "1002", "1500", "2000", "70", "80") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteMetroStationsSet = dashboardRepository.getPreferenceFlow("favorite_stations", "16,15,14")
        .map { favs -> if (favs.isNotEmpty()) favs.split(",").map { it.trim() }.toSet() else setOf("15", "16", "14", "1", "2") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteCercaniasStationsSet = dashboardRepository.getPreferenceFlow("favorite_cercanias_stations", "")
        .map { favs -> if (favs.isNotEmpty()) favs.split(",").map { it.trim() }.toSet() else setOf("65000", "65300", "65100") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val unifiedTransitFavorites: StateFlow<List<RecentSearch>> = combine(
        favoriteBusStopsSet,
        favoriteMetroStationsSet,
        favoriteCercaniasStationsSet
    ) { favBuses, favMetros, favCercanias ->
        val list = mutableListOf<RecentSearch>()
        try {
            val busStops = database.geoportalStopDao().getAllActiveStops()
            val metroStations = database.stationDao().getAllStations()
            val cercaniasStations = database.cercaniasStationDao().getAllStations()

            favBuses.forEach { id ->
                busStops.find { it.id_parada == id }?.let { stop ->
                    list.add(RecentSearch(
                        type = "bus",
                        id = stop.id_parada,
                        title = stop.denominacion,
                        subtitle = "Parada ${stop.id_parada}",
                        latitude = stop.lat,
                        longitude = stop.lon,
                        extraData = stop.lineas
                    ))
                }
            }

            favMetros.forEach { id ->
                metroStations.find { it.id.toString() == id }?.let { station ->
                    list.add(RecentSearch(
                        type = "metro",
                        id = station.id.toString(),
                        title = station.name,
                        subtitle = "Metrovalencia",
                        latitude = station.latitude ?: 39.4697,
                        longitude = station.longitude ?: -0.3734,
                        extraData = station.lines
                    ))
                }
            }

            favCercanias.forEach { id ->
                cercaniasStations.find { it.stop_id == id }?.let { station ->
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
        } catch (e: Exception) {
            // Ignore on initial load
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedModeFilters = MutableStateFlow<Set<RouteModeFilter>>(emptySet())
    val selectedModeFilters: StateFlow<Set<RouteModeFilter>> = _selectedModeFilters.asStateFlow()

    private val _fewestTransfers = MutableStateFlow(false)
    val fewestTransfers: StateFlow<Boolean> = _fewestTransfers.asStateFlow()

    private val _departureType = MutableStateFlow(DepartureType.LEAVE_NOW)
    val departureType: StateFlow<DepartureType> = _departureType.asStateFlow()

    private val _selectedTime = MutableStateFlow<String?>(null) // "HH:mm"
    val selectedTime: StateFlow<String?> = _selectedTime.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null) // "yyyy-MM-dd"
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _uiState = MutableStateFlow<RoutePlannerUiState>(RoutePlannerUiState.Idle)
    val uiState: StateFlow<RoutePlannerUiState> = _uiState.asStateFlow()

    private val _selectedItinerary = MutableStateFlow<PlannedItinerary?>(null)
    val selectedItinerary: StateFlow<PlannedItinerary?> = _selectedItinerary.asStateFlow()

    private var searchJob: Job? = null
    private var userLat: Double? = null
    private var userLon: Double? = null

    fun setUserLocationAsOrigin(location: Location?) {
        if (location != null) {
            userLat = location.latitude
            userLon = location.longitude
            useCurrentLocationAsOrigin(location.latitude, location.longitude)
        }
    }

    fun useCurrentLocationAsOrigin(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val lastLoc = LocationUtils.getBestLastLocation(context)
            if (lastLoc != null) {
                useCurrentLocationAsOrigin(lastLoc.latitude, lastLoc.longitude)
            } else {
                LocationUtils.requestDeviceLocation(context) { lat, lon ->
                    useCurrentLocationAsOrigin(lat, lon)
                }
            }
        }
    }

    fun useCurrentLocationAsOrigin(lat: Double = 39.4699, lon: Double = -0.3763) {
        userLat = lat
        userLon = lon
        val loc = PlannerLocation(
            title = "Ubicación actual",
            subtitle = "GPS",
            latitude = lat,
            longitude = lon,
            isUserGps = true
        )
        _origin.value = loc
        _originQuery.value = "Ubicación actual"
        _activeSearchField.value = PlannerSearchField.NONE
        _searchResults.value = emptyList()
        triggerAutoSearchIfReady()
    }

    fun setOrigin(location: PlannerLocation) {
        _origin.value = location
        _originQuery.value = location.title.ifBlank { location.subtitle ?: "Ubicación seleccionada" }
        _activeSearchField.value = PlannerSearchField.NONE
        _searchResults.value = emptyList()
        triggerAutoSearchIfReady()
    }

    fun setDestination(location: PlannerLocation) {
        _destination.value = location
        _destinationQuery.value = location.title.ifBlank { location.subtitle ?: "Ubicación seleccionada" }
        _activeSearchField.value = PlannerSearchField.NONE
        _searchResults.value = emptyList()
        triggerAutoSearchIfReady()
    }

    fun commitCurrentSearchQueryIfFieldUnfocused(field: PlannerSearchField) {
        if (_activeSearchField.value == field) {
            _activeSearchField.value = PlannerSearchField.NONE
        }
        val defaultLat = userLat ?: 39.4699
        val defaultLon = userLon ?: -0.3763

        if (field == PlannerSearchField.ORIGIN) {
            if (_origin.value == null && _originQuery.value.isNotBlank()) {
                val topResult = _searchResults.value.firstOrNull()
                if (topResult != null) {
                    selectSearchResult(topResult)
                } else {
                    val queryTitle = _originQuery.value.trim()
                    setOrigin(PlannerLocation(title = queryTitle, latitude = defaultLat, longitude = defaultLon))
                }
            } else if (_origin.value != null) {
                val title = _origin.value?.title ?: ""
                if (title.isNotBlank()) {
                    _originQuery.value = title
                }
            }
        } else if (field == PlannerSearchField.DESTINATION) {
            if (_destination.value == null && _destinationQuery.value.isNotBlank()) {
                val topResult = _searchResults.value.firstOrNull()
                if (topResult != null) {
                    selectSearchResult(topResult)
                } else {
                    val queryTitle = _destinationQuery.value.trim()
                    setDestination(PlannerLocation(title = queryTitle, latitude = defaultLat, longitude = defaultLon))
                }
            } else if (_destination.value != null) {
                val title = _destination.value?.title ?: ""
                if (title.isNotBlank()) {
                    _destinationQuery.value = title
                }
            }
        }
    }

    fun setDestinationFromCoordinates(title: String, lat: Double, lon: Double) {
        val loc = PlannerLocation(
            title = title,
            latitude = lat,
            longitude = lon
        )
        setDestination(loc)
    }

    fun onOriginFocused() {
        _activeSearchField.value = PlannerSearchField.ORIGIN
        executeSearchForQuery(_originQuery.value)
    }

    fun onDestinationFocused() {
        _activeSearchField.value = PlannerSearchField.DESTINATION
        executeSearchForQuery(_destinationQuery.value)
    }

    fun dismissSearchPanel() {
        searchJob?.cancel()
        _isSearching.value = false
        _activeSearchField.value = PlannerSearchField.NONE
        _searchResults.value = emptyList()
    }

    fun updateOriginQuery(query: String) {
        _originQuery.value = query
        if (_origin.value?.title != query) {
            _origin.value = null
        }
        _activeSearchField.value = PlannerSearchField.ORIGIN
        executeSearchForQuery(query)
    }

    fun updateDestinationQuery(query: String) {
        _destinationQuery.value = query
        if (_destination.value?.title != query) {
            _destination.value = null
        }
        _activeSearchField.value = PlannerSearchField.DESTINATION
        executeSearchForQuery(query)
    }

    private fun executeSearchForQuery(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }

        _isSearching.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200) // Debounce
            try {
                unifiedSearchEngine.performSearch(
                    query = trimmed,
                    userLat = userLat,
                    userLon = userLon,
                    customFavorites = customFavorites.value,
                    favoriteBusStops = favoriteBusStopsSet.value,
                    favoriteMetroStations = favoriteMetroStationsSet.value,
                    favoriteCercaniasStations = favoriteCercaniasStationsSet.value
                ).collect { results ->
                    _searchResults.value = results
                    _isSearching.value = false
                }
            } catch (e: Exception) {
                _isSearching.value = false
            }
        }
    }

    fun selectSearchResult(result: MapSearchResult) {
        val loc = mapSearchResultToPlannerLocation(result)
        
        // Save to Recent Searches
        val recentSearch = when (result) {
            is MapSearchResult.BusStop -> {
                val alias = result.alias
                RecentSearch(
                    type = "bus",
                    id = result.stop.id_parada,
                    title = alias ?: result.stop.denominacion,
                    subtitle = if (!alias.isNullOrBlank()) "${result.stop.denominacion} • Parada ${result.stop.id_parada}" else "Parada ${result.stop.id_parada}",
                    latitude = result.stop.lat,
                    longitude = result.stop.lon,
                    extraData = result.stop.lineas
                )
            }
            is MapSearchResult.Metro -> RecentSearch(
                type = "metro",
                id = result.station.id,
                title = result.station.name,
                subtitle = "Metrovalencia",
                latitude = result.station.latitude,
                longitude = result.station.longitude,
                extraData = result.station.lines.joinToString(",")
            )
            is MapSearchResult.Cercanias -> RecentSearch(
                type = "cercanias",
                id = result.station.stop_id,
                title = result.station.displayName,
                subtitle = "Cercanías",
                latitude = result.station.lat,
                longitude = result.station.lon
            )
            is MapSearchResult.Address -> {
                val isFav = result.result.type == "favorite" || result.result.category == "favorite" ||
                        customFavorites.value.any { it.latitude == result.result.latitude && it.longitude == result.result.longitude }
                val favItem = customFavorites.value.find { it.latitude == result.result.latitude && it.longitude == result.result.longitude }

                val mainTitle = favItem?.title ?: loc.title
                val subTitle = favItem?.subtitle ?: (loc.subtitle ?: "València")

                RecentSearch(
                    type = if (isFav) "favorite" else "address",
                    id = if (isFav) (favItem?.id ?: "fav_${result.result.latitude}_${result.result.longitude}") else "addr_${result.result.latitude}_${result.result.longitude}",
                    title = mainTitle,
                    subtitle = subTitle,
                    latitude = result.result.latitude,
                    longitude = result.result.longitude
                )
            }
        }
        addRecentSearch(recentSearch)

        val target = _activeSearchField.value
        if (target == PlannerSearchField.ORIGIN) {
            setOrigin(loc)
        } else if (target == PlannerSearchField.DESTINATION) {
            setDestination(loc)
        } else {
            if (_origin.value == null) {
                setOrigin(loc)
            } else {
                setDestination(loc)
            }
        }
    }

    fun selectRecentSearch(item: RecentSearch) {
        val result = recentSearchToSearchResult(item)
        selectSearchResult(result)
    }

    fun addRecentSearch(search: RecentSearch) {
        viewModelScope.launch {
            val currentList = recentSearches.value.toMutableList()
            currentList.removeAll { it.id == search.id || (it.latitude == search.latitude && it.longitude == search.longitude) }
            currentList.add(0, search)
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

    fun swapOriginAndDestination() {
        val tempOrig = _origin.value
        val tempOrigQuery = _originQuery.value
        _origin.value = _destination.value
        _originQuery.value = _destinationQuery.value
        _destination.value = tempOrig
        _destinationQuery.value = tempOrigQuery
        dismissSearchPanel()
        triggerAutoSearchIfReady()
    }

    fun toggleModeFilter(filter: RouteModeFilter) {
        val current = _selectedModeFilters.value.toMutableSet()
        if (current.contains(filter)) {
            current.remove(filter)
        } else {
            current.add(filter)
        }
        _selectedModeFilters.value = current
        searchRoutes()
    }

    fun clearModeFilters() {
        _selectedModeFilters.value = emptySet()
        searchRoutes()
    }

    fun toggleFewestTransfers() {
        _fewestTransfers.value = !_fewestTransfers.value
        searchRoutes()
    }

    fun setDepartureSchedule(type: DepartureType, time: String? = null, date: String? = null) {
        _departureType.value = type
        val now = Date()
        val defaultTime = time ?: if (type != DepartureType.LEAVE_NOW) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        } else null
        val defaultDate = date ?: if (type != DepartureType.LEAVE_NOW || !defaultTime.isNullOrEmpty()) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        } else null
        _selectedTime.value = defaultTime
        _selectedDate.value = defaultDate
        searchRoutes()
    }

    fun selectItinerary(itinerary: PlannedItinerary?) {
        _selectedItinerary.value = itinerary
    }

    fun recalculateFromStation(stationName: String, lat: Double = 0.0, lon: Double = 0.0) {
        viewModelScope.launch {
            val validLat = if (lat != 0.0) lat else (userLat ?: 39.4699)
            val validLon = if (lon != 0.0) lon else (userLon ?: -0.3763)
            val loc = PlannerLocation(
                title = stationName,
                subtitle = "Estación de transbordo",
                latitude = validLat,
                longitude = validLon
            )
            setOrigin(loc)
            selectItinerary(null)
        }
    }

    private fun triggerAutoSearchIfReady() {
        if (_origin.value != null && _destination.value != null) {
            searchRoutes()
        }
    }

    private var realTimeEnrichmentJob: Job? = null

    fun searchRoutes() {
        val orig = _origin.value
        val dest = _destination.value
        if (orig == null || dest == null) return

        realTimeEnrichmentJob?.cancel()
        _uiState.value = RoutePlannerUiState.Loading(PlannerLoadingStage.SCHEDULED_TRIPS)
        _selectedItinerary.value = null

        viewModelScope.launch {
            val maxTransfers = if (_fewestTransfers.value) 1 else 3
            val arriveBy = _departureType.value == DepartureType.ARRIVE_BY
            val isDepartNow = _departureType.value == DepartureType.LEAVE_NOW
            
            val selectedFilters = _selectedModeFilters.value
            val modes = if (selectedFilters.isEmpty()) {
                "WALK,SUBWAY,TRAM,BUS,COACH,REGIONAL_RAIL"
            } else {
                ("WALK," + selectedFilters.flatMap { it.modes }.distinct().joinToString(","))
            }

            val result = hybridRoutingRepository.planRoute(
                fromLat = orig.latitude,
                fromLon = orig.longitude,
                toLat = dest.latitude,
                toLon = dest.longitude,
                time = _selectedTime.value,
                date = _selectedDate.value,
                arriveBy = arriveBy,
                maxTransfers = maxTransfers,
                modes = modes,
                originName = orig.title,
                destinationName = dest.title
            )

            result.fold(
                onSuccess = { itineraries ->
                    if (itineraries.isEmpty()) {
                        _uiState.value = RoutePlannerUiState.Error("No se encontraron rutas para el trayecto seleccionado.")
                    } else {
                        if (isDepartNow) {
                            // Advance to real-time crossing stage
                            _uiState.value = RoutePlannerUiState.Loading(PlannerLoadingStage.REAL_TIME_CROSS)
                            
                            // Reconcile in background coroutine while keeping loading indicator clean
                            try {
                                val enrichedList = withContext(Dispatchers.IO) {
                                    hybridRoutingRepository.reconcileItineraries(itineraries, isDepartNow = true)
                                }
                                _uiState.value = RoutePlannerUiState.Loading(PlannerLoadingStage.BUILDING_ROUTES)
                                delay(220) // Smooth visual transition
                                _uiState.value = RoutePlannerUiState.Success(enrichedList)
                            } catch (e: Exception) {
                                _uiState.value = RoutePlannerUiState.Success(itineraries)
                            }
                        } else {
                            _uiState.value = RoutePlannerUiState.Loading(PlannerLoadingStage.BUILDING_ROUTES)
                            delay(180)
                            _uiState.value = RoutePlannerUiState.Success(itineraries)
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.value = RoutePlannerUiState.Error(
                        err.localizedMessage ?: "Error de conexión al calcular la ruta. Inténtalo de nuevo."
                    )
                }
            )
        }
    }

    private fun startBackgroundRealTimeEnrichment(initialItineraries: List<PlannedItinerary>) {
        realTimeEnrichmentJob?.cancel()
        realTimeEnrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val enrichedList = hybridRoutingRepository.reconcileItineraries(initialItineraries, isDepartNow = true)

                withContext(Dispatchers.Main) {
                    val currentState = _uiState.value
                    if (currentState is RoutePlannerUiState.Success) {
                        _uiState.value = RoutePlannerUiState.Success(enrichedList)
                        // Update selected itinerary if open
                        val currentSelected = _selectedItinerary.value
                        if (currentSelected != null) {
                            val updatedSelected = enrichedList.find { it.id == currentSelected.id }
                            if (updatedSelected != null) {
                                _selectedItinerary.value = updatedSelected
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Background enrichment silently falls back to initially rendered theoretical/quick itineraries
            }
        }
    }
}
