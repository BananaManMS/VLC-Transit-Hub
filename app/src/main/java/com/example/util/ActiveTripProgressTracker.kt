package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveProgressInfo(
    val progressWithinLeg: Float = 0.0f,
    val waitTimeMessage: String? = null,
    val statusDetail: String? = null,
    val isDeadReckoning: Boolean = false,
    val isBoarded: Boolean = false,
    val transitDepartureTimeMs: Long = 0L,
    val lastSeenArrivalMins: Int? = null,
    val trackedLegIndex: Int = -1,
    val distanceToTargetMeters: Double? = null,
    val isPedestrianDeviated: Boolean = false,
    val dynamicWalkMinutesRemaining: Int? = null,
    val remainingStopsCount: Int? = null
)

object ActiveTripProgressTracker {
    private val _progressState = MutableStateFlow(ActiveProgressInfo())
    val progressState: StateFlow<ActiveProgressInfo> = _progressState.asStateFlow()

    fun updateProgress(
        progressWithinLeg: Float,
        waitTimeMessage: String? = null,
        statusDetail: String? = null,
        isDeadReckoning: Boolean = false,
        isBoarded: Boolean? = null,
        transitDepartureTimeMs: Long? = null,
        lastSeenArrivalMins: Int? = null,
        legIndex: Int = -1,
        distanceToTargetMeters: Double? = null,
        isPedestrianDeviated: Boolean = false,
        dynamicWalkMinutesRemaining: Int? = null,
        remainingStopsCount: Int? = null
    ) {
        val current = _progressState.value
        val isNewLeg = legIndex != -1 && legIndex != current.trackedLegIndex

        val newBoarded = if (isNewLeg) (isBoarded ?: false) else (isBoarded ?: current.isBoarded)
        val newDepartureMs = if (isNewLeg) {
            (transitDepartureTimeMs ?: 0L)
        } else {
            transitDepartureTimeMs ?: current.transitDepartureTimeMs
        }
        val newLastSeen = if (isNewLeg) lastSeenArrivalMins else (lastSeenArrivalMins ?: current.lastSeenArrivalMins)
        val newRemainingStops = if (isNewLeg) remainingStopsCount else (remainingStopsCount ?: current.remainingStopsCount)

        _progressState.value = ActiveProgressInfo(
            progressWithinLeg = progressWithinLeg.coerceIn(0.0f, 1.0f),
            waitTimeMessage = waitTimeMessage,
            statusDetail = statusDetail,
            isDeadReckoning = isDeadReckoning,
            isBoarded = newBoarded,
            transitDepartureTimeMs = newDepartureMs,
            lastSeenArrivalMins = newLastSeen,
            trackedLegIndex = if (legIndex != -1) legIndex else current.trackedLegIndex,
            distanceToTargetMeters = distanceToTargetMeters ?: current.distanceToTargetMeters,
            isPedestrianDeviated = isPedestrianDeviated,
            dynamicWalkMinutesRemaining = dynamicWalkMinutesRemaining ?: current.dynamicWalkMinutesRemaining,
            remainingStopsCount = newRemainingStops
        )
    }

    /**
     * Explicitly resets progress and boarding state when transitioning to a new leg in a transfer or step progression.
     * Guarantees the next leg begins in waiting/unboarded state with fresh departure clock and real-time reconciliation.
     */
    fun resetForNewLeg(newLegIndex: Int) {
        _progressState.value = ActiveProgressInfo(
            progressWithinLeg = 0.0f,
            waitTimeMessage = null,
            statusDetail = null,
            isDeadReckoning = false,
            isBoarded = false,
            transitDepartureTimeMs = 0L,
            lastSeenArrivalMins = null,
            trackedLegIndex = newLegIndex,
            distanceToTargetMeters = null,
            isPedestrianDeviated = false,
            dynamicWalkMinutesRemaining = null,
            remainingStopsCount = null
        )
    }

    fun markAsBoarded(legIndex: Int = -1) {
        val current = _progressState.value
        val now = System.currentTimeMillis()
        _progressState.value = current.copy(
            isBoarded = true,
            lastSeenArrivalMins = null,
            transitDepartureTimeMs = if (current.transitDepartureTimeMs == 0L) now else current.transitDepartureTimeMs,
            trackedLegIndex = if (legIndex != -1) legIndex else current.trackedLegIndex
        )
    }

    fun reset() {
        _progressState.value = ActiveProgressInfo()
    }
}
