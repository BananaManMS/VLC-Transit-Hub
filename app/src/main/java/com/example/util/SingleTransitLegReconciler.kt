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
                } else if (theoreticalMinutesRemaining != null && theoreticalMinutesRemaining in 0..120) {
                    // Non-EMT bus (Metrobus/Interurban) scheduled departure estimation
                    liveMinutes = theoreticalMinutesRemaining
                    liveSeconds = theoreticalMinutesRemaining * 60
                    liveDestination = leg.headsign?.ifBlank { null } ?: leg.toName
                    isLive = false
                    delayMinutes = 0
                    matchedLineShortName = leg.routeShortName ?: normalizedLine
                    matchedDestination = liveDestination
                    val key = "METROBUS_${leg.routeShortName}_${leg.fromStopId ?: leg.fromName}"
                    val epochMs = nowMs + (theoreticalMinutesRemaining * 60 * 1000L)
                    onMatchedOrigin(key, null, epochMs)
                }
            }
            TransitMode.METROBUS -> {
                if (theoreticalMinutesRemaining != null && theoreticalMinutesRemaining in 0..120) {
                    liveMinutes = theoreticalMinutesRemaining
                    liveSeconds = theoreticalMinutesRemaining * 60
                    liveDestination = leg.headsign?.ifBlank { null } ?: leg.toName
                    isLive = false
                    delayMinutes = 0
                    matchedLineShortName = leg.routeShortName ?: normalizedLine
                    matchedDestination = liveDestination
                    val key = "METROBUS_${leg.routeShortName}_${leg.fromStopId ?: leg.fromName}"
                    val epochMs = nowMs + (theoreticalMinutesRemaining * 60 * 1000L)
                    onMatchedOrigin(key, null, epochMs)
                }
            }
            TransitMode.SUBWAY, TransitMode.TRAM -> {
                val stationIdInt = TransitIdMapper.extractMetroStationId(leg.fromStopId, leg.fromName)
                val stationId = stationIdInt?.toString() ?: leg.fromStopId?.filter { it.isDigit() }

                if (!stationId.isNullOrBlank()) {
                    val departures = TransitOperatorArrivalProvider.fetchMetroDepartures(stationId)
                    val allowedLines = if (isBoarded) {
                        val digits = normalizedLine.filter { it.isDigit() }
                        setOfNotNull(normalizedLine, if (digits.isNotBlank()) digits else null, "L$digits")
                    } else {
                        TransitIdMapper.getAlternativeTransitLines(
                            mode = leg.mode,
                            originalLine = normalizedLine,
                            fromName = leg.fromName,
                            toName = leg.toName
                        )
                    }

                    val matchingDepartures = departures.filter { dep ->
                        val depDigits = dep.line.filter { it.isDigit() }
                        allowedLines.any { allowed ->
                            val allowedDigits = allowed.filter { it.isDigit() }
                            dep.line.equals(allowed, ignoreCase = true) ||
                            (depDigits.isNotBlank() && depDigits == allowedDigits)
                        }
                    }

                    val destMatches = matchingDepartures.filter { dep ->
                        TripVehicleMatcher.isDestinationMatch(dep.destination, leg, dep.line)
                    }
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
                val allowedLineDigits = allowedLines.mapNotNull { al ->
                    com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(al).ifBlank { al.filter { it.isDigit() } }.ifBlank { null }
                }.toSet()

                val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                val stopIdDigits = leg.fromStopId?.filter { it.isDigit() }
                val toStopDigits = leg.toStopId?.filter { it.isDigit() }
                val expectedSchedEpochMs = TripTimeParser.parseTimeToMillis(leg.scheduledStartTime ?: leg.formattedStartTime)
                    ?: TripTimeParser.parseTimeToMillis(leg.startTime)

                if (tripUpdates.isNotEmpty()) {
                    val candidates = tripUpdates.values
                        .distinctBy { it.tripId }
                        .filter { update ->
                            if (update.isCanceled) return@filter false
                            val updateLineDigit = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineNumber(update.routeId, update.tripId)
                            val lineMatches = allowedLineDigits.isEmpty() || (updateLineDigit.isNotBlank() && updateLineDigit in allowedLineDigits)
                            if (!lineMatches) return@filter false

                            val stopMatches = if (!stopIdDigits.isNullOrBlank()) {
                                update.stopDelays.containsKey(stopIdDigits) ||
                                    update.stopEstimatedTimes.containsKey(stopIdDigits) ||
                                    update.stopTimes.any { it.stopId == stopIdDigits }
                            } else true
                            if (!stopMatches) return@filter false

                            if (!stopIdDigits.isNullOrBlank() && !toStopDigits.isNullOrBlank() && update.stopTimes.isNotEmpty()) {
                                val fromIdx = update.stopTimes.indexOfFirst { it.stopId == stopIdDigits }
                                val toIdx = update.stopTimes.indexOfFirst { it.stopId == toStopDigits }
                                if (fromIdx != -1 && toIdx != -1 && toIdx < fromIdx) return@filter false
                            }
                            true
                        }
                        .mapNotNull { update ->
                            val delaySec = if (!stopIdDigits.isNullOrBlank()) {
                                update.stopDelays[stopIdDigits] ?: update.delaySeconds
                            } else {
                                update.delaySeconds
                            }
                            val stopEstSec = if (!stopIdDigits.isNullOrBlank()) update.stopEstimatedTimes[stopIdDigits] else null
                            val estEpochMs = when {
                                stopEstSec != null && stopEstSec > 0L -> stopEstSec * 1000L
                                expectedSchedEpochMs != null -> expectedSchedEpochMs + (delaySec * 1000L)
                                else -> nowMs + (delaySec * 1000L)
                            }
                            if (estEpochMs >= (earliestReachableMs - 120_000L)) {
                                Triple(update, delaySec, estEpochMs)
                            } else null
                        }

                    val best = candidates.minByOrNull { (_, _, estEpochMs) ->
                        if (expectedSchedEpochMs != null) {
                            kotlin.math.abs(estEpochMs - expectedSchedEpochMs)
                        } else {
                            kotlin.math.abs(estEpochMs - nowMs)
                        }
                    }

                    if (best != null) {
                        val (matchingTripUpdate, matchedDelaySec, estEpochMs) = best
                        val delayM = (matchedDelaySec / 60).toInt()
                        delayMinutes = delayM
                        isLive = true

                        val liveMinsRemaining = ((estEpochMs - nowMs) / 60000L).toInt().coerceAtLeast(0)
                        liveMinutes = liveMinsRemaining
                        liveSeconds = liveMinsRemaining * 60
                        matchedLineShortName = com.example.ui.cercanias.CercaniasRouteUtils.getCanonicalLineCode(
                            matchingTripUpdate.routeId,
                            matchingTripUpdate.tripId,
                            cercaniasLine
                        )
                        val key = "RENFE_${matchingTripUpdate.tripId}"
                        onMatchedOrigin(key, null, estEpochMs)
                    } else if (theoreticalMinutesRemaining != null && theoreticalMinutesRemaining in 0..25) {
                        liveMinutes = theoreticalMinutesRemaining
                        liveSeconds = liveMinutes * 60
                    }
                } else if (theoreticalMinutesRemaining != null && theoreticalMinutesRemaining in 0..25) {
                    liveMinutes = theoreticalMinutesRemaining
                    liveSeconds = liveMinutes * 60
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
