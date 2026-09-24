package com.example.util

/**
 * Helper to enforce strict commercial line filtering for Metrovalencia stations,
 * specifically excluding depot/cochera transfer runs.
 *
 * Trains of Line 5 and Line 7 traverse Facultats, Benimaclet, and Machado exclusively
 * to enter and exit the Machado workshops and depot (talleres y cocheras de Machado).
 * They do not offer commercial service for L5/L7 passengers at those stations.
 */
object MetroDepotFilterHelper {

    private val DEPOT_STATION_IDS = setOf(
        "11", // Machado FGV ID
        "12", // Benimaclet FGV ID
        "13", // Facultats FGV ID
        "66", // Machado WebID (schedule)
        "67", // Benimaclet WebID (schedule)
        "68"  // Facultats WebID (schedule)
    )

    private val DEPOT_EXCLUDED_LINES = setOf("5", "7")

    /**
     * Returns true if [line] represents a non-commercial depot/cocheras movement
     * that should be excluded from the departures panel and station schedules.
     */
    fun isDepotExcludedStationLine(
        stationIdOrWebId: String?,
        stationName: String?,
        line: String
    ): Boolean {
        val cleanLine = line.replace("L", "", ignoreCase = true).trim()
        if (cleanLine !in DEPOT_EXCLUDED_LINES) return false

        val id = stationIdOrWebId?.trim() ?: ""
        if (id in DEPOT_STATION_IDS) return true

        val name = stationName?.lowercase() ?: ""
        if (name.contains("facultats") || name.contains("benimaclet") || name.contains("machado")) {
            return true
        }

        return false
    }
}
