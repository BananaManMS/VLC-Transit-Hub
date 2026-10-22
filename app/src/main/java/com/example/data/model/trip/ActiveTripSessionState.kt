package com.example.data.model.trip

import com.example.data.model.routing.PlannedLeg
import com.example.data.repository.ActiveTripState
import com.example.util.ActiveProgressInfo
import com.example.util.RealTimeTripStatus
import com.example.util.TripFormattedUIState
import com.example.util.TripUrgencyLevel

/**
 * Single source of truth for active trip session state ("La Pizarra de Estado").
 * Unifies active trip entity, leg progress, real-time status, and UI state
 * into a single immutable data structure.
 */
data class ActiveTripSessionState(
    val activeTrip: ActiveTripState,
    val currentLegIndex: Int = 0,
    val currentLeg: PlannedLeg? = null,
    val progressInfo: ActiveProgressInfo = ActiveProgressInfo(),
    val realTimeStatus: RealTimeTripStatus? = null,
    val formattedUiState: TripFormattedUIState? = null,
    val isBoarded: Boolean = false,
    val isOffRoute: Boolean = false,
    val isLive: Boolean = false,
    val isLeaveNowAlert: Boolean = false,
    val isImminentDebark: Boolean = false,
    val isTransferAtRisk: Boolean = false,
    val urgencyLevel: TripUrgencyLevel = TripUrgencyLevel.RELAXED,
    val shouldShowBoardingConfirmation: Boolean = false,
    val candidateTransitLeg: PlannedLeg? = null,
    val candidateLegIndex: Int = -1,
    val candidateLineBadge: String = "",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    fun toSnapshot(): UnifiedActiveTripSnapshot? {
        val uiState = formattedUiState ?: return null
        return UnifiedActiveTripSnapshot(
            activeTrip = activeTrip,
            currentLegIndex = currentLegIndex,
            currentLeg = currentLeg,
            progressInfo = progressInfo,
            realTimeStatus = realTimeStatus,
            formattedUiState = uiState,
            isBoarded = isBoarded,
            isOffRoute = isOffRoute,
            isLive = isLive,
            isLeaveNowAlert = isLeaveNowAlert,
            isImminentDebark = isImminentDebark,
            isTransferAtRisk = isTransferAtRisk,
            urgencyLevel = urgencyLevel,
            shouldShowBoardingConfirmation = shouldShowBoardingConfirmation,
            candidateTransitLeg = candidateTransitLeg,
            candidateLegIndex = candidateLegIndex,
            candidateLineBadge = candidateLineBadge,
            timestamp = lastUpdatedTimestamp
        )
    }
}
