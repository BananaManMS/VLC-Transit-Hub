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
        initialDepartureDelayMinutes: Int
    ) {
        currentTrackedLegIndex = currentLegIndex
        lastKnownDrift = initialDepartureDelayMinutes

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
                val theoreticalRemaining = calculateTheoreticalMinutesRemaining(
                    penultimateStop.scheduledTime ?: penultimateStop.formattedTime ?: leg.endTime,
                    nowMs
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
        val stopId = stop.stopId ?: return null
        val normalizedLine = TransitIdMapper.normalizeRouteShortName(leg.mode, leg.routeShortName)
        val rawLine = leg.routeShortName ?: normalizedLine

        // Expected arrival at penultimate stop based on schedule or progress
        val theoreticalPenultRemaining = calculateTheoreticalMinutesRemaining(
            stop.scheduledTime ?: stop.formattedTime ?: leg.endTime,
            nowMs
        ) ?: (leg.durationSeconds / 60).toInt().coerceAtLeast(1)

        val expectedPenultMinutes = (theoreticalPenultRemaining + lastKnownDrift).coerceAtLeast(0)

        return when (leg.mode) {
            TransitMode.BUS -> {
                val isEmt = TransitIdMapper.isEmtBus(
                    agencyName = leg.agencyName,
                    routeShortName = leg.routeShortName,
                    routeLongName = leg.routeLongName,
                    fromStopId = stopId,
                    fromName = stop.name
                )
                if (!isEmt) return null
                val emtStopNum = TransitIdMapper.extractEmtStopNumber(stopId, stop.name) ?: stopId
                val arrivals = RealTimeTransitRepository.getEmtLiveArrivals(emtStopNum, useFastTimeout = true)
                val lineArrivals = arrivals.filter { arr ->
                    TransitIdMapper.isSameEmtLine(arr.linea, rawLine) ||
                    TransitIdMapper.isSameEmtLine(arr.linea, normalizedLine)
                }
                val destArrivals = lineArrivals.filter { isDestinationMatch(it.destino, leg) }
                
                // Vehicle Correlation: Only match vehicles within +-4 min of expected arrival at this stop
                val matching = destArrivals.mapNotNull { arr ->
                    val mins = arr.minutos.filter { it.isDigit() }.toIntOrNull()
                        ?: if (arr.secondsRemaining > 0) arr.secondsRemaining / 60 else null
                    if (mins != null) Pair(arr, mins) else null
                }.filter { (_, mins) ->
                    kotlin.math.abs(mins - expectedPenultMinutes) <= 4
                }.minByOrNull { (_, mins) ->
                    kotlin.math.abs(mins - expectedPenultMinutes)
                }

                matching?.second
            }
            TransitMode.SUBWAY, TransitMode.TRAM -> {
                val stationId = TransitIdMapper.extractMetroStationId(stopId, stop.name)?.toString()
                    ?: stopId.filter { it.isDigit() }
                if (stationId.isNotBlank()) {
                    val arrivals = RealTimeTransitRepository.getMetroLiveArrivals(stationId)
                    val digits = normalizedLine.filter { it.isDigit() }
                    val lineArrivals = arrivals.filter { dep ->
                        if (digits.isBlank()) true
                        else dep.line.equals(normalizedLine, ignoreCase = true) || dep.line.filter { it.isDigit() } == digits
                    }
                    val destArrivals = lineArrivals.filter { isDestinationMatch(it.destination, leg) }
                    
                    // Vehicle Correlation: Only match trains within +-4 min of expected arrival at this stop
                    val matching = destArrivals.filter { dep ->
                        kotlin.math.abs(dep.minutes - expectedPenultMinutes) <= 4
                    }.minByOrNull { dep ->
                        kotlin.math.abs(dep.minutes - expectedPenultMinutes)
                    }

                    matching?.minutes
                } else null
            }
            TransitMode.RAIL -> {
                val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                val stopDigits = stopId.filter { it.isDigit() }
                val cercaniasLine = TransitIdMapper.extractCercaniasLine(leg.routeShortName, leg.routeLongName, leg.agencyName, leg.mode)
                    ?: leg.routeShortName ?: ""
                val cleanLine = cercaniasLine.replace("-", "").uppercase()

                val update = tripUpdates.values.firstOrNull { u ->
                    val lineMatches = cleanLine.isNotBlank() && u.tripId.replace("-", "").uppercase().contains(cleanLine)
                    val stopMatches = stopDigits.isNotBlank() && (u.stopDelays.containsKey(stopDigits) || u.stopEstimatedTimes.containsKey(stopDigits))
                    lineMatches || stopMatches
                }

                if (update != null) {
                    val stopEpoch = if (stopDigits.isNotBlank()) update.stopEstimatedTimes[stopDigits] else null
                    if (stopEpoch != null && stopEpoch > 0) {
                        val remainingSec = ((stopEpoch * 1000L) - nowMs) / 1000L
                        (remainingSec / 60L).toInt().coerceAtLeast(0)
                    } else {
                        val delaySec = if (stopDigits.isNotBlank()) update.stopDelays[stopDigits] ?: update.delaySeconds else update.delaySeconds
                        val delayM = delaySec / 60
                        (expectedPenultMinutes + delayM).coerceAtLeast(0)
                    }
                } else null
            }
            else -> null
        }
    }

    private fun isDestinationMatch(depDestination: String, leg: PlannedLeg): Boolean {
        return com.example.data.repository.routing.TransitIdMapper.isDestinationMatch(depDestination, leg)
    }

    private fun resolvePenultimateStop(leg: PlannedLeg): PlannedStop? {
        return when {
            leg.intermediateStops.isNotEmpty() -> leg.intermediateStops.last()
            else -> null
        }
    }

    private fun calculateTheoreticalMinutesRemaining(timeStr: String?, nowMs: Long): Int? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            if (timeStr.contains("T")) {
                val cleanIso = timeStr.substringBefore("Z").substringBefore("+")
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(cleanIso)?.time
                parsed?.let { ((it - nowMs) / 60000L).toInt() }
            } else if (timeStr.contains(":")) {
                val parts = timeStr.split(":")
                val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
                cal.set(Calendar.HOUR_OF_DAY, parts[0].trim().toInt())
                cal.set(Calendar.MINUTE, parts[1].trim().toInt())
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                var diff = cal.timeInMillis - nowMs
                if (diff < -12 * 3600 * 1000L) diff += 24 * 3600 * 1000L
                else if (diff > 12 * 3600 * 1000L) diff -= 24 * 3600 * 1000L
                ((diff) / 60000L).toInt()
            } else {
                null
            }
        } catch (_: Exception) {
            null
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
    }

    fun reset() {
        onAlightedOrTripEnded()
    }
}
