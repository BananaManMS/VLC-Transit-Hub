package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

object LocationUtils {

    fun hasLocationPermission(context: Context): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasOnlyCoarseLocationPermission(context: Context): Boolean {
        val hasFine = hasFineLocationPermission(context)
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return !hasFine && hasCoarse
    }

    @Suppress("MissingPermission")
    suspend fun getBestLastLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        return suspendCancellableCoroutine { continuation ->
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) {
                            if (location != null) {
                                continuation.resume(location)
                            } else {
                                continuation.resume(getLocationFromLocationManager(context))
                            }
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(getLocationFromLocationManager(context))
                        }
                    }
            } catch (_: Exception) {
                if (continuation.isActive) {
                    continuation.resume(getLocationFromLocationManager(context))
                }
            }
        }
    }

    @Suppress("MissingPermission")
    fun getLocationFromLocationManager(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            } catch (_: Exception) {
            }
        }
        return bestLocation
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(Locale("es", "ES"), "%.1f km", distanceMeters / 1000.0)
        }
    }

    fun openGoogleMapsForStation(context: Context, stationName: String) {
        val query = "Metro $stationName Valencia salidas"
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        )
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "No se pudo abrir Google Maps.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDeviceLocation(context: Context, onLocationResult: (Double, Double) -> Unit) {
        if (hasLocationPermission(context)) {
            CoroutineScope(Dispatchers.Main).launch {
                val location = getBestLastLocation(context)
                if (location != null) {
                    onLocationResult(location.latitude, location.longitude)
                }
            }
        }
    }

    /**
     * Streams real-time GPS location updates continuously as the user moves.
     * Uses FusedLocationProviderClient with PRIORITY_HIGH_ACCURACY and falls back to LocationManager.
     */
    @Suppress("MissingPermission")
    fun getLocationUpdates(
        context: Context,
        intervalMs: Long = 12000L,
        minDistanceMeters: Float = 10.0f
    ): Flow<Location> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close()
            return@callbackFlow
        }

        // Emit cached last location immediately as initial seed if available
        try {
            getBestLastLocation(context)?.let { trySend(it) }
        } catch (_: Exception) {}

        var fusedClient: FusedLocationProviderClient? = null
        var locationCallback: LocationCallback? = null
        var locationManager: LocationManager? = null
        var gpsListener: android.location.LocationListener? = null
        var networkListener: android.location.LocationListener? = null

        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .setMinUpdateDistanceMeters(minDistanceMeters)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        trySend(loc)
                    }
                }
            }
            locationCallback = callback
            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            // Fallback to LocationManager if Google Play Services / FusedLocation fails
            try {
                locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null) {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            trySend(location)
                        }
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    gpsListener = listener
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            intervalMs,
                            minDistanceMeters,
                            listener,
                            Looper.getMainLooper()
                        )
                    }
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        val netListener = object : android.location.LocationListener {
                            override fun onLocationChanged(location: Location) {
                                trySend(location)
                            }
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }
                        networkListener = netListener
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            intervalMs,
                            minDistanceMeters,
                            netListener,
                            Looper.getMainLooper()
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        awaitClose {
            try {
                if (fusedClient != null && locationCallback != null) {
                    fusedClient.removeLocationUpdates(locationCallback)
                }
            } catch (_: Exception) {}
            try {
                if (locationManager != null) {
                    gpsListener?.let { locationManager.removeUpdates(it) }
                    networkListener?.let { locationManager.removeUpdates(it) }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Streams continuous GPS location updates with a dynamically reactive interval.
     * Whenever intervalFlow emits a new value, the location request automatically adjusts.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("MissingPermission")
    fun getDynamicLocationUpdates(
        context: Context,
        intervalFlow: StateFlow<Long>,
        minDistanceMeters: Float = 10.0f
    ): Flow<Location> = intervalFlow.flatMapLatest { intervalMs ->
        getLocationUpdates(context, intervalMs, minDistanceMeters)
    }
}
