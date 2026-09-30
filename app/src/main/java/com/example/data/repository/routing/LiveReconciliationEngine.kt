package com.example.data.repository.routing

import android.content.Context
import android.util.Log
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.network.NetworkModule
import com.example.data.network.TransitousApiService
import com.example.data.repository.MetroAlertsRepository
import com.example.data.repository.RealTimeTransitRepository
import com.example.ui.bus.BusMapper
import com.example.ui.bus.EmtBusTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Encapsulates live real-time reconciliation logic for multi-modal transit itineraries.
 * Queries live feeds (Metrovalencia, EMT, Cercanías Renfe) and adjusts connection times and viabilities.
 */
class LiveReconciliationEngine(
    private val context: Context? = null,
    private val metroAlertsRepository: MetroAlertsRepository? = null,
    private val transitousApiService: TransitousApiService = NetworkModule.transitousApiService
) {
    private val nearbyStopCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var cachedActiveStops: List<com.example.data.database.GeoportalStopEntity>? = null

    companion object {
        private const val TAG = "LiveReconciliationEng"
    }

    suspend fun reconcileItineraries(
        baseItineraries: List<PlannedItinerary>,
        isDepartNow: Boolean = true
    ): List<PlannedItinerary> = coroutineScope {
        if (!isDepartNow || baseItineraries.isEmpty()) {
            return@coroutineScope baseItineraries
        }
        val claimedVehicleKeys = ConcurrentHashMap.newKeySet<String>()
        baseItineraries.map { itinerary ->
            async {
                try {
                    reconcileItineraryWithLiveData(itinerary, isDepartNow, claimedVehicleKeys)
                } catch (e: Exception) {
                    itinerary
                }
            }
        }.awaitAll().sortedWith(
            compareBy<PlannedItinerary> { RoutingDataMapper.getEffectiveArrivalEpochMs(it) }
                .thenBy { it.totalDurationSeconds }
                .thenBy { RoutingDataMapper.getEffectiveDepartureEpochMs(it) }
        )
    }

    suspend fun reconcileItinerary(
        baseItinerary: PlannedItinerary,
        isDepartNow: Boolean = true
    ): PlannedItinerary {
        val claimedVehicleKeys = ConcurrentHashMap.newKeySet<String>()
        return reconcileItineraryWithLiveData(baseItinerary, isDepartNow, claimedVehicleKeys)
    }

    suspend fun reconcileItineraryWithLiveData(
        baseItinerary: PlannedItinerary,
        isDepartNow: Boolean,
        claimedVehicleKeys: MutableSet<String>
    ): PlannedItinerary {
        val legs = baseItinerary.legs.map { it.copy() }.toMutableList()
        val (viability, viabilityNotice, recommendedLeaveTime, activeAlerts) = evaluateLiveReconciliation(
            legs = legs,
            theoreticalStartTimeIso = baseItinerary.startTime,
            isDepartNow = isDepartNow,
            claimedVehicleKeys = claimedVehicleKeys
        )

        val finalFormattedDeparture = legs.firstOrNull()?.formattedStartTime ?: baseItinerary.formattedDepartureTime
        val finalFormattedArrival = legs.lastOrNull()?.formattedEndTime ?: baseItinerary.formattedArrivalTime
        val totalDurationSec = RoutingDataMapper.calculateTotalDurationSec(legs, baseItinerary.totalDurationSeconds)
        val walkLegs = legs.filter { it.mode == TransitMode.WALK }
        val finalWalkDistance = walkLegs.sumOf { it.distanceMeters }
        val finalWalkDuration = walkLegs.sumOf { it.durationSeconds }
        val allPoints = legs.flatMap { it.geometry }
        val finalTransfersCount = legs.count { it.mode != TransitMode.WALK }.minus(1).coerceAtLeast(0)

        return baseItinerary.copy(
            legs = legs,
            viability = viability,
            viabilityNotice = viabilityNotice,
            recommendedStartTime = recommendedLeaveTime ?: finalFormattedDeparture,
            formattedDuration = RoutingDataMapper.formatSecondsToDuration(totalDurationSec),
            formattedDepartureTime = finalFormattedDeparture,
            formattedArrivalTime = finalFormattedArrival,
            totalDurationSeconds = totalDurationSec,
            transfersCount = finalTransfersCount,
            totalWalkDistanceMeters = finalWalkDistance,
            totalWalkDurationSeconds = finalWalkDuration,
            allRoutePolyline = if (allPoints.isNotEmpty()) allPoints else baseItinerary.allRoutePolyline,
            activeAlerts = activeAlerts
        )
    }

    private suspend fun evaluateLiveReconciliation(
        legs: MutableList<PlannedLeg>,
        theoreticalStartTimeIso: String?,
        isDepartNow: Boolean,
        claimedVehicleKeys: MutableSet<String>
    ): ReconciliationData {
        val transitLegIndices = legs.indices.filter { legs[it].mode != TransitMode.WALK }
        if (transitLegIndices.isEmpty()) {
            return ReconciliationData(
                viability = ItineraryViability.THEORETICAL_SCHEDULE,
                notice = "Ruta directa a pie",
                recommendedStartTime = null,
                activeAlerts = emptyList<String>()
            )
        }

        val firstTransitLegIdx = transitLegIndices.first()
        val activeAlerts = mutableListOf<String>()

        // Check service alerts for Metro / Cercanías
        transitLegIndices.forEach { legIndex ->
            val transitLeg = legs[legIndex]
            if (transitLeg.mode == TransitMode.SUBWAY || transitLeg.mode == TransitMode.TRAM) {
                val metroLine = TransitIdMapper.extractMetroLine(transitLeg.routeShortName, transitLeg.routeLongName)
                val stationName = transitLeg.fromName
                val alerts = checkMetroAlerts(metroLine, stationName)
                if (alerts.isNotEmpty()) {
                    activeAlerts.addAll(alerts)
                }
            }
        }

        // Determine if query is happening in real-time window
        val isCurrentRealTimeWindow = if (isDepartNow) {
            true
        } else {
            val startEpochMs = RoutingDataMapper.parseIsoToEpochMs(theoreticalStartTimeIso)
            if (startEpochMs > 0) {
                val diffMs = Math.abs(startEpochMs - System.currentTimeMillis())
                diffMs <= 60 * 60 * 1000L
            } else {
                false
            }
        }

        var reconciledNotice: String? = null
        var reconciledStartTime: String? = null
        var reconciledViability: ItineraryViability? = null
        val nowMs = System.currentTimeMillis()

        if (isCurrentRealTimeWindow) {
            // Reconcile legs sequentially so each transfer leg has the precise, real arrival time from the previous leg
            var currentAccumulatedTripMs = if (RoutingDataMapper.parseIsoToEpochMs(legs.firstOrNull()?.startTime) > nowMs) {
                RoutingDataMapper.parseIsoToEpochMs(legs.firstOrNull()?.startTime)
            } else {
                nowMs
            }

            var priorWalkBufferSec = 0L
            val legReconciliationResults = mutableListOf<Triple<Int, Int, String>>() // legIndex, delayMinutes, label
            var transferBrokenAndUnrecoverable = false
            var transferSpliced = false

            for (i in legs.indices) {
                val leg = legs[i]
                if (leg.mode == TransitMode.WALK) {
                    priorWalkBufferSec += leg.durationSeconds
                    continue
                }

                // Earliest time user can physically reach this boarding stop:
                val platformBufferMs = if (i == 0 || (i > 0 && legs[i-1].mode == TransitMode.WALK && i <= 1)) 300_000L else 0L
                val earliestReachableUserArrivalMs = currentAccumulatedTripMs + (Math.max(0L, priorWalkBufferSec - 150L) * 1000L) - platformBufferMs
                val exactUserArrivalAtStopMs = currentAccumulatedTripMs + (priorWalkBufferSec * 1000L)
                val scheduledLegStartEpochMs = RoutingDataMapper.parseIsoToEpochMs(leg.startTime)

                var matchedLiveDelayMins: Int? = null
                var matchedLiveLabel: String? = null
                var matchedLiveLine: String? = null
                var matchedLiveDestination: String? = null

                if (leg.mode == TransitMode.BUS) {
                    val isEmt = TransitIdMapper.isEmtBus(
                        agencyName = leg.agencyName,
                        routeShortName = leg.routeShortName,
                        routeLongName = leg.routeLongName,
                        fromStopId = leg.fromStopId,
                        fromName = leg.fromName
                    )
                    val isMetrobus = TransitIdMapper.isMetrobus(
                        agencyName = leg.agencyName,
                        routeShortName = leg.routeShortName,
                        routeLongName = leg.routeLongName
                    )

                    if (isEmt) {
                        val lineTarget = leg.routeShortName?.trim() ?: ""
                        var stopNumber = TransitIdMapper.extractEmtStopNumber(leg.fromStopId, leg.fromName)

                        if (stopNumber.isNullOrEmpty() && lineTarget.isNotEmpty()) {
                            val stopLat = if (leg.fromLat != 0.0) leg.fromLat else leg.geometry.firstOrNull()?.latitude ?: 0.0
                            val stopLon = if (leg.fromLon != 0.0) leg.fromLon else leg.geometry.firstOrNull()?.longitude ?: 0.0
                            stopNumber = findNearbyEmtStopId(stopLat, stopLon, lineTarget)
                        }

                        if (!stopNumber.isNullOrEmpty() && lineTarget.isNotEmpty()) {
                            val liveArrivals = fetchEmtLiveArrivals(stopNumber)
                            // Strict real-time check: ignore any schedule fallback data
                            val targetArrivals = liveArrivals.filter {
                                it.isRealTime && TransitIdMapper.isSameEmtLine(it.linea, lineTarget)
                            }

                            if (targetArrivals.isNotEmpty()) {
                                val validCandidates = targetArrivals.filter { arrival ->
                                    val liveArrivalMs = nowMs + (arrival.secondsRemaining * 1000L)
                                    val vehicleKey = "EMT_${stopNumber}_${arrival.linea}_${arrival.secondsRemaining / 60}"
                                    val matchesTimeWindow = if (scheduledLegStartEpochMs > 0) {
                                        Math.abs(liveArrivalMs - scheduledLegStartEpochMs) <= 45 * 60 * 1000L
                                    } else {
                                        true
                                    }
                                    liveArrivalMs >= earliestReachableUserArrivalMs && matchesTimeWindow && !claimedVehicleKeys.contains(vehicleKey)
                                }

                                val chosenCandidate = if (validCandidates.isNotEmpty()) {
                                    val bestCandidate = if (scheduledLegStartEpochMs > 0) {
                                        validCandidates.minByOrNull { arrival ->
                                            val liveArrivalMs = nowMs + (arrival.secondsRemaining * 1000L)
                                            Math.abs(liveArrivalMs - scheduledLegStartEpochMs)
                                        }
                                    } else {
                                        validCandidates.minByOrNull { it.secondsRemaining }
                                    }

                                    if (bestCandidate != null) {
                                        val liveArrivalMs = nowMs + (bestCandidate.secondsRemaining * 1000L)
                                        val delaySec = if (scheduledLegStartEpochMs > 0) ((liveArrivalMs - scheduledLegStartEpochMs) / 1000).toInt() else 0
                                        val delayMins = (delaySec / 60)
                                        Triple(bestCandidate, liveArrivalMs, delayMins)
                                    } else null
                                } else null

                                if (chosenCandidate != null) {
                                    val (arrival, _, delayMins) = chosenCandidate
                                    val vehicleKey = "EMT_${stopNumber}_${arrival.linea}_${arrival.secondsRemaining / 60}"
                                    val lineClean = lineTarget.removePrefix("L").removePrefix("l")
                                    val busLabel = if (lineClean.startsWith("C") || lineClean.startsWith("N") || lineClean.length >= 3) "Bus $lineClean" else "Bus $lineClean"
                                    claimedVehicleKeys.add(vehicleKey)
                                    matchedLiveDelayMins = delayMins
                                    matchedLiveLabel = busLabel
                                }
                            }
                        }
                    } else if (isMetrobus) {
                        val stopId = leg.fromStopId?.filter { it.isDigit() }
                        val lineTarget = leg.routeShortName?.trim() ?: ""
                        if (!stopId.isNullOrEmpty() && lineTarget.isNotEmpty()) {
                            val mbArrivals = fetchMetrobusLiveArrivals(stopId)
                            // Strict real-time check: ignore any schedule fallback data
                            val targetArrivals = mbArrivals.filter {
                                it.isRealTime && (it.lineCode.equals(lineTarget, ignoreCase = true) ||
                                        it.lineCode.filter { c -> c.isDigit() } == lineTarget.filter { c -> c.isDigit() })
                            }

                            if (targetArrivals.isNotEmpty()) {
                                val validCandidates = targetArrivals.filter { arrival ->
                                    val liveArrivalMs = nowMs + (arrival.secondsRemaining * 1000L)
                                    val vehicleKey = "METROBUS_${stopId}_${arrival.lineCode}_${arrival.secondsRemaining / 60}"
                                    val matchesTimeWindow = if (scheduledLegStartEpochMs > 0) {
                                        Math.abs(liveArrivalMs - scheduledLegStartEpochMs) <= 45 * 60 * 1000L
                                    } else {
                                        true
                                    }
                                    liveArrivalMs >= earliestReachableUserArrivalMs && matchesTimeWindow && !claimedVehicleKeys.contains(vehicleKey)
                                }

                                val chosenCandidate = if (validCandidates.isNotEmpty()) {
                                    val bestCandidate = if (scheduledLegStartEpochMs > 0) {
                                        validCandidates.minByOrNull { arrival ->
                                            val liveArrivalMs = nowMs + (arrival.secondsRemaining * 1000L)
                                            Math.abs(liveArrivalMs - scheduledLegStartEpochMs)
                                        }
                                    } else {
                                        validCandidates.minByOrNull { it.secondsRemaining }
                                    }

                                    if (bestCandidate != null) {
                                        val liveArrivalMs = nowMs + (bestCandidate.secondsRemaining * 1000L)
                                        val delaySec = if (scheduledLegStartEpochMs > 0) ((liveArrivalMs - scheduledLegStartEpochMs) / 1000).toInt() else 0
                                        val delayMins = (delaySec / 60)
                                        Triple(bestCandidate, liveArrivalMs, delayMins)
                                    } else null
                                } else null

                                if (chosenCandidate != null) {
                                    val (arrival, _, delayMins) = chosenCandidate
                                    val vehicleKey = "METROBUS_${stopId}_${arrival.lineCode}_${arrival.secondsRemaining / 60}"
                                    val lineClean = lineTarget.removePrefix("L").removePrefix("l")
                                    val busLabel = "Metrobús $lineClean"
                                    claimedVehicleKeys.add(vehicleKey)
                                    matchedLiveDelayMins = delayMins
                                    matchedLiveLabel = busLabel
                                }
                            }
                        }
                    }
                } else if (leg.mode == TransitMode.SUBWAY || leg.mode == TransitMode.TRAM) {
                    val stationIdInt = TransitIdMapper.extractMetroStationId(leg.fromStopId, leg.fromName)
                    val metroLine = TransitIdMapper.extractMetroLine(leg.routeShortName, leg.routeLongName) ?: leg.routeShortName ?: ""

                    if (stationIdInt != null) {
                        val liveDepartures = fetchMetroLiveArrivals(stationIdInt.toString())
                        val allowedLines = TransitIdMapper.getAlternativeTransitLines(
                            mode = leg.mode,
                            originalLine = metroLine,
                            fromName = leg.fromName,
                            toName = leg.toName
                        )

                        val targetDepartures = liveDepartures.filter { dep ->
                            dep.isRealTime && run {
                                val depDigits = dep.line.filter { it.isDigit() }
                                allowedLines.any { allowed ->
                                    val allowedDigits = allowed.filter { it.isDigit() }
                                    dep.line.equals(allowed, ignoreCase = true) ||
                                    (depDigits.isNotBlank() && depDigits == allowedDigits)
                                } && TransitIdMapper.isDestinationMatch(dep.destination, leg, dep.line)
                            }
                        }

                        if (targetDepartures.isNotEmpty()) {
                            val validCandidates = targetDepartures.filter { dep ->
                                val liveArrivalMs = nowMs + (dep.seconds * 1000L)
                                val vehicleKey = "METRO_${stationIdInt}_${dep.line}_${dep.destination}_${dep.seconds / 60}"
                                val matchesTimeWindow = if (scheduledLegStartEpochMs > 0) {
                                    val diffMs = liveArrivalMs - scheduledLegStartEpochMs
                                    diffMs >= -120_000L && diffMs <= 30 * 60 * 1000L
                                } else {
                                    true
                                }
                                liveArrivalMs >= earliestReachableUserArrivalMs && matchesTimeWindow && !claimedVehicleKeys.contains(vehicleKey)
                            }

                            val chosenCandidate = if (validCandidates.isNotEmpty()) {
                                val exactLineCandidates = validCandidates.filter { dep ->
                                    val depDigits = dep.line.filter { it.isDigit() }
                                    val metroDigits = metroLine.filter { it.isDigit() }
                                    dep.line.equals(metroLine, ignoreCase = true) ||
                                    (depDigits.isNotBlank() && metroDigits.isNotBlank() && depDigits == metroDigits)
                                }

                                val selectedDep = if (exactLineCandidates.isNotEmpty()) {
                                    if (scheduledLegStartEpochMs > 0) {
                                        exactLineCandidates.minByOrNull { dep ->
                                            val liveArrivalMs = nowMs + (dep.seconds * 1000L)
                                            Math.abs(liveArrivalMs - scheduledLegStartEpochMs)
                                        }
                                    } else {
                                        exactLineCandidates.minByOrNull { it.seconds }
                                    }
                                } else {
                                    if (scheduledLegStartEpochMs > 0) {
                                        validCandidates.minByOrNull { dep ->
                                            val liveArrivalMs = nowMs + (dep.seconds * 1000L)
                                            Math.abs(liveArrivalMs - scheduledLegStartEpochMs)
                                        }
                                    } else {
                                        validCandidates.minByOrNull { it.seconds }
                                    }
                                }

                                if (selectedDep != null) {
                                    val liveArrivalMs = nowMs + (selectedDep.seconds * 1000L)
                                    val delaySec = if (scheduledLegStartEpochMs > 0) ((liveArrivalMs - scheduledLegStartEpochMs) / 1000).toInt() else 0
                                    val delayMins = (delaySec / 60).coerceAtLeast(0)
                                    Triple(selectedDep, liveArrivalMs, delayMins)
                                } else null
                            } else null

                            if (chosenCandidate != null) {
                                val (dep, _, delayMins) = chosenCandidate
                                val vehicleKey = "METRO_${stationIdInt}_${dep.line}_${dep.destination}_${dep.seconds / 60}"
                                claimedVehicleKeys.add(vehicleKey)
                                val lineDisp = if (dep.line.startsWith("L") || dep.line.isBlank()) dep.line else "L${dep.line}"
                                matchedLiveDelayMins = delayMins
                                matchedLiveLabel = "Metro $lineDisp"
                                matchedLiveLine = dep.line
                                matchedLiveDestination = dep.destination
                            }
                        }
                    }
                } else if (leg.mode == TransitMode.RAIL) {
                    val cercaniasLine = TransitIdMapper.extractCercaniasLine(leg.routeShortName, leg.routeLongName, leg.agencyName, leg.mode) ?: leg.routeShortName ?: ""
                    val allowedLines = TransitIdMapper.getAlternativeTransitLines(
                        mode = leg.mode,
                        originalLine = cercaniasLine,
                        fromName = leg.fromName,
                        toName = leg.toName
                    )

                    val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                    val stopIdDigits = leg.fromStopId?.filter { it.isDigit() }

                    if (tripUpdates.isNotEmpty()) {
                        val exactMatchingUpdate = if (cercaniasLine.isNotBlank()) {
                            tripUpdates.values.firstOrNull { update ->
                                val clean = cercaniasLine.replace("-", "").uppercase()
                                clean.isNotBlank() && update.tripId.replace("-", "").uppercase().contains(clean) &&
                                (!stopIdDigits.isNullOrBlank() && (update.stopDelays.containsKey(stopIdDigits) || update.stopEstimatedTimes.containsKey(stopIdDigits)))
                            }
                        } else null

                        val matchingTripUpdate = exactMatchingUpdate ?: tripUpdates.values.firstOrNull { update ->
                            val tripId = update.tripId
                            val lineMatches = allowedLines.any { allowed ->
                                val clean = allowed.replace("-", "").uppercase()
                                clean.isNotBlank() && tripId.replace("-", "").uppercase().contains(clean)
                            }
                            val stopMatches = !stopIdDigits.isNullOrBlank() && (update.stopDelays.containsKey(stopIdDigits) || update.stopEstimatedTimes.containsKey(stopIdDigits))
                            lineMatches || stopMatches
                        }

                        if (matchingTripUpdate != null) {
                            val stopEstimatedEpochSec = if (!stopIdDigits.isNullOrBlank()) matchingTripUpdate.stopEstimatedTimes[stopIdDigits] else null
                            val delaySec = if (!stopIdDigits.isNullOrBlank()) {
                                matchingTripUpdate.stopDelays[stopIdDigits] ?: matchingTripUpdate.delaySeconds
                            } else {
                                matchingTripUpdate.delaySeconds
                            }

                            val delayMins = delaySec / 60
                            val liveArrivalMs = if (stopEstimatedEpochSec != null && stopEstimatedEpochSec > 0) {
                                stopEstimatedEpochSec * 1000L
                            } else if (scheduledLegStartEpochMs > 0) {
                                scheduledLegStartEpochMs + (delayMins * 60 * 1000L)
                            } else {
                                nowMs + (delayMins * 60 * 1000L)
                            }

                            val vehicleKey = "RENFE_${matchingTripUpdate.tripId}"
                            if (liveArrivalMs >= (earliestReachableUserArrivalMs - 120_000L) && !claimedVehicleKeys.contains(vehicleKey)) {
                                claimedVehicleKeys.add(vehicleKey)
                                matchedLiveDelayMins = delayMins.toInt()
                                val lineLabel = if (cercaniasLine.isNotBlank()) "Cercanías $cercaniasLine" else "Cercanías Renfe"
                                matchedLiveLabel = lineLabel
                            }
                        }
                    }
                }

                // If live GPS vehicle found:
                if (matchedLiveDelayMins != null && matchedLiveLabel != null) {
                    legReconciliationResults.add(Triple(i, matchedLiveDelayMins, matchedLiveLabel))

                    val origStart = leg.scheduledStartTime ?: leg.formattedStartTime
                    val origEnd = leg.scheduledEndTime ?: leg.formattedEndTime
                    val updatedStart = if (matchedLiveDelayMins != 0) RoutingDataMapper.shiftFormattedTime(origStart, matchedLiveDelayMins) else origStart
                    val updatedEnd = if (matchedLiveDelayMins != 0) RoutingDataMapper.shiftFormattedTime(origEnd, matchedLiveDelayMins) else origEnd

                    val lineShort = matchedLiveLine?.ifBlank { null } ?: leg.routeShortName
                    val lineColorHex = if (!matchedLiveLine.isNullOrBlank()) {
                        com.example.util.LineColorResolver.resolveRouteColorHex(leg.mode, lineShort, leg.routeColorHex, leg.agencyName).removePrefix("#")
                    } else leg.routeColorHex

                    legs[i] = leg.copy(
                        routeShortName = lineShort,
                        routeColorHex = lineColorHex,
                        headsign = matchedLiveDestination ?: leg.headsign,
                        scheduledStartTime = origStart,
                        scheduledEndTime = origEnd,
                        formattedStartTime = updatedStart,
                        formattedEndTime = updatedEnd,
                        isRealTimeVerified = true,
                        realTimeDelayMinutes = matchedLiveDelayMins
                    )

                    // Advance accumulated trip time to the end of this leg
                    val legEndEpochMs = RoutingDataMapper.parseIsoToEpochMs(leg.endTime)
                    val newLegEndMs = if (legEndEpochMs > 0) legEndEpochMs + (matchedLiveDelayMins * 60 * 1000L) else (nowMs + (leg.durationSeconds * 1000L))
                    currentAccumulatedTripMs = newLegEndMs
                    priorWalkBufferSec = 0L
                } else {
                    // Check if scheduled departure is broken/unreachable due to delay from previous legs
                    if (scheduledLegStartEpochMs > 0 && scheduledLegStartEpochMs < earliestReachableUserArrivalMs) {
                        val fromLat = if (leg.fromLat != 0.0) leg.fromLat else leg.geometry.firstOrNull()?.latitude ?: 0.0
                        val fromLon = if (leg.fromLon != 0.0) leg.fromLon else leg.geometry.firstOrNull()?.longitude ?: 0.0
                        val toLat = legs.last().toLat.takeIf { it != 0.0 } ?: legs.last().geometry.lastOrNull()?.latitude ?: 0.0
                        val toLon = legs.last().toLon.takeIf { it != 0.0 } ?: legs.last().geometry.lastOrNull()?.longitude ?: 0.0

                        val nextOfficialSubPlan = withTimeoutOrNull(3500) {
                            trySubqueryTransitousNext(fromLat, fromLon, toLat, toLon, exactUserArrivalAtStopMs)
                        }

                        if (nextOfficialSubPlan != null && nextOfficialSubPlan.legs.isNotEmpty()) {
                            val newWalkDist = nextOfficialSubPlan.legs.filter { it.mode == TransitMode.WALK }.sumOf { it.distanceMeters }
                            val oldRemainingWalkDist = legs.subList(i, legs.size).filter { it.mode == TransitMode.WALK }.sumOf { it.distanceMeters }

                            if (newWalkDist <= oldRemainingWalkDist + 600) {
                                while (legs.size > i) {
                                    legs.removeAt(legs.size - 1)
                                }
                                legs.addAll(nextOfficialSubPlan.legs)
                                transferSpliced = true
                                break
                            } else {
                                transferBrokenAndUnrecoverable = true
                            }
                        } else {
                            transferBrokenAndUnrecoverable = true
                        }
                    } else {
                        // Scheduled time is in the future and reachable
                        val legEndEpochMs = RoutingDataMapper.parseIsoToEpochMs(leg.endTime)
                        currentAccumulatedTripMs = if (legEndEpochMs > 0) legEndEpochMs else (currentAccumulatedTripMs + (leg.durationSeconds * 1000L))
                        priorWalkBufferSec = 0L
                    }
                }
            }

            var transferWarningNotice: String? = null

            if (legReconciliationResults.isNotEmpty()) {
                val notices = legReconciliationResults.map { (_, delayMinutes, label) ->
                    if (delayMinutes > 0) {
                        "$label con +$delayMinutes min de retraso"
                    } else {
                        "$label en hora"
                    }
                }
                reconciledNotice = notices.joinToString(" • ")
                reconciledViability = if (activeAlerts.isNotEmpty()) ItineraryViability.SERVICE_ALERT else ItineraryViability.VIABLE_ON_TIME
            }

            for ((legIndex, _, _) in legReconciliationResults) {
                val warning = adjustTransferWalkAndCheckSlack(legs, legIndex)
                if (warning != null && transferWarningNotice == null) {
                    transferWarningNotice = warning
                }
            }

            if (transferSpliced) {
                reconciledViability = ItineraryViability.ADJUSTED_NEXT_DEPARTURE
                val firstLegLabel = legReconciliationResults.firstOrNull()?.third ?: "Vehículo"
                val firstLegDelay = legReconciliationResults.firstOrNull()?.second ?: 0
                val nextTransit = legs.subList(firstTransitLegIdx.coerceAtMost(legs.size - 1), legs.size).firstOrNull { it.mode != TransitMode.WALK }
                val nextStart = nextTransit?.formattedStartTime ?: ""
                val nextLine = nextTransit?.routeShortName ?: ""
                reconciledNotice = if (firstLegDelay > 0) {
                    "En vivo: $firstLegLabel (+${firstLegDelay} min) • Siguiente salida $nextLine ($nextStart)"
                } else {
                    "Transbordo ajustado con el siguiente servicio ($nextStart)"
                }
            } else if (transferWarningNotice != null || transferBrokenAndUnrecoverable) {
                reconciledViability = ItineraryViability.ADJUSTED_NEXT_DEPARTURE
                val firstLegLabel = legReconciliationResults.firstOrNull()?.third ?: "Vehículo"
                val firstLegDelay = legReconciliationResults.firstOrNull()?.second ?: 0
                reconciledNotice = if (firstLegDelay > 0) {
                    "En vivo: $firstLegLabel (+${firstLegDelay} min) • Transbordo ajustado"
                } else {
                    "Transbordo ajustado con el siguiente servicio"
                }
            }

            // Set recommended departure time strictly based on first transit leg
            val firstTransitLeg = legs[firstTransitLegIdx]
            val priorWalkDurationSec = legs.take(firstTransitLegIdx).sumOf { it.durationSeconds }
            val priorWalkMins = (priorWalkDurationSec / 60).toInt()

            val firstTransitStartTime = firstTransitLeg.formattedStartTime
            reconciledStartTime = RoutingDataMapper.shiftFormattedTime(firstTransitStartTime, -priorWalkMins)

            // Synchronize the start and end times of the initial walk leg(s)
            if (firstTransitLegIdx > 0 && reconciledStartTime != null) {
                var currentWalkStart = reconciledStartTime
                for (wIdx in 0 until firstTransitLegIdx) {
                    val wLeg = legs[wIdx]
                    val wMins = (wLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
                    val wEnd = RoutingDataMapper.shiftFormattedTime(currentWalkStart!!, wMins)
                    legs[wIdx] = wLeg.copy(
                        formattedStartTime = currentWalkStart,
                        formattedEndTime = wEnd
                    )
                    currentWalkStart = wEnd
                }
            }

            if (reconciledNotice == null) {
                reconciledNotice = "En hora según horario oficial"
            }
        }

        val finalViability = reconciledViability ?: if (activeAlerts.isNotEmpty()) {
            ItineraryViability.SERVICE_ALERT
        } else {
            ItineraryViability.THEORETICAL_SCHEDULE
        }

        return ReconciliationData(
            viability = finalViability,
            notice = reconciledNotice ?: if (activeAlerts.isNotEmpty()) "Avisos activos en las líneas del trayecto" else null,
            recommendedStartTime = reconciledStartTime,
            activeAlerts = activeAlerts
        )
    }

    private fun adjustTransferWalkAndCheckSlack(legs: MutableList<PlannedLeg>, legIndex: Int): String? {
        val leg = legs[legIndex]
        val legEndTime = leg.formattedEndTime

        if (legIndex + 1 < legs.size && legs[legIndex + 1].mode == TransitMode.WALK) {
            val walkLeg = legs[legIndex + 1]
            val walkMins = (walkLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
            val newWalkStart = legEndTime
            val newWalkEnd = RoutingDataMapper.shiftFormattedTime(newWalkStart, walkMins)
            legs[legIndex + 1] = walkLeg.copy(
                formattedStartTime = newWalkStart,
                formattedEndTime = newWalkEnd
            )
        }

        var warningNotice: String? = null
        for (j in (legIndex + 1) until legs.size) {
            val currLeg = legs[j]
            val prevEnd = legs[j - 1].formattedEndTime
            if (currLeg.mode != TransitMode.WALK) {
                val scheduledDeparture = currLeg.formattedStartTime
                val slackMinutes = RoutingDataMapper.timeToMinutes(scheduledDeparture) - RoutingDataMapper.timeToMinutes(prevEnd)

                if (slackMinutes < 0) {
                    val modeName = if (currLeg.mode == TransitMode.SUBWAY || currLeg.mode == TransitMode.TRAM) "Metro" else "Bus"
                    val lineName = currLeg.routeShortName ?: ""
                    warningNotice = "Transbordo ajustado con $modeName $lineName"
                }
            } else {
                val walkMins = (currLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
                val newWalkEnd = RoutingDataMapper.shiftFormattedTime(prevEnd, walkMins)
                legs[j] = currLeg.copy(
                    formattedStartTime = prevEnd,
                    formattedEndTime = newWalkEnd
                )
            }
        }
        return warningNotice
    }

    private suspend fun trySubqueryTransitousNext(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        arrivalEpochMs: Long
    ): PlannedItinerary? {
        return try {
            val fromPlace = String.format(Locale.US, "%.5f,%.5f", fromLat, fromLon)
            val toPlace = String.format(Locale.US, "%.5f,%.5f", toLat, toLon)
            val isoTime = java.time.Instant.ofEpochMilli(arrivalEpochMs)
                .atZone(java.time.ZoneId.of("Europe/Madrid"))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            val resp = transitousApiService.plan(
                fromPlace = fromPlace,
                toPlace = toPlace,
                time = isoTime,
                date = null,
                arriveBy = null,
                maxTransfers = 1,
                modes = "WALK,SUBWAY,TRAM,BUS,COACH,REGIONAL_RAIL",
                maxWalkDuration = 30,
                maxWalkDist = 2000
            )
            val firstItin = resp.effectiveItineraries.firstOrNull() ?: return null
            RoutingDataMapper.mapDtoToItinerary(firstItin, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Subquery to Transitous failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchMetroLiveArrivals(stationId: String): List<com.example.data.repository.MetroArrival> {
        return RealTimeTransitRepository.getMetroLiveArrivals(stationId)
    }

    private suspend fun fetchEmtLiveArrivals(stopNumber: String): List<EmtBusTime> {
        return RealTimeTransitRepository.getEmtLiveArrivals(stopNumber, useFastTimeout = true, includeScheduled = false)
    }

    private val metrobusRepository by lazy {
        val appContext = context ?: com.example.MainApplication.instance
        com.example.data.repository.MetrobusRepository(
            database = com.example.data.database.AppDatabase.getDatabase(appContext),
            client = NetworkModule.okHttpClient,
            context = appContext
        )
    }

    private suspend fun fetchMetrobusLiveArrivals(stopId: String): List<com.example.ui.bus.MetrobusDepartureUiModel> {
        return try {
            metrobusRepository.getMetrobusArrivals(stopId, limitPerLine = 3, includeScheduled = false)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun checkMetroAlerts(metroLine: String?, stationName: String): List<String> {
        val alerts = mutableListOf<String>()
        val incidents = metroAlertsRepository?.activeIncidents?.value ?: emptyList()

        for (inc in incidents) {
            val text = "${inc.descriptionEs} ${inc.descriptionCa}"
            if (!metroLine.isNullOrEmpty() && (inc.lineaFgv?.contains(metroLine, ignoreCase = true) == true || text.contains("L$metroLine", ignoreCase = true) || text.contains("Línea $metroLine", ignoreCase = true))) {
                alerts.add("Aviso Metrovalencia L$metroLine: ${inc.descriptionEs.take(80)}")
            } else if (text.contains(stationName, ignoreCase = true)) {
                alerts.add("Incidencia en estación $stationName: ${inc.descriptionEs.take(80)}")
            }
        }
        return alerts
    }

    private suspend fun findNearbyEmtStopId(stopLat: Double, stopLon: Double, lineTarget: String): String? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (stopLat == 0.0 || stopLon == 0.0) return@withContext null

        val cacheKey = String.format(Locale.US, "%.4f,%.4f,%s", stopLat, stopLon, lineTarget)
        nearbyStopCache[cacheKey]?.let { return@withContext it }

        try {
            var activeStops = cachedActiveStops
            if (activeStops == null) {
                try {
                    val db = com.example.data.database.AppDatabase.getDatabase(ctx)
                    activeStops = db.geoportalStopDao().getAllActiveStops()
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching stops from DB: ${e.message}")
                }

                if (activeStops.isNullOrEmpty()) {
                    activeStops = BusMapper.loadStopsFromAssets(ctx)
                }
                cachedActiveStops = activeStops
            }

            if (activeStops.isNullOrEmpty()) return@withContext null

            var closestStop: com.example.data.database.GeoportalStopEntity? = null
            var minDistSq = Double.MAX_VALUE

            for (stop in activeStops) {
                if (!TransitIdMapper.stopPassesLine(stop.lineas, lineTarget)) continue

                val dLat = stop.lat - stopLat
                val dLon = stop.lon - stopLon
                val distSq = dLat * dLat + dLon * dLon
                if (distSq < minDistSq) {
                    minDistSq = distSq
                    closestStop = stop
                }
            }

            if (closestStop == null) {
                val assetStops = BusMapper.loadStopsFromAssets(ctx)
                for (stop in assetStops) {
                    if (!TransitIdMapper.stopPassesLine(stop.lineas, lineTarget)) continue
                    val dLat = stop.lat - stopLat
                    val dLon = stop.lon - stopLon
                    val distSq = dLat * dLat + dLon * dLon
                    if (distSq < minDistSq) {
                        minDistSq = distSq
                        closestStop = stop
                    }
                }
            }

            val resultStopId = closestStop?.id_parada
            if (resultStopId != null) {
                nearbyStopCache[cacheKey] = resultStopId
            }
            resultStopId
        } catch (e: Exception) {
            Log.w(TAG, "Error finding nearby EMT stop: ${e.message}")
            null
        }
    }

    private data class ReconciliationData(
        val viability: ItineraryViability,
        val notice: String?,
        val recommendedStartTime: String?,
        val activeAlerts: List<String>
    )
}
