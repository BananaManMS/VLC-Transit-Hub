package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.data.model.routing.TransitMode

object LineColorResolver {

    /**
     * Resolves the official color in HEX format for a Metrovalencia line.
     */
    fun getMetroLineColorHex(lineId: String): String {
        val clean = lineId.uppercase().trim()
            .replace(Regex("^(METRO|LÍNEA|LINEA|TRAM|METROVALENCIA)\\s*"), "")
            .replace("L-", "L")
            .replace("L ", "L")
            .trim()
        val digits = clean.filter { it.isDigit() }
        val lineKey = when {
            clean.startsWith("L") -> clean
            digits.isNotEmpty() -> "L$digits"
            else -> clean
        }

        return when (lineKey) {
            "L1" -> "#E1A92A"
            "L2" -> "#B3257D"
            "L3" -> "#C41833"
            "L4" -> "#1E4B90"
            "L5" -> "#068E63"
            "L6" -> "#7657AA"
            "L7" -> "#DA7A18"
            "L8" -> "#52BACC"
            "L9" -> "#A16E42"
            "L10" -> "#B3CB6D"
            else -> "#1E88E5" // Fallback standard blue
        }
    }

    /**
     * Resolves the official color in HEX format for a Cercanías Valencia line.
     */
    fun getCercaniasLineColorHex(lineId: String): String {
        val clean = lineId.uppercase().trim()
            .replace(Regex("^(CERCANÍAS|CERCANIAS|RENFE|RODALIES)\\s*"), "")
            .replace("C-", "C")
            .replace("C ", "C")
            .trim()
        val digits = clean.filter { it.isDigit() }
        val lineKey = when {
            clean.startsWith("C") -> clean
            digits.isNotEmpty() -> "C$digits"
            else -> clean
        }

        return when (lineKey) {
            "C1" -> "#30A3DC"
            "C2" -> "#FAB700"
            "C3" -> "#800080"
            "C4" -> "#FF0000"
            "C5" -> "#008A29"
            "C6" -> "#002D9A"
            else -> "#702B7B" // Fallback Renfe purple
        }
    }

    /**
     * Resolves Compose Color for Metrovalencia.
     */
    fun getMetroLineColor(lineId: String): Color {
        return Color(android.graphics.Color.parseColor(getMetroLineColorHex(lineId)))
    }

    /**
     * Resolves Compose Color for Cercanías.
     */
    fun getCercaniasLineColor(lineId: String): Color {
        return Color(android.graphics.Color.parseColor(getCercaniasLineColorHex(lineId)))
    }

    /**
     * Resolves a smart color in HEX based on mode and line name, falling back to routeColorHex or defaults.
     */
    fun resolveRouteColorHex(mode: TransitMode, routeShortName: String?, routeColorHex: String?, agencyName: String? = null): String {
        if (mode == TransitMode.WALK) return "#9E9E9E"
        if (mode == TransitMode.BICYCLE) return "#10B981"
        val shortName = routeShortName ?: ""
        val agency = agencyName ?: ""
        if (mode == TransitMode.SUBWAY || mode == TransitMode.TRAM) {
            if (shortName.isNotEmpty()) {
                return getMetroLineColorHex(shortName)
            }
        } else if (mode == TransitMode.RAIL) {
            if (shortName.isNotEmpty()) {
                return getCercaniasLineColorHex(shortName)
            }
        } else if (mode == TransitMode.BUS) {
            val isEmt = agency.contains("emt", ignoreCase = true) && !agency.contains("metrobus", ignoreCase = true) && !agency.contains("metropolitana", ignoreCase = true)
            val isMetrobus = !isEmt && (
                    agency.contains("metrobus", ignoreCase = true) ||
                    agency.contains("metrobús", ignoreCase = true) ||
                    agency.contains("metropolitana", ignoreCase = true) ||
                    agency.contains("consorci", ignoreCase = true) ||
                    agency.contains("atmv", ignoreCase = true) ||
                    agency.contains("fernan", ignoreCase = true) ||
                    agency.contains("avsa", ignoreCase = true) ||
                    agency.contains("buñol", ignoreCase = true) ||
                    agency.contains("bunyol", ignoreCase = true) ||
                    agency.contains("herca", ignoreCase = true) ||
                    agency.contains("edetania", ignoreCase = true) ||
                    agency.contains("edsa", ignoreCase = true) ||
                    agency.contains("hispano", ignoreCase = true) ||
                    agency.contains("ribera", ignoreCase = true) ||
                    agency.contains("auvaca", ignoreCase = true) ||
                    shortName.startsWith("L1") || shortName.startsWith("L2") || shortName.startsWith("L3") ||
                    (shortName.startsWith("1") && shortName.filter { it.isDigit() }.length >= 3)
            )
            if (isMetrobus) {
                return "#D97706" // Darker Metrobus orange-yellow for better text contrast
            }
            return "#E52320" // Standard Valencia EMT Red
        }

        if (!routeColorHex.isNullOrBlank()) {
            val clean = routeColorHex.trim()
            return if (clean.startsWith("#")) clean else "#$clean"
        }

        return when (mode) {
            TransitMode.WALK -> "#9E9E9E"
            TransitMode.BICYCLE -> "#10B981"
            TransitMode.BUS -> "#E52320"
            TransitMode.SUBWAY, TransitMode.TRAM -> "#1E88E5"
            TransitMode.RAIL -> "#702B7B"
            else -> "#0284C7"
        }
    }

    /**
     * Resolves Compose Color.
     */
    fun resolveRouteColor(mode: TransitMode, routeShortName: String?, routeColorHex: String?, agencyName: String? = null): Color {
        return Color(android.graphics.Color.parseColor(resolveRouteColorHex(mode, routeShortName, routeColorHex, agencyName)))
    }
}
