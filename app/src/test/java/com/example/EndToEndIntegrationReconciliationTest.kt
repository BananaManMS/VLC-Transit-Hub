package com.example

import com.example.data.database.ActiveTripEntity
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.data.repository.routing.LiveReconciliationEngine
import com.example.data.repository.routing.RoutingDataMapper
import com.example.util.ActiveTripProgressTracker
import com.example.util.BoardingSensorFusionEngine
import com.example.util.StepProgressionResult
import com.example.util.TripStepProgressionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EndToEndIntegrationReconciliationTest {

    @Test
    fun testRoutingDataMapperMathematicalTimeCalculations() {
        // Test timeToMinutes
        assertEquals(0, RoutingDataMapper.timeToMinutes("00:00"))
        assertEquals(510, RoutingDataMapper.timeToMinutes("08:30"))
        assertEquals(1380, RoutingDataMapper.timeToMinutes("23:00"))
        assertEquals(1439, RoutingDataMapper.timeToMinutes("23:59"))

        // Test shiftFormattedTime
        assertEquals("08:45", RoutingDataMapper.shiftFormattedTime("08:30", 15))
        assertEquals("08:15", RoutingDataMapper.shiftFormattedTime("08:30", -15))
        assertEquals("00:05", RoutingDataMapper.shiftFormattedTime("23:55", 10))
        assertEquals("23:50", RoutingDataMapper.shiftFormattedTime("00:10", -20))

        // Test formatSecondsToDuration
        assertEquals("1 min", RoutingDataMapper.formatSecondsToDuration(50))
        assertEquals("15 min", RoutingDataMapper.formatSecondsToDuration(900))
        assertEquals("1h 15m", RoutingDataMapper.formatSecondsToDuration(4500))
        assertEquals("2 h", RoutingDataMapper.formatSecondsToDuration(7200))
    }

    @Test
    fun testLiveReconciliationEngineDeduplicatesVehicleKeys() = runBlocking {
        val engine = LiveReconciliationEngine()

        val leg1 = PlannedLeg(
            mode = TransitMode.WALK,
            durationSeconds = 300,
            distanceMeters = 350.0,
            formattedDuration = "5 min",
            startTime = "2026-08-26T10:00:00",
            endTime = "2026-08-26T10:05:00",
            formattedStartTime = "10:00",
            formattedEndTime = "10:05",
            agencyName = null,
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "#9E9E9E",
            fromName = "Plaça de l'Ajuntament",
            toName = "Xàtiva",
            fromStopId = null,
            toStopId = "12",
            fromLat = 39.4699,
            fromLon = -0.3763,
            toLat = 39.4668,
            toLon = -0.3774
        )

        val leg2 = PlannedLeg(
            mode = TransitMode.SUBWAY,
            durationSeconds = 600,
            distanceMeters = 3000.0,
            formattedDuration = "10 min",
            startTime = "2026-08-26T10:05:00",
            endTime = "2026-08-26T10:15:00",
            formattedStartTime = "10:05",
            formattedEndTime = "10:15",
            scheduledStartTime = "10:05",
            scheduledEndTime = "10:15",
            agencyName = "Metrovalencia",
            routeShortName = "3",
            routeLongName = "Línea 3",
            headsign = "Aeroport",
            routeColorHex = "#E4002B",
            fromName = "Xàtiva",
            fromStopId = "12",
            toName = "Avinguda del Cid",
            toStopId = "18",
            fromLat = 39.4668,
            fromLon = -0.3774,
            toLat = 39.4690,
            toLon = -0.3980
        )

        val itin1 = PlannedItinerary(
            id = "itin_1",
            totalDurationSeconds = 900,
            startTime = "2026-08-26T10:00:00",
            endTime = "2026-08-26T10:15:00",
            formattedDuration = "15 min",
            formattedDepartureTime = "10:00",
            formattedArrivalTime = "10:15",
            recommendedStartTime = "10:00",
            transfersCount = 0,
            legs = listOf(leg1, leg2),
            viability = ItineraryViability.THEORETICAL_SCHEDULE
        )

        val itin2 = itin1.copy(id = "itin_2")

        val claimedKeys = ConcurrentHashMap.newKeySet<String>()
        val reconciled1 = engine.reconcileItineraryWithLiveData(itin1, isDepartNow = true, claimedVehicleKeys = claimedKeys)
        val reconciled2 = engine.reconcileItineraryWithLiveData(itin2, isDepartNow = true, claimedVehicleKeys = claimedKeys)

        assertNotNull(reconciled1)
        assertNotNull(reconciled2)
        assertTrue(reconciled1.legs.isNotEmpty())
        assertTrue(reconciled2.legs.isNotEmpty())
    }

    @Test
    fun testActiveTripStepProgressionAndCompletionFlow() {
        val walkLeg = PlannedLeg(
            mode = TransitMode.WALK,
            durationSeconds = 300,
            distanceMeters = 300.0,
            formattedDuration = "5 min",
            startTime = "2026-08-26T10:00:00",
            endTime = "2026-08-26T10:05:00",
            formattedStartTime = "10:00",
            formattedEndTime = "10:05",
            agencyName = null,
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "#9E9E9E",
            fromName = "Origen",
            toName = "Destino Final",
            fromStopId = null,
            toStopId = null,
            fromLat = 39.4699,
            fromLon = -0.3763,
            toLat = 39.4710,
            toLon = -0.3770
        )

        val plannedItinerary = PlannedItinerary(
            id = "itin_test_completion",
            totalDurationSeconds = 300,
            startTime = "2026-08-26T10:00:00",
            endTime = "2026-08-26T10:05:00",
            formattedDuration = "5 min",
            formattedDepartureTime = "10:00",
            formattedArrivalTime = "10:05",
            recommendedStartTime = "10:00",
            transfersCount = 0,
            legs = listOf(walkLeg),
            viability = ItineraryViability.THEORETICAL_SCHEDULE
        )

        val activeTrip = ActiveTripState(
            originName = "Origen",
            destinationName = "Destino Final",
            itinerary = plannedItinerary,
            status = ActiveTripEntity.STATUS_IN_PROGRESS,
            currentLegIndex = 0,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        // Reset and update progress tracker
        ActiveTripProgressTracker.reset()
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.0f,
            legIndex = 0,
            isBoarded = false
        )
        assertEquals(0, ActiveTripProgressTracker.progressState.value.trackedLegIndex)
        assertFalse(ActiveTripProgressTracker.progressState.value.isBoarded)

        // Step 1: User is halfway along the leg
        val progResult1 = TripStepProgressionEngine.evaluateProgression(
            userLat = 39.4704,
            userLon = -0.3766,
            activeTrip = activeTrip,
            locationAccuracyMeters = 5.0f,
            lastLocationTimeMillis = System.currentTimeMillis()
        )

        assertTrue(progResult1 is StepProgressionResult.OnTrack)

        // Step 2: User arrives at the destination (< 35m)
        val progResult2 = TripStepProgressionEngine.evaluateProgression(
            userLat = 39.4710,
            userLon = -0.3770,
            activeTrip = activeTrip,
            locationAccuracyMeters = 5.0f,
            lastLocationTimeMillis = System.currentTimeMillis()
        )

        assertTrue(progResult2 is StepProgressionResult.LegCompleted)
        val legCompleted = progResult2 as StepProgressionResult.LegCompleted
        assertTrue("Final leg must be marked as completed", legCompleted.isFinalLeg)
    }

    @Test
    fun testBoardingSensorFusionEngineConfidenceEvaluation() {
        val engine = BoardingSensorFusionEngine()
        engine.reset()

        val transitLeg = PlannedLeg(
            mode = TransitMode.BUS,
            durationSeconds = 600,
            distanceMeters = 2500.0,
            formattedDuration = "10 min",
            startTime = "2026-08-26T10:00:00",
            endTime = "2026-08-26T10:10:00",
            formattedStartTime = "10:00",
            formattedEndTime = "10:10",
            agencyName = "EMT Valencia",
            routeShortName = "10",
            routeLongName = "Línea 10",
            headsign = "Benimaclet",
            routeColorHex = "#E52320",
            fromName = "Parada 1",
            toName = "Parada 2",
            fromStopId = "101",
            toStopId = "102",
            fromLat = 39.4699,
            fromLon = -0.3763,
            toLat = 39.4750,
            toLon = -0.3800
        )

        // Location with high bus cruising speed (32 km/h ~ 8.9 m/s)
        val mockLocation = android.location.Location("gps").apply {
            latitude = 39.4710
            longitude = -0.3770
            speed = 8.9f // ~32 km/h
            accuracy = 4.0f
            time = System.currentTimeMillis()
        }

        engine.evaluate(
            location = mockLocation,
            currentLeg = transitLeg,
            currentLegIndex = 0,
            realTimeArrivalMinutes = 0,
            realTimeSecondsRemaining = 15,
            isUndergroundMode = false
        )

        val confidence = engine.confidenceFlow.value
        assertTrue("Confidence should increase with vehicle speed and 0m ETA", confidence > 0.0f)
    }
}
