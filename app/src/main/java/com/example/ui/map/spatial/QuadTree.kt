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

/**
 * Lightweight generic spatial QuadTree storing coordinates in parallel primitive arrays
 * to avoid per-item lambda invocations during recursive subdivision and range queries.
 */
class SpatialQuadTree<T>(
    val boundary: BoundingBox2D,
    private val capacity: Int = 32,
    private val maxDepth: Int = 10,
    private val depth: Int = 0
) {
    private val items = ArrayList<T>(capacity)
    private var lats = DoubleArray(capacity)
    private var lons = DoubleArray(capacity)
    private var northWest: SpatialQuadTree<T>? = null
    private var northEast: SpatialQuadTree<T>? = null
    private var southWest: SpatialQuadTree<T>? = null
    private var southEast: SpatialQuadTree<T>? = null
    private var divided = false

    fun insert(item: T, lat: Double, lon: Double): Boolean {
        if (!boundary.contains(lat, lon)) {
            return false
        }

        val count = items.size
        if (count < capacity || depth >= maxDepth) {
            if (count == lats.size) {
                val newCap = (lats.size * 2).coerceAtLeast(16)
                lats = lats.copyOf(newCap)
                lons = lons.copyOf(newCap)
            }
            lats[count] = lat
            lons[count] = lon
            items.add(item)
            return true
        }

        if (!divided) {
            subdivide()
        }

        return (northWest!!.insert(item, lat, lon) ||
                northEast!!.insert(item, lat, lon) ||
                southWest!!.insert(item, lat, lon) ||
                southEast!!.insert(item, lat, lon))
    }

    private fun subdivide() {
        val midLat = (boundary.minLat + boundary.maxLat) / 2.0
        val midLon = (boundary.minLon + boundary.maxLon) / 2.0

        northWest = SpatialQuadTree(BoundingBox2D(midLat, boundary.maxLat, boundary.minLon, midLon), capacity, maxDepth, depth + 1)
        northEast = SpatialQuadTree(BoundingBox2D(midLat, boundary.maxLat, midLon, boundary.maxLon), capacity, maxDepth, depth + 1)
        southWest = SpatialQuadTree(BoundingBox2D(boundary.minLat, midLat, boundary.minLon, midLon), capacity, maxDepth, depth + 1)
        southEast = SpatialQuadTree(BoundingBox2D(boundary.minLat, midLat, midLon, boundary.maxLon), capacity, maxDepth, depth + 1)

        divided = true

        val existingCount = items.size
        val existingItems = ArrayList(items)
        val existingLats = lats
        val existingLons = lons
        items.clear()

        for (i in 0 until existingCount) {
            val item = existingItems[i]
            val lat = existingLats[i]
            val lon = existingLons[i]
            if (!(northWest!!.insert(item, lat, lon) ||
                  northEast!!.insert(item, lat, lon) ||
                  southWest!!.insert(item, lat, lon) ||
                  southEast!!.insert(item, lat, lon))) {
                val idx = items.size
                if (idx == lats.size) {
                    val newCap = (lats.size * 2).coerceAtLeast(16)
                    lats = lats.copyOf(newCap)
                    lons = lons.copyOf(newCap)
                }
                lats[idx] = lat
                lons[idx] = lon
                items.add(item)
            }
        }
    }

    fun queryRange(range: BoundingBox2D, result: MutableList<T>) {
        if (!boundary.intersects(range)) {
            return
        }

        val count = items.size
        val localLats = lats
        val localLons = lons
        for (i in 0 until count) {
            val lat = localLats[i]
            val lon = localLons[i]
            if (range.contains(lat, lon)) {
                result.add(items[i])
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
        inline fun <T> buildTree(
            items: List<T>,
            getLat: (T) -> Double,
            getLon: (T) -> Double
        ): SpatialQuadTree<T>? {
            if (items.isEmpty()) return null
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE

            val n = items.size
            val itemLats = DoubleArray(n)
            val itemLons = DoubleArray(n)
            for (i in 0 until n) {
                val item = items[i]
                val lat = getLat(item)
                val lon = getLon(item)
                itemLats[i] = lat
                itemLons[i] = lon
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
            }

            val latPad = ((maxLat - minLat).coerceAtLeast(0.01)) * 0.05
            val lonPad = ((maxLon - minLon).coerceAtLeast(0.01)) * 0.05

            val tree = SpatialQuadTree<T>(
                BoundingBox2D(
                    minLat - latPad,
                    maxLat + latPad,
                    minLon - lonPad,
                    maxLon + lonPad
                )
            )

            for (i in 0 until n) {
                tree.insert(items[i], itemLats[i], itemLons[i])
            }

            return tree
        }
    }
}
