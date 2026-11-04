package com.example.ui.map.google

import androidx.compose.ui.graphics.Color
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.ui.cercanias.LiveVehicleInfo
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.MetroStationFootprintsOverlayManager
import com.example.ui.map.components.ValenbisiStation
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.util.GeoPoint

data class GoogleMapPolyline(
    val points: List<LatLng>,
    val color: Color,
    val width: Float,
    val lineRef: String = ""
)

data class GoogleMapStationFootprint(
    val points: List<LatLng>,
    val stationName: String,
    val centerLat: Double,
    val centerLon: Double
)

sealed interface GoogleMapTransitItem {
    val title: String
    val lat: Double
    val lon: Double

    data class Metro(val station: MetroStation) : GoogleMapTransitItem {
        override val title: String get() = station.name
        override val lat: Double get() = station.latitude
        override val lon: Double get() = station.longitude
    }

    data class Cercanias(val station: CercaniasStationEntity) : GoogleMapTransitItem {
        override val title: String get() = station.nombre
        override val lat: Double get() = station.lat
        override val lon: Double get() = station.lon
    }

    data class Bus(val stop: GeoportalStopEntity) : GoogleMapTransitItem {
        override val title: String get() = stop.denominacion
        override val lat: Double get() = stop.lat
        override val lon: Double get() = stop.lon
    }

    data class Metrobus(val stop: MetrobusStopEntity) : GoogleMapTransitItem {
        override val title: String get() = stop.denominacion
        override val lat: Double get() = stop.latitud
        override val lon: Double get() = stop.longitud
    }

    data class Merged(val busStop: GeoportalStopEntity, val mbStop: MetrobusStopEntity) : GoogleMapTransitItem {
        override val title: String get() = busStop.denominacion
        override val lat: Double get() = busStop.lat
        override val lon: Double get() = busStop.lon
    }

    data class Valenbisi(val station: ValenbisiStation) : GoogleMapTransitItem {
        override val title: String get() = station.name
        override val lat: Double get() = station.latitude
        override val lon: Double get() = station.longitude
    }

    data class CustomPlace(val item: SelectedMapItem.Address) : GoogleMapTransitItem {
        override val title: String get() = item.result.display_name
        override val lat: Double get() = item.result.lat.toDoubleOrNull() ?: 0.0
        override val lon: Double get() = item.result.lon.toDoubleOrNull() ?: 0.0
    }

    data class Train(val vehicle: LiveVehicleInfo) : GoogleMapTransitItem {
        override val title: String get() = "Tren ${vehicle.trainNum}"
        override val lat: Double get() = vehicle.latitude ?: 0.0
        override val lon: Double get() = vehicle.longitude ?: 0.0
    }

    data class Cluster(
        val count: Int,
        val centerLat: Double,
        val centerLon: Double,
        val isMetrobus: Boolean
    ) : GoogleMapTransitItem {
        override val title: String get() = "$count Paradas de ${if (isMetrobus) "Metrobús" else "EMT"}"
        override val lat: Double get() = centerLat
        override val lon: Double get() = centerLon
    }

    data class AccessPoint(
        val access: MetroStationFootprintsOverlayManager.AccessPointData
    ) : GoogleMapTransitItem {
        override val title: String get() = access.labelText
        override val lat: Double get() = access.geoPoint.latitude
        override val lon: Double get() = access.geoPoint.longitude
    }
}

data class RouteChevronArrow(
    val position: LatLng,
    val bearing: Float,
    val isMetrobus: Boolean
)

fun samplePolylineDirectionArrows(
    polylines: List<GoogleMapPolyline>,
    intervalMeters: Double = 450.0,
    initialOffsetMeters: Double = 120.0,
    isMetrobus: Boolean = false
): List<RouteChevronArrow> {
    if (polylines.isEmpty()) return emptyList()
    val arrows = mutableListOf<RouteChevronArrow>()

    for (line in polylines) {
        val points = line.points
        if (points.size < 2) continue

        var accumulatedDist = 0.0
        var nextTargetDist = initialOffsetMeters

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val dLat = Math.toRadians(p2.latitude - p1.latitude)
            val dLon = Math.toRadians(p2.longitude - p1.longitude)
            val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                    Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                    Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
            val segDist = 6371000.0 * 2.0 * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))

            if (segDist <= 0.5) continue

            while (accumulatedDist + segDist >= nextTargetDist) {
                val t = ((nextTargetDist - accumulatedDist) / segDist).coerceIn(0.0, 1.0)
                val lat = p1.latitude + t * (p2.latitude - p1.latitude)
                val lon = p1.longitude + t * (p2.longitude - p1.longitude)

                val lat1Rad = Math.toRadians(p1.latitude)
                val lon1Rad = Math.toRadians(p1.longitude)
                val lat2Rad = Math.toRadians(p2.latitude)
                val lon2Rad = Math.toRadians(p2.longitude)
                val y = Math.sin(lon2Rad - lon1Rad) * Math.cos(lat2Rad)
                val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(lon2Rad - lon1Rad)
                val bearing = ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()

                arrows.add(
                    RouteChevronArrow(
                        position = LatLng(lat, lon),
                        bearing = bearing,
                        isMetrobus = isMetrobus
                    )
                )

                nextTargetDist += intervalMeters
            }

            accumulatedDist += segDist
        }
    }
    return arrows
}

fun snapToTrackPolyline(
    lat: Double,
    lon: Double,
    trackPoints: List<List<GeoPoint>>
): LatLng {
    if (trackPoints.isEmpty()) return LatLng(lat, lon)
    var closestLat = lat
    var closestLon = lon
    var minDistanceSq = Double.MAX_VALUE

    for (segment in trackPoints) {
        for (p in segment) {
            val dLat = p.latitude - lat
            val dLon = p.longitude - lon
            val distSq = dLat * dLat + dLon * dLon
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestLat = p.latitude
                closestLon = p.longitude
            }
        }
    }
    return if (minDistanceSq < 0.000100) LatLng(closestLat, closestLon) else LatLng(lat, lon)
}
