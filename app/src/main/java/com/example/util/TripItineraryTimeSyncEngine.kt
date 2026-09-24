package com.example.util

import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.TransitMode

/**
 * Handles updating and synchronizing itinerary schedules with real-time delays,
 * vehicle arrival times, and intermediate stop estimations.
 */
object TripItineraryTimeSyncEngine {

    fun syncRealTimeItinerary(
        itinerary: PlannedItinerary,
        status: RealTimeTripStatus,
        currentLegIndex: Int
    ): PlannedItinerary {
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

        val isBoarded = ActiveTripProgressTracker.progressState.value.isBoarded

        if (targetTransitIdx != null && targetTransitIdx in updatedLegs.indices) {
            val targetLeg = updatedLegs[targetTransitIdx]
            val origStart = targetLeg.scheduledStartTime ?: targetLeg.formattedStartTime
            val origEnd = targetLeg.scheduledEndTime ?: targetLeg.formattedEndTime

            // Inmutabilidad Absoluta de la salida al estar embarcado (congelar en la salida real calculada o confirmada):
            val newStart = if (isBoarded) {
                targetLeg.formattedStartTime.ifBlank { origStart }
            } else {
                status.adjustedDepartureTime ?: if (status.delayMinutes != 0) {
                    TripTimeParser.shiftFormattedTime(origStart, status.delayMinutes)
                } else origStart
            }

            val startMs = TripTimeParser.parseTimeToMillis(origStart)
            val newStartMs = TripTimeParser.parseTimeToMillis(newStart)
            val effectiveDelayMins = if (startMs != null && newStartMs != null) {
                ((newStartMs - startMs) / 60000L).toInt()
            } else {
                status.delayMinutes
            }

            // ETA dinámico de llegada al estar a bordo:
            val newEnd = if (isBoarded && status.vehicleArrivalMinutes != null && status.vehicleArrivalMinutes > 0) {
                TripTimeParser.addMinutesToNow(status.vehicleArrivalMinutes)
            } else if (status.delayMinutes != 0) {
                TripTimeParser.shiftFormattedTime(origEnd, status.delayMinutes)
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

            val matchedLine = status.vehicleLine
            val matchedDest = status.vehicleDestination

            val newTargetLeg = targetLeg.copy(
                routeShortName = if (!matchedLine.isNullOrBlank()) matchedLine else targetLeg.routeShortName,
                routeLongName = if (!matchedLine.isNullOrBlank()) "Línea $matchedLine" else targetLeg.routeLongName,
                headsign = if (!matchedDest.isNullOrBlank()) matchedDest else targetLeg.headsign,
                isRealTimeVerified = status.isLive,
                realTimeDelayMinutes = intermediateDelayMins,
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

            // Update transfer walk to reflect when the user reaches the transfer platform
            if (targetTransitIdx + 1 < updatedLegs.size) {
                val nextLeg = updatedLegs[targetTransitIdx + 1]
                if (nextLeg.mode == TransitMode.WALK) {
                    val transferWalk = nextLeg
                    val walkSecs = transferWalk.durationSeconds
                    val transferWalkStart = newEnd
                    val transferWalkEnd = TripTimeParser.shiftFormattedTime(newEnd, (walkSecs / 60).coerceAtLeast(1).toInt())

                    val newTransferWalk = transferWalk.copy(
                        formattedStartTime = transferWalkStart,
                        formattedEndTime = transferWalkEnd
                    )
                    if (newTransferWalk != transferWalk) {
                        updatedLegs[targetTransitIdx + 1] = newTransferWalk
                        modified = true
                    }
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
