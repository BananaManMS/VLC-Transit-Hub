package com.example.util

import com.example.data.model.routing.PlannedLeg

/**
 * Result data holder after reconciling with origin cache.
 */
data class OriginCacheReconciliationResult(
    val isLive: Boolean,
    val liveMinutes: Int?,
    val liveSeconds: Int?,
    val liveDestination: String?,
    val delayMinutes: Int,
    val scheduledDepTime: String?,
    val adjustedDepTime: String?,
    val normalizedLine: String
)

/**
 * Encapsulates origin transit vehicle retention, anti-jump caching during walking phases,
 * and graceful decay during temporary telemetry network micro-drops.
 */
class TripOriginRealTimeCache {

    var lastMatchedOriginVehicleKey: String? = null
    var lastMatchedOriginVehicleId: String? = null
    var lastMatchedOriginArrivalEpochMs: Long? = null

    // Tracked vehicle state (the vehicle currently approaching or at the station)
    var trackedVehicleKey: String? = null
    var trackedVehicleId: String? = null
    var trackedVehicleLine: String? = null
    var trackedVehicleDest: String? = null
    var trackedVehicleDelay: Int = 0
    var trackedVehicleAdjustedDep: String? = null
    var trackedLiveDepartureEpochMs: Long? = null
    var trackedLastSeenLiveMinutes: Int? = null
    var trackedWasAtStationOrImminent: Boolean = false

    private var consecutiveEmptyOriginPolls = 0
    private var lastKnownOriginLiveMinutes: Int? = null
    private var lastKnownOriginLiveSeconds: Int? = null
    private var lastKnownOriginLiveDestination: String? = null
    private var lastKnownOriginDelayMinutes: Int = 0
    private var lastKnownOriginAdjustedDepTime: String? = null

    // 2-minute departure courtesy state: holds departed vehicle at 0m based on live departure time
    var departedVehicleGraceUntilMs: Long? = null
    var departedVehicleStartMs: Long? = null
    var departedVehicleLiveDepartureEpochMs: Long? = null
    var departedVehicleLine: String? = null
    var departedVehicleDest: String? = null
    var departedVehicleDelay: Int = 0
    var departedVehicleAdjustedDep: String? = null
    var departedVehicleKey: String? = null
    var departedVehicleId: String? = null

    fun reset() {
        lastMatchedOriginVehicleKey = null
        lastMatchedOriginVehicleId = null
        lastMatchedOriginArrivalEpochMs = null
        consecutiveEmptyOriginPolls = 0
        lastKnownOriginLiveMinutes = null
        lastKnownOriginLiveSeconds = null
        lastKnownOriginLiveDestination = null
        lastKnownOriginDelayMinutes = 0
        lastKnownOriginAdjustedDepTime = null
        clearTrackedState()
        clearDepartedState()
    }

    fun clearMatchedOrigin() {
        lastMatchedOriginVehicleKey = null
        lastMatchedOriginVehicleId = null
        lastMatchedOriginArrivalEpochMs = null
        clearTrackedState()
        clearDepartedState()
    }

    private fun clearTrackedState() {
        trackedVehicleKey = null
        trackedVehicleId = null
        trackedVehicleLine = null
        trackedVehicleDest = null
        trackedVehicleDelay = 0
        trackedVehicleAdjustedDep = null
        trackedLiveDepartureEpochMs = null
        trackedLastSeenLiveMinutes = null
        trackedWasAtStationOrImminent = false
    }

    private fun clearDepartedState() {
        departedVehicleGraceUntilMs = null
        departedVehicleStartMs = null
        departedVehicleLiveDepartureEpochMs = null
        departedVehicleLine = null
        departedVehicleDest = null
        departedVehicleDelay = 0
        departedVehicleAdjustedDep = null
        departedVehicleKey = null
        departedVehicleId = null
    }

    fun recordMatchedCandidate(key: String, vehicleId: String?, arrivalEpochMs: Long) {
        val nowMs = System.currentTimeMillis()
        val graceUntil = departedVehicleGraceUntilMs
        if (graceUntil != null && nowMs < graceUntil) {
            // In courtesy grace period: retain departed vehicle identity
            return
        }
        lastMatchedOriginVehicleKey = key
        if (vehicleId != null) {
            lastMatchedOriginVehicleId = vehicleId
        }
        lastMatchedOriginArrivalEpochMs = arrivalEpochMs
    }

    fun clearGracePeriod() {
        clearDepartedState()
    }

    fun isGracePeriodActive(): Boolean {
        val graceUntil = departedVehicleGraceUntilMs ?: return false
        return System.currentTimeMillis() < graceUntil
    }

    fun getGracePeriodUntilMs(): Long? = departedVehicleGraceUntilMs
    fun getGracePeriodStartTimeMs(): Long? = departedVehicleStartMs
    fun getGracePeriodLiveDepartureEpochMs(): Long? = departedVehicleLiveDepartureEpochMs
    fun getGracePeriodVehicleName(): String? = departedVehicleLine

    fun updateWithLiveResult(
        legLiveResult: LegReconciliationResult,
        nextTransitLeg: PlannedLeg,
        isUserPhysicallyAtStation: Boolean = true
    ): OriginCacheReconciliationResult {
        val nowMs = System.currentTimeMillis()

        // 1. If currently inside the 2-minute courtesy window, hold the departed vehicle at 0 min
        val graceUntil = departedVehicleGraceUntilMs
        if (graceUntil != null) {
            if (nowMs >= graceUntil) {
                // Courtesy period expired after 2 minutes
                android.util.Log.i("TripOriginRealTimeCache", "Courtesy window of 2 minutes expired for $departedVehicleLine. Rolling over.")
                clearDepartedState()
            } else {
                // Courtesy is active: hold vehicle at 0 min ("En andén / Saliendo") so boarding confirmation remains available
                return OriginCacheReconciliationResult(
                    isLive = true,
                    liveMinutes = 0,
                    liveSeconds = 0,
                    liveDestination = departedVehicleDest ?: legLiveResult.liveDestination,
                    delayMinutes = departedVehicleDelay,
                    scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null },
                    adjustedDepTime = departedVehicleAdjustedDep ?: legLiveResult.adjustedDepartureTime,
                    normalizedLine = departedVehicleLine ?: (legLiveResult.matchedLineShortName ?: legLiveResult.normalizedLine)
                )
            }
        }

        if (legLiveResult.isLive) {
            consecutiveEmptyOriginPolls = 0
            val liveMins = legLiveResult.liveMinutes
            val incomingLine = legLiveResult.matchedLineShortName ?: legLiveResult.normalizedLine
            val incomingDest = legLiveResult.liveDestination
            val incomingKey = lastMatchedOriginVehicleKey

            val incomingLiveDepEpochMs = if (liveMins != null) {
                nowMs + (liveMins * 60 * 1000L)
            } else {
                nowMs
            }

            // Check if a previously tracked vehicle has truly departed from the platform:
            val hadTrackedVehicle = trackedVehicleLine != null || trackedVehicleKey != null
            val wasAtPlatformZeroMin = trackedLastSeenLiveMinutes == 0
            val isDepartureEpochPast = trackedLiveDepartureEpochMs != null && nowMs >= (trackedLiveDepartureEpochMs!! + 35_000L)

            // A jump to a subsequent vehicle ONLY occurs if:
            // 1) The departure epoch has actually arrived/elapsed in real time (+20s buffer), AND
            // 2) The vehicle was genuinely confirmed at the platform (0 min) OR its departure epoch is definitely past, AND
            // 3) The new incoming vehicle is truly a subsequent vehicle (epoch >= +90s after the tracked departure),
            //    NEVER when the train is still approaching or if live minutes simply fluctuate between 1 and 2 mins!
            val isCandidateJumpToSubsequent = hadTrackedVehicle && liveMins != null && (
                (wasAtPlatformZeroMin && (nowMs >= (trackedLiveDepartureEpochMs ?: nowMs) - 5_000L) && (incomingLiveDepEpochMs - (trackedLiveDepartureEpochMs ?: nowMs)) >= 90_000L) ||
                (isDepartureEpochPast && (incomingLiveDepEpochMs - (trackedLiveDepartureEpochMs ?: nowMs)) >= 90_000L)
            )

            if (isUserPhysicallyAtStation && isCandidateJumpToSubsequent) {
                // THE TRACKED VEHICLE HAS JUST DEPARTED!
                // Start the 2-minute (120s) courtesy grace period strictly based on the vehicle's LIVE departure time:
                val liveDepartureMs = trackedLiveDepartureEpochMs ?: nowMs
                val courtesyWindowMs = 120_000L // 2 full minutes
                val graceUntilMs = maxOf(liveDepartureMs + courtesyWindowMs, nowMs + courtesyWindowMs)

                departedVehicleGraceUntilMs = graceUntilMs
                departedVehicleStartMs = nowMs
                departedVehicleLiveDepartureEpochMs = liveDepartureMs
                departedVehicleLine = trackedVehicleLine ?: incomingLine
                departedVehicleDest = trackedVehicleDest ?: incomingDest
                departedVehicleDelay = trackedVehicleDelay
                departedVehicleAdjustedDep = trackedVehicleAdjustedDep ?: legLiveResult.adjustedDepartureTime
                departedVehicleKey = trackedVehicleKey
                departedVehicleId = trackedVehicleId

                // Keep last matched vehicle keys locked to the departed vehicle during courtesy
                lastMatchedOriginVehicleKey = trackedVehicleKey
                lastMatchedOriginVehicleId = trackedVehicleId
                lastMatchedOriginArrivalEpochMs = liveDepartureMs

                android.util.Log.i("TripOriginRealTimeCache", "🚗💨 Transit vehicle departed ($departedVehicleLine). Activated 2 min courtesy based on live departure (until ${graceUntilMs - nowMs}ms from now)")

                return OriginCacheReconciliationResult(
                    isLive = true,
                    liveMinutes = 0,
                    liveSeconds = 0,
                    liveDestination = departedVehicleDest ?: incomingDest,
                    delayMinutes = departedVehicleDelay,
                    scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null },
                    adjustedDepTime = departedVehicleAdjustedDep ?: legLiveResult.adjustedDepartureTime,
                    normalizedLine = departedVehicleLine ?: incomingLine
                )
            }

            // Normal tracking update of the active vehicle approaching / at station:
            trackedVehicleKey = incomingKey
            trackedVehicleId = lastMatchedOriginVehicleId
            trackedVehicleLine = incomingLine
            trackedVehicleDest = incomingDest
            trackedVehicleDelay = legLiveResult.delayMinutes
            trackedVehicleAdjustedDep = legLiveResult.adjustedDepartureTime
            trackedLastSeenLiveMinutes = liveMins
            trackedLiveDepartureEpochMs = incomingLiveDepEpochMs

            if (liveMins != null && liveMins == 0) {
                trackedWasAtStationOrImminent = true
            }

            lastKnownOriginLiveMinutes = legLiveResult.liveMinutes
            lastKnownOriginLiveSeconds = legLiveResult.liveSeconds
            lastKnownOriginLiveDestination = legLiveResult.liveDestination
            lastKnownOriginDelayMinutes = legLiveResult.delayMinutes
            lastKnownOriginAdjustedDepTime = legLiveResult.adjustedDepartureTime

            return OriginCacheReconciliationResult(
                isLive = true,
                liveMinutes = legLiveResult.liveMinutes,
                liveSeconds = legLiveResult.liveSeconds,
                liveDestination = legLiveResult.liveDestination,
                delayMinutes = legLiveResult.delayMinutes,
                scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null },
                adjustedDepTime = legLiveResult.adjustedDepartureTime,
                normalizedLine = incomingLine
            )
        } else {
            consecutiveEmptyOriginPolls++
            val hadTrackedVehicle = trackedVehicleLine != null || trackedVehicleKey != null
            val isDepartureEpochPast = trackedLiveDepartureEpochMs != null && nowMs >= (trackedLiveDepartureEpochMs!! + 40_000L)
            val wasAtPlatformOrEpochPast = (trackedLastSeenLiveMinutes == 0 && consecutiveEmptyOriginPolls >= 2 && nowMs >= (trackedLiveDepartureEpochMs ?: nowMs) + 30_000L) || isDepartureEpochPast

            if (isUserPhysicallyAtStation && hadTrackedVehicle && wasAtPlatformOrEpochPast) {
                // The vehicle was at platform or its departure epoch elapsed, and disappeared completely from the feed:
                // It just departed!
                val liveDepartureMs = trackedLiveDepartureEpochMs ?: nowMs
                val courtesyWindowMs = 120_000L // 2 full minutes
                val graceUntilMs = maxOf(liveDepartureMs + courtesyWindowMs, nowMs + courtesyWindowMs)

                departedVehicleGraceUntilMs = graceUntilMs
                departedVehicleStartMs = nowMs
                departedVehicleLiveDepartureEpochMs = liveDepartureMs
                departedVehicleLine = trackedVehicleLine
                departedVehicleDest = trackedVehicleDest
                departedVehicleDelay = trackedVehicleDelay
                departedVehicleAdjustedDep = trackedVehicleAdjustedDep
                departedVehicleKey = trackedVehicleKey
                departedVehicleId = trackedVehicleId

                lastMatchedOriginVehicleKey = trackedVehicleKey
                lastMatchedOriginVehicleId = trackedVehicleId
                lastMatchedOriginArrivalEpochMs = liveDepartureMs

                android.util.Log.i("TripOriginRealTimeCache", "🚗💨 Transit vehicle disappeared from feed ($departedVehicleLine). Activated 2 min courtesy based on live departure")

                return OriginCacheReconciliationResult(
                    isLive = true,
                    liveMinutes = 0,
                    liveSeconds = 0,
                    liveDestination = departedVehicleDest ?: lastKnownOriginLiveDestination,
                    delayMinutes = departedVehicleDelay,
                    scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null },
                    adjustedDepTime = departedVehicleAdjustedDep ?: lastKnownOriginAdjustedDepTime,
                    normalizedLine = departedVehicleLine ?: (legLiveResult.matchedLineShortName ?: legLiveResult.normalizedLine)
                )
            }

            if (consecutiveEmptyOriginPolls < 4 && lastKnownOriginLiveMinutes != null) {
                // Grace tolerance for micro network drops (< 4 polls / ~60s): smoothly decay and retain isLive
                val decayedMins = (lastKnownOriginLiveMinutes!! - (consecutiveEmptyOriginPolls * 15 / 60)).coerceAtLeast(0)
                return OriginCacheReconciliationResult(
                    isLive = true,
                    liveMinutes = decayedMins,
                    liveSeconds = decayedMins * 60,
                    liveDestination = lastKnownOriginLiveDestination,
                    delayMinutes = lastKnownOriginDelayMinutes,
                    scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null },
                    adjustedDepTime = lastKnownOriginAdjustedDepTime,
                    normalizedLine = legLiveResult.matchedLineShortName ?: legLiveResult.normalizedLine
                )
            } else {
                if (consecutiveEmptyOriginPolls >= 4) {
                    clearMatchedOrigin()
                    lastKnownOriginLiveMinutes = null
                }
                return OriginCacheReconciliationResult(
                    isLive = false,
                    liveMinutes = legLiveResult.liveMinutes,
                    liveSeconds = legLiveResult.liveSeconds,
                    liveDestination = legLiveResult.liveDestination,
                    delayMinutes = legLiveResult.delayMinutes,
                    scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null },
                    adjustedDepTime = legLiveResult.adjustedDepartureTime,
                    normalizedLine = legLiveResult.matchedLineShortName ?: legLiveResult.normalizedLine
                )
            }
        }
    }
}
