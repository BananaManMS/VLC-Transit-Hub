package com.example.ui.metro

data class MetroIncident(
    val id: String,
    val descriptionEs: String,
    val descriptionCa: String,
    val descriptionEn: String,
    val lineaFgv: String?,
    val updatedAt: String?
)

data class AccessibilityIncident(
    val id: String,
    val tituloEs: String,
    val descripcionEs: String,
    val tituloCa: String,
    val descripcionCa: String,
    val creadoEl: String,
    val estacionId: Int? = null
)

data class RealTimeDeparture(
    val lineId: String,
    val destination: String,
    val minutesRemaining: Int,
    val secondsRemaining: Int,
    val colorHex: String,
    val id: String = java.util.UUID.randomUUID().toString()
)
