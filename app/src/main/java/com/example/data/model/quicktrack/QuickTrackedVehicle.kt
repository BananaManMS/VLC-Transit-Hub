package com.example.data.model.quicktrack

import java.io.Serializable

data class TrackedDownstreamStop(
    val stationId: String,
    val stationName: String,
    val zone: String,
    val scheduledArrivalTime: String?,
    val deltaMinutesFromOrigin: Int
) : Serializable

data class QuickTrackedVehicle(
    val lineId: String,
    val destination: String,
    val colorHex: String,
    val originStationId: String,
    val originStationName: String,
    val targetStationId: String? = null,
    val targetStationName: String? = null,
    val targetScheduledTime: String? = null,
    val initialMinutesRemaining: Int,
    val targetArrivalEpochMs: Long,
    val isRealTime: Boolean,
    val downstreamStops: List<TrackedDownstreamStop> = emptyList(),
    val penultimateStationId: String? = null,
    val penultimateStationName: String? = null,
    val isBoarded: Boolean = false,
    val hasAlertedDebark: Boolean = false,
    val vehicleId: String? = null,
    val trainServiceId: Int? = null,
    val originWebId: Int? = null,
    val destinationWebId: Int? = null,
    // --- Multi-Modal Transfer Trip Support ---
    val isTransferTrip: Boolean = false,
    val transferStationId: String? = null,
    val transferStationName: String? = null,
    val transferLineId: String? = null,
    val transferLineColorHex: String? = null,
    val transferDestination: String? = null,
    val transferArrivalEpochMs: Long = 0L,
    val transferWaitMinutes: Int = 0,
    val finalStationId: String? = null,
    val finalStationName: String? = null,
    val finalArrivalEpochMs: Long = 0L,
    val currentLegIndex: Int = 0,
    val hasAlertedTransfer: Boolean = false,
    val penultimateTransferStationId: String? = null,
    val penultimateTransferStationName: String? = null,
    val leg2DownstreamStops: List<TrackedDownstreamStop> = emptyList()
) : Serializable {

    val cleanLineNumber: String
        get() = lineId.replace("L", "", ignoreCase = true).trim()

    val cleanTransferLineNumber: String
        get() = (transferLineId ?: "").replace("L", "", ignoreCase = true).trim()

    val isDestinationAlert: Boolean
        get() = !targetStationId.isNullOrBlank() || isTransferTrip

    fun effectiveDestinationName(): String {
        return if (isTransferTrip) {
            finalStationName ?: targetStationName ?: destination
        } else {
            targetStationName ?: destination
        }
    }

    fun liveSecondsRemaining(nowMs: Long = System.currentTimeMillis()): Int {
        return ((targetArrivalEpochMs - nowMs) / 1000L).toInt()
    }

    fun liveMinutesRemaining(nowMs: Long = System.currentTimeMillis()): Int {
        val sec = liveSecondsRemaining(nowMs)
        return kotlin.math.max(0, sec / 60)
    }

    fun minutesRemainingToTransfer(nowMs: Long = System.currentTimeMillis()): Int {
        if (!isTransferTrip || transferArrivalEpochMs <= 0L) return liveMinutesRemaining(nowMs)
        val sec = ((transferArrivalEpochMs - nowMs) / 1000L).toInt()
        return kotlin.math.max(0, sec / 60)
    }

    fun minutesRemainingToFinalDestination(nowMs: Long = System.currentTimeMillis()): Int {
        if (!isTransferTrip || finalArrivalEpochMs <= 0L) return minutesRemainingToTarget(nowMs)
        val sec = ((finalArrivalEpochMs - nowMs) / 1000L).toInt()
        return kotlin.math.max(0, sec / 60)
    }

    fun minutesRemainingToTarget(nowMs: Long = System.currentTimeMillis()): Int {
        if (!isDestinationAlert) return liveMinutesRemaining(nowMs)
        if (isTransferTrip) {
            return minutesRemainingToFinalDestination(nowMs)
        }
        val stop = downstreamStops.find { it.stationId == targetStationId || it.stationName.equals(targetStationName, ignoreCase = true) }
        val deltaMin = stop?.deltaMinutesFromOrigin ?: 0
        val originSec = liveSecondsRemaining(nowMs)
        val targetSec = originSec + (deltaMin * 60)
        return kotlin.math.max(0, targetSec / 60)
    }

    fun liveSecondsRemainingToTarget(nowMs: Long = System.currentTimeMillis()): Int {
        if (!isDestinationAlert) return liveSecondsRemaining(nowMs)
        if (isTransferTrip) {
            val finalEpoch = if (finalArrivalEpochMs > 0L) finalArrivalEpochMs else targetArrivalEpochMs
            return ((finalEpoch - nowMs) / 1000L).toInt()
        }
        val stop = downstreamStops.find { it.stationId == targetStationId || it.stationName.equals(targetStationName, ignoreCase = true) }
        val deltaMin = stop?.deltaMinutesFromOrigin ?: 0
        val originSec = liveSecondsRemaining(nowMs)
        return originSec + (deltaMin * 60)
    }

    fun liveSecondsRemainingToPenultimate(nowMs: Long = System.currentTimeMillis()): Int {
        if (isTransferTrip && currentLegIndex == 0) {
            val penId = penultimateTransferStationId
            if (penId == null || penId == originStationId) {
                return liveSecondsRemaining(nowMs)
            }
            val stop = downstreamStops.find { it.stationId == penId || it.stationName.equals(penultimateTransferStationName, ignoreCase = true) }
            val deltaMin = stop?.deltaMinutesFromOrigin ?: 0
            val originSec = liveSecondsRemaining(nowMs)
            return originSec + (deltaMin * 60)
        }

        val penId = penultimateStationId
        if (penId == null || penId == originStationId) {
            return liveSecondsRemaining(nowMs)
        }
        val stopList = if (isTransferTrip && currentLegIndex == 1) leg2DownstreamStops else downstreamStops
        val stop = stopList.find { it.stationId == penId || it.stationName.equals(penultimateStationName, ignoreCase = true) }
        val deltaMin = stop?.deltaMinutesFromOrigin ?: 0
        val baseSec = if (isTransferTrip && currentLegIndex == 1) {
            ((transferArrivalEpochMs + (transferWaitMinutes * 60_000L) - nowMs) / 1000L).toInt()
        } else {
            liveSecondsRemaining(nowMs)
        }
        return baseSec + (deltaMin * 60)
    }

    fun minutesRemainingToPenultimate(nowMs: Long = System.currentTimeMillis()): Int {
        val sec = liveSecondsRemainingToPenultimate(nowMs)
        return kotlin.math.max(0, sec / 60)
    }
}
