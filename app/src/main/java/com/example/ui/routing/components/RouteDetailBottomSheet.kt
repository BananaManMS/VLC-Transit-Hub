package com.example.ui.routing.components

import android.location.Location
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.SchedulePhase
import com.example.data.model.routing.TransitMode
import com.example.ui.dashboard.AppLanguage
import com.example.ui.routing.PlannerLocation
import com.example.util.RealTimeTripStatus
import com.example.util.TripStartEligibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailBottomSheet(
    itinerary: PlannedItinerary,
    onDismiss: () -> Unit,
    onViewOnMap: () -> Unit,
    onStartTrip: (() -> Unit)? = null,
    onRecalculateTransfer: ((stationName: String, lat: Double, lon: Double, arrivalTime: String) -> Unit)? = null,
    isRecalculatingTransfer: Boolean = false,
    userLocation: Location? = null,
    originLocation: PlannerLocation? = null,
    currentLegIndex: Int = -1,
    realTimeStatus: RealTimeTripStatus? = null,
    isDarkMode: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.CA,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
) {
    val isDark = isDarkMode || isSystemInDarkTheme()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.statusBarsPadding(),
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
                        LegBadge(leg = leg, isDarkMode = isDark)
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
            val isItineraryLive = itinerary.legs.any { it.isRealTimeVerified && it.schedulePhase == SchedulePhase.LIVE_ACQUIRED } ||
                    (realTimeStatus?.isLive == true && realTimeStatus.schedulePhase == SchedulePhase.LIVE_ACQUIRED)
            val isTripDelayed = (realTimeStatus?.delayMinutes ?: 0) > 0 || (itinerary.recommendedStartTime.isNotEmpty() && itinerary.recommendedStartTime != itinerary.formattedDepartureTime)
            val tripTimeColor = when {
                isItineraryLive && isTripDelayed -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                isItineraryLive -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

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
                        color = tripTimeColor
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
                val isNoticeScheduled = liveNotice != null && (liveNotice.contains("Programado") || liveNotice.contains("Programat") || realTimeStatus?.isUpcomingTransferLive == false)
                val isLiveGps = !isNoticeScheduled && (
                    (liveNotice != null && realTimeStatus?.isUpcomingTransferLive == true && realTimeStatus.transferSchedulePhase == SchedulePhase.LIVE_ACQUIRED) ||
                    (liveNotice == null && (itinerary.viability == ItineraryViability.VIABLE_ON_TIME || itinerary.legs.any { it.isRealTimeVerified && it.schedulePhase == SchedulePhase.LIVE_ACQUIRED } || (realTimeStatus?.isLive == true && realTimeStatus.schedulePhase == SchedulePhase.LIVE_ACQUIRED)))
                )
                val (bannerBg, bannerIconColor, bannerIcon) = when {
                    itinerary.viability == ItineraryViability.SERVICE_ALERT || realTimeStatus?.isTransferAtRisk == true -> Triple(
                        if (isDark) Color(0xFF3E1E1E) else Color(0xFFFFEBEE),
                        if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                        Icons.Default.Warning
                    )
                    itinerary.viability == ItineraryViability.ADJUSTED_NEXT_DEPARTURE -> Triple(
                        if (isDark) Color(0xFF3E2C1E) else Color(0xFFFFF3E0),
                        if (isDark) Color(0xFFFFD180) else Color(0xFFE65100),
                        Icons.Default.Schedule
                    )
                    isLiveGps -> Triple(
                        if (isDark) Color(0xFF1B3822) else Color(0xFFE8F5E9),
                        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                        Icons.Default.RssFeed
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Icons.Default.Schedule
                    )
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
                        if (isLiveGps) {
                            com.example.ui.components.LiveRssFeedIcon(
                                contentDescription = null,
                                tint = bannerIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = bannerIcon,
                                contentDescription = null,
                                tint = bannerIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val rawNotice = liveNotice
                                ?: itinerary.viabilityNotice
                                ?: if (itinerary.activeAlerts.isNotEmpty()) itinerary.activeAlerts.first()
                                else if (itinerary.recommendedStartTime.isNotEmpty() && itinerary.recommendedStartTime != itinerary.formattedDepartureTime) {
                                    if (appLanguage == AppLanguage.ES) "Salida ajustada a las ${itinerary.recommendedStartTime}" else "Eixida ajustada a les ${itinerary.recommendedStartTime}"
                                } else null

                            val cleanNoticeText = rawNotice
                                ?.replace("GPS en directo: ", "")
                                ?.replace("GPS en directe: ", "")
                                ?.replace("En vivo: ", "")
                                ?.replace("En viu: ", "")

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
                        is TimelineItem.Transfer -> TransferTimelineRow(
                            item = item,
                            appLanguage = appLanguage,
                            onRecalculateTransfer = onRecalculateTransfer,
                            isRecalculating = isRecalculatingTransfer
                        )
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
                        colors = ButtonDefaults.buttonColors(
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
