package com.example.data.repository.renfe

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.RenfeScheduleItem
import com.example.data.mapper.CercaniasDepartureMapper
import com.example.ui.cercanias.CercaniasDeparture
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class RenfeRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    constructor(context: Context) : this(context, AppDatabase.getDatabase(context))

    private val syncManager = RenfeScheduleSyncManager(context, database)
    private val stationDao = database.cercaniasStationDao()

    suspend fun initDatabaseFromAssetsIfNeeded() {
        syncManager.initDatabaseFromAssetsIfNeeded()
    }

    suspend fun syncScheduleFromRemoteIfNeeded() {
        syncManager.syncScheduleFromRemoteIfNeeded()
    }

    suspend fun forceSyncScheduleFromRemote() {
        syncManager.forceSyncScheduleFromRemote()
    }

    suspend fun reloadFromAssets() {
        syncManager.reloadFromAssets()
    }

    suspend fun getAllStations(): List<CercaniasStationEntity> {
        return stationDao.getAllStations()
    }

    suspend fun fetchStations(): List<CercaniasStationEntity> {
        return getAllStations()
    }

    suspend fun getCachedStations(): List<CercaniasStationEntity> {
        return getAllStations()
    }

    fun getAllStationsFlow(): Flow<List<CercaniasStationEntity>> {
        return stationDao.getAllStationsFlow()
    }

    suspend fun getStationById(id: String): CercaniasStationEntity? {
        return stationDao.getStationByCode(id)
    }

    suspend fun updateStation(station: CercaniasStationEntity) {
        stationDao.insertStations(listOf(station))
    }

    suspend fun updateAllStations(stations: List<CercaniasStationEntity>) {
        stationDao.replaceAllStations(stations)
    }

    fun getFavoriteStationsFlow(): Flow<List<CercaniasStationEntity>> {
        return stationDao.getAllStationsFlow()
    }

    suspend fun getDeparturesForStation(stationId: String): List<CercaniasDeparture> {
        val station = stationDao.getStationByCode(stationId) ?: return emptyList()
        val stationName = station.nombre
        val results = mutableListOf<CercaniasDeparture>()
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        station.horarios.forEach { item ->
            val tripId = item.trip_ids.firstOrNull() ?: ""
            val dest = syncManager.getTripDestination(tripId, item.linea, stationName)
            if (CercaniasDepartureMapper.isValidDeparture(item, stationName, dest)) {
                val parts = item.llegada.split(":")
                val depMins = if (parts.size >= 2) {
                    (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                } else 0
                val remaining = if (depMins >= currentMinutes) depMins - currentMinutes else (1440 - currentMinutes + depMins)
                results.add(
                    CercaniasDeparture(
                        routeId = item.linea,
                        destination = dest,
                        minutesRemaining = remaining,
                        delayMinutes = 0,
                        tripId = tripId,
                        departureTime = item.llegada,
                        estimatedTime = item.llegada,
                        isLive = false,
                        platform = ""
                    )
                )
            }
        }
        return CercaniasDepartureMapper.sortDeparturesChronologically(results)
    }
}

