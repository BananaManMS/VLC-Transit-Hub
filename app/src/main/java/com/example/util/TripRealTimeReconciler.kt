package com.example.util

import android.util.Log
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.network.NetworkModule
import com.example.data.repository.ActiveTripState
import com.example.data.repository.routing.TransitIdMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Live reconciliation state holding vehicle real-time ETA, "Sal ya" triggers,
 * deviation calculations, checkpoint recovery, and live transfer monitoring (M_enlace).
 */
data class RealTimeTripStatus(
    val vehicleLine: String? = null,
    val vehicleDestination: String? = null,
    val vehicleArrivalMinutes: Int? = null,
    val vehicleSecondsRemaining: Int? = null,
    val delayMinutes: Int = 0,
    val isLive: Boolean = false,
    val isLeaveNowAlert: Boolean = false,
    val leaveInMinutes: Int? = null,
    val leaveNowMessageEs: String? = null,
    val leaveNowMessageCa: String? = null,
    val isOffRoute: Boolean = false,
    val offRouteDistanceMeters: Double = 0.0,
    val consecutiveOffRouteCount: Int = 0,
    val isPedestrianDeviated: Boolean = false,
    val dynamicWalkMinutesRemaining: Int? = null,
    // Checkpoint ETA & Destination Monitoring
    val checkpointEtaMinutes: Int? = null,
    val isCheckpointLive: Boolean = false,
    // Transfer Margin & Dynamic Multi-Leg Reconciliation
    val transferMarginMinutes: Int? = null,
    val isTransferAtRisk: Boolean = false,
    val transferWarningEs: String? = null,
    val transferWarningCa: String? = null,
    val upcomingTransferInfoEs: String? = null,
    val upcomingTransferInfoCa: String? = null,
    val isUpcomingTransferLive: Boolean = false,
    val upcomingTransferLine: String? = null,
    val upcomingTransferMinutes: Int? = null,
    val scheduledDepartureTime: String? = null,
    val adjustedDepartureTime: String? = null,
    val schedulePhase: com.example.data.model.routing.SchedulePhase = com.example.data.model.routing.SchedulePhase.THEORETICAL_AWAITING_RADAR,
    val transferSchedulePhase: com.example.data.model.routing.SchedulePhase = com.example.data.model.routing.SchedulePhase.THEORETICAL_AWAITING_RADAR,
    val lastCheckedTimestamp: Long = 0L
)

data class LegReconciliationResult(
    val normalizedLine: String,
    val liveMinutes: Int?,
    val liveSeconds: Int?,
    val liveDestination: String?,
    val isLive: Boolean,
    val delayMinutes: Int,
    val adjustedDepartureTime: String?,
    val matchedLineShortName: String? = null,
    val matchedDestination: String? = null,
    val schedulePhase: com.example.data.model.routing.SchedulePhase = com.example.data.model.routing.SchedulePhase.THEORETICAL_AWAITING_RADAR
)

/**
 * Engine responsible for 15-20s live polling across all transit modes (EMT Bus, Metrovalencia,
 * Renfe Cercanías, Metrobus), dynamic multi-leg transfer monitoring across time windows,
 * Checkpoint ETA, and "Sal ya" walking triggers.
 */
class TripRealTimeReconciler(
    private val client: OkHttpClient = NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    private var consecutiveOffRouteCount = 0
    private var maxAccumulatedDelayMinutes = 0
    private var currentTripStartTimestamp = 0L
    private val boardedDriftReconciler = BoardedDriftReconciler()
    private val boardedLegIndices = mutableSetOf<Int>()
    private var lastBoardedLegIndex: Int = -1
    private var lastReconciledLegIndex: Int = -1
    private var lastConfirmedBoardedMinsRemaining: Int? = null
    private var lastBoardedEstimationTimestamp: Long = 0L

    // GTFS-RT Cercanías Real-Time Destination Arrival Cache (Last Known State Retention)
    private var lastKnownCercaniasDestEpochSec: Long? = null
    private var lastKnownCercaniasDelayMinutes: Int = 0
    private var lastKnownCercaniasTripId: String? = null

    // Origin Transit Vehicle Retention & Anti-Jump Cache (Walk Phase)
    private val originCache = TripOriginRealTimeCache()

    fun reset() {
        consecutiveOffRouteCount = 0
        maxAccumulatedDelayMinutes = 0
        currentTripStartTimestamp = 0L
        boardedLegIndices.clear()
        lastBoardedLegIndex = -1
        lastReconciledLegIndex = -1
        lastConfirmedBoardedMinsRemaining = null
        lastBoardedEstimationTimestamp = 0L
        lastKnownCercaniasDestEpochSec = null
        lastKnownCercaniasDelayMinutes = 0
        lastKnownCercaniasTripId = null
        originCache.reset()
        boardedDriftReconciler.reset()
    }

    fun onBoardingConfirmed(leg: PlannedLeg, legIndex: Int, initialDepartureDelayMinutes: Int = maxAccumulatedDelayMinutes) {
        boardedLegIndices.add(legIndex)
        lastBoardedLegIndex = legIndex
        val vehicleIdToLock = originCache.lastMatchedOriginVehicleId
        originCache.clearMatchedOrigin()
        boardedDriftReconciler.onBoardingConfirmed(
            currentLeg = leg,
            currentLegIndex = legIndex,
            initialDepartureDelayMinutes = initialDepartureDelayMinutes,
            vehicleId = vehicleIdToLock
        )
    }

    fun getGracePeriodVehicleName(): String? {
        return originCache.departedVehicleLine
    }

    fun getGracePeriodStartTimeMs(): Long? {
        return originCache.departedVehicleStartMs
    }

    fun getGracePeriodUntilMs(): Long? {
        return originCache.departedVehicleGraceUntilMs
    }

    fun getGracePeriodLiveDepartureEpochMs(): Long? {
        return originCache.departedVehicleLiveDepartureEpochMs
    }

    fun isGracePeriodActive(): Boolean {
        return originCache.isGracePeriodActive()
    }

    fun clearGracePeriod() {
        originCache.clearGracePeriod()
        originCache.clearMatchedOrigin()
    }

    /**
     * Reconciles current active trip state with live transport APIs and user GPS telemetry.
     */
    suspend fun reconcile(
        activeTrip: ActiveTripState,
        userLat: Double?,
        userLon: Double?
    ): RealTimeTripStatus = withContext(Dispatchers.IO) {
        val legs = activeTrip.itinerary.legs
        val currentIdx = activeTrip.currentLegIndex

        if (activeTrip.startTimestamp != currentTripStartTimestamp) {
            currentTripStartTimestamp = activeTrip.startTimestamp
            maxAccumulatedDelayMinutes = 0
            lastReconciledLegIndex = currentIdx
            originCache.reset()
            boardedDriftReconciler.reset()
            consecutiveOffRouteCount = 0
        } else if (currentIdx != lastReconciledLegIndex) {
            lastReconciledLegIndex = currentIdx
            originCache.reset()
            boardedDriftReconciler.reset()
            consecutiveOffRouteCount = 0
        }

        if (legs.isEmpty() || currentIdx >= legs.size) {
            consecutiveOffRouteCount = 0
            return@withContext RealTimeTripStatus(delayMinutes = maxAccumulatedDelayMinutes)
        }

        val currentLeg = legs[currentIdx]
        val isCurrentWalk = currentLeg.mode == TransitMode.WALK

        // 1. Off-Route Detection & Target-Centric Pedestrian Tracking
        val offRouteResult = TripOffRouteEvaluator.evaluate(
            userLat = userLat,
            userLon = userLon,
            currentLeg = currentLeg,
            isCurrentWalk = isCurrentWalk,
            currentConsecutiveOffRouteCount = consecutiveOffRouteCount
        )
        val isOffRoute = offRouteResult.isOffRoute
        val offRouteDist = offRouteResult.offRouteDist
        consecutiveOffRouteCount = offRouteResult.consecutiveOffRouteCount
        val isPedestrianDeviated = offRouteResult.isPedestrianDeviated
        val dynamicWalkMinutesRemaining = offRouteResult.dynamicWalkMinutesRemaining

        // 2. Identify target transit leg for current monitoring
        val (targetTransitIdx, nextTransitLeg) = if (isCurrentWalk && currentIdx + 1 < legs.size) {
            (currentIdx + 1) to legs[currentIdx + 1]
        } else if (!isCurrentWalk) {
            currentIdx to currentLeg
        } else {
            null to null
        }

        if (nextTransitLeg == null) {
            return@withContext RealTimeTripStatus(
                delayMinutes = maxAccumulatedDelayMinutes,
                isOffRoute = false, // Walk or destination reached: never off-route
                offRouteDistanceMeters = offRouteDist,
                consecutiveOffRouteCount = consecutiveOffRouteCount,
                isPedestrianDeviated = isPedestrianDeviated,
                dynamicWalkMinutesRemaining = dynamicWalkMinutesRemaining,
                lastCheckedTimestamp = System.currentTimeMillis()
            )
        }

        val nowMs = System.currentTimeMillis()
        val progressState = ActiveTripProgressTracker.progressState.value
        val isConfirmedBoarded = progressState.isBoarded &&
                (progressState.trackedLegIndex == currentIdx || progressState.trackedLegIndex == -1)
        val isLegBoardedByEngine = TripStepProgressionEngine.isLegBoarded(currentIdx)
        val isExplicitlyBoarded = isConfirmedBoarded ||
                isLegBoardedByEngine ||
                boardedLegIndices.contains(currentIdx) ||
                (targetTransitIdx != null && boardedLegIndices.contains(targetTransitIdx)) ||
                (targetTransitIdx != null && TripStepProgressionEngine.isLegBoarded(targetTransitIdx)) ||
                ActiveTripStateManager.sessionState.value?.isBoarded == true

        val isFarFromOrigin = if (userLat != null && userLon != null && nextTransitLeg.fromLat != 0.0) {
            TripStepProgressionEngine.calculateDistanceMeters(
                userLat, userLon,
                nextTransitLeg.fromLat, nextTransitLeg.fromLon
            ) > 300.0
        } else false
        val isAdvancedAlongLeg = progressState.progressWithinLeg >= 0.15f

        val isAlreadyBoarded = isExplicitlyBoarded || (!isCurrentWalk && (isFarFromOrigin || isAdvancedAlongLeg))

        // Grace Period is applicable if scheduled departure OR last matched live arrival has passed within last 5 minutes
        val scheduledStartMs = TripTimeParser.parseTimeToMillis(
            nextTransitLeg.scheduledStartTime ?: nextTransitLeg.formattedStartTime
        )
        val minsSinceScheduled = if (scheduledStartMs != null && nowMs >= scheduledStartMs) {
            ((nowMs - scheduledStartMs) / 60000L).toInt()
        } else {
            null
        }
        val minsSinceLastMatchedLive = if (originCache.lastMatchedOriginArrivalEpochMs != null && nowMs >= originCache.lastMatchedOriginArrivalEpochMs!!) {
            ((nowMs - originCache.lastMatchedOriginArrivalEpochMs!!) / 60000L).toInt()
        } else {
            null
        }
        val isWithin5MinGracePeriod = (minsSinceScheduled != null && minsSinceScheduled in 0..5) ||
                (minsSinceLastMatchedLive != null && minsSinceLastMatchedLive in 0..5)

        val walkGraceMs = if (isAlreadyBoarded) {
            0L
        } else if (!isCurrentWalk || isWithin5MinGracePeriod) {
            // Platform waiting buffer: when the user is already on the transit leg or within 5 min grace
            -300_000L
        } else if (dynamicWalkMinutesRemaining != null) {
            // Continuous walk grace: smooth subtraction of 5 min platform buffer from estimated walk time (no abrupt cliffs)
            ((dynamicWalkMinutesRemaining * 60 * 1000L).toLong() - 300_000L)
        } else {
            val walkSec = currentLeg.durationSeconds
            ((walkSec * 1000L) - 300_000L)
        }
        val earliestReachableUserArrivalMs = nowMs + walkGraceMs

        var liveMinutes: Int? = null
        var liveSeconds: Int? = null
        var liveDestination: String? = null
        var isLive = false
        var delayMinutes = 0
        var scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null }
        var adjustedDepTime: String? = null
        var normalizedLine = TransitIdMapper.normalizeRouteShortName(nextTransitLeg.mode, nextTransitLeg.routeShortName)

        if (isAlreadyBoarded) {
            if (lastBoardedLegIndex != currentIdx) {
                lastBoardedLegIndex = currentIdx
                lastConfirmedBoardedMinsRemaining = null
                lastBoardedEstimationTimestamp = 0L
            }

            // Relevo de Polling: Bypassear polling de origen y reconciliar corredor / inercial
            val driftUpdate = boardedDriftReconciler.reconcileBoardedLeg(
                leg = nextTransitLeg,
                legIndex = currentIdx,
                nowMs = nowMs
            )

            val boardedResult = BoardedTransitTimeEstimator.estimateBoardedLeg(
                nextTransitLeg = nextTransitLeg,
                nowMs = nowMs,
                driftMinutes = driftUpdate.driftMinutes,
                isLiveFromCorridor = driftUpdate.isLiveFromCorridor,
                lastKnownCercaniasDestEpochSec = lastKnownCercaniasDestEpochSec,
                lastKnownCercaniasDelayMinutes = lastKnownCercaniasDelayMinutes,
                lastConfirmedBoardedMinsRemaining = lastConfirmedBoardedMinsRemaining,
                lastBoardedEstimationTimestamp = lastBoardedEstimationTimestamp
            )

            delayMinutes = boardedResult.delayMinutes
            isLive = boardedResult.isLive
            liveDestination = nextTransitLeg.headsign?.ifBlank { null } ?: nextTransitLeg.toName
            if (boardedResult.retainedTripId != null) {
                lastKnownCercaniasTripId = boardedResult.retainedTripId
            }
            if (boardedResult.retainedDestEpochSec != null) {
                lastKnownCercaniasDestEpochSec = boardedResult.retainedDestEpochSec
            }
            lastKnownCercaniasDelayMinutes = boardedResult.retainedDelayMinutes
            lastConfirmedBoardedMinsRemaining = boardedResult.confirmedBoardedMinsRemaining
            lastBoardedEstimationTimestamp = nowMs

            liveMinutes = boardedResult.liveMinutes
            liveSeconds = boardedResult.liveSeconds

            // Departure time is strictly frozen when boarded!
            adjustedDepTime = nextTransitLeg.scheduledStartTime ?: nextTransitLeg.formattedStartTime.ifBlank { null }
        } else {
            val distToOriginStation = if (userLat != null && userLon != null && nextTransitLeg.fromLat != 0.0) {
                TripStepProgressionEngine.calculateDistanceMeters(
                    userLat, userLon,
                    nextTransitLeg.fromLat, nextTransitLeg.fromLon
                )
            } else null

            val isUserPhysicallyAtStation = (distToOriginStation != null && distToOriginStation <= 250.0) ||
                    (dynamicWalkMinutesRemaining != null && dynamicWalkMinutesRemaining <= 2) ||
                    (userLat == null || userLat == 0.0) // In tunnels or stations with weak/no GPS, preserve station presence

            // 3. Reconcile Current Transit Leg (Bus, Metro, Cercanías) en origen
            val legLiveResult = reconcileSingleTransitLeg(
                leg = nextTransitLeg,
                nowMs = nowMs,
                earliestReachableMs = earliestReachableUserArrivalMs,
                isCurrentWalk = isCurrentWalk,
                isBoarded = isAlreadyBoarded
            )

            val cacheResult = originCache.updateWithLiveResult(
                legLiveResult = legLiveResult,
                nextTransitLeg = nextTransitLeg,
                isUserPhysicallyAtStation = isUserPhysicallyAtStation
            )
            liveMinutes = cacheResult.liveMinutes
            liveSeconds = cacheResult.liveSeconds
            liveDestination = cacheResult.liveDestination
            isLive = cacheResult.isLive
            delayMinutes = cacheResult.delayMinutes
            scheduledDepTime = cacheResult.scheduledDepTime
            adjustedDepTime = cacheResult.adjustedDepTime
            normalizedLine = cacheResult.normalizedLine
        }

        // 4. Checkpoint ETA & Transfer Monitoring
        var checkpointEtaMinutes: Int? = null
        var isCheckpointLive = false

        if (isAlreadyBoarded && liveMinutes != null) {
            checkpointEtaMinutes = liveMinutes
            isCheckpointLive = isLive
        }

        // 5. Dynamic Multi-Leg Transfer Monitoring (Windowing & M_enlace)
        val (walkTransferLeg, nextTransferTransitLeg) = TripRealTimeTransferEvaluator.findNextTransferPair(legs, nextTransitLeg)

        var transferMarginMinutes: Int? = null
        var isTransferAtRisk = false
        var transferWarningEs: String? = null
        var transferWarningCa: String? = null
        var upcomingTransferInfoEs: String? = null
        var upcomingTransferInfoCa: String? = null
        var isUpcomingTransferLive = false
        var upcomingTransferLine: String? = null
        var upcomingTransferMinutes: Int? = null

        if (nextTransferTransitLeg != null && isAlreadyBoarded) {
            val transferLineName = nextTransferTransitLeg.routeShortName?.ifBlank { null }
                ?: nextTransferTransitLeg.mode.displayNameEs

            val transferWalkMinutes = ((walkTransferLeg?.durationSeconds ?: 120L) / 60).toInt().coerceAtLeast(1)
            val currentLegRemainingMins = if (isAlreadyBoarded) {
                if (liveMinutes != null && liveMinutes > 0) {
                    liveMinutes
                } else {
                    val theoreticalEnd = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
                        ?: SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(nextTransitLeg.formattedEndTime)
                    (theoreticalEnd ?: (nextTransitLeg.durationSeconds / 60).toInt()).coerceAtLeast(1)
                }
            } else if (isCurrentWalk) {
                val walkToStartMins = dynamicWalkMinutesRemaining ?: ((currentLeg.durationSeconds / 60).toInt().coerceAtLeast(1))
                val waitAtStop = (liveMinutes ?: 0).coerceAtLeast(0)
                val legDurationMins = (nextTransitLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
                maxOf(walkToStartMins, waitAtStop) + legDurationMins
            } else if (isLive && liveMinutes != null) {
                liveMinutes + (nextTransitLeg.durationSeconds / 60).toInt()
            } else {
                (nextTransitLeg.durationSeconds / 60).toInt()
            }

            // Expected user arrival at the transfer boarding stop from NOW:
            val userMinutesUntilTransferBoarding = currentLegRemainingMins + transferWalkMinutes
            val userTransferArrivalEpochMs = nowMs + (userMinutesUntilTransferBoarding * 60 * 1000L)

            upcomingTransferLine = transferLineName

            // Check if transfer leg is within operational Real-Time window (< 60 minutes away)
            if (userMinutesUntilTransferBoarding <= 60) {
                val transferLiveResult = reconcileSingleTransitLeg(
                    leg = nextTransferTransitLeg,
                    nowMs = nowMs,
                    earliestReachableMs = userTransferArrivalEpochMs // Realistic platform arrival
                )

                val evalResult = TripRealTimeTransferEvaluator.evaluateTransferState(
                    nextTransferTransitLeg = nextTransferTransitLeg,
                    transferWalkMinutes = transferWalkMinutes,
                    currentLegRemainingMins = currentLegRemainingMins,
                    nowMs = nowMs,
                    userMinutesUntilTransferBoarding = userMinutesUntilTransferBoarding,
                    userTransferArrivalEpochMs = userTransferArrivalEpochMs,
                    transferLiveResult = transferLiveResult,
                    delayMinutes = delayMinutes,
                    isAlreadyBoarded = isAlreadyBoarded
                )
                transferMarginMinutes = evalResult.transferMarginMinutes
                isTransferAtRisk = evalResult.isTransferAtRisk
                transferWarningEs = evalResult.transferWarningEs
                transferWarningCa = evalResult.transferWarningCa
                upcomingTransferInfoEs = evalResult.upcomingTransferInfoEs
                upcomingTransferInfoCa = evalResult.upcomingTransferInfoCa
                isUpcomingTransferLive = evalResult.isUpcomingTransferLive
                upcomingTransferLine = evalResult.upcomingTransferLine
                upcomingTransferMinutes = evalResult.upcomingTransferMinutes
            } else {
                // Outside real-time window (e.g. 40-50 min in the future) -> Show official scheduled time
                val scheduledTime = nextTransferTransitLeg.formattedStartTime
                isUpcomingTransferLive = false
                upcomingTransferInfoEs = "Próximo transbordo: $transferLineName a las $scheduledTime • Programado"
                upcomingTransferInfoCa = "Pròxim transbordament: $transferLineName a les $scheduledTime • Programat"
            }
        }

        // 6. Evaluate "Sal ya" Rule and "Sal en X min" countdown
        val leaveNowEval = if (isCurrentWalk) {
            TripRealTimeTransferEvaluator.evaluateLeaveNow(
                currentLeg = currentLeg,
                nextTransitLeg = nextTransitLeg,
                userLat = userLat,
                userLon = userLon,
                liveMinutes = liveMinutes,
                normalizedLine = normalizedLine
            )
        } else {
            LeaveNowEvaluationResult()
        }
        val isLeaveNow = leaveNowEval.isLeaveNow
        val leaveInMinutes = leaveNowEval.leaveInMinutes
        val leaveNowEs = leaveNowEval.leaveNowEs
        val leaveNowCa = leaveNowEval.leaveNowCa

        if (!isCurrentWalk) {
            val currentProgressInfo = ActiveTripProgressTracker.progressState.value

            val isEngineBoarded = TripStepProgressionEngine.isLegBoarded(currentIdx)
            if (!currentProgressInfo.isBoarded && !isEngineBoarded) {
                ActiveTripProgressTracker.updateProgress(
                    progressWithinLeg = currentProgressInfo.progressWithinLeg,
                    waitTimeMessage = currentProgressInfo.waitTimeMessage,
                    statusDetail = currentProgressInfo.statusDetail,
                    isDeadReckoning = currentProgressInfo.isDeadReckoning,
                    isBoarded = false,
                    transitDepartureTimeMs = currentProgressInfo.transitDepartureTimeMs,
                    lastSeenArrivalMins = liveMinutes,
                    legIndex = currentIdx
                )
            } else {
                boardedDriftReconciler.onBoardingConfirmed(
                    currentLeg = nextTransitLeg,
                    currentLegIndex = currentIdx,
                    initialDepartureDelayMinutes = delayMinutes,
                    vehicleId = originCache.lastMatchedOriginVehicleId
                )
            }
        }

        if (delayMinutes in 1..45 && delayMinutes > maxAccumulatedDelayMinutes) {
            maxAccumulatedDelayMinutes = delayMinutes
        }
        val effectiveDelay = minOf(maxOf(delayMinutes, maxAccumulatedDelayMinutes), 45)

        RealTimeTripStatus(
            vehicleLine = normalizedLine.ifBlank { nextTransitLeg.routeShortName },
            vehicleDestination = liveDestination,
            vehicleArrivalMinutes = liveMinutes,
            vehicleSecondsRemaining = liveSeconds,
            delayMinutes = effectiveDelay,
            isLive = isLive,
            isLeaveNowAlert = isLeaveNow,
            leaveInMinutes = leaveInMinutes,
            leaveNowMessageEs = leaveNowEs,
            leaveNowMessageCa = leaveNowCa,
            isOffRoute = isOffRoute,
            offRouteDistanceMeters = offRouteDist,
            consecutiveOffRouteCount = consecutiveOffRouteCount,
            isPedestrianDeviated = isPedestrianDeviated,
            dynamicWalkMinutesRemaining = dynamicWalkMinutesRemaining,
            checkpointEtaMinutes = checkpointEtaMinutes,
            isCheckpointLive = isCheckpointLive,
            transferMarginMinutes = transferMarginMinutes,
            isTransferAtRisk = isTransferAtRisk,
            transferWarningEs = transferWarningEs,
            transferWarningCa = transferWarningCa,
            upcomingTransferInfoEs = upcomingTransferInfoEs,
            upcomingTransferInfoCa = upcomingTransferInfoCa,
            isUpcomingTransferLive = isUpcomingTransferLive,
            upcomingTransferLine = upcomingTransferLine,
            upcomingTransferMinutes = upcomingTransferMinutes,
            scheduledDepartureTime = scheduledDepTime,
            adjustedDepartureTime = adjustedDepTime,
            lastCheckedTimestamp = System.currentTimeMillis()
        )
    }

    private suspend fun reconcileSingleTransitLeg(
        leg: PlannedLeg,
        nowMs: Long,
        earliestReachableMs: Long,
        isCurrentWalk: Boolean = false,
        isBoarded: Boolean = false
    ): LegReconciliationResult {
        return SingleTransitLegReconciler.reconcile(
            leg = leg,
            nowMs = nowMs,
            earliestReachableMs = earliestReachableMs,
            isCurrentWalk = isCurrentWalk,
            isBoarded = isBoarded,
            lastMatchedOriginVehicleKey = originCache.lastMatchedOriginVehicleKey,
            onMatchedOrigin = { key, vehicleId, epochMs ->
                originCache.recordMatchedCandidate(key, vehicleId, epochMs)
            }
        )
    }

    companion object {
        private const val TAG = "TripRealTimeReconciler"

        fun syncRealTimeItinerary(
            itinerary: com.example.data.model.routing.PlannedItinerary,
            status: RealTimeTripStatus,
            currentLegIndex: Int
        ): com.example.data.model.routing.PlannedItinerary {
            return TripItineraryTimeSyncEngine.syncRealTimeItinerary(
                itinerary = itinerary,
                status = status,
                currentLegIndex = currentLegIndex
            )
        }
    }
}
