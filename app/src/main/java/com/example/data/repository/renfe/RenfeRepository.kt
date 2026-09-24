package com.example.data.repository.renfe

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.RenfeScheduleItem
import kotlinx.coroutines.flow.Flow

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

    suspend fun getDeparturesForStation(stationId: String): List<RenfeScheduleItem> {
        val station = stationDao.getStationByCode(stationId) ?: return emptyList()
        return station.horarios
    }
}
