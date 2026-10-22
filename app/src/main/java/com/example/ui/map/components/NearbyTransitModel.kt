package com.example.ui.map.components

import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.ui.bus.EmtBusStop

data class ValenbisiStation(
    val gid: Int,
    val name: String,
    val number: Int,
    val address: String,
    val open: Boolean,
    val available: Int,
    val free: Int,
    val total: Int,
    val ticket: Boolean,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double = 0.0,
    val distanceText: String = ""
)

sealed class NearbyTransitItem {
    abstract val distanceMeters: Double
    abstract val displayName: String
    abstract val key: String
    abstract val isFavorite: Boolean

    data class Bus(
        val stop: GeoportalStopEntity,
        val emtStopModel: EmtBusStop,
        override val distanceMeters: Double,
        override val isFavorite: Boolean = false
    ) : NearbyTransitItem() {
        override val displayName: String = stop.denominacion
        override val key: String = "BUS_${stop.id_parada}"
    }

    data class Metro(
        val station: MetroStation,
        override val distanceMeters: Double,
        override val isFavorite: Boolean = false
    ) : NearbyTransitItem() {
        override val displayName: String = station.name
        override val key: String = "METRO_${station.id}"
    }

    data class Cercanias(
        val station: CercaniasStationEntity,
        override val distanceMeters: Double,
        override val isFavorite: Boolean = false
    ) : NearbyTransitItem() {
        override val displayName: String = station.displayName
        override val key: String = "CERCANIAS_${station.stop_id}"
    }

    data class Metrobus(
        val stop: com.example.data.database.MetrobusStopEntity,
        val metrobusModel: com.example.ui.bus.MetrobusStop,
        override val distanceMeters: Double,
        override val isFavorite: Boolean = false
    ) : NearbyTransitItem() {
        override val displayName: String = stop.denominacion
        override val key: String = "METROBUS_${stop.id_parada}"
    }
}
