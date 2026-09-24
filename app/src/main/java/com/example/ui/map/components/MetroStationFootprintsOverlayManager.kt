package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import android.util.Log
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.model.MetroStation

object MetroStationFootprintsOverlayManager {

    private const val TAG = "MetroFootprints"
    // Show station footprints and access points at zoom >= 15.8 by default, or >= 15.2 when a station is selected
    private const val MIN_ZOOM_THRESHOLD = 15.8
    private const val SELECTED_MIN_ZOOM_THRESHOLD = 15.2

    @Volatile
    private var isLoaded = false
    @Volatile
    private var isLoading = false
    private val stationPolygonsData = java.util.concurrent.CopyOnWriteArrayList<StationPolygonData>()
    private val accessPointsData = java.util.concurrent.CopyOnWriteArrayList<AccessPointData>()

    private val cachedStationPolygons = mutableListOf<Pair<Polygon, StationPolygonData>>()
    private val cachedAccessMarkers = mutableListOf<Pair<Marker, AccessPointData>>()
    private var cachedMapViewRef = java.lang.ref.WeakReference<MapView>(null)

    data class StationPolygonData(
        val points: List<GeoPoint>,
        val stationName: String,
        val centerLat: Double,
        val centerLon: Double
    )

    data class AccessPointData(
        val geoPoint: GeoPoint,
        val typeCas: String,
        val labelText: String,
        val stationName: String,
        val stationAreaId: String = ""
    )

    fun preloadGeoJson(context: Context, mapView: MapView? = null) {
        if (mapView != null) {
            cachedMapViewRef = java.lang.ref.WeakReference(mapView)
        }
        if (isLoaded || isLoading) return
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                loadGeoJsonDataInternal(context.applicationContext)
                withContext(Dispatchers.Main) {
                    val mv = cachedMapViewRef.get()
                    if (mv != null && (mv.isAttachedToWindow || mv.parent != null)) {
                        mv.postInvalidate()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed async preload of GeoJSON", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadGeoJsonDataIfNeeded(context: Context, mapView: MapView) {
        if (isLoaded) return
        if (!isLoading) {
            preloadGeoJson(context, mapView)
        }
    }

    private fun loadGeoJsonDataInternal(context: Context) {
        if (isLoaded) return
        try {
            // 1. Load Station Footprints (Formas estaciones metrovalencia.geojson)
            val footprintsJson = context.assets.open("Formas estaciones metrovalencia.geojson")
                .bufferedReader().use { it.readText() }
            val footprintsObj = JSONObject(footprintsJson)
            val features = footprintsObj.optJSONArray("features")
            val loadedPolygonsList = mutableListOf<StationPolygonData>()
            if (features != null) {
                for (i in 0 until features.length()) {
                    val feature = features.optJSONObject(i) ?: continue
                    val props = feature.optJSONObject("properties")
                    val rawName = props?.optString("nombre") ?: ""
                    val stationName = MetroStationAccessMatcher.resolveFootprintStationName(rawName)
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    val geomType = geometry.optString("type")
                    val coordsArray = geometry.optJSONArray("coordinates") ?: continue

                    if (geomType == "Polygon") {
                        val ring = coordsArray.optJSONArray(0) ?: continue
                        val pts = parseRingCoordinates(ring)
                        if (pts.isNotEmpty()) {
                            val avgLat = pts.map { it.latitude }.average()
                            val avgLon = pts.map { it.longitude }.average()
                            loadedPolygonsList.add(StationPolygonData(pts, stationName, avgLat, avgLon))
                        }
                    } else if (geomType == "MultiPolygon") {
                        for (j in 0 until coordsArray.length()) {
                            val polyArray = coordsArray.optJSONArray(j) ?: continue
                            val ring = polyArray.optJSONArray(0) ?: continue
                            val pts = parseRingCoordinates(ring)
                            if (pts.isNotEmpty()) {
                                val avgLat = pts.map { it.latitude }.average()
                                val avgLon = pts.map { it.longitude }.average()
                                loadedPolygonsList.add(StationPolygonData(pts, stationName, avgLat, avgLon))
                            }
                        }
                    }
                }
            }

            // 2. Load Station Accesses (Accesos metrovalencia.geojson)
            val accessesJson = context.assets.open("Accesos metrovalencia.geojson")
                .bufferedReader().use { it.readText() }
            val accessesObj = JSONObject(accessesJson)
            val accessFeatures = accessesObj.optJSONArray("features")
            val loadedAccessPoints = mutableListOf<AccessPointData>()
            if (accessFeatures != null) {
                for (i in 0 until accessFeatures.length()) {
                    val feature = accessFeatures.optJSONObject(i) ?: continue
                    val props = feature.optJSONObject("properties")
                    val typeCas = props?.optString("tipo_acceso_cas") ?: "Boca metro"
                    val typeVal = props?.optString("tipo_acceso_val") ?: ""
                    // Skip emergency exits
                    if (typeCas.contains("emergencia", ignoreCase = true) || typeVal.contains("emerg", ignoreCase = true)) {
                        continue
                    }
                    val areaId = props?.optString("id_stationarea") ?: ""
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    if (geometry.optString("type") == "Point") {
                        val coords = geometry.optJSONArray("coordinates") ?: continue
                        if (coords.length() >= 2) {
                            val lon = coords.optDouble(0)
                            val lat = coords.optDouble(1)
                            if (lat != 0.0 && lon != 0.0) {
                                val stationName = MetroStationAccessMatcher.resolveAccessStationName(areaId, lat, lon)
                                val labelText = when {
                                    typeCas.contains("Ascensor", ignoreCase = true) -> "Ascensor"
                                    typeCas.contains("Tranvía", ignoreCase = true) -> "Acceso Tranvía"
                                    else -> "Acceso Metro"
                                }
                                loadedAccessPoints.add(
                                    AccessPointData(
                                        geoPoint = GeoPoint(lat, lon),
                                        typeCas = typeCas,
                                        labelText = labelText,
                                        stationName = stationName,
                                        stationAreaId = areaId
                                    )
                                )
                            }
                        }
                    }
                }
            }

            stationPolygonsData.clear()
            stationPolygonsData.addAll(loadedPolygonsList)
            accessPointsData.clear()
            accessPointsData.addAll(loadedAccessPoints)

            isLoaded = true
            Log.d(TAG, "Loaded ${stationPolygonsData.size} station footprints and ${accessPointsData.size} access points.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading GeoJSON overlays", e)
        }
    }

    private fun parseRingCoordinates(ring: org.json.JSONArray): List<GeoPoint> {
        val pts = mutableListOf<GeoPoint>()
        for (k in 0 until ring.length()) {
            val pt = ring.optJSONArray(k) ?: continue
            if (pt.length() >= 2) {
                val lon = pt.optDouble(0)
                val lat = pt.optDouble(1)
                pts.add(GeoPoint(lat, lon))
            }
        }
        return pts
    }

    fun addFootprintsToMap(
        context: Context,
        mapView: MapView,
        showMetro: Boolean,
        zoomLevel: Double,
        selectedStation: MetroStation? = null
    ) {
        loadGeoJsonDataIfNeeded(context, mapView)
        val minZoom = if (selectedStation != null) SELECTED_MIN_ZOOM_THRESHOLD else MIN_ZOOM_THRESHOLD
        val shouldShow = showMetro && zoomLevel >= minZoom
        if (shouldShow && isLoaded) {
            if (cachedMapViewRef.get() != mapView || cachedStationPolygons.isEmpty()) {
                cachedMapViewRef = java.lang.ref.WeakReference(mapView)
                cachedStationPolygons.clear()
                for (polyData in stationPolygonsData) {
                    val polygon = Polygon(mapView).apply {
                        points = polyData.points
                        isEnabled = true
                    }
                    polygon.setOnClickListener { _, _, _ -> false } // Non-clickable
                    cachedStationPolygons.add(Pair(polygon, polyData))
                }
            }

            for ((polygon, polyData) in cachedStationPolygons) {
                if (selectedStation != null) {
                    // Strict filtering: ONLY add the polygon belonging to the selected station
                    val isSelected = MetroStationAccessMatcher.matchesStation(polyData.stationName, selectedStation)
                    if (isSelected) {
                        polygon.fillColor = Color.parseColor("#4DE2001A") // Vibrant 30% red fill
                        polygon.strokeColor = Color.parseColor("#FFE2001A") // Solid prominent red border
                        polygon.strokeWidth = 3.5f
                        polygon.isEnabled = true
                        mapView.overlays.add(polygon)
                    }
                } else {
                    // No station selected: show standard subtle footprints at high zoom
                    polygon.fillColor = Color.parseColor("#33EF4444") // Standard ~20% fill
                    polygon.strokeColor = Color.parseColor("#99DC2626") // Standard ~60% border
                    polygon.strokeWidth = 2.5f
                    polygon.isEnabled = true
                    mapView.overlays.add(polygon)
                }
            }
        }
    }

    fun addAccessesToMap(
        context: Context,
        mapView: MapView,
        showMetro: Boolean,
        zoomLevel: Double,
        selectedStation: MetroStation? = null,
        isEmtHighlighted: Boolean = false,
        secondaryAlpha: Float = 1.0f,
        metroStations: List<MetroStation> = emptyList(),
        onSelectItem: ((com.example.ui.map.SelectedMapItem) -> Unit)? = null
    ) {
        loadGeoJsonDataIfNeeded(context, mapView)
        val minZoom = if (selectedStation != null) SELECTED_MIN_ZOOM_THRESHOLD else MIN_ZOOM_THRESHOLD
        val shouldShow = showMetro && zoomLevel >= minZoom
        if (shouldShow && isLoaded) {
            if (cachedMapViewRef.get() != mapView || cachedAccessMarkers.isEmpty()) {
                cachedMapViewRef = java.lang.ref.WeakReference(mapView)
                cachedAccessMarkers.clear()
                for (access in accessPointsData) {
                    val icon = MetroAccessPointRenderer.getAccessIcon(context, access.typeCas)
                    val marker = Marker(mapView).apply {
                        position = access.geoPoint
                        this.icon = icon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP)
                        alpha = 0.85f
                        infoWindow = MetroAccessPointRenderer.AccessToolTipInfoWindow(access.labelText, mapView)
                        setOnMarkerClickListener { m, _ ->
                            if (m.isInfoWindowShown) {
                                m.closeInfoWindow()
                            } else {
                                m.showInfoWindow()
                            }
                            if (metroStations.isNotEmpty() && onSelectItem != null) {
                                val match = metroStations.find {
                                    MetroStationAccessMatcher.matchesStation(access.stationName, it)
                                }
                                if (match != null) {
                                    onSelectItem.invoke(com.example.ui.map.SelectedMapItem.Metro(match))
                                }
                            }
                            true
                        }
                    }
                    cachedAccessMarkers.add(Pair(marker, access))
                }
            }

            val metroAlpha = MapFadeTransitionManager.getLayerAlpha(MapFadeTransitionManager.Layer.METRO)
            for ((marker, accessData) in cachedAccessMarkers) {
                if (selectedStation != null) {
                    // Strict filtering: ONLY add access markers belonging to the selected station
                    val isSelected = MetroStationAccessMatcher.matchesStation(accessData.stationName, selectedStation)
                    if (isSelected) {
                        marker.setVisible(true)
                        marker.alpha = 1.0f * metroAlpha
                        marker.isEnabled = true
                        mapView.overlays.add(marker)
                    }
                } else {
                    // No station selected: show at high zoom, dim if bus or another transit element is selected
                    val isDimmed = isEmtHighlighted || secondaryAlpha < 1.0f
                    val base = if (isDimmed) (secondaryAlpha * 0.85f).coerceAtMost(0.25f) else 0.85f
                    marker.setVisible(true)
                    marker.alpha = base * metroAlpha
                    marker.isEnabled = true
                    mapView.overlays.add(marker)
                }
            }
        }
    }

    fun clearFromMap(mapView: MapView) {
        try {
            if (cachedStationPolygons.isNotEmpty()) {
                mapView.overlays.removeAll(cachedStationPolygons.map { it.first })
            }
            if (cachedAccessMarkers.isNotEmpty()) {
                mapView.overlays.removeAll(cachedAccessMarkers.map { it.first })
            }
            if (mapView.isAttachedToWindow || mapView.parent != null) {
                mapView.invalidate()
            }
        } catch (_: Exception) {}
        cachedMapViewRef.clear()
        cachedStationPolygons.clear()
        cachedAccessMarkers.clear()
    }
}
