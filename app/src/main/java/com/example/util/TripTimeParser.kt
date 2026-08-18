package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Robust date/time parsing utilities for transit itineraries and departure/arrival timestamps.
 */
object TripTimeParser {

    private val TIME_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "HH:mm:ss",
        "HH:mm"
    )

    /**
     * Parses a timestamp string (epoch millis, ISO-8601, or HH:mm) into epoch milliseconds.
     * If the format is only HH:mm, it reconciles with today's date (or tomorrow if the time is past midnight).
     */
    fun parseTimeToMillis(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        val trimmed = timeStr.trim()

        // 1. Direct epoch millis
        trimmed.toLongOrNull()?.let { return it }

        // 2. ISO-8601 with timezone (e.g. 2026-08-17T09:45:00Z or 2026-08-17T11:45:00+02:00)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    return java.time.Instant.parse(trimmed).toEpochMilli()
                } catch (_: Exception) {
                    try {
                        return java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        // Fallback ISO check using local timezone if no explicit zone offset
        if (trimmed.contains("T")) {
            try {
                val cleanIso = trimmed.substringBefore("Z").substringBefore("+")
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                sdf.parse(cleanIso)?.time?.let { return it }
            } catch (_: Exception) {}
        }

        // 3. Short time format (HH:mm or HH:mm:ss)
        if (!trimmed.contains("-") && (trimmed.length == 5 || trimmed.length == 8 || trimmed.contains(":"))) {
            val parts = trimmed.split(":")
            if (parts.size >= 2) {
                val h = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (h != null && m != null) {
                    val nowCal = Calendar.getInstance()
                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, h)
                        set(Calendar.MINUTE, m)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // If parsed time is > 6h in the past compared to now, assume tomorrow
                    if (targetCal.timeInMillis < nowCal.timeInMillis - 6 * 3600 * 1000L) {
                        targetCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return targetCal.timeInMillis
                }
            }
        }

        // 4. Fallback SimpleDateFormat patterns for local datetime strings
        val timePatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )
        for (pattern in timePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val date = sdf.parse(trimmed)
                if (date != null) {
                    return date.time
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Shifts a formatted HH:mm time string by a given number of minutes (+ or -).
     */
    fun shiftFormattedTime(timeStr: String, minutesToAdd: Int): String {
        if (timeStr.isBlank() || minutesToAdd == 0) return timeStr
        return try {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                var hours = parts[0].trim().toInt()
                var minutes = parts[1].trim().take(2).toInt()
                minutes += minutesToAdd
                while (minutes >= 60) {
                    minutes -= 60
                    hours = (hours + 1) % 24
                }
                while (minutes < 0) {
                    minutes += 60
                    hours = (hours - 1 + 24) % 24
                }
                String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
            } else {
                timeStr
            }
        } catch (e: Exception) {
            timeStr
        }
    }

    /**
     * Formats current time plus given minutes into HH:mm format.
     */
    fun addMinutesToNow(minutesToAdd: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, minutesToAdd.coerceAtLeast(0))
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(cal.time)
    }
}
