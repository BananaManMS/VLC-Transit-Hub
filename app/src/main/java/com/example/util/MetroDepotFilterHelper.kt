package com.example.util

import java.util.Locale

object MetroDepotFilterHelper {

    private val L10_STATION_IDS = setOf("190", "191", "192", "193", "194", "195", "196", "197")
    private val L10_STATION_KEYWORDS = listOf(
        "alacant", "russafa", "amado granell", "montolivet", "quatre carreres",
        "ciutat arts", "ciutat de les arts", "oceanografic", "moreres", "natzaret"
    )

    fun isDepotExcludedStationLine(stationId: String?, stationName: String?, line: String): Boolean {
        val cleanLine = line.replace("L", "", ignoreCase = true).trim()
        if (cleanLine.isEmpty()) return true

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
