package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.routing.TransitIdMapper

/**
 * Normalized model for real-time arrival/departure candidate from any transit agency (EMT, Metrovalencia, Renfe, Metrobus).
 */
data class TransitArrivalCandidate(
    val line: String,
    val destination: String,
    val minutes: Int,
    val seconds: Int = minutes * 60,
    val vehicleId: String? = null,
    val rawStopId: String? = null,
    val isRealTime: Boolean = true
)

/**
 * Pure evaluation and matching engine to select the best arrival/departure candidate
 * for a planned transit leg, accounting for reachable departure time, schedules,
 * line alternatives and destination headsigns.
 */
object TripVehicleMatcher {

    /**
     * Matches best arrival candidate for EMT Bus or Metro.
     * Returns: Selected candidate, arrival minutes, delay minutes.
     */
    fun matchBestCandidate(
        candidates: List<TransitArrivalCandidate>,
        nowMs: Long,
        earliestReachableMs: Long,
        isCurrentWalk: Boolean,
        isBoarded: Boolean,
        theoreticalMinutesRemaining: Int?,
        lastMatchedOriginVehicleKey: String?,
        vehicleKeyPrefix: String
    ): Triple<TransitArrivalCandidate, Int, Int>? {
        if (candidates.isEmpty()) return null

        val validCandidates = candidates.filter { arr ->
            val liveArrivalMs = nowMs + (arr.seconds * 1000L)
            val isReachable = if (isCurrentWalk) arr.seconds >= -60 else liveArrivalMs >= earliestReachableMs

            // Prevent automatically suggesting/matching earlier departures than scheduled
            // unless user is already confirmed boarded. A tolerance of -2 mins is allowed for clock variance.
            val isNotPremature = if (!isBoarded && theoreticalMinutesRemaining != null) {
                arr.minutes >= (theoreticalMinutesRemaining - 2)
            } else true

            isReachable && isNotPremature
        }

        if (validCandidates.isEmpty()) return null

        val previouslyMatched = if (lastMatchedOriginVehicleKey != null) {
            validCandidates.find { candidate ->
                "${vehicleKeyPrefix}_${candidate.rawStopId}_${candidate.line}_${candidate.destination}" == lastMatchedOriginVehicleKey
            }
        } else null

        val matched = previouslyMatched ?: if (theoreticalMinutesRemaining != null) {
            validCandidates.minByOrNull { arr ->
                val diff = arr.minutes - theoreticalMinutesRemaining
                if (diff >= -2) diff else (kotlin.math.abs(diff) + 50)
            }
        } else {
            validCandidates.minByOrNull { it.seconds }
        } ?: return null

        val delayM = if (theoreticalMinutesRemaining != null) {
            (matched.minutes - theoreticalMinutesRemaining).coerceAtLeast(0)
        } else 0

        return Triple(matched, matched.minutes, delayM)
    }

    /**
     * Verifies if a given destination matches the planned leg destination or intermediate stops.
     */
    fun isDestinationMatch(depDestination: String, leg: PlannedLeg): Boolean {
        return TransitIdMapper.isDestinationMatch(depDestination, leg)
    }
}
