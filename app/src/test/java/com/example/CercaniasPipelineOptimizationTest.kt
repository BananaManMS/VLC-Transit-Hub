package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.renfe.GtfsCacheManager
import com.example.data.repository.renfe.GtfsRtStopTime
import com.example.data.repository.renfe.GtfsRtTripUpdate
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.cercanias.CercaniasRouteUtils
import com.example.ui.cercanias.LiveVehicleInfo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CercaniasPipelineOptimizationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var cacheManager: GtfsCacheManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CercaniasRouteUtils.init(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cacheManager = GtfsCacheManager.getInstance(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun canonicalLineHelpersDoNotFabricateFakeC1ForUnknownRoutes() {
        assertEquals("", CercaniasRouteUtils.getCanonicalLineNumber("", ""))
        assertEquals("", CercaniasRouteUtils.getCanonicalLineNumber("UNKNOWN", "NO_LINE_999"))
        assertEquals("", CercaniasRouteUtils.getCanonicalLineCode("", ""))
        assertEquals("CR", CercaniasRouteUtils.getCanonicalLineCode("", "", fallback = "CR"))

        assertEquals("2", CercaniasRouteUtils.getCanonicalLineNumber("402", ""))
        assertEquals("C2", CercaniasRouteUtils.getCanonicalLineCode("402", ""))
        assertEquals("C6", CercaniasRouteUtils.getCanonicalLineCode("", "4070M24450C6"))
    }

    @Test
    fun remainingStopsForTripFiltersExpressCivisStopsWhenAvailable() {
        val nordStopId = "65000"
        val lineStationsC6 = CercaniasRouteUtils.getRemainingStops(
            originStationId = nordStopId,
            originStationName = "València Nord",
            destinationName = "Castelló de la Plana",
            routeId = "C6"
        )
        assertTrue("Full C6 route should contain stations", lineStationsC6.size > 10)

        // Compare two scheduled C6 trips to verify per-trip stop lists and scheduled times populate cleanly
        val repo = RenfeRepository(context, db)
        runTest {
            repo.syncManager.ensureScheduleLoadedInMemory()
            val horarios = repo.syncManager.getHorariosForStation(nordStopId)
                .filter { it.linea.equals("C6", ignoreCase = true) }
            assertTrue("València Nord must have C6 departures", horarios.isNotEmpty())

            val tripIdsWithSizes = horarios.mapNotNull { h ->
                val tid = (h.trip_ids.ifEmpty { h.tripIds }).firstOrNull() ?: return@mapNotNull null
                val stops = CercaniasRouteUtils.getRemainingStopsForTrip(
                    tripId = tid,
                    originStationId = nordStopId,
                    originStationName = "València Nord",
                    destinationName = h.destino,
                    routeId = "C6",
                    allTripIds = h.trip_ids.ifEmpty { h.tripIds }
                )
                Triple(tid, stops.size, stops.any { !it.scheduledTime.isNullOrBlank() })
            }
            assertTrue("Trips should have scheduled times on stops", tripIdsWithSizes.any { it.third })
            val minStops = tripIdsWithSizes.minOf { it.second }
            val maxStops = tripIdsWithSizes.maxOf { it.second }
            assertTrue("CIVIS / short-turn C6 runs should have fewer stops ($minStops) than full runs ($maxStops)", minStops < maxStops)
        }
    }

    @Test
    fun parseFlotaJsonReadsFechaActualizacionBlankPorAvancAndPlatforms() {
        val ts = "2026-11-20T12:00:${System.nanoTime() % 100000}"
        val flotaJson = """
            {
              "fechaActualizacion": "$ts",
              "trenes": [
                {
                  "nucleo": "40",
                  "codLinea": "C2",
                  "codTren": "24111",
                  "tripId": "4070D24111C2",
                  "codEstAct": "65000",
                  "via": "4",
                  "codEstSig": "64100",
                  "nextVia": "2",
                  "porAvanc": "",
                  "retrasoMin": "3",
                  "latitud": 39.4667,
                  "longitud": -0.3774,
                  "nomEstOrigen": "València Nord",
                  "nomEstDestino": "Xàtiva"
                }
              ]
            }
        """.trimIndent()

        val tuMap = mutableMapOf<String, GtfsRtTripUpdate>()
        val vpMap = mutableMapOf<String, LiveVehicleInfo>()
        val covered = mutableSetOf<String>()

        val parsed = cacheManager.parseFlotaJson(flotaJson, tuMap, vpMap, covered)
        assertTrue(parsed)

        val vp = vpMap["4070D24111C2"]
        assertNotNull(vp)
        assertEquals("STOPPED_AT", vp!!.status)
        assertEquals("4", vp.platform)
        assertEquals("2", vp.nextPlatform)
        assertEquals(3, vp.delayMinutes)
        assertTrue(vp.hasExplicitDelay)
        assertTrue("4070D24111C2" in covered)
        assertTrue("C2_24111" in covered)

        val tu = tuMap["4070D24111C2"]
        assertNotNull(tu)
        assertEquals("4", tu!!.stopPlatforms["65000"])
        assertEquals("2", tu.stopPlatforms["64100"])
        assertEquals(180L, tu.delaySeconds)
    }

    @Test
    fun explicitZeroDelayFromTripUpdatesResetsStaleFlotaDelayWhileKeepingPlatforms() {
        val mergedTu = mutableMapOf<String, GtfsRtTripUpdate>()
        val mergedVp = mutableMapOf<String, LiveVehicleInfo>()

        val flotaTu = GtfsRtTripUpdate(
            tripId = "4070D24555C1",
            routeId = "C1",
            delaySeconds = 300L, // +5 min from flota
            stopDelays = mapOf("65000" to 300L),
            stopPlatforms = mapOf("65000" to "5"),
            vehicleId = "24555",
            hasExplicitDelay = true,
            timestamp = 1000L
        )
        val flotaVp = LiveVehicleInfo(
            tripId = "4070D24555C1",
            routeId = "C1",
            latitude = 39.46,
            longitude = -0.37,
            status = "IN_TRANSIT_TO",
            platform = "5",
            currentStopId = "65000",
            delayMinutes = 5,
            trainNum = "24555",
            hasExplicitDelay = true
        )
        mergedTu["4070D24555C1"] = flotaTu
        mergedVp["4070D24555C1"] = flotaVp

        // Later trip_updates explicitly reports delay = 0
        val tripUpdateZero = GtfsRtTripUpdate(
            tripId = "4070D24555C1",
            routeId = "C1",
            delaySeconds = 0L,
            stopDelays = mapOf("65000" to 0L),
            stopEstimatedTimes = mapOf("65000" to 2000L),
            stopTimes = listOf(GtfsRtStopTime(stopId = "65000", departureDelay = 0L, estimatedDepartureTime = 2000L)),
            vehicleId = "24555",
            hasExplicitDelay = true,
            timestamp = 2000L
        )

        cacheManager.mergeTripUpdates(
            target = mergedTu,
            source = mapOf("4070D24555C1" to tripUpdateZero),
            overwriteWithFlota = false
        )

        val resTu = mergedTu["4070D24555C1"]!!
        assertEquals("Explicit 0 delay from trip_updates must reset stale +5m", 0L, resTu.delaySeconds)
        assertEquals(0L, resTu.stopDelays["65000"])
        assertEquals("Rich stopPlatforms from flota must be preserved", "5", resTu.stopPlatforms["65000"])

        // When trip_updates omits explicit delay (hasExplicitDelay = false), keep existing delay
        val vpNoDelay = LiveVehicleInfo(
            tripId = "4070D24555C1",
            routeId = "C1",
            latitude = 39.47,
            longitude = -0.38,
            status = "IN_TRANSIT_TO",
            platform = "",
            delayMinutes = 0,
            trainNum = "24555",
            hasExplicitDelay = false
        )
        cacheManager.mergeVehiclePositions(mergedVp, mapOf("4070D24555C1" to vpNoDelay))
        assertEquals(5, mergedVp["4070D24555C1"]!!.delayMinutes)

        // When source explicitly sends hasExplicitDelay = true with 0, it overwrites existing 5
        cacheManager.mergeVehiclePositions(
            mergedVp,
            mapOf("4070D24555C1" to vpNoDelay.copy(hasExplicitDelay = true, delayMinutes = 0))
        )
        assertEquals(0, mergedVp["4070D24555C1"]!!.delayMinutes)
    }

    @Test
    fun parseVehiclePositionsSkipsFlotaCoveredAndEnrichesHeaderTrains() {
        val flotaTs = "2026-11-20T12:05:${System.nanoTime() % 100000}"
        val vpHeaderTs = System.currentTimeMillis() / 1000L + 100

        val flotaJson = """
            {
              "fechaActualizacion": "$flotaTs",
              "trenes": [
                {
                  "nucleo": "40",
                  "codLinea": "C1",
                  "codTren": "24007",
                  "tripId": "4070D24007C1",
                  "codEstAct": "65000",
                  "via": "3",
                  "porAvanc": "E",
                  "retrasoMin": "1",
                  "latitud": 39.466,
                  "longitud": -0.377
                }
              ]
            }
        """.trimIndent()

        val tuMap = mutableMapOf<String, GtfsRtTripUpdate>()
        val flotaVpMap = mutableMapOf<String, LiveVehicleInfo>()
        val coveredKeys = mutableSetOf<String>()
        assertTrue(cacheManager.parseFlotaJson(flotaJson, tuMap, flotaVpMap, coveredKeys))

        val tripUpdatesForEnrichment = mapOf(
            "4070D24999C2" to GtfsRtTripUpdate(
                tripId = "4070D24999C2",
                routeId = "C2",
                delaySeconds = 240L,
                hasExplicitDelay = true
            )
        )

        val vpJson = """
            {
              "header": { "timestamp": $vpHeaderTs },
              "entity": [
                {
                  "id": "VP_40_1",
                  "vehicle": {
                    "trip": { "trip_id": "4070D24007C1", "route_id": "40C1" },
                    "position": { "latitude": 39.466, "longitude": -0.377, "speed": 42.5, "bearing": 180.0 },
                    "current_status": "IN_TRANSIT_TO",
                    "vehicle": { "id": "24007", "label": "C1-24007" }
                  }
                },
                {
                  "id": "VP_40_2",
                  "vehicle": {
                    "trip": { "trip_id": "4070D24999C2", "route_id": "" },
                    "stop_id": "65000",
                    "position": { "latitude": 39.467, "longitude": -0.378 },
                    "current_status": "STOPPED_AT",
                    "vehicle": { "id": "24999", "label": "C2-24999-PLATF.(6)" }
                  }
                }
              ]
            }
        """.trimIndent()

        val vpOnlyMap = mutableMapOf<String, LiveVehicleInfo>()
        assertTrue(
            cacheManager.parseVehiclePositionsJson(
                jsonStr = vpJson,
                outMap = vpOnlyMap,
                flotaCoveredKeys = coveredKeys,
                flotaVpMap = flotaVpMap,
                tripUpdatesForEnrichment = tripUpdatesForEnrichment
            )
        )

        // Covered train skipped from vpOnlyMap, while speed/bearing enriched directly onto flotaVpMap
        assertFalse(vpOnlyMap.containsKey("4070D24007C1"))
        assertEquals(42.5, flotaVpMap["4070D24007C1"]!!.speed!!, 0.01)
        assertEquals("3", flotaVpMap["4070D24007C1"]!!.platform)

        // Uncovered header train included and enriched with trip_updates delay & line C2
        val headerTrain = vpOnlyMap["4070D24999C2"]
        assertNotNull(headerTrain)
        assertEquals("C2", headerTrain!!.routeId)
        assertEquals("6", headerTrain.platform)
        assertEquals(4, headerTrain.delayMinutes)
        assertTrue(headerTrain.hasExplicitDelay)
    }

    @Test
    fun tripNumberCollision24007BetweenC1AndC2IsIsolated() {
        val tuHeaderTs = System.currentTimeMillis() / 1000L + 200
        val tripUpdatesJson = """
            {
              "header": { "timestamp": $tuHeaderTs },
              "entity": [
                {
                  "id": "TU_40_C1",
                  "trip_update": {
                    "trip": { "trip_id": "4070D24007C1", "route_id": "C1" },
                    "delay": 120,
                    "vehicle": { "id": "24007" }
                  }
                },
                {
                  "id": "TU_40_C2",
                  "trip_update": {
                    "trip": { "trip_id": "4070D24007C2", "route_id": "C2" },
                    "delay": 600,
                    "vehicle": { "id": "24007" }
                  }
                }
              ]
            }
        """.trimIndent()

        val outTu = mutableMapOf<String, GtfsRtTripUpdate>()
        assertTrue(cacheManager.parseTripUpdatesJson(tripUpdatesJson, outTu))

        assertEquals(120L, outTu["C1_24007"]?.delaySeconds)
        assertEquals(600L, outTu["C2_24007"]?.delaySeconds)
        assertEquals(120L, outTu["4070D24007C1"]?.delaySeconds)
        assertEquals(600L, outTu["4070D24007C2"]?.delaySeconds)
    }

    @Test
    fun repositoryRespects2HourLiveWindowAndStationMatchedPlatforms() = runTest {
        val repo = RenfeRepository(context, db)
        repo.syncManager.ensureScheduleLoadedInMemory()

        val stationId = "65000" // València Nord
        val baselineDeps = repo.getDeparturesForStation(
            stopId = stationId,
            gtfsRtUpdates = emptyMap(),
            gtfsVehiclePositions = emptyMap()
        )
        assertTrue("Station 65000 should have scheduled departures", baselineDeps.isNotEmpty())

        // Pick one scheduled departure beyond +3h today (MUST NOT match live data)
        val farDep = baselineDeps.firstOrNull { !it.isTomorrow && it.minutesRemaining > 180 && it.tripId.isNotBlank() }
        // Pick one scheduled departure within +5..+90 min today (MUST match live data within +2h window)
        val nearDep = baselineDeps.firstOrNull { !it.isTomorrow && it.minutesRemaining in 5..90 && it.tripId.isNotBlank() }

        val updates = mutableMapOf<String, GtfsRtTripUpdate>()
        val vehicles = mutableMapOf<String, LiveVehicleInfo>()

        val farTripId = farDep?.tripId
        if (farDep != null && farTripId != null) {
            updates[farTripId] = GtfsRtTripUpdate(
                tripId = farTripId,
                routeId = farDep.routeId,
                delaySeconds = 420L,
                stopPlatforms = mapOf(stationId to "8"),
                hasExplicitDelay = true
            )
            vehicles[farTripId] = LiveVehicleInfo(
                tripId = farTripId,
                routeId = farDep.routeId,
                latitude = 39.46,
                longitude = -0.37,
                status = "STOPPED_AT",
                platform = "8",
                currentStopId = stationId,
                delayMinutes = 7,
                hasExplicitDelay = true
            )
        }

        val nearTripId = nearDep?.tripId
        if (nearDep != null && nearTripId != null && nearTripId != farTripId) {
            // Put platform ONLY on a different stopId (99999) for vehicle, and stopPlatforms[65000] blank
            updates[nearTripId] = GtfsRtTripUpdate(
                tripId = nearTripId,
                routeId = nearDep.routeId,
                delaySeconds = 240L,
                stopPlatforms = mapOf("99999" to "9"),
                vehicleLabel = "${nearDep.routeId}-12345-PLATF.(9)",
                hasExplicitDelay = true
            )
            vehicles[nearTripId] = LiveVehicleInfo(
                tripId = nearTripId,
                routeId = nearDep.routeId,
                latitude = 39.40,
                longitude = -0.35,
                status = "IN_TRANSIT_TO",
                platform = "9",
                currentStopId = "99999",
                nextStopId = "99998",
                nextPlatform = "2",
                delayMinutes = 4,
                hasExplicitDelay = true
            )
        }

        val deps = repo.getDeparturesForStation(
            stopId = stationId,
            gtfsRtUpdates = updates,
            gtfsVehiclePositions = vehicles
        )

        if (farTripId != null) {
            val farDeps = deps.filter { it.tripId == farTripId }
            assertTrue("Far departure ($farTripId) should remain in list", farDeps.isNotEmpty())
            assertTrue(
                "Departures > +2h and isTomorrow must NEVER receive live matching",
                farDeps.all { !it.isLive && it.platform.isBlank() && it.delayMinutes == 0 }
            )
        }

        if (nearTripId != null && nearTripId != farTripId) {
            val nearToday = deps.firstOrNull { it.tripId == nearTripId && !it.isTomorrow }
            assertNotNull("Near departure ($nearTripId) should remain in today's list", nearToday)
            assertTrue("Near departure within +2h must be live", nearToday!!.isLive)
            assertEquals(4, nearToday.delayMinutes)
            assertEquals("Platform from another station (99999/99998) MUST NOT leak to 65000", "", nearToday.platform)

            val tomorrowCopy = deps.firstOrNull { it.tripId == nearTripId && it.isTomorrow }
            if (tomorrowCopy != null) {
                assertFalse("Tomorrow departure must stay scheduled-only", tomorrowCopy.isLive)
                assertEquals("", tomorrowCopy.platform)
            }

            // Now set platform on the queried stationId specifically (nextStopId == stationId with nextVia="5")
            val stationMatchedVehicles = vehicles.toMutableMap()
            stationMatchedVehicles[nearTripId] = vehicles[nearTripId]!!.copy(nextStopId = stationId, nextPlatform = "5")
            val depsMatched = repo.getDeparturesForStation(
                stopId = stationId,
                gtfsRtUpdates = updates,
                gtfsVehiclePositions = stationMatchedVehicles
            )
            val matchedToday = depsMatched.first { it.tripId == nearTripId && !it.isTomorrow }
            assertEquals("5", matchedToday.platform)
        }
    }
}
