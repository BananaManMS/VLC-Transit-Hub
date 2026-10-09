package com.example.ui.metro

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.service.QuickVehicleTrackerManager
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import kotlinx.coroutines.delay

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Context
import android.os.Build

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetroLineDepartureCard(
    group: LineDeparturesGroupUiModel,
    metroViewModel: MetroViewModel,
    appLanguage: AppLanguage,
    texts: Translation,
    isDarkMode: Boolean,
    onExpired: (String) -> Unit,
    onLongClickDeparture: ((RealTimeDeparture) -> Unit)? = null,
    stationId: String? = group.stationId,
    stationName: String? = group.stationName
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val primaryDep = group.primaryDeparture.originalDeparture
    val activeTrackedVehicle by QuickVehicleTrackerManager.activeTrackedVehicle.collectAsState()
    val resolvedStationId = stationId ?: group.stationId ?: primaryDep.originStationId
    val resolvedStationName = stationName ?: group.stationName ?: primaryDep.originStationName

    val isPrimaryPinned = remember(activeTrackedVehicle, group.lineId, group.primaryDestination, primaryDep, resolvedStationId, resolvedStationName) {
        activeTrackedVehicle != null && QuickVehicleTrackerManager.isTrackingSpecificDeparture(
            departure = primaryDep,
            lineId = group.lineId,
            destination = group.primaryDestination,
            stationId = resolvedStationId,
            stationName = resolvedStationName
        )
    }

    val badgeColor = remember(group.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(group.colorHex))
        } catch (_: Exception) {
            Color(0xFFE53935)
        }
    }

    val activeIncidents by metroViewModel.activeIncidents.collectAsState()
    val lineIncidents = remember(group.lineId, activeIncidents) {
        metroViewModel.getIncidentsForLine(group.lineId)
    }

    val isDark = isDarkMode

    val containerBgColor = if (isDark) {
        badgeColor.copy(alpha = 0.16f).compositeOver(Color(0xFF23252E))
    } else {
        badgeColor.copy(alpha = 0.08f)
    }

    val cardTextColor = MaterialTheme.colorScheme.onSurface
    val hasMultipleDepartures = group.subsequentDepartures.isNotEmpty()

    if (!hasMultipleDepartures) {
        // Single departure: Make the ENTIRE card clickable edge-to-edge for 100% effortless touch target
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("line_departure_card_${group.lineId}_${group.primaryDestination}")
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        metroViewModel.selectDepartureDetails(primaryDep)
                    },
                    onLongClick = if (onLongClickDeparture != null) {
                        {
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                                    vibratorManager?.defaultVibrator
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(50)
                                }
                            } catch (_: Exception) {}
                            onLongClickDeparture(primaryDep)
                        }
                    } else null
                ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerBgColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Fila Principal: Logo + Destino + Tiempo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroLineBadge(
                        lineId = group.lineId,
                        fallbackColorHex = group.colorHex,
                        size = 36.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.primaryDestination,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                                color = cardTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isPrimaryPinned) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.quick_track_pinned_desc),
                                    tint = badgeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        
                        val extraInfo = listOfNotNull(
                            primaryDep.estimatedTime?.let { androidx.compose.ui.res.stringResource(com.example.R.string.departure_time_prefix, it) },
                            primaryDep.track,
                            primaryDep.status?.takeIf { it != "En hora" && it.isNotBlank() }
                        ).joinToString(" • ")
                        
                        if (extraInfo.isNotEmpty()) {
                            Text(
                                text = extraInfo,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Isolated second counter for zero parent re-renders
                    MetroDepartureTimeCountdown(
                        departure = primaryDep,
                        appLanguage = appLanguage,
                        texts = texts,
                        isDarkMode = isDarkMode,
                        metroViewModel = metroViewModel,
                        onExpired = onExpired
                    )
                }
            }
        }
    } else {
        // Multiple departures: distinct generous touch areas for each departure
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("line_departure_card_${group.lineId}_${group.primaryDestination}"),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerBgColor
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Fila Principal: Tapping anywhere on the top half selects the primary departure, long click pins it
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .combinedClickable(
                            onClick = {
                                metroViewModel.selectDepartureDetails(primaryDep)
                            },
                            onLongClick = if (onLongClickDeparture != null) {
                                {
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                                            vibratorManager?.defaultVibrator
                                        } else {
                                            @Suppress("DEPRECATION")
                                            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator?.vibrate(50)
                                        }
                                    } catch (_: Exception) {}
                                    onLongClickDeparture(primaryDep)
                                }
                            } else null
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetroLineBadge(
                        lineId = group.lineId,
                        fallbackColorHex = group.colorHex,
                        size = 36.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.primaryDestination,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                                color = cardTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isPrimaryPinned) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.quick_track_pinned_desc),
                                    tint = badgeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        
                        val extraInfo = listOfNotNull(
                            primaryDep.estimatedTime?.let { androidx.compose.ui.res.stringResource(com.example.R.string.departure_time_prefix, it) },
                            primaryDep.track,
                            primaryDep.status?.takeIf { it != "En hora" && it.isNotBlank() }
                        ).joinToString(" • ")
                        
                        if (extraInfo.isNotEmpty()) {
                            Text(
                                text = extraInfo,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Isolated second counter for zero parent re-renders
                    MetroDepartureTimeCountdown(
                        departure = primaryDep,
                        appLanguage = appLanguage,
                        texts = texts,
                        isDarkMode = isDarkMode,
                        metroViewModel = metroViewModel,
                        onExpired = onExpired
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 1.dp),
                    thickness = 0.75.dp,
                    color = if (isDark) badgeColor.copy(alpha = 0.28f) else badgeColor.copy(alpha = 0.18f)
                )

                // Siguientes salidas: cada una con área de toque amplia de borde a borde
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    group.subsequentDepartures.forEachIndexed { index, nextDep ->
                        val isLast = index == group.subsequentDepartures.size - 1 && lineIncidents.isEmpty()
                        val bottomShape = if (isLast) 16.dp else 4.dp
                        val isSubPinned = remember(activeTrackedVehicle, group.lineId, nextDep.destination, nextDep.originalDeparture, resolvedStationId, resolvedStationName) {
                            activeTrackedVehicle != null && QuickVehicleTrackerManager.isTrackingSpecificDeparture(
                                departure = nextDep.originalDeparture,
                                lineId = group.lineId,
                                destination = nextDep.destination,
                                stationId = resolvedStationId,
                                stationName = resolvedStationName
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = bottomShape, bottomEnd = bottomShape))
                                .combinedClickable(
                                    onClick = {
                                        metroViewModel.selectDepartureDetails(nextDep.originalDeparture)
                                    },
                                    onLongClick = if (onLongClickDeparture != null) {
                                        {
                                            try {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                                                    vibratorManager?.defaultVibrator
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                }
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    vibrator?.vibrate(50)
                                                }
                                            } catch (_: Exception) {}
                                            onLongClickDeparture(nextDep.originalDeparture)
                                        }
                                    } else null
                                )
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Espaciador alineado con la insignia (36dp + 12dp = 48dp)
                            Spacer(modifier = Modifier.width(48.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = nextDep.destination,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isSubPinned) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Pinned",
                                            tint = badgeColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                val subExtra = listOfNotNull(
                                    nextDep.estimatedTime,
                                    nextDep.track,
                                    nextDep.status?.takeIf { it != "En hora" && it.isNotBlank() }
                                ).joinToString(" • ")
                                if (subExtra.isNotEmpty()) {
                                    Text(
                                        text = subExtra,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            val liveMinutes = nextDep.originalDeparture.liveMinutesRemaining
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$liveMinutes min",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (nextDep.originalDeparture.isRealTime) {
                                    com.example.ui.components.LiveRssFeedIcon(
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(13.dp)
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

/**
 * Self-contained countdown timer widget.
 * Ticking every second triggers recomposition ONLY inside this small Composable,
 * without recomposing the parent Card, list items, or surrounding containers.
 */
@Composable
private fun MetroDepartureTimeCountdown(
    departure: RealTimeDeparture,
    appLanguage: AppLanguage,
    texts: Translation,
    isDarkMode: Boolean,
    metroViewModel: MetroViewModel,
    onExpired: (String) -> Unit
) {
    val currentEpochMs by com.example.util.AppTimeTicker.secondPulse.collectAsState(initial = System.currentTimeMillis())
    val secondsRemaining = remember(departure.targetArrivalEpochMs, currentEpochMs) {
        ((departure.targetArrivalEpochMs - currentEpochMs) / 1000L).toInt()
    }

    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining <= -10) {
            onExpired(departure.id)
        }
    }

    val primaryUiModel = remember(departure, secondsRemaining, appLanguage, texts, isDarkMode) {
        MetroMapper.toDepartureUiModel(
            departure,
            secondsRemaining,
            appLanguage,
            texts,
            isDarkMode
        ) { digit -> metroViewModel.getSharedLineDigits(digit) }
    }

    val timeColor = when {
        !departure.isRealTime -> if (isDarkMode) Color.White else Color(0xFF1E293B)
        primaryUiModel.isWarningColor -> MaterialTheme.colorScheme.error
        primaryUiModel.isSecondaryColor -> MaterialTheme.colorScheme.secondary
        else -> if (isDarkMode) Color.White else Color(0xFF1E293B)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "MetroCountdownBlink")
    val blinkAlpha by if (primaryUiModel.shouldBlink) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "MetroBlinkAlpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
    ) {
        Text(
            text = primaryUiModel.timeAnnotated,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
            color = timeColor,
            textAlign = TextAlign.End,
            modifier = Modifier.graphicsLayer {
                alpha = blinkAlpha
            }
        )
        if (departure.isRealTime) {
            com.example.ui.components.LiveRssFeedIcon(
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
