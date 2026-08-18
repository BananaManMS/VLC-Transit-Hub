package com.example.util

import android.util.Log
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.network.NetworkModule
import com.example.data.repository.ActiveTripState
import com.example.data.repository.RealTimeTransitRepository
import com.example.data.repository.renfe.GtfsRtTripUpdate
import com.example.data.repository.routing.TransitIdMapper
import com.example.ui.bus.BusMapper
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Live reconciliation state holding vehicle real-time ETA, "Sal ya" triggers,
 * deviation calculations, checkpoint recovery, and live transfer monitoring (M_enlace).
 */
data class RealTimeTripStatus(
    val vehicleLine: String? = null,
    val vehicleDestination: String? = null,
    val vehicleArrivalMinutes: Int? = null,
    val vehicleSecondsRemaining: Int? = null,
    val delayMinutes: Int = 0,
    val isLive: Boolean = false,
    val isLeaveNowAlert: Boolean = false,
    val leaveNowMessageEs: String? = null,
    val leaveNowMessageCa: String? = null,
    val isOffRoute: Boolean = false,
    val offRouteDistanceMeters: Double = 0.0,
    val consecutiveOffRouteCount: Int = 0,
    val isPedestrianDeviated: Boolean = false,
    val dynamicWalkMinutesRemaining: Int? = null,
    // Checkpoint ETA & Destination Monitoring
    val checkpointEtaMinutes: Int? = null,
    val isCheckpointLive: Boolean = false,
    // Transfer Margin & Dynamic Multi-Leg Reconciliation
    val transferMarginMinutes: Int? = null,
    val isTransferAtRisk: Boolean = false,
    val transferWarningEs: String? = null,
    val transferWarningCa: String? = null,
    val upcomingTransferInfoEs: String? = null,
    val upcomingTransferInfoCa: String? = null,
    val isUpcomingTransferLive: Boolean = false,
    val upcomingTransferLine: String? = null,
    val upcomingTransferMinutes: Int? = null,
    val scheduledDepartureTime: String? = null,
    val adjustedDepartureTime: String? = null,
    val lastCheckedTimestamp: Long = 0L
)

/**
 * Engine responsible for 15-20s live polling across all transit modes (EMT Bus, Metrovalencia,
 * Renfe Cercanías, Metrobus), dynamic multi-leg transfer monitoring across time windows,
 * Checkpoint ETA, and "Sal ya" walking triggers.
 */
class TripRealTimeReconciler(
    private val client: OkHttpClient = NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    private var consecutiveOffRouteCount = 0
    private var maxAccumulatedDelayMinutes = 0
    private var currentTripStartTimestamp = 0L
    private val boardedDriftReconciler = BoardedDriftReconciler()
    private var lastBoardedLegIndex: Int = -1
    private var lastConfirmedBoardedMinsRemaining: Int? = null
    private var lastBoardedEstimationTimestamp: Long = 0L

    // GTFS-RT Cercanías Real-Time Destination Arrival Cache (Last Known State Retention)
    private var lastKnownCercaniasDestEpochSec: Long? = null
    private var lastKnownCercaniasDelayMinutes: Int = 0
    private var lastKnownCercaniasTripId: String? = null

    fun reset() {
        consecutiveOffRouteCount = 0
        maxAccumulatedDelayMinutes = 0
        currentTripStartTimestamp = 0L
        lastBoardedLegIndex = -1
        lastConfirmedBoardedMinsRemaining = null
        lastBoardedEstimationTimestamp = 0L
        lastKnownCercaniasDestEpochSec = null
        lastKnownCercaniasDelayMinutes = 0
        lastKnownCercaniasTripId = null
        boardedDriftReconciler.reset()
    }

    /**
     * Confirms user has boarded the transit vehicle, stops origin polling, and starts corridor tracking.
     */
    fun onBoardingConfirmed(leg: PlannedLeg, legIndex: Int, initialDepartureDelayMinutes: Int = maxAccumulatedDelayMinutes) {
        boardedDriftReconciler.onBoardingConfirmed(
            currentLeg = leg,
            currentLegIndex = legIndex,
            initialDepartureDelayMinutes = initialDepartureDelayMinutes
        )
    }

    /**
     * Reconciles current active trip state with live transport APIs and user GPS telemetry.
     */
    suspend fun reconcile(
        activeTrip: ActiveTripState,
        userLat: Double?,
        userLon: Double?
    ): RealTimeTripStatus = withContext(Dispatchers.IO) {
        if (activeTrip.startTimestamp != currentTripStartTimestamp) {
            currentTripStartTimestamp = activeTrip.startTimestamp
            maxAccumulatedDelayMinutes = 0
        }

        val legs = activeTrip.itinerary.legs
        val currentIdx = activeTrip.currentLegIndex

        if (legs.isEmpty() || currentIdx >= legs.size) {
            consecutiveOffRouteCount = 0
            return@withContext RealTimeTripStatus(delayMinutes = maxAccumulatedDelayMinutes)
        }

        val currentLeg = legs[currentIdx]
        val isCurrentWalk = currentLeg.mode == TransitMode.WALK

        // 1. Off-Route Detection & Target-Centric Pedestrian Tracking
        var isOffRoute = false
        var offRouteDist = 0.0
        var isPedestrianDeviated = false
        var dynamicWalkMinutesRemaining: Int? = null

        if (userLat != null && userLon != null) {
            val targetCoords = TripStepProgressionEngine.getLegTargetCoordinates(currentLeg)
            val distToTarget = if (targetCoords != null) {
                TripStepProgressionEngine.calculateDistanceMeters(userLat, userLon, targetCoords.first, targetCoords.second)
            } else Double.MAX_VALUE

            if (isCurrentWalk) {
                // Inmunidad de Desvío Peatonal: Forzamos isOffRoute = false de forma inmutable durante caminatas.
                // El usuario local conoce la permeabilidad de su barrio. La métrica crítica es la reducción hacia el objetivo.
                isOffRoute = false
                consecutiveOffRouteCount = 0

                // Medición de desvío respecto a la sugerencia visual de OSM
                offRouteDist = TripStepProgressionEngine.calculateDistanceToLegPolyline(userLat, userLon, currentLeg)
                isPedestrianDeviated = offRouteDist > TripStepProgressionEngine.PEDESTRIAN_DEVIATION_THRESHOLD_METERS

                // Recálculo Dinámico del ETA Peatonal (T_walk) priorizando estimación de Transitous por calles
                dynamicWalkMinutesRemaining = TripStepProgressionEngine.calculateDynamicWalkMinutes(distToTarget, currentLeg)
            } else {
                // Modos de transporte público (Bus, Metro, Tram, Cercanías)
                offRouteDist = TripStepProgressionEngine.calculateDistanceToLegPolyline(
                    userLat, userLon, currentLeg
                )
                val originCoords = TripStepProgressionEngine.getLegOriginCoordinates(currentLeg)
                val distToOrigin = if (originCoords != null) TripStepProgressionEngine.calculateDistanceMeters(userLat, userLon, originCoords.first, originCoords.second) else Double.MAX_VALUE

                val isNearStation = distToOrigin <= 220.0 || distToTarget <= 220.0
                val isTransitLeg = currentLeg.mode in listOf(TransitMode.SUBWAY, TransitMode.BUS, TransitMode.TRAM, TransitMode.RAIL)
                val threshold = if (isTransitLeg) 350.0 else TripStepProgressionEngine.OFF_ROUTE_THRESHOLD_METERS

                if (!isNearStation && offRouteDist > threshold) {
                    consecutiveOffRouteCount++
                    if (consecutiveOffRouteCount >= TripStepProgressionEngine.CONSECUTIVE_OFF_ROUTE_REQUIRED) {
                        isOffRoute = true
                    }
                } else {
                    consecutiveOffRouteCount = 0
                    isOffRoute = false
                }
            }
        }

        // 2. Identify target transit leg for current monitoring
        val nextTransitLeg = if (isCurrentWalk && currentIdx + 1 < legs.size) {
            legs[currentIdx + 1]
        } else if (!isCurrentWalk) {
            currentLeg
        } else {
            null
        }

        if (nextTransitLeg == null) {
            return@withContext RealTimeTripStatus(
                delayMinutes = maxAccumulatedDelayMinutes,
                isOffRoute = false, // Walk or destination reached: never off-route
                offRouteDistanceMeters = offRouteDist,
                consecutiveOffRouteCount = consecutiveOffRouteCount,
                isPedestrianDeviated = isPedestrianDeviated,
                dynamicWalkMinutesRemaining = dynamicWalkMinutesRemaining,
                lastCheckedTimestamp = System.currentTimeMillis()
            )
        }

        val nowMs = System.currentTimeMillis()
        val isAlreadyBoarded = !isCurrentWalk && ActiveTripProgressTracker.progressState.value.isBoarded

        // Grace Period is ONLY applicable if the scheduled departure time has already passed (up to 5 min ago)
        val scheduledStartMs = TripTimeParser.parseTimeToMillis(
            nextTransitLeg.scheduledStartTime ?: nextTransitLeg.formattedStartTime
        )
        val minsSinceScheduled = if (scheduledStartMs != null && nowMs >= scheduledStartMs) {
            ((nowMs - scheduledStartMs) / 60000L).toInt()
        } else {
            null
        }
        val isWithin5MinGracePeriod = minsSinceScheduled != null && minsSinceScheduled in 0..5
        val isNearStation = dynamicWalkMinutesRemaining != null && dynamicWalkMinutesRemaining <= 3

        val walkGraceMs = if (isAlreadyBoarded) {
            0L
        } else if (!isCurrentWalk || isWithin5MinGracePeriod || isNearStation) {
            // Platform waiting buffer: when the user is already on the transit leg or near the platform,
            // never add future walk delays and keep departures reachable
            -300_000L
        } else if (dynamicWalkMinutesRemaining != null) {
            // Dynamic walk with fast walking leeway (up to 2 min faster / GPS lag)
            ((dynamicWalkMinutesRemaining - 2.0).coerceAtLeast(0.0) * 60 * 1000L).toLong()
        } else {
            val walkSec = currentLeg.durationSeconds
            ((walkSec * 0.70).toLong() * 1000L)
        }
        val earliestReachableUserArrivalMs = nowMs + walkGraceMs

        var liveMinutes: Int? = null
        var liveSeconds: Int? = null
        var liveDestination: String? = null
        var isLive = false
        var delayMinutes = 0
        var scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null }
        var adjustedDepTime: String? = null
        var normalizedLine = TransitIdMapper.normalizeRouteShortName(nextTransitLeg.mode, nextTransitLeg.routeShortName)

        if (isAlreadyBoarded) {
            if (lastBoardedLegIndex != currentIdx) {
                lastBoardedLegIndex = currentIdx
                lastConfirmedBoardedMinsRemaining = null
                lastBoardedEstimationTimestamp = 0L
            }

            // Relevo de Polling: Bypassear polling de origen y reconciliar corredor / inercial
            val driftUpdate = boardedDriftReconciler.reconcileBoardedLeg(
                leg = nextTransitLeg,
                legIndex = currentIdx,
                nowMs = nowMs
            )
            delayMinutes = driftUpdate.driftMinutes
            isLive = driftUpdate.isLiveFromCorridor
            liveDestination = nextTransitLeg.toName

            // Prioridad Absoluta Renfe GTFS-RT con Retención del Último Estado Conocido
            var gtfsRtArrivalMinutes: Int? = null
            if (nextTransitLeg.mode == TransitMode.RAIL) {
                val toStopDigits = nextTransitLeg.toStopId?.filter { it.isDigit() }
                val cercaniasLine = TransitIdMapper.extractCercaniasLine(
                    nextTransitLeg.routeShortName,
                    nextTransitLeg.routeLongName,
                    nextTransitLeg.agencyName,
                    nextTransitLeg.mode
                ) ?: nextTransitLeg.routeShortName ?: ""
                val cleanLine = cercaniasLine.replace("-", "").uppercase()

                val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                val liveTripUpdate = tripUpdates.values.firstOrNull { update ->
                    val lineMatches = cleanLine.isNotBlank() && update.tripId.replace("-", "").uppercase().contains(cleanLine)
                    val stopMatches = !toStopDigits.isNullOrBlank() && (update.stopDelays.containsKey(toStopDigits) || update.stopEstimatedTimes.containsKey(toStopDigits))
                    lineMatches || stopMatches
                }

                if (liveTripUpdate != null) {
                    lastKnownCercaniasTripId = liveTripUpdate.tripId
                    val stopEpoch = if (!toStopDigits.isNullOrBlank()) liveTripUpdate.stopEstimatedTimes[toStopDigits] else null
                    if (stopEpoch != null && stopEpoch > 0) {
                        lastKnownCercaniasDestEpochSec = stopEpoch
                    }
                    val stopDelaySec = if (!toStopDigits.isNullOrBlank()) {
                        liveTripUpdate.stopDelays[toStopDigits] ?: liveTripUpdate.delaySeconds
                    } else {
                        liveTripUpdate.delaySeconds
                    }
                    lastKnownCercaniasDelayMinutes = stopDelaySec / 60
                    isLive = true
                    delayMinutes = lastKnownCercaniasDelayMinutes
                }

                // If we have live data OR last known state retained:
                val epochToUse = lastKnownCercaniasDestEpochSec
                if (epochToUse != null && epochToUse > 0) {
                    val remainingSec = ((epochToUse * 1000L) - nowMs) / 1000L
                    val mins = (remainingSec / 60L).toInt().coerceAtLeast(0)
                    gtfsRtArrivalMinutes = mins
                    // Even if feed temporarily drops, maintain isLive flag & last known delay
                    isLive = true
                    delayMinutes = lastKnownCercaniasDelayMinutes
                }
            }

            // Compute dynamic remaining minutes to destination stop while boarded
            val endMinsTheoretical = calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
                ?: calculateTheoreticalMinutesRemaining(nextTransitLeg.formattedEndTime)
            val legProgressFraction = com.example.util.ActiveTripProgressTracker.progressState.value.progressWithinLeg.coerceIn(0f, 1f)
            val totalMins = (nextTransitLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
            val rawGpsRemainingMins = (totalMins * (1f - legProgressFraction)).toInt().coerceAtLeast(1)
            val progressBasedMins = (rawGpsRemainingMins + delayMinutes.coerceAtLeast(0)).coerceAtLeast(1)

            val rawEstimatedMinutes = if (gtfsRtArrivalMinutes != null) {
                // GTFS-RT Oficial de Renfe prevalece estrictamente
                gtfsRtArrivalMinutes
            } else if (legProgressFraction >= 0.70f || rawGpsRemainingMins <= 3) {
                // High progress or close to disembarkation: GPS progress strictly overrides API drift
                rawGpsRemainingMins
            } else if (endMinsTheoretical != null) {
                val theoreticalWithDelay = (endMinsTheoretical + delayMinutes).coerceAtLeast(1)
                val discrepancy = kotlin.math.abs(theoreticalWithDelay - progressBasedMins)
                if (discrepancy <= 3) theoreticalWithDelay else progressBasedMins
            } else {
                progressBasedMins
            }

            // Anti-Jump & Monotonic Decay Temporal Cache:
            val calculatedMinutes = if (lastConfirmedBoardedMinsRemaining != null && lastBoardedEstimationTimestamp > 0L) {
                val elapsedSeconds = ((nowMs - lastBoardedEstimationTimestamp) / 1000L).coerceAtLeast(0L)
                val elapsedMinutes = (elapsedSeconds / 60L).toInt()
                val decayedPreviousMins = (lastConfirmedBoardedMinsRemaining!! - elapsedMinutes).coerceAtLeast(1)

                // If new reading suddenly spikes by > 2 minutes compared to decayedPreviousMins,
                // reject the spike as an API/sensor jump and preserve smooth monotonic progress!
                if (rawEstimatedMinutes > decayedPreviousMins + 2) {
                    android.util.Log.w(TAG, "Anomalous disembark ETA jump detected (from ${decayedPreviousMins}m to ${rawEstimatedMinutes}m). Clamping to ${decayedPreviousMins}m.")
                    decayedPreviousMins
                } else {
                    rawEstimatedMinutes
                }
            } else {
                rawEstimatedMinutes
            }

            lastConfirmedBoardedMinsRemaining = calculatedMinutes
            lastBoardedEstimationTimestamp = nowMs

            liveMinutes = calculatedMinutes
            liveSeconds = calculatedMinutes * 60

            // Departure time is strictly frozen when boarded!
            adjustedDepTime = nextTransitLeg.scheduledStartTime ?: nextTransitLeg.formattedStartTime.ifBlank { null }
        } else {
            // 3. Reconcile Current Transit Leg (Bus, Metro, Cercanías) en origen
            val legLiveResult = reconcileSingleTransitLeg(
                leg = nextTransitLeg,
                nowMs = nowMs,
                earliestReachableMs = earliestReachableUserArrivalMs
            )

            liveMinutes = legLiveResult.liveMinutes
            liveSeconds = legLiveResult.liveSeconds
            liveDestination = legLiveResult.liveDestination
            isLive = legLiveResult.isLive
            delayMinutes = legLiveResult.delayMinutes
            scheduledDepTime = nextTransitLeg.formattedStartTime.ifBlank { null }
            adjustedDepTime = legLiveResult.adjustedDepartureTime
            normalizedLine = legLiveResult.normalizedLine
        }

        // 4. Checkpoint ETA & Transfer Monitoring
        var checkpointEtaMinutes: Int? = null
        var isCheckpointLive = false

        if (isAlreadyBoarded && liveMinutes != null) {
            checkpointEtaMinutes = liveMinutes
            isCheckpointLive = isLive
        }

        // 5. Dynamic Multi-Leg Transfer Monitoring (Windowing & M_enlace)
        // Find if there is a subsequent public transit transfer leg after this one
        val targetTransitLegIndex = legs.indexOf(nextTransitLeg)
        var nextTransferTransitLeg: PlannedLeg? = null
        var walkTransferLeg: PlannedLeg? = null

        if (targetTransitLegIndex != -1 && targetTransitLegIndex + 1 < legs.size) {
            for (j in (targetTransitLegIndex + 1) until legs.size) {
                val candidate = legs[j]
                if (candidate.mode == TransitMode.WALK) {
                    if (walkTransferLeg == null) walkTransferLeg = candidate
                } else {
                    nextTransferTransitLeg = candidate
                    break
                }
            }
        }

        var transferMarginMinutes: Int? = null
        var isTransferAtRisk = false
        var transferWarningEs: String? = null
        var transferWarningCa: String? = null
        var upcomingTransferInfoEs: String? = null
        var upcomingTransferInfoCa: String? = null
        var isUpcomingTransferLive = false
        var upcomingTransferLine: String? = null
        var upcomingTransferMinutes: Int? = null

        if (nextTransferTransitLeg != null && isAlreadyBoarded) {
            val transferLineName = nextTransferTransitLeg.routeShortName?.ifBlank { null }
                ?: nextTransferTransitLeg.mode.displayNameEs

            val transferWalkMinutes = ((walkTransferLeg?.durationSeconds ?: 120L) / 60).toInt().coerceAtLeast(1)
            val currentLegRemainingMins = if (isAlreadyBoarded) {
                val theoreticalEnd = calculateTheoreticalMinutesRemaining(nextTransitLeg.endTime)
                    ?: calculateTheoreticalMinutesRemaining(nextTransitLeg.formattedEndTime)
                (theoreticalEnd ?: (nextTransitLeg.durationSeconds / 60).toInt()).coerceAtLeast(1)
            } else if (isCurrentWalk) {
                val walkToStartMins = dynamicWalkMinutesRemaining ?: ((currentLeg.durationSeconds / 60).toInt().coerceAtLeast(1))
                val waitAtStop = (liveMinutes ?: 0).coerceAtLeast(0)
                val legDurationMins = (nextTransitLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
                maxOf(walkToStartMins, waitAtStop) + legDurationMins
            } else if (isLive && liveMinutes != null) {
                liveMinutes + (nextTransitLeg.durationSeconds / 60).toInt()
            } else {
                (nextTransitLeg.durationSeconds / 60).toInt()
            }

            // Expected user arrival at the transfer boarding stop from NOW:
            val userMinutesUntilTransferBoarding = currentLegRemainingMins + transferWalkMinutes
            val userTransferArrivalEpochMs = nowMs + (userMinutesUntilTransferBoarding * 60 * 1000L)

            upcomingTransferLine = transferLineName

            // Check if transfer leg is within operational Real-Time window (< 60 minutes away)
            if (userMinutesUntilTransferBoarding <= 60) {
                val transferLiveResult = reconcileSingleTransitLeg(
                    leg = nextTransferTransitLeg,
                    nowMs = nowMs,
                    earliestReachableMs = userTransferArrivalEpochMs - 120_000L // 2 min leeway
                )

                if (transferLiveResult.isLive && transferLiveResult.liveMinutes != null) {
                    isUpcomingTransferLive = true
                    val transferVehicleMinsFromNow = transferLiveResult.liveMinutes
                    upcomingTransferMinutes = transferVehicleMinsFromNow

                    // Slack calculation: M_enlace = T_transfer_real - T_user_arrival
                    val margin = transferVehicleMinsFromNow - userMinutesUntilTransferBoarding
                    transferMarginMinutes = margin

                    val transferModeName = when (nextTransferTransitLeg.mode) {
                        TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                        TransitMode.RAIL -> "Cercanías"
                        TransitMode.BUS -> "Bus"
                        else -> nextTransferTransitLeg.mode.displayNameEs
                    }

                    val transferModeNameCa = when (nextTransferTransitLeg.mode) {
                        TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                        TransitMode.RAIL -> "Rodalia"
                        TransitMode.BUS -> "Bus"
                        else -> nextTransferTransitLeg.mode.displayNameCa
                    }

                    if (margin <= -3) {
                        if (userMinutesUntilTransferBoarding <= 5) {
                            isTransferAtRisk = true
                            transferWarningEs = "Posible transbordo perdido: Conexión con $transferModeName $transferLineName perdida por $margin min."
                            transferWarningCa = "Possible transbordament perdut: Connexió amb $transferModeNameCa $transferLineName perduda per $margin min."
                            upcomingTransferInfoEs = "Transbordo en riesgo: $transferModeName $transferLineName ($transferVehicleMinsFromNow min)"
                            upcomingTransferInfoCa = "Transbordament en risc: $transferModeNameCa $transferLineName ($transferVehicleMinsFromNow min)"
                        } else {
                            isTransferAtRisk = false
                            upcomingTransferInfoEs = "Próximo transbordo: $transferModeName $transferLineName en $transferVehicleMinsFromNow min (GPS)"
                            upcomingTransferInfoCa = "Pròxim transbordament: $transferModeNameCa $transferLineName en $transferVehicleMinsFromNow min (GPS)"
                        }
                    } else if (margin in -2..2) {
                        isTransferAtRisk = false
                        upcomingTransferInfoEs = "Transbordo ajustado: $transferModeName $transferLineName en $transferVehicleMinsFromNow min (GPS)"
                        upcomingTransferInfoCa = "Transbordament ajustat: $transferModeNameCa $transferLineName en $transferVehicleMinsFromNow min (GPS)"
                    } else {
                        isTransferAtRisk = false
                        upcomingTransferInfoEs = "Próximo transbordo: $transferModeName $transferLineName en $transferVehicleMinsFromNow min (en hora GPS)"
                        upcomingTransferInfoCa = "Pròxim transbordament: $transferModeNameCa $transferLineName en $transferVehicleMinsFromNow min (en hora GPS)"
                    }
                } else {
                    // Within 60 min but API returned scheduled arrival or no telemetry yet
                    val scheduledTime = nextTransferTransitLeg.formattedStartTime
                    val scheduledDepMs = TripTimeParser.parseTimeToMillis(nextTransferTransitLeg.startTime) ?: 0L
                    
                    val bufferMs = if (!isAlreadyBoarded && delayMinutes < 3) 5 * 60 * 1000L else 3 * 60 * 1000L
                    
                    if (scheduledDepMs > 0 && userTransferArrivalEpochMs > (scheduledDepMs + bufferMs) && userMinutesUntilTransferBoarding <= 5) {
                        isTransferAtRisk = true
                        val transferModeName = when (nextTransferTransitLeg.mode) {
                            TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                            TransitMode.RAIL -> "Cercanías"
                            TransitMode.BUS -> "Bus"
                            else -> nextTransferTransitLeg.mode.displayNameEs
                        }
                        val transferModeNameCa = when (nextTransferTransitLeg.mode) {
                            TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                            TransitMode.RAIL -> "Rodalia"
                            TransitMode.BUS -> "Bus"
                            else -> nextTransferTransitLeg.mode.displayNameCa
                        }
                        val causeEs = if (delayMinutes > 0) "Tu vehículo lleva retraso y" else "Según la hora estimada de llegada,"
                        val causeCa = if (delayMinutes > 0) "El teu transport porta retràs i" else "Segons l'hora estimada d'arribada,"
                        transferWarningEs = "Posible transbordo perdido: $causeEs la salida programada de $transferModeName $transferLineName ($scheduledTime) es inalcanzable."
                        transferWarningCa = "Possible transbordament perdut: $causeCa la eixida programada de $transferModeNameCa $transferLineName ($scheduledTime) és inabastable."
                        upcomingTransferInfoEs = "Transbordo en riesgo: $transferModeName $transferLineName a las $scheduledTime"
                        upcomingTransferInfoCa = "Transbordament en risc: $transferModeNameCa $transferLineName a les $scheduledTime"
                    } else if (scheduledDepMs > 0 && userTransferArrivalEpochMs > scheduledDepMs) {
                        isTransferAtRisk = false
                        val transferModeName = when (nextTransferTransitLeg.mode) {
                            TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                            TransitMode.RAIL -> "Cercanías"
                            TransitMode.BUS -> "Bus"
                            else -> nextTransferTransitLeg.mode.displayNameEs
                        }
                        val transferModeNameCa = when (nextTransferTransitLeg.mode) {
                            TransitMode.SUBWAY, TransitMode.TRAM -> "Metro"
                            TransitMode.RAIL -> "Rodalia"
                            TransitMode.BUS -> "Bus"
                            else -> nextTransferTransitLeg.mode.displayNameCa
                        }
                        transferWarningEs = "Transbordo ajustado: Conexión con $transferModeName $transferLineName a las $scheduledTime muy justa."
                        transferWarningCa = "Transbordament ajustat: Connexió amb $transferModeNameCa $transferLineName a les $scheduledTime molt justa."
                        upcomingTransferInfoEs = "Transbordo ajustado: $transferModeName $transferLineName a las $scheduledTime"
                        upcomingTransferInfoCa = "Transbordament ajustat: $transferModeNameCa $transferLineName a les $scheduledTime"
                    } else {
                        isTransferAtRisk = false
                        upcomingTransferInfoEs = "Próximo transbordo: $transferLineName a las $scheduledTime • Programado"
                        upcomingTransferInfoCa = "Pròxim transbordament: $transferLineName a las $scheduledTime • Programat"
                    }
                }
            } else {
                // Outside real-time window (e.g. 40-50 min in the future) -> Show official scheduled time
                val scheduledTime = nextTransferTransitLeg.formattedStartTime
                isUpcomingTransferLive = false
                upcomingTransferInfoEs = "Próximo transbordo: $transferLineName a las $scheduledTime • Programado"
                upcomingTransferInfoCa = "Pròxim transbordament: $transferLineName a les $scheduledTime • Programat"
            }
        }

        // 6. Evaluate "Sal ya" Rule (Only trigger if user has NOT already started walking towards the stop)
        var isLeaveNow = false
        var leaveNowEs: String? = null
        var leaveNowCa: String? = null

        if (isCurrentWalk && liveMinutes != null) {
            val legProgressFraction = com.example.util.ActiveTripProgressTracker.progressState.value.progressWithinLeg
            val originCoords = TripStepProgressionEngine.getLegOriginCoordinates(currentLeg)
            val distFromStartMeters = if (userLat != null && userLon != null && originCoords != null) {
                LocationUtils.calculateDistanceMeters(userLat, userLon, originCoords.first, originCoords.second)
            } else 0.0

            // If user has already progressed >12% or moved >40m away from start point, they are ALREADY WALKING
            val isUserAlreadyWalking = legProgressFraction > 0.12f || distFromStartMeters > 40.0

            if (!isUserAlreadyWalking) {
                val plannedWalkMinutes = (currentLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
                val walkMinutesRemaining: Int = if (userLat != null && userLon != null) {
                    val targetCoords = TripStepProgressionEngine.getLegTargetCoordinates(currentLeg)
                        ?: TripStepProgressionEngine.getLegOriginCoordinates(nextTransitLeg)
                    val totalLegDist = currentLeg.distanceMeters.takeIf { it > 0 } ?: 1.0
                    val remainingDist = if (targetCoords != null) {
                        LocationUtils.calculateDistanceMeters(userLat, userLon, targetCoords.first, targetCoords.second)
                    } else totalLegDist
                    val fraction = (remainingDist / totalLegDist).coerceIn(0.1, 1.0)
                    ceil(plannedWalkMinutes * fraction).toInt().coerceAtLeast(1)
                } else {
                    plannedWalkMinutes
                }

                val marginMinutes = (liveMinutes ?: 0) - walkMinutesRemaining
                val lineDisplay = nextTransitLeg.mode.displayNameEs + (if (normalizedLine.isNotEmpty()) " $normalizedLine" else "")
                val lineDisplayCa = nextTransitLeg.mode.displayNameCa + (if (normalizedLine.isNotEmpty()) " $normalizedLine" else "")

                if (marginMinutes in 0..3) {
                    isLeaveNow = true
                    leaveNowEs = "¡Sal ya! $lineDisplay en $liveMinutes min (caminata $walkMinutesRemaining min)"
                    leaveNowCa = "¡Ix ja! $lineDisplayCa en $liveMinutes min (caminada $walkMinutesRemaining min)"
                } else if (marginMinutes < 0 && (liveMinutes ?: 0) >= 0) {
                    isLeaveNow = true
                    leaveNowEs = "¡Acelera! $lineDisplay en $liveMinutes min (caminata $walkMinutesRemaining min)"
                    leaveNowCa = "¡Accelera! $lineDisplayCa en $liveMinutes min (caminada $walkMinutesRemaining min)"
                }
            }
        }

        if (!isCurrentWalk) {
            val currentProgressInfo = ActiveTripProgressTracker.progressState.value

            if (!currentProgressInfo.isBoarded) {
                ActiveTripProgressTracker.updateProgress(
                    progressWithinLeg = currentProgressInfo.progressWithinLeg,
                    waitTimeMessage = currentProgressInfo.waitTimeMessage,
                    statusDetail = currentProgressInfo.statusDetail,
                    isDeadReckoning = currentProgressInfo.isDeadReckoning,
                    isBoarded = false,
                    transitDepartureTimeMs = currentProgressInfo.transitDepartureTimeMs,
                    lastSeenArrivalMins = liveMinutes,
                    legIndex = currentIdx
                )
            } else {
                boardedDriftReconciler.onBoardingConfirmed(
                    currentLeg = nextTransitLeg,
                    currentLegIndex = currentIdx,
                    initialDepartureDelayMinutes = delayMinutes
                )
            }
        }

        if (delayMinutes > maxAccumulatedDelayMinutes) {
            maxAccumulatedDelayMinutes = delayMinutes
        }
        val effectiveDelay = maxOf(delayMinutes, maxAccumulatedDelayMinutes)

        RealTimeTripStatus(
            vehicleLine = normalizedLine.ifBlank { nextTransitLeg.routeShortName },
            vehicleDestination = liveDestination,
            vehicleArrivalMinutes = liveMinutes,
            vehicleSecondsRemaining = liveSeconds,
            delayMinutes = effectiveDelay,
            isLive = isLive,
            isLeaveNowAlert = isLeaveNow,
            leaveNowMessageEs = leaveNowEs,
            leaveNowMessageCa = leaveNowCa,
            isOffRoute = isOffRoute,
            offRouteDistanceMeters = offRouteDist,
            consecutiveOffRouteCount = consecutiveOffRouteCount,
            isPedestrianDeviated = isPedestrianDeviated,
            dynamicWalkMinutesRemaining = dynamicWalkMinutesRemaining,
            checkpointEtaMinutes = checkpointEtaMinutes,
            isCheckpointLive = isCheckpointLive,
            transferMarginMinutes = transferMarginMinutes,
            isTransferAtRisk = isTransferAtRisk,
            transferWarningEs = transferWarningEs,
            transferWarningCa = transferWarningCa,
            upcomingTransferInfoEs = upcomingTransferInfoEs,
            upcomingTransferInfoCa = upcomingTransferInfoCa,
            isUpcomingTransferLive = isUpcomingTransferLive,
            upcomingTransferLine = upcomingTransferLine,
            upcomingTransferMinutes = upcomingTransferMinutes,
            scheduledDepartureTime = scheduledDepTime,
            adjustedDepartureTime = adjustedDepTime,
            lastCheckedTimestamp = System.currentTimeMillis()
        )
    }

    private data class LegReconciliationResult(
        val normalizedLine: String,
        val liveMinutes: Int?,
        val liveSeconds: Int?,
        val liveDestination: String?,
        val isLive: Boolean,
        val delayMinutes: Int,
        val adjustedDepartureTime: String?
    )

    private suspend fun reconcileSingleTransitLeg(
        leg: PlannedLeg,
        nowMs: Long,
        earliestReachableMs: Long
    ): LegReconciliationResult {
        val normalizedLine = TransitIdMapper.normalizeRouteShortName(leg.mode, leg.routeShortName)
        val rawLine = leg.routeShortName ?: normalizedLine

        var liveMinutes: Int? = null
        var liveSeconds: Int? = null
        var liveDestination: String? = null
        var isLive = false
        var delayMinutes = 0
        var adjustedDepTime: String? = null

        val theoreticalMinutesRemaining = calculateTheoreticalMinutesRemaining(leg.startTime)
            ?: calculateTheoreticalMinutesRemaining(leg.formattedStartTime)

        when (leg.mode) {
            TransitMode.BUS -> {
                val isEmt = TransitIdMapper.isEmtBus(
                    agencyName = leg.agencyName,
                    routeShortName = leg.routeShortName,
                    routeLongName = leg.routeLongName,
                    fromStopId = leg.fromStopId,
                    fromName = leg.fromName
                )
                if (isEmt) {
                    val stopNumber = TransitIdMapper.extractEmtStopNumber(leg.fromStopId, leg.fromName)
                    if (!stopNumber.isNullOrBlank()) {
                        val arrivals = fetchEmtArrivals(stopNumber)
                    val matchingArrivals = arrivals.filter { arr ->
                        TransitIdMapper.isSameEmtLine(arr.line, rawLine) ||
                        TransitIdMapper.isSameEmtLine(arr.line, normalizedLine)
                    }

                    val destMatches = matchingArrivals.filter { isDestinationMatch(it.destination, leg) }
                    val candidateArrivals = destMatches

                    if (candidateArrivals.isNotEmpty()) {
                        val maxHorizonMinutes = candidateArrivals.maxOfOrNull { it.minutes } ?: 0
                        val maxLiveHorizonMs = nowMs + (maxHorizonMinutes * 60 * 1000L) + (3 * 60 * 1000L)

                        val validCandidates = candidateArrivals.filter { arr ->
                            val liveArrivalMs = nowMs + (arr.minutes * 60 * 1000L)
                            liveArrivalMs >= earliestReachableMs
                        }

                        val bestArrivalCandidate = if (validCandidates.isNotEmpty()) {
                            val matched = if (theoreticalMinutesRemaining != null) {
                                validCandidates.minByOrNull { arr ->
                                    val diff = arr.minutes - theoreticalMinutesRemaining
                                    if (diff >= -2) diff else (Math.abs(diff) + 50)
                                }
                            } else {
                                validCandidates.minByOrNull { it.minutes }
                            }
                            if (matched != null) {
                                val delayM = if (theoreticalMinutesRemaining != null) {
                                    (matched.minutes - theoreticalMinutesRemaining).coerceAtLeast(0)
                                } else 0
                                Triple(matched, matched.minutes, delayM)
                            } else null
                        } else null

                        if (bestArrivalCandidate != null) {
                            val (targetArrival, mins, delayM) = bestArrivalCandidate
                            liveMinutes = mins
                            liveSeconds = mins * 60
                            liveDestination = targetArrival.destination
                            isLive = true
                            delayMinutes = delayM
                        }
                    }
                }
            }
            }
            TransitMode.SUBWAY, TransitMode.TRAM -> {
                val stationIdInt = TransitIdMapper.extractMetroStationId(leg.fromStopId, leg.fromName)
                val stationId = stationIdInt?.toString() ?: leg.fromStopId?.filter { it.isDigit() }

                if (!stationId.isNullOrBlank()) {
                    val departures = fetchMetroDepartures(stationId)
                    val matchingDepartures = departures.filter { dep ->
                        val digits = normalizedLine.filter { it.isDigit() }
                        if (digits.isBlank()) {
                            true
                        } else {
                            dep.lineId.equals(normalizedLine, ignoreCase = true) ||
                            dep.lineId.filter { it.isDigit() } == digits
                        }
                    }

                    // Strictly require departures going in the correct direction (matching headsign / destination stop name)
                    val destMatches = matchingDepartures.filter { isDestinationMatch(it.destination, leg) }
                    val candidateDepartures = destMatches

                    if (candidateDepartures.isNotEmpty()) {
                        val maxHorizonSeconds = candidateDepartures.maxOfOrNull { it.seconds } ?: 0
                        val maxLiveHorizonMs = nowMs + (maxHorizonSeconds * 1000L) + (3 * 60 * 1000L)

                        val validCandidates = candidateDepartures.filter { dep ->
                            val liveArrivalMs = nowMs + (dep.seconds * 1000L)
                            liveArrivalMs >= earliestReachableMs
                        }

                        val bestDepCandidate = if (validCandidates.isNotEmpty()) {
                            val matched = if (theoreticalMinutesRemaining != null) {
                                validCandidates.minByOrNull { dep ->
                                    val diff = dep.minutes - theoreticalMinutesRemaining
                                    if (diff >= -2) diff else (Math.abs(diff) + 50)
                                }
                            } else {
                                validCandidates.minByOrNull { it.seconds }
                            }
                            if (matched != null) {
                                val delayM = if (theoreticalMinutesRemaining != null) {
                                    (matched.minutes - theoreticalMinutesRemaining).coerceAtLeast(0)
                                } else 0
                                Triple(matched, matched.minutes, delayM)
                            } else null
                        } else null

                        if (bestDepCandidate != null) {
                            val (targetDeparture, mins, delayM) = bestDepCandidate
                            liveMinutes = mins
                            liveSeconds = mins * 60
                            liveDestination = targetDeparture.destination
                            isLive = true
                            delayMinutes = delayM
                        }
                    }
                }
            }
            TransitMode.RAIL -> {
                // Renfe Cercanías GTFS-RT Matching
                val cercaniasLine = TransitIdMapper.extractCercaniasLine(leg.routeShortName, leg.routeLongName, leg.agencyName, leg.mode)
                    ?: leg.routeShortName ?: ""

                val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                val livePositions = RealTimeTransitRepository.getCercaniasLivePositions()
                val stopIdDigits = leg.fromStopId?.filter { it.isDigit() }

                // Find matching live updates for this Cercanias line or stop
                if (tripUpdates.isNotEmpty() || livePositions.isNotEmpty()) {
                    val matchingTripUpdate = tripUpdates.values.firstOrNull { update ->
                        val tripId = update.tripId
                        val lineMatches = cercaniasLine.isNotBlank() && tripId.contains(cercaniasLine.replace("-", ""), ignoreCase = true)
                        val stopMatches = !stopIdDigits.isNullOrBlank() && (update.stopDelays.containsKey(stopIdDigits) || update.stopEstimatedTimes.containsKey(stopIdDigits))
                        lineMatches || stopMatches
                    }

                    val matchedDelaySec = if (matchingTripUpdate != null && !stopIdDigits.isNullOrBlank()) {
                        matchingTripUpdate.stopDelays[stopIdDigits] ?: matchingTripUpdate.delaySeconds
                    } else {
                        matchingTripUpdate?.delaySeconds
                    }

                    if (matchingTripUpdate != null && matchedDelaySec != null) {
                        val delayM = matchedDelaySec / 60
                        delayMinutes = delayM
                        isLive = true
                        
                        val estimatedStopEpochSec = if (!stopIdDigits.isNullOrBlank()) matchingTripUpdate.stopEstimatedTimes[stopIdDigits] else null
                        if (estimatedStopEpochSec != null && estimatedStopEpochSec > 0) {
                            val liveMinsRemaining = ((estimatedStopEpochSec * 1000L - nowMs) / 60000L).toInt().coerceAtLeast(0)
                            liveMinutes = liveMinsRemaining
                            liveSeconds = liveMinsRemaining * 60
                        } else if (theoreticalMinutesRemaining != null) {
                            liveMinutes = (theoreticalMinutesRemaining + delayM).coerceAtLeast(0)
                            liveSeconds = liveMinutes * 60
                        }
                    } else if (theoreticalMinutesRemaining != null && theoreticalMinutesRemaining in 0..25) {
                        // In operational active window
                        liveMinutes = theoreticalMinutesRemaining
                        liveSeconds = liveMinutes * 60
                    }
                }
            }
            else -> {
                // Fallback for bicycle, etc.
            }
        }

        if (isLive && liveMinutes != null) {
            val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
            cal.add(Calendar.MINUTE, liveMinutes)
            adjustedDepTime = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        return LegReconciliationResult(
            normalizedLine = normalizedLine,
            liveMinutes = liveMinutes,
            liveSeconds = liveSeconds,
            liveDestination = liveDestination,
            isLive = isLive,
            delayMinutes = delayMinutes,
            adjustedDepartureTime = adjustedDepTime
        )
    }

    private fun calculateTheoreticalMinutesRemaining(scheduledIsoOrTime: String?): Int? {
        if (scheduledIsoOrTime.isNullOrBlank()) return null
        return try {
            val parsedTime = TripTimeParser.parseTimeToMillis(scheduledIsoOrTime) ?: return null
            val nowMs = System.currentTimeMillis()
            val diffMs = parsedTime - nowMs
            (diffMs / 60000L).toInt()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchEmtArrivals(stopNumber: String): List<EmtArrivalParsed> {
        val liveArrivals = RealTimeTransitRepository.getEmtLiveArrivals(stopNumber, useFastTimeout = true)
        return liveArrivals.mapNotNull { time ->
            val mins = when {
                time.minutos.contains(":") -> {
                    val parts = time.minutos.split(":")
                    val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
                    cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                    cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                    cal.set(Calendar.SECOND, 0)
                    var diffMs = cal.timeInMillis - System.currentTimeMillis()
                    if (diffMs < -12 * 3600 * 1000L) diffMs += 24 * 3600 * 1000L
                    ((diffMs / 60000L).toInt()).coerceAtLeast(1)
                }
                time.minutos.startsWith("pr", ignoreCase = true) -> 1
                else -> time.minutos.filter { it.isDigit() }.toIntOrNull() ?: (if (time.secondsRemaining > 0) time.secondsRemaining / 60 else null)
            }

            if (mins != null && time.linea.isNotBlank()) {
                EmtArrivalParsed(
                    line = time.linea,
                    destination = time.destino,
                    minutes = mins
                )
            } else null
        }
    }

    private suspend fun fetchMetroDepartures(stationId: String): List<MetroDepartureParsed> {
        val departures = RealTimeTransitRepository.getMetroLiveArrivals(stationId)
        return departures.map { dep ->
            MetroDepartureParsed(
                lineId = dep.line,
                destination = dep.destination,
                minutes = dep.minutes,
                seconds = dep.seconds
            )
        }.sortedBy { it.seconds }
    }

    private fun isDestinationMatch(depDestination: String, leg: PlannedLeg): Boolean {
        return TransitIdMapper.isDestinationMatch(depDestination, leg)
    }

    private data class EmtArrivalParsed(val line: String, val destination: String, val minutes: Int)
    private data class MetroDepartureParsed(val lineId: String, val destination: String, val minutes: Int, val seconds: Int)

    companion object {
        private const val TAG = "TripRealTimeReconciler"

        fun syncRealTimeItinerary(
            itinerary: com.example.data.model.routing.PlannedItinerary,
            status: RealTimeTripStatus,
            currentLegIndex: Int
        ): com.example.data.model.routing.PlannedItinerary {
            val legs = itinerary.legs
            if (legs.isEmpty() || currentLegIndex !in legs.indices) return itinerary

            val currentLeg = legs[currentLegIndex]

            // Find target transit leg index
            val targetTransitIdx = if (currentLeg.mode != TransitMode.WALK) {
                currentLegIndex
            } else if (currentLegIndex + 1 < legs.size && legs[currentLegIndex + 1].mode != TransitMode.WALK) {
                currentLegIndex + 1
            } else {
                null
            }

            val updatedLegs = legs.toMutableList()
            var modified = false

            val isBoarded = com.example.util.ActiveTripProgressTracker.progressState.value.isBoarded

            if (targetTransitIdx != null && targetTransitIdx in updatedLegs.indices) {
                val targetLeg = updatedLegs[targetTransitIdx]
                val origStart = targetLeg.scheduledStartTime ?: targetLeg.formattedStartTime
                val origEnd = targetLeg.scheduledEndTime ?: targetLeg.formattedEndTime

                // Inmutabilidad Absoluta de la salida al estar embarcado:
                val newStart = if (isBoarded) {
                    origStart
                } else {
                    status.adjustedDepartureTime ?: if (status.delayMinutes != 0) {
                        TripTimeParser.shiftFormattedTime(origStart, status.delayMinutes)
                    } else origStart
                }

                val startMs = TripTimeParser.parseTimeToMillis(origStart)
                val newStartMs = TripTimeParser.parseTimeToMillis(newStart)
                val effectiveDelayMins = if (isBoarded) {
                    0 // No departure delay adjustment when boarded; departure is strictly in the past
                } else if (startMs != null && newStartMs != null) {
                    ((newStartMs - startMs) / 60000L).toInt()
                } else {
                    status.delayMinutes
                }

                // ETA dinámico de llegada al estar a bordo:
                val newEnd = if (isBoarded && status.vehicleArrivalMinutes != null && status.vehicleArrivalMinutes > 0) {
                    TripTimeParser.addMinutesToNow(status.vehicleArrivalMinutes)
                } else if (effectiveDelayMins != 0) {
                    TripTimeParser.shiftFormattedTime(origEnd, effectiveDelayMins)
                } else origEnd

                val liveEndMs = TripTimeParser.parseTimeToMillis(newEnd)
                val origEndMs = TripTimeParser.parseTimeToMillis(origEnd)
                val boardedDelayMins = if (liveEndMs != null && origEndMs != null) {
                    ((liveEndMs - origEndMs) / 60000L).toInt()
                } else {
                    status.delayMinutes
                }
                val intermediateDelayMins = if (isBoarded) boardedDelayMins else effectiveDelayMins

                val updatedStops = targetLeg.intermediateStops.map { stop ->
                    val origStopSched = stop.scheduledTime ?: stop.formattedTime
                    val newStopFormatted = if (intermediateDelayMins != 0 && origStopSched != null) {
                        TripTimeParser.shiftFormattedTime(origStopSched, intermediateDelayMins)
                    } else origStopSched ?: stop.formattedTime

                    stop.copy(
                        scheduledTime = origStopSched,
                        formattedTime = newStopFormatted
                    )
                }

                val newTargetLeg = targetLeg.copy(
                    isRealTimeVerified = status.isLive,
                    realTimeDelayMinutes = effectiveDelayMins,
                    scheduledStartTime = origStart,
                    scheduledEndTime = origEnd,
                    formattedStartTime = newStart,
                    formattedEndTime = newEnd,
                    intermediateStops = updatedStops
                )

                if (newTargetLeg != targetLeg) {
                    updatedLegs[targetTransitIdx] = newTargetLeg
                    modified = true
                }

                // Adjust walk leg prior to transit leg ONLY IF NOT BOARDED
                if (!isBoarded && targetTransitIdx > 0 && updatedLegs[targetTransitIdx - 1].mode == TransitMode.WALK) {
                    val priorWalk = updatedLegs[targetTransitIdx - 1]
                    val walkMins = (priorWalk.durationSeconds / 60).toInt().coerceAtLeast(1)
                    val newWalkStart = TripTimeParser.shiftFormattedTime(newStart, -walkMins)
                    val newWalkEnd = newStart

                    val newPriorWalk = priorWalk.copy(
                        formattedStartTime = newWalkStart,
                        formattedEndTime = newWalkEnd
                    )

                    if (newPriorWalk != priorWalk) {
                        updatedLegs[targetTransitIdx - 1] = newPriorWalk
                        modified = true
                    }
                }
            }

            // Also mark upcoming transfer transit leg with live real-time status if present
            if (status.isUpcomingTransferLive) {
                val startIdx = (targetTransitIdx ?: currentLegIndex) + 1
                val transferTransitIdx = (startIdx until updatedLegs.size).firstOrNull { updatedLegs[it].mode != TransitMode.WALK }
                if (transferTransitIdx != null) {
                    val transferLeg = updatedLegs[transferTransitIdx]
                    val updatedTransferLeg = transferLeg.copy(isRealTimeVerified = true)
                    if (updatedTransferLeg != transferLeg) {
                        updatedLegs[transferTransitIdx] = updatedTransferLeg
                        modified = true
                    }
                }
            }

            // Sync overall itinerary departure time, arrival time, and total duration
            val firstLegStart = updatedLegs.first().formattedStartTime
            val lastLegEnd = updatedLegs.last().formattedEndTime

            val startMs = TripTimeParser.parseTimeToMillis(firstLegStart)
            val endMs = TripTimeParser.parseTimeToMillis(lastLegEnd)
            val durationSecs = if (startMs != null && endMs != null && endMs >= startMs) {
                ((endMs - startMs) / 1000L).coerceAtLeast(60L)
            } else {
                updatedLegs.sumOf { it.durationSeconds }
            }

            val durMins = (durationSecs / 60).coerceAtLeast(1).toInt()
            val formattedDur = if (durMins >= 60) {
                val h = durMins / 60
                val m = durMins % 60
                if (m == 0) "$h h" else "$h h $m min"
            } else {
                "$durMins min"
            }

            if (!modified &&
                itinerary.formattedDepartureTime == firstLegStart &&
                itinerary.formattedArrivalTime == lastLegEnd &&
                itinerary.formattedDuration == formattedDur
            ) {
                return itinerary
            }

            return itinerary.copy(
                legs = updatedLegs,
                formattedDepartureTime = firstLegStart,
                formattedArrivalTime = lastLegEnd,
                formattedDuration = formattedDur,
                totalDurationSeconds = durationSecs
            )
        }
    }
}
