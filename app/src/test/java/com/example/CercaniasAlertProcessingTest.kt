package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CercaniasAlertProcessingTest {

    private val valenciaValidLines = listOf("C1", "C2", "C3", "C4", "C5", "C6")

    private fun normalizeValenciaRouteId(raw: String): String? {
        val upper = raw.uppercase(Locale.ROOT).trim()

        val directMatch = Regex("""^C-?([1-6])$""").find(upper)
        if (directMatch != null) {
            return "C${directMatch.groupValues[1]}"
        }

        val valenciaCodeMatch = Regex("""^40[A-Z0-9]*?(C-?[1-6])$""").find(upper)
        if (valenciaCodeMatch != null) {
            return valenciaCodeMatch.groupValues[1].replace("-", "")
        }

        for (line in valenciaValidLines) {
            if (upper.endsWith(line) || upper.endsWith("C-${line.removePrefix("C")}")) {
                return line
            }
        }

        return null
    }

    private fun extractLinesFromAlertText(text: String): List<String> {
        val found = mutableSetOf<String>()
        if (text.isBlank()) return emptyList()

        val lineRegex = Regex("""(?i)\b(?:L[íi]nea|L[ií]nia|L[íi]neas|L[ií]nies|Autob[uú]s|Servicio\s+(?:por\s+autob[uú]s\s+)?)\s*(C-?[1-6])\b""")
        lineRegex.findAll(text).forEach { match ->
            val num = match.groupValues[1].uppercase(Locale.ROOT).replace("-", "")
            found.add(num)
        }

        val anyLineRegex = Regex("""\b(C-?[1-6])\b""", RegexOption.IGNORE_CASE)
        anyLineRegex.findAll(text).forEach { match ->
            val num = match.groupValues[1].uppercase(Locale.ROOT).replace("-", "")
            found.add(num)
        }

        val startLineRegex = Regex("""(?i)^\s*(?:L[íi]nea\s+|L[ií]nia\s+)?(C-?[1-6])[\s.:\-]""")
        startLineRegex.find(text)?.let { match ->
            found.add(match.groupValues[1].uppercase(Locale.ROOT).replace("-", ""))
        }

        return valenciaValidLines.filter { found.contains(it) }
    }

    private fun resolveAlertRoutes(
        headerText: String,
        descText: String,
        rawRouteIds: List<String>
    ): List<String> {
        val linesInText = extractLinesFromAlertText("$headerText. $descText")
        val entityRouteIds = rawRouteIds.mapNotNull { normalizeValenciaRouteId(it) }.distinct()

        return if (linesInText.isNotEmpty()) {
            linesInText
        } else if (entityRouteIds.isNotEmpty()) {
            valenciaValidLines.filter { entityRouteIds.contains(it) }
        } else {
            emptyList()
        }
    }

    @Test
    fun testUserScreenshotCase_SingleTrainCancellationOnC1() {
        val rawRouteIds = listOf(
            "C3", "C5", "C2", "C6", "40T009C4", "40T0066ER24", "C1", "40T0010C4", "40T0065ER24"
        )
        val text = "Línea C1. Tren con salida prevista a las 16:43h de la estación de Gandia con destino València Nord, hoy no circula. Avería tren anterior."

        val routes = resolveAlertRoutes("", text, rawRouteIds)

        assertEquals(listOf("C1"), routes)
    }

    @Test
    fun testSingleTrainDelayOnC2() {
        val rawRouteIds = listOf("40C2")
        val text = "Línea C2. Tren con salida prevista a las 15:00h de la estación de València Nord con destino Xàtiva, circula por La Pobla Llarga con 16 minutos de retraso."

        val routes = resolveAlertRoutes("", text, rawRouteIds)

        assertEquals(listOf("C2"), routes)
    }

    @Test
    fun testBusSubstitutionOnC5() {
        val rawRouteIds = listOf("40C5")
        val text = "SERVICIO POR AUTOBÚS C-5: Plan alternativo de transporte por carretera."

        val routes = resolveAlertRoutes("", text, rawRouteIds)

        assertEquals(listOf("C5"), routes)
    }

    @Test
    fun testMultiLineIncidentInText() {
        val rawRouteIds = listOf("40C1", "40C2", "40C6")
        val text = "Avería de infraestructura que afecta a las líneas C1, C2 y C6 entre València Nord y Cabanyal."

        val routes = resolveAlertRoutes("", text, rawRouteIds)

        assertEquals(listOf("C1", "C2", "C6"), routes)
    }

    @Test
    fun testRouteIdNormalizationFromEntitiesWhenNoTextLine() {
        val rawRouteIds = listOf("40T009C4", "40C2", "40T0066ER24")
        val text = "Retrasos generalizados por causas meteorológicas en la zona de l'Horta Sud."

        val routes = resolveAlertRoutes("", text, rawRouteIds)

        // ER is skipped, 40T009C4 -> C4, 40C2 -> C2, ordered naturally: C2, C4
        assertEquals(listOf("C2", "C4"), routes)
    }
}
