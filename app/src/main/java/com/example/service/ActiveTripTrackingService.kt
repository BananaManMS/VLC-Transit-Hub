package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CombinedVibration
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripRepository
import com.example.data.repository.ActiveTripState
import com.example.util.ActiveTripProgressTracker
import com.example.util.BoardingSensorFusionEngine
import com.example.util.LocationUtils
import com.example.util.StepProgressionResult
import com.example.util.TripStepProgressionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Foreground Service for active multimodal trip GPS tracking and automatic step progression.
 * Delivers silent, rich Live Notifications featuring a graphical segmented progress bar,
 * live telemetry updates, ETA metrics, and one-tap trip cancellation.
 */
class ActiveTripTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    private lateinit var activeTripRepository: ActiveTripRepository
    private lateinit var geofencingClient: GeofencingClient
    private val tripReconciler = com.example.util.TripRealTimeReconciler()
    private val sensorFusionEngine = BoardingSensorFusionEngine()
    private var currentActiveTrip: ActiveTripState? = null
    private var latestLocation: android.location.Location? = null
    private var latestRealTimeStatus: com.example.util.RealTimeTripStatus? = null

    // Dynamic location interval state (default 12000ms for high energy efficiency)
    private val locationIntervalState = MutableStateFlow(12000L)

    // Geofencing & GPS Gate State
    private val isGeofenceGateOpenState = MutableStateFlow(true)
    private var lastManagedLegIndex = -1

    // GPS Energy Metrics tracking
    private var highAccuracyStartTimeMs: Long = 0L
    private var totalHighAccuracyActiveMs: Long = 0L
    private var tripStartTimeMs: Long = 0L

    // Notification throttling cache
    private var lastNotificationTimeMs = 0L
    private var lastPostedHeadline: String? = null
    private var lastPostedSubheadline: String? = null
    private var lastPostedEtaText: String? = null
    private var lastPostedLegIndex: Int = -1

    // Idempotencia estricta por tramo para el evento de embarque (BOARDED)
    private val lastBoardedLegIndex = AtomicInteger(-1)
    private var lastAlertedTransferLegIndex: Int = -1

    override fun onCreate() {
        super.onCreate()
        geofencingClient = LocationServices.getGeofencingClient(this)
        val database = AppDatabase.getDatabase(applicationContext)
        activeTripRepository = ActiveTripRepository(database.activeTripDao())
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lastBoardedLegIndex.set(-1)
                sensorFusionEngine.reset()
                lastManagedLegIndex = -1
                tripStartTimeMs = System.currentTimeMillis()
                totalHighAccuracyActiveMs = 0L
                highAccuracyStartTimeMs = System.currentTimeMillis()
                startForegroundTracking()
            }
            ACTION_STOP -> {
                logGpsMetricsSummary()
                lastBoardedLegIndex.set(-1)
                sensorFusionEngine.reset()
                clearGeofences()
                serviceScope.launch {
                    activeTripRepository.cancelActiveTrip()
                }
                stopForegroundTracking()
                stopSelf()
            }
            ACTION_GEOFENCE_TRANSITION -> {
                handleGeofenceTransition(intent)
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        if (!LocationUtils.hasLocationPermission(applicationContext)) {
            android.util.Log.w("ActiveTripTracking", "Location permission not granted. Cannot start Foreground Service.")
            stopSelf()
            return
        }

        val initialNotification = buildInitialFallbackNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            android.util.Log.e("ActiveTripTracking", "Failed to startForeground: ${e.message}", e)
            stopSelf()
            return
        }

        // Cancel previous job if running
        trackingJob?.cancel()

        trackingJob = serviceScope.launch {
            // Keep active trip state in sync and evaluate leg geofencing
            launch {
                activeTripRepository.getActiveTripFlow().collectLatest { trip ->
                    currentActiveTrip = trip
                    if (trip == null) {
                        stopForegroundTracking()
                        stopSelf()
                    } else {
                        checkAndSyncGeofenceForLeg(trip)
                        updateNotificationForTrip(trip)
                    }
                }
            }

            // 1-minute live reconciliation background loop
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

                        val syncedItinerary = com.example.util.TripRealTimeReconciler.syncRealTimeItinerary(
                            itinerary = trip.itinerary,
                            status = status,
                            currentLegIndex = trip.currentLegIndex
                        )
                        if (syncedItinerary != trip.itinerary) {
                            activeTripRepository.updateItinerary(syncedItinerary)
                        }

                        updateNotificationForTrip(trip)
                    }
                    kotlinx.coroutines.delay(60000L)
                }
            }

            // Sensor Fusion: Confidence collector for BOARDED transition orchestration
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
                        val wasAlreadyBoarded = !lastBoardedLegIndex.compareAndSet(
                            currentLegIndex - 1,
                            currentLegIndex
                        ) && lastBoardedLegIndex.get() == currentLegIndex

                        if (wasAlreadyBoarded) {
                            return@collect
                        }

                        android.util.Log.i(
                            TAG,
                            "Boarding confirmed by Sensor Fusion (confidence: $highConfidence) on leg #$currentLegIndex (${currentLeg.mode})"
                        )

                        // Parallel Atomic Dispatch: UX / Reconciler / Spatial Engine
                        dispatchBoardingActionsConcurrently(currentLeg, currentLegIndex)
                    }
            }

            // 4-second ticker for dead-reckoning progress estimation when underground or GPS fix is weak
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
                            updateNotificationForTrip(trip)
                        }
                    }
                    kotlinx.coroutines.delay(4000L)
                }
            }

            // Stream continuous GPS updates gated by target geofence (GPS sleeps while traveling between stations)
            @OptIn(ExperimentalCoroutinesApi::class)
            val gatedLocationFlow = isGeofenceGateOpenState.flatMapLatest { isGateOpen ->
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
                            activeTripRepository.completeActiveTrip()
                            updateNotificationSimple("¡Llegada a destino!", "Has completado tu viaje con éxito.")
                            stopForegroundTracking()
                            stopSelf()
                        } else {
                            activeTripRepository.advanceLegIndex(result.nextLegIndex)
                        }
                    }
                    is StepProgressionResult.OnTrack -> {
                        updateNotificationForTrip(trip, result.distanceToNextTargetMeters)
                    }
                    is StepProgressionResult.NoOp -> {
                        // Trip has no legs or is empty
                    }
                }
            }
        }
    }

    /**
     * Executes the 3 atomic actions concurrently upon boarding confirmation:
     * 1. Immediate UX (Vibration + StateFlow emission for Jetpack Compose)
     * 2. Network & Corridor Drift tracking (Background IO)
     * 3. Spatial engine update & tunnel dead-reckoning activation (Background Default)
     */
    private fun dispatchBoardingActionsConcurrently(leg: PlannedLeg, legIndex: Int) {
        // 1. UX Inmediata
        serviceScope.launch(Dispatchers.Main.immediate) {
            triggerBoardingHapticFeedback()
            ActiveTripProgressTracker.markAsBoarded(legIndex)
            currentActiveTrip?.let { updateNotificationForTrip(it) }
        }

        // 2. Red y Deriva (TripRealTimeReconciler onBoardingConfirmed)
        serviceScope.launch(Dispatchers.IO) {
            try {
                tripReconciler.onBoardingConfirmed(leg = leg, legIndex = legIndex)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error synchronizing Reconciler on boarding: ${e.message}", e)
            }
        }

        // 3. Motor Espacial (TripStepProgressionEngine dead-reckoning)
        serviceScope.launch(Dispatchers.Default) {
            try {
                TripStepProgressionEngine.notifyBoardingConfirmed(
                    legIndex = legIndex,
                    targetLeg = leg,
                    enableTunnelDeadReckoning = true
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error notifying Spatial Engine: ${e.message}", e)
            }
        }
    }

    /**
     * Triggers distinctive boarding haptic feedback using Android Vibrator / VibratorManager.
     */
    private fun triggerBoardingHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val combinedEffect = CombinedVibration.createParallel(
                    VibrationEffect.createWaveform(
                        BOARDING_VIBRATION_TIMINGS,
                        BOARDING_VIBRATION_AMPLITUDES,
                        -1
                    )
                )
                vibratorManager?.vibrate(combinedEffect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(
                        BOARDING_VIBRATION_TIMINGS,
                        BOARDING_VIBRATION_AMPLITUDES,
                        -1
                    )
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(BOARDING_VIBRATION_TIMINGS, -1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not emit boarding haptic feedback: ${e.message}")
        }
    }

    private fun reevaluateLocationInterval() {
        val trip = currentActiveTrip ?: return
        val legs = trip.itinerary.legs
        val currentLegIndex = trip.currentLegIndex
        val currentLeg = legs.getOrNull(currentLegIndex) ?: return
        val progressInfo = com.example.util.ActiveTripProgressTracker.progressState.value
        val realTime = latestRealTimeStatus

        val remainingLegSeconds = (currentLeg.durationSeconds * (1.0f - progressInfo.progressWithinLeg)).toLong()
        val realTimeRemainingSeconds = realTime?.vehicleSecondsRemaining?.toLong()
        val effectiveRemainingSeconds = realTimeRemainingSeconds ?: remainingLegSeconds
        val TTFF_GUARANTEE_WINDOW_SECONDS = 30L

        if (effectiveRemainingSeconds <= TTFF_GUARANTEE_WINDOW_SECONDS && !isGeofenceGateOpenState.value) {
            openHighAccuracyGate("TTFF Guarantee Dynamic Fail-safe: Remaining ETA <= ${TTFF_GUARANTEE_WINDOW_SECONDS}s")
        }

        val targetIntervalMs = when (currentLeg.mode) {
            TransitMode.SUBWAY -> {
                val isNearEndOrTransfer = progressInfo.progressWithinLeg >= 0.80f ||
                        (realTime != null && ((realTime.vehicleArrivalMinutes ?: 99) <= 1 || (realTime.vehicleSecondsRemaining ?: 999) <= 60)) ||
                        (currentLegIndex == legs.size - 1 && progressInfo.progressWithinLeg >= 0.70f)

                if (isNearEndOrTransfer) {
                    5000L // 4-5s interval near station/transfer to catch GPS fix during cut&cover platform stop
                } else {
                    12000L // 10-15s (12s) interval in subway tunnel relying on cell towers and dead-reckoning
                }
            }
            TransitMode.WALK, TransitMode.BICYCLE -> 6000L // 5-7s (6s) interval for walking/cycling
            TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL -> {
                val isNearTransferOrDest = (realTime != null && (realTime.vehicleArrivalMinutes ?: 99) <= 1) ||
                        progressInfo.progressWithinLeg >= 0.85f
                if (isNearTransferOrDest) 5000L else 12000L // 10-15s (12s) on surface, 5s near destination/transfer
            }
            else -> 12000L
        }

        if (locationIntervalState.value != targetIntervalMs) {
            android.util.Log.i(TAG, "Adjusting GPS location interval to ${targetIntervalMs}ms for mode ${currentLeg.mode}")
            locationIntervalState.value = targetIntervalMs
        }
    }

    private fun updateNotificationForTrip(trip: ActiveTripState, distanceToTarget: Double? = null) {
        reevaluateLocationInterval()

        val legs = trip.itinerary.legs
        val currentLegIndex = trip.currentLegIndex.coerceIn(0, (legs.size - 1).coerceAtLeast(0))
        val currentLeg = legs.getOrNull(currentLegIndex)
        val realTime = latestRealTimeStatus
        val progressInfo = com.example.util.ActiveTripProgressTracker.progressState.value

        val isSalYa = realTime?.isLeaveNowAlert == true
        val isLive = realTime?.isLive == true
        val isTransferAtRisk = realTime?.isTransferAtRisk == true
        val currentAppLanguage = getAppLanguage()

        // 1. Calculate Primary Instruction Title, Subtitle, and Adjusted ETA using single source of truth
        val formattedUI = com.example.util.TripUIStateFormatter.format(
            currentLeg = currentLeg,
            currentLegIndex = currentLegIndex,
            totalLegs = legs.size,
            realTimeStatus = realTime,
            isBoarded = progressInfo.isBoarded,
            scheduledArrivalTime = trip.itinerary.formattedArrivalTime,
            appLanguage = currentAppLanguage,
            distanceToTargetMeters = distanceToTarget,
            allLegs = legs
        )

        val titleText = formattedUI.headline
        val subtitleText = formattedUI.subheadline
        val etaText = formattedUI.formattedArrivalTimeText

        val now = System.currentTimeMillis()
        val contentChanged = titleText != lastPostedHeadline ||
                subtitleText != lastPostedSubheadline ||
                etaText != lastPostedEtaText ||
                currentLegIndex != lastPostedLegIndex

        val timeElapsed = now - lastNotificationTimeMs

        if (!contentChanged && timeElapsed < 10000L) {
            return
        }

        lastNotificationTimeMs = now
        lastPostedHeadline = titleText
        lastPostedSubheadline = subtitleText
        lastPostedEtaText = etaText
        lastPostedLegIndex = currentLegIndex

        // 2. Pending Intents
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isTransferAtRisk) {
                action = ACTION_SHOW_RECALCULATE_DIALOG
                putExtra("SHOW_TRANSFER_DIALOG", true)
            }
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recalculateIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SHOW_RECALCULATE_DIALOG
            putExtra("SHOW_TRANSFER_DIALOG", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val recalculatePendingIntent = PendingIntent.getActivity(
            this,
            2,
            recalculateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, ActiveTripTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (isTransferAtRisk && lastAlertedTransferLegIndex != currentLegIndex) {
            lastAlertedTransferLegIndex = currentLegIndex
            val alertTitle = if (currentAppLanguage == com.example.ui.dashboard.AppLanguage.ES) "Posible transbordo perdido" else "Possible transbordament perdut"
            val alertBody = if (currentAppLanguage == com.example.ui.dashboard.AppLanguage.ES) {
                realTime?.transferWarningEs ?: "Se estima que no llegarás a tiempo al enlace. Toca para recalcular ruta sin caminar más."
            } else {
                realTime?.transferWarningCa ?: "S'estima que no arribaràs a temps a l'enllaç. Toca per a recalcular ruta sense caminar més."
            }
            val alertAction = if (currentAppLanguage == com.example.ui.dashboard.AppLanguage.ES) "Buscar alternativas" else "Cercar alternatives"

            val alertNotif = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(alertTitle)
                .setContentText(alertBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(recalculatePendingIntent)
                .addAction(
                    android.R.drawable.ic_popup_sync,
                    alertAction,
                    recalculatePendingIntent
                )
                .build()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ALERT_ID, alertNotif)
        }

        // 3. Generate Progress Bar & Mode Icon Bitmaps
        val currentMode = currentLeg?.mode ?: TransitMode.WALK
        val modeIconBitmap = NotificationProgressBitmapGenerator.generateModeIconBitmap(applicationContext, currentMode, isSalYa)
        val progressBarBitmap = NotificationProgressBitmapGenerator.generateProgressBarBitmap(
            context = applicationContext,
            legs = legs,
            currentLegIndex = currentLegIndex,
            progressFractionInLeg = progressInfo.progressWithinLeg
        )

        // 4. Populate Expanded RemoteViews (Idéntica a la tarjeta con barra segmentada)
        val expandedView = RemoteViews(packageName, R.layout.notification_active_trip).apply {
            setImageViewBitmap(R.id.notif_mode_icon, modeIconBitmap)
            setTextViewText(R.id.notif_title, titleText)
            setTextViewText(R.id.notif_subtitle, subtitleText)

            if (!formattedUI.nextTransitDepartureInfo.isNullOrBlank()) {
                setTextViewText(R.id.notif_extra_info, formattedUI.nextTransitDepartureInfo)
                setViewVisibility(R.id.notif_extra_info, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.notif_extra_info, android.view.View.GONE)
            }

            setOnClickPendingIntent(R.id.notif_btn_cancel, cancelPendingIntent)

            setImageViewBitmap(R.id.notif_progress_bar_image, progressBarBitmap)

            setTextViewText(R.id.notif_eta_text, formattedUI.formattedArrivalTimeText)

            setTextViewText(R.id.notif_duration_text, trip.itinerary.formattedDuration)

            if (isLive) {
                setViewVisibility(R.id.notif_live_badge, android.view.View.VISIBLE)
                setTextViewText(R.id.notif_live_badge, if (currentAppLanguage == com.example.ui.dashboard.AppLanguage.ES) "● En directo" else "● En directe")
            } else {
                setViewVisibility(R.id.notif_live_badge, android.view.View.GONE)
            }
        }

        // 5. Populate Compact RemoteViews (Para estado colapsado)
        val compactView = RemoteViews(packageName, R.layout.notification_active_trip_compact).apply {
            setImageViewBitmap(R.id.notif_compact_icon, modeIconBitmap)
            setTextViewText(R.id.notif_compact_title, titleText)
            setTextViewText(R.id.notif_compact_subtitle, subtitleText)
            setTextViewText(R.id.notif_compact_eta, trip.itinerary.formattedDuration)
            setOnClickPendingIntent(R.id.notif_compact_cancel, cancelPendingIntent)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Silent live updates without audio/vibration spam!
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(openAppPendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationSimple(title: String, content: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun getAppLanguage(): com.example.ui.dashboard.AppLanguage {
        val prefs = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val langStr = prefs.getString("app_language", null) ?: "CA"
        return try {
            com.example.ui.dashboard.AppLanguage.valueOf(langStr)
        } catch (_: Exception) {
            com.example.ui.dashboard.AppLanguage.CA
        }
    }

    private fun buildInitialFallbackNotification(): Notification {
        val currentAppLanguage = getAppLanguage()
        val title = if (currentAppLanguage == com.example.ui.dashboard.AppLanguage.ES) "Viaje en curso" else "Viatge en curs"
        val desc = if (currentAppLanguage == com.example.ui.dashboard.AppLanguage.ES) "Siguiendo tu trayecto en tiempo real..." else "Seguint el teu trajecte en temps real..."
        val cancelBtn = if (currentAppLanguage == com.example.ui.dashboard.AppLanguage.ES) "Finalizar" else "Finalitzar"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ActiveTripTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(desc)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_close_notification,
                cancelBtn,
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Seguimiento de Viaje Activo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación en vivo del viaje multimodal en curso"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Alertas de Transbordo en Riesgo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas cuando un transbordo está en riesgo de perderse"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun checkAndSyncGeofenceForLeg(trip: ActiveTripState) {
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

            val currentLoc = latestLocation
            var isAlreadyNear = false
            if (currentLoc != null && targetLat != 0.0 && targetLon != 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    currentLoc.latitude, currentLoc.longitude,
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

    private fun openHighAccuracyGate(reason: String) {
        if (!isGeofenceGateOpenState.value) {
            highAccuracyStartTimeMs = System.currentTimeMillis()
            isGeofenceGateOpenState.value = true
            android.util.Log.i(
                TAG,
                "🟢 [GPS_GATE_OPEN] HIGH_ACCURACY GPS Activated ($reason). Total active so far: ${totalHighAccuracyActiveMs / 1000}s"
            )
        }
    }

    private fun closeHighAccuracyGate(reason: String) {
        if (isGeofenceGateOpenState.value) {
            if (highAccuracyStartTimeMs > 0L) {
                val activeSegmentMs = System.currentTimeMillis() - highAccuracyStartTimeMs
                totalHighAccuracyActiveMs += activeSegmentMs
                highAccuracyStartTimeMs = 0L
            }
            isGeofenceGateOpenState.value = false
            android.util.Log.i(
                TAG,
                "🔴 [GPS_GATE_CLOSED] HIGH_ACCURACY GPS Suspended ($reason). Receptor GNSS sleeping. Total active so far: ${totalHighAccuracyActiveMs / 1000}s"
            )
        }
    }

    private fun handleGeofenceTransition(intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent != null && geofencingEvent.hasError()) {
            android.util.Log.e(TAG, "GeofencingEvent error code: ${geofencingEvent.errorCode}")
            openHighAccuracyGate("Geofence error fallback")
            return
        }

        val transitionType = geofencingEvent?.geofenceTransition ?: -1
        val isSimulated = intent.getBooleanExtra(EXTRA_SIMULATED_TRANSITION, false)

        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
            transitionType == Geofence.GEOFENCE_TRANSITION_DWELL ||
            isSimulated
        ) {
            val requestId = intent.getStringExtra(EXTRA_GEOFENCE_REQUEST_ID) ?: "unknown"
            android.util.Log.i(
                TAG,
                "⚡ [GEOFENCE_TRANSITION_ENTER] Entered 350m target geofence (requestId: $requestId)! Switching to PRIORITY_HIGH_ACCURACY GPS (5s interval)."
            )
            openHighAccuracyGate("GEOFENCE_TRANSITION_ENTER for $requestId")
        }
    }

    private fun clearGeofences() {
        try {
            val intent = Intent(this, ActiveTripTrackingService::class.java).apply {
                action = ACTION_GEOFENCE_TRANSITION
            }
            val pendingIntent = PendingIntent.getService(
                this,
                GEOFENCE_PENDING_INTENT_REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            geofencingClient.removeGeofences(pendingIntent)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error clearing geofences: ${e.message}")
        }
    }

    @Suppress("MissingPermission")
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

            val intent = Intent(this, ActiveTripTrackingService::class.java).apply {
                action = ACTION_GEOFENCE_TRANSITION
                putExtra(EXTRA_GEOFENCE_REQUEST_ID, requestId)
            }

            val pendingIntent = PendingIntent.getService(
                this,
                GEOFENCE_PENDING_INTENT_REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            if (LocationUtils.hasLocationPermission(applicationContext)) {
                geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                    .addOnSuccessListener {
                        android.util.Log.i(
                            TAG,
                            "🎯 [GEOFENCE_REGISTERED] 350m Geofence registered around milestone ($targetLat, $targetLon) for $requestId"
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

    private fun logGpsMetricsSummary() {
        val now = System.currentTimeMillis()
        var activeMs = totalHighAccuracyActiveMs
        if (isGeofenceGateOpenState.value && highAccuracyStartTimeMs > 0L) {
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

    private fun stopForegroundTracking() {
        logGpsMetricsSummary()
        clearGeofences()
        trackingJob?.cancel()
        trackingJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val NOTIFICATION_ALERT_ID = 4002
        private const val CHANNEL_ID = "active_trip_tracking_channel"
        private const val CHANNEL_ALERT_ID = "active_trip_alert_channel"
        const val ACTION_START = "com.example.service.action.START_TRACKING"
        const val ACTION_STOP = "com.example.service.action.STOP_TRACKING"
        const val ACTION_SHOW_RECALCULATE_DIALOG = "com.example.service.action.SHOW_RECALCULATE_DIALOG"
        const val ACTION_GEOFENCE_TRANSITION = "com.example.service.action.GEOFENCE_TRANSITION"
        const val EXTRA_GEOFENCE_REQUEST_ID = "extra_geofence_request_id"
        const val EXTRA_SIMULATED_TRANSITION = "extra_simulated_transition"
        private const val GEOFENCE_PENDING_INTENT_REQ_CODE = 2001
        private const val TAG = "ActiveTripTracking"
        private const val BOARDING_CONFIDENCE_THRESHOLD = 0.75f

        // Patrón háptico distintivo de embarque: [espera, pulso 1, pausa, pulso 2]
        private val BOARDING_VIBRATION_TIMINGS = longArrayOf(0L, 80L, 100L, 160L)
        private val BOARDING_VIBRATION_AMPLITUDES = intArrayOf(0, 180, 0, 255)

        fun start(context: Context) {
            if (!LocationUtils.hasLocationPermission(context)) {
                android.util.Log.w("ActiveTripTracking", "Cannot start ActiveTripTrackingService: Location permission not granted.")
                return
            }
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.e("ActiveTripTracking", "Failed to start ForegroundService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
