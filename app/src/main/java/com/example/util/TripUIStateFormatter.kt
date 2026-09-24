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
import androidx.compose.material.icons.filled.Tram
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
    val dynamicArrivalTime: String = "",
    val remainingMinutes: Int = 0,
    val formattedRemainingDurationText: String = "",
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

    fun getDynamicVehicleArrivalMinutes(realTimeStatus: RealTimeTripStatus?): Int? {
        if (realTimeStatus == null) return null
        val baseMins = realTimeStatus.vehicleArrivalMinutes ?: return null
        if (realTimeStatus.lastCheckedTimestamp <= 0L) return baseMins
        val elapsedMs = System.currentTimeMillis() - realTimeStatus.lastCheckedTimestamp
        if (elapsedMs < 0L) return baseMins
        val elapsedMins = (elapsedMs / 60000L).toInt()
        return (baseMins - elapsedMins).coerceAtLeast(0)
    }

    fun getDynamicUpcomingTransferMinutes(realTimeStatus: RealTimeTripStatus?): Int? {
        if (realTimeStatus == null) return null
        val baseMins = realTimeStatus.upcomingTransferMinutes ?: return null
        if (realTimeStatus.lastCheckedTimestamp <= 0L) return baseMins
        val elapsedMs = System.currentTimeMillis() - realTimeStatus.lastCheckedTimestamp
        if (elapsedMs < 0L) return baseMins
        val elapsedMins = (elapsedMs / 60000L).toInt()
        return (baseMins - elapsedMins).coerceAtLeast(0)
    }

    fun isNearPenultimateStopOrTime(
        currentLeg: PlannedLeg,
        remainingMins: Int,
        distanceToTargetMeters: Double?,
        progressWithinLeg: Float
    ): Boolean {
        if (remainingMins <= 2) return true
        if (distanceToTargetMeters != null && distanceToTargetMeters <= 350.0) return true

        if (currentLeg.intermediateStops.isNotEmpty()) {
            val penultimateStop = currentLeg.intermediateStops.last()
            val targetLat = currentLeg.toLat
            val targetLon = currentLeg.toLon
            val distPenultimateToTarget = TripStepProgressionEngine.calculateDistanceMeters(
                penultimateStop.lat, penultimateStop.lon,
                targetLat, targetLon
            )
            if (distanceToTargetMeters != null && distanceToTargetMeters <= distPenultimateToTarget + 60.0 && progressWithinLeg >= 0.70f) {
                return true
            }
        }
        return false
    }

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
                TransitMode.TRAM -> if (appLanguage == AppLanguage.ES) "Tranvía" else "Tramvia"
                TransitMode.SUBWAY -> "Metro"
                TransitMode.RAIL -> if (appLanguage == AppLanguage.ES) "Cercanías" else "Rodalia"
                else -> if (appLanguage == AppLanguage.ES) "Línea" else "Línia"
            }
            val tIcon = when (upcomingTransitLeg.mode) {
                TransitMode.BUS -> Icons.Default.DirectionsBus
                TransitMode.TRAM -> Icons.Default.Tram
                TransitMode.SUBWAY -> Icons.Default.Subway
                TransitMode.RAIL -> Icons.Default.DirectionsRailway
                else -> Icons.Default.DirectionsBus
            }
            val lineName = upcomingTransitLeg.routeShortName ?: ""
            val lineStr = if (lineName.isNotBlank()) "$modeName $lineName" else modeName

            if (realTimeStatus?.isUpcomingTransferLive == true && isBoarded) {
                val tMins = getDynamicUpcomingTransferMinutes(realTimeStatus)
                if (tMins != null) {
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

        // 1. Calculate Dynamic Remaining Minutes for entire trip
        val remainingTripMinutes = calculateTripRemainingMinutes(
            currentLeg = currentLeg,
            currentLegIndex = currentLegIndex,
            allLegs = allLegs,
            realTimeStatus = realTimeStatus,
            isBoarded = isBoarded,
            distanceToTargetMeters = distanceToTargetMeters
        )
        val formattedRemainingDurationText = formatDurationMinutes(remainingTripMinutes)

        // 2. Recalculate Dynamic Arrival Time: (Now + Remaining Minutes) synchronized with real-time delays
        val isEs = appLanguage == AppLanguage.ES
        val arrivalPrefix = if (isEs) "Llegada" else "Arribada"
        val dynamicArrivalTime = calculateDynamicArrivalTime(scheduledArrivalTime, delayMins, remainingTripMinutes)
        val formattedArrivalTimeText = "$arrivalPrefix $dynamicArrivalTime"

        val liveMins = getDynamicVehicleArrivalMinutes(realTimeStatus)

        // Urgency Level determination purely by color semantics (no verbose nagging text)
        val urgencyLevel = when {
            isSalYa || (realTimeStatus?.isTransferAtRisk == true) -> TripUrgencyLevel.CRITICAL
            (liveMins != null && liveMins <= 2) || (realTimeStatus?.vehicleSecondsRemaining != null && realTimeStatus.vehicleSecondsRemaining <= 120) -> TripUrgencyLevel.BRISK
            delayMins > 3 -> TripUrgencyLevel.BRISK
            else -> TripUrgencyLevel.RELAXED
        }
        var computedUrgency = urgencyLevel

        if (currentLeg == null) {
            return TripFormattedUIState(
                headline = if (isEs) "En ruta" else "En ruta",
                subheadline = "",
                formattedArrivalTimeText = formattedArrivalTimeText,
                dynamicArrivalTime = dynamicArrivalTime,
                remainingMinutes = remainingTripMinutes,
                formattedRemainingDurationText = formattedRemainingDurationText,
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
            val targetStation = currentLeg.toName
            val walkMins = if (realTimeStatus?.dynamicWalkMinutesRemaining != null && realTimeStatus.dynamicWalkMinutesRemaining > 0) {
                realTimeStatus.dynamicWalkMinutesRemaining
            } else if (distanceToTargetMeters != null && distanceToTargetMeters > 0) {
                TripStepProgressionEngine.calculateDynamicWalkMinutes(distanceToTargetMeters, currentLeg)
            } else {
                (currentLeg.durationSeconds / 60).coerceAtLeast(1).toInt()
            }
            val walkLabel = if (isEs) "$walkMins min a pie" else "$walkMins min a peu"
            val h = if (isEs) "Sal ya hacia $targetStation" else "Ix ja cap a $targetStation"
            val s = if (distText != null) "$distText · $walkLabel" else walkLabel
            return TripFormattedUIState(
                headline = h,
                subheadline = s,
                formattedArrivalTimeText = formattedArrivalTimeText,
                dynamicArrivalTime = dynamicArrivalTime,
                remainingMinutes = remainingTripMinutes,
                formattedRemainingDurationText = formattedRemainingDurationText,
                delayMinutes = delayMins,
                icon = Icons.Default.DirectionsRun,
                isLive = isLive,
                urgencyLevel = TripUrgencyLevel.CRITICAL,
                lineBadge = line,
                distanceRemainingText = distText,
                targetStationName = targetStation,
                nextTransitDepartureInfo = nextTransitDepartureInfo,
                nextTransitIcon = nextTransitIcon
            )
        }

        if (realTimeStatus?.leaveInMinutes != null && realTimeStatus.leaveInMinutes > 0 && currentLeg.mode == TransitMode.WALK && currentLegIndex == 0) {
            val leaveMins = realTimeStatus.leaveInMinutes
            val targetStation = currentLeg.toName
            val walkMins = if (realTimeStatus.dynamicWalkMinutesRemaining != null && realTimeStatus.dynamicWalkMinutesRemaining > 0) {
                realTimeStatus.dynamicWalkMinutesRemaining
            } else if (distanceToTargetMeters != null && distanceToTargetMeters > 0) {
                TripStepProgressionEngine.calculateDynamicWalkMinutes(distanceToTargetMeters, currentLeg)
            } else {
                (currentLeg.durationSeconds / 60).coerceAtLeast(1).toInt()
            }
            val walkLabel = if (isEs) "$walkMins min a pie" else "$walkMins min a peu"
            val h = if (isEs) "Sal en $leaveMins min" else "Ix en $leaveMins min"
            val s = if (isEs) {
                "Camina a $targetStation${if (distText != null) " · $distText" else ""} · $walkLabel"
            } else {
                "Camina a $targetStation${if (distText != null) " · $distText" else ""} · $walkLabel"
            }
            return TripFormattedUIState(
                headline = h,
                subheadline = s,
                formattedArrivalTimeText = formattedArrivalTimeText,
                dynamicArrivalTime = dynamicArrivalTime,
                remainingMinutes = remainingTripMinutes,
                formattedRemainingDurationText = formattedRemainingDurationText,
                delayMinutes = delayMins,
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                isLive = isLive,
                urgencyLevel = TripUrgencyLevel.RELAXED,
                lineBadge = currentLeg.routeShortName ?: realTimeStatus.vehicleLine,
                distanceRemainingText = distText,
                targetStationName = targetStation,
                nextTransitDepartureInfo = nextTransitDepartureInfo,
                nextTransitIcon = nextTransitIcon
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
                val walkLabel = if (isEs) "$walkMins min a pie" else "$walkMins min a peu"

                if (currentLegIndex == 0) {
                    headline = if (isEs) "Camina a $targetStation" else "Camina a $targetStation"
                    subheadline = if (distText != null) "$distText · $walkLabel" else walkLabel
                } else if (currentLegIndex == totalLegs - 1) {
                    headline = if (isEs) "Camina a destino" else "Camina a destí"
                    subheadline = if (distText != null) "$distText · $walkLabel" else walkLabel
                } else {
                    val nextTransitLeg = allLegs.subList((currentLegIndex + 1).coerceAtMost(allLegs.size), allLegs.size)
                        .firstOrNull { it.mode != TransitMode.WALK && it.mode != TransitMode.BICYCLE }
                    val nextTransitName = if (nextTransitLeg != null) {
                        val mName = when (nextTransitLeg.mode) {
                            TransitMode.BUS -> "Bus"
                            TransitMode.TRAM -> if (isEs) "Tranvía" else "Tramvia"
                            TransitMode.SUBWAY -> "Metro"
                            TransitMode.RAIL -> if (isEs) "Cercanías" else "Rodalia"
                            else -> if (isEs) "Línea" else "Línia"
                        }
                        val rName = nextTransitLeg.routeShortName ?: ""
                        if (rName.isNotBlank()) "$mName $rName" else mName
                    } else {
                        if (isEs) "enlace" else "enllaç"
                    }
                    headline = if (isEs) "Enlace a $nextTransitName" else "Enllaç a $nextTransitName"
                    subheadline = if (isEs) "Hacia $targetStation${if (distText != null) " · $distText" else ""}" else "Cap a $targetStation${if (distText != null) " · $distText" else ""}"
                }
            }

            TransitMode.BUS -> {
                icon = Icons.Default.DirectionsBus
                val lineName = currentLeg.routeShortName ?: "Bus"
                val destName = currentLeg.toName
                lineBadge = lineName

                if (isBoarded) {
                    val (h, s, urg) = formatBoardedTransitPrompt(
                        mode = currentLeg.mode,
                        modeName = "Bus",
                        lineName = lineName,
                        destName = destName,
                        currentLeg = currentLeg,
                        realTimeStatus = realTimeStatus,
                        distanceToTargetMeters = distanceToTargetMeters,
                        isEs = isEs
                    )
                    headline = h
                    subheadline = s
                    computedUrgency = urg
                } else {
                    val (h, s) = formatWaitingTransitPrompt("Bus", lineName, currentLeg, realTimeStatus, isEs)
                    headline = h
                    subheadline = s
                }
            }

            TransitMode.SUBWAY, TransitMode.TRAM -> {
                icon = if (currentLeg.mode == TransitMode.TRAM) Icons.Default.Tram else Icons.Default.Subway
                val lineName = currentLeg.routeShortName ?: if (currentLeg.mode == TransitMode.TRAM) "Tranvía" else "Metro"
                val destName = currentLeg.toName
                val modeLabel = if (currentLeg.mode == TransitMode.TRAM) {
                    if (isEs) "Tranvía" else "Tramvia"
                } else "Metro"
                lineBadge = lineName

                if (isBoarded) {
                    val (h, s, urg) = formatBoardedTransitPrompt(
                        mode = currentLeg.mode,
                        modeName = modeLabel,
                        lineName = lineName,
                        destName = destName,
                        currentLeg = currentLeg,
                        realTimeStatus = realTimeStatus,
                        distanceToTargetMeters = distanceToTargetMeters,
                        isEs = isEs
                    )
                    headline = h
                    subheadline = s
                    computedUrgency = urg
                } else {
                    val (h, s) = formatWaitingTransitPrompt(modeLabel, lineName, currentLeg, realTimeStatus, isEs)
                    headline = h
                    subheadline = s
                }
            }

            TransitMode.RAIL -> {
                icon = Icons.Default.DirectionsRailway
                val lineName = currentLeg.routeShortName ?: "Tren"
                val destName = currentLeg.toName
                val railTitle = if (isEs) "Cercanías" else "Rodalia"
                lineBadge = lineName

                if (isBoarded) {
                    val (h, s, urg) = formatBoardedTransitPrompt(
                        mode = currentLeg.mode,
                        modeName = railTitle,
                        lineName = lineName,
                        destName = destName,
                        currentLeg = currentLeg,
                        realTimeStatus = realTimeStatus,
                        distanceToTargetMeters = distanceToTargetMeters,
                        isEs = isEs
                    )
                    headline = h
                    subheadline = s
                    computedUrgency = urg
                } else {
                    val (h, s) = formatWaitingTransitPrompt(railTitle, lineName, currentLeg, realTimeStatus, isEs)
                    headline = h
                    subheadline = s
                }
            }

            TransitMode.BICYCLE -> {
                icon = Icons.AutoMirrored.Filled.DirectionsWalk
                val mins = (currentLeg.durationSeconds / 60).coerceAtLeast(1)
                headline = "Bici · $mins min"
                subheadline = if (distText != null) "${currentLeg.toName} · $distText" else currentLeg.toName
            }

            TransitMode.METROBUS -> {
                val busTitle = "Metrobús"
                val lineName = currentLeg.routeShortName ?: currentLeg.routeLongName ?: ""
                val destName = currentLeg.headsign ?: currentLeg.toName
                icon = Icons.Default.DirectionsBus
                lineBadge = lineName

                if (isBoarded) {
                    val (h, s, urg) = formatBoardedTransitPrompt(
                        mode = currentLeg.mode,
                        modeName = busTitle,
                        lineName = lineName,
                        destName = destName,
                        currentLeg = currentLeg,
                        realTimeStatus = realTimeStatus,
                        distanceToTargetMeters = distanceToTargetMeters,
                        isEs = isEs
                    )
                    headline = h
                    subheadline = s
                    computedUrgency = urg
                } else {
                    val (h, s) = formatWaitingTransitPrompt(busTitle, lineName, currentLeg, realTimeStatus, isEs)
                    headline = h
                    subheadline = s
                }
            }

            TransitMode.CERCANIAS -> {
                val railTitle = if (isEs) "Cercanías" else "Rodalia"
                val lineName = currentLeg.routeShortName ?: currentLeg.routeLongName ?: ""
                val destName = currentLeg.headsign ?: currentLeg.toName
                icon = Icons.Default.DirectionsRailway
                lineBadge = lineName

                if (isBoarded) {
                    val (h, s, urg) = formatBoardedTransitPrompt(
                        mode = currentLeg.mode,
                        modeName = railTitle,
                        lineName = lineName,
                        destName = destName,
                        currentLeg = currentLeg,
                        realTimeStatus = realTimeStatus,
                        distanceToTargetMeters = distanceToTargetMeters,
                        isEs = isEs
                    )
                    headline = h
                    subheadline = s
                    computedUrgency = urg
                } else {
                    val (h, s) = formatWaitingTransitPrompt(railTitle, lineName, currentLeg, realTimeStatus, isEs)
                    headline = h
                    subheadline = s
                }
            }

            TransitMode.VALENBISI -> {
                icon = Icons.AutoMirrored.Filled.DirectionsWalk
                val mins = (currentLeg.durationSeconds / 60).coerceAtLeast(1)
                headline = "Valenbisi · $mins min"
                subheadline = if (distText != null) "${currentLeg.toName} · $distText" else currentLeg.toName
            }
        }

        return TripFormattedUIState(
            headline = headline,
            subheadline = subheadline,
            formattedArrivalTimeText = formattedArrivalTimeText,
            dynamicArrivalTime = dynamicArrivalTime,
            remainingMinutes = remainingTripMinutes,
            formattedRemainingDurationText = formattedRemainingDurationText,
            delayMinutes = delayMins,
            icon = icon,
            isLive = isLive,
            urgencyLevel = computedUrgency,
            lineBadge = lineBadge,
            walkBadgeMinutes = walkMinsBadge,
            distanceRemainingText = distText,
            targetStationName = currentLeg.toName,
            nextTransitDepartureInfo = nextTransitDepartureInfo,
            nextTransitIcon = nextTransitIcon
        )
    }

    private fun formatWaitingTransitPrompt(
        modeName: String,
        lineName: String,
        currentLeg: PlannedLeg,
        realTimeStatus: RealTimeTripStatus?,
        isEs: Boolean
    ): Pair<String, String> {
        val headline = if (isEs) "Sube a $modeName $lineName" else "Puja a $modeName $lineName"
        val headsign = currentLeg.headsign?.takeIf { it.isNotBlank() }
            ?: realTimeStatus?.vehicleDestination?.takeIf { it.isNotBlank() }
            ?: currentLeg.toName
        val dirPrefix = "Dir."
        val liveMins = getDynamicVehicleArrivalMinutes(realTimeStatus)
        val depTime = currentLeg.formattedStartTime

        val subheadline = if (realTimeStatus?.isLive == true && liveMins != null) {
            "$dirPrefix $headsign · en $liveMins min"
        } else if (depTime.isNotBlank()) {
            "$dirPrefix $headsign · $depTime"
        } else {
            "$dirPrefix $headsign"
        }
        return Pair(headline, subheadline)
    }

    private fun formatBoardedTransitPrompt(
        mode: TransitMode,
        modeName: String,
        lineName: String,
        destName: String,
        currentLeg: PlannedLeg,
        realTimeStatus: RealTimeTripStatus?,
        distanceToTargetMeters: Double?,
        isEs: Boolean
    ): Triple<String, String, TripUrgencyLevel> {
        val remainingMins = calculateBoardedRemainingMinutes(currentLeg, realTimeStatus)
        val progressInfo = ActiveTripProgressTracker.progressState.value
        val totalStopsInLeg = (currentLeg.intermediateStops.size + 1).coerceAtLeast(1)

        val remainingStops = progressInfo.remainingStopsCount ?: run {
            val passedStops = (progressInfo.progressWithinLeg * totalStopsInLeg).toInt().coerceIn(0, currentLeg.intermediateStops.size)
            (totalStopsInLeg - passedStops).coerceAtLeast(1)
        }

        val isArrived = (distanceToTargetMeters != null && distanceToTargetMeters <= 75.0) || remainingStops <= 0 || (remainingMins <= 0 && distanceToTargetMeters != null && distanceToTargetMeters <= 120.0)
        val isNearPenultimateOrTime = isNearPenultimateStopOrTime(
            currentLeg = currentLeg,
            remainingMins = remainingMins,
            distanceToTargetMeters = distanceToTargetMeters,
            progressWithinLeg = progressInfo.progressWithinLeg
        )

        return when {
            isArrived -> {
                val h = if (isEs) "Baja aquí" else "Baixa ací"
                val s = destName
                Triple(h, s, TripUrgencyLevel.CRITICAL)
            }
            remainingStops == 1 && isNearPenultimateOrTime -> {
                val nextStopWord = if (isEs) "Próxima parada" else "Pròxima parada"
                val h = "$modeName $lineName · $nextStopWord"
                val s = if (isEs) "Baja en $destName · $remainingMins min" else "Baixa en $destName · $remainingMins min"
                val urgency = if (remainingMins <= 2) TripUrgencyLevel.BRISK else TripUrgencyLevel.RELAXED
                Triple(h, s, urgency)
            }
            remainingStops == 1 -> {
                val inTransitWord = if (isEs) "En trayecto" else "En trajecte"
                val h = "$modeName $lineName · $inTransitWord"
                val s = if (isEs) "Baja en $destName · $remainingMins min" else "Baixa en $destName · $remainingMins min"
                Triple(h, s, TripUrgencyLevel.RELAXED)
            }
            else -> {
                val stopWord = if (isEs) "paradas" else "parades"
                val h = "$modeName $lineName · $remainingStops $stopWord"
                val s = if (isEs) "Baja en $destName · $remainingMins min" else "Baixa en $destName · $remainingMins min"
                Triple(h, s, TripUrgencyLevel.RELAXED)
            }
        }
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
        val realTimeEta = getDynamicVehicleArrivalMinutes(realTimeStatus) ?: realTimeStatus?.checkpointEtaMinutes
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

    /**
     * Calculates total dynamic remaining minutes for the entire trip, taking into account
     * live progress, real-time vehicle delays, dynamic walking speed, and downstream legs.
     */
    fun calculateTripRemainingMinutes(
        currentLeg: PlannedLeg?,
        currentLegIndex: Int,
        allLegs: List<PlannedLeg>,
        realTimeStatus: RealTimeTripStatus?,
        isBoarded: Boolean,
        distanceToTargetMeters: Double?
    ): Int {
        if (allLegs.isEmpty() || currentLeg == null) return 0

        // 1. Current Leg dynamic remaining minutes
        val currentLegRemainingMins: Int = when {
            currentLeg.mode == TransitMode.WALK || currentLeg.mode == TransitMode.BICYCLE -> {
                if (realTimeStatus?.dynamicWalkMinutesRemaining != null && realTimeStatus.dynamicWalkMinutesRemaining > 0) {
                    realTimeStatus.dynamicWalkMinutesRemaining
                } else if (distanceToTargetMeters != null && distanceToTargetMeters > 0) {
                    TripStepProgressionEngine.calculateDynamicWalkMinutes(distanceToTargetMeters, currentLeg)
                } else {
                    val progressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg.coerceIn(0f, 1f)
                    val totalMins = (currentLeg.durationSeconds / 60).coerceAtLeast(1).toInt()
                    (totalMins * (1f - progressFraction)).toInt().coerceAtLeast(1)
                }
            }
            isBoarded -> {
                calculateBoardedRemainingMinutes(currentLeg, realTimeStatus)
            }
            else -> {
                // Waiting for transit vehicle at platform / stop
                val waitMins = getDynamicVehicleArrivalMinutes(realTimeStatus)
                    ?: calculateTheoreticalMinutesRemaining(currentLeg.startTime)
                    ?: calculateTheoreticalMinutesRemaining(currentLeg.formattedStartTime)
                    ?: 0
                val travelMins = (currentLeg.durationSeconds / 60).coerceAtLeast(1).toInt()
                (waitMins.coerceAtLeast(0) + travelMins + (realTimeStatus?.delayMinutes ?: 0)).coerceAtLeast(1)
            }
        }

        // 2. Sum duration of all subsequent scheduled legs
        var futureLegsMins = 0
        if (currentLegIndex + 1 < allLegs.size) {
            for (i in (currentLegIndex + 1) until allLegs.size) {
                val leg = allLegs[i]
                val legMins = (leg.durationSeconds / 60).coerceAtLeast(1).toInt()
                futureLegsMins += legMins
            }
        }

        return (currentLegRemainingMins + futureLegsMins).coerceAtLeast(1)
    }

    /**
     * Formats remaining minutes into a human-glanceable string ("14 min", "1 h 10 min", "< 1 min").
     */
    fun formatDurationMinutes(minutes: Int): String {
        return when {
            minutes <= 0 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0) "$h h" else "$h h $m min"
            }
        }
    }

    /**
     * Calculates dynamic arrival time by projecting current timestamp + total remaining minutes.
     */
    fun calculateDynamicArrivalTime(
        scheduledArrivalTime: String,
        delayMinutes: Int,
        remainingMinutes: Int
    ): String {
        if (remainingMinutes > 0) {
            val nowMs = System.currentTimeMillis()
            val arrivalMs = nowMs + (remainingMinutes * 60_000L)
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(java.util.Date(arrivalMs))
        }
        return calculateAdjustedArrivalTime(scheduledArrivalTime, delayMinutes)
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
