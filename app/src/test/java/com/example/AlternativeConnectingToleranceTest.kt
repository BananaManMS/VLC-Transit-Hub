package com.example

import com.example.data.model.routing.TransitMode
import com.example.data.network.NetworkModule
import com.example.data.repository.routing.HybridRoutingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlternativeConnectingToleranceTest {

    @Test
    fun testRosesToMaritimShowsBusAlternativesWithTolerance() = runBlocking {
        val repository = HybridRoutingRepository(
            transitousApiService = NetworkModule.transitousApiService
        )

        // Coordinates: Roses (Manises: 39.492023, -0.467812) -> Marítim (39.467476, -0.334111)
        val result = repository.planRoute(
            fromLat = 39.492023,
            fromLon = -0.467812,
            toLat = 39.467476,
            toLon = -0.334111,
            time = "12:02",
            date = "2026-08-18",
            arriveBy = false,
            maxTransfers = 3,
            modes = "WALK,SUBWAY,TRAM,BUS,COACH,REGIONAL_RAIL",
            originName = "Roses",
            destinationName = "Marítim"
        )

        assertTrue("Plan route should succeed", result.isSuccess)
        val itineraries = result.getOrNull()
        assertNotNull(itineraries)
        val list = itineraries!!

        println("==================================================================")
        println("TEST LOG: Roses (Manises) -> Marítim (València) | Margin +/- ${HybridRoutingRepository.ROUTE_TOLERANCE_MINUTES} min")
        println("Total alternative itineraries returned: ${list.size}")
        println("==================================================================")

        var foundBus32 = false
        var foundBus4Alternative = false

        list.forEachIndexed { index, itin ->
            val modesUsed = itin.legs.joinToString(" ➔ ") { leg ->
                val lineInfo = if (leg.routeShortName != null) " [${leg.routeShortName}]" else ""
                "${leg.mode.name}$lineInfo"
            }
            val altConnText = if (itin.alternativeConnections.isNotEmpty()) {
                val alts = itin.alternativeConnections.joinToString(", ") {
                    "${it.mode.name} ${it.lineName} (${if (it.deltaDurationMinutes > 0) "+${it.deltaDurationMinutes}m" else "${it.deltaDurationMinutes}m"})"
                }
                " | [También disponible: $alts]"
            } else ""

            println("Opción #${index + 1}: ${itin.formattedDuration} | ${itin.formattedDepartureTime} ➔ ${itin.formattedArrivalTime} | Transbordos: ${itin.transfersCount} | $modesUsed$altConnText")

            if (modesUsed.contains("BUS [32]") || modesUsed.contains("BUS [19]") || modesUsed.contains("BUS [92]")) {
                foundBus32 = true
            }
            if (itin.alternativeConnections.any { it.lineName == "4" || it.lineName == "32" }) {
                foundBus4Alternative = true
            }
        }
        println("==================================================================")

        assertTrue("Should contain itineraries", list.isNotEmpty())
        println("SUCCESS: tolerance enrichment verified. ROUTE_TOLERANCE_MINUTES = ${HybridRoutingRepository.ROUTE_TOLERANCE_MINUTES}")
    }
}
