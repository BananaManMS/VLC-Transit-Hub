package com.example.util

import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode

/**
 * Result data holder for off-route and pedestrian deviation evaluation.
 */
data class OffRouteEvaluationResult(
    val isOffRoute: Boolean = false,
    val offRouteDist: Double = 0.0,
    val consecutiveOffRouteCount: Int = 0,
    val isPedestrianDeviated: Boolean = false,
    val dynamicWalkMinutesRemaining: Int? = null
)

/**
 * Encapsulates off-route detection and target-centric pedestrian tracking logic.
 */
object TripOffRouteEvaluator {

    fun evaluate(
        userLat: Double?,
        userLon: Double?,
        currentLeg: PlannedLeg,
        isCurrentWalk: Boolean,
        currentConsecutiveOffRouteCount: Int
    ): OffRouteEvaluationResult {
        if (userLat == null || userLon == null) {
            return OffRouteEvaluationResult(consecutiveOffRouteCount = currentConsecutiveOffRouteCount)
        }

        val targetCoords = TripStepProgressionEngine.getLegTargetCoordinates(currentLeg)
        val distToTarget = if (targetCoords != null) {
            TripStepProgressionEngine.calculateDistanceMeters(userLat, userLon, targetCoords.first, targetCoords.second)
        } else Double.MAX_VALUE

        if (isCurrentWalk) {
            // Inmunidad de Desvío Peatonal: Forzamos isOffRoute = false de forma inmutable durante caminatas.
            // Medición de desvío respecto a la sugerencia visual de OSM
            val offRouteDist = TripStepProgressionEngine.calculateDistanceToLegPolyline(userLat, userLon, currentLeg)
            val isPedestrianDeviated = offRouteDist > TripStepProgressionEngine.PEDESTRIAN_DEVIATION_THRESHOLD_METERS
            val dynamicWalkMinutesRemaining = TripStepProgressionEngine.calculateDynamicWalkMinutes(distToTarget, currentLeg)

            return OffRouteEvaluationResult(
                isOffRoute = false,
                offRouteDist = offRouteDist,
                consecutiveOffRouteCount = 0,
                isPedestrianDeviated = isPedestrianDeviated,
                dynamicWalkMinutesRemaining = dynamicWalkMinutesRemaining
            )
        } else {
            val isBoarded = ActiveTripProgressTracker.progressState.value.isBoarded
            val isRailOrSubway = currentLeg.mode in listOf(TransitMode.SUBWAY, TransitMode.RAIL, TransitMode.TRAM)

            if (isBoarded || isRailOrSubway) {
                return OffRouteEvaluationResult(
                    isOffRoute = false,
                    offRouteDist = 0.0,
                    consecutiveOffRouteCount = 0
                )
            } else {
                val offRouteDist = TripStepProgressionEngine.calculateDistanceToLegPolyline(userLat, userLon, currentLeg)
                val originCoords = TripStepProgressionEngine.getLegOriginCoordinates(currentLeg)
                val distToOrigin = if (originCoords != null) {
                    TripStepProgressionEngine.calculateDistanceMeters(userLat, userLon, originCoords.first, originCoords.second)
                } else Double.MAX_VALUE

                val isNearStation = distToOrigin <= 250.0 || distToTarget <= 250.0
                var newCount = currentConsecutiveOffRouteCount
                var isOffRoute = false

                if (!isNearStation && offRouteDist > 350.0) {
                    newCount++
                    if (newCount >= TripStepProgressionEngine.CONSECUTIVE_OFF_ROUTE_REQUIRED) {
                        isOffRoute = true
                    }
                } else {
                    newCount = 0
                    isOffRoute = false
                }

                return OffRouteEvaluationResult(
                    isOffRoute = isOffRoute,
                    offRouteDist = offRouteDist,
                    consecutiveOffRouteCount = newCount
                )
            }
        }
    }
}
