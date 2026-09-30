package com.example.ui.map

import android.app.Application
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.data.repository.MetroRepository
import com.example.data.repository.ValenbisiRepository
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.bus.BusMapper
import com.example.ui.bus.EmtBusTime
import com.example.ui.cercanias.CercaniasDeparture
import com.example.data.mapper.CercaniasDepartureMapper
import com.example.ui.map.components.ValenbisiStation
import com.example.ui.metro.RealTimeDeparture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MapDataLoader(
    private val application: Application,
    private val database: AppDatabase,
    private val metroRepository: MetroRepository,
    private val renfeRepository: RenfeRepository,
    private val valenbisiRepository: ValenbisiRepository,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val metroStations: MutableStateFlow<List<MetroStation>>,
    private val busStops: MutableStateFlow<List<GeoportalStopEntity>>,
    private val metrobusStops: MutableStateFlow<List<com.example.data.database.MetrobusStopEntity>>,
    private val cercaniasStations: MutableStateFlow<List<CercaniasStationEntity>>,
    private val valenbisiStations: MutableStateFlow<List<ValenbisiStation>>,
    private val valenbisiLoading: MutableStateFlow<Boolean>,
    private val busTimes: MutableStateFlow<List<EmtBusTime>>,
    private val busTimesLoading: MutableStateFlow<Boolean>,
    private val metroDepartures: MutableStateFlow<List<RealTimeDeparture>>,
    private val metroDeparturesLoading: MutableStateFlow<Boolean>,
    private val cercaniasDepartures: MutableStateFlow<List<CercaniasDeparture>>,
    private val cercaniasDeparturesLoading: MutableStateFlow<Boolean>
) {

    private val metrobusRepository = com.example.data.repository.MetrobusRepository(database, httpClient, application.applicationContext)
    private var valenbisiPeriodicJob: Job? = null
    val busTimesLoadingMore = MutableStateFlow(false)
    val metrobusTimesLoadingMore = MutableStateFlow(false)
    val selectedMetrobusShapes = MutableStateFlow<Map<String, List<org.osmdroid.util.GeoPoint>>>(emptyMap())

    // Dedicated Scheduled Departures States
    val emtScheduledDepartures = MutableStateFlow<List<EmtBusTime>>(emptyList())
    val emtScheduledLoading = MutableStateFlow(false)
    val isEmtScheduledLoaded = MutableStateFlow(false)
    private var emtScheduledLimit = 5

    val metrobusScheduledDepartures = MutableStateFlow<List<com.example.ui.bus.MetrobusDepartureUiModel>>(emptyList())
    val metrobusScheduledLoading = MutableStateFlow(false)
    val isMetrobusScheduledLoaded = MutableStateFlow(false)
    private var metrobusScheduledLimit = 5

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            // Load Metro Stations
            try {
                val stations = metroRepository.loadMetroStations()
                metroStations.value = stations
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MapDataLoader", "Error loading metro stations", e)
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            // Load Metrobús Stops from Room DB / Repo
            try {
                var activeStops = database.metrobusStopDao().getAllActiveStops()
                if (activeStops.isEmpty() || activeStops.none { !it.lineas.isNullOrEmpty() }) {
                    metrobusRepository.syncStops(forceRefresh = true)
                    activeStops = database.metrobusStopDao().getAllActiveStops()
                } else {
                    metrobusRepository.ensureStopsCached()
                }
                metrobusStops.value = activeStops
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MapDataLoader", "Error loading metrobus stops", e)
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            // Load Bus Stops from Room DB, only falling back to JSON seeding if empty
            try {
                var activeStops = database.geoportalStopDao().getAllActiveStops()
                if (activeStops.isEmpty() || activeStops.size < 50) {
                    val loadedFromSync = BusMapper.parseStopsFromJsonDirect(application)
                    if (loadedFromSync.isNotEmpty()) {
                        database.geoportalStopDao().replaceAllStops(loadedFromSync)
                        activeStops = database.geoportalStopDao().getAllActiveStops()
                    }
                }
                busStops.value = activeStops
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MapDataLoader", "Error loading bus stops", e)
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            // Load Cercanias Stations
            try {
                renfeRepository.initDatabaseFromAssetsIfNeeded()
                renfeRepository.getAllStationsFlow().collect { stations ->
                    cercaniasStations.value = stations.distinctBy { it.stop_id }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MapDataLoader", "Error loading cercanias stations", e)
                }
            }
        }

        // Load Valenbisi stations
        refreshValenbisiStations(force = false)
    }

    fun startValenbisiPeriodicRefresh() {
        if (valenbisiPeriodicJob?.isActive == true) return
        valenbisiPeriodicJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(600_000L) // 10 minutes interval matching Valenbisi API update cycle
                if (isActive) {
                    refreshValenbisiStations()
                }
            }
        }
    }

    fun stopValenbisiPeriodicRefresh() {
        valenbisiPeriodicJob?.cancel()
        valenbisiPeriodicJob = null
    }

    fun refreshValenbisiStations(force: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val cached = ValenbisiRepository.getCachedStations()
            if (!force && cached.isNotEmpty()) {
                if (valenbisiStations.value.isEmpty()) {
                    valenbisiStations.value = cached
                }
                return@launch
            }
            valenbisiLoading.value = true
            try {
                val stations = valenbisiRepository.fetchStations(force)
                if (stations.isNotEmpty()) {
                    valenbisiStations.value = stations
                }
            } catch (e: Exception) {
                Log.e("MapDataLoader", "Error refreshing Valenbisi stations", e)
            } finally {
                valenbisiLoading.value = false
            }
        }
    }

    private var isEmtScheduledExpanded = false
    private var busScheduledLimit = 3

    fun resetEmtScheduledState() {
        isEmtScheduledExpanded = false
        busScheduledLimit = 3
        emtScheduledDepartures.value = emptyList()
        emtScheduledLoading.value = false
        isEmtScheduledLoaded.value = false
        emtScheduledLimit = 100
    }

    fun fetchEmtScheduledDepartures(stopId: String, stopName: String? = null, isLoadMore: Boolean = false) {
        if (isEmtScheduledLoaded.value && emtScheduledDepartures.value.isNotEmpty() && !isLoadMore) {
            return
        }
        scope.launch(Dispatchers.IO) {
            emtScheduledLoading.value = true
            try {
                val sched = com.example.data.repository.RealTimeTransitRepository.getEmtScheduledDepartures(
                    stopNumber = stopId,
                    stopName = stopName,
                    limitPerLine = 100
                )
                emtScheduledDepartures.value = sched
                isEmtScheduledLoaded.value = true
            } catch (e: Exception) {
                Log.w("MapDataLoader", "Error loading EMT scheduled departures: ${e.message}")
            } finally {
                emtScheduledLoading.value = false
            }
        }
    }

    private var busTimesJob: kotlinx.coroutines.Job? = null
    private var metroDeparturesJob: kotlinx.coroutines.Job? = null
    private var cercaniasDeparturesJob: kotlinx.coroutines.Job? = null
    private var metrobusTimesJob: kotlinx.coroutines.Job? = null

    fun clearBusTimes() {
        busTimesJob?.cancel()
        busTimesJob = null
        busTimes.value = emptyList()
        busTimesLoading.value = false
        busTimesLoadingMore.value = false
    }

    fun clearMetroDepartures() {
        metroDeparturesJob?.cancel()
        metroDeparturesJob = null
        metroDepartures.value = emptyList()
        metroDeparturesLoading.value = false
    }

    fun clearCercaniasDepartures() {
        cercaniasDeparturesJob?.cancel()
        cercaniasDeparturesJob = null
        cercaniasDepartures.value = emptyList()
        cercaniasDeparturesLoading.value = false
    }

    fun clearMetrobusTimes() {
        metrobusTimesJob?.cancel()
        metrobusTimesJob = null
        metrobusTimesLoadingMore.value = false
    }

    fun fetchBusTimes(
        stopId: String,
        stopName: String? = null,
        limitPerLine: Int = 3,
        isLoadMore: Boolean = false,
        includeScheduled: Boolean = false
    ) {
        busTimesJob?.cancel()
        busTimesJob = scope.launch(Dispatchers.IO) {
            if (!isLoadMore) {
                busTimesLoading.value = true
            } else {
                busTimesLoadingMore.value = true
            }
            try {
                val arrivals = com.example.data.repository.RealTimeTransitRepository.getEmtLiveArrivals(
                    stopNumber = stopId,
                    stopName = stopName,
                    limitPerLine = limitPerLine,
                    includeScheduled = includeScheduled
                )
                busTimes.value = arrivals
            } catch (e: Exception) {
                Log.w("MapDataLoader", "EMT API issue for $stopId: ${e.message}")
            } finally {
                if (!isLoadMore) {
                    busTimesLoading.value = false
                } else {
                    busTimesLoadingMore.value = false
                }
            }
        }
    }

    fun loadMoreScheduledBusTimes(stopId: String) {
        fetchEmtScheduledDepartures(stopId, isLoadMore = true)
    }

    fun fetchMetroDepartures(station: MetroStation) {
        metroDeparturesJob?.cancel()
        metroDeparturesJob = scope.launch(Dispatchers.IO) {
            metroDeparturesLoading.value = true
            try {
                val numericId = station.id.toIntOrNull()
                var liveDeps = emptyList<RealTimeDeparture>()
                if (numericId != null) {
                    val arrivals = com.example.data.repository.RealTimeTransitRepository.getMetroLiveArrivals(numericId.toString())
                    liveDeps = arrivals.mapIndexed { i, arrival ->
                        val lineObj = ValenciaMetroData.lines.find { it.id == arrival.line }
                        val colorHex = lineObj?.colorHex ?: "#1E88E5"
                        RealTimeDeparture(
                            lineId = arrival.line,
                            destination = arrival.destination,
                            minutesRemaining = arrival.minutes,
                            secondsRemaining = arrival.seconds,
                            colorHex = colorHex,
                            estimatedTime = arrival.estimatedTime,
                            status = arrival.status,
                            track = arrival.track,
                            capacidad = arrival.capacidad,
                            id = "${arrival.line}_${arrival.destination}_$i",
                            isRealTime = arrival.isRealTime
                        )
                    }.sortedBy { it.secondsRemaining }
                }

                metroDepartures.value = liveDeps
            } catch (e: Exception) {
                Log.w("MapDataLoader", "Error or timeout fetching live metro departures for station ${station.id}: ${e.message}")
                metroDepartures.value = emptyList()
            } finally {
                metroDeparturesLoading.value = false
            }
        }
    }

    fun fetchCercaniasDepartures(stationId: String) {
        cercaniasDeparturesJob?.cancel()
        cercaniasDeparturesJob = scope.launch(Dispatchers.IO) {
            cercaniasDeparturesLoading.value = true
            try {
                val rawDeps = renfeRepository.getDeparturesForStation(stationId)
                val sorted = CercaniasDepartureMapper.sortDeparturesChronologically(rawDeps)
                cercaniasDepartures.value = sorted
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MapDataLoader", "Error fetching cercanias departures for $stationId", e)
                }
                if (e is kotlinx.coroutines.CancellationException) throw e
                cercaniasDepartures.value = emptyList()
            } finally {
                cercaniasDeparturesLoading.value = false
            }
        }
    }

    private var isMetrobusScheduledExpanded = false
    private var scheduledMetrobusLimitPerLine = 3

    fun resetMetrobusScheduledState() {
        isMetrobusScheduledExpanded = false
        scheduledMetrobusLimitPerLine = 3
        metrobusScheduledDepartures.value = emptyList()
        metrobusScheduledLoading.value = false
        isMetrobusScheduledLoaded.value = false
        metrobusScheduledLimit = 100
    }

    fun fetchMetrobusScheduledDepartures(stopId: String, isLoadMore: Boolean = false) {
        if (isMetrobusScheduledLoaded.value && metrobusScheduledDepartures.value.isNotEmpty() && !isLoadMore) {
            return
        }
        scope.launch(Dispatchers.IO) {
            metrobusScheduledLoading.value = true
            try {
                val sched = metrobusRepository.getMetrobusScheduledDepartures(
                    stopId = stopId,
                    limitPerLine = 100
                )
                metrobusScheduledDepartures.value = sched
                isMetrobusScheduledLoaded.value = true
            } catch (e: Exception) {
                Log.w("MapDataLoader", "Error loading Metrobus scheduled departures: ${e.message}")
            } finally {
                metrobusScheduledLoading.value = false
            }
        }
    }

    fun fetchMetrobusTimes(
        stopId: String,
        metrobusTimes: MutableStateFlow<List<com.example.ui.bus.MetrobusDepartureUiModel>>,
        metrobusTimesLoading: MutableStateFlow<Boolean>? = null,
        limitPerLine: Int = 3,
        isLoadMore: Boolean = false,
        includeScheduled: Boolean = false
    ) {
        metrobusTimesJob?.cancel()
        metrobusTimesJob = scope.launch(Dispatchers.IO) {
            if (!isLoadMore) {
                metrobusTimesLoading?.value = true
            } else {
                metrobusTimesLoadingMore.value = true
            }
            try {
                val results = metrobusRepository.getMetrobusArrivals(
                    stopId = stopId,
                    limitPerLine = limitPerLine,
                    includeScheduled = includeScheduled
                )
                metrobusTimes.value = results
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MapDataLoader", "Error fetching metrobus times for $stopId", e)
                }
                if (!isLoadMore) {
                    metrobusTimes.value = emptyList()
                }
            } finally {
                if (!isLoadMore) {
                    metrobusTimesLoading?.value = false
                } else {
                    metrobusTimesLoadingMore.value = false
                }
            }
        }
    }

    fun loadMoreScheduledMetrobusTimes(
        stopId: String,
        metrobusTimes: MutableStateFlow<List<com.example.ui.bus.MetrobusDepartureUiModel>>
    ) {
        fetchMetrobusScheduledDepartures(stopId, isLoadMore = true)
    }

    fun fetchMetrobusShapes(
        lineCodes: List<String>,
        stopLat: Double? = null,
        stopLon: Double? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val shapesMap = mutableMapOf<String, List<org.osmdroid.util.GeoPoint>>()
            lineCodes.forEach { lineCode ->
                try {
                    val shapeData = metrobusRepository.fetchLineShape(lineCode)
                    val polylines = shapeData?.polylinesByDirection ?: emptyMap()
                    if (polylines.isNotEmpty()) {
                        polylines.forEach { (dir, coordList) ->
                            val points = coordList.mapNotNull { pt ->
                                if (pt.size >= 2) org.osmdroid.util.GeoPoint(pt[0], pt[1]) else null
                            }
                            if (points.isNotEmpty()) {
                                shapesMap["${lineCode}_$dir"] = points
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            selectedMetrobusShapes.value = shapesMap
        }
    }
}
