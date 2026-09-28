package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        StationEntity::class,
        CercaniasStationEntity::class,
        CercaniasScheduleEntity::class,
        GeoportalStopEntity::class,
        MetrobusStopEntity::class,
        TransitCardEntity::class,
        ActiveTripEntity::class,
        CalendarItemEntity::class,
        PreferenceEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(CercaniasTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun cercaniasStationDao(): CercaniasStationDao
    abstract fun cercaniasScheduleDao(): CercaniasScheduleDao
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
                ).fallbackToDestructiveMigration().fallbackToDestructiveMigrationOnDowngrade().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
