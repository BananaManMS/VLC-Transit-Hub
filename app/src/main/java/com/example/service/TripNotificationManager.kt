package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.model.trip.UnifiedActiveTripSnapshot
import com.example.data.repository.ActiveTripState
import com.example.ui.dashboard.AppLanguage
import com.example.util.ActiveTripProgressTracker
import com.example.util.ActiveTripSnapshotBuilder
import com.example.util.RealTimeTripStatus
import com.example.util.TripSensoryAlertManager
import com.example.util.TripUIStateFormatter

/**
 * Manages notification channels, live Rich Notifications with RemoteViews progress bar,
 * and heads-up navigation alerts (transfer at risk, leave now, imminent debark) for ActiveTripTrackingService.
 */
class TripNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Notification throttling cache
    private var lastNotificationTimeMs = 0L
    private var lastPostedHeadline: String? = null
    private var lastPostedSubheadline: String? = null
    private var lastPostedEtaText: String? = null
    private var lastPostedLegIndex: Int = -1

    private var lastAlertedTransferLegIndex: Int = -1
    private var lastAlertedLeaveNowLegIndex: Int = -1
    private var lastAlertedDebarkLegIndex: Int = -1

    fun resetAlerts() {
        lastAlertedTransferLegIndex = -1
        lastAlertedLeaveNowLegIndex = -1
        lastAlertedDebarkLegIndex = -1
        lastNotificationTimeMs = 0L
        lastPostedHeadline = null
        lastPostedSubheadline = null
        lastPostedEtaText = null
        lastPostedLegIndex = -1
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Seguimiento de Viaje Activo",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificación en vivo del viaje multimodal en curso"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Alertas Críticas de Navegación",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de salida inminente, próxima parada para bajar y transbordos en riesgo"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildInitialFallbackNotification(): Notification {
        val currentAppLanguage = getAppLanguage()
        val title = if (currentAppLanguage == AppLanguage.ES) "Viaje en curso" else "Viatge en curs"
        val desc = if (currentAppLanguage == AppLanguage.ES) "Siguiendo tu trayecto en tiempo real..." else "Seguint el teu trajecte en temps real..."

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(desc)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(pendingIntent)
            .build()
    }

    fun updateNotificationForTrip(
        trip: ActiveTripState,
        latestRealTimeStatus: RealTimeTripStatus?,
        distanceToTarget: Double? = null
    ) {
        val progressInfo = ActiveTripProgressTracker.progressState.value
        val snapshot = ActiveTripSnapshotBuilder.build(
            activeTrip = trip,
            progressInfo = progressInfo,
            realTimeStatus = latestRealTimeStatus,
            appLanguage = getAppLanguage()
        )
        updateNotificationWithSnapshot(snapshot, distanceToTarget)
    }

    fun updateNotificationWithSnapshot(
        snapshot: UnifiedActiveTripSnapshot,
        distanceToTarget: Double? = null
    ) {
        val trip = snapshot.activeTrip
        val legs = trip.itinerary.legs
        val currentLegIndex = snapshot.currentLegIndex
        val currentLeg = snapshot.currentLeg
        val realTime = snapshot.realTimeStatus
        val progressInfo = snapshot.progressInfo

        val isSalYa = snapshot.isLeaveNowAlert
        val isLive = snapshot.isLive
        val isTransferAtRisk = snapshot.isTransferAtRisk
        val currentAppLanguage = getAppLanguage()

        // 1. Consume preformatted Primary Instruction Title, Subtitle, and Adjusted ETA from single source of truth
        val formattedUI = snapshot.formattedUiState

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
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isTransferAtRisk) {
                action = ActiveTripTrackingService.ACTION_SHOW_RECALCULATE_DIALOG
                putExtra("SHOW_TRANSFER_DIALOG", true)
            }
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recalculateIntent = Intent(context, MainActivity::class.java).apply {
            action = ActiveTripTrackingService.ACTION_SHOW_RECALCULATE_DIALOG
            putExtra("SHOW_TRANSFER_DIALOG", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val recalculatePendingIntent = PendingIntent.getActivity(
            context,
            2,
            recalculateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2.2 Intelligent Proactive Alerts (Heads-Up + Haptic + Sound)
        // A) Transbordo en riesgo
        if (isTransferAtRisk && lastAlertedTransferLegIndex != currentLegIndex) {
            lastAlertedTransferLegIndex = currentLegIndex
            val alertTitle = if (currentAppLanguage == AppLanguage.ES) "Posible transbordo perdido" else "Possible transbordament perdut"
            val alertBody = if (currentAppLanguage == AppLanguage.ES) {
                realTime?.transferWarningEs ?: "Se estima que no llegarás a tiempo al enlace. Toca para recalcular ruta sin caminar más."
            } else {
                realTime?.transferWarningCa ?: "S'estima que no arribaràs a temps a l'enllaç. Toca per a recalcular ruta sense caminar més."
            }
            val alertAction = if (currentAppLanguage == AppLanguage.ES) "Buscar alternativas" else "Cercar alternatives"

            val alertNotif = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
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
            notificationManager.notify(NOTIFICATION_ALERT_ID, alertNotif)
            TripSensoryAlertManager.triggerLevel2AttentionCall(context, playAudio = true)
        }

        // B) "Sal ya" / Aviso de salida inminente hacia el primer transporte
        if (isSalYa && currentLegIndex == 0 && lastAlertedLeaveNowLegIndex != currentLegIndex) {
            lastAlertedLeaveNowLegIndex = currentLegIndex
            val leaveTitle = if (currentAppLanguage == AppLanguage.ES) "¡Hora de salir!" else "¡Hora d'eixir!"
            val leaveBody = if (currentAppLanguage == AppLanguage.ES) {
                realTime?.leaveNowMessageEs ?: "Sal ahora para llegar a tiempo a ${currentLeg?.toName ?: "tu parada"}."
            } else {
                realTime?.leaveNowMessageCa ?: "Ix ara per arribar a temps a ${currentLeg?.toName ?: "la teua parada"}."
            }

            val leaveNotif = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(leaveTitle)
                .setContentText(leaveBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .build()
            notificationManager.notify(NOTIFICATION_LEAVE_NOW_ID, leaveNotif)
            TripSensoryAlertManager.triggerLevel2AttentionCall(context, playAudio = true)
        } else if (currentLegIndex > 0 || progressInfo.isBoarded || (!isSalYa && lastAlertedLeaveNowLegIndex != -1)) {
            notificationManager.cancel(NOTIFICATION_LEAVE_NOW_ID)
            if (currentLegIndex > 0 || progressInfo.isBoarded) {
                lastAlertedLeaveNowLegIndex = -1
            }
        }

        // C) Aviso de "Próxima parada / Prepárate para bajar"
        if (progressInfo.isBoarded && currentLeg != null && currentLeg.mode in listOf(TransitMode.BUS, TransitMode.SUBWAY, TransitMode.TRAM, TransitMode.RAIL)) {
            val totalStopsInLeg = (currentLeg.intermediateStops.size + 1).coerceAtLeast(1)
            val remainingStops = progressInfo.remainingStopsCount ?: run {
                val passedStops = (progressInfo.progressWithinLeg * totalStopsInLeg).toInt().coerceIn(0, currentLeg.intermediateStops.size)
                (totalStopsInLeg - passedStops).coerceAtLeast(1)
            }
            val remainingMins = TripUIStateFormatter.calculateBoardedRemainingMinutes(currentLeg, realTime)
            val isNearPenultimateOrTime = TripUIStateFormatter.isNearPenultimateStopOrTime(
                currentLeg = currentLeg,
                remainingMins = remainingMins,
                distanceToTargetMeters = distanceToTarget,
                progressWithinLeg = progressInfo.progressWithinLeg
            )
            val isImminentDebark = snapshot.isImminentDebark ||
                (remainingStops == 1 && isNearPenultimateOrTime) ||
                (distanceToTarget != null && distanceToTarget <= 350.0 && remainingMins <= 2)

            if (isImminentDebark && lastAlertedDebarkLegIndex != currentLegIndex) {
                lastAlertedDebarkLegIndex = currentLegIndex
                val modeLabel = when (currentLeg.mode) {
                    TransitMode.BUS -> "Bus"
                    TransitMode.SUBWAY -> "Metro"
                    TransitMode.TRAM -> if (currentAppLanguage == AppLanguage.ES) "Tranvía" else "Tramvia"
                    TransitMode.RAIL -> if (currentAppLanguage == AppLanguage.ES) "Tren" else "Tren"
                    else -> ""
                }
                val lineLabel = currentLeg.routeShortName ?: ""
                val destStation = currentLeg.toName
                val debarkTitle = if (currentAppLanguage == AppLanguage.ES) {
                    "Próxima parada: Baja en $destStation"
                } else {
                    "Pròxima parada: Baixa a $destStation"
                }
                val debarkBody = if (currentAppLanguage == AppLanguage.ES) {
                    "Prepárate para bajar de $modeLabel $lineLabel."
                } else {
                    "Prepara't per a baixar de $modeLabel $lineLabel."
                }

                val debarkNotif = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(debarkTitle)
                    .setContentText(debarkBody)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(openAppPendingIntent)
                    .build()
                notificationManager.notify(NOTIFICATION_DEBARK_ID, debarkNotif)
                TripSensoryAlertManager.triggerLevel2AttentionCall(context, playAudio = true)
            } else if (lastAlertedDebarkLegIndex != -1 && (progressInfo.progressWithinLeg >= 0.98f || distanceToTarget != null && distanceToTarget <= 50.0)) {
                notificationManager.cancel(NOTIFICATION_DEBARK_ID)
            }
        } else if (lastAlertedDebarkLegIndex != -1) {
            notificationManager.cancel(NOTIFICATION_DEBARK_ID)
            lastAlertedDebarkLegIndex = -1
        }

        // 3. System Dark/Light Mode Detection & Color Adaptation
        val isSystemDark = isSystemInDarkMode()

        val titleColor = if (isSystemDark) Color.parseColor("#FFFFFF") else Color.parseColor("#0F172A")
        val subtitleColor = if (isSystemDark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val etaColor = if (isSystemDark) Color.parseColor("#E2E8F0") else Color.parseColor("#1E293B")
        val extraInfoColor = if (isSystemDark) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
        val compactEtaColor = if (isSystemDark) Color.parseColor("#00A86B") else Color.parseColor("#047857")
        val destIconTint = if (isSystemDark) Color.parseColor("#E2E8F0") else Color.parseColor("#475569")

        val currentMode = currentLeg?.mode ?: TransitMode.WALK
        val modeIconBitmap = NotificationProgressBitmapGenerator.generateModeIconBitmap(
            context = context,
            mode = currentMode,
            isSalYa = isSalYa,
            isSystemDark = isSystemDark
        )
        val progressBarBitmap = NotificationProgressBitmapGenerator.generateProgressBarBitmap(
            context = context,
            legs = legs,
            currentLegIndex = currentLegIndex,
            progressFractionInLeg = progressInfo.progressWithinLeg,
            isSystemDark = isSystemDark
        )

        val dynamicDuration = if (formattedUI.formattedRemainingDurationText.isNotBlank()) formattedUI.formattedRemainingDurationText else trip.itinerary.formattedDuration

        // 4. Populate Expanded RemoteViews
        val expandedView = RemoteViews(context.packageName, R.layout.notification_active_trip).apply {
            setImageViewBitmap(R.id.notif_mode_icon, modeIconBitmap)
            setTextViewText(R.id.notif_title, titleText)
            setTextColor(R.id.notif_title, titleColor)

            setTextViewText(R.id.notif_subtitle, subtitleText)
            setTextColor(R.id.notif_subtitle, subtitleColor)

            if (!formattedUI.nextTransitDepartureInfo.isNullOrBlank()) {
                setTextViewText(R.id.notif_extra_info, formattedUI.nextTransitDepartureInfo)
                setTextColor(R.id.notif_extra_info, extraInfoColor)
                setViewVisibility(R.id.notif_extra_info, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.notif_extra_info, android.view.View.GONE)
            }

            setImageViewBitmap(R.id.notif_progress_bar_image, progressBarBitmap)

            setTextViewText(R.id.notif_eta_text, formattedUI.formattedArrivalTimeText)
            setTextColor(R.id.notif_eta_text, etaColor)
            setInt(R.id.notif_dest_icon, "setColorFilter", destIconTint)

            setTextViewText(R.id.notif_duration_text, dynamicDuration)
            setTextColor(R.id.notif_duration_text, titleColor)

            if (isLive) {
                setViewVisibility(R.id.notif_live_badge, android.view.View.VISIBLE)
                setTextViewText(R.id.notif_live_badge, if (currentAppLanguage == AppLanguage.ES) "● En directo" else "● En directe")
            } else {
                setViewVisibility(R.id.notif_live_badge, android.view.View.GONE)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val density = context.resources.displayMetrics.density
                val padH = (12 * density).toInt()
                val padV = (8 * density).toInt()
                setViewPadding(R.id.notif_expanded_root, padH, padV, padH, padV)
            } else {
                setViewPadding(R.id.notif_expanded_root, 0, 0, 0, 0)
            }
        }

        // 5. Populate Compact RemoteViews
        val compactView = RemoteViews(context.packageName, R.layout.notification_active_trip_compact).apply {
            setImageViewBitmap(R.id.notif_compact_icon, modeIconBitmap)
            setTextViewText(R.id.notif_compact_title, titleText)
            setTextColor(R.id.notif_compact_title, titleColor)

            setTextViewText(R.id.notif_compact_subtitle, subtitleText)
            setTextColor(R.id.notif_compact_subtitle, subtitleColor)

            setTextViewText(R.id.notif_compact_eta, dynamicDuration)
            setTextColor(R.id.notif_compact_eta, compactEtaColor)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val density = context.resources.displayMetrics.density
                val padH = (12 * density).toInt()
                val padV = (6 * density).toInt()
                setViewPadding(R.id.notif_compact_root, padH, padV, padH, padV)
            } else {
                setViewPadding(R.id.notif_compact_root, 0, 0, 0, 0)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(openAppPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateNotificationSimple(title: String, content: String) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelAllNotifications() {
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(NOTIFICATION_ALERT_ID)
        notificationManager.cancel(NOTIFICATION_LEAVE_NOW_ID)
        notificationManager.cancel(NOTIFICATION_DEBARK_ID)
    }

    fun getAppLanguage(): AppLanguage {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val langStr = prefs.getString("app_language", null) ?: "CA"
        return try {
            AppLanguage.valueOf(langStr)
        } catch (_: Exception) {
            AppLanguage.CA
        }
    }

    private fun isSystemInDarkMode(): Boolean {
        val systemUiMode = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return systemUiMode == Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        const val NOTIFICATION_ID = 4001
        const val NOTIFICATION_ALERT_ID = 4002
        const val NOTIFICATION_LEAVE_NOW_ID = 4003
        const val NOTIFICATION_DEBARK_ID = 4004
        const val CHANNEL_ID = "active_trip_tracking_channel"
        const val CHANNEL_ALERT_ID = "active_trip_alert_channel"
    }
}
