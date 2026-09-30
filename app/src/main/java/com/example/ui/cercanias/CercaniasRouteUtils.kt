package com.example.ui.cercanias

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

data class CercaniasLineStationInfo(
    val id: String,
    val name: String,
    val transferLines: List<String> = emptyList()
)

object CercaniasRouteUtils {

    private var lineStationsMap: Map<String, List<CercaniasLineStationInfo>> = emptyMap()
    private var tripScheduleMap: Map<String, Map<String, String>> = emptyMap()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            // 1. Load line stations
            val jsonString = context.assets.open("cercanias_line_stations.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val tempMap = mutableMapOf<String, List<CercaniasLineStationInfo>>()

            val rawMap = mutableMapOf<String, List<Pair<String, String>>>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val lineKey = keys.next()
                val array = jsonObject.getJSONArray(lineKey)
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(Pair(obj.getString("id"), obj.getString("name")))
                }
                rawMap[lineKey] = list
            }

            for ((lineId, stationPairs) in rawMap) {
                if (lineId == "C4" || lineId == "C-4") continue
                val lineStationInfos = stationPairs.map { (stId, stName) ->
                    val transfers = rawMap.entries
                        .filter { (otherLine, otherList) ->
                            otherLine != lineId && otherLine != "C4" && otherLine != "C-4" &&
                                otherList.any { it.first == stId || isSameStationName(it.second, stName) }
                        }
                        .map { it.key }
                        .filter { it != "C4" && it != "C-4" }
                        .distinct()
                        .sorted()

                    CercaniasLineStationInfo(
                        id = stId,
                        name = stName,
                        transferLines = transfers
                    )
                }
                tempMap[lineId] = lineStationInfos
            }

            lineStationsMap = tempMap

            // 2. Load trip schedules for station arrival times
            loadScheduleJsonMap(context)

            isInitialized = true
        } catch (e: Exception) {
            Log.e("CercaniasRouteUtils", "Error loading cercanias route data", e)
        }
    }

    private fun loadScheduleJsonMap(context: Context) {
        try {
            val localFile = File(context.filesDir, "cercanias_valencia_schedule.json")
            val schedStr = if (localFile.exists() && localFile.length() > 100) {
                try { localFile.readText() } catch (e: Exception) { "" }
            } else ""

            val validStr = if (schedStr.isNotBlank() && schedStr.trim().startsWith("[")) {
                schedStr
            } else {
                try {
                    context.assets.open("cercanias_valencia_schedule.json").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
            }

            if (validStr.isNotBlank()) {
                val jsonArray = JSONArray(validStr)
                val tempTripMap = mutableMapOf<String, MutableMap<String, String>>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val stopId = obj.optString("stop_id", "").trim()
                    val rawName = obj.optString("nombre", "").trim()
                    val normName = normalizeName(rawName)

                    val horariosArr = obj.optJSONArray("horarios") ?: continue
                    for (j in 0 until horariosArr.length()) {
                        val hObj = horariosArr.optJSONObject(j) ?: continue
                        val llegada = hObj.optString("llegada", "").trim()
                        if (llegada.isBlank()) continue

                        val timeParts = llegada.split(":")
                        if (timeParts.size < 2) continue
                        val h = timeParts[0].toIntOrNull() ?: continue
                        val m = timeParts[1].toIntOrNull() ?: continue
                        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", h, m)

                        val tripIdsArr = hObj.optJSONArray("trip_ids") ?: continue
                        for (k in 0 until tripIdsArr.length()) {
                            val tid = tripIdsArr.optString(k, "").trim()
                            if (tid.isBlank()) continue

                            val exactK = tid
                            val baseK = getBaseTripKey(exactK)
                            val trainNo = extractTrainNumber(exactK)

                            val keys = mutableListOf(exactK, baseK)
                            if (!trainNo.isNullOrBlank()) keys.add(trainNo)

                            for (key in keys) {
                                val subMap = tempTripMap.getOrPut(key) { mutableMapOf() }
                                if (stopId.isNotEmpty()) {
                                    subMap[stopId] = formattedTime
                                }
                                if (normName.isNotEmpty()) {
                                    subMap[normName] = formattedTime
                                }
                            }
                        }
                    }
                }
                tripScheduleMap = tempTripMap
            }
        } catch (e: Exception) {
            Log.e("CercaniasRouteUtils", "Error loading schedule json map", e)
        }
    }

    fun getTripKeys(tid: String?): List<String> {
        if (tid.isNullOrBlank()) return emptyList()
        val trimmed = tid.trim()
        val list = mutableListOf<String>()

        list.add(trimmed)
        list.add(trimmed.lowercase(Locale.ROOT))
        list.add(trimmed.uppercase(Locale.ROOT))

        // Strip suffix after _ or - (e.g. "4070M24249C1_1" -> "4070M24249C1")
        if (trimmed.contains("_") || trimmed.contains("-")) {
            val base = trimmed.split(Regex("[_-]")).firstOrNull { it.isNotBlank() } ?: ""
            if (base.isNotBlank()) {
                list.add(base)
                list.add(base.lowercase(Locale.ROOT))
                list.add(base.uppercase(Locale.ROOT))
            }
        }

        // If index 4 is a letter (e.g. "4070M24249C1"), add version without index 4 ("407024249C1") and with all day codes (L,M,X,J,V,S,D)
        if (trimmed.length > 5 && trimmed[4].isLetter()) {
            val prefix = trimmed.substring(0, 4)
            val suffix = trimmed.substring(5)
            val noLetter = prefix + suffix
            list.add(noLetter)
            list.add(noLetter.lowercase(Locale.ROOT))
            list.add(noLetter.uppercase(Locale.ROOT))

            val dayCodes = listOf("L", "M", "X", "J", "V", "S", "D")
            for (dc in dayCodes) {
                val variant = prefix + dc + suffix
                list.add(variant)
                list.add(variant.lowercase(Locale.ROOT))
            }
        }

        // Extract train number (e.g., "24249")
        val trainNo = extractTrainNumber(trimmed)
        if (!trainNo.isNullOrBlank()) {
            list.add(trainNo)
            val noZero = trainNo.trimStart('0')
            if (noZero.isNotBlank()) list.add(noZero)
        }

        // Extract 4-5 digit sequences
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length in 4..5) {
            list.add(digits)
            val noZeroDigits = digits.trimStart('0')
            if (noZeroDigits.isNotBlank()) list.add(noZeroDigits)
        }

        return list.distinct().filter { it.isNotBlank() }
    }

    fun getBaseTripKey(tid: String): String {
        return getTripKeys(tid).firstOrNull() ?: tid.trim()
    }

    fun isTripMatchedByAlert(
        departureTripId: String,
        departureAllTripIds: List<String>,
        alertTripIds: List<String>
    ): Boolean {
        if (alertTripIds.isEmpty()) return false
        val departureKeys = (listOf(departureTripId) + departureAllTripIds)
            .flatMap { getTripKeys(it) }
            .distinct()
        val alertKeys = alertTripIds
            .flatMap { getTripKeys(it) }
            .distinct()

        return departureKeys.any { dKey ->
            alertKeys.any { aKey ->
                dKey.equals(aKey, ignoreCase = true) ||
                (dKey.length >= 4 && aKey.contains(dKey, ignoreCase = true)) ||
                (aKey.length >= 4 && dKey.contains(aKey, ignoreCase = true))
            }
        }
    }

    fun getStationScheduledTime(tripId: String, stationId: String, stationName: String): String? {
        if (tripId.isBlank()) return null
        val exactK = tripId.trim()
        val baseK = getBaseTripKey(exactK)
        val trainNo = extractTrainNumber(exactK)

        val subMap = tripScheduleMap[exactK]
            ?: tripScheduleMap[baseK]
            ?: (if (trainNo != null) tripScheduleMap[trainNo] else null)
            ?: return null

        if (stationId.isNotBlank() && subMap.containsKey(stationId)) {
            return subMap[stationId]
        }

        val norm = normalizeName(stationName)
        if (norm.isNotBlank() && subMap.containsKey(norm)) {
            return subMap[norm]
        }

        if (norm.isNotBlank()) {
            for ((k, v) in subMap) {
                if (k.isNotEmpty() && (norm.contains(k) || k.contains(norm))) {
                    return v
                }
            }
        }
        return null
    }

    fun addMinutesToTime(timeStr: String, delayMin: Int): String {
        if (timeStr.isBlank() || !timeStr.contains(":")) return timeStr
        val parts = timeStr.trim().split(":")
        val h = parts[0].toIntOrNull() ?: return timeStr
        val m = parts[1].toIntOrNull() ?: return timeStr

        var totalM = h * 60 + m + delayMin
        totalM = ((totalM % (24 * 60)) + (24 * 60)) % (24 * 60)

        val newH = totalM / 60
        val newM = totalM % 60
        return String.format(Locale.getDefault(), "%02d:%02d", newH, newM)
    }

    private fun normalizeLineId(lineId: String): String {
        val clean = lineId.uppercase().replace("-", "").trim()
        return if (clean.startsWith("C") && clean.length > 1) clean else "C$clean"
    }

    private fun cleanStationName(s: String): String {
        if (s.isBlank()) return ""
        return s.replace("Buã‘ol", "Buñol", ignoreCase = true)
            .replace("Puã‡ol", "Puçol", ignoreCase = true)
            .replace("bua‘ol", "buñol", ignoreCase = true)
            .replace("pua‡ol", "puçol", ignoreCase = true)
    }

    fun normalizeName(s: String): String {
        if (s.isBlank()) return ""
        val cleaned = cleanStationName(s)
        val normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
        val stripped = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()

        return stripped
            .replace("valencia-", "")
            .replace("valencia ", "")
            .replace("valencia", "")
            .replace("estacio del nord", "nord")
            .replace("f. s. lluis", "font de sant lluis")
            .replace("castellon", "castello")
            .replace("platja i grau de gandia", "platja de gandia")
            .replace("direccion", "")
            .replace("direccio", "")
            .replace("bunyol", "buñol")
            .replace("l'alcudia de crespins", "alcudia")
            .replace("alcudia de crespins", "alcudia")
            .replace("l'alcudia", "alcudia")
            .replace("-", " ")
            .replace("/", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun isSameStationName(name1: String, name2: String): Boolean {
        val n1 = normalizeName(name1)
        val n2 = normalizeName(name2)
        if (n1.isEmpty() || n2.isEmpty()) return false
        if (n1 == n2) return true
        
        // Prevent "albal" from matching "albalat" (e.g. Estivella-Albalat dels Tarongers)
        if ((n1 == "albal" && n2.contains("albalat")) || (n2 == "albal" && n1.contains("albalat"))) {
            return false
        }

        // Prevent "gandia" from falsely matching "platja de gandia"
        if ((n1.contains("platja") && !n2.contains("platja")) || (!n1.contains("platja") && n2.contains("platja"))) {
            return false
        }
        
        return n1.contains(n2) || n2.contains(n1)
    }

    fun getRemainingStops(
        originStationId: String,
        originStationName: String,
        destinationName: String,
        routeId: String
    ): List<CercaniasLineStationInfo> {
        val lineKey = normalizeLineId(routeId)
        val stations = lineStationsMap[lineKey] ?: emptyList()
        if (stations.isEmpty()) return emptyList()

        var originIndex = stations.indexOfFirst { it.id == originStationId }
        if (originIndex == -1) {
            originIndex = stations.indexOfFirst { isSameStationName(it.name, originStationName) }
        }

        var destIndex = stations.indexOfFirst { isSameStationName(it.name, destinationName) }
        if (destIndex == -1) {
            val normDest = normalizeName(destinationName)
            destIndex = stations.indexOfFirst { st ->
                val normSt = normalizeName(st.name)
                normSt.isNotEmpty() && normDest.isNotEmpty() && (normSt.contains(normDest) || normDest.contains(normSt))
            }
        }

        if (originIndex == -1 || destIndex == -1) {
            if (originIndex != -1 && destIndex == -1) {
                if (originIndex < stations.size - 1) {
                    return stations.subList(originIndex + 1, stations.size)
                }
            }
            return emptyList()
        }

        if (originIndex == destIndex) {
            return emptyList()
        }

        return if (originIndex < destIndex) {
            stations.subList(originIndex + 1, destIndex + 1)
        } else {
            stations.subList(destIndex, originIndex).reversed()
        }
    }

    fun getOriginStationInfo(
        originStationId: String,
        originStationName: String,
        routeId: String
    ): CercaniasLineStationInfo? {
        val lineKey = normalizeLineId(routeId)
        val stations = lineStationsMap[lineKey] ?: emptyList()
        
        var match = stations.find { it.id == originStationId }
        if (match == null) {
            match = stations.find { isSameStationName(it.name, originStationName) }
        }
        if (match != null) return match

        // Search across all lines if not on current routeId list directly
        for ((_, lineList) in lineStationsMap) {
            val st = lineList.find { it.id == originStationId || isSameStationName(it.name, originStationName) }
            if (st != null) return st
        }

        return if (originStationName.isNotBlank()) {
            CercaniasLineStationInfo(id = originStationId, name = originStationName)
        } else null
    }

    fun extractTrainNumber(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val m1 = Regex("""[A-Za-z_](\d{4,5})(?=[A-Za-z]|$)""").find(input)
        if (m1 != null) return m1.groupValues[1]
        val m2 = Regex("""\b\d{4,5}\b""").find(input)
        if (m2 != null) return m2.value
        val m3 = Regex("""\d{5}""").find(input)
        return m3?.value
    }

    fun getEffectiveTrainNumber(trainNum: String?, tripId: String? = null): String {
        val num = (trainNum ?: "").trim()
        if (num.isNotBlank() && num.all { it.isDigit() }) return num
        val extracted = extractTrainNumber(num.ifBlank { tripId ?: "" })
        if (!extracted.isNullOrBlank()) return extracted
        return num.ifBlank { tripId ?: "" }
    }

    fun getCanonicalLineNumber(routeId: String?, tripId: String? = null): String {
        val r = (routeId ?: "").trim()
        val t = (tripId ?: "").trim()
        val combined = "$r $t".uppercase(Locale.ROOT)

        // 1. Direct line label checks
        for (digit in listOf("1", "2", "3", "4", "5", "6")) {
            if (combined.contains("C$digit") || combined.contains("C-$digit") || combined.contains("C_$digit") ||
                combined.contains("LÍNEA $digit") || combined.contains("LINEA $digit")) {
                return digit
            }
        }

        // 2. Nucleus 40 prefix: e.g. "401" -> "1", "402" -> "2", "403" -> "3", "405" -> "5", "406" -> "6"
        val match40 = Regex("""\b40([1-6])\b""").find(r)
        if (match40 != null) {
            return match40.groupValues[1]
        }

        val match40Anywhere = Regex("""40(?:C-?)?([1-6])""").find(combined)
        if (match40Anywhere != null) {
            return match40Anywhere.groupValues[1]
        }

        // 3. Single standalone digit 1..6
        val matchSingleDigit = Regex("""\b([1-6])\b""").find(r)
        if (matchSingleDigit != null) {
            return matchSingleDigit.groupValues[1]
        }

        // 4. Suffix digit in route or trip
        val matchEndDigit = Regex("""[_-]?([1-6])$""").find(r)
        if (matchEndDigit != null) {
            return matchEndDigit.groupValues[1]
        }

        return "1"
    }

    fun hasTripInSchedule(tripId: String, trainNum: String? = null): Boolean {
        val keys = getTripKeys(tripId) + (if (trainNum != null) getTripKeys(trainNum) else emptyList())
        return keys.any { tripScheduleMap.containsKey(it) }
    }

    fun getTripOriginAndDestination(tripId: String, routeId: String, trainNum: String? = null): Pair<String, String>? {
        if (tripId.isBlank() && (trainNum == null || trainNum.isBlank())) return null
        val keys = getTripKeys(tripId) + (if (!trainNum.isNullOrBlank()) getTripKeys(trainNum) else emptyList())
        var subMap: Map<String, String>? = null
        for (k in keys) {
            val m = tripScheduleMap[k]
            if (m != null && m.isNotEmpty()) {
                subMap = m
                break
            }
        }
        if (subMap == null || subMap.isEmpty()) return null

        // In subMap, we have entries where key is either stopId (digit) or normalized name (non-digit), and value is time (HH:mm)
        // Let's filter to entries where key is a stopId (digits)
        val stopEntries = subMap.entries.filter { it.key.all { c -> c.isDigit() } }
        if (stopEntries.isEmpty()) return null

        // Sort by time
        val sorted = stopEntries.sortedBy { it.value }
        val firstStopId = sorted.first().key
        val lastStopId = sorted.last().key

        // Now look up names of these stopIds using our stations list
        val lineKey = normalizeLineId(routeId)
        val stations = lineStationsMap[lineKey] ?: lineStationsMap.values.flatten()
        val originName = stations.find { it.id == firstStopId }?.name ?: ""
        val destName = stations.find { it.id == lastStopId }?.name ?: ""

        if (originName.isNotBlank() && destName.isNotBlank()) {
            return Pair(originName, destName)
        }
        return null
    }
}
