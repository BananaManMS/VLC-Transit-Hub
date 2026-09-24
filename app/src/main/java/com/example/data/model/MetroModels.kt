package com.example.data.model

data class MetroStation(
    val id: String,
    val name: String,
    val lines: List<String> = emptyList(),
    val zone: String = "A",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accessibility: Boolean = true,
    val isFavorite: Boolean = false
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

data class ForecastHour(
    val hour: Int = 12,
    val occupancyPercentage: Int = 30,
    val time: String = "$hour:00",
    val tempCelsius: Double = 22.0,
    val condition: WeatherCondition = WeatherCondition.SUNNY
) {
    constructor(time: String, tempCelsius: Double) : this(
        hour = time.takeWhile { it.isDigit() }.toIntOrNull() ?: 12,
        occupancyPercentage = 30,
        time = time,
        tempCelsius = tempCelsius
    )
}

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

data class MetroLineInfo(val id: String, val colorHex: String)

object ValenciaMetroData {
    val lines = listOf(
        MetroLineInfo("1", "#E2001A"),
        MetroLineInfo("2", "#A60067"),
        MetroLineInfo("3", "#EE1D23"),
        MetroLineInfo("4", "#00A859"),
        MetroLineInfo("5", "#0083B9"),
        MetroLineInfo("6", "#883A88"),
        MetroLineInfo("7", "#F08200"),
        MetroLineInfo("8", "#009999"),
        MetroLineInfo("9", "#8B5B29"),
        MetroLineInfo("10", "#5E2750")
    )
    val mainMetroStations = listOf(
        MetroStation("1", "Xàtiva", listOf("3", "5", "9"), "A", 39.4667, -0.3768),
        MetroStation("2", "Colón", listOf("3", "5", "7", "9"), "A", 39.4692, -0.3711),
        MetroStation("3", "Àngel Guimerà", listOf("1", "2", "3", "5", "9"), "A", 39.4715, -0.3831),
        MetroStation("4", "Benimaclet", listOf("3", "9", "4", "6"), "A", 39.4855, -0.3582),
        MetroStation("5", "Plaza de España", listOf("1", "2"), "A", 39.4678, -0.3808),
        MetroStation("6", "Roses", listOf("3", "5", "9"), "B", 39.4811, -0.4491),
        MetroStation("7", "La Carrasca", listOf("4", "6"), "A", 39.4795, -0.3421)
    )
}

fun cleanZoneCode(zone: String?): String {
    if (zone.isNullOrBlank()) return "A"
    return zone.trim().uppercase()
}
