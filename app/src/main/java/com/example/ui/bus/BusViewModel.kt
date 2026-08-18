package com.example.ui.bus

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.repository.DashboardRepository
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

    private val _lastLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val lastLocation = _lastLocation.asStateFlow()

    fun updateLocation(lat: Double, lon: Double) {
        _lastLocation.value = Pair(lat, lon)
        loadMetrobusStops()
    }

    private val _allNetworkStations = MutableStateFlow<List<MetroStation>>(emptyList())
    
    fun updateNetworkStations(stations: List<MetroStation>) {
        _allNetworkStations.value = stations
    }

    private val metrobusRepository = com.example.data.repository.MetrobusRepository(database, client)

    init {
        loadPreferences()
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

            // Once preferences are loaded, load both stop lists
            loadBusStops()
            loadMetrobusStops()
        }
    }

    // ==========================================
    // VALENBISI SECTION (API & ROOM STATE)
    // ==========================================
    private val valenbisiRepository = com.example.data.repository.ValenbisiRepository(client)

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

    fun setValenbisiFilterSource(source: ValenbisiFilterSource) {
        _currentValenbisiFilterSource.value = source
    }

    fun setValenbisiSearchQuery(query: String) {
        _valenbisiSearchQuery.value = query
    }

    fun selectMetroStationForValenbisi(stationId: String) {
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

    private val _selectedBusStop = MutableStateFlow<EmtBusStop?>(null)
    val selectedBusStop = _selectedBusStop.asStateFlow()

    fun setBusFilterSource(source: BusFilterSource) {
        _currentBusFilterSource.value = source
        loadBusStops()
    }

    fun setBusSearchQuery(query: String) {
        _busSearchQuery.value = query
        loadBusStops()
    }

    fun selectMetroStationForBus(stationId: String) {
        _selectedMetroStationIdForBus.value = stationId
        _currentBusFilterSource.value = BusFilterSource.METRO_STATION
        _selectedMetroStationIdForBus.value = stationId
    }

    private var busCountdownJob: Job? = null

    fun selectBusStop(stop: EmtBusStop?) {
        _selectedBusStop.value = stop
        viewModelScope.launch {
            busCountdownJob?.cancelAndJoin()
            if (stop != null) {
                fetchBusTimes(stop.opId)
                startBusCountdownTicker()
            } else {
                _busTimes.value = emptyList()
            }
        }
    }

    private fun startBusCountdownTicker() {
        busCountdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (!isActive) break
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

    private var loadBusStopsJob: Job? = null

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

                // Ensure stops from assets exist in DB
                if (database.geoportalStopDao().getStopCount() == 0) {
                    val assetStops = BusMapper.loadStopsFromAssets(getApplication())
                    if (assetStops.isNotEmpty()) {
                        database.geoportalStopDao().insertAll(assetStops)
                    }
                }

                // Ensure stop 2313 is suprimida
                try {
                    val stop2313 = database.geoportalStopDao().getStopById("2313")
                    if (stop2313 != null && stop2313.suprimida == 0) {
                        database.geoportalStopDao().insertAll(listOf(stop2313.copy(suprimida = 1)))
                    }
                } catch (e: Exception) {
                    Log.e("EmtBus", "Error marking stop 2313 as suprimida", e)
                }

                // Base coordinates defaults
                var refLat = 39.46975
                var refLon = -0.37739
                val loc = _lastLocation.value
                if (loc != null) {
                    refLat = loc.first
                    refLon = loc.second
                }

                val list = when (source) {
                    BusFilterSource.FAVORITES_BUS -> {
                        if (query.isNotEmpty()) {
                            val dbStops = database.geoportalStopDao().getAllActiveStops()
                            val filteredStops = dbStops.filter { !it.lineas.isNullOrBlank() }
                            val scoredStops = filteredStops.map { stop ->
                                val score = computeSearchScore(stop.id_parada, stop.denominacion, query, aliases[stop.id_parada])
                                Pair(stop, score)
                            }.filter { it.second > 0.0 }
                             .sortedByDescending { it.second }
                             .map { it.first }

                            scoredStops.map { stop ->
                                val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, stop.lat, stop.lon)
                                EmtBusStop(
                                    t = stop.lat.toString(),
                                    n = stop.lon.toString(),
                                    me = stop.denominacion,
                                    utes = BusMapper.getLinesForStop(stop),
                                    opId = stop.id_parada,
                                    ica = "Parada " + stop.id_parada,
                                    distanceText = LocationUtils.formatDistance(dist)
                                )
                            }.filter { it.utes.isNotEmpty() }
                        } else {
                            val dbStops = mutableListOf<com.example.data.database.GeoportalStopEntity>()
                            for (id in favs) {
                                database.geoportalStopDao().getStopById(id)?.let { dbStops.add(it) }
                            }
                            val filteredStops = dbStops.filter { !it.lineas.isNullOrBlank() }
                            filteredStops.map { stop ->
                                val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, stop.lat, stop.lon)
                                EmtBusStop(
                                    t = stop.lat.toString(),
                                    n = stop.lon.toString(),
                                    me = stop.denominacion,
                                    utes = BusMapper.getLinesForStop(stop),
                                    opId = stop.id_parada,
                                    ica = "Parada " + stop.id_parada,
                                    distanceText = LocationUtils.formatDistance(dist)
                                )
                            }.filter { it.utes.isNotEmpty() }
                        }
                    }
                    BusFilterSource.GPS_USER -> {
                        val allStops = database.geoportalStopDao().getAllActiveStops()
                        val filteredStops = allStops.filter { !it.lineas.isNullOrBlank() }
                        
                        if (query.isNotEmpty()) {
                            val scoredStops = filteredStops.map { stop ->
                                val score = computeSearchScore(stop.id_parada, stop.denominacion, query, aliases[stop.id_parada])
                                Pair(stop, score)
                            }.filter { it.second > 0.0 }
                             .sortedByDescending { it.second }
                             .map { it.first }

                            scoredStops.map { stop ->
                                val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, stop.lat, stop.lon)
                                EmtBusStop(
                                    t = stop.lat.toString(),
                                    n = stop.lon.toString(),
                                    me = stop.denominacion,
                                    utes = BusMapper.getLinesForStop(stop),
                                    opId = stop.id_parada,
                                    ica = "Parada " + stop.id_parada,
                                    distanceText = LocationUtils.formatDistance(dist)
                                )
                            }.filter { it.utes.isNotEmpty() }
                        } else {
                            val sortedAllByDist = filteredStops.sortedBy { stop ->
                                LocationUtils.calculateDistanceMeters(refLat, refLon, stop.lat, stop.lon)
                            }
                            
                            var inRadius = sortedAllByDist.filter { stop ->
                                val latDiff = Math.abs(stop.lat - refLat)
                                val lonDiff = Math.abs(stop.lon - refLon)
                                latDiff <= 0.015 && lonDiff <= 0.015
                            }
                            
                            if (inRadius.size < 10) {
                                inRadius = sortedAllByDist.take(25)
                            }
                            
                            val starred = inRadius.filter { favs.contains(it.id_parada) }
                            val nonStarred = inRadius.filter { !favs.contains(it.id_parada) }
                            val sortedNonStarred = nonStarred.sortedBy { stop ->
                                LocationUtils.calculateDistanceMeters(refLat, refLon, stop.lat, stop.lon)
                            }
                            
                            val finalEntities = starred + sortedNonStarred
                            finalEntities.map { stop ->
                                val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, stop.lat, stop.lon)
                                EmtBusStop(
                                    t = stop.lat.toString(),
                                    n = stop.lon.toString(),
                                    me = stop.denominacion,
                                    utes = BusMapper.getLinesForStop(stop),
                                    opId = stop.id_parada,
                                    ica = "Parada " + stop.id_parada,
                                    distanceText = LocationUtils.formatDistance(dist)
                                )
                            }.filter { it.utes.isNotEmpty() }
                        }
                    }
                    BusFilterSource.METRO_STATION -> {
                        val stationId = _selectedMetroStationIdForBus.value ?: "15"
                        val station = _allNetworkStations.value.find { it.id == stationId }
                        val (targetLat, targetLon) = if (station != null) {
                            Pair(station.latitude, station.longitude)
                        } else {
                            BusMapper.getCoordinatesForStation(getApplication(), stationId.toIntOrNull() ?: 15)
                        }
                        
                        val allStops = database.geoportalStopDao().getAllActiveStops()
                        val filteredStops = allStops.filter { !it.lineas.isNullOrBlank() }
                        val inQuadrant = filteredStops.filter { stop ->
                            val latDiff = Math.abs(stop.lat - targetLat)
                            val lonDiff = Math.abs(stop.lon - targetLon)
                            latDiff <= 0.005 && lonDiff <= 0.005
                        }
                        
                        // Prioritization Algorithm: Favorites pinned, rest sorted by distance from metro station
                        val starred = inQuadrant.filter { favs.contains(it.id_parada) }
                        val nonStarred = inQuadrant.filter { !favs.contains(it.id_parada) }
                        val sortedNonStarred = nonStarred.sortedBy { stop ->
                            LocationUtils.calculateDistanceMeters(targetLat, targetLon, stop.lat, stop.lon)
                        }
                        
                        val finalEntities = starred + sortedNonStarred
                        finalEntities.map { stop ->
                            val dist = LocationUtils.calculateDistanceMeters(targetLat, targetLon, stop.lat, stop.lon)
                            EmtBusStop(
                                t = stop.lat.toString(),
                                n = stop.lon.toString(),
                                me = stop.denominacion,
                                utes = BusMapper.getLinesForStop(stop),
                                opId = stop.id_parada,
                                ica = "Parada " + stop.id_parada,
                                distanceText = LocationUtils.formatDistance(dist)
                            )
                        }.filter { it.utes.isNotEmpty() }
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

    fun fetchBusTimes(stopId: String) {
        _busTimesLoading.value = true
        _busTimes.value = emptyList()
        viewModelScope.launch {
            try {
                val arrivals = com.example.data.repository.RealTimeTransitRepository.getEmtLiveArrivals(stopId)
                _busTimes.value = arrivals
            } catch (e: Exception) {
                Log.e("EmtBus", "Error fetching real bus times", e)
                _busTimes.value = emptyList()
            } finally {
                _busTimesLoading.value = false
            }
        }
    }

    // ==========================================
    // METROBUS VALENCIA BUS SECTION
    // ==========================================

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

    private val _selectedMetrobusStop = MutableStateFlow<MetrobusStop?>(null)
    val selectedMetrobusStop = _selectedMetrobusStop.asStateFlow()

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

    private var loadMetrobusStopsJob: Job? = null

    fun refreshMetrobusDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            _metrobusStopsLoading.value = true
            try {
                val success = metrobusRepository.syncStops(forceRefresh = true)
                if (success) {
                    Log.d("Metrobus", "Metrobús database refreshed successfully.")
                }
            } catch (e: Exception) {
                Log.e("Metrobus", "Error refreshing Metrobus database", e)
            } finally {
                _metrobusStopsLoading.value = false
                loadMetrobusStops()
            }
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

                val dbStops = if (query.isNotEmpty()) {
                    database.metrobusStopDao().searchActiveStops("%$query%")
                } else {
                    database.metrobusStopDao().getAllActiveStops()
                }

                val loc = _lastLocation.value
                val refLat = loc?.first ?: 39.46975
                val refLon = loc?.second ?: -0.37739

                val mapped = dbStops.map { entity ->
                    val linesList = entity.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    val dist = if (loc != null) {
                        LocationUtils.calculateDistanceMeters(loc.first, loc.second, entity.lat, entity.lon)
                    } else -1.0

                    MetrobusStop(
                        idParada = entity.id_parada,
                        denominacion = entity.denominacion,
                        lat = entity.lat,
                        lon = entity.lon,
                        lineas = linesList,
                        distanceText = if (dist >= 0) LocationUtils.formatDistance(dist) else ""
                    )
                }

                var filtered = mapped
                when (source) {
                    BusFilterSource.FAVORITES_BUS -> {
                        filtered = filtered.filter { favs.contains(it.idParada) }
                    }
                    BusFilterSource.GPS_USER -> {
                        if (loc != null) {
                            val sortedAllByDist = filtered.sortedBy { stop ->
                                LocationUtils.calculateDistanceMeters(loc.first, loc.second, stop.lat, stop.lon)
                            }
                            var inRadius = sortedAllByDist.filter { stop ->
                                val latDiff = Math.abs(stop.lat - refLat)
                                val lonDiff = Math.abs(stop.lon - refLon)
                                latDiff <= 0.015 && lonDiff <= 0.015
                            }
                            if (inRadius.size < 10) {
                                inRadius = sortedAllByDist.take(25)
                            }
                            val starred = inRadius.filter { favs.contains(it.idParada) }
                            val nonStarred = inRadius.filter { !favs.contains(it.idParada) }
                            filtered = starred + nonStarred
                        } else {
                            filtered = emptyList()
                        }
                    }
                    BusFilterSource.METRO_STATION -> {
                        val stationId = _selectedMetroStationIdForBus.value ?: "15"
                        val station = _allNetworkStations.value.find { it.id == stationId }
                        val (targetLat, targetLon) = if (station != null) {
                            Pair(station.latitude, station.longitude)
                        } else {
                            BusMapper.getCoordinatesForStation(getApplication(), stationId.toIntOrNull() ?: 15)
                        }

                        val inQuadrant = filtered.filter { stop ->
                            val latDiff = Math.abs(stop.lat - targetLat)
                            val lonDiff = Math.abs(stop.lon - targetLon)
                            latDiff <= 0.005 && lonDiff <= 0.005
                        }
                        val starred = inQuadrant.filter { favs.contains(it.idParada) }
                        val nonStarred = inQuadrant.filter { !favs.contains(it.idParada) }
                        val sortedNonStarred = nonStarred.sortedBy { stop ->
                            LocationUtils.calculateDistanceMeters(targetLat, targetLon, stop.lat, stop.lon)
                        }
                        filtered = starred + sortedNonStarred
                    }
                }

                // Default sorting and constraints for ALL or query searches
                if (source == BusFilterSource.FAVORITES_BUS && query.isEmpty() && filtered.isEmpty()) {
                    // Keep empty to show prompt
                } else if (query.isEmpty() && source != BusFilterSource.GPS_USER && source != BusFilterSource.METRO_STATION) {
                    if (loc != null) {
                        filtered = filtered.sortedBy { stop ->
                            LocationUtils.calculateDistanceMeters(loc.first, loc.second, stop.lat, stop.lon)
                        }.take(50)
                    } else {
                        filtered = filtered.take(50)
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

    private var metrobusCountdownJob: Job? = null

    fun selectMetrobusStop(stop: MetrobusStop?) {
        _selectedMetrobusStop.value = stop
        viewModelScope.launch {
            metrobusCountdownJob?.cancelAndJoin()
            if (stop != null) {
                fetchMetrobusTimes(stop.idParada)
                startMetrobusCountdownTicker()
            } else {
                _metrobusTimes.value = emptyList()
            }
        }
    }

    fun fetchMetrobusTimes(stopId: String) {
        _metrobusTimesLoading.value = true
        _metrobusTimes.value = emptyList()
        viewModelScope.launch {
            try {
                val linesMap = metrobusRepository.getLinesMap()
                val detail = metrobusRepository.fetchStopDetail(stopId)
                if (detail != null) {
                    _metrobusTimes.value = MetrobusTimeCalculator.getActiveDeparturesForToday(detail, linesMap)
                    // Once lines are enriched, reload stops to update line badges
                    loadMetrobusStops()
                } else {
                    _metrobusTimes.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("Metrobus", "Error fetching metrobus times", e)
                _metrobusTimes.value = emptyList()
            } finally {
                _metrobusTimesLoading.value = false
            }
        }
    }

    private fun startMetrobusCountdownTicker() {
        metrobusCountdownJob = viewModelScope.launch {
            while (isActive) {
                delay(30000) // Recalculate remaining minutes every 30 seconds
                if (!isActive) break
                val stop = _selectedMetrobusStop.value ?: break
                val linesMap = metrobusRepository.getLinesMap()
                val detail = metrobusRepository.fetchStopDetail(stop.idParada)
                if (detail != null && isActive) {
                    _metrobusTimes.value = MetrobusTimeCalculator.getActiveDeparturesForToday(detail, linesMap)
                }
            }
        }
    }

}
