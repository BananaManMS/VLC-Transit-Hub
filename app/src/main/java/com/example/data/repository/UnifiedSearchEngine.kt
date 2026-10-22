package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.bus.computeAddressSearchScore
import com.example.ui.bus.computeSearchScore
import com.example.ui.common.search.recentSearchToSearchResult
import com.example.ui.map.MapSearchResult
import com.example.ui.map.RecentSearch
import com.example.util.isBilingualTokenMatch
import com.example.util.isSubsequence
import com.example.util.levenshteinDistance
import com.example.util.normalizeForSearch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Multimodal search engine that unifies EMT Bus stops, Metrovalencia stations,
 * Cercanías Renfe stations, Metrobus stops, and remote Nominatim OpenStreetMap addresses.
 */
class UnifiedSearchEngine {
    private var context: Context? = null
    private var database: AppDatabase? = null
    private var geocodingRepository: GeocodingRepository? = null

    constructor(context: Context) {
        this.context = context
        this.database = AppDatabase.getDatabase(context)
        this.geocodingRepository = GeocodingRepository(context, database)
    }

    constructor(database: AppDatabase) {
        this.database = database
    }

    constructor(database: AppDatabase, geocodingRepository: GeocodingRepository) {
        this.database = database
        this.geocodingRepository = geocodingRepository
    }

    constructor(context: Context?, database: AppDatabase?, geocodingRepository: GeocodingRepository?) {
        this.context = context
        this.database = database
        this.geocodingRepository = geocodingRepository
    }

    @Volatile
    private var cachedMetroStations: List<MetroStation>? = null
    @Volatile
    private var cachedCercaniasStations: List<CercaniasStationEntity>? = null
    @Volatile
    private var cachedBusStops: List<GeoportalStopEntity>? = null
    @Volatile
    private var cachedMetrobusStops: List<MetrobusStopEntity>? = null

    private suspend fun getEffectiveMetroStations(passed: List<Any>): List<MetroStation> {
        val casted = passed.filterIsInstance<MetroStation>()
        if (casted.isNotEmpty()) return casted
        if (cachedMetroStations != null) return cachedMetroStations!!
        val loaded = context?.let { MetroRepository(it).loadMetroStations() } ?: com.example.data.model.ValenciaMetroData.mainMetroStations
        cachedMetroStations = loaded
        return loaded
    }

    private suspend fun getEffectiveCercaniasStations(passed: List<Any>): List<CercaniasStationEntity> {
        val casted = passed.filterIsInstance<CercaniasStationEntity>()
        if (casted.isNotEmpty()) return casted
        if (cachedCercaniasStations != null) return cachedCercaniasStations!!
        val loaded = try { database?.cercaniasStationDao()?.getAllStations() ?: emptyList() } catch (e: Exception) { emptyList() }
        cachedCercaniasStations = loaded
        return loaded
    }

    private suspend fun getEffectiveBusStops(passed: List<Any>): List<GeoportalStopEntity> {
        val casted = passed.filterIsInstance<GeoportalStopEntity>()
        if (casted.isNotEmpty()) return casted
        if (cachedBusStops != null) return cachedBusStops!!
        val loaded = try { database?.geoportalStopDao()?.getAllStops() ?: emptyList() } catch (e: Exception) { emptyList() }
        cachedBusStops = loaded
        return loaded
    }

    private suspend fun getEffectiveMetrobusStops(passed: List<Any>): List<MetrobusStopEntity> {
        val casted = passed.filterIsInstance<MetrobusStopEntity>()
        if (casted.isNotEmpty()) return casted
        if (cachedMetrobusStops != null) return cachedMetrobusStops!!
        val loaded = try { database?.metrobusStopDao()?.getAllStops() ?: emptyList() } catch (e: Exception) { emptyList() }
        cachedMetrobusStops = loaded
        return loaded
    }

    suspend fun search(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<String>()
        val db = database
        if (db != null) {
            val busMatches = db.geoportalStopDao().searchActiveStops(trimmed).take(5)
            busMatches.forEach { results.add(it.denominacion) }

            val metrobusMatches = db.metrobusStopDao().searchActiveStops(trimmed).take(5)
            metrobusMatches.forEach { results.add(it.denominacion) }
        }
        results.distinct()
    }

    fun performSearch(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null,
        busStops: List<Any> = emptyList(),
        metroStations: List<Any> = emptyList(),
        cercaniasStations: List<Any> = emptyList(),
        metrobusStops: List<Any> = emptyList(),
        busStopAliases: Map<String, String> = emptyMap(),
        metrobusStopAliases: Map<String, String> = emptyMap(),
        customFavorites: List<Any> = emptyList(),
        homeLocation: Any? = null,
        workLocation: Any? = null,
        favoriteBusStops: Set<String> = emptySet(),
        favoriteMetroStations: Set<String> = emptySet(),
        favoriteCercaniasStations: Set<String> = emptySet(),
        favoriteMetrobusStops: Set<String> = emptySet()
    ): Flow<List<MapSearchResult>> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val qNorm = trimmed.normalizeForSearch()
        val isGeneralFavoriteQuery = qNorm in listOf(
            "fav", "favs", "favorito", "favoritos", "favorita", "favoritas",
            "preferit", "preferits", "preferida", "preferides",
            "sitio", "sitios", "sitio favorito", "sitios favoritos",
            "lloc", "llocs", "lloc preferit", "llocs preferits",
            "guardado", "guardados", "guardada", "guardadas",
            "mis sitios", "meus llocs", "mis favoritos", "meus preferits"
        ) || qNorm.startsWith("favorit") || qNorm.startsWith("preferit") || qNorm.startsWith("guardad")

        val localResults = mutableListOf<MapSearchResult>()

        // 0. Saved Favorite Places (Custom Favorites, Home, Work)
        val loadedCustomFavs = mutableListOf<RecentSearch>()
        if (customFavorites.isNotEmpty()) {
            loadedCustomFavs.addAll(customFavorites.filterIsInstance<RecentSearch>())
        }
        try {
            val jsonDb = database?.preferenceDao()?.getPreference("custom_favorites")?.value
            val jsonPref = context?.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)?.getString("custom_favorites", null)
            val json = if (!jsonDb.isNullOrBlank()) jsonDb else jsonPref
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                val fromJson: List<RecentSearch> = Gson().fromJson(json, type) ?: emptyList()
                for (item in fromJson) {
                    if (loadedCustomFavs.none { Math.abs(it.latitude - item.latitude) < 0.0001 && Math.abs(it.longitude - item.longitude) < 0.0001 }) {
                        loadedCustomFavs.add(item)
                    }
                }
            }
        } catch (_: Exception) {}

        val effectiveHome: RecentSearch? = (homeLocation as? RecentSearch) ?: run {
            try {
                val json = database?.preferenceDao()?.getPreference("home_location")?.value
                    ?: context?.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)?.getString("home_location", null)
                if (!json.isNullOrBlank()) {
                    Gson().fromJson(json, RecentSearch::class.java)
                } else null
            } catch (_: Exception) { null }
        }

        val effectiveWork: RecentSearch? = (workLocation as? RecentSearch) ?: run {
            try {
                val json = database?.preferenceDao()?.getPreference("work_location")?.value
                    ?: context?.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)?.getString("work_location", null)
                if (!json.isNullOrBlank()) {
                    Gson().fromJson(json, RecentSearch::class.java)
                } else null
            } catch (_: Exception) { null }
        }

        val allSavedPlaces = mutableListOf<Pair<RecentSearch, String>>()
        effectiveHome?.let { allSavedPlaces.add(Pair(it, "home")) }
        effectiveWork?.let { allSavedPlaces.add(Pair(it, "work")) }
        for (fav in loadedCustomFavs) {
            val alreadyAdded = allSavedPlaces.any {
                Math.abs(it.first.latitude - fav.latitude) < 0.0001 &&
                Math.abs(it.first.longitude - fav.longitude) < 0.0001
            }
            if (!alreadyAdded) {
                allSavedPlaces.add(Pair(fav, "favorite"))
            }
        }

        for ((place, placeType) in allSavedPlaces) {
            val score = if (isGeneralFavoriteQuery) {
                1800.0
            } else {
                computeFavoriteSearchScore(place, placeType, trimmed)
            }
            if (score > 0) {
                val resultItem = recentSearchToSearchResult(place, score = score + 400.0)
                localResults.add(resultItem)
            }
        }

        val effectiveBus = getEffectiveBusStops(busStops)
        val effectiveMetro = getEffectiveMetroStations(metroStations)
        val effectiveCercanias = getEffectiveCercaniasStations(cercaniasStations)
        val effectiveMetrobus = getEffectiveMetrobusStops(metrobusStops)

        // Effective transit favorites sets with SharedPreferences fallback if empty
        val effFavBus = if (favoriteBusStops.isNotEmpty()) favoriteBusStops else run {
            try {
                val s = database?.preferenceDao()?.getPreference("favorite_bus_stops")?.value
                    ?: context?.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)?.getString("favorite_bus_stops", "")
                if (!s.isNullOrBlank()) s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()
            } catch (_: Exception) { emptySet() }
        }
        val effFavMetro = if (favoriteMetroStations.isNotEmpty()) favoriteMetroStations else run {
            try {
                val s = database?.preferenceDao()?.getPreference("favorite_stations")?.value
                    ?: context?.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)?.getString("favorite_stations", "")
                if (!s.isNullOrBlank()) s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()
            } catch (_: Exception) { emptySet() }
        }
        val effFavCercanias = if (favoriteCercaniasStations.isNotEmpty()) favoriteCercaniasStations else run {
            try {
                val s = database?.preferenceDao()?.getPreference("favorite_cercanias_stations")?.value
                    ?: context?.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)?.getString("favorite_cercanias_stations", "")
                if (!s.isNullOrBlank()) s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()
            } catch (_: Exception) { emptySet() }
        }
        val effFavMetrobus = if (favoriteMetrobusStops.isNotEmpty()) favoriteMetrobusStops else run {
            try {
                val s = database?.preferenceDao()?.getPreference("favorite_metrobus_stops")?.value
                    ?: context?.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)?.getString("favorite_metrobus_stops", "")
                if (!s.isNullOrBlank()) s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()
            } catch (_: Exception) { emptySet() }
        }

        // 1. EMT Bus Stops
        for (item in effectiveBus) {
            val isFav = effFavBus.contains(item.id_parada)
            val alias = busStopAliases[item.id_parada]
            var score = computeSearchScore(item, trimmed, alias)
            if (isGeneralFavoriteQuery && isFav) {
                score = 1500.0
            }
            if (score > 0) {
                val boost = if (isFav) 300.0 else 0.0
                localResults.add(MapSearchResult.BusStop(stop = item, alias = alias, score = score + boost, isFavorite = isFav))
            }
        }

        // 2. Metrovalencia Stations
        for (item in effectiveMetro) {
            val isFav = effFavMetro.contains(item.id)
            var score = computeSearchScore(
                stopId = item.id,
                stopName = item.name,
                query = trimmed,
                alias = null,
                lines = item.lines
            )
            if (isGeneralFavoriteQuery && isFav) {
                score = 1500.0
            }
            if (score > 0) {
                val boost = if (isFav) 300.0 else 0.0
                localResults.add(MapSearchResult.Metro(station = item, score = score + boost, isFavorite = isFav))
            }
        }

        // 3. Cercanias Stations
        for (item in effectiveCercanias) {
            val isFav = effFavCercanias.contains(item.stop_id)
            val linesList = item.lines.map { it.trim() }.filter { it.isNotEmpty() }
            var score = computeSearchScore(
                stopId = item.stop_id,
                stopName = item.nombre,
                query = trimmed,
                alias = null,
                lines = linesList
            )
            if (isGeneralFavoriteQuery && isFav) {
                score = 1500.0
            }
            if (score > 0) {
                val boost = if (isFav) 300.0 else 0.0
                localResults.add(MapSearchResult.Cercanias(station = item, score = score + boost, isFavorite = isFav))
            }
        }

        // 4. Metrobus Stops
        for (item in effectiveMetrobus) {
            val isFav = effFavMetrobus.contains(item.id_parada)
            val alias = metrobusStopAliases[item.id_parada]
            val linesList = item.lineas.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var score = computeSearchScore(
                stopId = item.id_parada,
                stopName = item.denominacion,
                query = trimmed,
                alias = alias,
                lines = linesList
            )
            if (isGeneralFavoriteQuery && isFav) {
                score = 1500.0
            }
            if (score > 0) {
                val boost = if (isFav) 300.0 else 0.0
                localResults.add(MapSearchResult.MetrobusStop(stop = item, alias = alias, score = score + boost, isFavorite = isFav))
            }
        }

        // Emit local results first immediately
        val initialSorted = localResults.sortedByDescending { it.score }
        emit(initialSorted)

        // 5. Remote Geocoding via Nominatim (for queries of 2 or more characters)
        val geocoder = geocodingRepository
        if (geocoder != null && trimmed.length >= 2 && !isGeneralFavoriteQuery) {
            try {
                val remotePlaces = geocoder.performSearch(trimmed, userLat = userLat, userLon = userLon)
                val allResults = localResults.toMutableList()
                for (place in remotePlaces) {
                    val isDuplicateOfFavorite = allSavedPlaces.any { (fav, _) ->
                        Math.abs(fav.latitude - place.latitude) < 0.0003 &&
                        Math.abs(fav.longitude - place.longitude) < 0.0003
                    }
                    if (isDuplicateOfFavorite) continue

                    val score = computeAddressSearchScore(place, trimmed)
                    if (score > 0) {
                        allResults.add(MapSearchResult.Address(result = place, score = score))
                    }
                }
                emit(allResults.sortedByDescending { it.score })
            } catch (e: Exception) {
                // Return local results if remote fails
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun computeFavoriteSearchScore(
        place: RecentSearch,
        placeType: String,
        query: String
    ): Double {
        val qNorm = query.normalizeForSearch()
        if (qNorm.isEmpty()) return 0.0

        val titleNorm = place.title.normalizeForSearch()
        val subtitleNorm = place.subtitle.normalizeForSearch()
        val placeNameNorm = place.placeName?.normalizeForSearch() ?: ""
        val roadNorm = place.road?.normalizeForSearch() ?: ""

        // 1. Direct title/alias matching (highest priority)
        if (titleNorm == qNorm) return 1600.0
        if (titleNorm.startsWith(qNorm)) return 1400.0 + (100.0 / titleNorm.length.coerceAtLeast(1))
        if (titleNorm.contains(qNorm)) return 1150.0

        // 2. Synonyms for Home and Work
        val isHome = placeType == "home" || place.type == "home" || place.id == "home_location" || titleNorm == "casa" || titleNorm == "home"
        val isWork = placeType == "work" || place.type == "work" || place.id == "work_location" || titleNorm == "trabajo" || titleNorm == "feina" || titleNorm == "work"

        if (isHome) {
            val homeSynonyms = listOf("casa", "home", "llar", "vivienda", "piso", "mi casa")
            if (homeSynonyms.any { it == qNorm || it.startsWith(qNorm) }) {
                return 1600.0
            }
        }
        if (isWork) {
            val workSynonyms = listOf("trabajo", "feina", "curro", "oficina", "work", "job", "empresa", "mi trabajo")
            if (workSynonyms.any { it == qNorm || it.startsWith(qNorm) }) {
                return 1600.0
            }
        }

        // 3. Word token matching on Title / Alias
        val titleWords = titleNorm.split(" ").filter { it.isNotEmpty() }
        val queryWords = qNorm.split(" ").filter { it.isNotEmpty() }

        var tokenScore = 0.0
        for (qWord in queryWords) {
            if (titleWords.any { isBilingualTokenMatch(qWord, it) }) {
                tokenScore += 500.0
            } else if (titleWords.any { it.startsWith(qWord) }) {
                tokenScore += 300.0
            }
        }
        if (tokenScore > 0) return 1000.0 + tokenScore

        // 4. Secondary address components (subtitle, road, placeName)
        if (roadNorm.isNotEmpty()) {
            if (roadNorm == qNorm) return 1100.0
            if (roadNorm.startsWith(qNorm)) return 950.0
            if (roadNorm.contains(qNorm)) return 800.0
        }
        if (placeNameNorm.isNotEmpty()) {
            if (placeNameNorm == qNorm) return 1100.0
            if (placeNameNorm.startsWith(qNorm)) return 950.0
            if (placeNameNorm.contains(qNorm)) return 800.0
        }
        if (subtitleNorm.isNotEmpty()) {
            if (subtitleNorm.startsWith(qNorm)) return 900.0
            if (subtitleNorm.contains(qNorm)) return 750.0
        }

        // 5. Token matches on address components
        val combinedAddrWords = "$subtitleNorm $roadNorm $placeNameNorm".split(" ").filter { it.isNotEmpty() }
        var addrTokenScore = 0.0
        for (qWord in queryWords) {
            if (combinedAddrWords.any { isBilingualTokenMatch(qWord, it) }) {
                addrTokenScore += 250.0
            } else if (combinedAddrWords.any { it.startsWith(qWord) }) {
                addrTokenScore += 150.0
            }
        }
        if (addrTokenScore > 0) return 600.0 + addrTokenScore

        // 6. Subsequence or Levenshtein for fuzzy queries
        if (isSubsequence(qNorm, titleNorm)) return 650.0
        if (qNorm.length >= 3 && titleNorm.length >= 3) {
            val dist = levenshteinDistance(qNorm, titleNorm)
            if (dist <= 2) return 600.0 - dist * 50.0
        }

        return 0.0
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): NominatimResult? {
        val geocoder = geocodingRepository ?: return null
        return try {
            val flow = geocoder.reverseGeocodeDetails(lat, lon)
            var result: NominatimResult? = null
            flow.collect { result = it }
            result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun reverseGeocodeDetails(lat: Double, lon: Double): NominatimResult? {
        return reverseGeocode(lat, lon)
    }
}
