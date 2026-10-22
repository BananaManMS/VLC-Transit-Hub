package com.example.data.model.trip

import com.example.data.model.routing.PlannedLeg
import com.example.data.repository.ActiveTripState
import com.example.util.ActiveProgressInfo
import com.example.util.TripFormattedUIState
import com.example.util.RealTimeTripStatus
import com.example.util.TripUrgencyLevel

data class UnifiedActiveTripSnapshot(
    val activeTrip: ActiveTripState,
    val currentLegIndex: Int = 0,
    val currentLeg: PlannedLeg? = null,
    val progressInfo: ActiveProgressInfo = ActiveProgressInfo(),
    val realTimeStatus: RealTimeTripStatus? = null,
    val formattedUiState: TripFormattedUIState,
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
    val timestamp: Long = System.currentTimeMillis()
)
