package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkSignalQuality {
    OFFLINE,
    POOR,      // Weak cellular signal (1-2 bars, low bandwidth, or unvalidated connection)
    GOOD       // Robust WiFi or solid LTE/5G
}

fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // Assume available if service cannot be retrieved
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true
    }
}

/**
 * Detects the real-time quality of the active network connection.
 */
fun getNetworkSignalQuality(context: Context): NetworkSignalQuality {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkSignalQuality.GOOD
        val activeNetwork = cm.activeNetwork ?: return NetworkSignalQuality.OFFLINE
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkSignalQuality.OFFLINE
        
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkSignalQuality.OFFLINE
        }

        // On Android 10+ (API 29+), check signal strength level if reported
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val signalLevel = capabilities.signalStrength
            // signalStrength is typically 0 to 4 (or SignalStrength.INVALID / Integer.MIN_VALUE if unmeasured)
            if (signalLevel in 0..1) {
                return NetworkSignalQuality.POOR
            }
        }

        // Check estimated downstream bandwidth
        val bandwidth = capabilities.linkDownstreamBandwidthKbps
        if (bandwidth in 1..450) {
            return NetworkSignalQuality.POOR
        }

        // If on mobile data without validated internet capability
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return NetworkSignalQuality.POOR
        }

        NetworkSignalQuality.GOOD
    } catch (_: Exception) {
        NetworkSignalQuality.GOOD
    }
}

fun isWeakNetworkSignal(context: Context): Boolean {
    val quality = getNetworkSignalQuality(context)
    return quality == NetworkSignalQuality.POOR || quality == NetworkSignalQuality.OFFLINE
}

/**
 * Calibrated adaptive timeout for transit departures fetching:
 * - 4500ms (4.5s) under weak signal to quickly display official scheduled departures without freezing.
 * - 7500ms (7.5s) under good signal to allow remote APIs a fair processing window before falling back.
 * - 300ms if strictly offline (immediate local load).
 */
fun getTransitFetchTimeoutMs(context: Context): Long {
    return when (getNetworkSignalQuality(context)) {
        NetworkSignalQuality.OFFLINE -> 300L
        NetworkSignalQuality.POOR -> 4500L
        NetworkSignalQuality.GOOD -> 7500L
    }
}

fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (cm == null) {
        trySend(true)
        close()
        return@callbackFlow
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }

        override fun onLost(network: Network) {
            trySend(isNetworkAvailable(context))
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            trySend(hasInternet)
        }
    }

    trySend(isNetworkAvailable(context))

    try {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    } catch (e: Exception) {
        Log.w("NetworkUtils", "Could not register network callback: ${e.message}")
    }

    awaitClose {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
    }
}.distinctUntilChanged()

suspend fun <T> retryIO(
    times: Int = 3,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 3000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("NetworkRetry", "Attempt ${attempt + 1}/$times failed: ${e.localizedMessage}", e)
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
    }
    return block() // Last attempt. If it fails, the exception is thrown.
}

