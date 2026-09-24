package com.example.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.util.LocationUtils
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Services Geofencing lifecycle, GPS accuracy gating, and energy metrics.
 * Gating suspends high-accuracy GNSS hardware between transit stops to preserve battery life,
 * waking it up via a 200m hardware circular geofence around arrival milestones.
 */
class TripGeofenceGpsController(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val _isGeofenceGateOpenState = MutableStateFlow(true)
    val isGeofenceGateOpenState: StateFlow<Boolean> = _isGeofenceGateOpenState.asStateFlow()

    private var lastManagedLegIndex = -1

    // GPS Energy Metrics tracking
    private var highAccuracyStartTimeMs: Long = 0L
    private var totalHighAccuracyActiveMs: Long = 0L
    private var tripStartTimeMs: Long = 0L

    fun startTrip() {
        lastManagedLegIndex = -1
        tripStartTimeMs = System.currentTimeMillis()
        totalHighAccuracyActiveMs = 0L
        highAccuracyStartTimeMs = System.currentTimeMillis()
        _isGeofenceGateOpenState.value = true
    }

    fun openHighAccuracyGate(reason: String) {
        if (!_isGeofenceGateOpenState.value) {
            highAccuracyStartTimeMs = System.currentTimeMillis()
            _isGeofenceGateOpenState.value = true
            android.util.Log.i(
                TAG,
                "🟢 [GPS_GATE_OPEN] HIGH_ACCURACY GPS Activated ($reason). Total active so far: ${totalHighAccuracyActiveMs / 1000}s"
            )
        }
    }

    fun closeHighAccuracyGate(reason: String) {
        if (_isGeofenceGateOpenState.value) {
            if (highAccuracyStartTimeMs > 0L) {
                val activeSegmentMs = System.currentTimeMillis() - highAccuracyStartTimeMs
                totalHighAccuracyActiveMs += activeSegmentMs
                highAccuracyStartTimeMs = 0L
            }
            _isGeofenceGateOpenState.value = false
            android.util.Log.i(
                TAG,
                "🔴 [GPS_GATE_CLOSED] HIGH_ACCURACY GPS Suspended ($reason). Receptor GNSS sleeping. Total active so far: ${totalHighAccuracyActiveMs / 1000}s"
            )
        }
    }

    fun checkAndSyncGeofenceForLeg(trip: ActiveTripState, latestLocation: Location?) {
        val currentLegIndex = trip.currentLegIndex
        val legs = trip.itinerary.legs
        val currentLeg = legs.getOrNull(currentLegIndex) ?: return

        if (currentLegIndex != lastManagedLegIndex) {
            lastManagedLegIndex = currentLegIndex
            val targetLat = currentLeg.toLat
            val targetLon = currentLeg.toLon
            val mode = currentLeg.mode

            clearGeofences()

            val isWalkOrBike = mode == TransitMode.WALK || mode == TransitMode.BICYCLE
            val isShortLeg = currentLeg.distanceMeters <= 250.0

            var isAlreadyNear = false
            if (latestLocation != null && targetLat != 0.0 && targetLon != 0.0) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    latestLocation.latitude, latestLocation.longitude,
                    targetLat, targetLon,
                    results
                )
                if (results[0] <= 250f) {
                    isAlreadyNear = true
                }
            }

            if (isWalkOrBike || isShortLeg || isAlreadyNear || (targetLat == 0.0 && targetLon == 0.0)) {
                android.util.Log.i(
                    TAG,
                    "🎯 [GEOFENCE] Leg #$currentLegIndex ($mode, ${currentLeg.distanceMeters.toInt()}m): Target already near or walk/short leg. Keeping HIGH_ACCURACY gate OPEN."
                )
                openHighAccuracyGate("Walk/Short leg or within 250m target")
            } else {
                android.util.Log.i(
                    TAG,
                    "🎯 [GEOFENCE] Leg #$currentLegIndex ($mode, ${currentLeg.distanceMeters.toInt()}m): Registering 200m geofence around target '${currentLeg.toName}' ($targetLat, $targetLon) and CLOSING HIGH_ACCURACY gate (GPS sleeping)."
                )
                closeHighAccuracyGate("Intermediate transit leg - waiting for 200m target geofence")
                registerTargetGeofence(
                    targetLat = targetLat,
                    targetLon = targetLon,
                    requestId = "leg_${currentLegIndex}_target",
                    radiusMeters = 200f
                )
            }
        }
    }

    fun handleGeofenceTransition(intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent != null && geofencingEvent.hasError()) {
            android.util.Log.e(TAG, "GeofencingEvent error code: ${geofencingEvent.errorCode}")
            openHighAccuracyGate("Geofence error fallback")
            return
        }

        val transitionType = geofencingEvent?.geofenceTransition ?: -1
        val isSimulated = intent.getBooleanExtra(ActiveTripTrackingService.EXTRA_SIMULATED_TRANSITION, false)

        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
            transitionType == Geofence.GEOFENCE_TRANSITION_DWELL ||
            isSimulated
        ) {
            val requestId = intent.getStringExtra(ActiveTripTrackingService.EXTRA_GEOFENCE_REQUEST_ID) ?: "unknown"
            android.util.Log.i(
                TAG,
                "⚡ [GEOFENCE_TRANSITION_ENTER] Entered 200m target geofence (requestId: $requestId)! Switching to PRIORITY_HIGH_ACCURACY GPS."
            )
            openHighAccuracyGate("GEOFENCE_TRANSITION_ENTER for $requestId")
        }
    }

    fun clearGeofences() {
        try {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ActiveTripTrackingService.ACTION_GEOFENCE_TRANSITION
            }
            val pendingIntent = PendingIntent.getService(
                context,
                GEOFENCE_PENDING_INTENT_REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            geofencingClient.removeGeofences(pendingIntent)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error clearing geofences: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerTargetGeofence(
        targetLat: Double,
        targetLon: Double,
        requestId: String,
        radiusMeters: Float = 200f
    ) {
        if (targetLat == 0.0 && targetLon == 0.0) {
            openHighAccuracyGate("Invalid target coordinates (0,0)")
            return
        }

        try {
            val geofence = Geofence.Builder()
                .setRequestId(requestId)
                .setCircularRegion(targetLat, targetLon, radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
                .setNotificationResponsiveness(1000)
                .build()

            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ActiveTripTrackingService.ACTION_GEOFENCE_TRANSITION
                putExtra(ActiveTripTrackingService.EXTRA_GEOFENCE_REQUEST_ID, requestId)
            }

            val pendingIntent = PendingIntent.getService(
                context,
                GEOFENCE_PENDING_INTENT_REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            if (LocationUtils.hasLocationPermission(context)) {
                geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                    .addOnSuccessListener {
                        android.util.Log.i(
                            TAG,
                            "🎯 [GEOFENCE_REGISTERED] 200m Geofence registered around milestone ($targetLat, $targetLon) for $requestId"
                        )
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e(TAG, "Failed to register geofence: ${e.message}. Fallback open gate.", e)
                        openHighAccuracyGate("Geofence registration failed")
                    }
            } else {
                openHighAccuracyGate("Location permission missing")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception registering geofence: ${e.message}. Fallback open gate.", e)
            openHighAccuracyGate("Exception registering geofence")
        }
    }

    fun logGpsMetricsSummary() {
        val now = System.currentTimeMillis()
        var activeMs = totalHighAccuracyActiveMs
        if (_isGeofenceGateOpenState.value && highAccuracyStartTimeMs > 0L) {
            activeMs += (now - highAccuracyStartTimeMs)
        }
        val totalTripMs = (now - tripStartTimeMs).coerceAtLeast(1L)
        val percentageActive = (activeMs * 100) / totalTripMs
        val savedMs = (totalTripMs - activeMs).coerceAtLeast(0L)
        val percentageSaved = 100 - percentageActive

        android.util.Log.i(
            TAG,
            """
            ================================================================================
            📊 [METRICS_SUMMARY] ActiveTripTrackingService GPS Energy Metrics:
            - Total Trip Duration: ${totalTripMs / 1000}s
            - HIGH_ACCURACY GPS Active Duration: ${activeMs / 1000}s ($percentageActive% of trip)
            - GPS Sleeping (Energy Saved): ${savedMs / 1000}s ($percentageSaved% battery saving)
            ================================================================================
            """.trimIndent()
        )
    }

    companion object {
        private const val TAG = "TripGeofenceGps"
        private const val GEOFENCE_PENDING_INTENT_REQ_CODE = 2001
    }
}
