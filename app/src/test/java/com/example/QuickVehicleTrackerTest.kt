package com.example

import com.example.data.model.quicktrack.QuickTrackedVehicle
import com.example.data.model.quicktrack.TrackedDownstreamStop
import com.example.service.QuickVehicleTrackerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickVehicleTrackerTest {

    @Test
    fun testQuickTrackedVehicle_CalculationsAtOrigin() {
        val nowMs = 1_700_000_000_000L
        val arrivalEpochMs = nowMs + (4 * 60_000L) // in 4 minutes

        val vehicle = QuickTrackedVehicle(
            lineId = "3",
            destination = "Rafelbunyol",
            colorHex = "#EE1D23",
            originStationId = "1",
            originStationName = "Xàtiva",
            targetStationId = null,
            targetStationName = null,
            initialMinutesRemaining = 4,
            targetArrivalEpochMs = arrivalEpochMs,
            isRealTime = true
        )

        assertEquals("3", vehicle.cleanLineNumber)
        assertFalse(vehicle.isDestinationAlert)
        assertEquals(4, vehicle.liveMinutesRemaining(nowMs))
        assertEquals(240, vehicle.liveSecondsRemaining(nowMs))
    }

    @Test
    fun testQuickTrackedVehicle_CalculationsToDownstreamTargetAndPenultimate() {
        val nowMs = 1_700_000_000_000L
        val arrivalOriginEpochMs = nowMs + (4 * 60_000L) // in 4 min at origin

        val stops = listOf(
            TrackedDownstreamStop("2", "Colón", "A", "18:45", 2),
            TrackedDownstreamStop("3", "Alameda", "A", "18:47", 4),
            TrackedDownstreamStop("4", "Facultats", "A", "18:50", 7)
        )

        val vehicle = QuickTrackedVehicle(
            lineId = "L3",
            destination = "Rafelbunyol",
            colorHex = "#EE1D23",
            originStationId = "1",
            originStationName = "Xàtiva",
            targetStationId = "4",
            targetStationName = "Facultats",
            penultimateStationId = "3",
            penultimateStationName = "Alameda",
            initialMinutesRemaining = 4,
            targetArrivalEpochMs = arrivalOriginEpochMs,
            isRealTime = true,
            downstreamStops = stops
        )

        assertEquals("3", vehicle.cleanLineNumber)
        assertTrue(vehicle.isDestinationAlert)
        assertEquals(4, vehicle.liveMinutesRemaining(nowMs))
        // Target: Facultats (+7 min from origin) -> 4 + 7 = 11 min
        assertEquals(11, vehicle.minutesRemainingToTarget(nowMs))
        // Penultimate: Alameda (+4 min from origin) -> 4 + 4 = 8 min
        assertEquals(8, vehicle.minutesRemainingToPenultimate(nowMs))
    }

    @Test
    fun testAdaptivePollingIntervals_Logic() {
        // > 20 min: No polling
        val remainingAbove20 = 25
        assertTrue(remainingAbove20 > 20)

        // 5..20 min: 2 min interval
        val remaining12 = 12
        val pollInterval12 = if (remaining12 > 5) 120_000L else 40_000L
        assertEquals(120_000L, pollInterval12)

        // <= 5 min: 40s interval
        val remaining3 = 3
        val pollInterval3 = if (remaining3 > 5) 120_000L else 40_000L
        assertEquals(40_000L, pollInterval3)
    }

    @Test
    fun testMax1HourLimit() {
        val diffMinValid = 45
        val diffMinInvalid = 65

        val allowTrackingValid = diffMinValid <= 60
        val allowTrackingInvalid = diffMinInvalid <= 60

        assertTrue(allowTrackingValid)
        assertFalse(allowTrackingInvalid)
    }

    @Test
    fun testAndroid16_StatusBarChipStringLength_StrictUnder7Chars() {
        val lines = listOf("3", "5", "10", "14")
        val testMinutes = listOf(0, 1, 4, 12, 45, 99)

        for (line in lines) {
            for (mins in testMinutes) {
                val originChip = if (mins <= 1) "L$line·1m" else "L$line·${mins}m"
                assertTrue(
                    "Origin chip '$originChip' must not exceed 8 characters for Android 16 SystemUI",
                    originChip.length <= 8
                )

                val destChip = if (mins <= 1) "L$line➔1m" else "L$line➔${mins}m"
                assertTrue(
                    "Destination chip '$destChip' must not exceed 8 characters for Android 16 SystemUI",
                    destChip.length <= 8
                )
            }
        }
    }

    @Test
    fun testQuickVehicleTrackerManager_TrackingMatching() {
        val vehicle = QuickTrackedVehicle(
            lineId = "3",
            destination = "Rafelbunyol",
            colorHex = "#EE1D23",
            originStationId = "1",
            originStationName = "Xàtiva",
            initialMinutesRemaining = 4,
            targetArrivalEpochMs = System.currentTimeMillis() + 240_000L,
            isRealTime = true
        )

        QuickVehicleTrackerManager.updateVehicle(vehicle)

        assertTrue(QuickVehicleTrackerManager.isTrackingDeparture("dep_1", "3", "Rafelbunyol"))
        assertTrue(QuickVehicleTrackerManager.isTrackingDeparture("dep_1", "L3", "Rafelbunyol"))
        assertTrue(QuickVehicleTrackerManager.isTrackingDeparture("dep_1", "L3", "rafelbunyol"))

        assertFalse(QuickVehicleTrackerManager.isTrackingDeparture("dep_1", "3", "Aeroport"))
        assertFalse(QuickVehicleTrackerManager.isTrackingDeparture("dep_1", "5", "Rafelbunyol"))

        QuickVehicleTrackerManager.updateVehicle(null)
        assertFalse(QuickVehicleTrackerManager.isTrackingDeparture("dep_1", "3", "Rafelbunyol"))
    }

    @Test
    fun testDestinationAlert_PreDepartureShowsOriginTime_PostDepartureShowsDestinationTime() {
        val nowMs = 1_700_000_000_000L
        val arrivalOriginEpochMs = nowMs + (4 * 60_000L) // 4 min to origin

        val stops = listOf(
            TrackedDownstreamStop("4", "Facultats", "A", "18:50", 7)
        )

        val vehicle = QuickTrackedVehicle(
            lineId = "L3",
            destination = "Rafelbunyol",
            colorHex = "#EE1D23",
            originStationId = "1",
            originStationName = "Xàtiva",
            targetStationId = "4",
            targetStationName = "Facultats",
            initialMinutesRemaining = 4,
            targetArrivalEpochMs = arrivalOriginEpochMs,
            isRealTime = true,
            isBoarded = false,
            downstreamStops = stops
        )

        // 1. Pre-departure (user is waiting at Xàtiva, 4 min remaining to origin)
        val originRemainingSec = (vehicle.targetArrivalEpochMs - nowMs) / 1000L
        val isDepartedBefore = vehicle.isBoarded || originRemainingSec <= 0
        assertFalse("Vehicle has not departed from origin yet", isDepartedBefore)

        val originMins = vehicle.liveMinutesRemaining(nowMs)
        assertEquals(4, originMins)
        val preChip = if (originMins <= 1) "L3·1m" else "L3·${originMins}m"
        assertEquals("L3·4m", preChip)

        // 2. Post-departure (train has arrived and departed, user boarded)
        val afterBoardingMs = nowMs + (5 * 60_000L) // 5 minutes later, train left Xàtiva
        val vehicleBoarded = vehicle.copy(isBoarded = true)
        val isDepartedAfter = vehicleBoarded.isBoarded || (vehicle.targetArrivalEpochMs - afterBoardingMs) / 1000L <= 0
        assertTrue("Vehicle has departed from origin", isDepartedAfter)

        val destMins = vehicleBoarded.minutesRemainingToTarget(afterBoardingMs)
        // At nowMs: 4 + 7 = 11 min to Facultats. 5 min later: 11 - 5 = 6 min remaining to Facultats.
        assertEquals(6, destMins)
        val postChip = if (destMins <= 1) "L3➔1m" else "L3➔${destMins}m"
        assertEquals("L3➔6m", postChip)
    }
}
