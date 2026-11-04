package com.example.ui.map.google

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.NominatimResult
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtRoute
import com.example.ui.bus.MetrobusStop
import com.example.ui.cercanias.LiveVehicleInfo
import com.example.ui.map.MapFilter
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.MetroStationAccessMatcher
import com.example.ui.map.components.ValenbisiStation
import org.osmdroid.util.GeoPoint

/**
 * Production-ready Google Maps Container for MapScreen.
 * Provides:
 * 1. Transit layer parity with OSMdroid (Metro, Cercanías, EMT, Metrobús, Valenbisi, Live Trains).
 * 2. Optical bottom padding for sheets without distorting GPS coordinates.
 * 3. Bidirectional SelectedMapItem integration.
 * 4. Anti-flash theme skeleton with smooth 250ms fade-out upon onMapLoaded.
 * 5. Full itinerary routing and tap disambiguation support.
 */
@Composable
fun GoogleMapViewContainer(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    isSatelliteMode: Boolean = false,
    cameraTarget: GeoPoint = org.osmdroid.util.GeoPoint(39.4699, -0.3763),
    cameraZoom: Double = 13.5,
    cameraAnimTrigger: Int = 0,
    zoomInTrigger: Int = 0,
    zoomOutTrigger: Int = 0,
    userLocation: GeoPoint? = null,
    busStops: List<GeoportalStopEntity> = emptyList(),
    metrobusStops: List<MetrobusStopEntity> = emptyList(),
    metroStations: List<MetroStation> = emptyList(),
    cercaniasStations: List<CercaniasStationEntity> = emptyList(),
    valenbisiStations: List<ValenbisiStation> = emptyList(),
    liveCercaniasVehicles: List<LiveVehicleInfo> = emptyList(),
    selectedItinerary: PlannedItinerary? = null,
    homeLocation: RecentSearch? = null,
    workLocation: RecentSearch? = null,
    customFavorites: List<RecentSearch> = emptyList(),
    destinationLocation: GeoPoint? = null,
    destinationTitle: String? = null,
    mapFilter: MapFilter = MapFilter.DEFAULT,
    selectedMapItem: SelectedMapItem? = null,
    selectedBusLineFilters: Set<String> = emptySet(),
    selectedDirectionFilter: String? = null,
    selectedMetrobusShapes: Map<String, List<GeoPoint>> = emptyMap(),
    bottomPanelOffsetPx: Float = 0f,
    busStopAliases: Map<String, String> = emptyMap(),
    favoriteBusStops: Set<String> = emptySet(),
    favoriteMetroStations: Set<String> = emptySet(),
    favoriteCercaniasStations: Set<String> = emptySet(),
    favoriteValenbisiStations: Set<String> = emptySet(),
    isCellTowerLocation: Boolean = false,
    userBearing: Float? = null,
    onSelectItem: (SelectedMapItem) -> Unit,
    onMapClick: () -> Unit,
    onMapLongClick: ((GeoPoint) -> Unit)? = null,
    onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null,
    onMapTouch: (() -> Unit)? = null,
    onMapPan: (() -> Unit)? = null,
    onCameraPositionChanged: ((GeoPoint, Double) -> Unit)? = null,
    onZoomLevelChanged: ((Double) -> Unit)? = null
) {
    val density = LocalDensity.current
    val bottomPaddingDp = with(density) { bottomPanelOffsetPx.toDp() }

    var isMapLoaded by remember { mutableStateOf(false) }

    // Smooth transition safeguard: dismiss skeleton mask if onMapLoaded takes more than 1000ms
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        isMapLoaded = true
    }

    // Convert SelectedMapItem to GoogleMapTransitItem for visual highlight
    val selectedTransitItem = remember(selectedMapItem) {
        when (selectedMapItem) {
            is SelectedMapItem.Metro -> GoogleMapTransitItem.Metro(selectedMapItem.station)
            is SelectedMapItem.Cercanias -> GoogleMapTransitItem.Cercanias(selectedMapItem.station)
            is SelectedMapItem.BusStop -> GoogleMapTransitItem.Bus(selectedMapItem.stop)
            is SelectedMapItem.MetrobusStopItem -> GoogleMapTransitItem.Metrobus(selectedMapItem.stop)
            is SelectedMapItem.Valenbisi -> GoogleMapTransitItem.Valenbisi(selectedMapItem.station)
            is SelectedMapItem.Address -> GoogleMapTransitItem.CustomPlace(selectedMapItem)
            is SelectedMapItem.LiveTrain -> GoogleMapTransitItem.Train(selectedMapItem.vehicle)
            else -> null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
    ) {
        GoogleMapsTestMapView(
            modifier = Modifier.fillMaxSize(),
            isDarkMode = isDarkMode,
            isSatelliteMode = isSatelliteMode,
            mapFilter = mapFilter,
            showMetroLines = mapFilter.isFavorites || mapFilter.showMetro,
            showCercaniasLines = mapFilter.isFavorites || mapFilter.showCercanias,
            showMetroFootprints = mapFilter.showMetro,
            showMetroStations = mapFilter.isFavorites || mapFilter.showMetro,
            showCercaniasStations = mapFilter.isFavorites || mapFilter.showCercanias,
            showBusStops = mapFilter.isFavorites || mapFilter.showBus,
            showMetrobusStops = mapFilter.isFavorites || mapFilter.showMetrobus,
            showValenbisi = mapFilter.isFavorites || mapFilter.showValenbisi,
            metroStations = metroStations,
            cercaniasStations = cercaniasStations,
            busStops = busStops,
            metrobusStops = metrobusStops,
            valenbisiStations = valenbisiStations,
            liveCercaniasVehicles = liveCercaniasVehicles,
            selectedItinerary = selectedItinerary,
            homeLocation = homeLocation,
            workLocation = workLocation,
            customFavorites = customFavorites,
            destinationLocation = destinationLocation,
            destinationTitle = destinationTitle,
            userLocation = userLocation,
            isCellTowerLocation = isCellTowerLocation,
            userBearing = userBearing,
            selectedItem = selectedTransitItem,
            selectedMapItem = selectedMapItem,
            selectedBusLineFilters = selectedBusLineFilters,
            selectedDirectionFilter = selectedDirectionFilter,
            selectedMetrobusShapes = selectedMetrobusShapes,
            bottomPaddingDp = bottomPaddingDp,
            cameraTarget = cameraTarget,
            cameraZoom = cameraZoom,
            cameraAnimTrigger = cameraAnimTrigger,
            zoomInTrigger = zoomInTrigger,
            zoomOutTrigger = zoomOutTrigger,
            busStopAliases = busStopAliases,
            favoriteBusStops = favoriteBusStops,
            favoriteMetroStations = favoriteMetroStations,
            favoriteCercaniasStations = favoriteCercaniasStations,
            favoriteValenbisiStations = favoriteValenbisiStations,
            onItemClick = { item ->
                when (item) {
                    is GoogleMapTransitItem.Metro -> {
                        onSelectItem(SelectedMapItem.Metro(item.station))
                    }
                    is GoogleMapTransitItem.Cercanias -> {
                        onSelectItem(SelectedMapItem.Cercanias(item.station))
                    }
                    is GoogleMapTransitItem.Bus -> {
                        val linesList = (item.stop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { EmtRoute(id_linea = it, SN = it) }
                        val emtModel = EmtBusStop(
                            t = item.stop.denominacion,
                            n = item.stop.denominacion,
                            me = item.stop.denominacion,
                            utes = linesList,
                            opId = item.stop.id_parada,
                            ica = item.stop.id_parada
                        )
                        onSelectItem(SelectedMapItem.BusStop(item.stop, emtModel))
                    }
                    is GoogleMapTransitItem.Metrobus -> {
                        val linesList = (item.stop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val metrobusModel = MetrobusStop(
                            idParada = item.stop.id,
                            denominacion = item.stop.denominacion,
                            lat = item.stop.latitud,
                            lon = item.stop.longitud,
                            lineas = linesList
                        )
                        onSelectItem(SelectedMapItem.MetrobusStopItem(item.stop, metrobusModel))
                    }
                    is GoogleMapTransitItem.Merged -> {
                        val linesList = (item.busStop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { EmtRoute(id_linea = it, SN = it) }
                        val emtModel = EmtBusStop(
                            t = item.busStop.denominacion,
                            n = item.busStop.denominacion,
                            me = item.busStop.denominacion,
                            utes = linesList,
                            opId = item.busStop.id_parada,
                            ica = item.busStop.id_parada
                        )
                        onSelectItem(SelectedMapItem.BusStop(item.busStop, emtModel))
                    }
                    is GoogleMapTransitItem.Valenbisi -> {
                        onSelectItem(SelectedMapItem.Valenbisi(item.station))
                    }
                    is GoogleMapTransitItem.CustomPlace -> {
                        onSelectItem(item.item)
                    }
                    is GoogleMapTransitItem.Train -> {
                        onSelectItem(SelectedMapItem.LiveTrain(item.vehicle))
                    }
                    is GoogleMapTransitItem.AccessPoint -> {
                        val match = metroStations.find {
                            MetroStationAccessMatcher.matchesStation(item.access.stationName, it)
                        }
                        if (match != null) {
                            onSelectItem(SelectedMapItem.Metro(match))
                        }
                    }
                    else -> {}
                }
            },
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onShowDisambiguationMenu = onShowDisambiguationMenu,
            onMapTouch = onMapTouch,
            onMapPan = onMapPan,
            onCameraPositionChanged = onCameraPositionChanged,
            onZoomLevelChanged = onZoomLevelChanged,
            onMapReady = {
                isMapLoaded = true
            }
        )

        // Smooth skeleton/mask layer with exact map ground background to prevent white surface flash
        AnimatedVisibility(
            visible = !isMapLoaded,
            enter = fadeIn(tween(80)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
