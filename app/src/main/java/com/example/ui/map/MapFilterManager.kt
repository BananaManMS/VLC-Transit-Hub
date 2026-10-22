package com.example.ui.map

import android.util.Log
import com.example.data.repository.DashboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class MapFilterManager(
    private val scope: CoroutineScope,
    private val dashboardRepository: DashboardRepository,
    private val _mapFilter: MutableStateFlow<MapFilter> = MutableStateFlow(MapFilter.DEFAULT)
) {
    val mapFilter: StateFlow<MapFilter> = _mapFilter.asStateFlow()

    init {
        loadFilterPreference()
    }

    private fun loadFilterPreference() {
        scope.launch(Dispatchers.IO) {
            try {
                val saved = dashboardRepository.getPreference("map_filter_preference", "")
                if (saved.isNotEmpty()) {
                    val jsonObj = JSONObject(saved)
                    _mapFilter.value = MapFilter(
                        isFavorites = jsonObj.optBoolean("isFavorites", false),
                        showBus = jsonObj.optBoolean("showBus", true),
                        showMetrobus = jsonObj.optBoolean("showMetrobus", false),
                        showMetro = jsonObj.optBoolean("showMetro", true),
                        showCercanias = jsonObj.optBoolean("showCercanias", true),
                        showValenbisi = jsonObj.optBoolean("showValenbisi", false)
                    )
                } else {
                    val preferredModesStr = dashboardRepository.getPreference("favorite_transit_modes", "METRO,EMT,CERCANIAS,VALENBISI,METROBUS")
                    val modes = preferredModesStr.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
                    if (modes.isNotEmpty()) {
                        _mapFilter.value = MapFilter(
                            isFavorites = false,
                            showBus = modes.contains("EMT"),
                            showMetrobus = modes.contains("METROBUS"),
                            showMetro = modes.contains("METRO"),
                            showCercanias = modes.contains("CERCANIAS"),
                            showValenbisi = modes.contains("VALENBISI")
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MapFilterManager", "Error loading map filter preference", e)
            }
        }
    }

    fun toggleFilter(type: MapFilterType): MapFilter {
        val current = _mapFilter.value
        val updated = when (type) {
            MapFilterType.FAVORITES -> {
                MapFilter(isFavorites = true, showBus = false, showMetro = false, showCercanias = false, showValenbisi = false)
            }
            MapFilterType.BUS -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = true, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                } else {
                    val newBus = !current.showBus
                    if (!newBus && !current.showMetrobus && !current.showMetro && !current.showCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showBus = newBus)
                    }
                }
            }
            MapFilterType.METROBUS -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = true, showMetro = false, showCercanias = false, showValenbisi = false)
                } else {
                    val newMetrobus = !current.showMetrobus
                    if (!current.showBus && !newMetrobus && !current.showMetro && !current.showCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showMetrobus = newMetrobus)
                    }
                }
            }
            MapFilterType.METRO -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = true, showCercanias = false, showValenbisi = false)
                } else {
                    val newMetro = !current.showMetro
                    if (!current.showBus && !current.showMetrobus && !newMetro && !current.showCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showMetro = newMetro)
                    }
                }
            }
            MapFilterType.CERCANIAS -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = true, showValenbisi = false)
                } else {
                    val newCercanias = !current.showCercanias
                    if (!current.showBus && !current.showMetrobus && !current.showMetro && !newCercanias && !current.showValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showCercanias = newCercanias)
                    }
                }
            }
            MapFilterType.VALENBISI -> {
                if (current.isFavorites) {
                    MapFilter(isFavorites = false, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = true)
                } else {
                    val newValenbisi = !current.showValenbisi
                    if (!current.showBus && !current.showMetrobus && !current.showMetro && !current.showCercanias && !newValenbisi) {
                        MapFilter(isFavorites = true, showBus = false, showMetrobus = false, showMetro = false, showCercanias = false, showValenbisi = false)
                    } else {
                        current.copy(showValenbisi = newValenbisi)
                    }
                }
            }
        }
        setFilter(updated)
        return updated
    }

    fun setFilter(filter: MapFilter) {
        _mapFilter.value = filter
        saveMapFilterPreference(filter)
    }

    fun saveMapFilterPreference(filter: MapFilter) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonObj = JSONObject().apply {
                    put("isFavorites", filter.isFavorites)
                    put("showBus", filter.showBus)
                    put("showMetrobus", filter.showMetrobus)
                    put("showMetro", filter.showMetro)
                    put("showCercanias", filter.showCercanias)
                    put("showValenbisi", filter.showValenbisi)
                }
                dashboardRepository.savePreference("map_filter_preference", jsonObj.toString())
            } catch (e: Exception) {
                Log.e("MapFilterManager", "Error saving map filter preference", e)
            }
        }
    }
}
