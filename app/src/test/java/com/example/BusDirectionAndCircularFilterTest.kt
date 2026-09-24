package com.example

import com.example.ui.map.components.EmtMapOverlayLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class BusDirectionAndCircularFilterTest {

    @Test
    fun testIsCircularLineDetection() {
        // EMT Official circular lines starting with 'C' (case-insensitive, with/without 'L')
        assertTrue("C1 must be detected as circular", EmtMapOverlayLoader.isCircularLine("C1"))
        assertTrue("C2 must be detected as circular", EmtMapOverlayLoader.isCircularLine("C2"))
        assertTrue("C3 must be detected as circular", EmtMapOverlayLoader.isCircularLine("C3"))
        assertTrue("c4 future circular must be detected", EmtMapOverlayLoader.isCircularLine("c4"))
        assertTrue("LC2 with L prefix must be detected", EmtMapOverlayLoader.isCircularLine("LC2"))

        // Lines starting with 'G' / 'g'
        assertTrue("g1 must be detected as circular", EmtMapOverlayLoader.isCircularLine("g1"))
        assertTrue("G5 must be detected as circular", EmtMapOverlayLoader.isCircularLine("G5"))

        // Explicit "circular" keyword in description or headsign
        assertTrue(
            "Service with circular in description must be detected",
            EmtMapOverlayLoader.isCircularLine("74", "Ruta Universitària (Circular)")
        )

        // Linear lines must NOT be circular, even if street contains "Ronda"
        assertFalse("Line 10 is linear", EmtMapOverlayLoader.isCircularLine("10"))
        assertFalse("Line 99 is linear", EmtMapOverlayLoader.isCircularLine("99"))
        assertFalse(
            "Line passing through Ronda Nord must NOT be detected as circular",
            EmtMapOverlayLoader.isCircularLine("99", "Ronda Nord - Estació del Cabanyal")
        )
        assertFalse(
            "Line with Ronda in headsign must NOT be circular",
            EmtMapOverlayLoader.isCircularLine("64", "Benicalap - Ronda Sud")
        )
    }

    @Test
    fun testLinearLineSelectsOnlySingleDirection() {
        // Line 99 with two opposing directions (ida and vuelta)
        val idaPoints = listOf(
            GeoPoint(39.4800, -0.3800),
            GeoPoint(39.4810, -0.3810)
        )
        val vueltaPoints = listOf(
            GeoPoint(39.4700, -0.3700),
            GeoPoint(39.4710, -0.3710)
        )

        val shapes = listOf(
            EmtMapOverlayLoader.EmtRouteShape("99", "1052_2230_1751", idaPoints, "Mendizábal"),
            EmtMapOverlayLoader.EmtRouteShape("99", "1052_1751_2230", vueltaPoints, "Palau de Congressos")
        )

        // Stop located right next to idaPoints (39.48002, -0.38001)
        val filtered = EmtMapOverlayLoader.pruneShapesForLine(
            shapes = shapes,
            lineCode = "99",
            selectedStopLat = 39.48002,
            selectedStopLon = -0.38001
        )

        assertEquals("Linear line must only return 1 directional shape", 1, filtered.size)
        assertEquals("Must be the closest direction (ida)", "1052_2230_1751", filtered.first().shapeId)
    }

    @Test
    fun testCircularLineClosesTheFullCircleForMatchingDirection() {
        // Line C2: clockwise direction variant '996' has 2 complementary segments.
        // Counter-clockwise direction variant '651' has 2 complementary segments.
        val seg1Clockwise = listOf(GeoPoint(39.4800, -0.3600), GeoPoint(39.4850, -0.3650))
        val seg2Clockwise = listOf(GeoPoint(39.4850, -0.3650), GeoPoint(39.4800, -0.3600))

        val seg1Counter = listOf(GeoPoint(39.4600, -0.3700), GeoPoint(39.4650, -0.3750))
        val seg2Counter = listOf(GeoPoint(39.4650, -0.3750), GeoPoint(39.4600, -0.3700))

        val shapes = listOf(
            EmtMapOverlayLoader.EmtRouteShape("C2", "996_1057_346", seg1Clockwise, "Estació d'autobusos"),
            EmtMapOverlayLoader.EmtRouteShape("C2", "996_346_1057", seg2Clockwise, "Estadi de Mestalla"),
            EmtMapOverlayLoader.EmtRouteShape("C2", "651_166_691", seg1Counter, "Plaça d'Espanya"),
            EmtMapOverlayLoader.EmtRouteShape("C2", "651_691_166", seg2Counter, "Blasco Ibáñez")
        )

        // Stop located close to seg1Clockwise
        val filtered = EmtMapOverlayLoader.pruneShapesForLine(
            shapes = shapes,
            lineCode = "C2",
            selectedStopLat = 39.48001,
            selectedStopLon = -0.36001
        )

        assertEquals("Circular line must close the loop by returning all segments of variant 996", 2, filtered.size)
        val variantIds = filtered.map { it.shapeId }
        assertTrue(variantIds.contains("996_1057_346"))
        assertTrue(variantIds.contains("996_346_1057"))
        assertFalse("Must NOT include counter-clockwise direction variant 651", variantIds.contains("651_166_691"))
    }
}
