package com.example.ui.map.components

import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.ui.map.MapFilter
import com.example.ui.map.spatial.BoundingBox2D
import com.example.ui.map.spatial.QuadTree
import com.example.ui.map.spatial.SpatialGridSampler
import org.osmdroid.util.BoundingBox

/**
 * Handles LOD (Level of Detail), spatial bounding box queries, and QuadTree sampling
 * for bus stops, metrobus stops, and Valenbisi stations.
 */
object ViewportTransitFilter {

    private var cachedBusStopsRef: List<GeoportalStopEntity>? = null
    private var cachedQuadTree: QuadTree? = null

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

        val latSpan = if (isValidBox) (latNorth - latSouth) else 0.08
        val lonSpan = if (isValidBox) (lonEast - lonWest) else 0.08

        val safeLatSpan = latSpan.coerceAtLeast(0.015)
        val safeLonSpan = lonSpan.coerceAtLeast(0.015)

        // Generous spatial margin (extra generous on the top/north side for transparent status bar, notch, and smooth panning)
        val latNorthMargin = safeLatSpan * 0.75
        val latSouthMargin = safeLatSpan * 0.50
        val lonMargin = safeLonSpan * 0.50

        val minLat = latSouth - latSouthMargin
        val maxLat = latNorth + latNorthMargin
        val minLon = lonWest - lonMargin
        val maxLon = lonEast + lonMargin

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

        val isOnlyBusSelected = mapFilter.showBus &&
                !mapFilter.showMetro &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        val isOnlyMetrobusSelected = mapFilter.showMetrobus &&
                !mapFilter.showMetro &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        // 1. Bus Stops
        val showBus = (mapFilter.isFavorites || mapFilter.showBus) &&
                (mapFilter.isFavorites || isOnlyBusSelected || currentZoom >= 15.2)

        val isFavoritesMode = mapFilter.isFavorites
        // Load bus stops in viewport if bus is active or if metrobus is active (for dual-stop detection)
        val shouldLoadBusStops = (mapFilter.isFavorites || showBus || (mapFilter.showMetrobus && isOnlyMetrobusSelected)) && busStops.isNotEmpty()
        val busStopsInViewport = if (shouldLoadBusStops) {
            if (isFavoritesMode || busStops.size <= 50) {
                busStops
            } else {
                if (cachedBusStopsRef !== busStops || cachedQuadTree == null) {
                    cachedBusStopsRef = busStops
                    cachedQuadTree = QuadTree.buildTree(busStops)
                }
                if (isValidBox && cachedQuadTree != null) {
                    val queryRange = BoundingBox2D(minLat, maxLat, minLon, maxLon)
                    val rangeResult = mutableListOf<GeoportalStopEntity>()
                    cachedQuadTree!!.queryRange(queryRange, rangeResult)
                    rangeResult
                } else {
                    SpatialGridSampler.sampleUniformlyByGrid(busStops, 200, { it.lat }, { it.lon })
                }
            }
        } else emptyList()

        // 2. Metrobus Stops
        val showMetrobus = (mapFilter.isFavorites || mapFilter.showMetrobus) &&
                (mapFilter.isFavorites || isOnlyMetrobusSelected || currentZoom >= 15.2)

        val isMetrobusFavoritesMode = mapFilter.isFavorites
        val metrobusStopsInViewport = if (showMetrobus && metrobusStops.isNotEmpty()) {
            if (isMetrobusFavoritesMode || metrobusStops.size <= 50) {
                metrobusStops
            } else {
                if (isValidBox) {
                    metrobusStops.filter { stop ->
                        stop.lat in minLat..maxLat && stop.lon in minLon..maxLon
                    }
                } else {
                    SpatialGridSampler.sampleUniformlyByGrid(metrobusStops, 200, { it.lat }, { it.lon })
                }
            }
        } else emptyList()

        // 3. Valenbisi
        val showValenbisi = mapFilter.showValenbisi
        val valenbisiInViewport = if (showValenbisi) {
            if (isValidBox) {
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
        lastFilteredResult = null
    }
}
