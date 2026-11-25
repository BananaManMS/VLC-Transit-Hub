package com.example.ui.metro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.dashboard.AppLanguage

enum class MetroNoticeCategory(
    val rawKey: String,
    val esLabel: String,
    val caLabel: String,
    val lightColor: Color,
    val darkColor: Color,
    val icon: ImageVector,
    val isCirculationImpact: Boolean // Only true circulation impact (incidencia, aviso)
) {
    INCIDENCIA(
        rawKey = "incidencia",
        esLabel = "INCIDENCIA",
        caLabel = "INCIDÈNCIA",
        lightColor = Color(0xFFC62828), // Red
        darkColor = Color(0xFFEF5350),
        icon = Icons.Default.Warning,
        isCirculationImpact = true
    ),
    AVISO(
        rawKey = "aviso",
        esLabel = "AVISO DE CIRCULACIÓN",
        caLabel = "AVÍS DE CIRCULACIÓ",
        lightColor = Color(0xFFD84315), // Deep Orange
        darkColor = Color(0xFFFF7043),
        icon = Icons.Default.Campaign,
        isCirculationImpact = true
    ),
    SERVICIO_ESPECIAL(
        rawKey = "servicio_especial",
        esLabel = "SERVICIO ESPECIAL",
        caLabel = "SERVEI ESPECIAL",
        lightColor = Color(0xFF7B1FA2), // Purple
        darkColor = Color(0xFFCE93D8),
        icon = Icons.Default.DirectionsBus,
        isCirculationImpact = false
    ),
    OBRAS(
        rawKey = "obras_obras",
        esLabel = "OBRAS Y TRABAJOS",
        caLabel = "OBRES I TREBALLS",
        lightColor = Color(0xFFE65100), // Amber/Orange
        darkColor = Color(0xFFFFB74D),
        icon = Icons.Default.Build,
        isCirculationImpact = false
    ),
    ALERTA_METEOROLOGICA(
        rawKey = "alerta_meteorologica",
        esLabel = "ALERTA METEOROLÓGICA",
        caLabel = "ALERTA METEOROLÒGICA",
        lightColor = Color(0xFFF57F17), // Yellow/Amber
        darkColor = Color(0xFFFFEE58),
        icon = Icons.Default.Thermostat,
        isCirculationImpact = false
    ),
    ACCESIBILIDAD(
        rawKey = "accesibilidad",
        esLabel = "ACCESIBILIDAD",
        caLabel = "ACCESSIBILITAT",
        lightColor = Color(0xFF00838F), // Cyan/Teal
        darkColor = Color(0xFF4DD0E1),
        icon = Icons.Default.Accessible,
        isCirculationImpact = false
    ),
    PROMOCION(
        rawKey = "promocion",
        esLabel = "PROMOCIÓN Y NOVEDADES",
        caLabel = "PROMOCIÓ I NOVETATS",
        lightColor = Color(0xFF2E7D32), // Green
        darkColor = Color(0xFF81C784),
        icon = Icons.Default.CardGiftcard,
        isCirculationImpact = false
    ),
    OTRO(
        rawKey = "otro",
        esLabel = "INFORMACIÓN GENERAL",
        caLabel = "INFORMACIÓ GENERAL",
        lightColor = Color(0xFF1565C0), // Blue
        darkColor = Color(0xFF42A5F5),
        icon = Icons.Default.Info,
        isCirculationImpact = false
    );

    fun getDisplayName(appLanguage: AppLanguage): String {
        return if (appLanguage == AppLanguage.CA) caLabel else esLabel
    }

    fun getColor(isDarkMode: Boolean): Color {
        return if (isDarkMode) darkColor else lightColor
    }

    companion object {
        private fun normalizeText(input: String?): String {
            if (input.isNullOrBlank()) return ""
            val decomposed = java.text.Normalizer.normalize(input.trim().lowercase(), java.text.Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        }

        private fun isPureWeatherAdvisory(normalizedText: String): Boolean {
            if (normalizedText.isBlank()) return false
            val isWeather = normalizedText.contains("alerta meteorol") ||
                normalizedText.contains("aviso naranja por calor") ||
                normalizedText.contains("aviso rojo por calor") ||
                normalizedText.contains("aviso amarillo por calor") ||
                normalizedText.contains("ola de calor") ||
                normalizedText.contains("onada de calor") ||
                normalizedText.contains("altas temperaturas") ||
                normalizedText.contains("temperaturas elevadas")
            if (!isWeather) return false

            val hasActualServiceDisruption = normalizedText.contains("interrumpid") ||
                normalizedText.contains("interrupcion") ||
                normalizedText.contains("interrupcio") ||
                normalizedText.contains("sin servicio") ||
                normalizedText.contains("sense servei") ||
                normalizedText.contains("servicio suspendido") ||
                normalizedText.contains("servei suspes") ||
                normalizedText.contains("corte de") ||
                normalizedText.contains("tall de") ||
                normalizedText.contains("retraso") ||
                normalizedText.contains("retard") ||
                normalizedText.contains("autobus sustitutorio") ||
                normalizedText.contains("bus sustitutorio")
            return !hasActualServiceDisruption
        }

        private fun isNonOperationalInfo(normalizedText: String): Boolean {
            if (normalizedText.isBlank()) return false
            return normalizedText.contains("punt del client") ||
                normalizedText.contains("atencion al cliente") ||
                normalizedText.contains("atencio al client") ||
                normalizedText.contains("objetos perdidos") ||
                normalizedText.contains("objectes perduts") ||
                normalizedText.contains("descuento") ||
                normalizedText.contains("descompte") ||
                normalizedText.contains("tarifa") ||
                normalizedText.contains("abono gratuito") ||
                normalizedText.contains("abonament gratuit") ||
                normalizedText.contains("recarga") ||
                normalizedText.contains("encuesta") ||
                normalizedText.contains("enquesta") ||
                normalizedText.contains("concurso") ||
                normalizedText.contains("sorteo")
        }

        private fun hasCirculationDisruptionKeywords(normalizedText: String): Boolean {
            if (normalizedText.isBlank()) return false
            val keywords = listOf(
                "circulacion", "circulacio", "circulan", "circula ",
                "interrumpid", "interrupcion", "interrupcio",
                "retraso", "retrasos", "retard", "retards", "demora",
                "averia", "avaria", "incidencia",
                "corte de servicio", "corte de circulacion", "corte en",
                "tall de circulacio", "tall de servei", "tram tallat", "tramo cortado",
                "servicio suspendido", "servei suspes", "sin servicio", "sense servei",
                "servicio afectado", "servei afectat", "servicio alterado", "servei alterat",
                "suspendid", "suprimid", "cancelad",
                "trenes detenidos", "tren detenido", "trens detinguts", "tren detingut",
                "via unica", "bucle",
                "no efectua parada", "no efectuan parada", "no realiza parada", "no realizan parada",
                "no fa parada", "no fan parada", "sin parada", "sense parada",
                "estacion cerrada", "estaciones cerradas", "estacio tancada", "estacions tancades",
                "cierre de estacion", "cierre de la estacion", "tancament de l'estacio",
                "acceso cerrado", "accesos cerrados", "acces tancat", "accessos tancats",
                "finaliza recorrido", "finalizan recorrido", "finalitza recorregut", "finalitzen recorregut",
                "autobus sustitutorio", "bus sustitutorio", "servicio sustitutorio",
                "autobus substitutori", "bus substitutori", "servei substitutori",
                "transbordo en autobus",
                "frecuencia de paso", "frecuencias", "alteracion", "alteracions",
                "afeccion", "afecciones", "afeccio", "afectacion", "afectacio",
                "restablecid", "restablit", "normalizad", "normalitzat", "reanud", "reemprend",
                "modificacion del servicio", "modificacio del servei",
                "desvio", "desviament"
            )
            return keywords.any { normalizedText.contains(it) }
        }

        private fun hasPriorityTransitOperationContext(normalizedText: String): Boolean {
            if (normalizedText.isBlank()) return false
            val contextKeywords = listOf(
                "tren", "tranvia", "tramvia", "linea ", "lineas ", "linia ", "linies ",
                "estacion", "estacio", "parada", "recorrido", "recorregut",
                "servicio", "servei", "tramo", "tram "
            )
            return contextKeywords.any { normalizedText.contains(it) }
        }

        private fun hasSevereIncidentKeywords(normalizedText: String): Boolean {
            val severeKeywords = listOf(
                "incidencia", "averia", "avaria",
                "interrumpid", "interrupcion", "interrupcio",
                "sin servicio", "sense servei", "suspendid", "suspes",
                "corte de", "tall de", "tramo cortado", "tram tallat",
                "detenido", "detingut", "via unica",
                "estacion cerrada", "estaciones cerradas", "estacio tancada", "estacions tancades"
            )
            return severeKeywords.any { normalizedText.contains(it) }
        }

        fun fromRaw(categoria: String?): MetroNoticeCategory {
            val clean = normalizeText(categoria)
            if (clean.isBlank()) return OTRO
            return when {
                clean == "accesibilidad" || clean.contains("accesib") || clean.contains("ascensor") || clean.contains("escalera") -> ACCESIBILIDAD
                clean == "alerta_meteorologica" || clean.contains("meteorolog") || clean.contains("calor") || clean.contains("clima") || clean.contains("tiempo") -> ALERTA_METEOROLOGICA
                clean == "promocion" || clean.contains("promo") || clean.contains("descuent") || clean.contains("campa") -> PROMOCION
                clean == "servicio_especial" || clean.contains("especial") -> SERVICIO_ESPECIAL
                clean == "obras_obras" || clean.contains("obra") || clean.contains("treball") || clean.contains("trabajo") -> OBRAS
                clean == "incidencia" || clean.contains("incidenc") || clean.contains("averia") || clean.contains("avaria") || clean.contains("interrupc") -> INCIDENCIA
                clean == "aviso" || clean == "avis" || clean.contains("circulac") || clean.contains("aviso") || clean.contains("prioritari") || clean.contains("urgent") || clean.contains("trafic") || clean.contains("retras") || clean.contains("retard") || clean.contains("corte") || clean.contains("alterac") -> AVISO
                else -> OTRO
            }
        }

        /**
         * Resolves the effective category by inspecting the raw category key, category display name,
         * text content, and whether the alert belongs to Metrovalencia's priority feed.
         */
        fun resolveEffectiveCategory(
            category: String?,
            categoryName: String? = null,
            title: String? = null,
            description: String? = null,
            isFromPriorityFeed: Boolean = false,
            hasAffectedLines: Boolean = false
        ): MetroNoticeCategory {
            val primaryCat = fromRaw(category)
            val namedCat = if (primaryCat == OTRO && !categoryName.isNullOrBlank()) {
                fromRaw(categoryName)
            } else {
                primaryCat
            }
            val textCombined = normalizeText("${title ?: ""} ${description ?: ""}")

            if (namedCat == ACCESIBILIDAD) return ACCESIBILIDAD
            if (isPureWeatherAdvisory(textCombined)) return ALERTA_METEOROLOGICA
            if (namedCat == ALERTA_METEOROLOGICA && !hasCirculationDisruptionKeywords(textCombined)) {
                return ALERTA_METEOROLOGICA
            }
            if (namedCat == PROMOCION) return PROMOCION

            if (namedCat == INCIDENCIA || namedCat == AVISO) return namedCat

            if (namedCat == OTRO) {
                if (textCombined.contains("ascensor") ||
                    textCombined.contains("escalera mecanica") ||
                    textCombined.contains("escales mecaniques")
                ) {
                    return ACCESIBILIDAD
                }
                val hasCirculationText = hasCirculationDisruptionKeywords(textCombined)
                val isPriorityTransitAlert = isFromPriorityFeed &&
                    !isNonOperationalInfo(textCombined) &&
                    (hasAffectedLines || hasPriorityTransitOperationContext(textCombined))
                if (hasCirculationText || isPriorityTransitAlert) {
                    return if (hasSevereIncidentKeywords(textCombined)) INCIDENCIA else AVISO
                }
            }

            return namedCat
        }

        /**
         * Determines whether a notice or incident represents a real operational circulation disruption
         * (e.g., delays, stopped trains, breakdowns, direct traffic impact),
         * strictly excluding non-operational categories like meteorological warnings (heat/rain), promotions, obras programadas, or accessibility info.
         */
        fun isRealCirculationIncident(
            category: String?,
            title: String? = null,
            description: String? = null,
            categoryName: String? = null,
            isFromPriorityFeed: Boolean = false,
            hasAffectedLines: Boolean = false
        ): Boolean {
            val resolvedCat = resolveEffectiveCategory(
                category = category,
                categoryName = categoryName,
                title = title,
                description = description,
                isFromPriorityFeed = isFromPriorityFeed,
                hasAffectedLines = hasAffectedLines
            )
            return resolvedCat.isCirculationImpact
        }
    }
}
