package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.lang.ref.WeakReference

/**
 * Handles deferred (lazy) loading and LOD caching of the optimized 80 KB Cercanías GeoJSON routes.
 * Avoids loading and parsing the GeoJSON during app cold start.
 */
object CercaniasMapOverlayLoader {
    private const val TAG = "CercaniasOverlayLoader"

    @Volatile
    private var isLoading: Boolean = false

    private val _isLoadedState = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isLoadedState: kotlinx.coroutines.flow.StateFlow<Boolean> = _isLoadedState

    val isLoaded: Boolean
        get() = _isLoadedState.value

    private val pendingCallbacks = mutableListOf<() -> Unit>()

    @Volatile
    private var polylineSets: MetroMapOverlayLoader.PolylineSets = MetroMapOverlayLoader.PolylineSets(
        highResClose = emptyList(),
        highResMedium = emptyList(),
        highResFar = emptyList(),
        lowResClose = emptyList(),
        lowResMedium = emptyList(),
        lowResFar = emptyList()
    )

    private var cachedMapViewRef: WeakReference<MapView>? = null
    private var cachedZoomCategory: MetroMapOverlayLoader.ZoomCategory? = null
    private var cachedUseHighRes: Boolean? = null
    private var cachedPolylines: List<Polyline> = emptyList()

    /**
     * Triggers asynchronous background parsing of ruta_cercanias_valencia.geojson if not already loaded.
     */
    @Synchronized
    fun ensureLoaded(context: Context, scope: CoroutineScope? = null, onComplete: (() -> Unit)? = null) {
        if (isLoaded) {
            onComplete?.invoke()
            return
        }

        if (onComplete != null) {
            pendingCallbacks.add(onComplete)
        }

        if (isLoading) return
        isLoading = true

        val coroutineScope = scope ?: CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                val sets = withContext(Dispatchers.IO) {
                    parseGeoJson(context)
                }
                val callbacksToRun = synchronized(CercaniasMapOverlayLoader) {
                    polylineSets = sets
                    _isLoadedState.value = true
                    isLoading = false
                    cachedMapViewRef = null // Invalidate cache so new polylines generate
                    val callbacks = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    callbacks
                }
                Log.d(TAG, "Successfully lazy-loaded Cercanias polylines from GeoJSON")
                withContext(Dispatchers.Main) {
                    callbacksToRun.forEach { it.invoke() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lazy-load cercanias lines GeoJSON", e)
                synchronized(CercaniasMapOverlayLoader) {
                    isLoading = false
                    pendingCallbacks.clear()
                }
            }
        }
    }

    /**
     * Returns the pre-built OSMPolyline objects for the given MapView, zoom, and resolution.
     * If not loaded yet, triggers lazy background loading without blocking the UI thread.
     */
    @Synchronized
    fun getLoadedPolylines(
        mapView: MapView? = null,
        context: Context? = null,
        zoomCategory: MetroMapOverlayLoader.ZoomCategory = MetroMapOverlayLoader.getZoomCategory(),
        useHighRes: Boolean = MetroMapOverlayLoader.isUseHighRes(),
        onLoaded: (() -> Unit)? = null
    ): List<Polyline> {
        if (!isLoaded) {
            val ctx = context ?: mapView?.context
            if (ctx != null) {
                ensureLoaded(ctx.applicationContext) {
                    mapView?.postInvalidate()
                    onLoaded?.invoke()
                }
            }
            return emptyList()
        }

        if (mapView != null) {
            checkAndRebuildCache(mapView, zoomCategory, useHighRes)
        }
        return cachedPolylines
    }

    private fun checkAndRebuildCache(
        mapView: MapView,
        zoomCategory: MetroMapOverlayLoader.ZoomCategory,
        useHighRes: Boolean
    ) {
        if (cachedMapViewRef?.get() != mapView || cachedZoomCategory != zoomCategory || cachedUseHighRes != useHighRes) {
            cachedMapViewRef = WeakReference(mapView)
            cachedZoomCategory = zoomCategory
            cachedUseHighRes = useHighRes

            val rawList = if (useHighRes) {
                when (zoomCategory) {
                    MetroMapOverlayLoader.ZoomCategory.CLOSE -> polylineSets.highResClose
                    MetroMapOverlayLoader.ZoomCategory.MEDIUM -> polylineSets.highResMedium
                    MetroMapOverlayLoader.ZoomCategory.FAR -> polylineSets.highResFar
                }
            } else {
                when (zoomCategory) {
                    MetroMapOverlayLoader.ZoomCategory.CLOSE -> polylineSets.lowResClose
                    MetroMapOverlayLoader.ZoomCategory.MEDIUM -> polylineSets.lowResMedium
                    MetroMapOverlayLoader.ZoomCategory.FAR -> polylineSets.lowResFar
                }
            }

            cachedPolylines = rawList.map { raw ->
                Polyline(mapView).apply {
                    relatedObject = raw
                    setPoints(raw.points)
                    outlinePaint.color = raw.color
                    outlinePaint.strokeWidth = raw.strokeWidth
                    outlinePaint.isAntiAlias = true
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    infoWindow = null
                    setOnClickListener { _, _, _ -> true }
                }
            }
        }
    }

    private fun parseGeoJson(context: Context): MetroMapOverlayLoader.PolylineSets {
        val highResCloseList = ArrayList<MetroMapOverlayLoader.RawPolyline>()
        val highResMediumList = ArrayList<MetroMapOverlayLoader.RawPolyline>()
        val highResFarList = ArrayList<MetroMapOverlayLoader.RawPolyline>()

        val lowResCloseList = ArrayList<MetroMapOverlayLoader.RawPolyline>()
        val lowResMediumList = ArrayList<MetroMapOverlayLoader.RawPolyline>()
        val lowResFarList = ArrayList<MetroMapOverlayLoader.RawPolyline>()

        val assetManager = context.assets
        val fileContent = try {
            assetManager.open("ruta_cercanias_valencia.geojson").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening ruta_cercanias_valencia.geojson", e)
            return MetroMapOverlayLoader.PolylineSets(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        val root = JSONObject(fileContent)
        val features = root.optJSONArray("features") ?: return MetroMapOverlayLoader.PolylineSets(
            emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList()
        )

        val epsilon = 0.0004

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val properties = feature.optJSONObject("properties") ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue

            val colourHex = properties.optString("colour", "#C1272D")
            val color = try {
                if (colourHex.startsWith("#")) Color.parseColor(colourHex) else Color.parseColor("#$colourHex")
            } catch (e: Exception) {
                try { Color.parseColor("#C1272D") } catch (ex: Exception) { Color.RED }
            }
            val lineRef = properties.optString("ref", "").trim()
            val strokeWidth = 9f

            val featureSegments = ArrayList<List<GeoPoint>>()
            val geomType = geometry.optString("type")

            if (geomType == "MultiLineString") {
                val coordsArray = geometry.optJSONArray("coordinates") ?: continue
                for (j in 0 until coordsArray.length()) {
                    val lineCoords = coordsArray.optJSONArray(j) ?: continue
                    val geoPoints = parseCoordinates(lineCoords)
                    if (geoPoints.size >= 2) {
                        featureSegments.add(geoPoints)
                    }
                }
            } else if (geomType == "LineString") {
                val coordsArray = geometry.optJSONArray("coordinates") ?: continue
                val geoPoints = parseCoordinates(coordsArray)
                if (geoPoints.size >= 2) {
                    featureSegments.add(geoPoints)
                }
            }

            val mergedSegments = mergeSegments(featureSegments)

            for (segment in mergedSegments) {
                val normalized = normalizeDirection(segment)
                if (normalized.size < 2) continue

                val highResPoly = MetroMapOverlayLoader.RawPolyline(normalized, color, strokeWidth, lineRef)
                highResCloseList.add(highResPoly)
                highResMediumList.add(highResPoly)
                highResFarList.add(highResPoly)

                val simplified = rdpSimplify(normalized, epsilon)
                val lowResPoly = MetroMapOverlayLoader.RawPolyline(simplified, color, strokeWidth, lineRef)
                lowResCloseList.add(lowResPoly)
                lowResMediumList.add(lowResPoly)
                lowResFarList.add(lowResPoly)
            }
        }

        return MetroMapOverlayLoader.PolylineSets(
            highResClose = highResCloseList,
            highResMedium = highResMediumList,
            highResFar = highResFarList,
            lowResClose = lowResCloseList,
            lowResMedium = lowResMediumList,
            lowResFar = lowResFarList
        )
    }

    private fun parseCoordinates(array: JSONArray): List<GeoPoint> {
        val list = ArrayList<GeoPoint>(array.length())
        for (i in 0 until array.length()) {
            val point = array.optJSONArray(i) ?: continue
            if (point.length() >= 2) {
                val lon = point.optDouble(0)
                val lat = point.optDouble(1)
                if (!lon.isNaN() && !lat.isNaN()) {
                    list.add(GeoPoint(lat, lon))
                }
            }
        }
        return list
    }

    private fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val latMid = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
        val dy = (p2.latitude - p1.latitude) * 111111.0
        val dx = (p2.longitude - p1.longitude) * 111111.0 * Math.cos(latMid)
        return Math.sqrt(dx * dx + dy * dy)
    }

    private fun mergeSegments(segments: List<List<GeoPoint>>): List<List<GeoPoint>> {
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

                    if (distLastFirst < 10.0) {
                        currentPath.addAll(s.subList(1, s.size))
                        pool.removeAt(i)
                        joinedAny = true
                    } else if (distLastLast < 10.0) {
                        currentPath.addAll(s.reversed().subList(1, s.size))
                        pool.removeAt(i)
                        joinedAny = true
                    } else if (distFirstLast < 10.0) {
                        currentPath.addAll(0, s.subList(0, s.size - 1))
                        pool.removeAt(i)
                        joinedAny = true
                    } else if (distFirstFirst < 10.0) {
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

    private fun normalizeDirection(points: List<GeoPoint>): List<GeoPoint> {
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

    private fun rdpSimplify(points: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
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

    private fun perpendicularDistance(p: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
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

    fun getLinePoints(lineCode: String): List<List<GeoPoint>> {
        val clean = lineCode.uppercase().replace("C-", "").replace("C_", "").replace("C", "").trim()
        val targetRef = when (clean) {
            "1", "10" -> "C1"
            "2", "20" -> "C2"
            "3", "30" -> "C3"
            "4", "40" -> "C4"
            "5", "50" -> "C5"
            "6", "60" -> "C6"
            else -> "C$clean"
        }
        val rawPolylines = polylineSets.highResClose.filter { 
            val refClean = it.lineRef.uppercase().replace("C-", "").replace("C_", "").replace("C", "").trim()
            it.lineRef.uppercase().trim() == targetRef || refClean == clean
        }
        return rawPolylines.map { it.points }
    }
}
