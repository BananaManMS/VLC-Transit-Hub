package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Subway
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.ui.dashboard.AppLanguage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Visual Urgency State encoded as color semantics:
 * - RELAXED: Margin >= 2 min (Green / Emerald / On-time)
 * - BRISK: Margin 0..2 min (Amber / Orange / Pace briskly)
 * - CRITICAL: Margin < 0 min (Red / Crimson / Immediate)
 */
enum class TripUrgencyLevel {
    RELAXED,
    BRISK,
    CRITICAL
}

data class TripFormattedUIState(
    val headline: String,
    val subheadline: String,
    val formattedArrivalTimeText: String,
    val delayMinutes: Int,
    val icon: ImageVector,
    val isLive: Boolean,
    val urgencyLevel: TripUrgencyLevel = TripUrgencyLevel.RELAXED,
    val lineBadge: String? = null,
    val walkBadgeMinutes: Int? = null,
    val distanceRemainingText: String? = null,
    val isDebarkNotice: Boolean = false,
    val targetStationName: String? = null,
    val nextTransitDepartureInfo: String? = null,
    val nextTransitIcon: ImageVector? = null
)

/**
 * Single source of truth for active trip UI prompt formatting,
 * arrival time recalculation, and microcopy reduction in movement.
 * Reduces cognitive load to glanceable discrete tokens: e.g. "L3 · 14:05 (en 5 min)" + 🚶 "3 min".
 */
object TripUIStateFormatter {

    fun format(
        currentLeg: PlannedLeg?,
        currentLegIndex: Int,
        totalLegs: Int,
        realTimeStatus: RealTimeTripStatus?,
        isBoarded: Boolean,
        scheduledArrivalTime: String,
        appLanguage: AppLanguage,
        distanceToTargetMeters: Double? = null,
        allLegs: List<PlannedLeg> = emptyList()
    ): TripFormattedUIState {
        val delayMins = realTimeStatus?.delayMinutes ?: 0
        val isLive = realTimeStatus?.isLive == true
        val isSalYa = realTimeStatus?.isLeaveNowAlert == true

        // Calculate next upcoming transit departure info if available
        var nextTransitDepartureInfo: String? = null
        var nextTransitIcon: ImageVector? = null

        val upcomingTransitLeg = if (currentLeg != null && (currentLeg.mode == TransitMode.WALK || currentLeg.mode == TransitMode.BICYCLE)) {
            // While walking or cycling to a stop/station, show departure time of the approaching transit leg
            allLegs.subList((currentLegIndex + 1).coerceAtMost(allLegs.size), allLegs.size)
                .firstOrNull { it.mode != TransitMode.WALK && it.mode != TransitMode.BICYCLE }
        } else if (isBoarded && allLegs.isNotEmpty()) {
            // While boarded on a vehicle, show the upcoming transfer transit leg
            allLegs.subList((currentLegIndex + 1).coerceAtMost(allLegs.size), allLegs.size)
                .firstOrNull { it.mode != TransitMode.WALK && it.mode != TransitMode.BICYCLE }
        } else {
            // User is waiting at the stop/platform for the current transit leg.
            // The main card already displays line, destination, and departure ETA prominently; avoid redundant pill.
            null
        }

        if (upcomingTransitLeg != null) {
            val modeName = when (upcomingTransitLeg.mode) {
                TransitMode.BUS -> "Bus"
                TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                TransitMode.RAIL -> if (appLanguage == AppLanguage.ES) "Cercanías" else "Rodalia"
                else -> if (appLanguage == AppLanguage.ES) "Línea" else "Línia"
            }
            val tIcon = when (upcomingTransitLeg.mode) {
                TransitMode.BUS -> Icons.Default.DirectionsBus
                TransitMode.SUBWAY, TransitMode.TRAM -> Icons.Default.Subway
                TransitMode.RAIL -> Icons.Default.DirectionsRailway
                else -> Icons.Default.DirectionsBus
            }
            val lineName = upcomingTransitLeg.routeShortName ?: ""
            val lineStr = if (lineName.isNotBlank()) "$modeName $lineName" else modeName

            if (realTimeStatus?.isUpcomingTransferLive == true && realTimeStatus.upcomingTransferMinutes != null && isBoarded) {
                val tMins = realTimeStatus.upcomingTransferMinutes
                nextTransitDepartureInfo = if (appLanguage == AppLanguage.ES) {
                    "Transbordo $lineStr en $tMins min"
                } else {
                    "Transbordament $lineStr en $tMins min"
                }
                nextTransitIcon = Icons.Default.RssFeed
            } else {
                val depTime = upcomingTransitLeg.formattedStartTime
                if (depTime.isNotBlank()) {
                    nextTransitDepartureInfo = if (appLanguage == AppLanguage.ES) {
                        "$lineStr sale a las $depTime"
                    } else {
                        "$lineStr ix a les $depTime"
                    }
                    nextTransitIcon = tIcon
                }
            }
        }

        // 1. Recalculate Adjusted Arrival Time: Scheduled Arrival + Delay
        val adjustedArrivalTime = calculateAdjustedArrivalTime(scheduledArrivalTime, delayMins)
        val isEs = appLanguage == AppLanguage.ES

        val arrivalPrefix = if (isEs) "Llegada" else "Arribada"
        val formattedArrivalTimeText = "$arrivalPrefix $adjustedArrivalTime"

        val liveMins = realTimeStatus?.vehicleArrivalMinutes

        // Urgency Level determination purely by color semantics (no verbose nagging text)
        val urgencyLevel = when {
            isSalYa || (realTimeStatus?.isTransferAtRisk == true) -> TripUrgencyLevel.CRITICAL
            (liveMins != null && liveMins <= 2) || (realTimeStatus?.vehicleSecondsRemaining != null && realTimeStatus.vehicleSecondsRemaining <= 120) -> TripUrgencyLevel.BRISK
            delayMins > 3 -> TripUrgencyLevel.BRISK
            else -> TripUrgencyLevel.RELAXED
        }

        if (currentLeg == null) {
            return TripFormattedUIState(
                headline = if (isEs) "En ruta" else "En ruta",
                subheadline = "",
                formattedArrivalTimeText = formattedArrivalTimeText,
                delayMinutes = delayMins,
                icon = Icons.Default.Navigation,
                isLive = isLive,
                urgencyLevel = urgencyLevel
            )
        }

        val distText = if (distanceToTargetMeters != null && distanceToTargetMeters > 0) {
            LocationUtils.formatDistance(distanceToTargetMeters)
        } else null

        if (isSalYa) {
            val line = currentLeg.routeShortName ?: realTimeStatus?.vehicleLine ?: "Bus"
            val liveStr = if (liveMins != null) " · $liveMins min" else ""
            return TripFormattedUIState(
                headline = "$line$liveStr",
                subheadline = if (isEs) "Hacia ${currentLeg.toName}" else "Cap a ${currentLeg.toName}",
                formattedArrivalTimeText = formattedArrivalTimeText,
                delayMinutes = delayMins,
                icon = Icons.Default.DirectionsRun,
                isLive = isLive,
                urgencyLevel = TripUrgencyLevel.CRITICAL,
                lineBadge = line,
                distanceRemainingText = distText
            )
        }

        val icon: ImageVector
        val headline: String
        val subheadline: String
        var lineBadge: String? = null
        var walkMinsBadge: Int? = null

        when (currentLeg.mode) {
            TransitMode.WALK -> {
                icon = Icons.AutoMirrored.Filled.DirectionsWalk
                val walkMins = if (realTimeStatus?.dynamicWalkMinutesRemaining != null && realTimeStatus.dynamicWalkMinutesRemaining > 0) {
                    realTimeStatus.dynamicWalkMinutesRemaining
                } else if (distanceToTargetMeters != null && distanceToTargetMeters > 0) {
                    TripStepProgressionEngine.calculateDynamicWalkMinutes(distanceToTargetMeters, currentLeg)
                } else {
                    (currentLeg.durationSeconds / 60).coerceAtLeast(1).toInt()
                }
                walkMinsBadge = walkMins

                val targetStation = currentLeg.toName
                val walkLabel = if (isEs) "${walkMins} min a pie" else "${walkMins} min a peu"
                val destLabel = if (isEs) "Destino" else "Destí"
                val linkLabel = if (isEs) "Enlace · ${walkMins} min" else "Enllaç · ${walkMins} min"

                if (currentLegIndex == 0) {
                    if (isLive && liveMins != null) {
                        val line = realTimeStatus?.vehicleLine ?: ""
                        headline = "$line · en $liveMins min"
                        subheadline = if (distText != null) "$targetStation · $distText" else targetStation
                        lineBadge = line
                    } else {
                        headline = walkLabel
                        subheadline = if (distText != null) "$targetStation · $distText" else targetStation
                    }
                } else if (currentLegIndex == totalLegs - 1) {
                    headline = walkLabel
                    subheadline = if (distText != null) "$destLabel · $distText" else destLabel
                } else {
                    headline = linkLabel
                    subheadline = if (distText != null) "$targetStation · $distText" else targetStation
                }
            }

            TransitMode.BUS -> {
                icon = Icons.Default.DirectionsBus
                val lineName = currentLeg.routeShortName ?: "Bus"
                val destName = currentLeg.toName
                lineBadge = lineName

                if (isBoarded) {
                    headline = "Bus $lineName ➔ $destName"
                    val remainingMins = calculateBoardedRemainingMinutes(currentLeg, realTimeStatus)
                    subheadline = if (remainingMins <= 1) {
                        if (isEs) "Prepárate para bajar" else "Prepara't per a baixar"
                    } else {
                        if (isEs) "Bajas en $remainingMins min" else "Baixes en $remainingMins min"
                    }
                } else {
                    val depTime = currentLeg.formattedStartTime
                    val liveStr = if (isLive && liveMins != null) " (en $liveMins min)" else ""
                    headline = "$lineName · $depTime$liveStr"
                    subheadline = "➔ $destName"
                }
            }

            TransitMode.SUBWAY, TransitMode.TRAM -> {
                icon = Icons.Default.Subway
                val lineName = currentLeg.routeShortName ?: "Metro"
                val destName = currentLeg.toName
                lineBadge = lineName

                if (isBoarded) {
                    headline = "Metro $lineName ➔ $destName"
                    val remainingMins = calculateBoardedRemainingMinutes(currentLeg, realTimeStatus)
                    subheadline = if (remainingMins <= 1) {
                        if (isEs) "Prepárate para bajar" else "Prepara't per a baixar"
                    } else {
                        if (isEs) "Bajas en $remainingMins min" else "Baixes en $remainingMins min"
                    }
                } else {
                    val depTime = currentLeg.formattedStartTime
                    val liveStr = if (isLive && liveMins != null) " (en $liveMins min)" else ""
                    headline = "$lineName · $depTime$liveStr"
                    subheadline = "➔ $destName"
                }
            }

            TransitMode.RAIL -> {
                icon = Icons.Default.DirectionsRailway
                val lineName = currentLeg.routeShortName ?: "Tren"
                val destName = currentLeg.toName
                val railTitle = if (isEs) "Cercanías" else "Rodalia"
                lineBadge = lineName

                if (isBoarded) {
                    headline = "$railTitle $lineName ➔ $destName"
                    val remainingMins = calculateBoardedRemainingMinutes(currentLeg, realTimeStatus)
                    subheadline = if (remainingMins <= 1) {
                        if (isEs) "Prepárate para bajar" else "Prepara't per a baixar"
                    } else {
                        if (isEs) "Bajas en $remainingMins min" else "Baixes en $remainingMins min"
                    }
                } else {
                    val depTime = currentLeg.formattedStartTime
                    val liveStr = if (isLive && liveMins != null) " (en $liveMins min)" else ""
                    headline = "$lineName · $depTime$liveStr"
                    subheadline = "➔ $destName"
                }
            }

            TransitMode.BICYCLE -> {
                icon = Icons.AutoMirrored.Filled.DirectionsWalk
                val mins = (currentLeg.durationSeconds / 60).coerceAtLeast(1)
                headline = "Bici · ${mins} min"
                subheadline = currentLeg.toName
            }
        }

        return TripFormattedUIState(
            headline = headline,
            subheadline = subheadline,
            formattedArrivalTimeText = formattedArrivalTimeText,
            delayMinutes = delayMins,
            icon = icon,
            isLive = isLive,
            urgencyLevel = urgencyLevel,
            lineBadge = lineBadge,
            walkBadgeMinutes = walkMinsBadge,
            distanceRemainingText = distText,
            targetStationName = currentLeg.toName,
            nextTransitDepartureInfo = nextTransitDepartureInfo,
            nextTransitIcon = nextTransitIcon
        )
    }

    /**
     * Parses scheduled arrival time string ("19:09") and adds delayMinutes,
     * returning the recalculated arrival time ("19:28").
     */
    fun calculateAdjustedArrivalTime(scheduledTimeStr: String, delayMinutes: Int): String {
        if (scheduledTimeStr.isBlank()) return scheduledTimeStr
        val trimmed = scheduledTimeStr.trim()
        if (delayMinutes <= 0) return trimmed

        val timePatterns = listOf("HH:mm:ss", "HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")

        for (pattern in timePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val date = sdf.parse(trimmed)
                if (date != null) {
                    val cal = Calendar.getInstance().apply {
                        time = date
                        add(Calendar.MINUTE, delayMinutes)
                    }
                    val outSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    return outSdf.format(cal.time)
                }
            } catch (_: Exception) {}
        }

        return trimmed
    }

    /**
     * Calculates remaining minutes until destination stop while boarded on transit.
     */
    fun calculateBoardedRemainingMinutes(leg: PlannedLeg, realTimeStatus: RealTimeTripStatus?): Int {
        val progressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg.coerceIn(0f, 1f)
        val totalMins = (leg.durationSeconds / 60).toInt().coerceAtLeast(1)
        val progressMins = (totalMins * (1f - progressFraction)).toInt().coerceAtLeast(1)

        val delay = realTimeStatus?.delayMinutes ?: 0
        val progressBasedMins = ((totalMins * (1f - progressFraction)).toInt() + delay).coerceAtLeast(1)

        // Direct real-time vehicle ETA at destination stop (supports any delay size without capping)
        val realTimeEta = realTimeStatus?.vehicleArrivalMinutes ?: realTimeStatus?.checkpointEtaMinutes
        if (realTimeEta != null && realTimeEta > 0) {
            return realTimeEta
        }

        // Theoretical remaining based on scheduled leg end time
        val endMinsTheoretical = calculateTheoreticalMinutesRemaining(leg.endTime)
            ?: calculateTheoreticalMinutesRemaining(leg.formattedEndTime)

        if (endMinsTheoretical != null) {
            val theoreticalWithDelay = (endMinsTheoretical + delay).coerceAtLeast(1)
            // If discrepancy between theoretical end and GPS progress is within 15 min OR if there is an active delay (>10m), trust theoretical
            // Otherwise if delay is ~0 but theoretical diverges by >15m, it indicates a mismatched leg end timestamp (e.g. whole-trip end time)
            val discrepancy = kotlin.math.abs(theoreticalWithDelay - progressBasedMins)
            if (discrepancy <= 15 || delay > 10) {
                return theoreticalWithDelay
            }
        }

        return progressBasedMins
    }

    private fun calculateTheoreticalMinutesRemaining(timeStr: String?): Int? {
        if (timeStr.isNullOrBlank()) return null
        val parsedMs = TripTimeParser.parseTimeToMillis(timeStr) ?: return null
        val nowMs = System.currentTimeMillis()
        val diffMs = parsedMs - nowMs
        val diffMins = (diffMs / 60_000L).toInt()
        return if (diffMins in -30..180) diffMins.coerceAtLeast(1) else null
    }
}
