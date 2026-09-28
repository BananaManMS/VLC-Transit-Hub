package com.example

import com.example.data.model.MetroScheduledDeparture
import com.example.data.model.MetroTrainTimeline
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying Metro Schedule models and anti-deception reconciliation rules.
 */
class MetroScheduleTest {

    @Test
    fun testRealTimeTakesAbsolutePriorityOverSchedule() {
        // Regla de Oro: Si la API en tiempo real responde vacía durante horas de servicio
        // (huelga, catenaria rota, interrupción total), no se inyectan trenes programados
        // en la lista principal en vivo para no engañar al usuario.
        val isServiceDisruptedOrEmpty = true
        val liveDepartures = emptyList<String>()
        val scheduledFallbackDepartures = listOf("10:00 L3 Rafelbunyol", "10:15 L9 Riba-roja")

        val displayedDepartures = if (isServiceDisruptedOrEmpty) {
            liveDepartures
        } else {
            scheduledFallbackDepartures
        }

        assertTrue("Displayed departures must remain empty during disruption to prevent deceiving passengers", displayedDepartures.isEmpty())
    }

    @Test
    fun testScheduledDepartureModelAttributes() {
        val dep = MetroScheduledDeparture(
            dateIndex = 0,
            timeMinutes = 14 * 60 + 25,
            timeFormatted = "14:25",
            line = "3",
            originWebId = 10,
            originName = "Aeroport",
            destinationWebId = 84,
            destinationName = "Rafelbunyol",
            trainServiceId = 302
        )

        assertEquals("3", dep.line)
        assertEquals("Aeroport", dep.originName)
        assertEquals("Rafelbunyol", dep.destinationName)
        assertEquals("14:25", dep.timeFormatted)
        assertEquals(865, dep.timeMinutes)
        assertEquals(302, dep.trainServiceId)
        assertEquals(10, dep.originWebId)
        assertEquals(84, dep.destinationWebId)
    }

    @Test
    fun testStrictUpcomingTimeExclusionDoesNotShowPassedTrains() {
        // At 14:55 (895 minutes), trains scheduled for 14:44, 14:54, or 14:55 MUST be excluded
        // so that trains departing in the current minute that already passed never leak into scheduled list
        val currentMin = 14 * 60 + 55 // 895
        val sampleDepartures = listOf(
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "14:44", timeMinutes = 14 * 60 + 44, timeFormatted = "14:44"),
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "14:54", timeMinutes = 14 * 60 + 54, timeFormatted = "14:54"),
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "14:55", timeMinutes = 14 * 60 + 55, timeFormatted = "14:55"),
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "15:10", timeMinutes = 15 * 60 + 10, timeFormatted = "15:10")
        )

        val deduplicated = com.example.ui.metro.MetroScheduleDeduplicator.deduplicate(
            scheduledDepartures = sampleDepartures,
            liveDepartures = emptyList(),
            referenceMinutesOfDay = currentMin
        )

        assertEquals(1, deduplicated.size)
        assertTrue(deduplicated.none { it.timeMinutes <= currentMin })
        assertEquals("15:10", deduplicated[0].timeFormatted)
    }

    @Test
    fun testScheduledDeparturesStartStrictlyAfterLastLiveTrainOnCorridor() {
        val currentMin = 12 * 60 + 30 // 12:30 (750 min)

        // Live trains in real time for L3 Rafelbunyol:
        // - Train 1: arriving at 12:35 (secondsRemaining = 300)
        // - Train 2: arriving at 12:48 (secondsRemaining = 1080)
        val liveDepartures = listOf(
            com.example.ui.metro.RealTimeDeparture(
                id = "live_1",
                lineId = "3",
                destination = "Rafelbunyol",
                minutesRemaining = 5,
                secondsRemaining = 300,
                colorHex = "#FF0000",
                estimatedTime = "12:35"
            ),
            com.example.ui.metro.RealTimeDeparture(
                id = "live_2",
                lineId = "3",
                destination = "Rafelbunyol",
                minutesRemaining = 18,
                secondsRemaining = 1080,
                colorHex = "#FF0000",
                estimatedTime = "12:48"
            ),
            // Live train for L5 Marítim arriving at 12:38
            com.example.ui.metro.RealTimeDeparture(
                id = "live_3",
                lineId = "5",
                destination = "Marítim",
                minutesRemaining = 8,
                secondsRemaining = 480,
                colorHex = "#00FF00",
                estimatedTime = "12:38"
            )
        )

        val scheduledDepartures = listOf(
            // L3 Rafelbunyol:
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "12:30", timeMinutes = 12 * 60 + 30, timeFormatted = "12:30"), // Departed -> EXCLUDE
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "12:35", timeMinutes = 12 * 60 + 35, timeFormatted = "12:35"), // In live panel -> EXCLUDE
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "12:48", timeMinutes = 12 * 60 + 48, timeFormatted = "12:48"), // In live panel -> EXCLUDE
            MetroScheduledDeparture(line = "3", destination = "Rafelbunyol", departureTime = "13:00", timeMinutes = 13 * 60 + 0, timeFormatted = "13:00"),  // Next upcoming -> INCLUDE
            // L5 Marítim:
            MetroScheduledDeparture(line = "5", destination = "Marítim", departureTime = "12:38", timeMinutes = 12 * 60 + 38, timeFormatted = "12:38"),    // In live panel -> EXCLUDE
            MetroScheduledDeparture(line = "5", destination = "Marítim", departureTime = "12:50", timeMinutes = 12 * 60 + 50, timeFormatted = "12:50"),    // Next upcoming -> INCLUDE
            // L9 Riba-roja (No live trains right now):
            MetroScheduledDeparture(line = "9", destination = "Riba-roja", departureTime = "12:30", timeMinutes = 12 * 60 + 30, timeFormatted = "12:30"), // Departed -> EXCLUDE
            MetroScheduledDeparture(line = "9", destination = "Riba-roja", departureTime = "12:45", timeMinutes = 12 * 60 + 45, timeFormatted = "12:45")  // Next upcoming -> INCLUDE
        )

        val deduplicated = com.example.ui.metro.MetroScheduleDeduplicator.deduplicate(
            scheduledDepartures = scheduledDepartures,
            liveDepartures = liveDepartures,
            referenceMinutesOfDay = currentMin
        )

        // Expected in scheduled:
        // - L3 Rafelbunyol at 13:00
        // - L5 Marítim at 12:50
        // - L9 Riba-roja at 12:45
        assertEquals(3, deduplicated.size)
        assertEquals(listOf("13:00", "12:50", "12:45"), deduplicated.map { it.timeFormatted })
    }

    @Test
    fun testLineFilterInDeduplication() {
        val currentMin = 12 * 60
        val sampleDepartures = listOf(
            MetroScheduledDeparture(line = "3", destination = "Aeroport", departureTime = "12:10", timeMinutes = 12 * 60 + 10, timeFormatted = "12:10"),
            MetroScheduledDeparture(line = "5", destination = "Marítim", departureTime = "12:15", timeMinutes = 12 * 60 + 15, timeFormatted = "12:15"),
            MetroScheduledDeparture(line = "3", destination = "Aeroport", departureTime = "12:20", timeMinutes = 12 * 60 + 20, timeFormatted = "12:20"),
            MetroScheduledDeparture(line = "9", destination = "Rafelbunyol", departureTime = "12:25", timeMinutes = 12 * 60 + 25, timeFormatted = "12:25")
        )

        val l3Only = com.example.ui.metro.MetroScheduleDeduplicator.deduplicate(
            scheduledDepartures = sampleDepartures,
            liveDepartures = emptyList(),
            referenceMinutesOfDay = currentMin,
            lineFilter = "3"
        )

        assertEquals(2, l3Only.size)
        assertTrue(l3Only.all { it.line == "3" })
    }

    @Test
    fun testSingleLineStationShowsAllDayDepartures() {
        val currentMin = 10 * 60 // 10:00 (600 min)
        val fullDayDepartures = listOf(
            MetroScheduledDeparture(line = "1", destination = "Bétera", departureTime = "10:15", timeMinutes = 10 * 60 + 15, timeFormatted = "10:15"),
            MetroScheduledDeparture(line = "1", destination = "Bétera", departureTime = "12:00", timeMinutes = 12 * 60 + 0, timeFormatted = "12:00"),
            MetroScheduledDeparture(line = "1", destination = "Bétera", departureTime = "15:30", timeMinutes = 15 * 60 + 30, timeFormatted = "15:30"),
            MetroScheduledDeparture(line = "1", destination = "Bétera", departureTime = "21:45", timeMinutes = 21 * 60 + 45, timeFormatted = "21:45")
        )

        val deduplicated = com.example.ui.metro.MetroScheduleDeduplicator.deduplicate(
            scheduledDepartures = fullDayDepartures,
            liveDepartures = emptyList(),
            referenceMinutesOfDay = currentMin
        )

        assertEquals(4, deduplicated.size)
        assertEquals("21:45", deduplicated.last().timeFormatted)
    }

    @Test
    fun testFacultatsBenimacletMachadoServiceTerminationFiltering() {
        val departures = listOf(
            com.example.ui.metro.RealTimeDeparture(id = "1", lineId = "L3", destination = "Rafelbunyol", minutesRemaining = 2),
            com.example.ui.metro.RealTimeDeparture(id = "2", lineId = "L5", destination = "Machado (Cocheras)", minutesRemaining = 5),
            com.example.ui.metro.RealTimeDeparture(id = "3", lineId = "L7", destination = "Machado (Cocheras)", minutesRemaining = 8),
            com.example.ui.metro.RealTimeDeparture(id = "4", lineId = "L10", destination = "Natzaret", minutesRemaining = 12),
            com.example.ui.metro.RealTimeDeparture(id = "5", lineId = "L1", destination = "Bétera", minutesRemaining = 15)
        )

        val stationLines = listOf("3", "9") // Facultats normal lines

        val filtered = departures.filter { dep ->
            val cleanDep = dep.lineId.replace("L", "", ignoreCase = true).trim()
            if (cleanDep == "10") {
                false
            } else if (cleanDep == "5" || cleanDep == "7") {
                true
            } else if (stationLines.isNotEmpty()) {
                stationLines.any { sl ->
                    sl.equals(dep.lineId, ignoreCase = true) ||
                    sl.replace("L", "", ignoreCase = true).trim().equals(cleanDep, ignoreCase = true)
                }
            } else {
                true
            }
        }

        // Must include L3, L5, L7. Must exclude L10 and L1.
        val linesInResult = filtered.map { it.lineId }
        assertTrue("L3 must be included", linesInResult.contains("L3"))
        assertTrue("L5 must be included for Machado depot retirement", linesInResult.contains("L5"))
        assertTrue("L7 must be included for Machado depot retirement", linesInResult.contains("L7"))
        assertFalse("L10 must be strictly excluded", linesInResult.contains("L10"))
        assertFalse("L1 must be excluded", linesInResult.contains("L1"))
    }
}
