package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "calendar_items")
data class CalendarItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val dueMillis: Long? = null,
    val isCompleted: Boolean = false,
    val itemType: String, // "EVENT" or "TASK"
    val colorHex: String = "#3B82F6",
    val calendarEventId: Long? = null,
    val isAllDay: Boolean = false
)

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val lines: String, // Comma-separated line IDs
    val zone: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY name ASC")
    suspend fun getAllStations(): List<StationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int

    @Query("DELETE FROM stations")
    suspend fun deleteAllStations()
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_items ORDER BY COALESCE(startMillis, dueMillis) ASC")
    fun getAllItems(): Flow<List<CalendarItemEntity>>

    @Query("SELECT * FROM calendar_items")
    suspend fun getAllItemsList(): List<CalendarItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CalendarItemEntity): Long

    @Update
    suspend fun updateItem(item: CalendarItemEntity)

    @Delete
    suspend fun deleteItem(item: CalendarItemEntity)

    @Query("DELETE FROM calendar_items WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM calendar_items WHERE itemType = 'EVENT' AND COALESCE(endMillis, startMillis, 0) < :nowMillis")
    suspend fun deletePastEvents(nowMillis: Long)
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): PreferenceEntity?

    @Query("SELECT * FROM preferences")
    fun getAllPreferencesFlow(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM preferences")
    suspend fun getAllPreferences(): List<PreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: PreferenceEntity)
}

@Entity(tableName = "transit_cards")
data class TransitCardEntity(
    @PrimaryKey val cardNumber: String, // 12-digit number
    val assignedName: String, // Name assigned by user, or default name
    val defaultName: String, // Default name from API (e.g. Móbilis, SUMA)
    val cardType: String, // "viajes", "saldo", "mensual"
    val remainingValue: String, // Trips remaining, balance remaining, or renewal date
    val detailsJson: String, // Complete JSON string from the API for the detail view
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface TransitCardDao {
    @Query("SELECT * FROM transit_cards ORDER BY lastUpdated DESC")
    fun getAllCardsFlow(): Flow<List<TransitCardEntity>>

    @Query("SELECT * FROM transit_cards ORDER BY lastUpdated DESC")
    suspend fun getAllCards(): List<TransitCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: TransitCardEntity)

    @Query("DELETE FROM transit_cards WHERE cardNumber = :cardNumber")
    suspend fun deleteCardByNumber(cardNumber: String)

    @Query("SELECT * FROM transit_cards WHERE cardNumber = :cardNumber LIMIT 1")
    suspend fun getCardByNumber(cardNumber: String): TransitCardEntity?
}

@Entity(tableName = "geoportal_stops")
data class GeoportalStopEntity(
    @PrimaryKey val id_parada: String,
    val denominacion: String,
    val suprimida: Int,
    val lat: Double,
    val lon: Double,
    val lineas: String? = null
)

@Dao
interface GeoportalStopDao {
    @Query("SELECT * FROM geoportal_stops WHERE suprimida = 0 ORDER BY denominacion ASC")
    suspend fun getAllActiveStops(): List<GeoportalStopEntity>

    @Query("SELECT * FROM geoportal_stops WHERE suprimida = 0 AND (id_parada LIKE :query OR denominacion LIKE :query) ORDER BY denominacion ASC")
    suspend fun searchActiveStops(query: String): List<GeoportalStopEntity>

    @Query("SELECT * FROM geoportal_stops WHERE id_parada = :id LIMIT 1")
    suspend fun getStopById(id: String): GeoportalStopEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<GeoportalStopEntity>)

    @Query("SELECT COUNT(*) FROM geoportal_stops")
    suspend fun getStopCount(): Int
}

data class RenfeScheduleItem(
    val linea: String = "",
    val trip_ids: List<String> = emptyList(),
    val llegada: String = ""
)

class CercaniasTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val array = org.json.JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrEmpty()) return emptyList()
        return try {
            val array = org.json.JSONArray(data)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromScheduleItemList(list: List<RenfeScheduleItem>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val array = org.json.JSONArray()
        list.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("linea", item.linea)
            val tripsArray = org.json.JSONArray()
            item.trip_ids.forEach { tripsArray.put(it) }
            obj.put("trip_ids", tripsArray)
            obj.put("llegada", item.llegada)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toScheduleItemList(data: String?): List<RenfeScheduleItem> {
        if (data.isNullOrEmpty()) return emptyList()
        return try {
            val array = org.json.JSONArray(data)
            val list = mutableListOf<RenfeScheduleItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val linea = obj.optString("linea", "")
                val llegada = obj.optString("llegada", "")
                val tripsArr = obj.optJSONArray("trip_ids")
                val tripIds = mutableListOf<String>()
                if (tripsArr != null) {
                    for (j in 0 until tripsArr.length()) {
                        tripIds.add(tripsArr.getString(j))
                    }
                }
                list.add(RenfeScheduleItem(linea = linea, trip_ids = tripIds, llegada = llegada))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Entity(tableName = "cercanias_stations")
@TypeConverters(CercaniasTypeConverters::class)
data class CercaniasStationEntity(
    @PrimaryKey val stop_id: String,
    val nombre: String,
    val lat: Double,
    val lon: Double,
    val lineas: List<String> = emptyList(),
    val horarios: List<RenfeScheduleItem> = emptyList(),
    val isFavorite: Boolean = false
) {
    @Ignore
    val id: String = stop_id
    @Ignore
    val codigo: String = stop_id
    @Ignore
    val latitud: Double = lat
    @Ignore
    val longitud: Double = lon
    @Ignore
    val lines: String = lineas.joinToString(",")
    
    val displayName: String
        get() = when (nombre) {
            "Valencia-Estacio del Nord" -> "Valencia Nord"
            "Valencia-La Font de Sant Lluis" -> "Valencia F. S. Lluís"
            "València-Cabanyal" -> "Cabanyal"
            "València Sant Isidre" -> "Valencia St. Isidre"
            else -> nombre
        }
}

@Dao
interface CercaniasStationDao {
    @Query("SELECT * FROM cercanias_stations ORDER BY nombre ASC")
    fun getAllStationsFlow(): Flow<List<CercaniasStationEntity>>

    @Query("SELECT * FROM cercanias_stations ORDER BY nombre ASC")
    suspend fun getAllStations(): List<CercaniasStationEntity>

    @Query("SELECT * FROM cercanias_stations WHERE stop_id = :stopId LIMIT 1")
    suspend fun getStationById(stopId: String): CercaniasStationEntity?

    @Query("SELECT * FROM cercanias_stations WHERE isFavorite = 1")
    fun getFavoriteStationsFlow(): Flow<List<CercaniasStationEntity>>

    @Query("SELECT * FROM cercanias_stations WHERE isFavorite = 1")
    suspend fun getFavoriteStations(): List<CercaniasStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<CercaniasStationEntity>)

    @Query("SELECT COUNT(*) FROM cercanias_stations")
    suspend fun getStationCount(): Int

    @Query("DELETE FROM cercanias_stations")
    suspend fun deleteAllStations()

    @Update
    suspend fun updateStation(station: CercaniasStationEntity)

    @Update
    suspend fun updateAll(stations: List<CercaniasStationEntity>)
}

@Entity(tableName = "cercanias_schedules")
data class CercaniasScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stopId: String,
    val line: String,
    val destination: String,
    val time: String,
    val days: String
)

@Dao
interface CercaniasScheduleDao {
    @Query("SELECT * FROM cercanias_schedules WHERE stopId = :stopId")
    suspend fun getSchedulesForStop(stopId: String): List<CercaniasScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<CercaniasScheduleEntity>)

    @Query("SELECT COUNT(*) FROM cercanias_schedules")
    suspend fun getScheduleCount(): Int

    @Query("DELETE FROM cercanias_schedules")
    suspend fun deleteAll()
}

@Entity(tableName = "metrobus_stops")
data class MetrobusStopEntity(
    @PrimaryKey val id_parada: String,
    val denominacion: String,
    val lat: Double,
    val lon: Double,
    val lineas: String? = null,
    val suprimida: Int = 0
)

@Dao
interface MetrobusStopDao {
    @Query("SELECT * FROM metrobus_stops WHERE suprimida = 0 ORDER BY denominacion ASC")
    suspend fun getAllActiveStops(): List<MetrobusStopEntity>

    @Query("SELECT * FROM metrobus_stops WHERE suprimida = 0 AND (id_parada LIKE :query OR denominacion LIKE :query) ORDER BY denominacion ASC")
    suspend fun searchActiveStops(query: String): List<MetrobusStopEntity>

    @Query("SELECT * FROM metrobus_stops WHERE id_parada = :id LIMIT 1")
    suspend fun getStopById(id: String): MetrobusStopEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<MetrobusStopEntity>)

    @Query("SELECT COUNT(*) FROM metrobus_stops")
    suspend fun getStopCount(): Int

    @Query("UPDATE metrobus_stops SET lineas = :lineas WHERE id_parada = :id")
    suspend fun updateLinesForStop(id: String, lineas: String)

    @Query("DELETE FROM metrobus_stops")
    suspend fun deleteAll()
}

@Entity(tableName = "active_trip")
data class ActiveTripEntity(
    @PrimaryKey val tripId: String = ACTIVE_TRIP_ID,
    val originName: String,
    val destinationName: String,
    val routeDataJson: String,
    val status: String, // "PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"
    val currentLegIndex: Int = 0,
    val lastLegScheduledArrivalTimeMillis: Long,
    val startTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val ACTIVE_TRIP_ID = "ACTIVE_TRIP"
        const val STATUS_PLANNED = "PLANNED"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val EXPIRATION_GRACE_PERIOD_MILLIS = 1_800_000L // 30 minutes
    }
}

@Dao
interface ActiveTripDao {
    @Query("SELECT * FROM active_trip WHERE tripId = :tripId LIMIT 1")
    fun getActiveTripFlow(tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID): Flow<ActiveTripEntity?>

    @Query("SELECT * FROM active_trip WHERE tripId = :tripId LIMIT 1")
    suspend fun getActiveTrip(tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID): ActiveTripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateActiveTrip(trip: ActiveTripEntity)

    @Query("DELETE FROM active_trip WHERE tripId = :tripId")
    suspend fun deleteActiveTrip(tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID)

    @Query("UPDATE active_trip SET currentLegIndex = :legIndex, lastUpdatedTimestamp = :updatedAt WHERE tripId = :tripId")
    suspend fun updateLegIndex(legIndex: Int, updatedAt: Long = System.currentTimeMillis(), tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID)

    @Query("UPDATE active_trip SET status = :status, lastUpdatedTimestamp = :updatedAt WHERE tripId = :tripId")
    suspend fun updateStatus(status: String, updatedAt: Long = System.currentTimeMillis(), tripId: String = ActiveTripEntity.ACTIVE_TRIP_ID)
}


