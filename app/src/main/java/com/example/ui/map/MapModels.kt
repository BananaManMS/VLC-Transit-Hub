package com.example.ui.map

import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.NominatimResult
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.MetrobusStop
import com.example.ui.cercanias.LiveVehicleInfo
import com.example.ui.map.components.ValenbisiStation

enum class MapFilterType {
    FAVORITES,
    BUS,
    METROBUS,
    METRO,
    CERCANIAS,
    VALENBISI
}

data class MapFilter(
    val isFavorites: Boolean = false,
    val showBus: Boolean = true,
    val showMetrobus: Boolean = true,
    val showMetro: Boolean = true,
    val showCercanias: Boolean = true,
    val showValenbisi: Boolean = false
) {
    companion object {
        val DEFAULT = MapFilter(isFavorites = false, showBus = true, showMetrobus = true, showMetro = true, showCercanias = true, showValenbisi = false)
        val FAVORITES = MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
        val BUS_ONLY = MapFilter(isFavorites = false, showBus = true, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
        val METROBUS_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = true, showMetro = false, showCercanias = false, showValenbisi = false)
        val METRO_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = true, showCercanias = false, showValenbisi = false)
        val CERCANIAS_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = true, showValenbisi = false)
        val VALENBISI_ONLY = MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = true)
        val SHOW_ALL = MapFilter(isFavorites = false, showBus = true, showMetrobus = true, showMetro = true, showCercanias = true, showValenbisi = true)
    }
}
sealed class SelectedMapItem {
    data class BusStop(val stop: GeoportalStopEntity, val emtStopModel: EmtBusStop) : SelectedMapItem()
    data class MetrobusStopItem(val stop: MetrobusStopEntity, val metrobusModel: MetrobusStop) : SelectedMapItem()
    data class Metro(val station: MetroStation) : SelectedMapItem()
    data class Cercanias(val station: CercaniasStationEntity) : SelectedMapItem()
    data class Valenbisi(val station: ValenbisiStation) : SelectedMapItem()
    data class Address(val result: NominatimResult) : SelectedMapItem()
    data class LiveTrain(val vehicle: LiveVehicleInfo) : SelectedMapItem()
}

sealed class MapSearchResult {
    abstract val score: Double
    data class BusStop(val stop: GeoportalStopEntity, val alias: String? = null, override val score: Double = 0.0, val isFavorite: Boolean = false) : MapSearchResult()
    data class MetrobusStop(val stop: MetrobusStopEntity, val alias: String? = null, override val score: Double = 0.0, val isFavorite: Boolean = false) : MapSearchResult()
    data class Metro(val station: MetroStation, override val score: Double = 0.0, val isFavorite: Boolean = false) : MapSearchResult()
    data class Cercanias(val station: CercaniasStationEntity, override val score: Double = 0.0, val isFavorite: Boolean = false) : MapSearchResult()
    data class Address(val result: NominatimResult, override val score: Double = 0.0, val isFavorite: Boolean = false, val customTitle: String? = null) : MapSearchResult()
}

enum class MapSelectionMode {
    NORMAL,
    SELECTING_LOCATION,
    SELECTING_HOME,
    SELECTING_WORK,
    SELECTING_FOR_PLANNER_ORIGIN,
    SELECTING_FOR_PLANNER_DESTINATION
}

data class RecentSearch(
    val type: String,     // "bus", "metro", "cercanias", "address", "valenbisi", "favorite"
    val id: String,       // Unique ID or "lat_lon"
    val title: String,    // Visible name
    val subtitle: String, // Secondary description
    val latitude: Double,
    val longitude: Double,
    val extraData: String? = null, // Any extra details (like lines)
    val categoryName: String? = null, // e.g. "SUPERMARKET", "PHARMACY", etc.
    val categoryType: String? = null,  // Raw OSM category/type e.g. "shop:supermarket"
    val placeName: String? = null,
    val road: String? = null,
    val houseNumber: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val openingHours: String? = null,
    val wheelchair: String? = null,
    val brand: String? = null,
    val operator: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val wikipedia: String? = null,
    val wikidata: String? = null,
    val fee: String? = null,
    val charge: String? = null,
    val startDate: String? = null,
    val historicType: String? = null,
    val showOnMap: Boolean = true,
    val colorHex: String? = null
)
