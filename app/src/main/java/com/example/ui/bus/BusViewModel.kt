package com.example.ui.bus

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.repository.DashboardRepository
import com.example.data.repository.StaticTransitDataCache
import com.example.data.model.MetroStation
import com.example.util.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.data.network.NetworkModule
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class BusViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = DashboardRepository(application, database)

    private val client = NetworkModule.okHttpClient
    private val metrobusRepository = com.example.data.repository.MetrobusRepository(database, client, application.applicationContext)
    private val valenbisiRepository = com.example.data.repository.ValenbisiRepository(application)

    // Location & Network Stations
    private val _lastLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val lastLocation = _lastLocation.asStateFlow()

    private val _allNetworkStations = MutableStateFlow<List<MetroStation>>(emptyList())
    val allNetworkStations = _allNetworkStations.asStateFlow()

    // Valenbisi State
    private val _favoriteValenbisi = MutableStateFlow<List<String>>(emptyList())
    val favoriteValenbisi = _favoriteValenbisi.asStateFlow()

    private val _valenbisiAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val valenbisiAliases = _valenbisiAliases.asStateFlow()

    private val _currentValenbisiFilterSource = MutableStateFlow(ValenbisiFilterSource.FAVORITES)
    val currentValenbisiFilterSource = _currentValenbisiFilterSource.asStateFlow()

    private val _valenbisiSearchQuery = MutableStateFlow("")
    val valenbisiSearchQuery = _valenbisiSearchQuery.asStateFlow()

    private val _selectedMetroStationIdForValenbisi = MutableStateFlow<String?>("15") // Default Colón
    val selectedMetroStationIdForValenbisi = _selectedMetroStationIdForValenbisi.asStateFlow()

    private val _valenbisiStations = MutableStateFlow<List<com.example.ui.map.components.ValenbisiStation>>(emptyList())
    val valenbisiStations = _valenbisiStations.asStateFlow()

    private val _valenbisiLoading = MutableStateFlow(false)
    val valenbisiLoading = _valenbisiLoading.asStateFlow()

    // EMT Bus State
    private val _favoriteBusStops = MutableStateFlow<List<String>>(emptyList())
    val favoriteBusStops = _favoriteBusStops.asStateFlow()

    private val _busStopAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val busStopAliases = _busStopAliases.asStateFlow()

    private val _currentBusFilterSource = MutableStateFlow(BusFilterSource.FAVORITES_BUS)
    val currentBusFilterSource = _currentBusFilterSource.asStateFlow()

    private val _busSearchQuery = MutableStateFlow("")
    val busSearchQuery = _busSearchQuery.asStateFlow()

    private val _selectedMetroStationIdForBus = MutableStateFlow<String?>("15") // Default to Colón
    val selectedMetroStationIdForBus = _selectedMetroStationIdForBus.asStateFlow()

    private val _busStopsList = MutableStateFlow<List<EmtBusStop>>(emptyList())
    val busStopsList = _busStopsList.asStateFlow()

    private val _busStopsLoading = MutableStateFlow(false)
    val busStopsLoading = _busStopsLoading.asStateFlow()

    private val _busTimes = MutableStateFlow<List<EmtBusTime>>(emptyList())
    val busTimes = _busTimes.asStateFlow()

    private val _busTimesLoading = MutableStateFlow(false)
    val busTimesLoading = _busTimesLoading.asStateFlow()

    private val _isLoadingMoreScheduled = MutableStateFlow(false)
    val isLoadingMoreScheduled = _isLoadingMoreScheduled.asStateFlow()

    // Dedicated EMT Scheduled Departures
    private val _emtScheduledTimes = MutableStateFlow<List<EmtBusTime>>(emptyList())
    val emtScheduledTimes = _emtScheduledTimes.asStateFlow()
    private val _isEmtScheduledLoading = MutableStateFlow(false)
    val isEmtScheduledLoading = _isEmtScheduledLoading.asStateFlow()
    private val _isEmtScheduledLoaded = MutableStateFlow(false)
    val isEmtScheduledLoaded = _isEmtScheduledLoaded.asStateFlow()
    private var emtScheduledLimit = 5

    private val _selectedBusStop = MutableStateFlow<EmtBusStop?>(null)
    val selectedBusStop = _selectedBusStop.asStateFlow()

    private val _selectedBusTabIndex = MutableStateFlow(0)
    val selectedBusTabIndex = _selectedBusTabIndex.asStateFlow()

    fun setSelectedBusTabIndex(index: Int) {
        _selectedBusTabIndex.value = index
    }

    // Metrobus State
    private val _favoriteMetrobusStops = MutableStateFlow<List<String>>(emptyList())
    val favoriteMetrobusStops = _favoriteMetrobusStops.asStateFlow()

    private val _metrobusStopAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val metrobusStopAliases = _metrobusStopAliases.asStateFlow()

    private val _metrobusSearchQuery = MutableStateFlow("")
    val metrobusSearchQuery = _metrobusSearchQuery.asStateFlow()

    private val _metrobusStopsList = MutableStateFlow<List<MetrobusStop>>(emptyList())
    val metrobusStopsList = _metrobusStopsList.asStateFlow()

    private val _metrobusStopsLoading = MutableStateFlow(false)
    val metrobusStopsLoading = _metrobusStopsLoading.asStateFlow()

    private val _metrobusTimes = MutableStateFlow<List<MetrobusDepartureUiModel>>(emptyList())
    val metrobusTimes = _metrobusTimes.asStateFlow()

    private val _metrobusTimesLoading = MutableStateFlow(false)
    val metrobusTimesLoading = _metrobusTimesLoading.asStateFlow()

    // Dedicated Metrobus Scheduled Departures
    private val _metrobusScheduledTimes = MutableStateFlow<List<MetrobusDepartureUiModel>>(emptyList())
    val metrobusScheduledTimes = _metrobusScheduledTimes.asStateFlow()
    private val _isMetrobusScheduledLoading = MutableStateFlow(false)
    val isMetrobusScheduledLoading = _isMetrobusScheduledLoading.asStateFlow()
    private val _isMetrobusScheduledLoaded = MutableStateFlow(false)
    val isMetrobusScheduledLoaded = _isMetrobusScheduledLoaded.asStateFlow()
    private var metrobusScheduledLimit = 5

    private val _selectedMetrobusStop = MutableStateFlow<MetrobusStop?>(null)
    val selectedMetrobusStop = _selectedMetrobusStop.asStateFlow()

    // Jobs
    private var busCountdownJob: Job? = null
    private var loadBusStopsJob: Job? = null
    private var loadMetrobusStopsJob: Job? = null
    private var metrobusCountdownJob: Job? = null

    fun updateLocation(lat: Double, lon: Double) {
        _lastLocation.value = Pair(lat, lon)
        if (_currentBusFilterSource.value == BusFilterSource.FAVORITES_BUS || _currentBusFilterSource.value == BusFilterSource.GPS_USER) {
            loadBusStops()
            loadMetrobusStops()
        }
    }

    fun updateNetworkStations(stations: List<MetroStation>) {
        if (stations.isNotEmpty()) {
            _allNetworkStations.value = stations
            if (_currentBusFilterSource.value == BusFilterSource.METRO_STATION) {
                loadBusStops()
                // loadMetrobusStops() // En pausa hasta que la pestaña de Metrobús se active en la UI
            }
        }
    }

    init {
        loadPreferences()
        loadMetroNetworkStations()
        fetchValenbisiStations()
        viewModelScope.launch(Dispatchers.IO) {
            val isOnboardingCompleted = repository.getPreference("has_completed_onboarding", "false") == "true"
            if (isOnboardingCompleted) {
                com.example.data.repository.emt.EmtDataSyncManager.syncIfNeeded(application.applicationContext)
                com.example.data.repository.metrobus.MetrobusDataSyncManager.syncIfNeeded(application.applicationContext)
            }
        }
    }

    private fun loadMetroNetworkStations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repoStations = com.example.data.repository.MetroRepository(getApplication()).loadMetroStations()
                if (repoStations.isNotEmpty()) {
                    _allNetworkStations.value = repoStations.distinctBy { it.id }
                } else {
                    _allNetworkStations.value = com.example.data.model.ValenciaMetroData.mainMetroStations
                }
            } catch (e: Exception) {
                Log.e("BusViewModel", "Error loading metro network stations", e)
                _allNetworkStations.value = com.example.data.model.ValenciaMetroData.mainMetroStations
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedBusFavs = repository.getPreference("favorite_bus_stops", "")
            if (savedBusFavs.isNotEmpty()) {
                _favoriteBusStops.value = savedBusFavs.split(",").filter { it.isNotEmpty() }
            }
            val savedAliasesJson = repository.getPreference("bus_stop_aliases", "{}")
            if (savedAliasesJson.isNotEmpty() && savedAliasesJson != "{}") {
                try {
                    val jsonObj = org.json.JSONObject(savedAliasesJson)
                    val map = mutableMapOf<String, String>()
                    jsonObj.keys().forEach { key ->
                        map[key] = jsonObj.getString(key)
                    }
                    _busStopAliases.value = map
                } catch (e: Exception) {
                    Log.e("EmtBus", "Error parsing saved bus stop aliases", e)
                }
            }

            val savedValenbisiFavs = repository.getPreference("favorite_valenbisi_stations", "")
            if (savedValenbisiFavs.isNotEmpty()) {
                _favoriteValenbisi.value = savedValenbisiFavs.split(",").filter { it.isNotEmpty() }
            }
            val savedValenbisiAliasesJson = repository.getPreference("valenbisi_aliases", "{}")
            if (savedValenbisiAliasesJson.isNotEmpty() && savedValenbisiAliasesJson != "{}") {
                try {
                    val jsonObj = org.json.JSONObject(savedValenbisiAliasesJson)
                    val map = mutableMapOf<String, String>()
                    jsonObj.keys().forEach { key ->
                        map[key] = jsonObj.getString(key)
                    }
                    _valenbisiAliases.value = map
                } catch (e: Exception) {
                    Log.e("Valenbisi", "Error parsing saved valenbisi aliases", e)
                }
            }

            val savedMetrobusFavs = repository.getPreference("favorite_metrobus_stops", "")
            if (savedMetrobusFavs.isNotEmpty()) {
                _favoriteMetrobusStops.value = savedMetrobusFavs.split(",").filter { it.isNotEmpty() }
            }
            val savedMetrobusAliasesJson = repository.getPreference("metrobus_stop_aliases", "{}")
            if (savedMetrobusAliasesJson.isNotEmpty() && savedMetrobusAliasesJson != "{}") {
                try {
                    val jsonObj = org.json.JSONObject(savedMetrobusAliasesJson)
                    val map = mutableMapOf<String, String>()
                    jsonObj.keys().forEach { key ->
                        map[key] = jsonObj.getString(key)
                    }
                    _metrobusStopAliases.value = map
                } catch (e: Exception) {
                    Log.e("Metrobus", "Error parsing saved metrobus aliases", e)
                }
            }

            // Once preferences are loaded, ensure stops exist and suppress 2313 once
            if (database.geoportalStopDao().getStopCount() == 0) {
                val assetStops = BusMapper.parseStopsFromJsonDirect(getApplication())
                if (assetStops.isNotEmpty()) {
                    database.geoportalStopDao().replaceAllStops(assetStops)
                }
            }
            try {
                val stop2313 = database.geoportalStopDao().getStopById("2313")
                if (stop2313 != null && stop2313.suprimida == 0) {
                    database.geoportalStopDao().insertAll(listOf(stop2313.copy(suprimida = 1)))
                }
            } catch (e: Exception) {
                Log.e("EmtBus", "Error marking stop 2313 as suprimida", e)
            }

            loadBusStops()
            // loadMetrobusStops() // En pausa hasta que la pestaña de Metrobús se active en la UI
        }
    }

    // ==========================================
    // VALENBISI SECTION (API & ROOM STATE)
    // ==========================================

    fun setValenbisiFilterSource(source: ValenbisiFilterSource) {
        if (source != ValenbisiFilterSource.FAVORITES) {
            _valenbisiSearchQuery.value = ""
        }
        _currentValenbisiFilterSource.value = source
    }

    fun setValenbisiSearchQuery(query: String) {
        _valenbisiSearchQuery.value = query
    }

    fun selectMetroStationForValenbisi(stationId: String) {
        _valenbisiSearchQuery.value = ""
        _selectedMetroStationIdForValenbisi.value = stationId
        _currentValenbisiFilterSource.value = ValenbisiFilterSource.METRO_STATION
    }

    fun fetchValenbisiStations() {
        viewModelScope.launch {
            _valenbisiLoading.value = true
            try {
                val list = valenbisiRepository.fetchStations()
                _valenbisiStations.value = list
            } catch (e: Exception) {
                Log.e("Valenbisi", "Error fetching valenbisi stations: ${e.message}", e)
            } finally {
                _valenbisiLoading.value = false
            }
        }
    }

    fun toggleValenbisiFavorite(stationNumber: String) {
        val current = _favoriteValenbisi.value.toMutableList()
        if (current.contains(stationNumber)) {
            current.remove(stationNumber)
        } else {
            current.add(stationNumber)
        }
        _favoriteValenbisi.value = current
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePreference("favorite_valenbisi_stations", current.joinToString(","))
        }
    }

    fun saveValenbisiAlias(stationNumber: String, alias: String) {
        val current = _valenbisiAliases.value.toMutableMap()
        if (alias.isBlank()) {
            current.remove(stationNumber)
        } else {
            current[stationNumber] = alias.trim()
        }
        _valenbisiAliases.value = current
        viewModelScope.launch(Dispatchers.IO) {
            val jsonObj = org.json.JSONObject()
            current.forEach { (k, v) -> jsonObj.put(k, v) }
            repository.savePreference("valenbisi_aliases", jsonObj.toString())
        }
    }

    // ==========================================
    // EMT VALENCIA BUS SECTION (API & ROOM STATE)
    // ==========================================

    fun setBusFilterSource(source: BusFilterSource) {
        if (source != BusFilterSource.FAVORITES_BUS) {
            _busSearchQuery.value = ""
            _metrobusSearchQuery.value = ""
        }
        _currentBusFilterSource.value = source
        loadBusStops()
        loadMetrobusStops()
    }

    fun setBusSearchQuery(query: String) {
        _busSearchQuery.value = query
        loadBusStops()
    }

    fun selectMetroStationForBus(stationId: String) {
        _busSearchQuery.value = ""
        _metrobusSearchQuery.value = ""
        _selectedMetroStationIdForBus.value = stationId
        _currentBusFilterSource.value = BusFilterSource.METRO_STATION
        loadBusStops()
        loadMetrobusStops()
    }

    fun selectBusStop(stop: EmtBusStop?) {
        _selectedBusStop.value = stop
        isEmtScheduledExpanded = false
        scheduledLimitPerLine = 3
        _emtScheduledTimes.value = emptyList()
        _isEmtScheduledLoaded.value = false
        _isEmtScheduledLoading.value = false
        emtScheduledLimit = 5
        viewModelScope.launch {
            busCountdownJob?.cancelAndJoin()
            if (stop != null) {
                fetchBusTimes(stop.opId, limitPerLine = scheduledLimitPerLine, isSilent = false, includeScheduled = false)
                startBusCountdownTicker()
            } else {
                _busTimes.value = emptyList()
            }
        }
    }

    fun selectBusStopFromEntity(stop: com.example.data.database.GeoportalStopEntity) {
        val existing = _busStopsList.value.find { it.opId == stop.id_parada }
        val emtStop = existing ?: EmtBusStop(
            t = stop.lat.toString(),
            n = stop.lon.toString(),
            me = stop.denominacion,
            utes = BusMapper.getLinesForStop(stop),
            opId = stop.id_parada,
            ica = "Parada " + stop.id_parada
        )
        selectBusStop(emtStop)
    }

    private fun startBusCountdownTicker() {
        busCountdownJob = viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                delay(1000)
                if (!isActive) break
                tickCount++
                val currentList = _busTimes.value
                if (currentList.isNotEmpty()) {
                    var changed = false
                    val updated = currentList.map { time ->
                        val secs = time.secondsRemaining
                        if (secs <= 0) {
                            time
                        } else {
                            changed = true
                            val newSecs = secs - 1
                            val newMinsVal = newSecs / 60
                            val newMinsStr = if (newMinsVal <= 0) "1" else newMinsVal.toString()
                            time.copy(
                                secondsRemaining = newSecs,
                                minutos = newMinsStr
                            )
                        }
                    }
                    if (changed && isActive) {
                        _busTimes.value = updated
                    }
                }

                // Poll real-time updates silently every 30 seconds
                if (tickCount % 30 == 0 && isActive) {
                    val stop = _selectedBusStop.value
                    if (stop != null) {
                        fetchBusTimes(
                            stopId = stop.opId,
                            limitPerLine = scheduledLimitPerLine,
                            isSilent = true,
                            includeScheduled = isEmtScheduledExpanded
                        )
                    }
                }
            }
        }
    }

    fun toggleFavoriteBusStop(stopId: String) {
        val current = _favoriteBusStops.value.toMutableList()
        if (current.contains(stopId)) {
            current.remove(stopId)
        } else {
            current.add(stopId)
        }
        _favoriteBusStops.value = current
        viewModelScope.launch {
            repository.savePreference("favorite_bus_stops", current.joinToString(","))
            loadBusStops()
        }
    }

    fun setBusStopAlias(stopId: String, alias: String) {
        val trimmed = alias.trim().take(32) // Enforce max 32 characters
        val currentMap = _busStopAliases.value.toMutableMap()
        if (trimmed.isBlank()) {
            currentMap.remove(stopId)
        } else {
            currentMap[stopId] = trimmed
        }
        _busStopAliases.value = currentMap
        viewModelScope.launch {
            val jsonObj = org.json.JSONObject()
            currentMap.forEach { (k, v) -> jsonObj.put(k, v) }
            repository.savePreference("bus_stop_aliases", jsonObj.toString())
            loadBusStops()
        }
    }

    fun syncGeoportalStops() {
        viewModelScope.launch {
            try {
                val stops = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BusMapper.loadStopsFromAssets(getApplication())
                }
                if (stops.isNotEmpty()) {
                    database.geoportalStopDao().insertAll(stops)
                    repository.savePreference("last_geoportal_sync", System.currentTimeMillis().toString())
                    Log.d("GeoportalSync", "Successfully loaded ${stops.size} stops from emt_paradas_lineas.json asset!")
                }
            } catch (e: Exception) {
                Log.e("GeoportalSync", "Error syncing bus stops from assets", e)
            }
        }
    }

    private suspend fun processEmtStops(
        baseStops: List<com.example.data.database.GeoportalStopEntity>,
        query: String,
        aliases: Map<String, String>,
        refLat: Double,
        refLon: Double,
        maxDistanceMeters: Double?,
        filterIds: List<String>? = null,
        sortByDistance: Boolean = true
    ): List<EmtBusStop> {
        val stopsToProcess = if (filterIds != null) {
            val stopMap = baseStops.associateBy { it.id_parada }
            val favStops = filterIds.mapNotNull { stopMap[it] ?: database.geoportalStopDao().getStopById(it) }
            if (query.isNotEmpty()) {
                favStops.filter { stop ->
                    computeSearchScore(stop.id_parada, stop.denominacion, query, aliases[stop.id_parada]) > 0.0
                }
            } else {
                favStops
            }
        } else if (query.isNotEmpty()) {
            baseStops.mapNotNull { stop ->
                val score = computeSearchScore(stop.id_parada, stop.denominacion, query, aliases[stop.id_parada])
                if (score > 0.0) Pair(stop, score) else null
            }.sortedByDescending { it.second }
             .map { it.first }
        } else {
            baseStops
        }

        val mapped = stopsToProcess.mapNotNull { stop ->
            if (stop.lineas.isNullOrBlank()) return@mapNotNull null
            val lines = BusMapper.getLinesForStop(stop)
            if (lines.isEmpty()) return@mapNotNull null
            val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, stop.lat, stop.lon)
            if (maxDistanceMeters != null && dist > maxDistanceMeters) return@mapNotNull null

            Pair(
                EmtBusStop(
                    t = stop.lat.toString(),
                    n = stop.lon.toString(),
                    me = stop.denominacion,
                    utes = lines,
                    opId = stop.id_parada,
                    ica = "Parada " + stop.id_parada,
                    distanceText = LocationUtils.formatDistance(dist)
                ),
                dist
            )
        }

        return if (sortByDistance) {
            mapped.sortedBy { it.second }.map { it.first }
        } else {
            mapped.map { it.first }
        }
    }

    fun loadBusStops() {
        loadBusStopsJob?.cancel()
        val query = _busSearchQuery.value
        val source = _currentBusFilterSource.value
        val favs = _favoriteBusStops.value

        loadBusStopsJob = viewModelScope.launch(Dispatchers.IO) {
            _busStopsLoading.value = true
            try {
                if (query.isNotEmpty()) {
                    delay(120) // Debounce rapid keystrokes to keep UI input buttery smooth
                }

                val aliases = _busStopAliases.value

                // Ensure stops from assets/JSON exist in DB
                if (database.geoportalStopDao().getStopCount() == 0) {
                    val assetStops = BusMapper.parseStopsFromJsonDirect(getApplication())
                    if (assetStops.isNotEmpty()) {
                        database.geoportalStopDao().replaceAllStops(assetStops)
                    }
                }

                // Base coordinates defaults
                var refLat = 39.46975
                var refLon = -0.37739
                val loc = _lastLocation.value
                if (loc != null) {
                    refLat = loc.first
                    refLon = loc.second
                }

                val baseActiveStops: List<com.example.data.database.GeoportalStopEntity> = 
                    database.geoportalStopDao().getAllActiveStops()

                val list: List<EmtBusStop> = when (source) {
                    BusFilterSource.FAVORITES_BUS -> {
                        if (query.isEmpty()) {
                            if (favs.isEmpty()) {
                                emptyList()
                            } else {
                                processEmtStops(
                                    baseStops = baseActiveStops,
                                    query = "",
                                    aliases = aliases,
                                    refLat = refLat,
                                    refLon = refLon,
                                    maxDistanceMeters = null,
                                    filterIds = favs,
                                    sortByDistance = true
                                )
                            }
                        } else {
                            processEmtStops(
                                baseStops = baseActiveStops,
                                query = query,
                                aliases = aliases,
                                refLat = refLat,
                                refLon = refLon,
                                maxDistanceMeters = null,
                                filterIds = null,
                                sortByDistance = true
                            )
                        }
                    }
                    BusFilterSource.GPS_USER -> {
                        processEmtStops(
                            baseStops = baseActiveStops,
                            query = query,
                            aliases = aliases,
                            refLat = refLat,
                            refLon = refLon,
                            maxDistanceMeters = if (loc != null && query.isEmpty()) 500.0 else null,
                            filterIds = null,
                            sortByDistance = true
                        )
                    }
                    BusFilterSource.METRO_STATION -> {
                        val stationId = _selectedMetroStationIdForBus.value ?: "15"
                        val allStations = _allNetworkStations.value.ifEmpty { com.example.data.repository.MetroRepository(getApplication()).loadMetroStations() }
                        val station = allStations.find { it.id == stationId }
                        val (targetLat, targetLon) = if (station != null && station.latitude != 0.0) {
                            Pair(station.latitude, station.longitude)
                        } else {
                            BusMapper.getCoordinatesForStation(getApplication(), stationId)
                        }
                        
                        processEmtStops(
                            baseStops = baseActiveStops,
                            query = query,
                            aliases = aliases,
                            refLat = targetLat,
                            refLon = targetLon,
                            maxDistanceMeters = if (query.isEmpty()) 500.0 else 1500.0,
                            filterIds = null,
                            sortByDistance = true
                        )
                    }
                }
                _busStopsList.value = list
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when a user types quickly and cancels the previous search
            } catch (e: Exception) {
                Log.e("EmtBus", "Error loading bus stops", e)
            } finally {
                if (loadBusStopsJob?.isCancelled != true) {
                    _busStopsLoading.value = false
                }
            }
        }
    }

    private fun markStopAsSuprimida(stopId: String) {
        viewModelScope.launch {
            try {
                val stop = database.geoportalStopDao().getStopById(stopId)
                if (stop != null) {
                    val updated = stop.copy(suprimida = 1)
                    database.geoportalStopDao().insertAll(listOf(updated))
                    Log.d("EmtBus", "Successfully marked stop $stopId as suprimida (unused/inactive)")
                    loadBusStops() // refresh lists instantly to hide it!
                }
            } catch (e: Exception) {
                Log.e("EmtBus", "Error marking stop $stopId as suprimida: ${e.message}", e)
            }
        }
    }

    private var isEmtScheduledExpanded = false
    private var scheduledLimitPerLine = 3

    fun fetchBusTimes(
        stopId: String,
        limitPerLine: Int = 3,
        isLoadMore: Boolean = false,
        isSilent: Boolean = false,
        includeScheduled: Boolean = isEmtScheduledExpanded
    ) {
        scheduledLimitPerLine = limitPerLine
        val startTime = System.currentTimeMillis()
        if (!isLoadMore && !isSilent) {
            _busTimesLoading.value = true
            if (_selectedBusStop.value?.opId != stopId) {
                _busTimes.value = emptyList()
            }
        } else if (isLoadMore) {
            _isLoadingMoreScheduled.value = true
        }
        viewModelScope.launch {
            try {
                val stopName = _selectedBusStop.value?.n
                val arrivals = com.example.data.repository.RealTimeTransitRepository.getEmtLiveArrivals(
                    stopNumber = stopId,
                    stopName = stopName,
                    limitPerLine = scheduledLimitPerLine,
                    includeScheduled = includeScheduled
                )
                if (arrivals.isNotEmpty() || !isSilent) {
                    _busTimes.value = arrivals
                }
            } catch (e: Exception) {
                Log.e("EmtBus", "Error fetching real bus times", e)
                if (!isSilent && _busTimes.value.isEmpty()) {
                    _busTimes.value = emptyList()
                }
            } finally {
                if (!isLoadMore && !isSilent) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < 400L) {
                        delay(400L - elapsed)
                    }
                    _busTimesLoading.value = false
                } else if (isLoadMore) {
                    _isLoadingMoreScheduled.value = false
                }
            }
        }
    }

    fun loadMoreScheduledTimes(stopId: String) {
        // Progressive in-memory reveal is managed smoothly via UnifiedScheduledSection scroll
        fetchEmtScheduledTimes(stopId, isLoadMore = false)
    }

    fun fetchEmtScheduledTimes(stopId: String, isLoadMore: Boolean = false) {
        if (_isEmtScheduledLoaded.value && _emtScheduledTimes.value.isNotEmpty() && !isLoadMore) {
            return
        }
        viewModelScope.launch {
            _isEmtScheduledLoading.value = true
            try {
                val stopName = _selectedBusStop.value?.n
                val sched = com.example.data.repository.RealTimeTransitRepository.getEmtScheduledDepartures(
                    stopNumber = stopId,
                    stopName = stopName,
                    limitPerLine = 100
                )
                _emtScheduledTimes.value = sched
                _isEmtScheduledLoaded.value = true
            } catch (e: Exception) {
                Log.e("EmtBus", "Error loading EMT scheduled times: ${e.message}", e)
                _isEmtScheduledLoaded.value = true
            } finally {
                _isEmtScheduledLoading.value = false
            }
        }
    }

    // ==========================================
    // METROBUS VALENCIA BUS SECTION
    // ==========================================

    fun setMetrobusSearchQuery(query: String) {
        _metrobusSearchQuery.value = query
        loadMetrobusStops()
    }

    fun toggleFavoriteMetrobusStop(stopId: String) {
        val current = _favoriteMetrobusStops.value.toMutableList()
        if (current.contains(stopId)) {
            current.remove(stopId)
        } else {
            current.add(stopId)
        }
        _favoriteMetrobusStops.value = current
        viewModelScope.launch {
            repository.savePreference("favorite_metrobus_stops", current.joinToString(","))
            loadMetrobusStops()
        }
    }

    fun setMetrobusStopAlias(stopId: String, alias: String) {
        val trimmed = alias.trim().take(32)
        val currentMap = _metrobusStopAliases.value.toMutableMap()
        if (trimmed.isBlank()) {
            currentMap.remove(stopId)
        } else {
            currentMap[stopId] = trimmed
        }
        _metrobusStopAliases.value = currentMap
        viewModelScope.launch {
            val jsonObj = org.json.JSONObject()
            currentMap.forEach { (k, v) -> jsonObj.put(k, v) }
            repository.savePreference("metrobus_stop_aliases", jsonObj.toString())
            loadMetrobusStops()
        }
    }

    fun refreshMetrobusDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            _metrobusStopsLoading.value = true
            try {
                metrobusRepository.syncStops(forceRefresh = true)
                Log.d("Metrobus", "Metrobús database refreshed successfully.")
            } catch (e: Exception) {
                Log.e("Metrobus", "Error refreshing Metrobus database", e)
            } finally {
                _metrobusStopsLoading.value = false
                loadMetrobusStops()
            }
        }
    }

    private fun processMetrobusStops(
        dbStops: List<com.example.data.database.MetrobusStopEntity>,
        query: String,
        aliases: Map<String, String>,
        refLat: Double?,
        refLon: Double?,
        maxDistanceMeters: Double?,
        filterIds: Set<String>? = null
    ): List<MetrobusStop> {
        val stopsToProcess = if (filterIds != null) {
            val stopMap = dbStops.associateBy { it.id_parada }
            val favStops = filterIds.mapNotNull { stopMap[it] }
            if (query.isNotEmpty()) {
                favStops.filter { stop ->
                    val linesList = stop.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    computeSearchScore(stop.id_parada, stop.denominacion, query, aliases[stop.id_parada], linesList) > 0.0
                }
            } else {
                favStops
            }
        } else if (query.isNotEmpty()) {
            dbStops.mapNotNull { stop ->
                val linesList = stop.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                val score = computeSearchScore(stop.id_parada, stop.denominacion, query, aliases[stop.id_parada], linesList)
                if (score > 0.0) Pair(stop, score) else null
            }.sortedByDescending { it.second }
             .map { it.first }
        } else {
            dbStops
        }

        if (refLat == null || refLon == null) {
            return stopsToProcess.map { entity ->
                val linesList = entity.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                MetrobusStop(
                    idParada = entity.id_parada,
                    denominacion = entity.denominacion,
                    lat = entity.lat,
                    lon = entity.lon,
                    lineas = linesList,
                    distanceText = ""
                )
            }
        }

        val mapped = stopsToProcess.mapNotNull { entity ->
            val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, entity.lat, entity.lon)
            if (maxDistanceMeters != null && dist > maxDistanceMeters) return@mapNotNull null
            Pair(
                MetrobusStop(
                    idParada = entity.id_parada,
                    denominacion = entity.denominacion,
                    lat = entity.lat,
                    lon = entity.lon,
                    lineas = entity.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                    distanceText = LocationUtils.formatDistance(dist)
                ),
                dist
            )
        }

        return if (filterIds != null || query.isEmpty()) {
            mapped.sortedBy { it.second }.map { it.first }
        } else {
            mapped.map { it.first }
        }
    }

    fun loadMetrobusStops() {
        loadMetrobusStopsJob?.cancel()
        val query = _metrobusSearchQuery.value
        val source = _currentBusFilterSource.value
        val favs = _favoriteMetrobusStops.value

        loadMetrobusStopsJob = viewModelScope.launch(Dispatchers.IO) {
            _metrobusStopsLoading.value = true
            try {
                if (query.isNotEmpty()) {
                    delay(120)
                }

                metrobusRepository.ensureStopsCached()

                val aliases = _metrobusStopAliases.value
                val baseActiveStops = database.metrobusStopDao().getAllActiveStops()

                var refLat = 39.46975
                var refLon = -0.37739
                val loc = _lastLocation.value
                if (loc != null) {
                    refLat = loc.first
                    refLon = loc.second
                }

                val filtered: List<MetrobusStop> = when (source) {
                    BusFilterSource.FAVORITES_BUS -> {
                        if (query.isEmpty()) {
                            if (favs.isEmpty()) {
                                emptyList()
                            } else {
                                processMetrobusStops(
                                    dbStops = baseActiveStops,
                                    query = "",
                                    aliases = aliases,
                                    refLat = refLat,
                                    refLon = refLon,
                                    maxDistanceMeters = null,
                                    filterIds = favs.toSet()
                                )
                            }
                        } else {
                            processMetrobusStops(
                                dbStops = baseActiveStops,
                                query = query,
                                aliases = aliases,
                                refLat = refLat,
                                refLon = refLon,
                                maxDistanceMeters = null,
                                filterIds = null
                            )
                        }
                    }
                    BusFilterSource.GPS_USER -> {
                        processMetrobusStops(
                            dbStops = baseActiveStops,
                            query = query,
                            aliases = aliases,
                            refLat = refLat,
                            refLon = refLon,
                            maxDistanceMeters = if (query.isEmpty()) (if (loc != null) 2000.0 else null) else null,
                            filterIds = null
                        )
                    }
                    BusFilterSource.METRO_STATION -> {
                        val stationId = _selectedMetroStationIdForBus.value ?: "15"
                        val allStations = _allNetworkStations.value.ifEmpty { com.example.data.repository.MetroRepository(getApplication()).loadMetroStations() }
                        val station = allStations.find { it.id == stationId }
                        val (targetLat, targetLon) = if (station != null && station.latitude != 0.0) {
                            Pair(station.latitude, station.longitude)
                        } else {
                            BusMapper.getCoordinatesForStation(getApplication(), stationId)
                        }

                        processMetrobusStops(
                            dbStops = baseActiveStops,
                            query = query,
                            aliases = aliases,
                            refLat = targetLat,
                            refLon = targetLon,
                            maxDistanceMeters = if (query.isEmpty()) 2000.0 else 5000.0,
                            filterIds = null
                        )
                    }
                }

                _metrobusStopsList.value = filtered
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore
            } catch (e: Exception) {
                Log.e("Metrobus", "Error loading Metrobus stops", e)
            } finally {
                if (loadMetrobusStopsJob?.isCancelled != true) {
                    _metrobusStopsLoading.value = false
                }
            }
        }
    }

    private var isMetrobusScheduledExpanded = false
    private var scheduledMetrobusLimitPerLine = 3
    private val _isLoadingMoreMetrobusScheduled = MutableStateFlow(false)
    val isLoadingMoreMetrobusScheduled = _isLoadingMoreMetrobusScheduled.asStateFlow()

    fun selectMetrobusStop(stop: MetrobusStop?) {
        _selectedMetrobusStop.value = stop
        isMetrobusScheduledExpanded = false
        scheduledMetrobusLimitPerLine = 3
        _metrobusScheduledTimes.value = emptyList()
        _isMetrobusScheduledLoaded.value = false
        _isMetrobusScheduledLoading.value = false
        metrobusScheduledLimit = 5
        viewModelScope.launch {
            metrobusCountdownJob?.cancelAndJoin()
            if (stop != null) {
                fetchMetrobusTimes(
                    stop.idParada,
                    limitPerLine = scheduledMetrobusLimitPerLine,
                    isSilent = false,
                    includeScheduled = false
                )
                startMetrobusCountdownTicker()
            } else {
                _metrobusTimes.value = emptyList()
            }
        }
    }

    fun selectMetrobusStopFromEntity(stop: com.example.data.database.MetrobusStopEntity) {
        val existing = _metrobusStopsList.value.find { it.idParada == stop.id_parada }
        val linesList = stop.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val mbStop = existing ?: MetrobusStop(
            idParada = stop.id_parada,
            denominacion = stop.denominacion,
            lat = stop.lat,
            lon = stop.lon,
            lineas = linesList
        )
        selectMetrobusStop(mbStop)
    }

    fun fetchMetrobusTimes(
        stopId: String,
        limitPerLine: Int = scheduledMetrobusLimitPerLine,
        isLoadMore: Boolean = false,
        isSilent: Boolean = false,
        includeScheduled: Boolean = isMetrobusScheduledExpanded
    ) {
        scheduledMetrobusLimitPerLine = limitPerLine
        if (!isLoadMore && !isSilent) {
            _metrobusTimesLoading.value = true
            if (_selectedMetrobusStop.value?.idParada != stopId) {
                _metrobusTimes.value = emptyList()
            }
        } else if (isLoadMore) {
            _isLoadingMoreMetrobusScheduled.value = true
        }
        viewModelScope.launch {
            try {
                val times = metrobusRepository.getMetrobusArrivals(
                    stopId = stopId,
                    limitPerLine = scheduledMetrobusLimitPerLine,
                    includeScheduled = includeScheduled
                )
                if (times.isNotEmpty() || !isSilent) {
                    _metrobusTimes.value = times
                }
                // Once times are loaded, ensure stops are loaded to update line badges
                loadMetrobusStops()
            } catch (e: Exception) {
                Log.e("Metrobus", "Error fetching metrobus times", e)
                if (!isSilent && _metrobusTimes.value.isEmpty()) {
                    _metrobusTimes.value = emptyList()
                }
            } finally {
                if (!isLoadMore && !isSilent) {
                    _metrobusTimesLoading.value = false
                } else if (isLoadMore) {
                    _isLoadingMoreMetrobusScheduled.value = false
                }
            }
        }
    }

    fun loadMoreMetrobusScheduledTimes(stopId: String) {
        // Progressive in-memory reveal is managed smoothly via UnifiedScheduledSection scroll
        fetchMetrobusScheduledTimes(stopId, isLoadMore = false)
    }

    fun fetchMetrobusScheduledTimes(stopId: String, isLoadMore: Boolean = false) {
        if (_isMetrobusScheduledLoaded.value && _metrobusScheduledTimes.value.isNotEmpty() && !isLoadMore) {
            return
        }
        viewModelScope.launch {
            _isMetrobusScheduledLoading.value = true
            try {
                val sched = metrobusRepository.getMetrobusScheduledDepartures(
                    stopId = stopId,
                    limitPerLine = 100
                )
                _metrobusScheduledTimes.value = sched
                _isMetrobusScheduledLoaded.value = true
            } catch (e: Exception) {
                Log.e("Metrobus", "Error loading Metrobus scheduled times: ${e.message}", e)
                _isMetrobusScheduledLoaded.value = true
            } finally {
                _isMetrobusScheduledLoading.value = false
            }
        }
    }

    private fun startMetrobusCountdownTicker() {
        metrobusCountdownJob = viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                delay(1000)
                if (!isActive) break
                tickCount++
                val currentList = _metrobusTimes.value
                if (currentList.isNotEmpty()) {
                    var changed = false
                    val updated = currentList.map { time ->
                        val secs = time.secondsRemaining
                        if (secs <= 0) {
                            time
                        } else {
                            changed = true
                            val newSecs = secs - 1
                            val newMinsVal = (newSecs + 59) / 60
                            val newMinsStr = if (newMinsVal <= 1) "1" else newMinsVal.toString()
                            val newLabel = if (newMinsVal <= 1) "Inminente" else "$newMinsVal min"
                            time.copy(
                                secondsRemaining = newSecs,
                                minutesRemaining = newMinsVal,
                                timeLabel = newLabel
                            )
                        }
                    }
                    if (changed && isActive) {
                        _metrobusTimes.value = updated
                    }
                }

                // Poll real-time / scheduled silently in background every 30s
                if (tickCount % 30 == 0 && isActive) {
                    val stop = _selectedMetrobusStop.value ?: break
                    if (isActive) {
                        fetchMetrobusTimes(
                            stop.idParada,
                            limitPerLine = scheduledMetrobusLimitPerLine,
                            isSilent = true,
                            includeScheduled = isMetrobusScheduledExpanded
                        )
                    }
                }
            }
        }
    }

}
