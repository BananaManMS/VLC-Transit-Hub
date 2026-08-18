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

    private val _lineStationsMap = MutableStateFlow<Map<String, com.example.data.repository.LineStationInfo>>(emptyMap())
    val lineStationsMap = MutableStateFlow<Map<String, List<com.example.data.repository.LineStationInfo>>>(emptyMap()).asStateFlow()
    private val _internalLineStationsMap = MutableStateFlow<Map<String, List<com.example.data.repository.LineStationInfo>>>(emptyMap())
    val lineStationsState = _internalLineStationsMap.asStateFlow()

    private val _selectedStationId = MutableStateFlow("15")
    val selectedStationId = _selectedStationId.asStateFlow()

    private var hasAutoSelectedClosestOnLaunch = false

    private val _favoriteStations = MutableStateFlow<List<String>>(listOf("16", "15", "14"))
    val favoriteStations = _favoriteStations.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation = _lastLocation.asStateFlow()

    private val _departures = MutableStateFlow<List<RealTimeDeparture>>(emptyList())
    val departures = _departures.asStateFlow()
    private val _realTimeDepartures = MutableStateFlow<List<RealTimeDeparture>>(emptyList())
    val realTimeDepartures = _realTimeDepartures.asStateFlow()

    private val _realTimeError = MutableStateFlow<String?>(null)
    val realTimeError = _realTimeError.asStateFlow()

    val activeIncidents = metroAlertsRepository.activeIncidents
    val isMetroAlertsLoading = metroAlertsRepository.isAlertsLoading

    val twitterIncidents = metroAlertsRepository.twitterIncidents

    val accessibilityIncidents = metroAlertsRepository.accessibilityIncidents

    private val _realTimeSelectedStationId = MutableStateFlow<String?>("15")
    val realTimeSelectedStationId = _realTimeSelectedStationId.asStateFlow()

    private val _selectedDepartureForDetails = MutableStateFlow<RealTimeDeparture?>(null)
    val selectedDepartureForDetails = _selectedDepartureForDetails.asStateFlow()

    private val _isBottomSheetVisible = MutableStateFlow(false)
    val isBottomSheetVisible = _isBottomSheetVisible.asStateFlow()

    private val _isStationInfoExpanded = MutableStateFlow(false)
    val isStationInfoExpanded = _isStationInfoExpanded.asStateFlow()


    private val _realTimeLoading = MutableStateFlow(false)
    val realTimeLoading = _realTimeLoading.asStateFlow()

    val twitterLoading = metroAlertsRepository.twitterLoading

    private var lastTwitterFetchTime = 0L
    private var lastAlertsFetchTime = 0L
    private val _isAppInForeground = MutableStateFlow(true)
    

    
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
        fetchTwitterIncidents()
    }

    private fun loadLineStationsData() {
        viewModelScope.launch(Dispatchers.IO) {
            _internalLineStationsMap.value = metroRepository.loadMetroLineStations()
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedMetroFavs = repository.getPreference("favorite_stations", "16,15,14")
            _favoriteStations.value = savedMetroFavs.split(",").filter { it.isNotEmpty() }
            
            val savedMetroStation = repository.getPreference("selected_station", "15")
            _selectedStationId.value = savedMetroStation
            _realTimeSelectedStationId.value = savedMetroStation
            
            withContext(Dispatchers.Main) {
                updateDeparturesList()
                fetchRealTimeDepartures(savedMetroStation)
                startRealTimeRefreshTicker()
            }
        }
    }

    private fun loadMetroStations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentVersion = repository.getPreference("metro_stations_json_version", "0")
                val defaultStations = metroRepository.loadMetroStations(forceReload = true)
                val currentDbStations = database.stationDao().getAllStations()

                if (currentVersion != "8" || currentDbStations.size != defaultStations.size) {
                    database.stationDao().deleteAllStations()
                    database.stationDao().insertAll(defaultStations.map { station ->
                        StationEntity(
                            id = station.id.toIntOrNull() ?: Math.abs(station.id.hashCode()),
                            name = station.name,
                            lines = station.lines.joinToString(","),
                            zone = station.zone,
                            latitude = station.latitude,
                            longitude = station.longitude
                        )
                    })
                    repository.savePreference("metro_stations_json_version", "8")
                }

                var dbStations = database.stationDao().getAllStations()
                if (dbStations.isEmpty()) {
                    database.stationDao().insertAll(defaultStations.map { station ->
                        StationEntity(
                            id = station.id.toIntOrNull() ?: Math.abs(station.id.hashCode()),
                            name = station.name,
                            lines = station.lines.joinToString(","),
                            zone = station.zone,
                            latitude = station.latitude,
                            longitude = station.longitude
                        )
                    })
                    dbStations = database.stationDao().getAllStations()
                }

                if (dbStations.isNotEmpty()) {
                    val stations = dbStations.map { entity ->
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
                    _allNetworkStations.value = defaultStations.distinctBy { it.id }
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
    }




private fun updateDeparturesList() {
        val stationId = _selectedStationId.value
        val slug = getStationSlug(stationId)
        val station = _allNetworkStations.value.find { it.id == stationId || getStationSlug(it.id) == slug }
        val stationLines = station?.lines ?: emptyList()

        _departures.value = emptyList()
    }

fun selectStation(stationId: String) {
        _selectedStationId.value = stationId
        _realTimeSelectedStationId.value = stationId
        viewModelScope.launch {
            repository.savePreference("selected_station", stationId)
            updateDeparturesList()
            fetchRealTimeDepartures(stationId)
            startRealTimeRefreshTicker()
        }
    }

fun toggleFavoriteMetroStation(stationId: String) {
        val current = _favoriteStations.value.toMutableList()
        if (current.contains(stationId)) {
            if (current.size > 1) { // Mantener al menos 1 estación favorita
                current.remove(stationId)
                updateFavoriteStations(current)
            }
        } else {
            if (current.size < 10) { // Límite de 10 favoritas
                current.add(stationId)
                updateFavoriteStations(current)
            }
        }
    }

fun updateFavoriteStations(stationIds: List<String>) {
        if (stationIds.size in 1..10) {
            _favoriteStations.value = stationIds
            viewModelScope.launch {
                repository.savePreference("favorite_stations", stationIds.joinToString(","))
                if (!stationIds.contains(_selectedStationId.value)) {
                    selectStation(stationIds.first())
                } else {
                    updateDeparturesList()
                    // generateSmartAdvisory()
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
        viewModelScope.launch {
            if (!isAutoRefresh) {
                _realTimeLoading.value = true
                _realTimeError.value = null
            }
            try {
                val numericId = stationId.toIntOrNull()
                if (numericId == null) {
                    if (!isAutoRefresh) {
                        _realTimeDepartures.value = emptyList()
                        _realTimeError.value = "Estación no encontrada"
                        _realTimeLoading.value = false
                    }
                    return@launch
                }

                val currentStation = _allNetworkStations.value.find { it.id == stationId || getStationSlug(it.id) == getStationSlug(stationId) }
                val stationLines = currentStation?.lines ?: emptyList()

                val rawArrivals = com.example.data.repository.RealTimeTransitRepository.getMetroLiveArrivals(numericId.toString())
                val departuresList = rawArrivals.map { arrival ->
                    val lineObj = ValenciaMetroData.lines.find { it.id == arrival.line }
                    val colorHex = lineObj?.colorHex ?: "#7F8C8D"
                    RealTimeDeparture(
                        lineId = arrival.line,
                        destination = arrival.destination,
                        minutesRemaining = arrival.minutes,
                        secondsRemaining = arrival.seconds,
                        colorHex = colorHex
                    )
                }.sortedBy { it.secondsRemaining }

                val filteredList = if (stationLines.isNotEmpty()) {
                    departuresList.filter { dep -> stationLines.contains(dep.lineId) }
                } else {
                    departuresList
                }

                _realTimeDepartures.value = filteredList
                _realTimeError.value = null
            } catch (e: Exception) {
                Log.w("RealTimeMetro", "Could not fetch live departures for $stationId: ${e.message}")
                _realTimeDepartures.value = emptyList()
                _realTimeError.value = null
            } finally {
                if (!isAutoRefresh) {
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
            while (true) {
                val currentStation = _realTimeSelectedStationId.value ?: return@launch
                fetchRealTimeDepartures(currentStation, isAutoRefresh = true)

                // Adaptive polling delay based on closest departure
                val minMins = _realTimeDepartures.value.minOfOrNull { it.minutesRemaining } ?: 999
                val nextDelayMs = when {
                    minMins < 3 -> 20000L   // <3 min -> 20s
                    minMins <= 10 -> 30000L // 3 to 10 min -> 30s
                    else -> 60000L          // >10 min or empty -> 60s
                }

                // Check if Geoportal stops sync is needed (every 1 hour in foreground)
                try {
                    val lastSyncStr = repository.getPreference("last_geoportal_sync", "0")
                    val lastSync = lastSyncStr.toLongOrNull() ?: 0L
                    val now = System.currentTimeMillis()
                    if (now - lastSync > 3600000L) { // 1 hour
                    }
                } catch (e: Exception) {
                    Log.e("GeoportalSync", "Error in foreground hourly sync", e)
                }

                delay(nextDelayMs)
            }
        }
    }

    fun stopRealTimeRefreshTicker() {
        realTimeRefreshJob?.cancel()
        realTimeRefreshJob = null
    }



    fun getIncidentsForLine(lineId: String): List<MetroIncident> {
        val numericDigit = lineId.filter { it.isDigit() }
        if (numericDigit.isEmpty()) return emptyList()
        return activeIncidents.value.filter { incident ->
            val matchesFgv = incident.lineaFgv == numericDigit
            val esLines = MetroMapper.extractLineNumbersFromText(incident.descriptionEs)
            val caLines = MetroMapper.extractLineNumbersFromText(incident.descriptionCa)
            val enLines = MetroMapper.extractLineNumbersFromText(incident.descriptionEn)
            val mentionsLine = esLines?.contains(numericDigit) == true ||
                    caLines?.contains(numericDigit) == true ||
                    enLines?.contains(numericDigit) == true

            val isGeneralNetworkIncident = (incident.lineaFgv.isNullOrBlank() || incident.lineaFgv == "0") &&
                    esLines.isNullOrEmpty() && caLines.isNullOrEmpty() && enLines.isNullOrEmpty()

            matchesFgv || mentionsLine || isGeneralNetworkIncident
        }.distinctBy { incident ->
            if (incident.id.isNotBlank()) incident.id.trim()
            else (incident.descriptionEs.trim() + "_" + incident.descriptionCa.trim()).ifBlank { incident.toString() }
        }
    }

fun autoSelectNearestMetroStationIfNeeded() {
        if (hasAutoSelectedClosestOnLaunch) return
        val favorites = _favoriteStations.value
        if (favorites.isEmpty()) return

        val sorted = sortedFavoriteStations.value
        if (sorted.isEmpty()) return

        hasAutoSelectedClosestOnLaunch = true
        // selectRealTimeStation(sorted.first())
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

    fun fetchAccessibilityAlerts() {
        viewModelScope.launch {
            metroAlertsRepository.fetchAccessibilityAlerts()
        }
    }





    fun fetchTwitterIncidents() {
        viewModelScope.launch {
            metroAlertsRepository.fetchTwitterIncidents()
            lastTwitterFetchTime = System.currentTimeMillis()
        }
    }

fun getStationInfo(stationId: String): MetroStation? {
        return _allNetworkStations.value.find { it.id == stationId }
    }


    fun getStationSlug(stationId: String): String {
        return MetroMapper.getStationSlug(stationId, _allNetworkStations.value)
    }





    fun selectRealTimeStation(stationId: String) {
        val sameStation = _realTimeSelectedStationId.value == stationId
        _realTimeSelectedStationId.value = stationId
        _selectedStationId.value = stationId
        viewModelScope.launch {
            repository.savePreference("selected_station", stationId)
            if (!sameStation || _realTimeDepartures.value.isEmpty()) {
                fetchRealTimeDepartures(stationId)
            }
            startRealTimeRefreshTicker()
        }
    }

    fun fetchRealTimeAlerts() {
        viewModelScope.launch {
            metroAlertsRepository.fetchRealTimeAlerts()
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


}