package com.example

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.data.repository.ActiveTripState
import com.example.util.TripRealTimeReconciler
import com.example.util.TripTimeParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveTripMatchingDepartureTest {

    @Test
    fun testSubway15MinDepartureDoesNotMatch4MinEarlierTrain() = runBlocking {
        val calNow = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
        val nowFormatted = String.format(Locale.getDefault(), "%02d:%02d", calNow.get(Calendar.HOUR_OF_DAY), calNow.get(Calendar.MINUTE))
        val metroDepartureTime = TripTimeParser.addMinutesToNow(15)

        val walkLeg = PlannedLeg(
            mode = TransitMode.WALK,
            durationSeconds = 600, // 10 min walk
            distanceMeters = 750.0,
            formattedDuration = "10 min",
            startTime = "2026-08-18T$nowFormatted:00+02:00",
            endTime = "2026-08-18T$metroDepartureTime:00+02:00",
            formattedStartTime = nowFormatted,
            formattedEndTime = metroDepartureTime,
            agencyName = null,
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "#9E9E9E",
            fromName = "Origen",
            toName = "Xàtiva",
            fromStopId = null,
            toStopId = "12",
            fromLat = 39.468,
            fromLon = -0.375,
            toLat = 39.466,
            toLon = -0.377
        )

        val metroLeg = PlannedLeg(
            mode = TransitMode.SUBWAY,
            durationSeconds = 900,
            distanceMeters = 8000.0,
            formattedDuration = "15 min",
            startTime = "2026-08-18T$metroDepartureTime:00+02:00",
            endTime = "2026-08-18T${TripTimeParser.addMinutesToNow(30)}:00+02:00",
            formattedStartTime = metroDepartureTime,
            formattedEndTime = TripTimeParser.addMinutesToNow(30),
            scheduledStartTime = metroDepartureTime,
            scheduledEndTime = TripTimeParser.addMinutesToNow(30),
            agencyName = "Metrovalencia",
            routeShortName = "3",
            routeLongName = "Línea 3",
            headsign = "Aeroport",
            routeColorHex = "#005BBB",
            fromName = "Xàtiva",
            fromStopId = "12",
            toName = "Aeroport",
            toStopId = "99"
        )

        val plannedItinerary = PlannedItinerary(
            id = "test_trip_15m",
            totalDurationSeconds = 1800,
            startTime = "2026-08-18T$nowFormatted:00+02:00",
            endTime = "2026-08-18T${TripTimeParser.addMinutesToNow(30)}:00+02:00",
            recommendedStartTime = nowFormatted,
            formattedDuration = "30 min",
            formattedDepartureTime = nowFormatted,
            formattedArrivalTime = TripTimeParser.addMinutesToNow(30),
            transfersCount = 0,
            legs = listOf(walkLeg, metroLeg),
            viability = ItineraryViability.VIABLE_ON_TIME
        )

        val activeTrip = ActiveTripState(
            originName = "Origen",
            destinationName = "Aeroport",
            itinerary = plannedItinerary,
            status = "IN_PROGRESS",
            currentLegIndex = 0,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        val reconciler = TripRealTimeReconciler()
        val result = reconciler.reconcile(
            activeTrip = activeTrip,
            userLat = 39.468,
            userLon = -0.375
        )

        println("==================================================")
        println("TEST RECONCILIATION RESULT:")
        println("Vehicle line: ${result.vehicleLine}")
        println("Vehicle arrival minutes: ${result.vehicleArrivalMinutes}")
        println("Delay minutes: ${result.delayMinutes}")
        println("Is live: ${result.isLive}")
        println("Is leave now alert: ${result.isLeaveNowAlert}")
        println("==================================================")

        // When starting walk leg towards station, the system must NOT trigger false "Leave now / Hurry" for a past/earlier 4-min train
        assertFalse("Should NOT trigger false Leave Now alert", result.isLeaveNowAlert)
    }
}
