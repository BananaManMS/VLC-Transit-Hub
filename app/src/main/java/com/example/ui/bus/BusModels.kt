package com.example.ui.bus

enum class BusFilterSource { FAVORITES_BUS, GPS_USER, METRO_STATION }

data class EmtBusStop(
    val t: String,
    val n: String,
    val me: String,
    val utes: List<EmtRoute>,
    val opId: String,
    val ica: String,
    val distanceText: String = ""
)

data class EmtRoute(
    val id_linea: String,
    val SN: String
)

data class EmtBusTime(
    val linea: String,
    val destino: String,
    val minutos: String,
    val horaLlegada: String,
    val secondsRemaining: Int = -1
)
