package com.example.ui.routing.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import android.location.Location
import com.example.ui.routing.PlannerLocation
import com.example.ui.dashboard.AppLanguage
import com.example.util.LineColorResolver
import com.example.util.TripStartEligibility
import com.example.util.RealTimeTripStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailBottomSheet(
    itinerary: PlannedItinerary,
    onDismiss: () -> Unit,
    onViewOnMap: () -> Unit,
    onStartTrip: (() -> Unit)? = null,
    onRecalculateFromStation: ((stationName: String, lat: Double, lon: Double) -> Unit)? = null,
    userLocation: Location? = null,
    originLocation: PlannerLocation? = null,
    currentLegIndex: Int = -1,
    realTimeStatus: RealTimeTripStatus? = null,
    appLanguage: AppLanguage = AppLanguage.CA,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            // Top Header: Summary pill ribbon & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route ribbon summary (scrollable horizontally so all transit legs are visible)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 4.dp)
                ) {
                    itinerary.legs.forEachIndexed { index, leg ->
                        LegBadge(leg = leg)
                        if (index < itinerary.legs.size - 1) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("route_detail_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Duration & Times Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = itinerary.formattedDuration,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${itinerary.formattedDepartureTime} — ${itinerary.formattedArrivalTime}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (itinerary.totalWalkDistanceMeters > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${itinerary.totalWalkDistanceMeters.toInt()} m",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Global Viability / Live Warning Banner
            val liveNotice = if (appLanguage == AppLanguage.ES) {
                realTimeStatus?.transferWarningEs ?: realTimeStatus?.upcomingTransferInfoEs
            } else {
                realTimeStatus?.transferWarningCa ?: realTimeStatus?.upcomingTransferInfoCa
            }
            if (itinerary.viabilityNotice != null || itinerary.activeAlerts.isNotEmpty() || liveNotice != null || (itinerary.recommendedStartTime.isNotEmpty() && itinerary.recommendedStartTime != itinerary.formattedDepartureTime)) {
                val isLiveGps = itinerary.viability == ItineraryViability.VIABLE_ON_TIME || itinerary.legs.any { it.isRealTimeVerified } || realTimeStatus?.isLive == true
                val (bannerBg, bannerIconColor, bannerIcon) = when {
                    itinerary.viability == ItineraryViability.SERVICE_ALERT || realTimeStatus?.isTransferAtRisk == true -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Warning)
                    itinerary.viability == ItineraryViability.ADJUSTED_NEXT_DEPARTURE -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Schedule)
                    isLiveGps -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.RssFeed)
                    else -> Triple(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Schedule)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bannerBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = null,
                            tint = bannerIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val rawNotice = liveNotice
                                ?: itinerary.viabilityNotice
                                ?: if (itinerary.activeAlerts.isNotEmpty()) itinerary.activeAlerts.first()
                                else if (itinerary.recommendedStartTime.isNotEmpty() && itinerary.recommendedStartTime != itinerary.formattedDepartureTime) {
                                    if (appLanguage == AppLanguage.ES) "Salida ajustada a las ${itinerary.recommendedStartTime}" else "Eixida ajustada a les ${itinerary.recommendedStartTime}"
                                } else null

                            val cleanNoticeText = rawNotice?.replace("GPS en directo: ", "")?.replace("GPS en directe: ", "")

                            if (cleanNoticeText != null) {
                                Text(
                                    text = cleanNoticeText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = bannerIconColor
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(8.dp))

            // Step by Step Multimodal Timeline
            val displayLegs = remember(itinerary) {
                if (itinerary.legs.size > 1) {
                    itinerary.legs.filter { leg -> leg.mode != TransitMode.WALK || leg.distanceMeters >= 5.0 }
                } else itinerary.legs
            }

            val timelineItems = remember(itinerary, appLanguage, currentLegIndex, realTimeStatus) {
                buildTimelineItems(itinerary, displayLegs, appLanguage, currentLegIndex, realTimeStatus)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                itemsIndexed(timelineItems) { _, item ->
                    when (item) {
                        is TimelineItem.Origin -> OriginTimelineRow(item)
                        is TimelineItem.Boarding -> BoardingTimelineRow(item)
                        is TimelineItem.TransitRide -> TransitRideTimelineRow(item, appLanguage, itinerary.activeAlerts)
                        is TimelineItem.Transfer -> TransferTimelineRow(item, appLanguage, onRecalculateFromStation)
                        is TimelineItem.Alighting -> AlightingTimelineRow(item)
                        is TimelineItem.Walk -> WalkTimelineRow(item, appLanguage)
                        is TimelineItem.Destination -> DestinationTimelineRow(item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Actions
            val canStart = onStartTrip != null && TripStartEligibility.canStartTrip(itinerary, userLocation, originLocation)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onViewOnMap,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("route_view_on_map_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (appLanguage == AppLanguage.ES) "Mapa" else "Mapa")
                }

                if (canStart) {
                    Button(
                        onClick = onStartTrip!!,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("route_start_trip_primary_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A86B),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Iniciar viaje" else "Iniciar viatge",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (appLanguage == AppLanguage.ES) "Aceptar" else "D'acord")
                    }
                }
            }
        }
    }
}

private sealed interface TimelineItem {
    val isPast: Boolean get() = false

    data class Origin(
        val title: String,
        val time: String,
        val nextColor: Color?,
        val nextDotted: Boolean,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Boarding(
        val stationName: String,
        val time: String,
        val scheduledTime: String?,
        val delayMins: Int?,
        val lineColor: Color,
        val prevColor: Color?,
        val prevDotted: Boolean,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class TransitRide(
        val leg: PlannedLeg,
        val lineColor: Color,
        override val isPast: Boolean = false,
        val isLive: Boolean = false
    ) : TimelineItem

    data class Transfer(
        val stationName: String,
        val arrivalTime: String,
        val departureTime: String,
        val durationStr: String,
        val incomingColor: Color,
        val outgoingColor: Color,
        val isRisk: Boolean = false,
        val outgoingMode: TransitMode = TransitMode.SUBWAY,
        val outgoingLine: String? = null,
        val destination: String? = null,
        val nextScheduledDepartureTime: String? = null,
        val transferLat: Double = 0.0,
        val transferLon: Double = 0.0,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Alighting(
        val stationName: String,
        val time: String,
        val scheduledTime: String?,
        val delayMins: Int?,
        val lineColor: Color,
        val nextColor: Color?,
        val nextDotted: Boolean,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Walk(
        val leg: PlannedLeg,
        val isTransfer: Boolean,
        val prevColor: Color?,
        val nextColor: Color?,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Destination(
        val title: String,
        val time: String,
        val prevColor: Color?,
        val prevDotted: Boolean,
        override val isPast: Boolean = false
    ) : TimelineItem
}

private fun buildTimelineItems(
    itinerary: PlannedItinerary,
    displayLegs: List<PlannedLeg>,
    appLanguage: AppLanguage,
    currentLegIndex: Int = -1,
    realTimeStatus: RealTimeTripStatus? = null
): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()
    if (displayLegs.isEmpty()) return items

    val dotColor = Color(0xFFB0BEC5)

    fun getLegColor(leg: PlannedLeg?): Color? {
        if (leg == null || leg.mode == TransitMode.WALK) return null
        return LineColorResolver.resolveRouteColor(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)
    }

    val firstLeg = displayLegs.first()
    val firstColor = getLegColor(firstLeg)
    val firstDotted = firstLeg.mode == TransitMode.WALK
    val originLabel = if (firstLeg.mode == TransitMode.WALK) {
        if (appLanguage == AppLanguage.ES) "Tu ubicación" else "La teua ubicació"
    } else {
        firstLeg.fromName.ifBlank { if (appLanguage == AppLanguage.ES) "Origen" else "Origen" }
    }

    items.add(
        TimelineItem.Origin(
            title = originLabel,
            time = itinerary.formattedDepartureTime,
            nextColor = firstColor ?: dotColor,
            nextDotted = firstDotted,
            isPast = currentLegIndex > 0
        )
    )

    var skipNextBoardingStationName: String? = null

    for (i in displayLegs.indices) {
        val leg = displayLegs[i]
        val prevLeg = displayLegs.getOrNull(i - 1)
        val nextLeg = displayLegs.getOrNull(i + 1)
        val isPastLeg = currentLegIndex != -1 && i < currentLegIndex

        if (leg.mode == TransitMode.WALK) {
            val isTransferWalk = prevLeg != null && nextLeg != null && prevLeg.mode != TransitMode.WALK && nextLeg.mode != TransitMode.WALK
            if (isTransferWalk && isSameStationName(prevLeg!!.toName, nextLeg!!.fromName)) {
                // Same station transfer handled in previous transit leg as Transfer item!
                continue
            }
            items.add(
                TimelineItem.Walk(
                    leg = leg,
                    isTransfer = isTransferWalk,
                    prevColor = dotColor,
                    nextColor = dotColor,
                    isPast = isPastLeg
                )
            )
        } else {
            val currentColor = getLegColor(leg)!!
            val prevDotted = prevLeg == null || prevLeg.mode == TransitMode.WALK

            // Check if boarding was handled by previous transfer node
            val isBoardingSkipped = skipNextBoardingStationName != null && isSameStationName(leg.fromName, skipNextBoardingStationName)
            if (isBoardingSkipped) {
                skipNextBoardingStationName = null
            } else {
                items.add(
                    TimelineItem.Boarding(
                        stationName = leg.fromName,
                        time = leg.formattedStartTime,
                        scheduledTime = leg.scheduledStartTime,
                        delayMins = leg.realTimeDelayMinutes,
                        lineColor = currentColor,
                        prevColor = dotColor,
                        prevDotted = prevDotted,
                        isPast = isPastLeg
                    )
                )
            }

            // Determine if this transit leg has live GPS telemetry
            val isCurrentTransit = currentLegIndex == i || (currentLegIndex == 0 && i == 1) || (currentLegIndex != -1 && !isPastLeg && i <= currentLegIndex + 1)
            val isTransferTransit = currentLegIndex != -1 && i > currentLegIndex && (nextLeg != null || prevLeg != null)
            val isLegLive = leg.isRealTimeVerified ||
                    (isCurrentTransit && realTimeStatus?.isLive == true) ||
                    (isTransferTransit && realTimeStatus?.isUpcomingTransferLive == true)

            // Transit Ride Card
            items.add(TimelineItem.TransitRide(leg, currentColor, isPast = isPastLeg, isLive = isLegLive))

            // Check if next transit leg transfers at same station
            val nextTransitLeg = if (nextLeg?.mode != TransitMode.WALK) nextLeg else displayLegs.getOrNull(i + 2)
            val isSameStationTransfer = nextTransitLeg != null && nextTransitLeg.mode != TransitMode.WALK && isSameStationName(leg.toName, nextTransitLeg.fromName)

            if (isSameStationTransfer && nextTransitLeg != null) {
                val outgoingColor = getLegColor(nextTransitLeg)!!
                val durationStr = if (nextLeg?.mode == TransitMode.WALK) nextLeg.formattedDuration else "1 min"
                val walkMins = if (nextLeg?.mode == TransitMode.WALK) (nextLeg.durationSeconds / 60).toInt().coerceAtLeast(1) else 1
                val arrMins = timeToMinutes(leg.formattedEndTime)
                val depMins = timeToMinutes(nextTransitLeg.formattedStartTime)
                val isRisk = (depMins - arrMins < walkMins + 1) || itinerary.viability == ItineraryViability.ADJUSTED_NEXT_DEPARTURE

                val intervalMins = if (nextTransitLeg.mode == TransitMode.SUBWAY && (nextTransitLeg.routeShortName?.contains("9") == true || nextTransitLeg.headsign?.contains("Riba", ignoreCase = true) == true)) 30 else 15
                val nextScheduledTime = shiftFormattedTime(nextTransitLeg.formattedStartTime, intervalMins)

                items.add(
                    TimelineItem.Transfer(
                        stationName = leg.toName,
                        arrivalTime = leg.formattedEndTime,
                        departureTime = nextTransitLeg.formattedStartTime,
                        durationStr = durationStr,
                        incomingColor = currentColor,
                        outgoingColor = outgoingColor,
                        isRisk = isRisk,
                        outgoingMode = nextTransitLeg.mode,
                        outgoingLine = nextTransitLeg.routeShortName,
                        destination = nextTransitLeg.headsign ?: nextTransitLeg.routeLongName ?: nextTransitLeg.toName,
                        nextScheduledDepartureTime = nextScheduledTime,
                        transferLat = nextTransitLeg.fromLat,
                        transferLon = nextTransitLeg.fromLon,
                        isPast = isPastLeg
                    )
                )
                skipNextBoardingStationName = nextTransitLeg.fromName
            } else {
                val nextColor = getLegColor(nextLeg)
                val nextDotted = nextLeg == null || nextLeg.mode == TransitMode.WALK
                items.add(
                    TimelineItem.Alighting(
                        stationName = leg.toName,
                        time = leg.formattedEndTime,
                        scheduledTime = leg.scheduledEndTime,
                        delayMins = leg.realTimeDelayMinutes,
                        lineColor = currentColor,
                        nextColor = nextColor ?: dotColor,
                        nextDotted = nextDotted,
                        isPast = isPastLeg
                    )
                )
            }
        }
    }

    val lastLeg = displayLegs.lastOrNull()
    val lastColor = getLegColor(lastLeg) ?: dotColor
    val lastDotted = lastLeg?.mode == TransitMode.WALK
    val rawDestLabel = lastLeg?.toName ?: ""
    val cleanDestLabel = if (rawDestLabel.isNotBlank() && rawDestLabel != "END" && rawDestLabel != "End") rawDestLabel else if (appLanguage == AppLanguage.ES) "Destino" else "Destinació"

    // If the last item added was an Alighting node at the exact same station name as the destination,
    // upgrade that Alighting item to be the Destination pin instead of duplicating the row!
    val lastItem = items.lastOrNull()
    if (lastItem is TimelineItem.Alighting && isSameStationName(lastItem.stationName, cleanDestLabel)) {
        items.removeAt(items.size - 1)
        items.add(
            TimelineItem.Destination(
                title = lastItem.stationName,
                time = lastItem.time,
                prevColor = lastItem.lineColor,
                prevDotted = false,
                isPast = lastItem.isPast
            )
        )
    } else {
        items.add(
            TimelineItem.Destination(
                title = cleanDestLabel,
                time = itinerary.formattedArrivalTime,
                prevColor = lastColor,
                prevDotted = lastDotted,
                isPast = false
            )
        )
    }

    return items
}

private fun isSameStationName(name1: String, name2: String): Boolean {
    if (name1.isBlank() || name2.isBlank()) return false
    val n1 = name1.lowercase().replace("estación", "").replace("estació", "").replace("parada", "").replace("metro", "").trim()
    val n2 = name2.lowercase().replace("estación", "").replace("estació", "").replace("parada", "").replace("metro", "").trim()
    if (n1 == n2) return true
    if (n1.length > 3 && n2.length > 3 && (n1.contains(n2) || n2.contains(n1))) return true
    return false
}

/**
 * Reusable Canvas to draw perfectly centered graphic lines and circles for the timeline
 */
@Composable
private fun TimelineNodeCanvas(
    modifier: Modifier = Modifier,
    topLineColor: Color? = null,
    bottomLineColor: Color? = null,
    topDotted: Boolean = false,
    bottomDotted: Boolean = false,
    nodeColor: Color? = null,
    innerColor: Color? = null,
    isPinIcon: Boolean = false,
    pinColor: Color = Color(0xFFE53935)
) {
    Box(
        modifier = modifier.width(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokeWidthPx = 5.dp.toPx()
            val outerRadiusPx = 8.5f.dp.toPx()
            val innerRadiusPx = 4f.dp.toPx()

            // Top Line
            if (topLineColor != null) {
                if (topDotted) {
                    val dotRadius = 2.5f.dp.toPx()
                    val step = 10f.dp.toPx()
                    var y = 2f.dp.toPx()
                    while (y <= cy - dotRadius) {
                        drawCircle(color = topLineColor, radius = dotRadius, center = Offset(cx, y))
                        y += step
                    }
                } else {
                    drawLine(
                        color = topLineColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, cy),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Square
                    )
                }
            }

            // Bottom Line
            if (bottomLineColor != null) {
                if (bottomDotted) {
                    val dotRadius = 2.5f.dp.toPx()
                    val step = 10f.dp.toPx()
                    var y = cy + dotRadius + 2f.dp.toPx()
                    while (y <= size.height - dotRadius) {
                        drawCircle(color = bottomLineColor, radius = dotRadius, center = Offset(cx, y))
                        y += step
                    }
                } else {
                    drawLine(
                        color = bottomLineColor,
                        start = Offset(cx, cy),
                        end = Offset(cx, size.height),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Square
                    )
                }
            }

            // Central Node Circle
            if (nodeColor != null) {
                drawCircle(color = nodeColor, radius = outerRadiusPx, center = Offset(cx, cy))
                if (innerColor != null) {
                    drawCircle(color = innerColor, radius = innerRadiusPx, center = Offset(cx, cy))
                }
            }
        }

        if (isPinIcon) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = pinColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun OriginTimelineRow(item: TimelineItem.Origin) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            bottomLineColor = item.nextColor,
            bottomDotted = item.nextDotted,
            nodeColor = Color(0xFF1976D2),
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.time,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BoardingTimelineRow(item: TimelineItem.Boarding) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.prevColor,
            topDotted = item.prevDotted,
            bottomLineColor = item.lineColor,
            nodeColor = item.lineColor,
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.stationName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time) {
                    Text(
                        text = item.scheduledTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (item.delayMins != null && item.delayMins != 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TransitRideTimelineRow(
    item: TimelineItem.TransitRide,
    appLanguage: AppLanguage,
    globalAlerts: List<String>
) {
    var expandedStops by remember { mutableStateOf(false) }
    val leg = item.leg
    val lineColor = item.lineColor
    val rowAlpha = if (item.isPast) 0.35f else 1.0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha)
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = lineColor,
            bottomLineColor = lineColor
        )
        Spacer(modifier = Modifier.width(10.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OperatorLogoOrIcon(leg = leg, lineColor = lineColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    val headsign = leg.headsign ?: leg.routeLongName
                    if (!headsign.isNullOrBlank()) {
                        Text(
                            text = "➔ $headsign",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isLive || leg.isRealTimeVerified) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF2E7D32), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "GPS en directo" else "GPS en directe",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    } else {
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Programado" else "Programat",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                val relevantAlert = when {
                    !leg.alertMessage.isNullOrBlank() -> leg.alertMessage
                    leg.hasActiveAlert -> "Aviso de servicio activo en esta línea"
                    else -> {
                        val line = leg.routeShortName ?: ""
                        val agency = leg.agencyName ?: ""
                        globalAlerts.firstOrNull { alert ->
                            (line.isNotEmpty() && alert.contains(line, ignoreCase = true)) ||
                            (agency.isNotEmpty() && alert.contains(agency, ignoreCase = true))
                        }
                    }
                }

                if (relevantAlert != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.ES) "Aviso de servicio" else "Avís de servei",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                )
                                Text(
                                    text = relevantAlert,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF5D4037),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                if (leg.intermediateStops.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { expandedStops = !expandedStops }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${leg.intermediateStops.size + 1} ${if (appLanguage == AppLanguage.ES) "paradas" else "parades"} (${leg.formattedDuration})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expandedStops) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = expandedStops,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 4.dp, top = 6.dp)
                                .fillMaxWidth()
                        ) {
                            leg.intermediateStops.forEach { stop ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(MaterialTheme.colorScheme.outline, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stop.name,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    if (stop.formattedTime != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!stop.scheduledTime.isNullOrBlank() && stop.scheduledTime != stop.formattedTime) {
                                                Text(
                                                    text = stop.scheduledTime,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textDecoration = TextDecoration.LineThrough
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = stop.formattedTime,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (!stop.scheduledTime.isNullOrBlank() && stop.scheduledTime != stop.formattedTime) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = if (!stop.scheduledTime.isNullOrBlank() && stop.scheduledTime != stop.formattedTime) FontWeight.Bold else FontWeight.Normal
                                                )
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
}

@Composable
private fun TransferTimelineRow(
    item: TimelineItem.Transfer,
    appLanguage: AppLanguage,
    onRecalculateFromStation: ((stationName: String, lat: Double, lon: Double) -> Unit)? = null
) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.Top
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.incomingColor,
            bottomLineColor = item.outgoingColor,
            nodeColor = if (item.isRisk) Color(0xFFE65100) else item.outgoingColor,
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.stationName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${item.arrivalTime} ➔ ${item.departureTime}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (item.isRisk) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (item.isRisk) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (appLanguage == AppLanguage.ES) "Transbordo en ${item.stationName} (${item.durationStr})" else "Transbord a ${item.stationName} (${item.durationStr})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (item.isRisk) Color(0xFFE65100) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (item.isRisk) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF3E0),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "Transbordo en riesgo" else "Transbord en risc",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            )
                        }

                        if (!item.nextScheduledDepartureTime.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val lineLabel = item.outgoingLine ?: ""
                            val destLabel = item.destination ?: ""
                            val lineDisp = if (lineLabel.startsWith("L") || lineLabel.isBlank()) lineLabel else "L$lineLabel"
                            val nextText = if (appLanguage == AppLanguage.ES) {
                                "🔴 Siguiente $lineDisp a $destLabel: ${item.nextScheduledDepartureTime} (Horario programado)"
                            } else {
                                "🔴 Següent $lineDisp a $destLabel: ${item.nextScheduledDepartureTime} (Horari programat)"
                            }

                            Text(
                                text = nextText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF37474F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        if (onRecalculateFromStation != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    onRecalculateFromStation(item.stationName, item.transferLat, item.transferLon)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SyncAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (appLanguage == AppLanguage.ES) "Buscar alternativas" else "Buscar alternatives",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlightingTimelineRow(item: TimelineItem.Alighting) {
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.lineColor,
            bottomLineColor = item.nextColor,
            bottomDotted = item.nextDotted,
            nodeColor = item.lineColor,
            innerColor = Color.White
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.stationName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.scheduledTime.isNullOrBlank() && item.scheduledTime != item.time) {
                    Text(
                        text = item.scheduledTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (item.delayMins != null && item.delayMins != 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WalkTimelineRow(
    item: TimelineItem.Walk,
    appLanguage: AppLanguage
) {
    val leg = item.leg
    val rowAlpha = if (item.isPast) 0.35f else 1.0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(rowAlpha)
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.prevColor,
            bottomLineColor = item.nextColor,
            topDotted = true,
            bottomDotted = true
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isTransfer) Icons.Default.SyncAlt else Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            val walkLabel = if (item.isTransfer) {
                if (appLanguage == AppLanguage.ES) "Transbordo (${leg.formattedDuration})" else "Transbord (${leg.formattedDuration})"
            } else {
                "${if (appLanguage == AppLanguage.ES) "Camina" else "Camina"} ${leg.formattedDuration} (${leg.distanceMeters.toInt()} m)"
            }
            Text(
                text = walkLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun DestinationTimelineRow(item: TimelineItem.Destination) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineNodeCanvas(
            modifier = Modifier.fillMaxHeight(),
            topLineColor = item.prevColor,
            topDotted = item.prevDotted,
            isPinIcon = true
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.time,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Official Operator Logo & Line Capsule Badge
 */
@Composable
fun OperatorLogoOrIcon(
    leg: PlannedLeg,
    lineColor: Color
) {
    val agency = leg.agencyName ?: ""
    val isEmt = agency.contains("EMT", ignoreCase = true) ||
            (agency.isBlank() && !leg.routeShortName.orEmpty().let {
                it.length >= 3 && (it.startsWith("1") || it.startsWith("2") || it.startsWith("3"))
            })

    val operatorLogoRes = when (leg.mode) {
        TransitMode.SUBWAY, TransitMode.TRAM -> R.drawable.logo_metrovalencia
        TransitMode.RAIL -> R.drawable.logo_cercanias
        TransitMode.BUS -> if (isEmt) R.drawable.logo_emt_valencia else R.drawable.logo_metrobus
        TransitMode.BICYCLE -> R.drawable.ic_bike
        TransitMode.WALK -> null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (operatorLogoRes != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Image(
                    painter = painterResource(id = operatorLogoRes),
                    contentDescription = leg.mode.displayNameEs,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(3.dp)
                )
            }
        }

        // Line pill (e.g. 3, 32, C2)
        val lineLabel = when (leg.mode) {
            TransitMode.WALK -> "A pie"
            TransitMode.BUS -> leg.routeShortName ?: "Bus"
            TransitMode.SUBWAY, TransitMode.TRAM -> {
                val short = leg.routeShortName ?: ""
                if (short.startsWith("L") || short.startsWith("T") || short.isEmpty()) short.ifEmpty { "Metro" } else "L$short"
            }
            TransitMode.RAIL -> leg.routeShortName ?: "Cercanías"
            TransitMode.BICYCLE -> "Bici"
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = lineColor,
            contentColor = Color.White
        ) {
            Text(
                text = lineLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private fun shiftFormattedTime(timeStr: String, minutesToAdd: Int): String {
    if (timeStr.isBlank() || !timeStr.contains(":")) return timeStr
    return try {
        val parts = timeStr.trim().split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val totalMinutes = (h * 60 + m + minutesToAdd + 1440) % 1440
        String.format(java.util.Locale.US, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
    } catch (e: Exception) {
        timeStr
    }
}

private fun timeToMinutes(timeStr: String): Int {
    if (timeStr.isBlank() || !timeStr.contains(":")) return 0
    return try {
        val parts = timeStr.trim().split(":")
        parts[0].toInt() * 60 + parts[1].toInt()
    } catch (e: Exception) {
        0
    }
}
