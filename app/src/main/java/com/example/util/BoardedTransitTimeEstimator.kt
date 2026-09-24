package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.RealTimeTransitRepository
import com.example.data.repository.routing.TransitIdMapper

/**
 * Encapsulates calculation of arrival ETA and delay when the user is already on board a transit vehicle.
 * Handles Renfe Cercanías GTFS-RT prioritization, monotonic time decay, and anti-jump filtering.
 */
object BoardedTransitTimeEstimator {

    data class BoardedEstimationResult(
        val liveMinutes: Int,
        val liveSeconds: Int,
        val delayMinutes: Int,
        val isLive: Boolean,
        val retainedDestEpochSec: Long?,
        val retainedDelayMinutes: Int,
        val retainedTripId: String?,
        val confirmedBoardedMinsRemaining: Int
    )

    suspend fun estimateBoardedLeg(
        nextTransitLeg: PlannedLeg,
        nowMs: Long,
        driftMinutes: Int,
        isLiveFromCorridor: Boolean,
        lastKnownCercaniasDestEpochSec: Long?,
        lastKnownCercaniasDelayMinutes: Int,
        lastConfirmedBoardedMinsRemaining: Int?,
        lastBoardedEstimationTimestamp: Long
    ): BoardedEstimationResult {
        var isLive = isLiveFromCorridor
        var delayMinutes = driftMinutes

        var retainedEpoch = lastKnownCercaniasDestEpochSec
        var retainedDelay = lastKnownCercaniasDelayMinutes
        var retainedTrip: String? = null

        var gtfsRtArrivalMinutes: Int? = null

        if (nextTransitLeg.mode == TransitMode.RAIL) {
            val toStopDigits = nextTransitLeg.toStopId?.filter { it.isDigit() }
            val cercaniasLine = TransitIdMapper.extractCercaniasLine(
                nextTransitLeg.routeShortName,
                nextTransitLeg.routeLongName,
                nextTransitLeg.agencyName,
                nextTransitLeg.mode
            ) ?: nextTransitLeg.routeShortName ?: ""
            val cleanLine = cercaniasLine.replace("-", "").uppercase()

            val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
            val liveTripUpdate = tripUpdates.values.firstOrNull { update ->
                val lineMatches = cleanLine.isNotBlank() && update.tripId.replace("-", "").uppercase().contains(cleanLine)
                val stopMatches = !toStopDigits.isNullOrBlank() && (update.stopDelays.containsKey(toStopDigits) || update.stopEstimatedTimes.containsKey(toStopDigits))
                lineMatches || stopMatches
            }

            if (liveTripUpdate != null) {
                retainedTrip = liveTripUpdate.tripId
                val stopEpoch = if (!toStopDigits.isNullOrBlank()) liveTripUpdate.stopEstimatedTimes[toStopDigits] else null
                if (stopEpoch != null && stopEpoch > 0) {
                    retainedEpoch = stopEpoch
                }
                val stopDelaySec = if (!toStopDigits.isNullOrBlank()) {
                    liveTripUpdate.stopDelays[toStopDigits] ?: liveTripUpdate.delaySeconds
                } else {
                    liveTripUpdate.delaySeconds
                }
                retainedDelay = (stopDelaySec / 60).toInt()
                isLive = true
                delayMinutes = retainedDelay
            }

            val epochToUse = retainedEpoch
            if (epochToUse != null && epochToUse > 0) {
                val remainingSec = ((epochToUse * 1000L) - nowMs) / 1000L
                val mins = (remainingSec / 60L).toInt().coerceAtLeast(0)
                gtfsRtArrivalMinutes = mins
                isLive = true
                delayMinutes = retainedDelay
            }
        }

        val endMinsTheoretical = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
            ?: SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.formattedEndTime)
        val legProgressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg.coerceIn(0f, 1f)
        val totalMins = (nextTransitLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
        val rawGpsRemainingMins = (totalMins * (1f - legProgressFraction)).toInt().coerceAtLeast(1)
        val progressBasedMins = (rawGpsRemainingMins + delayMinutes.coerceAtLeast(0)).coerceAtLeast(1)

        val rawEstimatedMinutes = if (gtfsRtArrivalMinutes != null) {
            gtfsRtArrivalMinutes
        } else if (legProgressFraction >= 0.70f || rawGpsRemainingMins <= 3) {
            rawGpsRemainingMins
        } else if (endMinsTheoretical != null) {
            val theoreticalWithDelay = (endMinsTheoretical + delayMinutes).coerceAtLeast(1)
            val discrepancy = kotlin.math.abs(theoreticalWithDelay - progressBasedMins)
            if (discrepancy <= 3) theoreticalWithDelay else progressBasedMins
        } else {
            progressBasedMins
        }

        val calculatedMinutes = if (lastConfirmedBoardedMinsRemaining != null && lastBoardedEstimationTimestamp > 0L) {
            val elapsedSeconds = ((nowMs - lastBoardedEstimationTimestamp) / 1000L).coerceAtLeast(0L)
            val elapsedMinutes = (elapsedSeconds / 60L).toInt()
            val decayedPreviousMins = (lastConfirmedBoardedMinsRemaining - elapsedMinutes).coerceAtLeast(1)

            if (rawEstimatedMinutes > decayedPreviousMins + 2) {
                decayedPreviousMins
            } else {
                rawEstimatedMinutes
            }
        } else {
            rawEstimatedMinutes
        }

        return BoardedEstimationResult(
            liveMinutes = calculatedMinutes,
            liveSeconds = calculatedMinutes * 60,
            delayMinutes = delayMinutes,
            isLive = isLive,
            retainedDestEpochSec = retainedEpoch,
            retainedDelayMinutes = retainedDelay,
            retainedTripId = retainedTrip,
            confirmedBoardedMinsRemaining = calculatedMinutes
        )
    }
}
