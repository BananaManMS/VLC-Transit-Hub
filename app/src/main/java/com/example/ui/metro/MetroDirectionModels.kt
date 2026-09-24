package com.example.ui.metro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString

/**
 * Representa una salida secundaria de una misma línea (ej. +12 min a otro destino o al mismo destino).
 */
data class UpcomingDepartureSummary(
    val id: String,
    val destination: String,
    val minutesRemaining: Int,
    val estimatedTime: String? = null,
    val status: String? = null,
    val track: String? = null,
    val originalDeparture: RealTimeDeparture
)

/**
 * Agrupa todas las salidas de una misma línea que van en la misma dirección general.
 */
data class LineDeparturesGroupUiModel(
    val lineId: String,
    val primaryDestination: String,
    val colorHex: String,
    val primaryDeparture: DepartureUiModel,
    val subsequentDepartures: List<UpcomingDepartureSummary>,
    val sharedDigits: List<String>,
    val direction: Int = 0,
    val directionTerminusName: String? = null
)

/**
 * Representa todas las líneas y salidas agrupadas en una dirección física/topológica concreta (Sentido 0 o Sentido 1).
 */
data class MetroDirectionGroup(
    val directionId: Int, // 0 o 1
    val lines: List<LineDeparturesGroupUiModel>
)
