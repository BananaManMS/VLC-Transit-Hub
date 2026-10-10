package com.example.ui.metro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.quicktrack.QuickTrackedVehicle
import com.example.data.model.quicktrack.TrackedDownstreamStop
import com.example.data.repository.LineStationInfo
import com.example.ui.dashboard.AppLanguage
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTrainTrackerBottomSheet(
    isVisible: Boolean,
    departure: RealTimeDeparture?,
    originStationName: String,
    originStationId: String,
    lineStations: List<LineStationInfo>,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onTrackSelected: (QuickTrackedVehicle) -> Unit
) {
    if (!isVisible || departure == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lineColor = remember(departure.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(departure.colorHex))
        } catch (_: Exception) {
            Color(0xFFE53935)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val metroViewModel: MetroViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val lineStationsMap by metroViewModel.lineStationsState.collectAsState()

    // Resolve lineStations using the exact robust logic from DepartureDetailsBottomSheet
    val resolvedLineStations = remember(lineStationsMap, lineStations, departure.lineId, departure.destination) {
        if (lineStations.isNotEmpty()) {
            val normDest = Normalizer.normalize(departure.destination, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
            val hasDest = lineStations.any {
                val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                normName == normDest || normName.contains(normDest) || normDest.contains(normName)
            }
            if (hasDest) return@remember lineStations
        }
        val cleanedLineId = departure.lineId.filter { it.isDigit() }
        val possibleKeys = listOf(
            departure.lineId,
            "L$cleanedLineId",
            "line_$cleanedLineId",
            cleanedLineId
        )
        var primaryList: List<LineStationInfo> = emptyList()
        for (k in possibleKeys) {
            if (lineStationsMap.containsKey(k)) {
                primaryList = lineStationsMap[k] ?: emptyList()
                if (primaryList.isNotEmpty()) break
            }
        }
        if (primaryList.isEmpty()) {
            primaryList = lineStationsMap.values.firstOrNull() ?: lineStations
        }

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

    // Resolve downstream stops
    val downstreamStops = remember(context, resolvedLineStations, departure.destination, originStationId, originStationName) {
        if (resolvedLineStations.isEmpty()) return@remember emptyList<TrackedDownstreamStop>()

        val normOrigin = Normalizer.normalize(originStationName, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
        val normDest = Normalizer.normalize(departure.destination, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()

        var currIdx = resolvedLineStations.indexOfFirst { it.id == originStationId }
        if (currIdx == -1 && originStationName.isNotBlank()) {
            currIdx = resolvedLineStations.indexOfFirst { it.name.equals(originStationName, ignoreCase = true) }
        }
        if (currIdx == -1 && originStationName.isNotBlank()) {
            currIdx = resolvedLineStations.indexOfFirst {
                val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                normName.contains(normOrigin) || normOrigin.contains(normName)
            }
        }
        if (currIdx == -1) currIdx = 0

        var destIdx = resolvedLineStations.indexOfFirst {
            val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
            normName == normDest
        }
        if (destIdx == -1) {
            destIdx = resolvedLineStations.indexOfFirst {
                val normName = Normalizer.normalize(it.name, Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
                normName.contains(normDest) || normDest.contains(normName)
            }
        }
        if (destIdx == -1) destIdx = resolvedLineStations.size - 1

        val isForward = destIdx >= currIdx
        val slice = if (isForward) {
            if (currIdx < resolvedLineStations.size - 1) resolvedLineStations.subList(currIdx + 1, minOf(destIdx + 1, resolvedLineStations.size)) else emptyList()
        } else {
            if (currIdx > 0) resolvedLineStations.subList(maxOf(0, destIdx), currIdx).reversed() else emptyList()
        }

        val madridTz = TimeZone.getTimeZone("Europe/Madrid")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = madridTz
        }

        val originStation = resolvedLineStations.getOrNull(currIdx)
        var accumDeltaMin = 0

        slice.mapIndexed { idx, st ->
            val prevStationId = if (idx == 0) (originStation?.id ?: originStationId) else slice[idx - 1].id
            val hopMin = com.example.util.MetroInterstationTimesHelper.getHopTime(context, prevStationId, st.id)
            accumDeltaMin += hopMin

            val arrivalEpoch = departure.targetArrivalEpochMs + (accumDeltaMin * 60_000L)
            val timeStr = timeFormat.format(Date(arrivalEpoch))
            TrackedDownstreamStop(
                stationId = st.id,
                stationName = st.name,
                zone = st.zone,
                scheduledArrivalTime = timeStr,
                deltaMinutesFromOrigin = accumDeltaMin
            )
        }
    }

    val favoriteStations by metroViewModel.favoriteStations.collectAsState()

    val sortedDownstreamStops = remember(downstreamStops, favoriteStations) {
        if (favoriteStations.isEmpty()) {
            downstreamStops
        } else {
            val favSet = favoriteStations.toSet()
            val favs = downstreamStops.filter { favSet.contains(it.stationId) || favSet.contains(it.stationName) }
            val others = downstreamStops.filter { !favSet.contains(it.stationId) && !favSet.contains(it.stationName) }
            favs + others
        }
    }

    val liveOriginMins = departure.liveMinutesRemaining

    val isValencian = appLanguage == AppLanguage.CA
    val sheetLocale = remember(isValencian) {
        if (isValencian) Locale("ca") else Locale("es")
    }
    val sheetConfig = remember(sheetLocale, context) {
        android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(sheetLocale)
        }
    }
    val sheetContext = remember(sheetLocale, context) {
        context.createConfigurationContext(sheetConfig)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        modifier = Modifier
            .statusBarsPadding()
            .testTag("quick_train_tracker_sheet")
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalConfiguration provides sheetConfig,
            androidx.compose.ui.platform.LocalContext provides sheetContext
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp, top = 2.dp)
            ) {
            // Header: Train Pill + Destination
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetroLineBadge(
                    lineId = departure.lineId,
                    fallbackColorHex = departure.colorHex,
                    size = 38.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = departure.destination,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sheetContext.getString(R.string.quick_track_sheet_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = lineColor.copy(alpha = if (isDarkMode) 0.25f else 0.12f)
                ) {
                    if (departure.isRealTime) {
                        Text(
                            text = if (liveOriginMins <= 0) sheetContext.getString(R.string.quick_track_departing_now) else "$liveOriginMins min",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else lineColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isDarkMode) Color.White else lineColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = departure.estimatedTime ?: "$liveOriginMins min",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color.White else lineColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Option 1: Pin here at current station
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_departure_here_btn")
                    .clickable {
                        val tracked = QuickTrackedVehicle(
                            lineId = departure.lineId,
                            destination = departure.destination,
                            colorHex = departure.colorHex,
                            originStationId = originStationId,
                            originStationName = originStationName,
                            targetStationId = null,
                            targetStationName = null,
                            targetScheduledTime = departure.estimatedTime,
                            initialMinutesRemaining = liveOriginMins,
                            targetArrivalEpochMs = departure.targetArrivalEpochMs,
                            isRealTime = departure.isRealTime,
                            downstreamStops = downstreamStops,
                            vehicleId = departure.vehicleId,
                            trainServiceId = departure.trainServiceId,
                            originWebId = departure.originWebId,
                            destinationWebId = departure.destinationWebId
                        )
                        onTrackSelected(tracked)
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) {
                        lineColor.copy(alpha = 0.20f).compositeOver(Color(0xFF222530))
                    } else {
                        lineColor.copy(alpha = 0.09f)
                    }
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(lineColor.copy(alpha = 0.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (isDarkMode) Color.White else lineColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sheetContext.getString(R.string.quick_track_pin_here_title, originStationName),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subText = if (departure.isRealTime) {
                            sheetContext.getString(R.string.quick_track_pin_here_sub_live, liveOriginMins)
                        } else {
                            sheetContext.getString(R.string.quick_track_pin_here_sub_sched, departure.estimatedTime ?: "")
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Downstream stations selector (if available)
            if (downstreamStops.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sheetContext.getString(R.string.quick_track_debark_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(sortedDownstreamStops) { idx, stop ->
                        val targetMin = liveOriginMins + stop.deltaMinutesFromOrigin
                        val downstreamIdx = downstreamStops.indexOfFirst { it.stationId == stop.stationId }
                        val penultimateStop = if (downstreamIdx > 0) downstreamStops[downstreamIdx - 1] else null
                        val penId = penultimateStop?.stationId ?: originStationId
                        val penName = penultimateStop?.stationName ?: originStationName

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_track_stop_${stop.stationId}")
                                .clickable {
                                    val tracked = QuickTrackedVehicle(
                                        lineId = departure.lineId,
                                        destination = departure.destination,
                                        colorHex = departure.colorHex,
                                        originStationId = originStationId,
                                        originStationName = originStationName,
                                        targetStationId = stop.stationId,
                                        targetStationName = stop.stationName,
                                        targetScheduledTime = stop.scheduledArrivalTime,
                                        initialMinutesRemaining = liveOriginMins,
                                        targetArrivalEpochMs = departure.targetArrivalEpochMs,
                                        isRealTime = departure.isRealTime,
                                        downstreamStops = downstreamStops,
                                        penultimateStationId = penId,
                                        penultimateStationName = penName,
                                        vehicleId = departure.vehicleId,
                                        trainServiceId = departure.trainServiceId,
                                        originWebId = departure.originWebId,
                                        destinationWebId = departure.destinationWebId
                                    )
                                    onTrackSelected(tracked)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkMode) Color(0xFF262832) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val favSet = remember(favoriteStations) { favoriteStations.toSet() }
                                val isFav = favSet.contains(stop.stationId) || favSet.contains(stop.stationName)

                                if (isFav) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = sheetContext.getString(R.string.favorite_badge_desc),
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(lineColor, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stop.stationName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val etaStr = stop.scheduledArrivalTime ?: ""
                                    val subtitleText = if (etaStr.isNotBlank()) {
                                        sheetContext.getString(R.string.quick_track_stop_arrival_format, etaStr, stop.deltaMinutesFromOrigin)
                                    } else {
                                        sheetContext.getString(R.string.quick_track_stop_duration_format, stop.deltaMinutesFromOrigin)
                                    }
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDarkMode) lineColor.copy(alpha = 0.22f).compositeOver(Color(0xFF262832)) else lineColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "$targetMin min",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color.White else lineColor,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

