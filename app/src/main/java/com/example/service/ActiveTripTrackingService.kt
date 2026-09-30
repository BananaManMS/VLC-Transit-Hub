package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.data.database.AppDatabase
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripRepository
import com.example.data.repository.ActiveTripState
import com.example.util.ActiveProgressInfo
import com.example.util.ActiveTripProgressTracker
import com.example.util.ActiveTripSnapshotBuilder
import com.example.util.BoardingSensorFusionEngine
import com.example.util.LocationUtils
import com.example.util.RealTimeTripStatus
import com.example.util.StepProgressionResult
import com.example.util.TripItineraryTimeSyncEngine
import com.example.util.TripRealTimeReconciler
import com.example.util.TripSensoryAlertManager
import com.example.util.TripStepProgressionEngine
import com.example.util.UnifiedActiveTripStateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground Service for active multimodal trip GPS tracking and automatic step progression.
 * Delivers silent, rich Live Notifications featuring a graphical segmented progress bar,
 * live telemetry updates, ETA metrics, and one-tap trip cancellation.
 */
class ActiveTripTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    private lateinit var activeTripRepository: ActiveTripRepository
    private lateinit var geofenceGpsController: TripGeofenceGpsController
    private lateinit var tripNotificationManager: TripNotificationManager

    private val tripReconciler = TripRealTimeReconciler()
    private val sensorFusionEngine = BoardingSensorFusionEngine()
    private var currentActiveTrip: ActiveTripState? = null
    private var latestLocation: android.location.Location? = null
    private var latestRealTimeStatus: RealTimeTripStatus? = null

    // Dynamic location interval state (default 12000ms for high energy efficiency)
    private val locationIntervalState = MutableStateFlow(12000L)

    // Strict per-leg idempotency for the BOARDED transit event
    private val lastBoardedLegIndex = AtomicInteger(-1)
    private var hasAlertedFinalArrival: Boolean = false
    private var hasAlertedBoardingConfirmation: Boolean = false

    override fun onCreate() {
        super.onCreate()
        geofenceGpsController = TripGeofenceGpsController(this)
        val database = AppDatabase.getDatabase(applicationContext)
        activeTripRepository = ActiveTripRepository(database.activeTripDao())
        tripNotificationManager = TripNotificationManager(this)
        tripNotificationManager.createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lastBoardedLegIndex.set(-1)
                sensorFusionEngine.reset()
                tripNotificationManager.resetAlerts()
                hasAlertedFinalArrival = false
                hasAlertedBoardingConfirmation = false
                geofenceGpsController.startTrip()
                startForegroundTracking()
            }
            ACTION_STOP -> {
                stopForegroundTracking()
                serviceScope.launch {
                    try {
                        activeTripRepository.cancelActiveTrip()
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Error cancelling active trip repository: ${e.message}")
                    } finally {
                        stopSelf()
                    }
                }
            }
            ACTION_GEOFENCE_TRANSITION -> {
                if (intent != null) {
                    geofenceGpsController.handleGeofenceTransition(intent)
                }
            }
            ACTION_MANUAL_BOARDING -> {
                val legIndex = intent.getIntExtra(EXTRA_LEG_INDEX, -1)
                if (legIndex >= 0) {
                    handleManualBoarding(legIndex)
                }
            }
            ACTION_REJECT_BOARDING -> {
                val legIndex = intent.getIntExtra(EXTRA_LEG_INDEX, -1)
                android.util.Log.i(TAG, "User rejected boarding on leg $legIndex, clearing grace period and recalculating")
                tripNotificationManager.dismissBoardingConfirmationNotification()
                tripReconciler.clearGracePeriod()
                hasAlertedBoardingConfirmation = false
                UnifiedActiveTripStateTracker.triggerImmediateReconcile()
            }
            ACTION_FORCE_RECONCILE -> {
                UnifiedActiveTripStateTracker.triggerImmediateReconcile()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        if (!LocationUtils.hasLocationPermission(applicationContext)) {
            android.util.Log.w(TAG, "Location permission not granted. Cannot start Foreground Service.")
            stopSelf()
            return
        }

        val initialNotification = tripNotificationManager.buildInitialFallbackNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TripNotificationManager.NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(TripNotificationManager.NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to startForeground: ${e.message}", e)
            stopSelf()
            return
        }

        // Cancel previous job if running
        trackingJob?.cancel()

        trackingJob = serviceScope.launch {
            // 1. Keep active trip state in sync and evaluate leg geofencing
            launch {
                activeTripRepository.getActiveTripFlow().collectLatest { trip ->
                    currentActiveTrip = trip
                    if (trip == null) {
                        stopForegroundTracking()
                        stopSelf()
                    } else {
                        geofenceGpsController.checkAndSyncGeofenceForLeg(trip, latestLocation)
                        updateSnapshotAndNotification(trip)
                    }
                }
            }

            // 2. Dynamic cadence live reconciliation background loop (25s FG / 60s BG)
            launch {
                while (coroutineContext.isActive) {
                    val trip = currentActiveTrip
                    val loc = latestLocation
                    if (trip != null) {
                        val status = tripReconciler.reconcile(
                            activeTrip = trip,
                            userLat = loc?.latitude,
                            userLon = loc?.longitude
                        )
                        latestRealTimeStatus = status

                        val syncedItinerary = TripItineraryTimeSyncEngine.syncRealTimeItinerary(
                            itinerary = trip.itinerary,
                            status = status,
                            currentLegIndex = trip.currentLegIndex
                        )
                        if (syncedItinerary != trip.itinerary) {
                            activeTripRepository.updateItinerary(syncedItinerary)
                        }

                        updateSnapshotAndNotification(trip)
                    }

                    // Adaptive cadence: 25s when app is foregrounded, 60s when backgrounded
                    val cadenceMs = if (UnifiedActiveTripStateTracker.isAppForegrounded.value) 25_000L else 60_000L
                    withTimeoutOrNull(cadenceMs) {
                        UnifiedActiveTripStateTracker.forceReconcileTrigger.first()
                    }
                }
            }

            // 3. Sensor Fusion: Confidence collector for BOARDED transition orchestration
            launch {
                sensorFusionEngine.confidenceFlow
                    .filter { confidence -> confidence >= BOARDING_CONFIDENCE_THRESHOLD }
                    .collect { highConfidence ->
                        val trip = currentActiveTrip ?: return@collect
                        val currentLegIndex = trip.currentLegIndex
                        val currentLeg = trip.itinerary.legs.getOrNull(currentLegIndex) ?: return@collect

                        if (currentLeg.mode == TransitMode.WALK || currentLeg.mode == TransitMode.BICYCLE) {
                            return@collect
                        }

                        // Idempotency check: trigger exactly once per transit leg
                        val prevBoarded = lastBoardedLegIndex.get()
                        if (prevBoarded == currentLegIndex) {
                            return@collect
                        }
                        if (!lastBoardedLegIndex.compareAndSet(prevBoarded, currentLegIndex)) {
                            return@collect
                        }

                        android.util.Log.i(
                            TAG,
                            "Boarding confirmed by Sensor Fusion (confidence: $highConfidence) on leg #$currentLegIndex (${currentLeg.mode})"
                        )

                        // Parallel Atomic Dispatch: UX / Reconciler / Spatial Engine
                        TripBoardingDispatcher.dispatchBoardingActionsConcurrently(
                            context = applicationContext,
                            scope = serviceScope,
                            reconciler = tripReconciler,
                            leg = currentLeg,
                            legIndex = currentLegIndex,
                            getActiveTrip = { currentActiveTrip },
                            onUpdateNotification = { updateSnapshotAndNotification(it) }
                        )
                    }
            }

            // 4. 4-second ticker for dead-reckoning progress estimation when underground or GPS fix is weak,
            // and checking the 2-minute departed vehicle grace period
            launch {
                while (coroutineContext.isActive) {
                    val trip = currentActiveTrip
                    if (trip != null) {
                        val loc = latestLocation
                        val currentLeg = trip.itinerary.legs.getOrNull(trip.currentLegIndex)
                        val isTransit = currentLeg?.mode in listOf(
                            TransitMode.SUBWAY,
                            TransitMode.BUS,
                            TransitMode.TRAM,
                            TransitMode.RAIL
                        )
                        if (isTransit) {
                            TripStepProgressionEngine.evaluateProgression(
                                userLat = loc?.latitude ?: 0.0,
                                userLon = loc?.longitude ?: 0.0,
                                activeTrip = trip,
                                locationAccuracyMeters = loc?.accuracy,
                                lastLocationTimeMillis = loc?.time ?: System.currentTimeMillis()
                            )
                            updateSnapshotAndNotification(trip)
                        }

                        // Grace period monitoring for departed/disappeared transit vehicle
                        if (tripReconciler.isGracePeriodActive()) {
                            val now = System.currentTimeMillis()
                            val graceUntilMs = tripReconciler.getGracePeriodUntilMs() ?: (now + 120_000L)
                            val targetTransitLegIndex = if (currentLeg?.mode == TransitMode.WALK && trip.currentLegIndex + 1 < trip.itinerary.legs.size) {
                                trip.currentLegIndex + 1
                            } else {
                                trip.currentLegIndex
                            }
                            val targetMode = trip.itinerary.legs.getOrNull(targetTransitLegIndex)?.mode ?: TransitMode.SUBWAY
                            val rawVehicleName = tripReconciler.getGracePeriodVehicleName()
                                ?: trip.itinerary.legs.getOrNull(targetTransitLegIndex)?.routeShortName
                                ?: "Metro"
                            val vehicleName = formatVehicleNameForNotification(rawVehicleName, targetMode)

                            // Prompt user via notification immediately when vehicle departs/disappears from departures board
                            if (!hasAlertedBoardingConfirmation) {
                                android.util.Log.i(TAG, "Grace period: vehicle departed/disappeared from board, prompting user for boarding confirmation: $vehicleName (leg $targetTransitLegIndex)")
                                hasAlertedBoardingConfirmation = true
                                tripNotificationManager.showBoardingConfirmationNotification(vehicleName, targetTransitLegIndex)
                            }

                            // 2 minutes of courtesy strictly based on live departure time expired without confirmation or motion
                            if (now >= graceUntilMs) {
                                android.util.Log.i(TAG, "Grace period: 2 minutes based on live departure time expired without confirmation or motion. Rollover/Recalculate now!")
                                tripNotificationManager.dismissBoardingConfirmationNotification()
                                tripReconciler.clearGracePeriod()
                                hasAlertedBoardingConfirmation = false
                                UnifiedActiveTripStateTracker.triggerImmediateReconcile()
                            }
                        } else if (hasAlertedBoardingConfirmation) {
                            val progressState = ActiveTripProgressTracker.progressState.value
                            if (progressState.isBoarded) {
                                tripNotificationManager.dismissBoardingConfirmationNotification()
                                hasAlertedBoardingConfirmation = false
                            }
                        }
                    }
                    delay(4000L)
                }
            }

            // 5. Stream continuous GPS updates gated by target geofence (GPS sleeps while traveling between stations)
            @OptIn(ExperimentalCoroutinesApi::class)
            val gatedLocationFlow = geofenceGpsController.isGeofenceGateOpenState.flatMapLatest { isGateOpen ->
                if (isGateOpen) {
                    android.util.Log.i(TAG, "🟢 [GPS_GATE] Gate OPEN: Requesting GPS updates with PRIORITY_HIGH_ACCURACY")
                    LocationUtils.getDynamicLocationUpdates(
                        context = applicationContext,
                        intervalFlow = locationIntervalState,
                        minDistanceMeters = 10.0f
                    )
                } else {
                    android.util.Log.i(TAG, "🔴 [GPS_GATE] Gate CLOSED: Cancelling HIGH_ACCURACY GPS requests. Receptor GNSS sleeping.")
                    emptyFlow()
                }
            }

            gatedLocationFlow.collectLatest { location ->
                latestLocation = location
                val trip = currentActiveTrip ?: return@collectLatest
                val legs = trip.itinerary.legs
                val currentLeg = legs.getOrNull(trip.currentLegIndex)

                // Feed Sensor Fusion Engine with latest GPS, kinematic and real-time feed data
                sensorFusionEngine.evaluate(
                    location = location,
                    currentLeg = currentLeg,
                    currentLegIndex = trip.currentLegIndex,
                    realTimeArrivalMinutes = latestRealTimeStatus?.vehicleArrivalMinutes,
                    realTimeSecondsRemaining = latestRealTimeStatus?.vehicleSecondsRemaining,
                    isUndergroundMode = currentLeg?.mode == TransitMode.SUBWAY
                )

                val result = TripStepProgressionEngine.evaluateProgression(
                    userLat = location.latitude,
                    userLon = location.longitude,
                    activeTrip = trip,
                    locationAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                    lastLocationTimeMillis = location.time
                )

                when (result) {
                    is StepProgressionResult.LegCompleted -> {
                        if (result.isFinalLeg) {
                            val isEs = tripNotificationManager.getAppLanguage() == com.example.ui.dashboard.AppLanguage.ES
                            val arrivalTitle = if (isEs) "¡Has llegado a tu destino!" else "¡Has arribat al teu destí!"
                            val arrivalContent = if (isEs) "Viaje completado con éxito · ${trip.destinationName}" else "Viatge completat amb èxit · ${trip.destinationName}"

                            activeTripRepository.markTripCompleted()
                            tripNotificationManager.updateNotificationSimple(arrivalTitle, arrivalContent)
                            geofenceGpsController.closeHighAccuracyGate("Final leg arrival completed")

                            if (!hasAlertedFinalArrival) {
                                hasAlertedFinalArrival = true
                                TripSensoryAlertManager.triggerLevel2AttentionCall(applicationContext, playAudio = true)
                            }

                            serviceScope.launch {
                                delay(45_000L)
                                activeTripRepository.completeActiveTrip()
                                stopForegroundTracking()
                                stopSelf()
                            }
                        } else {
                            activeTripRepository.advanceLegIndex(result.nextLegIndex)
                        }
                    }
                    is StepProgressionResult.OnTrack -> {
                        updateSnapshotAndNotification(trip, result.distanceToNextTargetMeters)
                    }
                    is StepProgressionResult.NoOp -> {
                        // Trip has no legs or is empty
                    }
                }
            }
        }
    }

    private fun handleManualBoarding(legIndex: Int) {
        serviceScope.launch {
            val trip = currentActiveTrip ?: return@launch
            val leg = trip.itinerary.legs.getOrNull(legIndex) ?: return@launch

            tripNotificationManager.dismissBoardingConfirmationNotification()
            hasAlertedBoardingConfirmation = false

            TripBoardingDispatcher.dispatchBoardingActionsConcurrently(
                context = applicationContext,
                scope = serviceScope,
                reconciler = tripReconciler,
                leg = leg,
                legIndex = legIndex,
                getActiveTrip = { currentActiveTrip },
                onUpdateNotification = { updateSnapshotAndNotification(it) }
            )

            if (legIndex != trip.currentLegIndex) {
                activeTripRepository.advanceLegIndex(legIndex)
            }
            UnifiedActiveTripStateTracker.triggerImmediateReconcile()
        }
    }

    private fun reevaluateLocationInterval() {
        val trip = currentActiveTrip ?: return
        val targetIntervalMs = TripLocationIntervalPolicy.computeLocationInterval(
            trip = trip,
            latestRealTimeStatus = latestRealTimeStatus,
            isGeofenceGateOpen = geofenceGpsController.isGeofenceGateOpenState.value,
            onOpenHighAccuracyGate = { reason -> geofenceGpsController.openHighAccuracyGate(reason) }
        )

        if (locationIntervalState.value != targetIntervalMs) {
            val mode = trip.itinerary.legs.getOrNull(trip.currentLegIndex)?.mode
            android.util.Log.i(TAG, "Adjusting GPS location interval to ${targetIntervalMs}ms for mode $mode")
            locationIntervalState.value = targetIntervalMs
        }
    }

    private fun updateSnapshotAndNotification(trip: ActiveTripState, distanceToTarget: Double? = null) {
        if (!serviceScope.isActive) return
        reevaluateLocationInterval()

        val progressInfo = ActiveTripProgressTracker.progressState.value
        val appLanguage = tripNotificationManager.getAppLanguage()
        val snapshot = ActiveTripSnapshotBuilder.build(
            activeTrip = trip,
            progressInfo = progressInfo,
            realTimeStatus = latestRealTimeStatus,
            appLanguage = appLanguage
        )
        UnifiedActiveTripStateTracker.updateSnapshot(snapshot)

        tripNotificationManager.updateNotificationWithSnapshot(
            snapshot = snapshot,
            distanceToTarget = distanceToTarget
        )
    }

    private fun formatVehicleNameForNotification(name: String, mode: TransitMode): String {
        val clean = name.trim()
        return when (mode) {
            TransitMode.SUBWAY -> {
                if (clean.startsWith("Metro", ignoreCase = true)) clean
                else if (clean.startsWith("L", ignoreCase = true) || clean.all { it.isDigit() }) "Metro $clean"
                else "Metro $clean"
            }
            TransitMode.BUS -> {
                if (clean.startsWith("Bus", ignoreCase = true) || clean.startsWith("EMT", ignoreCase = true)) clean
                else "Autobús $clean"
            }
            TransitMode.TRAM -> {
                if (clean.startsWith("Tranv", ignoreCase = true) || clean.startsWith("Tram", ignoreCase = true)) clean
                else "Tranvía $clean"
            }
            TransitMode.RAIL -> {
                if (clean.startsWith("Cercan", ignoreCase = true) || clean.startsWith("Tren", ignoreCase = true) || clean.startsWith("Rodalia", ignoreCase = true)) clean
                else "Tren $clean"
            }
            else -> clean.ifBlank { "Transporte" }
        }
    }

    private fun stopForegroundTracking() {
        geofenceGpsController.logGpsMetricsSummary()
        lastBoardedLegIndex.set(-1)
        sensorFusionEngine.reset()
        geofenceGpsController.clearGeofences()
        UnifiedActiveTripStateTracker.reset()
        try {
            trackingJob?.cancel()
            trackingJob = null
            serviceScope.coroutineContext.cancelChildren()
            stopForeground(STOP_FOREGROUND_REMOVE)
            tripNotificationManager.cancelAllNotifications()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping foreground tracking: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopForegroundTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.service.action.START_TRACKING"
        const val ACTION_STOP = "com.example.service.action.STOP_TRACKING"
        const val ACTION_SHOW_RECALCULATE_DIALOG = "com.example.service.action.SHOW_RECALCULATE_DIALOG"
        const val ACTION_MANUAL_BOARDING = "com.example.service.action.MANUAL_BOARDING"
        const val ACTION_REJECT_BOARDING = "com.example.service.action.REJECT_BOARDING"
        const val ACTION_FORCE_RECONCILE = "com.example.service.action.FORCE_RECONCILE"
        const val ACTION_GEOFENCE_TRANSITION = "com.example.service.action.GEOFENCE_TRANSITION"
        const val EXTRA_LEG_INDEX = "extra_leg_index"
        const val EXTRA_GEOFENCE_REQUEST_ID = "extra_geofence_request_id"
        const val EXTRA_SIMULATED_TRANSITION = "extra_simulated_transition"
        private const val TAG = "ActiveTripTracking"
        private const val BOARDING_CONFIDENCE_THRESHOLD = 0.75f

        fun start(context: Context) {
            if (!LocationUtils.hasLocationPermission(context)) {
                android.util.Log.w(TAG, "Cannot start ActiveTripTrackingService: Location permission not granted.")
                return
            }
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start ForegroundService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error stopping ActiveTripTrackingService: ${e.message}")
            }
        }

        fun confirmManualBoarding(context: Context, legIndex: Int) {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_MANUAL_BOARDING
                putExtra(EXTRA_LEG_INDEX, legIndex)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error sending manual boarding intent: ${e.message}")
            }
        }

        fun triggerReconciliation(context: Context) {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_FORCE_RECONCILE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error sending force reconcile intent: ${e.message}")
            }
        }
    }
}
