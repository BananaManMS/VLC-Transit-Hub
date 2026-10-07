package com.example.ui.metro

data class MetroIncident(
    val id: String,
    val descriptionEs: String,
    val descriptionCa: String,
    val descriptionEn: String,
    val lineaFgv: String?,
    val updatedAt: String?,
    val category: String? = null
)

data class AccessibilityIncident(
    val id: String,
    val tituloEs: String,
    val descripcionEs: String,
    val tituloCa: String,
    val descripcionCa: String,
    val creadoEl: String? = null,
    val estacionId: Int? = null,
    val estacionNombre: String? = null,
    val lineasAfectadas: String? = null
) {
    val name: String get() = tituloEs
}

data class AforoBloqueado(
    val desde: String? = null,
    val hasta: String? = null,
    val activo: Boolean = false
) {
    fun getFormattedTimeSpan(): String? {
        val d = parseTime(desde)
        val h = parseTime(hasta)
        return when {
            !d.isNullOrEmpty() && !h.isNullOrEmpty() -> "$d - $h"
            !d.isNullOrEmpty() -> "Desde $d"
            !h.isNullOrEmpty() -> "Hasta $h"
            else -> null
        }
    }

    private fun parseTime(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.trim().equals("null", ignoreCase = true)) return null
        val trimmed = raw.trim()
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.size >= 2) return "${parts[0].padStart(2, '0')}:${parts[1].padStart(2, '0')}"
            return trimmed
        }
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length == 4) {
            return "${digits.substring(0, 2)}:${digits.substring(2, 4)}"
        }
        return trimmed
    }
}

data class RealTimeDeparture(
    val lineId: String,
    val destination: String,
    val minutesRemaining: Int,
    val secondsRemaining: Int = minutesRemaining * 60,
    val colorHex: String = "#EE1D23",
    val estimatedTime: String? = null,
    val status: String? = null,
    val track: String? = null,
    val capacidad: Int? = null,
    val aforoBloqueado: AforoBloqueado? = null,
    val vehicleId: String? = null,
    val trainServiceId: Int? = null,
    val originStationName: String? = null,
    val originWebId: Int? = null,
    val destinationWebId: Int? = null,
    val targetArrivalEpochMs: Long = System.currentTimeMillis() + (secondsRemaining.coerceAtLeast(-10) * 1000L),
    val id: String = "${lineId}_${destination}_${targetArrivalEpochMs / 30_000L}",
    val isRealTime: Boolean = true,
    val originStationId: String? = null
) {
    val liveSecondsRemaining: Int
        get() = ((targetArrivalEpochMs - System.currentTimeMillis()) / 1000L).toInt()

    val liveMinutesRemaining: Int
        get() = kotlin.math.max(0, liveSecondsRemaining / 60)

    val carsCount: Int?
        get() {
            val cleanLine = lineId.replace("L", "").trim()
            // Strictly metro lines 1, 2, 3, 5, 7, 9 (NOT tram lines 4, 6, 8, 10)
            if (cleanLine !in listOf("1", "2", "3", "5", "7", "9")) return null
            val cap = capacidad
            return when {
                cap == null -> 4
                cap == 4 || cap in 300..499 -> 4
                cap == 5 || cap >= 500 -> 5
                else -> 4
            }
        }
}

data class MetroNotice(
    val id: String,
    val category: String,
    val categoryName: String,
    val title: String,
    val description: String,
    val publicationDate: String? = null,
    val lineasAfectadas: String? = null
)

data class MetroNewsItem(
    val id: Long,
    val idFgv: Long? = null,
    val title: String,
    val description: String,
    val content: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val publicationDate: String? = null
)
