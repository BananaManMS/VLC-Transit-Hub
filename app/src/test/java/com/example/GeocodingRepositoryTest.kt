package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.GeoportalStopEntity
import com.example.data.repository.GeocodingRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeocodingRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: GeocodingRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use an in-memory database for clean, isolated tests
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GeocodingRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testLocalBypassScenario() = runBlocking {
        // Insert a mock EMT stop into the local database
        val mockStop = GeoportalStopEntity(
            id_parada = "9876",
            denominacion = "Plaza de Pruebas Local",
            suprimida = 0,
            lat = 39.4697,
            lon = -0.3734,
            lineas = "99"
        )
        database.geoportalStopDao().insertAll(listOf(mockStop))

        // Querying for "Pruebas Local" should find the local stop and bypass Nominatim entirely
        val result = repository.searchAddress("Pruebas Local")
        
        assertTrue("Local search should succeed", result.isSuccess)
        val matches = result.getOrThrow()
        assertEquals("Should have exactly 1 local match", 1, matches.size)
        
        val match = matches.first()
        assertTrue("Match should be flagged as local stop", match.isLocalStop)
        assertEquals("Stop ID should match inserted mock ID", "9876", match.stopId)
        assertEquals("Stop Type should be EMT", "EMT", match.stopType)
        assertEquals("Latitude should match local database entry", 39.4697, match.latitude, 0.0001)
        assertEquals("Longitude should match local database entry", -0.3734, match.longitude, 0.0001)
    }

    @Test
    fun testRemoteNominatimIntegration() = runBlocking {
        // Fresh in-memory database has no local stops, so searching "Plaza del Ayuntamiento"
        // must trigger the remote Nominatim API call.
        val result = repository.searchAddress("Plaza del Ayuntamiento")
        
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            println("TEST FAILURE: Remote geocoding failed with exception: $exception")
            exception?.printStackTrace()
        }
        
        assertTrue("Remote query should succeed", result.isSuccess)
        val matches = result.getOrThrow()

        assertFalse("Should return at least one remote match from Nominatim", matches.isEmpty())
        
        // Output details of all geocoded results for diagnostic visibility
        println("=== NOMINATIM GEOLOCATION RESULTS (PLAZA DEL AYUNTAMIENTO) ===")
        matches.forEachIndexed { index, match ->
            println("[$index] ${match.displayName} | Lat: ${match.latitude}, Lon: ${match.longitude}")
        }
        
        // Explicitly assert that Alpera, Albacete and other external provinces are discarded
        matches.forEach { match ->
            assertFalse("Albacete results must be filtered out: ${match.displayName}", match.displayName.contains("Albacete", ignoreCase = true))
            assertFalse("Alpera results must be filtered out: ${match.displayName}", match.displayName.contains("Alpera", ignoreCase = true))
            assertFalse("Alicante results must be filtered out: ${match.displayName}", match.displayName.contains("Alicante", ignoreCase = true))
            assertFalse("Castellón results must be filtered out: ${match.displayName}", match.displayName.contains("Castellón", ignoreCase = true))
            assertFalse("Castelló results must be filtered out: ${match.displayName}", match.displayName.contains("Castelló", ignoreCase = true))
        }
        
        // Verify that ALL returned geocoded coordinates lie inside the Valencia province bounds
        matches.forEachIndexed { index, m ->
            assertTrue(
                "Result [$index] (${m.displayName}) latitude (${m.latitude}) should be inside Valencia province bounds",
                m.latitude in 38.7..40.2
            )
            assertTrue(
                "Result [$index] (${m.displayName}) longitude (${m.longitude}) should be inside Valencia province bounds",
                m.longitude in -1.45..0.35
            )
        }
    }

    @Test
    fun testRemoteNominatimRequenaAndXativa() = runBlocking {
        // Test Requena (La Plana de Utiel-Requena, western Valencia province)
        val requenaResult = repository.searchAddress("Requena")
        assertTrue("Requena search should succeed", requenaResult.isSuccess)
        val requenaMatches = requenaResult.getOrThrow()
        assertFalse("Requena should return results", requenaMatches.isEmpty())
        val firstRequena = requenaMatches.first()
        println("=== REQUENA RESULT === ${firstRequena.displayName} | Lat: ${firstRequena.latitude}, Lon: ${firstRequena.longitude}")
        assertTrue("Requena latitude should be ~39.48", firstRequena.latitude in 39.2..39.7)
        assertTrue("Requena longitude should be ~-1.10", firstRequena.longitude in -1.35..-0.95)

        // Test Xàtiva (La Costera, southern Valencia province)
        val xativaResult = repository.searchAddress("Xativa")
        assertTrue("Xàtiva search should succeed", xativaResult.isSuccess)
        val xativaMatches = xativaResult.getOrThrow()
        assertFalse("Xàtiva should return results", xativaMatches.isEmpty())
        val firstXativa = xativaMatches.first()
        println("=== XATIVA RESULT === ${firstXativa.displayName} | Lat: ${firstXativa.latitude}, Lon: ${firstXativa.longitude}")
        assertTrue("Xàtiva latitude should be ~38.98", firstXativa.latitude in 38.8..39.2)
        assertTrue("Xàtiva longitude should be ~-0.52", firstXativa.longitude in -0.7..-0.3)
    }

    @Test
    fun testProximitySortingWithMockedResults() = runBlocking {
        // Mock Nominatim API service with 3 Valencia results in unordered distance
        val valenciaAddress = com.example.data.network.NominatimAddressDto(
            stateDistrict = "Valencia",
            isoProvince = "ES-V"
        )
        val mockFarUtiel = com.example.data.network.NominatimResultDto(
            displayName = "Plaza Mayor, Utiel, Valencia",
            lat = "39.5669",
            lon = "-1.2057",
            type = "square",
            clazz = "place",
            category = "place",
            address = valenciaAddress
        )
        val mockCloseValencia = com.example.data.network.NominatimResultDto(
            displayName = "Plaza del Ayuntamiento, Valencia",
            lat = "39.4706",
            lon = "-0.3768",
            type = "square",
            clazz = "place",
            category = "place",
            address = valenciaAddress
        )
        val mockMidAlgemesi = com.example.data.network.NominatimResultDto(
            displayName = "Plaza Mayor, Algemesí, Valencia",
            lat = "39.1889",
            lon = "-0.4368",
            type = "square",
            clazz = "place",
            category = "place",
            address = valenciaAddress
        )

        val mockApiService = object : com.example.data.network.NominatimApiService {
            override suspend fun search(
                query: String,
                viewbox: String,
                bounded: Int,
                format: String,
                addressDetails: Int,
                countryCodes: String,
                limit: Int,
                acceptLanguage: String
            ): List<com.example.data.network.NominatimResultDto> {
                // Return them initially ordered as [Far (Utiel), Close (Valencia), Mid (Algemesí)]
                return listOf(mockFarUtiel, mockCloseValencia, mockMidAlgemesi)
            }

            override suspend fun reverse(
                lat: Double,
                lon: Double,
                format: String,
                addressDetails: Int,
                acceptLanguage: String
            ): com.example.data.network.NominatimResultDto {
                return mockCloseValencia
            }
        }

        val testRepo = GeocodingRepository(context, database, apiServiceOverride = mockApiService)

        // User is at Estación del Norte (lat: 39.4667, lon: -0.3774)
        val userLat = 39.4667
        val userLon = -0.3774

        val result = testRepo.searchAddress("Plaza", userLat = userLat, userLon = userLon)
        assertTrue("Search with proximity should succeed", result.isSuccess)
        val sortedList = result.getOrThrow()
        assertEquals("Should contain 3 results", 3, sortedList.size)

        println("=== PROXIMITY SORTED RESULTS (User at Estación del Norte: $userLat, $userLon) ===")
        sortedList.forEachIndexed { i, item ->
            val distMeters = com.example.util.LocationUtils.calculateDistanceMeters(userLat, userLon, item.latitude, item.longitude)
            println("[$i] ${item.displayName} -> Distance: ${com.example.util.LocationUtils.formatDistance(distMeters)} (${distMeters.toInt()}m)")
        }

        // Verification of ascending proximity ordering
        assertEquals("1st should be closest (Valencia)", "Plaza del Ayuntamiento, Valencia", sortedList[0].displayName)
        assertEquals("2nd should be medium distance (Algemesí)", "Plaza Mayor, Algemesí, Valencia", sortedList[1].displayName)
        assertEquals("3rd should be farthest (Utiel)", "Plaza Mayor, Utiel, Valencia", sortedList[2].displayName)
    }
}
