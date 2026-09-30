package com.example

import android.location.Location
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.PlannedStop
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.ui.dashboard.AppLanguage
import com.example.util.ActiveTripProgressTracker
import com.example.util.ActiveTripSnapshotBuilder
import com.example.util.BoardingSensorFusionEngine
import com.example.util.StepProgressionResult
import com.example.util.TripStepProgressionEngine
import com.example.util.TripUIStateFormatter
import com.example.util.TripUrgencyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardingAndStopsProgressionTest {

    private lateinit var sensorEngine: BoardingSensorFusionEngine

    @Before
    fun setUp() {
        sensorEngine = BoardingSensorFusionEngine()
        ActiveTripProgressTracker.reset()
    }

    private fun createBusLeg(
        intermediateStopsCount: Int = 3,
        fromLat: Double = 39.4699,
        fromLon: Double = -0.3763,
        toLat: Double = 39.4800,
        toLon: Double = -0.3763,
        startTime: String = "14:30",
        endTime: String = "14:40"
    ): PlannedLeg {
        val stops = (1..intermediateStopsCount).map { i ->
            PlannedStop(
                name = "Parada Intermedia $i",
                stopId = "stop_$i",
                lat = fromLat + (toLat - fromLat) * (i.toDouble() / (intermediateStopsCount + 1)),
                lon = fromLon,
                scheduledTime = if (startTime.isNotBlank()) "14:${30 + i * 2}" else "",
                formattedTime = if (startTime.isNotBlank()) "14:${30 + i * 2}" else ""
            )
        }

        return PlannedLeg(
            mode = TransitMode.BUS,
            durationSeconds = 600,
            distanceMeters = 2000.0,
            formattedDuration = "10 min",
            startTime = startTime,
            endTime = endTime,
            formattedStartTime = startTime,
            formattedEndTime = endTime,
            agencyName = "EMT",
            routeShortName = "71",
            routeLongName = "La Llum - Universitats",
            headsign = "Universitats",
            routeColorHex = "#D32F2F",
            fromName = "Plaça de l'Ajuntament",
            toName = "Universitats",
            fromStopId = "1001",
            toStopId = "1010",
            fromLat = fromLat,
            fromLon = fromLon,
            toLat = toLat,
            toLon = toLon,
            geometry = listOf(
                GeoPoint(fromLat, fromLon),
                GeoPoint(fromLat + 0.003, fromLon),
                GeoPoint(toLat, toLon)
            ),
            intermediateStops = stops
        )
    }

    @Test
    fun testWaitingAtBusStopWithRealTimeArrivalDoesNotPrematurelyTriggerBoarding() {
        val busLeg = createBusLeg()

        // User is 15 meters from the bus stop (waiting on the sidewalk)
        // Walking slowly / slight GPS drift
        val location = Location("gps").apply {
            latitude = 39.46995
            longitude = -0.3763
            speed = 1.2f // 4.3 km/h (walking speed)
            bearing = 0.0f
            accuracy = 10.0f
            time = System.currentTimeMillis()
        }

        // Bus 1 arrives (real-time: 0 min)
        val confidence = sensorEngine.evaluate(
            location = location,
            currentLeg = busLeg,
            currentLegIndex = 0,
            realTimeArrivalMinutes = 0,
            realTimeSecondsRemaining = 10,
            isUndergroundMode = false
        )

        // Confidence must remain strictly below boarding threshold (0.75) while in stop waiting zone
        assertTrue("Confidence ($confidence) must be <= 0.40 while at stop zone", confidence <= 0.40f)
    }

    @Test
    fun testMovingAwayFromStopAtTransitSpeedConfirmsBoarding() {
        val busLeg = createBusLeg()

        // User is now ~233m north of the bus stop, moving at 22 km/h (~6.1 m/s) with bearing north
        val location = Location("gps").apply {
            latitude = 39.4720 // ~233m away (outside 200m waiting zone)
            longitude = -0.3763
            speed = 6.1f
            bearing = 0.0f // Heading north along route
            accuracy = 8.0f
            time = System.currentTimeMillis()
        }

        // First reading
        sensorEngine.evaluate(
            location = location,
            currentLeg = busLeg,
            currentLegIndex = 0,
            realTimeArrivalMinutes = 0,
            realTimeSecondsRemaining = 0,
            isUndergroundMode = false
        )

        // Second reading (sustained speed)
        val confidence2 = sensorEngine.evaluate(
            location = location,
            currentLeg = busLeg,
            currentLegIndex = 0,
            realTimeArrivalMinutes = null,
            realTimeSecondsRemaining = null,
            isUndergroundMode = false
        )

        assertTrue("Confidence ($confidence2) should be >= 0.75 after moving away at speed", confidence2 >= 0.75f)
    }

    @Test
    fun testRemainingStopsFormattingMultipleStops() {
        val busLeg = createBusLeg(intermediateStopsCount = 3, startTime = "", endTime = "") // Total stops = 4 (3 intermediate + 1 dest)

        // Set remaining stops = 3
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.25f,
            isBoarded = true,
            remainingStopsCount = 3
        )

        val formattedEs = TripUIStateFormatter.format(
            currentLeg = busLeg,
            currentLegIndex = 0,
            totalLegs = 1,
            realTimeStatus = null,
            isBoarded = true,
            scheduledArrivalTime = "14:40",
            appLanguage = AppLanguage.ES,
            distanceToTargetMeters = 1500.0,
            allLegs = listOf(busLeg)
        )

        assertEquals("Bus 71 · 3 paradas", formattedEs.headline)
        assertEquals("Baja en Universitats · 7 min", formattedEs.subheadline)
        assertEquals(TripUrgencyLevel.RELAXED, formattedEs.urgencyLevel)

        val formattedCa = TripUIStateFormatter.format(
            currentLeg = busLeg,
            currentLegIndex = 0,
            totalLegs = 1,
            realTimeStatus = null,
            isBoarded = true,
            scheduledArrivalTime = "14:40",
            appLanguage = AppLanguage.CA,
            distanceToTargetMeters = 1500.0,
            allLegs = listOf(busLeg)
        )

        assertEquals("Bus 71 · 3 parades", formattedCa.headline)
        assertEquals("Baixa en Universitats · 7 min", formattedCa.subheadline)
    }

    @Test
    fun testRemainingStopsFormattingPenultimateStop() {
        val busLeg = createBusLeg(intermediateStopsCount = 3)

        // Only 1 stop remaining (the destination stop!)
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.85f,
            isBoarded = true,
            remainingStopsCount = 1
        )

        val formattedEs = TripUIStateFormatter.format(
            currentLeg = busLeg,
            currentLegIndex = 0,
            totalLegs = 1,
            realTimeStatus = null,
            isBoarded = true,
            scheduledArrivalTime = "14:40",
            appLanguage = AppLanguage.ES,
            distanceToTargetMeters = 400.0,
            allLegs = listOf(busLeg)
        )

        assertEquals("Bus 71 · Próxima parada", formattedEs.headline)
        assertEquals("Baja en Universitats · 1 min", formattedEs.subheadline)

        val formattedCa = TripUIStateFormatter.format(
            currentLeg = busLeg,
            currentLegIndex = 0,
            totalLegs = 1,
            realTimeStatus = null,
            isBoarded = true,
            scheduledArrivalTime = "14:40",
            appLanguage = AppLanguage.CA,
            distanceToTargetMeters = 400.0,
            allLegs = listOf(busLeg)
        )

        assertEquals("Bus 71 · Pròxima parada", formattedCa.headline)
        assertEquals("Baixa en Universitats · 1 min", formattedCa.subheadline)
    }

    @Test
    fun testRemainingStopsFormattingArrivalAtDestination() {
        val busLeg = createBusLeg(intermediateStopsCount = 3)

        // Arrived at destination stop (within 45 meters)
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.99f,
            isBoarded = true,
            remainingStopsCount = 0
        )

        val formattedEs = TripUIStateFormatter.format(
            currentLeg = busLeg,
            currentLegIndex = 0,
            totalLegs = 1,
            realTimeStatus = null,
            isBoarded = true,
            scheduledArrivalTime = "14:40",
            appLanguage = AppLanguage.ES,
            distanceToTargetMeters = 45.0,
            allLegs = listOf(busLeg)
        )

        assertEquals("Baja aquí", formattedEs.headline)
        assertEquals("Universitats", formattedEs.subheadline)
        assertEquals(TripUrgencyLevel.CRITICAL, formattedEs.urgencyLevel)

        val formattedCa = TripUIStateFormatter.format(
            currentLeg = busLeg,
            currentLegIndex = 0,
            totalLegs = 1,
            realTimeStatus = null,
            isBoarded = true,
            scheduledArrivalTime = "14:40",
            appLanguage = AppLanguage.CA,
            distanceToTargetMeters = 45.0,
            allLegs = listOf(busLeg)
        )

        assertEquals("Baixa ací", formattedCa.headline)
        assertEquals("Universitats", formattedCa.subheadline)
        assertEquals(TripUrgencyLevel.CRITICAL, formattedCa.urgencyLevel)
    }

    @Test
    fun testDirectMetroToMetroTransferResetsBoardedStateAndRestoresWaitingState() {
        TripStepProgressionEngine.reset()
        ActiveTripProgressTracker.reset()

        val leg1 = PlannedLeg(
            mode = TransitMode.SUBWAY,
            durationSeconds = 600,
            distanceMeters = 3000.0,
            formattedDuration = "10 min",
            startTime = "14:00",
            endTime = "14:10",
            formattedStartTime = "14:00",
            formattedEndTime = "14:10",
            agencyName = "Metrovalencia",
            routeShortName = "3",
            routeLongName = "Línea 3",
            headsign = "Rafelbunyol",
            routeColorHex = "#D32F2F",
            fromName = "Avinguda del Cid",
            toName = "Àngel Guimerà",
            fromStopId = "s1",
            toStopId = "s2",
            fromLat = 39.4680,
            fromLon = -0.3950,
            toLat = 39.4710,
            toLon = -0.3840,
            geometry = listOf(GeoPoint(39.4680, -0.3950), GeoPoint(39.4710, -0.3840))
        )

        val leg2 = PlannedLeg(
            mode = TransitMode.SUBWAY,
            durationSeconds = 480,
            distanceMeters = 2400.0,
            formattedDuration = "8 min",
            startTime = "14:15",
            endTime = "14:23",
            formattedStartTime = "14:15",
            formattedEndTime = "14:23",
            agencyName = "Metrovalencia",
            routeShortName = "1",
            routeLongName = "Línea 1",
            headsign = "Bétera",
            routeColorHex = "#F5A623",
            fromName = "Àngel Guimerà",
            toName = "Empalme",
            fromStopId = "s2",
            toStopId = "s3",
            fromLat = 39.4710,
            fromLon = -0.3840,
            toLat = 39.4950,
            toLon = -0.3980,
            geometry = listOf(GeoPoint(39.4710, -0.3840), GeoPoint(39.4950, -0.3980))
        )

        val itinerary = PlannedItinerary(
            id = "transfer_trip",
            totalDurationSeconds = 1380,
            formattedDuration = "23 min",
            startTime = "14:00",
            endTime = "14:23",
            recommendedStartTime = "14:00",
            formattedDepartureTime = "14:00",
            formattedArrivalTime = "14:23",
            transfersCount = 1,
            legs = listOf(leg1, leg2),
            viability = ItineraryViability.VIABLE_ON_TIME,
            totalWalkDistanceMeters = 0.0
        )

        val tripLeg1 = ActiveTripState(
            originName = "Avinguda del Cid",
            destinationName = "Empalme",
            itinerary = itinerary,
            status = "ACTIVE",
            currentLegIndex = 0,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        // 1. User boards Leg 1 (L3)
        TripStepProgressionEngine.notifyBoardingConfirmed(0, leg1)
        assertTrue(TripStepProgressionEngine.isLegBoarded(0))
        assertTrue(ActiveTripProgressTracker.progressState.value.isBoarded)
        assertEquals(0, ActiveTripProgressTracker.progressState.value.trackedLegIndex)

        // 2. User arrives at transfer station Àngel Guimerà (< 30m of leg1 destination)
        val arrivalResult = TripStepProgressionEngine.evaluateProgression(
            userLat = 39.4710,
            userLon = -0.3840,
            activeTrip = tripLeg1,
            locationAccuracyMeters = 8.0f,
            lastLocationTimeMillis = System.currentTimeMillis()
        )

        assertTrue(arrivalResult is StepProgressionResult.LegCompleted)
        val legCompleted = arrivalResult as StepProgressionResult.LegCompleted
        assertEquals(0, legCompleted.completedLegIndex)
        assertEquals(1, legCompleted.nextLegIndex)
        assertFalse(legCompleted.isFinalLeg)

        // Boarded state must be RESET for the new leg
        assertFalse("Leg 1 (completed) must not be boarded", TripStepProgressionEngine.isLegBoarded(0))
        assertFalse("Leg 2 (transfer) must NOT be boarded yet", TripStepProgressionEngine.isLegBoarded(1))
        assertFalse("Tracker must be reset to unboarded", ActiveTripProgressTracker.progressState.value.isBoarded)
        assertEquals("Tracker must now track leg 1", 1, ActiveTripProgressTracker.progressState.value.trackedLegIndex)
        assertEquals(0L, ActiveTripProgressTracker.progressState.value.transitDepartureTimeMs)

        // 3. User is standing on the platform at Àngel Guimerà waiting for L1
        val tripLeg2 = tripLeg1.copy(currentLegIndex = 1)
        val waitingResult = TripStepProgressionEngine.evaluateProgression(
            userLat = 39.4710,
            userLon = -0.3840,
            activeTrip = tripLeg2,
            locationAccuracyMeters = 10.0f,
            lastLocationTimeMillis = System.currentTimeMillis()
        )

        assertTrue(waitingResult is StepProgressionResult.OnTrack)
        assertFalse("User must remain unboarded while waiting on platform", ActiveTripProgressTracker.progressState.value.isBoarded)
        assertEquals("Progress must be frozen at station start", 0.05f, ActiveTripProgressTracker.progressState.value.progressWithinLeg, 0.01f)

        val snapshot = ActiveTripSnapshotBuilder.build(
            activeTrip = tripLeg2,
            progressInfo = ActiveTripProgressTracker.progressState.value,
            realTimeStatus = null,
            appLanguage = AppLanguage.ES
        )

        assertFalse("Snapshot must indicate user is not yet boarded", snapshot.isBoarded)
        assertFalse("Snapshot must not show imminent debark during wait", snapshot.isImminentDebark)
    }

    @Test
    fun testMetroToWalkToBusTransferResetsBoardedStateAtBusStop() {
        TripStepProgressionEngine.reset()
        ActiveTripProgressTracker.reset()

        val metroLeg = PlannedLeg(
            mode = TransitMode.SUBWAY,
            durationSeconds = 300,
            distanceMeters = 1500.0,
            formattedDuration = "5 min",
            startTime = "14:00",
            endTime = "14:05",
            formattedStartTime = "14:00",
            formattedEndTime = "14:05",
            agencyName = "Metrovalencia",
            routeShortName = "5",
            routeLongName = "Línea 5",
            headsign = "Marítim",
            routeColorHex = "#00A550",
            fromName = "Colón",
            toName = "Xàtiva",
            fromStopId = "m1",
            toStopId = "m2",
            fromLat = 39.4690,
            fromLon = -0.3720,
            toLat = 39.4670,
            toLon = -0.3770,
            geometry = listOf(GeoPoint(39.4690, -0.3720), GeoPoint(39.4670, -0.3770))
        )

        val walkLeg = PlannedLeg(
            mode = TransitMode.WALK,
            durationSeconds = 180,
            distanceMeters = 200.0,
            formattedDuration = "3 min",
            startTime = "14:05",
            endTime = "14:08",
            formattedStartTime = "14:05",
            formattedEndTime = "14:08",
            agencyName = "Walk",
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "#808080",
            fromName = "Xàtiva",
            toName = "Parada EMT Marqués de Sotelo",
            fromStopId = null,
            toStopId = "1002",
            fromLat = 39.4670,
            fromLon = -0.3770,
            toLat = 39.4680,
            toLon = -0.3765,
            geometry = listOf(GeoPoint(39.4670, -0.3770), GeoPoint(39.4680, -0.3765))
        )

        val busLeg = PlannedLeg(
            mode = TransitMode.BUS,
            durationSeconds = 600,
            distanceMeters = 2000.0,
            formattedDuration = "10 min",
            startTime = "14:10",
            endTime = "14:20",
            formattedStartTime = "14:10",
            formattedEndTime = "14:20",
            agencyName = "EMT",
            routeShortName = "71",
            routeLongName = "La Llum - Universitats",
            headsign = "Universitats",
            routeColorHex = "#D32F2F",
            fromName = "Parada EMT Marqués de Sotelo",
            toName = "Universitats",
            fromStopId = "1002",
            toStopId = "1010",
            fromLat = 39.4680,
            fromLon = -0.3765,
            toLat = 39.4850,
            toLon = -0.3765,
            geometry = listOf(GeoPoint(39.4680, -0.3765), GeoPoint(39.4850, -0.3765))
        )

        val itinerary = PlannedItinerary(
            id = "metro_walk_bus",
            totalDurationSeconds = 1200,
            formattedDuration = "20 min",
            startTime = "14:00",
            endTime = "14:20",
            recommendedStartTime = "14:00",
            formattedDepartureTime = "14:00",
            formattedArrivalTime = "14:20",
            transfersCount = 2,
            legs = listOf(metroLeg, walkLeg, busLeg),
            viability = ItineraryViability.VIABLE_ON_TIME,
            totalWalkDistanceMeters = 200.0
        )

        val tripWalk = ActiveTripState(
            originName = "Colón",
            destinationName = "Universitats",
            itinerary = itinerary,
            status = "ACTIVE",
            currentLegIndex = 1,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        // 1. User arrives at end of Walk Leg (Marqués de Sotelo bus stop)
        val walkCompleteResult = TripStepProgressionEngine.evaluateProgression(
            userLat = 39.4680,
            userLon = -0.3765,
            activeTrip = tripWalk,
            locationAccuracyMeters = 5.0f,
            lastLocationTimeMillis = System.currentTimeMillis()
        )

        assertTrue(walkCompleteResult is StepProgressionResult.LegCompleted)
        val legCompleted = walkCompleteResult as StepProgressionResult.LegCompleted
        assertEquals(1, legCompleted.completedLegIndex)
        assertEquals(2, legCompleted.nextLegIndex)

        // 2. User is now on Bus Leg (index 2) waiting at the bus stop
        val tripBus = tripWalk.copy(currentLegIndex = 2)
        val busWaitingResult = TripStepProgressionEngine.evaluateProgression(
            userLat = 39.4680,
            userLon = -0.3765,
            activeTrip = tripBus,
            locationAccuracyMeters = 5.0f,
            lastLocationTimeMillis = System.currentTimeMillis()
        )

        assertTrue(busWaitingResult is StepProgressionResult.OnTrack)
        assertFalse("Bus leg must be waiting at stop (not boarded)", ActiveTripProgressTracker.progressState.value.isBoarded)
        assertEquals(2, ActiveTripProgressTracker.progressState.value.trackedLegIndex)
        assertEquals(0.05f, ActiveTripProgressTracker.progressState.value.progressWithinLeg, 0.01f)
    }
}
