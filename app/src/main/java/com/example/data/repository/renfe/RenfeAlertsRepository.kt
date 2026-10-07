package com.example.data.repository.renfe

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.network.NetworkModule
import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.cercanias.CercaniasAlertClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class RenfeAlertsRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val _activeAlerts = MutableStateFlow<List<CercaniasAlert>>(emptyList())
    val activeAlerts = _activeAlerts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError = _hasError.asStateFlow()

    suspend fun fetchActiveAlerts(force: Boolean = false): List<CercaniasAlert> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && lastFetchTimestamp > 0L && (now - lastFetchTimestamp < CACHE_DURATION_MS)) {
            _activeAlerts.value = cachedAlerts
            return@withContext cachedAlerts
        }

        fetchMutex.withLock {
            val lockNow = System.currentTimeMillis()
            if (!force && lastFetchTimestamp > 0L && (lockNow - lastFetchTimestamp < CACHE_DURATION_MS)) {
                _activeAlerts.value = cachedAlerts
                return@withLock cachedAlerts
            }

            _isLoading.value = true
            _hasError.value = false
            try {
                val allValenciaStations = database.cercaniasStationDao().getAllStations()
                val valenciaStopIds = allValenciaStations.map { it.id }.toSet()
                val valenciaStationNamesNoAccents = allValenciaStations.map {
                    removeAccents(it.nombre.lowercase(Locale.ROOT).trim())
                }

                val request = Request.Builder()
                    .url("https://gtfsrt.renfe.com/alerts.json")
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .build()

                val responseBody = NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }

                if (!responseBody.isNullOrBlank()) {
                    val jsonObject = JSONObject(responseBody)
                    val entitiesArray = jsonObject.optJSONArray("entity") ?: jsonObject.optJSONArray("entities")
                    val list = mutableListOf<CercaniasAlert>()

                    if (entitiesArray != null) {
                        for (i in 0 until entitiesArray.length()) {
                            val entity = entitiesArray.optJSONObject(i) ?: continue
                            val id = entity.optString("id", "")
                            val alertObj = entity.optJSONObject("alert") ?: continue

                            val headerTextObj = alertObj.optJSONObject("header_text") ?: alertObj.optJSONObject("headerText")
                            val descTextObj = alertObj.optJSONObject("description_text") ?: alertObj.optJSONObject("descriptionText")

                            val headerEs = parseGtfsRtText(headerTextObj)
                            val descEs = parseGtfsRtText(descTextObj)

                            if (headerEs.isBlank() && descEs.isBlank()) {
                                continue
                            }

                            val informedEntitiesArray = alertObj.optJSONArray("informed_entity") ?: alertObj.optJSONArray("informedEntity")
                            val routeIds = mutableListOf<String>()
                            val tripIds = mutableListOf<String>()
                            val stopIds = mutableListOf<String>()

                            if (informedEntitiesArray != null) {
                                for (j in 0 until informedEntitiesArray.length()) {
                                    val inf = informedEntitiesArray.optJSONObject(j) ?: continue
                                    val routeId = inf.optString("route_id", "").ifBlank { inf.optString("routeId", "") }
                                    if (routeId.isNotBlank()) {
                                        routeIds.add(routeId)
                                        val m = Regex("""40[A-Za-z0-9]*?(\d{4})C?[1-6]?""").find(routeId)
                                        if (m != null) {
                                            val num4 = m.groupValues[1]
                                            val numShort = num4.trimStart('0')
                                            tripIds.add(routeId)
                                            tripIds.add(num4)
                                            if (numShort.isNotBlank()) tripIds.add(numShort)
                                        }
                                    }

                                    val tripIdObj = inf.optJSONObject("trip")
                                    val tripId = tripIdObj?.optString("trip_id", "")?.ifBlank { tripIdObj.optString("tripId", "") } ?: ""
                                    if (tripId.isNotBlank()) tripIds.add(tripId)

                                    val stopId = inf.optString("stop_id", "").ifBlank { inf.optString("stopId", "") }
                                    if (stopId.isNotBlank()) stopIds.add(stopId)
                                }
                            }

                            val cleanAlertStopIds = stopIds.map { sId -> sId.substringBefore('_').substringBefore('-').trim() }
                            val hasValenciaRoute = routeIds.isNotEmpty() && routeIds.any { it.startsWith("40") }
                            val hasValenciaStop = cleanAlertStopIds.isNotEmpty() && cleanAlertStopIds.any { valenciaStopIds.contains(it) }
                            val hasOtherHubRoute = routeIds.isNotEmpty() && routeIds.any { !it.startsWith("40") }
                            val hasOtherHubStop = cleanAlertStopIds.isNotEmpty() && cleanAlertStopIds.any { !valenciaStopIds.contains(it) }

                            val textToSearch = "$headerEs $descEs".lowercase(Locale.ROOT)
                            val textToSearchNoAccents = removeAccents(textToSearch)

                            var isValencia = false
                            if (hasValenciaRoute || hasValenciaStop) {
                                isValencia = !hasOtherHubRoute && !hasOtherHubStop
                            } else if (!hasOtherHubRoute && !hasOtherHubStop) {
                                val mentionsValenciaOrCastellon = textToSearchNoAccents.contains("valencia") ||
                                        textToSearchNoAccents.contains("valència") ||
                                        textToSearchNoAccents.contains("castello") ||
                                        textToSearchNoAccents.contains("castellon") ||
                                        textToSearchNoAccents.contains("gandia")

                                var hasValenciaStation = false
                                if (!mentionsValenciaOrCastellon) {
                                    hasValenciaStation = valenciaStationNamesNoAccents.any { stationName ->
                                        if (stationName.isBlank()) return@any false
                                        val cleanName = stationName.replace("(", " ").replace(")", " ").replace("-", " ").trim()
                                        if (cleanName.length > 3) {
                                            containsWordBoundaryMatch(textToSearchNoAccents, cleanName)
                                        } else {
                                            false
                                        }
                                    }
                                }

                                isValencia = mentionsValenciaOrCastellon || hasValenciaStation

                                val mentionsOtherHub = textToSearchNoAccents.contains("madrid") ||
                                        textToSearchNoAccents.contains("barcelona") ||
                                        textToSearchNoAccents.contains("sevilla") ||
                                        textToSearchNoAccents.contains("malaga") ||
                                        textToSearchNoAccents.contains("bilbao") ||
                                        textToSearchNoAccents.contains("zaragoza")
                                if (mentionsOtherHub && !textToSearchNoAccents.contains("valencia")) {
                                    isValencia = false
                                }
                            }

                            if (!isValencia) continue

                            val isAccessibility = CercaniasAlertClassifier.isAccessibility(textToSearch)
                            val isCirculation = !isAccessibility && CercaniasAlertClassifier.isStrongCirculationIncident(headerEs, descEs)
                            val timestamp = alertObj.optLong("timestamp", System.currentTimeMillis() / 1000)

                            val linesInText = extractLinesFromAlertText("$headerEs. $descEs")
                            val entityRouteIds = routeIds.mapNotNull { normalizeValenciaRouteId(it) }.distinct()

                            val combinedRouteSet = (linesInText + entityRouteIds).distinct()
                            val finalRouteIds = VALENCIA_VALID_LINES.filter { combinedRouteSet.contains(it) }

                            list.add(
                                CercaniasAlert(
                                    id = id,
                                    headerEs = headerEs,
                                    descriptionEs = descEs,
                                    routeIds = finalRouteIds,
                                    tripIds = tripIds,
                                    stopIds = stopIds,
                                    isAccessibility = isAccessibility,
                                    isCirculationIncident = isCirculation,
                                    timestamp = timestamp
                                )
                            )
                        }
                    }

                    val sortedList = list.distinctBy { it.id }.sortedByDescending { it.timestamp }
                    cachedAlerts = sortedList
                    lastFetchTimestamp = System.currentTimeMillis()
                    _activeAlerts.value = sortedList
                    _hasError.value = false
                    sortedList
                } else {
                    _hasError.value = true
                    cachedAlerts
                }
            } catch (e: Exception) {
                Log.w("RenfeAlertsRepository", "Error fetching Cercanías alerts: ${e.message}")
                _hasError.value = true
                cachedAlerts
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseGtfsRtText(textObj: JSONObject?): String {
        if (textObj == null) return ""
        val transArray = textObj.optJSONArray("translation") ?: textObj.optJSONArray("translations")
        if (transArray != null && transArray.length() > 0) {
            for (i in 0 until transArray.length()) {
                val tr = transArray.optJSONObject(i) ?: continue
                val lang = tr.optString("language", "").lowercase(Locale.ROOT)
                val text = tr.optString("text", "")
                if (lang == "es" || lang == "spa" || lang == "ca" || lang == "val") {
                    return text.trim()
                }
            }
            return transArray.optJSONObject(0)?.optString("text", "")?.trim() ?: ""
        }
        return textObj.optString("text", "").trim()
    }

    private fun removeAccents(str: String): String {
        return java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun containsWordBoundaryMatch(text: String, word: String): Boolean {
        if (word.isBlank()) return false
        val pattern = Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(text)
    }

    private fun extractLinesFromAlertText(text: String): List<String> {
        val found = mutableSetOf<String>()
        val regex = Regex("""\b[Cc][-_ ]?([1-6])\b|\bl[ií]nea[-_ ]?([1-6])\b""", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text)
        for (match in matches) {
            val lineNum = match.groupValues[1].ifBlank { match.groupValues[2] }
            if (lineNum.isNotBlank()) {
                found.add("C$lineNum")
            }
        }
        return found.toList().sorted()
    }

    private fun normalizeValenciaRouteId(routeId: String): String? {
        val clean = routeId.trim().uppercase(Locale.ROOT)
        if (clean.contains("53") || clean.endsWith("C1") || clean.endsWith("C-1") || clean == "401") return "C1"
        if (clean.contains("54") || clean.endsWith("C2") || clean.endsWith("C-2") || clean == "402") return "C2"
        if (clean.contains("55") || clean.endsWith("C3") || clean.endsWith("C-3") || clean == "403") return "C3"
        if (clean.contains("56") || clean.endsWith("C4") || clean.endsWith("C-4") || clean == "404") return "C4"
        if (clean.contains("57") || clean.endsWith("C5") || clean.endsWith("C-5") || clean == "405") return "C5"
        if (clean.contains("58") || clean.endsWith("C6") || clean.endsWith("C-6") || clean == "406") return "C6"
        val m = Regex("""C[-_]?([1-6])""").find(clean)
        return if (m != null) "C${m.groupValues[1]}" else null
    }

    companion object {
        private const val CACHE_DURATION_MS = 180_000L // 3 minutes cache
        private val fetchMutex = Mutex()
        private val VALENCIA_VALID_LINES = listOf("C1", "C2", "C3", "C4", "C5", "C6")

        @Volatile
        private var lastFetchTimestamp: Long = 0L

        @Volatile
        private var cachedAlerts: List<CercaniasAlert> = emptyList()

        fun hasValidCache(): Boolean {
            val now = System.currentTimeMillis()
            return lastFetchTimestamp > 0L && (now - lastFetchTimestamp < CACHE_DURATION_MS)
        }

        fun getCachedAlerts(): List<CercaniasAlert> = cachedAlerts
    }
}
