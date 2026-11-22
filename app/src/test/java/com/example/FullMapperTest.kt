package com.example

import com.example.data.mapper.CercaniasDepartureMapper
import org.junit.Test
import org.junit.Assert.*

class FullMapperTest {
    @Test
    fun testNormalize() {
        println(CercaniasDepartureMapper.normalizeStationName("Estació Del Nord"))
        println(CercaniasDepartureMapper.normalizeStationName("Valencia Nord"))
        println(CercaniasDepartureMapper.normalizeStationName("València Nord"))
        println(CercaniasDepartureMapper.normalizeStationName("Gandia"))
        println(CercaniasDepartureMapper.normalizeStationName("Gandía"))
        println(CercaniasDepartureMapper.normalizeStationName("Castelló de la Plana"))
        println(CercaniasDepartureMapper.normalizeStationName("Xàtiva"))
    }

    @Test
    fun testChronologicalSorting() {
        val dep12MinIncoming = com.example.ui.cercanias.CercaniasDeparture(
            routeId = "C1",
            destination = "Gandia",
            minutesRemaining = 12,
            departureTime = "14:12",
            isIncomingAt = true,
            isLive = true
        )
        val dep5MinScheduled = com.example.ui.cercanias.CercaniasDeparture(
            routeId = "C2",
            destination = "Xàtiva",
            minutesRemaining = 5,
            departureTime = "14:05",
            isIncomingAt = false,
            isLive = false
        )
        val dep0MinStopped = com.example.ui.cercanias.CercaniasDeparture(
            routeId = "C6",
            destination = "Castelló",
            minutesRemaining = 0,
            departureTime = "14:00",
            isStoppedAt = true,
            isLive = true
        )

        val unsorted = listOf(dep12MinIncoming, dep5MinScheduled, dep0MinStopped)
        val sorted = CercaniasDepartureMapper.sortDeparturesChronologically(unsorted)

        assertEquals(3, sorted.size)
        assertEquals(0, sorted[0].minutesRemaining) // 0 min first
        assertEquals(5, sorted[1].minutesRemaining) // 5 min second
        assertEquals(12, sorted[2].minutesRemaining) // 12 min third
    }

    @Test
    fun testPlatjaGandiaPreservedDistinctFromGandia() {
        val platjaVariants = listOf(
            "Platja i Grau de Gandia",
            "Platja de Gandia",
            "Platja i Grau de Gandía",
            "Playa y Grao de Gandia",
            "Playa y Grao de Gandía",
            "Playa de Gandia",
            "Playa de Gandía",
            "Grau de Gandia",
            "Grao de Gandia",
            "Gandia-Platja",
            "Gandia-Playa"
        )

        for (variant in platjaVariants) {
            assertEquals(
                "formatStationDisplayName must preserve '$variant' as Platja i Grau de Gandia",
                "Platja i Grau de Gandia",
                CercaniasDepartureMapper.formatStationDisplayName(variant)
            )
            assertEquals(
                "normalizeStationName must preserve '$variant' as platja i grau de gandia",
                "platja i grau de gandia",
                CercaniasDepartureMapper.normalizeStationName(variant)
            )
            assertFalse(
                "Gandia must NOT be treated as terminal arrival for '$variant'",
                CercaniasDepartureMapper.isTerminalArrival("Gandia", variant)
            )
            assertFalse(
                "'$variant' must NOT be treated as terminal arrival for Gandia",
                CercaniasDepartureMapper.isTerminalArrival(variant, "Gandia")
            )
            assertTrue(
                "'$variant' MUST be treated as terminal arrival at Platja i Grau de Gandia",
                CercaniasDepartureMapper.isTerminalArrival("Platja i Grau de Gandia", variant)
            )
            assertFalse(
                "CercaniasRouteUtils must NOT equate Gandia and '$variant'",
                com.example.ui.cercanias.CercaniasRouteUtils.isSameStationName("Gandia", variant)
            )
            assertTrue(
                "CercaniasRouteUtils MUST equate Platja i Grau de Gandia and '$variant'",
                com.example.ui.cercanias.CercaniasRouteUtils.isSameStationName("Platja i Grau de Gandia", variant)
            )
        }

        assertEquals("Gandia", CercaniasDepartureMapper.formatStationDisplayName("Gandia"))
        assertEquals("Gandia", CercaniasDepartureMapper.formatStationDisplayName("Gandía"))
        assertEquals("gandia", CercaniasDepartureMapper.normalizeStationName("Gandia"))
        assertEquals("gandia", CercaniasDepartureMapper.normalizeStationName("Gandía"))
    }
}
