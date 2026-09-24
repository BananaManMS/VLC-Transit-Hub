package com.example

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.PlannedStop
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.ui.dashboard.AppLanguage
import com.example.util.ActiveProgressInfo
import com.example.util.ActiveTripSnapshotBuilder
import com.example.util.RealTimeTripStatus
import com.example.util.TripUrgencyLevel
import com.example.util.UnifiedActiveTripStateTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnifiedActiveTripEngineTest {

    @Before
    fun setUp() {
        UnifiedActiveTripStateTracker.reset()
    }

    private fun createSampleTrip(
        currentLegIndex: Int = 0,
        mode: TransitMode = TransitMode.BUS,
        line: String = "71"
    ): ActiveTripState {
        val walkLeg = PlannedLeg(
            mode = TransitMode.WALK,
            durationSeconds = 180,
            distanceMeters = 150.0,
            formattedDuration = "3 min",
            startTime = "14:00",
            endTime = "14:03",
            formattedStartTime = "14:00",
            formattedEndTime = "14:03",
            agencyName = "Walk",
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "#808080",
            fromName = "Origen",
            toName = "Parada EMT 1001",
            fromStopId = null,
            toStopId = "1001",
            fromLat = 39.4690,
            fromLon = -0.3760,
            toLat = 39.4700,
            toLon = -0.3760,
            geometry = listOf(GeoPoint(39.4690, -0.3760), GeoPoint(39.4700, -0.3760))
        )

        val transitLeg = PlannedLeg(
            mode = mode,
            durationSeconds = 600,
            distanceMeters = 2000.0,
            formattedDuration = "10 min",
            startTime = "14:05",
            endTime = "14:15",
            formattedStartTime = "14:05",
            formattedEndTime = "14:15",
            agencyName = "EMT",
            routeShortName = line,
            routeLongName = "Linea $line",
            headsign = "Universitats",
            routeColorHex = "#D32F2F",
            fromName = "Parada EMT 1001",
            toName = "Universitats",
            fromStopId = "1001",
            toStopId = "1010",
            fromLat = 39.4700,
            fromLon = -0.3760,
            toLat = 39.4850,
            toLon = -0.3760,
            geometry = listOf(GeoPoint(39.4700, -0.3760), GeoPoint(39.4850, -0.3760)),
            intermediateStops = listOf(
                PlannedStop("Parada A", "A", 39.4750, -0.3760),
                PlannedStop("Parada B", "B", 39.4800, -0.3760)
            )
        )

        val itinerary = PlannedItinerary(
            id = "test_itin_1",
            totalDurationSeconds = 780,
            formattedDuration = "13 min",
            startTime = "14:00",
            endTime = "14:15",
            recommendedStartTime = "14:00",
            formattedDepartureTime = "14:00",
            formattedArrivalTime = "14:15",
            transfersCount = 0,
            legs = listOf(walkLeg, transitLeg),
            viability = ItineraryViability.VIABLE_ON_TIME,
            totalWalkDistanceMeters = 150.0
        )

        return ActiveTripState(
            originName = "Origen",
            destinationName = "Universitats",
            itinerary = itinerary,
            status = "ACTIVE",
            currentLegIndex = currentLegIndex,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun testUnifiedSnapshotBuilder_WalkPrecedingTransit_ShowsBoardingChipNearStation() {
        val trip = createSampleTrip(currentLegIndex = 0)
        val progressNearStation = ActiveProgressInfo(
            progressWithinLeg = 0.9f,
            distanceToTargetMeters = 40.0,
            isBoarded = false,
            trackedLegIndex = 0
        )

        val snapshot = ActiveTripSnapshotBuilder.build(
            activeTrip = trip,
            progressInfo = progressNearStation,
            realTimeStatus = RealTimeTripStatus(isLive = true, vehicleArrivalMinutes = 2),
            appLanguage = AppLanguage.ES
        )

        assertNotNull(snapshot)
        assertEquals(0, snapshot.currentLegIndex)
        assertEquals(TransitMode.WALK, snapshot.currentLeg?.mode)
        assertEquals(1, snapshot.candidateLegIndex)
        assertEquals("71", snapshot.candidateTransitLeg?.routeShortName)
        assertTrue("Should show boarding confirmation chip when within 60m of transit stop and vehicle arriving", snapshot.shouldShowBoardingConfirmation)
        assertFalse(snapshot.isImminentDebark)
    }

    @Test
    fun testUnifiedSnapshotBuilder_BoardedTransit_HidesBoardingChipAndShowsDebark() {
        val trip = createSampleTrip(currentLegIndex = 1)
        val progressBoarded = ActiveProgressInfo(
            progressWithinLeg = 0.95f,
            distanceToTargetMeters = 80.0,
            isBoarded = true,
            trackedLegIndex = 1,
            remainingStopsCount = 1
        )

        val snapshot = ActiveTripSnapshotBuilder.build(
            activeTrip = trip,
            progressInfo = progressBoarded,
            realTimeStatus = RealTimeTripStatus(isLive = true, vehicleArrivalMinutes = 1),
            appLanguage = AppLanguage.ES
        )

        assertNotNull(snapshot)
        assertEquals(1, snapshot.currentLegIndex)
        assertEquals(TransitMode.BUS, snapshot.currentLeg?.mode)
        assertFalse("Should NOT show boarding confirmation once already boarded", snapshot.shouldShowBoardingConfirmation)
        assertTrue("Should trigger imminent debark alert when 1 stop remaining and close to target", snapshot.isImminentDebark)
    }

    @Test
    fun testUnifiedSnapshotBuilder_LeaveNowAlert_SetsCriticalUrgency() {
        val trip = createSampleTrip(currentLegIndex = 0)
        val progress = ActiveProgressInfo(
            progressWithinLeg = 0.0f,
            distanceToTargetMeters = 150.0,
            isBoarded = false,
            trackedLegIndex = 0
        )

        val statusLeaveNow = RealTimeTripStatus(
            isLive = true,
            isLeaveNowAlert = true,
            vehicleArrivalMinutes = 1
        )

        val snapshot = ActiveTripSnapshotBuilder.build(
            activeTrip = trip,
            progressInfo = progress,
            realTimeStatus = statusLeaveNow,
            appLanguage = AppLanguage.ES
        )

        assertTrue(snapshot.isLeaveNowAlert)
        assertEquals(TripUrgencyLevel.CRITICAL, snapshot.urgencyLevel)
    }

    @Test
    fun testUnifiedActiveTripStateTracker_PublishesSnapshotAndForegroundToggle() {
        assertTrue("Initially foreground is true by default", UnifiedActiveTripStateTracker.isAppForegrounded.value)

        UnifiedActiveTripStateTracker.setAppForegrounded(false)
        assertFalse(UnifiedActiveTripStateTracker.isAppForegrounded.value)

        UnifiedActiveTripStateTracker.setAppForegrounded(true)
        assertTrue(UnifiedActiveTripStateTracker.isAppForegrounded.value)

        val trip = createSampleTrip()
        val snapshot = ActiveTripSnapshotBuilder.build(
            activeTrip = trip,
            progressInfo = ActiveProgressInfo(),
            realTimeStatus = RealTimeTripStatus(),
            appLanguage = AppLanguage.ES
        )

        UnifiedActiveTripStateTracker.updateSnapshot(snapshot)
        assertEquals("test_itin_1", UnifiedActiveTripStateTracker.snapshot.value?.activeTrip?.itinerary?.id)

        UnifiedActiveTripStateTracker.setAppForegrounded(false)
        assertFalse(UnifiedActiveTripStateTracker.isAppForegrounded.value)
        assertEquals("test_itin_1", UnifiedActiveTripStateTracker.snapshot.value?.activeTrip?.itinerary?.id)

        UnifiedActiveTripStateTracker.reset()
        assertEquals(null, UnifiedActiveTripStateTracker.snapshot.value)
    }
}
