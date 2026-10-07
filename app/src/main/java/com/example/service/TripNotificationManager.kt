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
                context.getString(R.string.notif_channel_active_trip),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_active_trip_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                context.getString(R.string.notif_channel_critical_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_critical_alerts_desc)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildInitialFallbackNotification(): Notification {
        val currentAppLanguage = getAppLanguage()
        val title = context.getString(R.string.notif_trip_in_progress)
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

        val cancelIntent = Intent(context, ActiveTripTrackingService::class.java).apply {
            action = ActiveTripTrackingService.ACTION_STOP
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelActionText = if (currentAppLanguage == AppLanguage.ES) "Cancelar viaje" else "Cancel·lar viatge"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_train_logo)
            .setContentTitle(title)
            .setContentText(desc)
            .setOngoing(true)
            .setSortKey("!0_PRIMARY_LIVE_TRIP")
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                cancelActionText,
                cancelPendingIntent
            )
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

        val titleText = formattedUI.notificationHeadline?.takeIf { it.isNotBlank() } ?: formattedUI.headline
        val subtitleText = formattedUI.notificationSubheadline ?: formattedUI.subheadline
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
            val alertTitle = context.getString(R.string.trip_warning_transfer_lost)
            val alertBody = if (currentAppLanguage == AppLanguage.ES) {
                realTime?.transferWarningEs ?: context.getString(R.string.notif_transfer_warning_fallback)
            } else {
                realTime?.transferWarningCa ?: context.getString(R.string.notif_transfer_warning_fallback)
            }
            val alertAction = context.getString(R.string.timeline_search_alternatives)

            val alertNotif = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setSmallIcon(R.drawable.ic_stat_train_logo)
                .setContentTitle(alertTitle)
                .setContentText(alertBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSortKey("z_trip_alert")
                .setTimeoutAfter(12000L)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
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
        } else if (!isTransferAtRisk && lastAlertedTransferLegIndex != -1) {
            notificationManager.cancel(NOTIFICATION_ALERT_ID)
            lastAlertedTransferLegIndex = -1
        }

        // B) "Sal ya" / Aviso de salida inminente hacia el primer transporte
        if (isSalYa && currentLegIndex == 0 && lastAlertedLeaveNowLegIndex != currentLegIndex) {
            lastAlertedLeaveNowLegIndex = currentLegIndex
            val leaveTitle = context.getString(R.string.notif_leave_now_title)
            val leaveBody = if (currentAppLanguage == AppLanguage.ES) {
                realTime?.leaveNowMessageEs ?: "Sal ahora para llegar a tiempo a ${currentLeg?.toName ?: "tu parada"}."
            } else {
                realTime?.leaveNowMessageCa ?: "Ix ara per arribar a temps a ${currentLeg?.toName ?: "la teua parada"}."
            }

            val leaveNotif = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setSmallIcon(R.drawable.ic_stat_train_logo)
                .setContentTitle(leaveTitle)
                .setContentText(leaveBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSortKey("z_trip_alert")
                .setTimeoutAfter(12000L)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
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
        if (progressInfo.isBoarded && currentLeg != null && currentLeg.mode in listOf(TransitMode.BUS, TransitMode.SUBWAY, TransitMode.TRAM, TransitMode.RAIL, TransitMode.METROBUS, TransitMode.CERCANIAS)) {
            val isImminentDebark = snapshot.isImminentDebark

            if (isImminentDebark && lastAlertedDebarkLegIndex != currentLegIndex) {
                lastAlertedDebarkLegIndex = currentLegIndex
                val modeLabel = when (currentLeg.mode) {
                    TransitMode.BUS -> "Bus"
                    TransitMode.METROBUS -> "Metrobús"
                    TransitMode.SUBWAY -> "Metro"
                    TransitMode.TRAM -> context.getString(R.string.transit_mode_tram)
                    TransitMode.RAIL, TransitMode.CERCANIAS -> context.getString(R.string.transit_mode_train)
                    else -> ""
                }
                val lineLabel = currentLeg.routeShortName ?: ""
                val destStation = currentLeg.toName
                val debarkTitle = context.getString(R.string.notif_next_stop_title, destStation)
                val debarkBody = context.getString(R.string.notif_prepare_alight, modeLabel, lineLabel)

                val debarkNotif = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                    .setSmallIcon(R.drawable.ic_stat_train_logo)
                    .setContentTitle(debarkTitle)
                    .setContentText(debarkBody)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSortKey("z_trip_alert")
                    .setTimeoutAfter(12000L)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
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

            if (subtitleText.isNotBlank() && subtitleText != formattedUI.nextTransitDepartureInfo) {
                setTextViewText(R.id.notif_subtitle, subtitleText)
                setTextColor(R.id.notif_subtitle, subtitleColor)
                setViewVisibility(R.id.notif_subtitle, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.notif_subtitle, android.view.View.GONE)
            }

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

        // 5. Populate Compact RemoteViews (clean strictly 2-line layout: title, departure/subtitle, duration)
        val compactView = RemoteViews(context.packageName, R.layout.notification_active_trip_compact).apply {
            setTextViewText(R.id.notif_compact_title, titleText)
            setTextColor(R.id.notif_compact_title, titleColor)

            val compactSub = if (subtitleText.isNotBlank()) subtitleText else (formattedUI.nextTransitDepartureInfo ?: "")
            if (compactSub.isNotBlank()) {
                setTextViewText(R.id.notif_compact_subtitle, compactSub)
                setTextColor(R.id.notif_compact_subtitle, if (subtitleText.isNotBlank()) subtitleColor else extraInfoColor)
                setViewVisibility(R.id.notif_compact_subtitle, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.notif_compact_subtitle, android.view.View.GONE)
            }

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

        try {
            val chipText = if (dynamicDuration.length <= 8) dynamicDuration else "En ruta"

            val cancelIntent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ActiveTripTrackingService.ACTION_STOP
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cancelActionText = if (currentAppLanguage == AppLanguage.ES) "Cancelar viaje" else "Cancel·lar viatge"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_train_logo)
                .setContentTitle(titleText)
                .setContentText(subtitleText)
                .setSubText(chipText)
                .setCustomContentView(compactView)
                .setCustomBigContentView(expandedView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setOngoing(true)
                .setSortKey("!0_PRIMARY_LIVE_TRIP")
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOnlyAlertOnce(true)
                .setSound(null)
                .setVibrate(null)
                .setNotificationSilent()
                .setContentIntent(openAppPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    cancelActionText,
                    cancelPendingIntent
                )
                .build()

            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    notification.flags = notification.flags or Notification.FLAG_PROMOTED_ONGOING
                    notification.extras.putBoolean("android.requestPromotedOngoing", true)
                } catch (_: Throwable) {}
            }

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.e("TripNotificationManager", "Error building or posting rich active trip notification: ${e.message}", e)
        }
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
            .setSmallIcon(R.drawable.ic_stat_train_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(false)
            .setGroup(GROUP_ACTIVE_TRIP_ONGOING)
            .setSortKey("0_ongoing_trip")
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(null)
            .setVibrate(null)
            .setNotificationSilent()
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showBoardingConfirmationNotification(vehicleName: String, legIndex: Int) {
        val currentAppLanguage = getAppLanguage()
        val title = context.getString(R.string.notif_board_check_title, vehicleName)
        val text = context.getString(R.string.notif_board_check_body)

        // Action SÍ: confirms manual boarding
        val yesIntent = Intent(context, ActiveTripTrackingService::class.java).apply {
            action = ActiveTripTrackingService.ACTION_MANUAL_BOARDING
            putExtra(ActiveTripTrackingService.EXTRA_LEG_INDEX, legIndex)
        }
        val yesPendingIntent = PendingIntent.getService(
            context,
            101,
            yesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action NO: rejects boarding and forces recalculation
        val noIntent = Intent(context, ActiveTripTrackingService::class.java).apply {
            action = ActiveTripTrackingService.ACTION_REJECT_BOARDING
            putExtra(ActiveTripTrackingService.EXTRA_LEG_INDEX, legIndex)
        }
        val noPendingIntent = PendingIntent.getService(
            context,
            102,
            noIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val yesLabel = context.getString(R.string.yes_label)
        val noLabel = context.getString(R.string.no_label)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_stat_train_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setContentIntent(openAppPendingIntent)
            .setSortKey("z_trip_alert")
            .setTimeoutAfter(30000L)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 350, 150, 350))
            .setAutoCancel(true)
            .addAction(0, yesLabel, yesPendingIntent)
            .addAction(0, noLabel, noPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_BOARDING_CONFIRM_ID, notification)
    }

    fun dismissBoardingConfirmationNotification() {
        notificationManager.cancel(NOTIFICATION_BOARDING_CONFIRM_ID)
    }

    fun cancelAllNotifications() {
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(NOTIFICATION_ALERT_ID)
        notificationManager.cancel(NOTIFICATION_LEAVE_NOW_ID)
        notificationManager.cancel(NOTIFICATION_DEBARK_ID)
        notificationManager.cancel(NOTIFICATION_BOARDING_CONFIRM_ID)
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
        const val NOTIFICATION_BOARDING_CONFIRM_ID = 4005
        const val CHANNEL_ID = "active_trip_channel_v4"
        const val CHANNEL_ALERT_ID = "active_trip_alert_channel_v4"
        const val GROUP_ACTIVE_TRIP_ONGOING = "com.example.transit.GROUP_ACTIVE_TRIP_ONGOING"
        const val GROUP_TRIP_ALERTS = "com.example.transit.GROUP_TRIP_ALERTS"
    }
}
