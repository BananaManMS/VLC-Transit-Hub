package com.example.ui.cercanias

import java.util.Locale

/**
 * Classifier to distinguish between real circulation incidents (Obras, Cortes,
 * Interrupción, Retrasos, Averías, Supresiones, Bus alternativo) and purely
 * informative notices (Nuevos horarios, tarifas, campañas, avisos generales).
 */
object CercaniasAlertClassifier {

    fun isAccessibility(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("ascensor") ||
                lower.contains("escalera") ||
                lower.contains("rampa") ||
                lower.contains("accesibilidad") ||
                lower.contains("pmr") ||
                lower.contains("movilidad reducida") ||
                lower.contains("adaptado")
    }

    fun isStrongCirculationIncident(headerEs: String, descriptionEs: String): Boolean {
        val fullText = "$headerEs $descriptionEs".lowercase(Locale.ROOT)

        // Informative, schedule announcements, promotional, or generic notices
        val isInformativeNotice = fullText.contains("nuevos horarios") ||
                fullText.contains("nuevo horario") ||
                fullText.contains("nuevos abonos") ||
                fullText.contains("abonos gratuitos") ||
                fullText.contains("tarifas") ||
                fullText.contains("campaña") ||
                fullText.contains("campana") ||
                fullText.contains("promoción") ||
                fullText.contains("promocion") ||
                fullText.contains("cambio de horario") ||
                fullText.contains("horario especial") ||
                fullText.contains("horarios especiales") ||
                fullText.contains("información de servicio") ||
                fullText.contains("informacion de servicio") ||
                fullText.contains("atención al cliente") ||
                fullText.contains("atencion al cliente") ||
                fullText.contains("app cercanías") ||
                fullText.contains("app cercanias") ||
                fullText.contains("renfe informa")

        // Strong circulation keywords required to be considered a disruptive priority alert
        val hasStrongCirculationKeywords = fullText.contains("obra") ||
                fullText.contains("obres") ||
                fullText.contains("trabajos en vía") ||
                fullText.contains("trabajos en via") ||
                fullText.contains("trabajos de mejora") ||
                fullText.contains("mantenimiento") ||
                fullText.contains("corte") ||
                fullText.contains("cortes") ||
                fullText.contains("cortada") ||
                fullText.contains("interrupción") ||
                fullText.contains("interrupcion") ||
                fullText.contains("interrumpid") ||
                fullText.contains("suspendid") ||
                fullText.contains("suspensión") ||
                fullText.contains("suspension") ||
                fullText.contains("sin servicio") ||
                fullText.contains("retraso") ||
                fullText.contains("retrasos") ||
                fullText.contains("demora") ||
                fullText.contains("demoras") ||
                fullText.contains("avería") ||
                fullText.contains("averia") ||
                fullText.contains("incidencia técnica") ||
                fullText.contains("incidencia tecnica") ||
                fullText.contains("incidencia") ||
                fullText.contains("plan alternativo") ||
                fullText.contains("servicio alternativo") ||
                fullText.contains("transbordo") ||
                fullText.contains("trasbordo") ||
                fullText.contains("autobús") ||
                fullText.contains("autobus") ||
                fullText.contains("bus alternativo") ||
                fullText.contains("suprimido") ||
                fullText.contains("suprimida") ||
                fullText.contains("supresión") ||
                fullText.contains("supresion") ||
                fullText.contains("cancelad") ||
                fullText.contains("catenaria") ||
                fullText.contains("tensión") ||
                fullText.contains("tension") ||
                fullText.contains("arrollamiento") ||
                fullText.contains("afectación a la circulación") ||
                fullText.contains("afectacion a la circulacion") ||
                fullText.contains("alteración del servicio") ||
                fullText.contains("alteracion del servicio")

        if (isInformativeNotice && !hasStrongCirculationKeywords) {
            return false
        }

        return hasStrongCirculationKeywords
    }
}
