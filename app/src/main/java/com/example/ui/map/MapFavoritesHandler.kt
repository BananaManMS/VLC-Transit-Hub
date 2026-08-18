package com.example.ui.map

import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.repository.DashboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class MapFavoritesHandler(
    private val scope: CoroutineScope,
    private val dashboardRepository: DashboardRepository,
    private val database: AppDatabase,
    private val mapFilter: MutableStateFlow<MapFilter>,
    private val favoriteBusStops: MutableStateFlow<Set<String>>,
    private val favoriteMetroStations: MutableStateFlow<Set<String>>,
    private val favoriteCercaniasStations: MutableStateFlow<Set<String>>,
    private val favoriteMetrobusStops: MutableStateFlow<Set<String>>,
    private val favoriteValenbisi: MutableStateFlow<Set<String>>,
    private val valenbisiAliases: MutableStateFlow<Map<String, String>>,
    private val busStopAliases: MutableStateFlow<Map<String, String>>
) {

    fun reloadFavorites() {
        scope.launch(Dispatchers.IO) {
            val savedFilterJson = dashboardRepository.getPreference("map_filter_preference", "")
            if (savedFilterJson.isNotEmpty()) {
                try {
                    val jsonObj = JSONObject(savedFilterJson)
                    val loadedFilter = MapFilter(
                        isFavorites = jsonObj.optBoolean("isFavorites", true),
                        showBus = jsonObj.optBoolean("showBus", false),
                        showMetro = jsonObj.optBoolean("showMetro", false),
                        showCercanias = jsonObj.optBoolean("showCercanias", false),
                        showValenbisi = jsonObj.optBoolean("showValenbisi", false)
                    )
                    mapFilter.value = loadedFilter
                } catch (e: Exception) {
                    Log.e("MapFavoritesHandler", "Error parsing saved map filter preference", e)
                }
            }

            val savedBusFavs = dashboardRepository.getPreference("favorite_bus_stops", "")
            if (savedBusFavs.isNotEmpty()) {
                favoriteBusStops.value = savedBusFavs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                favoriteBusStops.value = setOf("1001", "1002", "1500", "2000", "70", "80")
            }

            val savedMetroFavs = dashboardRepository.getPreference("favorite_stations", "16,15,14")
            if (savedMetroFavs.isNotEmpty()) {
                favoriteMetroStations.value = savedMetroFavs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                favoriteMetroStations.value = setOf("15", "16", "14", "1", "2")
            }

            val savedCercaniasFavs = dashboardRepository.getPreference("favorite_cercanias_stations", "")
            if (savedCercaniasFavs.isNotEmpty()) {
                favoriteCercaniasStations.value = savedCercaniasFavs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                favoriteCercaniasStations.value = emptySet()
            }

            val savedMetrobusFavs = dashboardRepository.getPreference("favorite_metrobus_stops", "")
            if (savedMetrobusFavs.isNotEmpty()) {
                favoriteMetrobusStops.value = savedMetrobusFavs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                favoriteMetrobusStops.value = emptySet()
            }

            val savedValenbisiFavs = dashboardRepository.getPreference("favorite_valenbisi_stations", "")
            if (savedValenbisiFavs.isNotEmpty()) {
                favoriteValenbisi.value = savedValenbisiFavs.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }

            val savedValenbisiAliasesJson = dashboardRepository.getPreference("valenbisi_aliases", "{}")
            try {
                val jsonObj = JSONObject(savedValenbisiAliasesJson)
                val map = mutableMapOf<String, String>()
                jsonObj.keys().forEach { key ->
                    map[key] = jsonObj.getString(key)
                }
                valenbisiAliases.value = map
            } catch (e: Exception) {
                Log.e("MapFavoritesHandler", "Error loading saved valenbisi aliases", e)
            }

            val savedAliasesJson = dashboardRepository.getPreference("bus_stop_aliases", "{}")
            try {
                val jsonObj = JSONObject(savedAliasesJson)
                val map = mutableMapOf<String, String>()
                jsonObj.keys().forEach { key ->
                    map[key] = jsonObj.getString(key)
                }
                busStopAliases.value = map
            } catch (e: Exception) {
                Log.e("MapFavoritesHandler", "Error loading saved bus stop aliases", e)
            }
        }
    }

    fun toggleFavoriteBusStop(stopId: String) {
        val current = favoriteBusStops.value.toMutableSet()
        if (current.contains(stopId)) {
            current.remove(stopId)
        } else {
            current.add(stopId)
        }
        favoriteBusStops.value = current
        scope.launch(Dispatchers.IO) {
            dashboardRepository.savePreference("favorite_bus_stops", current.joinToString(","))
        }
    }

    fun toggleFavoriteMetroStation(stationId: String) {
        val current = favoriteMetroStations.value.toMutableSet()
        if (current.contains(stationId)) {
            current.remove(stationId)
        } else {
            current.add(stationId)
        }
        favoriteMetroStations.value = current
        scope.launch(Dispatchers.IO) {
            dashboardRepository.savePreference("favorite_stations", current.joinToString(","))
        }
    }

    fun toggleFavoriteCercaniasStation(stationId: String) {
        val current = favoriteCercaniasStations.value.toMutableSet()
        if (current.contains(stationId)) {
            current.remove(stationId)
        } else {
            current.add(stationId)
        }
        favoriteCercaniasStations.value = current
        scope.launch(Dispatchers.IO) {
            dashboardRepository.savePreference("favorite_cercanias_stations", current.joinToString(","))
            val station = database.cercaniasStationDao().getStationById(stationId)
            if (station != null) {
                database.cercaniasStationDao().updateStation(station.copy(isFavorite = current.contains(stationId)))
            }
        }
    }

    fun toggleFavoriteMetrobusStop(stopId: String) {
        val current = favoriteMetrobusStops.value.toMutableSet()
        if (current.contains(stopId)) {
            current.remove(stopId)
        } else {
            current.add(stopId)
        }
        favoriteMetrobusStops.value = current
        scope.launch(Dispatchers.IO) {
            dashboardRepository.savePreference("favorite_metrobus_stops", current.joinToString(","))
        }
    }

    fun toggleFavoriteValenbisiStation(stationNumber: String) {
        val current = favoriteValenbisi.value.toMutableSet()
        if (current.contains(stationNumber)) {
            current.remove(stationNumber)
        } else {
            current.add(stationNumber)
        }
        favoriteValenbisi.value = current
        scope.launch(Dispatchers.IO) {
            dashboardRepository.savePreference("favorite_valenbisi_stations", current.joinToString(","))
        }
    }

    fun saveValenbisiAlias(stationNumber: String, alias: String) {
        val current = valenbisiAliases.value.toMutableMap()
        if (alias.isBlank()) {
            current.remove(stationNumber)
        } else {
            current[stationNumber] = alias.trim()
        }
        valenbisiAliases.value = current
        scope.launch(Dispatchers.IO) {
            val jsonObj = JSONObject()
            current.forEach { (k, v) -> jsonObj.put(k, v) }
            dashboardRepository.savePreference("valenbisi_aliases", jsonObj.toString())
        }
    }

    fun setBusStopAlias(stopId: String, alias: String) {
        val trimmed = alias.trim().take(32)
        val currentMap = busStopAliases.value.toMutableMap()
        if (trimmed.isEmpty()) {
            currentMap.remove(stopId)
        } else {
            currentMap[stopId] = trimmed
        }
        busStopAliases.value = currentMap

        scope.launch(Dispatchers.IO) {
            try {
                val jsonObj = JSONObject()
                currentMap.forEach { (k, v) -> jsonObj.put(k, v) }
                dashboardRepository.savePreference("bus_stop_aliases", jsonObj.toString())
            } catch (e: Exception) {
                Log.e("MapFavoritesHandler", "Error saving bus stop alias", e)
            }
        }
    }
}
