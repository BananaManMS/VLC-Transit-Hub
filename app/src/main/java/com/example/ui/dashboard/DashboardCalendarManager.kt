package com.example.ui.dashboard

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItemEntity
import com.example.data.repository.DashboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DashboardCalendarManager(
    private val application: Application,
    private val database: AppDatabase,
    private val repository: DashboardRepository,
    private val scope: CoroutineScope
) {

    fun addEvent(title: String, description: String, startHoursOffset: Int, durationHours: Int, colorHex: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            val hourInMillis = 3600_000L
            val item = CalendarItemEntity(
                title = title,
                description = description,
                startMillis = now + (startHoursOffset * hourInMillis),
                endMillis = now + ((startHoursOffset + durationHours) * hourInMillis),
                itemType = "EVENT",
                colorHex = colorHex
            )
            repository.insertCalendarItem(item)
        }
    }

    fun addTask(title: String, description: String, dueHoursOffset: Int, colorHex: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            val hourInMillis = 3600_000L
            val item = CalendarItemEntity(
                title = title,
                description = description,
                dueMillis = now + (dueHoursOffset * hourInMillis),
                itemType = "TASK",
                isCompleted = false,
                colorHex = colorHex
            )
            repository.insertCalendarItem(item)
        }
    }

    fun toggleTaskCompletion(item: CalendarItemEntity) {
        scope.launch {
            val updated = item.copy(isCompleted = !item.isCompleted)
            repository.updateCalendarItem(updated)
        }
    }

    fun deleteItem(item: CalendarItemEntity) {
        scope.launch {
            repository.deleteCalendarItem(item)
            if (item.calendarEventId.isNotBlank() && item.calendarEventId != "0") {
                try {
                    val now = System.currentTimeMillis()
                    val expiry = item.endMillis
                    val rawDeleted = repository.getPreference("deleted_google_event_ids", "")
                    val list = if (rawDeleted.isBlank()) mutableListOf() else rawDeleted.split(";").toMutableList()
                    list.add("${item.calendarEventId}:$expiry")
                    repository.savePreference("deleted_google_event_ids", list.joinToString(";"))
                } catch (e: Exception) {
                    Log.e("DashboardCalendarManager", "Error saving deleted event preference", e)
                }
            }
        }
    }

    fun syncGoogleCalendarEvents(force: Boolean = false) {
        if (ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        scope.launch {
            try {
                val now = System.currentTimeMillis()

                // 1. Automatically purge past events from Room database
                repository.deletePastEvents(now)

                // 2. Throttling check (minimum 15 minutes unless force == true)
                val lastSyncStr = repository.getPreference("last_gcal_sync_time", "0")
                val lastSyncTime = lastSyncStr.toLongOrNull() ?: 0L
                if (!force && (now - lastSyncTime < 15 * 60 * 1000L)) {
                    return@launch
                }
                repository.savePreference("last_gcal_sync_time", now.toString())

                // 3. Process and prune deleted Google Calendar Event IDs
                val rawDeleted = repository.getPreference("deleted_google_event_ids", "")
                val activeDeletedSet = mutableSetOf<Long>()
                val validDeletedEntries = mutableListOf<String>()

                if (rawDeleted.isNotBlank()) {
                    rawDeleted.split(";").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            val eventId = parts[0].toLongOrNull()
                            val expiry = parts[1].toLongOrNull()
                            if (eventId != null && expiry != null) {
                                if (expiry >= now) {
                                    activeDeletedSet.add(eventId)
                                    validDeletedEntries.add(entry)
                                }
                            }
                        }
                    }
                    repository.savePreference("deleted_google_event_ids", validDeletedEntries.joinToString(";"))
                }

                val contentResolver = application.contentResolver
                val uri = CalendarContract.Events.CONTENT_URI
                val sevenDaysLater = now + 7 * 24 * 3600 * 1000L

                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Events.ALL_DAY
                )

                val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.DELETED} = 0)"
                val selectionArgs = arrayOf(now.toString(), sevenDaysLater.toString())

                val cursor = contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC"
                )

                cursor?.use { c ->
                    val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                    val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                    val calNameIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                    val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)

                    val newEvents = mutableListOf<CalendarItemEntity>()
                    while (c.moveToNext()) {
                        val eventIdLong = if (idIdx >= 0) c.getLong(idIdx) else 0L

                        if (eventIdLong != 0L && activeDeletedSet.contains(eventIdLong)) {
                            continue
                        }

                        val title = if (titleIdx >= 0) c.getString(titleIdx) ?: "Untitled Event" else "Untitled Event"
                        val desc = if (descIdx >= 0) c.getString(descIdx) ?: "" else ""
                        val start = if (startIdx >= 0) c.getLong(startIdx) else now
                        val end = if (endIdx >= 0) c.getLong(endIdx) else start
                        val calendarName = if (calNameIdx >= 0) c.getString(calNameIdx) ?: "" else ""
                        val allDayVal = if (allDayIdx >= 0) c.getInt(allDayIdx) else 0

                        val isAllDayEvent = (allDayVal == 1) || (start != 0L && end != 0L && (end == start || (end - start) % 86400000L == 0L))

                        val calNameLower = calendarName.lowercase()
                        val titleLower = title.lowercase()

                        val isHolidayCalendar = calNameLower.contains("festiv") ||
                                                calNameLower.contains("holiday") ||
                                                calNameLower.contains("festu") ||
                                                calNameLower.contains("festes")

                        val isPublicHoliday = titleLower.contains("año nuevo") ||
                                              titleLower.contains("any nou") ||
                                              titleLower.contains("reyes") ||
                                              titleLower.contains("reigs") ||
                                              titleLower.contains("viernes santo") ||
                                              titleLower.contains("divendres sant") ||
                                              titleLower.contains("lunes de pascua") ||
                                              titleLower.contains("dilluns de pasqua") ||
                                              titleLower.contains("fiesta del trabajo") ||
                                              titleLower.contains("dia del treball") ||
                                              titleLower.contains("asunción") ||
                                              titleLower.contains("assumpció") ||
                                              titleLower.contains("fiesta nacional") ||
                                              titleLower.contains("todos los santos") ||
                                              titleLower.contains("tots sants") ||
                                              titleLower.contains("constitución") ||
                                              titleLower.contains("inmaculada") ||
                                              titleLower.contains("navidad") ||
                                              titleLower.contains("nadal") ||
                                              titleLower.contains("san vicente") ||
                                              titleLower.contains("sant vicent") ||
                                              titleLower.contains("9 d'octubre")

                        if (isHolidayCalendar || isPublicHoliday) {
                            continue
                        }

                        newEvents.add(
                            CalendarItemEntity(
                                title = title,
                                description = desc,
                                startMillis = start,
                                endMillis = if (isAllDayEvent) start else end,
                                itemType = "EVENT",
                                colorHex = "#0288D1",
                                calendarEventId = eventIdLong.toString(),
                                isAllDay = isAllDayEvent
                            )
                        )
                    }

                    val existingItems = database.calendarDao().getAllItemsList().toMutableList()
                    newEvents.forEach { event ->
                        val exists = existingItems.any {
                            (event.calendarEventId.isNotBlank() && event.calendarEventId != "0" && it.calendarEventId == event.calendarEventId) || 
                            (it.title.trim().lowercase() == event.title.trim().lowercase() && it.startMillis == event.startMillis && it.endMillis == event.endMillis)
                        }
                        val eventIdNum = event.calendarEventId.toLongOrNull() ?: -1L
                        if (!exists && (event.calendarEventId.isBlank() || !activeDeletedSet.contains(eventIdNum))) {
                            repository.insertCalendarItem(event)
                            existingItems.add(event)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardCalendarManager", "Error syncing calendar events", e)
            }
        }
    }
}
