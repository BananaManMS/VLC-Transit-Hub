package com.example.ui.metro
import okhttp3.Response

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.StationEntity
import com.example.data.repository.DashboardRepository
import com.example.data.repository.MetroRepository
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.data.model.Departure
import com.example.util.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

import com.example.data.database.TransitCardEntity
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.dashboard.TransitTripUiModel
import com.example.data.repository.MetroCardRepository
import com.example.data.repository.MetroAlertsRepository
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.util.normalizeForSearch

class MetroViewModel(application: Application, private val metroRepository: MetroRepository) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, MetroRepository(application))

    private val database = AppDatabase.getDatabase(application)
    private val repository = DashboardRepository(application, database)
    private val metroCardRepository = MetroCardRepository(application, database)
    private val metroAlertsRepository = MetroAlertsRepository()
    private val client = com.example.data.network.NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        class Factory(private val application: Application, private val metroRepository: MetroRepository) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MetroViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MetroViewModel(application, metroRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private val _allNetworkStations = MutableStateFlow<List<MetroStation>>(emptyList())
    val allNetworkStations = _allNetworkStations.asStateFlow()

    private val _internalLineStationsMap = MutableStateFlow<Map<String, List<com.example.data.repository.LineStationInfo>>>(emptyMap())
    val lineStationsState = _internalLineStationsMap.asStateFlow()

    private val _selectedStationId = MutableStateFlow("")
    val selectedStationId = _selectedStationId.asStateFlow()

    var hasUserManuallySelectedStation = false
        private set

    private val _favoriteStations = MutableStateFlow<List<String>>(emptyList())
    val favoriteStations = _favoriteStations.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation = _lastLocation.asStateFlow()

    private val _realTimeDepartures = MutableStateFlow<List<RealTimeDeparture>>(emptyList())
    val realTimeDepartures = _realTimeDepartures.asStateFlow()
    val departures = realTimeDepartures // Alias para compatibilidad con código existente

    private val _stationAforoBloqueado = MutableStateFlow<AforoBloqueado?>(null)
    val stationAforoBloqueado = _stationAforoBloqueado.asStateFlow()

    private val _realTimeError = MutableStateFlow<String?>(null)
    val realTimeError = _realTimeError.asStateFlow()

    val activeIncidents = metroAlertsRepository.activeIncidents
    val isMetroAlertsLoading = metroAlertsRepository.isAlertsLoading

    val accessibilityIncidents = metroAlertsRepository.accessibilityIncidents
    val specialNotices = metroAlertsRepository.specialNotices
    val metroNews = metroAlertsRepository.metroNews
    val isNewsLoading = metroAlertsRepository.isNewsLoading

    private val _realTimeSelectedStationId = MutableStateFlow<String?>("")
    val realTimeSelectedStationId = _realTimeSelectedStationId.asStateFlow()

    private val _selectedDepartureForDetails = MutableStateFlow<RealTimeDeparture?>(null)
    val selectedDepartureForDetails = _selectedDepartureForDetails.asStateFlow()

    private val _isBottomSheetVisible = MutableStateFlow(false)
    val isBottomSheetVisible = _isBottomSheetVisible.asStateFlow()

    private val _isStationInfoExpanded = MutableStateFlow(false)
    val isStationInfoExpanded = _isStationInfoExpanded.asStateFlow()

    private val _realTimeLoading = MutableStateFlow(false)
    val realTimeLoading = _realTimeLoading.asStateFlow()

    private var lastAlertsFetchTime = 0L
    private val _isAppInForeground = MutableStateFlow(true)
    

    
    private var fetchDeparturesJob: Job? = null
    private var realTimeRefreshJob: Job? = null
    private var alertsRefreshJob: Job? = null

    private val valenciaCenterLat = 39.46975
    private val valenciaCenterLon = -0.37739

    val sortedFavoriteStations: kotlinx.coroutines.flow.StateFlow<List<String>> = combine(
        _favoriteStations,
        _lastLocation,
        _allNetworkStations
    ) { favorites, loc, stations ->
        if (stations.isNotEmpty()) {
            val (lat, lon) = if (loc != null) Pair(loc.latitude, loc.longitude) else Pair(valenciaCenterLat, valenciaCenterLon)
            favorites.sortedWith { id1, id2 ->
                val s1 = stations.find { it.id == id1 }
                val s2 = stations.find { it.id == id2 }
                val d1 = s1?.let { LocationUtils.calculateDistanceMeters(lat, lon, it.latitude, it.longitude) } ?: Double.MAX_VALUE
                val d2 = s2?.let { LocationUtils.calculateDistanceMeters(lat, lon, it.latitude, it.longitude) } ?: Double.MAX_VALUE
                d1.compareTo(d2)
            }
        } else {
            favorites
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _metroSearchQuery = MutableStateFlow("")
    val metroSearchQuery = _metroSearchQuery.asStateFlow()

    fun setMetroSearchQuery(query: String) {
        _metroSearchQuery.value = query
    }

    val searchedStations: kotlinx.coroutines.flow.StateFlow<List<MetroStation>> = combine(
        _metroSearchQuery,
        _allNetworkStations,
        _favoriteStations
    ) { query, stations, favorites ->
        val baseList = if (query.isBlank()) {
            stations.map { Pair(it, 0.0) }
        } else {
            stations.map { station ->
                Pair(station, computeMetroSearchScore(station, query))
            }.filter { it.second > 0.0 }
        }
        val (favs, nonFavs) = baseList.partition { favorites.contains(it.first.id) }
        if (query.isBlank()) {
            favs.sortedBy { it.first.name.normalizeForSearch() }.map { it.first } + 
                nonFavs.sortedBy { it.first.name.normalizeForSearch() }.map { it.first }
        } else {
            favs.sortedByDescending { it.second }.map { it.first } + 
                nonFavs.sortedByDescending { it.second }.map { it.first }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )


    init {
        loadMetroStations()
        loadLineStationsData()
        loadPreferences()
        fetchAllAlerts()
        startAlertsRefreshTicker()
        autoRefreshCardsIfNeeded()

        viewModelScope.launch {
            sortedFavoriteStations.collect { sorted ->
                if (!hasUserManuallySelectedStation && sorted.isNotEmpty()) {
                    val topFav = sorted.first()
                    if (_realTimeSelectedStationId.value != topFav) {
                        selectRealTimeStation(topFav, isUserAction = false)
                    }
                }
            }
        }
    }

    private fun loadLineStationsData() {
        viewModelScope.launch(Dispatchers.IO) {
            _internalLineStationsMap.value = metroRepository.loadMetroLineStations()
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedMetroFavs = repository.getPreference("favorite_stations", "")
            _favoriteStations.value = savedMetroFavs.split(",").filter { it.isNotEmpty() }
            
            withContext(Dispatchers.Main) {
                if (!hasUserManuallySelectedStation) {
                    autoSelectNearestMetroStationIfNeeded()
                }
            }
        }
    }

    private fun loadMetroStations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentVersion = repository.getPreference("metro_stations_json_version", "0")
                var currentDbStations = database.stationDao().getAllStations()

                if (currentVersion != "8" || currentDbStations.isEmpty()) {
                    val defaultStations = metroRepository.loadMetroStations(forceReload = true)
                    database.stationDao().deleteAllStations()
                    database.stationDao().insertAll(defaultStations.map { station ->
                        StationEntity(
                            id = station.id,
                            name = station.name,
                            lines = station.lines.joinToString(","),
                            zone = station.zone,
                            lat = station.latitude,
                            lon = station.longitude
                        )
                    })
                    repository.savePreference("metro_stations_json_version", "8")
                    currentDbStations = database.stationDao().getAllStations()
                }

                if (currentDbStations.isNotEmpty()) {
                    val stations = currentDbStations.map { entity ->
                        MetroStation(
                            id = entity.id.toString(),
                            name = entity.name,
                            lines = entity.lines.split(",").filter { it.isNotEmpty() },
                            latitude = entity.latitude ?: 39.4697,
                            longitude = entity.longitude ?: -0.3734,
                            description = entity.zone,
                            zone = entity.zone
                        )
                    }.distinctBy { it.id }
                    _allNetworkStations.value = stations
                    Log.d("MetroViewModel", "Loaded ${stations.size} stations from database")
                } else {
                    val fallbackStations = metroRepository.loadMetroStations(forceReload = true)
                    _allNetworkStations.value = fallbackStations.distinctBy { it.id }
                    Log.d("MetroViewModel", "Loaded stations from assets")
                }
            } catch (e: Exception) {
                Log.e("MetroViewModel", "Error loading metro stations", e)
                _allNetworkStations.value = metroRepository.loadMetroStations(forceReload = true).distinctBy { it.id }
            }
        }
    }


    fun setLocation(location: Location?) {
        _lastLocation.value = location
        if (!hasUserManuallySelectedStation) {
            autoSelectNearestMetroStationIfNeeded()
        }
    }




    fun selectStation(stationId: String) {
        selectRealTimeStation(stationId)
    }

    fun toggleFavoriteMetroStation(stationId: String) {
        val current = _favoriteStations.value.toMutableList()
        if (current.contains(stationId)) {
            current.remove(stationId)
            updateFavoriteStations(current)
        } else {
            if (current.size < 10) { // Límite de 10 favoritas
                current.add(stationId)
                updateFavoriteStations(current)
            }
        }
    }

    fun updateFavoriteStations(stationIds: List<String>) {
        if (stationIds.size <= 10) {
            _favoriteStations.value = stationIds
            viewModelScope.launch {
                repository.savePreference("favorite_stations", stationIds.joinToString(","))
                if (stationIds.isNotEmpty() && !stationIds.contains(_selectedStationId.value)) {
                    selectStation(stationIds.first())
                }
            }
        }
    }

fun getSortedStations(): List<MetroStation> {
        val stations = _allNetworkStations.value
        val favIds = _favoriteStations.value.toSet()
        val favorites = stations.filter { it.id in favIds }.sortedBy { MetroMapper.normalizeForSort(it.name) }
        val nonFavorites = stations.filter { it.id !in favIds }.sortedBy { MetroMapper.normalizeForSort(it.name) }
        return favorites + nonFavorites
    }

    fun fetchRealTimeDepartures(stationId: String, isAutoRefresh: Boolean = false) {
        if (!isAutoRefresh) {
            fetchDeparturesJob?.cancel()
        }
        fetchDeparturesJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            if (!isAutoRefresh) {
                _realTimeLoading.value = true
                _realTimeError.value = null
            }
            try {
                if (!com.example.util.isNetworkAvailable(getApplication())) {
                    if (_realTimeSelectedStationId.value == stationId) {
                        _realTimeDepartures.value = emptyList()
                        _realTimeError.value = "Sin conexión a internet"
                    }
                    return@launch
                }

                val numericId = stationId.toIntOrNull()
                if (numericId == null) {
                    if (!isAutoRefresh && _realTimeSelectedStationId.value == stationId) {
                        _realTimeDepartures.value = emptyList()
                        _realTimeError.value = "Estación no encontrada"
                        _realTimeLoading.value = false
                    }
                    return@launch
                }

                val currentStation = _allNetworkStations.value.find { it.id == stationId || getStationSlug(it.id) == getStationSlug(stationId) }
                val stationLines = currentStation?.lines ?: emptyList()

                val rawArrivals = com.example.data.repository.RealTimeTransitRepository.getMetroLiveArrivals(
                    stationId = numericId.toString(),
                    forceRefresh = !isAutoRefresh
                )

                // If user switched stations while network request was running, discard result
                if (_realTimeSelectedStationId.value != stationId) {
                    return@launch
                }

                val nowMs = System.currentTimeMillis()
                val departuresList = rawArrivals.map { arrival ->
                    val lineObj = ValenciaMetroData.lines.find { 
                        it.id.equals(arrival.line, ignoreCase = true) ||
                        it.id.replace("L", "").trim().equals(arrival.line.replace("L", "").trim(), ignoreCase = true)
                    }
                    val colorHex = lineObj?.colorHex ?: "#7F8C8D"
                    val targetArrivalEpochMs = nowMs + arrival.seconds * 1000L
                    val targetMinuteBucket = targetArrivalEpochMs / 60_000L
                    val departureId = if (!arrival.vehicleId.isNullOrBlank()) {
                        "${arrival.line}_${arrival.destination}_${arrival.vehicleId}"
                    } else {
                        "${arrival.line}_${arrival.destination}_$targetMinuteBucket"
                    }
                    RealTimeDeparture(
                        id = departureId,
                        lineId = arrival.line,
                        destination = arrival.destination,
                        minutesRemaining = arrival.minutes,
                        secondsRemaining = arrival.seconds,
                        colorHex = colorHex,
                        estimatedTime = arrival.estimatedTime,
                        status = arrival.status,
                        track = arrival.track,
                        capacidad = arrival.capacidad,
                        aforoBloqueado = arrival.aforoBloqueado,
                        vehicleId = arrival.vehicleId,
                        targetArrivalEpochMs = targetArrivalEpochMs,
                        isRealTime = arrival.isRealTime
                    )
                }.sortedBy { it.secondsRemaining }

                val detectedAforo = rawArrivals.firstOrNull { it.aforoBloqueado != null }?.aforoBloqueado
                    ?: com.example.data.repository.RealTimeTransitRepository.getAforoBloqueadoForStation(stationId)

                val filteredList = departuresList.filter { dep ->
                    val cleanDep = dep.lineId.replace("L", "").trim()
                    if (com.example.util.MetroDepotFilterHelper.isDepotExcludedStationLine(stationId, currentStation?.name, cleanDep)) {
                        return@filter false
                    }
                    if (stationLines.isNotEmpty()) {
                        stationLines.any { sl ->
                            sl.equals(dep.lineId, ignoreCase = true) ||
                            sl.replace("L", "").trim().equals(cleanDep, ignoreCase = true)
                        }
                    } else {
                        true
                    }
                }

                if (_realTimeSelectedStationId.value == stationId) {
                    if (filteredList.isNotEmpty() || !isAutoRefresh) {
                        _realTimeDepartures.value = filteredList
                    } else if (filteredList.isEmpty() && isAutoRefresh && _realTimeDepartures.value.isNotEmpty()) {
                        // On auto-refresh, if empty received from transient network, keep existing non-expired departures
                        val elapsedSeconds = 30
                        val updated = _realTimeDepartures.value.mapNotNull { dep ->
                            val newSec = dep.secondsRemaining - elapsedSeconds
                            if (newSec >= -10) {
                                dep.copy(
                                    secondsRemaining = newSec,
                                    minutesRemaining = kotlin.math.max(0, newSec / 60)
                                )
                            } else null
                        }
                        if (updated.isNotEmpty()) {
                            _realTimeDepartures.value = updated
                        } else {
                            _realTimeDepartures.value = emptyList()
                        }
                    }
                    _stationAforoBloqueado.value = detectedAforo
                    _realTimeError.value = null
                }
            } catch (e: Exception) {
                Log.w("RealTimeMetro", "Could not fetch live departures for $stationId: ${e.message}")
                if (_realTimeSelectedStationId.value == stationId && !isAutoRefresh) {
                    _realTimeDepartures.value = emptyList()
                    val isOffline = !com.example.util.isNetworkAvailable(getApplication())
                    _realTimeError.value = if (isOffline) "Sin conexión a internet" else null
                }
            } finally {
                if (!isAutoRefresh && _realTimeSelectedStationId.value == stationId) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < 300L) {
                        delay(300L - elapsed)
                    }
                    _realTimeLoading.value = false
                }
            }
        }
    }

    fun getStationDistanceText(station: MetroStation): String? {
        val loc = _lastLocation.value ?: return null
        val dist = LocationUtils.calculateDistanceMeters(loc.latitude, loc.longitude, station.latitude, station.longitude)
        return LocationUtils.formatDistance(dist)
    }

    fun startRealTimeRefreshTicker() {
        realTimeRefreshJob?.cancel()
        realTimeRefreshJob = viewModelScope.launch {
            try {
                while (true) {
                    val currentStation = _realTimeSelectedStationId.value ?: return@launch
                    fetchRealTimeDepartures(currentStation, isAutoRefresh = true)

                    // Metrovalencia live API refresh interval: 30 seconds
                    val nextDelayMs = 30000L
                    delay(nextDelayMs)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when ticker is cancelled/restarted
            } catch (e: Exception) {
                Log.e("MetroViewModel", "Error in real-time refresh ticker", e)
            }
        }
    }

    fun stopRealTimeRefreshTicker() {
        realTimeRefreshJob?.cancel()
        realTimeRefreshJob = null
    }



    fun getIncidentsForLine(lineId: String): List<MetroIncident> {
        val numericDigit = lineId.filter { it.isDigit() }.trimStart('0')
        if (numericDigit.isEmpty()) return emptyList()
        return activeIncidents.value.filter { incident ->
            val fgvDigits = incident.lineaFgv?.split(",", " ")
                ?.map { it.filter { c -> c.isDigit() }.trimStart('0') }
                ?.filter { it.isNotEmpty() } ?: emptyList()

            val matchesFgv = fgvDigits.contains(numericDigit) ||
                    incident.lineaFgv?.contains("L$numericDigit") == true ||
                    incident.lineaFgv?.contains("L $numericDigit") == true

            val esLines = MetroMapper.extractLineNumbersFromText(incident.descriptionEs)
            val caLines = MetroMapper.extractLineNumbersFromText(incident.descriptionCa)
            val enLines = MetroMapper.extractLineNumbersFromText(incident.descriptionEn)
            val mentionsLine = esLines?.contains(numericDigit) == true ||
                    caLines?.contains(numericDigit) == true ||
                    enLines?.contains(numericDigit) == true ||
                    incident.descriptionEs.contains("Línea $numericDigit", ignoreCase = true) ||
                    incident.descriptionEs.contains("Línea L$numericDigit", ignoreCase = true) ||
                    incident.descriptionEs.contains("L$numericDigit", ignoreCase = true) ||
                    incident.descriptionCa.contains("Línia $numericDigit", ignoreCase = true) ||
                    incident.descriptionCa.contains("Línia L$numericDigit", ignoreCase = true) ||
                    incident.descriptionCa.contains("L$numericDigit", ignoreCase = true)

            val isGeneralNetworkIncident = (incident.lineaFgv.isNullOrBlank() || incident.lineaFgv == "0") &&
                    esLines.isNullOrEmpty() && caLines.isNullOrEmpty() && enLines.isNullOrEmpty()

            matchesFgv || mentionsLine || isGeneralNetworkIncident
        }.distinctBy { incident ->
            if (incident.id.isNotBlank()) incident.id.trim()
            else (incident.descriptionEs.trim() + "_" + incident.descriptionCa.trim()).ifBlank { incident.toString() }
        }
    }

fun autoSelectNearestMetroStationIfNeeded(force: Boolean = false) {
        if (hasUserManuallySelectedStation && !force) return
        val sorted = sortedFavoriteStations.value
        if (sorted.isNotEmpty()) {
            val topFav = sorted.first()
            if (_realTimeSelectedStationId.value != topFav) {
                selectRealTimeStation(topFav, isUserAction = false)
            }
        } else {
            val all = _allNetworkStations.value
            if (all.isNotEmpty()) {
                val loc = _lastLocation.value
                val (lat, lon) = if (loc != null) Pair(loc.latitude, loc.longitude) else Pair(valenciaCenterLat, valenciaCenterLon)
                val closest = all.minByOrNull { LocationUtils.calculateDistanceMeters(lat, lon, it.latitude, it.longitude) }
                val targetId = closest?.id ?: "15"
                if (_realTimeSelectedStationId.value != targetId) {
                    selectRealTimeStation(targetId, isUserAction = false)
                }
            } else if (_realTimeSelectedStationId.value.isNullOrBlank()) {
                selectRealTimeStation("15", isUserAction = false)
            }
        }
    }

private fun startAlertsRefreshTicker() {
        alertsRefreshJob?.cancel()
        alertsRefreshJob = viewModelScope.launch {
            while (true) {
                if (_isAppInForeground.value) {
                    val now = System.currentTimeMillis()
                    if (now - lastAlertsFetchTime >= 15 * 60 * 1000L) { // 15 minutes
                        fetchAllAlerts()
                    }
                }
                delay(30000) // Check state/time every 30 seconds
            }
        }
    }

    fun getStationInfo(stationId: String): MetroStation? {
        return _allNetworkStations.value.find { it.id == stationId }
    }


    fun getStationSlug(stationId: String): String {
        return MetroMapper.getStationSlug(stationId, _allNetworkStations.value)
    }





    fun selectRealTimeStation(stationId: String, isUserAction: Boolean = true) {
        if (isUserAction) {
            hasUserManuallySelectedStation = true
        }
        val sameStation = _realTimeSelectedStationId.value == stationId
        _realTimeSelectedStationId.value = stationId
        _selectedStationId.value = stationId

        if (!sameStation) {
            fetchDeparturesJob?.cancel()
            _realTimeDepartures.value = emptyList()
            _realTimeLoading.value = true
            _realTimeError.value = null
        }

        viewModelScope.launch {
            repository.savePreference("selected_station", stationId)
            fetchRealTimeDepartures(stationId, isAutoRefresh = false)
            startRealTimeRefreshTicker()
        }
    }

    fun toggleFavoriteStation(stationId: String) {
        toggleFavoriteMetroStation(stationId)
    }

    fun getSharedLineDigits(lineDigit: String): List<String> {
        return when (lineDigit) {
            "1" -> listOf("1", "2", "7")
            "2" -> listOf("1", "2", "7")
            "3" -> listOf("3", "5", "9")
            "5" -> listOf("3", "5", "7", "9")
            "7" -> listOf("1", "2", "5", "7")
            "9" -> listOf("3", "5", "9")
            "4" -> listOf("4", "6")
            "6" -> listOf("4", "6", "8")
            "8" -> listOf("6", "8")
            "10" -> listOf("10")
            else -> listOf(lineDigit)
        }
    }

    fun getStationNameForEstacionId(estacionId: Int?, tituloEs: String? = null, descripcionEs: String? = null): String {
        if (estacionId != null) {
            val found = _allNetworkStations.value.find { it.id == estacionId.toString() }?.name
            if (found != null) return found
        }
        return "Estación de Metro"
    }

    fun selectDepartureDetails(departure: RealTimeDeparture) {
        _selectedDepartureForDetails.value = departure
        _isBottomSheetVisible.value = true
    }

    fun dismissDepartureDetails() {
        _isBottomSheetVisible.value = false
        _selectedDepartureForDetails.value = null
    }

    fun toggleStationInfoExpanded() {
        _isStationInfoExpanded.value = !_isStationInfoExpanded.value
    }

    fun fetchAllAlerts() {
        viewModelScope.launch {
            metroAlertsRepository.fetchAllAlerts()
            lastAlertsFetchTime = System.currentTimeMillis()
        }
    }

    // Transit Cards Logic
    private val _isRefreshingCards = MutableStateFlow(false)
    val isRefreshingCards = _isRefreshingCards.asStateFlow()

    val transitCardsFlow = metroCardRepository.transitCardsFlow
        .map { list -> list.map { MetroMapper.mapToUiModel(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateTransitCardName(cardNumber: String, newName: String) {
        viewModelScope.launch {
            metroCardRepository.updateTransitCardName(cardNumber, newName)
        }
    }

    fun updateTransitCardManualStatus(cardNumber: String, isManuallyInactive: Boolean) {
        viewModelScope.launch {
            metroCardRepository.updateTransitCardManualStatus(cardNumber, isManuallyInactive)
        }
    }

    fun updateCardHomeVisibility(cardNumber: String, showOnHome: Boolean) {
        viewModelScope.launch {
            metroCardRepository.updateCardHomeVisibility(cardNumber, showOnHome)
        }
    }

    fun updateCardsOrder(cardNumbersInOrder: List<String>) {
        viewModelScope.launch {
            metroCardRepository.updateCardsOrder(cardNumbersInOrder)
        }
    }

    fun moveCardUp(cardNumber: String) {
        viewModelScope.launch {
            val currentCards = transitCardsFlow.value
            val index = currentCards.indexOfFirst { it.cardNumber == cardNumber }
            if (index > 0) {
                val mutable = currentCards.map { it.cardNumber }.toMutableList()
                val item = mutable.removeAt(index)
                mutable.add(index - 1, item)
                metroCardRepository.updateCardsOrder(mutable)
            }
        }
    }

    fun moveCardDown(cardNumber: String) {
        viewModelScope.launch {
            val currentCards = transitCardsFlow.value
            val index = currentCards.indexOfFirst { it.cardNumber == cardNumber }
            if (index in 0 until currentCards.size - 1) {
                val mutable = currentCards.map { it.cardNumber }.toMutableList()
                val item = mutable.removeAt(index)
                mutable.add(index + 1, item)
                metroCardRepository.updateCardsOrder(mutable)
            }
        }
    }

    fun deleteTransitCard(cardNumber: String) {
        viewModelScope.launch {
            metroCardRepository.deleteTransitCard(cardNumber)
        }
    }

    fun addTransitCard(
        cardNumber: String,
        customName: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = metroCardRepository.addTransitCard(cardNumber, customName)
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { throwable -> onError(throwable.message ?: "Ocurrió un error al añadir la tarjeta.") }
            )
        }
    }

    fun refreshTransitCards() {
        viewModelScope.launch {
            _isRefreshingCards.value = true
            try {
                metroCardRepository.refreshTransitCards()
            } finally {
                _isRefreshingCards.value = false
            }
        }
    }

    fun autoRefreshCardsIfNeeded() {
        viewModelScope.launch {
            try {
                metroCardRepository.refreshTransitCardsIfNeeded()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}