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
 * Handles deferred (lazy) loading and LOD caching of Cercanías routes with dynamic perpendicular lateral offsets.
 * Eliminates overlapping polyline artifacts on shared corridors (e.g. C1 and C2 on the Southern Trunk, C5 and C6).
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
                    cachedMapViewRef = null
                    val callbacks = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    callbacks
                }
                Log.d(TAG, "Successfully lazy-loaded Cercanias polylines with lateral offsets from GeoJSON")
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

            val rawRef = properties.optString("ref", "").trim().uppercase()
            val cleanRef = when {
                rawRef.startsWith("C1") || rawRef == "1" -> "C1"
                rawRef.startsWith("C2") || rawRef == "2" -> "C2"
                rawRef.startsWith("C3") || rawRef == "3" -> "C3"
                rawRef.startsWith("C5") || rawRef == "5" -> "C5"
                rawRef.startsWith("C6") || rawRef == "6" -> "C6"
                else -> rawRef
            }
            if (cleanRef.isBlank()) continue

            val colourHex = properties.optString("colour", "")
            val defaultColor = when (cleanRef) {
                "C1" -> Color.parseColor("#7AB3DE")
                "C2" -> Color.parseColor("#F79529")
                "C3" -> Color.parseColor("#7A2780")
                "C5" -> Color.parseColor("#018A27")
                "C6" -> Color.parseColor("#0D3386")
                else -> Color.parseColor("#E30613")
            }
            val color = try {
                if (colourHex.startsWith("#")) Color.parseColor(colourHex) else if (colourHex.isNotBlank()) Color.parseColor("#$colourHex") else defaultColor
            } catch (_: Exception) {
                defaultColor
            }

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

            for (points in featureSegments) {
                // Slightly thicker than Metrovalencia (Metro: 10f / 7f / 5f -> Cercanias: 12f / 8.5f / 6f)
                val strokeClose = 12.0f
                val strokeMed = 8.5f
                val strokeFar = 6.0f

                highResCloseList.add(MetroMapOverlayLoader.RawPolyline(points, color, strokeClose, cleanRef))
                highResMediumList.add(MetroMapOverlayLoader.RawPolyline(points, color, strokeMed, cleanRef))
                highResFarList.add(MetroMapOverlayLoader.RawPolyline(points, color, strokeFar, cleanRef))

                val simplified = MapPolylineOffsetHelper.rdpSimplify(points, epsilon)
                lowResCloseList.add(MetroMapOverlayLoader.RawPolyline(simplified, color, strokeClose, cleanRef))
                lowResMediumList.add(MetroMapOverlayLoader.RawPolyline(simplified, color, strokeMed, cleanRef))
                lowResFarList.add(MetroMapOverlayLoader.RawPolyline(simplified, color, strokeFar, cleanRef))
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
