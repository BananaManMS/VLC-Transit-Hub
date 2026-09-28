package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object LocationUtils {
    @JvmStatic
    fun hasLocationPermission(context: Context): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    @JvmStatic
    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun hasOnlyCoarseLocationPermission(context: Context): Boolean {
        val hasFine = hasFineLocationPermission(context)
        val hasCoarse = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED
        return !hasFine && hasCoarse
    }

    suspend fun getBestLastLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (continuation.isActive) {
                            continuation.resume(loc ?: getLocationFromLocationManager(context))
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(getLocationFromLocationManager(context))
                        }
                    }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(getLocationFromLocationManager(context))
                }
            }
        }
    }

    @JvmStatic
    fun getLocationFromLocationManager(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                    bestLocation = loc
                }
            } catch (_: Exception) {}
        }
        return bestLocation
    }

    @JvmStatic
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = Math.sin(deltaPhi / 2.0) * Math.sin(deltaPhi / 2.0) +
                Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2.0) * Math.sin(deltaLambda / 2.0)
        val c = Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)) * 2.0
        return 6371000.0 * c
    }

    @JvmStatic
    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000.0) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(Locale("es", "ES"), "%.1f km", distanceMeters / 1000.0)
        }
    }

    @JvmStatic
    fun openGoogleMapsForStation(context: Context, stationName: String) {
        val query = "Metro " + stationName + " Valencia salidas"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query)))
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir Google Maps.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDeviceLocation(context: Context, onLocationResult: (Double, Double) -> Unit) {
        if (!hasLocationPermission(context)) return
        CoroutineScope(Dispatchers.Main).launch {
            val loc = getBestLastLocation(context)
            if (loc != null) {
                onLocationResult(loc.latitude, loc.longitude)
            }
        }
    }

    fun getLocationUpdates(context: Context, intervalMs: Long = 12000L, minDistanceMeters: Float = 10.0f): Flow<Location> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close()
            return@callbackFlow
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = android.location.LocationListener { loc ->
            trySend(loc)
        }
        try {
            val mainLooper = android.os.Looper.getMainLooper()
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, minDistanceMeters, listener, mainLooper)
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, minDistanceMeters, listener, mainLooper)
            }
            // Piggyback on location updates from other apps (like Google Maps or system apps) using PASSIVE_PROVIDER.
            // This allows receiving fresh high-accuracy updates for free without firing up the GPS sensor ourselves.
            if (locationManager?.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) == true) {
                locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, intervalMs, minDistanceMeters, listener, mainLooper)
            }
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose {
            try {
                locationManager?.removeUpdates(listener)
            } catch (_: Exception) {}
        }
    }

    fun getDynamicLocationUpdates(context: Context, intervalFlow: StateFlow<Long>, minDistanceMeters: Float = 10.0f): Flow<Location> {
        return intervalFlow.transformLatest { interval ->
            emitAll(getLocationUpdates(context, interval, minDistanceMeters))
        }
    }
}
