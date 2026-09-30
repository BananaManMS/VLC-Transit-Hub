package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotAccessible
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.dashboard.AppLanguage
import com.example.ui.metro.AccessibilityIncident
import com.example.util.normalizeForSearch

data class StationAccessibilityInfo(
    val isAccessible: Boolean,
    val stationName: String,
    val issues: List<String> = emptyList()
)

data class GroupedAccessibilityIssue(
    val title: String,
    val details: List<String> = emptyList()
)

fun cleanAccessibilityText(rawText: String, stationName: String? = null): String {
    var cleaned = rawText.trim()

    val patterns = listOf(
        Regex("^(?:Afectaci[oó]n?|Incid[eè]ncia|Incidencia|Av[ií]s|Aviso)\\s+(?:d['’]accessibilitat|de\\s+accesibilidad)\\s+(?:en|a)\\s+(?:la\\s+|l['’])?estaci[oó]n?\\s+[^:]+:\\s*", RegexOption.IGNORE_CASE),
        Regex("^(?:Afectaci[oó]n?|Incid[eè]ncia|Incidencia|Av[ií]s|Aviso)\\s+(?:d['’]accessibilitat|de\\s+accesibilidad)\\s+(?:en|a)\\s+(?:la\\s+|l['’])?estaci[oó]n?\\s+[^:\\.\\n]+[\\.\\:]?\\s*", RegexOption.IGNORE_CASE),
        Regex("^(?:Afectaci[oó]n?|Incid[eè]ncia|Incidencia|Av[ií]s|Aviso)\\s+(?:d['’]accessibilitat|de\\s+accesibilidad)\\s*:\\s*", RegexOption.IGNORE_CASE)
    )

    for (pattern in patterns) {
        cleaned = pattern.replace(cleaned, "").trim()
    }

    if (!stationName.isNullOrBlank()) {
        val escapedName = java.util.regex.Pattern.quote(stationName.trim())
        cleaned = cleaned.replace(Regex("^Afectaci[oó]n?.*?$escapedName\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim()
    }

    return cleaned.ifBlank { rawText.trim() }
}

fun groupAccessibilityIssues(issues: List<String>, stationName: String? = null): List<GroupedAccessibilityIssue> {
    if (issues.isEmpty()) return emptyList()

    val map = LinkedHashMap<String, MutableList<String>>()

    for (raw in issues) {
        val cleaned = cleanAccessibilityText(raw, stationName)
        if (cleaned.isBlank()) continue

        val parts = cleaned.split(Regex("\\s*[-–—:]\\s+"), 2)
        val title = parts[0].trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        val detail = if (parts.size > 1) parts[1].trim() else null

        val detailsList = map.getOrPut(title) { mutableListOf() }
        if (!detail.isNullOrBlank() && !detailsList.contains(detail)) {
            detailsList.add(detail)
        }
    }

    return map.map { (title, details) ->
        GroupedAccessibilityIssue(title = title, details = details)
    }
}

fun computeMetroStationAccessibility(
    stationId: String,
    stationName: String,
    incidents: List<AccessibilityIncident>
): StationAccessibilityInfo {
    val normName = stationName.normalizeForSearch()
    val stationIdInt = stationId.toIntOrNull()

    val matching = incidents.filter { inc ->
        if (stationIdInt != null && inc.estacionId == stationIdInt) return@filter true
        if (inc.estacionId != null && inc.estacionId.toString() == stationId) return@filter true

        val incEstName = inc.estacionNombre?.trim()
        if (!incEstName.isNullOrEmpty()) {
            val normIncName = incEstName.normalizeForSearch()
            if (normIncName == normName || normIncName.equals(normName, ignoreCase = true)) {
                return@filter true
            }
        }

        // Only fallback if both estacionId and estacionNombre are completely null/empty
        if (inc.estacionId == null && incEstName.isNullOrBlank()) {
            val titleNorm = (inc.tituloEs + " " + inc.tituloCa).normalizeForSearch()
            if (titleNorm.contains("estacio de $normName") || titleNorm.contains("estacion de $normName")) {
                return@filter true
            }
        }
        false
    }

    val issues = matching.mapNotNull { inc ->
        val text = when {
            inc.descripcionEs.isNotBlank() -> inc.descripcionEs
            inc.descripcionCa.isNotBlank() -> inc.descripcionCa
            inc.tituloEs.isNotBlank() -> inc.tituloEs
            inc.tituloCa.isNotBlank() -> inc.tituloCa
            else -> null
        }
        text?.let { cleanAccessibilityText(it, stationName) }
    }.filter { it.isNotBlank() }.distinct()

    return StationAccessibilityInfo(
        isAccessible = issues.isEmpty(),
        stationName = stationName,
        issues = issues
    )
}

fun computeCercaniasStationAccessibility(
    stationId: String,
    stationName: String,
    alerts: List<CercaniasAlert>
): StationAccessibilityInfo {
    val normName = stationName.normalizeForSearch()
    val cleanId = stationId.substringBefore('_').substringBefore('-').trim()

    val matching = alerts.filter { alert ->
        if (!alert.isAccessibility) return@filter false

        // 1. If GTFS-RT alert has stopIds, it strictly identifies the affected stop
        if (alert.stopIds.isNotEmpty()) {
            return@filter alert.stopIds.any { sId ->
                val cleanSId = sId.substringBefore('_').substringBefore('-').trim()
                cleanSId.equals(cleanId, ignoreCase = true) || cleanSId.equals(stationId, ignoreCase = true)
            }
        }

        // 2. If stopIds is empty, strictly check if this station is the affected station
        // (Do NOT match "sentido <stationName>" or "dirección <stationName>")
        val fullText = "${alert.headerEs} ${alert.descriptionEs}".lowercase(java.util.Locale.ROOT)
        val cleanedText = fullText
            .replace(Regex("(sentido|dirección|direccion|hacia|destí|destino|destinació|destinacion)\\s+[a-záéíóúàèòñç·\\-\\s]+(de vía|de via|en vía|en via|,|\\.)"), " ")
            .replace(Regex("(sentido|dirección|direccion|hacia|destí|destino|destinació|destinacion)\\s+[a-záéíóúàèòñç·\\-\\s]+"), " ")
            .normalizeForSearch()

        if (cleanedText.contains("estacion de $normName") ||
            cleanedText.contains("estacio de $normName") ||
            cleanedText.contains("estacion $normName") ||
            cleanedText.contains("estacio $normName")
        ) {
            return@filter true
        }

        false
    }

    val issues = matching.mapNotNull { alert ->
        val desc = when {
            alert.descriptionEs.isNotBlank() -> alert.descriptionEs
            alert.headerEs.isNotBlank() -> alert.headerEs
            else -> null
        }
        desc?.let { cleanAccessibilityText(it, stationName) }
    }.filter { it.isNotBlank() }.distinct()

    return StationAccessibilityInfo(
        isAccessible = issues.isEmpty(),
        stationName = stationName,
        issues = issues
    )
}

@Composable
fun StationAccessibilityBadge(
    accessibilityInfo: StationAccessibilityInfo,
    appLanguage: AppLanguage = AppLanguage.ES,
    isDarkMode: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    modifier: Modifier = Modifier,
    iconSize: Dp = 19.dp,
    showContainerBadge: Boolean = false,
    showOnlyWhenNotAccessible: Boolean = true
) {
    // Hidden when accessible to avoid cluttering selection lists and favorite chips
    if (accessibilityInfo.isAccessible && showOnlyWhenNotAccessible) {
        return
    }

    var showBocadillo by remember { mutableStateOf(false) }

    val greenColor = if (isDarkMode) Color(0xFF34D399) else Color(0xFF059669)
    val redColor = if (isDarkMode) Color(0xFFF87171) else Color(0xFFDC2626)

    val activeColor = if (accessibilityInfo.isAccessible) greenColor else redColor
    val activeBg = if (accessibilityInfo.isAccessible) greenColor.copy(alpha = 0.14f) else redColor.copy(alpha = 0.16f)
    val activeBorder = if (accessibilityInfo.isAccessible) greenColor.copy(alpha = 0.35f) else redColor.copy(alpha = 0.45f)

    val contentDesc = if (accessibilityInfo.isAccessible) {
        if (appLanguage == AppLanguage.CA) "Estació accessible" else "Estación accesible"
    } else {
        if (appLanguage == AppLanguage.CA) "Incidència d'accessibilitat" else "Incidencia de accesibilidad"
    }

    // Accessible icons are non-clickable (no dialog popup). Only non-accessible (red) icons are clickable.
    val isClickable = !accessibilityInfo.isAccessible

    Box(
        modifier = modifier
            .wrapContentSize()
            .testTag("station_accessibility_badge_${if (accessibilityInfo.isAccessible) "green" else "red"}")
    ) {
        if (showContainerBadge || !accessibilityInfo.isAccessible) {
            Surface(
                modifier = Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (isClickable) {
                            Modifier.clickable { showBocadillo = true }
                        } else {
                            Modifier
                        }
                    ),
                shape = RoundedCornerShape(6.dp),
                color = activeBg,
                border = BorderStroke(1.dp, activeBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (accessibilityInfo.isAccessible) Icons.Default.Accessible else Icons.Default.NotAccessible,
                        contentDescription = contentDesc,
                        tint = activeColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(iconSize + 10.dp)
                    .clip(CircleShape)
                    .then(
                        if (isClickable) {
                            Modifier.clickable { showBocadillo = true }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Accessible,
                    contentDescription = contentDesc,
                    tint = greenColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        // Bocadillo popup (only triggers if non-accessible and user clicks)
        if (showBocadillo && !accessibilityInfo.isAccessible) {
            AccessibilityBocadilloDialog(
                accessibilityInfo = accessibilityInfo,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onDismiss = { showBocadillo = false }
            )
        }
    }
}

@Composable
fun AccessibilityBocadilloDialog(
    accessibilityInfo: StationAccessibilityInfo,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val greenColor = if (isDarkMode) Color(0xFF34D399) else Color(0xFF059669)
    val redColor = if (isDarkMode) Color(0xFFF87171) else Color(0xFFDC2626)
    val cardBg = if (isDarkMode) Color(0xFF1B2232) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF4B5563)
    val borderCol = if (accessibilityInfo.isAccessible) greenColor.copy(alpha = 0.5f) else redColor.copy(alpha = 0.5f)

    val groupedIssues = remember(accessibilityInfo.issues, accessibilityInfo.stationName) {
        groupAccessibilityIssues(accessibilityInfo.issues, accessibilityInfo.stationName)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(22.dp))
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(22.dp))
                .testTag("accessibility_bocadillo_dialog"),
            color = cardBg,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, borderCol),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabecera del bocadillo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (accessibilityInfo.isAccessible) greenColor.copy(alpha = 0.16f) else redColor.copy(alpha = 0.16f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (accessibilityInfo.isAccessible) Icons.Default.Accessible else Icons.Default.NotAccessible,
                                    contentDescription = null,
                                    tint = if (accessibilityInfo.isAccessible) greenColor else redColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Text(
                            text = accessibilityInfo.stationName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_close_accessibility_bocadillo")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Tancar" else "Cerrar",
                            tint = subtextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE5E7EB))

                // Cuerpo explicativo sin recuadros innecesarios
                if (accessibilityInfo.isAccessible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = greenColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) {
                                "Sense avisos actius d'accessibilitat"
                            } else {
                                "Sin avisos activos de accesibilidad"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Avisos reportats en esta estació:" else "Avisos reportados en esta estación:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = subtextColor
                        )

                        groupedIssues.forEach { grouped ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = redColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = grouped.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        lineHeight = 20.sp
                                    )
                                }

                                if (grouped.details.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 28.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        grouped.details.forEach { detail ->
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "•",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = redColor
                                                )
                                                Text(
                                                    text = detail,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = textColor,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pie de cierre
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (accessibilityInfo.isAccessible) greenColor else redColor
                    )
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Entés" else "Entendido",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AccessibilityAlertsDialog(
    stationName: String,
    rawAlerts: List<String>,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    isDarkMode: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val info = remember(stationName, rawAlerts) {
        StationAccessibilityInfo(
            isAccessible = rawAlerts.isEmpty(),
            stationName = stationName,
            issues = rawAlerts
        )
    }
    AccessibilityBocadilloDialog(
        accessibilityInfo = info,
        appLanguage = appLanguage,
        isDarkMode = isDarkMode,
        onDismiss = onDismiss
    )
}
