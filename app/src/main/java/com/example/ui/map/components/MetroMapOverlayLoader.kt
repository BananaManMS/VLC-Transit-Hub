package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

object MetroMapOverlayLoader {
    private const val TAG = "MetroMapOverlayLoader"

    enum class ZoomCategory {
        FAR,       // Zoom < 13.5
        MEDIUM,    // 13.5 <= Zoom < 16.0
        CLOSE      // Zoom >= 16.0
    }

    data class RawPolyline(
        val points: List<GeoPoint>,
        val color: Int,
        val strokeWidth: Float,
        val lineRef: String = ""
    )

    data class PolylineSets(
        val highResClose: List<RawPolyline>,
        val highResMedium: List<RawPolyline>,
        val highResFar: List<RawPolyline>,
        val lowResClose: List<RawPolyline>,
        val lowResMedium: List<RawPolyline>,
        val lowResFar: List<RawPolyline>
    )

    @Volatile
    private var zoomCategory: ZoomCategory = ZoomCategory.MEDIUM

    @Volatile
    private var useHighRes: Boolean = true

    @Volatile
    private var polylineSets: PolylineSets = PolylineSets(
        highResClose = emptyList(),
        highResMedium = emptyList(),
        highResFar = emptyList(),
        lowResClose = emptyList(),
        lowResMedium = emptyList(),
        lowResFar = emptyList()
    )

    private var cachedMapViewRef: java.lang.ref.WeakReference<org.osmdroid.views.MapView>? = null
    private var cachedZoomCategory: ZoomCategory? = null
    private var cachedUseHighRes: Boolean? = null
    private var cachedMetroPolylines: List<Polyline> = emptyList()

    @Volatile
    private var isLoading: Boolean = false

    private val _isLoadedState = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isLoadedState: kotlinx.coroutines.flow.StateFlow<Boolean> = _isLoadedState

    val isLoaded: Boolean
        get() = _isLoadedState.value

    private val pendingCallbacks = mutableListOf<() -> Unit>()

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
                    parseGeoJsonAndGeneratePolylines(context)
                }
                val callbacksToRun = synchronized(MetroMapOverlayLoader) {
                    polylineSets = sets
                    _isLoadedState.value = true
                    isLoading = false
                    cachedMapViewRef = null // Invalidate cache so new polylines generate
                    val callbacks = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    callbacks
                }
                Log.d(TAG, "Successfully loaded precomputed polyline sets (High-Res and Low-Res for Far/Medium/Close)")
                withContext(Dispatchers.Main) {
                    callbacksToRun.forEach { it.invoke() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load metro lines GeoJSON", e)
                synchronized(MetroMapOverlayLoader) {
                    isLoading = false
                    pendingCallbacks.clear()
                }
            }
        }
    }

    @Synchronized
    fun getLoadedPolylines(mapView: org.osmdroid.views.MapView? = null): List<Polyline> {
        if (mapView != null) {
            if (!isLoaded && !isLoading) {
                ensureLoaded(mapView.context)
            }
            checkAndRebuildPolylineCache(mapView)
        }
        return cachedMetroPolylines
    }

    fun loadMetroLines(context: Context, scope: CoroutineScope, onComplete: (() -> Unit)? = null) {
        ensureLoaded(context, scope, onComplete)
    }

    @Synchronized
    fun getLoadedCercaniasPolylines(mapView: org.osmdroid.views.MapView? = null): List<Polyline> {
        return CercaniasMapOverlayLoader.getLoadedPolylines(
            mapView = mapView,
            zoomCategory = zoomCategory,
            useHighRes = useHighRes
        )
    }

    private fun checkAndRebuildPolylineCache(mapView: org.osmdroid.views.MapView) {
        if (cachedMapViewRef?.get() != mapView || cachedZoomCategory != zoomCategory || cachedUseHighRes != useHighRes) {
            cachedMapViewRef = java.lang.ref.WeakReference(mapView)
            cachedZoomCategory = zoomCategory
            cachedUseHighRes = useHighRes

            val currentMetroRaw = if (useHighRes) {
                when (zoomCategory) {
                    ZoomCategory.CLOSE -> polylineSets.highResClose
                    ZoomCategory.MEDIUM -> polylineSets.highResMedium
                    ZoomCategory.FAR -> polylineSets.highResFar
                }
            } else {
                when (zoomCategory) {
                    ZoomCategory.CLOSE -> polylineSets.lowResClose
                    ZoomCategory.MEDIUM -> polylineSets.lowResMedium
                    ZoomCategory.FAR -> polylineSets.lowResFar
                }
            }

            cachedMetroPolylines = currentMetroRaw.map { raw ->
                SafePolyline(mapView).apply {
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

    fun isUseHighRes(): Boolean = useHighRes

    fun setUseHighRes(highRes: Boolean) {
        useHighRes = highRes
    }

    fun getZoomCategory(): ZoomCategory = zoomCategory

    fun setZoomCategory(category: ZoomCategory) {
        zoomCategory = category
    }

    fun getZoomCategoryForLevel(zoom: Double): ZoomCategory {
        return when {
            zoom < 13.5 -> ZoomCategory.FAR
            zoom < 16.0 -> ZoomCategory.MEDIUM
            else -> ZoomCategory.CLOSE
        }
    }

    fun loadCercaniasLines(context: Context, scope: CoroutineScope, onComplete: (() -> Unit)? = null) {
        CercaniasMapOverlayLoader.ensureLoaded(context, scope, onComplete)
    }

    private data class ParsedRoute(
        val ref: String,
        val color: Int,
        val strokeWidthClose: Float,
        val strokeWidthMedium: Float,
        val strokeWidthFar: Float,
        val isMetro: Boolean,
        val segments: List<List<GeoPoint>>
    )

    private fun distancePointToSegment(p: GeoPoint, s1: GeoPoint, s2: GeoPoint): Double =
        MapPolylineOffsetHelper.distancePointToSegment(p, s1, s2)

    private fun isRouteCloseToPoint(point: GeoPoint, route: ParsedRoute, maxDistance: Double): Boolean =
        MapPolylineOffsetHelper.isRouteCloseToPoint(point, route.segments, maxDistance)

    private fun smoothOffsets(rawOffsets: DoubleArray): DoubleArray =
        MapPolylineOffsetHelper.smoothOffsets(rawOffsets)

    private fun scaleOffsets(offsets: DoubleArray, factor: Double): DoubleArray =
        MapPolylineOffsetHelper.scaleOffsets(offsets, factor)

    private fun parseGeoJsonAndGeneratePolylines(context: Context): PolylineSets {
        val highResCloseList = ArrayList<RawPolyline>()
        val highResMediumList = ArrayList<RawPolyline>()
        val highResFarList = ArrayList<RawPolyline>()

        val lowResCloseList = ArrayList<RawPolyline>()
        val lowResMediumList = ArrayList<RawPolyline>()
        val lowResFarList = ArrayList<RawPolyline>()

        val assetManager = context.assets
        val filesToLoad = listOf("ruta_metrovalencia_2.geojson", "linea_4.geojson", "linea_6.geojson")

        // Epsilon threshold in degrees for simplification (approx 40 meters)
        val epsilon = 0.0004

        val metroRoutes = ArrayList<ParsedRoute>()
        val tramRoutes = ArrayList<ParsedRoute>()

        for (fileName in filesToLoad) {
            val fileContent = try {
                assetManager.open(fileName).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                continue
            }
            val root = JSONObject(fileContent)
            val features = root.optJSONArray("features") ?: continue

            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val properties = feature.optJSONObject("properties") ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue

                val ref = properties.optString("ref", "")
                val colourHex = properties.optString("colour", "#9E9E9E")
                val routeType = properties.optString("route", "subway")

                val color = try {
                    Color.parseColor(colourHex)
                } catch (e: Exception) {
                    Color.GRAY
                }
                val strokeWidthClose = if (routeType == "tram") 7f else 10f
                val strokeWidthMedium = if (routeType == "tram") 5f else 7f
                val strokeWidthFar = if (routeType == "tram") 3.5f else 5f

                val featureSegments = ArrayList<List<GeoPoint>>()
                val geomType = geometry.optString("type")

                if (geomType == "MultiLineString") {
                    val coordsArray = geometry.optJSONArray("coordinates") ?: continue
                    for (j in 0 until coordsArray.length()) {
                        val lineCoords = coordsArray.optJSONArray(j) ?: continue
                        val geoPoints = parseCoordinatesArray(lineCoords)
                        if (geoPoints.size >= 2) {
                            featureSegments.add(geoPoints)
                        }
                    }
                } else if (geomType == "LineString") {
                    val coordsArray = geometry.optJSONArray("coordinates") ?: continue
                    val geoPoints = parseCoordinatesArray(coordsArray)
                    if (geoPoints.size >= 2) {
                        featureSegments.add(geoPoints)
                    }
                }

                // Merge segments inside the same feature
                val mergedSegments = mergeSegments(featureSegments)
                val finalizedSegments = mergedSegments.map { normalizeDirection(it) }.filter { it.size >= 2 }

                val isMetro = (routeType != "tram") && (ref == "1" || ref == "2" || ref == "3" || ref == "5" || ref == "7" || ref == "9")

                val route = ParsedRoute(
                    ref = ref,
                    color = color,
                    strokeWidthClose = strokeWidthClose,
                    strokeWidthMedium = strokeWidthMedium,
                    strokeWidthFar = strokeWidthFar,
                    isMetro = isMetro,
                    segments = finalizedSegments
                )

                if (isMetro) {
                    metroRoutes.add(route)
                } else {
                    tramRoutes.add(route)
                }
            }
        }

        // Process all routes dynamically with centered dynamic offsets
        val allRoutes = metroRoutes + tramRoutes

        for (route in allRoutes) {
            val masterOrder = if (route.isMetro) {
                listOf("3", "2", "9", "1", "5", "7")
            } else {
                listOf("4", "6", "8", "10")
            }
            val spacing = if (route.isMetro) 3.0 else 2.0

            for (segment in route.segments) {
                val n = segment.size
                if (n < 2) continue

                val rawOffsets = DoubleArray(n)
                for (v in 0 until n) {
                    val p = segment[v]
                    val presentRefs = ArrayList<String>()
                    presentRefs.add(route.ref)

                    for (otherRoute in allRoutes) {
                        // Coexistence requires a different line reference within 30m (Rule 2)
                        if (otherRoute.ref != route.ref && isRouteCloseToPoint(p, otherRoute, 30.0)) {
                            if (!presentRefs.contains(otherRoute.ref)) {
                                presentRefs.add(otherRoute.ref)
                            }
                        }
                    }

                    // If no other different route is coexisting at this vertex, offset is strictly 0.0m (Rule 2)
                    if (presentRefs.size == 1) {
                        rawOffsets[v] = 0.0
                    } else {
                        val sortedPresent = masterOrder.filter { presentRefs.contains(it) }
                        val j = sortedPresent.indexOf(route.ref)
                        val N = sortedPresent.size
                        val rawOffset = if (j >= 0) {
                            (j - (N - 1) / 2.0) * spacing
                        } else {
                            0.0
                        }
                        rawOffsets[v] = rawOffset
                    }
                }

                // Identify split points before ramping
                val splitIndices = ArrayList<Int>()
                for (i in 0 until n - 1) {
                    if (rawOffsets[i] != 0.0 && rawOffsets[i + 1] == 0.0) {
                        splitIndices.add(i)
                    }
                }
                for (i in splitIndices) {
                    val valFrom = rawOffsets[i]
                    if (i + 1 < n) rawOffsets[i + 1] = valFrom * 0.75
                    if (i + 2 < n) rawOffsets[i + 2] = valFrom * 0.50
                    if (i + 3 < n) rawOffsets[i + 3] = valFrom * 0.25
                }

                // Identify join points before ramping
                val joinIndices = ArrayList<Int>()
                for (i in n - 1 downTo 1) {
                    if (rawOffsets[i] != 0.0 && rawOffsets[i - 1] == 0.0) {
                        joinIndices.add(i)
                    }
                }
                for (i in joinIndices) {
                    val valTo = rawOffsets[i]
                    if (i - 1 >= 0) rawOffsets[i - 1] = valTo * 0.75
                    if (i - 2 >= 0) rawOffsets[i - 2] = valTo * 0.50
                    if (i - 3 >= 0) rawOffsets[i - 3] = valTo * 0.25
                }

                val smoothedOffsets = smoothOffsets(rawOffsets)

                // 1. Zoom >= 16.0: Factor 1.0x
                val pointsClose = offsetPointsWithArray(segment, smoothedOffsets)
                // 2. Zoom 13.5 to 15.9: Factor 5.0x
                val pointsMedium = offsetPointsWithArray(segment, scaleOffsets(smoothedOffsets, 5.0))
                // 3. Zoom < 13.5: Factor 0.0x (collapses to center)
                val pointsFar = segment

                highResCloseList.add(createPolyline(pointsClose, route.color, route.strokeWidthClose, route.ref))
                highResMediumList.add(createPolyline(pointsMedium, route.color, route.strokeWidthMedium, route.ref))
                highResFarList.add(createPolyline(pointsFar, route.color, route.strokeWidthFar, route.ref))

                // Perform Ramer-Douglas-Peucker simplification for Low-Res lists
                val simplifiedClose = rdpSimplify(pointsClose, epsilon)
                val simplifiedMedium = rdpSimplify(pointsMedium, epsilon)
                val simplifiedFar = rdpSimplify(pointsFar, epsilon)

                lowResCloseList.add(createPolyline(simplifiedClose, route.color, route.strokeWidthClose, route.ref))
                lowResMediumList.add(createPolyline(simplifiedMedium, route.color, route.strokeWidthMedium, route.ref))
                lowResFarList.add(createPolyline(simplifiedFar, route.color, route.strokeWidthFar, route.ref))
            }
        }

        return PolylineSets(
            highResClose = highResCloseList,
            highResMedium = highResMediumList,
            highResFar = highResFarList,
            lowResClose = lowResCloseList,
            lowResMedium = lowResMediumList,
            lowResFar = lowResFarList
        )
    }

    private fun createPolyline(points: List<GeoPoint>, color: Int, strokeWidth: Float, lineRef: String = ""): RawPolyline {
        return RawPolyline(points, color, strokeWidth, lineRef)
    }

    private fun parseCoordinatesArray(array: org.json.JSONArray): List<GeoPoint> {
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

    private fun offsetPointsWithArray(points: List<GeoPoint>, offsets: DoubleArray): List<GeoPoint> =
        MapPolylineOffsetHelper.offsetPointsWithArray(points, offsets)

    private fun rdpSimplify(points: List<GeoPoint>, epsilon: Double): List<GeoPoint> =
        MapPolylineOffsetHelper.rdpSimplify(points, epsilon)
}
