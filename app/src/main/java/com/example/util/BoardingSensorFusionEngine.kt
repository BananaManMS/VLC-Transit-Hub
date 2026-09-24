package com.example.util

import android.location.Location
import android.util.Log
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sensor and telemetry fusion engine that calculates boarding confidence (0.0f to 1.0f)
 * to reliably detect when a user has boarded a transit vehicle.
 */
class BoardingSensorFusionEngine {

    companion object {
        private const val TAG = "BoardingSensorFusion"

        // Thresholds
        const val MIN_BOARDING_SPEED_MPS = 2.8 // ~10.0 km/h (supports street trams & city buses in traffic)
        const val SPRINT_SPEED_MAX_MPS = 7.0   // Above this, almost certainly motorized transit
        const val CLEAR_TRANSIT_SPEED_MPS = 4.5 // ~16.2 km/h
        const val AZIMUTH_TOLERANCE_DEGREES = 45.0
        const val STOP_WAITING_ZONE_RADIUS_METERS = 50.0 // Within this radius from origin stop, user is waiting on sidewalk
        const val RECENT_STOP_ARRIVAL_WINDOW_MS = 90_000L // 90 seconds
    }

    private val _confidenceFlow = MutableStateFlow(0.0f)
    val confidenceFlow: StateFlow<Float> = _confidenceFlow.asStateFlow()

    private var consecutiveHighSpeedReadings = 0
    private var lastEvaluatedLegIndex: Int = -1

    /**
     * Evaluates current sensor, GPS, and real-time telemetry inputs to update boarding confidence.
     */
    fun evaluate(
        location: Location?,
        currentLeg: PlannedLeg?,
        currentLegIndex: Int,
        realTimeArrivalMinutes: Int?,
        realTimeSecondsRemaining: Int?,
        isUndergroundMode: Boolean = false,
        distanceToOriginMeters: Double? = null
    ): Float {
        if (currentLeg == null || currentLeg.mode == TransitMode.WALK || currentLeg.mode == TransitMode.BICYCLE) {
            _confidenceFlow.value = 0.0f
            consecutiveHighSpeedReadings = 0
            return 0.0f
        }

        if (lastEvaluatedLegIndex != currentLegIndex) {
            lastEvaluatedLegIndex = currentLegIndex
            consecutiveHighSpeedReadings = 0
            _confidenceFlow.value = 0.0f
        }

        // Calculate distance from leg origin stop if not explicitly provided
        val distToOrigin = distanceToOriginMeters ?: run {
            if (location != null) {
                val originCoords = TripStepProgressionEngine.getLegOriginCoordinates(currentLeg)
                if (originCoords != null) {
                    TripStepProgressionEngine.calculateDistanceMeters(
                        location.latitude, location.longitude,
                        originCoords.first, originCoords.second
                    )
                } else Double.MAX_VALUE
            } else Double.MAX_VALUE
        }

        val isWithinStopZone = distToOrigin <= STOP_WAITING_ZONE_RADIUS_METERS

        var confidence = 0.0f

        // 1. GPS Kinematics + Persistence (Weight: up to 0.50)
        val speed = location?.speed?.toDouble() ?: 0.0
        val hasSpeed = location != null && location.hasSpeed() && speed > 0.1

        if (hasSpeed) {
            if (speed >= MIN_BOARDING_SPEED_MPS) {
                consecutiveHighSpeedReadings++
                val speedConfidence = when {
                    speed >= SPRINT_SPEED_MAX_MPS -> 0.50f // Motorized speed (> 25 km/h)
                    speed >= CLEAR_TRANSIT_SPEED_MPS && consecutiveHighSpeedReadings >= 2 -> 0.45f // Sustained fast transit speed
                    consecutiveHighSpeedReadings >= 2 -> 0.35f // Sustained transit speed (> 15 km/h)
                    else -> 0.20f
                }
                confidence += speedConfidence
            } else {
                consecutiveHighSpeedReadings = (consecutiveHighSpeedReadings - 1).coerceAtLeast(0)
            }
        }

        // 2. Direction / Azimuth Vector Alignment (Weight: up to 0.25)
        var isDirectionAligned = false
        if (location != null && location.hasBearing() && currentLeg.geometry.size >= 2) {
            val legBearing = calculateInitialLegBearing(currentLeg)
            if (legBearing != null) {
                val userBearing = location.bearing.toDouble()
                val angleDiff = abs(normalizeAngle(userBearing - legBearing))
                if (angleDiff <= AZIMUTH_TOLERANCE_DEGREES) {
                    confidence += 0.25f
                    isDirectionAligned = true
                } else if (angleDiff <= AZIMUTH_TOLERANCE_DEGREES * 1.5) {
                    confidence += 0.10f
                }
            }
        }

        // 3. Physical Displacement from Stop (Weight: up to 0.30)
        // Moving away from the origin stop along the route confirms departure
        if (!isWithinStopZone && distToOrigin in (STOP_WAITING_ZONE_RADIUS_METERS..800.0)) {
            if (isDirectionAligned || (hasSpeed && speed >= MIN_BOARDING_SPEED_MPS)) {
                confidence += 0.30f
            } else {
                confidence += 0.15f
            }
        }

        // 4. Real-Time Transit Feed Proximity (Accessory confirmation only: up to 0.20)
        // CRITICAL: Real-time arrival at the stop alone NEVER triggers boarding if the user is still in the waiting zone
        val isVehicleJustArrivedOrPast = realTimeArrivalMinutes == 0 ||
                (realTimeSecondsRemaining != null && realTimeSecondsRemaining <= 20)
        if (isVehicleJustArrivedOrPast) {
            // Only add RT confidence if user is actually moving or displaced from the stop
            if (!isWithinStopZone || (hasSpeed && speed >= CLEAR_TRANSIT_SPEED_MPS)) {
                confidence += 0.20f
            } else {
                confidence += 0.05f
            }
        } else if (realTimeArrivalMinutes != null && realTimeArrivalMinutes <= 1) {
            if (!isWithinStopZone || (hasSpeed && speed >= CLEAR_TRANSIT_SPEED_MPS)) {
                confidence += 0.10f
            }
        }

        // 5. Underground / Tunnel Signal Drop Heuristic (Weight: up to 0.35)
        if (isUndergroundMode && (currentLeg.mode == TransitMode.SUBWAY || currentLeg.mode == TransitMode.RAIL)) {
            val accuracy = if (location?.hasAccuracy() == true) location.accuracy else 999f
            if (accuracy > 60f || location == null) {
                // Signal degraded or lost in underground station right around scheduled departure
                confidence += 0.35f
            }
        }

        // SAFEGUARD: While inside the stop waiting zone (< 70m), strictly cap confidence for all modes unless speed is unmistakably motorized (> 22 km/h sustained)
        if (isWithinStopZone) {
            val isUnmistakablyMotorized = hasSpeed && speed >= CLEAR_TRANSIT_SPEED_MPS && consecutiveHighSpeedReadings >= 2
            if (!isUnmistakablyMotorized) {
                confidence = confidence.coerceAtMost(0.40f)
            }
        }

        val finalScore = confidence.coerceIn(0.0f, 1.0f)
        _confidenceFlow.value = finalScore
        return finalScore
    }

    /**
     * Manually emit or reset confidence.
     */
    fun reset() {
        consecutiveHighSpeedReadings = 0
        lastEvaluatedLegIndex = -1
        _confidenceFlow.value = 0.0f
    }

    private fun calculateInitialLegBearing(leg: PlannedLeg): Double? {
        val pts = leg.geometry
        if (pts.size < 2) return null
        val p1 = pts.first()
        val p2 = pts[1]

        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val initialBearingRad = atan2(y, x)
        return (Math.toDegrees(initialBearingRad) + 360.0) % 360.0
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a > 180.0) a -= 360.0
        if (a < -180.0) a += 360.0
        return a
    }
}
