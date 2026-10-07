package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.MetroScheduledDeparture
import com.example.data.model.MetroScheduledStopPass
import com.example.data.model.MetroTrainTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MetroScheduleRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MetroScheduleRepo"
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/BananaManMS/metro_valencia_schedule/refs/heads/data/metro_schedule.json"
        private const val CACHE_FILE_NAME = "metro_valencia_schedule_cache.json"
        private const val PREFS_NAME = "metro_schedule_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"

        @Volatile
        private var INSTANCE: MetroScheduleRepository? = null

        fun getInstance(context: Context): MetroScheduleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MetroScheduleRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // In-memory caches
    private var datesList: List<String> = emptyList()
    private val webIdToNameMap = mutableMapOf<Int, String>()
    private val nameToWebIdMap = mutableMapOf<String, Int>()
    
    // Map of stopWebId -> List of raw entries: [dateIdx, timeMinutes, line, originWebId, destWebId, trainServiceId]
    private val stopSchedules = mutableMapOf<Int, MutableList<IntArray>>() // IntArray of size 6: [dateIdx, min, lineInt, origWebId, destWebId, trainServiceId]
    private val lineStringMap = mutableMapOf<Int, String>() // e.g. 1 -> "1", 10 -> "10"

    private var isLoaded = false

    /**
     * Initializes mappings and loads schedules if not loaded yet.
     */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        loadScheduleData()
        isLoaded = true
    }

    /**
     * Synchronizes schedule from remote GitHub repository if more than 24h passed or cache missing.
     */
    suspend fun syncScheduleFromRemoteIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
            val now = System.currentTimeMillis()
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

            val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("Europe/Madrid")
            }
            val todayStr = sdf.format(Date())
            val hasTodayAsFirstDate = datesList.isNotEmpty() && datesList.firstOrNull() == todayStr
            val isCacheStale = (now - lastSync > 6 * 60 * 60 * 1000L) || !hasTodayAsFirstDate

            val needsSync = !cacheFile.exists() || isCacheStale
            if (!needsSync) {
                return@withContext false
            }

            Log.d(TAG, "Syncing Metrovalencia schedule from remote...")
            val url = URL(REMOTE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "VLC-Transit/1.0")

            val lastModifiedPref = prefs.getLong("last_modified_header", 0L)
            if (cacheFile.exists() && lastModifiedPref > 0L) {
                conn.ifModifiedSince = lastModifiedPref
            }

            if (conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.d(TAG, "Metrovalencia schedule has not changed on GitHub (304 Not Modified)")
                prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
                return@withContext false
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val lastModifiedHeader = conn.lastModified
                val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 50000) { // Valid non-empty file
                    if (cacheFile.exists()) cacheFile.delete()
                    tempFile.renameTo(cacheFile)
                    prefs.edit()
                        .putLong(KEY_LAST_SYNC, now)
                        .putLong("last_modified_header", lastModifiedHeader)
                        .apply()
                    Log.d(TAG, "Metrovalencia schedule synced successfully (${cacheFile.length()} bytes)")
                    // Reload in-memory
                    loadScheduleData()
                    return@withContext true
                } else {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download remote metro schedule: ${e.message}")
        }
        return@withContext false
    }

    /**
     * The GitHub schedule JSON only indexes stations by webID (e.g. 70 for Colón, 71 for Xàtiva, 25 for Àngel Guimerà)
     * and official station names. There is no station_id in the JSON.
     * We resolve the webID primarily by matching the station's name against the schedule's webID table.
     */
    fun getWebIdForStationId(stationId: String?, stationName: String? = null): Int? {
        return getWebIdForStation(stationId, stationName)
    }

    fun getWebIdForStation(stationId: String? = null, stationName: String? = null): Int? {
        // Priority 1: Match by station name in the schedule's official webID table
        if (!stationName.isNullOrBlank()) {
            findWebIdByStationName(stationName)?.let { return it }
        }

        // Priority 2: If stationId is already a valid webID directly present in webID dictionary
        val numericId = stationId?.trim()?.toIntOrNull()
        if (numericId != null && webIdToNameMap.containsKey(numericId)) {
            return numericId
        }

        return null
    }

    fun getStationNameForWebId(webId: Int): String {
        return webIdToNameMap[webId] ?: "Estación $webId"
    }

    /**
     * Returns scheduled departures for the given station starting from currentTimeMinutes (or now).
     * Only returns upcoming departures for the active date.
     */
    suspend fun getScheduledDepartures(
        stationWebId: Int,
        limit: Int = 500,
        fromMinutesOfDay: Int? = null,
        toMinutesOfDay: Int? = null,
        lineFilter: String? = null
    ): List<MetroScheduledDeparture> = withContext(Dispatchers.Default) {
        ensureLoaded()

        val list = stopSchedules[stationWebId] ?: return@withContext emptyList()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMin = fromMinutesOfDay ?: (currentHour * 60 + calendar.get(Calendar.MINUTE))
        val activeDateIdx = getActiveDateIndex()
        val prevDateIdx = getPreviousDateIndex()

        val normalizedLineFilter = lineFilter?.replace("L", "", ignoreCase = true)?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("ALL", ignoreCase = true) }

        val results = mutableListOf<MetroScheduledDeparture>()
        synchronized(list) {
            for (arr in list) {
                val dateIdx = arr[0]
                val rawMin = arr[1]
                val lineStr = lineStringMap[arr[2]] ?: arr[2].toString()
                val lineNumOnly = lineStr.replace("L", "", ignoreCase = true).trim()

                if (com.example.util.MetroDepotFilterHelper.isDepotExcludedStationLine(stationWebId.toString(), null, lineNumOnly)) {
                    continue
                }

                if (normalizedLineFilter != null && !lineNumOnly.equals(normalizedLineFilter, ignoreCase = true)) {
                    continue
                }

                val origWebId = arr[3]
                val destWebId = arr[4]
                val trainServiceId = arr[5]

                // Exclude departures whose destination is the current station (e.g. Aeroport at Aeroport)
                if (destWebId == stationWebId) {
                    continue
                }
                val destName = webIdToNameMap[destWebId] ?: "Estación $destWebId"
                val curStationName = webIdToNameMap[stationWebId]
                if (curStationName != null && destName.equals(curStationName, ignoreCase = true)) {
                    continue
                }

                val origName = webIdToNameMap[origWebId] ?: "Estación $origWebId"

                var effectiveMin: Int? = null

                if (currentHour < 6) {
                    // Early morning query (00:00 - 05:59):
                    // 1) Late night departures from yesterday's service day (e.g. 25:30 -> 01:30 AM today)
                    if (prevDateIdx != null && dateIdx == prevDateIdx && rawMin >= 1440) {
                        val effTodayMin = rawMin - 1440
                        if (effTodayMin >= currentMin - 2) {
                            effectiveMin = effTodayMin
                        }
                    } else if (dateIdx == activeDateIdx) {
                        // 2) Today's morning departures
                        val effTodayMin = if (rawMin >= 1440) rawMin - 1440 else rawMin
                        if (effTodayMin >= currentMin - 2) {
                            effectiveMin = effTodayMin
                        }
                    }
                } else {
                    // Daytime / evening query (06:00 - 23:59)
                    if (dateIdx == activeDateIdx && rawMin >= currentMin - 2) {
                        effectiveMin = rawMin
                    }
                }

                if (effectiveMin != null) {
                    if (toMinutesOfDay != null && effectiveMin > toMinutesOfDay) {
                        continue
                    }

                    val hh = (effectiveMin / 60) % 24
                    val mm = effectiveMin % 60
                    val formatted = String.format(Locale.getDefault(), "%02d:%02d", hh, mm)

                    results.add(
                        MetroScheduledDeparture(
                            dateIndex = dateIdx,
                            timeMinutes = effectiveMin,
                            timeFormatted = formatted,
                            line = lineStr,
                            originWebId = origWebId,
                            originName = origName,
                            destinationWebId = destWebId,
                            destinationName = destName,
                            trainServiceId = trainServiceId
                        )
                    )
                }
            }
        }
        results.sortedBy { it.timeMinutes }.take(limit)
    }

    /**
     * Returns the sorted unique line identifiers (e.g. ["1", "2", "3", "5", "9"]) available for a station.
     */
    fun getLinesForStation(stationWebId: Int): List<String> {
        val list = stopSchedules[stationWebId] ?: return emptyList()
        val stationName = webIdToNameMap[stationWebId]
        val linesSet = sortedSetOf<String>(Comparator { a, b ->
            val numA = a.toIntOrNull() ?: 999
            val numB = b.toIntOrNull() ?: 999
            if (numA != numB) numA.compareTo(numB) else a.compareTo(b)
        })
        synchronized(list) {
            for (arr in list) {
                val lineStr = lineStringMap[arr[2]] ?: arr[2].toString()
                val cleanLine = lineStr.replace("L", "", ignoreCase = true).trim()
                if (com.example.util.MetroDepotFilterHelper.isDepotExcludedStationLine(stationWebId.toString(), stationName, cleanLine)) {
                    continue
                }
                linesSet.add(cleanLine)
            }
        }
        return linesSet.toList()
    }

    /**
     * Given a departure (line, current station, destination, expected arrival minutes, and optional service info),
     * finds the matched scheduled train run and constructs its complete timeline of all stops with exact stop times.
     */
    suspend fun getTrainTimeline(
        currentStationWebId: Int,
        line: String,
        destinationName: String,
        estimatedArrivalMinutesOfDay: Int,
        originStationWebId: Int? = null,
        destinationWebId: Int? = null,
        trainServiceId: Int? = null
    ): MetroTrainTimeline? = withContext(Dispatchers.Default) {
        ensureLoaded()

        val cleanLine = line.replace("L", "").trim()
        val destWebId = destinationWebId ?: findWebIdByStationName(destinationName)
        val activeDateIdx = getActiveDateIndex()
        val prevDateIdx = getPreviousDateIndex()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        val stationEntries = stopSchedules[currentStationWebId] ?: return@withContext null

        // Find candidate departures from the current station matching line, destination, and closest time
        var bestEntry: IntArray? = null
        var minDiff = Int.MAX_VALUE

        synchronized(stationEntries) {
            for (arr in stationEntries) {
                val dIdx = arr[0]
                val isValidDate = dIdx == activeDateIdx || (currentHour < 6 && prevDateIdx != null && dIdx == prevDateIdx && arr[1] >= 1440)
                if (!isValidDate) continue
                val lineStr = lineStringMap[arr[2]] ?: arr[2].toString()
                if (lineStr != cleanLine) continue

                // Check destination if matched
                if (destWebId != null && arr[4] != destWebId) {
                    val entryDestName = webIdToNameMap[arr[4]] ?: ""
                    if (!destinationMatches(entryDestName, destinationName)) {
                        continue
                    }
                }

                if (originStationWebId != null && arr[3] != originStationWebId) {
                    continue
                }

                val diff = kotlin.math.abs(arr[1] - estimatedArrivalMinutesOfDay)

                if (diff < minDiff) {
                    minDiff = diff
                    bestEntry = arr
                    if (diff == 0) {
                        break
                    }
                }
            }
        }

        val matchedEntry = bestEntry ?: return@withContext null
        val originWebId = matchedEntry[3]
        val finalDestWebId = matchedEntry[4]
        val serviceId = matchedEntry[5]
        val originName = webIdToNameMap[originWebId] ?: "Estación $originWebId"
        val finalDestName = webIdToNameMap[finalDestWebId] ?: "Estación $finalDestWebId"
        val matchedCurrentTime = matchedEntry[1]

        // Collect all departures for this exact service run (activeDateIdx, line, origin, destination) across all stations
        val stationTimes = mutableMapOf<Int, MutableList<Int>>()
        for ((sWebId, entries) in stopSchedules) {
            synchronized(entries) {
                for (arr in entries) {
                    if (arr[0] == activeDateIdx &&
                        arr[2] == matchedEntry[2] &&
                        arr[3] == originWebId &&
                        arr[4] == finalDestWebId
                    ) {
                        stationTimes.getOrPut(sWebId) { mutableListOf() }.add(arr[1])
                    }
                }
            }
        }

        val currentStationTimes = stationTimes[currentStationWebId]?.sorted() ?: emptyList()
        val runIndex = if (currentStationTimes.isNotEmpty()) {
            val idx = currentStationTimes.indexOf(matchedCurrentTime)
            if (idx >= 0) idx else currentStationTimes.indices.minByOrNull { kotlin.math.abs(currentStationTimes[it] - matchedCurrentTime) } ?: 0
        } else 0

        val timelineStopsList = mutableListOf<MetroScheduledStopPass>()
        for ((sWebId, timesList) in stationTimes) {
            val sortedTimes = timesList.sorted()
            if (sortedTimes.isNotEmpty()) {
                val targetTimeMin = if (runIndex < sortedTimes.size) sortedTimes[runIndex] else sortedTimes.last()
                val name = webIdToNameMap[sWebId] ?: "Estación $sWebId"
                val fgvId = sWebId.toString()
                val hh = (targetTimeMin / 60) % 24
                val mm = targetTimeMin % 60
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", hh, mm)

                timelineStopsList.add(
                    MetroScheduledStopPass(
                        stationWebId = sWebId,
                        stationName = name,
                        stationFgvId = fgvId,
                        timeMinutes = targetTimeMin,
                        timeFormatted = formatted,
                        isOrigin = (sWebId == originWebId),
                        isCurrentStation = (sWebId == currentStationWebId),
                        isDestination = (sWebId == finalDestWebId),
                        isPassed = (targetTimeMin < matchedCurrentTime)
                    )
                )
            }
        }

        // Sort chronologically along the run from origin to destination
        timelineStopsList.sortBy { it.timeMinutes }

        MetroTrainTimeline(
            line = cleanLine,
            trainServiceId = serviceId,
            originName = originName,
            originWebId = originWebId,
            destinationName = finalDestName,
            destinationWebId = finalDestWebId,
            currentStationWebId = currentStationWebId,
            stops = timelineStopsList
        )
    }

    private fun getActiveDateIndex(): Int {
        if (datesList.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Europe/Madrid")
        }
        val todayStr = sdf.format(Date())
        val idx = datesList.indexOf(todayStr)
        if (idx >= 0) return idx

        // Fallback when GitHub file has not updated yet:
        // The JSON contains 2 days [day0, day1]. If today is >= day0, day1 (index 1) represents
        // the next day (el día siguiente) and is the closest/most recent schedule in the file.
        return if (datesList.size > 1 && todayStr >= datesList[0]) {
            1
        } else {
            datesList.lastIndex.coerceAtLeast(0)
        }
    }

    private fun getPreviousDateIndex(): Int? {
        if (datesList.isEmpty()) return null
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Europe/Madrid")
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)
        val idx = datesList.indexOf(yesterdayStr)
        if (idx >= 0) return idx

        val activeIdx = getActiveDateIndex()
        return if (activeIdx > 0) activeIdx - 1 else null
    }

    private fun loadScheduleData() {
        try {
            var inputStream: InputStream? = null
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (cacheFile.exists() && cacheFile.length() > 50000) {
                inputStream = cacheFile.inputStream()
            } else {
                inputStream = context.assets.open("metro_valencia_schedule.json")
            }

            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            // 1. Dates
            val datesArr = root.optJSONArray("dates")
            val dList = mutableListOf<String>()
            if (datesArr != null) {
                for (i in 0 until datesArr.length()) {
                    dList.add(datesArr.getString(i))
                }
            }
            datesList = dList

            // 2. webID station names
            val webIdObj = root.optJSONObject("webID")
            if (webIdObj != null) {
                webIdToNameMap.clear()
                nameToWebIdMap.clear()
                val keys = webIdObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val wId = k.toIntOrNull() ?: continue
                    val name = webIdObj.getString(k)
                    webIdToNameMap[wId] = name
                    nameToWebIdMap[normalizeStationName(name)] = wId
                }
            }

            // 3. Stops
            val stopsObj = root.optJSONObject("stops")
            if (stopsObj != null) {
                stopSchedules.clear()
                lineStringMap.clear()

                val sKeys = stopsObj.keys()
                while (sKeys.hasNext()) {
                    val sKey = sKeys.next()
                    val stopWebId = sKey.toIntOrNull() ?: continue
                    val entriesArr = stopsObj.getJSONArray(sKey)
                    val stopList = ArrayList<IntArray>(entriesArr.length())

                    for (i in 0 until entriesArr.length()) {
                        val row = entriesArr.getJSONArray(i)
                        val dIdx = row.getInt(0)
                        val min = row.getInt(1)
                        val lineStr = row.getString(2)
                        val lineInt = lineStr.toIntOrNull() ?: lineStr.hashCode()
                        lineStringMap[lineInt] = lineStr

                        val origWebId = row.getInt(3)
                        val destWebId = row.getInt(4)
                        val trainId = row.getInt(5)

                        stopList.add(intArrayOf(dIdx, min, lineInt, origWebId, destWebId, trainId))
                    }
                    stopSchedules[stopWebId] = stopList
                }
            }
            Log.d(TAG, "Loaded schedule for ${stopSchedules.size} stations, dates: $datesList")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metro schedule JSON: ${e.message}", e)
        }
    }

    private fun findWebIdByStationName(name: String): Int? {
        val norm = normalizeStationName(name)
        nameToWebIdMap[norm]?.let { return it }

        // Fuzzy match
        for ((normStored, wId) in nameToWebIdMap) {
            if (normStored.contains(norm) || norm.contains(normStored)) {
                return wId
            }
        }
        return null
    }

    private fun destinationMatches(storedName: String, queryName: String): Boolean {
        val s1 = normalizeStationName(storedName)
        val s2 = normalizeStationName(queryName)
        return s1 == s2 || s1.contains(s2) || s2.contains(s1)
    }

    private fun normalizeStationName(name: String): String {
        val nfd = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())
            .replace("-", " ")
            .replace(".", "")
            .replace("'", " ")
            .replace("’", " ")
            .replace("·", "")
            .replace("/", " ")
            .replace("  ", " ")
            .trim()
    }
}
