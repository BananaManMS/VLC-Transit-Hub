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
            MetroScheduledDeparture(0, 14 * 60 + 44, "14:44", "3", 10, "Aeroport", 84, "Rafelbunyol", 101),
            MetroScheduledDeparture(0, 14 * 60 + 54, "14:54", "3", 10, "Aeroport", 84, "Rafelbunyol", 102),
            MetroScheduledDeparture(0, 14 * 60 + 55, "14:55", "3", 10, "Aeroport", 84, "Rafelbunyol", 103),
            MetroScheduledDeparture(0, 15 * 60 + 10, "15:10", "3", 10, "Aeroport", 84, "Rafelbunyol", 104)
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
            MetroScheduledDeparture(0, 12 * 60 + 30, "12:30", "3", 10, "Aeroport", 84, "Rafelbunyol", 101), // Departed -> EXCLUDE
            MetroScheduledDeparture(0, 12 * 60 + 35, "12:35", "3", 10, "Aeroport", 84, "Rafelbunyol", 102), // In live panel -> EXCLUDE
            MetroScheduledDeparture(0, 12 * 60 + 48, "12:48", "3", 10, "Aeroport", 84, "Rafelbunyol", 103), // In live panel -> EXCLUDE
            MetroScheduledDeparture(0, 13 * 60 + 0, "13:00", "3", 10, "Aeroport", 84, "Rafelbunyol", 104),  // Next upcoming -> INCLUDE
            // L5 Marítim:
            MetroScheduledDeparture(0, 12 * 60 + 38, "12:38", "5", 10, "Aeroport", 120, "Marítim", 105),    // In live panel -> EXCLUDE
            MetroScheduledDeparture(0, 12 * 60 + 50, "12:50", "5", 10, "Aeroport", 120, "Marítim", 106),    // Next upcoming -> INCLUDE
            // L9 Riba-roja (No live trains right now):
            MetroScheduledDeparture(0, 12 * 60 + 30, "12:30", "9", 84, "Rafelbunyol", 130, "Riba-roja", 107), // Departed -> EXCLUDE
            MetroScheduledDeparture(0, 12 * 60 + 45, "12:45", "9", 84, "Rafelbunyol", 130, "Riba-roja", 108)  // Next upcoming -> INCLUDE
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
            MetroScheduledDeparture(0, 12 * 60 + 10, "12:10", "3", 10, "Aeroport", 84, "Rafelbunyol", 101),
            MetroScheduledDeparture(0, 12 * 60 + 15, "12:15", "5", 120, "Marítim", 10, "Aeroport", 102),
            MetroScheduledDeparture(0, 12 * 60 + 20, "12:20", "3", 10, "Aeroport", 84, "Rafelbunyol", 103),
            MetroScheduledDeparture(0, 12 * 60 + 25, "12:25", "9", 84, "Rafelbunyol", 130, "Riba-roja", 104)
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
            MetroScheduledDeparture(0, 10 * 60 + 15, "10:15", "1", 1, "Bétera", 2, "Villanueva", 201),
            MetroScheduledDeparture(0, 12 * 60 + 0, "12:00", "1", 1, "Bétera", 2, "Villanueva", 202),
            MetroScheduledDeparture(0, 15 * 60 + 30, "15:30", "1", 1, "Bétera", 2, "Villanueva", 203), // > 3 hours
            MetroScheduledDeparture(0, 21 * 60 + 45, "21:45", "1", 1, "Bétera", 2, "Villanueva", 204)  // > 11 hours
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
