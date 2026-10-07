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
        if (points.size < 2) return points

        val result = ArrayList<GeoPoint>(points.size)
        val n = points.size

        val normals = ArrayList<Point2D>(n - 1)
        for (i in 0 until n - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val latMid = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
            val dx = (p2.longitude - p1.longitude) * 111111.0 * Math.cos(latMid)
            val dy = (p2.latitude - p1.latitude) * 111111.0
            val len = Math.sqrt(dx * dx + dy * dy)
            if (len > 1e-9) {
                normals.add(Point2D(dy / len, -dx / len))
            } else {
                normals.add(Point2D(0.0, 0.0))
            }
        }

        for (i in 0 until n) {
            val curr = points[i]
            val miter: Point2D

            if (i == 0) {
                miter = normals[0]
            } else if (i == n - 1) {
                miter = normals[n - 2]
            } else {
                val n1 = normals[i - 1]
                val n2 = normals[i]

                val mx = n1.x + n2.x
                val my = n1.y + n2.y
                val mLen = Math.sqrt(mx * mx + my * my)

                if (mLen > 1e-9) {
                    val bx = mx / mLen
                    val by = my / mLen

                    val cosHalfAngle = bx * n1.x + by * n1.y
                    val scale = if (cosHalfAngle > 0.1) {
                        Math.min(1.0 / cosHalfAngle, 2.0)
                    } else {
                        2.0
                    }
                    miter = Point2D(bx * scale, by * scale)
                } else {
                    miter = n1
                }
            }

            val offsetMeters = offsets[i]
            val shiftLat = (offsetMeters * miter.y) / 111111.0
            val shiftLon = (offsetMeters * miter.x) / (111111.0 * Math.cos(Math.toRadians(curr.latitude)))

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
        if (points.size < 3) return points

        var dmax = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilon) {
            val recResults1 = rdpSimplify(points.subList(0, index + 1), epsilon)
            val recResults2 = rdpSimplify(points.subList(index, points.size), epsilon)
            recResults1.dropLast(1) + recResults2
        } else {
            listOf(points[0], points[end])
        }
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
