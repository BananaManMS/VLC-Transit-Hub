package com.example.ui.metro

import com.example.ui.dashboard.AppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun parseTimeAgo(dateTimeStr: String?, appLanguage: AppLanguage, isUpdated: Boolean = false): String? {
    if (dateTimeStr.isNullOrBlank() || dateTimeStr.trim().equals("null", ignoreCase = true)) {
        return null
    }

    try {
        val s = dateTimeStr.trim()
        var normalizedStr = s

        if (s.contains(".") && s.endsWith("Z")) {
            val dotIdx = s.indexOf(".")
            val zIdx = s.indexOf("Z")
            if (dotIdx in 0 until zIdx) {
                val beforeDot = s.substring(0, dotIdx)
                var frac = s.substring(dotIdx + 1, zIdx)
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
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US)
        )

        var date: Date? = null
        for (format in formats) {
            try {
                date = format.parse(normalizedStr)
                if (date != null) break
            } catch (e: Exception) {
                // Try next format
            }
        }

        if (date == null) return null

        val now = System.currentTimeMillis()
        val diffMs = now - date.time

        val isCa = appLanguage == AppLanguage.CA

        if (diffMs <= 0) {
            return if (isUpdated) {
                if (isCa) "Actualitzat fa uns moments" else "Actualizado hace unos momentos"
            } else {
                if (isCa) "Fa uns moments" else "Hace unos momentos"
            }
        }

        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHour = diffMin / 60
        val diffDay = diffHour / 24
        val diffMonth = diffDay / 30
        val diffYear = diffDay / 365

        val prefix = if (isUpdated) {
            if (isCa) "Actualitzat fa " else "Actualizado hace "
        } else {
            if (isCa) "Fa " else "Hace "
        }

        val relativeText = when {
            diffMin < 1 -> if (isCa) "uns moments" else "unos momentos"
            diffMin == 1L -> if (isCa) "1 minut" else "1 minuto"
            diffMin < 60 -> if (isCa) "$diffMin minuts" else "$diffMin minutos"
            diffHour == 1L -> if (isCa) "1 hora" else "1 hora"
            diffHour < 24 -> if (isCa) "$diffHour hores" else "$diffHour horas"
            diffDay == 1L -> if (isCa) "1 dia" else "1 día"
            diffDay < 30 -> if (isCa) "$diffDay dies" else "$diffDay días"
            diffMonth == 1L -> if (isCa) "1 mes" else "1 mes"
            diffMonth < 12 -> if (isCa) "$diffMonth mesos" else "$diffMonth meses"
            diffYear == 1L -> if (isCa) "1 any" else "1 año"
            else -> if (isCa) "$diffYear anys" else "$diffYear años"
        }

        return "$prefix$relativeText"
    } catch (e: Exception) {
        return null
    }
}
