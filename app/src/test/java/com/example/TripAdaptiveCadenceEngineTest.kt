package com.example

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.util.ActiveTripProgressTracker
import com.example.util.RealTimeTripStatus
import com.example.util.TripAdaptiveCadenceEngine
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripAdaptiveCadenceEngineTest {

    private fun createSampleTrip(isBoardedLeg: Boolean = false): ActiveTripState {
        val walkLeg = PlannedLeg(
            mode = TransitMode.WALK,
            durationSeconds = 480,
            distanceMeters = 500.0,
            formattedDuration = "8 min",
            startTime = "2026-10-07T10:00:00",
            endTime = "2026-10-07T10:08:00",
            formattedStartTime = "10:00",
            formattedEndTime = "10:08",
            agencyName = null,
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "#9E9E9E",
            fromName = "Origen",
            toName = "Colon",
            fromStopId = null,
            toStopId = "10",
            fromLat = 39.47,
            fromLon = -0.37,
            toLat = 39.46,
            toLon = -0.36
        )
        val subwayLeg = PlannedLeg(
            mode = TransitMode.SUBWAY,
            durationSeconds = 900,
            distanceMeters = 5000.0,
            formattedDuration = "15 min",
            startTime = "2026-10-07T10:08:00",
            endTime = "2026-10-07T10:23:00",
            formattedStartTime = "10:08",
            formattedEndTime = "10:23",
            agencyName = "Metrovalencia",
            routeShortName = "3",
            routeLongName = "Rafelbunyol - Aeroport",
            headsign = "Aeroport",
            routeColorHex = "#E52320",
            fromName = "Colon",
            toName = "Aeroport",
            fromStopId = "10",
            toStopId = "99",
            fromLat = 39.46,
            fromLon = -0.36,
            toLat = 39.49,
            toLon = -0.47
        )
        val itinerary = PlannedItinerary(
            id = "itinerary_test",
            totalDurationSeconds = 1380,
            startTime = "2026-10-07T10:00:00",
            endTime = "2026-10-07T10:23:00",
            recommendedStartTime = "10:00",
            formattedDuration = "23 min",
            formattedDepartureTime = "10:00",
            formattedArrivalTime = "10:23",
            transfersCount = 0,
            legs = listOf(walkLeg, subwayLeg),
            viability = ItineraryViability.THEORETICAL_SCHEDULE,
            totalWalkDistanceMeters = 500.0,
            totalWalkDurationSeconds = 480L
        )
        return ActiveTripState(
            originName = "Origen",
            destinationName = "Aeroport",
            itinerary = itinerary,
            status = com.example.data.database.ActiveTripEntity.STATUS_ACTIVE,
            currentLegIndex = if (isBoardedLeg) 1 else 0,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
    }

    @Before
    fun setup() {
        ActiveTripProgressTracker.reset()
    }

    @Test
    fun testImminentCadenceWhenLeaveNowActive() {
        val trip = createSampleTrip(isBoardedLeg = false)
        val status = RealTimeTripStatus(
            isLeaveNowAlert = true,
            vehicleArrivalMinutes = 1
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = true)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_IMMINENT_MS, cadence)
    }

    @Test
    fun testImminentCadenceWhenVehicleArrivingIn3Minutes() {
        val trip = createSampleTrip(isBoardedLeg = false)
        val status = RealTimeTripStatus(
            vehicleArrivalMinutes = 3
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = true)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_IMMINENT_MS, cadence)
    }

    @Test
    fun testImminentCadenceWhenTransferAtRisk() {
        val trip = createSampleTrip(isBoardedLeg = true)
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.5f,
            waitTimeMessage = "",
            statusDetail = "",
            isDeadReckoning = false,
            isBoarded = true,
            transitDepartureTimeMs = 0L,
            lastSeenArrivalMins = 5,
            legIndex = 1
        )
        val status = RealTimeTripStatus(
            isTransferAtRisk = true,
            checkpointEtaMinutes = 8
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = true)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_IMMINENT_MS, cadence)
    }

    @Test
    fun testImminentCadenceWhenAlightingIn2Minutes() {
        val trip = createSampleTrip(isBoardedLeg = true)
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.85f,
            waitTimeMessage = "",
            statusDetail = "",
            isDeadReckoning = false,
            isBoarded = true,
            transitDepartureTimeMs = 0L,
            lastSeenArrivalMins = 2,
            legIndex = 1
        )
        val status = RealTimeTripStatus(
            checkpointEtaMinutes = 2
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = true)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_IMMINENT_MS, cadence)
    }

    @Test
    fun testRelaxedCadenceWhenVehicleFarAway() {
        val trip = createSampleTrip(isBoardedLeg = false)
        val status = RealTimeTripStatus(
            vehicleArrivalMinutes = 18
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = true)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_RELAXED_MS, cadence)
    }

    @Test
    fun testRelaxedCadenceWhenBoardedLongTrip() {
        val trip = createSampleTrip(isBoardedLeg = true)
        ActiveTripProgressTracker.updateProgress(
            progressWithinLeg = 0.2f,
            waitTimeMessage = "",
            statusDetail = "",
            isDeadReckoning = false,
            isBoarded = true,
            transitDepartureTimeMs = 0L,
            lastSeenArrivalMins = 14,
            legIndex = 1
        )
        val status = RealTimeTripStatus(
            checkpointEtaMinutes = 14,
            upcomingTransferMinutes = 20
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = true)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_RELAXED_MS, cadence)
    }

    @Test
    fun testStandardCadenceWhenWalkingNormally() {
        val trip = createSampleTrip(isBoardedLeg = false)
        val status = RealTimeTripStatus(
            vehicleArrivalMinutes = 8,
            dynamicWalkMinutesRemaining = 6
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = true)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_STANDARD_MS, cadence)
    }

    @Test
    fun testRelaxedCadenceWhenBackgroundedAndNotImminent() {
        val trip = createSampleTrip(isBoardedLeg = false)
        val status = RealTimeTripStatus(
            vehicleArrivalMinutes = 8
        )
        val cadence = TripAdaptiveCadenceEngine.calculateCadenceMs(trip, status, isAppForegrounded = false)
        assertEquals(TripAdaptiveCadenceEngine.CADENCE_RELAXED_MS, cadence)
    }
}
