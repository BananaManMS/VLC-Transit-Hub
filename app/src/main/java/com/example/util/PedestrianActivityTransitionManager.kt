package com.example.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PedestrianState {
    object Walking : PedestrianState()
    object Still : PedestrianState()
    object InVehicle : PedestrianState()
}

/**
 * Gestor de transiciones de actividad parcheado.
 * Resuelve la ceguera del GPS en la transición STILL -> IN_VEHICLE
 * y escucha de forma reactiva al ActivityTransitionEventBus.
 */
class PedestrianActivityTransitionManager(
    private val context: Context,
    private val externalScope: CoroutineScope
) {
    private val client = ActivityRecognition.getClient(context)

    private val _rawMotionState = MutableStateFlow<PedestrianState>(PedestrianState.Walking)

    private val _throttledGpsIntervalMs = MutableStateFlow(FAST_WALKING_INTERVAL_MS)
    val throttledGpsIntervalMs: StateFlow<Long> = _throttledGpsIntervalMs.asStateFlow()

    private var stillDebounceJob: Job? = null

    init {
        // 1. Escuchar de forma pasiva los eventos provenientes del EventBus
        externalScope.launch(Dispatchers.Default) {
            ActivityTransitionEventBus.events.collect { event ->
                onTransitionEventReceived(event)
            }
        }

        // 2. Máquina de estados parcheada para gestionar WALKING, IN_VEHICLE y STILL
        externalScope.launch(Dispatchers.Default) {
            _rawMotionState.collect { state ->
                when (state) {
                    is PedestrianState.Walking, is PedestrianState.InVehicle -> {
                        // Solución al Bug STILL -> IN_VEHICLE:
                        // Tanto WALKING como IN_VEHICLE cancelan inmediatamente el estrangulamiento
                        // y restauran la frecuencia del GPS a 5000ms.
                        stillDebounceJob?.cancel()
                        stillDebounceJob = null
                        if (_throttledGpsIntervalMs.value != FAST_WALKING_INTERVAL_MS) {
                            val reason = if (state is PedestrianState.InVehicle) "IN_VEHICLE" else "WALKING"
                            android.util.Log.i(
                                TAG,
                                "🚀 [ACTIVITY_TRANSITION] Movimiento detectado ($reason). Restaurando GPS a 5s inmediatamente."
                            )
                            _throttledGpsIntervalMs.value = FAST_WALKING_INTERVAL_MS
                        }
                    }
                    is PedestrianState.Still -> {
                        // Ventana de histéresis de 10s para STILL
                        stillDebounceJob?.cancel()
                        stillDebounceJob = externalScope.launch(Dispatchers.Default) {
                            android.util.Log.i(
                                TAG,
                                "⏳ [ACTIVITY_TRANSITION] Usuario detenido (STILL). Esperando ventana de 10s..."
                            )
                            delay(STILL_THROTTLE_DELAY_MS)
                            if (_throttledGpsIntervalMs.value != SLOW_STILL_INTERVAL_MS) {
                                android.util.Log.i(
                                    TAG,
                                    "🛑 [ACTIVITY_TRANSITION] Detenido >= 10s. Estrangulando GPS a 30s."
                                )
                                _throttledGpsIntervalMs.value = SLOW_STILL_INTERVAL_MS
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun registerTransitions() {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            // Suscripción al evento IN_VEHICLE para evitar el cuelgue en paradas de autobús
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)
        val pendingIntent = getPendingIntent()

        client.requestActivityTransitionUpdates(request, pendingIntent)
            .addOnSuccessListener {
                android.util.Log.i(TAG, "✅ Transiciones (WALKING, STILL, IN_VEHICLE) registradas correctamente.")
            }
            .addOnFailureListener { e ->
                android.util.Log.e(TAG, "❌ Error al registrar transiciones: ${e.message}", e)
            }
    }

    @SuppressLint("MissingPermission")
    fun unregisterTransitions() {
        val pendingIntent = getPendingIntent()
        client.removeActivityTransitionUpdates(pendingIntent)
        stillDebounceJob?.cancel()
    }

    fun onTransitionEventReceived(event: ActivityTransitionEvent) {
        if (event.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) return

        when (event.activityType) {
            DetectedActivity.WALKING -> {
                _rawMotionState.value = PedestrianState.Walking
            }
            DetectedActivity.IN_VEHICLE -> {
                _rawMotionState.value = PedestrianState.InVehicle
            }
            DetectedActivity.STILL -> {
                _rawMotionState.value = PedestrianState.Still
            }
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityTransitionBroadcastReceiver::class.java).apply {
            action = ACTION_PROCESS_ACTIVITY_TRANSITIONS
        }
        return PendingIntent.getBroadcast(
            context,
            PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        private const val TAG = "PedestrianTransition"
        const val ACTION_PROCESS_ACTIVITY_TRANSITIONS = "com.example.action.PROCESS_ACTIVITY_TRANSITIONS"
        private const val PENDING_INTENT_REQUEST_CODE = 3001

        const val FAST_WALKING_INTERVAL_MS = 5000L
        const val SLOW_STILL_INTERVAL_MS = 30000L
        const val STILL_THROTTLE_DELAY_MS = 10000L
    }
}
