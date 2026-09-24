package com.example

import com.example.util.StationAccessibilityHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationAccessibilityHelperTest {

    @Test
    fun testExactMatchAndDiacritics() {
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Xàtiva",
                "Avería de ascensor en Xàtiva"
            )
        )
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Xàtiva",
                "Averia de ascensor en Xativa"
            )
        )
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Colón",
                "Mantenimiento programado estación Colon"
            )
        )
    }

    @Test
    fun testSillaWordBoundaryAndWheelchairExclusion() {
        // "silla de ruedas" should NOT match station "Silla"
        assertFalse(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Silla",
                "Servicio adaptado con rampa para silla de ruedas en Valencia Nord"
            )
        )
        assertFalse(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Silla",
                "Espacio reservado para sillas de ruedas"
            )
        )
        // Actual station "Silla" in title should match
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Silla",
                "Incidencia ascensor estación de Silla"
            )
        )
    }

    @Test
    fun testCompoundNames() {
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Facultats - Manuel Broseta",
                "Ascensor fuera de servicio en Facultats"
            )
        )
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Marítim - Serrería",
                "Escalera mecánica averiada en Marítim"
            )
        )
    }

    @Test
    fun testBilingualVariants() {
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "València Nord",
                "Ascensor de acceso a vías fuera de servicio en Valencia Norte"
            )
        )
        assertTrue(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Xàtiva",
                "Ascensor averiado en estación de Játiva"
            )
        )
    }

    @Test
    fun testNegativeCases() {
        assertFalse(
            StationAccessibilityHelper.isStationInAlertTitle(
                "Bétera",
                "Avería en estación de Torrent Avinguda"
            )
        )
        assertFalse(
            StationAccessibilityHelper.isStationInAlertTitle(
                "",
                "Avería en estación"
            )
        )
    }

    @Test
    fun testCleanAccessibilityAlertTextRemovesRedundantPrefix() {
        val inputEs = "Afectación de accesibilidad en la estación Jesús: Avería en ascensor - Acceso C/ Mora de Rubielos, 7 y andenes."
        val expectedEs = "Avería en ascensor - Acceso C/ Mora de Rubielos, 7 y andenes."
        org.junit.Assert.assertEquals(expectedEs, StationAccessibilityHelper.cleanAccessibilityAlertText(inputEs))

        val inputCa = "Afectació d'accessibilitat a l'estació Xàtiva: Avaries en ascensors."
        val expectedCa = "Avaries en ascensors."
        org.junit.Assert.assertEquals(expectedCa, StationAccessibilityHelper.cleanAccessibilityAlertText(inputCa))

        val inputIncidencia = "Incidencia de accesibilidad en la estación Colón: Avería ascensor"
        val expectedIncidencia = "Avería ascensor"
        org.junit.Assert.assertEquals(expectedIncidencia, StationAccessibilityHelper.cleanAccessibilityAlertText(inputIncidencia))

        // When text does not have prefix, keeps content intact
        val noPrefix = "Avería de ascensor en andén 1"
        org.junit.Assert.assertEquals(noPrefix, StationAccessibilityHelper.cleanAccessibilityAlertText(noPrefix))
    }
}
