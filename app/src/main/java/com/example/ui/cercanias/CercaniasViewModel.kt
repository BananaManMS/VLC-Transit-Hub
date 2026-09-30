package com.example.ui.cercanias

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.mapper.CercaniasDepartureMapper
import com.example.data.repository.DashboardRepository
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.dashboard.AppLanguage
import com.example.util.LocationUtils
import com.example.util.removeAccents
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import android.location.Location
import android.util.Log

class CercaniasViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DashboardRepository(application, database)
    private val renfeRepository = RenfeRepository(application, database)

    // UI state flows
    val isDarkMode: StateFlow<Boolean> = repository.getPreferenceFlow(
        "is_dark_mode",
        repository.getPreferenceSync("is_dark_mode", "false")
    )
        .map { it.toBoolean() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = repository.getPreferenceSync("is_dark_mode", "false").toBoolean()
        )

    private val _appLanguage = MutableStateFlow(
        try {
            AppLanguage.valueOf(repository.getPreferenceSync("app_language", "CA"))
        } catch (e: Exception) {
            AppLanguage.CA
        }
    )
    val appLanguage = _appLanguage.asStateFlow()

    private val valenciaCenterLat = 39.46975
    private val valenciaCenterLon = -0.37739

    private val _lastLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val lastLocation = _lastLocation.asStateFlow()

    // Cercanías State
    private val _cercaniasDepartures = MutableStateFlow<List<CercaniasDeparture>>(emptyList())
    val cercaniasDepartures: StateFlow<List<CercaniasDeparture>> = _cercaniasDepartures.asStateFlow()

    private val _cercaniasLoading = MutableStateFlow(false)
    val cercaniasLoading: StateFlow<Boolean> = _cercaniasLoading.asStateFlow()

    private val _cercaniasError = MutableStateFlow<String?>(null)
    val cercaniasError: StateFlow<String?> = _cercaniasError.asStateFlow()

    private val _cercaniasSelectedStationId = MutableStateFlow("")
    val cercaniasSelectedStationId: StateFlow<String> = _cercaniasSelectedStationId.asStateFlow()

    private var cercaniasJob: Job? = null
    
    var hasUserManuallySelectedStation = false
        private set
    private val _selectedCercaniasDeparture = MutableStateFlow<CercaniasDeparture?>(null)
    val selectedCercaniasDeparture = _selectedCercaniasDeparture.asStateFlow()
    
    private val _isCercaniasBottomSheetVisible = MutableStateFlow(false)
    val isCercaniasBottomSheetVisible = _isCercaniasBottomSheetVisible.asStateFlow()

    private val _cercaniasAlerts = MutableStateFlow<List<CercaniasAlert>>(emptyList())
    val cercaniasAlerts = _cercaniasAlerts.asStateFlow()

    private val _isCercaniasAlertsLoading = MutableStateFlow(true)
    val isCercaniasAlertsLoading = _isCercaniasAlertsLoading.asStateFlow()

    private val _hasCercaniasAlertsError = MutableStateFlow(false)
    val hasCercaniasAlertsError = _hasCercaniasAlertsError.asStateFlow()

    private val _allCercaniasStations = MutableStateFlow<List<CercaniasStationEntity>>(emptyList())
    val allCercaniasStations: StateFlow<List<CercaniasStationEntity>> = _allCercaniasStations.asStateFlow()

    val activeCercaniasAlerts: StateFlow<List<CercaniasAlert>> = _cercaniasAlerts
        .map { alerts -> alerts.filter { !it.isAccessibility && it.isCirculationIncident } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val generalCercaniasNotices: StateFlow<List<CercaniasAlert>> = _cercaniasAlerts
        .map { alerts -> alerts.filter { !it.isAccessibility && !it.isCirculationIncident } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val groupedAccessibilityAlerts: StateFlow<Map<String, List<CercaniasAlert>>> = combine(
        _cercaniasAlerts,
        _allCercaniasStations
    ) { alerts, stations ->
        val accessibilityAlerts = alerts.filter { it.isAccessibility }
        val groups = mutableMapOf<String, MutableList<CercaniasAlert>>()
        val excludedPhrases = listOf(
            "sillas de ruedas",
            "silla de ruedas",
            "sillas de rueda",
            "silla de rueda"
        )
        for (alert in accessibilityAlerts) {
            var stationName: String? = null
            
            if (alert.stopIds.isNotEmpty()) {
                val matched = stations.find { station ->
                    alert.stopIds.any { sId ->
                        val cleanSId = sId.substringBefore('_').substringBefore('-').trim()
                        cleanSId == station.id
                    }
                }
                if (matched != null) {
                    stationName = matched.displayName
                }
            }
            
            if (stationName == null) {
                val textToSearchLower = alert.headerEs.lowercase(java.util.Locale.ROOT)
                var sanitizedText = textToSearchLower
                for (phrase in excludedPhrases) {
                    sanitizedText = sanitizedText.replace(phrase, " ")
                }
                val matched = stations.sortedByDescending { it.nombre.length }.find { station ->
                    val nameLower = station.nombre.lowercase(java.util.Locale.ROOT).trim()
                    if (nameLower.length > 3) {
                        containsWordBoundaryMatch(sanitizedText, nameLower)
                    } else {
                        false
                    }
                }
                if (matched != null) {
                    stationName = matched.displayName
                }
            }
            
            val key = stationName ?: if (alert.routeIds.any { VALENCIA_VALID_LINES.contains(it) }) "Red de València" else null
            if (key != null) {
                groups.getOrPut(key) { mutableListOf() }.add(alert)
            }
        }
        groups.toList().sortedWith(compareBy { (key, _) ->
            if (key == "Red de València") "zzz" else key
        }).toMap()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val accessibilityCercaniasAlerts: StateFlow<List<CercaniasAlert>> = groupedAccessibilityAlerts
        .map { it.values.flatten() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _cercaniasFavoriteStations = MutableStateFlow<List<CercaniasStationEntity>>(emptyList())
    val cercaniasFavoriteStations: StateFlow<List<CercaniasStationEntity>> = combine(
        _cercaniasFavoriteStations,
        _lastLocation
    ) { favorites, loc ->
        val (lat, lon) = loc ?: Pair(valenciaCenterLat, valenciaCenterLon)
        favorites.sortedBy { station ->
            LocationUtils.calculateDistanceMeters(lat, lon, station.latitud, station.longitud)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            CercaniasRouteUtils.init(getApplication())

            val savedLang = repository.getPreference("app_language", "CA")
            _appLanguage.value = try { AppLanguage.valueOf(savedLang) } catch (e: Exception) { AppLanguage.CA }

            val isOnboardingCompleted = repository.getPreference("has_completed_onboarding", "false") == "true"
            if (isOnboardingCompleted) {
                renfeRepository.initDatabaseFromAssetsIfNeeded()
                renfeRepository.syncScheduleFromRemoteIfNeeded()
            }

            val savedCercaniasFavs = repository.getPreference("favorite_cercanias_stations", "")
            if (savedCercaniasFavs.isNotBlank() && isOnboardingCompleted) {
                val favIds = savedCercaniasFavs.split(",").filter { it.isNotBlank() }.toSet()
                val all = renfeRepository.getAllStations()
                if (all.any { it.stop_id in favIds && !it.isFavorite }) {
                    val updated = all.map { station ->
                        station.copy(isFavorite = favIds.contains(station.stop_id))
                    }
                    renfeRepository.updateAllStations(updated)
                }
            }

            _allCercaniasStations.value = renfeRepository.getAllStations()
            fetchCercaniasRealTimeAlerts()

            renfeRepository.getFavoriteStationsFlow().collect { favs ->
                _cercaniasFavoriteStations.value = favs
                if (!hasUserManuallySelectedStation) {
                    autoSelectNearestCercaniasStationIfNeeded()
                }
            }
        }

        viewModelScope.launch {
            cercaniasFavoriteStations.collect { sortedFavs ->
                if (!hasUserManuallySelectedStation && sortedFavs.isNotEmpty()) {
                    val topFav = sortedFavs.first()
                    val targetId = topFav.stop_id.ifBlank { topFav.id }
                    if (_cercaniasSelectedStationId.value != targetId) {
                        selectCercaniasStation(targetId, isUserAction = false)
                    }
                }
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double) {
        _lastLocation.value = Pair(lat, lon)
        if (!hasUserManuallySelectedStation) {
            autoSelectNearestCercaniasStationIfNeeded()
        }
    }

    fun fetchCercaniasDepartures() {
        cercaniasJob?.cancel()
        _cercaniasLoading.value = true
        _cercaniasError.value = null

        cercaniasJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var isFirstLoop = true
            while (isActive) {
                val startTime = System.currentTimeMillis()
                try {
                    val stationId = _cercaniasSelectedStationId.value
                    if (stationId.isNotBlank()) {
                        val rawDepartures = renfeRepository.getDeparturesForStation(stationId)
                        val filteredAndSorted = CercaniasDepartureMapper.sortDeparturesChronologically(rawDepartures)
                        
                        _cercaniasDepartures.value = filteredAndSorted
                        _cercaniasError.value = null

                        val currentSelected = _selectedCercaniasDeparture.value
                        if (currentSelected != null) {
                            val selectedKeys = (listOf(currentSelected.tripId) + currentSelected.allTripIds)
                                .flatMap { CercaniasRouteUtils.getTripKeys(it) }
                                .distinct()

                            val updated = filteredAndSorted.find { item ->
                                val itemKeys = (listOf(item.tripId) + item.allTripIds)
                                    .flatMap { CercaniasRouteUtils.getTripKeys(it) }
                                    .distinct()
                                selectedKeys.any { sk -> itemKeys.contains(sk) } ||
                                (item.departureTime == currentSelected.departureTime && item.routeId == currentSelected.routeId)
                            }
                            if (updated != null) {
                                _selectedCercaniasDeparture.value = updated
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (_cercaniasDepartures.value.isEmpty()) {
                        _cercaniasError.value = "Error conectando con Renfe"
                    }
                } finally {
                    if (isFirstLoop) {
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed < 400L) {
                            delay(400L - elapsed)
                        }
                        _cercaniasLoading.value = false
                        isFirstLoop = false
                    }
                }
                
                // Polling delay: guaranteed refresh every 30s (20s if departure is imminent)
                val minMins = _cercaniasDepartures.value.minOfOrNull { it.minutesRemaining } ?: 999
                val nextDelayMs = if (minMins < 3) 20000L else 30000L
                delay(nextDelayMs)
            }
        }
    }

    fun stopCercaniasPolling() {
        cercaniasJob?.cancel()
        cercaniasJob = null
    }

    fun forceSyncCercaniasSchedule() {
        cercaniasJob?.cancel()
        _cercaniasLoading.value = true
        _cercaniasError.value = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                renfeRepository.reloadFromAssets()
                renfeRepository.forceSyncScheduleFromRemote()
                val updatedStations = renfeRepository.getAllStations()
                _allCercaniasStations.value = updatedStations

                if (_cercaniasSelectedStationId.value.isBlank() && updatedStations.isNotEmpty()) {
                    autoSelectNearestCercaniasStationIfNeeded()
                }
            } catch (e: Exception) {
                Log.e("CercaniasViewModel", "Error force syncing schedule", e)
            } finally {
                fetchCercaniasDepartures()
            }
        }
    }

    fun selectCercaniasStation(stationId: String, isUserAction: Boolean = true) {
        if (isUserAction) {
            hasUserManuallySelectedStation = true
        }
        val isSameStation = _cercaniasSelectedStationId.value == stationId
        _cercaniasSelectedStationId.value = stationId
        if (!isSameStation || _cercaniasDepartures.value.isEmpty()) {
            _cercaniasDepartures.value = emptyList()
            _cercaniasLoading.value = true
            _cercaniasError.value = null
            fetchCercaniasDepartures()
        }
    }

    fun selectCercaniasDepartureDetails(departure: CercaniasDeparture) {
        _selectedCercaniasDeparture.value = departure
        _isCercaniasBottomSheetVisible.value = true
    }

    fun dismissCercaniasDepartureDetails() {
        _isCercaniasBottomSheetVisible.value = false
        _selectedCercaniasDeparture.value = null
    }

    suspend fun getAllCercaniasStations(): List<CercaniasStationEntity> {
        return renfeRepository.getAllStations()
    }

    fun getCercaniasStationDistanceText(station: CercaniasStationEntity): String? {
        val loc = _lastLocation.value ?: return null
        val distance = LocationUtils.calculateDistanceMeters(loc.first, loc.second, station.latitud, station.longitud)
        return LocationUtils.formatDistance(distance)
    }

    fun autoSelectNearestCercaniasStationIfNeeded(force: Boolean = false) {
        if (hasUserManuallySelectedStation && !force) return
        
        val favs = cercaniasFavoriteStations.value.ifEmpty { _cercaniasFavoriteStations.value }
        val all = _allCercaniasStations.value
        val loc = _lastLocation.value

        if (favs.isNotEmpty()) {
            val closestFav = if (loc != null) {
                favs.minByOrNull { station ->
                    LocationUtils.calculateDistanceMeters(loc.first, loc.second, station.latitud, station.longitud)
                } ?: favs.first()
            } else {
                favs.first()
            }
            val targetId = closestFav.stop_id.ifBlank { closestFav.id }
            selectCercaniasStation(targetId, isUserAction = false)
        } else if (all.isNotEmpty()) {
            val closestStation = if (loc != null) {
                all.minByOrNull { station ->
                    LocationUtils.calculateDistanceMeters(loc.first, loc.second, station.latitud, station.longitud)
                }
            } else {
                all.find { it.stop_id == "65000" || it.nombre.contains("Nord", ignoreCase = true) }
            }
            val targetId = (closestStation ?: all.first()).let { it.stop_id.ifBlank { it.id } }
            selectCercaniasStation(targetId, isUserAction = false)
        }
    }

    fun updateCercaniasFavoriteStations(stations: List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val favString = stations.joinToString(",")
            repository.savePreference("favorite_cercanias_stations", favString)
            val all = renfeRepository.getAllStations()
            val updated = all.map { station ->
                station.copy(isFavorite = stations.contains(station.stop_id))
            }
            renfeRepository.updateAllStations(updated)
            _allCercaniasStations.value = renfeRepository.getAllStations()
        }
    }

    fun toggleFavoriteCercaniasStation(stationId: String) {
        val currentFavs = _cercaniasFavoriteStations.value.map { it.stop_id.ifBlank { it.id } }.toMutableList()
        if (currentFavs.contains(stationId)) {
            currentFavs.remove(stationId)
        } else {
            currentFavs.add(stationId)
        }
        updateCercaniasFavoriteStations(currentFavs)
    }


    companion object {
        private val VALENCIA_VALID_LINES = listOf("C1", "C2", "C3", "C4", "C5", "C6")
    }

    private fun normalizeValenciaRouteId(raw: String): String? {
        val upper = raw.uppercase(java.util.Locale.ROOT).trim()

        // 1. Direct match: C1..C6 or C-1..C-6
        val directMatch = Regex("""^C-?([1-6])$""").find(upper)
        if (directMatch != null) {
            return "C${directMatch.groupValues[1]}"
        }

        // 2. Renfe technical pattern: e.g. 40C1, 40C2, 40T009C4, 40T0010C4, 40C6
        val valenciaCodeMatch = Regex("""^40[A-Z0-9]*?(C-?[1-6])$""").find(upper)
        if (valenciaCodeMatch != null) {
            return valenciaCodeMatch.groupValues[1].replace("-", "")
        }

        // 3. Fallback endsWith check for C1..C6
        for (line in VALENCIA_VALID_LINES) {
            if (upper.endsWith(line) || upper.endsWith("C-${line.removePrefix("C")}")) {
                return line
            }
        }

        return null
    }

    private fun extractLinesFromAlertText(text: String): List<String> {
        val found = mutableSetOf<String>()
        if (text.isBlank()) return emptyList()

        // Match explicit patterns like "Línea C1", "Línea C-1", "Línia C2", "SERVICIO POR AUTOBÚS C-5"
        val lineRegex = Regex("""(?i)\b(?:L[íi]nea|L[ií]nia|L[íi]neas|L[ií]nies|Autob[uú]s|Servicio\s+(?:por\s+autob[uú]s\s+)?)\s*(C-?[1-6])\b""")
        lineRegex.findAll(text).forEach { match ->
            val num = match.groupValues[1].uppercase(java.util.Locale.ROOT).replace("-", "")
            found.add(num)
        }

        // Match any line tokens C1..C6 (e.g. in list "líneas C1, C2 y C6")
        val anyLineRegex = Regex("""\b(C-?[1-6])\b""", RegexOption.IGNORE_CASE)
        anyLineRegex.findAll(text).forEach { match ->
            val num = match.groupValues[1].uppercase(java.util.Locale.ROOT).replace("-", "")
            found.add(num)
        }

        // Check start of string e.g. "C1. Tren..." or "C-2: ..." or "Línea C1."
        val startLineRegex = Regex("""(?i)^\s*(?:L[íi]nea\s+|L[ií]nia\s+)?(C-?[1-6])[\s.:\-]""")
        startLineRegex.find(text)?.let { match ->
            found.add(match.groupValues[1].uppercase(java.util.Locale.ROOT).replace("-", ""))
        }

        return VALENCIA_VALID_LINES.filter { found.contains(it) }
    }

    fun fetchCercaniasRealTimeAlerts() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isCercaniasAlertsLoading.value = true
            _hasCercaniasAlertsError.value = false
            try {
                val allValenciaStations = database.cercaniasStationDao().getAllStations()
                val valenciaStopIds = allValenciaStations.map { it.id }.toSet()
                
                val valenciaStationNamesNoAccents = allValenciaStations.map { 
                    removeAccents(it.nombre.lowercase(java.util.Locale.ROOT).trim()) 
                }

                val request = okhttp3.Request.Builder()
                    .url("https://gtfsrt.renfe.com/alerts.json")
                    .header("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
                    .build()

                val responseBody = com.example.data.network.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }

                if (!responseBody.isNullOrBlank()) {
                    val jsonObject = org.json.JSONObject(responseBody)
                    val entitiesArray = jsonObject.optJSONArray("entity") ?: jsonObject.optJSONArray("entities")
                    val list = mutableListOf<CercaniasAlert>()

                    if (entitiesArray != null) {
                        for (i in 0 until entitiesArray.length()) {
                            val entity = entitiesArray.optJSONObject(i) ?: continue
                            val id = entity.optString("id", "")
                            val alertObj = entity.optJSONObject("alert") ?: continue
                            
                            val headerTextObj = alertObj.optJSONObject("header_text") ?: alertObj.optJSONObject("headerText")
                            val descTextObj = alertObj.optJSONObject("description_text") ?: alertObj.optJSONObject("descriptionText")
                            
                            val headerEs = parseGtfsRtText(headerTextObj)
                            val descEs = parseGtfsRtText(descTextObj)
                            
                            if (headerEs.isBlank() && descEs.isBlank()) {
                                continue
                            }
                            
                            val informedEntitiesArray = alertObj.optJSONArray("informed_entity") ?: alertObj.optJSONArray("informedEntity")
                            val routeIds = mutableListOf<String>()
                            val tripIds = mutableListOf<String>()
                            val stopIds = mutableListOf<String>()

                            if (informedEntitiesArray != null) {
                                for (j in 0 until informedEntitiesArray.length()) {
                                    val inf = informedEntitiesArray.optJSONObject(j) ?: continue
                                    val routeId = inf.optString("route_id", "").ifBlank { inf.optString("routeId", "") }
                                    if (routeId.isNotBlank()) {
                                        routeIds.add(routeId)
                                        // Renfe encodes trip-specific alerts inside routeId e.g. "40T0053C2"
                                        val m = Regex("""40[A-Za-z0-9]*?(\d{4})C?[1-6]?""").find(routeId)
                                        if (m != null) {
                                            val num4 = m.groupValues[1]
                                            val numShort = num4.trimStart('0')
                                            tripIds.add(routeId)
                                            tripIds.add(num4)
                                            if (numShort.isNotBlank()) tripIds.add(numShort)
                                        }
                                    }
                                    
                                    val tripIdObj = inf.optJSONObject("trip")
                                    val tripId = tripIdObj?.optString("trip_id", "")?.ifBlank { tripIdObj.optString("tripId", "") } ?: ""
                                    if (tripId.isNotBlank()) tripIds.add(tripId)
                                    
                                    val stopId = inf.optString("stop_id", "").ifBlank { inf.optString("stopId", "") }
                                    if (stopId.isNotBlank()) stopIds.add(stopId)
                                }
                            }
                            
                            val cleanAlertStopIds = stopIds.map { sId -> sId.substringBefore('_').substringBefore('-').trim() }
                            
                            val hasValenciaRoute = routeIds.isNotEmpty() && routeIds.any { it.startsWith("40") }
                            val hasValenciaStop = cleanAlertStopIds.isNotEmpty() && cleanAlertStopIds.any { valenciaStopIds.contains(it) }
                            
                            val hasOtherHubRoute = routeIds.isNotEmpty() && routeIds.any { !it.startsWith("40") }
                            val hasOtherHubStop = cleanAlertStopIds.isNotEmpty() && cleanAlertStopIds.any { !valenciaStopIds.contains(it) }

                            val textToSearch = "$headerEs $descEs".lowercase(java.util.Locale.ROOT)
                            val textToSearchNoAccents = removeAccents(textToSearch)
                            
                            var isValencia = false
                            if (hasValenciaRoute || hasValenciaStop) {
                                isValencia = !hasOtherHubRoute && !hasOtherHubStop
                            } else if (!hasOtherHubRoute && !hasOtherHubStop) {
                                // Fallback to searching text only if there are no entity routes/stops of other hubs
                                val mentionsValenciaOrCastellon = textToSearchNoAccents.contains("valencia") || 
                                                                   textToSearchNoAccents.contains("valència") || 
                                                                   textToSearchNoAccents.contains("castello") || 
                                                                   textToSearchNoAccents.contains("castellon") || 
                                                                   textToSearchNoAccents.contains("gandia")
                                
                                var hasValenciaStation = false
                                if (!mentionsValenciaOrCastellon) {
                                    hasValenciaStation = valenciaStationNamesNoAccents.any { stationName ->
                                        if (stationName.isBlank()) return@any false
                                        val cleanName = stationName.replace("(", " ").replace(")", " ").replace("-", " ").trim()
                                        if (cleanName.length > 3) {
                                            containsWordBoundaryMatch(textToSearchNoAccents, cleanName)
                                        } else {
                                            false
                                        }
                                    }
                                }
                                
                                isValencia = mentionsValenciaOrCastellon || hasValenciaStation
                                
                                val mentionsOtherHub = textToSearchNoAccents.contains("madrid") || 
                                                       textToSearchNoAccents.contains("barcelona") || 
                                                       textToSearchNoAccents.contains("catalunya") || 
                                                       textToSearchNoAccents.contains("sevilla") || 
                                                       textToSearchNoAccents.contains("malaga") || 
                                                       textToSearchNoAccents.contains("bilbao") || 
                                                       textToSearchNoAccents.contains("san sebastian") || 
                                                       textToSearchNoAccents.contains("donostia") || 
                                                       textToSearchNoAccents.contains("asturias") || 
                                                       textToSearchNoAccents.contains("cantabria") || 
                                                       textToSearchNoAccents.contains("zaragoza") || 
                                                       textToSearchNoAccents.contains("cadiz") ||
                                                       textToSearchNoAccents.contains("valdemoro") ||
                                                       textToSearchNoAccents.contains("aranjuez") ||
                                                       textToSearchNoAccents.contains("getafe") ||
                                                       textToSearchNoAccents.contains("mostoles") ||
                                                       textToSearchNoAccents.contains("alcala") ||
                                                       textToSearchNoAccents.contains("parla") ||
                                                       textToSearchNoAccents.contains("leganes")
                                if (mentionsOtherHub && !textToSearchNoAccents.contains("valencia")) {
                                    isValencia = false
                                }
                            }
                            
                            // If alert explicitly targets ONLY other hubs with zero Valencia references, skip it
                            if (!isValencia) {
                                continue 
                            }
                            
                            val isAccessibility = CercaniasAlertClassifier.isAccessibility(textToSearch)
                            val isCirculation = !isAccessibility && CercaniasAlertClassifier.isStrongCirculationIncident(headerEs, descEs)
                            
                            val timestamp = alertObj.optLong("timestamp", System.currentTimeMillis() / 1000)

                            val linesInText = extractLinesFromAlertText("$headerEs. $descEs")
                            val entityRouteIds = routeIds.mapNotNull { normalizeValenciaRouteId(it) }.distinct()

                            // If text explicitly mentions specific line(s) (e.g. "Línea C1. Tren con salida..."),
                            // prioritize them over entity routes to avoid over-matching multi-route hub stations.
                            val finalRouteIds = if (linesInText.isNotEmpty()) {
                                linesInText
                            } else if (entityRouteIds.isNotEmpty()) {
                                VALENCIA_VALID_LINES.filter { entityRouteIds.contains(it) }
                            } else {
                                emptyList()
                            }

                            list.add(
                                CercaniasAlert(
                                    id = id,
                                    headerEs = headerEs,
                                    descriptionEs = descEs,
                                    routeIds = finalRouteIds,
                                    tripIds = tripIds,
                                    stopIds = stopIds,
                                    isAccessibility = isAccessibility,
                                    isCirculationIncident = isCirculation,
                                    timestamp = timestamp
                                )
                            )
                        }
                    }
                    _cercaniasAlerts.value = list
                    _hasCercaniasAlertsError.value = false
                } else {
                    _hasCercaniasAlertsError.value = true
                }
            } catch (e: java.net.SocketTimeoutException) {
                _hasCercaniasAlertsError.value = true
                android.util.Log.w("CercaniasAlerts", "Timeout fetching Cercanías alerts from Renfe GTFS-RT: ${e.message}")
            } catch (e: java.io.IOException) {
                _hasCercaniasAlertsError.value = true
                android.util.Log.w("CercaniasAlerts", "Network error fetching Cercanías alerts: ${e.message}")
            } catch (e: Exception) {
                _hasCercaniasAlertsError.value = true
                android.util.Log.w("CercaniasAlerts", "Unexpected error fetching Cercanías alerts: ${e.message}")
            } finally {
                _isCercaniasAlertsLoading.value = false
            }
        }
    }

    private fun parseGtfsRtText(textObj: org.json.JSONObject?): String {
        if (textObj == null) return ""
        val translations = textObj.optJSONArray("translation") ?: textObj.optJSONArray("translations")
        if (translations != null && translations.length() > 0) {
            for (t in 0 until translations.length()) {
                val trans = translations.optJSONObject(t) ?: continue
                val lang = trans.optString("language", "").lowercase(java.util.Locale.ROOT)
                if (lang.startsWith("es")) {
                    return trans.optString("text", "")
                }
            }
            for (t in 0 until translations.length()) {
                val trans = translations.optJSONObject(t) ?: continue
                val lang = trans.optString("language", "").lowercase(java.util.Locale.ROOT)
                if (lang.startsWith("ca") || lang.startsWith("va")) {
                    return trans.optString("text", "")
                }
            }
            for (t in 0 until translations.length()) {
                val trans = translations.optJSONObject(t) ?: continue
                val lang = trans.optString("language", "").lowercase(java.util.Locale.ROOT)
                if (lang.startsWith("en")) {
                    return trans.optString("text", "")
                }
            }
            val firstTrans = translations.optJSONObject(0)
            return firstTrans?.optString("text", "") ?: ""
        }
        return ""
    }

    private fun containsWordBoundaryMatch(text: String, keyword: String): Boolean {
        if (keyword.isBlank() || text.isBlank()) return false
        var startIndex = 0
        while (startIndex < text.length) {
            val index = text.indexOf(keyword, startIndex)
            if (index == -1) return false
            val beforeOk = index == 0 || !text[index - 1].isLetterOrDigit()
            val endIndex = index + keyword.length
            val afterOk = endIndex >= text.length || !text[endIndex].isLetterOrDigit()
            if (beforeOk && afterOk) {
                return true
            }
            startIndex = index + 1
        }
        return false
    }

    // --- Standalone Live Train Map Support ---
    private val _liveCercaniasVehicles = MutableStateFlow<List<LiveVehicleInfo>>(emptyList())
    val liveCercaniasVehicles: StateFlow<List<LiveVehicleInfo>> = _liveCercaniasVehicles.asStateFlow()

    private val _isLiveMapLoading = MutableStateFlow(false)
    val isLiveMapLoading: StateFlow<Boolean> = _isLiveMapLoading.asStateFlow()

    private var liveTrainsJob: Job? = null

    fun startLiveTrainsPolling() {
        liveTrainsJob?.cancel()
        if (_liveCercaniasVehicles.value.isEmpty()) {
            _isLiveMapLoading.value = true
        }
        liveTrainsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Instant cache load: populate train markers with ZERO latency if currently empty
            try {
                val cached = renfeRepository.getUniqueLiveVehicles()
                if (cached.isNotEmpty()) {
                    _liveCercaniasVehicles.value = cached
                }
            } catch (e: Exception) {
                Log.d("CercaniasViewModel", "Error fetching cached live vehicles: ${e.message}")
            } finally {
                _isLiveMapLoading.value = false
            }

            // 2. Continuous background poll with adaptive cadence:
            // - If Renfe header timestamp is unchanged: wait 5s to re-query (body parse is skipped to save resources).
            // - If Renfe provides fresh data: emit updated vehicles and wait 20s before next standard cycle.
            while (isActive) {
                var hasNewData = false
                try {
                    val (vehicles, isFresh) = renfeRepository.getLiveVehiclesWithFreshness(forceFetch = true)
                    hasNewData = isFresh
                    if (isFresh || _liveCercaniasVehicles.value.isEmpty()) {
                        _liveCercaniasVehicles.value = vehicles
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.w("CercaniasViewModel", "Error polling live vehicles: ${e.message}")
                    }
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }

                val nextDelay = if (hasNewData) 20_000L else 5_000L
                delay(nextDelay)
            }
        }
    }

    fun stopLiveTrainsPolling(clearState: Boolean = false) {
        liveTrainsJob?.cancel()
        liveTrainsJob = null
        if (clearState) {
            _liveCercaniasVehicles.value = emptyList()
        }
        _isLiveMapLoading.value = false
    }
}
