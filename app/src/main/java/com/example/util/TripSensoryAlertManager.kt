package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Hierarchical Sensory Alert System (Haptics & Audio) for active navigation.
 * Strictly limited to:
 * - Level 1 (Silent Confirmation): Short single haptic pulse for natural state transitions (boarding, station proximity).
 * - Level 2 (Heads-Up Attention): Dual haptic pulse + gentle chime sound for actionable alerts (upcoming exit, 1 stop to debark).
 */
object TripSensoryAlertManager {

    private val LEVEL_1_VIBRATION_TIMINGS = longArrayOf(0, 50)
    private val LEVEL_1_VIBRATION_AMPLITUDES = intArrayOf(0, 140)

    private val LEVEL_2_VIBRATION_TIMINGS = longArrayOf(0, 80, 80, 100)
    private val LEVEL_2_VIBRATION_AMPLITUDES = intArrayOf(0, 200, 0, 255)

    /**
     * Nivel 1 (Confirmación Silenciosa):
     * Utiliza un pulso de vibración corto (~50ms) sin sonido para transiciones de estado
     * naturales (e.g. entrar en radio de parada, confirmación de 'A bordo', tramo completado).
     */
    fun triggerLevel1SilentConfirmation(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = CombinedVibration.createParallel(
                    VibrationEffect.createWaveform(LEVEL_1_VIBRATION_TIMINGS, LEVEL_1_VIBRATION_AMPLITUDES, -1)
                )
                vibratorManager?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(LEVEL_1_VIBRATION_TIMINGS, LEVEL_1_VIBRATION_AMPLITUDES, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TripSensoryAlert", "Level 1 vibration error: ${e.message}")
        }
    }

    /**
     * Nivel 2 (Llamada de Atención):
     * Combina una vibración de doble pulso con un tono corto y amable (tipo chime/beep)
     * para avisos clave que requieren acción (e.g. aviso de bajada en la siguiente parada, HUD de desembarque).
     */
    fun triggerLevel2AttentionCall(context: Context, playAudio: Boolean = true) {
        try {
            // 1. Dual-pulse haptics
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = CombinedVibration.createParallel(
                    VibrationEffect.createWaveform(LEVEL_2_VIBRATION_TIMINGS, LEVEL_2_VIBRATION_AMPLITUDES, -1)
                )
                vibratorManager?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(LEVEL_2_VIBRATION_TIMINGS, LEVEL_2_VIBRATION_AMPLITUDES, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(LEVEL_2_VIBRATION_TIMINGS, -1)
                }
            }

            // 2. Gentle chime sound
            if (playAudio) {
                try {
                    val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                } catch (e: Exception) {
                    android.util.Log.w("TripSensoryAlert", "Tone generator error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TripSensoryAlert", "Level 2 attention error: ${e.message}")
        }
    }
}
