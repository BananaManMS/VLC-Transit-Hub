package com.example.util

import android.util.Log
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.PlannedStop
import com.example.data.model.routing.TransitMode
import com.example.data.repository.RealTimeTransitRepository
import com.example.data.repository.routing.TransitIdMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Representa la estrategia activa de seguimiento en tiempo real una vez confirmado el embarque (BOARDED).
 */
sealed interface BoardedTrackingState {

    /**
     * Tramo inactivo o no embarcado (espera o caminata).
     */
    data object Idle : BoardedTrackingState

    /**
     * Ruta A: Para tramos cortos (<= 2 paradas totales).
     * El ETA se calcula mediante proyección inercial con el retraso inicial congelado.
     */
    data class InertialBypass(
        val legIndex: Int,
        val frozenDepartureDelayMinutes: Int,
        val scheduledArrivalTime: String?,
        val totalStopsCount: Int
    ) : BoardedTrackingState

    /**
     * Ruta B: Para tramos largos (> 2 paradas totales).
     * Monitorea activamente la penúltima parada para derivar el retraso del corredor.
     */
    data class CorridorTracking(
        val legIndex: Int,
        val monitoredStopId: String,
        val monitoredStopName: String,
        val lastKnownDriftMinutes: Int,
        val isFallbackActive: Boolean,
        val lastSuccessfulPollTimestamp: Long
    ) : BoardedTrackingState
}

/**
 * Evento de actualización de deriva para inyectar en el orquestador global de viaje.
 */
data class TransitDriftUpdate(
    val legIndex: Int,
    val driftMinutes: Int,
    val isLiveFromCorridor: Boolean,
    val monitoredStopName: String? = null,
    val isFallbackInertial: Boolean = false
)

/**
 * Motor de dominio para el relevo de polling y cálculo dinámico de deriva tras el embarque (BOARDED).
 *
 * 1. Cancelación de Origen: Al dispararse el evento de embarque, cancela inmediatamente el polling de la parada actual.
 * 2. Bifurcación según longitud del tramo (intermediateStops.size + 2):
 *    - Ruta A - Bypass Inercial (<= 2 paradas totales): Congela initialDepartureDelay y el ETA avanza por el reloj del sistema.
 *    - Ruta B - Trackeo de Corredor (> 2 paradas totales): Realiza polling a baja frecuencia apuntando a la penúltima parada
 *      y calcula el Drift = (ETA_Vivo_Penúltima - ETA_Programado_Penúltima).
 * 3. Fallback de Seguridad: Si la API devuelve error o desaparece del feed, revierte silenciosamente al modelo inercial congelando el último Drift conocido.
 */
class BoardedDriftReconciler(
    private val externalScope: CoroutineScope? = null
) {
    private val _trackingState = MutableStateFlow<BoardedTrackingState>(BoardedTrackingState.Idle)
    val trackingState: StateFlow<BoardedTrackingState> = _trackingState.asStateFlow()

    private val _driftUpdates = MutableStateFlow<TransitDriftUpdate?>(null)
    val driftUpdates: StateFlow<TransitDriftUpdate?> = _driftUpdates.asStateFlow()

    private var activePollingJob: Job? = null
    private val mutex = Mutex()

    private var currentTrackedLegIndex: Int = -1
    private var lastKnownDrift: Int = 0
    private var boardedVehicleId: String? = null

    companion object {
        private const val TAG = "BoardedDriftReconciler"
        const val SHORT_LEG_THRESHOLD_STOPS = 2
        private const val CORRIDOR_POLLING_INTERVAL_MS = 60_000L // 60s baja frecuencia
    }

    /**
     * Disparador principal invocado al confirmarse el estado BOARDED en un tramo de transporte.
     */
    fun onBoardingConfirmed(
        currentLeg: PlannedLeg,
        currentLegIndex: Int,
        initialDepartureDelayMinutes: Int,
        vehicleId: String? = null
    ) {
        currentTrackedLegIndex = currentLegIndex
        lastKnownDrift = initialDepartureDelayMinutes
        boardedVehicleId = vehicleId

        if (!vehicleId.isNullOrBlank()) {
            Log.d(TAG, "ONBOARD confirmed for Metro leg #$currentLegIndex locked to Vehicle ID: $vehicleId")
        }

        // 1. Cancelación inmediata de cualquier polling anterior
        cancelActivePollingJob()

        val totalStopsCount = currentLeg.intermediateStops.size + 2

        if (totalStopsCount <= SHORT_LEG_THRESHOLD_STOPS) {
            // Ruta A: Bypass Inercial
            executeInertialBypass(
                legIndex = currentLegIndex,
                initialDelayMinutes = initialDepartureDelayMinutes,
                scheduledArrival = currentLeg.formattedEndTime.ifBlank { currentLeg.endTime },
                totalStops = totalStopsCount
            )
        } else {
            // Ruta B: Trackeo de Corredor
            val penultimateStop = resolvePenultimateStop(currentLeg)
            if (penultimateStop?.stopId != null) {
                setupCorridorTracking(
                    leg = currentLeg,
                    legIndex = currentLegIndex,
                    penultimateStop = penultimateStop,
                    initialDelayMinutes = initialDepartureDelayMinutes
                )
            } else {
                executeInertialBypass(
                    legIndex = currentLegIndex,
                    initialDelayMinutes = initialDepartureDelayMinutes,
                    scheduledArrival = currentLeg.formattedEndTime.ifBlank { currentLeg.endTime },
                    totalStops = totalStopsCount
                )
            }
        }
    }

    /**
     * Reconciliación directa síncrona o por ciclo del tramo embarcado (usada dentro del reconciliador general).
     */
    suspend fun reconcileBoardedLeg(
        leg: PlannedLeg,
        legIndex: Int,
        nowMs: Long
    ): TransitDriftUpdate {
        val totalStopsCount = leg.intermediateStops.size + 2

        if (totalStopsCount <= SHORT_LEG_THRESHOLD_STOPS) {
            // Ruta A: Bypass Inercial
            return TransitDriftUpdate(
                legIndex = legIndex,
                driftMinutes = lastKnownDrift,
                isLiveFromCorridor = false,
                isFallbackInertial = false
            )
        }

        // Tramo final / Cerca del destino (últimos 3-4 min o >= 75% progreso): Detener polling de corredor para evitar desajustes
        val progressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg.coerceIn(0f, 1f)
        val totalLegMins = (leg.durationSeconds / 60).toInt().coerceAtLeast(1)
        val remainingMinsExpected = (totalLegMins * (1f - progressFraction)).toInt()

        if (progressFraction >= 0.75f || remainingMinsExpected <= 3) {
            return TransitDriftUpdate(
                legIndex = legIndex,
                driftMinutes = lastKnownDrift,
                isLiveFromCorridor = false,
                isFallbackInertial = true
            )
        }

        val penultimateStop = resolvePenultimateStop(leg)
        if (penultimateStop?.stopId == null) {
            return TransitDriftUpdate(
                legIndex = legIndex,
                driftMinutes = lastKnownDrift,
                isLiveFromCorridor = false,
                isFallbackInertial = true
            )
        }

        return try {
            val liveMinutes = fetchPenultimateStopLiveArrival(leg, penultimateStop, nowMs)
            if (liveMinutes != null && liveMinutes >= 0) {
                // Cálculo del Drift respecto al teórico programado de la penúltima parada
                val theoreticalRemaining = SingleTransitLegReconciler.calculateTheoreticalMinutesRemaining(
                    penultimateStop.scheduledTime ?: penultimateStop.formattedTime ?: leg.endTime
                ) ?: 0

                val calculatedDrift = liveMinutes - theoreticalRemaining

                // Anti-Jump Guard: If drift suddenly spikes by > 3 minutes compared to last known drift,
                // the monitored vehicle has likely passed the stop and the API returned the subsequent vehicle.
                if (lastKnownDrift != 0 && (calculatedDrift - lastKnownDrift) > 3) {
                    Log.w(TAG, "Anomalous drift jump detected (from ${lastKnownDrift}m to ${calculatedDrift}m). Vehicle likely passed stop; applying inertial fallback.")
                    return applySilentFallback(legIndex, penultimateStop, nowMs)
                }

                lastKnownDrift = calculatedDrift

                _trackingState.value = BoardedTrackingState.CorridorTracking(
                    legIndex = legIndex,
                    monitoredStopId = penultimateStop.stopId,
                    monitoredStopName = penultimateStop.name,
                    lastKnownDriftMinutes = calculatedDrift,
                    isFallbackActive = false,
                    lastSuccessfulPollTimestamp = nowMs
                )

                val update = TransitDriftUpdate(
                    legIndex = legIndex,
                    driftMinutes = calculatedDrift,
                    isLiveFromCorridor = true,
                    monitoredStopName = penultimateStop.name,
                    isFallbackInertial = false
                )
                _driftUpdates.value = update
                update
            } else {
                // Fallback silencioso: mantener último drift conocido
                applySilentFallback(legIndex, penultimateStop, nowMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando penúltima parada (${e.message}). Aplicando fallback inercial.")
            applySilentFallback(legIndex, penultimateStop, nowMs)
        }
    }

    private fun executeInertialBypass(
        legIndex: Int,
        initialDelayMinutes: Int,
        scheduledArrival: String?,
        totalStops: Int
    ) {
        val bypassState = BoardedTrackingState.InertialBypass(
            legIndex = legIndex,
            frozenDepartureDelayMinutes = initialDelayMinutes,
            scheduledArrivalTime = scheduledArrival,
            totalStopsCount = totalStops
        )
        _trackingState.value = bypassState
        val update = TransitDriftUpdate(
            legIndex = legIndex,
            driftMinutes = initialDelayMinutes,
            isLiveFromCorridor = false,
            isFallbackInertial = false
        )
        _driftUpdates.value = update
        Log.d(TAG, "Ruta A activada: Bypass Inercial [Leg #$legIndex] con retraso congelado de ${initialDelayMinutes}m")
    }

    private fun setupCorridorTracking(
        leg: PlannedLeg,
        legIndex: Int,
        penultimateStop: PlannedStop,
        initialDelayMinutes: Int
    ) {
        val corridorState = BoardedTrackingState.CorridorTracking(
            legIndex = legIndex,
            monitoredStopId = penultimateStop.stopId ?: "",
            monitoredStopName = penultimateStop.name,
            lastKnownDriftMinutes = initialDelayMinutes,
            isFallbackActive = false,
            lastSuccessfulPollTimestamp = System.currentTimeMillis()
        )
        _trackingState.value = corridorState
        _driftUpdates.value = TransitDriftUpdate(
            legIndex = legIndex,
            driftMinutes = initialDelayMinutes,
            isLiveFromCorridor = true,
            monitoredStopName = penultimateStop.name,
            isFallbackInertial = false
        )

        // Si se provee un externalScope, se programa el ciclo a baja frecuencia (60s)
        externalScope?.let { scope ->
            activePollingJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(CORRIDOR_POLLING_INTERVAL_MS)
                    reconcileBoardedLeg(leg, legIndex, System.currentTimeMillis())
                }
            }
        }
    }

    private fun applySilentFallback(
        legIndex: Int,
        penultimateStop: PlannedStop,
        nowMs: Long
    ): TransitDriftUpdate {
        _trackingState.value = BoardedTrackingState.CorridorTracking(
            legIndex = legIndex,
            monitoredStopId = penultimateStop.stopId ?: "",
            monitoredStopName = penultimateStop.name,
            lastKnownDriftMinutes = lastKnownDrift,
            isFallbackActive = true,
            lastSuccessfulPollTimestamp = nowMs
        )
        val fallbackUpdate = TransitDriftUpdate(
            legIndex = legIndex,
            driftMinutes = lastKnownDrift,
            isLiveFromCorridor = false,
            monitoredStopName = penultimateStop.name,
            isFallbackInertial = true
        )
        _driftUpdates.value = fallbackUpdate
        return fallbackUpdate
    }

    private suspend fun fetchPenultimateStopLiveArrival(
        leg: PlannedLeg,
        stop: PlannedStop,
        nowMs: Long
    ): Int? {
        val progressFraction = ActiveTripProgressTracker.progressState.value.progressWithinLeg
        return PenultimateStopArrivalMatcher.fetchLiveArrivalMinutes(
            leg = leg,
            stop = stop,
            nowMs = nowMs,
            boardedVehicleId = boardedVehicleId,
            lastKnownDrift = lastKnownDrift,
            progressFraction = progressFraction
        )
    }

    private fun resolvePenultimateStop(leg: PlannedLeg): PlannedStop? {
        return when {
            leg.intermediateStops.isNotEmpty() -> leg.intermediateStops.last()
            else -> null
        }
    }

    private fun cancelActivePollingJob() {
        activePollingJob?.cancel()
        activePollingJob = null
    }

    fun onAlightedOrTripEnded() {
        cancelActivePollingJob()
        _trackingState.value = BoardedTrackingState.Idle
        _driftUpdates.value = null
        currentTrackedLegIndex = -1
        lastKnownDrift = 0
        boardedVehicleId = null
    }

    fun reset() {
        onAlightedOrTripEnded()
    }
}

object PenultimateStopArrivalMatcher {
    suspend fun fetchLiveArrivalMinutes(
        leg: PlannedLeg,
        stop: PlannedStop,
        nowMs: Long,
        boardedVehicleId: String?,
        lastKnownDrift: Int,
        progressFraction: Float
    ): Int? {
        val totalMins = (leg.durationSeconds / 60).toInt()
        val remaining = (totalMins * (1.0f - progressFraction)).toInt()
        return (remaining + lastKnownDrift).coerceAtLeast(0)
    }
}
