package com.example.ui.metro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.data.repository.LineStationInfo
import com.example.ui.dashboard.AppLanguage

fun formatZoneText(rawZone: String): String {
    val z = com.example.data.model.cleanZoneCode(rawZone)
    return "Zona $z"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteStationRow(
    stationInfo: LineStationInfo,
    allNetworkStations: List<MetroStation>,
    lineColor: Color,
    currentLineId: String,
    isPassed: Boolean,
    isDestination: Boolean,
    appLanguage: AppLanguage,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    showZoneBadge: Boolean = false,
    isDarkMode: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    estimatedArrivalFormatted: String? = null
) {
    val fullStation = remember(allNetworkStations, stationInfo.id, stationInfo.name) {
        allNetworkStations.find { it.id == stationInfo.id || it.name.equals(stationInfo.name, ignoreCase = true) }
    }
    val stationLines = fullStation?.lines ?: emptyList()
    val transferLines = stationLines.filter { it != currentLineId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Track + Node Dot
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            val trackColor = if (isPassed) lineColor.copy(alpha = 0.3f) else lineColor

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cX = size.width / 2f
                val cY = size.height / 2f
                val strokeWidthPx = 3.dp.toPx()

                // Top segment
                if (!isFirst) {
                    drawLine(
                        color = trackColor,
                        start = Offset(cX, 0f),
                        end = Offset(cX, cY),
                        strokeWidth = strokeWidthPx
                    )
                }

                // Bottom segment
                if (!isLast) {
                    drawLine(
                        color = trackColor,
                        start = Offset(cX, cY),
                        end = Offset(cX, size.height),
                        strokeWidth = strokeWidthPx
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(if (isDestination) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (isPassed) Color.LightGray else Color.White)
                    .border(
                        width = if (isDestination) 3.dp else 2.5.dp,
                        color = if (isPassed) lineColor.copy(alpha = 0.5f) else lineColor,
                        shape = CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Station Info Content
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stationInfo.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isDestination) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isPassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    if (isDestination) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = lineColor,
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.destination_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (transferLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        transferLines.forEach { tLineId ->
                            val tLineObj = ValenciaMetroData.lines.find { it.id == tLineId }
                            val tColor = try {
                                Color(android.graphics.Color.parseColor(tLineObj?.colorHex ?: "#666666"))
                            } catch (e: Exception) {
                                Color(0xFF666666)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isPassed) tColor.copy(alpha = 0.5f) else tColor
                            ) {
                                Text(
                                    text = tLineId,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showZoneBadge) {
                    val zColors = getColorsForZone(stationInfo.zone, isPassed, isDarkMode)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = zColors.backgroundColor
                    ) {
                        Text(
                            text = formatZoneText(stationInfo.zone),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = zColors.textColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (estimatedArrivalFormatted != null) {
                    val badgeContainerColor = if (isDarkMode) {
                        lineColor.copy(alpha = 0.22f).compositeOver(Color(0xFF242732))
                    } else {
                        lineColor.copy(alpha = 0.1f)
                    }
                    val badgeContentColor = if (isDarkMode) Color.White else lineColor

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeContainerColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = badgeContentColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = estimatedArrivalFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeContentColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetroLineIncidentsSection(
    directIncidents: List<MetroIncident>,
    indirectIncidents: List<Pair<String, MetroIncident>>,
    lineId: String,
    appLanguage: AppLanguage,
    isDarkMode: Boolean
) {
    if (directIncidents.isEmpty() && indirectIncidents.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (directIncidents.isNotEmpty()) {
            directIncidents.forEach { incident ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF331818) else Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_incident_on_line, lineId),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) incident.descriptionCa else incident.descriptionEs,
                                color = if (isDarkMode) Color(0xFFFFCDD2) else Color(0xFFC62828),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        if (indirectIncidents.isNotEmpty()) {
            indirectIncidents.forEach { (sharedLineId, incident) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF331900) else Color(0xFFFFF3E0)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_incident_shared_track, sharedLineId),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) incident.descriptionCa else incident.descriptionEs,
                                color = if (isDarkMode) Color(0xFFFFE0B2) else Color(0xFFE65100),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ZoneColors(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color
)

fun getColorsForZone(rawZone: String, isPassed: Boolean, isDarkMode: Boolean): ZoneColors {
    val clean = rawZone.uppercase().replace(" ", "")
    val alpha = if (isPassed) (if (isDarkMode) 0.25f else 0.15f) else (if (isDarkMode) 0.5f else 0.35f)
    return when {
        clean.contains("AB") || (clean.contains("A") && clean.contains("B")) -> {
            if (isDarkMode) {
                ZoneColors(
                    backgroundColor = Color(0xFF064E3B).copy(alpha = alpha),
                    borderColor = Color(0xFF10B981).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFFA7F3D0).copy(alpha = 0.5f) else Color(0xFFA7F3D0)
                )
            } else {
                ZoneColors(
                    backgroundColor = Color(0xFFD1FAE5).copy(alpha = alpha),
                    borderColor = Color(0xFF10B981).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFF047857).copy(alpha = 0.5f) else Color(0xFF065F46)
                )
            }
        }
        clean.contains("A") -> {
            if (isDarkMode) {
                ZoneColors(
                    backgroundColor = Color(0xFF78350F).copy(alpha = alpha),
                    borderColor = Color(0xFFF59E0B).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFFFDE68A).copy(alpha = 0.5f) else Color(0xFFFDE68A)
                )
            } else {
                ZoneColors(
                    backgroundColor = Color(0xFFFEF3C7).copy(alpha = alpha),
                    borderColor = Color(0xFFF59E0B).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFFB45309).copy(alpha = 0.5f) else Color(0xFF92400E)
                )
            }
        }
        clean.contains("B") -> {
            if (isDarkMode) {
                ZoneColors(
                    backgroundColor = Color(0xFF1E3A8A).copy(alpha = alpha),
                    borderColor = Color(0xFF3B82F6).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFFBFDBFE).copy(alpha = 0.5f) else Color(0xFFBFDBFE)
                )
            } else {
                ZoneColors(
                    backgroundColor = Color(0xFFDBEAFE).copy(alpha = alpha),
                    borderColor = Color(0xFF3B82F6).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFF1D4ED8).copy(alpha = 0.5f) else Color(0xFF1E40AF)
                )
            }
        }
        clean.contains("+") -> {
            if (isDarkMode) {
                ZoneColors(
                    backgroundColor = Color(0xFF4C1D95).copy(alpha = alpha),
                    borderColor = Color(0xFF8B5CF6).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFFDDD6FE).copy(alpha = 0.5f) else Color(0xFFDDD6FE)
                )
            } else {
                ZoneColors(
                    backgroundColor = Color(0xFFEDE9FE).copy(alpha = alpha),
                    borderColor = Color(0xFF8B5CF6).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFF6D28D9).copy(alpha = 0.5f) else Color(0xFF5B21B6)
                )
            }
        }
        else -> {
            if (isDarkMode) {
                ZoneColors(
                    backgroundColor = Color(0xFF374151).copy(alpha = alpha),
                    borderColor = Color(0xFF9CA3AF).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFFE5E7EB).copy(alpha = 0.5f) else Color(0xFFF3F4F6)
                )
            } else {
                ZoneColors(
                    backgroundColor = Color(0xFFF3F4F6).copy(alpha = alpha),
                    borderColor = Color(0xFF9CA3AF).copy(alpha = if (isPassed) 0.3f else 1f),
                    textColor = if (isPassed) Color(0xFF374151).copy(alpha = 0.5f) else Color(0xFF111827)
                )
            }
        }
    }
}
