package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.cercanias.CercaniasRouteUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CercaniasTrainAssociationTest {

    @Test
    fun testCanonicalLineNumberFromNucleus40Prefix() {
        assertEquals("1", CercaniasRouteUtils.getCanonicalLineNumber("401"))
        assertEquals("2", CercaniasRouteUtils.getCanonicalLineNumber("402"))
        assertEquals("3", CercaniasRouteUtils.getCanonicalLineNumber("403"))
        assertEquals("5", CercaniasRouteUtils.getCanonicalLineNumber("405"))
        assertEquals("6", CercaniasRouteUtils.getCanonicalLineNumber("406"))
    }

    @Test
    fun testCanonicalLineNumberFromStandardLineLabels() {
        assertEquals("1", CercaniasRouteUtils.getCanonicalLineNumber("C1"))
        assertEquals("2", CercaniasRouteUtils.getCanonicalLineNumber("C-2"))
        assertEquals("3", CercaniasRouteUtils.getCanonicalLineNumber("C_3"))
        assertEquals("6", CercaniasRouteUtils.getCanonicalLineNumber("Línea 6"))
        assertEquals("2", CercaniasRouteUtils.getCanonicalLineNumber("40C2"))
    }

    @Test
    fun testCanonicalLineNumberFromTripId() {
        assertEquals("1", CercaniasRouteUtils.getCanonicalLineNumber("", "4070M24249C1_1"))
        assertEquals("2", CercaniasRouteUtils.getCanonicalLineNumber("", "4070M26854C2"))
        assertEquals("6", CercaniasRouteUtils.getCanonicalLineNumber("", "4070M24450C6"))
    }

    @Test
    fun testEffectiveTrainNumberExtraction() {
        assertEquals("24249", CercaniasRouteUtils.getEffectiveTrainNumber("24249", ""))
        assertEquals("24249", CercaniasRouteUtils.getEffectiveTrainNumber("", "4070M24249C1"))
        assertEquals("26854", CercaniasRouteUtils.getEffectiveTrainNumber("", "4070M26854C2_1"))
        assertEquals("24450", CercaniasRouteUtils.getEffectiveTrainNumber("24450", "4070M24450C6"))
    }

    @Test
    fun testAlertTripMatchingFromScheduledData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CercaniasRouteUtils.init(context)

        val alertText = "Línea C2. Tren con salida prevista a las 08:30h de la estación de València Nord con destino l'Alcúdia, circula con 15 minutos de retraso."
        val matchedTrips = CercaniasRouteUtils.findMatchingTripIdsForAlert(alertText, listOf("C2"))

        // Should find trip IDs associated with 08:30 from València Nord to L'Alcúdia
        assertTrue("Should find trip IDs", matchedTrips.isNotEmpty())
        assertTrue("Should contain 4019D24019C2 or 24019", matchedTrips.any { it.contains("24019") })

        // Should match departure on that train
        val departureTripId = "4019D24019C2"
        val isMatched = CercaniasRouteUtils.isTripMatchedByAlert(departureTripId, listOf("4020L24019C2"), matchedTrips)
        assertTrue("Departure should be matched by alert", isMatched)

        // Should NOT match a train on another route or another time
        val otherDepartureTripId = "4020L24300C6"
        val isOtherMatched = CercaniasRouteUtils.isTripMatchedByAlert(otherDepartureTripId, emptyList(), matchedTrips)
        assertFalse("Unrelated train should NOT be matched", isOtherMatched)
    }
}
