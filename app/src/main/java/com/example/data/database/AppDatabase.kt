package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface StationDao {
    @Query("SELECT * FROM stations")
    suspend fun getAllStations(): List<StationEntity>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>)
}

@Dao
interface CercaniasStationDao {
    @Query("SELECT * FROM cercanias_stations")
    suspend fun getAllStations(): List<CercaniasStationEntity>

    @Query("SELECT * FROM cercanias_stations")
    fun getAllStationsFlow(): kotlinx.coroutines.flow.Flow<List<CercaniasStationEntity>>

    @Query("SELECT * FROM cercanias_stations WHERE stop_id = :code LIMIT 1")
    suspend fun getStationByCode(code: String): CercaniasStationEntity?

    @Query("SELECT COUNT(*) FROM cercanias_stations")
    suspend fun getStationCount(): Int

    @Query("DELETE FROM cercanias_stations")
    suspend fun deleteAllStations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<CercaniasStationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllStations(stations: List<CercaniasStationEntity>)
}

@Dao
interface GeoportalStopDao {
    @Query("SELECT * FROM geoportal_stops")
    suspend fun getAllStops(): List<GeoportalStopEntity>

    @Query("SELECT * FROM geoportal_stops WHERE suprimida = 0")
    suspend fun getAllActiveStops(): List<GeoportalStopEntity>

    @Query("SELECT * FROM geoportal_stops WHERE id_parada = :id LIMIT 1")
    suspend fun getStopById(id: String): GeoportalStopEntity?

    @Query("SELECT COUNT(*) FROM geoportal_stops")
    suspend fun getStopCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<GeoportalStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<GeoportalStopEntity>)

    @Query("DELETE FROM geoportal_stops")
    suspend fun deleteAllStops()

    @androidx.room.Transaction
    suspend fun replaceAllStops(stops: List<GeoportalStopEntity>) {
        deleteAllStops()
        insertAll(stops)
    }
}

@Dao
interface MetrobusStopDao {
    @Query("SELECT * FROM metrobus_stops")
    suspend fun getAllStops(): List<MetrobusStopEntity>

    @Query("SELECT * FROM metrobus_stops WHERE suprimida = 0")
    suspend fun getAllActiveStops(): List<MetrobusStopEntity>

    @Query("SELECT * FROM metrobus_stops WHERE id_parada = :id LIMIT 1")
    suspend fun getStopById(id: String): MetrobusStopEntity?

    @Query("SELECT COUNT(*) FROM metrobus_stops")
    suspend fun getStopCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<MetrobusStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<MetrobusStopEntity>)

    @Query("DELETE FROM metrobus_stops")
    suspend fun deleteAllStops()

    @androidx.room.Transaction
    suspend fun replaceAllStops(stops: List<MetrobusStopEntity>) {
        deleteAllStops()
        insertAll(stops)
    }
}

@Dao
interface PreferenceDao {
    @Query("SELECT value FROM user_preferences WHERE key = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM user_preferences WHERE key = :key LIMIT 1")
    suspend fun getPreference(key: String): UserPreferenceEntity?

    @Query("SELECT * FROM user_preferences")
    suspend fun getAll(): List<UserPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(pref: UserPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(pref: UserPreferenceEntity)

    @Query("DELETE FROM user_preferences WHERE key = :key")
    suspend fun deleteKey(key: String)
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_items")
    suspend fun getAllEvents(): List<CalendarItemEntity>

    @Query("SELECT * FROM calendar_items")
    suspend fun getAllItemsList(): List<CalendarItemEntity>

    @Query("SELECT * FROM calendar_items")
    fun getAllItemsFlow(): kotlinx.coroutines.flow.Flow<List<CalendarItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarItem(item: CalendarItemEntity)

    @androidx.room.Update
    suspend fun updateCalendarItem(item: CalendarItemEntity)

    @Query("DELETE FROM calendar_items WHERE eventId = :id")
    suspend fun deleteCalendarItem(id: String)

    @Query("DELETE FROM calendar_items WHERE endMillis < :now")
    suspend fun deletePastEvents(now: Long)
}

@Dao
interface ActiveTripDao {
    @Query("SELECT * FROM active_trips WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTrip(): ActiveTripEntity?

    @Query("SELECT * FROM active_trips WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveTripFlow(): kotlinx.coroutines.flow.Flow<ActiveTripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: ActiveTripEntity)

    @Query("UPDATE active_trips SET status = :status WHERE tripId = :tripId")
    suspend fun updateTripStatus(tripId: String, status: String)
}

@Dao
interface TransitCardDao {
    @Query("SELECT * FROM transit_cards")
    suspend fun getAllCards(): List<TransitCardEntity>

    @Query("SELECT * FROM transit_cards")
    fun getAllCardsFlow(): kotlinx.coroutines.flow.Flow<List<TransitCardEntity>>

    @Query("SELECT * FROM transit_cards WHERE cardUid = :uid LIMIT 1")
    suspend fun getCardByUid(uid: String): TransitCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: TransitCardEntity)

    @Query("UPDATE transit_cards SET alias = :newName WHERE cardUid = :cardNumber")
    suspend fun updateCardName(cardNumber: String, newName: String)

    @Query("DELETE FROM transit_cards WHERE cardUid = :cardNumber")
    suspend fun deleteCard(cardNumber: String)
}

@Database(
    entities = [
        StationEntity::class,
        CercaniasStationEntity::class,
        GeoportalStopEntity::class,
        MetrobusStopEntity::class,
        RenfeScheduleItem::class,
        TransitCardEntity::class,
        ActiveTripEntity::class,
        CalendarItemEntity::class,
        UserPreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun cercaniasStationDao(): CercaniasStationDao
    abstract fun geoportalStopDao(): GeoportalStopDao
    abstract fun metrobusStopDao(): MetrobusStopDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun calendarDao(): CalendarDao
    abstract fun activeTripDao(): ActiveTripDao
    abstract fun transitCardDao(): TransitCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "valencia_transit.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
