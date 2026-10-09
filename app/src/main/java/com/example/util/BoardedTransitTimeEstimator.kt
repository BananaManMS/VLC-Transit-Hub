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

        val legProgressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg.coerceIn(0f, 1f)
        val totalMins = (nextTransitLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
        val rawGpsRemainingMins = (totalMins * (1f - legProgressFraction)).toInt().coerceAtLeast(1)
        val progressBasedMins = (rawGpsRemainingMins + delayMinutes.coerceAtLeast(0)).coerceAtLeast(1)

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
            // Prioritize exact retained tripId if captured at origin, otherwise match closest to expected schedule
            val liveTripUpdate = if (!retainedTrip.isNullOrBlank()) {
                tripUpdates[retainedTrip]
            } else {
                val matchingUpdates = tripUpdates.values.filter { update ->
                    val lineMatches = cleanLine.isNotBlank() && update.tripId.replace("-", "").uppercase().contains(cleanLine)
                    val stopMatches = !toStopDigits.isNullOrBlank() && (update.stopDelays.containsKey(toStopDigits) || update.stopEstimatedTimes.containsKey(toStopDigits))
                    lineMatches || stopMatches
                }
                matchingUpdates.minByOrNull { update ->
                    val stopEpoch = if (!toStopDigits.isNullOrBlank()) update.stopEstimatedTimes[toStopDigits] else null
                    if (stopEpoch != null && stopEpoch > 0) {
                        val remainingSec = ((stopEpoch * 1000L) - nowMs) / 1000L
                        val mins = (remainingSec / 60L).toInt()
                        kotlin.math.abs(mins - progressBasedMins)
                    } else {
                        Int.MAX_VALUE
                    }
                } ?: matchingUpdates.firstOrNull()
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
                if (kotlin.math.abs(mins - progressBasedMins) <= 8) {
                    operatorLiveArrivalMinutes = mins
                    isLive = true
                    delayMinutes = retainedDelay
                }
            }
        } else if (nextTransitLeg.mode in listOf(TransitMode.SUBWAY, TransitMode.TRAM)) {
            val normalizedLine = TransitIdMapper.normalizeRouteShortName(nextTransitLeg.mode, nextTransitLeg.routeShortName)
            // Once boarded, lock strictly to the boarded line; never switch to alternative lines or train departures
            val allowedLines = setOfNotNull(
                normalizedLine.ifBlank { null },
                nextTransitLeg.routeShortName?.ifBlank { null },
                if (normalizedLine.startsWith("L", ignoreCase = true)) normalizedLine.drop(1) else null,
                if (!normalizedLine.startsWith("L", ignoreCase = true) && normalizedLine.isNotBlank()) "L$normalizedLine" else null
            )

            val intermediateStops = nextTransitLeg.intermediateStops
            val penultimateStop = intermediateStops.lastOrNull()
            
            // Calculate scheduled minutes between penultimate stop and final destination according to Transitous
            val minsPenultToDest = if (penultimateStop != null) {
                val penultScheduled = penultimateStop.scheduledTime ?: penultimateStop.formattedTime
                val destScheduled = nextTransitLeg.scheduledEndTime ?: nextTransitLeg.formattedEndTime
                val penultMs = TripTimeParser.parseTimeToMillis(penultScheduled)
                val destMs = TripTimeParser.parseTimeToMillis(destScheduled)
                if (penultMs != null && destMs != null && destMs >= penultMs) {
                    ((destMs - penultMs) / 60000L).toInt().coerceIn(1, 8)
                } else {
                    (totalMins / (intermediateStops.size + 1)).coerceIn(1, 4)
                }
            } else {
                totalMins
            }

            // Expected remaining minutes until reaching penultimate stop
            val expectedMinsToPenult = (progressBasedMins - minsPenultToDest).coerceAtLeast(0)

            // Dynamic Forward Radar: Only switch away from penultimate stop if user has physically passed it
            // or if the remaining expected minutes to penultimate stop is <= 0 (train already passed penultimate stop).
            // Do NOT cut off early at 70%, 75% or 80%! Stay connected to penultimate stop until vehicle departs/passes it!
            val shouldQueryDestination = penultimateStop == null || expectedMinsToPenult <= 0 || legProgressFraction >= 0.98f

            val checkStationId = if (!shouldQueryDestination && penultimateStop != null) {
                TransitIdMapper.extractMetroStationId(penultimateStop.stopId, penultimateStop.name)?.toString()
            } else {
                TransitIdMapper.extractMetroStationId(nextTransitLeg.toStopId, nextTransitLeg.toName)?.toString()
            }

            val targetExpected = if (!shouldQueryDestination && penultimateStop != null) expectedMinsToPenult else progressBasedMins
            val addedMinsAfterStop = if (!shouldQueryDestination && penultimateStop != null) minsPenultToDest else 0

            if (!checkStationId.isNullOrBlank()) {
                val arrivals = TransitOperatorArrivalProvider.fetchMetroDepartures(checkStationId)
                val matchingDepartures = arrivals.filter { dep ->
                    val depDigits = dep.line.filter { it.isDigit() }
                    val lineMatch = allowedLines.any { allowed ->
                        val allowedDigits = allowed.filter { it.isDigit() }
                        dep.line.equals(allowed, ignoreCase = true) ||
                                (depDigits.isNotBlank() && depDigits == allowedDigits)
                    }
                    lineMatch && TripVehicleMatcher.isDestinationMatch(dep.destination, nextTransitLeg, dep.line)
                }

                // Match the realistic vehicle closest to our expected scheduled arrival at target station (tolerance: max 4 min)
                val realisticMatch = matchingDepartures
                    .filter { dep ->
                        val diff = kotlin.math.abs(dep.minutes - targetExpected)
                        diff <= 4
                    }
                    .minByOrNull { dep -> kotlin.math.abs(dep.minutes - targetExpected) }

                if (realisticMatch != null) {
                    val finalEstimatedMins = (realisticMatch.minutes + addedMinsAfterStop).coerceAtLeast(1)
                    operatorLiveArrivalMinutes = finalEstimatedMins
                    isLive = realisticMatch.isRealTime
                    val scheduledTimeStr = nextTransitLeg.scheduledEndTime?.ifBlank { null }
                        ?: nextTransitLeg.formattedEndTime.ifBlank { null }
                    val schedRemaining = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(scheduledTimeStr)
                        ?: SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
                    if (schedRemaining != null) {
                        val rawDelay = finalEstimatedMins - schedRemaining
                        val sanitizedDelay = if (rawDelay > 30 && (nextTransitLeg.mode == TransitMode.SUBWAY || nextTransitLeg.mode == TransitMode.TRAM)) {
                            if (rawDelay in 110..135) {
                                (rawDelay - 120).coerceAtLeast(0) // Strip UTC/CEST 120 min timezone artifact
                            } else if (rawDelay in 50..75) {
                                (rawDelay - 60).coerceAtLeast(0) // Strip UTC/CET 60 min timezone artifact
                            } else {
                                rawDelay.coerceIn(0, 30) // Cap realistic metro delay to 30 min
                            }
                        } else {
                            rawDelay.coerceAtLeast(0)
                        }
                        delayMinutes = sanitizedDelay
                    }
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
                val intermediateStops = nextTransitLeg.intermediateStops
                val penultimateStop = intermediateStops.lastOrNull()
                val minsPenultToDest = if (penultimateStop != null) {
                    (totalMins / (intermediateStops.size + 1)).coerceIn(1, 6)
                } else {
                    totalMins
                }
                val expectedMinsToPenult = (progressBasedMins - minsPenultToDest).coerceAtLeast(0)

                // Dynamic Forward Radar for EMT Bus - remain locked on penultimate stop until vehicle departs it
                val shouldQueryDestination = penultimateStop == null || expectedMinsToPenult <= 0 || legProgressFraction >= 0.98f

                val checkStopNum = if (!shouldQueryDestination && penultimateStop != null) {
                    TransitIdMapper.extractEmtStopNumber(penultimateStop.stopId, penultimateStop.name)
                } else {
                    TransitIdMapper.extractEmtStopNumber(nextTransitLeg.toStopId, nextTransitLeg.toName)
                }

                val targetExpected = if (!shouldQueryDestination && penultimateStop != null) expectedMinsToPenult else progressBasedMins
                val addedMinsAfterStop = if (!shouldQueryDestination && penultimateStop != null) minsPenultToDest else 0

                if (checkStopNum != null) {
                    val rawLine = nextTransitLeg.routeShortName ?: ""
                    val normalizedLine = TransitIdMapper.normalizeRouteShortName(TransitMode.BUS, rawLine)
                    val arrivals = TransitOperatorArrivalProvider.fetchEmtArrivals(checkStopNum)
                    val matching = arrivals.filter { arr ->
                        (TransitIdMapper.isSameEmtLine(arr.line, rawLine) || TransitIdMapper.isSameEmtLine(arr.line, normalizedLine)) &&
                                TripVehicleMatcher.isDestinationMatch(arr.destination, nextTransitLeg)
                    }

                    val realisticMatch = matching
                        .filter { arr ->
                            val diff = kotlin.math.abs(arr.minutes - targetExpected)
                            diff <= 4
                        }
                        .minByOrNull { arr -> kotlin.math.abs(arr.minutes - targetExpected) }

                    if (realisticMatch != null) {
                        val finalEstimatedMins = (realisticMatch.minutes + addedMinsAfterStop).coerceAtLeast(1)
                        operatorLiveArrivalMinutes = finalEstimatedMins
                        isLive = realisticMatch.isRealTime
                        val scheduledTimeStr = nextTransitLeg.scheduledEndTime?.ifBlank { null }
                            ?: nextTransitLeg.formattedEndTime.ifBlank { null }
                        val schedRemaining = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(scheduledTimeStr)
                            ?: SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
                        if (schedRemaining != null) {
                            val rawDelay = finalEstimatedMins - schedRemaining
                            val sanitizedDelay = if (rawDelay > 60) {
                                if (rawDelay in 110..135) (rawDelay - 120).coerceAtLeast(0)
                                else if (rawDelay in 50..75) (rawDelay - 60).coerceAtLeast(0)
                                else rawDelay.coerceIn(0, 45)
                            } else {
                                rawDelay.coerceAtLeast(0)
                            }
                            delayMinutes = sanitizedDelay
                        }
                    }
                }
            }
        }

        val rawEstimatedMinutes = if (operatorLiveArrivalMinutes != null) {
            operatorLiveArrivalMinutes
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
