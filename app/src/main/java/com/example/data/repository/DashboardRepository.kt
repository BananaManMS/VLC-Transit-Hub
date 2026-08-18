package com.example.data.repository

import com.example.data.database.CalendarDao
import com.example.data.database.CalendarItemEntity
import com.example.data.database.PreferenceDao
import com.example.data.database.PreferenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DashboardRepository(
    private val context: android.content.Context,
    private val database: com.example.data.database.AppDatabase
) {
    private val calendarDao = database.calendarDao()
    private val preferenceDao = database.preferenceDao()
    private val sharedPreferences = context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)

    val allCalendarItems: Flow<List<CalendarItemEntity>> = calendarDao.getAllItems()

    fun getPreferenceSync(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, null) ?: defaultValue
    }

    suspend fun insertCalendarItem(item: CalendarItemEntity): Long {
        val result = calendarDao.insertItem(item)
        com.example.data.database.DatabaseBackupManager.backupCalendarItems(context, database)
        return result
    }

    suspend fun updateCalendarItem(item: CalendarItemEntity) {
        calendarDao.updateItem(item)
        com.example.data.database.DatabaseBackupManager.backupCalendarItems(context, database)
    }

    suspend fun deleteCalendarItem(item: CalendarItemEntity) {
        calendarDao.deleteItem(item)
        com.example.data.database.DatabaseBackupManager.backupCalendarItems(context, database)
    }

    suspend fun deleteCalendarItemById(id: Int) {
        calendarDao.deleteById(id)
        com.example.data.database.DatabaseBackupManager.backupCalendarItems(context, database)
    }

    suspend fun deletePastEvents(nowMillis: Long) {
        calendarDao.deletePastEvents(nowMillis)
        com.example.data.database.DatabaseBackupManager.backupCalendarItems(context, database)
    }

    // Preferences helpers
    suspend fun getPreference(key: String, defaultValue: String): String {
        val cached = sharedPreferences.getString(key, null)
        if (cached != null) return cached
        val dbVal = preferenceDao.getPreference(key)?.value
        if (dbVal != null) {
            sharedPreferences.edit().putString(key, dbVal).apply()
            return dbVal
        }
        return defaultValue
    }

    suspend fun savePreference(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
        preferenceDao.insertPreference(PreferenceEntity(key, value))
        com.example.data.database.DatabaseBackupManager.backupPreferences(context, database)
    }

    fun getPreferenceFlow(key: String, defaultValue: String): Flow<String> {
        return preferenceDao.getAllPreferencesFlow().map { prefs ->
            val found = prefs.find { it.key == key }?.value
            if (found != null) {
                if (sharedPreferences.getString(key, null) != found) {
                    sharedPreferences.edit().putString(key, found).apply()
                }
                found
            } else {
                getPreferenceSync(key, defaultValue)
            }
        }
    }

    suspend fun ensureDefaultCalendarItems() {
        // Run query safely to check if empty
        val items = calendarDao.getAllItems().first()
        if (items.isEmpty()) {
            val now = System.currentTimeMillis()
            val hourInMillis = 3600_000L
            val dayInMillis = 24 * hourInMillis

            val defaults = listOf(
                CalendarItemEntity(
                    title = "Paella Lunch at Malvarrosa Beach",
                    description = "Traditional Valencian lunch by the beach with family.",
                    startMillis = now + 2 * hourInMillis,
                    endMillis = now + 4 * hourInMillis,
                    itemType = "EVENT",
                    colorHex = "#F97316" // Orange
                ),
                CalendarItemEntity(
                    title = "Arts & Sciences City Tour",
                    description = "Explore the museum and Hemisfèric. Best access via Alameda Station (L3, L5, L7, L9).",
                    startMillis = now + 22 * hourInMillis,
                    endMillis = now + 25 * hourInMillis,
                    itemType = "EVENT",
                    colorHex = "#3B82F6" // Blue
                ),
                CalendarItemEntity(
                    title = "Buy Valencia SUMA Card",
                    description = "Purchase a multi-modal transit ticket at Xàtiva station.",
                    dueMillis = now + 4 * hourInMillis,
                    itemType = "TASK",
                    isCompleted = false,
                    colorHex = "#10B981" // Emerald Green
                ),
                CalendarItemEntity(
                    title = "Check Metro Line 10 Schedule",
                    description = "Verify tram service frequency from Alacant station to Ruzafa.",
                    dueMillis = now - hourInMillis,
                    itemType = "TASK",
                    isCompleted = true,
                    colorHex = "#84CC16" // Lime Green
                )
            )

            defaults.forEach { calendarDao.insertItem(it) }
        }
    }
}
