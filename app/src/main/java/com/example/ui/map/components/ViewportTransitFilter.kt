package com.example.ui.map.components

import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.ui.map.MapFilter
import com.example.ui.map.spatial.BoundingBox2D
import com.example.ui.map.spatial.QuadTree
import com.example.ui.map.spatial.SpatialGridSampler
import com.example.ui.map.spatial.SpatialQuadTree
import org.osmdroid.util.BoundingBox

/**
 * Handles LOD (Level of Detail), spatial bounding box queries, and QuadTree sampling
 * for bus stops, metrobus stops, and Valenbisi stations.
 */
object ViewportTransitFilter {

    private var cachedBusStopsRef: List<GeoportalStopEntity>? = null
    private var cachedQuadTree: QuadTree? = null

    private var cachedMetrobusStopsRef: List<MetrobusStopEntity>? = null
    private var cachedMetrobusQuadTree: SpatialQuadTree<MetrobusStopEntity>? = null

    private var cachedValenbisiRef: List<ValenbisiStation>? = null
    private var cachedValenbisiQuadTree: SpatialQuadTree<ValenbisiStation>? = null

    private var lastMinLat: Double = 0.0
    private var lastMaxLat: Double = 0.0
    private var lastMinLon: Double = 0.0
    private var lastMaxLon: Double = 0.0
    private var lastZoom: Double = 0.0
    private var lastMapFilter: MapFilter? = null
    private var lastBusStopsRef: List<GeoportalStopEntity>? = null
    private var lastMetrobusStopsRef: List<MetrobusStopEntity>? = null
    private var lastValenbisiStationsRef: List<ValenbisiStation>? = null
    private var lastFilteredResult: FilteredTransit? = null

    data class FilteredTransit(
        val showBus: Boolean,
        val busStopsInViewport: List<GeoportalStopEntity>,
        val isFavoritesMode: Boolean,
        val showMetrobus: Boolean,
        val metrobusStopsInViewport: List<MetrobusStopEntity>,
        val isMetrobusFavoritesMode: Boolean,
        val showValenbisi: Boolean,
        val valenbisiStationsInViewport: List<ValenbisiStation>
    )

    private fun getOrBuildBusQuadTree(busStops: List<GeoportalStopEntity>): QuadTree? {
        if (busStops.isEmpty()) return null
        if (cachedBusStopsRef !== busStops || cachedQuadTree == null) {
            cachedBusStopsRef = busStops
            cachedQuadTree = QuadTree.buildTree(busStops)
        }
        return cachedQuadTree
    }

    private fun getOrBuildMetrobusQuadTree(metrobusStops: List<MetrobusStopEntity>): SpatialQuadTree<MetrobusStopEntity>? {
        if (metrobusStops.isEmpty()) return null
        if (cachedMetrobusStopsRef !== metrobusStops || cachedMetrobusQuadTree == null) {
            cachedMetrobusStopsRef = metrobusStops
            cachedMetrobusQuadTree = SpatialQuadTree.buildTree(metrobusStops, { it.lat }, { it.lon })
        }
        return cachedMetrobusQuadTree
    }

    private fun getOrBuildValenbisiQuadTree(stations: List<ValenbisiStation>): SpatialQuadTree<ValenbisiStation>? {
        if (stations.isEmpty()) return null
        if (cachedValenbisiRef !== stations || cachedValenbisiQuadTree == null) {
            cachedValenbisiRef = stations
            cachedValenbisiQuadTree = SpatialQuadTree.buildTree(stations, { it.latitude }, { it.longitude })
        }
        return cachedValenbisiQuadTree
    }

    fun queryBusStopsInBox(
        busStops: List<GeoportalStopEntity>,
        range: BoundingBox2D
    ): List<GeoportalStopEntity> {
        if (busStops.isEmpty()) return emptyList()
        if (busStops.size <= 50) {
            val result = ArrayList<GeoportalStopEntity>()
            for (s in busStops) {
                if (range.contains(s.lat, s.lon)) result.add(s)
            }
            return result
        }
        val tree = getOrBuildBusQuadTree(busStops) ?: return emptyList()
        val result = ArrayList<GeoportalStopEntity>()
        tree.queryRange(range, result)
        return result
    }

    fun queryMetrobusStopsInBox(
        metrobusStops: List<MetrobusStopEntity>,
        range: BoundingBox2D
    ): List<MetrobusStopEntity> {
        if (metrobusStops.isEmpty()) return emptyList()
        if (metrobusStops.size <= 50) {
            val result = ArrayList<MetrobusStopEntity>()
            for (s in metrobusStops) {
                if (range.contains(s.lat, s.lon)) result.add(s)
            }
            return result
        }
        val tree = getOrBuildMetrobusQuadTree(metrobusStops) ?: return emptyList()
        val result = ArrayList<MetrobusStopEntity>()
        tree.queryRange(range, result)
        return result
    }

    fun queryValenbisiInBox(
        stations: List<ValenbisiStation>,
        range: BoundingBox2D
    ): List<ValenbisiStation> {
        if (stations.isEmpty()) return emptyList()
        if (stations.size <= 50) {
            val result = ArrayList<ValenbisiStation>()
            for (s in stations) {
                if (range.contains(s.latitude, s.longitude)) result.add(s)
            }
            return result
        }
        val tree = getOrBuildValenbisiQuadTree(stations) ?: return emptyList()
        val result = ArrayList<ValenbisiStation>()
        tree.queryRange(range, result)
        return result
    }

    internal fun computeQueryBoundingBox(
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double
    ): BoundingBox2D {
        val isValidBox = latNorth > 30.0 && latSouth > 30.0 && latNorth > latSouth && lonEast > lonWest
        val latSpan = if (isValidBox) (latNorth - latSouth).coerceAtLeast(0.0003) else 0.08
        val lonSpan = if (isValidBox) (lonEast - lonWest).coerceAtLeast(0.0003) else 0.08

        // Proportional overscan so pins/pills overlapping screen edges stay mounted during micro-pans,
        // without forcing a 2-3 km query window when zoomed closely onto 1-2 stops.
        val latNorthMargin = (latSpan * 0.25).coerceIn(0.00025, 0.015)
        val latSouthMargin = (latSpan * 0.15).coerceIn(0.00020, 0.010)
        val lonMargin = (lonSpan * 0.20).coerceIn(0.00025, 0.012)

        return BoundingBox2D(
            minLat = latSouth - latSouthMargin,
            maxLat = latNorth + latNorthMargin,
            minLon = lonWest - lonMargin,
            maxLon = lonEast + lonMargin
        )
    }

    fun filterForViewport(
        boundingBox: BoundingBox,
        busStops: List<GeoportalStopEntity>,
        metrobusStops: List<MetrobusStopEntity>,
        valenbisiStations: List<ValenbisiStation>,
        mapFilter: MapFilter,
        currentZoom: Double
    ): FilteredTransit {
        val latSouth = boundingBox.latSouth
        val latNorth = boundingBox.latNorth
        val lonWest = boundingBox.lonWest
        val lonEast = boundingBox.lonEast
        val isValidBox = latNorth > 30.0 && latSouth > 30.0

        val bufferedRange = computeQueryBoundingBox(latSouth, latNorth, lonWest, lonEast)
        val minLat = bufferedRange.minLat
        val maxLat = bufferedRange.maxLat
        val minLon = bufferedRange.minLon
        val maxLon = bufferedRange.maxLon

        // Check if camera movement is negligible (< 0.00003 degrees, ~3 meters) and filters/datasets are identical
        val prevResult = lastFilteredResult
        if (prevResult != null &&
            lastMapFilter == mapFilter &&
            lastBusStopsRef === busStops &&
            lastMetrobusStopsRef === metrobusStops &&
            lastValenbisiStationsRef === valenbisiStations &&
            Math.abs(lastZoom - currentZoom) < 0.01 &&
            Math.abs(lastMinLat - minLat) < 0.00003 &&
            Math.abs(lastMaxLat - maxLat) < 0.00003 &&
            Math.abs(lastMinLon - minLon) < 0.00003 &&
            Math.abs(lastMaxLon - maxLon) < 0.00003
        ) {
            return prevResult
        }

        val isOnlyBusModeSelected = (mapFilter.showBus || mapFilter.showMetrobus) &&
                !mapFilter.showMetro &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        val queryRange = if (isValidBox) BoundingBox2D(minLat, maxLat, minLon, maxLon) else null

        // 1. Bus Stops
        val showBus = (mapFilter.isFavorites || mapFilter.showBus) &&
                (mapFilter.isFavorites || isOnlyBusModeSelected || currentZoom >= 15.2)

        val isFavoritesMode = mapFilter.isFavorites
        // Load bus stops in viewport if bus is active or if metrobus is active (for dual-stop detection)
        val shouldLoadBusStops = (mapFilter.isFavorites || showBus || (mapFilter.showMetrobus && isOnlyBusModeSelected)) && busStops.isNotEmpty()
        val busStopsInViewport = if (shouldLoadBusStops) {
            if (isFavoritesMode || busStops.size <= 50) {
                busStops
            } else {
                val tree = getOrBuildBusQuadTree(busStops)
                if (queryRange != null && tree != null) {
                    val rangeResult = ArrayList<GeoportalStopEntity>(128)
                    tree.queryRange(queryRange, rangeResult)
                    rangeResult
                } else {
                    SpatialGridSampler.sampleUniformlyByGrid(busStops, 200, { it.lat }, { it.lon })
                }
            }
        } else emptyList()

        // 2. Metrobus Stops
        val showMetrobus = (mapFilter.isFavorites || mapFilter.showMetrobus) &&
                (mapFilter.isFavorites || isOnlyBusModeSelected || currentZoom >= 15.2)

        val isMetrobusFavoritesMode = mapFilter.isFavorites
        val metrobusStopsInViewport = if (showMetrobus && metrobusStops.isNotEmpty()) {
            if (isMetrobusFavoritesMode || metrobusStops.size <= 50) {
                metrobusStops
            } else {
                val tree = getOrBuildMetrobusQuadTree(metrobusStops)
                if (queryRange != null && tree != null) {
                    val rangeResult = ArrayList<MetrobusStopEntity>(96)
                    tree.queryRange(queryRange, rangeResult)
                    rangeResult
                } else {
                    SpatialGridSampler.sampleUniformlyByGrid(metrobusStops, 200, { it.lat }, { it.lon })
                }
            }
        } else emptyList()

        // 3. Valenbisi
        val showValenbisi = mapFilter.showValenbisi
        val valenbisiInViewport = if (showValenbisi && valenbisiStations.isNotEmpty()) {
            if (queryRange != null && valenbisiStations.size > 50) {
                val tree = getOrBuildValenbisiQuadTree(valenbisiStations)
                if (tree != null) {
                    val rangeResult = ArrayList<ValenbisiStation>(96)
                    tree.queryRange(queryRange, rangeResult)
                    rangeResult
                } else {
                    valenbisiStations
                }
            } else if (queryRange != null) {
                valenbisiStations.filter { station ->
                    station.latitude in minLat..maxLat && station.longitude in minLon..maxLon
                }
            } else {
                valenbisiStations
            }
        } else emptyList()

        val result = FilteredTransit(
            showBus = showBus,
            busStopsInViewport = busStopsInViewport,
            isFavoritesMode = isFavoritesMode,
            showMetrobus = showMetrobus,
            metrobusStopsInViewport = metrobusStopsInViewport,
            isMetrobusFavoritesMode = isMetrobusFavoritesMode,
            showValenbisi = showValenbisi,
            valenbisiStationsInViewport = valenbisiInViewport
        )

        lastMinLat = minLat
        lastMaxLat = maxLat
        lastMinLon = minLon
        lastMaxLon = maxLon
        lastZoom = currentZoom
        lastMapFilter = mapFilter
        lastBusStopsRef = busStops
        lastMetrobusStopsRef = metrobusStops
        lastValenbisiStationsRef = valenbisiStations
        lastFilteredResult = result

        return result
    }

    fun clearCache() {
        cachedBusStopsRef = null
        cachedQuadTree = null
        cachedMetrobusStopsRef = null
        cachedMetrobusQuadTree = null
        cachedValenbisiRef = null
        cachedValenbisiQuadTree = null
        lastFilteredResult = null
    }
}
