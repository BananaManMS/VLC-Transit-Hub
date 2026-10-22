package com.example.data.repository.renfe

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.PreferenceEntity
import com.example.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Calendar

/**
 * Manages weekly (every Monday or 7-day interval) synchronization of Cercanías Valencia stations CSV:
 * https://raw.githubusercontent.com/BananaManMS/cercanias-vlc-schedule/refs/heads/main/estaciones_cercanias_valencia.csv
 */
class CercaniasCsvSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CercaniasCsvSync"
        private const val PREF_LAST_CSV_SYNC = "last_cercanias_csv_sync"
        private const val CSV_URL = "https://raw.githubusercontent.com/BananaManMS/cercanias-vlc-schedule/refs/heads/main/estaciones_cercanias_valencia.csv"
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L

        fun getLocalCsvFile(context: Context): File {
            return File(context.filesDir, "estaciones_cercanias_valencia.csv")
        }

        suspend fun syncIfNeeded(context: Context, force: Boolean = false) {
            CercaniasCsvSyncManager(context).syncWeeklyCsv(force)
        }
    }

    suspend fun syncWeeklyCsv(force: Boolean = false) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefDao = db.preferenceDao()
        val now = System.currentTimeMillis()

        val lastSyncStr = try { prefDao.getPreference(PREF_LAST_CSV_SYNC)?.value } catch (_: Exception) { null }
        val lastSync = lastSyncStr?.toLongOrNull() ?: 0L

        val cal = Calendar.getInstance()
        val isMonday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
        val daysSinceLastSync = (now - lastSync) / (24 * 60 * 60 * 1000L)

        // Only sync if forced, or if it's Monday and hasn't synced today, or if more than 7 days passed
        val shouldSync = force || (isMonday && daysSinceLastSync >= 1) || (daysSinceLastSync >= 7) || (lastSync == 0L)
        if (!shouldSync) {
            return@withContext
        }

        val csvFile = getLocalCsvFile(context)

        try {
            val request = Request.Builder().url(CSV_URL).build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val csvText = response.body?.string() ?: ""
                    if (csvText.isNotBlank()) {
                        val parsedStations = parseCsv(csvText, db)
                        if (parsedStations.isNotEmpty()) {
                            writeAtomic(csvFile, csvText)
                            // Merge stations preserving existing favorites and schedules
                            mergeStationsIntoDb(db, parsedStations)
                            prefDao.insertPreference(PreferenceEntity(PREF_LAST_CSV_SYNC, now.toString()))
                            Log.i(TAG, "Successfully synced and updated ${parsedStations.size} Cercanías stations from weekly CSV.")
                        }
                    }
                } else {
                    Log.w(TAG, "Cercanías CSV request returned code ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Cercanías CSV from GitHub: ${e.message}")
        }
    }

    private suspend fun mergeStationsIntoDb(db: AppDatabase, newStations: List<CercaniasStationEntity>) {
        val existingMap = try {
            db.cercaniasStationDao().getAllStations().associateBy { it.stop_id }
        } catch (_: Exception) {
            emptyMap()
        }

        val merged = newStations.map { station ->
            val existing = existingMap[station.stop_id]
            if (existing != null) {
                station.copy(
                    isFavorite = existing.isFavorite,
                    horarios = if (station.horarios.isEmpty()) existing.horarios else station.horarios
                )
            } else {
                station
            }
        }

        if (merged.size >= 10) {
            db.cercaniasStationDao().replaceAllStations(merged)
        }
    }

    private fun parseCsv(csvText: String, db: AppDatabase): List<CercaniasStationEntity> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()

        val header = lines.first().lowercase().split(",").map { it.trim().trim('"', '\'') }
        val idIdx = header.indexOfFirst { it == "stop_id" || it == "id" || it == "codigo" }
        val nameIdx = header.indexOfFirst { it == "nombre" || it == "name" || it == "stop_name" || it == "denominacion" }
        val latIdx = header.indexOfFirst { it == "lat" || it == "stop_lat" || it == "latitud" || it == "latitude" }
        val lonIdx = header.indexOfFirst { it == "lon" || it == "stop_lon" || it == "longitud" || it == "longitude" || it == "lng" }
        val linesIdx = header.indexOfFirst { it == "lineas" || it == "lines" || it == "linea" }

        if (idIdx == -1 || nameIdx == -1 || latIdx == -1 || lonIdx == -1) {
            Log.w(TAG, "CSV header missing required columns: $header")
            return emptyList()
        }

        val result = mutableListOf<CercaniasStationEntity>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val cols = parseCsvLine(line)
            if (cols.size > maxOf(idIdx, nameIdx, latIdx, lonIdx)) {
                val stopId = cols[idIdx].trim().trim('"', '\'')
                val rawName = cols[nameIdx].trim().trim('"', '\'')
                val lat = cols[latIdx].trim().trim('"', '\'').toDoubleOrNull() ?: 0.0
                val lon = cols[lonIdx].trim().trim('"', '\'').toDoubleOrNull() ?: 0.0

                val rawLines = if (linesIdx != -1 && cols.size > linesIdx) {
                    cols[linesIdx].trim().trim('"', '\'')
                } else ""
                val lineasList = rawLines.split(";", "-", "/", "|", ",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { if (it.length == 1 && it[0].isDigit()) "C$it" else it }

                if (stopId.isNotEmpty() && rawName.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                    result.add(
                        CercaniasStationEntity(
                            stop_id = stopId,
                            nombre = rawName,
                            lat = lat,
                            lon = lon,
                            lineas = lineasList,
                            horarios = emptyList(),
                            isFavorite = false
                        )
                    )
                }
            }
        }
        return result
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString())
        return tokens
    }

    private fun writeAtomic(targetFile: File, content: String) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeText(content, Charsets.UTF_8)
        if (tempFile.exists() && tempFile.length() > 0) {
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }
    }
}
