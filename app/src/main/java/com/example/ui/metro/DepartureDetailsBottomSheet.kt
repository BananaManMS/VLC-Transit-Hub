package com.example.ui.metro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroScheduledStopPass
import com.example.data.repository.LineStationInfo
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private fun normalizeForMatch(text: String): String {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("-", " ")
        .replace(".", "")
        .replace("'", " ")
        .replace("’", " ")
        .replace("·", "")
        .replace("/", " ")
        .lowercase(Locale.getDefault())
        .trim()
}

private fun findMatchingTimelineStop(
    stationName: String,
    stops: List<MetroScheduledStopPass>
): MetroScheduledStopPass? {
    if (stops.isEmpty() || stationName.isBlank()) return null
    val targetNorm = normalizeForMatch(stationName)

    // 1. Exact normalized match
    val exact = stops.find { stop ->
        normalizeForMatch(stop.stationName) == targetNorm
    }
    if (exact != null) return exact

    // 2. Contains match
    val containsMatch = stops.find { stop ->
        val stopNorm = normalizeForMatch(stop.stationName)
        stopNorm.contains(targetNorm) || targetNorm.contains(stopNorm)
    }
    if (containsMatch != null) return containsMatch

    // 3. Significant token match (e.g. "Plaça d'Espanya" vs "Plaza de España")
    val targetTokens = targetNorm.split(" ").filter { it.length >= 3 }
    if (targetTokens.isNotEmpty()) {
        val best = stops.maxByOrNull { stop ->
            val stopNorm = normalizeForMatch(stop.stationName)
            val stopTokens = stopNorm.split(" ").filter { it.length >= 3 }
            targetTokens.count { t -> stopTokens.contains(t) }
        }
        if (best != null) {
            val stopNorm = normalizeForMatch(best.stationName)
            val stopTokens = stopNorm.split(" ").filter { it.length >= 3 }
            val matchCount = targetTokens.count { t -> stopTokens.contains(t) }
            if (matchCount > 0) return best
        }
    }

    return null
}

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
    onDismiss: () -> Unit,
    metroScheduleViewModel: MetroScheduleViewModel? = null,
    onStartQuickTrack: ((RealTimeDeparture) -> Unit)? = null
) {
    if (isBottomSheetVisible && selectedDepartureDetails != null) {
        val departure = selectedDepartureDetails
        val scheduleVm = metroScheduleViewModel ?: androidx.lifecycle.viewmodel.compose.viewModel()
        val timeline by scheduleVm.selectedTrainTimeline.collectAsState()
        val isLoadingTimeline by scheduleVm.isLoadingTimeline.collectAsState()

        val selectedStationId by metroViewModel.selectedStationId.collectAsState()
        val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
        val currentStationObj = remember(allNetworkStations, selectedStationId) {
            allNetworkStations.find { it.id == selectedStationId }
        }

        androidx.compose.runtime.LaunchedEffect(departure.id, selectedStationId) {
            scheduleVm.loadTimelineForLiveTrain(
                stationFgvId = selectedStationId ?: "",
                stationName = currentStationObj?.name ?: "",
                line = departure.lineId,
                destinationName = departure.destination,
                secondsRemaining = departure.secondsRemaining,
                estimatedTime = departure.estimatedTime,
                originStationWebId = departure.originWebId,
                destinationWebId = departure.destinationWebId,
                trainServiceId = departure.trainServiceId
            )
        }

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

        val lineStations = remember(lineStationsMap, departure.lineId, departure.destination) {
            val primaryList = lineStationsMap[departure.lineId] ?: emptyList()
            val normDest = Normalizer.normalize(departure.destination, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
            
            val hasDest = primaryList.any {
                val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                normName == normDest || normName.contains(normDest) || normDest.contains(normName)
            }
            if (hasDest) {
                primaryList
            } else {
                var foundList: List<LineStationInfo>? = null
                for ((_, stations) in lineStationsMap) {
                    val matches = stations.any {
                        val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                        normName == normDest || normName.contains(normDest) || normDest.contains(normName)
                    }
                    if (matches) {
                        foundList = stations
                        break
                    }
                }
                foundList ?: primaryList
            }
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

        val timelineStopPasses: List<MetroScheduledStopPass> = timeline?.stops ?: emptyList()

        // Stations the train already stopped at before reaching the current station
        val previousStations = remember(lineStations, currIdx, isForward) {
            if (lineStations.isEmpty()) emptyList()
            else if (isForward) {
                if (currIdx > 0) lineStations.subList(0, currIdx) else emptyList()
            } else {
                if (currIdx < lineStations.size - 1) lineStations.subList(currIdx + 1, lineStations.size).reversed() else emptyList()
            }
        }

        var isPreviousStationsExpanded by remember { mutableStateOf(false) }

        // Compute estimated arrival time at the current station in Europe/Madrid timezone
        val madridTimeZone = remember { TimeZone.getTimeZone("Europe/Madrid") }

        val currentStationArrivalMinutes = remember(departure.estimatedTime, departure.secondsRemaining) {
            if (!departure.estimatedTime.isNullOrBlank() && departure.estimatedTime.contains(":")) {
                val parts = departure.estimatedTime.trim().split(":")
                val h = parts.getOrNull(0)?.toIntOrNull()
                val m = parts.getOrNull(1)?.toIntOrNull()
                if (h != null && m != null) {
                    h * 60 + m
                } else {
                    val cal = Calendar.getInstance(madridTimeZone)
                    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    nowMin + (departure.secondsRemaining / 60)
                }
            } else {
                val cal = Calendar.getInstance(madridTimeZone)
                val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                nowMin + (departure.secondsRemaining / 60)
            }
        }

        val currentStationArrivalFormatted = remember(departure.estimatedTime, currentStationArrivalMinutes) {
            if (!departure.estimatedTime.isNullOrBlank() && departure.estimatedTime.contains(":")) {
                departure.estimatedTime.trim()
            } else {
                val hh = (currentStationArrivalMinutes / 60) % 24
                val mm = currentStationArrivalMinutes % 60
                String.format(Locale.getDefault(), "%02d:%02d", hh, mm)
            }
        }

        // Matched stop for current station in schedule timeline
        val matchedCurrentStop = remember(timelineStopPasses, currentStationName) {
            timelineStopPasses.find { it.isCurrentStation }
                ?: findMatchingTimelineStop(currentStationName, timelineStopPasses)
        }

        // Calculate real-time delay relative to timetable (e.g. if scheduled at 12:30 and real time is 13:33, delay is +63 min)
        val delayMinutes = remember(matchedCurrentStop, currentStationArrivalMinutes, departure.id) {
            val isScheduledOnly = departure.id.startsWith("sched_")
            if (matchedCurrentStop != null && !isScheduledOnly) {
                var diff = currentStationArrivalMinutes - matchedCurrentStop.timeMinutes
                if (diff > 720) diff -= 1440
                if (diff < -720) diff += 1440
                diff
            } else {
                0
            }
        }

        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier
                .statusBarsPadding()
                .testTag("departure_details_bottom_sheet")
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
                    MetroLineBadge(
                        lineId = departure.lineId,
                        fallbackColorHex = departure.colorHex,
                        size = 48.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = departure.destination,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val timeColor = if (uiModel.bottomSheetText.contains("Eixint ara") || uiModel.bottomSheetText.contains("Saliendo ahora")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            Text(
                                text = uiModel.bottomSheetText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = timeColor
                            )

                            departure.carsCount?.let { count ->
                                val carsLabel = androidx.compose.ui.res.stringResource(com.example.R.string.metro_cars_count_format, count)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Text(
                                        text = carsLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        val detailsRow = listOfNotNull(
                            departure.estimatedTime?.let { "Hora est.: $it" },
                            departure.track,
                            departure.status?.takeIf { it != "En hora" && it.isNotBlank() }
                        ).joinToString(" • ")

                        if (detailsRow.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = detailsRow,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (onStartQuickTrack != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onStartQuickTrack(departure)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("departure_details_track_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkMode) lineColor.copy(alpha = 0.22f).compositeOver(Color(0xFF262832)) else lineColor.copy(alpha = 0.12f),
                            contentColor = if (isDarkMode) Color.White else lineColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Seguir este tren (Notificació en viu)" else "Seguir este tren (Notificación en vivo)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Incidents & Service Alerts Section
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

                if (directIncidents.isNotEmpty() || indirectIncidents.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    )

                    Text(
                        text = if (appLanguage == AppLanguage.CA) "ESTAT DE LA LÍNIA" else "ESTADO DEL SERVICIO",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    MetroLineIncidentsSection(
                        directIncidents = directIncidents,
                        indirectIncidents = indirectIncidents,
                        lineId = departure.lineId,
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode
                    )
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

                // Section 1: Previous Stations
                if (previousStations.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("previous_stations_section")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isPreviousStationsExpanded = !isPreviousStationsExpanded }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("toggle_previous_stations"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (appLanguage == AppLanguage.CA)
                                        "Estacions anteriors (${previousStations.size})"
                                    else
                                        "Estaciones anteriores (${previousStations.size})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isPreviousStationsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isPreviousStationsExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isPreviousStationsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                previousStations.forEachIndexed { index, station ->
                                    val prevStationZoneClean = if (index == 0) {
                                        com.example.data.model.cleanZoneCode(station.zone)
                                    } else {
                                        com.example.data.model.cleanZoneCode(previousStations[index - 1].zone)
                                    }
                                    val thisStationZoneClean = com.example.data.model.cleanZoneCode(station.zone)
                                    val showZoneBadge = index == 0 || thisStationZoneClean != prevStationZoneClean

                                    val matchedStop = findMatchingTimelineStop(station.name, timelineStopPasses)
                                    val prevArrivalFormatted = matchedStop?.timeFormatted?.takeIf { it.isNotBlank() }

                                    RouteStationRow(
                                        stationInfo = station,
                                        allNetworkStations = allNetworkStations,
                                        lineColor = lineColor,
                                        currentLineId = departure.lineId,
                                        isPassed = true,
                                        isDestination = false,
                                        appLanguage = appLanguage,
                                        isFirst = (index == 0),
                                        isLast = false,
                                        showZoneBadge = showZoneBadge,
                                        isDarkMode = isDarkMode,
                                        estimatedArrivalFormatted = prevArrivalFormatted
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Current Station
                val curName = currentStationInfo?.name ?: currentStationName
                if (curName.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) lineColor.copy(alpha = 0.22f).compositeOver(Color(0xFF232630)) else lineColor.copy(alpha = 0.12f)
                        )
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
                                    color = if (isDarkMode) Color.White.copy(alpha = 0.85f) else lineColor
                                )
                            }
                            if (currentStationInfo != null) {
                                val zColors = getColorsForZone(currentStationInfo.zone, false, isDarkMode)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = zColors.backgroundColor,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = formatZoneText(currentStationInfo.zone),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = zColors.textColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            val currentStationTimeContainerColor = if (isDarkMode) {
                                lineColor.copy(alpha = 0.35f).compositeOver(Color(0xFF232632))
                            } else {
                                lineColor.copy(alpha = 0.18f)
                            }
                            val currentStationTimeContentColor = if (isDarkMode) Color.White else lineColor

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = currentStationTimeContainerColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = currentStationTimeContentColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = currentStationArrivalFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = currentStationTimeContentColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Next Stations with Forecast Delay-Adjusted Arrival Times
                if (nextStations.isNotEmpty()) {
                    val currentZoneClean = currentStationInfo?.let { com.example.data.model.cleanZoneCode(it.zone) }
                        ?: (currentStationObj?.zone?.let { com.example.data.model.cleanZoneCode(it) })
                        ?: "A"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                    ) {
                        nextStations.forEachIndexed { index, station ->
                            val isDest = index == nextStations.size - 1 || station.name.equals(departure.destination, ignoreCase = true)
                            val thisStationZoneClean = com.example.data.model.cleanZoneCode(station.zone)
                            val prevStationZoneClean = if (index == 0) {
                                currentZoneClean
                            } else {
                                com.example.data.model.cleanZoneCode(nextStations[index - 1].zone)
                            }
                            val showZoneBadge = thisStationZoneClean != prevStationZoneClean

                            // Compute arrival time for upcoming station (exact timetable time for scheduled trains, delay-adjusted for live trains)
                            val matchedStop = findMatchingTimelineStop(station.name, timelineStopPasses)

                            val arrivalFormatted = if (matchedStop != null) {
                                if (departure.id.startsWith("sched_")) {
                                    matchedStop.timeFormatted
                                } else {
                                    val adjustedMin = (matchedStop.timeMinutes + delayMinutes + 1440 * 2) % 1440
                                    val hh = (adjustedMin / 60) % 24
                                    val mm = adjustedMin % 60
                                    String.format(Locale.getDefault(), "%02d:%02d", hh, mm)
                                }
                            } else {
                                null
                            }

                            RouteStationRow(
                                stationInfo = station,
                                allNetworkStations = allNetworkStations,
                                lineColor = lineColor,
                                currentLineId = departure.lineId,
                                isPassed = false,
                                isDestination = isDest,
                                appLanguage = appLanguage,
                                isFirst = false,
                                isLast = (index == nextStations.size - 1),
                                showZoneBadge = showZoneBadge,
                                isDarkMode = isDarkMode,
                                estimatedArrivalFormatted = arrivalFormatted
                            )
                        }
                    }
                }
            }
        }
    }
}
