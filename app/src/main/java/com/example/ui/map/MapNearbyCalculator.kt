package com.example.ui.map

import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtRoute
import com.example.ui.bus.MetrobusStop
import com.example.ui.map.components.NearbyTransitItem
import com.example.ui.map.components.ValenbisiStation
import com.example.util.LocationUtils
import org.osmdroid.util.GeoPoint

object MapNearbyCalculator {

    fun calculateNearbyValenbisi(
        center: GeoPoint,
        stations: List<ValenbisiStation>
    ): List<ValenbisiStation> {
        return stations.map { station ->
            val dist = LocationUtils.calculateDistanceMeters(
                center.latitude,
                center.longitude,
                station.latitude,
                station.longitude
            )
            val distText = if (dist >= 1000) {
                String.format("%.1f km", dist / 1000.0)
            } else {
                "${dist.toInt()} m"
            }
            station.copy(distanceMeters = dist, distanceText = distText)
        }.sortedBy { it.distanceMeters }
    }

    fun calculateNearbyTransitItems(
        center: GeoPoint,
        busStops: List<GeoportalStopEntity>,
        metroStations: List<MetroStation>,
        cercaniasStations: List<CercaniasStationEntity>,
        metrobusStops: List<MetrobusStopEntity>,
        filter: MapFilter,
        favBuses: Set<String>,
        favMetros: Set<String>,
        favCercanias: Set<String>,
        favMetrobus: Set<String>
    ): List<NearbyTransitItem> {
        val maxBusDistance = 600.0
        val maxMetroDistance = 1000.0
        val maxCercaniasDistance = 1750.0
        val maxMetrobusDistance = 1000.0

        val busCandidates = mutableListOf<NearbyTransitItem.Bus>()
        val metroCandidates = mutableListOf<NearbyTransitItem.Metro>()
        val cercaniasCandidates = mutableListOf<NearbyTransitItem.Cercanias>()
        val metrobusCandidates = mutableListOf<NearbyTransitItem.Metrobus>()

        // 1. Bus Stops
        val includeBus = filter.isFavorites || filter.showBus
        if (includeBus) {
            busStops.forEach { stop ->
                val isFav = stop.id_parada in favBuses
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(
                        center.latitude,
                        center.longitude,
                        stop.lat,
                        stop.lon
                    )
                    if (dist <= maxBusDistance) {
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
                        busCandidates.add(NearbyTransitItem.Bus(stop, emtModel, dist, isFavorite = isFav))
                    }
                }
            }
        }

        // 2. Metro Stations
        val includeMetro = filter.isFavorites || filter.showMetro
        if (includeMetro) {
            metroStations.forEach { station ->
                val isFav = station.id in favMetros
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(
                        center.latitude,
                        center.longitude,
                        station.latitude,
                        station.longitude
                    )
                    if (dist <= maxMetroDistance) {
                        metroCandidates.add(NearbyTransitItem.Metro(station, dist, isFavorite = isFav))
                    }
                }
            }
        }

        // 3. Cercanias Stations
        val includeCercanias = filter.isFavorites || filter.showCercanias
        if (includeCercanias) {
            cercaniasStations.forEach { station ->
                val isFav = station.stop_id in favCercanias
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(
                        center.latitude,
                        center.longitude,
                        station.lat,
                        station.lon
                    )
                    if (dist <= maxCercaniasDistance) {
                        cercaniasCandidates.add(NearbyTransitItem.Cercanias(station, dist, isFavorite = isFav))
                    }
                }
            }
        }

        // 4. Metrobus Stops
        val includeMetrobus = filter.isFavorites || filter.showMetrobus
        if (includeMetrobus) {
            metrobusStops.forEach { stop ->
                val isFav = stop.id_parada in favMetrobus
                if (!filter.isFavorites || isFav) {
                    val dist = LocationUtils.calculateDistanceMeters(
                        center.latitude,
                        center.longitude,
                        stop.lat,
                        stop.lon
                    )
                    if (dist <= maxMetrobusDistance) {
                        val linesList = (stop.lineas ?: "").split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        val metrobusModel = MetrobusStop(
                            idParada = stop.id_parada,
                            denominacion = stop.denominacion,
                            lat = stop.lat,
                            lon = stop.lon,
                            lineas = linesList
                        )
                        metrobusCandidates.add(NearbyTransitItem.Metrobus(stop, metrobusModel, dist, isFavorite = isFav))
                    }
                }
            }
        }

        val finalBuses = busCandidates.sortedWith(
            compareByDescending<NearbyTransitItem.Bus> { it.isFavorite && it.distanceMeters < 400.0 }
                .thenBy { it.distanceMeters }
        ).take(5)
        val finalMetros = metroCandidates.sortedWith(
            compareByDescending<NearbyTransitItem.Metro> { it.isFavorite && it.distanceMeters < 400.0 }
                .thenBy { it.distanceMeters }
        ).take(3)
        val finalCercanias = cercaniasCandidates.sortedWith(
            compareByDescending<NearbyTransitItem.Cercanias> { it.isFavorite && it.distanceMeters < 400.0 }
                .thenBy { it.distanceMeters }
        ).take(2)
        val finalMetrobus = metrobusCandidates.sortedWith(
            compareByDescending<NearbyTransitItem.Metrobus> { it.isFavorite && it.distanceMeters < 400.0 }
                .thenBy { it.distanceMeters }
        ).take(3)

        val maxCombinedCount = if (filter.showMetrobus) 12 else 10

        val combined = finalBuses + finalMetrobus + finalMetros + finalCercanias
        return combined.sortedWith(
            compareByDescending<NearbyTransitItem> { it.isFavorite && it.distanceMeters < 400.0 }
                .thenBy { it.distanceMeters }
        ).take(maxCombinedCount)
    }
}
