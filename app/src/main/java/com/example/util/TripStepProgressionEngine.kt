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
        ActiveTripProgressTracker.markAsBoarded(legIndex)
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
        val currentTracker = ActiveTripProgressTracker.progressState.value
        val departureTimeMs = if (currentTracker.trackedLegIndex == legIndex && currentTracker.transitDepartureTimeMs > 0L) {
            currentTracker.transitDepartureTimeMs
        } else {
            now
        }
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
     * scaled proportionally by the ratio of remaining straight-line distance to the target
     * over the initial straight-line distance from origin to target.
     * Falls back to Haversine speed calculation only if leg data is unavailable.
     */
    fun calculateDynamicWalkMinutes(
        distanceMeters: Double,
        leg: PlannedLeg? = null
    ): Int {
        if (distanceMeters <= 0.0) return 0

        if (leg != null && (leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE) && leg.durationSeconds > 0) {
            val transitousBaseMins = (leg.durationSeconds / 60.0).coerceAtLeast(1.0)
            val originCoords = getLegOriginCoordinates(leg)
            val targetCoords = getLegTargetCoordinates(leg)
            val totalStraightLineDist = if (originCoords != null && targetCoords != null) {
                calculateDistanceMeters(originCoords.first, originCoords.second, targetCoords.first, targetCoords.second)
            } else if (leg.distanceMeters > 0.0) {
                leg.distanceMeters
            } else 0.0

            if (totalStraightLineDist > 10.0) {
                val remainingFraction = (distanceMeters / totalStraightLineDist).coerceIn(0.0, 1.0)
                val remainingMins = kotlin.math.ceil(transitousBaseMins * remainingFraction).toInt()
                return if (distanceMeters < 25.0) 0 else remainingMins.coerceAtLeast(1)
            }

            return if (distanceMeters < 25.0) 0 else transitousBaseMins.toInt().coerceAtLeast(1)
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
        if (leg.toLat != 0.0 && leg.toLon != 0.0) {
            return Pair(leg.toLat, leg.toLon)
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
        if (leg.fromLat != 0.0 && leg.fromLon != 0.0) {
            return Pair(leg.fromLat, leg.fromLon)
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
                if (distToStation <= 120.0) {
                    boardedLegIndices.remove(0)
                    boardedLegIndices.remove(1)
                    ActiveTripProgressTracker.resetForNewLeg(1)
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
            TransitMode.SUBWAY, TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL, TransitMode.METROBUS, TransitMode.CERCANIAS
        )

        val captureRadius = if (isTransitLeg) 75.0 else CAPTURE_RADIUS_METERS

        // Capture condition: Within capture radius of leg destination (Only for WALK or if already boarded/moving on transit)
        if (distanceToTarget <= captureRadius) {
            val nextIndex = currentIndex + 1
            val isFinalLeg = nextIndex >= legs.size
            boardedLegIndices.remove(currentIndex)
            boardedLegIndices.remove(nextIndex)
            if (!isFinalLeg) {
                ActiveTripProgressTracker.resetForNewLeg(nextIndex)
            } else {
                ActiveTripProgressTracker.updateProgress(
                    progressWithinLeg = 1.0f,
                    isBoarded = true,
                    legIndex = currentIndex
                )
            }
            return StepProgressionResult.LegCompleted(
                completedLegIndex = currentIndex,
                nextLegIndex = nextIndex,
                isFinalLeg = isFinalLeg
            )
        }

        // Also check if user has already entered within range of the NEXT leg's path/origin
        // Safeguard: Only jump to next leg if this leg is WALK, or if current transit leg was actually boarded and progressed!
        if (currentIndex + 1 < legs.size) {
            val nextLeg = legs[currentIndex + 1]
            val nextOriginCoords = getLegOriginCoordinates(nextLeg)
            val currentProgress = ActiveTripProgressTracker.progressState.value
            val isCurrentTransitBoardedOrAdvanced = !isTransitLeg || (currentProgress.isBoarded && currentProgress.trackedLegIndex == currentIndex) || (currentProgress.progressWithinLeg >= 0.70f)

            if (nextOriginCoords != null && isCurrentTransitBoardedOrAdvanced) {
                val distanceToNextOrigin = calculateDistanceMeters(
                    userLat, userLon,
                    nextOriginCoords.first, nextOriginCoords.second
                )
                val nextCaptureRadius = if (nextLeg.mode != TransitMode.WALK) 70.0 else 50.0
                if (distanceToNextOrigin <= nextCaptureRadius) {
                    val nextIndex = currentIndex + 1
                    boardedLegIndices.remove(currentIndex)
                    boardedLegIndices.remove(nextIndex)
                    ActiveTripProgressTracker.resetForNewLeg(nextIndex)
                    return StepProgressionResult.LegCompleted(
                        completedLegIndex = currentIndex,
                        nextLegIndex = nextIndex,
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

            // Distance threshold to consider departed: for TRAM/BUS in city center, 80m is enough to confirm vehicle departure
            val minDepartureDist = when (currentLeg.mode) {
                TransitMode.SUBWAY, TransitMode.RAIL, TransitMode.CERCANIAS -> 150.0
                TransitMode.TRAM -> 80.0
                TransitMode.BUS, TransitMode.METROBUS -> 200.0
                else -> 80.0
            }

            // Check if user has passed or reached intermediate/subsequent stops (via cell towers, Wi-Fi or GPS)
            var hasPassedAnyIntermediateStop = false
            if (currentLeg.intermediateStops.isNotEmpty() && userLat != 0.0 && userLon != 0.0) {
                // Adaptive tolerance for underground cellular repeaters / microcells and coarse location:
                val stopTolerance = maxOf(140.0, (locationAccuracyMeters ?: 40.0f).toDouble() * 1.3)
                for (stop in currentLeg.intermediateStops) {
                    val distToStop = calculateDistanceMeters(userLat, userLon, stop.lat, stop.lon)
                    if (distToStop <= stopTolerance && distanceToOrigin > (minDepartureDist * 0.8)) {
                        hasPassedAnyIntermediateStop = true
                        break
                    }
                }
            }

            // Gradual displacement away from origin towards destination / subsequent stops
            val effectiveMinDepartureDist = maxOf(minDepartureDist, (locationAccuracyMeters ?: 30.0f).toDouble() * 1.3)
            val hasGraduallyMovedAway = distanceToOrigin > effectiveMinDepartureDist &&
                    totalLegDist > effectiveMinDepartureDist &&
                    distanceToTarget < (totalLegDist - 80.0)

            // When user is detected at an intermediate stop or clearly displaced along the corridor towards the destination:
            // This is a definitive confirmation of boarding, safely applied even with coarse cell tower fixes!
            val hasMovedAwayByGpsOrNetwork = hasPassedAnyIntermediateStop || hasGraduallyMovedAway

            val isManuallyBoarded = boardedLegIndices.contains(currentIndex)
            val isTrackerBoarded = currentProgressInfo.isBoarded && currentProgressInfo.trackedLegIndex == currentIndex
            val isCurrentlyBoarded = isManuallyBoarded || isTrackerBoarded || hasMovedAwayByGpsOrNetwork

            if (isCurrentlyBoarded) {
                boardedLegIndices.add(currentIndex)
                if (!currentProgressInfo.isBoarded) {
                    ActiveTripProgressTracker.markAsBoarded(currentIndex)
                }
            }

            if (!isCurrentlyBoarded) {
                // User is STILL AT THE STATION waiting for transit!
                // Freeze progress at 0.05f (origin station icon). Do NOT creep along the line.
                val modeLabel = when (currentLeg.mode) {
                    TransitMode.SUBWAY -> "Espera al metro"
                    TransitMode.BUS, TransitMode.METROBUS -> "Espera al autobús"
                    TransitMode.RAIL, TransitMode.CERCANIAS -> "Espera al tren"
                    TransitMode.TRAM -> "Espera al tranvía"
                    else -> "Espera al transporte"
                }
                val waitMins = ((currentLeg.durationSeconds / 60) / 2).coerceAtLeast(1)
                val waitMessage = "$modeLabel: $waitMins min"
                val initialRemainingStops = (currentLeg.intermediateStops.size + 1).coerceAtLeast(1)

                ActiveTripProgressTracker.updateProgress(
                    progressWithinLeg = 0.05f,
                    waitTimeMessage = waitMessage,
                    isDeadReckoning = false,
                    isBoarded = false,
                    transitDepartureTimeMs = 0L,
                    legIndex = currentIndex,
                    remainingStopsCount = initialRemainingStops
                )
            } else {
                // User HAS BOARDED / DEPARTED station!
                val departureTimeMs = if (currentProgressInfo.trackedLegIndex == currentIndex && currentProgressInfo.transitDepartureTimeMs > 0L) {
                    currentProgressInfo.transitDepartureTimeMs
                } else {
                    now
                }

                val totalStopsInLeg = (currentLeg.intermediateStops.size + 1).coerceAtLeast(1)
                val intermediateStops = currentLeg.intermediateStops

                val previouslyPassedStops = if (currentProgressInfo.trackedLegIndex == currentIndex && currentProgressInfo.remainingStopsCount != null) {
                    (totalStopsInLeg - currentProgressInfo.remainingStopsCount).coerceIn(0, intermediateStops.size)
                } else 0

                if (isGpsInaccurate) {
                    // Underground tunnel / weak GPS: Time-based Dead Reckoning FROM ACTUAL DEPARTURE TIME
                    val legDuration = currentLeg.durationSeconds.coerceAtLeast(60).toFloat()
                    val elapsedSec = ((now - departureTimeMs) / 1000).coerceAtLeast(0).toFloat()
                    val deadReckoningProgress = (elapsedSec / legDuration).coerceIn(0.05f, 0.98f)

                    var passedStops = previouslyPassedStops
                    if (intermediateStops.isNotEmpty()) {
                        for (i in intermediateStops.indices) {
                            val stopProgressThreshold = (i + 1.0f) / totalStopsInLeg
                            if (deadReckoningProgress >= stopProgressThreshold + 0.05f && i <= passedStops) {
                                passedStops = maxOf(passedStops, i + 1)
                            }
                        }
                    } else {
                        passedStops = (deadReckoningProgress * totalStopsInLeg).toInt().coerceIn(0, intermediateStops.size)
                    }

                    passedStops = maxOf(previouslyPassedStops, passedStops).coerceIn(0, intermediateStops.size)

                    val remainingStops = if (distanceToTarget <= captureRadius || deadReckoningProgress >= 0.98f) {
                        0
                    } else {
                        (totalStopsInLeg - passedStops).coerceAtLeast(1)
                    }

                    ActiveTripProgressTracker.updateProgress(
                        progressWithinLeg = deadReckoningProgress,
                        waitTimeMessage = null,
                        isDeadReckoning = true,
                        isBoarded = true,
                        transitDepartureTimeMs = departureTimeMs,
                        legIndex = currentIndex,
                        remainingStopsCount = remainingStops
                    )
                } else {
                    // Normal GPS continuous tracking
                    val progress = if (totalLegDist > 10.0) {
                        (1.0 - (distanceToTarget / totalLegDist)).toFloat().coerceIn(0.05f, 0.98f)
                    } else 0.5f

                    var passedStops = previouslyPassedStops
                    if (intermediateStops.isNotEmpty()) {
                        for (i in intermediateStops.indices) {
                            val stop = intermediateStops[i]
                            val distToStop = if (userLat != 0.0 && userLon != 0.0) {
                                calculateDistanceMeters(userLat, userLon, stop.lat, stop.lon)
                            } else null

                            val stopProgressThreshold = (i + 1.0f) / totalStopsInLeg
                            val isStopReached = (distToStop != null && distToStop <= 100.0) ||
                                    (progress >= stopProgressThreshold + 0.05f)

                            if (isStopReached && i <= passedStops) {
                                passedStops = maxOf(passedStops, i + 1)
                            }
                        }
                    } else {
                        passedStops = (progress * totalStopsInLeg).toInt().coerceIn(0, intermediateStops.size)
                    }

                    passedStops = maxOf(previouslyPassedStops, passedStops).coerceIn(0, intermediateStops.size)

                    val remainingStops = if (distanceToTarget <= captureRadius || progress >= 0.98f) {
                        0
                    } else {
                        (totalStopsInLeg - passedStops).coerceAtLeast(1)
                    }

                    ActiveTripProgressTracker.updateProgress(
                        progressWithinLeg = progress,
                        waitTimeMessage = null,
                        isDeadReckoning = false,
                        isBoarded = true,
                        transitDepartureTimeMs = departureTimeMs,
                        legIndex = currentIndex,
                        remainingStopsCount = remainingStops
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
