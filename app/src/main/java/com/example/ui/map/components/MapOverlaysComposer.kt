package com.example.ui.map.components

import android.content.Context
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import com.example.data.model.routing.PlannedItinerary

/**
 * Organizes and layers overlays on the MapView according to transit hierarchy:
 * 1. mapEventsOverlay (lowest layer, click listener on empty map space)
 * 2. Base polygons / station footprints / transit line polylines
 * 3. Transit Stops: Bus, Metrobus, Valenbisi, Metro, Cercanías
 * 4. Custom Places: Home, Work, Custom Favorites
 * 5. Destination Marker & Active Itinerary
 * 6. User Location overlay (highest layer)
 */
object MapOverlaysComposer {

    data class ComposeParams(
        val context: Context, val mapView: MapView,
        val selectedItinerary: PlannedItinerary?,
        val showMetro: Boolean, val showCercanias: Boolean,
        val showMetrobus: Boolean, val showValenbisi: Boolean,
        val currentZoom: Double, val isDarkMode: Boolean,
        val mapEventsOverlay: MapEventsOverlay?,
        val destinationMarker: Marker?, val destinationLocation: GeoPoint?,
        val userMarker: Marker?, val userLocation: GeoPoint?,
        val activeBusCount: Int, val activeClusterCount: Int,
        val recycledBusMarkers: List<Marker>, val recycledClusterMarkers: List<Marker>,
        val activeMergedBusCount: Int = 0, val recycledMergedBusMarkers: List<Marker> = emptyList(),
        val activeMetrobusCount: Int, val activeMetrobusClusterCount: Int,
        val recycledMetrobusMarkers: List<Marker>, val recycledMetrobusClusterMarkers: List<Marker>,
        val activeValenbisiCount: Int, val activeValenbisiClusterCount: Int,
        val recycledValenbisiMarkers: List<Marker>, val recycledValenbisiClusterMarkers: List<Marker>,
        val validMetroStationsCount: Int, val recycledMetroMarkers: List<Marker>,
        val validCercaniasStationsCount: Int, val recycledCercaniasMarkers: List<Marker>,
        val activeCustomFavCount: Int, val recycledCustomFavoriteMarkers: List<Marker>,
        val lastZoomedItineraryId: String?,
        val selectedMapItem: com.example.ui.map.SelectedMapItem? = null,
        val validMetroStations: List<com.example.data.model.MetroStation> = emptyList(),
        val selectedBusLineFilters: Set<String> = emptySet(),
        val selectedMetrobusShapes: Map<String, List<GeoPoint>> = emptyMap(),
        val selectedDirectionFilter: String? = null,
        val onSelectItem: ((com.example.ui.map.SelectedMapItem) -> Unit)? = null
    )

    fun composeOverlays(p: ComposeParams): String? {
        var updatedZoomedId = p.lastZoomedItineraryId

        p.mapView.overlays.clear()

        p.mapEventsOverlay?.let { p.mapView.overlays.add(it) }

        val metroHighlight = MetroStationHighlightManager.getHighlightState(p.selectedMapItem)
        val emtHighlight = EmtStationHighlightManager.getHighlightState(
            selectedMapItem = p.selectedMapItem,
            isolatedLineFilters = p.selectedBusLineFilters,
            selectedDirectionFilter = p.selectedDirectionFilter
        )
        val cercaniasHighlight = CercaniasStationHighlightManager.getHighlightState(p.selectedMapItem, p.selectedBusLineFilters)
        val mbHighlight = MetrobusStationHighlightManager.getHighlightState(p.selectedMapItem, p.selectedBusLineFilters)

        val isAnyNonMetroTransitSelected = p.selectedMapItem is com.example.ui.map.SelectedMapItem.BusStop ||
                p.selectedMapItem is com.example.ui.map.SelectedMapItem.Cercanias ||
                p.selectedMapItem is com.example.ui.map.SelectedMapItem.MetrobusStopItem

        // Effective Metro highlight state: if EMT, Cercanias, or Metrobus stop is highlighted, dim all metro lines
        val effectiveMetroHighlight = if (emtHighlight.isHighlighted || cercaniasHighlight.isHighlighted || mbHighlight.isHighlighted || isAnyNonMetroTransitSelected) {
            MetroStationHighlightManager.MetroHighlightState(
                isHighlighted = true,
                activeLineRefs = emptySet(),
                selectedStationName = null
            )
        } else {
            metroHighlight
        }

        val secondaryAlpha = if (metroHighlight.isHighlighted || emtHighlight.isHighlighted || cercaniasHighlight.isHighlighted || mbHighlight.isHighlighted || isAnyNonMetroTransitSelected) 0.25f else 1.0f

        val selectedMetroStation = (p.selectedMapItem as? com.example.ui.map.SelectedMapItem.Metro)?.station

        // In "Ruta Activa" mode (selectedItinerary != null), hide all secondary layers
        if (p.selectedItinerary == null) {
            val selectedBusStop = (p.selectedMapItem as? com.example.ui.map.SelectedMapItem.BusStop)?.stop
            val selectedMetrobusStop = (p.selectedMapItem as? com.example.ui.map.SelectedMapItem.MetrobusStopItem)?.stop
            val selectedCercaniasStation = (p.selectedMapItem as? com.example.ui.map.SelectedMapItem.Cercanias)?.station
            val selectedValenbisiStation = (p.selectedMapItem as? com.example.ui.map.SelectedMapItem.Valenbisi)?.station

            val isBusVisible = p.activeBusCount > 0 || p.activeClusterCount > 0 || selectedBusStop != null
            val isMetrobusVisible = (p.showMetrobus && (p.activeMetrobusCount > 0 || p.activeMetrobusClusterCount > 0)) || selectedMetrobusStop != null
            val isValenbisiVisible = (p.showValenbisi && (p.activeValenbisiCount > 0 || p.activeValenbisiClusterCount > 0)) || selectedValenbisiStation != null
            val isMetroVisible = (p.showMetro && p.currentZoom >= 11.5 && p.validMetroStationsCount > 0) || selectedMetroStation != null
            val isCercaniasVisible = (p.showCercanias && p.validCercaniasStationsCount > 0) || selectedCercaniasStation != null
            val isCustomPlacesVisible = p.activeCustomFavCount > 0

            MapFadeTransitionManager.updateLayerVisibility(
                mapView = p.mapView,
                isBusVisible = isBusVisible,
                isMetrobusVisible = isMetrobusVisible,
                isValenbisiVisible = isValenbisiVisible,
                isMetroVisible = isMetroVisible,
                isCercaniasVisible = isCercaniasVisible,
                isCustomPlacesVisible = isCustomPlacesVisible
            )

            val busLayerAlpha = MapFadeTransitionManager.getLayerAlpha(MapFadeTransitionManager.Layer.BUS)
            val metrobusLayerAlpha = MapFadeTransitionManager.getLayerAlpha(MapFadeTransitionManager.Layer.METROBUS)
            val valenbisiLayerAlpha = MapFadeTransitionManager.getLayerAlpha(MapFadeTransitionManager.Layer.VALENBISI)
            val metroLayerAlpha = MapFadeTransitionManager.getLayerAlpha(MapFadeTransitionManager.Layer.METRO)
            val cercaniasLayerAlpha = MapFadeTransitionManager.getLayerAlpha(MapFadeTransitionManager.Layer.CERCANIAS)
            val customPlacesLayerAlpha = MapFadeTransitionManager.getLayerAlpha(MapFadeTransitionManager.Layer.CUSTOM_PLACES)

            val dimmedMarkers = mutableListOf<Marker>()
            val activeMarkers = mutableListOf<Marker>()
            var topSelectedTransitMarker: Marker? = null

            // 1. Process Bus markers and cluster markers
            val busMarkerPairs = ArrayList<Pair<Marker, Float>>(p.activeBusCount + p.activeClusterCount)

            for (i in 0 until p.activeBusCount) {
                val marker = p.recycledBusMarkers[i]
                val isSelected = selectedBusStop != null && (
                    (marker.position.latitude == selectedBusStop.lat && marker.position.longitude == selectedBusStop.lon) ||
                    marker.snippet?.startsWith("Parada ${selectedBusStop.id_parada}") == true
                )
                val baseAlpha = if (isSelected) {
                    1.0f
                } else if (emtHighlight.isHighlighted) {
                    if (marker.snippet?.contains("Líneas:") == true) {
                        val linesSub = marker.snippet.substringAfter("Líneas:").trim()
                        val sharesLine = linesSub.split(",").any {
                            emtHighlight.effectiveLinesToDraw.contains(EmtMapOverlayLoader.normalizeLine(it))
                        }
                        if (sharesLine) 0.95f else 0.25f
                    } else {
                        0.25f
                    }
                } else {
                    secondaryAlpha
                }
                busMarkerPairs.add(Pair(marker, baseAlpha))
                marker.alpha = if (isSelected) 1.0f else (baseAlpha * busLayerAlpha)
                if (isSelected) {
                    topSelectedTransitMarker = marker
                } else if (busLayerAlpha > 0.005f || isBusVisible) {
                    if (baseAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                }
            }
            for (i in 0 until p.activeClusterCount) {
                val marker = p.recycledClusterMarkers[i]
                busMarkerPairs.add(Pair(marker, secondaryAlpha))
                marker.alpha = secondaryAlpha * busLayerAlpha
                if (busLayerAlpha > 0.005f || isBusVisible) {
                    if (secondaryAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                }
            }

            // 2. Process merged EMT + Metrobus markers
            for (i in 0 until p.activeMergedBusCount) {
                val marker = p.recycledMergedBusMarkers[i]
                val isSelected = (selectedBusStop != null && Math.abs(marker.position.latitude - selectedBusStop.lat) < 0.0001 && Math.abs(marker.position.longitude - selectedBusStop.lon) < 0.0001) ||
                                 (selectedMetrobusStop != null && Math.abs(marker.position.latitude - selectedMetrobusStop.lat) < 0.0001 && Math.abs(marker.position.longitude - selectedMetrobusStop.lon) < 0.0001)
                
                val sub = marker.subDescription ?: ""
                val emtLinesStr = sub.substringAfter("LINES_EMT:", "").substringBefore("|")
                val mbLinesStr = sub.substringAfter("LINES_MB:", "")
                
                val stopLines = emtLinesStr.split(",").map { EmtMapOverlayLoader.normalizeLine(it) }.filter { it.isNotEmpty() }
                val mbLines = mbLinesStr.split(",").map { MetrobusStationHighlightManager.normalizeLine(it) }.filter { it.isNotEmpty() }

                val baseAlpha = if (isSelected) {
                    1.0f
                } else if (emtHighlight.isHighlighted) {
                    val hasEmtMatch = stopLines.any { emtHighlight.effectiveLinesToDraw.contains(it) }
                    val hasMbMatch = mbLines.any { emtHighlight.effectiveLinesToDraw.contains(it) }
                    if (hasEmtMatch || hasMbMatch) 1.0f else 0.25f
                } else if (mbHighlight.isHighlighted) {
                    val hasEmtMatch = stopLines.any { mbHighlight.effectiveLines.contains(it) }
                    val hasMbMatch = mbLines.any { mbHighlight.effectiveLines.contains(it) }
                    if (hasEmtMatch || hasMbMatch) 1.0f else 0.25f
                } else {
                    secondaryAlpha
                }

                busMarkerPairs.add(Pair(marker, baseAlpha))
                marker.alpha = if (isSelected) 1.0f else (baseAlpha * busLayerAlpha)
                if (isSelected) {
                    topSelectedTransitMarker = marker
                } else if (busLayerAlpha > 0.005f || isBusVisible) {
                    if (baseAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                }
            }

            MapFadeTransitionManager.registerActiveMarkers(MapFadeTransitionManager.Layer.BUS, busMarkerPairs)

            // 3. Process Metrobus markers
            if (p.showMetrobus || metrobusLayerAlpha > 0.005f || selectedMetrobusStop != null) {
                val metrobusMarkerPairs = ArrayList<Pair<Marker, Float>>(p.activeMetrobusCount + p.activeMetrobusClusterCount)

                for (i in 0 until p.activeMetrobusCount) {
                    val marker = p.recycledMetrobusMarkers[i]
                    val isSelected = selectedMetrobusStop != null && (
                        (marker.position.latitude == selectedMetrobusStop.lat && marker.position.longitude == selectedMetrobusStop.lon) ||
                        marker.snippet?.startsWith("Metrobús ${selectedMetrobusStop.id_parada}") == true
                    )
                    
                    val baseAlpha = if (isSelected) {
                        1.0f
                    } else if (mbHighlight.isHighlighted) {
                        if (marker.snippet?.contains("Líneas:") == true) {
                            val linesSub = marker.snippet.substringAfter("Líneas:").trim()
                            val sharesLine = linesSub.split(",").any {
                                mbHighlight.effectiveLines.contains(MetrobusStationHighlightManager.normalizeLine(it))
                            }
                            if (sharesLine) 1.0f else 0.25f
                        } else {
                            0.25f
                        }
                    } else {
                        secondaryAlpha
                    }

                    metrobusMarkerPairs.add(Pair(marker, baseAlpha))
                    marker.alpha = if (isSelected) 1.0f else (baseAlpha * metrobusLayerAlpha)
                    if (isSelected) {
                        topSelectedTransitMarker = marker
                    } else if (p.showMetrobus || metrobusLayerAlpha > 0.005f) {
                        if (baseAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                    }
                }
                for (i in 0 until p.activeMetrobusClusterCount) {
                    val marker = p.recycledMetrobusClusterMarkers[i]
                    metrobusMarkerPairs.add(Pair(marker, secondaryAlpha))
                    marker.alpha = secondaryAlpha * metrobusLayerAlpha
                    if (p.showMetrobus || metrobusLayerAlpha > 0.005f) {
                        if (secondaryAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                    }
                }
                MapFadeTransitionManager.registerActiveMarkers(MapFadeTransitionManager.Layer.METROBUS, metrobusMarkerPairs)
            }

            // 4. Process Valenbisi markers
            if (p.showValenbisi || valenbisiLayerAlpha > 0.005f || selectedValenbisiStation != null) {
                val valenbisiMarkerPairs = ArrayList<Pair<Marker, Float>>(p.activeValenbisiCount + p.activeValenbisiClusterCount)
                for (i in 0 until p.activeValenbisiCount) {
                    val marker = p.recycledValenbisiMarkers[i]
                    val isSelected = selectedValenbisiStation != null && (
                        (marker.position.latitude == selectedValenbisiStation.latitude && marker.position.longitude == selectedValenbisiStation.longitude) ||
                        marker.title?.contains(selectedValenbisiStation.name) == true
                    )
                    val base = if (isSelected) {
                        1.0f
                    } else if (marker.alpha <= 0.6f && !emtHighlight.isHighlighted && !metroHighlight.isHighlighted) {
                        0.55f
                    } else {
                        secondaryAlpha
                    }
                    val finalBase = if (!isSelected && marker.snippet?.contains("Disponibles: 0") == true) minOf(base, 0.55f) else base
                    valenbisiMarkerPairs.add(Pair(marker, finalBase))
                    marker.alpha = if (isSelected) 1.0f else (finalBase * valenbisiLayerAlpha)
                    if (isSelected) {
                        topSelectedTransitMarker = marker
                    } else if (p.showValenbisi || valenbisiLayerAlpha > 0.005f) {
                        if (finalBase >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                    }
                }
                for (i in 0 until p.activeValenbisiClusterCount) {
                    val marker = p.recycledValenbisiClusterMarkers[i]
                    valenbisiMarkerPairs.add(Pair(marker, secondaryAlpha))
                    marker.alpha = secondaryAlpha * valenbisiLayerAlpha
                    if (secondaryAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                }
                MapFadeTransitionManager.registerActiveMarkers(MapFadeTransitionManager.Layer.VALENBISI, valenbisiMarkerPairs)
            }

            // 5. Process Metro station markers
            if (p.showMetro && (p.currentZoom >= 11.5 || metroLayerAlpha > 0.005f || selectedMetroStation != null)) {
                val metroMarkerPairs = ArrayList<Pair<Marker, Float>>(p.validMetroStationsCount)
                for (i in 0 until p.validMetroStationsCount) {
                    val marker = p.recycledMetroMarkers[i]
                    val station = if (i < p.validMetroStations.size) p.validMetroStations[i] else null
                    val isSelected = selectedMetroStation != null && station?.name.equals(selectedMetroStation.name, ignoreCase = true)
                    val baseAlpha = if (isSelected) {
                        1.0f
                    } else if (emtHighlight.isHighlighted || isAnyNonMetroTransitSelected) {
                        secondaryAlpha
                    } else {
                        MetroStationHighlightManager.getMetroMarkerAlpha(station, metroHighlight)
                    }
                    metroMarkerPairs.add(Pair(marker, baseAlpha))
                    marker.alpha = if (isSelected) 1.0f else (baseAlpha * metroLayerAlpha)
                    if (isSelected) {
                        topSelectedTransitMarker = marker
                    } else if (p.currentZoom >= 11.5 || metroLayerAlpha > 0.005f) {
                        if (baseAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                    }
                }
                MapFadeTransitionManager.registerActiveMarkers(MapFadeTransitionManager.Layer.METRO, metroMarkerPairs)
            }

            // 6. Process Cercanías markers
            if (p.showCercanias || cercaniasLayerAlpha > 0.005f || selectedCercaniasStation != null) {
                val cercaniasMarkerPairs = ArrayList<Pair<Marker, Float>>(p.validCercaniasStationsCount)
                for (i in 0 until p.validCercaniasStationsCount) {
                    val marker = p.recycledCercaniasMarkers[i]
                    val stationEntity = marker.relatedObject as? com.example.data.database.CercaniasStationEntity
                    val isSelected = selectedCercaniasStation != null && (
                        (marker.position.latitude == selectedCercaniasStation.lat && marker.position.longitude == selectedCercaniasStation.lon) ||
                        marker.title?.equals(selectedCercaniasStation.displayName, ignoreCase = true) == true ||
                        marker.title?.equals(selectedCercaniasStation.nombre, ignoreCase = true) == true
                    )
                    val baseAlpha = if (isSelected) {
                        1.0f
                    } else if (cercaniasHighlight.isHighlighted) {
                        CercaniasStationHighlightManager.getCercaniasMarkerAlpha(stationEntity, cercaniasHighlight)
                    } else {
                        secondaryAlpha
                    }
                    cercaniasMarkerPairs.add(Pair(marker, baseAlpha))
                    marker.alpha = if (isSelected) 1.0f else (baseAlpha * cercaniasLayerAlpha)
                    if (isSelected) {
                        topSelectedTransitMarker = marker
                    } else if (p.showCercanias || cercaniasLayerAlpha > 0.005f) {
                        if (baseAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                    }
                }
                MapFadeTransitionManager.registerActiveMarkers(MapFadeTransitionManager.Layer.CERCANIAS, cercaniasMarkerPairs)
            }

            // 7. Process Custom Places markers (Home, Work, Favorites)
            if (p.activeCustomFavCount > 0 || customPlacesLayerAlpha > 0.005f) {
                val customFavPairs = ArrayList<Pair<Marker, Float>>(p.activeCustomFavCount)
                for (i in 0 until p.activeCustomFavCount) {
                    val marker = p.recycledCustomFavoriteMarkers[i]
                    customFavPairs.add(Pair(marker, secondaryAlpha))
                    marker.alpha = secondaryAlpha * customPlacesLayerAlpha
                    if (secondaryAlpha >= 0.7f) activeMarkers.add(marker) else dimmedMarkers.add(marker)
                }
                MapFadeTransitionManager.registerActiveMarkers(MapFadeTransitionManager.Layer.CUSTOM_PLACES, customFavPairs)
            }

            // --- LAYER ASSEMBLY ---
            // A. Base polygons (station footprints of unselected stations)
            if (p.showMetro && selectedMetroStation == null) {
                MetroStationFootprintsOverlayManager.addFootprintsToMap(
                    context = p.context,
                    mapView = p.mapView,
                    showMetro = p.showMetro,
                    zoomLevel = p.currentZoom,
                    selectedStation = null
                )
            }

            // B. Dimmed Metro line polylines
            if (p.showMetro) {
                MetroStationHighlightManager.addDimmedMetroPolylinesToMap(p.mapView, effectiveMetroHighlight)
            }

            // C. Dimmed Cercanías line polylines
            if (p.showCercanias) {
                val isOtherLayerHighlighted = metroHighlight.isHighlighted || emtHighlight.isHighlighted || mbHighlight.isHighlighted || isAnyNonMetroTransitSelected
                CercaniasStationHighlightManager.addDimmedCercaniasPolylinesToMap(
                    context = p.context,
                    mapView = p.mapView,
                    highlightState = cercaniasHighlight,
                    isAnyOtherLayerHighlighted = isOtherLayerHighlighted
                )
            }

            // D. ALL DIMMED MARKERS (transparent elements layered BEHIND active lines and active stops)
            dimmedMarkers.forEach { p.mapView.overlays.add(it) }

            // E. Active Metro station access points (escaleras/ascensores)
            if (p.showMetro) {
                MetroStationFootprintsOverlayManager.addAccessesToMap(
                    context = p.context,
                    mapView = p.mapView,
                    showMetro = p.showMetro,
                    zoomLevel = p.currentZoom,
                    selectedStation = selectedMetroStation,
                    isEmtHighlighted = emtHighlight.isHighlighted,
                    secondaryAlpha = secondaryAlpha,
                    metroStations = p.validMetroStations,
                    onSelectItem = p.onSelectItem
                )
            }

            // F. Selected Metro station footprint (prominent highlighted polygon)
            if (p.showMetro && selectedMetroStation != null) {
                MetroStationFootprintsOverlayManager.addFootprintsToMap(
                    context = p.context,
                    mapView = p.mapView,
                    showMetro = p.showMetro,
                    zoomLevel = p.currentZoom,
                    selectedStation = selectedMetroStation
                )
            }

            // G. ACTIVE TRANSIT POLYLINES (Drawn cleanly ON TOP of dimmed markers)
            if (p.showMetro) {
                MetroStationHighlightManager.addActiveMetroPolylinesToMap(p.mapView, effectiveMetroHighlight)
            }
            if (p.showCercanias) {
                CercaniasStationHighlightManager.addActiveCercaniasPolylinesToMap(
                    context = p.context,
                    mapView = p.mapView,
                    highlightState = cercaniasHighlight
                )
            }
            if (emtHighlight.isHighlighted) {
                EmtStationHighlightManager.addEmtPolylinesToMap(p.context, p.mapView, emtHighlight, p.currentZoom)
            }
            if (p.selectedMapItem is com.example.ui.map.SelectedMapItem.MetrobusStopItem) {
                val stopLoc = GeoPoint(p.selectedMapItem.stop.lat, p.selectedMapItem.stop.lon)
                MetrobusStationHighlightManager.addMetrobusPolylinesToMap(
                    mapView = p.mapView,
                    selectedMetrobusShapes = p.selectedMetrobusShapes,
                    currentZoom = p.currentZoom,
                    stopLocation = stopLoc,
                    selectedDirection = p.selectedDirectionFilter
                )
            } else {
                MetrobusStationHighlightManager.clearPolylines(p.mapView)
            }

            // H. ACTIVE TRANSIT STOPS (Stops on the active route - non-transparent, drawn ON TOP of the active line)
            activeMarkers.forEach { p.mapView.overlays.add(it) }

            // I. SELECTED STOP/STATION (The focused pin - top of transit elements)
            topSelectedTransitMarker?.let {
                it.alpha = 1.0f
                p.mapView.overlays.add(it)
            }
        } else {
            MetroStationFootprintsOverlayManager.clearFromMap(p.mapView)
            MapFadeTransitionManager.updateLayerVisibility(
                mapView = p.mapView,
                isBusVisible = false,
                isMetrobusVisible = false,
                isValenbisiVisible = false,
                isMetroVisible = false,
                isCercaniasVisible = false,
                isCustomPlacesVisible = false
            )
        }

        // Draw multimodal active itinerary polylines and markers
        if (p.selectedItinerary != null) {
            updatedZoomedId = ItineraryMapRenderer.renderItinerary(
                context = p.context,
                mapView = p.mapView,
                itinerary = p.selectedItinerary,
                currentZoom = p.currentZoom,
                isDarkMode = p.isDarkMode,
                lastZoomedItineraryId = p.lastZoomedItineraryId
            )
        } else {
            updatedZoomedId = null
        }

        // Add destination marker on top of transit stops when not previewing an itinerary
        p.destinationMarker?.let {
            if (p.destinationLocation != null && p.selectedItinerary == null) {
                p.mapView.overlays.add(it)
            }
        }

        // Add user location marker on top of everything
        p.userMarker?.let {
            if (p.userLocation != null) {
                try {
                    p.mapView.overlays.add(it)
                } catch (_: Exception) {}
            }
        }

        try {
            if (p.mapView.isAttachedToWindow || p.mapView.parent != null) {
                p.mapView.invalidate()
            }
        } catch (_: Exception) {}

        return updatedZoomedId
    }
}
