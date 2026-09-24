package com.example.ui.map.components

import android.graphics.Path
import org.osmdroid.util.GeoPoint

/**
 * Geometric and styling utilities for EMT route polyline rendering,
 * directional arrows calculation, and automatic topology-based circular ring stitching.
 */
object EmtPolylineUtils {

    data class MilestoneConfig(
        val strokeWidth: Float,
        val recurrencePixels: Double,
        val initialOffsetPixels: Double,
        val arrowPath: Path?,
        val arrowStrokeWidth: Float
    )

    /**
     * Determines directional chevron appearance and frequency based on zoom level.
     */
    fun getMilestoneConfig(zoom: Double): MilestoneConfig {
        return when {
            zoom < 14.0 -> {
                MilestoneConfig(
                    strokeWidth = 6.5f,
                    recurrencePixels = 0.0,
                    initialOffsetPixels = 0.0,
                    arrowPath = null,
                    arrowStrokeWidth = 0f
                )
            }
            zoom < 15.5 -> {
                MilestoneConfig(
                    strokeWidth = 7.5f,
                    recurrencePixels = 260.0,
                    initialOffsetPixels = 100.0,
                    arrowPath = createStyledChevronPath(length = 14f, halfWidth = 8f, indent = 5f),
                    arrowStrokeWidth = 2.2f
                )
            }
            zoom < 17.0 -> {
                MilestoneConfig(
                    strokeWidth = 9.0f,
                    recurrencePixels = 220.0,
                    initialOffsetPixels = 80.0,
                    arrowPath = createStyledChevronPath(length = 17f, halfWidth = 10f, indent = 6f),
                    arrowStrokeWidth = 2.6f
                )
            }
            else -> {
                MilestoneConfig(
                    strokeWidth = 10.0f,
                    recurrencePixels = 180.0,
                    initialOffsetPixels = 60.0,
                    arrowPath = createStyledChevronPath(length = 20f, halfWidth = 12f, indent = 7.5f),
                    arrowStrokeWidth = 3.0f
                )
            }
        }
    }

    /**
     * Constructs a closed, aerodynamic chevron/dart pointing along +X (direction of travel).
     */
    fun createStyledChevronPath(length: Float, halfWidth: Float, indent: Float): Path {
        return Path().apply {
            moveTo(length * 0.5f, 0f)
            lineTo(-length * 0.5f, -halfWidth)
            lineTo(-length * 0.5f + indent, 0f)
            lineTo(-length * 0.5f, halfWidth)
            close()
        }
    }

    /**
     * Calculates distance between two GeoPoints in meters using the Haversine formula.
     */
    fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
        val c = 2.0 * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))
        return 6371000.0 * c
    }

    /**
     * Calculates perpendicular distance from point P to segment AB in meters.
     */
    fun distancePointToSegment(
        pLat: Double,
        pLon: Double,
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double
    ): Double {
        val pLatRad = Math.toRadians(pLat)
        val cosLat = Math.cos(pLatRad)
        val dx = (bLon - aLon) * cosLat
        val dy = bLat - aLat
        val px = (pLon - aLon) * cosLat
        val py = pLat - aLat
        val denom = dx * dx + dy * dy
        val t = if (denom > 0.0) ((px * dx + py * dy) / denom).coerceIn(0.0, 1.0) else 0.0
        val projLat = aLat + t * (bLat - aLat)
        val projLon = aLon + t * (bLon - aLon)

        val dLatP = Math.toRadians(projLat - pLat)
        val dLonP = Math.toRadians(projLon - pLon)
        val a = Math.sin(dLatP / 2.0) * Math.sin(dLatP / 2.0) +
                Math.cos(pLatRad) * Math.cos(Math.toRadians(projLat)) *
                Math.sin(dLonP / 2.0) * Math.sin(dLonP / 2.0)
        val c = 2.0 * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))
        return 6371000.0 * c
    }

    /**
     * Evaluates whether a closed ring of coordinates encloses a significant geographical area (in km²),
     * distinguishing genuine circular ring routes from linear routes that simply double back on the same corridor.
     */
    fun isSignificantPolygonEnclosure(points: List<GeoPoint>, minAreaKm2: Double = 0.8): Boolean {
        if (points.size < 4) return false
        var area = 0.0
        val cosLat = Math.cos(Math.toRadians(points[0].latitude))
        for (i in points.indices) {
            val j = (i + 1) % points.size
            val x1 = points[i].longitude * 111320.0 * cosLat
            val y1 = points[i].latitude * 110540.0
            val x2 = points[j].longitude * 111320.0 * cosLat
            val y2 = points[j].latitude * 110540.0
            area += (x1 * y2 - x2 * y1)
        }
        val areaKm2 = Math.abs(area) / 2.0 / 1000000.0
        return areaKm2 >= minAreaKm2
    }

    /**
     * Stitches complementary semicircular sub-shapes of circular lines into continuous 360° closed rings.
     * Only applies if lineRef starts with 'C' (e.g. C1, C2, C3) to save calculations on non-circular lines.
     */
    fun stitchCircularShapes(
        lineRef: String,
        rawShapes: List<EmtMapOverlayLoader.EmtRouteShape>
    ): List<EmtMapOverlayLoader.EmtRouteShape> {
        if (rawShapes.size < 2 || !lineRef.startsWith("C", ignoreCase = true)) return rawShapes

        val byPrefix = rawShapes.groupBy { it.shapeId.substringBefore('_') }
        val intermediate = mutableListOf<EmtMapOverlayLoader.EmtRouteShape>()

        for ((prefix, segs) in byPrefix) {
            if (segs.size == 2) {
                val s1 = segs[0]
                val s2 = segs[1]
                val gap1 = distanceBetween(s1.points.last(), s2.points.first())
                val gap2 = distanceBetween(s2.points.last(), s1.points.first())

                val orderedSegs = when {
                    gap1 <= 100.0 -> Pair(s1, s2)
                    gap2 <= 100.0 -> Pair(s2, s1)
                    else -> null
                }

                if (orderedSegs != null) {
                    val firstSeg = orderedSegs.first
                    val secondSeg = orderedSegs.second
                    val stitchedPoints = ArrayList<GeoPoint>(firstSeg.points.size + secondSeg.points.size)
                    stitchedPoints.addAll(firstSeg.points)
                    if (secondSeg.points.size > 1) {
                        stitchedPoints.addAll(secondSeg.points.subList(1, secondSeg.points.size))
                    }
                    val combinedHeadsign = listOfNotNull(firstSeg.headsign, secondSeg.headsign).joinToString(" / ")
                    intermediate.add(
                        EmtMapOverlayLoader.EmtRouteShape(
                            lineRef = lineRef,
                            shapeId = "${prefix}_circular_ring",
                            points = stitchedPoints,
                            headsign = combinedHeadsign.ifBlank { null }
                        )
                    )
                    continue
                }
            }
            intermediate.addAll(segs)
        }

        // Second pass: chain any unstitched segments where end connects to start
        val result = mutableListOf<EmtMapOverlayLoader.EmtRouteShape>()
        val consumed = BooleanArray(intermediate.size)

        for (i in intermediate.indices) {
            if (consumed[i]) continue
            var current = intermediate[i]
            consumed[i] = true

            for (j in intermediate.indices) {
                if (consumed[j]) continue
                val other = intermediate[j]
                val d = distanceBetween(current.points.last(), other.points.first())
                if (d <= 100.0) {
                    val joinedPts = ArrayList<GeoPoint>(current.points.size + other.points.size)
                    joinedPts.addAll(current.points)
                    if (other.points.size > 1) {
                        joinedPts.addAll(other.points.subList(1, other.points.size))
                    }
                    val combinedHs = listOfNotNull(current.headsign, other.headsign).joinToString(" / ")
                    current = EmtMapOverlayLoader.EmtRouteShape(
                        lineRef = lineRef,
                        shapeId = "${current.shapeId}_joined_${other.shapeId}",
                        points = joinedPts,
                        headsign = combinedHs.ifBlank { null }
                    )
                    consumed[j] = true
                    break
                }
            }
            result.add(current)
        }

        return result
    }

    /**
     * Determines the optimal directional route shape passing through the specified stop location.
     */
    fun findBestShapeForStop(
        shapes: List<EmtMapOverlayLoader.EmtRouteShape>,
        stopLocation: GeoPoint,
        targetHeadsign: String? = null
    ): EmtMapOverlayLoader.EmtRouteShape? {
        if (shapes.isEmpty()) return null
        if (shapes.size == 1) return shapes.first()

        val filteredShapes = if (!targetHeadsign.isNullOrBlank()) {
            val matches = shapes.filter { it.headsign?.contains(targetHeadsign, ignoreCase = true) == true }
            if (matches.isNotEmpty()) matches else shapes
        } else shapes

        data class ShapeCandidate(
            val shape: EmtMapOverlayLoader.EmtRouteShape,
            val minDistance: Double,
            val progressFraction: Double
        )

        val candidates = filteredShapes.map { shape ->
            var minD = Double.MAX_VALUE
            var minIdx = 0
            val pts = shape.points
            for (i in 0 until pts.size - 1) {
                val d = distancePointToSegment(
                    stopLocation.latitude, stopLocation.longitude,
                    pts[i].latitude, pts[i].longitude,
                    pts[i + 1].latitude, pts[i + 1].longitude
                )
                if (d < minD) {
                    minD = d
                    minIdx = i
                }
            }
            val fraction = if (pts.size > 1) minIdx.toDouble() / (pts.size - 1) else 0.0
            ShapeCandidate(shape, minD, fraction)
        }

        val sorted = candidates.sortedWith { a, b ->
            val distDiff = a.minDistance - b.minDistance
            if (Math.abs(distDiff) > 15.0) {
                a.minDistance.compareTo(b.minDistance)
            } else {
                a.progressFraction.compareTo(b.progressFraction)
            }
        }

        return sorted.firstOrNull()?.shape
    }
}
