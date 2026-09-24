package com.example.ui.metro

import com.example.data.model.MetroScheduledDeparture
import java.text.Normalizer
import java.util.Calendar
import java.util.TimeZone

/**
 * Deduplicates scheduled/theoretical departures against active live departures.
 * 
 * Logic:
 * 1. If a line + destination corridor has active real-time trains in [liveDepartures], the real-time panel
 *    is already tracking upcoming trains up to the latest live train (e.g., at 12:45). Any scheduled train
 *    at or before the latest tracked live train (<= maxLiveArrivalMinutes) is either already presented
 *    live or has already passed/departed (e.g. at 12:30). Therefore, scheduled trains for that corridor
 *    must start strictly AFTER the last tracked live train (> maxLiveArrivalMinutes).
 * 2. If a line + destination corridor currently has NO live departures, we show scheduled departures
 *    starting strictly AFTER the current minute (> currentMinOfDay) to ensure a train that departed
 *    in the seconds of the current minute is not shown as upcoming.
 */
object MetroScheduleDeduplicator {

    private fun normalizeString(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    private fun normalizeLineId(line: String): String {
        return line.replace("L", "").replace("l", "").trim()
    }

    /**
     * Filters [scheduledDepartures] removing passed trains and trains already covered by [liveDepartures].
     * 
     * Highly optimized:
     * - The live list has at most 8 trains. We pre-index live departures by line into a Map.
     * - Each scheduled departure (out of ~300) does a single O(1) map lookup for its line,
     *   avoiding full nested iteration over all live trains.
     */
    fun deduplicate(
        scheduledDepartures: List<MetroScheduledDeparture>,
        liveDepartures: List<RealTimeDeparture>,
        referenceMinutesOfDay: Int? = null,
        lineFilter: String? = null
    ): List<MetroScheduledDeparture> {
        if (scheduledDepartures.isEmpty()) return emptyList()

        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        val currentMinOfDay = referenceMinutesOfDay ?: (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
        val normLineFilter = lineFilter?.replace("L", "", ignoreCase = true)?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("ALL", ignoreCase = true) }

        // Filter by user-selected line filter (if any)
        val filteredScheduled = if (normLineFilter != null) {
            scheduledDepartures.filter { normalizeLineId(it.line).equals(normLineFilter, ignoreCase = true) }
        } else {
            scheduledDepartures
        }

        if (liveDepartures.isEmpty()) {
            // Strictly after current minute so departed trains from the current minute do not appear
            return filteredScheduled.filter { it.timeMinutes > currentMinOfDay }
        }

        // Fast pre-index: Map lineId -> list of (normalizedDest, arrivalMinute)
        data class LiveCorridor(val normDest: String, val arrivalMinOfDay: Int)
        val lineToLiveCorridors = mutableMapOf<String, MutableList<LiveCorridor>>()

        for (live in liveDepartures) {
            val liveLine = normalizeLineId(live.lineId)
            val liveDest = normalizeString(live.destination)

            val arrivalMin = if (live.secondsRemaining > 0) {
                currentMinOfDay + (live.secondsRemaining / 60)
            } else {
                val parts = live.estimatedTime?.trim()?.split(":")
                if (parts != null && parts.size == 2) {
                    val hh = parts[0].toIntOrNull() ?: (currentMinOfDay / 60)
                    val mm = parts[1].toIntOrNull() ?: (currentMinOfDay % 60)
                    hh * 60 + mm
                } else {
                    currentMinOfDay
                }
            }
            lineToLiveCorridors.getOrPut(liveLine) { mutableListOf() }
                .add(LiveCorridor(liveDest, arrivalMin))
        }

        // O(S) filtering with fast indexed line lookup
        return filteredScheduled.filter { sched ->
            val schedLine = normalizeLineId(sched.line)
            val liveCorridors = lineToLiveCorridors[schedLine]

            if (liveCorridors == null || liveCorridors.isEmpty()) {
                // No live train on this line -> strictly after current minute
                sched.timeMinutes > currentMinOfDay
            } else {
                val schedDest = normalizeString(sched.destinationName)
                var maxLiveArrivalMin = -1

                for (corridor in liveCorridors) {
                    if (schedDest == corridor.normDest ||
                        schedDest.contains(corridor.normDest) ||
                        corridor.normDest.contains(schedDest)
                    ) {
                        if (corridor.arrivalMinOfDay > maxLiveArrivalMin) {
                            maxLiveArrivalMin = corridor.arrivalMinOfDay
                        }
                    }
                }

                if (maxLiveArrivalMin != -1) {
                    // Start strictly after the last live train on this corridor
                    sched.timeMinutes > maxLiveArrivalMin
                } else {
                    sched.timeMinutes > currentMinOfDay
                }
            }
        }
    }
}
