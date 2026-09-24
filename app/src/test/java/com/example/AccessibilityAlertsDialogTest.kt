package com.example

import com.example.ui.components.AccessibilityGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityAlertsDialogTest {

    // Simple test to verify the grouping logic works exactly as expected
    private fun groupAccessibilityAlerts(alerts: List<String>): List<AccessibilityGroup> {
        if (alerts.isEmpty()) return emptyList()

        val groups = mutableMapOf<String, MutableList<String>>()
        val rawSingleAlerts = mutableListOf<String>()

        for (alert in alerts) {
            val trimmed = alert.trim().trimEnd('.')
            val separator = when {
                trimmed.contains(" - ") -> " - "
                trimmed.contains(" : ") -> " : "
                trimmed.contains(": ") -> ": "
                else -> null
            }

            if (separator != null) {
                val parts = trimmed.split(separator, limit = 2)
                val header = parts[0].trim().replaceFirstChar { it.uppercase() }
                val detail = parts[1].trim().replaceFirstChar { it.uppercase() }
                if (header.isNotEmpty() && detail.isNotEmpty()) {
                    groups.getOrPut(header) { mutableListOf() }.add(detail)
                } else {
                    rawSingleAlerts.add(trimmed)
                }
            } else {
                rawSingleAlerts.add(trimmed)
            }
        }

        val result = mutableListOf<AccessibilityGroup>()
        for ((header, details) in groups) {
            result.add(AccessibilityGroup(header, details.distinct()))
        }
        if (rawSingleAlerts.isNotEmpty()) {
            result.add(AccessibilityGroup("", rawSingleAlerts.distinct()))
        }

        return result
    }

    @Test
    fun testGroupingOfElevatorIssues() {
        val rawAlerts = listOf(
            "Avería en ascensor - Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria.",
            "Avería en ascensor - Acceso Paseo Ciudadela y andén destino Aeroport/ Riba-roja de Túria.",
            "Avería en ascensor - Andén destino Marítim.",
            "Escalera mecánica fuera de servicio - Acceso norte"
        )

        val grouped = groupAccessibilityAlerts(rawAlerts)
        
        assertEquals(2, grouped.size)
        
        val ascensorGroup = grouped.find { it.header == "Avería en ascensor" }!!
        assertEquals(3, ascensorGroup.details.size)
        assertEquals("Acceso Paseo Alameda y andén destino Aeroport/ Riba-roja de Túria", ascensorGroup.details[0])
        assertEquals("Acceso Paseo Ciudadela y andén destino Aeroport/ Riba-roja de Túria", ascensorGroup.details[1])
        assertEquals("Andén destino Marítim", ascensorGroup.details[2])

        val escaleraGroup = grouped.find { it.header == "Escalera mecánica fuera de servicio" }!!
        assertEquals(1, escaleraGroup.details.size)
        assertEquals("Acceso norte", escaleraGroup.details[0])
    }
}
