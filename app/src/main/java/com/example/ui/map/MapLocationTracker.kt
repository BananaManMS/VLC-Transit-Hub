package com.example.ui.map

import android.content.Context
import android.location.Location
import com.example.util.LocationUtils
import kotlinx.coroutines.flow.Flow

/**
 * Stateless assistant service that exposes cold GPS location streams and one-off queries.
 * Coroutine jobs and lifecycle are owned and managed by MapViewModel's viewModelScope.
 */
class MapLocationTracker {

    /**
     * Cold Flow for continuous GPS updates.
     * Begins emitting when collected and cleanly cancels underlying listener when the collector cancels.
     */
    fun getLocationUpdates(
        context: Context,
        intervalMs: Long = 12000L,
        minDistanceMeters: Float = 10.0f
    ): Flow<Location> {
        return LocationUtils.getLocationUpdates(
            context = context.applicationContext,
            intervalMs = intervalMs,
            minDistanceMeters = minDistanceMeters
        )
    }

    suspend fun getLastKnownLocation(context: Context): Location? {
        return LocationUtils.getBestLastLocation(context.applicationContext)
    }

    fun requestSingleLocation(context: Context, onLocation: (Double, Double) -> Unit) {
        LocationUtils.requestDeviceLocation(context.applicationContext, onLocation)
    }
}
