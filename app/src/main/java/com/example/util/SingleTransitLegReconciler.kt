package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.RealTimeTransitRepository
import com.example.data.repository.routing.TransitIdMapper
import java.util.Calendar
import java.util.Locale

/**
 * Executes operator-specific real-time arrival reconciliation for single transit legs.
 */
object SingleTransitLegReconciler {

    suspend fun reconcile(
        leg: PlannedLeg,
        nowMs: Long,
        earliestReachableMs: Long,
        isCurrentWalk: Boolean,
        isBoarded: Boolean,
        lastMatchedOriginVehicleKey: String?,
        onMatchedOrigin: (key: String, vehicleId: String?, arrivalEpochMs: Long) -> Unit
    ): LegReconciliationResult {
        val normalizedLine = TransitIdMapper.normalizeRouteShortName(leg.mode, leg.routeShortName)
        val rawLine = leg.routeShortName ?: normalizedLine

        var liveMinutes: Int? = null
        var liveSeconds: Int? = null
        var liveDestination: String? = null
        var isLive = false
        var delayMinutes = 0
        var adjustedDepTime: String? = null
        var matchedLineShortName: String? = null
        var matchedDestination: String? = null

        val theoreticalMinutesRemaining = calculateTheoreticalMinutesRemaining(leg.startTime)
            ?: calculateTheoreticalMinutesRemaining(leg.formattedStartTime)

        when (leg.mode) {
            TransitMode.BUS -> {
                val isEmt = TransitIdMapper.isEmtBus(
                    agencyName = leg.agencyName,
                    routeShortName = leg.routeShortName,
                    routeLongName = leg.routeLongName,
                    fromStopId = leg.fromStopId,
                    fromName = leg.fromName
                )
                if (isEmt) {
                    val stopNumber = TransitIdMapper.extractEmtStopNumber(leg.fromStopId, leg.fromName)
                    if (stopNumber != null) {
                        val arrivals = TransitOperatorArrivalProvider.fetchEmtArrivals(stopNumber.toString())
                        val matchingArrivals = arrivals.filter { arr ->
                            TransitIdMapper.isSameEmtLine(arr.line, rawLine) ||
                            TransitIdMapper.isSameEmtLine(arr.line, normalizedLine)
                        }

                        val destMatches = matchingArrivals.filter { TripVehicleMatcher.isDestinationMatch(it.destination, leg) }
                        val bestArrivalCandidate = TripVehicleMatcher.matchBestCandidate(
                            candidates = destMatches,
                            nowMs = nowMs,
                            earliestReachableMs = earliestReachableMs,
                            isCurrentWalk = isCurrentWalk,
                            isBoarded = isBoarded,
                            theoreticalMinutesRemaining = theoreticalMinutesRemaining,
                            lastMatchedOriginVehicleKey = lastMatchedOriginVehicleKey,
                            vehicleKeyPrefix = "EMT"
                        )

                        if (bestArrivalCandidate != null) {
                            val (targetArrival, mins, delayM) = bestArrivalCandidate
                            liveMinutes = mins
                            liveSeconds = mins * 60
                            liveDestination = targetArrival.destination
                            isLive = targetArrival.isRealTime
                            delayMinutes = if (targetArrival.isRealTime) delayM else 0
                            matchedLineShortName = targetArrival.line
                            matchedDestination = targetArrival.destination

                            val key = "EMT_${stopNumber}_${targetArrival.line}_${targetArrival.destination}"
                            val epochMs = nowMs + (mins * 60 * 1000L)
                            onMatchedOrigin(key, targetArrival.vehicleId, epochMs)
                        }
                    }
                }
            }
            TransitMode.SUBWAY, TransitMode.TRAM -> {
                val stationIdInt = TransitIdMapper.extractMetroStationId(leg.fromStopId, leg.fromName)
                val stationId = stationIdInt?.toString() ?: leg.fromStopId?.filter { it.isDigit() }

                if (!stationId.isNullOrBlank()) {
                    val departures = TransitOperatorArrivalProvider.fetchMetroDepartures(stationId)
                    val allowedLines = TransitIdMapper.getAlternativeTransitLines(
                        mode = leg.mode,
                        originalLine = normalizedLine,
                        fromName = leg.fromName,
                        toName = leg.toName
                    )

                    val matchingDepartures = departures.filter { dep ->
                        val depDigits = dep.line.filter { it.isDigit() }
                        allowedLines.any { allowed ->
                            val allowedDigits = allowed.filter { it.isDigit() }
                            dep.line.equals(allowed, ignoreCase = true) ||
                            (depDigits.isNotBlank() && depDigits == allowedDigits)
                        }
                    }

                    val destMatches = matchingDepartures.filter { TripVehicleMatcher.isDestinationMatch(it.destination, leg) }
                    val bestDepCandidate = TripVehicleMatcher.matchBestCandidate(
                        candidates = destMatches,
                        nowMs = nowMs,
                        earliestReachableMs = earliestReachableMs,
                        isCurrentWalk = isCurrentWalk,
                        isBoarded = isBoarded,
                        theoreticalMinutesRemaining = theoreticalMinutesRemaining,
                        lastMatchedOriginVehicleKey = lastMatchedOriginVehicleKey,
                        vehicleKeyPrefix = "METRO"
                    )

                    if (bestDepCandidate != null) {
                        val (targetDeparture, mins, delayM) = bestDepCandidate
                        liveMinutes = mins
                        liveSeconds = mins * 60
                        liveDestination = targetDeparture.destination
                        isLive = targetDeparture.isRealTime
                        delayMinutes = if (targetDeparture.isRealTime) delayM else 0
                        matchedLineShortName = if (targetDeparture.line.startsWith("L", ignoreCase = true)) targetDeparture.line else "L${targetDeparture.line}"
                        matchedDestination = targetDeparture.destination

                        val key = "METRO_${stationId}_${targetDeparture.line}_${targetDeparture.destination}"
                        val epochMs = nowMs + (mins * 60 * 1000L)
                        onMatchedOrigin(key, targetDeparture.vehicleId, epochMs)
                    }
                }
            }
            TransitMode.RAIL -> {
                val cercaniasLine = TransitIdMapper.extractCercaniasLine(leg.routeShortName, leg.routeLongName, leg.agencyName, leg.mode)
                    ?: leg.routeShortName ?: ""

                val allowedLines = TransitIdMapper.getAlternativeTransitLines(
                    mode = leg.mode,
                    originalLine = cercaniasLine,
                    fromName = leg.fromName,
                    toName = leg.toName
                )

                val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                val livePositions = RealTimeTransitRepository.getCercaniasLivePositions()
                val stopIdDigits = leg.fromStopId?.filter { it.isDigit() }

                if (tripUpdates.isNotEmpty() || livePositions.isNotEmpty()) {
                    val matchingTripUpdate = tripUpdates.values.firstOrNull { update ->
                        val tripId = update.tripId
                        val lineMatches = allowedLines.any { allowed ->
                            val clean = allowed.replace("-", "").uppercase()
                            clean.isNotBlank() && tripId.replace("-", "").uppercase().contains(clean)
                        }
                        val stopMatches = !stopIdDigits.isNullOrBlank() && (update.stopDelays.containsKey(stopIdDigits) || update.stopEstimatedTimes.containsKey(stopIdDigits))
                        lineMatches || stopMatches
                    }

                    val matchedDelaySec = if (matchingTripUpdate != null && !stopIdDigits.isNullOrBlank()) {
                        matchingTripUpdate.stopDelays[stopIdDigits] ?: matchingTripUpdate.delaySeconds
                    } else {
                        matchingTripUpdate?.delaySeconds
                    }

                    if (matchingTripUpdate != null && matchedDelaySec != null) {
                        val delayM = (matchedDelaySec / 60).toInt()
                        delayMinutes = delayM
                        isLive = true

                        val estimatedStopEpochSec = if (!stopIdDigits.isNullOrBlank()) matchingTripUpdate.stopEstimatedTimes[stopIdDigits] else null
                        if (estimatedStopEpochSec != null && estimatedStopEpochSec > 0) {
                            val liveMinsRemaining = ((estimatedStopEpochSec * 1000L - nowMs) / 60000L).toInt().coerceAtLeast(0)
                            liveMinutes = liveMinsRemaining
                            liveSeconds = liveMinsRemaining * 60
                        } else if (theoreticalMinutesRemaining != null) {
                            liveMinutes = (theoreticalMinutesRemaining + delayM).coerceAtLeast(0)
                            liveSeconds = liveMinutes * 60
                        }
                        matchedLineShortName = cercaniasLine
                        val key = "RENFE_${matchingTripUpdate.tripId}"
                        val epochMs = nowMs + ((liveMinutes ?: 0) * 60 * 1000L)
                        onMatchedOrigin(key, null, epochMs)
                    } else if (theoreticalMinutesRemaining != null && theoreticalMinutesRemaining in 0..25) {
                        liveMinutes = theoreticalMinutesRemaining
                        liveSeconds = liveMinutes * 60
                    }
                }
            }
            else -> {
                // Fallback for bicycle, etc.
            }
        }

        if (isLive && liveMinutes != null) {
            val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
            cal.add(Calendar.MINUTE, liveMinutes)
            adjustedDepTime = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        return LegReconciliationResult(
            normalizedLine = normalizedLine,
            liveMinutes = liveMinutes,
            liveSeconds = liveSeconds,
            liveDestination = liveDestination,
            isLive = isLive,
            delayMinutes = delayMinutes,
            adjustedDepartureTime = adjustedDepTime,
            matchedLineShortName = matchedLineShortName,
            matchedDestination = matchedDestination,
            schedulePhase = if (isLive) com.example.data.model.routing.SchedulePhase.LIVE_ACQUIRED else com.example.data.model.routing.SchedulePhase.THEORETICAL_AWAITING_RADAR
        )
    }

    fun calculateTheoreticalMinutesRemaining(scheduledIsoOrTime: String?): Int? {
        if (scheduledIsoOrTime.isNullOrBlank()) return null
        return try {
            val parsedTime = TripTimeParser.parseTimeToMillis(scheduledIsoOrTime) ?: return null
            val nowMs = System.currentTimeMillis()
            val diffMs = parsedTime - nowMs
            (diffMs / 60000L).toInt()
        } catch (e: Exception) {
            null
        }
    }
}
