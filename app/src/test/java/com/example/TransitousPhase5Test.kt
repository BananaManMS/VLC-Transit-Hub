package com.example

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.TransitMode
import com.example.data.model.transitous.TransitousItineraryDto
import com.example.data.model.transitous.TransitousLegDto
import com.example.data.model.transitous.TransitousPlaceDto
import com.example.data.model.transitous.TransitousPlanResponse
import com.example.data.network.TransitousApiService
import com.example.data.repository.routing.HybridRoutingRepository
import com.example.util.PolylineDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransitousPhase5Test {

    @Test
    fun testPolylineDecoderEdgeCases() {
        // Edge case 1: Empty or blank string
        assertTrue(PolylineDecoder.decode("", precision = 6).isEmpty())
        assertTrue(PolylineDecoder.decode("   ", precision = 6).isEmpty())

        // Edge case 2: Single coordinate point precision 6
        val singlePointEncoded = "cj{gjA|``V"
        val decodedSingle = PolylineDecoder.decode(singlePointEncoded, precision = 6)
        assertFalse(decodedSingle.isEmpty())
        assertTrue(decodedSingle.first().latitude in 39.40..39.55)

        // Edge case 3: Precision 5 vs 6
        val coordsP6 = PolylineDecoder.decodeToCoordinates(singlePointEncoded, precision = 6)
        val coordsP5 = PolylineDecoder.decodeToCoordinates(singlePointEncoded, precision = 5)
        assertNotEquals(coordsP6.first().first, coordsP5.first().first)
    }

    @Test
    fun testNetworkFallbackAndResilience() = runBlocking {
        // Mock API throwing an IOException to simulate network outage / timeout
        val failingApiService = object : TransitousApiService {
            override suspend fun plan(
                fromPlace: String,
                toPlace: String,
                time: String?,
                date: String?,
                arriveBy: Boolean?,
                maxTransfers: Int?,
                modes: String?,
                numItineraries: Int?,
                pageCursor: String?,
                maxWalkDuration: Int?,
                maxWalkDist: Int?
            ): TransitousPlanResponse {
                throw java.io.IOException("Transitous server timeout (8s)")
            }
        }

        val repository = HybridRoutingRepository(transitousApiService = failingApiService)
        val result = repository.planRoute(
            fromLat = 39.4699,
            fromLon = -0.3763,
            toLat = 39.4880,
            toLon = -0.3570
        )

        assertTrue("Result should be failure on network exception", result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()?.message?.contains("timeout") == true)
    }

    @Test
    fun testEmptyItinerariesFallback() = runBlocking {
        // Mock API returning empty itineraries array
        val emptyApiService = object : TransitousApiService {
            override suspend fun plan(
                fromPlace: String,
                toPlace: String,
                time: String?,
                date: String?,
                arriveBy: Boolean?,
                maxTransfers: Int?,
                modes: String?,
                numItineraries: Int?,
                pageCursor: String?,
                maxWalkDuration: Int?,
                maxWalkDist: Int?
            ): TransitousPlanResponse {
                return TransitousPlanResponse(itineraries = emptyList())
            }
        }

        val repository = HybridRoutingRepository(transitousApiService = emptyApiService)
        val result = repository.planRoute(
            fromLat = 39.4699,
            fromLon = -0.3763,
            toLat = 39.4880,
            toLon = -0.3570
        )

        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertNotNull(list)
        assertTrue("Empty list returned gracefully without crash", list!!.isEmpty())
    }

    @Test
    fun testItinerarySortingByViabilityAndDuration() = runBlocking {
        val fakeApiService = object : TransitousApiService {
            override suspend fun plan(
                fromPlace: String,
                toPlace: String,
                time: String?,
                date: String?,
                arriveBy: Boolean?,
                maxTransfers: Int?,
                modes: String?,
                numItineraries: Int?,
                pageCursor: String?,
                maxWalkDuration: Int?,
                maxWalkDist: Int?
            ): TransitousPlanResponse {
                return TransitousPlanResponse(
                    itineraries = listOf(
                        // Long 25 min walking-only
                        TransitousItineraryDto(
                            id = "itin_walk",
                            duration = 1500L,
                            startTime = "2026-08-14T16:00:00Z",
                            endTime = "2026-08-14T16:25:00Z",
                            transfers = 0,
                            legs = listOf(
                                TransitousLegDto(
                                    mode = "WALK",
                                    duration = 1500L,
                                    distance = 1800.0,
                                    from = TransitousPlaceDto(name = "Origen", lat = 39.4699, lon = -0.3763),
                                    to = TransitousPlaceDto(name = "Destino", lat = 39.4880, lon = -0.3570)
                                )
                            )
                        ),
                        // 10 min Metro
                        TransitousItineraryDto(
                            id = "itin_metro",
                            duration = 600L,
                            startTime = "2026-08-14T16:00:00Z",
                            endTime = "2026-08-14T16:10:00Z",
                            transfers = 0,
                            legs = listOf(
                                TransitousLegDto(
                                    mode = "WALK",
                                    duration = 120L,
                                    from = TransitousPlaceDto(name = "Origen", lat = 39.4699, lon = -0.3763),
                                    to = TransitousPlaceDto(name = "Xàtiva", stopId = "es-Metro-de-Valencia_71")
                                ),
                                TransitousLegDto(
                                    mode = "SUBWAY",
                                    duration = 360L,
                                    agencyName = "Metrovalencia",
                                    routeShortName = "3",
                                    from = TransitousPlaceDto(name = "Xàtiva", stopId = "es-Metro-de-Valencia_71"),
                                    to = TransitousPlaceDto(name = "Facultats", stopId = "es-Metro-de-Valencia_68")
                                ),
                                TransitousLegDto(
                                    mode = "WALK",
                                    duration = 120L,
                                    from = TransitousPlaceDto(name = "Facultats", stopId = "es-Metro-de-Valencia_68"),
                                    to = TransitousPlaceDto(name = "Destino", lat = 39.4880, lon = -0.3570)
                                )
                            )
                        )
                    )
                )
            }
        }

        val repository = HybridRoutingRepository(transitousApiService = fakeApiService)
        val result = repository.planRoute(fromLat = 39.4699, fromLon = -0.3763, toLat = 39.4880, toLon = -0.3570)

        assertTrue(result.isSuccess)
        val itineraries = result.getOrNull()!!
        assertEquals(2, itineraries.size)

        // Metro itinerary should be prioritized
        assertEquals("itin_metro", itineraries[0].id)
        assertEquals(TransitMode.SUBWAY, itineraries[0].legs[1].mode)
    }
}
