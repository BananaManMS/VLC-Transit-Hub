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
        fun fromRaw(categoria: String?): MetroNoticeCategory {
            if (categoria.isNullOrBlank()) return OTRO
            val clean = categoria.trim().lowercase()
            return when {
                clean == "incidencia" || clean.contains("incidenc") -> INCIDENCIA
                clean == "aviso" || clean == "avis" -> AVISO
                clean == "servicio_especial" || clean.contains("especial") -> SERVICIO_ESPECIAL
                clean == "obras_obras" || clean.contains("obra") || clean.contains("treball") || clean.contains("trabajo") -> OBRAS
                clean == "alerta_meteorologica" || clean.contains("meteorolog") || clean.contains("calor") || clean.contains("clima") || clean.contains("tiempo") -> ALERTA_METEOROLOGICA
                clean == "accesibilidad" || clean.contains("accesib") -> ACCESIBILIDAD
                clean == "promocion" || clean.contains("promo") || clean.contains("descuent") || clean.contains("campa") -> PROMOCION
                else -> OTRO
            }
        }

        /**
         * Determines whether a notice or incident represents a real operational circulation disruption
         * (e.g., delays, stopped trains, breakdowns, direct traffic impact),
         * strictly excluding non-operational categories like meteorological warnings (heat/rain), promotions, obras programadas, or accessibility info.
         */
        fun isRealCirculationIncident(category: String?, title: String? = null, description: String? = null): Boolean {
            val catEnum = fromRaw(category)
            if (!catEnum.isCirculationImpact) return false

            // Even if category is incidencia or aviso, check if it's actually weather or promotion disguised as an aviso
            val textCombined = ((title ?: "") + " " + (description ?: "")).lowercase()
            val isWeatherDisguised = textCombined.contains("alerta meteorol") ||
                    textCombined.contains("aviso naranja por calor") ||
                    textCombined.contains("aviso rojo por calor") ||
                    textCombined.contains("ola de calor") ||
                    textCombined.contains("altas temperaturas") ||
                    textCombined.contains("temperaturas elevadas")
            if (isWeatherDisguised) return false

            return true
        }
    }
}
