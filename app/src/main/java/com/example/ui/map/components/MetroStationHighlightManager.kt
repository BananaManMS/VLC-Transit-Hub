package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import com.example.data.model.MetroStation
import com.example.ui.map.SelectedMapItem
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Manages dynamic highlighting and dimming of transit lines and map elements
 * when a Metrovalencia station is selected.
 *
 * When a Metro station is active:
 * - Lines passing through the station retain 100% opacity and are emphasized on top.
 * - Non-passing metro lines and cercanías lines are dimmed to ~20-25% opacity.
 * - Non-connected transit markers (buses, bikes, unrelated stations) are dimmed.
 */
object MetroStationHighlightManager {

    private const val DIMMED_ALPHA_MARKER = 0.25f
    private const val ACTIVE_ALPHA_MARKER = 1.0f
    private const val CONNECTED_STATION_ALPHA = 0.95f
    private const val DIMMED_ALPHA_COLOR_INT = 45

    data class MetroHighlightState(
        val isHighlighted: Boolean,
        val activeLineRefs: Set<String>,
        val selectedStationName: String?
    ) {
        companion object {
            val INACTIVE = MetroHighlightState(
                isHighlighted = false,
                activeLineRefs = emptySet(),
                selectedStationName = null
            )
        }
    }

    /**
     * Normalizes line reference by removing optional "L" prefix and whitespace.
     * E.g., "L3" -> "3", " 5 " -> "5".
     */
    fun normalizeLineRef(rawLine: String): String {
        return rawLine.trim()
            .removePrefix("L")
            .removePrefix("l")
            .trim()
    }

    /**
     * Inspects the selected item and extracts the highlight state if it is a Metro station.
     */
    fun getHighlightState(selectedMapItem: SelectedMapItem?): MetroHighlightState {
        if (selectedMapItem !is SelectedMapItem.Metro) {
            return MetroHighlightState.INACTIVE
        }

        val station = selectedMapItem.station
        val activeRefs = station.lines
            .map { normalizeLineRef(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        return MetroHighlightState(
            isHighlighted = activeRefs.isNotEmpty(),
            activeLineRefs = activeRefs,
            selectedStationName = station.name
        )
    }

    /**
     * Calculates the alpha transparency for a Metro station marker based on whether
     * it shares any line with the currently selected station.
     */
    fun getMetroMarkerAlpha(
        station: MetroStation?,
        highlightState: MetroHighlightState
    ): Float {
        if (!highlightState.isHighlighted) return ACTIVE_ALPHA_MARKER
        if (station == null) return DIMMED_ALPHA_MARKER

        if (station.name.equals(highlightState.selectedStationName, ignoreCase = true)) {
            return ACTIVE_ALPHA_MARKER
        }

        val hasSharedLine = station.lines.any { rawLine ->
            highlightState.activeLineRefs.contains(normalizeLineRef(rawLine))
        }

        return if (hasSharedLine) CONNECTED_STATION_ALPHA else DIMMED_ALPHA_MARKER
    }

    /**
     * Calculates marker alpha for secondary transit elements (Bus, Valenbisi, Cercanías, Custom Places).
     */
    fun getSecondaryElementAlpha(highlightState: MetroHighlightState): Float {
        return if (highlightState.isHighlighted) DIMMED_ALPHA_MARKER else ACTIVE_ALPHA_MARKER
    }

    /**
     * Styles and adds metro polylines to the map.
     * When highlighted, dimmed lines are added first, followed by emphasized active lines on top.
     */
    fun addMetroPolylinesToMap(
        mapView: MapView,
        highlightState: MetroHighlightState
    ) {
        val loadedLines = MetroMapOverlayLoader.getLoadedPolylines(mapView)
        if (!highlightState.isHighlighted) {
            loadedLines.forEach { polyline ->
                val raw = polyline.relatedObject as? MetroMapOverlayLoader.RawPolyline
                if (raw != null) {
                    polyline.outlinePaint.color = raw.color
                    polyline.outlinePaint.strokeWidth = raw.strokeWidth
                }
                mapView.overlays.add(polyline)
            }
            return
        }

        val activeLines = mutableListOf<Polyline>()
        val dimmedLines = mutableListOf<Polyline>()

        for (polyline in loadedLines) {
            val raw = polyline.relatedObject as? MetroMapOverlayLoader.RawPolyline
            if (raw != null) {
                val cleanRef = normalizeLineRef(raw.lineRef)
                if (highlightState.activeLineRefs.contains(cleanRef)) {
                    polyline.outlinePaint.color = raw.color
                    polyline.outlinePaint.strokeWidth = raw.strokeWidth * 1.3f
                    activeLines.add(polyline)
                } else {
                    val r = Color.red(raw.color)
                    val g = Color.green(raw.color)
                    val b = Color.blue(raw.color)
                    polyline.outlinePaint.color = Color.argb(DIMMED_ALPHA_COLOR_INT, r, g, b)
                    polyline.outlinePaint.strokeWidth = raw.strokeWidth * 0.85f
                    dimmedLines.add(polyline)
                }
            } else {
                dimmedLines.add(polyline)
            }
        }

        // Draw dimmed lines first, active lines layered above
        dimmedLines.forEach { mapView.overlays.add(it) }
        activeLines.forEach { mapView.overlays.add(it) }
    }

    /**
     * Styles and adds Cercanías polylines to the map, applying dimmed styling if a Metro station is highlighted.
     */
    fun addCercaniasPolylinesToMap(
        context: Context,
        mapView: MapView,
        highlightState: MetroHighlightState
    ) {
        val loadedCercanias = CercaniasMapOverlayLoader.getLoadedPolylines(
            mapView = mapView,
            context = context,
            zoomCategory = MetroMapOverlayLoader.getZoomCategory(),
            useHighRes = MetroMapOverlayLoader.isUseHighRes(),
            onLoaded = { mapView.postInvalidate() }
        )

        loadedCercanias.forEach { polyline ->
            val raw = polyline.relatedObject as? MetroMapOverlayLoader.RawPolyline
            if (highlightState.isHighlighted) {
                val baseColor = raw?.color ?: polyline.outlinePaint.color
                val r = Color.red(baseColor)
                val g = Color.green(baseColor)
                val b = Color.blue(baseColor)
                polyline.outlinePaint.color = Color.argb(DIMMED_ALPHA_COLOR_INT, r, g, b)
                polyline.outlinePaint.strokeWidth = (raw?.strokeWidth ?: 9f) * 0.85f
            } else {
                if (raw != null) {
                    polyline.outlinePaint.color = raw.color
                    polyline.outlinePaint.strokeWidth = raw.strokeWidth
                } else {
                    val c = polyline.outlinePaint.color
                    polyline.outlinePaint.color = Color.argb(255, Color.red(c), Color.green(c), Color.blue(c))
                }
            }
            mapView.overlays.add(polyline)
        }
    }
}
