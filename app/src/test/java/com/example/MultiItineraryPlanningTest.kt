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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiItineraryPlanningTest {

    @Test
    fun testColontoBlascoIbanezReturnsMultipleItinerariesSortedByDepartureTime() = runBlocking {
        val repository = HybridRoutingRepository(
            transitousApiService = NetworkModule.transitousApiService
        )

        // Coordinates for Colón (39.4738, -0.3789) -> Blasco Ibáñez (39.4801, -0.3606)
        val result = repository.planRoute(
            fromLat = 39.4738,
            fromLon = -0.3789,
            toLat = 39.4801,
            toLon = -0.3606,
            time = "11:30",
            date = "2026-08-18",
            arriveBy = false,
            maxTransfers = 3,
            modes = "WALK,SUBWAY,TRAM,BUS,COACH,REGIONAL_RAIL",
            originName = "Colón",
            destinationName = "Blasco Ibáñez"
        )

        assertTrue("Plan route should succeed", result.isSuccess)
        val itineraries = result.getOrNull()
        assertNotNull(itineraries)
        val list = itineraries!!

        val minDuration = list.minOf { it.totalDurationSeconds }

        println("==================================================================")
        println("REAL QUERY TEST LOG: Colón -> Blasco Ibáñez (11:30h)")
        println("Sorting criterion: SALIDA MÁS PRÓXIMA PRIMERO (startTime ASC)")
        println("Total alternative itineraries parsed & sorted: ${list.size}")
        println("==================================================================")

        list.forEachIndexed { index, itin ->
            val modesUsed = itin.legs.joinToString(" ➔ ") { leg ->
                val lineInfo = if (leg.routeShortName != null) " [${leg.routeShortName}]" else ""
                "${leg.mode.name}$lineInfo"
            }
            val fastestTag = if (itin.totalDurationSeconds == minDuration) " ⭐ [MÁS RÁPIDA]" else ""
            println("Opción #${index + 1}: Sale a las ${itin.formattedDepartureTime} | Llega a las ${itin.formattedArrivalTime} | Duración: ${itin.formattedDuration} (${itin.totalDurationSeconds / 60}m)$fastestTag | Transbordos: ${itin.transfersCount} | $modesUsed")
        }
        println("==================================================================")

        // Verify that all itineraries from API are returned (at least 5-7 alternatives)
        assertTrue("Should return multiple alternative itineraries (found: ${list.size})", list.size >= 5)

        // Verify sorted by departure time ascending
        for (i in 0 until list.size - 1) {
            val t1 = Instant.parse(list[i].startTime).toEpochMilli()
            val t2 = Instant.parse(list[i + 1].startTime).toEpochMilli()
            assertTrue(
                "Itineraries must be sorted by departure time ascending: $t1 <= $t2",
                t1 <= t2
            )
        }
    }
}

