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

        // Motorized speed thresholds: pedestrian walking/running (<= 15 km/h) never qualifies as transit
        const val MIN_BOARDING_SPEED_MPS = 5.0 // ~18.0 km/h
        const val CLEAR_TRANSIT_SPEED_MPS = 6.1 // ~22.0 km/h (strictly motorized transit speed)
        const val SPRINT_SPEED_MAX_MPS = 7.5   // ~27.0 km/h (high-speed motorized transit)
        const val AZIMUTH_TOLERANCE_DEGREES = 45.0
        const val STOP_WAITING_ZONE_RADIUS_METERS = 120.0 // Within this radius from origin stop, user is waiting on platform / station
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
            } else 0.0 // If location is lost underground at origin, assume user is still at origin, NOT infinitely far away
        }

        val locAccuracy = if (location?.hasAccuracy() == true) location.accuracy.toDouble() else 40.0
        val baseStopZoneRadius = if (currentLeg.mode == TransitMode.BUS) 200.0 else STOP_WAITING_ZONE_RADIUS_METERS
        val effectiveStopZoneRadius = maxOf(baseStopZoneRadius, locAccuracy * 1.5)
        val isWithinStopZone = location == null || distToOrigin <= effectiveStopZoneRadius

        // If location is completely unavailable, we cannot reliably confirm automatic boarding without user input
        if (location == null) {
            _confidenceFlow.value = 0.0f
            return 0.0f
        }

        var confidence = 0.0f

        // 1. GPS Kinematics + Persistence (Weight: up to 0.50)
        val speed = location.speed.toDouble()
        val hasSpeed = location.hasSpeed() && speed > 0.1 && locAccuracy <= 45.0

        if (hasSpeed) {
            if (speed >= MIN_BOARDING_SPEED_MPS) {
                consecutiveHighSpeedReadings++
                val speedConfidence = when {
                    speed >= SPRINT_SPEED_MAX_MPS && consecutiveHighSpeedReadings >= 2 -> 0.50f // Motorized speed (> 25 km/h)
                    speed >= CLEAR_TRANSIT_SPEED_MPS && consecutiveHighSpeedReadings >= 2 -> 0.40f // Sustained fast transit speed
                    consecutiveHighSpeedReadings >= 2 -> 0.30f // Sustained transit speed (> 15 km/h)
                    else -> 0.15f
                }
                confidence += speedConfidence
            } else {
                consecutiveHighSpeedReadings = (consecutiveHighSpeedReadings - 1).coerceAtLeast(0)
            }
        }

        // 2. Direction / Azimuth Vector Alignment (Weight: up to 0.25)
        var isDirectionAligned = false
        if (location.hasBearing() && locAccuracy <= 40.0 && currentLeg.geometry.size >= 2) {
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

        // 3. Subsequent Stop / Intermediate Station Detection (via Cell Towers, Wi-Fi or GPS)
        // If telecom repeaters or GPS locate the user at the next or intermediate stop along the route,
        // this is decisive confirmation that the user is onboard the transit vehicle.
        var hasReachedSubsequentStop = false
        if (!isWithinStopZone && currentLeg.intermediateStops.isNotEmpty()) {
            val stopTolerance = maxOf(140.0, locAccuracy * 1.3)
            for (stop in currentLeg.intermediateStops) {
                val distToStop = TripStepProgressionEngine.calculateDistanceMeters(
                    location.latitude, location.longitude,
                    stop.lat, stop.lon
                )
                if (distToStop <= stopTolerance && distToOrigin > (effectiveStopZoneRadius * 0.8)) {
                    hasReachedSubsequentStop = true
                    break
                }
            }
        }

        // 4. Gradual Physical Displacement from Origin Stop along Route towards Target
        val targetCoords = TripStepProgressionEngine.getLegTargetCoordinates(currentLeg)
        val originCoords = TripStepProgressionEngine.getLegOriginCoordinates(currentLeg)
        val totalLegDist = if (originCoords != null && targetCoords != null) {
            TripStepProgressionEngine.calculateDistanceMeters(
                originCoords.first, originCoords.second,
                targetCoords.first, targetCoords.second
            )
        } else 0.0

        val distanceToTarget = if (targetCoords != null) {
            TripStepProgressionEngine.calculateDistanceMeters(
                location.latitude, location.longitude,
                targetCoords.first, targetCoords.second
            )
        } else Double.MAX_VALUE

        val isGraduallyMovingTowardsTarget = !isWithinStopZone &&
                totalLegDist > 200.0 &&
                distToOrigin >= (effectiveStopZoneRadius * 1.2) &&
                distanceToTarget < (totalLegDist - 80.0)

        if (hasReachedSubsequentStop) {
            // Decisive: at or near an intermediate stop away from origin
            confidence = maxOf(confidence, 0.95f)
        } else if (isGraduallyMovingTowardsTarget) {
            // Gradual forward displacement along corridor towards downstream stations
            val displacementConfidence = if (isDirectionAligned || (hasSpeed && speed >= MIN_BOARDING_SPEED_MPS)) {
                0.85f
            } else {
                0.80f // Decisive confidence even with cell tower / network positioning
            }
            confidence = maxOf(confidence, displacementConfidence)
        } else if (!isWithinStopZone && locAccuracy <= 55.0 && distToOrigin in (effectiveStopZoneRadius..800.0)) {
            if (isDirectionAligned || (hasSpeed && speed >= MIN_BOARDING_SPEED_MPS)) {
                confidence += 0.30f
            } else {
                confidence += 0.15f
            }
        }

        // 5. Real-Time Transit Feed Proximity (Accessory confirmation only: up to 0.20)
        // Real-time arrival at the stop alone NEVER triggers boarding if the user is still in the waiting zone
        val isVehicleJustArrivedOrPast = realTimeArrivalMinutes == 0 ||
                (realTimeSecondsRemaining != null && realTimeSecondsRemaining <= 20)
        if (isVehicleJustArrivedOrPast) {
            // Only add RT confidence if user is actually moving at motorized transit speed away from the stop
            if (!isWithinStopZone && hasSpeed && speed >= CLEAR_TRANSIT_SPEED_MPS) {
                confidence += 0.20f
            } else if (!isWithinStopZone) {
                confidence += 0.05f
            }
        } else if (realTimeArrivalMinutes != null && realTimeArrivalMinutes <= 1) {
            if (!isWithinStopZone && hasSpeed && speed >= CLEAR_TRANSIT_SPEED_MPS) {
                confidence += 0.10f
            }
        }

        // SAFEGUARD: While inside the stop waiting zone, strictly cap confidence for all modes.
        // User is waiting at the platform: NEVER auto-board unless undeniably motorized with sustained high speed (> 25 km/h).
        if (isWithinStopZone) {
            val isUnmistakablyMotorized = hasSpeed && speed >= SPRINT_SPEED_MAX_MPS && consecutiveHighSpeedReadings >= 3 && locAccuracy <= 30.0
            if (!isUnmistakablyMotorized) {
                confidence = confidence.coerceAtMost(0.25f)
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
