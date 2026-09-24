package com.example.ui.bus

enum class BusFilterSource { FAVORITES_BUS, GPS_USER, METRO_STATION }

data class EmtBusStop(
    val t: String = "",
    val n: String = "",
    val me: String = "0.0",
    val utes: List<EmtRoute> = emptyList(),
    val opId: String = "",
    val ica: String = "0.0",
    val distanceText: String = "",
    val latitude: Double = me.toDoubleOrNull() ?: 0.0,
    val longitude: Double = ica.toDoubleOrNull() ?: 0.0,
    val description: String = n
) {
    val stopId: String get() = t
    val stopName: String get() = n
    val lat: Double get() = latitude
    val lon: Double get() = longitude
    val lineas: String get() = utes.joinToString(",") { it.SN }
    val lines: String get() = lineas
}

data class EmtRoute(
    val id_linea: String = "",
    val SN: String = ""
)

data class EmtBusTime(
    val linea: String = "",
    val destino: String = "",
    val minutos: String = "",
    val horaLlegada: String = "",
    val secondsRemaining: Int = -1,
    val isRealTime: Boolean = true,
    val isDiverted: Boolean = false,
    val divertedMessage: String = ""
) {
    val line: String get() = linea
    val destination: String get() = destino
    val minutes: Int get() = minutos.toIntOrNull() ?: 0
    val colorHex: String? get() = null
    val estimatedTime: String get() = horaLlegada
    val status: String get() = ""
    val track: String get() = ""
    val capacidad: String get() = ""
}
