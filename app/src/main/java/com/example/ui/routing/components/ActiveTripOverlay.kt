package com.example.ui.routing.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ActiveTripEntity
import com.example.data.model.routing.TransitMode
import com.example.data.model.trip.UnifiedActiveTripSnapshot
import com.example.data.repository.ActiveTripState
import com.example.ui.dashboard.AppLanguage
import com.example.util.ActiveTripProgressTracker
import com.example.util.ActiveTripSnapshotBuilder
import com.example.util.RealTimeTripStatus
import com.example.util.TripUIStateFormatter
import com.example.util.TripUrgencyLevel
import com.example.util.UnifiedActiveTripStateTracker

/**
 * Floating bottom overlay card inspired by Transit App (Transit GO mode).
 * Supports:
 * - Compact Floating Pill (~52dp) when browsing the app.
 * - Auto-expanding interactive prompt card when prompts (boarding confirmation, transfer recalculation, off-route) occur.
 * - Full expanded navigation card when viewing the active route map.
 * - Dynamic height reporting for seamless bottom content padding.
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
    isNavigationMapView: Boolean = false,
    onOpenRouteOnMap: (() -> Unit)? = null,
    onHeightChanged: ((Dp) -> Unit)? = null,
    unifiedSnapshot: UnifiedActiveTripSnapshot? = null,
    modifier: Modifier = Modifier
) {
    val itinerary = activeTrip.itinerary
    val legs = itinerary.legs
    val currentLegIndex = activeTrip.currentLegIndex.coerceIn(0, (legs.size - 1).coerceAtLeast(0))
    val currentLeg = legs.getOrNull(currentLegIndex)

    val progressInfo by ActiveTripProgressTracker.progressState.collectAsState()
    val trackerSnapshot by UnifiedActiveTripStateTracker.snapshot.collectAsState()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f

    // Pure Painter: Consume canonical snapshot precalculated by the service engine
    val currentSnapshot = unifiedSnapshot
        ?: trackerSnapshot
        ?: remember(activeTrip, progressInfo, realTimeStatus, appLanguage) {
            ActiveTripSnapshotBuilder.build(
                activeTrip = activeTrip,
                progressInfo = progressInfo,
                realTimeStatus = realTimeStatus,
                appLanguage = appLanguage
            )
        }

    val isOffRoute = currentSnapshot.isOffRoute
    val isLive = currentSnapshot.isLive
    val promptData = currentSnapshot.formattedUiState
    val candidateTransitLeg = currentSnapshot.candidateTransitLeg
    val candidateLegIndex = currentSnapshot.candidateLegIndex
    val shouldShowConfirmation = currentSnapshot.shouldShowBoardingConfirmation

    var isTransferWarningDismissed by remember(currentSnapshot.realTimeStatus?.upcomingTransferLine) { mutableStateOf(false) }
    val isTransferAtRisk = currentSnapshot.isTransferAtRisk && !isTransferWarningDismissed
    var showCancelConfirmationDialog by remember { mutableStateOf(false) }

    if (showCancelConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmationDialog = false },
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.trip_end_dialog_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.trip_end_dialog_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmationDialog = false
                        onCancelTrip()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_finish_trip),
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmationDialog = false }) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_continue_trip),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    val isTripCompleted = activeTrip.status == ActiveTripEntity.STATUS_COMPLETED

    // Color Semantics based on Urgency Level (Green / Orange / Red / Completed Emerald)
    val containerColor = when {
        isTripCompleted -> if (isDark) Color(0xFF132A1C) else Color(0xFFE8F5E9)
        promptData.urgencyLevel == TripUrgencyLevel.CRITICAL -> if (isDark) Color(0xFF281114) else Color(0xFFFFF1F2)
        promptData.urgencyLevel == TripUrgencyLevel.BRISK -> if (isDark) Color(0xFF281C10) else Color(0xFFFFF8F0)
        else -> com.example.ui.theme.AppThemeColors.cardBackground(isDark)
    }

    val borderColor = when {
        isTripCompleted -> Color(0xFF2E7D32).copy(alpha = if (isDark) 0.6f else 0.4f)
        promptData.urgencyLevel == TripUrgencyLevel.CRITICAL -> Color(0xFFE53935).copy(alpha = if (isDark) 0.8f else 0.5f)
        promptData.urgencyLevel == TripUrgencyLevel.BRISK -> Color(0xFFFF9800).copy(alpha = if (isDark) 0.8f else 0.5f)
        else -> com.example.ui.theme.AppThemeColors.subtleBorder(isDark)
    }

    val primaryTextColor = when {
        isTripCompleted -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
        promptData.urgencyLevel == TripUrgencyLevel.CRITICAL -> if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
        promptData.urgencyLevel == TripUrgencyLevel.BRISK -> if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val secondaryTextColor = if (isTripCompleted) {
        if (isDark) Color(0xFFC8E6C9) else Color(0xFF2E7D32)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val density = LocalDensity.current

    val hasActiveInteractivePrompt = shouldShowConfirmation || isTransferAtRisk || recalculateError != null || isOffRoute

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .onGloballyPositioned { coordinates ->
                val heightDp = with(density) { coordinates.size.height.toDp() }
                onHeightChanged?.invoke(heightDp)
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                if (isTripCompleted) {
                    onCancelTrip()
                } else if (isNavigationMapView) {
                    onExpandDetails()
                } else {
                    onOpenRouteOnMap?.invoke() ?: onExpandDetails()
                }
            }
            .testTag("active_trip_overlay"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = 1.dp, color = borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        when {
            // Case 0: Destination Reached / Trip Completed
            isTripCompleted -> {
                ArrivalCompletedContent(
                    destinationName = activeTrip.destinationName,
                    isDark = isDark,
                    appLanguage = appLanguage,
                    onDismiss = onCancelTrip
                )
            }

            // Case 1: Full Navigation View (on the Map screen while following active route)
            isNavigationMapView -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Route Deviation Pill
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
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.trip_status_route_diverted),
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
                    if (isTransferAtRisk) {
                        TransferRiskPromptContent(
                            isDark = isDark,
                            appLanguage = appLanguage,
                            realTimeStatus = realTimeStatus,
                            isRecalculating = isRecalculating,
                            secondaryTextColor = secondaryTextColor,
                            onRecalculateTransfer = onRecalculateTransfer,
                            onDismiss = { isTransferWarningDismissed = true }
                        )
                    }

                    // Recalculation Error Banner
                    if (recalculateError != null) {
                        RecalculateErrorBanner(
                            error = recalculateError,
                            isDark = isDark,
                            onDismiss = onDismissRecalculateError
                        )
                    }

                    // Next Transit Departure Info Banner
                    if (!promptData.nextTransitDepartureInfo.isNullOrBlank() && !isTransferAtRisk && !isOffRoute && promptData.subheadline != promptData.nextTransitDepartureInfo) {
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
                            onClick = { showCancelConfirmationDialog = true },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("active_trip_cancel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.cancel_trip_btn),
                                tint = if (isDark) Color(0xFF78909C) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Interactive Onboard Confirmation Chip ("¿A bordo?")
                    if (shouldShowConfirmation && candidateTransitLeg != null) {
                        BoardingConfirmationContent(
                            candidateTransitLeg = candidateTransitLeg,
                            candidateLegIndex = candidateLegIndex,
                            isDark = isDark,
                            appLanguage = appLanguage,
                            onAdvanceLeg = onAdvanceLeg
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Middle: Multi-modal Segmented Stepper Bar
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
                                    com.example.ui.components.LiveRssFeedIcon(
                                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.status_live),
                                        tint = Color(0xFF00A86B),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.trip_status_live),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF00A86B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = if (promptData.formattedRemainingDurationText.isNotBlank()) promptData.formattedRemainingDurationText else itinerary.formattedDuration,
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

            // Case 2: Interactive Prompt Card (Expanded when an alert or confirmation happens outside navigation map)
            hasActiveInteractivePrompt -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Top Mini-Header: Destination info + Reopen Map + Cancel button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        when (promptData.urgencyLevel) {
                                            TripUrgencyLevel.CRITICAL -> Color(0xFFFF5252).copy(alpha = if (isDark) 0.25f else 0.15f)
                                            TripUrgencyLevel.BRISK -> Color(0xFFFF9800).copy(alpha = if (isDark) 0.25f else 0.15f)
                                            TripUrgencyLevel.RELAXED -> Color(0xFF00A86B).copy(alpha = if (isDark) 0.25f else 0.15f)
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = promptData.icon,
                                    contentDescription = null,
                                    tint = when (promptData.urgencyLevel) {
                                        TripUrgencyLevel.CRITICAL -> Color(0xFFE53935)
                                        TripUrgencyLevel.BRISK -> Color(0xFFF57C00)
                                        TripUrgencyLevel.RELAXED -> Color(0xFF00A86B)
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = promptData.headline,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${promptData.formattedArrivalTimeText} • ${if (promptData.formattedRemainingDurationText.isNotBlank()) promptData.formattedRemainingDurationText else itinerary.formattedDuration}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = secondaryTextColor,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // View map button
                            if (onOpenRouteOnMap != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.4f else 0.2f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenRouteOnMap() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.btn_view_map),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.R.string.map_label),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            IconButton(
                                onClick = { showCancelConfirmationDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.cancel_trip_btn),
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 1. Route Deviation Warning
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
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.trip_status_route_diverted),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // 2. Boarding Confirmation Prompt
                    if (shouldShowConfirmation && candidateTransitLeg != null) {
                        BoardingConfirmationContent(
                            candidateTransitLeg = candidateTransitLeg,
                            candidateLegIndex = candidateLegIndex,
                            isDark = isDark,
                            appLanguage = appLanguage,
                            onAdvanceLeg = onAdvanceLeg
                        )
                    }

                    // 3. Missed Transfer Prompt
                    if (isTransferAtRisk) {
                        TransferRiskPromptContent(
                            isDark = isDark,
                            appLanguage = appLanguage,
                            realTimeStatus = realTimeStatus,
                            isRecalculating = isRecalculating,
                            secondaryTextColor = secondaryTextColor,
                            onRecalculateTransfer = onRecalculateTransfer,
                            onDismiss = { isTransferWarningDismissed = true }
                        )
                    }

                    // 4. Recalculation Error
                    if (recalculateError != null) {
                        RecalculateErrorBanner(
                            error = recalculateError,
                            isDark = isDark,
                            onDismiss = onDismissRecalculateError
                        )
                    }
                }
            }

            // Case 3: Non-Map Transit GO Banner (Rich, glanceable, perfectly proportioned)
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Row 1: Mode Icon Badge + Instruction/Headline + Walk Badge + "Ver ruta" CTA + Cancel Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Icon + Headline + Walk Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val iconBg = when (promptData.urgencyLevel) {
                                TripUrgencyLevel.CRITICAL -> Color(0xFFFF5252).copy(alpha = if (isDark) 0.25f else 0.15f)
                                TripUrgencyLevel.BRISK -> Color(0xFFFF9800).copy(alpha = if (isDark) 0.25f else 0.15f)
                                TripUrgencyLevel.RELAXED -> Color(0xFF00A86B).copy(alpha = if (isDark) 0.22f else 0.12f)
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
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = promptData.headline,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryTextColor,
                                            fontSize = 14.sp
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
                                                modifier = Modifier.size(10.dp),
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
                                if (promptData.subheadline.isNotBlank()) {
                                    Text(
                                        text = promptData.subheadline,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = secondaryTextColor,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right: "Ver ruta" CTA button + Cancel icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.12f),
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onOpenRouteOnMap?.invoke() ?: onExpandDetails()
                                    }
                                    .testTag("active_trip_view_map_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.view_route_on_map),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showCancelConfirmationDialog = true },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("active_trip_compact_cancel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.cancel_trip_btn),
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // Optional Boarding Confirmation
                    if (shouldShowConfirmation && candidateTransitLeg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        BoardingConfirmationContent(
                            candidateTransitLeg = candidateTransitLeg,
                            candidateLegIndex = candidateLegIndex,
                            isDark = isDark,
                            appLanguage = appLanguage,
                            onAdvanceLeg = onAdvanceLeg
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Bottom Row: ETA & Duration + "Pasos >" chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = when (promptData.urgencyLevel) {
                                    TripUrgencyLevel.CRITICAL -> Color(0xFFE53935)
                                    TripUrgencyLevel.BRISK -> Color(0xFFFF9800)
                                    TripUrgencyLevel.RELAXED -> Color(0xFF00A86B)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${promptData.formattedArrivalTimeText} • ${if (promptData.formattedRemainingDurationText.isNotBlank()) promptData.formattedRemainingDurationText else itinerary.formattedDuration}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = secondaryTextColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isLive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF00A86B).copy(alpha = if (isDark) 0.22f else 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    com.example.ui.components.LiveRssFeedIcon(
                                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.status_live),
                                        tint = Color(0xFF00A86B),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.trip_status_live),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF00A86B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }

                        // "Pasos >" Clickable text
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_steps),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onExpandDetails() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
