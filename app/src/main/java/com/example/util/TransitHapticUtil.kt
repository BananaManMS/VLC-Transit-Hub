package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Clean haptic feedback utility for scheduled departures pull-up interaction.
 *
 * 1. Medium-strong short vibration when reaching the unlock threshold.
 * 2. Slightly stronger vibration when theoretical departures finish loading and display.
 */
object TransitHapticUtil {

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Moderately strong but short vibration when reaching the threshold to unlock scheduled trains.
     * (Disabled per user request)
     */
    fun performThresholdReachedFeedback(context: Context) {
        // No vibration
    }

    /**
     * Slightly stronger vibration when scheduled departures finish loading and appear.
     * (Disabled per user request)
     */
    fun performLoadedFeedback(context: Context) {
        // No vibration
    }
}
