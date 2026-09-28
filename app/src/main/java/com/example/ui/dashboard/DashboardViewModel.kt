package com.example.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItemEntity
import com.example.data.model.WeatherData
import com.example.data.model.WeatherService
import com.example.data.repository.DashboardRepository
import com.example.util.LocationUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.example.ui.map.RecentSearch
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DashboardRepository(application, database)
    private val calendarManager = DashboardCalendarManager(application, database, repository, viewModelScope)
    private val gson = Gson()

    private val initialLat = repository.getPreferenceSync("last_known_lat", "").toDoubleOrNull()
    private val initialLon = repository.getPreferenceSync("last_known_lon", "").toDoubleOrNull()

    private val _lastLocation = MutableStateFlow<Pair<Double, Double>?>(
        if (initialLat != null && initialLon != null) Pair(initialLat, initialLon) else null
    )
    val lastLocation = _lastLocation.asStateFlow()

    private val activeTripManager = DashboardActiveTripManager(
        application = application,
        database = database,
        getLastLocation = { _lastLocation.value },
        scope = viewModelScope
    )

    val activeTripState: StateFlow<com.example.data.repository.ActiveTripState?> = activeTripManager.activeTripState
    val unifiedTripSnapshot: StateFlow<com.example.data.model.trip.UnifiedActiveTripSnapshot?> = activeTripManager.unifiedTripSnapshot
    val realTimeTripStatus: StateFlow<com.example.util.RealTimeTripStatus> = activeTripManager.realTimeTripStatus
    val isRecalculatingTransfer: StateFlow<Boolean> = activeTripManager.isRecalculatingTransfer
    val recalculateError: StateFlow<String?> = activeTripManager.recalculateError
    val showTransferRiskDialog: StateFlow<Boolean> = activeTripManager.showTransferRiskDialog

    fun triggerTransferRiskDialog() = activeTripManager.triggerTransferRiskDialog()
    fun dismissTransferRiskDialog() = activeTripManager.dismissTransferRiskDialog()
    fun dismissRecalculateError() = activeTripManager.dismissRecalculateError()
    fun recalculateMissedTransfer() = activeTripManager.recalculateMissedTransfer()

    fun startActiveTrip(
        itinerary: com.example.data.model.routing.PlannedItinerary,
        originName: String,
        destinationName: String
    ) = activeTripManager.startActiveTrip(itinerary, originName, destinationName)

    fun cancelActiveTrip() = activeTripManager.cancelActiveTrip()
    fun completeActiveTrip() = activeTripManager.completeActiveTrip()
    fun advanceActiveTripLeg(newIndex: Int) = activeTripManager.advanceActiveTripLeg(newIndex)
    fun confirmBoarding(targetLegIndex: Int) = activeTripManager.confirmBoarding(targetLegIndex)
    fun refreshRealTimeTripStatus() = activeTripManager.refreshRealTimeTripStatus()

    private val _nearbyBusStops = MutableStateFlow<List<Pair<com.example.data.database.GeoportalStopEntity, Boolean>>>(emptyList())
    val nearbyBusStops: StateFlow<List<Pair<com.example.data.database.GeoportalStopEntity, Boolean>>> = _nearbyBusStops.asStateFlow()

    private val _nearbyMetrobusStops = MutableStateFlow<List<Pair<com.example.data.database.MetrobusStopEntity, Boolean>>>(emptyList())
    val nearbyMetrobusStops: StateFlow<List<Pair<com.example.data.database.MetrobusStopEntity, Boolean>>> = _nearbyMetrobusStops.asStateFlow()

    private var nearbyComputeJob: Job? = null

    fun computeNearbyStops(
        userCoords: Pair<Double, Double>?,
        favoriteBusStops: List<String>,
        favoriteMetrobusStops: List<String>,
        showEmtNearby: Boolean,
        showMetrobusNearby: Boolean
    ) {
        nearbyComputeJob?.cancel()
        nearbyComputeJob = viewModelScope.launch(Dispatchers.Default) {
            if (userCoords == null) {
                _nearbyBusStops.value = emptyList()
                _nearbyMetrobusStops.value = emptyList()
                return@launch
            }
            val (refLat, refLon) = userCoords
            val maxNearbyDistanceMeters = 1000.0

            if (showEmtNearby) {
                val allActive: List<com.example.data.database.GeoportalStopEntity> = com.example.data.repository.StaticTransitDataCache.getOrLoadEmtStops(getApplication<Application>() as android.content.Context)
                    .filter { it.suprimida == 0 && !it.lineas.isNullOrBlank() }

                if (allActive.isEmpty()) {
                    _nearbyBusStops.value = emptyList()
                } else {
                    val sortedStopsWithDist = allActive
                        .map { Pair(it, LocationUtils.calculateDistanceMeters(refLat, refLon, it.lat, it.lon)) }
                        .filter { it.second <= maxNearbyDistanceMeters }
                        .sortedBy { it.second }

                    if (sortedStopsWithDist.isEmpty()) {
                        _nearbyBusStops.value = emptyList()
                    } else {
                        val closestFav = sortedStopsWithDist.firstOrNull { favoriteBusStops.contains(it.first.id_parada) }
                        val closestStop = sortedStopsWithDist.firstOrNull()

                        val result = mutableListOf<Pair<com.example.data.database.GeoportalStopEntity, Boolean>>()
                        if (closestFav != null) {
                            if (closestFav.first.id_parada == closestStop?.first?.id_parada || closestFav.second <= 500.0) {
                                result.add(Pair(closestFav.first, true))
                            }
                        }

                        for (pair in sortedStopsWithDist) {
                            if (result.size >= 2) break
                            val stop = pair.first
                            val dist = pair.second
                            if (result.none { it.first.id_parada == stop.id_parada }) {
                                val isFav = (stop.id_parada == closestStop?.first?.id_parada && favoriteBusStops.contains(stop.id_parada)) ||
                                        (favoriteBusStops.contains(stop.id_parada) && dist <= 500.0)
                                result.add(Pair(stop, isFav))
                            }
                        }
                        _nearbyBusStops.value = result
                    }
                }
            } else {
                _nearbyBusStops.value = emptyList()
            }

            if (showMetrobusNearby) {
                val allActive: List<com.example.data.database.MetrobusStopEntity> = com.example.data.repository.StaticTransitDataCache.getOrLoadMetrobusStops(getApplication<Application>() as android.content.Context)
                    .filter { it.suprimida == 0 && !it.lineas.isNullOrBlank() }

                if (allActive.isEmpty()) {
                    _nearbyMetrobusStops.value = emptyList()
                } else {
                    val sortedStopsWithDist: List<Pair<com.example.data.database.MetrobusStopEntity, Double>> = allActive
                        .map { Pair(it, LocationUtils.calculateDistanceMeters(refLat, refLon, it.lat, it.lon)) }
                        .filter { it.second <= maxNearbyDistanceMeters }
                        .sortedBy { it.second }

                    if (sortedStopsWithDist.isEmpty()) {
                        _nearbyMetrobusStops.value = emptyList()
                    } else {
                        val closestFav = sortedStopsWithDist.firstOrNull { favoriteMetrobusStops.contains(it.first.id_parada) }
                        val closestStop = sortedStopsWithDist.firstOrNull()

                        val result = mutableListOf<Pair<com.example.data.database.MetrobusStopEntity, Boolean>>()
                        if (closestFav != null) {
                            if (closestFav.first.id_parada == closestStop?.first?.id_parada || closestFav.second <= 500.0) {
                                result.add(Pair(closestFav.first, true))
                            }
                        }

                        for (pair in sortedStopsWithDist) {
                            if (result.size >= 2) break
                            val stop = pair.first
                            val dist = pair.second
                            if (result.none { it.first.id_parada == stop.id_parada }) {
                                val isFav = (stop.id_parada == closestStop?.first?.id_parada && favoriteMetrobusStops.contains(stop.id_parada)) ||
                                        (favoriteMetrobusStops.contains(stop.id_parada) && dist <= 500.0)
                                result.add(Pair(stop, isFav))
                            }
                        }
                        _nearbyMetrobusStops.value = result
                    }
                }
            } else {
                _nearbyMetrobusStops.value = emptyList()
            }
        }
    }

    // UI state flows initialized immediately from synchronous persistent preferences
    private val _shouldShowOnboarding = MutableStateFlow(
        repository.getPreferenceSync("has_completed_onboarding", "false") == "false"
    )
    val shouldShowOnboarding = _shouldShowOnboarding.asStateFlow()

    private val _currentTime = MutableStateFlow("")
    val currentTime = _currentTime.asStateFlow()

    private val _useGpsOnOpen = MutableStateFlow(
        repository.getPreferenceSync("use_gps_on_open", "false").toBoolean()
    )
    val useGpsOnOpen = _useGpsOnOpen.asStateFlow()

    private val _weatherCity = MutableStateFlow(
        repository.getPreferenceSync("weather_city", "valencia")
    )
    val weatherCity = _weatherCity.asStateFlow()

    private val initialWeather: WeatherData? = run {
        val cachedJson = repository.getPreferenceSync("cached_weather_json", "")
        if (cachedJson.isNotBlank()) {
            try {
                gson.fromJson(cachedJson, WeatherData::class.java)
            } catch (_: Exception) {
                null
            }
        } else null
    }
    private val _weatherData = MutableStateFlow<WeatherData?>(initialWeather)
    val weatherData = _weatherData.asStateFlow()

    private val _isFahrenheit = MutableStateFlow(
        repository.getPreferenceSync("is_fahrenheit", "false").toBoolean()
    )
    val isFahrenheit = _isFahrenheit.asStateFlow()

    private val _isUiReady = MutableStateFlow(true)
    val isUiReady = _isUiReady.asStateFlow()

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

    private val initialLanguage: AppLanguage = try {
        AppLanguage.valueOf(repository.getPreferenceSync("app_language", "CA"))
    } catch (e: Exception) {
        AppLanguage.CA
    }
    private val _appLanguage = MutableStateFlow(initialLanguage)
    val appLanguage = _appLanguage.asStateFlow()


    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val isOnline: StateFlow<Boolean> = com.example.util.observeNetworkConnectivity(getApplication())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.util.isNetworkAvailable(getApplication())
        )

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground = _isAppInForeground.asStateFlow()

    // Home and Work locations synchronized with Map & RoutePlanner
    val homeLocation: StateFlow<RecentSearch?> = repository.getPreferenceFlow("home_location", "")
        .map { json ->
            if (json.isBlank()) null
            else try {
                gson.fromJson(json, RecentSearch::class.java)
            } catch (e: Exception) {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workLocation: StateFlow<RecentSearch?> = repository.getPreferenceFlow("work_location", "")
        .map { json ->
            if (json.isBlank()) null
            else try {
                gson.fromJson(json, RecentSearch::class.java)
            } catch (e: Exception) {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveHomeLocation(location: RecentSearch?) {
        viewModelScope.launch {
            val json = if (location == null) "" else gson.toJson(location)
            repository.savePreference("home_location", json)
        }
    }

    fun saveWorkLocation(location: RecentSearch?) {
        viewModelScope.launch {
            val json = if (location == null) "" else gson.toJson(location)
            repository.savePreference("work_location", json)
        }
    }

    val recentSearches: StateFlow<List<RecentSearch>> = repository.getPreferenceFlow("recent_searches", "[]")
        .map { json ->
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customFavorites: StateFlow<List<RecentSearch>> = repository.getPreferenceFlow("custom_favorites", "[]")
        .map { json ->
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTransitModes: StateFlow<Set<String>> = repository.getPreferenceFlow(
        "favorite_transit_modes",
        repository.getPreferenceSync("favorite_transit_modes", "METRO,EMT,CERCANIAS,VALENBISI,METROBUS")
    )
        .map { modesStr ->
            if (modesStr.isBlank()) emptySet()
            else modesStr.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = repository.getPreferenceSync("favorite_transit_modes", "METRO,EMT,CERCANIAS,VALENBISI,METROBUS")
                .split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        )

    fun togglePreferredTransitMode(mode: String) {
        viewModelScope.launch {
            val current = favoriteTransitModes.value
            val updated = if (current.contains(mode.uppercase())) current - mode.uppercase() else current + mode.uppercase()
            val savedStr = updated.joinToString(",")
            repository.savePreference("favorite_transit_modes", savedStr)
            
            // Sync default map filter preference accordingly
            val mapFilterJson = org.json.JSONObject().apply {
                put("isFavorites", false)
                put("showBus", updated.contains("EMT"))
                put("showMetrobus", updated.contains("METROBUS"))
                put("showMetro", updated.contains("METRO"))
                put("showCercanias", updated.contains("CERCANIAS"))
                put("showValenbisi", updated.contains("VALENBISI"))
            }
            repository.savePreference("map_filter_preference", mapFilterJson.toString())
        }
    }

    private val favoriteBusStopsSet = repository.getPreferenceFlow("favorite_bus_stops", "")
        .map { favs -> if (favs.isNotEmpty()) favs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.getPreferenceSync("favorite_bus_stops", "").let { if (it.isNotEmpty()) it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet() else emptySet() }
        )

    private val favoriteMetroStationsSet = repository.getPreferenceFlow("favorite_stations", "")
        .map { favs -> if (favs.isNotEmpty()) favs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.getPreferenceSync("favorite_stations", "").let { if (it.isNotEmpty()) it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet() else emptySet() }
        )

    private val favoriteCercaniasStationsSet = repository.getPreferenceFlow("favorite_cercanias_stations", "")
        .map { favs -> if (favs.isNotEmpty()) favs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.getPreferenceSync("favorite_cercanias_stations", "").let { if (it.isNotEmpty()) it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet() else emptySet() }
        )

    val unifiedTransitFavorites: StateFlow<List<RecentSearch>> = kotlinx.coroutines.flow.combine(
        favoriteBusStopsSet,
        favoriteMetroStationsSet,
        favoriteCercaniasStationsSet
    ) { favBuses, favMetros, favCercanias ->
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<RecentSearch>()
            try {
                val busStops = database.geoportalStopDao().getAllActiveStops()
                val metroStations = database.stationDao().getAllStations()
                val cercaniasStations = database.cercaniasStationDao().getAllStations()

                favBuses.forEach { id ->
                    busStops.find { it.id_parada == id }?.let { stop ->
                        list.add(
                            RecentSearch(
                                type = "bus",
                                id = stop.id_parada,
                                title = stop.denominacion,
                                subtitle = "EMT Parada ${stop.id_parada}",
                                latitude = stop.lat,
                                longitude = stop.lon,
                                extraData = stop.lineas
                            )
                        )
                    }
                }
                favMetros.forEach { id ->
                    metroStations.find { it.id.toString() == id }?.let { st ->
                        list.add(
                            RecentSearch(
                                type = "metro",
                                id = st.id.toString(),
                                title = st.name,
                                subtitle = "Metrovalencia",
                                latitude = st.lat,
                                longitude = st.lon,
                                extraData = st.lines
                            )
                        )
                    }
                }
                favCercanias.forEach { id ->
                    cercaniasStations.find { it.stop_id == id }?.let { st ->
                        list.add(
                            RecentSearch(
                                type = "cercanias",
                                id = st.stop_id,
                                title = st.nombre,
                                subtitle = "Renfe Rodalies",
                                latitude = st.lat,
                                longitude = st.lon
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error building transit favorites: ${e.message}")
            }
            list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.savePreference("recent_searches", "[]")
        }
    }

    fun removeRecentSearch(id: String) {
        viewModelScope.launch {
            val currentList = recentSearches.value.toMutableList()
            currentList.removeAll { it.id == id }
            repository.savePreference("recent_searches", gson.toJson(currentList))
        }
    }

    private val searchEngine: com.example.data.repository.UnifiedSearchEngine by lazy {
        com.example.data.repository.UnifiedSearchEngine(
            context = getApplication(),
            database = database,
            geocodingRepository = com.example.data.repository.GeocodingRepository(getApplication(), database)
        )
    }

    fun searchLocations(query: String): Flow<List<com.example.ui.map.MapSearchResult>> {
        return searchEngine.performSearch(
            query = query,
            userLat = _lastLocation.value?.first,
            userLon = _lastLocation.value?.second
        )
    }

    // Database items
    val calendarItems: StateFlow<List<CalendarItemEntity>> = repository.allCalendarItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var clockJob: Job? = null

    init {
        // Automatically sync local Android Calendar events if permission is granted
        syncGoogleCalendarEvents()

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Load saved preferences
            val savedLang = repository.getPreference("app_language", "CA")
            _appLanguage.value = try { AppLanguage.valueOf(savedLang) } catch (e: Exception) { AppLanguage.CA }

            val savedFahr = repository.getPreference("is_fahrenheit", "false")
            _isFahrenheit.value = savedFahr.toBoolean()

            val savedCity = repository.getPreference("weather_city", "valencia")
            _weatherCity.value = savedCity

            val savedGpsOnOpen = repository.getPreference("use_gps_on_open", "false")
            _useGpsOnOpen.value = savedGpsOnOpen.toBoolean()

            val onboardingCompleted = repository.getPreference("has_completed_onboarding", "false")
            _shouldShowOnboarding.value = onboardingCompleted == "false"

            // Load saved location cache so we have data immediately
            val savedLat = repository.getPreference("last_known_lat", "").toDoubleOrNull()
            val savedLon = repository.getPreference("last_known_lon", "").toDoubleOrNull()
            if (savedLat != null && savedLon != null) {
                _lastLocation.value = Pair(savedLat, savedLon)
            }

            // Load cached weather so there's no layout flashing
            val cachedJson = repository.getPreference("cached_weather_json", "")
            if (cachedJson.isNotBlank()) {
                try {
                    _weatherData.value = gson.fromJson(cachedJson, WeatherData::class.java)
                } catch (_: Exception) {}
            }

            repository.ensureDefaultCalendarItems()

            // Mark UI as ready immediately once local preferences, location/weather cache, and database state are loaded!
            // Never block the Splash Screen on external network calls or GPS fixes, but guarantee local state is loaded.
            _isUiReady.value = true

            // Resolve location and weather in the background with timeout guards
            try {
                val location = if (_useGpsOnOpen.value) {
                    withTimeoutOrNull(2000L) {
                        LocationUtils.getBestLastLocation(getApplication())
                    }
                } else null

                if (location != null) {
                    _lastLocation.value = Pair(location.latitude, location.longitude)
                    repository.savePreference("last_known_lat", location.latitude.toString())
                    repository.savePreference("last_known_lon", location.longitude.toString())
                }

                val cachedTimestamp = repository.getPreference("cached_weather_timestamp", "0").toLongOrNull() ?: 0L
                val isCacheExpired = (System.currentTimeMillis() - cachedTimestamp) > 30 * 60 * 1000L // 30 minutes

                if (_weatherData.value == null || isCacheExpired) {
                    val weather = withTimeoutOrNull(3500L) {
                        val currentLoc = _lastLocation.value
                        if (currentLoc != null) {
                            WeatherService.getWeatherDataByCoords(currentLoc.first, currentLoc.second, _weatherCity.value)
                        } else {
                            WeatherService.getWeatherData(_weatherCity.value)
                        }
                    }
                    if (weather != null) {
                        _weatherData.value = weather
                        try {
                            repository.savePreference("cached_weather_json", gson.toJson(weather))
                            repository.savePreference("cached_weather_timestamp", System.currentTimeMillis().toString())
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Background weather or location fetch failed or timed out: ${e.message}")
            }

            // Periodic 24h/weekly data synchronization against GitHub (only if onboarding is already completed)
            if (!_shouldShowOnboarding.value) {
                try {
                    com.example.data.repository.emt.EmtDataSyncManager.syncIfNeeded(getApplication())
                    com.example.data.repository.metrobus.MetrobusDataSyncManager.syncIfNeeded(getApplication())
                    com.example.data.repository.renfe.CercaniasCsvSyncManager.syncIfNeeded(getApplication())
                } catch (e: Exception) {
                    Log.w("DashboardViewModel", "Background transit sync failed: ${e.message}")
                }
            }
        }

        startClock()
    }

    fun onAppForegrounded() {
        _isAppInForeground.value = true
        startClock()
        activeTripManager.onAppForegrounded()
    }

    fun onAppBackgrounded() {
        _isAppInForeground.value = false
        clockJob?.cancel()
        clockJob = null
        activeTripManager.onAppBackgrounded()
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            while (true) {
                _currentTime.value = timeFormat.format(Date())
                delay(1000)
            }
        }
    }

    fun isTimeInNextWindow(timeStr: String, windowMinutes: Int = 120): Boolean {
        return DashboardTimeUtils.isTimeInNextWindow(timeStr, windowMinutes)
    }

    fun setWeatherCity(city: String) {
        _weatherCity.value = city
        viewModelScope.launch {
            _weatherData.value = null
            repository.savePreference("weather_city", city)
            _weatherData.value = WeatherService.getWeatherData(city)
        }
    }

    fun toggleFahrenheit() {
        val next = !_isFahrenheit.value
        _isFahrenheit.value = next
        viewModelScope.launch {
            repository.savePreference("is_fahrenheit", next.toString())
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val next = !isDarkMode.value
            repository.savePreference("is_dark_mode", next.toString())
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        viewModelScope.launch {
            repository.savePreference("app_language", language.name)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePreference("has_completed_onboarding", "true")
            _shouldShowOnboarding.value = false
            try {
                com.example.data.repository.emt.EmtDataSyncManager.syncIfNeeded(getApplication())
                com.example.data.repository.metrobus.MetrobusDataSyncManager.syncIfNeeded(getApplication())
                com.example.data.repository.renfe.CercaniasCsvSyncManager.syncIfNeeded(getApplication())
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Deferred transit sync failed: ${e.message}")
            }
            val loc = _lastLocation.value
            if (loc != null) {
                val favBus = favoriteBusStopsSet.value.toList()
                val favMb = try { repository.getPreferenceSync("favorite_metrobus_stops", "").split(",").filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
                computeNearbyStops(
                    userCoords = loc,
                    favoriteBusStops = favBus,
                    favoriteMetrobusStops = favMb,
                    showEmtNearby = true,
                    showMetrobusNearby = true
                )
            }
        }
    }

    fun restartOnboarding() {
        _shouldShowOnboarding.value = true
    }

    fun refreshAll() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _isRefreshing.value = true
            val currentLocation = if (_useGpsOnOpen.value) _lastLocation.value else null
            val newData = if (currentLocation != null) {
                WeatherService.getWeatherDataByCoords(currentLocation.first, currentLocation.second, _weatherCity.value)
            } else {
                WeatherService.getWeatherData(_weatherCity.value)
            }
            if (newData != null) {
                _weatherData.value = newData
            }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 400L) {
                delay(400L - elapsed)
            }
            _isRefreshing.value = false
        }
    }

    // Calendar Operations
    fun addEvent(title: String, description: String, startHoursOffset: Int, durationHours: Int, colorHex: String) {
        calendarManager.addEvent(title, description, startHoursOffset, durationHours, colorHex)
    }

    fun addTask(title: String, description: String, dueHoursOffset: Int, colorHex: String) {
        calendarManager.addTask(title, description, dueHoursOffset, colorHex)
    }

    fun toggleTaskCompletion(item: CalendarItemEntity) {
        calendarManager.toggleTaskCompletion(item)
    }

    fun deleteItem(item: CalendarItemEntity) {
        calendarManager.deleteItem(item)
    }

    fun syncGoogleCalendarEvents(force: Boolean = false) {
        calendarManager.syncGoogleCalendarEvents(force)
    }

    private var lastLocationUpdateTime = 0L

    fun shouldRequestLocationUpdate(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastLocationUpdateTime >= 10 * 60 * 1000 // 10 minutes in ms
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        _lastLocation.value = Pair(latitude, longitude)
        if (activeTripState.value != null) {
            refreshRealTimeTripStatus()
        }
    }

    fun updateWeatherByLocation(latitude: Double, longitude: Double, context: android.content.Context) {
        lastLocationUpdateTime = System.currentTimeMillis()
        _lastLocation.value = Pair(latitude, longitude)
        viewModelScope.launch {
            repository.savePreference("last_latitude", latitude.toString())
            repository.savePreference("last_longitude", longitude.toString())
            try {
                val geocoder = android.location.Geocoder(context, Locale.getDefault())
                var cityName: String? = null
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val deferredCity = kotlinx.coroutines.CompletableDeferred<String?>()
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            val resolved = addresses.firstOrNull()?.locality 
                                ?: addresses.firstOrNull()?.subAdminArea 
                                ?: addresses.firstOrNull()?.adminArea
                            deferredCity.complete(resolved)
                        }
                        cityName = deferredCity.await()
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                        cityName = addresses?.firstOrNull()?.locality 
                            ?: addresses?.firstOrNull()?.subAdminArea 
                            ?: addresses?.firstOrNull()?.adminArea
                    }
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Geocoder failed", e)
                }

                val finalCity = cityName ?: "Ubicación GPS"
                _weatherCity.value = finalCity
                val fetchedData = WeatherService.getWeatherDataByCoords(latitude, longitude, finalCity)
                if (fetchedData != null) {
                    _weatherData.value = fetchedData
                }
                repository.savePreference("weather_city", finalCity)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error updating weather by location", e)
            }
        }
    }

}
