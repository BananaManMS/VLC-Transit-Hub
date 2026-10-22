package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// 1. Metro Station Entity
@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val lines: String,
    val zone: String,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    @get:Ignore
    val code: String get() = id.toString()

    @get:Ignore
    val lat: Double get() = latitude ?: 0.0

    @get:Ignore
    val lon: Double get() = longitude ?: 0.0

    @get:Ignore
    val accessibility: Boolean get() = true

    @get:Ignore
    val isFavorite: Boolean get() = false

    @get:Ignore
    val description: String get() = zone

    @get:Ignore
    val displayName: String get() = name
}

// 2. Calendar Item Entity
@Entity(tableName = "calendar_items")
data class CalendarItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val dueMillis: Long? = null,
    val isCompleted: Boolean = false,
    val itemType: String,
    val colorHex: String = "#3B82F6",
    val calendarEventId: Long? = null,
    val isAllDay: Boolean = false
) {
    @get:Ignore
    val date: String get() = startMillis?.toString() ?: ""

    @get:Ignore
    val startTime: String get() = startMillis?.toString() ?: ""

    @get:Ignore
    val endTime: String get() = endMillis?.toString() ?: ""
}

// 3. Preference Entity
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String
)

// 4. Transit Card Entity
@Entity(tableName = "transit_cards")
data class TransitCardEntity(
    @PrimaryKey val cardNumber: String,
    val assignedName: String = "",
    val defaultName: String = "",
    val cardType: String = "",
    val remainingValue: String = "",
    val detailsJson: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val customOrder: Int = 0,
    val showOnHome: Boolean = true,
    val isManuallyInactive: Boolean = false
)

// 5. EMT Geoportal Stop Entity
@Entity(tableName = "geoportal_stops")
data class GeoportalStopEntity(
    @PrimaryKey val id_parada: String,
    val denominacion: String,
    val suprimida: Int = 0,
    val lat: Double,
    val lon: Double,
    val lineas: String? = null
) {
    @get:Ignore
    val id: Int get() = id_parada.toIntOrNull() ?: 0

    @get:Ignore
    val name: String get() = denominacion

    @get:Ignore
    val displayName: String get() = denominacion

    @get:Ignore
    val latitud: Double get() = lat

    @get:Ignore
    val longitud: Double get() = lon

    constructor(id: Int, name: String, lat: Double, lon: Double, lineas: String = "") : this(
        id_parada = id.toString(),
        denominacion = name,
        suprimida = 0,
        lat = lat,
        lon = lon,
        lineas = lineas
    )
}

// 6. Cercanias Station Entity
data class RenfeScheduleItem(
    val linea: String = "",
    val trip_ids: List<String> = emptyList(),
    val llegada: String = "",
    val tripIds: List<String> = trip_ids,
    val destino: String = ""
) {
    val effectiveTripIds: List<String>
        get() = if (trip_ids.isNotEmpty()) trip_ids else tripIds
}

@Entity(tableName = "cercanias_stations")
data class CercaniasStationEntity(
    @PrimaryKey val stop_id: String,
    val nombre: String,
    val lat: Double,
    val lon: Double,
    val lineas: List<String> = emptyList(),
    val horarios: List<RenfeScheduleItem> = emptyList(),
    val isFavorite: Boolean = false
) {
    @get:Ignore
    val id: String get() = stop_id

    @get:Ignore
    val name: String get() = nombre

    @get:Ignore
    val displayName: String get() = com.example.data.mapper.CercaniasDepartureMapper.formatStationDisplayName(nombre)

    @get:Ignore
    val lines: List<String> get() = lineas

    @get:Ignore
    val latitud: Double get() = lat

    @get:Ignore
    val longitud: Double get() = lon
}

// 7. Cercanias Schedule Entity
@Entity(tableName = "cercanias_schedules")
data class CercaniasScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trip_id: String,
    val linea: String,
    val origen: String,
    val destino: String,
    val hora_salida: String,
    val hora_llegada: String,
    val dias_operacion: String? = null,
    val paradas_intermedias: List<String> = emptyList()
)

// 8. Metrobus Stop Entity
@Entity(tableName = "metrobus_stops")
data class MetrobusStopEntity(
    @PrimaryKey val id: String,
    val denominacion: String,
    val municipio: String,
    val latitud: Double,
    val longitud: Double,
    val lineas: String = "",
    val codigoParada: String = "",
    val direccion: String = ""
) {
    @get:Ignore
    val name: String get() = denominacion

    @get:Ignore
    val displayName: String get() = denominacion

    @get:Ignore
    val lat: Double get() = latitud

    @get:Ignore
    val lon: Double get() = longitud

    @get:Ignore
    val id_parada: String get() = id

    @get:Ignore
    val suprimida: Int get() = 0

    constructor(
        id_parada: String,
        denominacion: String,
        lat: Double,
        lon: Double,
        lineas: String = "",
        suprimida: Int = 0
    ) : this(
        id = id_parada,
        denominacion = denominacion,
        municipio = "",
        latitud = lat,
        longitud = lon,
        lineas = lineas,
        codigoParada = id_parada,
        direccion = ""
    )
}

// 9. Active Trip Entity
@Entity(tableName = "active_trip")
data class ActiveTripEntity(
    @PrimaryKey val tripId: String = ACTIVE_TRIP_ID,
    val originName: String,
    val destinationName: String,
    val routeDataJson: String,
    val status: String,
    val currentLegIndex: Int = 0,
    val lastLegScheduledArrivalTimeMillis: Long = 0L,
    val startTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    @get:Ignore
    val startTime: Long get() = startTimestamp

    @get:Ignore
    val currentStepIndex: Int get() = currentLegIndex

    @get:Ignore
    val totalSteps: Int get() = 1

    @get:Ignore
    val rawJsonData: String get() = routeDataJson

    companion object {
        const val ACTIVE_TRIP_ID = "ACTIVE_TRIP"
        const val EXPIRATION_GRACE_PERIOD_MILLIS = 1800000L
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_ACTIVE = "IN_PROGRESS"
        const val STATUS_PLANNED = "PLANNED"
    }
}

// Converters
class CercaniasTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return ""
        val array = JSONArray()
        for (item in list) {
            array.put(item)
        }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(data)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromScheduleItemList(list: List<RenfeScheduleItem>?): String {
        if (list.isNullOrEmpty()) return ""
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("linea", item.linea)
            obj.put("llegada", item.llegada)
            obj.put("destino", item.destino)
            val tripsArray = JSONArray()
            val trips = item.effectiveTripIds
            trips.forEach { tripsArray.put(it) }
            obj.put("trip_ids", tripsArray)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toScheduleItemList(data: String?): List<RenfeScheduleItem> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(data)
            val list = mutableListOf<RenfeScheduleItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val linea = obj.optString("linea", "")
                val llegada = obj.optString("llegada", "")
                val destino = obj.optString("destino", "")
                val tripsArr = obj.optJSONArray("trip_ids")
                val tripIds = mutableListOf<String>()
                if (tripsArr != null) {
                    for (j in 0 until tripsArr.length()) {
                        tripIds.add(tripsArr.getString(j))
                    }
                }
                list.add(RenfeScheduleItem(linea = linea, trip_ids = tripIds, tripIds = tripIds, llegada = llegada, destino = destino))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// DAOs
@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY name ASC")
    suspend fun getAllStations(): List<StationEntity>

    @Query("SELECT * FROM stations ORDER BY name ASC")
    fun getAllStationsFlow(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: Int): StationEntity?

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getStationByIdString(id: String): StationEntity? {
        val numericId = id.toIntOrNull() ?: return null
        return getStationById(numericId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>) {
        insertAll(stations)
    }

    @Query("DELETE FROM stations")
    suspend fun deleteAllStations()

    @Transaction
    suspend fun replaceAllStations(stations: List<StationEntity>) {
        deleteAllStations()
        insertAll(stations)
    }
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_items ORDER BY COALESCE(startMillis, dueMillis, 0) ASC")
    fun getAllItems(): Flow<List<CalendarItemEntity>>

    fun getAllItemsFlow(): Flow<List<CalendarItemEntity>> = getAllItems()

    @Query("SELECT * FROM calendar_items ORDER BY COALESCE(startMillis, dueMillis, 0) ASC")
    suspend fun getAllItemsList(): List<CalendarItemEntity>

    suspend fun getAllEvents(): List<CalendarItemEntity> = getAllItemsList()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CalendarItemEntity): Long

    suspend fun insertCalendarItem(item: CalendarItemEntity) {
        insertItem(item)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarItemEntity>) {
        events.forEach { insertItem(it) }
    }

    @Update
    suspend fun updateItem(item: CalendarItemEntity)

    suspend fun updateCalendarItem(item: CalendarItemEntity) = updateItem(item)

    @Delete
    suspend fun deleteItem(item: CalendarItemEntity)

    @Query("DELETE FROM calendar_items WHERE id = :id")
    suspend fun deleteById(id: Int)

    suspend fun deleteCalendarItem(id: String) {
        id.toIntOrNull()?.let { deleteById(it) }
    }

    @Query("DELETE FROM calendar_items WHERE itemType = 'EVENT' AND (endMillis < :nowMillis OR (endMillis IS NULL AND startMillis < :nowMillis))")
    suspend fun deletePastEvents(nowMillis: Long)
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): PreferenceEntity?

    @Query("SELECT value FROM preferences WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM preferences")
    fun getAllPreferencesFlow(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM preferences")
    suspend fun getAllPreferences(): List<PreferenceEntity>

    suspend fun getAll(): List<PreferenceEntity> = getAllPreferences()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: PreferenceEntity)

    suspend fun setValue(pref: PreferenceEntity) = insertPreference(pref)

    @Query("DELETE FROM preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)

    suspend fun deleteKey(key: String) = deletePreference(key)
}

@Dao
interface TransitCardDao {
    @Query("SELECT * FROM transit_cards ORDER BY customOrder ASC, lastUpdated DESC")
    suspend fun getAllCards(): List<TransitCardEntity>

    @Query("SELECT * FROM transit_cards ORDER BY customOrder ASC, lastUpdated DESC")
    fun getAllCardsFlow(): Flow<List<TransitCardEntity>>

    @Query("SELECT * FROM transit_cards WHERE cardNumber = :cardNumber LIMIT 1")
    suspend fun getCardByNumber(cardNumber: String): TransitCardEntity?

    suspend fun getCardByUid(uid: String): TransitCardEntity? = getCardByNumber(uid)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: TransitCardEntity)

    @Query("UPDATE transit_cards SET assignedName = :newName WHERE cardNumber = :cardNumber")
    suspend fun updateCardName(cardNumber: String, newName: String)

    @Query("UPDATE transit_cards SET showOnHome = :showOnHome WHERE cardNumber = :cardNumber")
    suspend fun updateCardHomeVisibility(cardNumber: String, showOnHome: Boolean)

    @Query("UPDATE transit_cards SET customOrder = :order WHERE cardNumber = :cardNumber")
    suspend fun updateCardOrder(cardNumber: String, order: Int)

    @Query("DELETE FROM transit_cards WHERE cardNumber = :cardNumber")
    suspend fun deleteCardByNumber(cardNumber: String)

    suspend fun deleteCard(cardNumber: String) = deleteCardByNumber(cardNumber)
}

@Dao
interface GeoportalStopDao {
    @Query("SELECT * FROM geoportal_stops")
    suspend fun getAllStops(): List<GeoportalStopEntity>

    @Query("SELECT * FROM geoportal_stops WHERE suprimida = 0 ORDER BY denominacion ASC")
    suspend fun getAllActiveStops(): List<GeoportalStopEntity>

    @Query("SELECT * FROM geoportal_stops WHERE denominacion LIKE '%' || :query || '%' OR id_parada LIKE '%' || :query || '%'")
    suspend fun searchActiveStops(query: String): List<GeoportalStopEntity>

    @Query("SELECT * FROM geoportal_stops WHERE id_parada = :id LIMIT 1")
    suspend fun getStopById(id: String): GeoportalStopEntity?

    @Query("SELECT COUNT(*) FROM geoportal_stops")
    suspend fun getStopCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<GeoportalStopEntity>)

    suspend fun insertStops(stops: List<GeoportalStopEntity>) = insertAll(stops)

    @Query("DELETE FROM geoportal_stops")
    suspend fun deleteAllStops()

    @Transaction
    suspend fun replaceAllStops(stops: List<GeoportalStopEntity>) {
        deleteAllStops()
        insertAll(stops)
    }
}

@Dao
interface CercaniasStationDao {
    @Query("SELECT * FROM cercanias_stations ORDER BY nombre ASC")
    fun getAllStationsFlow(): Flow<List<CercaniasStationEntity>>

    @Query("SELECT * FROM cercanias_stations ORDER BY nombre ASC")
    suspend fun getAllStations(): List<CercaniasStationEntity>

    @Query("SELECT * FROM cercanias_stations WHERE isFavorite = 1 ORDER BY nombre ASC")
    fun getFavoriteStationsFlow(): Flow<List<CercaniasStationEntity>>

    @Query("SELECT * FROM cercanias_stations WHERE isFavorite = 1 ORDER BY nombre ASC")
    suspend fun getFavoriteStations(): List<CercaniasStationEntity>

    @Query("SELECT * FROM cercanias_stations WHERE stop_id = :stopId LIMIT 1")
    suspend fun getStationById(stopId: String): CercaniasStationEntity?

    suspend fun getStationByCode(code: String): CercaniasStationEntity? = getStationById(code)

    @Query("SELECT COUNT(*) FROM cercanias_stations")
    suspend fun getStationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<CercaniasStationEntity>)

    suspend fun insertStations(stations: List<CercaniasStationEntity>) = insertAll(stations)

    @Query("DELETE FROM cercanias_stations")
    suspend fun deleteAllStations()

    @Transaction
    suspend fun replaceAllStations(stations: List<CercaniasStationEntity>) {
        deleteAllStations()
        insertAll(stations)
    }

    @Update
    suspend fun updateStation(station: CercaniasStationEntity)

    @Update
    suspend fun updateAll(stations: List<CercaniasStationEntity>)
}

@Dao
interface CercaniasScheduleDao {
    @Query("SELECT * FROM cercanias_schedules WHERE linea = :linea")
    suspend fun getSchedulesForLine(linea: String): List<CercaniasScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<CercaniasScheduleEntity>)

    @Query("DELETE FROM cercanias_schedules")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAllSchedules(schedules: List<CercaniasScheduleEntity>) {
        deleteAll()
        insertAll(schedules)
    }
}

@Dao
interface MetrobusStopDao {
    @Query("SELECT * FROM metrobus_stops")
    suspend fun getAllStops(): List<MetrobusStopEntity>

    @Query("SELECT * FROM metrobus_stops ORDER BY denominacion ASC")
    suspend fun getAllActiveStops(): List<MetrobusStopEntity>

    @Query("SELECT * FROM metrobus_stops WHERE denominacion LIKE '%' || :query || '%' OR municipio LIKE '%' || :query || '%' OR codigoParada LIKE '%' || :query || '%'")
    suspend fun searchActiveStops(query: String): List<MetrobusStopEntity>

    @Query("SELECT * FROM metrobus_stops WHERE id = :id LIMIT 1")
    suspend fun getStopById(id: String): MetrobusStopEntity?

    @Query("SELECT COUNT(*) FROM metrobus_stops")
    suspend fun getStopCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<MetrobusStopEntity>)

    suspend fun insertStops(stops: List<MetrobusStopEntity>) = insertAll(stops)

    @Query("UPDATE metrobus_stops SET lineas = :lineas WHERE id = :id")
    suspend fun updateLinesForStop(id: String, lineas: String)

    @Query("DELETE FROM metrobus_stops")
    suspend fun deleteAllStops()

    suspend fun deleteAll() = deleteAllStops()

    @Transaction
    suspend fun replaceAllStops(stops: List<MetrobusStopEntity>) {
        deleteAllStops()
        insertAll(stops)
    }
}

@Dao
interface ActiveTripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateActiveTrip(activeTripEntity: ActiveTripEntity)

    suspend fun insertTrip(trip: ActiveTripEntity) = insertOrUpdateActiveTrip(trip)

    @Query("SELECT * FROM active_trip WHERE tripId = :tripId AND status != 'CANCELLED' LIMIT 1")
    suspend fun getActiveTrip(tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID): ActiveTripEntity?

    @Query("SELECT * FROM active_trip WHERE status != 'CANCELLED' LIMIT 1")
    fun getActiveTripFlow(): Flow<ActiveTripEntity?>

    @Query("UPDATE active_trip SET status = :status, lastUpdatedTimestamp = :lastUpdatedTimestamp WHERE tripId = :tripId")
    suspend fun updateStatus(status: String, lastUpdatedTimestamp: Long = System.currentTimeMillis(), tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID)

    suspend fun updateTripStatus(tripId: String, status: String) {
        if (status == ActiveTripEntity.STATUS_CANCELLED) {
            deleteActiveTrip(tripId)
        } else {
            updateStatus(status = status, tripId = tripId)
        }
    }

    @Query("UPDATE active_trip SET currentLegIndex = :currentLegIndex, lastUpdatedTimestamp = :lastUpdatedTimestamp WHERE tripId = :tripId")
    suspend fun updateLegIndex(currentLegIndex: Int, lastUpdatedTimestamp: Long = System.currentTimeMillis(), tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID)

    @Query("DELETE FROM active_trip WHERE tripId = :tripId")
    suspend fun deleteActiveTrip(tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID)

    suspend fun clearActiveTrip(tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID) = deleteActiveTrip(tripId)
}

