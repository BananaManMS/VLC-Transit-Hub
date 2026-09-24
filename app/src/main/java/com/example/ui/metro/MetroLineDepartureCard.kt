package com.example.ui.metro

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import kotlinx.coroutines.delay

@Composable
fun MetroLineDepartureCard(
    group: LineDeparturesGroupUiModel,
    metroViewModel: MetroViewModel,
    appLanguage: AppLanguage,
    texts: Translation,
    isDarkMode: Boolean,
    onExpired: (String) -> Unit
) {
    val primaryDep = group.primaryDeparture.originalDeparture

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
                .clickable {
                    metroViewModel.selectDepartureDetails(primaryDep)
                },
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
                        Text(
                            text = group.primaryDestination,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                            color = cardTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val extraInfo = listOfNotNull(
                            primaryDep.estimatedTime?.let { "Salida $it" },
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

                // Avisos de la línea si los hubiera
                if (lineIncidents.isNotEmpty()) {
                    val text = if (lineIncidents.size == 1) {
                        if (appLanguage == AppLanguage.CA) "1 avís de línia" else "1 aviso de línea"
                    } else {
                        if (appLanguage == AppLanguage.CA) "${lineIncidents.size} avisos de línia" else "${lineIncidents.size} avisos de línea"
                    }
                    Row(
                        modifier = Modifier.padding(start = 48.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = text,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                // Fila Principal: Tapping anywhere on the top half selects the primary departure
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .clickable {
                            metroViewModel.selectDepartureDetails(primaryDep)
                        }
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
                        Text(
                            text = group.primaryDestination,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                            color = cardTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val extraInfo = listOfNotNull(
                            primaryDep.estimatedTime?.let { "Salida $it" },
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
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = bottomShape, bottomEnd = bottomShape))
                                .clickable {
                                    metroViewModel.selectDepartureDetails(nextDep.originalDeparture)
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Espaciador alineado con la insignia (36dp + 12dp = 48dp)
                            Spacer(modifier = Modifier.width(48.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nextDep.destination,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                            Text(
                                text = "$liveMinutes min",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Avisos de la línea si los hubiera
                if (lineIncidents.isNotEmpty()) {
                    val text = if (lineIncidents.size == 1) {
                        if (appLanguage == AppLanguage.CA) "1 avís de línia" else "1 aviso de línea"
                    } else {
                        if (appLanguage == AppLanguage.CA) "${lineIncidents.size} avisos de línia" else "${lineIncidents.size} avisos de línea"
                    }
                    Row(
                        modifier = Modifier.padding(start = 62.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = text,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
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
    var secondsRemaining by remember(departure.id, departure.targetArrivalEpochMs) {
        mutableIntStateOf(departure.liveSecondsRemaining)
    }

    LaunchedEffect(departure.id, departure.targetArrivalEpochMs) {
        secondsRemaining = departure.liveSecondsRemaining
        while (secondsRemaining > -10) {
            delay(1000)
            secondsRemaining = departure.liveSecondsRemaining
        }
        onExpired(departure.id)
    }

    LaunchedEffect(departure.secondsRemaining, departure.targetArrivalEpochMs) {
        secondsRemaining = departure.liveSecondsRemaining
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

    val infiniteTransition = rememberInfiniteTransition(label = "metro_departing_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "metro_departing_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = if (departure.isRealTime && primaryUiModel.shouldBlink) Modifier.graphicsLayer(alpha = pulseAlpha) else Modifier
    ) {
        Text(
            text = primaryUiModel.timeAnnotated,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
            color = timeColor,
            textAlign = TextAlign.End
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
