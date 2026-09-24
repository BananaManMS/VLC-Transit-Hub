package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import kotlin.math.ceil

/**
 * Result data holder for multi-leg transfer monitoring evaluation.
 */
data class TransferEvaluationResult(
    val transferMarginMinutes: Int? = null,
    val isTransferAtRisk: Boolean = false,
    val transferWarningEs: String? = null,
    val transferWarningCa: String? = null,
    val upcomingTransferInfoEs: String? = null,
    val upcomingTransferInfoCa: String? = null,
    val isUpcomingTransferLive: Boolean = false,
    val upcomingTransferLine: String? = null,
    val upcomingTransferMinutes: Int? = null
)

/**
 * Result data holder for "Sal ya" walking countdown evaluation.
 */
data class LeaveNowEvaluationResult(
    val isLeaveNow: Boolean = false,
    val leaveInMinutes: Int? = null,
    val leaveNowEs: String? = null,
    val leaveNowCa: String? = null
)

/**
 * Domain evaluation logic extracted from TripRealTimeReconciler.
 * Handles:
 * - Multi-leg transfer risk slack calculations (M_enlace = T_transfer_real - T_user_arrival)
 * - "Sal ya" departure alert evaluation for walking segments to first transit stop
 */
object TripRealTimeTransferEvaluator {

    /**
     * Evaluates whether the next transfer in a multi-leg journey is at risk or comfortable.
     */
    fun evaluateTransferState(
        nextTransferTransitLeg: PlannedLeg,
        transferWalkMinutes: Int,
        currentLegRemainingMins: Int,
        nowMs: Long,
        userMinutesUntilTransferBoarding: Int,
        userTransferArrivalEpochMs: Long,
        transferLiveResult: LegReconciliationResult,
        delayMinutes: Int,
        isAlreadyBoarded: Boolean
    ): TransferEvaluationResult {
        val transferLineName = nextTransferTransitLeg.routeShortName?.ifBlank { null }
            ?: nextTransferTransitLeg.mode.displayNameEs

        if (transferLiveResult.isLive && transferLiveResult.liveMinutes != null) {
            val transferVehicleMinsFromNow = transferLiveResult.liveMinutes
            val margin = transferVehicleMinsFromNow - userMinutesUntilTransferBoarding

            val transferModeName = when (nextTransferTransitLeg.mode) {
                TransitMode.TRAM -> "Tranvía"
                TransitMode.SUBWAY -> "Metro"
                TransitMode.RAIL -> "Cercanías"
                TransitMode.BUS -> "Bus"
                else -> nextTransferTransitLeg.mode.displayNameEs
            }

            val transferModeNameCa = when (nextTransferTransitLeg.mode) {
                TransitMode.TRAM -> "Tramvia"
                TransitMode.SUBWAY -> "Metro"
                TransitMode.RAIL -> "Rodalia"
                TransitMode.BUS -> "Bus"
                else -> nextTransferTransitLeg.mode.displayNameCa
            }

            return if (margin < 0) {
                TransferEvaluationResult(
                    transferMarginMinutes = margin,
                    isTransferAtRisk = true,
                    transferWarningEs = "Posible transbordo perdido: Conexión con $transferModeName $transferLineName perdida por ${-margin} min.",
                    transferWarningCa = "Possible transbordament perdut: Connexió amb $transferModeNameCa $transferLineName perduda per ${-margin} min.",
                    upcomingTransferInfoEs = "Transbordo en riesgo: $transferModeName $transferLineName ($transferVehicleMinsFromNow min)",
                    upcomingTransferInfoCa = "Transbordament en risc: $transferModeNameCa $transferLineName ($transferVehicleMinsFromNow min)",
                    isUpcomingTransferLive = true,
                    upcomingTransferLine = transferLineName,
                    upcomingTransferMinutes = transferVehicleMinsFromNow
                )
            } else if (margin in 0..1) {
                TransferEvaluationResult(
                    transferMarginMinutes = margin,
                    isTransferAtRisk = true,
                    transferWarningEs = "Transbordo muy ajustado: Margen de conexión con $transferModeName $transferLineName de solo $margin min.",
                    transferWarningCa = "Transbordament molt ajustat: Marge de connexió amb $transferModeNameCa $transferLineName de només $margin min.",
                    upcomingTransferInfoEs = "Transbordo ajustado: $transferModeName $transferLineName en $transferVehicleMinsFromNow min (en vivo)",
                    upcomingTransferInfoCa = "Transbordament ajustat: $transferModeNameCa $transferLineName en $transferVehicleMinsFromNow min (en viu)",
                    isUpcomingTransferLive = true,
                    upcomingTransferLine = transferLineName,
                    upcomingTransferMinutes = transferVehicleMinsFromNow
                )
            } else {
                TransferEvaluationResult(
                    transferMarginMinutes = margin,
                    isTransferAtRisk = false,
                    upcomingTransferInfoEs = "Próximo transbordo: $transferModeName $transferLineName en $transferVehicleMinsFromNow min (en vivo)",
                    upcomingTransferInfoCa = "Pròxim transbordament: $transferModeNameCa $transferLineName en $transferVehicleMinsFromNow min (en viu)",
                    isUpcomingTransferLive = true,
                    upcomingTransferLine = transferLineName,
                    upcomingTransferMinutes = transferVehicleMinsFromNow
                )
            }
        } else {
            // Within 60 min but API returned scheduled arrival or no telemetry yet
            val scheduledTime = nextTransferTransitLeg.formattedStartTime
            val scheduledDepMs = TripTimeParser.parseTimeToMillis(nextTransferTransitLeg.startTime) ?: 0L

            val bufferMs = if (!isAlreadyBoarded && delayMinutes < 3) 5 * 60 * 1000L else 3 * 60 * 1000L

            val transferModeName = when (nextTransferTransitLeg.mode) {
                TransitMode.TRAM -> "Tranvía"
                TransitMode.SUBWAY -> "Metro"
                TransitMode.RAIL -> "Cercanías"
                TransitMode.BUS -> "Bus"
                else -> nextTransferTransitLeg.mode.displayNameEs
            }
            val transferModeNameCa = when (nextTransferTransitLeg.mode) {
                TransitMode.TRAM -> "Tramvia"
                TransitMode.SUBWAY -> "Metro"
                TransitMode.RAIL -> "Rodalia"
                TransitMode.BUS -> "Bus"
                else -> nextTransferTransitLeg.mode.displayNameCa
            }

            return if (scheduledDepMs > 0 && userTransferArrivalEpochMs > (scheduledDepMs + bufferMs) && userMinutesUntilTransferBoarding <= 5) {
                val causeEs = if (delayMinutes > 0) "Tu vehículo lleva retraso y" else "Según la hora estimada de llegada,"
                val causeCa = if (delayMinutes > 0) "El teu transport porta retràs i" else "Segons l'hora estimada d'arribada,"
                TransferEvaluationResult(
                    isTransferAtRisk = true,
                    transferWarningEs = "Posible transbordo perdido: $causeEs la salida programada de $transferModeName $transferLineName ($scheduledTime) es inalcanzable.",
                    transferWarningCa = "Possible transbordament perdut: $causeCa la eixida programada de $transferModeNameCa $transferLineName ($scheduledTime) és inabastable.",
                    upcomingTransferInfoEs = "Transbordo en riesgo: $transferModeName $transferLineName a las $scheduledTime",
                    upcomingTransferInfoCa = "Transbordament en risc: $transferModeNameCa $transferLineName a les $scheduledTime",
                    isUpcomingTransferLive = false,
                    upcomingTransferLine = transferLineName
                )
            } else if (scheduledDepMs > 0 && userTransferArrivalEpochMs > scheduledDepMs) {
                TransferEvaluationResult(
                    isTransferAtRisk = false,
                    transferWarningEs = "Transbordo ajustado: Conexión con $transferModeName $transferLineName a las $scheduledTime muy justa.",
                    transferWarningCa = "Transbordament ajustat: Connexió amb $transferModeNameCa $transferLineName a les $scheduledTime molt justa.",
                    upcomingTransferInfoEs = "Transbordo ajustado: $transferModeName $transferLineName a las $scheduledTime",
                    upcomingTransferInfoCa = "Transbordament ajustat: $transferModeNameCa $transferLineName a les $scheduledTime",
                    isUpcomingTransferLive = false,
                    upcomingTransferLine = transferLineName
                )
            } else {
                TransferEvaluationResult(
                    isTransferAtRisk = false,
                    upcomingTransferInfoEs = "Próximo transbordo: $transferLineName a las $scheduledTime • Programado",
                    upcomingTransferInfoCa = "Pròxim transbordament: $transferLineName a las $scheduledTime • Programat",
                    isUpcomingTransferLive = false,
                    upcomingTransferLine = transferLineName
                )
            }
        }
    }

    /**
     * Evaluates "Sal ya" and "Sal en X min" walking departure alerts.
     */
    fun evaluateLeaveNow(
        currentLeg: PlannedLeg,
        nextTransitLeg: PlannedLeg,
        userLat: Double?,
        userLon: Double?,
        liveMinutes: Int?,
        normalizedLine: String
    ): LeaveNowEvaluationResult {
        val legProgressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg
        val originCoords = TripStepProgressionEngine.getLegOriginCoordinates(currentLeg)
        val distFromStartMeters = if (userLat != null && userLon != null && originCoords != null) {
            LocationUtils.calculateDistanceMeters(userLat, userLon, originCoords.first, originCoords.second)
        } else 0.0

        // If user has already progressed >12% or moved >40m away from start point, they are ALREADY WALKING
        val isUserAlreadyWalking = legProgressFraction > 0.12f || distFromStartMeters > 40.0
        if (isUserAlreadyWalking) {
            return LeaveNowEvaluationResult()
        }

        val plannedWalkMinutes = (currentLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
        val walkMinutesRemaining: Int = if (userLat != null && userLon != null) {
            val targetCoords = TripStepProgressionEngine.getLegTargetCoordinates(currentLeg)
                ?: TripStepProgressionEngine.getLegOriginCoordinates(nextTransitLeg)
            val totalLegDist = currentLeg.distanceMeters.takeIf { it > 0 } ?: 1.0
            val remainingDist = if (targetCoords != null) {
                LocationUtils.calculateDistanceMeters(userLat, userLon, targetCoords.first, targetCoords.second)
            } else totalLegDist
            val fraction = (remainingDist / totalLegDist).coerceIn(0.1, 1.0)
            ceil(plannedWalkMinutes * fraction).toInt().coerceAtLeast(1)
        } else {
            plannedWalkMinutes
        }

        val effectiveTransitMinutes: Int? = if (liveMinutes != null) {
            liveMinutes
        } else {
            val schedMs = TripTimeParser.parseTimeToMillis(nextTransitLeg.startTime)
                ?: TripTimeParser.parseTimeToMillis(nextTransitLeg.formattedStartTime)
            if (schedMs != null) {
                val diff = ((schedMs - System.currentTimeMillis()) / 60_000L).toInt()
                if (diff in 0..180) diff else null
            } else null
        }

        if (effectiveTransitMinutes != null) {
            val marginMinutes = effectiveTransitMinutes - walkMinutesRemaining
            return if (marginMinutes <= 1) {
                LeaveNowEvaluationResult(
                    isLeaveNow = true,
                    leaveInMinutes = 0,
                    leaveNowEs = "Sal ya hacia ${currentLeg.toName}",
                    leaveNowCa = "Ix ja cap a ${currentLeg.toName}"
                )
            } else {
                LeaveNowEvaluationResult(
                    isLeaveNow = false,
                    leaveInMinutes = marginMinutes
                )
            }
        }

        return LeaveNowEvaluationResult()
    }

    data class TransferPair(
        val walkTransferLeg: PlannedLeg?,
        val nextTransferTransitLeg: PlannedLeg?
    )

    fun findNextTransferPair(legs: List<PlannedLeg>, targetTransitLeg: PlannedLeg): TransferPair {
        val targetTransitLegIndex = legs.indexOf(targetTransitLeg)
        var nextTransferTransitLeg: PlannedLeg? = null
        var walkTransferLeg: PlannedLeg? = null

        if (targetTransitLegIndex != -1 && targetTransitLegIndex + 1 < legs.size) {
            for (j in (targetTransitLegIndex + 1) until legs.size) {
                val candidate = legs[j]
                if (candidate.mode == TransitMode.WALK) {
                    if (walkTransferLeg == null) walkTransferLeg = candidate
                } else {
                    nextTransferTransitLeg = candidate
                    break
                }
            }
        }
        return TransferPair(walkTransferLeg, nextTransferTransitLeg)
    }
}
