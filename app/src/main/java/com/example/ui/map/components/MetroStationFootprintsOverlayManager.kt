package com.example.ui.map.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.LruCache
import android.view.View
import android.widget.TextView
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.infowindow.InfoWindow

object MetroStationFootprintsOverlayManager {

    private const val TAG = "MetroFootprints"
    // Show station footprints and access points only at close-to-maximum zoom (>= 16.2)
    private const val MIN_ZOOM_THRESHOLD = 16.2

    private var isLoaded = false
    private val stationPolygons = mutableListOf<Polygon>()
    private val accessMarkers = mutableListOf<Marker>()

    private val iconCache = LruCache<String, Drawable>(32)

    private fun loadGeoJsonDataIfNeeded(context: Context, mapView: MapView) {
        if (isLoaded) return
        try {
            // 1. Load Station Footprints (Formas estaciones metrovalencia.geojson)
            val footprintsJson = context.assets.open("Formas estaciones metrovalencia.geojson")
                .bufferedReader().use { it.readText() }
            val footprintsObj = JSONObject(footprintsJson)
            val features = footprintsObj.optJSONArray("features")
            if (features != null) {
                for (i in 0 until features.length()) {
                    val feature = features.optJSONObject(i) ?: continue
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    val geomType = geometry.optString("type")
                    val coordsArray = geometry.optJSONArray("coordinates") ?: continue

                    val polygonGeoPointsList = mutableListOf<List<GeoPoint>>()

                    if (geomType == "Polygon") {
                        val ring = coordsArray.optJSONArray(0) ?: continue
                        val pts = parseRingCoordinates(ring)
                        if (pts.isNotEmpty()) {
                            polygonGeoPointsList.add(pts)
                        }
                    } else if (geomType == "MultiPolygon") {
                        for (j in 0 until coordsArray.length()) {
                            val polyArray = coordsArray.optJSONArray(j) ?: continue
                            val ring = polyArray.optJSONArray(0) ?: continue
                            val pts = parseRingCoordinates(ring)
                            if (pts.isNotEmpty()) {
                                polygonGeoPointsList.add(pts)
                            }
                        }
                    }

                    for (pts in polygonGeoPointsList) {
                        val polygon = Polygon().apply {
                            points = pts
                            fillColor = Color.parseColor("#33EF4444") // Subtle semi-transparent red fill (~20% opacity)
                            strokeColor = Color.parseColor("#99DC2626") // Semi-transparent red border (~60% opacity)
                            strokeWidth = 2.5f
                            isEnabled = true
                        }
                        polygon.setOnClickListener { _, _, _ -> false } // Non-clickable
                        stationPolygons.add(polygon)
                    }
                }
            }

            // 2. Load Station Accesses (Accesos metrovalencia.geojson)
            val accessesJson = context.assets.open("Accesos metrovalencia.geojson")
                .bufferedReader().use { it.readText() }
            val accessesObj = JSONObject(accessesJson)
            val accessFeatures = accessesObj.optJSONArray("features")
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
                    val stationName = props?.optString("nom_cataleg") ?: ""
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    if (geometry.optString("type") == "Point") {
                        val coords = geometry.optJSONArray("coordinates") ?: continue
                        if (coords.length() >= 2) {
                            val lon = coords.optDouble(0)
                            val lat = coords.optDouble(1)
                            if (lat != 0.0 && lon != 0.0) {
                                val labelText = when {
                                    typeCas.contains("Ascensor", ignoreCase = true) -> "Ascensor"
                                    typeCas.contains("Tranvía", ignoreCase = true) -> "Acceso Tranvía"
                                    else -> "Acceso Metro"
                                }
                                val toolTipInfoWindow = AccessToolTipInfoWindow(labelText, mapView)
                                val marker = Marker(mapView).apply {
                                    position = GeoPoint(lat, lon)
                                    icon = getAccessIcon(context, typeCas)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP)
                                    alpha = 0.85f // Make entire marker semi-transparent
                                    infoWindow = toolTipInfoWindow
                                    setOnMarkerClickListener { m, _ ->
                                        if (m.isInfoWindowShown) {
                                            m.closeInfoWindow()
                                        } else {
                                            m.showInfoWindow()
                                        }
                                        true
                                    }
                                }
                                accessMarkers.add(marker)
                            }
                        }
                    }
                }
            }

            isLoaded = true
            Log.d(TAG, "Loaded ${stationPolygons.size} station polygons and ${accessMarkers.size} access markers.")
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
        zoomLevel: Double
    ) {
        loadGeoJsonDataIfNeeded(context, mapView)
        val shouldShow = showMetro && zoomLevel >= MIN_ZOOM_THRESHOLD
        if (shouldShow) {
            for (polygon in stationPolygons) {
                polygon.isEnabled = true
                mapView.overlays.add(polygon)
            }
        }
    }

    fun addAccessesToMap(
        context: Context,
        mapView: MapView,
        showMetro: Boolean,
        zoomLevel: Double
    ) {
        loadGeoJsonDataIfNeeded(context, mapView)
        val shouldShow = showMetro && zoomLevel >= MIN_ZOOM_THRESHOLD
        if (shouldShow) {
            for (marker in accessMarkers) {
                marker.isEnabled = true
                marker.setVisible(true)
                mapView.overlays.add(marker)
            }
        }
    }

    fun clearFromMap(mapView: MapView) {
        mapView.overlays.removeAll(stationPolygons)
        mapView.overlays.removeAll(accessMarkers)
        mapView.invalidate()
    }

    private fun getAccessIcon(context: Context, typeCas: String): Drawable {
        val cacheKey = typeCas
        iconCache.get(cacheKey)?.let { return it }

        val density = context.resources.displayMetrics.density
        // Compact icon size: 18dp
        val sizePx = (18 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Semi-transparent Metro red background (~70% opacity)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3DC2626")
            style = Paint.Style.FILL
        }

        // Crisp subtle border (~80% white opacity)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }

        val cornerRadius = 4f * density
        val rect = RectF(1f * density, 1f * density, sizePx - 1f * density, sizePx - 1f * density)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f

        when {
            typeCas.contains("Ascensor", ignoreCase = true) -> {
                // Elevator icon: frame with up/down arrows
                val boxWidth = 6.5f * density
                val boxHeight = 8f * density
                val boxRect = RectF(cx - boxWidth / 2f, cy - boxHeight / 2f, cx + boxWidth / 2f, cy + boxHeight / 2f)
                canvas.drawRoundRect(boxRect, 1.5f * density, 1.5f * density, iconPaint)

                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                val pathUp = Path().apply {
                    moveTo(cx, cy - 2f * density)
                    lineTo(cx - 1.5f * density, cy + 0.2f * density)
                    lineTo(cx + 1.5f * density, cy + 0.2f * density)
                    close()
                }
                val pathDown = Path().apply {
                    moveTo(cx, cy + 2f * density)
                    lineTo(cx - 1.5f * density, cy - 0.2f * density)
                    lineTo(cx + 1.5f * density, cy - 0.2f * density)
                    close()
                }
                canvas.drawPath(pathUp, arrowPaint)
                canvas.drawPath(pathDown, arrowPaint)
            }
            else -> {
                // Boca metro / Acceso subterráneo / Acceso estación / Tranvía -> Stairs icon
                val stairsPath = Path().apply {
                    moveTo(cx - 3.5f * density, cy + 3.5f * density)
                    lineTo(cx - 1f * density, cy + 3.5f * density)
                    lineTo(cx - 1f * density, cy + 1f * density)
                    lineTo(cx + 1f * density, cy + 1f * density)
                    lineTo(cx + 1f * density, cy - 1.5f * density)
                    lineTo(cx + 3.5f * density, cy - 1.5f * density)
                    lineTo(cx + 3.5f * density, cy - 3.5f * density)
                }
                canvas.drawPath(stairsPath, iconPaint)
            }
        }

        val drawable = BitmapDrawable(context.resources, bitmap)
        iconCache.put(cacheKey, drawable)
        return drawable
    }

    private class AccessToolTipInfoWindow(
        titleText: String,
        mapView: MapView
    ) : InfoWindow(createView(mapView.context, titleText), mapView) {

        override fun onOpen(item: Any?) {
            closeAllInfoWindowsOn(mMapView)
            val density = mMapView.context.resources.displayMetrics.density
            mView.translationY = -6f * density
            mView.removeCallbacks(dismissRunnable)
            mView.postDelayed(dismissRunnable, 2200)
        }

        override fun onClose() {
            mView.removeCallbacks(dismissRunnable)
        }

        private val dismissRunnable = Runnable {
            if (isOpen) {
                close()
                mMapView.invalidate()
            }
        }

        companion object {
            private fun createView(context: Context, text: String): View {
                val density = context.resources.displayMetrics.density
                return TextView(context).apply {
                    setText(text)
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(
                        (8 * density).toInt(),
                        (4 * density).toInt(),
                        (8 * density).toInt(),
                        (4 * density).toInt()
                    )
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F00F172A")) // Modern dark slate tooltip badge
                        cornerRadius = 6f * density
                        setStroke(
                            (1f * density).toInt(),
                            Color.parseColor("#475569")
                        )
                    }
                    elevation = 4f * density
                }
            }
        }
    }
}
