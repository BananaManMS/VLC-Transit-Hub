package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.components.shimmerEffect
import com.example.ui.metro.MetroIncident
import com.example.ui.metro.MetroNotice
import com.example.ui.theme.appCardBorder
import com.example.util.LineColorResolver

private val METRO_LINES = listOf("L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8", "L9", "L10")
private val CERCANIAS_LINES = listOf("C1", "C2", "C3", "C4", "C5", "C6")

enum class IncidentCategory {
    URGENT,
    BUS,
    OBRAS
}

data class LineAlertStatus(
    val lineCode: String,
    val color: Color,
    val category: IncidentCategory = IncidentCategory.URGENT,
    val customTag: String? = null
)

@Composable
fun IncidenciasWidget(
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    metroIncidents: List<MetroIncident>,
    cercaniasAlerts: List<CercaniasAlert>,
    onOpenMetroAvisos: () -> Unit,
    onOpenCercaniasAvisos: () -> Unit,
    modifier: Modifier = Modifier,
    isMetroLoading: Boolean = false,
    isCercaniasLoading: Boolean = false,
    hasMetroError: Boolean = false,
    hasCercaniasError: Boolean = false,
    isOnline: Boolean = true,
    preferredTransitModes: Set<String> = emptySet()
) {
    val isCa = appLanguage == AppLanguage.CA

    val showMetro = preferredTransitModes.isEmpty() ||
            preferredTransitModes.contains("METRO") ||
            (!preferredTransitModes.contains("METRO") && !preferredTransitModes.contains("CERCANIAS"))

    val showCercanias = preferredTransitModes.isEmpty() ||
            preferredTransitModes.contains("CERCANIAS") ||
            (!preferredTransitModes.contains("METRO") && !preferredTransitModes.contains("CERCANIAS"))

    // Analyze Metro Real Circulation Breakdown (Only INCIDENCIA & AVISO DE CIRCULACIÓN)
    val categoryCounts = remember(metroIncidents) {
        var incidencias = 0
        var avisos = 0

        for (incident in metroIncidents) {
            val catEnum = com.example.ui.metro.MetroNoticeCategory.fromRaw(incident.category ?: "incidencia")
            when (catEnum) {
                com.example.ui.metro.MetroNoticeCategory.AVISO -> avisos++
                else -> incidencias++
            }
        }

        MetroCategoryCounts(
            incidencias = incidencias,
            avisos = avisos
        )
    }

    val metroCategoryBadges = remember(categoryCounts, isCa, isDarkMode) {
        val list = mutableListOf<CategoryCountBadge>()
        val incColor = if (isDarkMode) Color(0xFFEF5350) else Color(0xFFC62828)
        val avisoColor = if (isDarkMode) Color(0xFFFF7043) else Color(0xFFD84315)

        if (categoryCounts.incidencias > 0) {
            list.add(
                CategoryCountBadge(
                    label = if (isCa) if (categoryCounts.incidencias == 1) "INCIDÈNCIA" else "INCIDÈNCIES" else if (categoryCounts.incidencias == 1) "INCIDENCIA" else "INCIDENCIAS",
                    count = categoryCounts.incidencias,
                    color = incColor,
                    icon = Icons.Default.ReportProblem
                )
            )
        }
        if (categoryCounts.avisos > 0) {
            list.add(
                CategoryCountBadge(
                    label = if (isCa) if (categoryCounts.avisos == 1) "AVÍS" else "AVISOS" else if (categoryCounts.avisos == 1) "AVISO" else "AVISOS",
                    count = categoryCounts.avisos,
                    color = avisoColor,
                    icon = Icons.Default.ReportProblem
                )
            )
        }
        list
    }

    // Analyze Metro Line Incidents
    val (metroAffectedLines, metroHasAll, metroIsObras) = remember(metroIncidents) {
        if (metroIncidents.isEmpty()) {
            Triple(emptyList<LineAlertStatus>(), false, false)
        } else {
            val linesSet = mutableSetOf<LineAlertStatus>()
            var allLines = false
            var obras = false

            for (incident in metroIncidents) {
                val descLower = (incident.descriptionEs + " " + incident.descriptionCa).lowercase()
                val isBus = descLower.contains("autobús") || descLower.contains("bus")
                val isOb = descLower.contains("obra") || descLower.contains("plan alternativo") || descLower.contains("prolongad") || descLower.contains("treballs") || descLower.contains("mantenimient")

                if (isOb || isBus) obras = true

                val category = when {
                    isBus -> IncidentCategory.BUS
                    isOb -> IncidentCategory.OBRAS
                    else -> IncidentCategory.URGENT
                }

                val lineFgv = incident.lineaFgv?.uppercase() ?: ""
                if (lineFgv.contains("TODAS") || lineFgv.contains("TOTES")) {
                    allLines = true
                } else {
                    for (line in METRO_LINES) {
                        if (lineFgv.contains(line)) {
                            linesSet.add(LineAlertStatus(line, LineColorResolver.getMetroLineColor(line), category = category))
                        }
                    }
                }
            }

            Triple(linesSet.sortedBy { it.lineCode }, allLines, obras)
        }
    }

    // Analyze Cercanías Incidents (only strong circulation incidents)
    val filteredCercaniasAlerts = remember(cercaniasAlerts) {
        cercaniasAlerts.filter { it.isCirculationIncident && !it.isAccessibility }
    }

    val (cercaniasAffectedLines, cercaniasHasAll, cercaniasIsObras) = remember(filteredCercaniasAlerts) {
        if (filteredCercaniasAlerts.isEmpty()) {
            Triple(emptyList<LineAlertStatus>(), false, false)
        } else {
            val linesSet = mutableSetOf<LineAlertStatus>()
            var allLines = false
            var obras = false

            for (alert in filteredCercaniasAlerts) {
                val textLower = (alert.headerEs + " " + alert.descriptionEs).lowercase()
                val isBus = textLower.contains("autobús") || textLower.contains("bus") || textLower.contains("autobuses")
                val isOb = textLower.contains("obra") || textLower.contains("corte prolongado") ||
                        textLower.contains("recorrido alternativo") || textLower.contains("plan alternativo")

                if (isOb || isBus) obras = true

                val category = when {
                    isBus -> IncidentCategory.BUS
                    isOb -> IncidentCategory.OBRAS
                    else -> IncidentCategory.URGENT
                }

                if (alert.routeIds.isEmpty()) {
                    for (line in CERCANIAS_LINES) {
                        if (textLower.contains(line.lowercase())) {
                            linesSet.add(LineAlertStatus(line, LineColorResolver.getCercaniasLineColor(line), category = category))
                        }
                    }
                } else {
                    for (routeId in alert.routeIds) {
                        val cleanRoute = routeId.uppercase().trim()
                        val color = LineColorResolver.getCercaniasLineColor(cleanRoute)
                        linesSet.add(LineAlertStatus(cleanRoute, color, category = category))
                    }
                }
            }

            Triple(linesSet.sortedBy { it.lineCode }, allLines, obras)
        }
    }

    val totalMetroIncidents = if (showMetro) metroIncidents.size else 0
    val totalCercaniasIncidents = if (showCercanias) filteredCercaniasAlerts.size else 0
    val totalActiveAlerts = totalMetroIncidents + totalCercaniasIncidents

    val isAnyLoading = (showMetro && isMetroLoading) || (showCercanias && isCercaniasLoading)

    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("incidencias_widget_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = if (isCa) "Incidències en el servei" else "Incidencias en el servicio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        if (isAnyLoading) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .width(140.dp)
                                    .height(14.dp)
                                    .shimmerEffect(shape = RoundedCornerShape(4.dp))
                            )
                        } else if (totalActiveAlerts == 0) {
                            if (!isOnline) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isCa) "Sense connexió • Incidències no disponibles" else "Sin conexión • Incidencias no disponibles",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metro Row
            if (showMetro) {
                OperatorIncidentsRow(
                    operatorName = "Metrovalencia",
                    iconVector = Icons.Default.Subway,
                    iconTint = Color(0xFF1E88E5),
                    incidentsCount = totalMetroIncidents,
                    affectedLines = metroAffectedLines,
                    categoryBadges = metroCategoryBadges,
                    hasAllLines = metroHasAll,
                    isCa = isCa,
                    isLoading = isMetroLoading,
                    hasError = hasMetroError,
                    isDarkMode = isDarkMode,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = onOpenMetroAvisos,
                    isOnline = isOnline
                )
            }

            if (showMetro && showCercanias) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // Cercanías Row
            if (showCercanias) {
                OperatorIncidentsRow(
                    operatorName = "Cercanías València",
                    iconVector = Icons.Default.DirectionsRailway,
                    iconTint = Color(0xFFE53935),
                    incidentsCount = filteredCercaniasAlerts.size,
                    affectedLines = cercaniasAffectedLines,
                    hasAllLines = cercaniasHasAll,
                    isCa = isCa,
                    isLoading = isCercaniasLoading,
                    hasError = hasCercaniasError,
                    isDarkMode = isDarkMode,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = onOpenCercaniasAvisos,
                    isOnline = isOnline
                )
            }
        }
    }
}

@Composable
private fun OperatorIncidentsRow(
    operatorName: String,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    incidentsCount: Int,
    affectedLines: List<LineAlertStatus>,
    categoryBadges: List<CategoryCountBadge> = emptyList(),
    hasAllLines: Boolean,
    isCa: Boolean,
    isLoading: Boolean,
    hasError: Boolean = false,
    isDarkMode: Boolean = false,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    isOnline: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = operatorName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(110.dp)
                            .height(13.dp)
                            .shimmerEffect(shape = RoundedCornerShape(4.dp))
                    )
                } else if (incidentsCount == 0) {
                    if (!isOnline) {
                        Text(
                            text = if (isCa) "Sense connexió" else "Sin conexión",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    } else if (hasError) {
                        Text(
                            text = if (isCa) "Informació no disponible" else "Información no disponible",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706),
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = if (isCa) "Sense incidències" else "Sin incidencias",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF10B981),
                            fontSize = 12.sp
                        )
                    }
                } else if (categoryBadges.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categoryBadges.forEach { badge ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badge.color.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = badge.icon,
                                        contentDescription = null,
                                        tint = badge.color,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${badge.count} ${badge.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badge.color,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (hasAllLines) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCa) "Afecta a totes les línies" else "Afecta a todas las líneas",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (affectedLines.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCa) "$incidentsCount avisos actius" else "$incidentsCount avisos activos",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        affectedLines.forEach { lineStatus ->
                            LineBadgeChip(lineStatus = lineStatus)
                        }
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LineBadgeChip(lineStatus: LineAlertStatus) {
    val (bgColor, textColor) = when (lineStatus.category) {
        IncidentCategory.URGENT -> Pair(
            lineStatus.color,
            Color.White
        )
        IncidentCategory.BUS -> Pair(
            lineStatus.color,
            Color.White
        )
        IncidentCategory.OBRAS -> Pair(
            lineStatus.color.copy(alpha = 0.16f),
            lineStatus.color
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (lineStatus.category) {
                IncidentCategory.URGENT -> {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
                IncidentCategory.BUS -> {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
                IncidentCategory.OBRAS -> {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = lineStatus.color,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Text(
                text = lineStatus.lineCode,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.sp
            )

            val tagText = lineStatus.customTag ?: when (lineStatus.category) {
                IncidentCategory.BUS -> "Bus"
                IncidentCategory.OBRAS -> "Obras"
                IncidentCategory.URGENT -> null
            }

            if (!tagText.isNullOrBlank()) {
                Text(
                    text = tagText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (lineStatus.category == IncidentCategory.OBRAS) FontWeight.Medium else FontWeight.SemiBold,
                    color = textColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}
