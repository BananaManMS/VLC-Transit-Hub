package com.example.ui.metro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MetroScheduledDeparture
import com.example.data.model.MetroTrainTimeline
import com.example.data.repository.MetroScheduleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * Dedicated ViewModel handling scheduled departures and train timeline calculation
 * keeping MetroViewModel and UI components under 500 lines.
 */
class MetroScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val scheduleRepository = MetroScheduleRepository.getInstance(application)

    private val _scheduledDepartures = MutableStateFlow<List<MetroScheduledDeparture>>(emptyList())
    val scheduledDepartures = _scheduledDepartures.asStateFlow()

    private val _isLoadingScheduled = MutableStateFlow(false)
    val isLoadingScheduled = _isLoadingScheduled.asStateFlow()

    private val _isScheduledSheetVisible = MutableStateFlow(false)
    val isScheduledSheetVisible = _isScheduledSheetVisible.asStateFlow()

    // Inline theoretical departures shown by scrolling up / pulling up at the bottom
    private val _inlineTheoreticalDepartures = MutableStateFlow<List<MetroScheduledDeparture>>(emptyList())
    val inlineTheoreticalDepartures = _inlineTheoreticalDepartures.asStateFlow()

    private val _selectedLineFilter = MutableStateFlow<String?>(null)
    val selectedLineFilter = _selectedLineFilter.asStateFlow()

    private val _availableLines = MutableStateFlow<List<String>>(emptyList())
    val availableLines = _availableLines.asStateFlow()

    private var activeStationFgvId: String = ""
    private var activeStationName: String = ""

    private var cachedStationDepartures: List<MetroScheduledDeparture> = emptyList()

    private val _isLoadingInlineTheoretical = MutableStateFlow(false)
    val isLoadingInlineTheoretical = _isLoadingInlineTheoretical.asStateFlow()

    private val _isInlineTheoreticalLoaded = MutableStateFlow(false)
    val isInlineTheoreticalLoaded = _isInlineTheoreticalLoaded.asStateFlow()

    private val _selectedTrainTimeline = MutableStateFlow<MetroTrainTimeline?>(null)
    val selectedTrainTimeline = _selectedTrainTimeline.asStateFlow()

    private val _isLoadingTimeline = MutableStateFlow(false)
    val isLoadingTimeline = _isLoadingTimeline.asStateFlow()

    init {
        viewModelScope.launch {
            scheduleRepository.ensureLoaded()
        }
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                if (_isInlineTheoreticalLoaded.value || _isScheduledSheetVisible.value) {
                    pruneDeparturesPastCurrentTime()
                }
            }
        }
    }

    private fun pruneDeparturesPastCurrentTime() {
        if (!_isInlineTheoreticalLoaded.value && !_isScheduledSheetVisible.value) return
        if (cachedStationDepartures.isEmpty() && _scheduledDepartures.value.isEmpty()) return
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        val currentMinOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        if (_isInlineTheoreticalLoaded.value && cachedStationDepartures.isNotEmpty()) {
            val validCached = cachedStationDepartures.filter { it.timeMinutes > currentMinOfDay }
            if (validCached.size != cachedStationDepartures.size) {
                cachedStationDepartures = validCached
                _inlineTheoreticalDepartures.value = filterDeparturesInMemory(cachedStationDepartures, _selectedLineFilter.value, _availableLines.value)
            }
        }

        if (_isScheduledSheetVisible.value && _scheduledDepartures.value.isNotEmpty()) {
            val updatedSheet = _scheduledDepartures.value.filter { it.timeMinutes > currentMinOfDay }
            if (updatedSheet.size != _scheduledDepartures.value.size) {
                _scheduledDepartures.value = updatedSheet
            }
        }
    }

    private fun filterDeparturesInMemory(
        allUpcoming: List<MetroScheduledDeparture>,
        lineFilter: String?,
        stationLines: List<String>
    ): List<MetroScheduledDeparture> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        val currentMinOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val normFilter = lineFilter?.replace("L", "", ignoreCase = true)?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("ALL", ignoreCase = true) }

        val isSingleLineStation = stationLines.size <= 1
        val isSpecificLineSelected = normFilter != null

        // If single line station or user specifically picked a line -> Show full day schedule
        // If multi-line station and all lines are showing -> 3 hour window (180 min)
        val maxMinutes = if (isSingleLineStation || isSpecificLineSelected) {
            null
        } else {
            currentMinOfDay + 180
        }

        return allUpcoming.filter { dep ->
            if (com.example.util.MetroDepotFilterHelper.isDepotExcludedStationLine(activeStationFgvId, activeStationName, dep.line)) {
                return@filter false
            }
            // Strictly greater than current minute so departed trains are immediately removed
            val isUpcoming = dep.timeMinutes > currentMinOfDay
            val withinWindow = if (maxMinutes != null) dep.timeMinutes <= maxMinutes else true
            val matchesLine = if (normFilter != null) {
                dep.line.replace("L", "", ignoreCase = true).trim().equals(normFilter, ignoreCase = true)
            } else true

            isUpcoming && withinWindow && matchesLine
        }
    }

    /**
     * Loads theoretical/scheduled departures starting from the current time.
     * Caches departures for the day in memory so line filtering is instantaneous.
     */
    fun loadInlineTheoreticalDepartures(
        stationFgvId: String,
        stationName: String,
        lineFilter: String? = _selectedLineFilter.value
    ) {
        if (_isLoadingInlineTheoretical.value) return
        _isLoadingInlineTheoretical.value = true
        activeStationFgvId = stationFgvId
        activeStationName = stationName
        _selectedLineFilter.value = lineFilter

        viewModelScope.launch {
            try {
                scheduleRepository.ensureLoaded()
                val webId = scheduleRepository.getWebIdForStationId(stationFgvId, stationName)
                if (webId != null) {
                    val lines = scheduleRepository.getLinesForStation(webId)
                    _availableLines.value = lines

                    val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
                    val currentMinOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

                    // Fetch all upcoming departures for this station today into cache
                    val departures = scheduleRepository.getScheduledDepartures(
                        stationWebId = webId,
                        limit = 1000,
                        fromMinutesOfDay = currentMinOfDay + 1,
                        toMinutesOfDay = null,
                        lineFilter = null
                    )
                    cachedStationDepartures = departures.filter { it.timeMinutes > currentMinOfDay }
                    _inlineTheoreticalDepartures.value = filterDeparturesInMemory(cachedStationDepartures, lineFilter, lines)
                } else {
                    cachedStationDepartures = emptyList()
                    _inlineTheoreticalDepartures.value = emptyList()
                    _availableLines.value = emptyList()
                }
                _isInlineTheoreticalLoaded.value = true
            } catch (e: Exception) {
                cachedStationDepartures = emptyList()
                _inlineTheoreticalDepartures.value = emptyList()
                _isInlineTheoreticalLoaded.value = true
            } finally {
                _isLoadingInlineTheoretical.value = false
            }
        }
    }

    /**
     * Instantaneous in-memory line filter without reloading the entire page or triggering loading state.
     */
    fun setLineFilter(line: String?) {
        _selectedLineFilter.value = line
        _inlineTheoreticalDepartures.value = filterDeparturesInMemory(cachedStationDepartures, line, _availableLines.value)
    }

    fun resetInlineTheoreticalDepartures() {
        cachedStationDepartures = emptyList()
        _inlineTheoreticalDepartures.value = emptyList()
        _isInlineTheoreticalLoaded.value = false
        _isLoadingInlineTheoretical.value = false
        _selectedLineFilter.value = null
    }

    fun showScheduledDepartures(stationFgvId: String, stationName: String) {
        _isScheduledSheetVisible.value = true
        _isLoadingScheduled.value = true
        viewModelScope.launch {
            try {
                scheduleRepository.ensureLoaded()
                val webId = scheduleRepository.getWebIdForStationId(stationFgvId, stationName)
                if (webId != null) {
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
                    val currentMinOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    val raw = scheduleRepository.getScheduledDepartures(
                        stationWebId = webId,
                        fromMinutesOfDay = currentMinOfDay + 1
                    )
                    _scheduledDepartures.value = raw.filter { it.timeMinutes > currentMinOfDay }
                } else {
                    _scheduledDepartures.value = emptyList()
                }
            } catch (e: Exception) {
                _scheduledDepartures.value = emptyList()
            } finally {
                _isLoadingScheduled.value = false
            }
        }
    }

    fun dismissScheduledDepartures() {
        _isScheduledSheetVisible.value = false
        _scheduledDepartures.value = emptyList()
    }

    /**
     * Finds and loads the complete train timeline for a scheduled or live departure.
     */
    fun loadTimelineForLiveTrain(
        stationFgvId: String,
        stationName: String,
        line: String,
        destinationName: String,
        secondsRemaining: Int,
        estimatedTime: String? = null,
        originStationWebId: Int? = null,
        destinationWebId: Int? = null,
        trainServiceId: Int? = null
    ) {
        _isLoadingTimeline.value = true
        _selectedTrainTimeline.value = null
        viewModelScope.launch {
            try {
                scheduleRepository.ensureLoaded()
                val currentWebId = scheduleRepository.getWebIdForStationId(stationFgvId, stationName)
                if (currentWebId != null) {
                    val madridZone = TimeZone.getTimeZone("Europe/Madrid")
                    val expectedArrivalMin = if (!estimatedTime.isNullOrBlank() && estimatedTime.contains(":")) {
                        val parts = estimatedTime.trim().split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull()
                        val m = parts.getOrNull(1)?.toIntOrNull()
                        if (h != null && m != null) {
                            h * 60 + m
                        } else {
                            val cal = Calendar.getInstance(madridZone)
                            (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) + (secondsRemaining / 60)
                        }
                    } else {
                        val cal = Calendar.getInstance(madridZone)
                        (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) + (secondsRemaining / 60)
                    }

                    val timeline = scheduleRepository.getTrainTimeline(
                        currentStationWebId = currentWebId,
                        line = line,
                        destinationName = destinationName,
                        estimatedArrivalMinutesOfDay = expectedArrivalMin,
                        originStationWebId = originStationWebId,
                        destinationWebId = destinationWebId,
                        trainServiceId = trainServiceId
                    )
                    _selectedTrainTimeline.value = timeline
                }
            } catch (e: Exception) {
                _selectedTrainTimeline.value = null
            } finally {
                _isLoadingTimeline.value = false
            }
        }
    }

    fun clearTrainTimeline() {
        _selectedTrainTimeline.value = null
    }
}
