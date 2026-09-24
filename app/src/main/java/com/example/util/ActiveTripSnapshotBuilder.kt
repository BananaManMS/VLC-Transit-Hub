package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.model.trip.UnifiedActiveTripSnapshot
import com.example.data.repository.ActiveTripState
import com.example.ui.dashboard.AppLanguage

/**
 * Factory for building canonical [UnifiedActiveTripSnapshot] instances.
 * Centralizes calculations for candidate transit legs, boarding prompt activation,
 * debark warnings, and formatted texts so all consumers receive identical state.
 */
object ActiveTripSnapshotBuilder {

    fun build(
        activeTrip: ActiveTripState,
        progressInfo: ActiveProgressInfo,
        realTimeStatus: RealTimeTripStatus?,
        appLanguage: AppLanguage = AppLanguage.CA
    ): UnifiedActiveTripSnapshot {
        val itinerary = activeTrip.itinerary
        val legs = itinerary.legs
        val currentLegIndex = activeTrip.currentLegIndex.coerceIn(0, (legs.size - 1).coerceAtLeast(0))
        val currentLeg = legs.getOrNull(currentLegIndex)

        val isBoarded = (progressInfo.isBoarded && (progressInfo.trackedLegIndex == currentLegIndex || progressInfo.trackedLegIndex == -1)) ||
                TripStepProgressionEngine.isLegBoarded(currentLegIndex)
        val isOffRoute = realTimeStatus?.isOffRoute == true
        val isLive = realTimeStatus?.isLive == true

        // 1. Formatted UI presentation model
        val formattedUiState = TripUIStateFormatter.format(
            currentLeg = currentLeg,
            currentLegIndex = currentLegIndex,
            totalLegs = legs.size,
            realTimeStatus = realTimeStatus,
            isBoarded = isBoarded,
            scheduledArrivalTime = itinerary.formattedArrivalTime,
            appLanguage = appLanguage,
            distanceToTargetMeters = progressInfo.distanceToTargetMeters,
            allLegs = legs
        )

        // 2. Candidate Transit Leg for Onboard Confirmation Chip ("¿A bordo?")
        val (candidateTransitLeg, candidateLegIndex) = when {
            currentLeg?.mode in listOf(TransitMode.SUBWAY, TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL) -> {
                Pair(currentLeg, currentLegIndex)
            }
            currentLeg?.mode == TransitMode.WALK && currentLegIndex + 1 < legs.size &&
                    legs[currentLegIndex + 1].mode in listOf(TransitMode.SUBWAY, TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL) -> {
                val distToStation = progressInfo.distanceToTargetMeters
                val walkMins = progressInfo.dynamicWalkMinutesRemaining
                val isAtStation = (distToStation != null && distToStation <= 60.0) || (walkMins != null && walkMins <= 1)
                if (isAtStation) Pair(legs[currentLegIndex + 1], currentLegIndex + 1) else Pair(null, -1)
            }
            else -> Pair(null, -1)
        }

        val isCandidateBoarded = if (candidateLegIndex >= 0) {
            (progressInfo.isBoarded && progressInfo.trackedLegIndex == candidateLegIndex) ||
                    TripStepProgressionEngine.isLegBoarded(candidateLegIndex)
        } else {
            true
        }

        // 3. Boarding confirmation visibility window (-3..+5 min or <= 3 min live ETA)
        val shouldShowBoardingConfirmation = if (candidateTransitLeg == null || isCandidateBoarded) {
            false
        } else {
            val scheduledDepMs = TripTimeParser.parseTimeToMillis(candidateTransitLeg.startTime)
            val nowMs = System.currentTimeMillis()
            val minsSinceScheduled = if (scheduledDepMs != null) ((nowMs - scheduledDepMs) / 60000L).toInt() else null

            val realTimeMins = realTimeStatus?.vehicleArrivalMinutes
            val realTimeSecs = realTimeStatus?.vehicleSecondsRemaining

            when {
                realTimeMins != null -> realTimeMins <= 3
                realTimeSecs != null -> realTimeSecs <= 180
                minsSinceScheduled != null -> minsSinceScheduled in -3..5
                else -> true
            }
        }

        val candidateLineBadge = candidateTransitLeg?.routeShortName
            ?: candidateTransitLeg?.routeLongName
            ?: candidateTransitLeg?.mode?.name
            ?: ""

        // 4. Imminent debark evaluation
        val remainingStops = progressInfo.remainingStopsCount
        val arrivalMins = TripUIStateFormatter.getDynamicVehicleArrivalMinutes(realTimeStatus) ?: progressInfo.lastSeenArrivalMins
        val arrivalSecs = realTimeStatus?.vehicleSecondsRemaining
        val distToTarget = progressInfo.distanceToTargetMeters

        val isImminentDebark = isBoarded && currentLeg != null && (
                (remainingStops != null && remainingStops == 1 && TripUIStateFormatter.isNearPenultimateStopOrTime(currentLeg, arrivalMins ?: 99, distToTarget, progressInfo.progressWithinLeg)) ||
                (arrivalSecs != null && arrivalSecs <= 120) ||
                (arrivalMins != null && arrivalMins <= 2) ||
                (distToTarget != null && distToTarget <= 250.0)
        )

        return UnifiedActiveTripSnapshot(
            activeTrip = activeTrip,
            currentLegIndex = currentLegIndex,
            currentLeg = currentLeg,
            progressInfo = progressInfo,
            realTimeStatus = realTimeStatus,
            formattedUiState = formattedUiState,
            isBoarded = isBoarded,
            isOffRoute = isOffRoute,
            isLive = isLive,
            isLeaveNowAlert = realTimeStatus?.isLeaveNowAlert == true,
            isImminentDebark = isImminentDebark,
            isTransferAtRisk = realTimeStatus?.isTransferAtRisk == true,
            urgencyLevel = formattedUiState.urgencyLevel,
            shouldShowBoardingConfirmation = shouldShowBoardingConfirmation,
            candidateTransitLeg = candidateTransitLeg,
            candidateLegIndex = candidateLegIndex,
            candidateLineBadge = candidateLineBadge,
            timestamp = System.currentTimeMillis()
        )
    }
}
