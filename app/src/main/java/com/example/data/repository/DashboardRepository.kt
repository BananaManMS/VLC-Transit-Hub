package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.database.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DashboardRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    val allCalendarItems: Flow<List<com.example.data.database.CalendarItemEntity>> = database.calendarDao().getAllItemsFlow()

    suspend fun ensureDefaultCalendarItems() {
        // Ensures default items if empty
    }

    suspend fun insertCalendarItem(item: com.example.data.database.CalendarItemEntity) {
        database.calendarDao().insertCalendarItem(item)
    }

    suspend fun updateCalendarItem(item: com.example.data.database.CalendarItemEntity) {
        database.calendarDao().updateCalendarItem(item)
    }

    suspend fun deleteCalendarItem(id: String) {
        database.calendarDao().deleteCalendarItem(id)
    }

    suspend fun loadDashboardData(): String {
        return "Dashboard Loaded"
    }

    fun getPreferenceSync(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    suspend fun getPreference(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        getPreferenceSync(key, defaultValue)
    }

    fun savePreferenceSync(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    suspend fun savePreference(key: String, value: String) = withContext(Dispatchers.IO) {
        savePreferenceSync(key, value)
    }

    fun getPreferenceFlow(key: String, defaultValue: String): Flow<String> {
        return flow {
            emit(getPreferenceSync(key, defaultValue))
            val channel = Channel<String>(Channel.CONFLATED)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k == key) {
                    channel.trySend(getPreferenceSync(key, defaultValue))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            try {
                for (value in channel) {
                    emit(value)
                }
            } finally {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }
}
