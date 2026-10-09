package com.example.util

import com.example.data.repository.MetroArrival
import com.example.ui.metro.RealTimeDeparture
import java.util.Locale

/**
 * Utility for real-time Metrovalencia departure and arrival filtering rules.
 *
 * Specific rules:
 * 1. Facultats, Benimaclet, and Machado section ("tramo de Facultats, Benimaclet y Machado"):
 *    - Permits Lines 5 and 7 (L5, L7) explicitly so that trains retiring to Machado depots ("cocheras de Machado")
 *      appear on screens and panels.
 *    - Strictly excludes Line 10 (L10) to discard erroneous or desynchronized arrivals returned by the FGV API.
 */
object MetroFilterUtils {

    /**
     * Normalizes a station string for comparison (removes diacritics and converts to lowercase).
     */
    fun normalizeStationName(name: String): String {
        return name.trim().lowercase(Locale.ROOT)
            .replace("á", "a").replace("à", "a")
            .replace("é", "e").replace("è", "e")
            .replace("í", "i").replace("ï", "i")
            .replace("ó", "o").replace("ò", "o")
            .replace("ú", "u").replace("ü", "u")
    }

    /**
     * Checks if the station belongs to the Facultats, Benimaclet, Machado section.
     */
    fun isFacultatsBenimacletMachadoSection(stationIdOrName: String?): Boolean {
        if (stationIdOrName.isNullOrBlank()) return false
        val norm = normalizeStationName(stationIdOrName)

        if (norm.contains("facultats") || norm.contains("benimaclet") || norm.contains("machado")) {
            return true
        }

        val cleanDigits = norm.filter { it.isDigit() }
        if (cleanDigits == "11" || cleanDigits == "12" || cleanDigits == "13" || cleanDigits == "4") {
            return true
        }

        return norm.startsWith("vt-010a_01") || norm.startsWith("vt-010a_02") || norm.startsWith("vt-010a_03")
    }

    /**
     * Extracts numeric digits from line string (e.g., "L10" -> "10", "L5" -> "5").
     */
    fun extractLineDigits(lineId: String?): String {
        if (lineId.isNullOrBlank()) return ""
        return lineId.filter { it.isDigit() }
    }

    /**
     * Checks if line represents Line 10 (L10).
     */
    fun isLine10(lineId: String?): Boolean {
        if (lineId.isNullOrBlank()) return false
        val digits = extractLineDigits(lineId)
        if (digits == "10") return true
        val upper = lineId.trim().uppercase(Locale.ROOT)
        return upper == "L10" || upper == "10" || upper.contains("LINEA 10") || upper.contains("LÍNEA 10")
    }

    /**
     * Checks if line represents Line 5 or Line 7 (L5 / L7).
     */
    fun isLine5Or7(lineId: String?): Boolean {
        if (lineId.isNullOrBlank()) return false
        val digits = extractLineDigits(lineId)
        if (digits == "5" || digits == "7") return true
        val upper = lineId.trim().uppercase(Locale.ROOT)
        return upper == "L5" || upper == "L7" || upper == "5" || upper == "7"
    }

    /**
     * Checks if a train service is an L5 or L7 service with origin or destination to Machado
     * (cocheras de Machado early morning departures and late night returns).
     */
    fun isMachadoService(lineId: String?, destination: String? = null, origin: String? = null): Boolean {
        if (!isLine5Or7(lineId)) return false
        val destNorm = destination?.let { normalizeStationName(it) } ?: ""
        val origNorm = origin?.let { normalizeStationName(it) } ?: ""
        return destNorm.contains("machado") || origNorm.contains("machado")
    }

    /**
     * Checks if line represents Line 3 (L3).
     */
    fun isLine3(lineId: String?): Boolean {
        if (lineId.isNullOrBlank()) return false
        val digits = extractLineDigits(lineId)
        if (digits == "3") return true
        val upper = lineId.trim().uppercase(Locale.ROOT)
        return upper == "L3" || upper == "3" || upper.contains("LINEA 3") || upper.contains("LÍNEA 3")
    }

    /**
     * Checks if the station belongs to the La Cova -> Riba-roja de Túria branch:
     * - La Cova (183)
     * - La Presa (184)
     * - València la Vella (188)
     * - Masia de Traver (185)
     * - Riba-roja de Túria (186)
     */
    fun isLaCovaToRibarrojaSection(stationIdOrName: String?): Boolean {
        if (stationIdOrName.isNullOrBlank()) return false
        val norm = normalizeStationName(stationIdOrName)
        if (norm.contains("cova") || norm.contains("presa") ||
            norm.contains("vella") || norm.contains("traver") ||
            norm.contains("riba-roja") || norm.contains("riba roja") ||
            norm.contains("ribarroja")) {
            return true
        }
        val cleanDigits = norm.filter { it.isDigit() }
        return cleanDigits in setOf("183", "184", "185", "188", "186")
    }

    /**
     * Determines if a given line is valid for arrival/departure display at a station.
     */
    fun isValidLineForStation(
        stationIdOrName: String?,
        lineId: String?,
        stationLines: List<String> = emptyList(),
        destination: String? = null,
        origin: String? = null
    ): Boolean {
        if (lineId.isNullOrBlank()) return true

        // Permitir explícitamente Líneas 5 y 7 con origen o destino a Machado (salidas y retiradas a cocheras)
        if (isMachadoService(lineId, destination, origin)) {
            return true
        }

        // Permitir explícitamente Línea 3 (L3) en las estaciones desde La Cova hasta Riba-roja de Túria
        // si aparece en las salidas en vivo de la API de Metrovalencia
        if (isLine3(lineId) && isLaCovaToRibarrojaSection(stationIdOrName)) {
            return true
        }

        val inSection = isFacultatsBenimacletMachadoSection(stationIdOrName)

        if (inSection) {
            // Regla 1: Exclusión estricta de la Línea 10 en Facultats, Benimaclet y Machado
            if (isLine10(lineId)) {
                return false
            }
            // Regla 2: Permitidas Líneas 5 y 7 en Facultats, Benimaclet y Machado para retiradas a cocheras de Machado
            if (isLine5Or7(lineId)) {
                return true
            }
        }

        // Si la estación tiene líneas definidas, comprobamos si coincide
        if (stationLines.isNotEmpty()) {
            val depDigits = extractLineDigits(lineId)
            val matches = stationLines.any { sl ->
                val slDigits = extractLineDigits(sl)
                sl.equals(lineId, ignoreCase = true) ||
                (slDigits.isNotBlank() && depDigits.isNotBlank() && slDigits == depDigits)
            }
            return matches
        }

        return true
    }

    /**
     * Filters a list of MetroArrival objects based on station line validity rules.
     */
    fun filterMetroArrivals(
        stationIdOrName: String?,
        arrivals: List<MetroArrival>,
        stationLines: List<String> = emptyList()
    ): List<MetroArrival> {
        return arrivals.filter { arrival ->
            isValidLineForStation(
                stationIdOrName = stationIdOrName,
                lineId = arrival.line,
                stationLines = stationLines,
                destination = arrival.destination
            )
        }
    }

    /**
     * Filters a list of RealTimeDeparture objects based on station line validity rules.
     */
    fun filterRealTimeDepartures(
        stationIdOrName: String?,
        departures: List<RealTimeDeparture>,
        stationLines: List<String> = emptyList()
    ): List<RealTimeDeparture> {
        return departures.filter { departure ->
            isValidLineForStation(
                stationIdOrName = stationIdOrName,
                lineId = departure.lineId,
                stationLines = stationLines,
                destination = departure.destination
            )
        }
    }
}
