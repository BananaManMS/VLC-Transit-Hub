package com.example

import com.example.ui.cercanias.CercaniasRouteUtils
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
