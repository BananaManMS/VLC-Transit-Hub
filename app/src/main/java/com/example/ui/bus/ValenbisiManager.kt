package com.example.ui.bus

import android.util.Log
import com.example.data.repository.DashboardRepository
import com.example.data.repository.ValenbisiRepository
import com.example.ui.map.components.ValenbisiStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ValenbisiManager(
    private val repository: DashboardRepository,
    private val valenbisiRepository: ValenbisiRepository,
    private val scope: CoroutineScope
) {
    private val _favoriteValenbisi = MutableStateFlow<List<String>>(emptyList())
    val favoriteValenbisi: StateFlow<List<String>> = _favoriteValenbisi.asStateFlow()

    private val _valenbisiAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val valenbisiAliases: StateFlow<Map<String, String>> = _valenbisiAliases.asStateFlow()

    private val _currentValenbisiFilterSource = MutableStateFlow(ValenbisiFilterSource.FAVORITES)
    val currentValenbisiFilterSource: StateFlow<ValenbisiFilterSource> = _currentValenbisiFilterSource.asStateFlow()

    private val _valenbisiSearchQuery = MutableStateFlow("")
    val valenbisiSearchQuery: StateFlow<String> = _valenbisiSearchQuery.asStateFlow()

    private val _selectedMetroStationIdForValenbisi = MutableStateFlow<String?>("15") // Default Colón
    val selectedMetroStationIdForValenbisi: StateFlow<String?> = _selectedMetroStationIdForValenbisi.asStateFlow()

    private val _valenbisiStations = MutableStateFlow<List<ValenbisiStation>>(emptyList())
    val valenbisiStations: StateFlow<List<ValenbisiStation>> = _valenbisiStations.asStateFlow()

    private val _valenbisiLoading = MutableStateFlow(false)
    val valenbisiLoading: StateFlow<Boolean> = _valenbisiLoading.asStateFlow()

    suspend fun loadPreferences() {
        val savedValenbisiFavs = repository.getPreference("favorite_valenbisi_stations", "")
        if (savedValenbisiFavs.isNotEmpty()) {
            _favoriteValenbisi.value = savedValenbisiFavs.split(",").filter { it.isNotEmpty() }
        }
        val savedValenbisiAliasesJson = repository.getPreference("valenbisi_aliases", "{}")
        if (savedValenbisiAliasesJson.isNotEmpty() && savedValenbisiAliasesJson != "{}") {
            try {
                val jsonObj = JSONObject(savedValenbisiAliasesJson)
                val map = mutableMapOf<String, String>()
                jsonObj.keys().forEach { key ->
                    map[key] = jsonObj.getString(key)
                }
                _valenbisiAliases.value = map
            } catch (e: Exception) {
                Log.e("Valenbisi", "Error parsing saved valenbisi aliases", e)
            }
        }
    }

    fun setFilterSource(source: ValenbisiFilterSource) {
        if (source != ValenbisiFilterSource.FAVORITES) {
            _valenbisiSearchQuery.value = ""
        }
        _currentValenbisiFilterSource.value = source
    }

    fun setSearchQuery(query: String) {
        _valenbisiSearchQuery.value = query
    }

    fun selectMetroStation(stationId: String) {
        _valenbisiSearchQuery.value = ""
        _selectedMetroStationIdForValenbisi.value = stationId
        _currentValenbisiFilterSource.value = ValenbisiFilterSource.METRO_STATION
    }

    fun fetchStations() {
        scope.launch {
            _valenbisiLoading.value = true
            try {
                val list = valenbisiRepository.fetchStations()
                _valenbisiStations.value = list
            } catch (e: Exception) {
                Log.e("Valenbisi", "Error fetching valenbisi stations: ${e.message}", e)
            } finally {
                _valenbisiLoading.value = false
            }
        }
    }

    fun toggleFavorite(stationNumber: String) {
        val current = _favoriteValenbisi.value.toMutableList()
        if (current.contains(stationNumber)) {
            current.remove(stationNumber)
        } else {
            current.add(stationNumber)
        }
        _favoriteValenbisi.value = current
        scope.launch(Dispatchers.IO) {
            repository.savePreference("favorite_valenbisi_stations", current.joinToString(","))
        }
    }

    fun saveAlias(stationNumber: String, alias: String) {
        val current = _valenbisiAliases.value.toMutableMap()
        if (alias.isBlank()) {
            current.remove(stationNumber)
        } else {
            current[stationNumber] = alias.trim()
        }
        _valenbisiAliases.value = current
        scope.launch(Dispatchers.IO) {
            val jsonObj = JSONObject()
            current.forEach { (k, v) -> jsonObj.put(k, v) }
            repository.savePreference("valenbisi_aliases", jsonObj.toString())
        }
    }
}
