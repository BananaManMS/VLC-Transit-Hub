package com.example.ui.cercanias

import java.util.Locale

/**
 * Classifier to distinguish between real circulation incidents (train cancellations, active delays,
 * catenary/infrastructure failures, meteorological disruptions) and planned works, alternative bus services,
 * and general informative notices (timetables, fares, campaigns).
 */
object CercaniasAlertClassifier {

    fun isAccessibility(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("ascensor") ||
                lower.contains("escalera mecánica") ||
                lower.contains("escalera mecanica") ||
                lower.contains("rampa") ||
                lower.contains("accesibilidad") ||
                lower.contains("accessibilitat") ||
                lower.contains("pmr") ||
                lower.contains("movilidad reducida") ||
                lower.contains("mobilitat reduïda") ||
                lower.contains("adaptado") ||
                lower.contains("adaptat")
    }

    /**
     * Determines whether the notice is a real-time active circulation disruption
     * (train cancellation, active delay/incident, weather disruption, track cut).
     * This avoids marking general informative notices or planned works as critical circulation incidents.
     */
    fun isRealTimeCirculationIncident(headerEs: String, descriptionEs: String): Boolean {
        if (isAccessibility("$headerEs $descriptionEs")) return false
        val cat = CercaniasNoticeCategory.resolveFromText(headerEs, descriptionEs)
        return cat == CercaniasNoticeCategory.SUPRESION ||
               cat == CercaniasNoticeCategory.INCIDENCIA ||
               cat == CercaniasNoticeCategory.ALERTA_METEOROLOGICA
    }

    fun isCirculationNotice(headerEs: String, descriptionEs: String): Boolean {
        return isRealTimeCirculationIncident(headerEs, descriptionEs)
    }

    fun isStrongCirculationIncident(headerEs: String, descriptionEs: String): Boolean {
        return isRealTimeCirculationIncident(headerEs, descriptionEs)
    }

    fun isObras(headerEs: String, descriptionEs: String): Boolean {
        if (isAccessibility("$headerEs $descriptionEs")) return false
        val cat = CercaniasNoticeCategory.resolveFromText(headerEs, descriptionEs)
        return cat == CercaniasNoticeCategory.OBRAS || cat == CercaniasNoticeCategory.PLAN_ALTERNATIVO
    }
}
