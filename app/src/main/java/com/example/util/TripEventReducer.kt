package com.example.util

import com.example.data.model.routing.TransitMode
import com.example.data.model.trip.ActiveTripSessionState
import com.example.data.model.trip.UnifiedActiveTripSnapshot
import com.example.ui.dashboard.AppLanguage

/**
 * Pure domain reducer that computes the next [ActiveTripSessionState] ("La Pizarra")
 * from an incoming [TripEvent], codifying all test-user rules as first-class domain invariants:
 * - Rule 1: 120s live departure courtesy window (retains departed vehicle)
 * - Rule 2: Single-latch per-leg "Sal Ya" walking alert
 * - Rule 3: Monotonic non-decreasing stop progression lock
 * - Rule 4: Real-time boarding departure time inertial dead-reckoning
 * - Rule 5: Imminent debarkation notice on penultimate stop approach
 */
object TripEventReducer {

    fun reduce(
        currentState: ActiveTripSessionState?,
        event: TripEvent,
        appLanguage: AppLanguage = AppLanguage.ES
    ): ActiveTripSessionState? {
        if (currentState == null && event !is TripEvent.ActiveTripUpdated) {
            return null
        }

        return when (event) {
            is TripEvent.ActiveTripUpdated -> {
                val trip = event.trip
                val snapshot = ActiveTripSnapshotBuilder.build(
                    activeTrip = trip,
                    progressInfo = currentState?.progressInfo ?: ActiveProgressInfo(),
                    realTimeStatus = currentState?.realTimeStatus,
                    appLanguage = appLanguage
                )
                fromSnapshot(snapshot)
            }
            is TripEvent.BoardingConfirmed -> {
                if (currentState == null) return null
                val updatedProgress = currentState.progressInfo.copy(
                    isBoarded = true,
                    transitDepartureTimeMs = if (currentState.progressInfo.transitDepartureTimeMs > 0L) {
                        currentState.progressInfo.transitDepartureTimeMs
                    } else System.currentTimeMillis(),
                    trackedLegIndex = event.legIndex
                )
                val snapshot = ActiveTripSnapshotBuilder.build(
                    activeTrip = currentState.activeTrip,
                    progressInfo = updatedProgress,
                    realTimeStatus = currentState.realTimeStatus,
                    appLanguage = appLanguage
                )
                fromSnapshot(snapshot).copy(isBoarded = true)
            }
            is TripEvent.LegAdvanced -> {
                if (currentState == null) return null
                val updatedTrip = currentState.activeTrip.copy(
                    currentLegIndex = event.newLegIndex,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
                val newProgress = ActiveProgressInfo(
                    progressWithinLeg = 0.0f,
                    isBoarded = false,
                    trackedLegIndex = event.newLegIndex
                )
                val snapshot = ActiveTripSnapshotBuilder.build(
                    activeTrip = updatedTrip,
                    progressInfo = newProgress,
                    realTimeStatus = currentState.realTimeStatus,
                    appLanguage = appLanguage
                )
                fromSnapshot(snapshot)
            }
            is TripEvent.RealTimeStatusUpdated -> {
                if (currentState == null) return null
                val snapshot = ActiveTripSnapshotBuilder.build(
                    activeTrip = currentState.activeTrip,
                    progressInfo = currentState.progressInfo,
                    realTimeStatus = event.status,
                    appLanguage = appLanguage
                )
                fromSnapshot(snapshot)
            }
            is TripEvent.LocationUpdated -> {
                if (currentState == null) return null
                val currentLeg = currentState.activeTrip.itinerary.legs.getOrNull(currentState.currentLegIndex)
                if (currentLeg != null && currentLeg.mode != TransitMode.WALK && currentLeg.mode != TransitMode.BICYCLE) {
                    val progression = TripStepProgressionEngine.evaluateProgression(
                        userLat = event.latitude,
                        userLon = event.longitude,
                        activeTrip = currentState.activeTrip,
                        locationAccuracyMeters = event.accuracyMeters,
                        lastLocationTimeMillis = event.timestampMs
                    )
                    val updatedProgress = ActiveTripProgressTracker.progressState.value
                    val snapshot = ActiveTripSnapshotBuilder.build(
                        activeTrip = currentState.activeTrip,
                        progressInfo = updatedProgress,
                        realTimeStatus = currentState.realTimeStatus,
                        appLanguage = appLanguage
                    )
                    fromSnapshot(snapshot)
                } else {
                    currentState
                }
            }
            is TripEvent.Tick -> {
                if (currentState == null) return null
                val snapshot = ActiveTripSnapshotBuilder.build(
                    activeTrip = currentState.activeTrip,
                    progressInfo = currentState.progressInfo,
                    realTimeStatus = currentState.realTimeStatus,
                    appLanguage = appLanguage
                )
                fromSnapshot(snapshot)
            }
        }
    }

    private fun fromSnapshot(snapshot: UnifiedActiveTripSnapshot): ActiveTripSessionState {
        return ActiveTripSessionState(
            activeTrip = snapshot.activeTrip,
            currentLegIndex = snapshot.currentLegIndex,
            currentLeg = snapshot.currentLeg,
            progressInfo = snapshot.progressInfo,
            realTimeStatus = snapshot.realTimeStatus,
            formattedUiState = snapshot.formattedUiState,
            isBoarded = snapshot.isBoarded,
            isOffRoute = snapshot.isOffRoute,
            isLive = snapshot.isLive,
            isLeaveNowAlert = snapshot.isLeaveNowAlert,
            isImminentDebark = snapshot.isImminentDebark,
            isTransferAtRisk = snapshot.isTransferAtRisk,
            urgencyLevel = snapshot.urgencyLevel,
            shouldShowBoardingConfirmation = snapshot.shouldShowBoardingConfirmation,
            candidateTransitLeg = snapshot.candidateTransitLeg,
            candidateLegIndex = snapshot.candidateLegIndex,
            candidateLineBadge = snapshot.candidateLineBadge,
            lastUpdatedTimestamp = snapshot.timestamp
        )
    }
}
