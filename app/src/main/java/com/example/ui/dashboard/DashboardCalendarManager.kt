package com.example.ui.dashboard

import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItemEntity
import com.example.data.repository.DashboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardCalendarManager(
    private val application: Application,
    private val database: AppDatabase,
    private val repository: DashboardRepository,
    private val scope: CoroutineScope
) {
    fun addEvent(
        title: String,
        description: String,
        startHoursOffset: Int,
        durationHours: Int,
        colorHex: String
    ) {
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val startMillis = now + (startHoursOffset * 3600000L)
            val endMillis = startMillis + (durationHours * 3600000L)
            val item = CalendarItemEntity(
                title = title,
                description = description,
                startMillis = startMillis,
                endMillis = endMillis,
                itemType = "EVENT",
                colorHex = colorHex
            )
            repository.insertCalendarItem(item)
        }
    }

    fun addTask(
        title: String,
        description: String,
        dueHoursOffset: Int,
        colorHex: String
    ) {
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val dueMillis = now + (dueHoursOffset * 3600000L)
            val item = CalendarItemEntity(
                title = title,
                description = description,
                dueMillis = dueMillis,
                itemType = "TASK",
                colorHex = colorHex
            )
            repository.insertCalendarItem(item)
        }
    }

    fun toggleTaskCompletion(item: CalendarItemEntity) {
        scope.launch(Dispatchers.IO) {
            val updated = item.copy(isCompleted = !item.isCompleted)
            repository.updateCalendarItem(updated)
        }
    }

    fun deleteItem(item: CalendarItemEntity) {
        scope.launch(Dispatchers.IO) {
            repository.deleteCalendarItem(item)
        }
    }

    fun syncGoogleCalendarEvents(force: Boolean = false) {
        if (ContextCompat.checkSelfPermission(application, android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val sevenDaysLater = now + (7 * 86400000L)
                val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(uriBuilder, now)
                ContentUris.appendId(uriBuilder, sevenDaysLater)

                val projection = arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY
                )

                val cursor = application.contentResolver.query(
                    uriBuilder.build(),
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )

                val syncedItems = mutableListOf<CalendarItemEntity>()
                cursor?.use { c ->
                    val idIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                    val descIdx = c.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                    val startIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
                    val allDayIdx = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)

                    while (c.moveToNext()) {
                        val eventId = if (idIdx >= 0) c.getLong(idIdx) else null
                        val title = (if (titleIdx >= 0) c.getString(titleIdx) else null) ?: "Evento"
                        val desc = (if (descIdx >= 0) c.getString(descIdx) else null) ?: ""
                        val start = if (startIdx >= 0) c.getLong(startIdx) else null
                        val end = if (endIdx >= 0) c.getLong(endIdx) else null
                        val allDay = if (allDayIdx >= 0) c.getInt(allDayIdx) == 1 else false

                        syncedItems.add(
                            CalendarItemEntity(
                                title = title,
                                description = desc,
                                startMillis = start,
                                endMillis = end,
                                isAllDay = allDay,
                                itemType = "EVENT",
                                colorHex = "#4285F4",
                                calendarEventId = eventId
                            )
                        )
                    }
                }

                if (syncedItems.isNotEmpty()) {
                    val existing = database.calendarDao().getAllItemsList()
                    val existingEventIds = existing.mapNotNull { it.calendarEventId }.toSet()
                    syncedItems.forEach { item ->
                        if (item.calendarEventId == null || !existingEventIds.contains(item.calendarEventId)) {
                            database.calendarDao().insertItem(item)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardCalendarManager", "Error syncing Google Calendar events", e)
            }
        }
    }
}
