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
import android.content.Context
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
    private val dismissedPrefs = application.getSharedPreferences("calendar_dismissed_events_cache", Context.MODE_PRIVATE)

    private fun markEventAsDismissed(item: CalendarItemEntity) {
        val now = System.currentTimeMillis()
        val defaultDuration = 3600000L * 24 // 24 hours minimum retention
        val expire = maxOf(
            item.endMillis ?: 0L,
            (item.startMillis ?: 0L) + 3600000L,
            now + defaultDuration
        )
        val editor = dismissedPrefs.edit()
        item.calendarEventId?.let { eventId ->
            editor.putLong("event_$eventId", expire)
            item.startMillis?.let { start ->
                editor.putLong("instance_${eventId}_$start", expire)
            }
        }
        if (item.title.isNotBlank()) {
            val sig = "sig_${item.title.trim().lowercase().hashCode()}_${item.startMillis ?: 0L}"
            editor.putLong(sig, expire)
        }
        editor.apply()
    }

    private fun cleanExpiredDismissedEvents(currentNow: Long) {
        try {
            val all = dismissedPrefs.all
            val toRemove = mutableListOf<String>()
            all.forEach { (key, value) ->
                val expireTime = (value as? Long) ?: (value as? Number)?.toLong() ?: 0L
                if (expireTime < currentNow) {
                    toRemove.add(key)
                }
            }
            if (toRemove.isNotEmpty()) {
                val editor = dismissedPrefs.edit()
                toRemove.forEach { editor.remove(it) }
                editor.apply()
            }
        } catch (e: Exception) {
            Log.e("DashboardCalendarManager", "Error cleaning expired dismissed events", e)
        }
    }

    private fun isEventDismissed(calendarEventId: Long?, startMillis: Long?, title: String, currentNow: Long): Boolean {
        if (calendarEventId != null) {
            val eventKey = "event_$calendarEventId"
            val expire = dismissedPrefs.getLong(eventKey, 0L)
            if (expire >= currentNow) return true

            if (startMillis != null) {
                val instanceKey = "instance_${calendarEventId}_$startMillis"
                val instanceExpire = dismissedPrefs.getLong(instanceKey, 0L)
                if (instanceExpire >= currentNow) return true
            }
        }
        if (title.isNotBlank()) {
            val sig = "sig_${title.trim().lowercase().hashCode()}_${startMillis ?: 0L}"
            val sigExpire = dismissedPrefs.getLong(sig, 0L)
            if (sigExpire >= currentNow) return true
        }
        return false
    }

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
            markEventAsDismissed(item)
            repository.deleteCalendarItem(item)
        }
    }

    fun syncGoogleCalendarEvents(force: Boolean = false) {
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                database.calendarDao().deletePastEvents(now)
            } catch (e: Exception) {
                Log.e("DashboardCalendarManager", "Error deleting past events", e)
            }
        }

        if (ContextCompat.checkSelfPermission(application, android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val currentNow = System.currentTimeMillis()
                database.calendarDao().deletePastEvents(currentNow)
                cleanExpiredDismissedEvents(currentNow)

                val sevenDaysLater = currentNow + (7 * 86400000L)
                val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(uriBuilder, currentNow)
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

                        // Check if finished or dismissed
                        val isFinished = (end != null && end < currentNow) || (end == null && start != null && start < currentNow - 1800000L)
                        val isDismissed = isEventDismissed(eventId, start, title, currentNow)

                        if (!isFinished && !isDismissed) {
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
                }

                val existing = database.calendarDao().getAllItemsList()

                // Purge any existing database item that has been dismissed by user
                existing.forEach { item ->
                    if (isEventDismissed(item.calendarEventId, item.startMillis, item.title, currentNow)) {
                        database.calendarDao().deleteItem(item)
                    }
                }

                val currentSyncedEvents = database.calendarDao().getAllItemsList().filter { it.calendarEventId != null }
                val newSyncedEventIds = syncedItems.mapNotNull { it.calendarEventId }.toSet()

                // Remove previous synced events that no longer exist or have passed
                currentSyncedEvents.forEach { item ->
                    val eventId = item.calendarEventId
                    if (eventId != null && (!newSyncedEventIds.contains(eventId) || (item.endMillis != null && item.endMillis < currentNow))) {
                        database.calendarDao().deleteItem(item)
                    }
                }

                // Insert or update active upcoming events
                syncedItems.forEach { item ->
                    val existingItem = existing.find { it.calendarEventId == item.calendarEventId }
                    if (existingItem != null) {
                        database.calendarDao().updateItem(item.copy(id = existingItem.id))
                    } else {
                        database.calendarDao().insertItem(item)
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardCalendarManager", "Error syncing Google Calendar events", e)
            }
        }
    }
}
