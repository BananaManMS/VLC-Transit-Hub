package com.example.ui.metro

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.data.repository.LineStationInfo
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DepartureDetailsBottomSheet(
    isBottomSheetVisible: Boolean,
    selectedDepartureDetails: RealTimeDeparture?,
    metroViewModel: MetroViewModel,
    appLanguage: AppLanguage,
    texts: Translation,
    isDarkMode: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    if (isBottomSheetVisible && selectedDepartureDetails != null) {
        val departure = selectedDepartureDetails

        val uiModel = remember(departure, appLanguage, texts, isDarkMode) {
            MetroMapper.toDepartureUiModel(
                departure,
                departure.secondsRemaining,
                appLanguage,
                texts,
                isDarkMode
            ) { digit -> metroViewModel.getSharedLineDigits(digit) }
        }

        val lineColor = remember(departure.colorHex) {
            try {
                Color(android.graphics.Color.parseColor(departure.colorHex))
            } catch (e: Exception) {
                Color(0xFFF59E0B)
            }
        }

        val lineStationsMap by metroViewModel.lineStationsState.collectAsState()
        val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
        val selectedStationId by metroViewModel.selectedStationId.collectAsState()

        val lineStations = remember(lineStationsMap, departure.lineId) {
            lineStationsMap[departure.lineId] ?: emptyList()
        }

        val currentStationObj = remember(allNetworkStations, selectedStationId) {
            allNetworkStations.find { it.id == selectedStationId }
        }
        val currentStationName = currentStationObj?.name ?: ""

        val currIdx = remember(lineStations, selectedStationId, currentStationName) {
            var idx = lineStations.indexOfFirst { it.id == selectedStationId }
            if (idx == -1 && currentStationName.isNotBlank()) {
                idx = lineStations.indexOfFirst { it.name.equals(currentStationName, ignoreCase = true) }
            }
            if (idx == -1 && currentStationName.isNotBlank()) {
                val normCurr = Normalizer.normalize(currentStationName, Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                idx = lineStations.indexOfFirst {
                    val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                    normName.contains(normCurr) || normCurr.contains(normName)
                }
            }
            if (idx == -1) 0 else idx
        }

        val destIdx = remember(lineStations, departure.destination) {
            val normDest = Normalizer.normalize(departure.destination, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
            var idx = lineStations.indexOfFirst {
                val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                normName == normDest
            }
            if (idx == -1) {
                idx = lineStations.indexOfFirst {
                    val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                    normName.contains(normDest) || normDest.contains(normName)
                }
            }
            if (idx == -1) lineStations.size - 1 else idx
        }

        val isForward = destIdx >= currIdx

        val currentStationInfo = remember(lineStations, currIdx) {
            if (lineStations.isNotEmpty() && currIdx in lineStations.indices) lineStations[currIdx] else null
        }

        val nextStations = remember(lineStations, currIdx, destIdx, isForward) {
            if (lineStations.isEmpty()) emptyList()
            else if (isForward) {
                if (currIdx < lineStations.size - 1) lineStations.subList(currIdx + 1, minOf(destIdx + 1, lineStations.size)) else emptyList()
            } else {
                if (currIdx > 0) lineStations.subList(maxOf(0, destIdx), currIdx).reversed() else emptyList()
            }
        }

        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier.testTag("departure_details_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp, top = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(lineColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = departure.lineId,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = departure.destination,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val timeColor = if (uiModel.bottomSheetText.contains("Eixint ara") || uiModel.bottomSheetText.contains("Saliendo ahora")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            text = uiModel.bottomSheetText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = timeColor
                        )
                    }
                }

                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )

                // Estado de la Línea (Incidencias / Normalidad)
                Text(
                    text = if (appLanguage == AppLanguage.CA) "ESTAT DE LA LÍNIA" else "ESTADO DEL SERVICIO",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                val directIncidents = metroViewModel.getIncidentsForLine(departure.lineId)
                    .distinctBy { if (it.id.isNotBlank()) it.id.trim() else (it.descriptionEs.trim() + "_" + it.descriptionCa.trim()).ifBlank { it.toString() } }
                val existingKeys = mutableSetOf<String>()
                directIncidents.forEach { incident ->
                    if (incident.id.isNotBlank()) existingKeys.add(incident.id.trim())
                    val descKey = (incident.descriptionEs.trim() + "_" + incident.descriptionCa.trim()).trim('_')
                    if (descKey.isNotBlank()) existingKeys.add(descKey)
                }

                val indirectIncidents = mutableListOf<Pair<String, MetroIncident>>()
                for (sharedDigit in uiModel.sharedDigits) {
                    val sharedLineId = "L$sharedDigit"
                    val sharedIncidents = metroViewModel.getIncidentsForLine(sharedLineId)
                    for (incident in sharedIncidents) {
                        val idKey = incident.id.trim()
                        val descKey = (incident.descriptionEs.trim() + "_" + incident.descriptionCa.trim()).trim('_')
                        val isDuplicate = (idKey.isNotEmpty() && existingKeys.contains(idKey)) ||
                                (descKey.isNotEmpty() && existingKeys.contains(descKey))
                        if (!isDuplicate) {
                            if (idKey.isNotEmpty()) existingKeys.add(idKey)
                            if (descKey.isNotEmpty()) existingKeys.add(descKey)
                            indirectIncidents.add(Pair(sharedLineId, incident))
                        }
                    }
                }

                val hasDirect = directIncidents.isNotEmpty()
                val hasIndirect = indirectIncidents.isNotEmpty()

                if (hasDirect || hasIndirect) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (hasDirect) {
                            directIncidents.forEach { incident ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDarkMode) Color(0xFF331818) else Color(0xFFFFEBEE)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f))
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
                                                text = if (appLanguage == AppLanguage.CA) "Incidència en ${departure.lineId}" else "Incidencia en ${departure.lineId}",
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
                        if (hasIndirect) {
                            indirectIncidents.forEach { (lineId, incident) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDarkMode) Color(0xFF331900) else Color(0xFFFFF3E0)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
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
                                                text = if (appLanguage == AppLanguage.CA) "Afectació per pas compartit ($lineId)" else "Afectación por paso compartido ($lineId)",
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
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF132219) else Color(0xFFE8F5E9)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2ECC71),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Funcionant amb normalitat" else "Funcionando con normalidad",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color(0xFFC8E6C9) else Color(0xFF1B5E20),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Línia operant sense incidències en aquest moment." else "Línea operando sin incidencias en este momento.",
                                    color = if (isDarkMode) Color.White.copy(alpha = 0.9f) else Color(0xFF2E7D32).copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )

                // Recorrido y Estaciones Header
                Text(
                    text = if (appLanguage == AppLanguage.CA) "RECORREGUT I PRÒXIMES ESTACIONS" else "RECORRIDO Y PRÓXIMAS ESTACIONES",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // Current Station
                val curName = currentStationInfo?.name ?: currentStationName
                if (curName.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = lineColor.copy(alpha = 0.15f)
                        ),
                        border = BorderStroke(1.5.dp, lineColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(lineColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsSubway,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = curName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Estació actual" else "Estación actual",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = lineColor
                                )
                            }
                            if (currentStationInfo != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = formatZoneText(currentStationInfo.zone),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Next Stations (NO HACE FALTA QUE SEA DESPLEGABLE / DIRECTLY VISIBLE)
                if (nextStations.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                    ) {
                        nextStations.forEachIndexed { index, station ->
                            val isDest = index == nextStations.size - 1 || station.name.equals(departure.destination, ignoreCase = true)
                            RouteStationRow(
                                stationInfo = station,
                                allNetworkStations = allNetworkStations,
                                lineColor = lineColor,
                                currentLineId = departure.lineId,
                                isPassed = false,
                                isDestination = isDest,
                                appLanguage = appLanguage,
                                isFirst = false,
                                isLast = (index == nextStations.size - 1)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatZoneText(rawZone: String): String {
    val z = com.example.data.model.cleanZoneCode(rawZone)
    return "Zona $z"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteStationRow(
    stationInfo: LineStationInfo,
    allNetworkStations: List<MetroStation>,
    lineColor: Color,
    currentLineId: String,
    isPassed: Boolean,
    isDestination: Boolean,
    appLanguage: AppLanguage,
    isFirst: Boolean = false,
    isLast: Boolean = false
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

                // Top segment (from top to dot center)
                if (!isFirst) {
                    drawLine(
                        color = trackColor,
                        start = Offset(cX, 0f),
                        end = Offset(cX, cY),
                        strokeWidth = strokeWidthPx
                    )
                }

                // Bottom segment (from dot center to bottom)
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
                                text = if (appLanguage == AppLanguage.CA) "Destí" else "Destino",
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

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Text(
                    text = formatZoneText(stationInfo.zone),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}
