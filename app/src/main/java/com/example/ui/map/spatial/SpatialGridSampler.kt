package com.example.ui.map.spatial

/**
 * Mathematical spatial density grid sampler for transit stops (EMT Bus, Metrobus, etc.)
 * Provides uniform spatial distribution across viewport cells to prevent over-clustering
 * and maintain consistent frame rates during pan and zoom gestures.
 */
object SpatialGridSampler {

    @PublishedApi
    internal class CellBucket(
        val cellX: Int,
        val cellY: Int,
        val distToScreenSq: Double
    ) {
        var indices = IntArray(4)
        var size = 0

        fun add(index: Int) {
            if (size == indices.size) {
                indices = indices.copyOf(indices.size * 2)
            }
            indices[size++] = index
        }
    }

    /**
     * Uniformly samples items across a dynamic 2D geographical grid.
     *
     * @param items List of items to sample
     * @param maxCount Maximum number of items to return
     * @param getLat Selector for latitude
     * @param getLon Selector for longitude
     */
    inline fun <T> sampleUniformlyByGrid(
        items: List<T>,
        maxCount: Int,
        crossinline getLat: (T) -> Double,
        crossinline getLon: (T) -> Double
    ): List<T> {
        val n = items.size
        if (n <= maxCount) return items

        val lats = DoubleArray(n)
        val lons = DoubleArray(n)

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (i in 0 until n) {
            val item = items[i]
            val lat = getLat(item)
            val lon = getLon(item)
            lats[i] = lat
            lons[i] = lon
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0

        // Fixed geographic step size (~300m cells) so cell keys do NOT shift when panning
        val cellStep = 0.003

        val grid = HashMap<Long, CellBucket>(minOf(n, 512))
        for (i in 0 until n) {
            val cellX = kotlin.math.floor(lats[i] / cellStep).toInt()
            val cellY = kotlin.math.floor(lons[i] / cellStep).toInt()
            val packedKey = (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)
            var bucket = grid[packedKey]
            if (bucket == null) {
                val cellCenterLat = (cellX + 0.5) * cellStep
                val cellCenterLon = (cellY + 0.5) * cellStep
                val dLat = cellCenterLat - centerLat
                val dLon = cellCenterLon - centerLon
                bucket = CellBucket(cellX, cellY, dLat * dLat + dLon * dLon)
                grid[packedKey] = bucket
            }
            bucket.add(i)
        }

        val bucketCount = grid.size
        val buckets = arrayOfNulls<CellBucket>(bucketCount)
        var bIdx = 0
        for (bucket in grid.values) {
            // Sort items inside each cell deterministically by distance to cell center (insertion sort is fast for small cells)
            val count = bucket.size
            if (count > 1) {
                val cellCenterLat = (bucket.cellX + 0.5) * cellStep
                val cellCenterLon = (bucket.cellY + 0.5) * cellStep
                val arr = bucket.indices
                for (i in 1 until count) {
                    val keyIdx = arr[i]
                    val dLatK = lats[keyIdx] - cellCenterLat
                    val dLonK = lons[keyIdx] - cellCenterLon
                    val keyDist = dLatK * dLatK + dLonK * dLonK
                    var j = i - 1
                    while (j >= 0) {
                        val cmpIdx = arr[j]
                        val dLatC = lats[cmpIdx] - cellCenterLat
                        val dLonC = lons[cmpIdx] - cellCenterLon
                        if (dLatC * dLatC + dLonC * dLonC > keyDist) {
                            arr[j + 1] = cmpIdx
                            j--
                        } else {
                            break
                        }
                    }
                    arr[j + 1] = keyIdx
                }
            }
            buckets[bIdx++] = bucket
        }

        // Sort grid buckets deterministically by distance of cell center to screen center
        @Suppress("UNCHECKED_CAST")
        val sortedBuckets = buckets as Array<CellBucket>
        java.util.Arrays.sort(sortedBuckets) { a, b ->
            java.lang.Double.compare(a.distToScreenSq, b.distToScreenSq)
        }

        val selectedIndices = IntArray(maxCount)
        var selectedCount = 0
        var activeCount = bucketCount
        var index = 0

        while (selectedCount < maxCount && activeCount > 0) {
            var writeIdx = 0
            for (i in 0 until activeCount) {
                if (selectedCount >= maxCount) break
                val bucket = sortedBuckets[i]
                if (index < bucket.size) {
                    selectedIndices[selectedCount++] = bucket.indices[index]
                    if (index + 1 < bucket.size) {
                        sortedBuckets[writeIdx++] = bucket
                    }
                }
            }
            activeCount = writeIdx
            index++
        }

        // Sort final indices identically by lats[idx] * 100000.0 + lons[idx]
        val boxedIndices = Array<Int>(selectedCount) { selectedIndices[it] }
        java.util.Arrays.sort(boxedIndices) { a, b ->
            java.lang.Double.compare(
                lats[a] * 100000.0 + lons[a],
                lats[b] * 100000.0 + lons[b]
            )
        }

        val result = ArrayList<T>(selectedCount)
        for (idx in boxedIndices) {
            result.add(items[idx])
        }
        return result
    }
}
