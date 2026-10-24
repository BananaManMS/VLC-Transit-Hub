package com.example.ui.cercanias

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LiveTrainFormatUtils {
    fun formatEstimatedArrivalTime(rawTime: String): String {
        if (rawTime.isBlank()) return ""
        val trimmed = rawTime.trim()

        val numericTs = trimmed.toLongOrNull()
        if (numericTs != null && numericTs > 1_000_000_000L) {
            val millis = if (numericTs < 10_000_000_000L) numericTs * 1000L else numericTs
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("Europe/Madrid")
            }
            return sdf.format(Date(millis))
        }

        val timePart = when {
            trimmed.contains("T", ignoreCase = true) -> trimmed.split(Regex("(?i)T")).getOrNull(1) ?: trimmed
            trimmed.contains(" ") -> trimmed.split(" ").getOrNull(1) ?: trimmed
            else -> trimmed
        }.trim()

        val match = Regex("""^(\d{1,2})[.:](\d{2})(?:[.:]\d{2})?""").find(timePart)
        if (match != null) {
            val (h, m) = match.destructured
            val hh = h.padStart(2, '0')
            return "$hh:$m"
        }

        val matchAnywhere = Regex("""(\d{1,2})[.:](\d{2})""").find(timePart)
        if (matchAnywhere != null) {
            val (h, m) = matchAnywhere.destructured
            val hh = h.padStart(2, '0')
            return "$hh:$m"
        }

        return trimmed
    }
}
