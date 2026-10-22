package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE transit_cards ADD COLUMN customOrder INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE transit_cards ADD COLUMN showOnHome INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE transit_cards ADD COLUMN isManuallyInactive INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "valencia_transit.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
