package com.example.data.util

import com.example.ui.dashboard.AppLanguage
import java.util.Calendar
import java.util.Locale

/**
 * Robust, user-friendly parser for OSM opening_hours tags.
 * Provides rich today status, human-readable full weekly schedules,
 * and day-by-day breakdowns.
 */
object OpeningHoursParser {

    data class DaySchedule(
        val dayKey: String, // "Mo", "Tu", ...
        val dayNameEs: String,
        val dayNameCa: String,
        val scheduleEs: String,
        val scheduleCa: String,
        val isToday: Boolean = false,
        val isClosed: Boolean = false
    )

    data class ParsedSchedule(
        val isOpenNow: Boolean?,
        val statusTextEs: String,
        val statusTextCa: String,
        val todayHoursEs: String?,
        val todayHoursCa: String?,
        val weeklySummaryEs: String,
        val weeklySummaryCa: String,
        val dailySchedules: List<DaySchedule>
    )

    private val DAYS_OF_WEEK = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    private val DAY_NAMES_ES = mapOf(
        "Mo" to "Lunes", "Tu" to "Martes", "We" to "Miércoles",
        "Th" to "Jueves", "Fr" to "Viernes", "Sa" to "Sábado", "Su" to "Domingo"
    )

    private val DAY_NAMES_CA = mapOf(
        "Mo" to "Dilluns", "Tu" to "Dimarts", "We" to "Dimecres",
        "Th" to "Dijous", "Fr" to "Divendres", "Sa" to "Dissabte", "Su" to "Diumenge"
    )

    fun parse(raw: String?, calendar: Calendar = Calendar.getInstance()): ParsedSchedule? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim()

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val todayCode = when (dayOfWeek) {
            Calendar.MONDAY -> "Mo"
            Calendar.TUESDAY -> "Tu"
            Calendar.WEDNESDAY -> "We"
            Calendar.THURSDAY -> "Th"
            Calendar.FRIDAY -> "Fr"
            Calendar.SATURDAY -> "Sa"
            Calendar.SUNDAY -> "Su"
            else -> "Mo"
        }

        if (normalized.equals("24/7", ignoreCase = true)) {
            val daily = DAYS_OF_WEEK.map { code ->
                DaySchedule(
                    dayKey = code,
                    dayNameEs = DAY_NAMES_ES[code] ?: code,
                    dayNameCa = DAY_NAMES_CA[code] ?: code,
                    scheduleEs = "Abierto 24 horas",
                    scheduleCa = "Obert 24 hores",
                    isToday = code == todayCode,
                    isClosed = false
                )
            }
            return ParsedSchedule(
                isOpenNow = true,
                statusTextEs = "Abierto",
                statusTextCa = "Obert",
                todayHoursEs = "24 horas",
                todayHoursCa = "24 hores",
                weeklySummaryEs = "Abierto todos los días las 24 horas",
                weeklySummaryCa = "Obert tots els dies les 24 hores",
                dailySchedules = daily
            )
        }

        val rules = normalized.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val dayMap = mutableMapOf<String, String>() // "Mo" -> "09:00 a 14:00, 17:00 a 20:30" or "Cerrado"

        for (rule in rules) {
            val isClosedRule = rule.contains("off", ignoreCase = true) || rule.contains("closed", ignoreCase = true)
            val timeRanges = extractTimeRanges(rule)
            val formattedTime = if (isClosedRule) "Cerrado" else if (timeRanges.isNotEmpty()) formatTimeRangesPretty(timeRanges) else rule

            val targetDays = resolveDaysForRule(rule)
            for (d in targetDays) {
                dayMap[d] = formattedTime
            }
        }

        // Fill days that were not specified with closed or default
        val dailySchedules = DAYS_OF_WEEK.map { code ->
            val sched = dayMap[code] ?: "No especificado"
            val isClosed = sched.equals("Cerrado", ignoreCase = true)
            val schedCa = when {
                isClosed -> "Tancat"
                sched == "No especificado" -> "No especificat"
                else -> sched.replace(" a ", " a ")
            }
            DaySchedule(
                dayKey = code,
                dayNameEs = DAY_NAMES_ES[code] ?: code,
                dayNameCa = DAY_NAMES_CA[code] ?: code,
                scheduleEs = sched,
                scheduleCa = schedCa,
                isToday = code == todayCode,
                isClosed = isClosed
            )
        }

        // Determine current open/closed status for today
        var isOpenNow: Boolean? = null
        var todayHoursEs: String? = null
        var todayHoursCa: String? = null

        val todaySched = dayMap[todayCode]
        if (todaySched != null) {
            if (todaySched.equals("Cerrado", ignoreCase = true)) {
                isOpenNow = false
                todayHoursEs = "Cerrado todo el día"
                todayHoursCa = "Tancat tot el dia"
            } else {
                // Find matching rule for today to get raw time ranges
                val matchingRule = rules.firstOrNull { dayMatches(it, todayCode) }
                if (matchingRule != null) {
                    val ranges = extractTimeRanges(matchingRule)
                    if (ranges.isNotEmpty()) {
                        isOpenNow = ranges.any { (start, end) ->
                            if (end < start) { // Over midnight (e.g. 20:00 - 02:00)
                                currentMinutes >= start || currentMinutes <= end
                            } else {
                                currentMinutes in start..end
                            }
                        }
                        todayHoursEs = formatTimeRangesPretty(ranges)
                        todayHoursCa = todayHoursEs
                    }
                }
            }
        }

        // Weekly summary calculation
        val summary = buildWeeklySummary(dailySchedules)

        val statusTextEs = when (isOpenNow) {
            true -> "Abierto"
            false -> "Cerrado"
            null -> "Horario"
        }
        val statusTextCa = when (isOpenNow) {
            true -> "Obert"
            false -> "Tancat"
            null -> "Horari"
        }

        return ParsedSchedule(
            isOpenNow = isOpenNow,
            statusTextEs = statusTextEs,
            statusTextCa = statusTextCa,
            todayHoursEs = todayHoursEs,
            todayHoursCa = todayHoursCa,
            weeklySummaryEs = summary.first,
            weeklySummaryCa = summary.second,
            dailySchedules = dailySchedules
        )
    }

    private fun resolveDaysForRule(rule: String): List<String> {
        // Strip out seasonal prefix like "Mar 15-Oct 15:" or "easter:"
        val cleanRule = if (rule.contains(":") && !rule.substringBefore(":").contains(Regex("\\d{1,2}:\\d{2}"))) {
            val prefix = rule.substringBefore(":")
            if (prefix.contains(Regex("[A-Za-z]{3}\\s*\\d+")) || prefix.contains("easter", ignoreCase = true)) {
                rule.substringAfter(":").trim()
            } else {
                rule
            }
        } else {
            rule
        }

        // Days part is everything before the first time pattern (e.g. "10:00" or "off" or "closed")
        val timeMatch = Regex("\\b(\\d{1,2}:\\d{2}|off|closed)\\b", RegexOption.IGNORE_CASE).find(cleanRule)
        val daysPart = if (timeMatch != null) {
            cleanRule.substring(0, timeMatch.range.first).trim()
        } else {
            cleanRule.trim()
        }

        val result = mutableListOf<String>()
        val dayTokens = daysPart.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        var matchedAny = false

        for (token in dayTokens) {
            if (token.contains("-")) {
                val parts = token.split("-").map { it.trim() }
                if (parts.size == 2) {
                    val start = parts[0]
                    val end = parts[1]
                    val startIndex = DAYS_OF_WEEK.indexOf(start)
                    val endIndex = DAYS_OF_WEEK.indexOf(end)
                    if (startIndex != -1 && endIndex != -1) {
                        matchedAny = true
                        if (startIndex <= endIndex) {
                            for (i in startIndex..endIndex) result.add(DAYS_OF_WEEK[i])
                        } else {
                            for (i in startIndex until DAYS_OF_WEEK.size) result.add(DAYS_OF_WEEK[i])
                            for (i in 0..endIndex) result.add(DAYS_OF_WEEK[i])
                        }
                    }
                }
            } else if (DAYS_OF_WEEK.contains(token)) {
                matchedAny = true
                result.add(token)
            } else if (token.equals("PH", ignoreCase = true) || token.equals("SH", ignoreCase = true)) {
                // Public holidays - associate with Sunday schedule by default for tourist attractions
                matchedAny = true
                result.add("Su")
            }
        }

        if (!matchedAny) {
            return DAYS_OF_WEEK
        }

        return result.distinct()
    }

    private fun dayMatches(rule: String, todayCode: String): Boolean {
        val days = resolveDaysForRule(rule)
        return days.contains(todayCode)
    }

    private fun extractTimeRanges(rule: String): List<Pair<Int, Int>> {
        val regex = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")
        val matches = regex.findAll(rule)
        val ranges = mutableListOf<Pair<Int, Int>>()

        for (m in matches) {
            val h1 = m.groupValues[1].toIntOrNull() ?: continue
            val m1 = m.groupValues[2].toIntOrNull() ?: continue
            val h2 = m.groupValues[3].toIntOrNull() ?: continue
            val m2 = m.groupValues[4].toIntOrNull() ?: continue

            val startMin = h1 * 60 + m1
            val endMin = h2 * 60 + m2
            ranges.add(Pair(startMin, endMin))
        }

        return ranges
    }

    private fun formatTimeRangesPretty(ranges: List<Pair<Int, Int>>): String {
        return ranges.joinToString(", ") { (start, end) ->
            val h1 = start / 60
            val m1 = start % 60
            val h2 = end / 60
            val m2 = end % 60
            String.format(Locale.getDefault(), "%02d:%02d a %02d:%02d", h1, m1, h2, m2)
        }
    }

    private fun buildWeeklySummary(schedules: List<DaySchedule>): Pair<String, String> {
        val distinctSchedules = schedules.map { it.scheduleEs }.distinct()
        if (distinctSchedules.size == 1 && distinctSchedules[0] != "No especificado") {
            val s = distinctSchedules[0]
            return Pair("Lunes a domingo de $s", "Dilluns a diumenge de $s")
        }

        val monToFri = schedules.subList(0, 5).map { it.scheduleEs }.distinct()
        val satSun = schedules.subList(5, 7).map { it.scheduleEs }.distinct()

        if (monToFri.size == 1 && satSun.size == 1) {
            val mf = monToFri[0]
            val ss = satSun[0]
            if (ss == "Cerrado") {
                return Pair("Lunes a viernes de $mf (Fines de semana cerrado)", "Dilluns a divendres de $mf (Caps de setmana tancat)")
            }
            return Pair("Lunes a viernes de $mf • Sáb-Dom $ss", "Dilluns a divendres de $mf • Diss-Diu $ss")
        }

        val today = schedules.find { it.isToday }
        val schedToday = today?.scheduleEs ?: "Consultar horario"
        return Pair("Hoy: $schedToday", "Hui: $schedToday")
    }
}
