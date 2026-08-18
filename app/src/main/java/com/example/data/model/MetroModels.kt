package com.example.data.model

import kotlin.random.Random

data class MetroLine(
    val id: String,
    val name: String,
    val colorHex: String,
    val destinations: List<String>
)

data class MetroStation(
    val id: String,
    val name: String,
    val lines: List<String>, // list of line IDs
    val description: String,
    val latitude: Double = 39.4697, // default to Colón
    val longitude: Double = -0.3734,
    val zone: String = "A"
)

fun cleanZoneCode(rawZone: String?): String {
    if (rawZone.isNullOrBlank()) return "A"
    var z = rawZone.trim()
    if (z.startsWith("Zona", ignoreCase = true)) {
        z = z.substring(4).trim()
    }
    z = z.replace(" ", "").replace("-", "").replace(",", "").uppercase()
    if (z == "+") return "C"
    if (z.isEmpty()) return "A"
    return z
}

data class Departure(
    val id: String,
    val lineId: String,
    val destination: String,
    val minutesRemaining: Int,
    val platform: Int,
    val status: String // "On Time", "Delayed", "Departing", "Scheduled"
)

object ValenciaMetroData {
    val mainMetroStations = listOf(
        MetroStation("15", "Colón", listOf("L3", "L5", "L7", "L9"), "Zona A", 39.4697, -0.3734),
        MetroStation("1", "Xàtiva", listOf("L3", "L5", "L9"), "Zona A", 39.4668, -0.3774),
        MetroStation("2", "Àngel Guimerà", listOf("L1", "L2", "L3", "L5", "L9"), "Zona A", 39.4716, -0.3846),
        MetroStation("5", "Benimaclet", listOf("L3", "L4", "L6", "L9"), "Zona A", 39.4826, -0.3582),
        MetroStation("8", "Alameda", listOf("L3", "L5", "L7", "L9"), "Zona A", 39.4713, -0.3644),
        MetroStation("12", "Pl. Espanya", listOf("L1", "L2"), "Zona A", 39.4660, -0.3812),
        MetroStation("18", "Facultats", listOf("L3", "L9"), "Zona A", 39.4789, -0.3588),
        MetroStation("20", "Marítim", listOf("L5", "L6", "L7", "L8"), "Zona A", 39.4633, -0.3347),
        MetroStation("22", "Empalme", listOf("L1", "L2", "L4"), "Zona A", 39.4975, -0.3980),
        MetroStation("25", "Amistat", listOf("L5", "L7"), "Zona A", 39.4699, -0.3521)
    )

    val lines = listOf(
        MetroLine("L1", "Line 1", "#F59E0B", listOf("Bétera", "Castelló", "Seminari-CEU", "Picassent", "l'Alcúdia")),
        MetroLine("L2", "Line 2", "#EC4899", listOf("Llíria", "Torrent Avinguda", "Paterna")),
        MetroLine("L3", "Line 3", "#EF4444", listOf("Rafelbunyol", "Aeroport", "Alboraya Peris Aragó")),
        MetroLine("L4", "Line 4 (Tram)", "#003366", listOf("Mas del Rosari", "Doctor Lluch", "Vicent Andrés Estellés", "Lloma Llarga Terramelar")),
        MetroLine("L5", "Line 5", "#10B981", listOf("Marítim", "Aeroport")),
        MetroLine("L6", "Line 6 (Tram)", "#8B5CF6", listOf("Tossal del Rei", "Marítim")),
        MetroLine("L7", "Line 7", "#F97316", listOf("Marítim", "Torrent Avinguda")),
        MetroLine("L8", "Line 8 (Tram)", "#14B8A6", listOf("Marítim", "Marina Reial")),
        MetroLine("L9", "Line 9", "#78350F", listOf("Alboraya Peris Aragó", "Riba-roja de Túria")),
        MetroLine("L10", "Line 10 (Tram)", "#84CC16", listOf("Alacant", "Natzaret")),
        MetroLine("Cercanías", "Cercanías", "#FF5500", listOf("C1 Gandia / Platja de Gandia", "C2 Moixent / Xàtiva", "C3 Utiel / Buñol", "C5 Caudiel", "C6 Castelló de la Plana / Vinaròs"))
    )

    data class DepartureRule(
        val lineId: String,
        val baseDestination: String,
        val platform: Int,
        val intervalMinutes: Int,
        val offsetMinutes: Int
    )

    val stationRules = mapOf(
        "xativa" to listOf(
            DepartureRule("L3", "Rafelbunyol", 1, 15, 3),
            DepartureRule("L3", "Aeroport", 2, 15, 7),
            DepartureRule("L5", "Marítim", 1, 15, 10),
            DepartureRule("L5", "Aeroport", 2, 15, 12),
            DepartureRule("L9", "Alboraya Peris Aragó", 1, 20, 5),
            DepartureRule("L9", "Riba-roja de Túria", 2, 20, 15)
        ),
        "estacio_del_nord" to listOf(
            DepartureRule("L3", "Rafelbunyol", 1, 15, 3),
            DepartureRule("L3", "Aeroport", 2, 15, 7),
            DepartureRule("L5", "Marítim", 1, 15, 10),
            DepartureRule("L5", "Aeroport", 2, 15, 12),
            DepartureRule("L9", "Alboraya Peris Aragó", 1, 20, 5),
            DepartureRule("L9", "Riba-roja de Túria", 2, 20, 15)
        ),
        "colon" to listOf(
            DepartureRule("L3", "Rafelbunyol", 1, 15, 5),
            DepartureRule("L3", "Aeroport", 2, 15, 5),
            DepartureRule("L5", "Marítim", 1, 15, 12),
            DepartureRule("L5", "Aeroport", 2, 15, 10),
            DepartureRule("L7", "Marítim", 1, 15, 2),
            DepartureRule("L7", "Torrent Avinguda", 2, 15, 8),
            DepartureRule("L9", "Alboraya Peris Aragó", 1, 20, 7),
            DepartureRule("L9", "Riba-roja de Túria", 2, 20, 13)
        ),
        "alameda" to listOf(
            DepartureRule("L3", "Rafelbunyol", 2, 15, 7),
            DepartureRule("L3", "Aeroport", 1, 15, 3),
            DepartureRule("L5", "Marítim", 4, 15, 14),
            DepartureRule("L5", "Aeroport", 3, 15, 8),
            DepartureRule("L7", "Marítim", 4, 15, 4),
            DepartureRule("L7", "Torrent Avinguda", 3, 15, 6),
            DepartureRule("L9", "Alboraya Peris Aragó", 2, 20, 9),
            DepartureRule("L9", "Riba-roja de Túria", 1, 20, 11)
        ),
        "angel_guimera" to listOf(
            DepartureRule("L1", "Bétera", 1, 20, 4),
            DepartureRule("L1", "Castelló", 2, 20, 14),
            DepartureRule("L2", "Llíria", 1, 20, 9),
            DepartureRule("L2", "Torrent Avinguda", 2, 20, 19),
            DepartureRule("L3", "Rafelbunyol", 3, 15, 1),
            DepartureRule("L3", "Aeroport", 4, 15, 9),
            DepartureRule("L5", "Marítim", 3, 15, 8),
            DepartureRule("L5", "Aeroport", 4, 15, 14),
            DepartureRule("L9", "Alboraya Peris Aragó", 3, 20, 3),
            DepartureRule("L9", "Riba-roja de Túria", 4, 20, 17)
        ),
        "benimaclet" to listOf(
            DepartureRule("L3", "Rafelbunyol", 1, 15, 11),
            DepartureRule("L3", "Aeroport", 2, 15, 14),
            DepartureRule("L9", "Alboraya Peris Aragó", 1, 20, 13),
            DepartureRule("L9", "Riba-roja de Túria", 2, 20, 7),
            DepartureRule("L4", "Doctor Lluch", 3, 12, 2),
            DepartureRule("L4", "Mas del Rosari", 4, 12, 8),
            DepartureRule("L6", "Marítim", 3, 20, 5),
            DepartureRule("L6", "Tossal del Rei", 4, 20, 15)
        ),
        "torrent_avinguda" to listOf(
            DepartureRule("L2", "Llíria", 1, 20, 0),
            DepartureRule("L7", "Marítim", 2, 15, 5)
        ),
        "maritim" to listOf(
            DepartureRule("L5", "Aeroport", 1, 15, 3),
            DepartureRule("L7", "Torrent Avinguda", 2, 15, 11),
            DepartureRule("L6", "Tossal del Rei", 3, 20, 4),
            DepartureRule("L8", "Marina Reial", 4, 15, 7)
        ),
        "alacant" to listOf(
            DepartureRule("L10", "Natzaret", 1, 15, 4),
            DepartureRule("L10", "Natzaret", 2, 15, 11)
        ),
        "aeroport" to listOf(
            DepartureRule("L3", "Rafelbunyol", 1, 15, 2),
            DepartureRule("L5", "Marítim", 2, 15, 10)
        ),
        "rosas" to listOf(
            DepartureRule("L3", "Rafelbunyol", 1, 15, 4),
            DepartureRule("L3", "Aeroport", 2, 15, 8),
            DepartureRule("L5", "Marítim", 1, 15, 12),
            DepartureRule("L5", "Aeroport", 2, 15, 6),
            DepartureRule("L9", "Alboraya Peris Aragó", 1, 20, 9),
            DepartureRule("L9", "Riba-roja de Túria", 2, 20, 11)
        ),
        "facultats" to listOf(
            DepartureRule("L3", "Rafelbunyol", 1, 15, 9),
            DepartureRule("L3", "Aeroport", 2, 15, 11),
            DepartureRule("L9", "Alboraya Peris Aragó", 1, 20, 12),
            DepartureRule("L9", "Riba-roja de Túria", 2, 20, 14)
        ),
        "empalme" to listOf(
            DepartureRule("L1", "Bétera", 1, 20, 2),
            DepartureRule("L1", "Castelló", 2, 20, 12),
            DepartureRule("L2", "Llíria", 1, 20, 7),
            DepartureRule("L2", "Torrent Avinguda", 2, 20, 17),
            DepartureRule("L4", "Doctor Lluch", 3, 12, 4),
            DepartureRule("L4", "Mas del Rosari", 4, 12, 10)
        ),
        "plaza_espana" to listOf(
            DepartureRule("L1", "Bétera", 1, 20, 6),
            DepartureRule("L1", "Castelló", 2, 20, 16),
            DepartureRule("L2", "Llíria", 1, 20, 11),
            DepartureRule("L2", "Torrent Avinguda", 2, 20, 1)
        ),
        "amistat" to listOf(
            DepartureRule("L5", "Marítim", 1, 15, 14),
            DepartureRule("L5", "Aeroport", 2, 15, 8),
            DepartureRule("L7", "Marítim", 1, 15, 6),
            DepartureRule("L7", "Torrent Avinguda", 2, 15, 4)
        ),
        "tarongers" to listOf(
            DepartureRule("L4", "Doctor Lluch", 1, 12, 8),
            DepartureRule("L4", "Mas del Rosari", 2, 12, 2),
            DepartureRule("L6", "Marítim", 1, 20, 11),
            DepartureRule("L6", "Tossal del Rei", 2, 20, 9)
        ),
        "pont_de_fusta" to listOf(
            DepartureRule("L4", "Doctor Lluch", 1, 12, 5),
            DepartureRule("L4", "Mas del Rosari", 2, 12, 11)
        )
    )

    fun getLine(id: String): MetroLine? = lines.find { it.id == id }

    fun generateDeparturesForStation(stationId: String, stationLines: List<String> = emptyList(), seed: Long = System.currentTimeMillis()): List<Departure> {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)

        val result = mutableListOf<Departure>()
        val rulesForStation = stationRules[stationId]?.toMutableList() ?: mutableListOf()

        if (rulesForStation.isEmpty() && stationLines.isNotEmpty()) {
            var platformNum = 1
            stationLines.forEach { lineId ->
                val lineObj = lines.find { it.id == lineId }
                val dests = lineObj?.destinations ?: listOf("Valencia")
                dests.take(2).forEach { dest ->
                    rulesForStation.add(DepartureRule(lineId, dest, platformNum++, 15, 5))
                }
            }
        }

        rulesForStation.forEach { rule ->
            // Let's generate 2 upcoming departures for this rule/direction
            var depMin1 = rule.offsetMinutes
            while (depMin1 < currentMinute) {
                depMin1 += rule.intervalMinutes
            }
            val minsRemaining1 = depMin1 - currentMinute

            // The second departure is simply after one full interval
            val minsRemaining2 = minsRemaining1 + rule.intervalMinutes

            listOf(minsRemaining1, minsRemaining2).forEach { mins ->
                val departureMinute = (currentMinute + mins) % 60
                
                // Determine destination with realistic variation based on departureMinute
                val dest = if (rule.lineId == "L1" && rule.baseDestination == "Bétera") {
                    if (departureMinute % 3 == 0) "Seminari-CEU" else "Bétera"
                } else if (rule.lineId == "L1" && rule.baseDestination == "Castelló") {
                    if (departureMinute % 3 == 0) "Picassent" else if (departureMinute % 4 == 0) "l'Alcúdia" else "Castelló"
                } else if (rule.lineId == "L2" && rule.baseDestination == "Llíria") {
                    if (departureMinute % 3 == 0) "Paterna" else "Llíria"
                } else if (rule.lineId == "L4" && rule.baseDestination == "Mas del Rosari") {
                    if (departureMinute % 3 == 0) "Vicent Andrés Estellés" else if (departureMinute % 3 == 1) "Lloma Llarga Terramelar" else "Mas del Rosari"
                } else {
                    rule.baseDestination
                }

                // Determine status based on mins
                val status = when {
                    mins <= 1 -> "Departing"
                    departureMinute % 13 == 0 -> "Delayed (+2m)"
                    else -> "On Time"
                }

                result.add(
                    Departure(
                        id = "${rule.lineId}_${dest}_${currentHour}_${departureMinute}_${rule.platform}",
                        lineId = rule.lineId,
                        destination = dest,
                        minutesRemaining = mins,
                        platform = rule.platform,
                        status = status
                    )
                )
            }
        }

        return result.sortedWith(compareBy({ it.minutesRemaining }, { it.lineId }))
    }
}
