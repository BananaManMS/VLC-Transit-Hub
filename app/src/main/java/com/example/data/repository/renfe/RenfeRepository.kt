package com.example.data.repository.renfe

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.ui.cercanias.CercaniasDeparture
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RenfeRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val stationDao = database.cercaniasStationDao()
    
    private val syncManager = RenfeScheduleSyncManager(context, database)
    
    private val networkDataSource = GtfsNetworkDataSource()
    private val gtfsParser = GtfsParser()
    private val gtfsCacheManager = GtfsCacheManager(networkDataSource, gtfsParser)
    
    private val scheduleProcessor by lazy {
        CercaniasScheduleProcessor(
            gtfsCacheManager,
            syncManager::getTripDestination
        )
    }

    suspend fun syncScheduleFromRemoteIfNeeded() = syncManager.syncScheduleFromRemoteIfNeeded()
    suspend fun forceSyncScheduleFromRemote(): Boolean = syncManager.forceSyncScheduleFromRemote()
    suspend fun initDatabaseFromAssetsIfNeeded() = syncManager.initDatabaseFromAssetsIfNeeded()
    suspend fun reloadFromAssets() = syncManager.reloadFromAssets()

    suspend fun fetchGtfsRtVehiclePositions(): Map<String, LiveVehicleInfo> = gtfsCacheManager.getLiveVehiclePositions()
    suspend fun fetchGtfsRtTripUpdates(): Map<String, GtfsRtTripUpdate> = gtfsCacheManager.getLiveTripUpdates()

    suspend fun getDeparturesForStation(
        stopId: String,
        gtfsRtUpdates: Map<String, GtfsRtTripUpdate>? = null,
        gtfsVehiclePositions: Map<String, LiveVehicleInfo>? = null
    ): List<CercaniasDeparture> = withContext(Dispatchers.IO) {
        var station = stationDao.getStationById(stopId)
        if (station == null || station.horarios.isEmpty()) {
            initDatabaseFromAssetsIfNeeded()
            station = stationDao.getStationById(stopId)
            if (station == null) return@withContext emptyList()
        }
        
        val liveUpdatesMap = gtfsRtUpdates ?: fetchGtfsRtTripUpdates()
        val vehicleMap = gtfsVehiclePositions ?: fetchGtfsRtVehiclePositions()
        
        scheduleProcessor.processDepartures(
            station, 
            liveUpdatesMap, 
            vehicleMap, 
            syncManager.getStationNameMap()
        )
    }

    suspend fun getAllStations(): List<CercaniasStationEntity> = withContext(Dispatchers.IO) {
        if (stationDao.getStationCount() == 0) {
            initDatabaseFromAssetsIfNeeded()
        }
        stationDao.getAllStations().distinctBy { it.stop_id }
    }

    fun getAllStationsFlow(): Flow<List<CercaniasStationEntity>> = stationDao.getAllStationsFlow()

    fun getFavoriteStationsFlow(): Flow<List<CercaniasStationEntity>> = stationDao.getFavoriteStationsFlow()

    suspend fun getStationById(stopId: String): CercaniasStationEntity? = withContext(Dispatchers.IO) {
        stationDao.getStationById(stopId)
    }

    suspend fun updateStation(station: CercaniasStationEntity) = withContext(Dispatchers.IO) {
        stationDao.updateStation(station)
    }

    suspend fun updateAllStations(stations: List<CercaniasStationEntity>) = withContext(Dispatchers.IO) {
        stationDao.updateAll(stations)
    }
}
