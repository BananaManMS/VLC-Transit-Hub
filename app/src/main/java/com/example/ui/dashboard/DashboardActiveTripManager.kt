package com.example.ui.dashboard

import android.app.Application
import android.util.Log
import com.example.data.database.ActiveTripEntity
import com.example.data.database.AppDatabase
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.TransitMode
import com.example.data.model.trip.UnifiedActiveTripSnapshot
import com.example.data.repository.ActiveTripRepository
import com.example.data.repository.ActiveTripState
import com.example.data.repository.MetroAlertsRepository
import com.example.data.repository.routing.HybridRoutingRepository
import com.example.service.ActiveTripTrackingService
import com.example.util.ActiveTripProgressTracker
import com.example.util.RealTimeTripStatus
import com.example.util.TripStepProgressionEngine
import com.example.util.TripTimeParser
import com.example.util.TripUIStateFormatter
import com.example.util.UnifiedActiveTripStateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActiveTripManager(
    private val application: Application,
    database: AppDatabase,
    private val getLastLocation: () -> Pair<Double, Double>?,
    private val scope: CoroutineScope
) {

    private val activeTripRepository = ActiveTripRepository(database.activeTripDao())
    private val metroAlertsRepository = MetroAlertsRepository()
    private val hybridRoutingRepository = HybridRoutingRepository(
        metroAlertsRepository = metroAlertsRepository,
        context = application.applicationContext
    )

    val activeTripState: StateFlow<ActiveTripState?> = activeTripRepository.getActiveTripFlow()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val unifiedTripSnapshot: StateFlow<UnifiedActiveTripSnapshot?> = UnifiedActiveTripStateTracker.snapshot

    val realTimeTripStatus: StateFlow<RealTimeTripStatus> = UnifiedActiveTripStateTracker.snapshot
        .map { it?.realTimeStatus ?: RealTimeTripStatus() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = RealTimeTripStatus()
        )

    private val _isRecalculatingTransfer = MutableStateFlow(false)
    val isRecalculatingTransfer: StateFlow<Boolean> = _isRecalculatingTransfer.asStateFlow()

    private val _recalculateError = MutableStateFlow<String?>(null)
    val recalculateError: StateFlow<String?> = _recalculateError.asStateFlow()

    private val _showTransferRiskDialog = MutableStateFlow(false)
    val showTransferRiskDialog: StateFlow<Boolean> = _showTransferRiskDialog.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            // Clean expired trips if needed
            activeTripRepository.checkAndCleanExpiredTrip()

            // Restore tracking service if active trip is in progress
            val currentTrip = activeTripRepository.getActiveTrip()
            if (currentTrip != null && currentTrip.status == ActiveTripEntity.STATUS_IN_PROGRESS) {
                ActiveTripTrackingService.start(application)
            }
        }
    }

    fun onAppForegrounded() {
        UnifiedActiveTripStateTracker.setAppForegrounded(true)
    }

    fun onAppBackgrounded() {
        UnifiedActiveTripStateTracker.setAppForegrounded(false)
    }

    fun triggerTransferRiskDialog() {
        _showTransferRiskDialog.value = true
    }

    fun dismissTransferRiskDialog() {
        _showTransferRiskDialog.value = false
    }

    fun dismissRecalculateError() {
        _recalculateError.value = null
    }

    fun recalculateMissedTransfer() {
        scope.launch {
            val trip = activeTripState.value ?: return@launch
            val currentIdx = trip.currentLegIndex
            val legs = trip.itinerary.legs
            if (legs.isEmpty() || currentIdx >= legs.size) return@launch

            _isRecalculatingTransfer.value = true
            _recalculateError.value = null

            try {
                val currentLeg = legs[currentIdx]
                val transferOriginLat = currentLeg.toLat
                val transferOriginLon = currentLeg.toLon
                val transferOriginName = currentLeg.toName.ifBlank { "Estación de transbordo" }

                val destinationLeg = legs.last()
                val destLat = destinationLeg.toLat
                val destLon = destinationLeg.toLon
                val destName = trip.destinationName.ifBlank { destinationLeg.toName }

                // Calculate expected arrival at transfer station from current leg status
                val remainingMinsOnCurrent = TripUIStateFormatter.calculateBoardedRemainingMinutes(currentLeg, realTimeTripStatus.value)
                val userArrivalAtTransferEpochMs = System.currentTimeMillis() + (remainingMinsOnCurrent * 60_000L)
                val targetDepEpochMs = userArrivalAtTransferEpochMs + 2 * 60_000L // 2 min platform/transfer walk buffer

                val targetTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(targetDepEpochMs))
                val targetDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(targetDepEpochMs))

                val result = hybridRoutingRepository.planRoute(
                    fromLat = transferOriginLat,
                    fromLon = transferOriginLon,
                    toLat = destLat,
                    toLon = destLon,
                    time = targetTime,
                    date = targetDate,
                    arriveBy = false,
                    maxTransfers = 2,
                    modes = "WALK,SUBWAY,TRAM,BUS,REGIONAL_RAIL",
                    originName = transferOriginName,
                    destinationName = destName
                )

                result.fold(
                    onSuccess = { candidateItineraries ->
                        if (candidateItineraries.isEmpty()) {
                            _recalculateError.value = "No se encontraron conexiones alternativas desde $transferOriginName a las $targetTime."
                        } else {
                            val bestNew = candidateItineraries.first()
                            val keptLegs = legs.take(currentIdx + 1)
                            val splicedLegs = keptLegs + bestNew.legs

                            val newTotalSecs = keptLegs.sumOf { it.durationSeconds } + bestNew.totalDurationSeconds
                            val splicedItinerary = trip.itinerary.copy(
                                id = "${trip.itinerary.id}_recalc_${System.currentTimeMillis()}",
                                legs = splicedLegs,
                                totalDurationSeconds = newTotalSecs,
                                formattedDuration = "${(newTotalSecs / 60).coerceAtLeast(1)} min",
                                endTime = bestNew.endTime,
                                formattedArrivalTime = bestNew.formattedArrivalTime,
                                transfersCount = (splicedLegs.count { it.mode != TransitMode.WALK } - 1).coerceAtLeast(0)
                            )

                            activeTripRepository.updateItinerary(splicedItinerary)
                            refreshRealTimeTripStatus()
                            _showTransferRiskDialog.value = false
                        }
                    },
                    onFailure = {
                        _recalculateError.value = "No ha sido posible recalcular el trayecto en este momento."
                    }
                )
            } catch (e: Exception) {
                _recalculateError.value = "Error al recalcular enlace: ${e.message}"
            } finally {
                _isRecalculatingTransfer.value = false
            }
        }
    }

    fun startActiveTrip(
        itinerary: PlannedItinerary,
        originName: String,
        destinationName: String
    ) {
        scope.launch {
            try {
                activeTripRepository.startTrip(itinerary, originName, destinationName)
                ActiveTripTrackingService.start(application)
                refreshRealTimeTripStatus()
            } catch (e: Throwable) {
                Log.e("DashboardActiveTripMgr", "Error starting active trip: ${e.message}", e)
            }
        }
    }

    fun cancelActiveTrip() {
        scope.launch {
            try {
                activeTripRepository.cancelActiveTrip()
                TripStepProgressionEngine.reset()
                ActiveTripTrackingService.stop(application)
                UnifiedActiveTripStateTracker.reset()
            } catch (e: Exception) {
                Log.e("DashboardActiveTripMgr", "Error in cancelActiveTrip: ${e.message}", e)
            }
        }
    }

    fun completeActiveTrip() {
        scope.launch {
            try {
                activeTripRepository.completeActiveTrip()
                TripStepProgressionEngine.reset()
                ActiveTripTrackingService.stop(application)
                UnifiedActiveTripStateTracker.reset()
            } catch (e: Exception) {
                Log.e("DashboardActiveTripMgr", "Error in completeActiveTrip: ${e.message}", e)
            }
        }
    }

    fun advanceActiveTripLeg(newIndex: Int) {
        scope.launch {
            val currentTrip = activeTripState.value
            ActiveTripProgressTracker.resetForNewLeg(newIndex)
            if (currentTrip != null && newIndex != currentTrip.currentLegIndex) {
                activeTripRepository.advanceLegIndex(newIndex)
            }
            refreshRealTimeTripStatus()
        }
    }

    fun confirmBoarding(targetLegIndex: Int) {
        scope.launch {
            val currentTrip = activeTripState.value
            val legs = currentTrip?.itinerary?.legs
            val targetLeg = legs?.getOrNull(targetLegIndex)

            val isTransit = targetLeg != null && targetLeg.mode in listOf(
                TransitMode.SUBWAY,
                TransitMode.BUS,
                TransitMode.TRAM,
                TransitMode.RAIL
            )

            if (isTransit && targetLeg != null) {
                TripStepProgressionEngine.markLegBoarded(targetLegIndex)
                TripStepProgressionEngine.notifyBoardingConfirmed(targetLegIndex, targetLeg)
                ActiveTripProgressTracker.markAsBoarded(targetLegIndex)
                ActiveTripTrackingService.confirmManualBoarding(application, targetLegIndex)
            } else {
                ActiveTripProgressTracker.resetForNewLeg(targetLegIndex)
            }

            if (currentTrip != null && targetLegIndex != currentTrip.currentLegIndex) {
                activeTripRepository.advanceLegIndex(targetLegIndex)
            }
            refreshRealTimeTripStatus()
        }
    }

    fun refreshRealTimeTripStatus() {
        ActiveTripTrackingService.triggerReconciliation(application)
        UnifiedActiveTripStateTracker.triggerImmediateReconcile()
    }
}
