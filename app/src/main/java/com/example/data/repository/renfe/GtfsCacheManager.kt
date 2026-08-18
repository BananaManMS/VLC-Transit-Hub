package com.example.data.repository.renfe

import android.util.Log

import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class GtfsCacheManager(
    private val networkDataSource: GtfsNetworkDataSource,
    private val gtfsParser: GtfsParser
) {
    private val vehiclePositionsCache = ConcurrentHashMap<String, LiveVehicleInfo>()
    private val tripUpdatesCache = ConcurrentHashMap<String, GtfsRtTripUpdate>()
    private val vehicleCacheTimes = ConcurrentHashMap<String, Long>()
    private val tripUpdateCacheTimes = ConcurrentHashMap<String, Long>()

    @Volatile
    var lastVehiclePositionsHeaderTimestamp: Long = 0L
    @Volatile
    var lastTripUpdatesHeaderTimestamp: Long = 0L

    @Volatile
    private var lastVehicleFetchTime: Long = 0L
    @Volatile
    private var lastUpdatesFetchTime: Long = 0L

    // Network request cache threshold (e.g. 15 seconds)
    private val NETWORK_FETCH_THRESHOLD_SEC = 15
    // Data expiration threshold (10 minutes)
    private val DATA_EXPIRATION_THRESHOLD_SEC = 600

    fun getVehicleCacheTime(tripId: String): Long? = vehicleCacheTimes[tripId]

    suspend fun getLiveVehiclePositions(): Map<String, LiveVehicleInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        val shouldFetch = now - lastVehicleFetchTime > NETWORK_FETCH_THRESHOLD_SEC

        if (shouldFetch) {
            try {
                val text = networkDataSource.fetchVehiclePositionsJson()
                if (text != null) {
                    val (headerTimestamp, parsedMap) = gtfsParser.parseVehiclePositions(text, now)
                    lastVehiclePositionsHeaderTimestamp = headerTimestamp
                    lastVehicleFetchTime = now
                    
                    // Cleanup expired items
                    for ((key, time) in vehicleCacheTimes) {
                        if (now - time > DATA_EXPIRATION_THRESHOLD_SEC) {
                            vehiclePositionsCache.remove(key)
                            vehicleCacheTimes.remove(key)
                        }
                    }
                    
                    // Merge new items
                    for ((tripId, newVeh) in parsedMap) {
                        var resolvedVeh = newVeh
                        if (newVeh.status != "STOPPED_AT") {
                            val cachedVeh = vehiclePositionsCache[tripId]
                            if (cachedVeh != null && 
                                cachedVeh.status == "STOPPED_AT" && 
                                newVeh.status == "IN_TRANSIT_TO" &&
                                cachedVeh.currentStopId == newVeh.currentStopId
                            ) {
                                resolvedVeh = newVeh.copy(status = "STOPPED_AT")
                            }
                        }
                        vehiclePositionsCache[tripId] = resolvedVeh
                        vehicleCacheTimes[tripId] = now
                    }
                }
            } catch (e: Exception) {
                Log.w("GtfsCacheManager", "Live Vehicle Positions fetch warning: ${e.message}")
            }
        }
        
        // Return a copy of the current cache
        vehiclePositionsCache.toMap()
    }

    suspend fun getLiveTripUpdates(): Map<String, GtfsRtTripUpdate> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        val shouldFetch = now - lastUpdatesFetchTime > NETWORK_FETCH_THRESHOLD_SEC

        if (shouldFetch) {
            try {
                val text = networkDataSource.fetchTripUpdatesJson()
                if (text != null) {
                    val (headerTimestamp, parsedMap) = gtfsParser.parseTripUpdates(text)
                    lastTripUpdatesHeaderTimestamp = headerTimestamp
                    lastUpdatesFetchTime = now
                    
                    // Cleanup expired items
                    for ((key, time) in tripUpdateCacheTimes) {
                        if (now - time > DATA_EXPIRATION_THRESHOLD_SEC) {
                            tripUpdatesCache.remove(key)
                            tripUpdateCacheTimes.remove(key)
                        }
                    }
                    
                    // Merge new items
                    for ((tripId, update) in parsedMap) {
                        tripUpdatesCache[tripId] = update
                        tripUpdateCacheTimes[tripId] = now
                    }
                }
            } catch (e: Exception) {
                Log.w("GtfsCacheManager", "Live GTFS-RT fetch warning: ${e.message}")
            }
        }
        
        // Return a copy of the current cache
        tripUpdatesCache.toMap()
    }
}
