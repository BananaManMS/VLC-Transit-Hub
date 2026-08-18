package com.example.ui.dashboard

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItemEntity
import com.example.data.database.TransitCardEntity
import com.example.data.model.WeatherData
import com.example.data.model.WeatherService
import com.example.data.repository.DashboardRepository
import com.example.util.LocationUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DashboardRepository(application, database)
    private val activeTripRepository = com.example.data.repository.ActiveTripRepository(database.activeTripDao())
    private val tripReconciler = com.example.util.TripRealTimeReconciler()

    val activeTripState: StateFlow<com.example.data.repository.ActiveTripState?> = activeTripRepository.getActiveTripFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val _realTimeTripStatus = MutableStateFlow(com.example.util.RealTimeTripStatus())
    val realTimeTripStatus: StateFlow<com.example.util.RealTimeTripStatus> = _realTimeTripStatus.asStateFlow()

    private val _isRecalculatingTransfer = MutableStateFlow(false)
    val isRecalculatingTransfer: StateFlow<Boolean> = _isRecalculatingTransfer.asStateFlow()

    private val _recalculateError = MutableStateFlow<String?>(null)
    val recalculateError: StateFlow<String?> = _recalculateError.asStateFlow()

    private val _showTransferRiskDialog = MutableStateFlow(false)
    val showTransferRiskDialog: StateFlow<Boolean> = _showTransferRiskDialog.asStateFlow()

    fun triggerTransferRiskDialog() {
        _showTransferRiskDialog.value = true
    }

    fun dismissTransferRiskDialog() {
        _showTransferRiskDialog.value = false
    }

    fun dismissRecalculateError() {
        _recalculateError.value = null
    }

    fun recalculateMissedTransfer() {
        viewModelScope.launch {
            val trip = activeTripState.value ?: return@launch
            val currentIdx = trip.currentLegIndex
            val legs = trip.itinerary.legs
            if (legs.isEmpty() || currentIdx >= legs.size) return@launch

            _isRecalculatingTransfer.value = true
            _recalculateError.value = null

            try {
                val currentLeg = legs[currentIdx]
                val transferOriginLat = currentLeg.toLat
                val transferOriginLon = currentLeg.toLon
                val transferOriginName = currentLeg.toName.ifBlank { "Estación de transbordo" }

                val destinationLeg = legs.last()
                val destLat = destinationLeg.toLat
                val destLon = destinationLeg.toLon
                val destName = trip.destinationName.ifBlank { destinationLeg.toName }

                val hybridRoutingRepository = com.example.data.repository.routing.HybridRoutingRepository(
                    metroAlertsRepository = com.example.data.repository.MetroAlertsRepository(),
                    context = getApplication()
                )

                val nowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val nowDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val result = hybridRoutingRepository.planRoute(
                    fromLat = transferOriginLat,
                    fromLon = transferOriginLon,
                    toLat = destLat,
                    toLon = destLon,
                    time = nowTime,
                    date = nowDate,
                    arriveBy = false,
                    maxTransfers = 2,
                    modes = "WALK,SUBWAY,TRAM,BUS,REGIONAL_RAIL",
                    originName = transferOriginName,
                    destinationName = destName
                )

                result.fold(
                    onSuccess = { candidateItineraries ->
                        val currentWalkSecs = legs.drop(currentIdx + 1)
                            .filter { it.mode == com.example.data.model.routing.TransitMode.WALK }
                            .sumOf { it.durationSeconds }

                        val validCandidates = candidateItineraries.filter { candidate ->
                            val firstTransit = candidate.legs.firstOrNull { it.mode != com.example.data.model.routing.TransitMode.WALK }
                            val candidateWalkSecs = candidate.legs.filter { it.mode == com.example.data.model.routing.TransitMode.WALK }.sumOf { it.durationSeconds }
                            val candidateWalkMeters = candidate.legs.filter { it.mode == com.example.data.model.routing.TransitMode.WALK }.sumOf { it.distanceMeters.toInt() }

                            val waitTooLong = if (firstTransit?.startTime != null) {
                                val depMs = com.example.util.TripTimeParser.parseTimeToMillis(firstTransit.startTime)
                                if (depMs != null) {
                                    val waitMins = (depMs - System.currentTimeMillis()) / 60000L
                                    waitMins > 45
                                } else false
                            } else false

                            val excessiveWalk = candidateWalkMeters > 900 || (candidateWalkSecs - currentWalkSecs) > 600

                            !waitTooLong && !excessiveWalk
                        }

                        if (validCandidates.isEmpty()) {
                            val nextServiceWaitMins = candidateItineraries.firstOrNull()?.legs?.firstOrNull { it.mode != com.example.data.model.routing.TransitMode.WALK }?.let { leg ->
                                val depMs = com.example.util.TripTimeParser.parseTimeToMillis(leg.startTime)
                                if (depMs != null) ((depMs - System.currentTimeMillis()) / 60000L).coerceAtLeast(1) else null
                            }

                            val errorMsg = if (nextServiceWaitMins != null) {
                                "No es posible recalcular un enlace cercano (Siguiente servicio en $nextServiceWaitMins min)."
                            } else {
                                "No ha sido posible recalcular el trayecto alternativo."
                            }
                            _recalculateError.value = errorMsg
                        } else {
                            val bestNew = validCandidates.first()
                            val keptLegs = legs.take(currentIdx + 1)
                            val splicedLegs = keptLegs + bestNew.legs

                            val newTotalSecs = keptLegs.sumOf { it.durationSeconds } + bestNew.totalDurationSeconds
                            val splicedItinerary = trip.itinerary.copy(
                                legs = splicedLegs,
                                totalDurationSeconds = newTotalSecs,
                                formattedDuration = "${(newTotalSecs / 60).coerceAtLeast(1)} min",
                                endTime = bestNew.endTime,
                                formattedArrivalTime = bestNew.formattedArrivalTime
                            )

                            activeTripRepository.updateItinerary(splicedItinerary)
                            refreshRealTimeTripStatus()
                            _showTransferRiskDialog.value = false
                        }
                    },
                    onFailure = {
                        _recalculateError.value = "No ha sido posible recalcular el trayecto en este momento."
                    }
                )
            } catch (e: Exception) {
                _recalculateError.value = "Error al recalcular enlace: ${e.message}"
            } finally {
                _isRecalculatingTransfer.value = false
            }
        }
    }

    private var tripReconcileJob: Job? = null

    fun startActiveTrip(
        itinerary: com.example.data.model.routing.PlannedItinerary,
        originName: String,
        destinationName: String
    ) {
        viewModelScope.launch {
            activeTripRepository.startTrip(itinerary, originName, destinationName)
            com.example.service.ActiveTripTrackingService.start(getApplication())
            refreshRealTimeTripStatus()
        }
    }

    fun cancelActiveTrip() {
        viewModelScope.launch {
            activeTripRepository.cancelActiveTrip()
            com.example.util.TripStepProgressionEngine.reset()
            tripReconciler.reset()
            com.example.service.ActiveTripTrackingService.stop(getApplication())
            _realTimeTripStatus.value = com.example.util.RealTimeTripStatus()
        }
    }

    fun completeActiveTrip() {
        viewModelScope.launch {
            activeTripRepository.completeActiveTrip()
            com.example.util.TripStepProgressionEngine.reset()
            tripReconciler.reset()
            com.example.service.ActiveTripTrackingService.stop(getApplication())
            _realTimeTripStatus.value = com.example.util.RealTimeTripStatus()
        }
    }

    fun advanceActiveTripLeg(newIndex: Int) {
        viewModelScope.launch {
            val currentTrip = activeTripState.value
            val legs = currentTrip?.itinerary?.legs
            val targetLeg = legs?.getOrNull(newIndex)

            com.example.util.TripStepProgressionEngine.markLegBoarded(newIndex)
            if (targetLeg != null && targetLeg.mode in listOf(
                    com.example.data.model.routing.TransitMode.SUBWAY,
                    com.example.data.model.routing.TransitMode.BUS,
                    com.example.data.model.routing.TransitMode.TRAM,
                    com.example.data.model.routing.TransitMode.RAIL
                )) {
                com.example.util.TripStepProgressionEngine.notifyBoardingConfirmed(newIndex, targetLeg)
            }
            com.example.util.ActiveTripProgressTracker.markAsBoarded(newIndex)

            if (currentTrip != null && newIndex != currentTrip.currentLegIndex) {
                activeTripRepository.advanceLegIndex(newIndex)
            }
            refreshRealTimeTripStatus()
        }
    }

    fun confirmBoarding(targetLegIndex: Int) {
        advanceActiveTripLeg(targetLegIndex)
    }

    fun refreshRealTimeTripStatus() {
        viewModelScope.launch {
            val trip = activeTripState.value ?: return@launch
            val loc = _lastLocation.value
            val status = tripReconciler.reconcile(
                activeTrip = trip,
                userLat = loc?.first,
                userLon = loc?.second
            )
            _realTimeTripStatus.value = status
        }
    }

    // UI state flows
    private val _shouldShowOnboarding = MutableStateFlow(false)
    val shouldShowOnboarding = _shouldShowOnboarding.asStateFlow()

    private val _currentTime = MutableStateFlow("")
    val currentTime = _currentTime.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate = _currentDate.asStateFlow()

    private val _lastLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val lastLocation = _lastLocation.asStateFlow()

    private val _useGpsOnOpen = MutableStateFlow(false)
    val useGpsOnOpen = _useGpsOnOpen.asStateFlow()

    private val _weatherCity = MutableStateFlow("valencia")
    val weatherCity = _weatherCity.asStateFlow()

    private val _weatherData = MutableStateFlow<WeatherData?>(null)
    val weatherData = _weatherData.asStateFlow()

    private val _isFahrenheit = MutableStateFlow(false)
    val isFahrenheit = _isFahrenheit.asStateFlow()

    private val _isUiReady = MutableStateFlow(false)
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

    private val _appLanguage = MutableStateFlow(
        try {
            AppLanguage.valueOf(repository.getPreferenceSync("app_language", "CA"))
        } catch (e: Exception) {
            AppLanguage.CA
        }
    )
    val appLanguage = _appLanguage.asStateFlow()


    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground = _isAppInForeground.asStateFlow()

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

        // 20-second Live Polling loop for active multimodal trip
        viewModelScope.launch {
            activeTripState
                .map { it?.startTimestamp }
                .distinctUntilChanged()
                .collectLatest { startTimestamp ->
                    tripReconcileJob?.cancel()
                    if (startTimestamp != null) {
                        tripReconcileJob = launch {
                            while (isActive) {
                                val trip = activeTripState.value ?: break
                                val loc = _lastLocation.value
                                val status = tripReconciler.reconcile(
                                    activeTrip = trip,
                                    userLat = loc?.first,
                                    userLon = loc?.second
                                )
                                _realTimeTripStatus.value = status

                                // Dynamically sync real-time status & delays to active trip legs and overall itinerary
                                val syncedItinerary = com.example.util.TripRealTimeReconciler.syncRealTimeItinerary(
                                    itinerary = trip.itinerary,
                                    status = status,
                                    currentLegIndex = trip.currentLegIndex
                                )
                                if (syncedItinerary != trip.itinerary) {
                                    activeTripRepository.updateItinerary(syncedItinerary)
                                }
                                delay(20000L) // 20-second live polling loop
                            }
                        }
                    } else {
                        _realTimeTripStatus.value = com.example.util.RealTimeTripStatus()
                    }
                }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Clean expired trips if needed
            activeTripRepository.checkAndCleanExpiredTrip()

            // Restore tracking service if active trip is in progress
            val currentTrip = activeTripRepository.getActiveTrip()
            if (currentTrip != null && currentTrip.status == com.example.data.database.ActiveTripEntity.STATUS_IN_PROGRESS) {
                com.example.service.ActiveTripTrackingService.start(getApplication())
            }

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

            repository.ensureDefaultCalendarItems()

            // 3. Resolve location with fallback
            val location = if (_useGpsOnOpen.value) LocationUtils.getBestLastLocation(getApplication()) else null

            if (location != null) {
                lastLatitude = location.latitude
                lastLongitude = location.longitude
                _lastLocation.value = Pair(location.latitude, location.longitude)
            }

            // 4. Load weather initial data
            if (location != null) {
                _weatherData.value = WeatherService.getWeatherDataByCoords(location.latitude, location.longitude, _weatherCity.value)
            } else {
                _weatherData.value = WeatherService.getWeatherData(_weatherCity.value)
            }

            // 5. Mark UI as ready
            _isUiReady.value = true
        }

        startClock()
    }

    fun onAppForegrounded() {
        _isAppInForeground.value = true
    }

    fun onAppBackgrounded() {
        _isAppInForeground.value = false
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
            while (true) {
                val now = Date()
                _currentTime.value = timeFormat.format(now)
                _currentDate.value = dateFormat.format(now)
                delay(1000)
            }
        }
    }

    fun isTimeInNextWindow(timeStr: String, windowMinutes: Int = 120): Boolean {
        if (timeStr.isBlank()) {
            Log.w("DashboardViewModel", "isTimeInNextWindow received blank or empty time string.")
            return false
        }

        val trimmed = timeStr.trim()
        val supportedPatterns = listOf(
            "HH:mm",
            "HH:mm:ss",
            "hh:mm a",
            "hh:mm:ss a",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )

        var targetMillis: Long? = null
        var lastException: Exception? = null

        val epochMillis = trimmed.toLongOrNull()
        if (epochMillis != null) {
            targetMillis = epochMillis
        } else {
            for (pattern in supportedPatterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                    val parsedDate = sdf.parse(trimmed)
                    if (parsedDate != null) {
                        val calNow = Calendar.getInstance()
                        val targetCal = Calendar.getInstance().apply { time = parsedDate }

                        if (!pattern.contains("yyyy") && !pattern.contains("MM")) {
                            calNow.set(Calendar.HOUR_OF_DAY, targetCal.get(Calendar.HOUR_OF_DAY))
                            calNow.set(Calendar.MINUTE, targetCal.get(Calendar.MINUTE))
                            calNow.set(Calendar.SECOND, targetCal.get(Calendar.SECOND))
                            calNow.set(Calendar.MILLISECOND, 0)
                            targetMillis = calNow.timeInMillis
                        } else {
                            targetMillis = parsedDate.time
                        }
                        break
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
        }

        if (targetMillis == null) {
            Log.w(
                "DashboardViewModel",
                "Failed to parse time string '$timeStr' in isTimeInNextWindow. Reason: ${lastException?.message ?: "Unrecognized or unsupported time format"}"
            )
            return false
        }

        val now = System.currentTimeMillis()
        val windowEnd = now + (windowMinutes * 60 * 1000L)
        return targetMillis in now..windowEnd
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
        viewModelScope.launch {
            repository.savePreference("has_completed_onboarding", "true")
            _shouldShowOnboarding.value = false
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _weatherData.value = null
            val lat = if (_useGpsOnOpen.value) lastLatitude else null
            val lng = if (_useGpsOnOpen.value) lastLongitude else null
            if (lat != null && lng != null) {
                _weatherData.value = WeatherService.getWeatherDataByCoords(lat, lng, _weatherCity.value)
            } else {
                _weatherData.value = WeatherService.getWeatherData(_weatherCity.value)
            }
            _isRefreshing.value = false
        }
    }

    // Calendar Operations
    fun addEvent(title: String, description: String, startHoursOffset: Int, durationHours: Int, colorHex: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val hourInMillis = 3600_000L
            val item = CalendarItemEntity(
                title = title,
                description = description,
                startMillis = now + (startHoursOffset * hourInMillis),
                endMillis = now + ((startHoursOffset + durationHours) * hourInMillis),
                itemType = "EVENT",
                colorHex = colorHex
            )
            repository.insertCalendarItem(item)
        }
    }

    fun addTask(title: String, description: String, dueHoursOffset: Int, colorHex: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val hourInMillis = 3600_000L
            val item = CalendarItemEntity(
                title = title,
                description = description,
                dueMillis = now + (dueHoursOffset * hourInMillis),
                itemType = "TASK",
                isCompleted = false,
                colorHex = colorHex
            )
            repository.insertCalendarItem(item)
        }
    }

    fun toggleTaskCompletion(item: CalendarItemEntity) {
        viewModelScope.launch {
            val updated = item.copy(isCompleted = !item.isCompleted)
            repository.updateCalendarItem(updated)
        }
    }

    fun deleteItem(item: CalendarItemEntity) {
        viewModelScope.launch {
            repository.deleteCalendarItem(item)
            if (item.calendarEventId != null && item.calendarEventId != 0L) {
                try {
                    val now = System.currentTimeMillis()
                    val expiry = item.endMillis ?: item.startMillis ?: (now + 7 * 24 * 3600 * 1000L)
                    val rawDeleted = repository.getPreference("deleted_google_event_ids", "")
                    val list = if (rawDeleted.isBlank()) mutableListOf() else rawDeleted.split(";").toMutableList()
                    list.add("${item.calendarEventId}:$expiry")
                    repository.savePreference("deleted_google_event_ids", list.joinToString(";"))
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Error saving deleted event preference", e)
                }
            }
        }
    }

    fun syncGoogleCalendarEvents(force: Boolean = false) {
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()

                // 1. Automatically purge past events from Room database
                repository.deletePastEvents(now)

                // 2. Throttling check (minimum 15 minutes unless force == true)
                val lastSyncStr = repository.getPreference("last_gcal_sync_time", "0")
                val lastSyncTime = lastSyncStr.toLongOrNull() ?: 0L
                if (!force && (now - lastSyncTime < 15 * 60 * 1000L)) {
                    return@launch
                }
                repository.savePreference("last_gcal_sync_time", now.toString())

                // 3. Process and prune deleted Google Calendar Event IDs
                val rawDeleted = repository.getPreference("deleted_google_event_ids", "")
                val activeDeletedSet = mutableSetOf<Long>()
                val validDeletedEntries = mutableListOf<String>()

                if (rawDeleted.isNotBlank()) {
                    rawDeleted.split(";").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            val eventId = parts[0].toLongOrNull()
                            val expiry = parts[1].toLongOrNull()
                            if (eventId != null && expiry != null) {
                                if (expiry >= now) {
                                    activeDeletedSet.add(eventId)
                                    validDeletedEntries.add(entry)
                                }
                            }
                        }
                    }
                    repository.savePreference("deleted_google_event_ids", validDeletedEntries.joinToString(";"))
                }

                val contentResolver = context.contentResolver
                val uri = CalendarContract.Events.CONTENT_URI
                val sevenDaysLater = now + 7 * 24 * 3600 * 1000L
                
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Events.ALL_DAY
                )
                
                val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.DELETED} = 0)"
                val selectionArgs = arrayOf(now.toString(), sevenDaysLater.toString())
                
                val cursor = contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC"
                )
                
                cursor?.use { c ->
                    val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                    val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                    val calNameIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                    val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
                    
                    val newEvents = mutableListOf<CalendarItemEntity>()
                    while (c.moveToNext()) {
                        val eventIdLong = if (idIdx >= 0) c.getLong(idIdx) else 0L

                        if (eventIdLong != 0L && activeDeletedSet.contains(eventIdLong)) {
                            continue
                        }

                        val title = if (titleIdx >= 0) c.getString(titleIdx) ?: "Untitled Event" else "Untitled Event"
                        val desc = if (descIdx >= 0) c.getString(descIdx) ?: "" else ""
                        val start = if (startIdx >= 0) c.getLong(startIdx) else now
                        val end = if (endIdx >= 0) c.getLong(endIdx) else start
                        val calendarName = if (calNameIdx >= 0) c.getString(calNameIdx) ?: "" else ""
                        val allDayVal = if (allDayIdx >= 0) c.getInt(allDayIdx) else 0

                        val isAllDayEvent = (allDayVal == 1) || (start != 0L && end != 0L && (end == start || (end - start) % 86400000L == 0L))
                        
                        val calNameLower = calendarName.lowercase()
                        val titleLower = title.lowercase()
                        
                        val isHolidayCalendar = calNameLower.contains("festiv") ||
                                                calNameLower.contains("holiday") ||
                                                calNameLower.contains("festu") ||
                                                calNameLower.contains("festes")
                                                
                        val isPublicHoliday = titleLower.contains("año nuevo") ||
                                              titleLower.contains("any nou") ||
                                              titleLower.contains("reyes") ||
                                              titleLower.contains("reigs") ||
                                              titleLower.contains("viernes santo") ||
                                              titleLower.contains("divendres sant") ||
                                              titleLower.contains("lunes de pascua") ||
                                              titleLower.contains("dilluns de pasqua") ||
                                              titleLower.contains("fiesta del trabajo") ||
                                              titleLower.contains("dia del treball") ||
                                              titleLower.contains("asunción") ||
                                              titleLower.contains("assumpció") ||
                                              titleLower.contains("fiesta nacional") ||
                                              titleLower.contains("todos los santos") ||
                                              titleLower.contains("tots sants") ||
                                              titleLower.contains("constitución") ||
                                              titleLower.contains("inmaculada") ||
                                              titleLower.contains("navidad") ||
                                              titleLower.contains("nadal") ||
                                              titleLower.contains("san vicente") ||
                                              titleLower.contains("sant vicent") ||
                                              titleLower.contains("9 d'octubre")
                                              
                        if (isHolidayCalendar || isPublicHoliday) {
                            continue
                        }
                        
                        newEvents.add(
                            CalendarItemEntity(
                                title = title,
                                description = desc,
                                startMillis = start,
                                endMillis = if (isAllDayEvent) start else end,
                                itemType = "EVENT",
                                colorHex = "#0288D1",
                                calendarEventId = eventIdLong,
                                isAllDay = isAllDayEvent
                            )
                        )
                    }
                    
                    val existingItems = database.calendarDao().getAllItemsList().toMutableList()
                    newEvents.forEach { event ->
                        val exists = existingItems.any {
                            (event.calendarEventId != null && event.calendarEventId != 0L && it.calendarEventId == event.calendarEventId) || 
                            (it.title.trim().lowercase() == event.title.trim().lowercase() && it.startMillis == event.startMillis && it.endMillis == event.endMillis)
                        }
                        if (!exists && (event.calendarEventId == null || !activeDeletedSet.contains(event.calendarEventId))) {
                            repository.insertCalendarItem(event)
                            existingItems.add(event)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error syncing calendar events", e)
            }
        }
    }

    private var lastLocationUpdateTime = 0L
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    fun shouldRequestLocationUpdate(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastLocationUpdateTime >= 10 * 60 * 1000 // 10 minutes in ms
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        lastLatitude = latitude
        lastLongitude = longitude
        _lastLocation.value = Pair(latitude, longitude)
        if (activeTripState.value != null) {
            refreshRealTimeTripStatus()
        }
    }

    fun updateWeatherByLocation(latitude: Double, longitude: Double, context: android.content.Context) {
        lastLocationUpdateTime = System.currentTimeMillis()
        
        lastLatitude = latitude
        lastLongitude = longitude
        _lastLocation.value = Pair(latitude, longitude)
        viewModelScope.launch {
            _weatherData.value = null
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
                _weatherData.value = WeatherService.getWeatherDataByCoords(latitude, longitude, finalCity)
                repository.savePreference("weather_city", finalCity)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error updating weather by location", e)
            }
        }
    }

}

data class TransitCardUiModel(
    val entity: TransitCardEntity,
    val cardNumber: String,
    val assignedName: String,
    val defaultName: String,
    val cardType: String,
    val remainingValue: String,
    val detailsJson: String,
    val isFaded: Boolean,
    val isManuallyInactive: Boolean,
    val category: String,
    val title: String,
    val clase: String,
    val operador: String,
    val zonas: String,
    val ampliado: String,
    val fechaCaducidad: String,
    val fechaRecarga: String,
    val isCurrentlyActive: Boolean,
    val viajesList: List<TransitTripUiModel>
)

data class TransitTripUiModel(
    val estacion: String,
    val fecha: String,
    val tipoValidacion: String,
    val zona: String
)
