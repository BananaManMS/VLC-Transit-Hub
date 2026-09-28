package com.example.util

import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.components.cleanAccessibilityText
import com.example.ui.metro.AccessibilityIncident

object StationAccessibilityHelper {

    fun cleanAccessibilityAlertText(rawText: String): String {
        return cleanAccessibilityText(rawText)
    }

    fun isStationInAlertTitle(stationName: String, alertTitle: String): Boolean {
        val normName = stationName.normalizeForSearch()
        val titleNorm = alertTitle.normalizeForSearch()
        return titleNorm.contains(normName)
    }

    fun isMetroStationAffected(
        stationId: String,
        stationName: String,
        incident: AccessibilityIncident
    ): Boolean {
        val normName = stationName.normalizeForSearch()
        val stationIdInt = stationId.toIntOrNull()

        if (stationIdInt != null && incident.estacionId == stationIdInt) return true
        if (incident.estacionId != null && incident.estacionId.toString() == stationId) return true

        val incEstName = incident.estacionNombre?.trim()
        if (!incEstName.isNullOrEmpty()) {
            val normIncName = incEstName.normalizeForSearch()
            if (normIncName == normName || normIncName.equals(normName, ignoreCase = true)) {
                return true
            }
        }

        if (incident.estacionId == null && incEstName.isNullOrBlank()) {
            val titleNorm = (incident.tituloEs + " " + incident.tituloCa).normalizeForSearch()
            if (titleNorm.contains("estacio de $normName") || titleNorm.contains("estacion de $normName")) {
                return true
            }
        }
        return false
    }

    fun isCercaniasStationAffected(
        stationId: String,
        stationNombre: String,
        displayName: String,
        alert: CercaniasAlert
    ): Boolean {
        if (!alert.isAccessibility) return false

        val cleanId = stationId.substringBefore('_').substringBefore('-').trim()
        if (alert.stopIds.isNotEmpty()) {
            val matchesStop = alert.stopIds.any { sId ->
                val cleanSId = sId.substringBefore('_').substringBefore('-').trim()
                cleanSId.equals(cleanId, ignoreCase = true) || cleanSId.equals(stationId, ignoreCase = true)
            }
            if (matchesStop) return true
        }

        val namesToCheck = listOf(stationNombre, displayName).filter { it.isNotBlank() }
        val fullText = "${alert.headerEs} ${alert.descriptionEs}".lowercase(java.util.Locale.ROOT)
        val cleanedText = fullText
            .replace(Regex("(sentido|dirección|direccion|hacia|destí|destino|destinació|destinacion)\\s+[a-záéíóúàèòñç·\\-\\s]+(de vía|de via|en vía|en via|,|\\.)"), " ")
            .replace(Regex("(sentido|dirección|direccion|hacia|destí|destino|destinació|destinacion)\\s+[a-záéíóúàèòñç·\\-\\s]+"), " ")
            .normalizeForSearch()

        for (name in namesToCheck) {
            val normName = name.normalizeForSearch()
            if (normName.isBlank()) continue
            if (cleanedText.contains("estacion de $normName") ||
                cleanedText.contains("estacio de $normName") ||
                cleanedText.contains("estacion $normName") ||
                cleanedText.contains("estacio $normName")
            ) {
                return true
            }
        }

        return false
    }
}
