package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.NominatimResult
import com.example.ui.bus.computeAddressSearchScore
import com.example.ui.bus.computeSearchScore
import com.example.ui.map.MapSearchResult
import com.example.ui.map.RecentSearch
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

    constructor(context: Context, database: AppDatabase, geocodingRepository: GeocodingRepository) {
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

        val localResults = mutableListOf<MapSearchResult>()

        val effectiveBus = getEffectiveBusStops(busStops)
        val effectiveMetro = getEffectiveMetroStations(metroStations)
        val effectiveCercanias = getEffectiveCercaniasStations(cercaniasStations)
        val effectiveMetrobus = getEffectiveMetrobusStops(metrobusStops)

        // 1. EMT Bus Stops
        for (item in effectiveBus) {
            val alias = busStopAliases[item.id_parada]
            val score = computeSearchScore(item, trimmed, alias)
            if (score > 0) {
                val boost = if (favoriteBusStops.contains(item.id_parada)) 50.0 else 0.0
                localResults.add(MapSearchResult.BusStop(stop = item, alias = alias, score = score + boost))
            }
        }

        // 2. Metrovalencia Stations
        for (item in effectiveMetro) {
            val score = computeSearchScore(
                stopId = item.id,
                stopName = item.name,
                query = trimmed,
                alias = null,
                lines = item.lines
            )
            if (score > 0) {
                val boost = if (favoriteMetroStations.contains(item.id)) 50.0 else 0.0
                localResults.add(MapSearchResult.Metro(station = item, score = score + boost))
            }
        }

        // 3. Cercanias Stations
        for (item in effectiveCercanias) {
            val linesList = item.lines.map { it.trim() }.filter { it.isNotEmpty() }
            val score = computeSearchScore(
                stopId = item.stop_id,
                stopName = item.nombre,
                query = trimmed,
                alias = null,
                lines = linesList
            )
            if (score > 0) {
                val boost = if (favoriteCercaniasStations.contains(item.stop_id)) 50.0 else 0.0
                localResults.add(MapSearchResult.Cercanias(station = item, score = score + boost))
            }
        }

        // 4. Metrobus Stops
        for (item in effectiveMetrobus) {
            val alias = metrobusStopAliases[item.id_parada]
            val linesList = item.lineas.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val score = computeSearchScore(
                stopId = item.id_parada,
                stopName = item.denominacion,
                query = trimmed,
                alias = alias,
                lines = linesList
            )
            if (score > 0) {
                val boost = if (favoriteMetrobusStops.contains(item.id_parada)) 50.0 else 0.0
                localResults.add(MapSearchResult.MetrobusStop(stop = item, alias = alias, score = score + boost))
            }
        }

        // Emit local results first immediately
        val initialSorted = localResults.sortedByDescending { it.score }
        emit(initialSorted)

        // 5. Remote Geocoding via Nominatim
        val geocoder = geocodingRepository
        if (geocoder != null) {
            try {
                val remotePlaces = geocoder.performSearch(trimmed, userLat = userLat, userLon = userLon)
                val allResults = localResults.toMutableList()
                for (place in remotePlaces) {
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
