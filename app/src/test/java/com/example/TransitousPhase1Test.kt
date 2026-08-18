package com.example

import com.example.data.model.transitous.TransitousPlanResponse
import com.example.data.network.NetworkModule
import com.example.util.PolylineDecoder
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TransitousPhase1Test {

    @Test
    fun testPolylineDecoderPrecision6() {
        // Sample polyline from MOTIS 2 with precision 6 in Valencia: Xàtiva -> Facultats
        val encoded = "cj{gjA|``VccTye]n_G~|Bnm@dvA|rAftE}aKmiL"
        val points = PolylineDecoder.decode(encoded, precision = 6)

        assertFalse("Decoded points should not be empty", points.isEmpty())
        
        val firstPoint = points.first()
        val lastPoint = points.last()

        // Verify latitude and longitude are within Valencia bounding box (39.4 ~ 39.5, -0.4 ~ -0.3)
        assertTrue("First lat should be in Valencia (~39.46): ${firstPoint.latitude}", firstPoint.latitude in 39.40..39.55)
        assertTrue("First lon should be in Valencia (~ -0.37): ${firstPoint.longitude}", firstPoint.longitude in -0.45..-0.30)
        
        assertTrue("Last lat should be in Valencia (~39.47): ${lastPoint.latitude}", lastPoint.latitude in 39.40..39.55)
        assertTrue("Last lon should be in Valencia (~ -0.36): ${lastPoint.longitude}", lastPoint.longitude in -0.45..-0.30)
    }

    @Test
    fun testTransitousJsonResponseDeserialization() {
        val sampleJson = """
        {
          "itineraries": [
            {
              "duration": 1020,
              "startTime": "2026-08-14T15:53:00Z",
              "endTime": "2026-08-14T16:10:00Z",
              "transfers": 0,
              "id": "test-itin-123",
              "legs": [
                {
                  "mode": "WALK",
                  "duration": 360,
                  "distance": 450.0,
                  "from": {
                    "name": "Estación del Norte",
                    "lat": 39.4671,
                    "lon": -0.3773
                  },
                  "to": {
                    "name": "Xàtiva",
                    "stopId": "es-Metro-de-Valencia_71",
                    "lat": 39.4672,
                    "lon": -0.3774
                  }
                },
                {
                  "mode": "SUBWAY",
                  "duration": 300,
                  "agencyName": "Metro Valencia",
                  "routeShortName": "9",
                  "routeColor": "b7dd79",
                  "routeTextColor": "000000",
                  "headsign": "Alboraia Peris Aragó",
                  "from": {
                    "name": "Xàtiva",
                    "stopId": "es-Metro-de-Valencia_71",
                    "lat": 39.467186,
                    "lon": -0.377375,
                    "departure": "2026-08-14T15:59:00Z"
                  },
                  "to": {
                    "name": "Facultats - Manuel Broseta",
                    "stopId": "es-Metro-de-Valencia_68",
                    "lat": 39.478004,
                    "lon": -0.361905,
                    "arrival": "2026-08-14T16:04:00Z"
                  },
                  "intermediateStops": [
                    { "name": "Colón", "stopId": "es-Metro-de-Valencia_70", "lat": 39.4701, "lon": -0.3709 },
                    { "name": "Alameda", "stopId": "es-Metro-de-Valencia_69", "lat": 39.4731, "lon": -0.3653 }
                  ],
                  "legGeometry": {
                    "points": "cj{gjA|``VccTye]n_G~|Bnm@dvA|rAftE}aKmiL",
                    "precision": 6,
                    "length": 6
                  }
                }
              ]
            }
          ],
          "previousPageCursor": "EARLIER|1786722660",
          "nextPageCursor": "LATER|1786723620"
        }
        """.trimIndent()

        val gson = Gson()
        val response = gson.fromJson(sampleJson, TransitousPlanResponse::class.java)

        assertNotNull(response)
        assertNotNull(response.itineraries)
        assertEquals(1, response.itineraries?.size)

        val itinerary = response.itineraries!!.first()
        assertEquals(1020L, itinerary.duration)
        assertEquals(0, itinerary.transfers)
        assertEquals("test-itin-123", itinerary.id)
        assertEquals(2, itinerary.legs.size)

        val walkLeg = itinerary.legs[0]
        assertEquals("WALK", walkLeg.mode)
        assertEquals(360L, walkLeg.duration)
        assertEquals(450.0, walkLeg.distance ?: 0.0, 0.01)

        val subwayLeg = itinerary.legs[1]
        assertEquals("SUBWAY", subwayLeg.mode)
        assertEquals("Metro Valencia", subwayLeg.agencyName)
        assertEquals("9", subwayLeg.routeShortName)
        assertEquals("b7dd79", subwayLeg.routeColor)
        assertEquals("Xàtiva", subwayLeg.from?.name)
        assertEquals("Facultats - Manuel Broseta", subwayLeg.to?.name)
        assertEquals(2, subwayLeg.intermediateStops?.size)
        assertEquals("Colón", subwayLeg.intermediateStops?.get(0)?.name)
        assertEquals("Alameda", subwayLeg.intermediateStops?.get(1)?.name)
        assertEquals(6, subwayLeg.legGeometry?.precision)
    }

    @Test
    fun testNetworkModuleTransitousConfig() {
        assertNotNull(NetworkModule.transitousRetrofit)
        assertNotNull(NetworkModule.transitousApiService)
        assertEquals("https://api.transitous.org/api/v2/", NetworkModule.transitousRetrofit.baseUrl().toString())
    }
}
