package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.ui.theme.appCardBorder

private val METRO_LINE_COLORS = mapOf(
    "L1" to Color(0xFFF59E0B),
    "L2" to Color(0xFFEC4899),
    "L3" to Color(0xFFEF4444),
    "L4" to Color(0xFF003366),
    "L5" to Color(0xFF10B981),
    "L6" to Color(0xFF8B5CF6),
    "L7" to Color(0xFFF97316),
    "L8" to Color(0xFF14B8A6),
    "L9" to Color(0xFF78350F),
    "L10" to Color(0xFF84CC16)
)

private val CERCANIAS_LINE_COLORS = mapOf(
    "C1" to Color(0xFF00A3E0),
    "C-1" to Color(0xFF00A3E0),
    "C2" to Color(0xFFFF6A00),
    "C-2" to Color(0xFFFF6A00),
    "C3" to Color(0xFF7A287B),
    "C-3" to Color(0xFF7A287B),
    "C4" to Color(0xFFE52321),
    "C-4" to Color(0xFFE52321),
    "C5" to Color(0xFF009639),
    "C-5" to Color(0xFF009639),
    "C6" to Color(0xFF002F6C),
    "C-6" to Color(0xFF002F6C)
)

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
    isCercaniasLoading: Boolean = false
) {
    val isCa = appLanguage == AppLanguage.CA

    // Analyze Metro Incidents
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
                    // Search for line pattern L1, L2, etc.
                    for ((line, color) in METRO_LINE_COLORS) {
                        if (lineFgv.contains(line)) {
                            linesSet.add(LineAlertStatus(line, color, category = category))
                        }
                    }
                }
            }

            Triple(linesSet.sortedBy { it.lineCode }, allLines, obras)
        }
    }

    // Analyze Cercanías Incidents
    val (cercaniasAffectedLines, cercaniasHasAll, cercaniasIsObras) = remember(cercaniasAlerts) {
        if (cercaniasAlerts.isEmpty()) {
            Triple(emptyList<LineAlertStatus>(), false, false)
        } else {
            val linesSet = mutableSetOf<LineAlertStatus>()
            var allLines = false
            var obras = false

            for (alert in cercaniasAlerts) {
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
                    // Check if description mentions all or general
                    for ((line, color) in CERCANIAS_LINE_COLORS) {
                        if (textLower.contains(line.lowercase())) {
                            linesSet.add(LineAlertStatus(line, color, category = category))
                        }
                    }
                } else {
                    for (routeId in alert.routeIds) {
                        val cleanRoute = routeId.uppercase()
                        val color = CERCANIAS_LINE_COLORS[cleanRoute] ?: Color(0xFFD32F2F)
                        linesSet.add(LineAlertStatus(cleanRoute, color, category = category))
                    }
                }
            }

            Triple(linesSet.sortedBy { it.lineCode }, allLines, obras)
        }
    }

    val totalActiveAlerts = metroIncidents.size + cercaniasAlerts.size

    val isAnyLoading = isMetroLoading || isCercaniasLoading

    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("incidencias_widget_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkMode) 0.dp else 2.dp)
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
                            Text(
                                text = if (isCa) "Servei habitual a les xarxes" else "Servicio habitual en las redes",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metro Row
            OperatorIncidentsRow(
                operatorName = "Metrovalencia",
                iconVector = Icons.Default.Subway,
                iconTint = Color(0xFF1E88E5),
                incidentsCount = metroIncidents.size,
                affectedLines = metroAffectedLines,
                hasAllLines = metroHasAll,
                isCa = isCa,
                isLoading = isMetroLoading,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenMetroAvisos
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Cercanías Row
            OperatorIncidentsRow(
                operatorName = "Cercanías València",
                iconVector = Icons.Default.DirectionsRailway,
                iconTint = Color(0xFFE53935),
                incidentsCount = cercaniasAlerts.size,
                affectedLines = cercaniasAffectedLines,
                hasAllLines = cercaniasHasAll,
                isCa = isCa,
                isLoading = isCercaniasLoading,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenCercaniasAvisos
            )
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
    hasAllLines: Boolean,
    isCa: Boolean,
    isLoading: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                    Text(
                        text = if (isCa) "Sense incidències" else "Sin incidencias",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF10B981),
                        fontSize = 12.sp
                    )
                } else if (hasAllLines) {
                    Text(
                        text = if (isCa) "Afecta a totes les línies" else "Afecta a todas las líneas",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (affectedLines.isEmpty()) {
                    Text(
                        text = if (isCa) "$incidentsCount avisos actius" else "$incidentsCount avisos activos",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800),
                        fontSize = 12.sp
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        affectedLines.take(4).forEach { lineStatus ->
                            LineBadgeChip(lineStatus = lineStatus)
                        }
                        if (affectedLines.size > 4) {
                            Text(
                                text = "+${affectedLines.size - 4}",
                                style = MaterialTheme.typography.labelSmall,
                                color = textSecondary
                            )
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
    val (bgColor, textColor, border) = when (lineStatus.category) {
        IncidentCategory.URGENT -> Triple(
            lineStatus.color,
            Color.White,
            null
        )
        IncidentCategory.BUS -> Triple(
            lineStatus.color,
            Color.White,
            BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        )
        IncidentCategory.OBRAS -> Triple(
            lineStatus.color.copy(alpha = 0.16f),
            lineStatus.color,
            BorderStroke(1.dp, lineStatus.color.copy(alpha = 0.55f))
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = border,
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
