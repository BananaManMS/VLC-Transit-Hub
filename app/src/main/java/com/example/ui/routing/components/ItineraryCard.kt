package com.example.ui.routing.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.ui.dashboard.AppLanguage

import android.location.Location
import com.example.ui.routing.PlannerLocation
import com.example.util.TripStartEligibility

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItineraryCard(
    itinerary: PlannedItinerary,
    isSelected: Boolean = false,
    isFastest: Boolean = false,
    onClick: () -> Unit,
    onStartTrip: ((PlannedItinerary) -> Unit)? = null,
    userLocation: Location? = null,
    originLocation: PlannerLocation? = null,
    appLanguage: AppLanguage = AppLanguage.CA,
    modifier: Modifier = Modifier
) {
    val cardBorder = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("itinerary_card_${itinerary.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Duration & Departure/Arrival Times & Transfers Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = itinerary.formattedDuration,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (isFastest) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE8F5E9),
                            contentColor = Color(0xFF1B5E20)
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "Más rápida" else "Més ràpida",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${itinerary.formattedDepartureTime} ➔ ${itinerary.formattedArrivalTime}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Transfers count badge ("Sin transbordos" / "1 transbordo", NOT confused with "En directo")
                val transferText = when (itinerary.transfersCount) {
                    0 -> if (appLanguage == AppLanguage.ES) "Sin transbordos" else "Sense transbord"
                    1 -> if (appLanguage == AppLanguage.ES) "1 transbordo" else "1 transbord"
                    else -> "${itinerary.transfersCount} ${if (appLanguage == AppLanguage.ES) "transbordos" else "transbords"}"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = transferText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live GPS Real-Time / Service Alert / Programado Status Badge
            Spacer(modifier = Modifier.height(10.dp))
            ViabilityBadge(itinerary = itinerary, appLanguage = appLanguage)

            Spacer(modifier = Modifier.height(14.dp))

            // Visual Multimodal Timeline Badges
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itinerary.legs.forEachIndexed { index, leg ->
                    LegBadge(leg = leg)
                    if (index < itinerary.legs.size - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Luego",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Alternative connecting lines within tolerance (e.g. "También disponible: Bus 4 (+1m)")
            if (itinerary.alternativeConnections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "También disponible:" else "També disponible:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itinerary.alternativeConnections.forEach { alt ->
                                val chipColor = try {
                                    Color(android.graphics.Color.parseColor(alt.routeColorHex))
                                } catch (e: Exception) {
                                    Color(0xFFDA291C)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = chipColor,
                                    contentColor = Color.White
                                ) {
                                    val deltaText = when {
                                        alt.deltaDurationMinutes > 0 -> "+${alt.deltaDurationMinutes} min"
                                        alt.deltaDurationMinutes < 0 -> "${alt.deltaDurationMinutes} min"
                                        else -> if (appLanguage == AppLanguage.ES) "mismo tiempo" else "mateix temps"
                                    }
                                    val modeLabel = if (alt.mode == TransitMode.TRAM) "Tranvía" else "Bus"
                                    Text(
                                        text = "$modeLabel ${alt.lineName} ($deltaText)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom summary footer & Start Action
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (itinerary.totalWalkDistanceMeters >= 10.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = "A pie",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${itinerary.totalWalkDistanceMeters.toInt()} m ${if (appLanguage == AppLanguage.ES) "a pie" else "a peu"}",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                val canStart = onStartTrip != null && TripStartEligibility.canStartTrip(itinerary, userLocation, originLocation)

                if (canStart) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF00A86B),
                        contentColor = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onStartTrip!!(itinerary) }
                            .testTag("start_trip_button_${itinerary.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "Iniciar" else "Iniciar",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onClick() }
                            .testTag("view_route_button_${itinerary.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.ES) "Ver trayecto" else "Veure trajecte",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViabilityBadge(
    itinerary: PlannedItinerary,
    appLanguage: AppLanguage = AppLanguage.CA
) {
    if (itinerary.viability == ItineraryViability.CHECKING_REAL_TIME) {
        val infiniteTransition = rememberInfiniteTransition(label = "skeleton_pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton_alpha"
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha * 0.25f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == AppLanguage.ES) "Comprobando GPS en directo..." else "Comprovant GPS en directe...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    ),
                    maxLines = 1
                )
            }
        }
        return
    }

    val isLiveGps = itinerary.viability == ItineraryViability.VIABLE_ON_TIME || itinerary.legs.any { it.isRealTimeVerified }
    val (bgColor, textColor, icon) = when {
        itinerary.viability == ItineraryViability.SERVICE_ALERT -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFB71C1C),
            Icons.Default.Warning
        )
        itinerary.viability == ItineraryViability.ADJUSTED_NEXT_DEPARTURE -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            Icons.Default.Schedule
        )
        isLiveGps -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF1B5E20),
            Icons.Default.RssFeed
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Schedule
        )
    }

    val noticeText = when {
        !itinerary.viabilityNotice.isNullOrBlank() -> itinerary.viabilityNotice?.replace("GPS en directo: ", "")?.replace("GPS en directe: ", "")
        itinerary.activeAlerts.isNotEmpty() -> itinerary.activeAlerts.first()
        isLiveGps -> if (appLanguage == AppLanguage.ES) "En directo • En hora" else "En directe • A l'hora"
        itinerary.viability == ItineraryViability.ADJUSTED_NEXT_DEPARTURE -> if (appLanguage == AppLanguage.ES) "Salida recalculada en directo" else "Eixida recalculada en directe"
        itinerary.viability == ItineraryViability.SERVICE_ALERT -> if (appLanguage == AppLanguage.ES) "Aviso de servicio activo" else "Avís de servei actiu"
        else -> if (appLanguage == AppLanguage.ES) "Horario programado" else "Horari programat"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        contentColor = textColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = textColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = noticeText ?: (if (appLanguage == AppLanguage.ES) "Horario programado" else "Horari programat"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                maxLines = 2
            )
        }
    }
}

@Composable
fun LegBadge(leg: PlannedLeg) {
    val hexColor = com.example.util.LineColorResolver.resolveRouteColor(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)

    val isEmtBus = leg.mode == TransitMode.BUS && (
        leg.agencyName?.contains("EMT", ignoreCase = true) == true ||
        (leg.agencyName.isNullOrBlank() && !leg.routeShortName.orEmpty().let {
            it.length >= 3 && (it.startsWith("1") || it.startsWith("2") || it.startsWith("3"))
        })
    )

    val icon: ImageVector = when (leg.mode) {
        TransitMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        TransitMode.BUS -> Icons.Default.DirectionsBus
        TransitMode.SUBWAY -> Icons.Default.Subway
        TransitMode.TRAM -> Icons.Default.Subway
        TransitMode.RAIL -> Icons.Default.DirectionsRailway
        TransitMode.BICYCLE -> Icons.AutoMirrored.Filled.DirectionsWalk
    }

    val badgeLabel = when (leg.mode) {
        TransitMode.WALK -> "${(leg.durationSeconds / 60).coerceAtLeast(1)} min"
        TransitMode.BUS -> leg.routeShortName ?: "Bus"
        TransitMode.SUBWAY -> if (leg.routeShortName.isNullOrBlank()) "Metro" else if (leg.routeShortName.startsWith("L")) leg.routeShortName else "L${leg.routeShortName}"
        TransitMode.TRAM -> if (leg.routeShortName.isNullOrBlank()) "Tranvía" else if (leg.routeShortName.startsWith("L") || leg.routeShortName.startsWith("T")) leg.routeShortName else "L${leg.routeShortName}"
        TransitMode.RAIL -> leg.routeShortName ?: "Rodalia"
        TransitMode.BICYCLE -> "Bici"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (leg.mode == TransitMode.WALK) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            hexColor
        },
        contentColor = if (leg.mode == TransitMode.WALK) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            Color.White
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEmtBus) {
                Icon(
                    painter = painterResource(id = R.drawable.logo_emt_valencia),
                    contentDescription = "EMT",
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = leg.mode.displayNameEs,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}
