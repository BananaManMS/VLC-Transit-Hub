package com.example.ui.map.components

/**
 * State for detail bottom sheets on the map (Bus stop, Metro station, Cercanias, Valenbisi, Metrobus, Address).
 * - COLLAPSED: Compact view showing only header and quick action buttons (145.dp),
 *              allowing free map exploration when panning or interacting with the map.
 * - HALF_EXPANDED: Default open state (approx. half screen, ~340.dp), showing header, quick buttons and arrivals/departures.
 * - FULLY_EXPANDED: Maximum height state when dragged up, showing the full departures/services list.
 */
enum class DetailSheetState {
    COLLAPSED,
    HALF_EXPANDED,
    FULLY_EXPANDED
}
