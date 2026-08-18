package com.example.util

import android.location.Location
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState

/**
 * Result of evaluating user position against the active trip itinerary.
 */
sealed interface StepProgressionResult {
    /** The user is progressing normally on the current leg */
    data class OnTrack(
        val currentLegIndex: Int,
        val distanceToNextTargetMeters: Double,
        val targetName: String
    ) : StepProgressionResult

    /** The user reached within capture radius (<= 30m) of the leg's end point -> Advance to next leg */
    data class LegCompleted(
        val completedLegIndex: Int,
        val nextLegIndex: Int,
        val isFinalLeg: Boolean
    ) : StepProgressionResult

    /** Trip has no legs or is already finished */
    object NoOp : StepProgressionResult
}

/**
 * Deterministic state machine engine for active multimodal trips.
 */
object TripStepProgressionEngine {

    /**
     * Deterministic arrival capture radius to automatically advance legs / detect arrival.
     * Set to 30.0 meters.
     */
    const val CAPTURE_RADIUS_METERS: Double = 30.0

    /**
     * Station Proximity Radius: Within 50 meters of origin station to detect transit wait.
     */
    const val STATION_PROXIMITY_RADIUS_METERS: Double = 50.0

    /**
     * Threshold in meters to consider the user deviated from the planned route.
     */
    const val OFF_ROUTE_THRESHOLD_METERS: Double = 150.0

    /**
     * Number of consecutive GPS readings required above the threshold before raising an Off-Route alert.
     */
    const val CONSECUTIVE_OFF_ROUTE_REQUIRED: Int = 3

    /**
     * Sustained average walking speed (1.4 m/s ≈ 5.04 km/h) for dynamic pedestrian ETA recalculation.
     */
    const val SUSTAINED_WALKING_SPEED_MPS: Double = 1.4

    /**
     * Set of leg indices that have been explicitly or automatically confirmed as boarded.
     */
    private val boardedLegIndices = mutableSetOf<Int>()

    /**
     * Checks if a specific leg index has been confirmed as boarded.
     */
    fun isLegBoarded(legIndex: Int): Boolean = boardedLegIndices.contains(legIndex)

    /**
     * Explicitly marks a leg as boarded.
     */
    fun markLegBoarded(legIndex: Int) {
        boardedLegIndices.add(legIndex)
    }

    /**
     * Explicitly notifies the engine that the user has boarded the transit vehicle.
     * Updates ActiveTripProgressTracker and activates tunnel dead-reckoning support.
     */
    fun notifyBoardingConfirmed(
        legIndex: Int,
        targetLeg: PlannedLeg,
        enableTunnelDeadReckoning: Boolean = true
    ) {
        boardedLegIndices.add(legIndex)
        val now = System.currentTimeMillis()
        val departureTimeMs = ActiveTripProgressTracker.progressState.value.transitDepartureTimeMs.takeIf { it > 0L } ?: now
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.05f,
            waitTimeMessage = null,
            isDeadReckoning = enableTunnelDeadReckoning,
            isBoarded = true,
            transitDepartureTimeMs = departureTimeMs,
            legIndex = legIndex
        )
    }

    /**
     * Resets internal tracking and engine state.
     */
    fun reset() {
        boardedLegIndices.clear()
        ActiveTripProgressTracker.reset()
    }

    /**
     * Threshold in meters to detect visual deviation from precalculated pedestrian polyline.
     */
    const val PEDESTRIAN_DEVIATION_THRESHOLD_METERS: Double = 40.0

    /**
     * Calculates remaining walking time dynamically.
     * Prioritizes the Transitous routing street-network walk duration (leg.durationSeconds)
     * scaled by the percentage of remaining distance to the target.
     * Falls back to Haversine speed calculation if leg data is unavailable.
     */
    fun calculateDynamicWalkMinutes(
        distanceMeters: Double,
        leg: PlannedLeg? = null
    ): Int {
        if (distanceMeters <= 0.0) return 0

        if (leg != null && leg.mode == TransitMode.WALK && leg.durationSeconds > 0) {
            val transitousBaseMins = (leg.durationSeconds / 60.0).coerceAtLeast(1.0)
            val originCoords = getLegOriginCoordinates(leg)
            val targetCoords = getLegTargetCoordinates(leg)
            val totalLegDist = if (leg.distanceMeters > 0.0) {
                leg.distanceMeters
            } else if (originCoords != null && targetCoords != null) {
                calculateDistanceMeters(originCoords.first, originCoords.second, targetCoords.first, targetCoords.second)
            } else 0.0

            if (totalLegDist > 10.0) {
                val remainingFraction = (distanceMeters / totalLegDist).coerceIn(0.0, 1.0)
                val remainingMins = kotlin.math.ceil(transitousBaseMins * remainingFraction).toInt()
                return if (distanceMeters < 25.0) 0 else remainingMins.coerceAtLeast(1)
            }
        }

        // Fallback: 1.4 m/s sustained speed + 1.10 urban tortuosity factor
        val seconds = (distanceMeters * 1.10) / SUSTAINED_WALKING_SPEED_MPS
        return kotlin.math.ceil(seconds / 60.0).toInt().coerceAtLeast(1)
    }

    /**
     * Distance in meters between two WGS84 geographic coordinates.
     */
    fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    /**
     * Computes the perpendicular/minimum distance in meters from a user GPS point
     * to the full polyline geometry of a planned leg.
     */
    fun calculateDistanceToLegPolyline(
        userLat: Double,
        userLon: Double,
        leg: PlannedLeg
    ): Double {
        val points = leg.geometry
        if (points.isEmpty()) {
            val origin = getLegOriginCoordinates(leg)
            val target = getLegTargetCoordinates(leg)
            if (origin != null && target != null) {
                return distancePointToSegmentMeters(
                    userLat, userLon,
                    origin.first, origin.second,
                    target.first, target.second
                )
            } else if (target != null) {
                return calculateDistanceMeters(userLat, userLon, target.first, target.second)
            }
            return 0.0
        }

        if (points.size == 1) {
            return calculateDistanceMeters(userLat, userLon, points[0].latitude, points[0].longitude)
        }

        var minDistance = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val dist = distancePointToSegmentMeters(
                userLat, userLon,
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude
            )
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    /**
     * Computes the minimum distance from point P to line segment AB in meters.
     */
    fun distancePointToSegmentMeters(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        val latMid = Math.toRadians((aLat + bLat) / 2.0)
        val cosLat = Math.cos(latMid)

        // Flat projection meters delta
        val metersPerDegLat = 111132.954 - 559.822 * Math.cos(2 * latMid)
        val metersPerDegLon = 111412.84 * cosLat

        val ax = aLon * metersPerDegLon
        val ay = aLat * metersPerDegLat
        val bx = bLon * metersPerDegLon
        val by = bLat * metersPerDegLat
        val px = pLon * metersPerDegLon
        val py = pLat * metersPerDegLat

        val dx = bx - ax
        val dy = by - ay
        val segLengthSq = dx * dx + dy * dy

        if (segLengthSq == 0.0) {
            return calculateDistanceMeters(pLat, pLon, aLat, aLon)
        }

        // Projection factor t
        val t = ((px - ax) * dx + (py - ay) * dy) / segLengthSq
        val clampedT = t.coerceIn(0.0, 1.0)

        val closestLat = aLat + clampedT * (bLat - aLat)
        val closestLon = aLon + clampedT * (bLon - aLon)

        return calculateDistanceMeters(pLat, pLon, closestLat, closestLon)
    }

    /**
     * Resolve the destination point coordinate of a given leg.
     */
    fun getLegTargetCoordinates(leg: PlannedLeg): Pair<Double, Double>? {
        if (leg.geometry.isNotEmpty()) {
            val lastPoint = leg.geometry.last()
            return Pair(lastPoint.latitude, lastPoint.longitude)
        }
        val lastStop = leg.intermediateStops.lastOrNull()
        if (lastStop != null) {
            return Pair(lastStop.lat, lastStop.lon)
        }
        return null
    }

    /**
     * Resolve origin coordinate of a leg.
     */
    fun getLegOriginCoordinates(leg: PlannedLeg): Pair<Double, Double>? {
        if (leg.geometry.isNotEmpty()) {
            val firstPoint = leg.geometry.first()
            return Pair(firstPoint.latitude, firstPoint.longitude)
        }
        val firstStop = leg.intermediateStops.firstOrNull()
        if (firstStop != null) {
            return Pair(firstStop.lat, firstStop.lon)
        }
        return null
    }

    /**
     * Evaluates user GPS location against the current leg and determines whether
     * to advance the leg or maintain tracking.
     */
    fun evaluateProgression(
        userLat: Double,
        userLon: Double,
        activeTrip: ActiveTripState,
        locationAccuracyMeters: Float? = null,
        lastLocationTimeMillis: Long = System.currentTimeMillis()
    ): StepProgressionResult {
        val legs = activeTrip.itinerary.legs
        val currentIndex = activeTrip.currentLegIndex

        if (legs.isEmpty() || currentIndex >= legs.size) {
            reset()
            return StepProgressionResult.NoOp
        }

        // 1. AUTO-SKIP initial walk leg if user is already at/near the first transit station (< 75m)
        if (currentIndex == 0 && legs[0].mode == TransitMode.WALK && legs.size > 1) {
            val transitLeg = legs[1]
            val stationCoords = getLegOriginCoordinates(transitLeg) ?: getLegTargetCoordinates(legs[0])
            if (stationCoords != null) {
                val distToStation = calculateDistanceMeters(userLat, userLon, stationCoords.first, stationCoords.second)
                if (distToStation <= 75.0) {
                    boardedLegIndices.remove(0)
                    ActiveTripProgressTracker.updateProgress(0.0f)
                    return StepProgressionResult.LegCompleted(
                        completedLegIndex = 0,
                        nextLegIndex = 1,
                        isFinalLeg = false
                    )
                }
            }
        }

        val currentLeg = legs[currentIndex]
        val targetCoords = getLegTargetCoordinates(currentLeg)
        val originCoords = getLegOriginCoordinates(currentLeg)

        if (targetCoords == null) {
            return StepProgressionResult.OnTrack(
                currentLegIndex = currentIndex,
                distanceToNextTargetMeters = 0.0,
                targetName = currentLeg.toName
            )
        }

        val distanceToTarget = calculateDistanceMeters(
            userLat, userLon,
            targetCoords.first, targetCoords.second
        )

        val isTransitLeg = currentLeg.mode in listOf(
            TransitMode.SUBWAY, TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL
        )

        val captureRadius = if (isTransitLeg) 75.0 else CAPTURE_RADIUS_METERS

        // Capture condition: Within capture radius of leg destination
        if (distanceToTarget <= captureRadius) {
            val nextIndex = currentIndex + 1
            val isFinalLeg = nextIndex >= legs.size
            boardedLegIndices.remove(currentIndex)
            if (!isFinalLeg) {
                ActiveTripProgressTracker.updateProgress(0.0f)
            } else {
                ActiveTripProgressTracker.updateProgress(1.0f)
            }
            return StepProgressionResult.LegCompleted(
                completedLegIndex = currentIndex,
                nextLegIndex = nextIndex,
                isFinalLeg = isFinalLeg
            )
        }

        // Also check if user has already entered within range of the NEXT leg's path/origin
        if (currentIndex + 1 < legs.size) {
            val nextLeg = legs[currentIndex + 1]
            val nextOriginCoords = getLegOriginCoordinates(nextLeg)
            if (nextOriginCoords != null) {
                val distanceToNextOrigin = calculateDistanceMeters(
                    userLat, userLon,
                    nextOriginCoords.first, nextOriginCoords.second
                )
                val nextCaptureRadius = if (nextLeg.mode != TransitMode.WALK) 80.0 else 60.0
                if (distanceToNextOrigin <= nextCaptureRadius) {
                    boardedLegIndices.remove(currentIndex)
                    ActiveTripProgressTracker.updateProgress(0.0f)
                    return StepProgressionResult.LegCompleted(
                        completedLegIndex = currentIndex,
                        nextLegIndex = currentIndex + 1,
                        isFinalLeg = false
                    )
                }
            }
        }

        // --- Continuous Progress & Station Detection & Dead Reckoning ---

        val distanceToOrigin = if (originCoords != null) {
            calculateDistanceMeters(userLat, userLon, originCoords.first, originCoords.second)
        } else Double.MAX_VALUE

        val now = System.currentTimeMillis()
        val timeSinceLastGpsSec = ((now - lastLocationTimeMillis) / 1000).coerceAtLeast(0)
        val isGpsInaccurate = (locationAccuracyMeters != null && locationAccuracyMeters > 50.0f) || timeSinceLastGpsSec > 25

        if (isTransitLeg) {
            val currentProgressInfo = ActiveTripProgressTracker.progressState.value
            val totalLegDist = if (originCoords != null && targetCoords != null) {
                calculateDistanceMeters(originCoords.first, originCoords.second, targetCoords.first, targetCoords.second)
            } else 0.0

            val hasMovedAwayByGps = distanceToOrigin > 120.0 && distanceToTarget < (totalLegDist - 80.0)
            val isManuallyBoarded = boardedLegIndices.contains(currentIndex)
            val isCurrentlyBoarded = isManuallyBoarded || currentProgressInfo.isBoarded || hasMovedAwayByGps

            if (isCurrentlyBoarded) {
                boardedLegIndices.add(currentIndex)
            }

            if (!isCurrentlyBoarded) {
                // User is STILL AT THE STATION waiting for transit!
                // Freeze progress at 0.05f (origin station icon). Do NOT creep along the line.
                val modeLabel = when (currentLeg.mode) {
                    TransitMode.SUBWAY -> "Espera al metro"
                    TransitMode.BUS -> "Espera al autobús"
                    TransitMode.RAIL -> "Espera al tren"
                    TransitMode.TRAM -> "Espera al tranvía"
                    else -> "Espera al transporte"
                }
                val waitMins = ((currentLeg.durationSeconds / 60) / 2).coerceAtLeast(1)
                val waitMessage = "$modeLabel: $waitMins min"

                ActiveTripProgressTracker.updateProgress(
                    progressWithinLeg = 0.05f,
                    waitTimeMessage = waitMessage,
                    isDeadReckoning = false,
                    isBoarded = false,
                    legIndex = currentIndex
                )
            } else {
                // User HAS BOARDED / DEPARTED station!
                val departureTimeMs = if (currentProgressInfo.transitDepartureTimeMs > 0L) {
                    currentProgressInfo.transitDepartureTimeMs
                } else {
                    now
                }

                if (isGpsInaccurate) {
                    // Underground tunnel / weak GPS: Time-based Dead Reckoning FROM ACTUAL DEPARTURE TIME
                    val legDuration = currentLeg.durationSeconds.coerceAtLeast(60).toFloat()
                    val elapsedSec = ((now - departureTimeMs) / 1000).coerceAtLeast(0).toFloat()
                    val deadReckoningProgress = (elapsedSec / legDuration).coerceIn(0.05f, 0.98f)

                    ActiveTripProgressTracker.updateProgress(
                        progressWithinLeg = deadReckoningProgress,
                        waitTimeMessage = null,
                        isDeadReckoning = true,
                        isBoarded = true,
                        transitDepartureTimeMs = departureTimeMs,
                        legIndex = currentIndex
                    )
                } else {
                    // Normal GPS continuous tracking
                    val progress = if (totalLegDist > 10.0) {
                        (1.0 - (distanceToTarget / totalLegDist)).toFloat().coerceIn(0.05f, 0.98f)
                    } else 0.5f

                    ActiveTripProgressTracker.updateProgress(
                        progressWithinLeg = progress,
                        waitTimeMessage = null,
                        isDeadReckoning = false,
                        isBoarded = true,
                        transitDepartureTimeMs = departureTimeMs,
                        legIndex = currentIndex
                    )
                }
            }
        } else {
            // Target-Centric Tracking for WALK legs:
            // Orthogonal projection against polyline segments is deactivated for navigation state.
            // Progress and ETA are evaluated purely target-centrically based on Haversine distance to targetCoords.
            val totalLegDist = if (originCoords != null && targetCoords != null) {
                calculateDistanceMeters(originCoords.first, originCoords.second, targetCoords.first, targetCoords.second)
            } else 0.0

            val progress = if (totalLegDist > 10.0) {
                (1.0 - (distanceToTarget / totalLegDist)).toFloat().coerceIn(0.02f, 0.98f)
            } else 0.5f

            // Check visual deviation from precalculated polyline purely for map styling (opacity reduction & desire line)
            val polylineDist = calculateDistanceToLegPolyline(userLat, userLon, currentLeg)
            val isPedestrianDeviated = polylineDist > PEDESTRIAN_DEVIATION_THRESHOLD_METERS

            // Recalculate dynamic walking ETA (T_walk) based on Transitous duration & remaining progress fraction
            val dynamicWalkMins = calculateDynamicWalkMinutes(distanceToTarget, currentLeg)

            ActiveTripProgressTracker.updateProgress(
                progressWithinLeg = progress,
                waitTimeMessage = null,
                isDeadReckoning = false,
                legIndex = currentIndex,
                distanceToTargetMeters = distanceToTarget,
                isPedestrianDeviated = isPedestrianDeviated,
                dynamicWalkMinutesRemaining = dynamicWalkMins
            )
        }

        return StepProgressionResult.OnTrack(
            currentLegIndex = currentIndex,
            distanceToNextTargetMeters = distanceToTarget,
            targetName = currentLeg.toName
        )
    }
}
