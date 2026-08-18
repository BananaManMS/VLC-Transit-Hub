package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import android.util.LruCache
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtRoute
import com.example.ui.map.MapFilter
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.spatial.BoundingBox2D
import com.example.ui.map.spatial.QuadTree
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.RecentSearch
import org.osmdroid.views.overlay.Polyline
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.TransitMode
import org.osmdroid.views.overlay.Marker
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

object MapMarkersManager {

    data class MarkerIconResult(
        val drawable: Drawable,
        val anchorU: Float,
        val anchorV: Float
    )

    // Recycled instances to prevent GC allocations during pan/zoom
    internal var lastMapView: MapView? = null
    private var mapEventsOverlay: MapEventsOverlay? = null
    internal var userMarker: Marker? = null
    internal var destinationMarker: Marker? = null
    private val recycledMetroMarkers = mutableListOf<Marker>()
    private val recycledCercaniasMarkers = mutableListOf<Marker>()
    private val recycledBusMarkers = mutableListOf<Marker>()
    private val recycledMetrobusMarkers = mutableListOf<Marker>()
    private val recycledMetrobusClusterMarkers = mutableListOf<Marker>()
    private val recycledClusterMarkers = mutableListOf<Marker>()
    private val recycledValenbisiMarkers = mutableListOf<Marker>()
    private val recycledValenbisiClusterMarkers = mutableListOf<Marker>()
    private val recycledItineraryMarkers = mutableListOf<Marker>()
    private val recycledCustomFavoriteMarkers = mutableListOf<Marker>()

    // Spatial indexing via QuadTree
    private var cachedBusStopsRef: List<GeoportalStopEntity>? = null
    private var cachedQuadTree: QuadTree? = null

    private var currentOnMapClick: (() -> Unit)? = null
    private var currentOnMapLongClick: ((GeoPoint) -> Unit)? = null
    private var currentOnSelectItem: ((SelectedMapItem) -> Unit)? = null
    private var currentOnShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null
    private var currentMetroStations = emptyList<MetroStation>()
    private var currentCercaniasStations = emptyList<com.example.data.database.CercaniasStationEntity>()
    private var currentBusStopsInViewport = emptyList<GeoportalStopEntity>()
    private var currentMetrobusStopsInViewport = emptyList<com.example.data.database.MetrobusStopEntity>()
    private var currentValenbisiStations = emptyList<com.example.ui.map.components.ValenbisiStation>()
    private var currentMetroPositions = emptyMap<String, GeoPoint>()
    private var currentCercaniasPositions = emptyMap<String, GeoPoint>()
    private var showMetro: Boolean = false
    private var showCercanias: Boolean = false
    private var showMetrobus: Boolean = false
    private var showBus: Boolean = false
    private var showValenbisi: Boolean = false
    private var currentZoomLevel: Double = 16.0
    private var currentCustomFavorites = emptyList<RecentSearch>()
    private var isOnlyMetroSelected: Boolean = false
    private var isOnlyCercaniasSelected: Boolean = false
    private var currentDestinationLocation: GeoPoint? = null
    private var currentDestinationTitle: String? = null

    private var lastGestureTime = 0L
    private var lastZoomedItineraryId: String? = null

    fun notifyGesture() {
        lastGestureTime = System.currentTimeMillis()
    }

    fun isGestureActive(): Boolean {
        return System.currentTimeMillis() - lastGestureTime < 450L
    }

    private fun handleTapAtGeoPoint(context: Context, mapView: MapView, p: GeoPoint): Boolean {
        if (isGestureActive()) {
            return true
        }

        val density = context.resources.displayMetrics.density
        val thresholdPx = 44f * density // 44dp touch radius

        val tapPixel = android.graphics.Point()
        mapView.projection.toPixels(p, tapPixel)

        val candidates = mutableListOf<SelectedMapItem>()
        val markerPixel = android.graphics.Point()

        // Check Metro stations
        val isMetroVisibleOnMap = showMetro && (currentZoomLevel >= 13.5 || isOnlyMetroSelected)
        if (isMetroVisibleOnMap) {
            currentMetroStations.forEach { station ->
                val lat = station.latitude
                val lon = station.longitude
                if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                    val pos = currentMetroPositions[station.name] ?: GeoPoint(lat, lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = tapPixel.x - markerPixel.x
                    val dy = tapPixel.y - markerPixel.y
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                    if (distance <= thresholdPx) {
                        candidates.add(SelectedMapItem.Metro(station))
                    }
                }
            }
        }

        // Check Cercanias stations
        val isCercaniasVisibleOnMap = showCercanias && (currentZoomLevel >= 11.5 || isOnlyCercaniasSelected)
        if (isCercaniasVisibleOnMap) {
            currentCercaniasStations.forEach { station ->
                if (station.lat != 0.0 && station.lon != 0.0) {
                    val pos = currentCercaniasPositions[station.stop_id] ?: GeoPoint(station.lat, station.lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = tapPixel.x - markerPixel.x
                    val dy = tapPixel.y - markerPixel.y
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                    if (distance <= thresholdPx) {
                        candidates.add(SelectedMapItem.Cercanias(station))
                    }
                }
            }
        }

        // Check Valenbisi stations
        if (showValenbisi) {
            currentValenbisiStations.forEach { station ->
                if (station.latitude != 0.0 && station.longitude != 0.0) {
                    val pos = GeoPoint(station.latitude, station.longitude)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = tapPixel.x - markerPixel.x
                    val dy = tapPixel.y - markerPixel.y
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                    if (distance <= thresholdPx) {
                        candidates.add(SelectedMapItem.Valenbisi(station))
                    }
                }
            }
        }

        // Check Bus stops
        val isFavoritesOrSmallList = currentBusStopsInViewport.size <= 50 || currentZoomLevel >= 13.5
        if (showBus && (isFavoritesOrSmallList || currentZoomLevel >= 13.5)) {
            val stopsToRender = if (isFavoritesOrSmallList) {
                currentBusStopsInViewport
            } else if (currentZoomLevel < 15.2) {
                currentBusStopsInViewport.take(180)
            } else {
                currentBusStopsInViewport.take(250)
            }

            stopsToRender.forEach { stop ->
                if (stop.lat != 0.0 && stop.lon != 0.0) {
                    val pos = GeoPoint(stop.lat, stop.lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = tapPixel.x - markerPixel.x
                    val dy = tapPixel.y - markerPixel.y
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                    if (distance <= thresholdPx) {
                        val linesList = (stop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { EmtRoute(id_linea = it, SN = it) }

                        val emtModel = EmtBusStop(
                            t = stop.denominacion,
                            n = stop.denominacion,
                            me = stop.denominacion,
                            utes = linesList,
                            opId = stop.id_parada,
                            ica = stop.id_parada
                        )
                        candidates.add(SelectedMapItem.BusStop(stop, emtModel))
                    }
                }
            }
        }

        // Check Metrobus stops
        if (showMetrobus && (currentMetrobusStopsInViewport.size <= 50 || currentZoomLevel >= 13.5)) {
            currentMetrobusStopsInViewport.forEach { stop ->
                if (stop.lat != 0.0 && stop.lon != 0.0) {
                    val pos = GeoPoint(stop.lat, stop.lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = tapPixel.x - markerPixel.x
                    val dy = tapPixel.y - markerPixel.y
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                    if (distance <= thresholdPx) {
                        val linesList = (stop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val metrobusModel = com.example.ui.bus.MetrobusStop(
                            idParada = stop.id_parada,
                            denominacion = stop.denominacion,
                            lat = stop.lat,
                            lon = stop.lon,
                            lineas = linesList
                        )
                        candidates.add(SelectedMapItem.MetrobusStopItem(stop, metrobusModel))
                    }
                }
            }
        }

        // Check Destination Pin
        val destLoc = currentDestinationLocation
        if (destLoc != null) {
            mapView.projection.toPixels(destLoc, markerPixel)
            val dx = tapPixel.x - markerPixel.x
            val dy = tapPixel.y - markerPixel.y
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
            if (distance <= thresholdPx) {
                val destItem = SelectedMapItem.Address(
                    com.example.data.model.NominatimResult(
                        displayName = currentDestinationTitle ?: "Destino",
                        latitude = destLoc.latitude,
                        longitude = destLoc.longitude,
                        type = "address",
                        category = "place",
                        isLocalStop = false,
                        stopId = null,
                        stopType = null
                    )
                )
                candidates.add(destItem)
            }
        }

        // Check Custom Favorites
        currentCustomFavorites.forEach { fav ->
            val pos = GeoPoint(fav.latitude, fav.longitude)
            mapView.projection.toPixels(pos, markerPixel)
            val dx = tapPixel.x - markerPixel.x
            val dy = tapPixel.y - markerPixel.y
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
            if (distance <= thresholdPx) {
                val favItem = SelectedMapItem.Address(
                    com.example.data.model.NominatimResult(
                        displayName = fav.title + ", " + fav.subtitle,
                        latitude = fav.latitude,
                        longitude = fav.longitude,
                        type = "address",
                        category = "place",
                        isLocalStop = false,
                        stopId = null,
                        stopType = null
                    )
                )
                candidates.add(favItem)
            }
        }

        val uniqueCandidates = candidates.distinctBy { item ->
            when (item) {
                is SelectedMapItem.Metro -> "METRO_${item.station.id}_${item.station.name}"
                is SelectedMapItem.Cercanias -> "CERCANIAS_${item.station.stop_id}"
                is SelectedMapItem.BusStop -> "BUS_${item.stop.id_parada}"
                is SelectedMapItem.MetrobusStopItem -> "METROBUS_${item.stop.id_parada}"
                is SelectedMapItem.Valenbisi -> "VALENBISI_${item.station.gid}"
                is SelectedMapItem.Address -> "ADDR_${item.result.latitude}_${item.result.longitude}"
            }
        }

        when {
            uniqueCandidates.isEmpty() -> {
                currentOnMapClick?.invoke()
            }
            uniqueCandidates.size == 1 -> {
                currentOnSelectItem?.invoke(uniqueCandidates.first())
            }
            else -> {
                val onShowMenu = currentOnShowDisambiguationMenu
                if (onShowMenu != null) {
                    onShowMenu.invoke(uniqueCandidates)
                } else {
                    currentOnSelectItem?.invoke(uniqueCandidates.first())
                }
            }
        }
        return true
    }

    fun updateMarkers(
        context: Context,
        mapView: MapView,
        busStops: List<GeoportalStopEntity>,
        metrobusStops: List<com.example.data.database.MetrobusStopEntity> = emptyList(),
        metroStations: List<MetroStation>,
        cercaniasStations: List<com.example.data.database.CercaniasStationEntity>,
        valenbisiStations: List<com.example.ui.map.components.ValenbisiStation> = emptyList(),
        customFavorites: List<RecentSearch> = emptyList(),
        homeLocation: RecentSearch? = null,
        workLocation: RecentSearch? = null,
        mapFilter: MapFilter,
        userLocation: GeoPoint?,
        destinationLocation: GeoPoint? = null,
        destinationTitle: String? = null,
        isDarkMode: Boolean,
        busStopAliases: Map<String, String> = emptyMap(),
        appLanguage: AppLanguage = AppLanguage.CA,
        selectedItinerary: PlannedItinerary? = null,
        onSelectItem: (SelectedMapItem) -> Unit,
        onMapClick: () -> Unit,
        onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null,
        onMapLongClick: ((GeoPoint) -> Unit)? = null
    ) {
        currentOnMapClick = onMapClick
        currentOnMapLongClick = onMapLongClick
        currentOnSelectItem = onSelectItem
        currentOnShowDisambiguationMenu = onShowDisambiguationMenu
        currentDestinationLocation = destinationLocation
        currentDestinationTitle = destinationTitle
        currentCustomFavorites = customFavorites

        // Reset recycling cache if MapView instance changes
        if (lastMapView != mapView) {
            stopLiveLocationUpdates()
            lastMapView?.let { MetroStationFootprintsOverlayManager.clearFromMap(it) }
            lastMapView = mapView
            mapEventsOverlay = null
            userMarker = null
            recycledMetroMarkers.clear()
            recycledCercaniasMarkers.clear()
            recycledBusMarkers.clear()
            recycledMetrobusMarkers.clear()
            recycledMetrobusClusterMarkers.clear()
            recycledClusterMarkers.clear()
            recycledValenbisiMarkers.clear()
            recycledValenbisiClusterMarkers.clear()
            recycledCustomFavoriteMarkers.clear()
            mapView.overlays.clear()
        }

        // 1. Map Events Receiver (Click on map empty space)
        if (mapEventsOverlay == null) {
            val mapEventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    if (p == null) {
                        currentOnMapClick?.invoke()
                        return true
                    }
                    return handleTapAtGeoPoint(context, mapView, p)
                }

                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        currentOnMapLongClick?.invoke(p)
                        return true
                    }
                    return false
                }
            }
            val overlay = MapEventsOverlay(mapEventsReceiver)
            mapEventsOverlay = overlay
            mapView.overlays.add(0, overlay)
        }

        // Get or create cached icons from LruCache
        val busDrawable = getMarkerIcon(context, "BUS", Color.parseColor("#E53935"))
        val busDotDrawable = getCompactDotIcon(context, Color.parseColor("#E53935"))
        val metroDrawable = getMarkerIcon(context, "METRO", Color.parseColor("#1E88E5"))

        val currentZoom = mapView.zoomLevelDouble

        lastMapView = mapView

        // 2. User Location Marker
        if (userLocation != null) {
            val marker = userMarker ?: Marker(mapView).also {
                userMarker = it
                it.title = "Tu ubicación"
                it.infoWindow = null
                it.setOnMarkerClickListener { _, _ -> true }
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                mapView.overlays.add(it)
            }
            marker.infoWindow = null
            marker.setOnMarkerClickListener { _, _ -> true }
            marker.closeInfoWindow()
            marker.position = userLocation
            marker.setVisible(true)
            marker.icon = createUserLiveIcon(context)
            startLiveLocationUpdates(context)
        } else {
            userMarker?.setVisible(false)
            stopLiveLocationUpdates()
        }

        // 2b. Destination Location Marker
        if (destinationLocation != null) {
            val destIconResult = getDestinationMarkerIcon(context, isDarkMode)
            val marker = destinationMarker ?: Marker(mapView).also {
                destinationMarker = it
                it.infoWindow = null
                mapView.overlays.add(it)
            }
            marker.infoWindow = null
            marker.closeInfoWindow()
            marker.position = destinationLocation
            marker.title = destinationTitle ?: "Destino"
            marker.snippet = "Destino seleccionado"
            marker.icon = destIconResult.drawable
            marker.setAnchor(destIconResult.anchorU, destIconResult.anchorV)
            marker.setVisible(true)
            marker.isEnabled = true
            marker.setOnMarkerClickListener { m, _ ->
                val destItem = SelectedMapItem.Address(
                    com.example.data.model.NominatimResult(
                        displayName = destinationTitle ?: "Destino",
                        latitude = m.position.latitude,
                        longitude = m.position.longitude,
                        type = "address",
                        category = "place",
                        isLocalStop = false,
                        stopId = null,
                        stopType = null
                    )
                )
                currentOnSelectItem?.invoke(destItem)
                true
            }
        } else {
            destinationMarker?.setVisible(false)
            destinationMarker?.setOnMarkerClickListener(null)
        }
        // 3. Metro Stations & Line Shapes (Draw if filter is FAVORITES, METRO_ONLY, or SHOW_ALL)
        val showMetro = mapFilter.isFavorites || mapFilter.showMetro
        val showCercanias = mapFilter.isFavorites || mapFilter.showCercanias

        // Dynamically toggle high-res / low-res (LOD) based on the zoom level threshold (12.0)
        MetroMapOverlayLoader.setUseHighRes(currentZoom >= 12.0)

        // Dynamically toggle precomputed ZoomCategory (Far/Medium/Close) based on zoom range
        val category = MetroMapOverlayLoader.getZoomCategoryForLevel(currentZoom)
        if (MetroMapOverlayLoader.getZoomCategory() != category) {
            MetroMapOverlayLoader.setZoomCategory(category)
        }

        val validMetroStations = if (showMetro) {
            metroStations.filter { station ->
                val lat = station.latitude
                val lon = station.longitude
                lat != null && lon != null && lat != 0.0 && lon != 0.0
            }
        } else emptyList()

        val validCercaniasStations = if (showCercanias) {
            cercaniasStations.filter { station ->
                station.lat != 0.0 && station.lon != 0.0
            }
        } else emptyList()

        // 2a. Pre-calculate adjusted coordinates for Metro and Cercanias stations to prevent overlaps (Option 2)
        val metroPositions = validMetroStations.associate { it.name to GeoPoint(it.latitude!!, it.longitude!!) }.toMutableMap()
        val cercaniasPositions = validCercaniasStations.associate { it.stop_id to GeoPoint(it.lat, it.lon) }.toMutableMap()

        // Displace stations that are extremely close to each other (less than ~40 meters)
        for (mStation in validMetroStations) {
            val mName = mStation.name
            val mPt = metroPositions[mName] ?: continue

            for (cStation in validCercaniasStations) {
                val cId = cStation.stop_id
                val cPt = cercaniasPositions[cId] ?: continue

                val dy = mPt.latitude - cPt.latitude
                val dx = mPt.longitude - cPt.longitude
                val dist = Math.sqrt(dx * dx + dy * dy)

                if (dist < 0.00035) {
                    // Shift Metro slightly North-West, Cercanias slightly South-East
                    metroPositions[mName] = GeoPoint(mPt.latitude + 0.00015, mPt.longitude - 0.00015)
                    cercaniasPositions[cId] = GeoPoint(cPt.latitude - 0.00015, cPt.longitude + 0.00015)
                }
            }
        }

        this.currentMetroStations = validMetroStations
        this.currentCercaniasStations = validCercaniasStations
        this.currentMetroPositions = metroPositions
        this.currentCercaniasPositions = cercaniasPositions
        this.showMetro = showMetro
        this.showCercanias = showCercanias
        this.currentZoomLevel = currentZoom

        val isOnlyMetroSelected = mapFilter.showMetro &&
                !mapFilter.showBus &&
                !mapFilter.showCercanias &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        val isOnlyCercaniasSelected = mapFilter.showCercanias &&
                !mapFilter.showBus &&
                !mapFilter.showMetro &&
                !mapFilter.showValenbisi &&
                !mapFilter.isFavorites

        this.isOnlyMetroSelected = isOnlyMetroSelected
        this.isOnlyCercaniasSelected = isOnlyCercaniasSelected

        // Option 3: Zoom level of detail (hide pills/text at lower zoom levels, show only pins)
        val showPill = currentZoom >= 14.5

        val isMetroVisible = currentZoom >= 13.5 || isOnlyMetroSelected
        validMetroStations.forEachIndexed { index, station ->
            val marker = if (index < recycledMetroMarkers.size) {
                recycledMetroMarkers[index]
            } else {
                Marker(mapView).also {
                    recycledMetroMarkers.add(it)
                    mapView.overlays.add(it)
                }
            }

            marker.infoWindow = null
            marker.closeInfoWindow()
            val adjustedPos = metroPositions[station.name] ?: GeoPoint(station.latitude!!, station.longitude!!)
            val metroResult = if (currentZoom < 13.5) {
                getMetroTinyDotIcon(context, isDarkMode)
            } else if (currentZoom < 15.0) {
                getMetroCompactDotIcon(context, isDarkMode)
            } else {
                getMetroMarkerIcon(context, station.name, isDarkMode, showPill)
            }
            marker.position = adjustedPos
            marker.icon = metroResult.drawable
            marker.title = station.name
            marker.snippet = "Metrovalencia • ${station.lines.joinToString(", ")}"
            marker.setAnchor(metroResult.anchorU, metroResult.anchorV)
            
            if (isMetroVisible) {
                marker.isEnabled = true
                marker.setVisible(true)
            } else {
                marker.isEnabled = false
                marker.setVisible(false)
            }

            marker.setOnMarkerClickListener { m, _ ->
                handleTapAtGeoPoint(context, mapView, m.position)
            }
        }

        // Hide unused recycled metro markers
        for (i in validMetroStations.size until recycledMetroMarkers.size) {
            recycledMetroMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        val isCercaniasVisible = currentZoom >= 11.5 || isOnlyCercaniasSelected
        validCercaniasStations.forEachIndexed { index, station ->
            val marker = if (index < recycledCercaniasMarkers.size) {
                recycledCercaniasMarkers[index]
            } else {
                Marker(mapView).also {
                    recycledCercaniasMarkers.add(it)
                    mapView.overlays.add(it)
                }
            }

            marker.infoWindow = null
            marker.closeInfoWindow()
            val adjustedPos = cercaniasPositions[station.stop_id] ?: GeoPoint(station.lat, station.lon)
            val cercaniasResult = if (currentZoom < 11.5) {
                getCercaniasTinyDotIcon(context, isDarkMode)
            } else if (currentZoom < 13.5) {
                getCercaniasLogoSmallIcon(context, isDarkMode)
            } else if (currentZoom < 15.0) {
                getCercaniasMarkerIcon(context, station.displayName, isDarkMode, showPill = false)
            } else {
                getCercaniasMarkerIcon(context, station.displayName, isDarkMode, showPill = showPill)
            }
            marker.position = adjustedPos
            marker.icon = cercaniasResult.drawable
            marker.title = station.displayName
            marker.snippet = if (appLanguage == AppLanguage.CA) "Rodalia Renfe • ${station.lines}" else "Cercanías Renfe • ${station.lines}"
            marker.setAnchor(cercaniasResult.anchorU, cercaniasResult.anchorV)
            
            if (isCercaniasVisible) {
                marker.isEnabled = true
                marker.setVisible(true)
            } else {
                marker.isEnabled = false
                marker.setVisible(false)
            }

            marker.setOnMarkerClickListener { m, _ ->
                handleTapAtGeoPoint(context, mapView, m.position)
            }
        }

        // Hide unused recycled cercanias markers
        for (i in validCercaniasStations.size until recycledCercaniasMarkers.size) {
            recycledCercaniasMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // 4. EMT Bus Stops - Level of Detail (LOD) & Clustering
        // Zoom LOD: If all modes are active, hide bus stops entirely when zoomed out below detailed pin zoom (zoom < 15.2)
        val isAllModesActive = mapFilter.showBus && mapFilter.showMetro && mapFilter.showCercanias
        val showBus = (mapFilter.isFavorites || mapFilter.showBus) &&
                !(isAllModesActive && currentZoom < 15.2)
        val boundingBox = mapView.boundingBox
        val latSouth = boundingBox.latSouth
        val latNorth = boundingBox.latNorth
        val lonWest = boundingBox.lonWest
        val lonEast = boundingBox.lonEast
        val isValidBox = latNorth > 30.0 && latSouth > 30.0

        // Expanded viewport padding (+20% margin) for seamless panning
        val latMargin = if (isValidBox) (latNorth - latSouth) * 0.20 else 0.05
        val lonMargin = if (isValidBox) (lonEast - lonWest) * 0.20 else 0.05
        val minLat = latSouth - latMargin
        val maxLat = latNorth + latMargin
        val minLon = lonWest - lonMargin
        val maxLon = lonEast + lonMargin

        val isFavoritesMode = mapFilter.isFavorites || busStops.size <= 50
        val busStopsInViewport = if (showBus && busStops.isNotEmpty()) {
            if (isFavoritesMode) {
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
                    sampleUniformlyByGrid(busStops, 80)
                }
            }
        } else emptyList()

        this.currentBusStopsInViewport = busStopsInViewport
        this.showBus = showBus
        this.currentZoomLevel = currentZoom

        var activeClusterCount = 0
        var activeBusCount = 0

        if (showBus && busStopsInViewport.isNotEmpty()) {
            if (isFavoritesMode) {
                // In FAVORITES mode or small lists, render ALL favorite bus stops directly as full bus pins
                busStopsInViewport.forEach { stop ->
                    val marker = if (activeBusCount < recycledBusMarkers.size) {
                        recycledBusMarkers[activeBusCount]
                    } else {
                        Marker(mapView).also {
                            recycledBusMarkers.add(it)
                            mapView.overlays.add(it)
                        }
                    }
                    activeBusCount++

                    marker.infoWindow = null
                    marker.closeInfoWindow()
                    val alias = busStopAliases[stop.id_parada]
                    marker.position = GeoPoint(stop.lat, stop.lon)
                    marker.icon = busDrawable
                    marker.title = if (!alias.isNullOrBlank()) "$alias (${stop.denominacion})" else stop.denominacion
                    marker.snippet = "Parada ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.isEnabled = true
                    marker.setVisible(true)
                    marker.setOnMarkerClickListener { m, _ ->
                        handleTapAtGeoPoint(context, mapView, m.position)
                    }
                }
            } else {
                when {
                    // LOD 1: ZOOM < 13.5 -> Radius Grid Clustering
                    currentZoom < 13.5 -> {
                        val gridSize = if (currentZoom < 11.0) 0.06 else 0.025
                        val clusters = busStopsInViewport.groupBy { stop ->
                            val gridX = (stop.lat / gridSize).toInt()
                            val gridY = (stop.lon / gridSize).toInt()
                            Pair(gridX, gridY)
                        }

                        clusters.values.forEach { group ->
                            val avgLat = group.map { it.lat }.average()
                            val avgLon = group.map { it.lon }.average()
                            val count = group.size

                            val clusterMarker = if (activeClusterCount < recycledClusterMarkers.size) {
                                recycledClusterMarkers[activeClusterCount]
                            } else {
                                Marker(mapView).also {
                                    recycledClusterMarkers.add(it)
                                    mapView.overlays.add(it)
                                }
                            }
                            activeClusterCount++

                            clusterMarker.infoWindow = null
                            clusterMarker.closeInfoWindow()
                            clusterMarker.position = GeoPoint(avgLat, avgLon)
                            clusterMarker.icon = getClusterIcon(context, count, Color.parseColor("#E53935"))
                            clusterMarker.title = "$count Paradas de EMT Bus"
                            clusterMarker.snippet = "Toca para ampliar área"
                            clusterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            clusterMarker.isEnabled = true
                            clusterMarker.setVisible(true)
                            clusterMarker.setOnMarkerClickListener { _, _ ->
                                if (showBus && clusterMarker.isEnabled) {
                                    mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                                    mapView.controller.zoomTo(currentZoom + 2.2)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    }

                    // LOD 2: 13.5 <= ZOOM < 15.2 -> Compact Dot Icons
                    currentZoom in 13.5..15.2 -> {
                        val stopsToRender = sampleUniformlyByGrid(busStopsInViewport, 200)
                        stopsToRender.forEach { stop ->
                            val marker = if (activeBusCount < recycledBusMarkers.size) {
                                recycledBusMarkers[activeBusCount]
                            } else {
                                Marker(mapView).also {
                                    recycledBusMarkers.add(it)
                                    mapView.overlays.add(it)
                                }
                            }
                            activeBusCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            val alias = busStopAliases[stop.id_parada]
                            marker.position = GeoPoint(stop.lat, stop.lon)
                            marker.icon = busDotDrawable
                            marker.title = if (!alias.isNullOrBlank()) "$alias (${stop.denominacion})" else stop.denominacion
                            marker.snippet = "Parada ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            marker.isEnabled = true
                            marker.setVisible(true)
                            marker.setOnMarkerClickListener { m, _ ->
                                handleTapAtGeoPoint(context, mapView, m.position)
                            }
                        }
                    }

                    // LOD 3: ZOOM >= 15.2 -> Full Detailed Pins
                    else -> {
                        val stopsToRender = sampleUniformlyByGrid(busStopsInViewport, 300)
                        stopsToRender.forEach { stop ->
                            val marker = if (activeBusCount < recycledBusMarkers.size) {
                                recycledBusMarkers[activeBusCount]
                            } else {
                                Marker(mapView).also {
                                    recycledBusMarkers.add(it)
                                    mapView.overlays.add(it)
                                }
                            }
                            activeBusCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            val alias = busStopAliases[stop.id_parada]
                            marker.position = GeoPoint(stop.lat, stop.lon)
                            marker.icon = busDrawable
                            marker.title = if (!alias.isNullOrBlank()) "$alias (${stop.denominacion})" else stop.denominacion
                            marker.snippet = "Parada ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            marker.isEnabled = true
                            marker.setVisible(true)
                            marker.setOnMarkerClickListener { m, _ ->
                                handleTapAtGeoPoint(context, mapView, m.position)
                            }
                        }
                    }
                }
            }
        }

        // Hide unused recycled bus markers
        for (i in activeBusCount until recycledBusMarkers.size) {
            recycledBusMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Hide unused recycled cluster markers
        for (i in activeClusterCount until recycledClusterMarkers.size) {
            recycledClusterMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // 5. Metrobús Stops
        val showMetrobus = (mapFilter.isFavorites || mapFilter.showMetrobus) &&
                !(isAllModesActive && currentZoom < 15.2)

        val isMetrobusFavoritesMode = mapFilter.isFavorites || metrobusStops.size <= 50
        val metrobusStopsInViewport = if (showMetrobus && metrobusStops.isNotEmpty()) {
            if (isMetrobusFavoritesMode) {
                metrobusStops
            } else {
                if (isValidBox) {
                    metrobusStops.filter { stop ->
                        stop.lat in minLat..maxLat && stop.lon in minLon..maxLon
                    }
                } else {
                    sampleUniformlyByMetrobusGrid(metrobusStops, 80)
                }
            }
        } else emptyList()

        this.currentMetrobusStopsInViewport = metrobusStopsInViewport
        this.showMetrobus = showMetrobus

        var activeMetrobusClusterCount = 0
        var activeMetrobusCount = 0
        val metrobusDrawable = getMarkerIcon(context, "MB", Color.parseColor("#FFB300"))
        val metrobusDotDrawable = getCompactDotIcon(context, Color.parseColor("#FFB300"))

        if (showMetrobus && metrobusStopsInViewport.isNotEmpty()) {
            if (isMetrobusFavoritesMode) {
                metrobusStopsInViewport.forEach { stop ->
                    val marker = if (activeMetrobusCount < recycledMetrobusMarkers.size) {
                        recycledMetrobusMarkers[activeMetrobusCount]
                    } else {
                        Marker(mapView).also {
                            recycledMetrobusMarkers.add(it)
                            mapView.overlays.add(it)
                        }
                    }
                    activeMetrobusCount++

                    marker.infoWindow = null
                    marker.closeInfoWindow()
                    marker.position = GeoPoint(stop.lat, stop.lon)
                    marker.icon = metrobusDrawable
                    marker.title = stop.denominacion
                    marker.snippet = "Metrobús ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.isEnabled = true
                    marker.setVisible(true)
                    marker.setOnMarkerClickListener { m, _ ->
                        handleTapAtGeoPoint(context, mapView, m.position)
                    }
                }
            } else {
                when {
                    // LOD 1: ZOOM < 13.5 -> Radius Grid Clustering
                    currentZoom < 13.5 -> {
                        val gridSize = if (currentZoom < 11.0) 0.06 else 0.025
                        val clusters = metrobusStopsInViewport.groupBy { stop ->
                            val gridX = (stop.lat / gridSize).toInt()
                            val gridY = (stop.lon / gridSize).toInt()
                            Pair(gridX, gridY)
                        }

                        clusters.values.forEach { group ->
                            val avgLat = group.map { it.lat }.average()
                            val avgLon = group.map { it.lon }.average()
                            val count = group.size

                            val clusterMarker = if (activeMetrobusClusterCount < recycledMetrobusClusterMarkers.size) {
                                recycledMetrobusClusterMarkers[activeMetrobusClusterCount]
                            } else {
                                Marker(mapView).also {
                                    recycledMetrobusClusterMarkers.add(it)
                                    mapView.overlays.add(it)
                                }
                            }
                            activeMetrobusClusterCount++

                            clusterMarker.infoWindow = null
                            clusterMarker.closeInfoWindow()
                            clusterMarker.position = GeoPoint(avgLat, avgLon)
                            clusterMarker.icon = getClusterIcon(context, count, Color.parseColor("#FFB300"))
                            clusterMarker.title = "$count Paradas de Metrobús"
                            clusterMarker.snippet = "Toca para ampliar área"
                            clusterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            clusterMarker.isEnabled = true
                            clusterMarker.setVisible(true)
                            clusterMarker.setOnMarkerClickListener { _, _ ->
                                if (showMetrobus && clusterMarker.isEnabled) {
                                    mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                                    mapView.controller.zoomTo(currentZoom + 2.2)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    }

                    // LOD 2: 13.5 <= ZOOM < 15.2 -> Compact Dot Icons
                    currentZoom in 13.5..15.2 -> {
                        val stopsToRender = sampleUniformlyByMetrobusGrid(metrobusStopsInViewport, 200)
                        stopsToRender.forEach { stop ->
                            val marker = if (activeMetrobusCount < recycledMetrobusMarkers.size) {
                                recycledMetrobusMarkers[activeMetrobusCount]
                            } else {
                                Marker(mapView).also {
                                    recycledMetrobusMarkers.add(it)
                                    mapView.overlays.add(it)
                                }
                            }
                            activeMetrobusCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            marker.position = GeoPoint(stop.lat, stop.lon)
                            marker.icon = metrobusDotDrawable
                            marker.title = stop.denominacion
                            marker.snippet = "Metrobús ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            marker.isEnabled = true
                            marker.setVisible(true)
                            marker.setOnMarkerClickListener { m, _ ->
                                handleTapAtGeoPoint(context, mapView, m.position)
                            }
                        }
                    }

                    // LOD 3: ZOOM >= 15.2 -> Full Detailed Pins with Metrobús Logo
                    else -> {
                        val stopsToRender = sampleUniformlyByMetrobusGrid(metrobusStopsInViewport, 300)
                        stopsToRender.forEach { stop ->
                            val marker = if (activeMetrobusCount < recycledMetrobusMarkers.size) {
                                recycledMetrobusMarkers[activeMetrobusCount]
                            } else {
                                Marker(mapView).also {
                                    recycledMetrobusMarkers.add(it)
                                    mapView.overlays.add(it)
                                }
                            }
                            activeMetrobusCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            marker.position = GeoPoint(stop.lat, stop.lon)
                            marker.icon = metrobusDrawable
                            marker.title = stop.denominacion
                            marker.snippet = "Metrobús ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            marker.isEnabled = true
                            marker.setVisible(true)
                            marker.setOnMarkerClickListener { m, _ ->
                                handleTapAtGeoPoint(context, mapView, m.position)
                            }
                        }
                    }
                }
            }
        }

        // Hide unused recycled metrobus markers & clusters
        for (i in activeMetrobusCount until recycledMetrobusMarkers.size) {
            recycledMetrobusMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }
        for (i in activeMetrobusClusterCount until recycledMetrobusClusterMarkers.size) {
            recycledMetrobusClusterMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Render Valenbisi markers
        val showValenbisi = mapFilter.showValenbisi
        this.showValenbisi = showValenbisi
        val validValenbisiStations = if (showValenbisi) {
            if (isValidBox) {
                valenbisiStations.filter { station ->
                    station.latitude in minLat..maxLat && station.longitude in minLon..maxLon
                }
            } else {
                valenbisiStations
            }
        } else {
            emptyList()
        }
        this.currentValenbisiStations = validValenbisiStations

        var activeValenbisiCount = 0
        var activeValenbisiClusterCount = 0

        if (showValenbisi) {
            val isOnlyValenbisi = !mapFilter.showBus && !mapFilter.showMetro && !mapFilter.showCercanias && !mapFilter.isFavorites

            if (isOnlyValenbisi) {
                when {
                    // 1. Zoom < 13.5 -> Group/cluster them
                    currentZoom < 13.5 -> {
                        val gridSize = if (currentZoom < 11.0) 0.06 else 0.025
                        val clusters = validValenbisiStations.groupBy { station ->
                            val gridX = (station.latitude / gridSize).toInt()
                            val gridY = (station.longitude / gridSize).toInt()
                            Pair(gridX, gridY)
                        }

                        clusters.values.forEach { group ->
                            val avgLat = group.map { it.latitude }.average()
                            val avgLon = group.map { it.longitude }.average()
                            val count = group.size

                            val clusterMarker = if (activeValenbisiClusterCount < recycledValenbisiClusterMarkers.size) {
                                recycledValenbisiClusterMarkers[activeValenbisiClusterCount]
                            } else {
                                Marker(mapView).also {
                                    recycledValenbisiClusterMarkers.add(it)
                                }
                            }
                            activeValenbisiClusterCount++

                            clusterMarker.infoWindow = null
                            clusterMarker.closeInfoWindow()
                            clusterMarker.position = GeoPoint(avgLat, avgLon)
                            // Use Emerald green for Valenbisi clusters: #10B981
                            clusterMarker.icon = getClusterIcon(context, count, Color.parseColor("#10B981"))
                            clusterMarker.title = "$count Estaciones de Valenbisi"
                            clusterMarker.snippet = "Toca para ampliar área"
                            clusterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            clusterMarker.isEnabled = true
                            clusterMarker.setVisible(true)
                            clusterMarker.setOnMarkerClickListener { _, _ ->
                                if (showValenbisi && clusterMarker.isEnabled) {
                                    mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                                    mapView.controller.zoomTo(currentZoom + 2.2)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    }

                    // 2. Zoom in 13.5..15.2 -> Show color dots
                    currentZoom in 13.5..15.2 -> {
                        validValenbisiStations.forEach { station ->
                            val marker = if (activeValenbisiCount < recycledValenbisiMarkers.size) {
                                recycledValenbisiMarkers[activeValenbisiCount]
                            } else {
                                Marker(mapView).also {
                                    recycledValenbisiMarkers.add(it)
                                }
                            }
                            activeValenbisiCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            marker.position = GeoPoint(station.latitude, station.longitude)

                            val dotResult = getValenbisiCompactDotIcon(context, station.available)
                            marker.icon = dotResult.drawable
                            marker.setAnchor(dotResult.anchorU, dotResult.anchorV)

                            marker.title = station.name
                            marker.snippet = "Bicis: ${station.available} • Huecos: ${station.free}"
                            marker.isEnabled = true
                            marker.setVisible(true)

                            if (station.available == 0) {
                                marker.alpha = 0.55f
                            } else {
                                marker.alpha = 1.0f
                            }

                            marker.setOnMarkerClickListener { m, _ ->
                                handleTapAtGeoPoint(context, mapView, m.position)
                            }
                        }
                    }

                    // 3. Zoom >= 15.2 -> Show full detailed pins
                    else -> {
                        val showPill = currentZoom >= 14.5
                        validValenbisiStations.forEach { station ->
                            val marker = if (activeValenbisiCount < recycledValenbisiMarkers.size) {
                                recycledValenbisiMarkers[activeValenbisiCount]
                            } else {
                                Marker(mapView).also {
                                    recycledValenbisiMarkers.add(it)
                                }
                            }
                            activeValenbisiCount++

                            marker.infoWindow = null
                            marker.closeInfoWindow()
                            marker.position = GeoPoint(station.latitude, station.longitude)

                            val iconResult = getValenbisiMarkerIcon(context, station.available, station.free, isDarkMode, showPill)
                            marker.icon = iconResult.drawable
                            marker.setAnchor(iconResult.anchorU, iconResult.anchorV)

                            marker.title = station.name
                            marker.snippet = "Disponibles: ${station.available} • Huecos: ${station.free}"
                            marker.isEnabled = true
                            marker.setVisible(true)

                            if (station.available == 0) {
                                marker.alpha = 0.55f
                            } else {
                                marker.alpha = 1.0f
                            }

                            marker.setOnMarkerClickListener { m, _ ->
                                handleTapAtGeoPoint(context, mapView, m.position)
                            }
                        }
                    }
                }
            } else {
                // If not "only Valenbisi", hide completely below zoom 15.2
                if (currentZoom >= 15.2) {
                    val showPill = currentZoom >= 14.5
                    validValenbisiStations.forEach { station ->
                        val marker = if (activeValenbisiCount < recycledValenbisiMarkers.size) {
                            recycledValenbisiMarkers[activeValenbisiCount]
                        } else {
                            Marker(mapView).also {
                                recycledValenbisiMarkers.add(it)
                            }
                        }
                        activeValenbisiCount++

                        marker.infoWindow = null
                        marker.closeInfoWindow()
                        marker.position = GeoPoint(station.latitude, station.longitude)

                        val iconResult = getValenbisiMarkerIcon(context, station.available, station.free, isDarkMode, showPill)
                        marker.icon = iconResult.drawable
                        marker.setAnchor(iconResult.anchorU, iconResult.anchorV)

                        marker.title = station.name
                        marker.snippet = "Disponibles: ${station.available} • Huecos: ${station.free}"
                        marker.isEnabled = true
                        marker.setVisible(true)

                        if (station.available == 0) {
                            marker.alpha = 0.55f
                        } else {
                            marker.alpha = 1.0f
                        }

                        marker.setOnMarkerClickListener { m, _ ->
                            handleTapAtGeoPoint(context, mapView, m.position)
                        }
                    }
                }
            }
        }

        // Hide unused recycled valenbisi markers
        for (i in activeValenbisiCount until recycledValenbisiMarkers.size) {
            recycledValenbisiMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Hide unused recycled valenbisi cluster markers
        for (i in activeValenbisiClusterCount until recycledValenbisiClusterMarkers.size) {
            recycledValenbisiClusterMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Paint Custom Places (Home, Work, and Favorites) with Zoom LOD
        val allCustomPlaces = mutableListOf<Pair<RecentSearch, CustomPlaceType>>()
        homeLocation?.let { home ->
            allCustomPlaces.add(Pair(home, CustomPlaceType.HOME))
        }
        workLocation?.let { work ->
            allCustomPlaces.add(Pair(work, CustomPlaceType.WORK))
        }
        customFavorites.forEach { fav ->
            val placeType = when {
                fav.title.equals("Casa", ignoreCase = true) || fav.type.equals("home", ignoreCase = true) -> CustomPlaceType.HOME
                fav.title.equals("Trabajo", ignoreCase = true) || fav.type.equals("work", ignoreCase = true) -> CustomPlaceType.WORK
                else -> CustomPlaceType.FAVORITE
            }
            val alreadyAdded = allCustomPlaces.any { it.first.latitude == fav.latitude && it.first.longitude == fav.longitude }
            if (!alreadyAdded) {
                allCustomPlaces.add(Pair(fav, placeType))
            }
        }

        var activeCustomFavCount = 0
        allCustomPlaces.forEach { (place, placeType) ->
            val marker = if (activeCustomFavCount < recycledCustomFavoriteMarkers.size) {
                recycledCustomFavoriteMarkers[activeCustomFavCount]
            } else {
                Marker(mapView).also {
                    recycledCustomFavoriteMarkers.add(it)
                }
            }
            activeCustomFavCount++

            marker.infoWindow = null
            marker.closeInfoWindow()
            marker.position = GeoPoint(place.latitude, place.longitude)
            
            val iconResult = getCustomPlaceMarkerIcon(context, placeType, currentZoom, isDarkMode)
            marker.icon = iconResult.drawable
            marker.setAnchor(iconResult.anchorU, iconResult.anchorV)
            
            marker.title = place.title
            marker.snippet = place.subtitle
            marker.isEnabled = true
            marker.setVisible(true)
            
            marker.setOnMarkerClickListener { m, _ ->
                val addrItem = SelectedMapItem.Address(
                    com.example.data.model.NominatimResult(
                        displayName = if (place.subtitle.isNotEmpty()) place.title + ", " + place.subtitle else place.title,
                        latitude = m.position.latitude,
                        longitude = m.position.longitude,
                        type = "address",
                        category = "place",
                        isLocalStop = false,
                        stopId = null,
                        stopType = null
                    )
                )
                currentOnSelectItem?.invoke(addrItem)
                true
            }
        }

        // Hide unused recycled custom favorite markers
        for (i in activeCustomFavCount until recycledCustomFavoriteMarkers.size) {
            recycledCustomFavoriteMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Re-order overlays to respect the layer sequence:
        // 1. mapEventsOverlay (lowest layer, click listener on empty map space)
        // 2. Base polygons / footprints / transit polylines
        // 3. Transit Stops: Bus, Metrobus, Valenbisi, Metro, Cercanias
        // 4. Custom Places: Home (🏠), Work (💼), Favorites (⭐) - Drawn ON TOP of transit stops!
        // 5. Destination Marker & Active Itinerary
        // 6. User Location overlay (highest layer, on top of everything)
        mapView.overlays.clear()

        mapEventsOverlay?.let { mapView.overlays.add(it) }

        // Add station footprints (polygons) on top of base map when no active route
        if (selectedItinerary == null) {
            MetroStationFootprintsOverlayManager.addFootprintsToMap(context, mapView, showMetro, currentZoom)
        }

        // Add active metro line polylines when no active route
        if (showMetro && selectedItinerary == null) {
            val loadedLines = MetroMapOverlayLoader.getLoadedPolylines()
            loadedLines.forEach { polyline ->
                mapView.overlays.add(polyline)
            }
        }

        // Add active cercanías line polylines when no active route
        if (showCercanias && selectedItinerary == null) {
            val loadedCercaniasLines = MetroMapOverlayLoader.getLoadedCercaniasPolylines()
            loadedCercaniasLines.forEach { polyline ->
                mapView.overlays.add(polyline)
            }
        }

        // In "Ruta Activa" mode (selectedItinerary != null), hide all secondary layers (bus, metrobus, valenbisi, clusters, station markers)
        if (selectedItinerary == null) {
            // Add active bus markers and active cluster markers
            for (i in 0 until activeBusCount) {
                mapView.overlays.add(recycledBusMarkers[i])
            }
            for (i in 0 until activeClusterCount) {
                mapView.overlays.add(recycledClusterMarkers[i])
            }

            // Add active Metrobus markers
            if (showMetrobus) {
                for (i in 0 until activeMetrobusCount) {
                    mapView.overlays.add(recycledMetrobusMarkers[i])
                }
                for (i in 0 until activeMetrobusClusterCount) {
                    mapView.overlays.add(recycledMetrobusClusterMarkers[i])
                }
            }

            // Add active Valenbisi markers
            if (showValenbisi) {
                for (i in 0 until activeValenbisiCount) {
                    mapView.overlays.add(recycledValenbisiMarkers[i])
                }
                for (i in 0 until activeValenbisiClusterCount) {
                    mapView.overlays.add(recycledValenbisiClusterMarkers[i])
                }
            }

            // Add active metro station access points (escaleras/ascensores) and metro markers
            MetroStationFootprintsOverlayManager.addAccessesToMap(context, mapView, showMetro, currentZoom)

            if (showMetro) {
                for (i in 0 until validMetroStations.size) {
                    mapView.overlays.add(recycledMetroMarkers[i])
                }
            }

            // Add active cercanías markers
            if (showCercanias) {
                for (i in 0 until validCercaniasStations.size) {
                    mapView.overlays.add(recycledCercaniasMarkers[i])
                }
            }

            // Add custom places markers (Home, Work, Favorites) ON TOP of all transit stops
            for (i in 0 until activeCustomFavCount) {
                mapView.overlays.add(recycledCustomFavoriteMarkers[i])
            }
        } else {
            MetroStationFootprintsOverlayManager.clearFromMap(mapView)
        }

        // Draw multimodal active itinerary polylines and markers
        selectedItinerary?.let { itinerary ->
            val allPoints = mutableListOf<GeoPoint>()
            itinerary.legs.forEach { leg ->
                val pts = if (leg.geometry.isNotEmpty()) {
                    leg.geometry
                } else if (leg.intermediateStops.isNotEmpty()) {
                    leg.intermediateStops.map { GeoPoint(it.lat, it.lon) }
                } else {
                    val fromPt = GeoPoint(leg.fromLat, leg.fromLon)
                    val toPt = GeoPoint(leg.toLat, leg.toLon)
                    if (fromPt.latitude != 0.0 && toPt.latitude != 0.0) listOf(fromPt, toPt) else emptyList()
                }

                if (pts.isNotEmpty()) {
                    allPoints.addAll(pts)
                    val polyline = Polyline(mapView).apply {
                        setPoints(pts)
                        infoWindow = null
                        setOnClickListener { _, _, _ -> true }
                        if (leg.mode == TransitMode.WALK) {
                            outlinePaint.color = Color.parseColor("#64748B")
                            outlinePaint.alpha = 220
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 12f), 0f)
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        } else {
                            val hexColorStr = com.example.util.LineColorResolver.resolveRouteColorHex(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)
                            outlinePaint.color = Color.parseColor(hexColorStr)
                            outlinePaint.alpha = 255
                            outlinePaint.strokeWidth = 14f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                    }
                    mapView.overlays.add(polyline)
                }
            }

            // Helper function to format route name nicely (e.g. 3 -> L3 for subway, 1 -> C1 for rail)
            fun formatRouteNameLocal(shortName: String?, mode: TransitMode): String {
                if (shortName.isNullOrBlank()) return mode.displayNameEs
                val trimmed = shortName.trim()
                if (trimmed.all { it.isDigit() }) {
                    return when (mode) {
                        TransitMode.SUBWAY, TransitMode.TRAM -> "L$trimmed"
                        TransitMode.RAIL -> "C$trimmed"
                        else -> trimmed
                    }
                }
                return trimmed
            }

            // Helper function for local distance calculation
            fun distanceMetersLocal(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
                val latMid = (lat1 + lat2) / 2.0 * Math.PI / 180.0
                val dLat = (lat2 - lat1) * 111139.0
                val dLon = (lon2 - lon1) * 111139.0 * Math.cos(latMid)
                return Math.sqrt(dLat * dLat + dLon * dLon)
            }

            // Helper functions to safely extract start/end points of a leg
            fun getLegStartPoint(l: com.example.data.model.routing.PlannedLeg): GeoPoint? {
                return l.geometry.firstOrNull()
                    ?: l.intermediateStops.firstOrNull()?.let { GeoPoint(it.lat, it.lon) }
            }

            fun getLegEndPoint(l: com.example.data.model.routing.PlannedLeg): GeoPoint? {
                return l.geometry.lastOrNull()
                    ?: l.intermediateStops.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
            }

            // Draw intermediate stop white dots with smart halo text for transit legs (BUS, SUBWAY, TRAM, RAIL)
            val currentZoom = mapView.zoomLevelDouble
            if (currentZoom >= 13.0) {
                itinerary.legs.forEach { leg ->
                    if (leg.mode != TransitMode.WALK && leg.mode != TransitMode.BICYCLE) {
                        // 1. Draw intermediate stops
                        leg.intermediateStops.forEach { stop ->
                            if (stop.lat in 38.0..41.0 && stop.lon in -2.0..1.0) {
                                val stopBitmap = createStopDotWithTextIcon(context, stop.name, currentZoom, isDarkMode)
                                val dotMarker = Marker(mapView).apply {
                                    position = GeoPoint(stop.lat, stop.lon)
                                    icon = android.graphics.drawable.BitmapDrawable(context.resources, stopBitmap)
                                    
                                    val density = context.resources.displayMetrics.density
                                    val dotRadius = 4f * density
                                    val borderSize = 1.2f * density
                                    setAnchor((dotRadius + borderSize) / stopBitmap.width.toFloat(), 0.5f)
                                    
                                    title = stop.name
                                    subDescription = stop.formattedTime
                                    infoWindow = null
                                    setOnMarkerClickListener { _, _ -> true }
                                }
                                mapView.overlays.add(dotMarker)
                            }
                        }
                        // 2. Draw final station of this transit leg (equally important)
                        val endPt = getLegEndPoint(leg)
                        if (endPt != null && endPt.latitude in 38.0..41.0 && endPt.longitude in -2.0..1.0 && leg.toName.isNotBlank()) {
                            val stopBitmap = createStopDotWithTextIcon(context, leg.toName, currentZoom, isDarkMode)
                            val dotMarker = Marker(mapView).apply {
                                position = endPt
                                icon = android.graphics.drawable.BitmapDrawable(context.resources, stopBitmap)
                                val density = context.resources.displayMetrics.density
                                val dotRadius = 4f * density
                                val borderSize = 1.2f * density
                                setAnchor((dotRadius + borderSize) / stopBitmap.width.toFloat(), 0.5f)
                                title = leg.toName
                                infoWindow = null
                                setOnMarkerClickListener { _, _ -> true }
                            }
                            mapView.overlays.add(dotMarker)
                        }
                    }
                }
            }

            // Draw Origin, Destination, and Transfer markers for the route
            if (itinerary.legs.isNotEmpty()) {
                val itineraryMarkerIndex = 0

                // 1. Origin Marker
                val firstLeg = itinerary.legs.first()
                val originPt = getLegStartPoint(firstLeg)
                if (originPt != null) {
                    val originResult = getOriginMarkerIcon(context)
                    val originMarker = Marker(mapView).apply {
                        position = originPt
                        title = firstLeg.fromName.ifBlank { "Origen" }
                        snippet = "Punto de origen"
                        icon = originResult.drawable
                        setAnchor(originResult.anchorU, originResult.anchorV)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    mapView.overlays.add(originMarker)
                }

                // 2. Transfer / Connection Markers
                for (i in 1 until itinerary.legs.size) {
                    val leg = itinerary.legs[i]
                    
                    // Skip if the current leg is WALK (redundant walking label filtered out)
                    if (leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE) continue
                    
                    // Look back for the previous active transit leg to detect direct transfers
                    var prevTransitLeg: com.example.data.model.routing.PlannedLeg? = null
                    for (j in i - 1 downTo 0) {
                        if (itinerary.legs[j].mode != TransitMode.WALK && itinerary.legs[j].mode != TransitMode.BICYCLE) {
                            prevTransitLeg = itinerary.legs[j]
                            break
                        }
                    }

                    val transferPt = getLegStartPoint(leg)
                    if (transferPt != null) {
                        val isDirectTransfer = if (prevTransitLeg != null) {
                            val prevEnd = getLegEndPoint(prevTransitLeg)
                            val currStart = getLegStartPoint(leg)
                            if (prevEnd != null && currStart != null) {
                                val dist = distanceMetersLocal(prevEnd.latitude, prevEnd.longitude, currStart.latitude, currStart.longitude)
                                dist < 100.0 // extremely close stations -> direct in-station transfer
                            } else {
                                false
                            }
                        } else {
                            false
                        }

                        if (isDirectTransfer && prevTransitLeg != null) {
                            val stationName = leg.fromName.ifBlank { prevTransitLeg.toName }
                            val fromFormatted = formatRouteNameLocal(prevTransitLeg.routeShortName, prevTransitLeg.mode)
                            val toFormatted = formatRouteNameLocal(leg.routeShortName, leg.mode)
                            val transitionLabel = "$stationName ($fromFormatted) ➔ ($toFormatted)"
                            
                            val transferBitmap = createTransferLabelIcon(context, transitionLabel, currentZoom, isDarkMode)
                            val transferMarker = Marker(mapView).apply {
                                position = transferPt
                                icon = android.graphics.drawable.BitmapDrawable(context.resources, transferBitmap)
                                val density = context.resources.displayMetrics.density
                                val dotRadius = 5.5f * density
                                val borderSize = 1.5f * density
                                setAnchor((dotRadius + borderSize) / transferBitmap.width.toFloat(), 0.5f)
                                title = transitionLabel
                                snippet = "Transbordo directo"
                                infoWindow = null
                                setOnMarkerClickListener { _, _ -> true }
                            }
                            mapView.overlays.add(transferMarker)
                        } else {
                            val label = leg.routeShortName ?: leg.mode.displayNameEs
                            val transferResult = getTransferMarkerIcon(context, leg.routeColorHex, label)
                            val transferMarker = Marker(mapView).apply {
                                position = transferPt
                                title = leg.fromName.ifBlank { "Transbordo" }
                                snippet = "Transbordo • ${leg.mode.displayNameEs}"
                                icon = transferResult.drawable
                                setAnchor(transferResult.anchorU, transferResult.anchorV)
                                infoWindow = null
                                setOnMarkerClickListener { _, _ -> true }
                            }
                            mapView.overlays.add(transferMarker)
                        }
                    }
                }

                // 3. Destination Marker
                val lastLeg = itinerary.legs.last()
                val destPt = lastLeg.geometry.lastOrNull()
                    ?: lastLeg.intermediateStops.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
                if (destPt != null) {
                    val destResult = getDestinationMarkerIcon(context, isDarkMode)
                    val routeDestMarker = Marker(mapView).apply {
                        position = destPt
                        title = lastLeg.toName.ifBlank { "Destino" }
                        snippet = "Llegada al destino"
                        icon = destResult.drawable
                        setAnchor(destResult.anchorU, destResult.anchorV)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    mapView.overlays.add(routeDestMarker)
                }
            }

            // Auto-fit bounding box to entire route ONLY the first time an itinerary is loaded or selected
            if (allPoints.isNotEmpty() && lastZoomedItineraryId != itinerary.id) {
                lastZoomedItineraryId = itinerary.id
                val minLat = allPoints.minOf { it.latitude }
                val maxLat = allPoints.maxOf { it.latitude }
                val minLon = allPoints.minOf { it.longitude }
                val maxLon = allPoints.maxOf { it.longitude }
                if (maxLat - minLat > 0.0001 && maxLon - minLon > 0.0001) {
                    val box = org.osmdroid.util.BoundingBox(maxLat + 0.002, maxLon + 0.002, minLat - 0.002, minLon - 0.002)
                    mapView.post {
                        try {
                            mapView.zoomToBoundingBox(box, true, 80)
                        } catch (e: Exception) {
                            android.util.Log.w("MapMarkersManager", "Could not zoom to itinerary bounding box", e)
                        }
                    }
                }
            }
        }
        if (selectedItinerary == null) {
            lastZoomedItineraryId = null
        }

        // Add destination marker on top of transit stops
        destinationMarker?.let {
            if (destinationLocation != null) {
                mapView.overlays.add(it)
            }
        }

        // Add user location marker on top of everything
        userMarker?.let {
            if (userLocation != null) {
                mapView.overlays.add(it)
            }
        }

        mapView.invalidate()
    }

    private fun sampleUniformlyByGrid(stops: List<GeoportalStopEntity>, maxCount: Int): List<GeoportalStopEntity> {
        if (stops.size <= maxCount) return stops

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        stops.forEach { stop ->
            if (stop.lat < minLat) minLat = stop.lat
            if (stop.lat > maxLat) maxLat = stop.lat
            if (stop.lon < minLon) minLon = stop.lon
            if (stop.lon > maxLon) maxLon = stop.lon
        }

        val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)

        val gridSide = kotlin.math.sqrt(maxCount.toDouble() * 1.5).toInt().coerceAtLeast(4)
        val latStep = latSpan / gridSide
        val lonStep = lonSpan / gridSide

        val grid = HashMap<Pair<Int, Int>, MutableList<GeoportalStopEntity>>()
        stops.forEach { stop ->
            val cellX = ((stop.lat - minLat) / latStep).toInt().coerceIn(0, gridSide - 1)
            val cellY = ((stop.lon - minLon) / lonStep).toInt().coerceIn(0, gridSide - 1)
            grid.getOrPut(Pair(cellX, cellY)) { mutableListOf() }.add(stop)
        }

        val result = ArrayList<GeoportalStopEntity>(maxCount)
        val cellLists = grid.values.filter { it.isNotEmpty() }.toMutableList()

        var index = 0
        while (result.size < maxCount && cellLists.isNotEmpty()) {
            val iterator = cellLists.iterator()
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

        return result
    }

    private fun sampleUniformlyByMetrobusGrid(stops: List<com.example.data.database.MetrobusStopEntity>, maxCount: Int): List<com.example.data.database.MetrobusStopEntity> {
        if (stops.size <= maxCount) return stops

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        stops.forEach { stop ->
            if (stop.lat < minLat) minLat = stop.lat
            if (stop.lat > maxLat) maxLat = stop.lat
            if (stop.lon < minLon) minLon = stop.lon
            if (stop.lon > maxLon) maxLon = stop.lon
        }

        val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)

        val gridSide = kotlin.math.sqrt(maxCount.toDouble() * 1.5).toInt().coerceAtLeast(4)
        val latStep = latSpan / gridSide
        val lonStep = lonSpan / gridSide

        val grid = HashMap<Pair<Int, Int>, MutableList<com.example.data.database.MetrobusStopEntity>>()
        stops.forEach { stop ->
            val cellX = ((stop.lat - minLat) / latStep).toInt().coerceIn(0, gridSide - 1)
            val cellY = ((stop.lon - minLon) / lonStep).toInt().coerceIn(0, gridSide - 1)
            grid.getOrPut(Pair(cellX, cellY)) { mutableListOf() }.add(stop)
        }

        val result = ArrayList<com.example.data.database.MetrobusStopEntity>(maxCount)
        val cellLists = grid.values.filter { it.isNotEmpty() }.toMutableList()

        var index = 0
        while (result.size < maxCount && cellLists.isNotEmpty()) {
            val iterator = cellLists.iterator()
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

        return result
    }
}
