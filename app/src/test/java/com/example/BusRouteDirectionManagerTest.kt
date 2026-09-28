package com.example

import com.example.ui.map.components.BusRouteDirectionManager
import com.example.ui.map.components.EmtMapOverlayLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class BusRouteDirectionManagerTest {

    @Test
    fun testIsCircularLineDetection() {
        assertTrue(BusRouteDirectionManager.isCircularLine("C1"))
        assertTrue(BusRouteDirectionManager.isCircularLine("C2"))
        assertTrue(BusRouteDirectionManager.isCircularLine("C3"))
        assertTrue(BusRouteDirectionManager.isCircularLine("c1"))
        assertTrue(BusRouteDirectionManager.isCircularLine("LC1"))
        assertTrue(BusRouteDirectionManager.isCircularLine("Circular 1"))

        assertFalse(BusRouteDirectionManager.isCircularLine("4"))
        assertFalse(BusRouteDirectionManager.isCircularLine("19"))
        assertFalse(BusRouteDirectionManager.isCircularLine("95"))
        assertFalse(BusRouteDirectionManager.isCircularLine("L150"))
    }

    @Test
    fun testCloseCircularShapesJoinsTwoDestinationsIntoSingleRing() {
        val pA = GeoPoint(39.48041, -0.37771) // e.g. start of half 1
        val pB = GeoPoint(39.46796, -0.37837) // seam / regulation stop
        val pMidA = GeoPoint(39.47500, -0.37000)
        val pMidB = GeoPoint(39.47200, -0.38000)

        val shape1 = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "C1",
            shapeId = "1018_1959_2277",
            points = listOf(pA, pMidA, pB),
            headsign = "Xàtiva - Institut Lluís Vives"
        )
        val shape2 = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "C1",
            shapeId = "1018_2277_1959",
            points = listOf(pB, pMidB, pA),
            headsign = "Blanqueria - Pare d'Òrfens"
        )

        val closed = BusRouteDirectionManager.closeCircularShapes("C1", listOf(shape1, shape2))

        assertEquals("Should merge 2 halves into 1 closed circular shape", 1, closed.size)
        val ring = closed.first()
        assertTrue("Ring points must start and end at the exact same location to close loop",
            BusRouteDirectionManager.distanceMeters(ring.points.first(), ring.points.last()) < 1.0
        )
        assertTrue("Merged ring must contain intermediate points from both halves", ring.points.size >= 5)
    }

    @Test
    fun testDetectRegulationPointsIdentifiesTerminalConnection() {
        val termStop = GeoPoint(39.4600, -0.3300)
        val other1 = GeoPoint(39.4700, -0.3700)
        val other2 = GeoPoint(39.4800, -0.3600)

        val ida = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "4",
            shapeId = "ida",
            points = listOf(other1, termStop),
            headsign = "Natzaret"
        )
        val vuelta = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "4",
            shapeId = "vuelta",
            points = listOf(termStop, other2),
            headsign = "Plaça de l'Ajuntament"
        )

        val regPoints = BusRouteDirectionManager.detectRegulationPoints(listOf(ida, vuelta))
        assertEquals(1, regPoints.size)
        assertEquals("4", regPoints.first().incomingLineRef)
        assertEquals("4", regPoints.first().outgoingLineRef)
        assertTrue(BusRouteDirectionManager.isNearRegulationPoint(termStop, regPoints))
    }

    @Test
    fun testFilterStopsFromRegulationStopShowsNextStationsOnly() {
        val allStops = listOf("Parada 1", "Parada 2", "Cabecera Regulacion", "Parada 4", "Parada 5")
        val regulationIndex = 2

        val nextStops = BusRouteDirectionManager.filterStopsFromRegulationStop(allStops, regulationIndex)

        assertEquals("Next stops sequence must start at the regulation stop", 3, nextStops.size)
        assertEquals("Cabecera Regulacion", nextStops[0])
        assertEquals("Parada 4", nextStops[1])
        assertEquals("Parada 5", nextStops[2])
    }

    @Test
    fun testFixed500mMeterDistanceConstant() {
        assertEquals(500.0, EmtMapOverlayLoader.FIXED_ARROW_INTERVAL_METERS, 0.001)
    }

    @Test
    fun testDirectionIsolationForEmtStop() {
        val stopAlongIda = GeoPoint(39.4650, -0.3500)
        val ida = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "4",
            shapeId = "ida",
            points = listOf(GeoPoint(39.4700, -0.3700), GeoPoint(39.4652, -0.3498), GeoPoint(39.4600, -0.3300)),
            headsign = "Natzaret"
        )
        val vuelta = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "4",
            shapeId = "vuelta",
            points = listOf(GeoPoint(39.4600, -0.3300), GeoPoint(39.4800, -0.3600)),
            headsign = "Plaça de l'Ajuntament"
        )

        val isolated = BusRouteDirectionManager.findMatchingShapeForStop(stopAlongIda, listOf(ida, vuelta))
        assertEquals("ida", isolated?.shapeId)
        assertEquals("Natzaret", isolated?.headsign)
    }

    @Test
    fun testDirectionIsolationAtRegulationStopPicksOutgoingDirection() {
        val regStop = GeoPoint(39.4600, -0.3300)
        val ida = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "4",
            shapeId = "ida",
            points = listOf(GeoPoint(39.4700, -0.3700), regStop),
            headsign = "Natzaret"
        )
        val vuelta = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "4",
            shapeId = "vuelta",
            points = listOf(regStop, GeoPoint(39.4800, -0.3600)),
            headsign = "Plaça de l'Ajuntament"
        )

        val isolated = BusRouteDirectionManager.findMatchingShapeForStop(regStop, listOf(ida, vuelta))
        assertEquals("At regulation stop, departing passengers take the outgoing direction", "vuelta", isolated?.shapeId)
    }

    @Test
    fun testDirectionIsolationForMetrobus() {
        val stopLoc = GeoPoint(39.4801, -0.4201)
        val shapesByDir = mapOf(
            "L150_0" to listOf(GeoPoint(39.4700, -0.3800), GeoPoint(39.4800, -0.4200), GeoPoint(39.4900, -0.4500)),
            "L150_1" to listOf(GeoPoint(39.4900, -0.4500), GeoPoint(39.5100, -0.4100), GeoPoint(39.4700, -0.3800))
        )

        val isolated = BusRouteDirectionManager.findMatchingMetrobusDirectionForStop(stopLoc, shapesByDir)
        assertEquals(1, isolated.size)
        assertTrue("Must contain only the matching direction L150_0", isolated.containsKey("L150_0"))
        assertFalse("Must NOT contain opposite direction L150_1", isolated.containsKey("L150_1"))
    }
}
