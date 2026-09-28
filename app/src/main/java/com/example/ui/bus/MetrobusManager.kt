package com.example.ui.bus

import android.app.Application
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.MetroStation
import com.example.data.repository.DashboardRepository
import com.example.data.repository.MetrobusRepository
import com.example.util.LocationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class MetrobusManager(
    private val application: Application,
    private val database: AppDatabase,
    private val repository: DashboardRepository,
    private val metrobusRepository: MetrobusRepository,
    private val scope: CoroutineScope,
    private val getLastLocation: () -> Pair<Double, Double>?,
    private val getAllNetworkStations: () -> List<MetroStation>,
    private val getCurrentFilterSource: () -> BusFilterSource,
    private val getSelectedMetroStationIdForBus: () -> String?
) {
    private val _favoriteMetrobusStops = MutableStateFlow<List<String>>(emptyList())
    val favoriteMetrobusStops: StateFlow<List<String>> = _favoriteMetrobusStops.asStateFlow()

    private val _metrobusStopAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val metrobusStopAliases: StateFlow<Map<String, String>> = _metrobusStopAliases.asStateFlow()

    private val _metrobusSearchQuery = MutableStateFlow("")
    val metrobusSearchQuery: StateFlow<String> = _metrobusSearchQuery.asStateFlow()

    private val _metrobusStopsList = MutableStateFlow<List<MetrobusStop>>(emptyList())
    val metrobusStopsList: StateFlow<List<MetrobusStop>> = _metrobusStopsList.asStateFlow()

    private val _metrobusStopsLoading = MutableStateFlow(false)
    val metrobusStopsLoading: StateFlow<Boolean> = _metrobusStopsLoading.asStateFlow()

    private val _metrobusTimes = MutableStateFlow<List<MetrobusDepartureUiModel>>(emptyList())
    val metrobusTimes: StateFlow<List<MetrobusDepartureUiModel>> = _metrobusTimes.asStateFlow()

    private val _metrobusTimesLoading = MutableStateFlow(false)
    val metrobusTimesLoading: StateFlow<Boolean> = _metrobusTimesLoading.asStateFlow()

    private val _metrobusScheduledTimes = MutableStateFlow<List<MetrobusDepartureUiModel>>(emptyList())
    val metrobusScheduledTimes: StateFlow<List<MetrobusDepartureUiModel>> = _metrobusScheduledTimes.asStateFlow()

    private val _isMetrobusScheduledLoading = MutableStateFlow(false)
    val isMetrobusScheduledLoading: StateFlow<Boolean> = _isMetrobusScheduledLoading.asStateFlow()

    private val _isMetrobusScheduledLoaded = MutableStateFlow(false)
    val isMetrobusScheduledLoaded: StateFlow<Boolean> = _isMetrobusScheduledLoaded.asStateFlow()

    private var metrobusScheduledLimit = 5
    private var isMetrobusScheduledExpanded = false
    private var scheduledMetrobusLimitPerLine = 3

    private val _isLoadingMoreMetrobusScheduled = MutableStateFlow(false)
    val isLoadingMoreMetrobusScheduled: StateFlow<Boolean> = _isLoadingMoreMetrobusScheduled.asStateFlow()

    private val _selectedMetrobusStop = MutableStateFlow<MetrobusStop?>(null)
    val selectedMetrobusStop: StateFlow<MetrobusStop?> = _selectedMetrobusStop.asStateFlow()

    private var loadMetrobusStopsJob: Job? = null
    private var metrobusCountdownJob: Job? = null

    suspend fun loadPreferences() {
        val savedMetrobusFavs = repository.getPreference("favorite_metrobus_stops", "")
        if (savedMetrobusFavs.isNotEmpty()) {
            _favoriteMetrobusStops.value = savedMetrobusFavs.split(",").filter { it.isNotEmpty() }
        }
        val savedMetrobusAliasesJson = repository.getPreference("metrobus_stop_aliases", "{}")
        if (savedMetrobusAliasesJson.isNotEmpty() && savedMetrobusAliasesJson != "{}") {
            try {
                val jsonObj = JSONObject(savedMetrobusAliasesJson)
                val map = mutableMapOf<String, String>()
                jsonObj.keys().forEach { key ->
                    map[key] = jsonObj.getString(key)
                }
                _metrobusStopAliases.value = map
            } catch (e: Exception) {
                Log.e("Metrobus", "Error parsing saved metrobus aliases", e)
            }
        }
    }

    fun setMetrobusSearchQuery(query: String) {
        _metrobusSearchQuery.value = query
        loadMetrobusStops()
    }

    fun clearMetrobusSearchQuery() {
        _metrobusSearchQuery.value = ""
    }

    fun toggleFavoriteMetrobusStop(stopId: String) {
        val current = _favoriteMetrobusStops.value.toMutableList()
        if (current.contains(stopId)) {
            current.remove(stopId)
        } else {
            current.add(stopId)
        }
        _favoriteMetrobusStops.value = current
        scope.launch {
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
        scope.launch {
            val jsonObj = JSONObject()
            currentMap.forEach { (k, v) -> jsonObj.put(k, v) }
            repository.savePreference("metrobus_stop_aliases", jsonObj.toString())
            loadMetrobusStops()
        }
    }

    fun refreshMetrobusDatabase() {
        scope.launch(Dispatchers.IO) {
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
            filterIds.mapNotNull { stopMap[it] }
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

        return if (query.isEmpty()) {
            mapped.sortedBy { it.second }.map { it.first }
        } else {
            mapped.map { it.first }
        }
    }

    fun loadMetrobusStops() {
        loadMetrobusStopsJob?.cancel()
        val query = _metrobusSearchQuery.value
        val source = getCurrentFilterSource()
        val favs = _favoriteMetrobusStops.value

        loadMetrobusStopsJob = scope.launch(Dispatchers.IO) {
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
                val loc = getLastLocation()
                if (loc != null) {
                    refLat = loc.first
                    refLon = loc.second
                }

                val filtered: List<MetrobusStop> = when (source) {
                    BusFilterSource.FAVORITES_BUS -> {
                        val filterIds = if (query.isEmpty()) favs.toSet() else null
                        processMetrobusStops(
                            dbStops = baseActiveStops,
                            query = query,
                            aliases = aliases,
                            refLat = refLat,
                            refLon = refLon,
                            maxDistanceMeters = null,
                            filterIds = filterIds
                        )
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
                        val stationId = getSelectedMetroStationIdForBus() ?: "15"
                        val station = getAllNetworkStations().find { it.id == stationId }
                            ?: com.example.data.model.ValenciaMetroData.mainMetroStations.find { it.id == stationId }
                        val (targetLat, targetLon) = if (station != null) {
                            Pair(station.latitude, station.longitude)
                        } else {
                            BusMapper.getCoordinatesForStation(application, stationId)
                        }

                        processMetrobusStops(
                            dbStops = baseActiveStops,
                            query = query,
                            aliases = aliases,
                            refLat = targetLat,
                            refLon = targetLon,
                            maxDistanceMeters = if (query.isEmpty()) 2000.0 else null,
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

    fun selectMetrobusStop(stop: MetrobusStop?) {
        _selectedMetrobusStop.value = stop
        isMetrobusScheduledExpanded = false
        scheduledMetrobusLimitPerLine = 3
        _metrobusScheduledTimes.value = emptyList()
        _isMetrobusScheduledLoaded.value = false
        _isMetrobusScheduledLoading.value = false
        metrobusScheduledLimit = 5
        scope.launch {
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
        scope.launch {
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
        fetchMetrobusScheduledTimes(stopId, isLoadMore = false)
    }

    fun fetchMetrobusScheduledTimes(stopId: String, isLoadMore: Boolean = false) {
        if (_isMetrobusScheduledLoaded.value && _metrobusScheduledTimes.value.isNotEmpty() && !isLoadMore) {
            return
        }
        scope.launch {
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
            } finally {
                _isMetrobusScheduledLoading.value = false
            }
        }
    }

    private fun startMetrobusCountdownTicker() {
        metrobusCountdownJob = scope.launch {
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
