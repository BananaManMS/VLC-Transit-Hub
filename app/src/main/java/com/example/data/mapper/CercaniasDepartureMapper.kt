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
    private val normalizationCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun stripAccentsLower(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    internal fun isPlatjaGandiaName(normalizedNoAccentsLower: String): Boolean {
        return normalizedNoAccentsLower.contains("platja") ||
            normalizedNoAccentsLower.contains("playa") ||
            normalizedNoAccentsLower.contains("grau") ||
            normalizedNoAccentsLower.contains("grao")
    }

    fun normalizeStationName(name: String): String {
        if (name.isBlank()) return ""
        return normalizationCache.getOrPut(name) {
            val normalized = stripAccentsLower(name)
                .replace("estacion", "estacio")
                .replace("norte", "nord")
                .replace("nord", "nord")
            
            when {
                normalized.contains("nord") || normalized.contains("valencia nord") || normalized.contains("estacio del nord") || normalized.contains("norte") -> "valencia nord"
                normalized.contains("sant isidre") || normalized.contains("st. isidre") -> "valencia st. isidre"
                normalized.contains("cabanyal") -> "cabanyal"
                normalized.contains("font de sant lluis") || normalized.contains("sant lluis") || normalized.contains("f. s. lluis") -> "valencia f. s. lluis"
                normalized.contains("vinaros") -> "vinaros"
                normalized.contains("cullera") -> "cullera"
                normalized.contains("benicarlo") || normalized.contains("peniscola") -> "benicarlo"
                normalized.contains("castello") -> "castello de la plana"
                normalized.contains("xativa") -> "xativa"
                normalized.contains("moixent") || normalized.contains("mogente") -> "moixent"
                isPlatjaGandiaName(normalized) -> "platja i grau de gandia"
                normalized.contains("gandia") -> "gandia"
                normalized.contains("siete aguas") || normalized.contains("venta mina") -> "venta mina-siete aguas"
                normalized.contains("utiel") -> "utiel"
                normalized.contains("bunol") || normalized.contains("bua") || normalized.contains("buñ") || normalized.contains("bunyol") || (normalized.contains("bu") && normalized.contains("ol")) -> "bunol"
                normalized.contains("caudiel") -> "caudiel"
                normalized.contains("alcudia") || normalized.contains("crespins") -> "l'alcudia de crespins"
                else -> normalized
            }
        }
    }

    /**
     * Devuelve el nombre canónico y normalizado para mostrar de una estación o destino.
     */
    fun formatStationDisplayName(name: String): String {
        if (name.isBlank()) return ""
        val lower = name.lowercase(Locale.ROOT).trim()
        val normalized = stripAccentsLower(name)
        return when {
            normalized.contains("nord") || normalized.contains("estacio del nord") || normalized.contains("norte") -> "València Nord"
            normalized.contains("sant isidre") || normalized.contains("st. isidre") -> "València St. Isidre"
            normalized.contains("cabanyal") -> "Cabanyal"
            normalized.contains("font de sant lluis") || normalized.contains("sant lluis") || normalized.contains("f. s. lluis") -> "València F. S. Lluís"
            normalized.contains("alcudia") || normalized.contains("crespins") -> "L'Alcúdia de Crespins"
            lower.contains("bunol") || lower.contains("buã") || lower.contains("buñ") || lower.contains("bunyol") -> "Buñol"
            lower.contains("puc") || lower.contains("puç") || lower.contains("puã") || lower.contains("pua") -> "Puçol"
            isPlatjaGandiaName(normalized) -> "Platja i Grau de Gandia"
            normalized.contains("gandia") -> "Gandia"
            else -> name
        }
    }

    /**
     * Comprueba si la estación consultada coincide con el destino final del trayecto (llegada término).
     * Si la estación consultada es igual a la estación de destino, se considera una llegada término
     * y debe descartarse de la lista de salidas.
     */
    fun isTerminalArrival(currentStationName: String, destinationName: String, preNormalizedCurrent: String? = null): Boolean {
        val normCurrent = preNormalizedCurrent ?: normalizeStationName(currentStationName)
        val normDest = normalizeStationName(destinationName)
        
        if (normCurrent.isBlank() || normDest.isBlank()) return false
        
        // Prevent false positives between Gandia and Platja i Grau de Gandia
        if (isPlatjaGandiaName(normCurrent) != isPlatjaGandiaName(normDest)) {
            return false
        }
        
        return normCurrent == normDest ||
               (normCurrent.contains(normDest) && normDest.length >= 4) ||
               (normDest.contains(normCurrent) && normCurrent.length >= 4)
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
            .filter { it.minutesRemaining >= -1 && (it.minutesRemaining <= 1440 || it.isIndeterminateDelay) }
            .distinctBy { "${it.departureTime}_${it.routeId}_${it.destination}_${it.isTomorrow}" }
            .sortedWith(
                compareBy<CercaniasDeparture> {
                    // Canceled trains placed after active ones
                    if (it.isCanceled) 1 else 0
                }
                    .thenBy {
                        if (it.isTomorrow) 1 else 0
                    }
                    .thenBy {
                        // Strict chronological ordering by minutes remaining
                        if (it.isIndeterminateDelay) 9999 else it.minutesRemaining.coerceAtLeast(0)
                    }
                    .thenBy { it.departureTime }
                    .thenBy {
                        // Tiebreaker only when departure minutes and time are identical
                        when {
                            it.isStoppedAt -> 0
                            it.isIncomingAt -> 1
                            it.isLive -> 2
                            else -> 3
                        }
                    }
            )
    }
}
