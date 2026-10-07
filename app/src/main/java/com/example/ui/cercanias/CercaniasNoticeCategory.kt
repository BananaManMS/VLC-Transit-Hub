package com.example.ui.cercanias

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Block
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

enum class CercaniasAlertGroup(
    val id: String,
    val esLabel: String,
    val caLabel: String,
    val colorLight: Color,
    val colorDark: Color,
    val icon: ImageVector
) {
    TODOS(
        id = "all",
        esLabel = "Todos",
        caLabel = "Tots",
        colorLight = Color(0xFF1E88E5),
        colorDark = Color(0xFF64B5F6),
        icon = Icons.Default.Campaign
    ),
    INCIDENCIAS(
        id = "incidencias",
        esLabel = "Incidencias",
        caLabel = "Incidències",
        colorLight = Color(0xFFD32F2F),
        colorDark = Color(0xFFEF5350),
        icon = Icons.Default.Warning
    ),
    PLANES_Y_OBRAS(
        id = "obras_planes",
        esLabel = "Obras y Buses",
        caLabel = "Obres i Busos",
        colorLight = Color(0xFF7B1FA2),
        colorDark = Color(0xFFCE93D8),
        icon = Icons.Default.DirectionsBus
    ),
    HORARIOS_E_INFO(
        id = "horarios_info",
        esLabel = "Horarios e Info",
        caLabel = "Horaris i Info",
        colorLight = Color(0xFF0284C7),
        colorDark = Color(0xFF38BDF8),
        icon = Icons.Default.Schedule
    ),
    ACCESIBILIDAD(
        id = "accesibilidad",
        esLabel = "Accesibilidad",
        caLabel = "Accessibilitat",
        colorLight = Color(0xFF00838F),
        colorDark = Color(0xFF4DD0E1),
        icon = Icons.Default.Accessible
    );

    fun getDisplayName(appLanguage: AppLanguage): String {
        return if (appLanguage == AppLanguage.CA) caLabel else esLabel
    }
}

enum class CercaniasNoticeCategory(
    val esLabel: String,
    val caLabel: String,
    val lightColor: Color,
    val darkColor: Color,
    val icon: ImageVector,
    val group: CercaniasAlertGroup
) {
    SUPRESION(
        esLabel = "TREN SUPRIMIDO",
        caLabel = "TREN SUPRIMIT",
        lightColor = Color(0xFFB71C1C),
        darkColor = Color(0xFFEF5350),
        icon = Icons.Default.Block,
        group = CercaniasAlertGroup.INCIDENCIAS
    ),
    INCIDENCIA(
        esLabel = "INCIDENCIA / RETRASO",
        caLabel = "INCIDÈNCIA / RETARD",
        lightColor = Color(0xFFC62828),
        darkColor = Color(0xFFEF5350),
        icon = Icons.Default.Warning,
        group = CercaniasAlertGroup.INCIDENCIAS
    ),
    ALERTA_METEOROLOGICA(
        esLabel = "ALERTA METEOROLÓGICA",
        caLabel = "ALERTA METEOROLÒGICA",
        lightColor = Color(0xFFD84315),
        darkColor = Color(0xFFFF8A65),
        icon = Icons.Default.Thermostat,
        group = CercaniasAlertGroup.INCIDENCIAS
    ),
    PLAN_ALTERNATIVO(
        esLabel = "SERVICIO POR AUTOBÚS",
        caLabel = "SERVEI PER AUTOBÚS",
        lightColor = Color(0xFF7B1FA2),
        darkColor = Color(0xFFCE93D8),
        icon = Icons.Default.DirectionsBus,
        group = CercaniasAlertGroup.PLANES_Y_OBRAS
    ),
    OBRAS(
        esLabel = "OBRAS Y TRABAJOS",
        caLabel = "OBRES I TREBALLS",
        lightColor = Color(0xFFE65100),
        darkColor = Color(0xFFFFB74D),
        icon = Icons.Default.Build,
        group = CercaniasAlertGroup.PLANES_Y_OBRAS
    ),
    HORARIOS(
        esLabel = "HORARIOS Y SERVICIOS",
        caLabel = "HORARIS I SERVEIS",
        lightColor = Color(0xFF0284C7),
        darkColor = Color(0xFF38BDF8),
        icon = Icons.Default.Schedule,
        group = CercaniasAlertGroup.HORARIOS_E_INFO
    ),
    TARIFAS(
        esLabel = "TARIFAS Y ABONOS",
        caLabel = "TARIFES I ABONAMENTS",
        lightColor = Color(0xFF2E7D32),
        darkColor = Color(0xFF81C784),
        icon = Icons.Default.CardGiftcard,
        group = CercaniasAlertGroup.HORARIOS_E_INFO
    ),
    AVISO(
        esLabel = "AVISO DE CIRCULACIÓN",
        caLabel = "AVÍS DE CIRCULACIÓ",
        lightColor = Color(0xFF0284C7),
        darkColor = Color(0xFF38BDF8),
        icon = Icons.Default.Campaign,
        group = CercaniasAlertGroup.HORARIOS_E_INFO
    ),
    ACCESIBILIDAD(
        esLabel = "ACCESIBILIDAD",
        caLabel = "ACCESSIBILITAT",
        lightColor = Color(0xFF00838F),
        darkColor = Color(0xFF4DD0E1),
        icon = Icons.Default.Accessible,
        group = CercaniasAlertGroup.ACCESIBILIDAD
    ),
    INFORMACION(
        esLabel = "INFORMACIÓN DE SERVICIO",
        caLabel = "INFORMACIÓ DE SERVEI",
        lightColor = Color(0xFF1565C0),
        darkColor = Color(0xFF42A5F5),
        icon = Icons.Default.Info,
        group = CercaniasAlertGroup.HORARIOS_E_INFO
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
                // 1. Accessibility (Elevators, escalators, ramps)
                CercaniasAlertClassifier.isAccessibility(text) -> ACCESIBILIDAD

                // 2. Train cancellations & non-circulation
                text.contains("no circula") || text.contains("no circulará") || text.contains("no circulara") ||
                text.contains("suprimido") || text.contains("suprimida") || text.contains("suprimits") ||
                text.contains("suprimides") || text.contains("supresión") || text.contains("supresion") ||
                text.contains("cancelado") || text.contains("cancelada") || text.contains("cancel·lat") ||
                text.contains("no presta servicio") || text.contains("no prestará servicio") -> SUPRESION

                // 3. Alternative bus replacements
                text.contains("servicio por autobús") || text.contains("servicio alternativo por autobús") ||
                text.contains("servicio por autobus") || text.contains("servicio alternativo por autobus") ||
                text.contains("bus alternativo") || text.contains("plan alternativo") || text.contains("pla alternatiu") ||
                text.contains("servicio alternativo") || text.contains("transbordo por carretera") ||
                text.contains("trasbordo por carretera") || text.contains("transbordo en autobús") ||
                text.contains("servei per autobús") || text.contains("servei alternatiu") ||
                (text.contains("autobús") || text.contains("autobus")) && !text.contains("metro") -> PLAN_ALTERNATIVO

                // 4. Live operational incidents, breakdowns, disruptions, delays
                text.contains("retraso") || text.contains("retrasos") || text.contains("demora") ||
                text.contains("demoras") || text.contains("avería") || text.contains("averia") ||
                text.contains("averies") || text.contains("incidencia técnica") || text.contains("incidencia tecnica") ||
                text.contains("incidencia en") || text.contains("falta de tensión") || text.contains("falta de tension") ||
                text.contains("catenaria") || text.contains("arrollamiento") || text.contains("corte de vía") ||
                text.contains("corte de via") || text.contains("interrupción de la circulación") ||
                text.contains("interrupcion de la circulacion") || text.contains("circulación interrumpida") ||
                text.contains("circulacion interrumpida") || text.contains("sin servicio") ||
                text.contains("sense servei") || text.contains("afectación") || text.contains("afectació") ||
                text.contains("alteración") || text.contains("alteracion") -> INCIDENCIA

                // 5. Weather emergencies & alerts
                text.contains("meteorol") || text.contains("climatolog") || text.contains("aemet") ||
                text.contains("alerta roja") || text.contains("alerta naranja") || text.contains("alerta taronja") ||
                text.contains("temporal de lluvia") || text.contains("inundac") -> ALERTA_METEOROLOGICA

                // 6. Infrastructure works & maintenance
                text.contains("obra") || text.contains("obres") || text.contains("mantenimiento") ||
                text.contains("trabajos en vía") || text.contains("trabajos en via") || text.contains("trabajos de mejora") ||
                text.contains("soterramiento") || text.contains("renovación de vía") || text.contains("renovacion de via") -> OBRAS

                // 7. Tariffs & travel passes
                text.contains("abono") || text.contains("abonos") || text.contains("tarifa") ||
                text.contains("tarifas") || text.contains("tarifes") || text.contains("billete") ||
                text.contains("billetes") || text.contains("gratuito") || text.contains("gratuidad") ||
                text.contains("título de transporte") || text.contains("titulo de transporte") ||
                text.contains("precio") -> TARIFAS

                // 8. Timetables & special calendar services
                text.contains("horario") || text.contains("horarios") || text.contains("horari") ||
                text.contains("horaris") || text.contains("frecuencia") || text.contains("frecuencias") ||
                text.contains("calendario") || text.contains("refuerzo") || text.contains("festivo") ||
                text.contains("festivos") || text.contains("fallas") || text.contains("huelga") ||
                text.contains("servicios mínimos") || text.contains("servicios minimos") -> HORARIOS

                // 9. Circulation notices / restoration
                text.contains("aviso") || text.contains("avís") || text.contains("restablece") ||
                text.contains("normaliza") || text.contains("circulación normalizada") || text.contains("reanuda") -> AVISO

                else -> INFORMACION
            }
        }
    }
}
