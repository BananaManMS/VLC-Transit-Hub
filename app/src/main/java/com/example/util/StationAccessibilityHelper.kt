package com.example.util

import java.text.Normalizer
import java.util.Locale

/**
 * Utility for matching station names strictly within accessibility alert titles
 * for both Metrovalencia and Renfe Cercanías APIs.
 */
object StationAccessibilityHelper {

    private val EXCLUDED_PHRASES = listOf(
        "sillas de ruedas",
        "silla de ruedas",
        "sillas de rueda",
        "silla de rueda"
    )

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTIPLE_SPACES_REGEX = Regex("\\s+")

    private fun clean(str: String): String {
        val unaccented = Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
        return unaccented.lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .replace(MULTIPLE_SPACES_REGEX, " ")
            .trim()
    }

    /**
     * Checks if [stationName] appears in [alertTitle], strictly ignoring descriptions.
     * Handles accents, case insensitivity, word boundaries, and common Valencian/Spanish synonyms.
     */
    fun isStationInAlertTitle(stationName: String, alertTitle: String): Boolean {
        if (stationName.isBlank() || alertTitle.isBlank()) return false

        // 1. Remove excluded phrases like "silla de ruedas" from title so "Silla" station isn't falsely matched
        var titleToClean = alertTitle.lowercase(Locale.ROOT)
        for (phrase in EXCLUDED_PHRASES) {
            titleToClean = titleToClean.replace(phrase, " ")
        }
        val normTitle = clean(titleToClean)
        val normStation = clean(stationName)

        if (normStation.length < 3 || normTitle.isBlank()) return false

        // 2. Direct full-name word-boundary match
        val fullRegex = Regex("""\b${Regex.escape(normStation)}\b""")
        if (fullRegex.containsMatchIn(normTitle)) {
            return true
        }

        // 3. Bilingual variants (nord <-> norte, jativa <-> xativa)
        if (normStation.contains("nord")) {
            val norteVariant = normStation.replace("nord", "norte")
            if (Regex("""\b${Regex.escape(norteVariant)}\b""").containsMatchIn(normTitle)) {
                return true
            }
        }
        if (normStation.contains("norte")) {
            val nordVariant = normStation.replace("norte", "nord")
            if (Regex("""\b${Regex.escape(nordVariant)}\b""").containsMatchIn(normTitle)) {
                return true
            }
        }
        if (normStation.contains("xativa")) {
            val jativaVariant = normStation.replace("xativa", "jativa")
            if (Regex("""\b${Regex.escape(jativaVariant)}\b""").containsMatchIn(normTitle)) {
                return true
            }
        }
        if (normStation.contains("jativa")) {
            val xativaVariant = normStation.replace("jativa", "xativa")
            if (Regex("""\b${Regex.escape(xativaVariant)}\b""").containsMatchIn(normTitle)) {
                return true
            }
        }

        // 4. Compound station name parts (e.g., "Facultats - Manuel Broseta", "Marítim - Serrería")
        val parts = stationName.split("-", "/", "(")
            .map { clean(it) }
            .filter { it.length >= 4 && !it.contains("estacion") && !it.contains("estacio") }

        for (part in parts) {
            if (part != normStation && Regex("""\b${Regex.escape(part)}\b""").containsMatchIn(normTitle)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if a Metrovalencia station is affected by a given accessibility incident.
     * Strictly matches stationId, estacionNombre, or alert title header.
     * NEVER scans description text, which often mentions platforms or destinations (e.g., "andén destino Aeroport").
     */
    fun isMetroStationAffected(
        stationId: String,
        stationName: String,
        incident: com.example.ui.metro.AccessibilityIncident
    ): Boolean {
        // 1. Direct stationId match (normalizing numeric IDs to strip leading zeros or prefixes)
        if (incident.estacionId != null) {
            val incIdClean = incident.estacionId.toString().replace(Regex("[^0-9]"), "").trimStart('0')
            val targetIdClean = stationId.replace(Regex("[^0-9]"), "").trimStart('0')
            if (incIdClean.isNotBlank() && incIdClean == targetIdClean) {
                return true
            }
            if (incident.estacionId.toString().trim() == stationId.trim()) {
                return true
            }
        }

        // 2. Direct estacionNombre match (if provided by the API, the alert belongs strictly to that station)
        val incEstNombre = incident.estacionNombre?.trim()
        if (!incEstNombre.isNullOrBlank()) {
            val cleanStation = clean(stationName)
            val cleanIncEst = clean(incEstNombre)
            if (cleanStation == cleanIncEst || isStationInAlertTitle(stationName, incEstNombre)) {
                return true
            }
            // Explicit station specified by API does not match this station
            return false
        }

        // 3. Fallback: If no station_id or estacion_nombre was provided, check ONLY the alert title
        if (isStationInAlertTitle(stationName, incident.tituloEs) || isStationInAlertTitle(stationName, incident.tituloCa)) {
            return true
        }

        return false
    }

    /**
     * Checks if a Cercanías station is affected by a given accessibility alert.
     * Strictly matches stopIds or header titles.
     * NEVER scans description text.
     */
    fun isCercaniasStationAffected(
        stopId: String,
        stationNombre: String,
        stationDisplayName: String,
        alert: com.example.ui.cercanias.CercaniasAlert
    ): Boolean {
        // 1. Stop IDs match
        if (alert.stopIds.isNotEmpty()) {
            val cleanStopId = stopId.substringBefore('_').substringBefore('-').trim()
            val cleanStopIdNo60 = cleanStopId.removePrefix("60")
            if (alert.stopIds.any { sId ->
                    val cleanS = sId.substringBefore('_').substringBefore('-').trim()
                    cleanS == cleanStopId || cleanS == cleanStopIdNo60 || cleanS.removePrefix("60") == cleanStopIdNo60
                }) {
                return true
            }
            return false
        }

        // 2. Fallback: Header / title search only
        if (isStationInAlertTitle(stationNombre, alert.headerEs) || isStationInAlertTitle(stationDisplayName, alert.headerEs)) {
            return true
        }

        return false
    }

    /**
     * Cleans redundant repetitive prefixes from accessibility alert texts
     * (e.g., "Afectación de accesibilidad en la estación Jesús: Avería en ascensor..." -> "Avería en ascensor...").
     */
    fun cleanAccessibilityAlertText(text: String): String {
        if (text.isBlank()) return ""
        val cleaned = text.replace(
            Regex(
                """^(?:Afectaci[oó]n?\s+(?:de|d')\s*accessibilitat|Afectaci[oó]n?\s+de\s+accesibilidad|Incid[eè]ncia?\s+(?:de|d')\s*accessibilitat|Incidencia\s+de\s+accesibilidad)(?:\s+(?:en|a)\s+(?:la\s+estaci[oó]n|l'estaci[oó]))?\s*[^:]*:\s*""",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trim()
        return cleaned.ifBlank { text.trim() }
    }
}

object AccessibilityNoticeFormatter {
    fun cleanAccessibilityNoticeText(rawText: String, stationName: String? = null): String {
        if (rawText.isBlank()) return ""
        var result = rawText
        if (!stationName.isNullOrBlank()) {
            val stationClean = stationName.trim()
            result = result.replace(Regex("""(?i)\b(?:en|a)\s+(?:la\s+estaci[oó]n|l'estaci[oó])\s+(?:de\s+)?${Regex.escape(stationClean)}\b"""), "")
        }
        return StationAccessibilityHelper.cleanAccessibilityAlertText(result)
    }

    fun deduplicateAccessibilityIncidents(
        incidents: List<com.example.ui.metro.AccessibilityIncident>,
        stationName: String? = null
    ): List<com.example.ui.metro.AccessibilityIncident> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<com.example.ui.metro.AccessibilityIncident>()
        for (inc in incidents) {
            val key = cleanAccessibilityNoticeText(inc.descripcionEs.ifBlank { inc.tituloEs }, stationName).lowercase(Locale.ROOT)
            if (seen.add(key)) {
                result.add(inc)
            }
        }
        return result
    }
}

