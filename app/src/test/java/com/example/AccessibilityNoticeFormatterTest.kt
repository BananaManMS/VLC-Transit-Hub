package com.example

import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.metro.AccessibilityIncident
import com.example.util.AccessibilityNoticeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityNoticeFormatterTest {

    @Test
    fun testCleanAccessibilityNoticeText_removesBoilerplate() {
        val inputMetro = "Afectación de accesibilidad en la estación Alameda: Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria."
        val cleanedMetro = AccessibilityNoticeFormatter.cleanAccessibilityNoticeText(inputMetro, "Alameda")
        assertEquals(
            "Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria.",
            cleanedMetro
        )

        val inputValencian = "Afectació d'accessibilitat a l'estació de Jesús: Avaria en ascensor - Accés C/ Mora de Rubielos."
        val cleanedVal = AccessibilityNoticeFormatter.cleanAccessibilityNoticeText(inputValencian, "Jesús")
        assertEquals(
            "Avaria en ascensor - Accés C/ Mora de Rubielos.",
            cleanedVal
        )

        val inputCercanias = "Afectación a la accesibilidad en la estación de Xirivella-Alqueries: Ascensor fuera de servicio."
        val cleanedCerc = AccessibilityNoticeFormatter.cleanAccessibilityNoticeText(inputCercanias, "Xirivella-Alqueries")
        assertEquals(
            "Ascensor fuera de servicio.",
            cleanedCerc
        )

        val inputWithStationOnly = "Alameda: Avería en ascensor."
        val cleanedSt = AccessibilityNoticeFormatter.cleanAccessibilityNoticeText(inputWithStationOnly, "Alameda")
        assertEquals(
            "Avería en ascensor.",
            cleanedSt
        )

        val alreadyClean = "Avería en ascensor - Acceso Paseo Alameda."
        val cleanedAlready = AccessibilityNoticeFormatter.cleanAccessibilityNoticeText(alreadyClean, "Alameda")
        assertEquals(
            "Avería en ascensor - Acceso Paseo Alameda.",
            cleanedAlready
        )
    }

    @Test
    fun testDeduplicateAccessibilityTexts_collapsesDuplicatesAndSubstrings() {
        val list = listOf(
            "Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria.",
            "Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria",
            "Avería en ascensor - Acceso Paseo Alameda",
            "Avería en escalera mecánica - Acceso exterior."
        )

        val deduplicated = AccessibilityNoticeFormatter.deduplicateAccessibilityTexts(list)
        assertEquals(2, deduplicated.size)
        assertTrue(deduplicated.contains("Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria."))
        assertTrue(deduplicated.contains("Avería en escalera mecánica - Acceso exterior."))
    }

    @Test
    fun testDeduplicateAccessibilityIncidents() {
        val incidents = listOf(
            AccessibilityIncident(
                id = "acc-1",
                tituloEs = "Afectación de accesibilidad en la estación Alameda",
                descripcionEs = "Afectación de accesibilidad en la estación Alameda: Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria.",
                tituloCa = "",
                descripcionCa = "",
                creadoEl = "2026-03-30",
                estacionId = 1,
                estacionNombre = "Alameda"
            ),
            AccessibilityIncident(
                id = "acc-2",
                tituloEs = "Avería en ascensor en Alameda",
                descripcionEs = "Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria.",
                tituloCa = "",
                descripcionCa = "",
                creadoEl = "2026-03-30",
                estacionId = 1,
                estacionNombre = "Alameda"
            )
        )

        val deduped = AccessibilityNoticeFormatter.deduplicateAccessibilityIncidents(incidents, "Alameda")
        assertEquals(1, deduped.size)
    }

    @Test
    fun testDeduplicateCercaniasAlerts() {
        val alerts = listOf(
            CercaniasAlert(
                id = "1",
                headerEs = "Afectación accesibilidad estación de Silla",
                descriptionEs = "Afectación a la accesibilidad en la estación de Silla: Ascensor fuera de servicio en vía 2.",
                isAccessibility = true
            ),
            CercaniasAlert(
                id = "2",
                headerEs = "Ascensor fuera de servicio en vía 2",
                descriptionEs = "Ascensor fuera de servicio en vía 2",
                isAccessibility = true
            )
        )

        val deduped = AccessibilityNoticeFormatter.deduplicateCercaniasAccessibilityAlerts(alerts, "Silla")
        assertEquals(1, deduped.size)
    }

    @Test
    fun testGroupAccessibilityTexts_consolidatesRepeatedCategory() {
        val texts = listOf(
            "Avería en ascensor - Acceso Paseo Ciudadela y andén destino Aeroport/ Riba-roja de Túria.",
            "Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria.",
            "Avería en ascensor - Andén destino Marítim."
        )
        val groups = AccessibilityNoticeFormatter.groupAccessibilityTexts(texts)
        assertEquals(1, groups.size)
        assertEquals("Avería en ascensor", groups[0].category)
        assertEquals(3, groups[0].details.size)
        assertEquals("Acceso Paseo Ciudadela y andén destino Aeroport/ Riba-roja de Túria.", groups[0].details[0])
        assertEquals("Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria.", groups[0].details[1])
        assertEquals("Andén destino Marítim.", groups[0].details[2])
    }
}
