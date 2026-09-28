package com.example.ui.map.components

import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles directional bus geometry processing for EMT and Metrobús:
 * 1. Detection and closing of circular bus line loops (lines starting with 'C', e.g. C1, C2, C3).
 * 2. Detection of regulation/terminal stops where one direction ends and another begins.
 * 3. Directional classification and stop filtering (showing next stations from regulation stops).
 */
object BusRouteDirectionManager {

    private const val REGULATION_STOP_THRESHOLD_METERS = 85.0
    private const val CIRCULAR_CLOSE_THRESHOLD_METERS = 100.0

    /**
     * Checks if a line is circular (e.g. C1, C2, C3 or explicit circular naming).
     */
    fun isCircularLine(lineRef: String): Boolean {
        val clean = lineRef.trim().removePrefix("L").removePrefix("l").uppercase()
        return clean.startsWith("C") || clean.contains("CIRCULAR")
    }

    /**
     * For circular lines (C1, C2, C3), merges pairs of shapes that represent the same
     * circular loop split into two halves ("destinations") by timing/regulation points.
     * Returns closed rings for each circular direction.
     */
    fun closeCircularShapes(
        lineRef: String,
        shapes: List<EmtMapOverlayLoader.EmtRouteShape>
    ): List<EmtMapOverlayLoader.EmtRouteShape> {
        if (!isCircularLine(lineRef) || shapes.size < 2) {
            return shapes
        }

        val remaining = shapes.toMutableList()
        val closedShapes = mutableListOf<EmtMapOverlayLoader.EmtRouteShape>()

        var i = 0
        while (i < remaining.size) {
            val shapeA = remaining[i]
            val ptsA = shapeA.points
            if (ptsA.size < 2) {
                closedShapes.add(shapeA)
                remaining.removeAt(i)
                continue
            }

            val startA = ptsA.first()
            val endA = ptsA.last()

            // Look for a matching shapeB whose start is close to endA, and end is close to startA
            var matchedIndex = -1
            for (j in (i + 1) until remaining.size) {
                val shapeB = remaining[j]
                val ptsB = shapeB.points
                if (ptsB.size < 2) continue

                val startB = ptsB.first()
                val endB = ptsB.last()

                val dEndAStartB = distanceMeters(endA, startB)
                val dEndBStartA = distanceMeters(endB, startA)

                if (dEndAStartB <= CIRCULAR_CLOSE_THRESHOLD_METERS && dEndBStartA <= CIRCULAR_CLOSE_THRESHOLD_METERS) {
                    matchedIndex = j
                    break
                }
            }

            if (matchedIndex != -1) {
                val shapeB = remaining[matchedIndex]
                val ptsB = shapeB.points

                // Merge points: shapeA + shapeB without duplicate seam point + closing point
                val mergedPoints = ArrayList<GeoPoint>(ptsA.size + ptsB.size + 1)
                mergedPoints.addAll(ptsA)
                // Avoid duplicating the connection point if very close
                val dropCount = if (ptsB.isNotEmpty() && distanceMeters(ptsA.last(), ptsB.first()) < 15.0) 1 else 0
                mergedPoints.addAll(ptsB.drop(dropCount))

                // Ensure loop is explicitly closed
                if (distanceMeters(mergedPoints.last(), mergedPoints.first()) > 1.0) {
                    mergedPoints.add(mergedPoints.first())
                }

                val unifiedHeadsign = shapeA.headsign ?: shapeB.headsign ?: "Circular $lineRef"

                closedShapes.add(
                    EmtMapOverlayLoader.EmtRouteShape(
                        lineRef = shapeA.lineRef,
                        shapeId = "${shapeA.shapeId}_${shapeB.shapeId}_circular",
                        points = mergedPoints,
                        headsign = unifiedHeadsign
                    )
                )

                // Remove both processed shapes
                remaining.removeAt(matchedIndex)
                remaining.removeAt(i)
            } else {
                // No cyclic match found; keep shape as-is
                closedShapes.add(shapeA)
                remaining.removeAt(i)
            }
        }

        return closedShapes
    }

    /**
     * Information about a regulation / terminus stop between two directions.
     */
    data class RegulationStopInfo(
        val location: GeoPoint,
        val incomingLineRef: String,
        val outgoingLineRef: String,
        val isTerminalRegulator: Boolean = true
    )

    /**
     * Detects regulation/terminus points where an inbound polyline ends and an outbound polyline begins.
     */
    fun detectRegulationPoints(shapes: List<EmtMapOverlayLoader.EmtRouteShape>): List<RegulationStopInfo> {
        val regulationPoints = mutableListOf<RegulationStopInfo>()
        if (shapes.size < 2) return emptyList()

        for (i in shapes.indices) {
            val shapeA = shapes[i]
            val endA = shapeA.points.lastOrNull() ?: continue

            for (j in shapes.indices) {
                if (i == j) continue
                val shapeB = shapes[j]
                val startB = shapeB.points.firstOrNull() ?: continue

                if (distanceMeters(endA, startB) <= REGULATION_STOP_THRESHOLD_METERS) {
                    regulationPoints.add(
                        RegulationStopInfo(
                            location = endA,
                            incomingLineRef = shapeA.lineRef,
                            outgoingLineRef = shapeB.lineRef,
                            isTerminalRegulator = true
                        )
                    )
                }
            }
        }
        return regulationPoints
    }

    /**
     * Checks if a stop coordinate is at or near a regulation / terminus point.
     */
    fun isNearRegulationPoint(stopLocation: GeoPoint, regulationPoints: List<RegulationStopInfo>): Boolean {
        return regulationPoints.any { distanceMeters(stopLocation, it.location) <= REGULATION_STOP_THRESHOLD_METERS }
    }

    /**
     * Filters a sequence of stops along a bus line starting from a regulation stop onwards.
     * In real-life transport, at a regulation/terminus stop, the bus begins a new cycle.
     * Passengers boarding here travel to subsequent stops, so prior stops from the arriving
     * service are filtered out.
     */
    fun <T> filterStopsFromRegulationStop(
        stopsInLineOrder: List<T>,
        regulationIndex: Int
    ): List<T> {
        if (regulationIndex < 0 || regulationIndex >= stopsInLineOrder.size) {
            return stopsInLineOrder
        }
        return stopsInLineOrder.subList(regulationIndex, stopsInLineOrder.size)
    }

    /**
     * Computes great-circle distance between two GeoPoints in meters.
     */
    fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val r = 6371000.0 // Earth radius in meters
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Calculates perpendicular / closest distance from a point to a line segment in meters.
     */
    fun distanceToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val midLatRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosLat = cos(midLatRad)
        val ax = a.longitude * cosLat * 111320.0
        val ay = a.latitude * 110540.0
        val bx = b.longitude * cosLat * 111320.0
        val by = b.latitude * 110540.0
        val px = p.longitude * cosLat * 111320.0
        val py = p.latitude * 110540.0

        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) {
            return hypot(px - ax, py - ay)
        }
        val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)
        val projX = ax + clampedT * dx
        val projY = ay + clampedT * dy
        return hypot(px - projX, py - projY)
    }

    /**
     * Calculates the minimum distance in meters between a point and an entire polyline.
     */
    fun minDistanceToPolyline(point: GeoPoint, polyline: List<GeoPoint>): Double {
        if (polyline.isEmpty()) return Double.MAX_VALUE
        if (polyline.size == 1) return distanceMeters(point, polyline[0])

        var minD = Double.MAX_VALUE
        for (i in 0 until (polyline.size - 1)) {
            val d = distanceToSegment(point, polyline[i], polyline[i + 1])
            if (d < minD) {
                minD = d
            }
        }
        return minD
    }

    /**
     * Determines which directional shape corresponds to a given bus stop location.
     * Takes into account:
     * 1. Regulation/terminal stops: if the stop is located where shape A ends and shape B begins,
     *    in real public transport the departing bus starts along shape B (outgoing direction).
     * 2. Perpendicular/minimum distance to polyline: the stop is physically on the side of the street
     *    where that direction runs.
     */
    fun findMatchingShapeForStop(
        stopLocation: GeoPoint,
        shapes: List<EmtMapOverlayLoader.EmtRouteShape>
    ): EmtMapOverlayLoader.EmtRouteShape? {
        if (shapes.isEmpty()) return null
        if (shapes.size == 1) return shapes.first()

        // 1. Regulation stop detection:
        val regPoints = detectRegulationPoints(shapes)
        for (reg in regPoints) {
            if (distanceMeters(stopLocation, reg.location) <= REGULATION_STOP_THRESHOLD_METERS) {
                // Outgoing shape begins here! Find the shape starting near this point
                val outgoing = shapes.find { s ->
                    val start = s.points.firstOrNull() ?: return@find false
                    distanceMeters(stopLocation, start) <= REGULATION_STOP_THRESHOLD_METERS
                }
                if (outgoing != null) return outgoing
            }
        }

        // 2. Minimum distance from stop to shape segments
        return shapes.minByOrNull { shape ->
            minDistanceToPolyline(stopLocation, shape.points)
        }
    }

    /**
     * For Metrobús lines (with keys e.g. "L150_0" and "L150_1"), isolates and returns only
     * the shape matching the direction passing through the given stop location.
     */
    fun findMatchingMetrobusDirectionForStop(
        stopLocation: GeoPoint,
        shapesByDirection: Map<String, List<GeoPoint>>
    ): Map<String, List<GeoPoint>> {
        if (shapesByDirection.size <= 1) return shapesByDirection

        // Group by line code (e.g. "L150_0", "L150_1" -> grouped by "L150")
        val lineGroups = shapesByDirection.keys.groupBy { it.substringBeforeLast("_") }
        val result = mutableMapOf<String, List<GeoPoint>>()

        lineGroups.forEach { (_, keys) ->
            if (keys.size <= 1) {
                keys.forEach { k -> shapesByDirection[k]?.let { result[k] = it } }
            } else {
                // Check regulation stop between directions
                var matchedKey: String? = null
                for (i in keys.indices) {
                    val keyA = keys[i]
                    val ptsA = shapesByDirection[keyA] ?: continue
                    val endA = ptsA.lastOrNull() ?: continue

                    for (j in keys.indices) {
                        if (i == j) continue
                        val keyB = keys[j]
                        val ptsB = shapesByDirection[keyB] ?: continue
                        val startB = ptsB.firstOrNull() ?: continue

                        if (distanceMeters(endA, startB) <= REGULATION_STOP_THRESHOLD_METERS &&
                            distanceMeters(stopLocation, startB) <= REGULATION_STOP_THRESHOLD_METERS
                        ) {
                            matchedKey = keyB
                            break
                        }
                    }
                    if (matchedKey != null) break
                }

                if (matchedKey == null) {
                    matchedKey = keys.minByOrNull { key ->
                        val pts = shapesByDirection[key] ?: return@minByOrNull Double.MAX_VALUE
                        minDistanceToPolyline(stopLocation, pts)
                    }
                }

                if (matchedKey != null) {
                    shapesByDirection[matchedKey]?.let { result[matchedKey] = it }
                }
            }
        }
        return result
    }
}
