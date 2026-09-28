package com.example.ui.bus

enum class BusFilterSource {
    GPS_USER,
    FAVORITES_BUS,
    METRO_STATION
}

data class EmtRoute(
    val id_linea: String,
    val SN: String
)

data class EmtBusStop(
    val t: String,
    val n: String,
    val me: String,
    val utes: List<EmtRoute> = emptyList(),
    val opId: String,
    val ica: String,
    val distanceText: String = ""
)

data class EmtBusTime(
    val linea: String,
    val destino: String,
    val minutos: String,
    val horaLlegada: String,
    val secondsRemaining: Int = 0,
    val isRealTime: Boolean = true,
    val isDiverted: Boolean = false,
    val divertedMessage: String = ""
)
