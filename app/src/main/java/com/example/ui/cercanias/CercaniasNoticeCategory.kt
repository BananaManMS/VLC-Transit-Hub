package com.example.ui.cercanias

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.dashboard.AppLanguage
import java.util.Locale

enum class CercaniasNoticeCategory(
    val esLabel: String,
    val caLabel: String,
    val lightColor: Color,
    val darkColor: Color,
    val icon: ImageVector
) {
    INCIDENCIA(
        esLabel = "INCIDENCIA",
        caLabel = "INCIDÈNCIA",
        lightColor = Color(0xFFC62828),
        darkColor = Color(0xFFEF5350),
        icon = Icons.Default.Warning
    ),
    AVISO(
        esLabel = "AVISO DE CIRCULACIÓN",
        caLabel = "AVÍS DE CIRCULACIÓ",
        lightColor = Color(0xFFD84315),
        darkColor = Color(0xFFFF7043),
        icon = Icons.Default.Campaign
    ),
    PLAN_ALTERNATIVO(
        esLabel = "PLAN ALTERNATIVO",
        caLabel = "PLA ALTERNATIU",
        lightColor = Color(0xFF7B1FA2),
        darkColor = Color(0xFFCE93D8),
        icon = Icons.Default.DirectionsBus
    ),
    OBRAS(
        esLabel = "OBRAS Y TRABAJOS",
        caLabel = "OBRES I TREBALLS",
        lightColor = Color(0xFFE65100),
        darkColor = Color(0xFFFFB74D),
        icon = Icons.Default.Build
    ),
    HORARIOS(
        esLabel = "HORARIOS Y SERVICIOS",
        caLabel = "HORARIS I SERVEIS",
        lightColor = Color(0xFF0284C7),
        darkColor = Color(0xFF38BDF8),
        icon = Icons.Default.Schedule
    ),
    TARIFAS(
        esLabel = "TARIFAS Y ABONOS",
        caLabel = "TARIFES I ABONAMENTS",
        lightColor = Color(0xFF2E7D32),
        darkColor = Color(0xFF81C784),
        icon = Icons.Default.CardGiftcard
    ),
    ALERTA_METEOROLOGICA(
        esLabel = "ALERTA METEOROLÓGICA",
        caLabel = "ALERTA METEOROLÒGICA",
        lightColor = Color(0xFFF57F17),
        darkColor = Color(0xFFFFEE58),
        icon = Icons.Default.Thermostat
    ),
    ACCESIBILIDAD(
        esLabel = "ACCESIBILIDAD",
        caLabel = "ACCESSIBILITAT",
        lightColor = Color(0xFF00838F),
        darkColor = Color(0xFF4DD0E1),
        icon = Icons.Default.Accessible
    ),
    INFORMACION(
        esLabel = "INFORMACIÓN DE SERVICIO",
        caLabel = "INFORMACIÓ DE SERVEI",
        lightColor = Color(0xFF1565C0),
        darkColor = Color(0xFF42A5F5),
        icon = Icons.Default.Info
    );

    fun getDisplayName(appLanguage: AppLanguage): String {
        return if (appLanguage == AppLanguage.CA) caLabel else esLabel
    }

    fun getColor(isDarkMode: Boolean): Color {
        return if (isDarkMode) darkColor else lightColor
    }

    companion object {
        fun resolveFromText(header: String, description: String = ""): CercaniasNoticeCategory {
            val text = "$header $description".lowercase(Locale.ROOT)
            return when {
                CercaniasAlertClassifier.isAccessibility(text) -> ACCESIBILIDAD
                text.contains("obra") || text.contains("obres") || text.contains("mantenimiento") || text.contains("trabajos en vía") || text.contains("trabajos en via") -> OBRAS
                text.contains("bus alternativo") || text.contains("plan alternativo") || text.contains("servicio alternativo") || text.contains("autobús") || text.contains("autobus") || text.contains("transbordo") -> PLAN_ALTERNATIVO
                text.contains("lluvia") || text.contains("temporal") || text.contains("viento") || text.contains("meteorolog") || text.contains("alerta") -> ALERTA_METEOROLOGICA
                text.contains("corte") || text.contains("interrup") || text.contains("avería") || text.contains("averia") || text.contains("suprim") || text.contains("retraso") || text.contains("demora") || text.contains("incidencia") -> INCIDENCIA
                text.contains("horario") || text.contains("frecuencia") || text.contains("calendario") || text.contains("refuerzo") -> HORARIOS
                text.contains("abono") || text.contains("tarifa") || text.contains("billete") || text.contains("gratuito") || text.contains("precio") -> TARIFAS
                text.contains("aviso") || text.contains("importante") || text.contains("atención") || text.contains("atencion") -> AVISO
                else -> INFORMACION
            }
        }
    }
}
