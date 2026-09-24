package com.example.ui.routing.components

import androidx.compose.ui.graphics.Color
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.ui.dashboard.AppLanguage
import com.example.util.LineColorResolver
import com.example.util.RealTimeTripStatus

/**
 * Flat intermediate data model for timeline rows with pre-resolved primitive colors and status.
 */
sealed interface TimelineItem {
    val isPast: Boolean get() = false

    data class Origin(
        val title: String,
        val time: String,
        val nextColor: Color?,
        val nextDotted: Boolean,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Boarding(
        val stationName: String,
        val time: String,
        val scheduledTime: String?,
        val delayMins: Int?,
        val lineColor: Color,
        val prevColor: Color?,
        val prevDotted: Boolean,
        val isLive: Boolean = false,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class TransitRide(
        val leg: PlannedLeg,
        val lineColor: Color,
        override val isPast: Boolean = false,
        val isLive: Boolean = false
    ) : TimelineItem

    data class Transfer(
        val stationName: String,
        val arrivalTime: String,
        val departureTime: String,
        val durationStr: String,
        val incomingColor: Color,
        val outgoingColor: Color,
        val isRisk: Boolean = false,
        val outgoingMode: TransitMode = TransitMode.SUBWAY,
        val outgoingLine: String? = null,
        val destination: String? = null,
        val nextScheduledDepartureTime: String? = null,
        val transferLat: Double = 0.0,
        val transferLon: Double = 0.0,
        val isLive: Boolean = false,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Alighting(
        val stationName: String,
        val time: String,
        val scheduledTime: String?,
        val delayMins: Int?,
        val lineColor: Color,
        val nextColor: Color?,
        val nextDotted: Boolean,
        val isLive: Boolean = false,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Walk(
        val leg: PlannedLeg,
        val isTransfer: Boolean,
        val prevColor: Color?,
        val nextColor: Color?,
        override val isPast: Boolean = false
    ) : TimelineItem

    data class Destination(
        val title: String,
        val time: String,
        val scheduledTime: String? = null,
        val delayMins: Int? = null,
        val isLive: Boolean = false,
        val prevColor: Color?,
        val prevDotted: Boolean,
        override val isPast: Boolean = false
    ) : TimelineItem
}

/**
 * Transforms itinerary legs into an ordered flat list of [TimelineItem] instances.
 */
fun buildTimelineItems(
    itinerary: PlannedItinerary,
    displayLegs: List<PlannedLeg>,
    appLanguage: AppLanguage,
    currentLegIndex: Int = -1,
    realTimeStatus: RealTimeTripStatus? = null
): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()
    if (displayLegs.isEmpty()) return items

    val dotColor = Color(0xFFB0BEC5)

    fun getLegColor(leg: PlannedLeg?): Color? {
        if (leg == null || leg.mode == TransitMode.WALK) return null
        return LineColorResolver.resolveRouteColor(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName)
    }

    val firstLeg = displayLegs.first()
    val firstColor = getLegColor(firstLeg)
    val firstDotted = firstLeg.mode == TransitMode.WALK
    val originLabel = if (firstLeg.mode == TransitMode.WALK) {
        if (appLanguage == AppLanguage.ES) "Tu ubicación" else "La teua ubicació"
    } else {
        firstLeg.fromName.ifBlank { if (appLanguage == AppLanguage.ES) "Origen" else "Origen" }
    }

    // Only add a separate Origin row if the trip starts with a walk segment (e.g. "Tu ubicación").
    // If the trip starts directly at a transit station/stop, the first Boarding row will act as the origin point.
    var skipNextBoardingStationName: String? = null
    if (firstLeg.mode == TransitMode.WALK) {
        items.add(
            TimelineItem.Origin(
                title = originLabel,
                time = itinerary.formattedDepartureTime,
                nextColor = firstColor ?: dotColor,
                nextDotted = firstDotted,
                isPast = currentLegIndex > 0
            )
        )
    }

    for (i in displayLegs.indices) {
        val leg = displayLegs[i]
        val prevLeg = displayLegs.getOrNull(i - 1)
        val nextLeg = displayLegs.getOrNull(i + 1)
        val isPastLeg = currentLegIndex != -1 && i < currentLegIndex

        if (leg.mode == TransitMode.WALK) {
            val isTransferWalk = prevLeg != null && nextLeg != null && prevLeg.mode != TransitMode.WALK && nextLeg.mode != TransitMode.WALK
            if (isTransferWalk && isSameStationName(prevLeg!!.toName, nextLeg!!.fromName)) {
                // Same station transfer handled in previous transit leg as Transfer item!
                continue
            }
            items.add(
                TimelineItem.Walk(
                    leg = leg,
                    isTransfer = isTransferWalk,
                    prevColor = dotColor,
                    nextColor = dotColor,
                    isPast = isPastLeg
                )
            )
        } else {
            val currentColor = getLegColor(leg)!!
            val prevDotted = prevLeg == null || prevLeg.mode == TransitMode.WALK

            // Determine if this transit leg has live telemetry
            val isCurrentTransit = currentLegIndex == i || (currentLegIndex == 0 && i == 1) || (currentLegIndex != -1 && !isPastLeg && i <= currentLegIndex + 1)
            val isTransferTransit = currentLegIndex != -1 && i > currentLegIndex && (nextLeg != null || prevLeg != null)
            val isLegLive = leg.isRealTimeVerified ||
                    (isCurrentTransit && realTimeStatus?.isLive == true) ||
                    (isTransferTransit && realTimeStatus?.isUpcomingTransferLive == true)

            // Check if boarding was handled by previous transfer node
            val isBoardingSkipped = skipNextBoardingStationName != null && isSameStationName(leg.fromName, skipNextBoardingStationName)
            if (isBoardingSkipped) {
                skipNextBoardingStationName = null
            } else {
                items.add(
                    TimelineItem.Boarding(
                        stationName = leg.fromName,
                        time = leg.formattedStartTime,
                        scheduledTime = leg.scheduledStartTime,
                        delayMins = leg.realTimeDelayMinutes,
                        lineColor = currentColor,
                        prevColor = dotColor,
                        prevDotted = prevDotted,
                        isLive = isLegLive,
                        isPast = isPastLeg
                    )
                )
            }

            // Transit Ride Card
            items.add(TimelineItem.TransitRide(leg, currentColor, isPast = isPastLeg, isLive = isLegLive))

            // Check if next transit leg transfers at same station
            val nextTransitLeg = if (nextLeg?.mode != TransitMode.WALK) nextLeg else displayLegs.getOrNull(i + 2)
            val isSameStationTransfer = nextTransitLeg != null && nextTransitLeg.mode != TransitMode.WALK && isSameStationName(leg.toName, nextTransitLeg.fromName)

            if (isSameStationTransfer && nextTransitLeg != null) {
                val outgoingColor = getLegColor(nextTransitLeg)!!
                val durationStr = if (nextLeg?.mode == TransitMode.WALK) nextLeg.formattedDuration else "1 min"
                val walkMins = if (nextLeg?.mode == TransitMode.WALK) (nextLeg.durationSeconds / 60).toInt().coerceAtLeast(1) else 1
                val arrMins = timeToMinutes(leg.formattedEndTime)
                val depMins = timeToMinutes(nextTransitLeg.formattedStartTime)
                val isNextTransitLive = nextTransitLeg.realTimeDelayMinutes != null ||
                        (realTimeStatus?.isUpcomingTransferLive == true && realTimeStatus.upcomingTransferLine == nextTransitLeg.routeShortName)
                val isRisk = (realTimeStatus != null && realTimeStatus.isTransferAtRisk && i == currentLegIndex) ||
                        (depMins - arrMins < walkMins + 1) ||
                        itinerary.viability == ItineraryViability.ADJUSTED_NEXT_DEPARTURE

                val altLines = com.example.data.repository.routing.TransitIdMapper.getAlternativeTransitLines(
                    nextTransitLeg.mode,
                    nextTransitLeg.routeShortName,
                    nextTransitLeg.fromName,
                    nextTransitLeg.toName
                ).filter { it.startsWith("L") || it.startsWith("C") }.distinct().sorted()

                val outgoingLineDisplay = if (altLines.size >= 2) {
                    altLines.joinToString("/")
                } else {
                    nextTransitLeg.routeShortName
                }

                val intervalMins = if (altLines.size >= 2) {
                    7
                } else if (nextTransitLeg.mode == TransitMode.SUBWAY && (nextTransitLeg.routeShortName?.contains("9") == true || nextTransitLeg.headsign?.contains("Riba", ignoreCase = true) == true)) {
                    30
                } else {
                    15
                }
                val nextScheduledTime = shiftFormattedTime(nextTransitLeg.formattedStartTime, intervalMins)

                items.add(
                    TimelineItem.Transfer(
                        stationName = leg.toName,
                        arrivalTime = leg.formattedEndTime,
                        departureTime = nextTransitLeg.formattedStartTime,
                        durationStr = durationStr,
                        incomingColor = currentColor,
                        outgoingColor = outgoingColor,
                        isRisk = isRisk,
                        outgoingMode = nextTransitLeg.mode,
                        outgoingLine = outgoingLineDisplay,
                        destination = nextTransitLeg.headsign ?: nextTransitLeg.routeLongName ?: nextTransitLeg.toName,
                        nextScheduledDepartureTime = nextScheduledTime,
                        transferLat = nextTransitLeg.fromLat,
                        transferLon = nextTransitLeg.fromLon,
                        isLive = isNextTransitLive,
                        isPast = isPastLeg
                    )
                )
                skipNextBoardingStationName = nextTransitLeg.fromName
            } else {
                val nextColor = getLegColor(nextLeg)
                val nextDotted = nextLeg == null || nextLeg.mode == TransitMode.WALK
                items.add(
                    TimelineItem.Alighting(
                        stationName = leg.toName,
                        time = leg.formattedEndTime,
                        scheduledTime = leg.scheduledEndTime,
                        delayMins = leg.realTimeDelayMinutes,
                        lineColor = currentColor,
                        nextColor = nextColor ?: dotColor,
                        nextDotted = nextDotted,
                        isLive = isLegLive,
                        isPast = isPastLeg
                    )
                )
            }
        }
    }

    val lastLeg = displayLegs.lastOrNull()
    val lastColor = getLegColor(lastLeg) ?: dotColor
    val lastDotted = lastLeg?.mode == TransitMode.WALK
    val rawDestLabel = lastLeg?.toName ?: ""
    val cleanDestLabel = if (rawDestLabel.isNotBlank() && rawDestLabel != "END" && rawDestLabel != "End") rawDestLabel else if (appLanguage == AppLanguage.ES) "Destino" else "Destinació"

    // If the last item added was an Alighting node at the exact same station name as the destination,
    // upgrade that Alighting item to be the Destination pin instead of duplicating the row!
    val lastItem = items.lastOrNull()
    if (lastItem is TimelineItem.Alighting && isSameStationName(lastItem.stationName, cleanDestLabel)) {
        items.removeAt(items.size - 1)
        items.add(
            TimelineItem.Destination(
                title = lastItem.stationName,
                time = lastItem.time,
                scheduledTime = lastItem.scheduledTime,
                delayMins = lastItem.delayMins,
                isLive = lastItem.isLive,
                prevColor = lastItem.lineColor,
                prevDotted = false,
                isPast = lastItem.isPast
            )
        )
    } else {
        val lastTransitLeg = displayLegs.lastOrNull { it.mode != TransitMode.WALK }
        val isDestLive = lastTransitLeg?.isRealTimeVerified == true || realTimeStatus?.isLive == true
        items.add(
            TimelineItem.Destination(
                title = cleanDestLabel,
                time = itinerary.formattedArrivalTime,
                scheduledTime = lastLeg?.scheduledEndTime,
                delayMins = realTimeStatus?.delayMinutes ?: lastTransitLeg?.realTimeDelayMinutes,
                isLive = isDestLive,
                prevColor = lastColor,
                prevDotted = lastDotted,
                isPast = false
            )
        )
    }

    return items
}

private fun isSameStationName(name1: String, name2: String): Boolean {
    if (name1.isBlank() || name2.isBlank()) return false
    val n1 = name1.lowercase().replace("estación", "").replace("estació", "").replace("parada", "").replace("metro", "").trim()
    val n2 = name2.lowercase().replace("estación", "").replace("estació", "").replace("parada", "").replace("metro", "").trim()
    if (n1 == n2) return true
    if (n1.length > 3 && n2.length > 3 && (n1.contains(n2) || n2.contains(n1))) return true
    return false
}

private fun shiftFormattedTime(timeStr: String, minutesToAdd: Int): String {
    if (timeStr.isBlank() || !timeStr.contains(":")) return timeStr
    return try {
        val parts = timeStr.trim().split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val totalMinutes = (h * 60 + m + minutesToAdd + 1440) % 1440
        String.format(java.util.Locale.US, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
    } catch (e: Exception) {
        timeStr
    }
}

private fun timeToMinutes(timeStr: String): Int {
    if (timeStr.isBlank() || !timeStr.contains(":")) return 0
    return try {
        val parts = timeStr.trim().split(":")
        parts[0].toInt() * 60 + parts[1].toInt()
    } catch (e: Exception) {
        0
    }
}
