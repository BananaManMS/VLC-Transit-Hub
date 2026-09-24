package com.example.ui.bus

import org.json.JSONArray
import org.json.JSONObject

data class MetrobusStop(
    val idParada: String,
    val denominacion: String,
    val lat: Double,
    val lon: Double,
    val lineas: List<String> = emptyList(),
    val distanceText: String = ""
)

data class MetrobusLineInfo(
    val lineCode: String,      // e.g. "106A"
    val routeId: String,       // e.g. "106"
    val routeShortName: String,// e.g. "106A"
    val routeLongName: String, // e.g. "Torrent - C.C. Bonaire - Aeroport"
    val concesion: String      // e.g. "CV106"
)

data class MetrobusDepartureUiModel(
    val lineCode: String,
    val destination: String,
    val departureTime: String = "",
    val minutesRemaining: Int,
    val timeLabel: String,
    val agencyName: String = "Metrobús",
    val routeColor: String? = null,
    val lineName: String? = null,
    val ocupacion: String = "SIN DATOS",
    val vehicleId: Int? = null,
    val isRealTime: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tripId: String? = null,
    val secondsRemaining: Int = if (minutesRemaining > 0) minutesRemaining * 60 else 0
)

data class MetrobusShapeData(
    val lineCode: String,
    val polylinesByDirection: Map<String, String> // "0" -> polyline, "1" -> polyline
)


