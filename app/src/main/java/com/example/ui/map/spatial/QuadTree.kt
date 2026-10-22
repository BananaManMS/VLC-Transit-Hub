package com.example.ui.map.spatial

import com.example.data.database.GeoportalStopEntity

data class BoundingBox2D(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    fun contains(lat: Double, lon: Double): Boolean {
        return lat in minLat..maxLat && lon in minLon..maxLon
    }

    fun intersects(other: BoundingBox2D): Boolean {
        return !(other.minLat > maxLat || other.maxLat < minLat || other.minLon > maxLon || other.maxLon < minLon)
    }
}

class QuadTree(
    val boundary: BoundingBox2D,
    private val capacity: Int = 32,
    private val maxDepth: Int = 10,
    private val depth: Int = 0
) {
    private val points = mutableListOf<GeoportalStopEntity>()
    private var northWest: QuadTree? = null
    private var northEast: QuadTree? = null
    private var southWest: QuadTree? = null
    private var southEast: QuadTree? = null
    private var divided = false

    fun insert(stop: GeoportalStopEntity): Boolean {
        if (!boundary.contains(stop.lat, stop.lon)) {
            return false
        }

        if (points.size < capacity || depth >= maxDepth) {
            points.add(stop)
            return true
        }

        if (!divided) {
            subdivide()
        }

        return (northWest?.insert(stop) == true ||
                northEast?.insert(stop) == true ||
                southWest?.insert(stop) == true ||
                southEast?.insert(stop) == true)
    }

    private fun subdivide() {
        val midLat = (boundary.minLat + boundary.maxLat) / 2.0
        val midLon = (boundary.minLon + boundary.maxLon) / 2.0

        northWest = QuadTree(BoundingBox2D(midLat, boundary.maxLat, boundary.minLon, midLon), capacity, maxDepth, depth + 1)
        northEast = QuadTree(BoundingBox2D(midLat, boundary.maxLat, midLon, boundary.maxLon), capacity, maxDepth, depth + 1)
        southWest = QuadTree(BoundingBox2D(boundary.minLat, midLat, boundary.minLon, midLon), capacity, maxDepth, depth + 1)
        southEast = QuadTree(BoundingBox2D(boundary.minLat, midLat, midLon, boundary.maxLon), capacity, maxDepth, depth + 1)

        divided = true

        val existing = ArrayList(points)
        points.clear()
        for (stop in existing) {
            if (!(northWest!!.insert(stop) || northEast!!.insert(stop) || southWest!!.insert(stop) || southEast!!.insert(stop))) {
                points.add(stop)
            }
        }
    }

    fun queryRange(range: BoundingBox2D, result: MutableList<GeoportalStopEntity>) {
        if (!boundary.intersects(range)) {
            return
        }

        for (stop in points) {
            if (range.contains(stop.lat, stop.lon)) {
                result.add(stop)
            }
        }

        if (divided) {
            northWest?.queryRange(range, result)
            northEast?.queryRange(range, result)
            southWest?.queryRange(range, result)
            southEast?.queryRange(range, result)
        }
    }

    companion object {
        fun buildTree(stops: List<GeoportalStopEntity>): QuadTree? {
            if (stops.isEmpty()) return null
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE

            for (s in stops) {
                if (s.lat < minLat) minLat = s.lat
                if (s.lat > maxLat) maxLat = s.lat
                if (s.lon < minLon) minLon = s.lon
                if (s.lon > maxLon) maxLon = s.lon
            }

            val latPad = ((maxLat - minLat).coerceAtLeast(0.01)) * 0.05
            val lonPad = ((maxLon - minLon).coerceAtLeast(0.01)) * 0.05

            val tree = QuadTree(
                BoundingBox2D(
                    minLat - latPad,
                    maxLat + latPad,
                    minLon - lonPad,
                    maxLon + lonPad
                )
            )

            for (s in stops) {
                tree.insert(s)
            }

            return tree
        }
    }
}
