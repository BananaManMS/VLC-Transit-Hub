package com.example.data.mapper

import com.example.data.database.RenfeScheduleItem
import com.example.ui.cercanias.CercaniasDeparture
import java.text.Normalizer
import java.util.Locale

/**
 * Mapper y filtro para transformar horarios de Renfe Cercanías en el panel de salidas.
 * Aplica reglas estrictas para excluir trenes que finalizan su recorrido en la estación consultada (llegadas término).
 */
object CercaniasDepartureMapper {

    /**
     * Normaliza los nombres de estación para comparaciones insensibles a mayúsculas, acentos y espacios,
     * e iguala variantes de nombres de la red de Cercanías Valencia a claves canónicas.
     */
    fun normalizeStationName(name: String): String {
        if (name.isBlank()) return ""
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
            .replace("estacion", "estacio")
            .replace("norte", "nord")
            .replace("nord", "nord")
        
        return when {
            normalized.contains("nord") || normalized.contains("valencia nord") || normalized.contains("estacio del nord") -> "valencia nord"
            normalized.contains("vinaros") -> "vinaros"
            normalized.contains("benicarlo") || normalized.contains("peniscola") -> "benicarlo"
            normalized.contains("castello") -> "castello de la plana"
            normalized.contains("xativa") -> "xativa"
            normalized.contains("moixent") || normalized.contains("mogente") -> "moixent"
            normalized.contains("platja") && normalized.contains("gandia") -> "platja i grau de gandia"
            normalized.contains("gandia") -> "gandia"
            normalized.contains("siete aguas") || normalized.contains("venta mina") -> "venta mina-siete aguas"
            normalized.contains("utiel") -> "utiel"
            normalized.contains("bunol") || normalized.contains("bua") || normalized.contains("buñ") || (normalized.contains("bu") && normalized.contains("ol")) -> "bunol"
            normalized.contains("caudiel") -> "caudiel"
            normalized.contains("alcudia") -> "l'alcudia"
            else -> normalized
        }
    }

    /**
     * Comprueba si la estación consultada coincide con el destino final del trayecto (llegada término).
     */
    fun isTerminalArrival(currentStationName: String, destinationName: String): Boolean {
        val normCurrent = normalizeStationName(currentStationName)
        val normDest = normalizeStationName(destinationName)
        
        println("Debug: normCurrent=$normCurrent, normDest=$normDest")
        
        // Allow trains arriving at València Nord, as they are not terminal arrivals for the user
        if (normCurrent == "valencia nord") return false
        
        return normCurrent.isNotEmpty() && normCurrent == normDest
    }

    /**
     * Determina si un ítem de horario representa una salida válida desde la estación hacia otra dirección.
     * 
     * Criterios de exclusión:
     * 1. Hora de salida vacía o en blanco: No hay servicio programado.
     * 2. Estación consultada idéntica al destino final del trayecto: La estación es el término del tren (llegada término).
     */
    fun isValidDeparture(
        scheduleItem: RenfeScheduleItem,
        currentStationName: String,
        destinationName: String
    ): Boolean {
        // Exclusión 1: No existe hora de salida explícita
        if (scheduleItem.llegada.isBlank()) {
            return false
        }

        // Exclusión 2: La estación consultada es el destino final del trayecto
        if (isTerminalArrival(currentStationName, destinationName)) {
            return false
        }

        return true
    }

    /**
     * Ordena la lista de salidas de forma cronológica y elimina duplicados de misma hora, línea y destino.
     */
    fun sortDeparturesChronologically(departures: List<CercaniasDeparture>): List<CercaniasDeparture> {
        return departures
            .filter { it.minutesRemaining <= 1440 }
            .distinctBy { Triple(it.departureTime, it.routeId, it.destination) }
            .sortedWith(
                compareBy<CercaniasDeparture> {
                    when {
                        it.isCanceled -> 3
                        it.isStoppedAt -> 0
                        it.isIncomingAt -> 1
                        else -> 2
                    }
                }
                    .thenBy { it.minutesRemaining }
                    .thenBy { it.departureTime }
            )
    }
}
