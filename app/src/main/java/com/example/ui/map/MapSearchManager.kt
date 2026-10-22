package com.example.ui.map

import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.repository.DashboardRepository
import com.example.data.repository.GeocodingRepository
import com.example.data.repository.UnifiedSearchEngine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.content.Context

class MapSearchManager(
    private val scope: CoroutineScope,
    private val dashboardRepository: DashboardRepository,
    private val geocodingRepository: GeocodingRepository,
    private val database: AppDatabase,
    private val gson: Gson = Gson(),
    private val context: Context? = null
) {
    val unifiedSearchEngine = UnifiedSearchEngine(context = context, database = database, geocodingRepository = geocodingRepository)

    // Recent Searches StateFlow
    val recentSearches: StateFlow<List<RecentSearch>> = dashboardRepository.getPreferenceFlow("recent_searches", "[]")
        .map { json ->
            try {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home Location StateFlow
    val homeLocation: StateFlow<RecentSearch?> = dashboardRepository.getPreferenceFlow("home_location", "")
        .map { json ->
            if (json.isBlank()) null
            else {
                try {
                    gson.fromJson(json, RecentSearch::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // Work Location StateFlow
    val workLocation: StateFlow<RecentSearch?> = dashboardRepository.getPreferenceFlow("work_location", "")
        .map { json ->
            if (json.isBlank()) null
            else {
                try {
                    gson.fromJson(json, RecentSearch::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // Custom Favorites StateFlow
    val customFavorites: StateFlow<List<RecentSearch>> = dashboardRepository.getPreferenceFlow("custom_favorites", "[]")
        .map { json ->
            try {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun performSearch(
        query: String,
        userLat: Double?,
        userLon: Double?,
        busStops: List<GeoportalStopEntity>,
        metroStations: List<MetroStation>,
        cercaniasStations: List<CercaniasStationEntity>,
        metrobusStops: List<MetrobusStopEntity> = emptyList(),
        busStopAliases: Map<String, String>,
        metrobusStopAliases: Map<String, String> = emptyMap(),
        customFavorites: List<RecentSearch> = emptyList(),
        homeLocation: RecentSearch? = null,
        workLocation: RecentSearch? = null,
        favoriteBusStops: Set<String> = emptySet(),
        favoriteMetroStations: Set<String> = emptySet(),
        favoriteCercaniasStations: Set<String> = emptySet(),
        favoriteMetrobusStops: Set<String> = emptySet()
    ): Flow<List<MapSearchResult>> {
        val effectiveCustomFavs = if (customFavorites.isNotEmpty()) customFavorites else this.customFavorites.value
        val effectiveHome = homeLocation ?: this.homeLocation.value
        val effectiveWork = workLocation ?: this.workLocation.value
        return unifiedSearchEngine.performSearch(
            query = query,
            userLat = userLat,
            userLon = userLon,
            busStops = busStops,
            metroStations = metroStations,
            cercaniasStations = cercaniasStations,
            metrobusStops = metrobusStops,
            busStopAliases = busStopAliases,
            metrobusStopAliases = metrobusStopAliases,
            customFavorites = effectiveCustomFavs,
            homeLocation = effectiveHome,
            workLocation = effectiveWork,
            favoriteBusStops = favoriteBusStops,
            favoriteMetroStations = favoriteMetroStations,
            favoriteCercaniasStations = favoriteCercaniasStations,
            favoriteMetrobusStops = favoriteMetrobusStops
        )
    }

    fun reverseGeocode(lat: Double, lon: Double): Flow<String?> {
        return geocodingRepository.reverseGeocode(lat, lon)
    }

    fun reverseGeocodeDetails(lat: Double, lon: Double): Flow<com.example.data.model.NominatimResult?> {
        return geocodingRepository.reverseGeocodeDetails(lat, lon)
    }

    fun addRecentSearch(search: RecentSearch) {
        scope.launch(Dispatchers.IO) {
            val currentList = recentSearches.value.toMutableList()
            // Deduplicate
            currentList.removeAll { it.id == search.id || (it.latitude == search.latitude && it.longitude == search.longitude) }
            // Add to the top
            currentList.add(0, search)
            // Limit to 8 recent search items
            val limitedList = currentList.take(8)
            val json = gson.toJson(limitedList)
            dashboardRepository.savePreference("recent_searches", json)
        }
    }

    fun clearRecentSearches() {
        scope.launch(Dispatchers.IO) {
            dashboardRepository.savePreference("recent_searches", "[]")
        }
    }

    fun removeRecentSearch(searchId: String) {
        scope.launch(Dispatchers.IO) {
            val currentList = recentSearches.value.toMutableList()
            currentList.removeAll { it.id == searchId }
            val json = gson.toJson(currentList)
            dashboardRepository.savePreference("recent_searches", json)
        }
    }

    fun saveHomeLocation(location: RecentSearch?) {
        scope.launch(Dispatchers.IO) {
            val json = if (location == null) "" else gson.toJson(location)
            dashboardRepository.savePreference("home_location", json)
        }
    }

    fun saveWorkLocation(location: RecentSearch?) {
        scope.launch(Dispatchers.IO) {
            val json = if (location == null) "" else gson.toJson(location)
            dashboardRepository.savePreference("work_location", json)
        }
    }

    fun savePinnedLocation(location: RecentSearch?) {
        scope.launch(Dispatchers.IO) {
            val json = if (location == null) "" else gson.toJson(location)
            dashboardRepository.savePreference("pinned_location", json)
        }
    }

    fun saveCustomFavorite(
        alias: String,
        subtitle: String,
        latitude: Double,
        longitude: Double,
        showOnMap: Boolean = true,
        colorHex: String? = null,
        nominatimResult: com.example.data.model.NominatimResult? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val currentList = customFavorites.value.toMutableList()
            val existing = currentList.find { Math.abs(it.latitude - latitude) < 0.0001 && Math.abs(it.longitude - longitude) < 0.0001 }
            // Remove any existing one at the same coordinates to avoid duplicates
            currentList.removeAll { Math.abs(it.latitude - latitude) < 0.0001 && Math.abs(it.longitude - longitude) < 0.0001 }
            val item = RecentSearch(
                type = "favorite",
                id = existing?.id ?: "custom_${System.currentTimeMillis()}",
                title = alias,
                subtitle = subtitle,
                latitude = latitude,
                longitude = longitude,
                categoryName = nominatimResult?.placeCategory?.name ?: existing?.categoryName,
                categoryType = if (nominatimResult != null) "${nominatimResult.category}:${nominatimResult.type}" else existing?.categoryType,
                placeName = nominatimResult?.placeName ?: existing?.placeName,
                road = nominatimResult?.road ?: existing?.road,
                houseNumber = nominatimResult?.houseNumber ?: existing?.houseNumber,
                suburb = nominatimResult?.suburb ?: existing?.suburb,
                city = nominatimResult?.city ?: existing?.city,
                postcode = nominatimResult?.postcode ?: existing?.postcode,
                openingHours = nominatimResult?.openingHours ?: existing?.openingHours,
                wheelchair = nominatimResult?.wheelchair ?: existing?.wheelchair,
                brand = nominatimResult?.brand ?: existing?.brand,
                operator = nominatimResult?.operator ?: existing?.operator,
                phone = nominatimResult?.phone ?: existing?.phone,
                email = nominatimResult?.email ?: existing?.email,
                website = nominatimResult?.website ?: existing?.website,
                wikipedia = nominatimResult?.wikipedia ?: existing?.wikipedia,
                wikidata = nominatimResult?.wikidata ?: existing?.wikidata,
                fee = nominatimResult?.fee ?: existing?.fee,
                charge = nominatimResult?.charge ?: existing?.charge,
                startDate = nominatimResult?.startDate ?: existing?.startDate,
                historicType = nominatimResult?.historicType ?: existing?.historicType,
                showOnMap = showOnMap,
                colorHex = colorHex ?: existing?.colorHex ?: "#F59E0B"
            )
            currentList.add(0, item)
            val json = gson.toJson(currentList)
            dashboardRepository.savePreference("custom_favorites", json)
        }
    }

    fun deleteCustomFavorite(latitude: Double, longitude: Double) {
        scope.launch(Dispatchers.IO) {
            val currentList = customFavorites.value.toMutableList()
            currentList.removeAll { Math.abs(it.latitude - latitude) < 0.0001 && Math.abs(it.longitude - longitude) < 0.0001 }
            val json = gson.toJson(currentList)
            dashboardRepository.savePreference("custom_favorites", json)
        }
    }
}
