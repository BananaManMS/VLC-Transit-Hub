package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.example.data.repository.emt.EmtDataSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.milestones.MilestoneManager
import org.osmdroid.views.overlay.milestones.MilestoneMeterDistanceLister
import org.osmdroid.views.overlay.milestones.MilestonePathDisplayer
import java.io.File

/**
 * Loads and parses EMT Valencia route shapes from JSON (Encoded Polylines):
 * - Checks local storage (emt_shapes.json downloaded via EmtDataSyncManager)
 * - Falls back to assets shapes.json if local storage is absent on first launch
 *
 * Caches line geometries in memory for fast, on-demand polyline generation
 * when a bus stop or specific bus line is selected.
 */
object EmtMapOverlayLoader {
    private const val TAG = "EmtMapOverlayLoader"

    const val DEFAULT_EMT_COLOR = "#E52320"

    data class EmtRouteShape(
        val lineRef: String,
        val shapeId: String,
        val points: List<GeoPoint>,
        val headsign: String? = null
    )

    @Volatile
    private var isLoading: Boolean = false

    private val _isLoadedState = MutableStateFlow(false)
    val isLoadedState: StateFlow<Boolean> = _isLoadedState

    val isLoaded: Boolean
        get() = _isLoadedState.value

    private val pendingCallbacks = mutableListOf<() -> Unit>()

    // Normalized Line Name (e.g. "4", "C1", "19") -> List of shapes (outbound, inbound, variants)
    @Volatile
    private var lineToShapesMap: Map<String, List<EmtRouteShape>> = emptyMap()

    /**
     * Normalizes line references (e.g. " 4 " -> "4", "L4" -> "4", "c1" -> "C1").
     */
    fun normalizeLine(raw: String): String {
        return raw.trim()
            .removePrefix("L")
            .removePrefix("l")
            .trim()
            .uppercase()
    }

    /**
     * Forces a reload of EMT shapes when new data is downloaded.
     */
    fun reload(context: Context) {
        synchronized(this) {
            _isLoadedState.value = false
            lineToShapesMap = emptyMap()
        }
        ensureLoaded(context)
    }

    /**
     * Triggers asynchronous background parsing of routes and shapes if not already loaded.
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
                val parsedMap = withContext(Dispatchers.IO) {
                    parseEmtData(context.applicationContext)
                }
                val callbacksToRun = synchronized(EmtMapOverlayLoader) {
                    lineToShapesMap = parsedMap
                    _isLoadedState.value = true
                    isLoading = false
                    val callbacks = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    callbacks
                }
                Log.d(TAG, "Successfully loaded ${parsedMap.size} EMT bus lines shapes from JSON")
                withContext(Dispatchers.Main) {
                    callbacksToRun.forEach { it.invoke() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load EMT bus shapes", e)
                synchronized(EmtMapOverlayLoader) {
                    isLoading = false
                    pendingCallbacks.clear()
                }
            }
        }
    }

    private fun parseEmtData(context: Context): Map<String, List<EmtRouteShape>> {
        val jsonText = try {
            val localFile = EmtDataSyncManager.getLocalShapesFile(context)
            if (localFile.exists() && localFile.length() > 100) {
                localFile.readText(Charsets.UTF_8)
            } else {
                context.assets.open("shapes.json").bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EMT shapes JSON", e)
            return emptyMap()
        }

        val result = mutableMapOf<String, MutableList<EmtRouteShape>>()
        try {
            val rootObj = JSONObject(jsonText)
            val lineKeys = rootObj.keys()

            while (lineKeys.hasNext()) {
                val rawLine = lineKeys.next()
                val cleanLine = normalizeLine(rawLine)
                val lineObj = rootObj.optJSONObject(rawLine) ?: continue

                val shapeKeys = lineObj.keys()
                while (shapeKeys.hasNext()) {
                    val shapeId = shapeKeys.next()
                    val shapeData = lineObj.optJSONObject(shapeId) ?: continue
                    val headsign = shapeData.optString("headsign", "")
                    val polyStr = shapeData.optString("poly", "")

                    if (polyStr.isNotEmpty()) {
                        val points = decodePolyline(polyStr)
                        if (points.size >= 2) {
                            result.getOrPut(cleanLine) { mutableListOf() }.add(
                                EmtRouteShape(
                                    lineRef = cleanLine,
                                    shapeId = shapeId,
                                    points = points,
                                    headsign = headsign.ifBlank { null }
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EMT shapes JSON: ${e.message}", e)
        }

        return result
    }

    /**
     * Decodes a Google Encoded Polyline string into a List of GeoPoints.
     */
    fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val pLat = lat.toDouble() / 1E5
            val pLng = lng.toDouble() / 1E5
            poly.add(GeoPoint(pLat, pLng))
        }
        return poly
    }

    private data class MilestoneConfig(
        val strokeWidth: Float,
        val recurrenceMeters: Double,
        val arrowPath: Path?,
        val arrowStrokeWidth: Float
    )

    /**
     * Anchors directional chevrons in physical meters along the route so that they remain
     * perfectly pinned to geographic locations on the road without shifting or jumping during zoom/pan gestures.
     * Chevrons are large, bold, and high-contrast (bright white fill + EMT red border).
     */
    private fun getMilestoneConfig(zoom: Double): MilestoneConfig {
        return if (zoom < 13.0) {
            MilestoneConfig(
                strokeWidth = 7.0f,
                recurrenceMeters = 0.0,
                arrowPath = null,
                arrowStrokeWidth = 0f
            )
        } else {
            MilestoneConfig(
                strokeWidth = 8.5f,
                recurrenceMeters = 250.0,
                arrowPath = createStyledChevronPath(length = 18f, halfWidth = 11f, indent = 6.5f),
                arrowStrokeWidth = 2.8f
            )
        }
    }

    /**
     * Constructs a bold, high-contrast aerodynamic chevron pointing along +X (direction of travel).
     * Has a pure white interior fill and a thick EMT red stroke border for maximum legibility.
     */
    private fun createStyledChevronPath(length: Float, halfWidth: Float, indent: Float): Path {
        return Path().apply {
            moveTo(length * 0.5f, 0f)              // Tip (front)
            lineTo(-length * 0.5f, -halfWidth)     // Top rear wing
            lineTo(-length * 0.5f + indent, 0f)    // Inner middle notch
            lineTo(-length * 0.5f, halfWidth)      // Bottom rear wing
            close()
        }
    }

    /**
     * Identifies if a line is circular strictly by:
     * 1. Line code prefix 'C' (official EMT Valencia circular line designation: C1, C2, C3, and future C4, C5...) or 'G' / 'g'.
     * 2. Explicit keyword "circular" in headsign or description (excluding street/avenue names).
     */
    fun isCircularLine(lineCode: String, headsignOrDesc: String? = null): Boolean {
        val clean = normalizeLine(lineCode).uppercase()
        if (clean.startsWith("C") || clean.startsWith("G")) {
            return true
        }
        if (headsignOrDesc != null && headsignOrDesc.contains("circular", ignoreCase = true)) {
            return true
        }
        return false
    }

    private data class ShapeMatchResult(
        val shape: EmtRouteShape,
        val score: Double
    )

    private fun evaluateShapeForStop(
        shape: EmtRouteShape,
        selectedStopId: String?,
        stopLat: Double,
        stopLon: Double
    ): ShapeMatchResult {
        val points = shape.points
        if (points.isEmpty()) return ShapeMatchResult(shape, Double.MAX_VALUE)

        var minDistSq = Double.MAX_VALUE
        val cosLat = Math.cos(Math.toRadians(stopLat))

        // Compute high-precision perpendicular distance to polyline segments
        if (points.size == 1) {
            val dLat = (points[0].latitude - stopLat) * 111320.0
            val dLon = (points[0].longitude - stopLon) * 111320.0 * cosLat
            minDistSq = dLat * dLat + dLon * dLon
        } else {
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]

                val x1 = (p1.longitude - stopLon) * 111320.0 * cosLat
                val y1 = (p1.latitude - stopLat) * 111320.0
                val x2 = (p2.longitude - stopLon) * 111320.0 * cosLat
                val y2 = (p2.latitude - stopLat) * 111320.0

                val dx = x2 - x1
                val dy = y2 - y1
                val lenSq = dx * dx + dy * dy

                val segDistSq = if (lenSq <= 0.0001) {
                    x1 * x1 + y1 * y1
                } else {
                    val t = ((-x1 * dx) + (-y1 * dy)) / lenSq
                    val clampedT = t.coerceIn(0.0, 1.0)
                    val projX = x1 + clampedT * dx
                    val projY = y1 + clampedT * dy
                    projX * projX + projY * projY
                }

                if (segDistSq < minDistSq) {
                    minDistSq = segDistSq
                }
            }
        }

        val minDistMeters = Math.sqrt(minDistSq)

        // Parse origin and destination stop IDs from shapeId (format: variant_origin_destination)
        val parts = shape.shapeId.split("_")
        val originStop = if (parts.size >= 3) parts[1] else null
        val destStop = if (parts.size >= 3) parts[2] else null

        val isOriginMatch = selectedStopId != null && originStop == selectedStopId
        val isDestMatch = selectedStopId != null && destStop == selectedStopId

        var score = minDistMeters

        if (isOriginMatch) {
            // Priority at terminus/cabecera: prioritize the onward departing service starting here
            score -= 10000.0
        } else if (isDestMatch) {
            // At terminus/cabecera: deprioritize the arriving journey that finishes here
            score += 10000.0
        } else if (selectedStopId == null) {
            // Fallback for coordinate-only selection at terminal endpoints
            val firstPt = points.first()
            val lastPt = points.last()
            val dFirst = Math.hypot((firstPt.latitude - stopLat) * 111320.0, (firstPt.longitude - stopLon) * 111320.0 * cosLat)
            val dLast = Math.hypot((lastPt.latitude - stopLat) * 111320.0, (lastPt.longitude - stopLon) * 111320.0 * cosLat)
            if (dFirst < 30.0) {
                score -= 5000.0
            } else if (dLast < 30.0) {
                score += 5000.0
            }
        }

        // For all regular intermediate stops (including the final stops before the terminus),
        // each stop strictly belongs to its active route direction without arbitrary progress penalties.
        return ShapeMatchResult(shape, score)
    }

    /**
     * Prunes variant shapes for a line based on headsigns or selected stop direction.
     * If a stop position is provided:
     * - For linear lines: shows only the departing directional shape that serves that stop onward.
     * - For circular lines: groups the segments of that circular variant to close the loop completely.
     */
    fun pruneShapesForLine(
        shapes: List<EmtRouteShape>,
        targetHeadsign: String? = null,
        lineCode: String? = null,
        selectedStopId: String? = null,
        selectedStopLat: Double? = null,
        selectedStopLon: Double? = null
    ): List<EmtRouteShape> {
        if (shapes.isEmpty()) return emptyList()

        if (!targetHeadsign.isNullOrBlank()) {
            val matching = shapes.filter {
                it.headsign != null && it.headsign.contains(targetHeadsign, ignoreCase = true)
            }
            if (matching.isNotEmpty()) return matching
        }

        // When a stop is selected, filter by the direction of that stop (prioritizing departing journey)
        if (selectedStopLat != null && selectedStopLon != null && shapes.size > 1) {
            val bestShape = shapes.map { shape ->
                evaluateShapeForStop(shape, selectedStopId, selectedStopLat, selectedStopLon)
            }.minByOrNull { it.score }?.shape

            if (bestShape != null) {
                val isCircular = isCircularLine(lineCode ?: bestShape.lineRef, bestShape.headsign)
                return if (isCircular) {
                    // For circular lines: close the full circular loop for this direction.
                    // EMT shapes in shapes.json use format "<variantId>_<origin>_<dest>".
                    // The complementary segments of the circular loop share the same variantId prefix.
                    val variantPrefix = bestShape.shapeId.substringBefore("_")
                    val matchingVariant = shapes.filter { it.shapeId.substringBefore("_") == variantPrefix }
                    if (matchingVariant.isNotEmpty()) matchingVariant else listOf(bestShape)
                } else {
                    // For linear lines: show ONLY the departing direction matching that stop
                    listOf(bestShape)
                }
            }
        }

        // Group shapes by headsign (or fallback to shapeId if headsign is null)
        val grouped = shapes.groupBy { it.headsign ?: it.shapeId }
        
        // Pick the shape with the most points for each headsign direction (main ida & main vuelta)
        return grouped.mapValues { (_, shapeGroup) ->
            shapeGroup.maxByOrNull { it.points.size } ?: shapeGroup.first()
        }.values.toList()
    }

    /**
     * Builds Osmdroid Polyline overlays for the requested lines.
     * Uses official EMT Red with directional arrows adapted to the current zoom level when showArrows is true.
     * When showArrows is false (e.g. multiple lines active in "Todas"), polylines are rendered
     * as clean paths without chevrons to prevent overlapping clutter.
     */
    fun createPolylinesForLines(
        mapView: MapView,
        lines: Set<String>,
        strokeWidth: Float? = null,
        currentZoom: Double = 16.0,
        showArrows: Boolean = lines.size == 1,
        targetHeadsign: String? = null,
        selectedStopId: String? = null,
        selectedStopLat: Double? = null,
        selectedStopLon: Double? = null
    ): List<Polyline> {
        if (lines.isEmpty()) return emptyList()

        val polylines = mutableListOf<Polyline>()
        val sortedLines = lines.sortedWith(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })

        val config = getMilestoneConfig(currentZoom)
        val finalStrokeWidth = strokeWidth ?: config.strokeWidth

        sortedLines.forEachIndexed { index, rawLine ->
            val cleanLine = normalizeLine(rawLine)
            val rawShapes = lineToShapesMap[cleanLine] ?: return@forEachIndexed
            val shapes = pruneShapesForLine(
                shapes = rawShapes,
                targetHeadsign = targetHeadsign,
                lineCode = cleanLine,
                selectedStopId = selectedStopId,
                selectedStopLat = selectedStopLat,
                selectedStopLon = selectedStopLon
            )

            val colorInt = Color.parseColor(DEFAULT_EMT_COLOR)

            for (shape in shapes) {
                val polyline = Polyline(mapView).apply {
                    setPoints(shape.points)
                    outlinePaint.color = colorInt
                    outlinePaint.strokeWidth = finalStrokeWidth
                    outlinePaint.isAntiAlias = true
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    infoWindow = null
                    relatedObject = shape
                    setOnClickListener { _, _, _ -> true }

                    if (showArrows && config.arrowPath != null && config.recurrenceMeters > 0.0) {
                        // 1. High-contrast white fill inside the chevron
                        val fillPaint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        // 2. Integrated EMT Red stroke border matching the route line
                        val borderPaint = Paint().apply {
                            color = colorInt
                            style = Paint.Style.STROKE
                            this.strokeWidth = config.arrowStrokeWidth
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        val lister = MilestoneMeterDistanceLister(config.recurrenceMeters)
                        val fillDisplayer = MilestonePathDisplayer(0.0, true, config.arrowPath, fillPaint)
                        val borderDisplayer = MilestonePathDisplayer(0.0, true, config.arrowPath, borderPaint)

                        setMilestoneManagers(listOf(
                            MilestoneManager(lister, fillDisplayer),
                            MilestoneManager(lister, borderDisplayer)
                        ))
                    }
                }
                polylines.add(polyline)
            }
        }

        return polylines
    }
}
