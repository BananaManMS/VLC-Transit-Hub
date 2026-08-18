package com.example

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.TransitMode
import com.example.data.model.transitous.TransitousItineraryDto
import com.example.data.model.transitous.TransitousLegDto
import com.example.data.model.transitous.TransitousPlaceDto
import com.example.data.model.transitous.TransitousPlanResponse
import com.example.data.network.TransitousApiService
import com.example.data.repository.routing.HybridRoutingRepository
import com.example.data.repository.routing.TransitIdMapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransitousPhase2Test {

    @Test
    fun testTransitIdMapperEmtStop() {
        assertEquals("1234", TransitIdMapper.extractEmtStopNumber("es-EMT-Valencia_1234"))
        assertEquals("567", TransitIdMapper.extractEmtStopNumber("567"))
        assertEquals("890", TransitIdMapper.extractEmtStopNumber(null, "Parada 890 - Plaça de l'Ajuntament"))
    }

    @Test
    fun testTransitIdMapperMetroStation() {
        assertEquals(71, TransitIdMapper.extractMetroStationId("es-Metro-de-Valencia_71"))
        assertEquals(71, TransitIdMapper.extractMetroStationId(null, "Xàtiva"))
        assertEquals(70, TransitIdMapper.extractMetroStationId(null, "Colón"))
        assertEquals(69, TransitIdMapper.extractMetroStationId(null, "Alameda"))
        assertEquals(68, TransitIdMapper.extractMetroStationId(null, "Facultats - Manuel Broseta"))
    }

    @Test
    fun testTransitIdMapperLines() {
        assertEquals("9", TransitIdMapper.extractMetroLine("9", "Línea 9"))
        assertEquals("3", TransitIdMapper.extractMetroLine("L3", null))
        assertEquals("C-1", TransitIdMapper.extractCercaniasLine("C1", "Línea C-1"))
        assertEquals("C-2", TransitIdMapper.extractCercaniasLine("C-2", null))
    }

    @Test
    fun testTransitModeParsing() {
        assertEquals(TransitMode.WALK, TransitMode.fromString("WALK"))
        assertEquals(TransitMode.BUS, TransitMode.fromString("BUS"))
        assertEquals(TransitMode.SUBWAY, TransitMode.fromString("SUBWAY"))
        assertEquals(TransitMode.SUBWAY, TransitMode.fromString("METRO"))
        assertEquals(TransitMode.TRAM, TransitMode.fromString("TRAM"))
        assertEquals(TransitMode.RAIL, TransitMode.fromString("RAIL"))
        assertEquals(TransitMode.BICYCLE, TransitMode.fromString("BIKE"))
    }

    @Test
    fun testHybridRoutingPlanRouteMapping() = runBlocking {
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
                        TransitousItineraryDto(
                            duration = 900L,
                            startTime = "2026-08-14T16:00:00Z",
                            endTime = "2026-08-14T16:15:00Z",
                            transfers = 0,
                            legs = listOf(
                                TransitousLegDto(
                                    mode = "WALK",
                                    duration = 180L,
                                    distance = 200.0,
                                    from = TransitousPlaceDto(name = "Origen", lat = 39.4699, lon = -0.3763),
                                    to = TransitousPlaceDto(name = "Xàtiva", stopId = "es-Metro-de-Valencia_71", lat = 39.4671, lon = -0.3773)
                                ),
                                TransitousLegDto(
                                    mode = "SUBWAY",
                                    duration = 480L,
                                    agencyName = "Metro Valencia",
                                    routeShortName = "3",
                                    routeColor = "E62238",
                                    headsign = "Rafelbunyol",
                                    from = TransitousPlaceDto(name = "Xàtiva", stopId = "es-Metro-de-Valencia_71", lat = 39.4671, lon = -0.3773),
                                    to = TransitousPlaceDto(name = "Benimaclet", stopId = "es-Metro-de-Valencia_42", lat = 39.4862, lon = -0.3591)
                                ),
                                TransitousLegDto(
                                    mode = "WALK",
                                    duration = 240L,
                                    distance = 250.0,
                                    from = TransitousPlaceDto(name = "Benimaclet", stopId = "es-Metro-de-Valencia_42", lat = 39.4862, lon = -0.3591),
                                    to = TransitousPlaceDto(name = "Destino", lat = 39.4880, lon = -0.3570)
                                )
                            )
                        )
                    )
                )
            }
        }

        val repository = HybridRoutingRepository(transitousApiService = fakeApiService)
        val result = repository.planRoute(
            fromLat = 39.4699,
            fromLon = -0.3763,
            toLat = 39.4880,
            toLon = -0.3570
        )

        assertTrue(result.isSuccess)
        val itineraries = result.getOrNull()
        assertNotNull(itineraries)
        assertEquals(1, itineraries!!.size)

        val itin = itineraries[0]
        assertEquals(900L, itin.totalDurationSeconds)
        assertEquals("15 min", itin.formattedDuration)
        assertEquals(0, itin.transfersCount)
        assertEquals(3, itin.legs.size)
        assertEquals(450.0, itin.totalWalkDistanceMeters, 0.1)
        assertEquals(420L, itin.totalWalkDurationSeconds)
        assertEquals(TransitMode.WALK, itin.legs[0].mode)
        assertEquals(TransitMode.SUBWAY, itin.legs[1].mode)
        assertEquals("L3", itin.legs[1].routeShortName)
        assertEquals("C41833", itin.legs[1].routeColorHex)
    }
}
