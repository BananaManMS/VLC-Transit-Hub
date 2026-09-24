package com.example.ui.transit

import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtBusTime
import com.example.ui.bus.MetrobusDepartureUiModel
import com.example.ui.bus.MetrobusStop

enum class TransitOperator(val displayName: String, val colorHex: Long) {
    EMT("EMT", 0xFFC62828),
    METROBUS("Metrobús", 0xFFD97706)
}

data class UnifiedTransitDeparture(
    val id: String,
    val lineCode: String,
    val destination: String,
    val minutesRemaining: Int,
    val secondsRemaining: Int,
    val formattedEstimatedTime: String,
    val isRealTime: Boolean,
    val operator: TransitOperator,
    val isDiverted: Boolean = false,
    val divertedMessage: String? = null
)

data class UnifiedTransitStop(
    val id: String,
    val name: String,
    val alias: String? = null,
    val distanceText: String = "",
    val availableLines: List<String> = emptyList(),
    val operator: TransitOperator,
    val isFavorite: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null
)

fun EmtBusTime.toUnifiedDeparture(index: Int = 0): UnifiedTransitDeparture {
    val mins = minutos.toIntOrNull() ?: if (minutos.contains(":")) -1 else 0
    return UnifiedTransitDeparture(
        id = "emt_${linea}_${destino}_${minutos}_$index",
        lineCode = linea.trim(),
        destination = destino.trim(),
        minutesRemaining = mins,
        secondsRemaining = secondsRemaining,
        formattedEstimatedTime = horaLlegada.trim(),
        isRealTime = isRealTime,
        operator = TransitOperator.EMT,
        isDiverted = isDiverted,
        divertedMessage = divertedMessage
    )
}

fun MetrobusDepartureUiModel.toUnifiedDeparture(index: Int = 0): UnifiedTransitDeparture {
    return UnifiedTransitDeparture(
        id = "metrobus_${lineCode}_${destination}_${departureTime}_$index",
        lineCode = lineCode.trim(),
        destination = destination.trim(),
        minutesRemaining = minutesRemaining,
        secondsRemaining = secondsRemaining,
        formattedEstimatedTime = departureTime.trim(),
        isRealTime = isRealTime,
        operator = TransitOperator.METROBUS
    )
}

fun EmtBusStop.toUnifiedStop(alias: String? = null, isFavorite: Boolean = false): UnifiedTransitStop {
    val linesFromUtes = utes.map { it.id_linea.trim() }.filter { it.isNotEmpty() }
    val cleanName = me.replace(Regex("\\s*\\([^)]+\\)\\s*$"), "").trim()
    return UnifiedTransitStop(
        id = opId,
        name = cleanName,
        alias = alias,
        distanceText = distanceText,
        availableLines = linesFromUtes.distinct().sortedWith(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE }),
        operator = TransitOperator.EMT,
        isFavorite = isFavorite,
        latitude = t.toDoubleOrNull(),
        longitude = n.toDoubleOrNull()
    )
}

fun MetrobusStop.toUnifiedStop(alias: String? = null, isFavorite: Boolean = false): UnifiedTransitStop {
    val cleanLines = lineas.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    return UnifiedTransitStop(
        id = idParada,
        name = denominacion.trim(),
        alias = alias,
        distanceText = distanceText,
        availableLines = cleanLines,
        operator = TransitOperator.METROBUS,
        isFavorite = isFavorite,
        latitude = lat,
        longitude = lon
    )
}
