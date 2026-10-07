package com.example.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.model.quicktrack.QuickTrackedVehicle
import com.example.util.TripSensoryAlertManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object QuickVehicleTrackerManager {

    private val _activeTrackedVehicle = MutableStateFlow<QuickTrackedVehicle?>(null)
    val activeTrackedVehicle: StateFlow<QuickTrackedVehicle?> = _activeTrackedVehicle.asStateFlow()

    fun startTracking(context: Context, vehicle: QuickTrackedVehicle) {
        _activeTrackedVehicle.value = vehicle

        // Haptic feedback tick for effortless confirmation
        TripSensoryAlertManager.triggerLevel1SilentConfirmation(context.applicationContext)

        val intent = Intent(context.applicationContext, QuickVehicleTrackingService::class.java).apply {
            action = QuickVehicleTrackingService.ACTION_START
            putExtra(QuickVehicleTrackingService.EXTRA_VEHICLE, vehicle)
        }
        try {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        } catch (e: Exception) {
            android.util.Log.e("QuickTrackerMgr", "Error starting QuickVehicleTrackingService: ${e.message}", e)
        }
    }

    fun stopTracking(context: Context) {
        _activeTrackedVehicle.value = null
        val intent = Intent(context.applicationContext, QuickVehicleTrackingService::class.java).apply {
            action = QuickVehicleTrackingService.ACTION_STOP
        }
        try {
            context.applicationContext.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("QuickTrackerMgr", "Error stopping QuickVehicleTrackingService: ${e.message}", e)
        }
    }

    fun updateVehicle(updated: QuickTrackedVehicle?) {
        _activeTrackedVehicle.value = updated
    }

    fun isTrackingDeparture(
        departureId: String?,
        lineId: String,
        destination: String,
        stationId: String? = null,
        stationName: String? = null
    ): Boolean {
        val current = _activeTrackedVehicle.value ?: return false
        if (!isStationMatch(current, stationId, stationName)) return false
        val cleanCurrentLine = current.lineId.replace("L", "", ignoreCase = true).trim()
        val cleanTargetLine = lineId.replace("L", "", ignoreCase = true).trim()
        return cleanCurrentLine == cleanTargetLine &&
                current.destination.equals(destination, ignoreCase = true)
    }

    fun isTrackingSpecificDeparture(
        departure: com.example.ui.metro.RealTimeDeparture,
        lineId: String,
        destination: String,
        stationId: String? = null,
        stationName: String? = null
    ): Boolean {
        val current = _activeTrackedVehicle.value ?: return false

        // 1. Station verification: candidate departure must be at the origin station where user pinned the train
        val effectiveStationId = stationId ?: departure.originStationId
        val effectiveStationName = stationName ?: departure.originStationName
        if (!isStationMatch(current, effectiveStationId, effectiveStationName)) {
            return false
        }

        // 2. Line verification
        val cleanCurrentLine = current.lineId.replace("L", "", ignoreCase = true).trim()
        val cleanTargetLine = lineId.replace("L", "", ignoreCase = true).trim()
        val lineMatch = cleanCurrentLine.equals(cleanTargetLine, ignoreCase = true)
        if (!lineMatch) return false

        // 3. Destination verification
        val destMatch = current.destination.equals(destination, ignoreCase = true) ||
                current.destination.contains(destination, ignoreCase = true) ||
                destination.contains(current.destination, ignoreCase = true)
        if (!destMatch) return false

        // 4. Vehicle ID exact match if available
        if (!departure.vehicleId.isNullOrBlank() && !current.vehicleId.isNullOrBlank()) {
            if (departure.vehicleId == current.vehicleId) return true
        }
        if (!departure.id.isBlank() && !current.vehicleId.isNullOrBlank()) {
            if (departure.id.contains(current.vehicleId ?: "___")) return true
        }

        // 5. Scheduled departure time match
        if (!departure.estimatedTime.isNullOrBlank() && !current.targetScheduledTime.isNullOrBlank()) {
            if (departure.estimatedTime == current.targetScheduledTime) return true
        }

        // 6. Arrival epoch difference within 2 minutes (at the SAME station!)
        val epochDiff = kotlin.math.abs(current.targetArrivalEpochMs - departure.targetArrivalEpochMs)
        return epochDiff <= 120_000L
    }

    private fun isStationMatch(
        current: QuickTrackedVehicle,
        stationId: String?,
        stationName: String?
    ): Boolean {
        // If candidate departure provided no station info at all, we don't block
        if (stationId.isNullOrBlank() && stationName.isNullOrBlank()) {
            return true
        }

        // Check stationId match
        if (!stationId.isNullOrBlank() && current.originStationId.isNotBlank()) {
            val cleanTargetId = stationId.trim()
            val cleanOriginId = current.originStationId.trim()
            if (cleanTargetId.equals(cleanOriginId, ignoreCase = true)) {
                return true
            }
        }

        // Check stationName match
        if (!stationName.isNullOrBlank() && current.originStationName.isNotBlank()) {
            val normTarget = normalizeStationName(stationName)
            val normOrigin = normalizeStationName(current.originStationName)
            if (normTarget.isNotEmpty() && normOrigin.isNotEmpty()) {
                if (normTarget == normOrigin ||
                    normTarget.contains(normOrigin) ||
                    normOrigin.contains(normTarget)) {
                    return true
                }
            }
        }

        // Station info was provided but did not match origin station
        return false
    }

    private fun normalizeStationName(name: String): String {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .trim()
    }
}
