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
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.quicktrack.QuickTrackedVehicle
import com.example.data.repository.RealTimeTransitRepository
import com.example.util.TripSensoryAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lightweight Foreground Service for tracking a single pinned Metro train.
 * Supports silent, persistent Live Notifications and Android 16+ Rich Ongoing Notification
 * status bar chips (Dynamic Island style), with automatic debark alarms.
 *
 * Implements intelligent adaptive polling:
 * - > 20 min remaining: Pure local countdown (no network polling)
 * - 5 to 20 min remaining: 2-minute polling interval
 * - <= 5 min remaining: 40-second polling interval
 * - Penultimate stop monitoring: Checks live API at penultimate station to trigger debark alert when train is at station.
 * - Scheduled fallback: Triggers debark alert when <= 2 min remaining to destination.
 */
class QuickVehicleTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.example.quicktrack.START"
        const val ACTION_STOP = "com.example.quicktrack.STOP"
        const val ACTION_DISMISS = "com.example.quicktrack.DISMISS"

        const val EXTRA_VEHICLE = "extra_quick_tracked_vehicle"

        const val NOTIFICATION_ID = 20042
        const val ALERT_NOTIFICATION_ID = 20043

        const val CHANNEL_ONGOING = "quick_vehicle_live_ongoing_v4"
        const val CHANNEL_ALERT = "quick_vehicle_alert_channel"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null
    private var apiSyncJob: Job? = null

    private lateinit var notificationManager: NotificationManager
    private var currentVehicle: QuickTrackedVehicle? = null

    private var hasAlertedDebark = false
    private var isSelfTerminating = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val vehicleFromIntent = try {
                    intent?.setExtrasClassLoader(QuickTrackedVehicle::class.java.classLoader)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent?.getSerializableExtra(EXTRA_VEHICLE, QuickTrackedVehicle::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent?.getSerializableExtra(EXTRA_VEHICLE) as? QuickTrackedVehicle
                    }
                } catch (e: Exception) {
                    android.util.Log.w("QuickTrackService", "Intent deserialization error: ${e.message}")
                    null
                }

                val vehicle = vehicleFromIntent ?: QuickVehicleTrackerManager.activeTrackedVehicle.value

                if (vehicle != null) {
                    currentVehicle = vehicle
                    hasAlertedDebark = vehicle.hasAlertedDebark
                    isSelfTerminating = false
                    startForegroundTracking(vehicle)
                } else {
                    stopSelf()
                }
            }
            ACTION_DISMISS, ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.quick_track_notif_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.quick_track_notif_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(ongoingChannel)

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT,
                getString(R.string.quick_track_alert_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.quick_track_alert_channel_desc)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun startForegroundTracking(vehicle: QuickTrackedVehicle) {
        val initialNotif = buildOngoingNotification(vehicle)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotif)
            }
        } catch (e: Throwable) {
            android.util.Log.e("QuickTrackService", "startForeground primary error: ${e.message}", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotif)
                }
            } catch (e2: Throwable) {
                android.util.Log.e("QuickTrackService", "startForeground secondary error: ${e2.message}", e2)
            }
        }

        // Direct notification post ensuring display across all OEM launchers
        try {
            notificationManager.notify(NOTIFICATION_ID, initialNotif)
        } catch (e: Exception) {
            android.util.Log.w("QuickTrackService", "Direct notify error: ${e.message}")
        }

        startTickerLoop()
        startApiSyncLoop()
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && !isSelfTerminating) {
                val vehicle = currentVehicle ?: break
                val nowMs = System.currentTimeMillis()
                val originRemainingSec = vehicle.liveSecondsRemaining(nowMs)

                if (!vehicle.isDestinationAlert) {
                    // Only tracking departure at origin
                    if (originRemainingSec < -30) {
                        // Train has departed and 30s grace elapsed
                        showDepartureFinishedNotification(vehicle)
                        break
                    } else {
                        updateNotification(vehicle)
                    }
                } else {
                    // Tracking journey to destination
                    val targetRemainingMin = vehicle.minutesRemainingToTarget(nowMs)

                    // Check transition to boarded
                    val isBoardedNow = originRemainingSec <= 0 || vehicle.isBoarded
                    if (isBoardedNow && !vehicle.isBoarded) {
                        currentVehicle = vehicle.copy(isBoarded = true)
                        QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                    }

                    // Penultimate station arrival check:
                    // Condition 1: Si es programado, se avisa cuando llegue la hora programada de la penúltima parada antes del destino
                    if (!vehicle.isRealTime && !hasAlertedDebark && isBoardedNow) {
                        val penRemainingSec = vehicle.liveSecondsRemainingToPenultimate(nowMs)
                        if (penRemainingSec <= 0) {
                            hasAlertedDebark = true
                            currentVehicle = currentVehicle?.copy(hasAlertedDebark = true)
                            QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                            triggerDebarkAlert(currentVehicle ?: vehicle)
                        }
                    } else if (vehicle.isRealTime && !hasAlertedDebark && isBoardedNow) {
                        // Para metros en vivo: si se filtró la desaparición por quedar > 5 min o la API falló,
                        // avisar automáticamente cuando queden menos de 2 minutos para bajar (< 2 min / <= 120s)
                        val targetRemainingSec = vehicle.liveSecondsRemainingToTarget(nowMs)
                        val targetRemainingMin = vehicle.minutesRemainingToTarget(nowMs)
                        if (targetRemainingSec <= 120 || targetRemainingMin < 2) {
                            hasAlertedDebark = true
                            currentVehicle = currentVehicle?.copy(hasAlertedDebark = true)
                            QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                            triggerDebarkAlert(currentVehicle ?: vehicle)
                        }
                    }

                    if (targetRemainingMin <= 0 && isBoardedNow && originRemainingSec < -60) {
                        // Arrived at destination
                        showArrivalFinishedNotification(vehicle)
                        break
                    } else {
                        updateNotification(currentVehicle ?: vehicle)
                    }
                }

                delay(1000L)
            }
        }
    }

    /**
     * Adaptive Polling Loop:
     * - Before boarding (tracking origin):
     *   - If remaining > 20 min: No polling (delay 30s before re-checking remaining time)
     *   - If remaining 5 to 20 min: Poll origin every 2 min (120s)
     *   - If remaining <= 5 min: Poll origin every 40s
     * - After boarding (tracking destination):
     *   - Penultimate station monitoring:
     *     - If remaining to penultimate > 5 min: Poll penultimate station every 2 min (120s)
     *     - If remaining to penultimate <= 5 min: Poll penultimate station every 40s
     *     - When train arrives at penultimate station: Trigger debark alert!
     */
    private fun startApiSyncLoop() {
        apiSyncJob?.cancel()
        apiSyncJob = serviceScope.launch {
            while (isActive && !isSelfTerminating) {
                val vehicle = currentVehicle ?: break
                val nowMs = System.currentTimeMillis()
                val originRemainingSec = vehicle.liveSecondsRemaining(nowMs)
                val isBoardedNow = originRemainingSec <= 0 || vehicle.isBoarded

                if (!isBoardedNow) {
                    // Phase 1: Waiting at Origin Station
                    val originMins = vehicle.liveMinutesRemaining(nowMs)

                    if (originMins > 20) {
                        // > 20 min: Pure local countdown, no API query. Check again in 30 seconds.
                        delay(30_000L)
                    } else {
                        // Determine poll interval: 2 min if > 5 min, 40s if <= 5 min
                        val pollIntervalMs = if (originMins > 5) 120_000L else 40_000L

                        try {
                            val numericStationId = vehicle.originStationId.toIntOrNull()
                            if (numericStationId != null) {
                                val arrivals = RealTimeTransitRepository.getMetroLiveArrivals(
                                    stationId = numericStationId.toString(),
                                    forceRefresh = true
                                )
                                val cleanLine = vehicle.cleanLineNumber
                                val matchingArrivals = arrivals.filter { arr ->
                                    val arrLine = arr.line.replace("L", "", ignoreCase = true).trim()
                                    val isLineMatch = arrLine == cleanLine
                                    val isDestMatch = arr.destination.equals(vehicle.destination, ignoreCase = true) ||
                                            arr.destination.contains(vehicle.destination, ignoreCase = true) ||
                                            vehicle.destination.contains(arr.destination, ignoreCase = true)
                                    isLineMatch && isDestMatch
                                }

                                val targetVehicleId = vehicle.vehicleId
                                val targetServiceIdStr = vehicle.trainServiceId?.toString()
                                val targetSchedTime = vehicle.targetScheduledTime

                                val matched = when {
                                    !targetVehicleId.isNullOrBlank() -> {
                                        matchingArrivals.find { it.vehicleId == targetVehicleId }
                                            ?: matchingArrivals.find { targetServiceIdStr != null && it.vehicleId == targetServiceIdStr }
                                            ?: matchingArrivals.find { !targetSchedTime.isNullOrBlank() && it.estimatedTime == targetSchedTime }
                                            ?: matchingArrivals.minByOrNull { arr ->
                                                val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                                kotlin.math.abs(arrEpoch - vehicle.targetArrivalEpochMs)
                                            }
                                    }
                                    !targetServiceIdStr.isNullOrBlank() -> {
                                        matchingArrivals.find { it.vehicleId == targetServiceIdStr }
                                            ?: matchingArrivals.find { !targetSchedTime.isNullOrBlank() && it.estimatedTime == targetSchedTime }
                                            ?: matchingArrivals.minByOrNull { arr ->
                                                val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                                kotlin.math.abs(arrEpoch - vehicle.targetArrivalEpochMs)
                                            }
                                    }
                                    !targetSchedTime.isNullOrBlank() -> {
                                        matchingArrivals.find { it.estimatedTime == targetSchedTime }
                                            ?: matchingArrivals.minByOrNull { arr ->
                                                val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                                kotlin.math.abs(arrEpoch - vehicle.targetArrivalEpochMs)
                                            }
                                    }
                                    else -> {
                                        matchingArrivals.minByOrNull { arr ->
                                            val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                            kotlin.math.abs(arrEpoch - vehicle.targetArrivalEpochMs)
                                        }
                                    }
                                }

                                if (matched != null) {
                                    val arrEpoch = System.currentTimeMillis() + (matched.seconds * 1000L)
                                    val diffMs = kotlin.math.abs(arrEpoch - vehicle.targetArrivalEpochMs)
                                    val isExactIdMatch = (!targetVehicleId.isNullOrBlank() && matched.vehicleId == targetVehicleId) ||
                                            (!targetServiceIdStr.isNullOrBlank() && matched.vehicleId == targetServiceIdStr)
                                    val isExactTimeMatch = (!targetSchedTime.isNullOrBlank() && matched.estimatedTime == targetSchedTime)

                                    if (isExactIdMatch || isExactTimeMatch || diffMs <= 3 * 60_000L) {
                                        val newEpochMs = System.currentTimeMillis() + (matched.seconds * 1000L)
                                        currentVehicle = vehicle.copy(
                                            targetArrivalEpochMs = newEpochMs,
                                            isRealTime = true,
                                            initialMinutesRemaining = matched.minutes,
                                            vehicleId = matched.vehicleId ?: vehicle.vehicleId
                                        )
                                        QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                        updateNotification(currentVehicle!!)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("QuickTrackService", "Error syncing origin API: ${e.message}")
                        }

                        delay(pollIntervalMs)
                    }
                } else {
                    // Phase 2: Onboard towards Destination
                    if (!vehicle.isDestinationAlert || hasAlertedDebark) {
                        // No downstream destination or already alerted debark -> simple idle check
                        delay(30_000L)
                    } else {
                        val targetStationId = (vehicle.targetStationId ?: "").trim()
                        val targetNumericId = targetStationId.filter { it.isDigit() }
                        val isTerminus = isHeadsignEqualToTargetStation(vehicle)

                        val penStationId = (vehicle.penultimateStationId ?: vehicle.originStationId).trim()
                        val penNumericId = penStationId.filter { it.isDigit() }

                        if (!isTerminus && targetNumericId.isNotBlank()) {
                            // Estrategia A: Si headsign != destino (no es fin de línea),
                            // trackeamos las salidas de la parada de destino directamente para obtener
                            // el countdown más preciso posible en tiempo real.
                            val targetRemainingMins = vehicle.minutesRemainingToTarget(nowMs)
                            val pollIntervalMs = when {
                                targetRemainingMins > 5 -> 60_000L
                                targetRemainingMins in 3..5 -> 30_000L
                                else -> 15_000L
                            }

                            if (targetRemainingMins > 20) {
                                delay(30_000L)
                            } else {
                                try {
                                    val destArrivals = RealTimeTransitRepository.getMetroLiveArrivals(
                                        stationId = targetNumericId,
                                        forceRefresh = true
                                    )
                                    val cleanLine = vehicle.cleanLineNumber
                                    val destMatching = destArrivals.filter { arr ->
                                        val arrLine = arr.line.replace("L", "", ignoreCase = true).trim()
                                        val isLineMatch = arrLine == cleanLine
                                        val isDestMatch = arr.destination.equals(vehicle.destination, ignoreCase = true) ||
                                                arr.destination.contains(vehicle.destination, ignoreCase = true) ||
                                                vehicle.destination.contains(arr.destination, ignoreCase = true)
                                        isLineMatch && isDestMatch
                                    }

                                    val targetVehicleId = vehicle.vehicleId
                                    val targetServiceIdStr = vehicle.trainServiceId?.toString()
                                    val targetSchedTime = vehicle.targetScheduledTime

                                    val targetDeltaMins = vehicle.downstreamStops.find {
                                        it.stationId == targetStationId || it.stationName.equals(vehicle.targetStationName, ignoreCase = true)
                                    }?.deltaMinutesFromOrigin ?: 0
                                    val expectedTargetEpochMs = vehicle.targetArrivalEpochMs + (targetDeltaMins * 60_000L)

                                    val matchedDest = if (!targetVehicleId.isNullOrBlank()) {
                                        destMatching.find { it.vehicleId == targetVehicleId }
                                            ?: destMatching.find { targetServiceIdStr != null && it.vehicleId == targetServiceIdStr }
                                    } else {
                                        if (!targetSchedTime.isNullOrBlank()) {
                                            destMatching.find { it.estimatedTime == targetSchedTime }
                                        } else {
                                            destMatching.minByOrNull { arr ->
                                                val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                                kotlin.math.abs(arrEpoch - expectedTargetEpochMs)
                                            }?.takeIf { arr ->
                                                val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                                kotlin.math.abs(arrEpoch - expectedTargetEpochMs) <= 3 * 60_000L
                                            }
                                        }
                                    }

                                    if (matchedDest != null) {
                                        val newOriginEpochMs = System.currentTimeMillis() + (matchedDest.seconds * 1000L) - (targetDeltaMins * 60_000L)
                                        currentVehicle = (currentVehicle ?: vehicle).copy(
                                            targetArrivalEpochMs = newOriginEpochMs,
                                            isRealTime = true,
                                            vehicleId = matchedDest.vehicleId ?: vehicle.vehicleId
                                        )
                                        QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                        updateNotification(currentVehicle!!)

                                        // Alerta de bajada en la próxima parada cuando está en rango del destino (<= 2 min o inminente)
                                        val isArrivingAtDest = matchedDest.minutes <= 1 ||
                                                matchedDest.seconds <= 120 ||
                                                matchedDest.status?.let { s ->
                                                    val lower = s.lowercase()
                                                    lower.contains("lleg") || lower.contains("arrib") || lower.contains("inmin") || lower.contains("andén") || lower.contains("anden")
                                                } == true ||
                                                matchedDest.estimatedTime?.let { e ->
                                                    val lower = e.lowercase()
                                                    lower.contains("lleg") || lower.contains("arrib") || lower.contains("inmin")
                                                } == true

                                        if (isArrivingAtDest && !hasAlertedDebark && isBoardedNow) {
                                            hasAlertedDebark = true
                                            currentVehicle = currentVehicle?.copy(hasAlertedDebark = true)
                                            QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                            triggerDebarkAlert(currentVehicle ?: vehicle)
                                        }
                                    } else {
                                        // Si no aparece en salidas del destino:
                                        // Filtro: si faltan > 5 min no avisar (falso aviso / caída API).
                                        // Fallback: si quedan <= 2 min (< 120s), avisar automáticamente.
                                        val targetRemainingSec = vehicle.liveSecondsRemainingToTarget(nowMs)
                                        val targetRemainingMin = vehicle.minutesRemainingToTarget(nowMs)

                                        if (!hasAlertedDebark && isBoardedNow) {
                                            if (targetRemainingSec <= 120 || targetRemainingMin < 2) {
                                                hasAlertedDebark = true
                                                currentVehicle = currentVehicle?.copy(hasAlertedDebark = true)
                                                QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                                triggerDebarkAlert(currentVehicle ?: vehicle)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("QuickTrackService", "Error syncing destination API: ${e.message}")
                                }
                                delay(pollIntervalMs)
                            }
                        } else {
                            // Estrategia B: Si headsign == destino (fin de línea) o no hay ID numérico de destino:
                            // Usamos el trackeo de la penúltima parada como regla de referencia para terminales
                            val penultimateRemainingMins = vehicle.minutesRemainingToPenultimate(nowMs)

                            // Polling cadence to penultimate station: responsive when close
                            val pollIntervalMs = when {
                                penultimateRemainingMins > 5 -> 60_000L
                                penultimateRemainingMins in 3..5 -> 30_000L
                                else -> 20_000L
                            }

                            if (penultimateRemainingMins > 20) {
                                // > 20 min: No need to poll yet
                                delay(30_000L)
                            } else if (penNumericId.isNotBlank()) {
                                try {
                                    val penArrivals = RealTimeTransitRepository.getMetroLiveArrivals(
                                        stationId = penNumericId,
                                        forceRefresh = true
                                    )
                                    val cleanLine = vehicle.cleanLineNumber
                                    val penMatching = penArrivals.filter { arr ->
                                        val arrLine = arr.line.replace("L", "", ignoreCase = true).trim()
                                        val isLineMatch = arrLine == cleanLine
                                        val isDestMatch = arr.destination.equals(vehicle.destination, ignoreCase = true) ||
                                                arr.destination.contains(vehicle.destination, ignoreCase = true) ||
                                                vehicle.destination.contains(arr.destination, ignoreCase = true)
                                        isLineMatch && isDestMatch
                                    }

                                    val expectedPenEpochMs = vehicle.targetArrivalEpochMs +
                                        (vehicle.downstreamStops.find { it.stationId == penStationId || it.stationName.equals(vehicle.penultimateStationName, ignoreCase = true) }?.deltaMinutesFromOrigin ?: 0) * 60_000L

                                    val targetVehicleId = vehicle.vehicleId
                                    val targetServiceIdStr = vehicle.trainServiceId?.toString()
                                    val targetSchedTime = vehicle.targetScheduledTime

                                    val matchedPen = if (!targetVehicleId.isNullOrBlank()) {
                                        // Strictly match by vehicle ID or service ID (track specific train)
                                        penMatching.find { it.vehicleId == targetVehicleId }
                                            ?: penMatching.find { targetServiceIdStr != null && it.vehicleId == targetServiceIdStr }
                                    } else {
                                        // Vehicle ID not known yet: match by scheduled time or closest expected epoch
                                        if (!targetSchedTime.isNullOrBlank()) {
                                            penMatching.find { it.estimatedTime == targetSchedTime }
                                        } else {
                                            penMatching.minByOrNull { arr ->
                                                val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                                kotlin.math.abs(arrEpoch - expectedPenEpochMs)
                                            }?.takeIf { arr ->
                                                val arrEpoch = System.currentTimeMillis() + (arr.seconds * 1000L)
                                                kotlin.math.abs(arrEpoch - expectedPenEpochMs) <= 3 * 60_000L
                                            }
                                        }
                                    }

                                    if (matchedPen != null) {
                                        // Update live telemetry with live arrival at penultimate stop
                                        val penDeltaMins = vehicle.downstreamStops.find {
                                            it.stationId == penStationId || it.stationName.equals(vehicle.penultimateStationName, ignoreCase = true)
                                        }?.deltaMinutesFromOrigin ?: 0

                                        val newOriginEpochMs = System.currentTimeMillis() + (matchedPen.seconds * 1000L) - (penDeltaMins * 60_000L)
                                        currentVehicle = (currentVehicle ?: vehicle).copy(
                                            targetArrivalEpochMs = newOriginEpochMs,
                                            isRealTime = true,
                                            vehicleId = matchedPen.vehicleId ?: vehicle.vehicleId
                                        )
                                        QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                        updateNotification(currentVehicle!!)

                                        // Condition 2.A: Si es en vivo, cuando aparezca como llegando
                                        val isArrivingNow = matchedPen.minutes <= 0 ||
                                                matchedPen.seconds <= 60 ||
                                                matchedPen.status?.let { s ->
                                                    val lower = s.lowercase()
                                                    lower.contains("lleg") || lower.contains("arrib") || lower.contains("inmin") || lower.contains("andén") || lower.contains("anden")
                                                } == true ||
                                                matchedPen.estimatedTime?.let { e ->
                                                    val lower = e.lowercase()
                                                    lower.contains("lleg") || lower.contains("arrib") || lower.contains("inmin")
                                                } == true

                                        if (isArrivingNow && !hasAlertedDebark && isBoardedNow) {
                                            hasAlertedDebark = true
                                            currentVehicle = currentVehicle?.copy(hasAlertedDebark = true)
                                            QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                            triggerDebarkAlert(currentVehicle ?: vehicle)
                                        }
                                    } else {
                                        // Condition 2.B: Si es en vivo, cuando no aparezca ya ese metro en el panel de salidas en vivo
                                        // con filtro de si el vehículo desaparece y quedan más de 5 min no avisar porque sería un falso aviso
                                        val penRemainingSec = vehicle.liveSecondsRemainingToPenultimate(nowMs)
                                        val penRemainingMins = vehicle.minutesRemainingToPenultimate(nowMs)
                                        val targetRemainingSec = vehicle.liveSecondsRemainingToTarget(nowMs)
                                        val targetRemainingMin = vehicle.minutesRemainingToTarget(nowMs)

                                        if (!hasAlertedDebark && isBoardedNow) {
                                            if (penRemainingSec <= 300 && penRemainingMins <= 5) {
                                                hasAlertedDebark = true
                                                currentVehicle = currentVehicle?.copy(hasAlertedDebark = true)
                                                QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                                triggerDebarkAlert(currentVehicle ?: vehicle)
                                            } else if (targetRemainingSec <= 120 || targetRemainingMin < 2) {
                                                // Si desapareció con > 5 min pero ahora ya quedan < 2 min para bajar:
                                                // avisar automáticamente para no quedarse sin aviso
                                                hasAlertedDebark = true
                                                currentVehicle = currentVehicle?.copy(hasAlertedDebark = true)
                                                QuickVehicleTrackerManager.updateVehicle(currentVehicle)
                                                triggerDebarkAlert(currentVehicle ?: vehicle)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("QuickTrackService", "Error syncing penultimate API: ${e.message}")
                                }

                                delay(pollIntervalMs)
                            } else {
                                delay(30_000L)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(vehicle: QuickTrackedVehicle) {
        if (isSelfTerminating) return
        val notif = buildOngoingNotification(vehicle)
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    private fun buildOngoingNotification(vehicle: QuickTrackedVehicle): Notification {
        val nowMs = System.currentTimeMillis()
        val cleanLine = vehicle.cleanLineNumber

        // Micro-copy strictly designed for Android 16+ status bar chips (<7 chars)
        val chipText: String
        val titleText: String
        val bodyText: String

        val madridTz = TimeZone.getTimeZone("Europe/Madrid")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = madridTz
        }

        val originRemainingSec = (vehicle.targetArrivalEpochMs - nowMs) / 1000L
        val isDepartedFromOrigin = vehicle.isBoarded || originRemainingSec <= 0

        if (!vehicle.isDestinationAlert) {
            val mins = vehicle.liveMinutesRemaining(nowMs)
            chipText = "L$cleanLine: ${mins}m"
            titleText = "L$cleanLine ${vehicle.destination} · ${vehicle.originStationName}"
            val etaStr = timeFormat.format(Date(vehicle.targetArrivalEpochMs))
            bodyText = if (mins <= 0) {
                getString(R.string.quick_track_departing_now)
            } else {
                "Pasa en $mins min · Salida prevista: $etaStr"
            }
        } else {
            val destName = vehicle.targetStationName ?: vehicle.destination
            val targetEtaMs = vehicle.targetArrivalEpochMs + (vehicle.downstreamStops.find { it.stationName.equals(destName, ignoreCase = true) }?.deltaMinutesFromOrigin ?: 0) * 60_000L
            val destEtaStr = timeFormat.format(Date(targetEtaMs))

            if (!isDepartedFromOrigin) {
                // Before metro arrives / departs origin: Display time to origin station where user is waiting (e.g. L6: 1m / L6: 5m)
                val originMins = vehicle.liveMinutesRemaining(nowMs)
                chipText = "L$cleanLine: ${originMins}m"
                titleText = "L$cleanLine · En $originMins min en ${vehicle.originStationName}"
                val originEtaStr = timeFormat.format(Date(vehicle.targetArrivalEpochMs))
                bodyText = if (originMins <= 0) {
                    "Pasa ahora por ${vehicle.originStationName} · Destino: $destName ($destEtaStr)"
                } else {
                    "Pasa por ${vehicle.originStationName} a las $originEtaStr · Destino: $destName ($destEtaStr)"
                }
            } else {
                // After metro has arrived / departed origin: Display time to destination station (e.g. ➔ 1m / ➔ 20m)
                val destMins = vehicle.minutesRemainingToTarget(nowMs)
                val penRemainingSec = vehicle.liveSecondsRemainingToPenultimate(nowMs)
                val isPastPenultimate = penRemainingSec <= 10 || destMins <= 1

                if (isPastPenultimate) {
                    chipText = "➔ $destName"
                    titleText = getString(R.string.quick_track_approaching_dest_title, destName)
                    bodyText = getString(R.string.quick_track_approaching_dest_desc, cleanLine)
                } else {
                    chipText = "➔ ${destMins}m"
                    titleText = "L$cleanLine ➔ $destName"
                    bodyText = "Llegada a $destName en $destMins min · Hora: $destEtaStr"
                }
            }
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, QuickVehicleTrackingService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 16 (API 36) Native Rich Ongoing Notification with ProgressStyle and SystemUI chip
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val progressStyle = Notification.ProgressStyle()
                val progressPercent = if (!vehicle.isDestinationAlert) {
                    val remainingMins = vehicle.liveMinutesRemaining(nowMs)
                    if (vehicle.initialMinutesRemaining > 0) {
                        val elapsed = vehicle.initialMinutesRemaining - remainingMins
                        ((elapsed.toFloat() / vehicle.initialMinutesRemaining.toFloat()) * 100).toInt().coerceIn(0, 100)
                    } else 50
                } else {
                    if (!isDepartedFromOrigin) {
                        // Phase 1: Train approaching origin station (0% to 50%)
                        val remainingMins = vehicle.liveMinutesRemaining(nowMs)
                        if (vehicle.initialMinutesRemaining > 0) {
                            val elapsed = vehicle.initialMinutesRemaining - remainingMins
                            ((elapsed.toFloat() / vehicle.initialMinutesRemaining.toFloat()) * 50).toInt().coerceIn(0, 50)
                        } else 25
                    } else {
                        // Phase 2: Train en route to destination station (50% to 100%)
                        val destMins = vehicle.minutesRemainingToTarget(nowMs)
                        val totalTripMinutes = (vehicle.downstreamStops.find { it.stationName.equals(vehicle.targetStationName ?: vehicle.destination, ignoreCase = true) }?.deltaMinutesFromOrigin ?: 10)
                        val fraction = if (totalTripMinutes > 0) {
                            (1f - (destMins.toFloat() / totalTripMinutes.toFloat())).coerceIn(0f, 1f)
                        } else 0.5f
                        (50 + (fraction * 50)).toInt().coerceIn(50, 100)
                    }
                }
                progressStyle.setProgress(progressPercent)
                progressStyle.setStyledByProgress(true)

                val dismissAction = Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_train_logo),
                    getString(R.string.quick_track_action_dismiss),
                    dismissPendingIntent
                ).build()

                val nativeBuilder = Notification.Builder(this, CHANNEL_ONGOING)
                    .setSmallIcon(R.drawable.ic_stat_train_logo)
                    .setContentTitle(titleText)
                    .setContentText(bodyText)
                    .setSubText(chipText)
                    .setShortCriticalText(chipText)
                    .setStyle(progressStyle)
                    .setOngoing(true)
                    .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_NAVIGATION)
                    .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                    .setContentIntent(contentPendingIntent)
                    .addAction(dismissAction)

                val nativeNotif = nativeBuilder.build()
                nativeNotif.flags = nativeNotif.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_PROMOTED_ONGOING
                nativeNotif.extras.putBoolean("android.requestPromotedOngoing", true)
                nativeNotif.extras.putCharSequence("android.shortCriticalText", chipText)
                nativeNotif.extras.putString("android.shortCriticalText", chipText)
                return nativeNotif
            } catch (e: Throwable) {
                android.util.Log.e("QuickTrackService", "Error building Android 16 native ProgressStyle notification: ${e.message}", e)
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_train_logo)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setSubText(chipText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText).setSummaryText(chipText))
            .setOngoing(true)
            .setSortKey("z_quick_track")
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setNotificationSilent()
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_stat_train_logo,
                getString(R.string.quick_track_action_dismiss),
                dismissPendingIntent
            )

        // Android 16 Live Update / Promoted Ongoing Notification Extras (SystemUI Standard Keys)
        builder.extras.apply {
            putBoolean("android.requestPromotedOngoing", true)
            putBoolean("android.extra.REQUEST_PROMOTED_ONGOING", true)
            putCharSequence("android.shortCriticalText", chipText)
            putString("android.shortCriticalText", chipText)
            putCharSequence("android.extra.SHORT_CRITICAL_TEXT", chipText)
        }

        val notif = builder.build()
        val flagPromoted = 262144 // Notification.FLAG_PROMOTED_ONGOING in Android 16
        notif.flags = notif.flags or Notification.FLAG_ONGOING_EVENT or flagPromoted
        notif.extras.putBoolean("android.requestPromotedOngoing", true)
        notif.extras.putCharSequence("android.shortCriticalText", chipText)
        notif.extras.putString("android.shortCriticalText", chipText)

        return notif
    }

    private fun triggerDebarkAlert(vehicle: QuickTrackedVehicle) {
        val destName = vehicle.targetStationName ?: vehicle.destination
        val title = getString(R.string.quick_track_approaching_dest_title, destName)
        val desc = getString(R.string.quick_track_approaching_dest_desc, vehicle.cleanLineNumber)

        TripSensoryAlertManager.triggerLevel2AttentionCall(this, playAudio = false)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            2,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotif = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_train_logo)
            .setContentTitle(title)
            .setContentText(desc)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotif)
    }

    private fun showDepartureFinishedNotification(vehicle: QuickTrackedVehicle) {
        isSelfTerminating = true
        val text = getString(R.string.quick_track_train_departed, vehicle.originStationName)
        val notif = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_train_logo)
            .setContentTitle("L${vehicle.cleanLineNumber} ${vehicle.destination}")
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notif)

        serviceScope.launch {
            delay(15_000L)
            stopTracking()
        }
    }

    private fun showArrivalFinishedNotification(vehicle: QuickTrackedVehicle) {
        isSelfTerminating = true
        val destName = vehicle.targetStationName ?: vehicle.destination
        val text = getString(R.string.quick_track_arrived_dest, destName)
        val notif = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_train_logo)
            .setContentTitle("L${vehicle.cleanLineNumber} ${vehicle.destination}")
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notif)

        serviceScope.launch {
            delay(20_000L)
            stopTracking()
        }
    }

    private fun isHeadsignEqualToTargetStation(vehicle: QuickTrackedVehicle): Boolean {
        val headsign = vehicle.destination.trim()
        val targetName = (vehicle.targetStationName ?: "").trim()
        if (targetName.isBlank()) return true

        fun normalize(s: String): String = s.lowercase(Locale.ROOT)
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace("à", "a").replace("è", "e").replace("ò", "o")
            .replace("·", "")
            .replace("-", " ")
            .replace("/", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val normHead = normalize(headsign)
        val normTarget = normalize(targetName)

        if (normHead == normTarget) return true

        // Split composite terminus like "Alboraia Peris Aragó" or "Rafelbunyol / Alboraia"
        val parts = normHead.split(" / ", "/", "-").map { normalize(it) }
        if (parts.any { it == normTarget }) return true

        if (normHead.startsWith(normTarget) || normTarget.startsWith(normHead)) return true

        return false
    }

    private fun stopTracking() {
        tickerJob?.cancel()
        apiSyncJob?.cancel()
        currentVehicle = null
        QuickVehicleTrackerManager.updateVehicle(null)
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        apiSyncJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
