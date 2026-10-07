package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItemEntity
import com.example.data.database.PreferenceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class DashboardRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val calendarDao = database.calendarDao()
    private val preferenceDao = database.preferenceDao()
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    @get:JvmName("getCalendarItemsPropertyFlow")
    val allCalendarItems: Flow<List<CalendarItemEntity>> = calendarDao.getAllItems()

    fun getAllCalendarItems(): Flow<List<CalendarItemEntity>> = allCalendarItems

    fun getPreferenceSync(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    suspend fun getPreference(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        preferenceDao.getPreference(key)?.value ?: getPreferenceSync(key, defaultValue)
    }

    suspend fun savePreference(key: String, value: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(key, value).apply()
        preferenceDao.insertPreference(PreferenceEntity(key, value))
    }

    fun savePreferenceSync(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getPreferenceFlow(key: String, defaultValue: String): Flow<String> = flow {
        emit(getPreference(key, defaultValue))
        val channel = Channel<String>(Channel.CONFLATED)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                channel.trySend(getPreferenceSync(key, defaultValue))
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            for (value in channel) {
                emit(value)
            }
        } finally {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    suspend fun insertCalendarItem(item: CalendarItemEntity): Long = withContext(Dispatchers.IO) {
        calendarDao.insertItem(item)
    }

    suspend fun updateCalendarItem(item: CalendarItemEntity) = withContext(Dispatchers.IO) {
        calendarDao.updateItem(item)
    }

    suspend fun deleteCalendarItem(item: CalendarItemEntity) = withContext(Dispatchers.IO) {
        calendarDao.deleteItem(item)
    }

    suspend fun deleteCalendarItemById(id: Int) = withContext(Dispatchers.IO) {
        calendarDao.deleteById(id)
    }

    suspend fun deletePastEvents(thresholdMillis: Long) = withContext(Dispatchers.IO) {
        calendarDao.deletePastEvents(thresholdMillis)
    }

    suspend fun ensureDefaultCalendarItems() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        calendarDao.deletePastEvents(now)
    }

    suspend fun loadDashboardData(): String = "Dashboard Loaded"
}
