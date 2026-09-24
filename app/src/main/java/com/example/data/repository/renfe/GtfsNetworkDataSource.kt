package com.example.data.repository.renfe

object GtfsNetworkDataSource {
    suspend fun fetchGtfsData(): ByteArray? = null

    suspend fun fetchTripUpdates(): Map<String, GtfsRtTripUpdate> {
        return emptyMap()
    }
}
