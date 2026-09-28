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

    private var consecutiveEmptyOriginPolls = 0
    private var lastKnownOriginLiveMinutes: Int? = null
    private var lastKnownOriginLiveSeconds: Int? = null
    private var lastKnownOriginLiveDestination: String? = null
    private var lastKnownOriginDelayMinutes: Int = 0
    private var lastKnownOriginAdjustedDepTime: String? = null

    // 2-minute departure courtesy state: holds departed vehicle at 0m so user can confirm boarding
    var departedVehicleGraceUntilMs: Long? = null
    var departedVehicleStartMs: Long? = null
    var departedVehicleLine: String? = null
    var departedVehicleDest: String? = null
    var departedVehicleDelay: Int = 0
    var departedVehicleAdjustedDep: String? = null

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
        departedVehicleGraceUntilMs = null
        departedVehicleStartMs = null
        departedVehicleLine = null
        departedVehicleDest = null
        departedVehicleDelay = 0
        departedVehicleAdjustedDep = null
    }

    fun clearMatchedOrigin() {
        lastMatchedOriginVehicleKey = null
        lastMatchedOriginVehicleId = null
        lastMatchedOriginArrivalEpochMs = null
        departedVehicleGraceUntilMs = null
        departedVehicleStartMs = null
        departedVehicleLine = null
        departedVehicleDest = null
        departedVehicleDelay = 0
        departedVehicleAdjustedDep = null
    }

    fun updateWithLiveResult(
        legLiveResult: LegReconciliationResult,
        nextTransitLeg: PlannedLeg,
        isUserPhysicallyAtStation: Boolean = true
    ): OriginCacheReconciliationResult {
        val nowMs = System.currentTimeMillis()

        if (!isUserPhysicallyAtStation) {
            // User is still in transit / far away from platform: clear any departed vehicle grace
            departedVehicleGraceUntilMs = null
            departedVehicleStartMs = null
            departedVehicleLine = null
            departedVehicleDest = null
        }

        if (legLiveResult.isLive) {
            consecutiveEmptyOriginPolls = 0
            val liveMins = legLiveResult.liveMinutes

            // If a matched vehicle reaches platform/departure (<= 1 min or <= 0 min) AND user is at station, initiate 2-minute courtesy window
            if (isUserPhysicallyAtStation && liveMins != null && liveMins <= 1) {
                if (departedVehicleGraceUntilMs == null) {
                    departedVehicleStartMs = nowMs
                }
                departedVehicleGraceUntilMs = nowMs + (2 * 60 * 1000L)
                departedVehicleLine = legLiveResult.matchedLineShortName ?: legLiveResult.normalizedLine
                departedVehicleDest = legLiveResult.liveDestination
                departedVehicleDelay = legLiveResult.delayMinutes
                departedVehicleAdjustedDep = legLiveResult.adjustedDepartureTime
            }

            // Check if the API jumped to a subsequent vehicle (e.g. >= 4 min) while user is at station within the 2-minute courtesy
            val graceUntil = departedVehicleGraceUntilMs
            if (isUserPhysicallyAtStation && graceUntil != null && nowMs < graceUntil && liveMins != null && liveMins >= 4) {
                // Hold the departed vehicle at 0 min ("En andén / Saliendo") so boarding confirmation remains available
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
            } else if (graceUntil != null && (!isUserPhysicallyAtStation || nowMs >= graceUntil)) {
                // Courtesy expired or user not at station -> roll over to next vehicle
                departedVehicleGraceUntilMs = null
                departedVehicleStartMs = null
                departedVehicleLine = null
                departedVehicleDest = null
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
                normalizedLine = legLiveResult.matchedLineShortName ?: legLiveResult.normalizedLine
            )
        } else {
            // Check 2-minute departure courtesy if API returned empty/no live candidates
            val graceUntil = departedVehicleGraceUntilMs
            if (graceUntil != null && nowMs < graceUntil) {
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

            consecutiveEmptyOriginPolls++
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
                // After 4 consecutive failed polls, drop to scheduled/static
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
