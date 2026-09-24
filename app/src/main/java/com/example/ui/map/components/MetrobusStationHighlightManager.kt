package com.example.ui.map.components

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.data.database.MetrobusStopEntity
import com.example.ui.map.SelectedMapItem
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.milestones.MilestoneManager
import org.osmdroid.views.overlay.milestones.MilestoneMeterDistanceLister
import org.osmdroid.views.overlay.milestones.MilestonePathDisplayer

/**
 * Manages Metrobús Valencia route polyline overlays with caching
 * to eliminate GC overhead during map gestures, and coordinates
 * stop highlighting/dimming.
 */
object MetrobusStationHighlightManager {

    private const val ACTIVE_ALPHA = 1.0f
    private const val CONNECTED_ALPHA = 1.0f
    private const val DIMMED_ALPHA = 0.25f

    data class MetrobusHighlightState(
        val isHighlighted: Boolean,
        val selectedStopId: String?,
        val allStopLines: Set<String>,
        val isolatedLineFilters: Set<String>
    ) {
        companion object {
            val INACTIVE = MetrobusHighlightState(
                isHighlighted = false,
                selectedStopId = null,
                allStopLines = emptySet(),
                isolatedLineFilters = emptySet()
            )
        }

        val effectiveLines: Set<String>
            get() = if (isolatedLineFilters.isNotEmpty()) isolatedLineFilters else allStopLines
    }

    fun normalizeLine(line: String): String {
        val trimmed = line.trim().uppercase()
        return if (trimmed.startsWith("L") && trimmed.length > 1 && trimmed[1].isDigit()) {
            trimmed.substring(1)
        } else {
            trimmed
        }
    }

    fun getHighlightState(
        selectedMapItem: SelectedMapItem?,
        isolatedLineFilters: Set<String> = emptySet()
    ): MetrobusHighlightState {
        if (selectedMapItem !is SelectedMapItem.MetrobusStopItem) {
            return MetrobusHighlightState.INACTIVE
        }

        val stop = selectedMapItem.stop
        val lines = mutableSetOf<String>()

        stop.lineas?.split(",")?.forEach {
            val clean = normalizeLine(it)
            if (clean.isNotEmpty()) lines.add(clean)
        }

        selectedMapItem.metrobusModel.lineas.forEach {
            val clean = normalizeLine(it)
            if (clean.isNotEmpty()) lines.add(clean)
        }

        val normalizedFilters = isolatedLineFilters
            .map { normalizeLine(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        return MetrobusHighlightState(
            isHighlighted = lines.isNotEmpty(),
            selectedStopId = stop.id_parada,
            allStopLines = lines,
            isolatedLineFilters = normalizedFilters
        )
    }

    fun getMetrobusMarkerAlpha(
        stop: MetrobusStopEntity?,
        highlightState: MetrobusHighlightState,
        fallbackAlpha: Float = 1.0f
    ): Float {
        if (!highlightState.isHighlighted) return fallbackAlpha
        if (stop == null) return DIMMED_ALPHA

        if (stop.id_parada == highlightState.selectedStopId) {
            return ACTIVE_ALPHA
        }

        val stopLines = stop.lineas?.split(",")?.map { normalizeLine(it) } ?: emptyList()
        val sharesLine = stopLines.any { highlightState.effectiveLines.contains(it) }

        return if (sharesLine) CONNECTED_ALPHA else DIMMED_ALPHA
    }

    private var cachedKey: String = ""
    private var cachedPolylines: List<Polyline> = emptyList()

    fun addMetrobusPolylinesToMap(
        mapView: MapView,
        selectedMetrobusShapes: Map<String, List<GeoPoint>>,
        currentZoom: Double
    ) {
        if (selectedMetrobusShapes.isEmpty()) {
            cachedKey = ""
            cachedPolylines = emptyList()
            return
        }

        val shapesKey = selectedMetrobusShapes.keys.sorted().joinToString(",") + "_" +
                selectedMetrobusShapes.values.sumOf { it.size }

        if (shapesKey != cachedKey || cachedPolylines.isEmpty()) {
            cachedKey = shapesKey
            val uniqueLines = selectedMetrobusShapes.keys.map { it.substringBefore("_") }.toSet()
            val showArrows = uniqueLines.size == 1
            val orangeColorInt = Color.parseColor("#F97316")

            val newPolylines = mutableListOf<Polyline>()
            selectedMetrobusShapes.forEach { (_, points) ->
                if (points.isEmpty()) return@forEach
                val polyline = Polyline(mapView).apply {
                    setPoints(points)
                    outlinePaint.color = orangeColorInt
                    outlinePaint.isAntiAlias = true
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.strokeWidth = 8.5f
                    infoWindow = null
                    setOnClickListener { _, _, _ -> true }

                    if (showArrows) {
                        val arrowPath = createStyledChevronPath(length = 18f, halfWidth = 11f, indent = 6.5f)
                        val arrowStrokeWidth = 2.8f
                        val recurrenceMeters = 250.0

                        val fillPaint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val borderPaint = Paint().apply {
                            color = orangeColorInt
                            style = Paint.Style.STROKE
                            this.strokeWidth = arrowStrokeWidth
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        val lister = MilestoneMeterDistanceLister(recurrenceMeters)
                        val fillDisplayer = MilestonePathDisplayer(0.0, true, arrowPath, fillPaint)
                        val borderDisplayer = MilestonePathDisplayer(0.0, true, arrowPath, borderPaint)

                        setMilestoneManagers(listOf(
                            MilestoneManager(lister, fillDisplayer),
                            MilestoneManager(lister, borderDisplayer)
                        ))
                    }
                }
                newPolylines.add(polyline)
            }
            cachedPolylines = newPolylines
        }

        cachedPolylines.forEach { mapView.overlays.add(it) }
    }

    private fun createStyledChevronPath(length: Float = 18f, halfWidth: Float = 11f, indent: Float = 6.5f): Path {
        return Path().apply {
            moveTo(length * 0.5f, 0f)              // Tip (front)
            lineTo(-length * 0.5f, -halfWidth)     // Top rear wing
            lineTo(-length * 0.5f + indent, 0f)    // Inner middle notch
            lineTo(-length * 0.5f, halfWidth)      // Bottom rear wing
            close()
        }
    }

    fun clearCache() {
        cachedKey = ""
        cachedPolylines = emptyList()
    }
}
