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
    fun isCircularLine(lineCode: String, headsign: String? = null): Boolean {
        val clean = lineCode.trim().uppercase()
        if (clean == "C1" || clean == "C2" || clean == "C3" || clean == "C4" || clean == "LC1" || clean == "LC2" || clean == "LC3" || clean == "79" || clean == "80") return true
        if (clean.startsWith("G")) return true
        if (headsign != null && headsign.contains("circular", ignoreCase = true)) return true
        return false
    }

    fun setShapesForTesting(shapes: Map<String, List<EmtRouteShape>>) {
        lineToShapesMap = shapes
        _isLoadedState.value = true
    }

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

    const val FIXED_ARROW_INTERVAL_METERS = 500.0

    private data class MilestoneConfig(
        val strokeWidth: Float,
        val arrowPath: Path?,
        val arrowStrokeWidth: Float
    )

    /**
     * Determines directional chevron appearance based on zoom level.
     * Below zoom 14.0 (city overview), chevrons are omitted to keep the map clean.
     * At zoom >= 14.0, directional chevrons are anchored at a fixed geographical distance of 500m
     * along the polyline path so they do not shift or jump when zooming.
     */
    private fun getMilestoneConfig(zoom: Double): MilestoneConfig {
        return when {
            zoom < 14.0 -> {
                MilestoneConfig(
                    strokeWidth = 6.5f,
                    arrowPath = null,
                    arrowStrokeWidth = 0f
                )
            }
            zoom < 15.5 -> {
                // Zoom medio: chevrons estilizados cada 500m
                MilestoneConfig(
                    strokeWidth = 7.5f,
                    arrowPath = createStyledChevronPath(length = 14f, halfWidth = 8f, indent = 5f),
                    arrowStrokeWidth = 2.2f
                )
            }
            zoom < 17.0 -> {
                // Zoom estándar de detalle cada 500m
                MilestoneConfig(
                    strokeWidth = 9.0f,
                    arrowPath = createStyledChevronPath(length = 17f, halfWidth = 10f, indent = 6f),
                    arrowStrokeWidth = 2.6f
                )
            }
            else -> {
                // Zoom cercano cada 500m
                MilestoneConfig(
                    strokeWidth = 10.0f,
                    arrowPath = createStyledChevronPath(length = 20f, halfWidth = 12f, indent = 7.5f),
                    arrowStrokeWidth = 3.0f
                )
            }
        }
    }

    /**
     * Constructs a closed, aerodynamic chevron/dart pointing along +X (direction of travel).
     * Has a white interior fill and an integrated EMT red stroke for maximum contrast and elegance.
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
     * Prunes variant shapes for a line based on headsigns, circular loop closing, and stop location.
     * For circular lines starting with 'C' (e.g. C1, C2, C3), merges and closes the rings
     * that are split across two timing/regulation destinations.
     * When stopLocation is provided, isolates the direction serving that specific stop.
     */
    fun pruneShapesForLine(
        shapes: List<EmtRouteShape>,
        lineRef: String? = null,
        targetHeadsign: String? = null,
        stopLocation: GeoPoint? = null,
        lineCode: String? = lineRef,
        selectedStopLat: Double? = null,
        selectedStopLon: Double? = null
    ): List<EmtRouteShape> {
        val effectiveLine = lineCode ?: lineRef
        val effectiveStopLocation = stopLocation ?: if (selectedStopLat != null && selectedStopLon != null) {
            GeoPoint(selectedStopLat, selectedStopLon)
        } else null

        if (shapes.isEmpty()) return emptyList()

        // 1. If line is circular (starts with C), close and form unified circular rings
        val processedShapes = if (effectiveLine != null && BusRouteDirectionManager.isCircularLine(effectiveLine)) {
            BusRouteDirectionManager.closeCircularShapes(effectiveLine, shapes)
        } else {
            shapes
        }

        if (!targetHeadsign.isNullOrBlank()) {
            val matching = processedShapes.filter {
                it.headsign != null && it.headsign.contains(targetHeadsign, ignoreCase = true)
            }
            if (matching.isNotEmpty()) return matching
        }

        // Group shapes by headsign (or fallback to shapeId if headsign is null)
        val grouped = processedShapes.groupBy { it.headsign ?: it.shapeId }
        
        // Pick the shape with the most points for each headsign direction (main ida & main vuelta)
        val mainShapes = grouped.mapValues { (_, shapeGroup) ->
            shapeGroup.maxByOrNull { it.points.size } ?: shapeGroup.first()
        }.values.toList()

        // 4. Direction isolation:
        // When a specific stop location is known, isolate the EXACT direction
        // that serves this stop! Do NOT show both directions!
        if (effectiveStopLocation != null && mainShapes.size > 1) {
            val isolated = BusRouteDirectionManager.findMatchingShapeForStop(effectiveStopLocation, mainShapes)
            if (isolated != null) {
                return listOf(isolated)
            }
        }

        return mainShapes
    }

    /**
     * Builds Osmdroid Polyline overlays for the requested lines.
     * Uses official EMT Red with directional arrows anchored every 500m fixed meters
     * when showArrows is true and zoom >= 14.0.
     * Isolates direction according to stopLocation or targetHeadsign.
     */
    fun createPolylinesForLines(
        mapView: MapView,
        lines: Set<String>,
        strokeWidth: Float? = null,
        currentZoom: Double = 16.0,
        showArrows: Boolean = lines.size == 1,
        stopLocation: GeoPoint? = null,
        targetHeadsign: String? = null
    ): List<Polyline> {
        if (lines.isEmpty()) return emptyList()

        val polylines = mutableListOf<Polyline>()
        val sortedLines = lines.sortedWith(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })

        val config = getMilestoneConfig(currentZoom)
        val finalStrokeWidth = strokeWidth ?: config.strokeWidth

        sortedLines.forEachIndexed { index, rawLine ->
            val cleanLine = normalizeLine(rawLine)
            val rawShapes = lineToShapesMap[cleanLine] ?: return@forEachIndexed
            val shapes = pruneShapesForLine(rawShapes, cleanLine, targetHeadsign, stopLocation)

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

                    if (showArrows && config.arrowPath != null) {
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

                        // Fixed 500 meters recurrence across all zoom levels
                        val lister = MilestoneMeterDistanceLister(FIXED_ARROW_INTERVAL_METERS)
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
