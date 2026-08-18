package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.NominatimResult
import com.example.data.network.NetworkModule
import com.example.data.network.NominatimApiService
import com.example.data.network.NominatimResultDto
import com.example.ui.map.MapConfig
import com.example.util.LocationUtils
import com.example.util.retryIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import android.util.LruCache

class NominatimNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
class NominatimRateLimitException(message: String) : Exception(message)
class NominatimServerException(val code: Int, message: String) : Exception("Nominatim server returned code $code: $message")

class GeocodingRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val apiServiceOverride: NominatimApiService? = null
) {
    private val geoportalStopDao = database.geoportalStopDao()
    private val stationDao = database.stationDao()
    private val cercaniasStationDao = database.cercaniasStationDao()
    private val metrobusStopDao = database.metrobusStopDao()

    private val apiService: NominatimApiService by lazy {
        apiServiceOverride ?: NetworkModule.nominatimRetrofit.create(NominatimApiService::class.java)
    }

    // Fast in-memory LRU Cache for query results (50 entries)
    private val addressSearchCache = LruCache<String, List<NominatimResult>>(50)
    private val remoteSearchCache = LruCache<String, List<NominatimResult>>(50)

    suspend fun searchAddress(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null
    ): Result<List<NominatimResult>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val cacheKey = trimmedQuery.lowercase()
        addressSearchCache.get(cacheKey)?.let { cached ->
            return@withContext Result.success(cached)
        }

        try {
            // 1. Search locally in Room database first
            val localMatches = mutableListOf<NominatimResult>()

            // A. EMT Stops
            val emtStops = geoportalStopDao.searchActiveStops("%$trimmedQuery%")
            emtStops.forEach { stop ->
                localMatches.add(
                    NominatimResult(
                        displayName = "Parada ${stop.id_parada} - ${stop.denominacion} (EMT)",
                        latitude = stop.lat,
                        longitude = stop.lon,
                        type = "bus_stop",
                        category = "bus",
                        isLocalStop = true,
                        stopId = stop.id_parada,
                        stopType = "EMT"
                    )
                )
            }

            // B. Metrobus Stops
            val metrobusStops = metrobusStopDao.searchActiveStops("%$trimmedQuery%")
            metrobusStops.forEach { stop ->
                localMatches.add(
                    NominatimResult(
                        displayName = "Parada ${stop.id_parada} - ${stop.denominacion} (MetroBus)",
                        latitude = stop.lat,
                        longitude = stop.lon,
                        type = "bus_stop",
                        category = "bus",
                        isLocalStop = true,
                        stopId = stop.id_parada,
                        stopType = "METROBUS"
                    )
                )
            }

            // C. Metrovalencia Stations
            val metroStations = stationDao.getAllStations()
            val filteredMetro = metroStations.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) || it.id.toString() == trimmedQuery
            }
            filteredMetro.forEach { station ->
                localMatches.add(
                    NominatimResult(
                        displayName = "Estación ${station.name} (Metrovalencia)",
                        latitude = station.latitude ?: 39.4697,
                        longitude = station.longitude ?: -0.3734,
                        type = "station",
                        category = "subway",
                        isLocalStop = true,
                        stopId = station.id.toString(),
                        stopType = "METRO"
                    )
                )
            }

            // D. Cercanías Stations
            val cercaniasStations = cercaniasStationDao.getAllStations()
            val filteredCercanias = cercaniasStations.filter {
                it.nombre.contains(trimmedQuery, ignoreCase = true) || it.stop_id.contains(trimmedQuery, ignoreCase = true)
            }
            filteredCercanias.forEach { station ->
                localMatches.add(
                    NominatimResult(
                        displayName = "Estación ${station.displayName} (Cercanías)",
                        latitude = station.lat,
                        longitude = station.lon,
                        type = "station",
                        category = "rail",
                        isLocalStop = true,
                        stopId = station.stop_id,
                        stopType = "CERCANIAS"
                    )
                )
            }

            val sortedLocal = if (userLat != null && userLon != null) {
                localMatches.sortedBy { match ->
                    LocationUtils.calculateDistanceMeters(userLat, userLon, match.latitude, match.longitude)
                }
            } else {
                localMatches
            }

            val remoteResults = try {
                searchRemoteAddress(trimmedQuery, userLat, userLon).getOrDefault(emptyList())
            } catch (e: Exception) {
                emptyList()
            }

            val combined = (sortedLocal.take(8) + remoteResults)
                .distinctBy { Pair(String.format(Locale.US, "%.4f", it.latitude), String.format(Locale.US, "%.4f", it.longitude)) }

            addressSearchCache.put(cacheKey, combined)
            return@withContext Result.success(combined)

        } catch (e: Exception) {
            Log.e("GeocodingRepository", "Unexpected error in local search", e)
            return@withContext searchRemoteAddress(trimmedQuery, userLat, userLon)
        }
    }

    suspend fun searchRemoteAddress(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null,
        acceptLanguage: String = "ca,es;q=0.9,en;q=0.8"
    ): Result<List<NominatimResult>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val cacheKey = trimmedQuery.lowercase()
        remoteSearchCache.get(cacheKey)?.let { cached ->
            return@withContext Result.success(cached)
        }

        try {
            val dtoList = retryIO {
                apiService.search(
                    query = trimmedQuery,
                    viewbox = MapConfig.VALENCIA_PROVINCE_VIEWBOX,
                    bounded = 1,
                    acceptLanguage = acceptLanguage
                )
            }

            val remoteResults = dtoList
                .filter { dto -> isValenciaProvince(dto) }
                .map { dto ->
                    NominatimResult(
                        displayName = dto.displayName ?: "Ubicación desconocida",
                        latitude = dto.lat?.toDoubleOrNull() ?: 0.0,
                        longitude = dto.lon?.toDoubleOrNull() ?: 0.0,
                        type = dto.type ?: "",
                        category = dto.category ?: dto.clazz ?: "",
                        isLocalStop = false,
                        stopId = null,
                        stopType = null
                    )
                }

            val finalResults = if (userLat != null && userLon != null) {
                remoteResults.sortedBy { result ->
                    LocationUtils.calculateDistanceMeters(userLat, userLon, result.latitude, result.longitude)
                }
            } else {
                remoteResults
            }

            remoteSearchCache.put(cacheKey, finalResults)
            Result.success(finalResults)

        } catch (e: HttpException) {
            val code = e.code()
            Log.e("GeocodingRepository", "HttpException from Nominatim with code $code", e)
            val wrapped = when (code) {
                429 -> NominatimRateLimitException("Nominatim API rate limit reached")
                else -> NominatimServerException(code, e.message() ?: "Server Error")
            }
            Result.failure(wrapped)
        } catch (e: IOException) {
            Log.e("GeocodingRepository", "IOException from Nominatim", e)
            Result.failure(NominatimNetworkException("Network error contacting Nominatim: ${e.localizedMessage}", e))
        } catch (e: Exception) {
            Log.e("GeocodingRepository", "Unexpected error in Nominatim geocoding", e)
            Result.failure(e)
        }
    }

    private fun isValenciaProvince(dto: com.example.data.network.NominatimResultDto): Boolean {
        val address = dto.address
        val displayName = dto.displayName.orEmpty()

        if (address != null) {
            val iso = address.isoProvince?.trim()
            if (iso.equals("ES-V", ignoreCase = true)) {
                return true
            }
            if (!iso.isNullOrEmpty() && !iso.equals("ES-V", ignoreCase = true)) {
                return false
            }

            val provinceName = (address.stateDistrict ?: address.province)?.trim()
            if (provinceName != null) {
                if (provinceName.equals("Valencia", ignoreCase = true) ||
                    provinceName.equals("València", ignoreCase = true) ||
                    provinceName.equals("Valéncia", ignoreCase = true)
                ) {
                    return true
                }
                // Explicitly in another province (e.g. Albacete, Alicante, Castellón, Teruel, Cuenca)
                return false
            }

            val county = address.county?.trim()
            if (county != null && (county.contains("Valencia", ignoreCase = true) || county.contains("València", ignoreCase = true))) {
                return true
            }
        }

        return displayName.contains("Valencia", ignoreCase = true) ||
               displayName.contains("València", ignoreCase = true)
    }

    fun reverseGeocode(lat: Double, lon: Double): Flow<String?> = flow {
        try {
            val response = retryIO {
                apiService.reverse(lat, lon)
            }
            val displayName = response.displayName
            if (!displayName.isNullOrBlank()) {
                val address = response.address
                val shortName = if (address != null && !address.road.isNullOrBlank()) {
                    val road = address.road
                    val hn = if (!address.houseNumber.isNullOrBlank()) " ${address.houseNumber}" else ""
                    val city = address.city ?: address.town ?: address.village ?: "Valencia"
                    "$road$hn, $city"
                } else {
                    displayName
                }
                emit(shortName)
            } else {
                emit("Ubicación seleccionada (${String.format(Locale.US, "%.4f, %.4f", lat, lon)})")
            }
        } catch (e: Exception) {
            Log.e("GeocodingRepository", "Error reverse geocoding $lat, $lon", e)
            emit("Ubicación seleccionada (${String.format(Locale.US, "%.4f, %.4f", lat, lon)})")
        }
    }.flowOn(Dispatchers.IO)
}
