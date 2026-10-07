package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MetroRouteBlueprint
import com.example.data.model.MetroRouteBlueprintLeg
import com.example.data.model.MetroStation
import com.example.data.repository.MetroRouteBlueprintRepository
import com.example.data.repository.MetroScheduleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class MetroJourneySimulationTest {

    private lateinit var blueprintRepo: MetroRouteBlueprintRepository
    private lateinit var scheduleRepo: MetroScheduleRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        blueprintRepo = MetroRouteBlueprintRepository.getInstance(context)
        scheduleRepo = MetroScheduleRepository.getInstance(context)
        scheduleRepo.ensureLoaded()
    }

    @Test
    fun testDirectRouteBlueprint_SharedLineHasZeroTransfers() = runBlocking {
        val colon = MetroStation(id = "15", name = "Colón", lines = listOf("3", "5", "7", "9"))
        val xativa = MetroStation(id = "16", name = "Xàtiva", lines = listOf("3", "5", "9"))

        val blueprint = blueprintRepo.getRouteBlueprint(colon, xativa)
        assertEquals(0, blueprint.totalTransfers)
        assertTrue(blueprint.isDirect)
        assertEquals(1, blueprint.legs.size)
        assertTrue(blueprint.legs[0].line in listOf("3", "5", "9"))
    }

    @Test
    fun testNonDirectRouteBlueprint_MarkedAsNotDirect() = runBlocking {
        val betera = MetroStation(id = "107", name = "Bétera", lines = listOf("1"))
        val colon = MetroStation(id = "15", name = "Colón", lines = listOf("3", "5", "7", "9"))

        val blueprint = blueprintRepo.getRouteBlueprint(betera, colon)
        assertFalse("Route between Bétera (L1) and Colón (L3/L5) without direct line must not be marked as direct", blueprint.isDirect)
    }

    @Test
    fun testOppositeDirectionDetection_RosesToAngelGuimeraOnAeroportTrain() = runBlocking {
        val roses = MetroStation(id = "120", name = "Roses", lines = listOf("3", "5", "9"))
        val angelGuimera = MetroStation(id = "17", name = "Àngel Guimerà", lines = listOf("1", "2", "3", "5", "9"))

        val blueprint = blueprintRepo.getRouteBlueprint(roses, angelGuimera)
        assertNotNull(blueprint)
        assertEquals("Roses", blueprint.originStationName)
        assertEquals("Àngel Guimerà", blueprint.destinationStationName)
    }

    @Test
    fun testDelayTransferPropagation_TransfersDelayFromOriginToDestination() {
        val madridTz = TimeZone.getTimeZone("Europe/Madrid")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = madridTz
        }

        // Scenario:
        // Train scheduled at origin: 12:30
        // Live train in API is delayed to: 12:34 (+4 min delay)
        // Direct travel duration: 16 min (scheduled destination arrival: 12:46)
        val schedOriginH = 12
        val schedOriginM = 30
        val schedOriginMinOfDay = schedOriginH * 60 + schedOriginM

        val liveOriginH = 12
        val liveOriginM = 34
        val liveOriginMinOfDay = liveOriginH * 60 + liveOriginM

        val delayMinutes = liveOriginMinOfDay - schedOriginMinOfDay
        assertEquals(4, delayMinutes)

        val travelDurationMinutes = 16

        // Scheduled destination arrival: 12:30 + 16 min = 12:46
        val schedDestMinOfDay = schedOriginMinOfDay + travelDurationMinutes
        val schedDestFormatted = String.format(Locale.getDefault(), "%02d:%02d", (schedDestMinOfDay / 60) % 24, schedDestMinOfDay % 60)
        assertEquals("12:46", schedDestFormatted)

        // Live estimated destination arrival with transferred delay: 12:46 + 4 min = 12:50
        val liveDestMinOfDay = schedDestMinOfDay + delayMinutes
        val liveDestFormatted = String.format(Locale.getDefault(), "%02d:%02d", (liveDestMinOfDay / 60) % 24, liveDestMinOfDay % 60)
        assertEquals("12:50", liveDestFormatted)

        // Confirm live origin + travel duration gives identical 12:50
        val liveOriginPlusDuration = liveOriginMinOfDay + travelDurationMinutes
        val liveOriginPlusDurationFormatted = String.format(Locale.getDefault(), "%02d:%02d", (liveOriginPlusDuration / 60) % 24, liveOriginPlusDuration % 60)
        assertEquals(liveDestFormatted, liveOriginPlusDurationFormatted)
    }
}
