package com.example.ui.map.components

import android.content.Context
import com.example.data.database.GeoportalStopEntity
import com.example.ui.map.SelectedMapItem
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Coordinates EMT Valencia bus line highlighting on the map when a bus stop is selected.
 * Supports isolating individual lines and directions when filtered in the bottom sheet or selected.
 */
object EmtStationHighlightManager {

    private const val ACTIVE_ALPHA = 1.0f
    private const val CONNECTED_ALPHA = 1.0f
    private const val DIMMED_ALPHA = 0.25f

    data class EmtHighlightState(
        val isHighlighted: Boolean,
        val selectedStopId: String?,
        val selectedStopLocation: GeoPoint? = null,
        val allStopLines: Set<String>,
        val isolatedLineFilters: Set<String>,
        val selectedDirectionFilter: String? = null,
        val selectedStopLat: Double? = selectedStopLocation?.latitude,
        val selectedStopLon: Double? = selectedStopLocation?.longitude,
        val activeDirectionalShapeIds: Set<String> = emptySet()
    ) {
        companion object {
            val INACTIVE = EmtHighlightState(
                isHighlighted = false,
                selectedStopId = null,
                selectedStopLocation = null,
                allStopLines = emptySet(),
                isolatedLineFilters = emptySet(),
                selectedDirectionFilter = null
            )
        }

        val effectiveLinesToDraw: Set<String>
            get() = if (isolatedLineFilters.isNotEmpty()) isolatedLineFilters else allStopLines

        /**
         * Directional arrows are displayed exclusively when an individual line is isolated/selected.
         * When viewing "Todas" (multiple lines simultaneously), arrows are omitted to avoid
         * cluttering shared streets with overlapping chevrons.
         */
        val showDirectionalArrows: Boolean
            get() = isolatedLineFilters.size == 1 || (isolatedLineFilters.isEmpty() && allStopLines.size == 1)
    }

    /**
     * Inspects the selected item and returns the EMT highlight state.
     */
    fun getHighlightState(
        selectedMapItem: SelectedMapItem?,
        isolatedLineFilters: Set<String> = emptySet(),
        selectedDirectionFilter: String? = null
    ): EmtHighlightState {
        if (selectedMapItem !is SelectedMapItem.BusStop) {
            return EmtHighlightState.INACTIVE
        }

        val stop = selectedMapItem.stop
        val emtModel = selectedMapItem.emtStopModel
        val stopLocation = GeoPoint(stop.lat, stop.lon)

        val lines = mutableSetOf<String>()

        // Extract from GeoportalStopEntity comma-separated string
        stop.lineas?.split(",")?.forEach {
            val clean = EmtMapOverlayLoader.normalizeLine(it)
            if (clean.isNotEmpty()) lines.add(clean)
        }

        // Extract from EmtBusStop utes list
        emtModel.utes.forEach {
            val clean = EmtMapOverlayLoader.normalizeLine(it.id_linea)
            if (clean.isNotEmpty()) lines.add(clean)
        }

        val normalizedFilters = isolatedLineFilters
            .map { EmtMapOverlayLoader.normalizeLine(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        return EmtHighlightState(
            isHighlighted = lines.isNotEmpty(),
            selectedStopId = stop.id_parada,
            selectedStopLocation = stopLocation,
            allStopLines = lines,
            isolatedLineFilters = normalizedFilters,
            selectedDirectionFilter = selectedDirectionFilter
        )
    }

    /**
     * Computes the alpha for a bus marker when EMT highlight is active.
     */
    fun getBusMarkerAlpha(
        stop: GeoportalStopEntity?,
        highlightState: EmtHighlightState,
        fallbackAlpha: Float
    ): Float {
        if (!highlightState.isHighlighted) return fallbackAlpha
        if (stop == null) return DIMMED_ALPHA

        if (stop.id_parada == highlightState.selectedStopId) {
            return ACTIVE_ALPHA
        }

        val stopLines = stop.lineas?.split(",")?.map { EmtMapOverlayLoader.normalizeLine(it) } ?: emptyList()
        val sharesLine = stopLines.any { highlightState.effectiveLinesToDraw.contains(it) }

        if (!sharesLine) return DIMMED_ALPHA

        if (highlightState.activeDirectionalShapeIds.isNotEmpty() && stop.lat != 0.0 && stop.lon != 0.0) {
            val isNearActiveDirection = EmtMapOverlayLoader.isStopNearShapes(
                stopLat = stop.lat,
                stopLon = stop.lon,
                shapeIds = highlightState.activeDirectionalShapeIds,
                maxDistanceMeters = 150.0
            )
            if (!isNearActiveDirection) {
                return DIMMED_ALPHA
            }
        }

        return CONNECTED_ALPHA
    }

    /**
     * Adds the EMT bus polylines for the active/isolated lines to the map,
     * styling the lines and directional arrows appropriately for the current zoom level.
     */
    fun addEmtPolylinesToMap(
        context: Context,
        mapView: MapView,
        highlightState: EmtHighlightState,
        currentZoom: Double = 16.0
    ) {
        if (!highlightState.isHighlighted) return

        if (!EmtMapOverlayLoader.isLoaded) {
            EmtMapOverlayLoader.ensureLoaded(context) {
                mapView.postInvalidate()
            }
            return
        }

        val polylines = EmtMapOverlayLoader.createPolylinesForLines(
            mapView = mapView,
            lines = highlightState.effectiveLinesToDraw,
            currentZoom = currentZoom,
            showArrows = highlightState.showDirectionalArrows,
            stopLocation = highlightState.selectedStopLocation,
            targetHeadsign = highlightState.selectedDirectionFilter
        )

        polylines.forEach { mapView.overlays.add(it) }
    }
}
