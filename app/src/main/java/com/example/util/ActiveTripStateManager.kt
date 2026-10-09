package com.example.util

import com.example.data.model.trip.ActiveTripSessionState
import com.example.data.repository.ActiveTripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authoritative single State Bus ("La Pizarra de Estado") managing [ActiveTripSessionState].
 * Seamlessly bridges updates to [UnifiedActiveTripStateTracker] and handles key event
 * persistence to Room DB [ActiveTripRepository].
 */
object ActiveTripStateManager {

    private val _sessionState = MutableStateFlow<ActiveTripSessionState?>(null)
    val sessionState: StateFlow<ActiveTripSessionState?> = _sessionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Updates the single session state ("La Pizarra") and automatically propagates
     * the snapshot to UI consumers via [UnifiedActiveTripStateTracker].
     */
    fun updateState(
        newState: ActiveTripSessionState?,
        repository: ActiveTripRepository? = null
    ) {
        val previousState = _sessionState.value
        _sessionState.value = newState

        // Propagate snapshot to UnifiedActiveTripStateTracker for backward compatibility with UI
        UnifiedActiveTripStateTracker.updateSnapshot(newState?.toSnapshot())

        // Sync key events (leg progression or completion) to Room DB
        if (newState != null && repository != null) {
            val hasLegAdvanced = previousState != null && newState.currentLegIndex != previousState.currentLegIndex
            if (hasLegAdvanced) {
                scope.launch {
                    try {
                        repository.advanceLegIndex(newState.currentLegIndex)
                    } catch (e: Exception) {
                        android.util.Log.e("ActiveTripStateManager", "Error syncing leg index advance to Room: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Directly updates state from a newly built snapshot for Phase 1 compatibility.
     */
    fun updateFromSnapshot(
        snapshot: com.example.data.model.trip.UnifiedActiveTripSnapshot?,
        repository: ActiveTripRepository? = null
    ) {
        if (snapshot == null) {
            updateState(null, repository)
            return
        }

        val state = ActiveTripSessionState(
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
        updateState(state, repository)
    }

    /**
     * Dispatches an event to [TripEventReducer], updating the session state
     * and syncing key events to Room DB.
     */
    fun dispatchEvent(
        event: TripEvent,
        repository: ActiveTripRepository? = null,
        appLanguage: com.example.ui.dashboard.AppLanguage = com.example.ui.dashboard.AppLanguage.ES
    ) {
        val currentState = _sessionState.value
        val newState = TripEventReducer.reduce(currentState, event, appLanguage)
        updateState(newState, repository)
    }

    /**
     * Resets the active session state cleanly.
     */
    fun reset() {
        _sessionState.value = null
        UnifiedActiveTripStateTracker.reset()
    }
}
