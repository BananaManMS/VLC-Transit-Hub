package com.example.ui.map.spatial

/**
 * Mathematical spatial density grid sampler for transit stops (EMT Bus, Metrobus, etc.)
 * Provides uniform spatial distribution across viewport cells to prevent over-clustering
 * and maintain consistent frame rates during pan and zoom gestures.
 */
object SpatialGridSampler {

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
        if (items.size <= maxCount) return items

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (item in items) {
            val lat = getLat(item)
            val lon = getLon(item)
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0

        // Fixed geographic step size (~300m cells) so cell keys do NOT shift when panning
        val cellStep = 0.003

        val grid = HashMap<Pair<Int, Int>, MutableList<T>>()
        for (item in items) {
            val lat = getLat(item)
            val lon = getLon(item)
            val cellX = kotlin.math.floor(lat / cellStep).toInt()
            val cellY = kotlin.math.floor(lon / cellStep).toInt()
            grid.getOrPut(Pair(cellX, cellY)) { mutableListOf() }.add(item)
        }

        // Sort items inside each cell deterministically by distance to cell center
        grid.forEach { (cellKey, cellList) ->
            val cellCenterLat = (cellKey.first + 0.5) * cellStep
            val cellCenterLon = (cellKey.second + 0.5) * cellStep
            cellList.sortBy { item ->
                val dLat = getLat(item) - cellCenterLat
                val dLon = getLon(item) - cellCenterLon
                dLat * dLat + dLon * dLon
            }
        }

        // Sort grid entries deterministically by distance of cell center to screen center
        val sortedCellLists = grid.entries
            .sortedBy { (cellKey, _) ->
                val cellCenterLat = (cellKey.first + 0.5) * cellStep
                val cellCenterLon = (cellKey.second + 0.5) * cellStep
                val dLat = cellCenterLat - centerLat
                val dLon = cellCenterLon - centerLon
                dLat * dLat + dLon * dLon
            }
            .map { it.value }
            .filter { it.isNotEmpty() }
            .toMutableList()

        val result = ArrayList<T>(maxCount)

        var index = 0
        while (result.size < maxCount && sortedCellLists.isNotEmpty()) {
            val iterator = sortedCellLists.iterator()
            while (iterator.hasNext() && result.size < maxCount) {
                val cellStops = iterator.next()
                if (index < cellStops.size) {
                    result.add(cellStops[index])
                } else {
                    iterator.remove()
                }
            }
            index++
        }

        return result.sortedBy { getLat(it) * 100000.0 + getLon(it) }
    }
}
