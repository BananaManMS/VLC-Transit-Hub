package com.example

import com.example.data.database.GeoportalStopEntity
import com.example.data.model.NominatimResult
import com.example.ui.bus.computeAddressSearchScore
import com.example.ui.bus.computeSearchScore
import com.example.ui.map.MapSearchResult
import com.example.ui.metro.computeMetroSearchScore
import com.example.data.model.MetroStation
import com.example.util.isBilingualTokenMatch
import com.example.util.normalizeForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceTest {

    @Test
    fun testRelevanceOrdering_ExactRemoteBeatsWeakLocal() {
        val query = "Colon"

        // 1. A local bus stop that only weakly matches in a peripheral word
        val weakLocalStop = GeoportalStopEntity(
            id_parada = "1234",
            denominacion = "Hospital General de Valencia",
            suprimida = 0,
            lat = 39.4697,
            lon = -0.3734,
            lineas = "99"
        )
        val weakLocalScore = computeSearchScore(weakLocalStop, query) // 0.0

        // 2. A remote address that exactly matches "Carrer de Colon"
        val exactRemote = NominatimResult(
            displayName = "Carrer de Colon, 14, Valencia",
            latitude = 39.4699,
            longitude = -0.3755,
            type = "road",
            category = "highway",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        val exactRemoteScore = computeAddressSearchScore(exactRemote, query)

        val localCandidate = MapSearchResult.BusStop(weakLocalStop, null, weakLocalScore)
        val remoteCandidate = MapSearchResult.Address(exactRemote, exactRemoteScore)

        val combinedList = listOf(localCandidate, remoteCandidate)
        val sortedList = combinedList.sortedWith(
            compareByDescending<MapSearchResult> { it.score }
                .thenByDescending { it !is MapSearchResult.Address }
        )

        assertEquals("Exact remote address should rank higher than non-matching/weak local stop", remoteCandidate, sortedList.first())
    }

    @Test
    fun testTiebreaker_LocalOverRemoteWhenScoresEqual() {
        val query = "Xativa"

        // Local station with exact match score
        val localStop = GeoportalStopEntity(
            id_parada = "9999",
            denominacion = "Xàtiva",
            suprimida = 0,
            lat = 39.4666,
            lon = -0.3777,
            lineas = "5"
        )
        val localScore = computeSearchScore(localStop, query)

        // Remote address with exact match score
        val remoteAddress = NominatimResult(
            displayName = "Xàtiva, València",
            latitude = 39.4666,
            longitude = -0.3777,
            type = "city",
            category = "place",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        val remoteScore = computeAddressSearchScore(remoteAddress, query)

        val localCandidate = MapSearchResult.BusStop(localStop, null, localScore)
        val remoteCandidate = MapSearchResult.Address(remoteAddress, remoteScore)

        val combinedList = listOf(remoteCandidate, localCandidate)
        val sortedList = combinedList.sortedWith(
            compareByDescending<MapSearchResult> { it.score }
                .thenByDescending { it !is MapSearchResult.Address }
        )

        assertEquals("When scores are similar/equal, local stop must win the tiebreaker", localCandidate, sortedList.first())
    }

    @Test
    fun testBilingualAndAccentTolerance() {
        // Accents
        assertEquals("xativa", "Xàtiva".normalizeForSearch())
        assertEquals("colon", "Colón".normalizeForSearch())
        assertEquals("angel guimera", "Àngel Guimerà".normalizeForSearch())

        // Bilingual token equivalence
        assertTrue(isBilingualTokenMatch("jativa", "xativa"))
        assertTrue(isBilingualTokenMatch("xativa", "jativa"))
        assertTrue(isBilingualTokenMatch("ayuntamiento", "ajuntament"))
        assertTrue(isBilingualTokenMatch("ajuntament", "ayuntamiento"))
        assertTrue(isBilingualTokenMatch("calle", "carrer"))
        assertTrue(isBilingualTokenMatch("carrer", "calle"))

        // Bus stop search with bilingual query
        val stop = GeoportalStopEntity(
            id_parada = "1001",
            denominacion = "Plaça de l'Ajuntament",
            suprimida = 0,
            lat = 39.4699,
            lon = -0.3762,
            lineas = "6,8"
        )
        val scoreCastilian = computeSearchScore(stop, "Ayuntamiento")
        assertTrue("Searching 'Ayuntamiento' should match 'Plaça de l'Ajuntament'", scoreCastilian >= 150.0)

        val scoreValencian = computeSearchScore(stop, "Ajuntament")
        assertTrue("Searching 'Ajuntament' should match 'Plaça de l'Ajuntament'", scoreValencian >= 150.0)
    }

    @Test
    fun testAjuntamentValencia_LocalStopsAndValenciaTownhallWinWithoutBooster() {
        val query = "Ajuntament Valencia"

        // 1. Local stop: Plaça de l'Ajuntament (EMT Valencia) -> matches "ajuntament" -> 150.0
        val localStop = GeoportalStopEntity(
            id_parada = "1001",
            denominacion = "Plaça de l'Ajuntament",
            suprimida = 0,
            lat = 39.4699,
            lon = -0.3762,
            lineas = "6,8,10,11"
        )
        val localScore = computeSearchScore(localStop, query)

        // 2. Remote Nominatim result from another municipality (e.g. Mislata) -> matches "ajuntament" -> 150.0
        val remoteMislata = NominatimResult(
            displayName = "Ajuntament de Mislata, 8, Plaça de la Constitució, Mislata, l'Horta Sud, València, Comunitat Valenciana, 46920, Espanya",
            latitude = 39.475197,
            longitude = -0.418047,
            type = "townhall",
            category = "amenity",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        val remoteMislataScore = computeAddressSearchScore(remoteMislata, query)

        // 3. Remote Nominatim result from Valencia city itself -> matches "ajuntament" + "valencia" -> 300.0
        val remoteValenciaCity = NominatimResult(
            displayName = "Ajuntament de València, Plaça de l'Ajuntament, Ciutat Vella, València, Comarca de València, València, Comunitat Valenciana, 46002, Espanya",
            latitude = 39.469797,
            longitude = -0.377199,
            type = "townhall",
            category = "amenity",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        val remoteValenciaScore = computeAddressSearchScore(remoteValenciaCity, query)

        println("=== Query: 'Ajuntament Valencia' ===")
        println("Local Stop 'Plaça de l'Ajuntament' Score: $localScore")
        println("Remote Mislata Townhall Score: $remoteMislataScore")
        println("Remote Valencia Townhall Score: $remoteValenciaScore")

        // Valencia townhall (300.0) wins first place overall
        assertTrue(remoteValenciaScore > remoteMislataScore)
        assertTrue(remoteValenciaScore > localScore)

        // Local stop (150.0) ties with other municipality townhall (150.0), and localStop tiebreaker gives precedence to the local stop
        assertEquals(150.0, localScore, 0.01)
        assertEquals(150.0, remoteMislataScore, 0.01)
    }

    @Test
    fun testCrossCheck_AjuntamentMislata() {
        val query = "Ajuntament Mislata"

        // 1. Remote Nominatim result for Mislata Townhall
        val remoteMislata = NominatimResult(
            displayName = "Ajuntament de Mislata, 8, Plaça de la Constitució, Mislata, l'Horta Sud, València, Comunitat Valenciana, 46920, Espanya",
            latitude = 39.475197,
            longitude = -0.418047,
            type = "townhall",
            category = "amenity",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        val remoteMislataScore = computeAddressSearchScore(remoteMislata, query)

        // 2. Local EMT stop 'Plaça de l'Ajuntament' (Valencia capital)
        val localValenciaStop = GeoportalStopEntity(
            id_parada = "1001",
            denominacion = "Plaça de l'Ajuntament",
            suprimida = 0,
            lat = 39.4699,
            lon = -0.3762,
            lineas = "6,8,10,11"
        )
        val localValenciaScore = computeSearchScore(localValenciaStop, query)

        // 3. Local stop located in Mislata (e.g. Mislata Metro or local bus stop if any)
        val localMislataStop = GeoportalStopEntity(
            id_parada = "2050",
            denominacion = "Mislata - Ajuntament",
            suprimida = 0,
            lat = 39.4750,
            lon = -0.4180,
            lineas = "150"
        )
        val localMislataScore = computeSearchScore(localMislataStop, query)

        println("=== Query: 'Ajuntament Mislata' ===")
        println("Remote Mislata Townhall (Nominatim) Score: $remoteMislataScore")
        println("Local Stop 'Plaça de l'Ajuntament' (Valencia capital) Score: $localValenciaScore")
        println("Local Stop 'Mislata - Ajuntament' Score: $localMislataScore")

        assertTrue("Remote Mislata townhall (300.0) must cleanly beat Valencia capital stops (150.0)", remoteMislataScore > localValenciaScore)
    }

    @Test
    fun testEvaluation_IsBoosterNeededForAjuntamentValencia() {
        val query = "Ajuntament Valencia"

        // Let's test with and without booster:
        // Without booster, 'Plaça de l'Ajuntament' matches 1 token ("ajuntament") -> 150.0
        // Remote 'Ajuntament de Mislata' matches 1 token ("ajuntament") against mainTitle ("Ajuntament de Mislata") -> 150.0
        // Because "Mislata" != "Valencia".
        // Remote 'Ajuntament de València' matches 2 tokens ("ajuntament", "valencia") against mainTitle ("Ajuntament de València") -> 300.0

        val remoteValenciaCity = NominatimResult(
            displayName = "Ajuntament de València, Plaça de l'Ajuntament, Ciutat Vella, València, Comarca de València, València, Comunitat Valenciana, 46002, Espanya",
            latitude = 39.469797,
            longitude = -0.377199,
            type = "townhall",
            category = "amenity",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        val remoteMislata = NominatimResult(
            displayName = "Ajuntament de Mislata, 8, Plaça de la Constitució, Mislata, l'Horta Sud, València, Comunitat Valenciana, 46920, Espanya",
            latitude = 39.475197,
            longitude = -0.418047,
            type = "townhall",
            category = "amenity",
            isLocalStop = false,
            stopId = null,
            stopType = null
        )
        val valenciaTownhallScore = computeAddressSearchScore(remoteValenciaCity, query)
        val mislataTownhallScore = computeAddressSearchScore(remoteMislata, query)

        println("=== Evaluation: MainTitle Matching without booster ===")
        println("Ajuntament de València (mainTitle match 2 tokens): $valenciaTownhallScore")
        println("Ajuntament de Mislata (mainTitle match 1 token): $mislataTownhallScore")

        assertTrue(valenciaTownhallScore > mislataTownhallScore)
    }
}
