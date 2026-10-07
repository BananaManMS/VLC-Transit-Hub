package com.example

import com.example.ui.cercanias.formatEstimatedArrivalTime
import org.junit.Assert.assertEquals
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveTrainArrivalTimeTest {

    @Test
    fun testIsoWithDotsIsFormattedToHourAndMinute() {
        // Renfe sometimes outputs "2026-09-30T11.26:45"
        val input = "2026-09-30T11.26:45"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testIsoWithColonsIsFormattedToHourAndMinute() {
        val input = "2026-09-30T11:26:45"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testShortTimeWithDots() {
        val input = "11.26"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testStandardShortTimeWithColons() {
        val input = "11:26"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testSingleDigitHourPadded() {
        val input = "2026-09-30T9.05:00"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("09:05", formatted)
    }

    @Test
    fun testEmptyInputReturnsBlank() {
        val formatted = formatEstimatedArrivalTime("")
        assertEquals("", formatted)
    }

    @Test
    fun testIsValenciaNucleus40_FiltersOutTrainsOutsideValencia() {
        val cacheManager = com.example.data.repository.renfe.GtfsCacheManager(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )

        // 1. Valid Valencia trains (València Nord, Gandia, Castelló, Utiel)
        val valenciaNordTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "40_C1_1234",
            routeId = "40_C1",
            latitude = 39.466,
            longitude = -0.377,
            status = "STOPPED_AT",
            platform = "1"
        )
        val gandiaTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "24225",
            routeId = "C1",
            latitude = 38.969,
            longitude = -0.181,
            status = "IN_TRANSIT_TO",
            platform = "2"
        )
        val castelloTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "24600",
            routeId = "C6",
            latitude = 39.988,
            longitude = -0.052,
            status = "IN_TRANSIT_TO",
            platform = ""
        )
        val utielTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "24300",
            routeId = "C3",
            latitude = 39.566,
            longitude = -1.206,
            status = "IN_TRANSIT_TO",
            platform = ""
        )

        org.junit.Assert.assertTrue("Valencia Nord must be in Núcleo 40", cacheManager.isValenciaNucleus40(valenciaNordTrain))
        org.junit.Assert.assertTrue("Gandia train must be in Núcleo 40", cacheManager.isValenciaNucleus40(gandiaTrain))
        org.junit.Assert.assertTrue("Castelló train must be in Núcleo 40", cacheManager.isValenciaNucleus40(castelloTrain))
        org.junit.Assert.assertTrue("Utiel train must be in Núcleo 40", cacheManager.isValenciaNucleus40(utielTrain))

        // 2. Trains from other nuclei across Spain (Madrid, Barcelona, Sevilla, Bilbao)
        val madridAtochaTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "10_C3_9999",
            routeId = "10_C3",
            latitude = 40.406,
            longitude = -3.689,
            status = "IN_TRANSIT_TO",
            platform = "3"
        )
        val madridGenericTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "MAD_12345",
            routeId = "C3",
            latitude = 40.406,
            longitude = -3.689,
            status = "IN_TRANSIT_TO",
            platform = "3"
        )
        val barcelonaSantsTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "20_R1_5555",
            routeId = "20_R1",
            latitude = 41.379,
            longitude = 2.140,
            status = "IN_TRANSIT_TO",
            platform = "7"
        )
        val sevillaTrain = com.example.ui.cercanias.LiveVehicleInfo(
            tripId = "30_C1_7777",
            routeId = "30_C1",
            latitude = 37.392,
            longitude = -5.975,
            status = "IN_TRANSIT_TO",
            platform = "1"
        )

        org.junit.Assert.assertFalse("Madrid Atocha train must NOT be in Núcleo 40", cacheManager.isValenciaNucleus40(madridAtochaTrain))
        org.junit.Assert.assertFalse("Madrid generic train must NOT be in Núcleo 40", cacheManager.isValenciaNucleus40(madridGenericTrain))
        org.junit.Assert.assertFalse("Barcelona train must NOT be in Núcleo 40", cacheManager.isValenciaNucleus40(barcelonaSantsTrain))
        org.junit.Assert.assertFalse("Sevilla train must NOT be in Núcleo 40", cacheManager.isValenciaNucleus40(sevillaTrain))
    }

    @Test
    fun testQuickTrackedVehicle_TransferTripCalculations() {
        val now = 1700000000000L
        val leg1Arrival = now + (8 * 60_000L) // +8 min to Alameda
        val finalArrival = now + (18 * 60_000L) // +18 min to Marítim

        val transferVehicle = com.example.data.model.quicktrack.QuickTrackedVehicle(
            lineId = "L3",
            destination = "Rafelbunyol",
            colorHex = "#E53935",
            originStationId = "10",
            originStationName = "Colón",
            initialMinutesRemaining = 3,
            targetArrivalEpochMs = now + (3 * 60_000L),
            isRealTime = true,
            isTransferTrip = true,
            transferStationId = "20",
            transferStationName = "Alameda",
            transferLineId = "L5",
            transferDestination = "Marítim",
            transferArrivalEpochMs = leg1Arrival,
            transferWaitMinutes = 2,
            finalStationId = "30",
            finalStationName = "Marítim",
            finalArrivalEpochMs = finalArrival,
            currentLegIndex = 0
        )

        org.junit.Assert.assertTrue("Should be marked as transfer trip", transferVehicle.isTransferTrip)
        org.junit.Assert.assertTrue("Should be marked as destination alert", transferVehicle.isDestinationAlert)
        assertEquals("Marítim", transferVehicle.effectiveDestinationName())
        assertEquals(3, transferVehicle.liveMinutesRemaining(now))
        assertEquals(8, transferVehicle.minutesRemainingToTransfer(now))
        assertEquals(18, transferVehicle.minutesRemainingToFinalDestination(now))
        assertEquals(18, transferVehicle.minutesRemainingToTarget(now))
    }
}
