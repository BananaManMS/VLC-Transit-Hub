package com.example.ui.map.components

import org.osmdroid.util.GeoPoint

/**
 * Shared geometry and lateral offset calculation engine for transit polylines (Metro and Cercanías).
 * Provides perpendicular bisector (miter) offsets, segment proximity detection, ramping, and RDP simplification.
 */
object MapPolylineOffsetHelper {
    data class Point2D(val x: Double, val y: Double)

    fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val latMid = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
        val dy = (p2.latitude - p1.latitude) * 111111.0
        val dx = (p2.longitude - p1.longitude) * 111111.0 * Math.cos(latMid)
        return Math.sqrt(dx * dx + dy * dy)
    }

    fun distancePointToSegment(p: GeoPoint, s1: GeoPoint, s2: GeoPoint): Double {
        val latMid = Math.toRadians((s1.latitude + s2.latitude) / 2.0)
        val x2 = (s2.longitude - s1.longitude) * 111111.0 * Math.cos(latMid)
        val y2 = (s2.latitude - s1.latitude) * 111111.0
        val xp = (p.longitude - s1.longitude) * 111111.0 * Math.cos(latMid)
        val yp = (p.latitude - s1.latitude) * 111111.0

        val segmentLenSq = x2 * x2 + y2 * y2
        if (segmentLenSq < 1e-9) {
            return Math.sqrt(xp * xp + yp * yp)
        }

        val t = ((xp * x2) + (yp * y2)) / segmentLenSq
        val tClamped = Math.max(0.0, Math.min(1.0, t))

        val closestX = tClamped * x2
        val closestY = tClamped * y2

        val dx = xp - closestX
        val dy = yp - closestY
        return Math.sqrt(dx * dx + dy * dy)
    }

    fun isRouteCloseToPoint(point: GeoPoint, segments: List<List<GeoPoint>>, maxDistance: Double): Boolean {
        for (segment in segments) {
            if (segment.isEmpty()) continue
            if (segment.size == 1) {
                if (distanceBetween(point, segment[0]) < maxDistance) {
                    return true
                }
                continue
            }
            for (i in 0 until segment.size - 1) {
                val s1 = segment[i]
                val s2 = segment[i + 1]

                val minLat = Math.min(s1.latitude, s2.latitude) - 0.001
                val maxLat = Math.max(s1.latitude, s2.latitude) + 0.001
                val minLon = Math.min(s1.longitude, s2.longitude) - 0.001
                val maxLon = Math.max(s1.longitude, s2.longitude) + 0.001

                if (point.latitude < minLat || point.latitude > maxLat ||
                    point.longitude < minLon || point.longitude > maxLon) {
                    continue
                }

                if (distancePointToSegment(point, s1, s2) < maxDistance) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Uniform spatial grid indexing route segments for O(1) exact coexistence checks
     * matching isRouteCloseToPoint's 0.001° bounding box semantics.
     */
    class SegmentSpatialGrid(private val cellStep: Double = 0.001) {
        private class IndexedSegment(
            val routeRef: String,
            val s1: GeoPoint,
            val s2: GeoPoint?, // null for 1-point degenerate segments
            val minLat: Double,
            val maxLat: Double,
            val minLon: Double,
            val maxLon: Double
        )

        private val grid = HashMap<Long, MutableList<IndexedSegment>>(1024)

        private fun packKey(gx: Int, gy: Int): Long =
            (gx.toLong() shl 32) or (gy.toLong() and 0xFFFFFFFFL)

        fun addSegments(routeRef: String, segments: List<List<GeoPoint>>) {
            for (segment in segments) {
                if (segment.isEmpty()) continue
                if (segment.size == 1) {
                    val p = segment[0]
                    val minLat = p.latitude - cellStep
                    val maxLat = p.latitude + cellStep
                    val minLon = p.longitude - cellStep
                    val maxLon = p.longitude + cellStep
                    val item = IndexedSegment(routeRef, p, null, minLat, maxLat, minLon, maxLon)
                    insertIntoCells(item, minLat, maxLat, minLon, maxLon)
                    continue
                }
                for (i in 0 until segment.size - 1) {
                    val s1 = segment[i]
                    val s2 = segment[i + 1]
                    val minLat = Math.min(s1.latitude, s2.latitude) - cellStep
                    val maxLat = Math.max(s1.latitude, s2.latitude) + cellStep
                    val minLon = Math.min(s1.longitude, s2.longitude) - cellStep
                    val maxLon = Math.max(s1.longitude, s2.longitude) + cellStep
                    val item = IndexedSegment(routeRef, s1, s2, minLat, maxLat, minLon, maxLon)
                    insertIntoCells(item, minLat, maxLat, minLon, maxLon)
                }
            }
        }

        private fun insertIntoCells(
            item: IndexedSegment,
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double
        ) {
            val gx0 = kotlin.math.floor(minLat / cellStep).toInt()
            val gx1 = kotlin.math.floor(maxLat / cellStep).toInt()
            val gy0 = kotlin.math.floor(minLon / cellStep).toInt()
            val gy1 = kotlin.math.floor(maxLon / cellStep).toInt()
            for (gx in gx0..gx1) {
                for (gy in gy0..gy1) {
                    grid.getOrPut(packKey(gx, gy)) { ArrayList(4) }.add(item)
                }
            }
        }

        fun collectNearbyRoutes(
            point: GeoPoint,
            selfRef: String,
            maxDistance: Double,
            outRefs: MutableList<String>
        ) {
            val gx = kotlin.math.floor(point.latitude / cellStep).toInt()
            val gy = kotlin.math.floor(point.longitude / cellStep).toInt()
            val bucket = grid[packKey(gx, gy)] ?: return
            val pLat = point.latitude
            val pLon = point.longitude
            for (i in 0 until bucket.size) {
                val seg = bucket[i]
                if (seg.routeRef == selfRef) continue
                if (outRefs.contains(seg.routeRef)) continue
                if (pLat < seg.minLat || pLat > seg.maxLat || pLon < seg.minLon || pLon > seg.maxLon) continue
                val dist = if (seg.s2 == null) {
                    distanceBetween(point, seg.s1)
                } else {
                    distancePointToSegment(point, seg.s1, seg.s2)
                }
                if (dist < maxDistance) {
                    outRefs.add(seg.routeRef)
                }
            }
        }
    }

    fun smoothOffsets(rawOffsets: DoubleArray, radius: Int = 3): DoubleArray {
        val n = rawOffsets.size
        val smoothed = DoubleArray(n)
        for (i in 0 until n) {
            var sum = 0.0
            var count = 0
            val start = Math.max(0, i - radius)
            val end = Math.min(n - 1, i + radius)
            for (k in start..end) {
                sum += rawOffsets[k]
                count++
            }
            smoothed[i] = sum / count
        }
        return smoothed
    }

    fun scaleOffsets(offsets: DoubleArray, factor: Double): DoubleArray {
        val result = DoubleArray(offsets.size)
        for (i in offsets.indices) {
            result[i] = offsets[i] * factor
        }
        return result
    }

    fun offsetPointsWithArray(points: List<GeoPoint>, offsets: DoubleArray): List<GeoPoint> {
        val n = points.size
        if (n < 2) return points

        val normX = DoubleArray(n - 1)
        val normY = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val latMid = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
            val dx = (p2.longitude - p1.longitude) * 111111.0 * Math.cos(latMid)
            val dy = (p2.latitude - p1.latitude) * 111111.0
            val len = Math.sqrt(dx * dx + dy * dy)
            if (len > 1e-9) {
                normX[i] = dy / len
                normY[i] = -dx / len
            }
        }

        val result = ArrayList<GeoPoint>(n)
        for (i in 0 until n) {
            val curr = points[i]
            val offsetMeters = offsets[i]
            if (offsetMeters == 0.0) {
                result.add(GeoPoint(curr.latitude, curr.longitude))
                continue
            }

            var mx: Double
            var my: Double
            if (i == 0) {
                mx = normX[0]
                my = normY[0]
            } else if (i == n - 1) {
                mx = normX[n - 2]
                my = normY[n - 2]
            } else {
                val n1x = normX[i - 1]
                val n1y = normY[i - 1]
                val n2x = normX[i]
                val n2y = normY[i]

                val sumX = n1x + n2x
                val sumY = n1y + n2y
                val mLen = Math.sqrt(sumX * sumX + sumY * sumY)

                if (mLen > 1e-9) {
                    val bx = sumX / mLen
                    val by = sumY / mLen
                    val cosHalfAngle = bx * n1x + by * n1y
                    val scale = if (cosHalfAngle > 0.1) {
                        Math.min(1.0 / cosHalfAngle, 2.0)
                    } else {
                        2.0
                    }
                    mx = bx * scale
                    my = by * scale
                } else {
                    mx = n1x
                    my = n1y
                }
            }

            val shiftLat = (offsetMeters * my) / 111111.0
            val shiftLon = (offsetMeters * mx) / (111111.0 * Math.cos(Math.toRadians(curr.latitude)))

            result.add(GeoPoint(curr.latitude + shiftLat, curr.longitude + shiftLon))
        }

        return result
    }

    fun mergeSegments(segments: List<List<GeoPoint>>): List<List<GeoPoint>> {
        if (segments.isEmpty()) return emptyList()
        val pool = segments.map { it.toList() }.toMutableList()
        val merged = ArrayList<List<GeoPoint>>()

        while (pool.isNotEmpty()) {
            val currentPath = ArrayList<GeoPoint>(pool.removeAt(0))
            var joinedAny: Boolean
            do {
                joinedAny = false
                var i = 0
                while (i < pool.size) {
                    val s = pool[i]
                    if (s.isEmpty()) {
                        pool.removeAt(i)
                        continue
                    }
                    val distLastFirst = distanceBetween(currentPath.last(), s.first())
                    val distLastLast = distanceBetween(currentPath.last(), s.last())
                    val distFirstLast = distanceBetween(currentPath.first(), s.last())
                    val distFirstFirst = distanceBetween(currentPath.first(), s.first())

                    if (distLastFirst < 15.0) {
                        currentPath.addAll(s.subList(1, s.size))
                        pool.removeAt(i)
                        joinedAny = true
                    } else if (distLastLast < 15.0) {
                        currentPath.addAll(s.reversed().subList(1, s.size))
                        pool.removeAt(i)
                        joinedAny = true
                    } else if (distFirstLast < 15.0) {
                        currentPath.addAll(0, s.subList(0, s.size - 1))
                        pool.removeAt(i)
                        joinedAny = true
                    } else if (distFirstFirst < 15.0) {
                        currentPath.addAll(0, s.reversed().subList(0, s.size - 1))
                        pool.removeAt(i)
                        joinedAny = true
                    } else {
                        i++
                    }
                }
            } while (joinedAny && pool.isNotEmpty())
            merged.add(currentPath)
        }
        return merged
    }

    fun normalizeDirection(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size < 2) return points
        val start = points.first()
        val end = points.last()

        val deltaLat = Math.abs(end.latitude - start.latitude)
        val deltaLon = Math.abs(end.longitude - start.longitude)

        val shouldReverse = if (deltaLat > deltaLon) {
            start.latitude > end.latitude
        } else {
            start.longitude > end.longitude
        }

        return if (shouldReverse) points.reversed() else points
    }

    fun rdpSimplify(points: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
        val n = points.size
        if (n < 3) return points

        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true

        // Iterative explicit stack of (start, end) ranges to eliminate recursive subList allocations
        var stackCap = 64
        var starts = IntArray(stackCap)
        var ends = IntArray(stackCap)
        var sp = 0
        starts[sp] = 0
        ends[sp] = n - 1
        sp++

        while (sp > 0) {
            sp--
            val start = starts[sp]
            val end = ends[sp]
            if (end - start <= 1) continue

            val pStart = points[start]
            val pEnd = points[end]
            val x1 = pStart.longitude
            val y1 = pStart.latitude
            val x2 = pEnd.longitude
            val y2 = pEnd.latitude
            val dx = x2 - x1
            val dy = y2 - y1
            val den = Math.sqrt(dy * dy + dx * dx)
            val crossOffset = x2 * y1 - y2 * x1

            var dmax = 0.0
            var index = start
            for (i in (start + 1) until end) {
                val p = points[i]
                val d = if (den == 0.0) 0.0 else Math.abs(dy * p.longitude - dx * p.latitude + crossOffset) / den
                if (d > dmax) {
                    index = i
                    dmax = d
                }
            }

            if (dmax > epsilon) {
                keep[index] = true
                if (sp + 2 >= stackCap) {
                    stackCap *= 2
                    starts = starts.copyOf(stackCap)
                    ends = ends.copyOf(stackCap)
                }
                starts[sp] = start
                ends[sp] = index
                sp++
                starts[sp] = index
                ends[sp] = end
                sp++
            }
        }

        val out = ArrayList<GeoPoint>(n / 3 + 4)
        for (i in 0 until n) {
            if (keep[i]) out.add(points[i])
        }
        return out
    }

    fun perpendicularDistance(p: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
        val x = p.longitude
        val y = p.latitude
        val x1 = lineStart.longitude
        val y1 = lineStart.latitude
        val x2 = lineEnd.longitude
        val y2 = lineEnd.latitude

        val dx = x2 - x1
        val dy = y2 - y1

        val num = Math.abs(dy * x - dx * y + x2 * y1 - y2 * x1)
        val den = Math.sqrt(dy * dy + dx * dx)
        return if (den == 0.0) 0.0 else num / den
    }
}
