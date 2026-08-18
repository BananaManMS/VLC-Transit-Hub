package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.TypeConverters

@Database(entities = [CalendarItemEntity::class, PreferenceEntity::class, StationEntity::class, TransitCardEntity::class, GeoportalStopEntity::class, CercaniasStationEntity::class, CercaniasScheduleEntity::class, MetrobusStopEntity::class, ActiveTripEntity::class], version = 15, exportSchema = false)
@TypeConverters(CercaniasTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun stationDao(): StationDao
    abstract fun transitCardDao(): TransitCardDao
    abstract fun geoportalStopDao(): GeoportalStopDao
    abstract fun cercaniasStationDao(): CercaniasStationDao
    abstract fun cercaniasScheduleDao(): CercaniasScheduleDao
    abstract fun metrobusStopDao(): MetrobusStopDao
    abstract fun activeTripDao(): ActiveTripDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vlc_transit_hub_db"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
