package com.example.ui.metro

import com.example.ui.dashboard.AppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun parseTimeAgo(dateTimeStr: String, appLanguage: AppLanguage): String {
    try {
        var normalizedStr = dateTimeStr
        if (dateTimeStr.contains(".") && dateTimeStr.endsWith("Z")) {
            val dotIdx = dateTimeStr.indexOf(".")
            val zIdx = dateTimeStr.indexOf("Z")
            if (dotIdx in 0 until zIdx) {
                val beforeDot = dateTimeStr.substring(0, dotIdx)
                var frac = dateTimeStr.substring(dotIdx + 1, zIdx)
                if (frac.length > 3) {
                    frac = frac.substring(0, 3)
                } else {
                    while (frac.length < 3) {
                        frac += "0"
                    }
                }
                normalizedStr = "${beforeDot}.${frac}Z"
            }
        }

        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )

        var date: Date? = null
        for (format in formats) {
            try {
                if (format.toPattern().contains("'Z'")) {
                    format.timeZone = TimeZone.getTimeZone("UTC")
                }
                date = format.parse(normalizedStr)
                if (date != null) break
            } catch (e: Exception) {
                // Continue trying other formats
            }
        }

        if (date == null) return ""

        val now = Date().time
        val diffMs = now - date.time
        if (diffMs <= 0) return if (appLanguage == AppLanguage.CA) "ara" else "ahora"

        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHour = diffMin / 60
        val diffDay = diffHour / 24

        val isCa = appLanguage == AppLanguage.CA

        return when {
            diffMin < 1 -> if (isCa) "fa uns segons" else "hace unos segundos"
            diffMin < 60 -> if (isCa) "fa $diffMin min" else "hace $diffMin min"
            diffHour < 24 -> if (isCa) "fa $diffHour h" else "hace $diffHour h"
            diffDay < 7 -> if (isCa) "fa $diffDay d" else "hace $diffDay d"
            else -> {
                val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                outputFormat.format(date)
            }
        }
    } catch (e: Exception) {
        return ""
    }
}
