package com.example.data.model

data class MetroStation(
    val id: String,
    val name: String,
    val lines: List<String> = emptyList(),
    val zone: String = "A",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accessibility: Boolean = true,
    val isFavorite: Boolean = false,
    val description: String = zone
)

data class Departure(
    val line: String,
    val destination: String,
    val minutesRemaining: Int,
    val destinationHeader: String = destination,
    val isRealTime: Boolean = true,
    val vehicleId: String? = null,
    val isImminent: Boolean = minutesRemaining <= 1
)



data class MetroScheduledDeparture(
    val line: String = "",
    val destination: String = "",
    val departureTime: String = "",
    val isRealTime: Boolean = false,
    val dateIndex: Int = 0,
    val timeMinutes: Int = 0,
    val timeFormatted: String = departureTime,
    val destinationName: String = destination,
    val originName: String = "",
    val originWebId: Int? = null,
    val destinationWebId: Int? = null,
    val trainServiceId: Int = 0
)

data class MetroScheduledStopPass(
    val stopId: String = "",
    val stopName: String = "",
    val scheduledTime: String = "",
    val stationName: String = stopName,
    val stationWebId: Int = 0,
    val stationFgvId: String = "",
    val timeMinutes: Int = 0,
    val timeFormatted: String = scheduledTime,
    val isCurrentStation: Boolean = false,
    val isOrigin: Boolean = false,
    val isDestination: Boolean = false,
    val isPassed: Boolean = false
)

data class MetroTrainTimeline(
    val tripId: String = "",
    val trainServiceId: Int = 0,
    val line: String = "",
    val originName: String = "",
    val originWebId: Int? = null,
    val destinationName: String = "",
    val destinationWebId: Int? = null,
    val currentStationName: String = "",
    val currentStationWebId: Int? = null,
    val nextStationName: String = "",
    val remainingMinutes: Int = 0,
    val stops: List<MetroScheduledStopPass> = emptyList()
)

data class MetroRouteBlueprintLeg(
    val legIndex: Int = 0,
    val line: String = "",
    val fromStationId: String = "",
    val fromStationName: String = "",
    val toStationId: String = "",
    val toStationName: String = "",
    val isTransferStation: Boolean = false,
    val transferStationName: String? = null,
    val transferBufferMinutes: Int = 1
)

data class MetroRouteBlueprint(
    val originStationId: String = "",
    val originStationName: String = "",
    val destinationStationId: String = "",
    val destinationStationName: String = "",
    val totalTransfers: Int = 0,
    val estimatedDurationMinutes: Int = 0,
    val legs: List<MetroRouteBlueprintLeg> = emptyList(),
    val isOppositeDirection: Boolean = false,
    val oppositeDirectionTerminalName: String? = null,
    val scheduledDepartureTime: String? = null,
    val scheduledArrivalTime: String? = null,
    val isDirect: Boolean = true,
    val intermediateStopsCount: Int = 0,
    val stopsList: List<String> = emptyList()
)

data class MetroJourneyLeg(
    val legIndex: Int = 0,
    val line: String = "",
    val trainServiceId: Int = 0,
    val departureTimeFormatted: String = "",
    val departureMinutesOfDay: Int = 0,
    val arrivalTimeFormatted: String = "",
    val arrivalMinutesOfDay: Int = 0,
    val originStationName: String = "",
    val destinationStationName: String = "",
    val stops: List<MetroScheduledStopPass> = emptyList(),
    val isRealTime: Boolean = false,
    val liveRemainingMinutes: Int? = null,
    val isTransferNext: Boolean = false,
    val transferStationName: String? = null,
    val transferWaitMinutes: Int = 0
)

data class MetroJourneySimulation(
    val originStationName: String = "",
    val destinationStationName: String = "",
    val totalDurationMinutes: Int = 0,
    val departureTimeFormatted: String = "",
    val arrivalTimeFormatted: String = "",
    val totalTransfers: Int = 0,
    val legs: List<MetroJourneyLeg> = emptyList(),
    val isRealTimeAnchored: Boolean = true
)

data class MetroLineInfo(
    val id: String,
    val colorHex: String,
    val name: String = "Línea $id",
    val destinations: List<String> = emptyList()
)

object ValenciaMetroData {
    val lines = listOf(
        MetroLineInfo("1", "#E1A92A", "Línea 1", listOf("Bétera", "Castelló")),
        MetroLineInfo("2", "#B3257D", "Línea 2", listOf("Llíria", "Torrent Avinguda")),
        MetroLineInfo("3", "#C41833", "Línea 3", listOf("Rafelbunyol", "Aeroport")),
        MetroLineInfo("4", "#1E4B90", "Línea 4", listOf("Mas del Rosari", "Dr. Lluch")),
        MetroLineInfo("5", "#068E63", "Línea 5", listOf("Marítim", "Aeroport")),
        MetroLineInfo("6", "#7657AA", "Línea 6", listOf("Tossal del Rei", "Marítim")),
        MetroLineInfo("7", "#DA7A18", "Línea 7", listOf("Marítim", "Torrent Avinguda")),
        MetroLineInfo("8", "#52BACC", "Línea 8", listOf("Marítim", "Neptú")),
        MetroLineInfo("9", "#A16E42", "Línea 9", listOf("Alboraya Peris Aragó", "Riba-roja de Túria")),
        MetroLineInfo("10", "#B3CB6D", "Línea 10", listOf("Alacant", "Natzaret"))
    )

    fun getLine(lineId: String): MetroLineInfo? {
        val cleanId = lineId.replace("L", "", ignoreCase = true).trim()
        return lines.find {
            it.id.equals(lineId, ignoreCase = true) ||
            it.id.equals(cleanId, ignoreCase = true) ||
            "L${it.id}".equals(lineId, ignoreCase = true)
        }
    }

    val mainMetroStations = listOf(
        MetroStation("1", "Xàtiva", listOf("3", "5", "9"), "A", 39.4667, -0.3768),
        MetroStation("2", "Colón", listOf("3", "5", "7", "9"), "A", 39.4692, -0.3711),
        MetroStation("3", "Àngel Guimerà", listOf("1", "2", "3", "5", "9"), "A", 39.4715, -0.3831),
        MetroStation("4", "Benimaclet", listOf("3", "9", "4", "6"), "A", 39.4855, -0.3582),
        MetroStation("5", "Plaza de España", listOf("1", "2"), "A", 39.4678, -0.3808),
        MetroStation("6", "Roses", listOf("3", "5", "9"), "B", 39.4811, -0.4491),
        MetroStation("7", "La Carrasca", listOf("4", "6"), "A", 39.4795, -0.3421)
    )

    @Volatile
    var allNetworkStationsCache: List<MetroStation> = emptyList()

    fun findStation(idOrName: String): MetroStation? {
        val trimmed = idOrName.trim()
        return allNetworkStationsCache.find { it.id.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
            ?: mainMetroStations.find { it.id.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
    }
}

fun cleanZoneCode(zone: String?): String {
    if (zone.isNullOrBlank()) return "A"
    val trimmed = zone.trim()

    // Match "Zona A", "Zona B", "Zona AB", "Zona A B", "Zona +", etc.
    val zoneRegexMatch = Regex("""(?i)\b(?:zona|zone)\s*([AB\+]|A\s*B|\+)""").find(trimmed)
    if (zoneRegexMatch != null) {
        val extracted = zoneRegexMatch.groupValues[1].replace(" ", "").uppercase()
        if (extracted in listOf("A", "B", "AB", "+")) return extracted
    }

    // Strip words like "Metrovalencia", "Zona", "Zone", "Metro", "FGV", punctuation, etc.
    val stripped = trimmed
        .replace(Regex("""(?i)\b(zona|zone|metrovalencia|metro|fgv)\b"""), "")
        .replace("•", "")
        .replace("-", "")
        .replace("/", "")
        .replace(" ", "")
        .trim()
        .uppercase()

    if (stripped in listOf("A", "B", "AB", "+")) {
        return stripped
    }

    val directUpper = trimmed.replace(" ", "").uppercase()
    if (directUpper in listOf("A", "B", "AB", "+")) {
        return directUpper
    }

    return "A"
}
