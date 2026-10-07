package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import com.example.data.database.CercaniasStationEntity
import com.example.ui.map.SelectedMapItem
import org.osmdroid.views.MapView

/**
 * Coordinates Cercanías (Renfe Rodalia) station and line highlighting / dimming.
 * Supports isolating individual lines (C1, C2, C3, C5, C6) when filtered in the bottom sheet.
 */
object CercaniasStationHighlightManager {

    private const val ACTIVE_ALPHA_MARKER = 1.0f
    private const val CONNECTED_STATION_ALPHA = 0.95f
    private const val DIMMED_ALPHA_MARKER = 0.25f
    private const val DIMMED_ALPHA_COLOR_INT = 45

    data class CercaniasHighlightState(
        val isHighlighted: Boolean,
        val activeLineRefs: Set<String>,
        val isolatedLineFilters: Set<String>,
        val selectedStationId: String?,
        val selectedStationName: String?
    ) {
        companion object {
            val INACTIVE = CercaniasHighlightState(
                isHighlighted = false,
                activeLineRefs = emptySet(),
                isolatedLineFilters = emptySet(),
                selectedStationId = null,
                selectedStationName = null
            )
        }

        val effectiveLines: Set<String>
            get() = if (isolatedLineFilters.isNotEmpty()) isolatedLineFilters else activeLineRefs
    }

    /**
     * Normalizes line references: "C1", "c1", " 1 " -> "C1".
     */
    fun normalizeLineRef(rawLine: String): String {
        val trimmed = rawLine.trim().uppercase()
        return if (trimmed.isNotEmpty() && !trimmed.startsWith("C")) {
            "C$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Inspects the selected item and isolated line filters to determine the highlight state.
     */
    fun getHighlightState(
        selectedMapItem: SelectedMapItem?,
        isolatedLineFilters: Set<String> = emptySet()
    ): CercaniasHighlightState {
        if (selectedMapItem !is SelectedMapItem.Cercanias) {
            return CercaniasHighlightState.INACTIVE
        }

        val station = selectedMapItem.station
        val rawLines = station.lineas.ifEmpty { station.lines }

        val activeRefs = rawLines
            .map { normalizeLineRef(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        val normalizedIsolated = isolatedLineFilters
            .map { normalizeLineRef(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        return CercaniasHighlightState(
            isHighlighted = activeRefs.isNotEmpty(),
            activeLineRefs = activeRefs,
            isolatedLineFilters = normalizedIsolated,
            selectedStationId = station.stop_id,
            selectedStationName = station.displayName
        )
    }

    /**
     * Calculates the alpha transparency for a Cercanías station marker based on whether
     * it connects with the currently selected station and active line filter.
     */
    fun getCercaniasMarkerAlpha(
        station: CercaniasStationEntity?,
        highlightState: CercaniasHighlightState,
        defaultAlpha: Float = 1.0f
    ): Float {
        if (!highlightState.isHighlighted) return defaultAlpha
        if (station == null) return DIMMED_ALPHA_MARKER

        if (station.stop_id == highlightState.selectedStationId ||
            station.displayName.equals(highlightState.selectedStationName, ignoreCase = true)
        ) {
            return ACTIVE_ALPHA_MARKER
        }

        val stationLines = station.lineas.ifEmpty { station.lines }
            .map { normalizeLineRef(it) }
            .filter { it.isNotEmpty() }

        val hasSharedLine = stationLines.any { line ->
            highlightState.effectiveLines.contains(line)
        }

        return if (hasSharedLine) CONNECTED_STATION_ALPHA else DIMMED_ALPHA_MARKER
    }

    /**
     * Styles and adds Cercanías polylines to the map.
     * When highlighted:
     * - Connected polylines (matching effectiveLines) are drawn on top at full opacity and stroke width.
     * - Unrelated Cercanías polylines are dimmed.
     */
    fun addCercaniasPolylinesToMap(
        context: Context,
        mapView: MapView,
        highlightState: CercaniasHighlightState,
        isAnyOtherLayerHighlighted: Boolean
    ) {
        addDimmedCercaniasPolylinesToMap(context, mapView, highlightState, isAnyOtherLayerHighlighted)
        addActiveCercaniasPolylinesToMap(context, mapView, highlightState)
    }

    fun addDimmedCercaniasPolylinesToMap(
        context: Context,
        mapView: MapView,
        highlightState: CercaniasHighlightState,
        isAnyOtherLayerHighlighted: Boolean
    ) {
        val loadedCercanias = CercaniasMapOverlayLoader.getLoadedPolylines(
            mapView = mapView,
            context = context,
            zoomCategory = MetroMapOverlayLoader.getZoomCategory(),
            useHighRes = MetroMapOverlayLoader.isUseHighRes(),
            onLoaded = { mapView.postInvalidate() }
        )

        if (!highlightState.isHighlighted && !isAnyOtherLayerHighlighted) {
            // Normal mode: draw all polylines at original colors
            loadedCercanias.forEach { polyline ->
                val raw = polyline.relatedObject as? MetroMapOverlayLoader.RawPolyline
                if (raw != null) {
                    polyline.outlinePaint.color = raw.color
                    polyline.outlinePaint.strokeWidth = raw.strokeWidth
                } else {
                    val c = polyline.outlinePaint.color
                    polyline.outlinePaint.color = Color.argb(255, Color.red(c), Color.green(c), Color.blue(c))
                }
                mapView.overlays.add(polyline)
            }
            return
        }

        if (!highlightState.isHighlighted && isAnyOtherLayerHighlighted) {
            // Another layer (e.g. Metro or Bus) is highlighted -> dim all Cercanías polylines
            loadedCercanias.forEach { polyline ->
                val raw = polyline.relatedObject as? MetroMapOverlayLoader.RawPolyline
                val baseColor = raw?.color ?: polyline.outlinePaint.color
                val r = Color.red(baseColor)
                val g = Color.green(baseColor)
                val b = Color.blue(baseColor)
                polyline.outlinePaint.color = Color.argb(DIMMED_ALPHA_COLOR_INT, r, g, b)
                polyline.outlinePaint.strokeWidth = (raw?.strokeWidth ?: 12f) * 0.85f
                mapView.overlays.add(polyline)
            }
            return
        }

        // Cercanías station is highlighted: add dimmed lines only
        loadedCercanias.forEach { polyline ->
            val raw = polyline.relatedObject as? MetroMapOverlayLoader.RawPolyline
            val lineRef = raw?.lineRef?.let { normalizeLineRef(it) }

            val isActive = if (lineRef != null && lineRef.isNotEmpty()) {
                highlightState.effectiveLines.contains(lineRef)
            } else {
                false
            }

            if (!isActive) {
                val baseColor = raw?.color ?: polyline.outlinePaint.color
                val r = Color.red(baseColor)
                val g = Color.green(baseColor)
                val b = Color.blue(baseColor)
                polyline.outlinePaint.color = Color.argb(DIMMED_ALPHA_COLOR_INT, r, g, b)
                polyline.outlinePaint.strokeWidth = (raw?.strokeWidth ?: 12f) * 0.85f
                mapView.overlays.add(polyline)
            }
        }
    }

    fun addActiveCercaniasPolylinesToMap(
        context: Context,
        mapView: MapView,
        highlightState: CercaniasHighlightState
    ) {
        if (!highlightState.isHighlighted) return
        val loadedCercanias = CercaniasMapOverlayLoader.getLoadedPolylines(
            mapView = mapView,
            context = context,
            zoomCategory = MetroMapOverlayLoader.getZoomCategory(),
            useHighRes = MetroMapOverlayLoader.isUseHighRes(),
            onLoaded = { mapView.postInvalidate() }
        )

        loadedCercanias.forEach { polyline ->
            val raw = polyline.relatedObject as? MetroMapOverlayLoader.RawPolyline
            val lineRef = raw?.lineRef?.let { normalizeLineRef(it) }

            val isActive = if (lineRef != null && lineRef.isNotEmpty()) {
                highlightState.effectiveLines.contains(lineRef)
            } else {
                false
            }

            if (isActive) {
                if (raw != null) {
                    polyline.outlinePaint.color = raw.color
                    polyline.outlinePaint.strokeWidth = raw.strokeWidth * 1.25f
                } else {
                    val c = polyline.outlinePaint.color
                    polyline.outlinePaint.color = Color.argb(255, Color.red(c), Color.green(c), Color.blue(c))
                }
                mapView.overlays.add(polyline)
            }
        }
    }
}
