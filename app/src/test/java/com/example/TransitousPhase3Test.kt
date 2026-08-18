package com.example

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.ui.routing.DepartureType
import com.example.ui.routing.PlannerLocation
import com.example.ui.routing.RouteModeFilter
import com.example.ui.routing.RoutePlannerUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransitousPhase3Test {

    @Test
    fun testPlannerLocationCreation() {
        val location = PlannerLocation(
            title = "Plaça de l'Ajuntament",
            subtitle = "Valencia, España",
            latitude = 39.4699,
            longitude = -0.3763,
            isUserGps = false
        )
        assertEquals("Plaça de l'Ajuntament", location.title)
        assertEquals(39.4699, location.latitude, 0.0001)
        assertEquals(-0.3763, location.longitude, 0.0001)
        assertFalse(location.isUserGps)
    }

    @Test
    fun testRouteModeFilterParams() {
        assertEquals(listOf("SUBWAY", "TRAM"), RouteModeFilter.METRO.modes)
        assertEquals(listOf("BUS"), RouteModeFilter.BUS.modes)
        assertEquals(listOf("RAIL"), RouteModeFilter.TRAIN.modes)
    }

    @Test
    fun testDepartureTypeLabels() {
        assertEquals("Salir ahora", DepartureType.LEAVE_NOW.labelEs)
        assertEquals("Eixir ara", DepartureType.LEAVE_NOW.labelCa)
        assertEquals("Salir a las...", DepartureType.DEPART_AT.labelEs)
        assertEquals("Llegar antes de...", DepartureType.ARRIVE_BY.labelEs)
    }

    @Test
    fun testPlannedItineraryDataIntegrity() {
        val leg1 = PlannedLeg(
            mode = TransitMode.WALK,
            durationSeconds = 180L,
            distanceMeters = 200.0,
            formattedDuration = "3 min",
            startTime = "16:00",
            endTime = "16:03",
            formattedStartTime = "16:00",
            formattedEndTime = "16:03",
            agencyName = null,
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "9E9E9E",
            fromName = "Origen",
            toName = "Xàtiva",
            fromStopId = null,
            toStopId = "71"
        )
        val leg2 = PlannedLeg(
            mode = TransitMode.SUBWAY,
            durationSeconds = 480L,
            distanceMeters = 3000.0,
            formattedDuration = "8 min",
            startTime = "16:05",
            endTime = "16:13",
            formattedStartTime = "16:05",
            formattedEndTime = "16:13",
            agencyName = "Metrovalencia",
            routeShortName = "3",
            routeLongName = "Línea 3",
            headsign = "Rafelbunyol",
            routeColorHex = "E62238",
            fromName = "Xàtiva",
            toName = "Benimaclet",
            fromStopId = "71",
            toStopId = "42"
        )

        val itinerary = PlannedItinerary(
            id = "itin_1",
            totalDurationSeconds = 780L,
            startTime = "2026-08-14T16:00:00Z",
            endTime = "2026-08-14T16:13:00Z",
            recommendedStartTime = "16:00",
            formattedDuration = "13 min",
            formattedDepartureTime = "16:00",
            formattedArrivalTime = "16:13",
            transfersCount = 0,
            legs = listOf(leg1, leg2),
            viability = ItineraryViability.VIABLE_ON_TIME,
            viabilityNotice = "GPS en directo: Metro en 5 min",
            totalWalkDistanceMeters = 200.0,
            totalWalkDurationSeconds = 180L
        )

        assertEquals("itin_1", itinerary.id)
        assertEquals(2, itinerary.legs.size)
        assertEquals(ItineraryViability.VIABLE_ON_TIME, itinerary.viability)
        assertEquals("13 min", itinerary.formattedDuration)
        assertEquals("16:00", itinerary.formattedDepartureTime)
        assertEquals("16:13", itinerary.formattedArrivalTime)
        assertEquals(200.0, itinerary.totalWalkDistanceMeters, 0.01)
    }
}
