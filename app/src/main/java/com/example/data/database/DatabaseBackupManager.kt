package com.example.data.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object DatabaseBackupManager {
    private const val TAG = "DatabaseBackup"
    private val hasRestored = java.util.concurrent.atomic.AtomicBoolean(false)
    private const val PREFS_NAME = "vlc_transit_backup_prefs"
    private const val KEY_PREFERENCES_BACKUP = "preferences_backup"
    private const val KEY_CARDS_BACKUP = "cards_backup"
    private const val KEY_CALENDAR_BACKUP = "calendar_backup"

    private const val FILE_PREFERENCES_BACKUP = "preferences_backup.json"
    private const val FILE_CARDS_BACKUP = "cards_backup.json"
    private const val FILE_CALENDAR_BACKUP = "calendar_backup.json"

    private fun writeAtomicBackupFile(context: Context, fileName: String, content: String) {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        val targetFile = File(backupDir, fileName)
        val tempFile = File(backupDir, "$fileName.tmp")

        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                Log.d(TAG, "Atomic file backup written successfully: $fileName")
            } else {
                Log.e(TAG, "Failed atomic write for $fileName: temp file is empty or missing.")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing atomic backup write for $fileName: ${e.message}", e)
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun readBackupFile(context: Context, fileName: String): String? {
        return try {
            val backupDir = File(context.filesDir, "backups")
            val targetFile = File(backupDir, fileName)
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile.readText(Charsets.UTF_8)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading backup file $fileName: ${e.message}")
            null
        }
    }

    suspend fun backupPreferences(context: Context, database: AppDatabase) {
        withContext(Dispatchers.IO) {
            try {
                val list = database.preferenceDao().getAllPreferences()
                val array = JSONArray()
                for (pref in list) {
                    val obj = JSONObject()
                    obj.put("key", pref.key)
                    obj.put("value", pref.value)
                    array.put(obj)
                }
                val jsonString = array.toString()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PREFERENCES_BACKUP, jsonString)
                    .apply()

                writeAtomicBackupFile(context, FILE_PREFERENCES_BACKUP, jsonString)
                Log.d(TAG, "Preferences backed up successfully. Count: ${list.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up preferences: ${e.message}", e)
            }
        }
    }

    suspend fun backupTransitCards(context: Context, database: AppDatabase) {
        withContext(Dispatchers.IO) {
            try {
                val list = database.transitCardDao().getAllCards()
                val array = JSONArray()
                for (card in list) {
                    val obj = JSONObject()
                    obj.put("cardNumber", card.cardNumber)
                    obj.put("assignedName", card.assignedName)
                    obj.put("defaultName", card.defaultName)
                    obj.put("cardType", card.cardType)
                    obj.put("remainingValue", card.remainingValue)
                    obj.put("detailsJson", card.detailsJson)
                    obj.put("lastUpdated", card.lastUpdated)
                    array.put(obj)
                }
                val jsonString = array.toString()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CARDS_BACKUP, jsonString)
                    .apply()

                writeAtomicBackupFile(context, FILE_CARDS_BACKUP, jsonString)
                Log.d(TAG, "Transit cards backed up successfully. Count: ${list.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up transit cards: ${e.message}", e)
            }
        }
    }

    suspend fun backupCalendarItems(context: Context, database: AppDatabase) {
        withContext(Dispatchers.IO) {
            try {
                val list = database.calendarDao().getAllItemsList()
                val array = JSONArray()
                for (item in list) {
                    val obj = JSONObject()
                    obj.put("id", item.id)
                    obj.put("title", item.title)
                    obj.put("description", item.description)
                    if (item.startMillis != null) obj.put("startMillis", item.startMillis)
                    if (item.endMillis != null) obj.put("endMillis", item.endMillis)
                    if (item.dueMillis != null) obj.put("dueMillis", item.dueMillis)
                    obj.put("isCompleted", item.isCompleted)
                    obj.put("itemType", item.itemType)
                    obj.put("colorHex", item.colorHex)
                    array.put(obj)
                }
                val jsonString = array.toString()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CALENDAR_BACKUP, jsonString)
                    .apply()

                writeAtomicBackupFile(context, FILE_CALENDAR_BACKUP, jsonString)
                Log.d(TAG, "Calendar items backed up successfully. Count: ${list.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up calendar items: ${e.message}", e)
            }
        }
    }

    suspend fun restoreIfNeeded(context: Context, database: AppDatabase) {
        if (hasRestored.getAndSet(true)) {
            Log.d(TAG, "restoreIfNeeded already executed during this app lifecycle. Skipping.")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val backupPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val prefDao = database.preferenceDao()
                val cardDao = database.transitCardDao()
                val calDao = database.calendarDao()

                // Restore preferences
                val currentPrefs = prefDao.getAllPreferences()
                val hasFavorites = currentPrefs.any { it.key == "favorite_stations" || it.key == "favorite_bus_stops" }
                if (!hasFavorites || currentPrefs.isEmpty()) {
                    val backupStr = backupPrefs.getString(KEY_PREFERENCES_BACKUP, null)
                        ?: readBackupFile(context, FILE_PREFERENCES_BACKUP)
                    if (!backupStr.isNullOrEmpty()) {
                        Log.d(TAG, "Restoring preferences from backup...")
                        val array = JSONArray(backupStr)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val key = obj.getString("key")
                            val value = obj.getString("value")
                            prefDao.insertPreference(PreferenceEntity(key, value))
                        }
                        Log.d(TAG, "Preferences restored.")
                    }
                }

                // Restore transit cards
                val currentCards = cardDao.getAllCards()
                if (currentCards.isEmpty()) {
                    val backupStr = backupPrefs.getString(KEY_CARDS_BACKUP, null)
                        ?: readBackupFile(context, FILE_CARDS_BACKUP)
                    if (!backupStr.isNullOrEmpty()) {
                        Log.d(TAG, "Restoring transit cards from backup...")
                        val array = JSONArray(backupStr)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val card = TransitCardEntity(
                                cardNumber = obj.getString("cardNumber"),
                                assignedName = obj.getString("assignedName"),
                                defaultName = obj.getString("defaultName"),
                                cardType = obj.getString("cardType"),
                                remainingValue = obj.getString("remainingValue"),
                                detailsJson = obj.getString("detailsJson"),
                                lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                            )
                            cardDao.insertCard(card)
                        }
                        Log.d(TAG, "Transit cards restored.")
                    }
                }

                // Restore calendar items
                val currentCalItems = calDao.getAllItemsList()
                if (currentCalItems.isEmpty() || currentCalItems.size <= 4) {
                    val backupStr = backupPrefs.getString(KEY_CALENDAR_BACKUP, null)
                        ?: readBackupFile(context, FILE_CALENDAR_BACKUP)
                    if (!backupStr.isNullOrEmpty()) {
                        val array = JSONArray(backupStr)
                        if (array.length() > currentCalItems.size) {
                            Log.d(TAG, "Restoring calendar items from backup...")
                            // Clear default ones to avoid duplicates if restoring full set
                            for (existingItem in currentCalItems) {
                                calDao.deleteItem(existingItem)
                            }
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val item = CalendarItemEntity(
                                    id = 0,
                                    title = obj.getString("title"),
                                    description = obj.getString("description"),
                                    startMillis = if (obj.has("startMillis")) obj.getLong("startMillis") else null,
                                    endMillis = if (obj.has("endMillis")) obj.getLong("endMillis") else null,
                                    dueMillis = if (obj.has("dueMillis")) obj.getLong("dueMillis") else null,
                                    isCompleted = obj.optBoolean("isCompleted", false),
                                    itemType = obj.getString("itemType"),
                                    colorHex = obj.optString("colorHex", "#3B82F6")
                                )
                                calDao.insertItem(item)
                            }
                            Log.d(TAG, "Calendar items restored.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in restoreIfNeeded: ${e.message}", e)
            }
        }
    }
}
