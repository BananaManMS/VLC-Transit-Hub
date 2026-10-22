package com.example

import com.example.util.MetroDepotFilterHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroDepotFilterTest {

    @Test
    fun testFacultatsExcludesL5AndL7() {
        // Facultats: FGV ID 13, WebID 68
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("13", "Facultats", "L5"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("13", "Facultats - Manuel Broseta", "5"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("68", "Facultats", "L7"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("68", "Facultats", "7"))

        // Commercial lines for Facultats (L3, L9) must NOT be excluded
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("13", "Facultats", "L3"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("13", "Facultats", "3"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("68", "Facultats", "L9"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("68", "Facultats", "9"))
    }

    @Test
    fun testBenimacletExcludesL5AndL7() {
        // Benimaclet: FGV ID 12, WebID 67
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("12", "Benimaclet", "L5"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("12", "Benimaclet", "5"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("67", "Benimaclet", "L7"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("67", "Benimaclet", "7"))

        // Commercial lines for Benimaclet (L3, L4, L6, L9) must NOT be excluded
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("12", "Benimaclet", "L3"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("12", "Benimaclet", "L4"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("12", "Benimaclet", "L6"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("12", "Benimaclet", "L9"))
    }

    @Test
    fun testMachadoExcludesL5AndL7() {
        // Machado: FGV ID 11, WebID 66
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("11", "Machado", "L5"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("11", "Machado", "5"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("66", "Machado", "L7"))
        assertTrue(MetroDepotFilterHelper.isDepotExcludedStationLine("66", "Machado", "7"))

        // Commercial lines for Machado (L3, L9) must NOT be excluded
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("11", "Machado", "L3"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("11", "Machado", "L9"))
    }

    @Test
    fun testOtherStationsDoNotExcludeL5OrL7() {
        // Xàtiva (FGV ID 16 / WebID 71) serves L3, L5, L9
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("16", "Xàtiva", "L5"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("71", "Xàtiva", "5"))

        // Alameda (FGV ID 14 / WebID 69) serves L3, L5, L7, L9
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("14", "Alameda", "L5"))
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("14", "Alameda", "L7"))

        // Colón serves L3, L5, L7, L9
        assertFalse(MetroDepotFilterHelper.isDepotExcludedStationLine("15", "Colón", "L7"))
    }
}
