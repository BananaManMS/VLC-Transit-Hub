package com.example.ui.dashboard

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DashboardTimeUtils {

    private val supportedPatterns = listOf(
        "HH:mm",
        "HH:mm:ss",
        "hh:mm a",
        "hh:mm:ss a",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS"
    )

    fun isTimeInNextWindow(timeStr: String, windowMinutes: Int = 120): Boolean {
        if (timeStr.isBlank()) {
            Log.w("DashboardTimeUtils", "isTimeInNextWindow received blank or empty time string.")
            return false
        }

        val trimmed = timeStr.trim()

        var targetMillis: Long? = null
        var lastException: Exception? = null

        val epochMillis = trimmed.toLongOrNull()
        if (epochMillis != null) {
            targetMillis = epochMillis
        } else {
            for (pattern in supportedPatterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                    val parsedDate = sdf.parse(trimmed)
                    if (parsedDate != null) {
                        val calNow = Calendar.getInstance()
                        val targetCal = Calendar.getInstance().apply { time = parsedDate }

                        if (!pattern.contains("yyyy") && !pattern.contains("MM")) {
                            calNow.set(Calendar.HOUR_OF_DAY, targetCal.get(Calendar.HOUR_OF_DAY))
                            calNow.set(Calendar.MINUTE, targetCal.get(Calendar.MINUTE))
                            calNow.set(Calendar.SECOND, targetCal.get(Calendar.SECOND))
                            calNow.set(Calendar.MILLISECOND, 0)
                            targetMillis = calNow.timeInMillis
                        } else {
                            targetMillis = parsedDate.time
                        }
                        break
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
        }

        if (targetMillis == null) {
            Log.w(
                "DashboardTimeUtils",
                "Failed to parse time string '$timeStr' in isTimeInNextWindow. Reason: ${lastException?.message ?: "Unrecognized or unsupported time format"}"
            )
            return false
        }

        val now = System.currentTimeMillis()
        val windowEnd = now + (windowMinutes * 60 * 1000L)
        return targetMillis in now..windowEnd
    }
}
