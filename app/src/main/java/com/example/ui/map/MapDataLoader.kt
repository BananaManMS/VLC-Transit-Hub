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

    private val metrobusRepository = com.example.data.repository.MetrobusRepository(database, httpClient)

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            // Load Metro Stations
            try {
                val stations = metroRepository.loadMetroStations()
                metroStations.value = stations
            } catch (e: Exception) {
                Log.e("MapDataLoader", "Error loading metro stations", e)
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
                Log.e("MapDataLoader", "Error loading metrobus stops", e)
            }
        }

        scope.launch(Dispatchers.IO) {
            // Load Bus Stops from Room DB / Assets
            try {
                val activeStops = database.geoportalStopDao().getAllActiveStops()
                if (activeStops.isNotEmpty()) {
                    busStops.value = activeStops
                } else {
                    val loaded = BusMapper.loadStopsFromAssets(application)
                    database.geoportalStopDao().insertAll(loaded)
                    busStops.value = loaded.filter { it.suprimida == 0 }
                }
            } catch (e: Exception) {
                Log.e("MapDataLoader", "Error loading bus stops", e)
            }
        }

        scope.launch(Dispatchers.IO) {
            // Load Cercanias Stations
            try {
                if (database.cercaniasStationDao().getStationCount() == 0) {
                    renfeRepository.initDatabaseFromAssetsIfNeeded()
                }
                renfeRepository.getAllStationsFlow().collect { stations ->
                    cercaniasStations.value = stations.distinctBy { it.stop_id }
                }
            } catch (e: Exception) {
                Log.e("MapDataLoader", "Error loading cercanias stations", e)
            }
        }

        // Load Valenbisi Stations initially and set up periodic refresh
        scope.launch(Dispatchers.IO) {
            while (true) {
                refreshValenbisiStations()
                delay(45_000)
            }
        }
    }

    fun refreshValenbisiStations() {
        scope.launch(Dispatchers.IO) {
            valenbisiLoading.value = true
            try {
                val stations = valenbisiRepository.fetchStations()
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

    fun fetchBusTimes(stopId: String) {
        scope.launch(Dispatchers.IO) {
            busTimesLoading.value = true
            busTimes.value = emptyList()
            try {
                val arrivals = com.example.data.repository.RealTimeTransitRepository.getEmtLiveArrivals(stopId)
                busTimes.value = arrivals
            } catch (e: Exception) {
                Log.w("MapDataLoader", "EMT API issue for $stopId: ${e.message}")
                busTimes.value = emptyList()
            } finally {
                busTimesLoading.value = false
            }
        }
    }

    fun fetchMetroDepartures(station: MetroStation) {
        scope.launch(Dispatchers.IO) {
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
                            id = "${arrival.line}_${arrival.destination}_$i"
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
        scope.launch(Dispatchers.IO) {
            cercaniasDeparturesLoading.value = true
            try {
                val rawDeps = renfeRepository.getDeparturesForStation(stationId)
                val sorted = CercaniasDepartureMapper.sortDeparturesChronologically(rawDeps)
                cercaniasDepartures.value = sorted
            } catch (e: Exception) {
                Log.e("MapDataLoader", "Error fetching cercanias departures for $stationId", e)
                cercaniasDepartures.value = emptyList()
            } finally {
                cercaniasDeparturesLoading.value = false
            }
        }
    }
}
