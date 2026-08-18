package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.ui.bus.computeAddressSearchScore
import com.example.ui.bus.computeSearchScore
import com.example.ui.cercanias.computeCercaniasSearchScore
import com.example.ui.metro.computeMetroSearchScore
import com.example.ui.map.MapSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Unified Search Engine for querying transit stops and addresses
 * across EMT Bus, Metrovalencia, Renfe Cercanías, and OpenStreetMap/Nominatim.
 */
class UnifiedSearchEngine(
    private val database: AppDatabase,
    private val geocodingRepository: GeocodingRepository
) {
    suspend fun performSearch(
        query: String,
        userLat: Double?,
        userLon: Double?,
        busStops: List<GeoportalStopEntity>? = null,
        metroStations: List<MetroStation>? = null,
        cercaniasStations: List<CercaniasStationEntity>? = null,
        busStopAliases: Map<String, String> = emptyMap(),
        customFavorites: List<com.example.ui.map.RecentSearch> = emptyList(),
        favoriteBusStops: Set<String> = emptySet(),
        favoriteMetroStations: Set<String> = emptySet(),
        favoriteCercaniasStations: Set<String> = emptySet()
    ): Flow<List<MapSearchResult>> = flow {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            emit(emptyList())
            return@flow
        }

        val allBusStops = busStops ?: database.geoportalStopDao().getAllActiveStops()
        val allMetroStations = metroStations ?: database.stationDao().getAllStations().map { entity ->
            val cleanZone = com.example.data.model.cleanZoneCode(entity.zone)
            MetroStation(
                id = entity.id.toString(),
                name = entity.name,
                lines = entity.lines.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                description = "Zona $cleanZone",
                latitude = entity.latitude ?: 39.4697,
                longitude = entity.longitude ?: -0.3734,
                zone = cleanZone
            )
        }
        val allCercaniasStations = cercaniasStations ?: database.cercaniasStationDao().getAllStations()

        val localResults = mutableListOf<MapSearchResult>()

        // 0. Custom Favorites matching query
        customFavorites.forEach { fav ->
            val matchTitle = fav.title.contains(trimmed, ignoreCase = true)
            val matchSub = fav.subtitle.contains(trimmed, ignoreCase = true)
            if (matchTitle || matchSub) {
                val nomResult = com.example.data.model.NominatimResult(
                    displayName = if (fav.subtitle.isNotEmpty()) "${fav.title}, ${fav.subtitle}" else fav.title,
                    latitude = fav.latitude,
                    longitude = fav.longitude,
                    type = "favorite",
                    category = "favorite",
                    isLocalStop = false
                )
                localResults.add(MapSearchResult.Address(nomResult, 10000.0))
            }
        }

        // 1. Local Bus Stops
        allBusStops.forEach { stop ->
            val alias = busStopAliases[stop.id_parada]
            val baseScore = computeSearchScore(stop, trimmed, alias)
            if (baseScore > 0.0) {
                val isFav = favoriteBusStops.contains(stop.id_parada)
                val finalScore = if (isFav) baseScore + 10000.0 else baseScore
                localResults.add(MapSearchResult.BusStop(stop, alias, finalScore))
            }
        }

        // 2. Local Metro Stations
        allMetroStations.forEach { station ->
            val baseScore = computeMetroSearchScore(station, trimmed)
            if (baseScore > 0.0) {
                val isFav = favoriteMetroStations.contains(station.id)
                val finalScore = if (isFav) baseScore + 10000.0 else baseScore
                localResults.add(MapSearchResult.Metro(station, finalScore))
            }
        }

        // 3. Local Cercanias Stations
        allCercaniasStations.forEach { station ->
            val baseScore = computeCercaniasSearchScore(station, trimmed)
            if (baseScore > 0.0) {
                val isFav = favoriteCercaniasStations.contains(station.stop_id) || favoriteCercaniasStations.contains(station.id.toString())
                val finalScore = if (isFav) baseScore + 10000.0 else baseScore
                localResults.add(MapSearchResult.Cercanias(station, finalScore))
            }
        }

        // Sort local results by score descending
        val initialSorted = localResults.sortedWith(
            compareByDescending<MapSearchResult> { it.score }
                .thenByDescending { it !is MapSearchResult.Address }
        ).take(12)

        // Emit local results first for instant user feedback
        emit(initialSorted)

        // 4. Remote Nominatim search (always searched and unified by relevance score)
        try {
            val geocodeResult = geocodingRepository.searchRemoteAddress(
                query = trimmed,
                userLat = userLat,
                userLon = userLon,
                acceptLanguage = "ca,es;q=0.9,en;q=0.8"
            )

            if (geocodeResult.isSuccess) {
                val nominatimList = geocodeResult.getOrDefault(emptyList())
                val addressResults = nominatimList
                    .filter { !it.isLocalStop }
                    .map { addr ->
                        val score = computeAddressSearchScore(addr, trimmed)
                        MapSearchResult.Address(addr, score)
                    }
                    .filter { it.score > 0.0 }

                val sortedLocal = localResults
                    .sortedByDescending { it.score }
                    .take(5)

                val combined = (sortedLocal + addressResults)
                    .distinctBy { res ->
                        when (res) {
                            is MapSearchResult.BusStop -> "bus_${res.stop.id_parada}"
                            is MapSearchResult.Metro -> "metro_${res.station.id}"
                            is MapSearchResult.Cercanias -> "cercanias_${res.station.stop_id}"
                            is MapSearchResult.Address -> "addr_${res.result.latitude}_${res.result.longitude}_${res.result.displayName}"
                        }
                    }
                    .sortedWith(
                        compareByDescending<MapSearchResult> { it.score }
                            .thenByDescending { it !is MapSearchResult.Address }
                    )
                    .take(15)

                emit(combined)
            }
        } catch (e: Exception) {
            // Keep local results
        }
    }.flowOn(Dispatchers.Default)
}
