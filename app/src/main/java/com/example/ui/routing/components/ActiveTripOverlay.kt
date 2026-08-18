package com.example.ui.routing.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.ui.dashboard.AppLanguage
import com.example.util.ActiveTripProgressTracker
import com.example.util.LineColorResolver
import com.example.util.RealTimeTripStatus
import com.example.util.TripUIStateFormatter
import com.example.util.TripUrgencyLevel

/**
 * Floating bottom overlay card inspired by Transit App (Transit GO mode).
 * Streamlined microcopy for movement: eliminates long sentences, shows discrete glanceable tokens,
 * applies color semantics for urgency without anxiety-inducing pedestrian speedometers.
 */
@Composable
fun ActiveTripOverlay(
    activeTrip: ActiveTripState,
    onExpandDetails: () -> Unit,
    onCancelTrip: () -> Unit,
    onAdvanceLeg: (newIndex: Int) -> Unit = {},
    onRecalculateTransfer: (() -> Unit)? = null,
    isRecalculating: Boolean = false,
    recalculateError: String? = null,
    onDismissRecalculateError: (() -> Unit)? = null,
    realTimeStatus: RealTimeTripStatus? = null,
    appLanguage: AppLanguage = AppLanguage.CA,
    modifier: Modifier = Modifier
) {
    val itinerary = activeTrip.itinerary
    val legs = itinerary.legs
    val currentLegIndex = activeTrip.currentLegIndex.coerceIn(0, (legs.size - 1).coerceAtLeast(0))
    val currentLeg = legs.getOrNull(currentLegIndex)

    val progressInfo by ActiveTripProgressTracker.progressState.collectAsState()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val isOffRoute = realTimeStatus?.isOffRoute == true
    val isLive = realTimeStatus?.isLive == true

    // Dynamic prompt and glanceable token calculation
    val promptData = remember(
        currentLegIndex, currentLeg, itinerary, appLanguage, realTimeStatus,
        progressInfo.isBoarded, progressInfo.distanceToTargetMeters, progressInfo.dynamicWalkMinutesRemaining, legs
    ) {
        TripUIStateFormatter.format(
            currentLeg = currentLeg,
            currentLegIndex = currentLegIndex,
            totalLegs = legs.size,
            realTimeStatus = realTimeStatus,
            isBoarded = progressInfo.isBoarded,
            scheduledArrivalTime = itinerary.formattedArrivalTime,
            appLanguage = appLanguage,
            distanceToTargetMeters = progressInfo.distanceToTargetMeters,
            allLegs = legs
        )
    }

    // Color Semantics based on Urgency Level (Green / Orange / Red)
    val containerColor = when (promptData.urgencyLevel) {
        TripUrgencyLevel.CRITICAL -> if (isDark) Color(0xFF281114) else Color(0xFFFFF1F2)
        TripUrgencyLevel.BRISK -> if (isDark) Color(0xFF281C10) else Color(0xFFFFF8F0)
        TripUrgencyLevel.RELAXED -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when (promptData.urgencyLevel) {
        TripUrgencyLevel.CRITICAL -> Color(0xFFE53935).copy(alpha = if (isDark) 0.8f else 0.5f)
        TripUrgencyLevel.BRISK -> Color(0xFFFF9800).copy(alpha = if (isDark) 0.8f else 0.5f)
        TripUrgencyLevel.RELAXED -> MaterialTheme.colorScheme.outlineVariant
    }

    val primaryTextColor = when (promptData.urgencyLevel) {
        TripUrgencyLevel.CRITICAL -> if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
        TripUrgencyLevel.BRISK -> if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)
        TripUrgencyLevel.RELAXED -> MaterialTheme.colorScheme.onSurface
    }

    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onExpandDetails() }
            .testTag("active_trip_overlay"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(width = 1.dp, color = borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Compact Route Deviation Pill
            if (isOffRoute) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF5252).copy(alpha = if (isDark) 0.2f else 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Ruta desviada (+150m)" else "Ruta desviada (+150m)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Missed Transfer Risk Prompt Card
            val isTransferAtRisk = realTimeStatus?.isTransferAtRisk == true
            
            if (isTransferAtRisk) {
                var isDismissed by remember(realTimeStatus?.upcomingTransferLine) { mutableStateOf(false) }

                if (!isDismissed) {
                    val cardColor = Color(0xFFFF9800)
                    val cardContentColor = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                        border = BorderStroke(1.dp, cardColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isTransferAtRisk) Icons.Default.Warning else Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = cardContentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTransferAtRisk) {
                                        if (appLanguage == AppLanguage.ES) "Posible transbordo perdido" else "Possible transbordament perdut"
                                    } else {
                                        if (appLanguage == AppLanguage.ES) "Transbordo ajustado" else "Transbordament ajustat"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = cardContentColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ES) {
                                    realTimeStatus?.transferWarningEs ?: "El transporte actual lleva retraso y la conexión es inviable."
                                } else {
                                    realTimeStatus?.transferWarningCa ?: "El transport actual porta retard i la connexió és inviable."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { isDismissed = true },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = if (appLanguage == AppLanguage.ES) "No, mantener" else "No, mantindre",
                                        style = MaterialTheme.typography.labelMedium.copy(color = secondaryTextColor)
                                    )
                                }
                                Button(
                                    onClick = { onRecalculateTransfer?.invoke() },
                                    enabled = !isRecalculating,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                                ) {
                                    if (isRecalculating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (appLanguage == AppLanguage.ES) "Recalculando..." else "Recalculant...",
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    } else {
                                        Text(
                                            text = if (appLanguage == AppLanguage.ES) "Sí, recalcular" else "Sí, recalcular",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recalculation Error Banner
            if (recalculateError != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFD32F2F).copy(alpha = if (isDark) 0.25f else 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = recalculateError,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDismissRecalculateError?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Next Transit Departure Info Banner (without repeating station name)
            if (!promptData.nextTransitDepartureInfo.isNullOrBlank() && !isTransferAtRisk && !isOffRoute) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = promptData.nextTransitIcon ?: Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = promptData.nextTransitDepartureInfo,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // Top Row: Glanceable Microcopy Tokens (Line, Next Time, ETA, Action Icon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val iconBg = when (promptData.urgencyLevel) {
                        TripUrgencyLevel.CRITICAL -> Color(0xFFFF5252).copy(alpha = if (isDark) 0.22f else 0.12f)
                        TripUrgencyLevel.BRISK -> Color(0xFFFF9800).copy(alpha = if (isDark) 0.22f else 0.12f)
                        TripUrgencyLevel.RELAXED -> Color(0xFF00A86B).copy(alpha = if (isDark) 0.2f else 0.12f)
                    }
                    val iconTint = when (promptData.urgencyLevel) {
                        TripUrgencyLevel.CRITICAL -> Color(0xFFE53935)
                        TripUrgencyLevel.BRISK -> Color(0xFFF57C00)
                        TripUrgencyLevel.RELAXED -> Color(0xFF00A86B)
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(iconBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = promptData.icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = promptData.headline,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryTextColor,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (promptData.walkBadgeMinutes != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = secondaryTextColor
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${promptData.walkBadgeMinutes}m",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = secondaryTextColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                        if (promptData.subheadline.isNotEmpty()) {
                            Text(
                                text = promptData.subheadline,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = secondaryTextColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Compact Close/Cancel Button
                IconButton(
                    onClick = onCancelTrip,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("active_trip_cancel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar viaje",
                        tint = if (isDark) Color(0xFF78909C) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Interactive Onboard Confirmation Chip ("¿A bordo?")
            val (candidateTransitLeg, candidateLegIndex) = remember(
                currentLegIndex, legs, progressInfo.distanceToTargetMeters, progressInfo.dynamicWalkMinutesRemaining
            ) {
                when {
                    // Current leg is already transit (subway, bus, tram, rail)
                    currentLeg?.mode in listOf(TransitMode.SUBWAY, TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL) -> {
                        Pair(currentLeg, currentLegIndex)
                    }
                    // Current leg is a WALK immediately preceding a transit leg, but ONLY when already at or right next to the station (<= 60m or <= 1 min walk)
                    currentLeg?.mode == TransitMode.WALK && currentLegIndex + 1 < legs.size &&
                    legs[currentLegIndex + 1].mode in listOf(TransitMode.SUBWAY, TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL) -> {
                        val distToStation = progressInfo.distanceToTargetMeters
                        val walkMins = progressInfo.dynamicWalkMinutesRemaining
                        val isAtStation = (distToStation != null && distToStation <= 60.0) || (walkMins != null && walkMins <= 1)
                        if (isAtStation) Pair(legs[currentLegIndex + 1], currentLegIndex + 1) else Pair(null, -1)
                    }
                    else -> Pair(null, -1)
                }
            }

            val isCandidateBoarded = if (candidateLegIndex >= 0) {
                (progressInfo.isBoarded && progressInfo.trackedLegIndex == candidateLegIndex) ||
                com.example.util.TripStepProgressionEngine.isLegBoarded(candidateLegIndex)
            } else {
                true
            }

            val shouldShowConfirmation = remember(
                candidateTransitLeg, candidateLegIndex, isCandidateBoarded, realTimeStatus, currentLegIndex
            ) {
                if (candidateTransitLeg == null || isCandidateBoarded) {
                    false
                } else {
                    val scheduledDepMs = com.example.util.TripTimeParser.parseTimeToMillis(candidateTransitLeg.startTime)
                    val nowMs = System.currentTimeMillis()
                    val minsSinceScheduled = if (scheduledDepMs != null) ((nowMs - scheduledDepMs) / 60000L).toInt() else null
                    
                    val realTimeMins = realTimeStatus?.vehicleArrivalMinutes
                    val realTimeSecs = realTimeStatus?.vehicleSecondsRemaining

                    // Show confirmation chip when vehicle is arriving (within 3 min) AND hold it up to 5 min AFTER departure
                    when {
                        realTimeMins != null -> realTimeMins <= 3
                        realTimeSecs != null -> realTimeSecs <= 180
                        minsSinceScheduled != null -> minsSinceScheduled in -3..5
                        else -> true // Fallback to always show confirmation chip when at station / transit leg before boarding
                    }
                }
            }

            if (shouldShowConfirmation && candidateTransitLeg != null) {
                val modeLabel = when (candidateTransitLeg.mode) {
                    TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                    TransitMode.BUS -> "Bus"
                    TransitMode.RAIL -> "Tren"
                    else -> "transporte"
                }
                val targetLegIndex = candidateLegIndex

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF00A86B).copy(alpha = if (isDark) 0.16f else 0.08f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF00A86B).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAdvanceLeg(targetLegIndex) }
                        .testTag("active_trip_board_confirm_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = when (candidateTransitLeg.mode) {
                                    TransitMode.BUS -> Icons.Default.DirectionsBus
                                    TransitMode.RAIL -> Icons.Default.DirectionsRailway
                                    else -> Icons.Default.Subway
                                },
                                contentDescription = null,
                                tint = Color(0xFF00A86B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "¿A bordo del $modeLabel?" else "A bord del $modeLabel?",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00A86B),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "Confirmar ➔",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle: Multi-modal Segmented Stepper Bar (Purely visual, non-clickable)
            SegmentedTransitProgress(
                legs = legs,
                currentLegIndex = currentLegIndex,
                currentLegProgressFraction = progressInfo.progressWithinLeg,
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: ETA and Remaining Distance / Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Arrival Time & Destination
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val destIcon = if (activeTrip.destinationName.contains("Casa", ignoreCase = true) ||
                        activeTrip.destinationName.contains("Home", ignoreCase = true)) Icons.Default.Home else Icons.Default.LocationOn

                    Icon(
                        imageVector = destIcon,
                        contentDescription = null,
                        tint = when (promptData.urgencyLevel) {
                            TripUrgencyLevel.CRITICAL -> Color(0xFFE53935)
                            TripUrgencyLevel.BRISK -> Color(0xFFFF9800)
                            TripUrgencyLevel.RELAXED -> Color(0xFF00A86B)
                        },
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = promptData.formattedArrivalTimeText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: Compact Live Indicator + Total Duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isLive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00A86B).copy(alpha = if (isDark) 0.22f else 0.12f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RssFeed,
                                contentDescription = "En vivo",
                                tint = Color(0xFF00A86B),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "En vivo" else "En directe",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF00A86B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = itinerary.formattedDuration,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = when (promptData.urgencyLevel) {
                                TripUrgencyLevel.CRITICAL -> (if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828))
                                TripUrgencyLevel.BRISK -> (if (isDark) Color(0xFFFFD180) else Color(0xFFE65100))
                                TripUrgencyLevel.RELAXED -> Color(0xFF00A86B)
                            },
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Segmented Stepper Bar that divides the progress across multi-modal legs (Walk, Bus, Metro, Train).
 * Non-clickable: progress is automatically synchronized with GPS and the active step engine.
 */
@Composable
private fun SegmentedTransitProgress(
    legs: List<PlannedLeg>,
    currentLegIndex: Int,
    currentLegProgressFraction: Float = 0.5f,
    isDark: Boolean
) {
    if (legs.isEmpty()) return

    val inactiveSegmentColor = if (isDark) Color(0xFF38434F) else Color(0xFFCBD5E1)
    val completedColor = Color(0xFF00A86B)

    // Calculate balanced visual weights so that walks (even 1-2 mins) always have ample minimum physical space
    fun getLegWeight(leg: PlannedLeg): Float {
        val durationMin = (leg.durationSeconds / 60f).coerceAtLeast(1f)
        return when (leg.mode) {
            TransitMode.WALK, TransitMode.BICYCLE -> {
                // Guaranteed base weight for walking so labels and dots never get crushed
                1.0f + (durationMin.coerceIn(1f, 15f) - 1f) * 0.08f
            }
            else -> {
                // Moderately scaled transit duration weight
                1.25f + (durationMin.coerceIn(1f, 30f) - 1f) * 0.12f
            }
        }
    }

    val legWeights = remember(legs) { legs.map { getLegWeight(it) } }
    val totalWeight = remember(legWeights) { legWeights.sum().coerceAtLeast(1f) }

    val safeLegIndex = currentLegIndex.coerceIn(0, legs.size - 1)
    val completedWeight = legWeights.take(safeLegIndex).sum()
    val currentLegWeight = legWeights[safeLegIndex]
    val progressFraction = ((completedWeight + currentLegWeight * currentLegProgressFraction.coerceIn(0f, 1f)) / totalWeight).coerceIn(0.02f, 0.98f)

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            val totalWidthPx = maxWidth

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                legs.forEachIndexed { index, leg ->
                    val isCompleted = index < currentLegIndex
                    val isCurrent = index == currentLegIndex
                    val legColor = LineColorResolver.resolveRouteColor(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)
                    val weight = legWeights[index]

                    val segmentColor = when {
                        isCompleted -> completedColor
                        isCurrent -> legColor
                        else -> inactiveSegmentColor
                    }

                    if (leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE) {
                        // Uniformly spaced dots via Canvas so all dots across all walk segments have identical pitch
                        Canvas(
                            modifier = Modifier
                                .weight(weight)
                                .height(8.dp)
                        ) {
                            val dotRadius = 2.dp.toPx()
                            val dotSpacing = 7.dp.toPx() // Exact constant distance between dot centers
                            val availableWidth = size.width
                            val centerY = size.height / 2f

                            if (availableWidth >= dotRadius * 2) {
                                val count = ((availableWidth - dotRadius * 2) / dotSpacing).toInt() + 1
                                val actualCount = count.coerceAtLeast(1)
                                val totalSpan = (actualCount - 1) * dotSpacing
                                val startX = (availableWidth - totalSpan) / 2f

                                for (i in 0 until actualCount) {
                                    val cx = startX + i * dotSpacing
                                    drawCircle(
                                        color = segmentColor,
                                        radius = dotRadius,
                                        center = Offset(cx, centerY)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(7.dp)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(segmentColor)
                        )
                    }
                }
            }

            val dotOffset = totalWidthPx * progressFraction - 7.dp
            Box(
                modifier = Modifier
                    .offset(x = dotOffset, y = (-2).dp)
                    .size(13.dp)
                    .background(Color(0xFF00E676).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFF00A86B), CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            legs.forEachIndexed { index, leg ->
                val isCompleted = index < currentLegIndex
                val isCurrent = index == currentLegIndex
                val legColor = LineColorResolver.resolveRouteColor(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)
                val weight = legWeights[index]

                val icon = when (leg.mode) {
                    TransitMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
                    TransitMode.BUS -> Icons.Default.DirectionsBus
                    TransitMode.SUBWAY, TransitMode.TRAM -> Icons.Default.Subway
                    TransitMode.RAIL -> Icons.Default.DirectionsRailway
                    TransitMode.BICYCLE -> Icons.AutoMirrored.Filled.DirectionsWalk
                }

                val label = when (leg.mode) {
                    TransitMode.WALK, TransitMode.BICYCLE -> {
                        val mins = (leg.durationSeconds / 60).coerceAtLeast(1)
                        "${mins}m"
                    }
                    TransitMode.BUS -> leg.routeShortName ?: "Bus"
                    TransitMode.SUBWAY, TransitMode.TRAM -> leg.routeShortName ?: "Metro"
                    TransitMode.RAIL -> leg.routeShortName ?: "Tren"
                }

                val itemTint = when {
                    isCurrent -> legColor
                    isCompleted -> completedColor
                    isDark -> Color(0xFF78909C)
                    else -> Color(0xFF94A3B8)
                }

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isCurrent) legColor.copy(alpha = if (isDark) 0.25f else 0.15f) else Color.Transparent
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = itemTint,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                color = itemTint,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
