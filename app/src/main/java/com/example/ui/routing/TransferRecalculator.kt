package com.example.ui.routing

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.routing.HybridRoutingRepository
import com.example.data.repository.routing.RoutingDataMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Handles seamless recalculation and splicing of transit transfer connections
 * without disrupting the user's primary journey origin or destination.
 */
object TransferRecalculator {

    /**
     * Recalculates only the remaining journey leg from the transfer station to destination,
     * maintaining the user's initial origin and preceding transit legs intact.
     */
    suspend fun recalculateTransferAlternative(
        hybridRoutingRepository: HybridRoutingRepository,
        currentItinerary: PlannedItinerary,
        stationName: String,
        transferLat: Double,
        transferLon: Double,
        arrivalTime: String,
        walkBufferMinutes: Int = 2,
        destination: PlannerLocation,
        selectedDate: String?,
        selectedModes: Set<RouteModeFilter>,
        isCa: Boolean
    ): Result<PlannedItinerary> = withContext(Dispatchers.IO) {
        try {
            val targetDepartureTime = addMinutesToTime(arrivalTime, walkBufferMinutes.coerceAtLeast(1))

            val modes = if (selectedModes.isEmpty()) {
                "WALK,SUBWAY,TRAM,BUS,COACH,REGIONAL_RAIL"
            } else {
                ("WALK," + selectedModes.flatMap { it.modes }.distinct().joinToString(","))
            }

            val isDestStation = destination.stopType != null || destination.stopId != null || com.example.data.repository.routing.RoutingDataMapper.isStationOrStopDescriptor(destination.title, destination.stopType, destination.stopId)

            val routeResult = hybridRoutingRepository.planRoute(
                fromLat = transferLat,
                fromLon = transferLon,
                toLat = destination.latitude,
                toLon = destination.longitude,
                time = targetDepartureTime,
                date = selectedDate,
                arriveBy = false,
                maxTransfers = 2,
                modes = modes,
                originName = stationName,
                destinationName = destination.title,
                isOriginStationOrStop = true,
                isDestinationStationOrStop = isDestStation
            )

            routeResult.fold(
                onSuccess = { alternatives ->
                    if (alternatives.isEmpty()) {
                        val errMsg = if (isCa) {
                            "No s'han trobat alternatives posteriors des de $stationName a les $targetDepartureTime."
                        } else {
                            "No se encontraron alternativas posteriores desde $stationName a las $targetDepartureTime."
                        }
                        Result.failure(Exception(errMsg))
                    } else {
                        val bestSuffix = alternatives.first()
                        val prefixLegs = findPrefixLegs(currentItinerary.legs, stationName, arrivalTime)
                        val mergedLegs = prefixLegs + bestSuffix.legs

                        val totalDurationSec = RoutingDataMapper.calculateTotalDurationSec(
                            mergedLegs,
                            mergedLegs.sumOf { it.durationSeconds }
                        )
                        val startDepTime = mergedLegs.firstOrNull()?.formattedStartTime
                            ?: currentItinerary.formattedDepartureTime
                        val endArrTime = mergedLegs.lastOrNull()?.formattedEndTime
                            ?: bestSuffix.formattedArrivalTime
                        val walkLegs = mergedLegs.filter { it.mode == TransitMode.WALK }
                        val totalWalkDist = walkLegs.sumOf { it.distanceMeters }
                        val totalWalkDur = walkLegs.sumOf { it.durationSeconds }
                        val allPolyline = mergedLegs.flatMap { it.geometry }

                        val notice = if (isCa) {
                            "Transbord recalculat: eixida a les ${bestSuffix.formattedDepartureTime}"
                        } else {
                            "Transbordo recalculado: salida a las ${bestSuffix.formattedDepartureTime}"
                        }

                        val recalculatedItinerary = currentItinerary.copy(
                            id = "${currentItinerary.id}_recalc_${System.currentTimeMillis()}",
                            totalDurationSeconds = totalDurationSec,
                            formattedDuration = RoutingDataMapper.formatSecondsToDuration(totalDurationSec),
                            formattedDepartureTime = startDepTime,
                            formattedArrivalTime = endArrTime,
                            recommendedStartTime = startDepTime,
                            transfersCount = (mergedLegs.count { it.mode != TransitMode.WALK } - 1).coerceAtLeast(0),
                            legs = mergedLegs,
                            viability = ItineraryViability.VIABLE_ON_TIME,
                            viabilityNotice = notice,
                            allRoutePolyline = allPolyline,
                            totalWalkDistanceMeters = totalWalkDist,
                            totalWalkDurationSeconds = totalWalkDur
                        )

                        Result.success(recalculatedItinerary)
                    }
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findPrefixLegs(
        originalLegs: List<PlannedLeg>,
        stationName: String,
        arrivalTime: String
    ): List<PlannedLeg> {
        val cleanTarget = stationName.trim().lowercase(Locale.ROOT)
        val index = originalLegs.indexOfFirst { leg ->
            val cleanTo = leg.toName.trim().lowercase(Locale.ROOT)
            (cleanTo == cleanTarget || cleanTo.contains(cleanTarget) || cleanTarget.contains(cleanTo)) &&
                    (leg.formattedEndTime == arrivalTime || arrivalTime.isBlank())
        }
        if (index != -1) {
            return originalLegs.subList(0, index + 1)
        }

        val fallbackIndex = originalLegs.indexOfFirst { leg ->
            val cleanTo = leg.toName.trim().lowercase(Locale.ROOT)
            cleanTo == cleanTarget || cleanTo.contains(cleanTarget) || cleanTarget.contains(cleanTo)
        }
        if (fallbackIndex != -1) {
            return originalLegs.subList(0, fallbackIndex + 1)
        }

        return if (originalLegs.isNotEmpty()) listOf(originalLegs.first()) else emptyList()
    }

    fun addMinutesToTime(timeStr: String, minutesToAdd: Int): String {
        if (!timeStr.contains(":")) return timeStr
        val parts = timeStr.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return timeStr
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return timeStr
        val totalMins = (h * 60 + m + minutesToAdd).mod(24 * 60)
        val newH = totalMins / 60
        val newM = totalMins % 60
        return String.format(Locale.US, "%02d:%02d", newH, newM)
    }
}
