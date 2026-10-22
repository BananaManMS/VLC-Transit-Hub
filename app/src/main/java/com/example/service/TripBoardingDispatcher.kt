package com.example.service

import android.content.Context
import com.example.data.model.routing.PlannedLeg
import com.example.data.repository.ActiveTripState
import com.example.util.ActiveTripProgressTracker
import com.example.util.TripRealTimeReconciler
import com.example.util.TripSensoryAlertManager
import com.example.util.TripStepProgressionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles haptic feedback and parallel dispatch of boarding actions across UI,
 * real-time drift reconciler, and dead-reckoning spatial engine.
 */
object TripBoardingDispatcher {

    private const val TAG = "TripBoardingDispatcher"

    /**
     * Executes the 3 atomic actions concurrently upon boarding confirmation:
     * 1. Immediate UX (Vibration + StateFlow emission for Jetpack Compose)
     * 2. Network & Corridor Drift tracking (Background IO)
     * 3. Spatial engine update & tunnel dead-reckoning activation (Background Default)
     */
    fun dispatchBoardingActionsConcurrently(
        context: Context,
        scope: CoroutineScope,
        reconciler: TripRealTimeReconciler,
        leg: PlannedLeg,
        legIndex: Int,
        getActiveTrip: () -> ActiveTripState?,
        onUpdateNotification: (ActiveTripState) -> Unit
    ) {
        // 1. Immediate UX
        scope.launch(Dispatchers.Main.immediate) {
            triggerBoardingHapticFeedback(context)
            ActiveTripProgressTracker.markAsBoarded(legIndex)
            getActiveTrip()?.let { onUpdateNotification(it) }
        }

        // 2. Network and Drift reconciliation
        scope.launch(Dispatchers.IO) {
            try {
                reconciler.onBoardingConfirmed(leg = leg, legIndex = legIndex)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error synchronizing Reconciler on boarding: ${e.message}", e)
            }
        }

        // 3. Spatial engine & tunnel dead-reckoning
        scope.launch(Dispatchers.Default) {
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
     * Triggers distinctive boarding haptic feedback using TripSensoryAlertManager.
     */
    fun triggerBoardingHapticFeedback(context: Context) {
        TripSensoryAlertManager.triggerLevel1SilentConfirmation(context)
    }
}
