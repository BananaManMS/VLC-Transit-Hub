package com.example.util

import java.util.Locale

object MetroDepotFilterHelper {

    private val L10_STATION_IDS = setOf("190", "191", "192", "193", "194", "195", "196", "197")
    private val L10_STATION_KEYWORDS = listOf(
        "alacant", "russafa", "amado granell", "montolivet", "quatre carreres",
        "ciutat arts", "ciutat de les arts", "oceanografic", "moreres", "natzaret"
    )

    // Depot-excluded lines for northern corridor stations:
    // Facultats (FGV ID 13, WebID 68)
    // Benimaclet (FGV ID 12, WebID 67)
    // Machado (FGV ID 11, WebID 66)
    // Commercial lines for these are L3 and L9. Lines 5 and 7 only run here as non-commercial depot transfers.
    private val DEPOT_STATION_IDS = setOf("11", "12", "13", "66", "67", "68")
    private val DEPOT_STATION_KEYWORDS = listOf("facultats", "benimaclet", "machado")

    fun isDepotExcludedStationLine(stationId: String?, stationName: String?, line: String, destination: String? = null): Boolean {
        val cleanLine = line.replace("L", "", ignoreCase = true).trim()
        if (cleanLine.isEmpty()) return true

        if (cleanLine == "5" || cleanLine == "7") {
            // Allow L5 and L7 if it is a Machado service (with origin/destination to Machado cocheras)
            val destNorm = destination?.lowercase(Locale.ROOT) ?: ""
            if (destNorm.contains("machado")) {
                return false
            }
            if (stationId != null && DEPOT_STATION_IDS.contains(stationId.trim())) {
                return true
            }
            if (stationName != null) {
                val norm = stationName.lowercase(Locale.ROOT)
                if (DEPOT_STATION_KEYWORDS.any { norm.contains(it) }) {
                    return true
                }
            }
        }

        if (cleanLine == "10") {
            if (stationId != null && L10_STATION_IDS.contains(stationId.trim())) {
                return false
            }
            if (stationName != null) {
                val norm = stationName.lowercase(Locale.ROOT)
                val matchesL10 = L10_STATION_KEYWORDS.any { norm.contains(it) }
                if (matchesL10) return false
            }
            // Line 10 does not serve standard underground/suburban metro stations
            return true
        }

        return false
    }
}
