package com.example

import com.example.data.database.GeoportalStopEntity
import com.example.ui.map.components.EmtMapOverlayLoader
import com.example.ui.map.components.EmtStationHighlightManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.osmdroid.util.GeoPoint

class EmtDirectionalStopHighlightTest {

    @Before
    fun setUp() {
        // Mock two directional shapes for line 99:
        // Shape 1 (Outbound: East -> West along Lat ~ 39.48)
        val outboundShape99 = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "99",
            shapeId = "99_outbound_palau",
            points = listOf(
                GeoPoint(39.4800, -0.3500),
                GeoPoint(39.4800, -0.3700),
                GeoPoint(39.4800, -0.3900)
            ),
            headsign = "Palau de Congressos"
        )

        // Shape 2 (Inbound: West -> East along Lat ~ 39.46, 2km away or on opposing corridor)
        val inboundShape99 = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "99",
            shapeId = "99_inbound_mendizabal",
            points = listOf(
                GeoPoint(39.4600, -0.3900),
                GeoPoint(39.4600, -0.3700),
                GeoPoint(39.4600, -0.3500)
            ),
            headsign = "Mendizábal"
        )

        // Mock circular line C1 with a single closed loop
        val circularShapeC1 = EmtMapOverlayLoader.EmtRouteShape(
            lineRef = "C1",
            shapeId = "C1_loop",
            points = listOf(
                GeoPoint(39.4700, -0.3750),
                GeoPoint(39.4750, -0.3750),
                GeoPoint(39.4750, -0.3700),
                GeoPoint(39.4700, -0.3700),
                GeoPoint(39.4700, -0.3750)
            ),
            headsign = "Centre Històric"
        )

        EmtMapOverlayLoader.setShapesForTesting(
            mapOf(
                "99" to listOf(outboundShape99, inboundShape99),
                "C1" to listOf(circularShapeC1)
            )
        )
    }

    @Test
    fun testOppositeDirectionStopsAreDimmedForLine99() {
        // Selected stop on outbound shape 99 (at 39.4801, -0.3510)
        val highlightState = EmtStationHighlightManager.EmtHighlightState(
            isHighlighted = true,
            selectedStopId = "1001",
            selectedStopLat = 39.4801,
            selectedStopLon = -0.3510,
            allStopLines = setOf("99"),
            isolatedLineFilters = setOf("99"),
            activeDirectionalShapeIds = setOf("99_outbound_palau")
        )

        // Stop A: On the outbound route (39.4802, -0.3705)
        val stopOutbound = GeoportalStopEntity(
            id_parada = "1002",
            denominacion = "Outbound Stop",
            suprimida = 0,
            lineas = "99",
            lat = 39.4802,
            lon = -0.3705
        )

        // Stop B: On the inbound/return route (39.4598, -0.3705)
        val stopInboundOpposite = GeoportalStopEntity(
            id_parada = "2002",
            denominacion = "Opposite Direction Stop",
            suprimida = 0,
            lineas = "99",
            lat = 39.4598,
            lon = -0.3705
        )

        // Stop C: Unrelated stop on line 10
        val stopUnrelated = GeoportalStopEntity(
            id_parada = "3001",
            denominacion = "Unrelated Stop",
            suprimida = 0,
            lineas = "10",
            lat = 39.4800,
            lon = -0.3700
        )

        val alphaOutbound = EmtStationHighlightManager.getBusMarkerAlpha(stopOutbound, highlightState, 1.0f)
        val alphaInbound = EmtStationHighlightManager.getBusMarkerAlpha(stopInboundOpposite, highlightState, 1.0f)
        val alphaUnrelated = EmtStationHighlightManager.getBusMarkerAlpha(stopUnrelated, highlightState, 1.0f)

        assertEquals("Outbound stop on same direction must be fully highlighted (1.0f)", 1.0f, alphaOutbound, 0.001f)
        assertEquals("Inbound stop on opposite direction must be dimmed (0.25f)", 0.25f, alphaInbound, 0.001f)
        assertEquals("Unrelated stop must be dimmed (0.25f)", 0.25f, alphaUnrelated, 0.001f)
    }

    @Test
    fun testCircularLineHighlightsAllStopsAlongLoop() {
        val highlightState = EmtStationHighlightManager.EmtHighlightState(
            isHighlighted = true,
            selectedStopId = "5001",
            selectedStopLat = 39.4702,
            selectedStopLon = -0.3748,
            allStopLines = setOf("C1"),
            isolatedLineFilters = setOf("C1"),
            activeDirectionalShapeIds = setOf("C1_loop")
        )

        val stopOnLoop = GeoportalStopEntity(
            id_parada = "5002",
            denominacion = "Loop Stop",
            suprimida = 0,
            lineas = "C1",
            lat = 39.4748,
            lon = -0.3702
        )

        val alpha = EmtStationHighlightManager.getBusMarkerAlpha(stopOnLoop, highlightState, 1.0f)
        assertEquals("Circular route stop must be highlighted (1.0f)", 1.0f, alpha, 0.001f)
    }
}
