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

    data class PolylineSets(
        val highResClose: List<Polyline>,
        val highResMedium: List<Polyline>,
        val highResFar: List<Polyline>,
        val lowResClose: List<Polyline>,
        val lowResMedium: List<Polyline>,
        val lowResFar: List<Polyline>
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

    @Volatile
    private var cercaniasPolylineSets: PolylineSets = PolylineSets(
        highResClose = emptyList(),
        highResMedium = emptyList(),
        highResFar = emptyList(),
        lowResClose = emptyList(),
        lowResMedium = emptyList(),
        lowResFar = emptyList()
    )

    fun getLoadedPolylines(): List<Polyline> {
        return if (useHighRes) {
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
    }

    fun getLoadedCercaniasPolylines(): List<Polyline> {
        return if (useHighRes) {
            when (zoomCategory) {
                ZoomCategory.CLOSE -> cercaniasPolylineSets.highResClose
                ZoomCategory.MEDIUM -> cercaniasPolylineSets.highResMedium
                ZoomCategory.FAR -> cercaniasPolylineSets.highResFar
            }
        } else {
            when (zoomCategory) {
                ZoomCategory.CLOSE -> cercaniasPolylineSets.lowResClose
                ZoomCategory.MEDIUM -> cercaniasPolylineSets.lowResMedium
                ZoomCategory.FAR -> cercaniasPolylineSets.lowResFar
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

    fun loadMetroLines(context: Context, scope: CoroutineScope, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val sets = withContext(Dispatchers.IO) {
                    parseGeoJsonAndGeneratePolylines(context)
                }
                polylineSets = sets
                Log.d(TAG, "Successfully loaded precomputed polyline sets (High-Res and Low-Res for Far/Medium/Close)")
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load metro lines GeoJSON", e)
            }
        }
    }

    fun loadCercaniasLines(context: Context, scope: CoroutineScope, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val sets = withContext(Dispatchers.IO) {
                    parseCercaniasGeoJsonAndGeneratePolylines(context)
                }
                cercaniasPolylineSets = sets
                Log.d(TAG, "Successfully loaded precomputed Cercanias polyline sets")
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cercanias lines GeoJSON", e)
            }
        }
    }

    private fun parseCercaniasGeoJsonAndGeneratePolylines(context: Context): PolylineSets {
        val highResCloseList = ArrayList<Polyline>()
        val highResMediumList = ArrayList<Polyline>()
        val highResFarList = ArrayList<Polyline>()

        val lowResCloseList = ArrayList<Polyline>()
        val lowResMediumList = ArrayList<Polyline>()
        val lowResFarList = ArrayList<Polyline>()

        val assetManager = context.assets
        val fileContent = try {
            assetManager.open("ruta_cercanias_valencia.geojson").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening ruta_cercanias_valencia.geojson", e)
            return PolylineSets(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        val root = JSONObject(fileContent)
        val features = root.optJSONArray("features") ?: return PolylineSets(
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
                if (colourHex.startsWith("#")) {
                    Color.parseColor(colourHex)
                } else {
                    Color.parseColor("#$colourHex")
                }
            } catch (e: Exception) {
                try {
                    Color.parseColor("#C1272D")
                } catch (ex: Exception) {
                    Color.RED
                }
            }
            val strokeWidth = 9f

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

            val mergedSegments = mergeSegments(featureSegments)

            for (segment in mergedSegments) {
                val normalized = normalizeDirection(segment)
                if (normalized.size < 2) continue

                val pointsClose = normalized
                val pointsMedium = normalized
                val pointsFar = normalized

                highResCloseList.add(createPolyline(pointsClose, color, strokeWidth))
                highResMediumList.add(createPolyline(pointsMedium, color, strokeWidth))
                highResFarList.add(createPolyline(pointsFar, color, strokeWidth))

                val simplifiedClose = rdpSimplify(pointsClose, epsilon)
                val simplifiedMedium = rdpSimplify(pointsMedium, epsilon)
                val simplifiedFar = rdpSimplify(pointsFar, epsilon)

                lowResCloseList.add(createPolyline(simplifiedClose, color, strokeWidth))
                lowResMediumList.add(createPolyline(simplifiedMedium, color, strokeWidth))
                lowResFarList.add(createPolyline(simplifiedFar, color, strokeWidth))
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

    private data class ParsedRoute(
        val ref: String,
        val color: Int,
        val strokeWidthClose: Float,
        val strokeWidthMedium: Float,
        val strokeWidthFar: Float,
        val isMetro: Boolean,
        val segments: List<List<GeoPoint>>
    )

    private class RouteGroup(
        val ref: String,
        val color: Int,
        val strokeWidthClose: Float,
        val strokeWidthMedium: Float,
        val strokeWidthFar: Float,
        val isMetro: Boolean
    ) {
        val rawSegments = ArrayList<List<GeoPoint>>()
    }

    private fun distancePointToSegment(p: GeoPoint, s1: GeoPoint, s2: GeoPoint): Double {
        val latMid = Math.toRadians((s1.latitude + s2.latitude) / 2.0)
        val x2 = (s2.longitude - s1.longitude) * 111111.0 * Math.cos(latMid)
        val y2 = (s2.latitude - s1.latitude) * 111111.0
        val xp = (p.longitude - s1.longitude) * 111111.0 * Math.cos(latMid)
        val yp = (p.latitude - s1.latitude) * 111111.0

        val segmentLenSq = x2 * x2 + y2 * y2
        if (segmentLenSq < 1e-9) {
            return Math.sqrt(xp * xp + yp * yp)
        }

        val t = ((xp * x2) + (yp * y2)) / segmentLenSq
        val tClamped = Math.max(0.0, Math.min(1.0, t))

        val closestX = tClamped * x2
        val closestY = tClamped * y2

        val dx = xp - closestX
        val dy = yp - closestY
        return Math.sqrt(dx * dx + dy * dy)
    }

    private fun isRouteCloseToPoint(point: GeoPoint, route: ParsedRoute, maxDistance: Double): Boolean {
        for (segment in route.segments) {
            if (segment.isEmpty()) continue
            if (segment.size == 1) {
                if (distanceBetween(point, segment[0]) < maxDistance) {
                    return true
                }
                continue
            }
            for (i in 0 until segment.size - 1) {
                val s1 = segment[i]
                val s2 = segment[i + 1]

                // Fast bounding box buffer (~111 meters) to filter out distant segments instantly
                val minLat = Math.min(s1.latitude, s2.latitude) - 0.001
                val maxLat = Math.max(s1.latitude, s2.latitude) + 0.001
                val minLon = Math.min(s1.longitude, s2.longitude) - 0.001
                val maxLon = Math.max(s1.longitude, s2.longitude) + 0.001

                if (point.latitude < minLat || point.latitude > maxLat ||
                    point.longitude < minLon || point.longitude > maxLon) {
                    continue
                }

                if (distancePointToSegment(point, s1, s2) < maxDistance) {
                    return true
                }
            }
        }
        return false
    }

    private fun smoothOffsets(rawOffsets: DoubleArray): DoubleArray {
        val n = rawOffsets.size
        val smoothed = DoubleArray(n)
        val radius = 3 // 7-point symmetric window for beautiful ramp/smooth transition
        for (i in 0 until n) {
            var sum = 0.0
            var count = 0
            val start = Math.max(0, i - radius)
            val end = Math.min(n - 1, i + radius)
            for (k in start..end) {
                sum += rawOffsets[k]
                count++
            }
            smoothed[i] = sum / count
        }
        return smoothed
    }

    private fun scaleOffsets(offsets: DoubleArray, factor: Double): DoubleArray {
        val result = DoubleArray(offsets.size)
        for (i in offsets.indices) {
            result[i] = offsets[i] * factor
        }
        return result
    }

    private fun parseGeoJsonAndGeneratePolylines(context: Context): PolylineSets {
        val highResCloseList = ArrayList<Polyline>()
        val highResMediumList = ArrayList<Polyline>()
        val highResFarList = ArrayList<Polyline>()

        val lowResCloseList = ArrayList<Polyline>()
        val lowResMediumList = ArrayList<Polyline>()
        val lowResFarList = ArrayList<Polyline>()

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

                highResCloseList.add(createPolyline(pointsClose, route.color, route.strokeWidthClose))
                highResMediumList.add(createPolyline(pointsMedium, route.color, route.strokeWidthMedium))
                highResFarList.add(createPolyline(pointsFar, route.color, route.strokeWidthFar))

                // Perform Ramer-Douglas-Peucker simplification for Low-Res lists
                val simplifiedClose = rdpSimplify(pointsClose, epsilon)
                val simplifiedMedium = rdpSimplify(pointsMedium, epsilon)
                val simplifiedFar = rdpSimplify(pointsFar, epsilon)

                lowResCloseList.add(createPolyline(simplifiedClose, route.color, route.strokeWidthClose))
                lowResMediumList.add(createPolyline(simplifiedMedium, route.color, route.strokeWidthMedium))
                lowResFarList.add(createPolyline(simplifiedFar, route.color, route.strokeWidthFar))
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

    private fun createPolyline(points: List<GeoPoint>, color: Int, strokeWidth: Float): Polyline {
        return Polyline().apply {
            setPoints(points)
            setColor(color)
            width = strokeWidth
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            infoWindow = null
            setOnClickListener { _, _, _ -> true }
        }
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

    private fun offsetPointsWithArray(points: List<GeoPoint>, offsets: DoubleArray): List<GeoPoint> {
        if (points.size < 2) return points

        val result = ArrayList<GeoPoint>(points.size)
        val n = points.size

        // Precompute segment normals and directions
        val normals = ArrayList<Point2D>(n - 1)
        val segmentDX = ArrayList<Double>(n - 1)
        val segmentDY = ArrayList<Double>(n - 1)

        for (i in 0 until n - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val latMid = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
            val dx = (p2.longitude - p1.longitude) * 111111.0 * Math.cos(latMid)
            val dy = (p2.latitude - p1.latitude) * 111111.0
            segmentDX.add(dx)
            segmentDY.add(dy)
            val len = Math.sqrt(dx * dx + dy * dy)
            if (len > 1e-9) {
                // Perpendicular normal pointing right relative to line direction (East/South side)
                normals.add(Point2D(dy / len, -dx / len))
            } else {
                normals.add(Point2D(0.0, 0.0))
            }
        }

        for (i in 0 until n) {
            val curr = points[i]
            val miter: Point2D

            if (i == 0) {
                miter = normals[0]
            } else if (i == n - 1) {
                miter = normals[n - 2]
            } else {
                val n1 = normals[i - 1]
                val n2 = normals[i]

                // Bisector normal (average)
                val mx = n1.x + n2.x
                val my = n1.y + n2.y
                val mLen = Math.sqrt(mx * mx + my * my)

                if (mLen > 1e-9) {
                    val bx = mx / mLen
                    val by = my / mLen

                    // Scale adjustment to maintain a constant perpendicular offset width
                    // Límite máximo de bisectriz 2.0x (Rule 6)
                    val cosHalfAngle = bx * n1.x + by * n1.y
                    val scale = if (cosHalfAngle > 0.1) {
                        Math.min(1.0 / cosHalfAngle, 2.0)
                    } else {
                        2.0
                    }
                    miter = Point2D(bx * scale, by * scale)
                } else {
                    miter = n1
                }
            }

            // Project offset in lat/lon space using the specific smoothed offset for this vertex
            val offsetMeters = offsets[i]
            val shiftLat = (offsetMeters * miter.y) / 111111.0
            val shiftLon = (offsetMeters * miter.x) / (111111.0 * Math.cos(Math.toRadians(curr.latitude)))

            result.add(GeoPoint(curr.latitude + shiftLat, curr.longitude + shiftLon))
        }

        return result
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

    private fun isPointCloseToSegment(point: GeoPoint, segment: List<GeoPoint>, maxDistance: Double): Boolean {
        if (segment.isEmpty()) return false
        if (segment.size == 1) {
            return distanceBetween(point, segment[0]) < maxDistance
        }
        for (i in 0 until segment.size - 1) {
            val s1 = segment[i]
            val s2 = segment[i + 1]

            val minLat = Math.min(s1.latitude, s2.latitude) - 0.001
            val maxLat = Math.max(s1.latitude, s2.latitude) + 0.001
            val minLon = Math.min(s1.longitude, s2.longitude) - 0.001
            val maxLon = Math.max(s1.longitude, s2.longitude) + 0.001

            if (point.latitude < minLat || point.latitude > maxLat ||
                point.longitude < minLon || point.longitude > maxLon) {
                continue
            }

            if (distancePointToSegment(point, s1, s2) < maxDistance) {
                return true
            }
        }
        return false
    }

    private fun isSegmentRedundant(segment: List<GeoPoint>, existingSegments: List<List<GeoPoint>>): Boolean {
        if (segment.isEmpty()) return true
        var closePoints = 0
        for (p in segment) {
            var isClose = false
            for (existing in existingSegments) {
                if (isPointCloseToSegment(p, existing, 15.0)) {
                    isClose = true
                    break
                }
            }
            if (isClose) {
                closePoints++
            }
        }
        return (closePoints.toDouble() / segment.size) > 0.85
    }

    private data class Point2D(val x: Double, val y: Double)
}
