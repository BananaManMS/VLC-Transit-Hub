package com.example.ui.bus

data class MetrobusStop(
    val idParada: String,
    val denominacion: String,
    val lat: Double,
    val lon: Double,
    val lineas: List<String> = emptyList(),
    val distanceText: String? = null
)

data class MetrobusDepartureUiModel(
    val lineCode: String,
    val destination: String,
    val departureTime: String,
    val minutesRemaining: Int,
    val timeLabel: String,
    val agencyName: String = "",
    val routeColor: String = "",
    val lineName: String = "",
    val ocupacion: String = "",
    val vehicleId: String? = null,
    val isRealTime: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tripId: String = "",
    val secondsRemaining: Int = 0
)

data class MetrobusLineInfo(
    val lineCode: String,
    val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val concesion: String
)

data class MetrobusShapeData(
    val lineCode: String,
    val polylinesByDirection: Map<String, List<List<Double>>>
)
