package com.example.data.repository.renfe

import android.content.Context
import com.example.ui.cercanias.LiveVehicleInfo

class GtfsCacheManager(private val context: Context) {
    suspend fun updateCache() {}

    suspend fun getLiveVehiclePositions(): Map<String, LiveVehicleInfo> {
        return emptyMap()
    }

    suspend fun getLiveTripUpdates(): Map<String, GtfsRtTripUpdate> {
        return emptyMap()
    }
}
