package com.example.ui.map.components

import android.content.Context
import android.graphics.Point
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtRoute
import com.example.ui.bus.MetrobusStop
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Handles map tap hit-testing and disambiguation when users tap on or near markers.
 */
object MapTapHandler {

    data class TapContext(
        val showMetro: Boolean,
        val showCercanias: Boolean,
        val showBus: Boolean,
        val showMetrobus: Boolean,
        val showValenbisi: Boolean,
        val currentZoomLevel: Double,
        val isOnlyMetroSelected: Boolean,
        val isOnlyCercaniasSelected: Boolean,
        val currentMetroStations: List<MetroStation>,
        val currentCercaniasStations: List<CercaniasStationEntity>,
        val currentBusStopsInViewport: List<GeoportalStopEntity>,
        val currentMetrobusStopsInViewport: List<MetrobusStopEntity>,
        val currentValenbisiStations: List<ValenbisiStation>,
        val currentMetroPositions: Map<String, GeoPoint>,
        val currentCercaniasPositions: Map<String, GeoPoint>,
        val currentDestinationLocation: GeoPoint?,
        val currentDestinationTitle: String?,
        val currentCustomFavorites: List<RecentSearch>,
        val currentOnSelectItem: ((SelectedMapItem) -> Unit)?,
        val currentOnMapClick: (() -> Unit)?,
        val currentOnShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)?
    )

    fun handleTapAtGeoPoint(
        context: Context,
        mapView: MapView,
        p: GeoPoint,
        tc: TapContext
    ): Boolean {
        val density = context.resources.displayMetrics.density
        val thresholdPx = 44f * density // 44dp touch radius
        val thresholdSq = (thresholdPx * thresholdPx).toDouble()

        val tapPixel = Point()
        mapView.projection.toPixels(p, tapPixel)

        val candidates = mutableListOf<SelectedMapItem>()
        val markerPixel = Point()

        // Check Metro stations
        val isMetroVisibleOnMap = tc.showMetro && (tc.currentZoomLevel >= 11.5 || tc.isOnlyMetroSelected)
        if (isMetroVisibleOnMap) {
            tc.currentMetroStations.forEach { station ->
                val lat = station.latitude
                val lon = station.longitude
                if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                    val pos = tc.currentMetroPositions[station.name] ?: GeoPoint(lat, lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = (tapPixel.x - markerPixel.x).toDouble()
                    val dy = (tapPixel.y - markerPixel.y).toDouble()
                    if (dx * dx + dy * dy <= thresholdSq) {
                        candidates.add(SelectedMapItem.Metro(station))
                    }
                }
            }
        }

        // Check Cercanias stations
        val isCercaniasVisibleOnMap = tc.showCercanias && (tc.currentZoomLevel >= 11.5 || tc.isOnlyCercaniasSelected)
        if (isCercaniasVisibleOnMap) {
            tc.currentCercaniasStations.forEach { station ->
                if (station.lat != 0.0 && station.lon != 0.0) {
                    val pos = tc.currentCercaniasPositions[station.stop_id] ?: GeoPoint(station.lat, station.lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = (tapPixel.x - markerPixel.x).toDouble()
                    val dy = (tapPixel.y - markerPixel.y).toDouble()
                    if (dx * dx + dy * dy <= thresholdSq) {
                        candidates.add(SelectedMapItem.Cercanias(station))
                    }
                }
            }
        }

        // Check Valenbisi stations (only when visible at current zoom level)
        val isOnlyValenbisi = tc.showValenbisi && !tc.showBus && !tc.showMetro && !tc.showCercanias && !tc.showMetrobus
        val isValenbisiVisibleAtZoom = if (isOnlyValenbisi) tc.currentZoomLevel >= 13.5 else tc.currentZoomLevel >= 15.2
        if (tc.showValenbisi && isValenbisiVisibleAtZoom) {
            tc.currentValenbisiStations.forEach { station ->
                if (station.latitude != 0.0 && station.longitude != 0.0) {
                    val pos = GeoPoint(station.latitude, station.longitude)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = (tapPixel.x - markerPixel.x).toDouble()
                    val dy = (tapPixel.y - markerPixel.y).toDouble()
                    if (dx * dx + dy * dy <= thresholdSq) {
                        candidates.add(SelectedMapItem.Valenbisi(station))
                    }
                }
            }
        }

        // Check Merged EMT + Metrobús stop groups
        if ((tc.showBus || tc.showMetrobus) && (tc.currentBusStopsInViewport.isNotEmpty() || tc.currentMetrobusStopsInViewport.isNotEmpty())) {
            val mergedGroups = BusMarkersRenderer.findMergedStopGroups(
                busStopsInViewport = tc.currentBusStopsInViewport,
                metrobusStopsInViewport = tc.currentMetrobusStopsInViewport,
                selectedMapItem = null,
                showBus = tc.showBus,
                showMetrobus = tc.showMetrobus,
                isFavoritesMode = false,
                currentZoom = tc.currentZoomLevel
            )
            mergedGroups.forEach { group ->
                val avgLat = (group.busStop.lat + group.mbStop.lat) / 2.0
                val avgLon = (group.busStop.lon + group.mbStop.lon) / 2.0
                val pos = GeoPoint(avgLat, avgLon)
                mapView.projection.toPixels(pos, markerPixel)
                val dx = (tapPixel.x - markerPixel.x).toDouble()
                val dy = (tapPixel.y - markerPixel.y).toDouble()
                if (dx * dx + dy * dy <= thresholdSq) {
                    if (tc.showBus) {
                        val linesList = (group.busStop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { EmtRoute(id_linea = it, SN = it) }

                        val emtModel = EmtBusStop(
                            t = group.busStop.denominacion,
                            n = group.busStop.denominacion,
                            me = group.busStop.denominacion,
                            utes = linesList,
                            opId = group.busStop.id_parada,
                            ica = group.busStop.id_parada
                        )
                        candidates.add(SelectedMapItem.BusStop(group.busStop, emtModel))
                    }
                    if (tc.showMetrobus) {
                        val linesList = (group.mbStop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val metrobusModel = MetrobusStop(
                            idParada = group.mbStop.id_parada,
                            denominacion = group.mbStop.denominacion,
                            lat = group.mbStop.lat,
                            lon = group.mbStop.lon,
                            lineas = linesList
                        )
                        candidates.add(SelectedMapItem.MetrobusStopItem(group.mbStop, metrobusModel))
                    }
                }
            }
        }

        // Check Bus stops
        if (tc.showBus && (tc.currentBusStopsInViewport.size <= 50 || tc.currentZoomLevel >= 13.5)) {
            tc.currentBusStopsInViewport.forEach { stop ->
                if (stop.lat != 0.0 && stop.lon != 0.0) {
                    val pos = GeoPoint(stop.lat, stop.lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = (tapPixel.x - markerPixel.x).toDouble()
                    val dy = (tapPixel.y - markerPixel.y).toDouble()
                    if (dx * dx + dy * dy <= thresholdSq) {
                        val linesList = (stop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { EmtRoute(id_linea = it, SN = it) }

                        val emtModel = EmtBusStop(
                            t = stop.denominacion,
                            n = stop.denominacion,
                            me = stop.denominacion,
                            utes = linesList,
                            opId = stop.id_parada,
                            ica = stop.id_parada
                        )
                        candidates.add(SelectedMapItem.BusStop(stop, emtModel))
                    }
                }
            }
        }

        // Check Metrobus stops
        if (tc.showMetrobus && (tc.currentMetrobusStopsInViewport.size <= 50 || tc.currentZoomLevel >= 13.5)) {
            tc.currentMetrobusStopsInViewport.forEach { stop ->
                if (stop.lat != 0.0 && stop.lon != 0.0) {
                    val pos = GeoPoint(stop.lat, stop.lon)
                    mapView.projection.toPixels(pos, markerPixel)
                    val dx = (tapPixel.x - markerPixel.x).toDouble()
                    val dy = (tapPixel.y - markerPixel.y).toDouble()
                    if (dx * dx + dy * dy <= thresholdSq) {
                        val linesList = (stop.lineas ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val metrobusModel = MetrobusStop(
                            idParada = stop.id_parada,
                            denominacion = stop.denominacion,
                            lat = stop.lat,
                            lon = stop.lon,
                            lineas = linesList
                        )
                        candidates.add(SelectedMapItem.MetrobusStopItem(stop, metrobusModel))
                    }
                }
            }
        }

        // Check Destination Pin
        val destLoc = tc.currentDestinationLocation
        if (destLoc != null) {
            mapView.projection.toPixels(destLoc, markerPixel)
            val dx = (tapPixel.x - markerPixel.x).toDouble()
            val dy = (tapPixel.y - markerPixel.y).toDouble()
            if (dx * dx + dy * dy <= thresholdSq) {
                val destItem = SelectedMapItem.Address(
                    NominatimResult(
                        displayName = tc.currentDestinationTitle ?: "Destino",
                        latitude = destLoc.latitude,
                        longitude = destLoc.longitude,
                        type = "address",
                        category = "place",
                        isLocalStop = false,
                        stopId = null,
                        stopType = null
                    )
                )
                candidates.add(destItem)
            }
        }

        // Check Custom Favorites
        tc.currentCustomFavorites.forEach { fav ->
            val pos = GeoPoint(fav.latitude, fav.longitude)
            mapView.projection.toPixels(pos, markerPixel)
            val dx = (tapPixel.x - markerPixel.x).toDouble()
            val dy = (tapPixel.y - markerPixel.y).toDouble()
            if (dx * dx + dy * dy <= thresholdSq) {
                val favItem = SelectedMapItem.Address(
                    NominatimResult(
                        displayName = if (fav.subtitle.isNotEmpty()) fav.title + ", " + fav.subtitle else fav.title,
                        latitude = fav.latitude,
                        longitude = fav.longitude,
                        type = "favorite",
                        category = "favorite",
                        isLocalStop = false,
                        stopId = null,
                        stopType = null,
                        placeCategory = PlaceCategory.FAVORITE,
                        placeName = fav.title
                    )
                )
                candidates.add(favItem)
            }
        }

        val uniqueCandidates = candidates.distinctBy { item ->
            when (item) {
                is SelectedMapItem.Metro -> "METRO_${item.station.id}_${item.station.name}"
                is SelectedMapItem.Cercanias -> "CERCANIAS_${item.station.stop_id}"
                is SelectedMapItem.BusStop -> "BUS_${item.stop.id_parada}"
                is SelectedMapItem.MetrobusStopItem -> "METROBUS_${item.stop.id_parada}"
                is SelectedMapItem.Valenbisi -> "VALENBISI_${item.station.gid}"
                is SelectedMapItem.Address -> "ADDR_${item.result.latitude}_${item.result.longitude}"
            }
        }

        when {
            uniqueCandidates.isEmpty() -> {
                tc.currentOnMapClick?.invoke()
            }
            uniqueCandidates.size == 1 -> {
                tc.currentOnSelectItem?.invoke(uniqueCandidates.first())
            }
            else -> {
                val onShowMenu = tc.currentOnShowDisambiguationMenu
                if (onShowMenu != null) {
                    onShowMenu.invoke(uniqueCandidates)
                } else {
                    tc.currentOnSelectItem?.invoke(uniqueCandidates.first())
                }
            }
        }
        return true
    }
}
