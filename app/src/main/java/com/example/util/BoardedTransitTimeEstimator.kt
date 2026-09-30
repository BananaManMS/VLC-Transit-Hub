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
        var operatorLiveArrivalMinutes: Int? = null

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
                operatorLiveArrivalMinutes = mins
                isLive = true
                delayMinutes = retainedDelay
            }
        } else if (nextTransitLeg.mode in listOf(TransitMode.SUBWAY, TransitMode.TRAM)) {
            val normalizedLine = TransitIdMapper.normalizeRouteShortName(nextTransitLeg.mode, nextTransitLeg.routeShortName)
            val allowedLines = TransitIdMapper.getAlternativeTransitLines(
                mode = nextTransitLeg.mode,
                originalLine = normalizedLine,
                fromName = nextTransitLeg.fromName,
                toName = nextTransitLeg.toName
            )
            val destStationId = TransitIdMapper.extractMetroStationId(nextTransitLeg.toStopId, nextTransitLeg.toName)?.toString()

            var matchedMins: Int? = null

            // 1. Check destination station departures (if not a terminal/cabecera or if active departures found)
            if (!destStationId.isNullOrBlank()) {
                val destDepartures = TransitOperatorArrivalProvider.fetchMetroDepartures(destStationId)
                val destMatches = destDepartures.filter { dep ->
                    val depDigits = dep.line.filter { it.isDigit() }
                    val lineMatch = allowedLines.any { allowed ->
                        val allowedDigits = allowed.filter { it.isDigit() }
                        dep.line.equals(allowed, ignoreCase = true) ||
                                (depDigits.isNotBlank() && depDigits == allowedDigits)
                    }
                    lineMatch && TripVehicleMatcher.isDestinationMatch(dep.destination, nextTransitLeg, dep.line)
                }
                if (destMatches.isNotEmpty()) {
                    val best = destMatches.minByOrNull { it.seconds }
                    if (best != null && best.seconds >= 0) {
                        matchedMins = best.minutes
                        isLive = best.isRealTime
                    }
                }
            }

            // 2. If destination is a cabecera/terminal (or returned 0 incoming departures), inspect penultimate stop or Alameda!
            if (matchedMins == null) {
                val intermediateStops = nextTransitLeg.intermediateStops
                val penultimateStop = intermediateStops.lastOrNull()
                val alamedaStop = intermediateStops.find { it.name.contains("Alameda", ignoreCase = true) }
                val targetStopToCheck = alamedaStop ?: penultimateStop

                val checkStationId = targetStopToCheck?.let {
                    TransitIdMapper.extractMetroStationId(it.stopId, it.name)?.toString()
                }

                if (!checkStationId.isNullOrBlank()) {
                    val penultDepartures = TransitOperatorArrivalProvider.fetchMetroDepartures(checkStationId)
                    val penultMatches = penultDepartures.filter { dep ->
                        val depDigits = dep.line.filter { it.isDigit() }
                        val lineMatch = allowedLines.any { allowed ->
                            val allowedDigits = allowed.filter { it.isDigit() }
                            dep.line.equals(allowed, ignoreCase = true) ||
                                    (depDigits.isNotBlank() && depDigits == allowedDigits)
                        }
                        lineMatch && TripVehicleMatcher.isDestinationMatch(dep.destination, nextTransitLeg, dep.line)
                    }

                    val bestPenult = penultMatches.minByOrNull { it.seconds }
                    if (bestPenult != null && bestPenult.seconds >= 0) {
                        val penultScheduled = targetStopToCheck.scheduledTime ?: targetStopToCheck.formattedTime
                        val destScheduled = nextTransitLeg.scheduledEndTime ?: nextTransitLeg.formattedEndTime
                        val penultMs = TripTimeParser.parseTimeToMillis(penultScheduled)
                        val destMs = TripTimeParser.parseTimeToMillis(destScheduled)
                        val deltaMins = if (penultMs != null && destMs != null && destMs >= penultMs) {
                            ((destMs - penultMs) / 60000L).toInt().coerceIn(1, 8)
                        } else {
                            val remainingStopsFromCheck = if (targetStopToCheck == alamedaStop && alamedaStop != penultimateStop) {
                                val idx = intermediateStops.indexOf(alamedaStop)
                                (intermediateStops.size - idx).coerceAtLeast(1)
                            } else 1
                            (remainingStopsFromCheck * 2).coerceIn(1, 8)
                        }
                        matchedMins = (bestPenult.minutes + deltaMins).coerceAtLeast(1)
                        isLive = bestPenult.isRealTime
                    }
                }
            }

            if (matchedMins != null) {
                operatorLiveArrivalMinutes = matchedMins
                val schedRemaining = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
                    ?: SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.formattedEndTime)
                if (schedRemaining != null) {
                    delayMinutes = (matchedMins - schedRemaining).coerceAtLeast(0)
                }
            }
        } else if (nextTransitLeg.mode == TransitMode.BUS) {
            val isEmt = TransitIdMapper.isEmtBus(
                agencyName = nextTransitLeg.agencyName,
                routeShortName = nextTransitLeg.routeShortName,
                routeLongName = nextTransitLeg.routeLongName,
                fromStopId = nextTransitLeg.fromStopId,
                fromName = nextTransitLeg.fromName
            )
            if (isEmt) {
                val destStopNum = TransitIdMapper.extractEmtStopNumber(nextTransitLeg.toStopId, nextTransitLeg.toName)
                if (destStopNum != null) {
                    val rawLine = nextTransitLeg.routeShortName ?: ""
                    val normalizedLine = TransitIdMapper.normalizeRouteShortName(TransitMode.BUS, rawLine)
                    val arrivals = TransitOperatorArrivalProvider.fetchEmtArrivals(destStopNum)
                    val matching = arrivals.filter { arr ->
                        (TransitIdMapper.isSameEmtLine(arr.line, rawLine) || TransitIdMapper.isSameEmtLine(arr.line, normalizedLine)) &&
                                TripVehicleMatcher.isDestinationMatch(arr.destination, nextTransitLeg)
                    }
                    val best = matching.minByOrNull { it.seconds }
                    if (best != null && best.minutes >= 0) {
                        operatorLiveArrivalMinutes = best.minutes
                        isLive = best.isRealTime
                        val schedRemaining = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
                            ?: SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.formattedEndTime)
                        if (schedRemaining != null) {
                            delayMinutes = (best.minutes - schedRemaining).coerceAtLeast(0)
                        }
                    }
                }
            }
        }

        val endMinsTheoretical = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
            ?: SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.formattedEndTime)
        val legProgressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg.coerceIn(0f, 1f)
        val totalMins = (nextTransitLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
        val rawGpsRemainingMins = (totalMins * (1f - legProgressFraction)).toInt().coerceAtLeast(1)
        val progressBasedMins = (rawGpsRemainingMins + delayMinutes.coerceAtLeast(0)).coerceAtLeast(1)

        val rawEstimatedMinutes = if (operatorLiveArrivalMinutes != null) {
            operatorLiveArrivalMinutes
        } else if (legProgressFraction >= 0.70f || rawGpsRemainingMins <= 3) {
            rawGpsRemainingMins
        } else {
            progressBasedMins
        }

        val calculatedMinutes = if (lastConfirmedBoardedMinsRemaining != null && lastBoardedEstimationTimestamp > 0L) {
            val elapsedSeconds = ((nowMs - lastBoardedEstimationTimestamp) / 1000L).coerceAtLeast(0L)
            val elapsedMinutes = (elapsedSeconds / 60L).toInt()
            val decayedPreviousMins = (lastConfirmedBoardedMinsRemaining - elapsedMinutes).coerceAtLeast(1)

            if (operatorLiveArrivalMinutes != null) {
                operatorLiveArrivalMinutes
            } else if (rawEstimatedMinutes > decayedPreviousMins + 2) {
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
