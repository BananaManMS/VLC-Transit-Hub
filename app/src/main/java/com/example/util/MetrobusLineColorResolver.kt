package com.example.util

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for Metrobús Valencia line colors and badges across the application.
 * Matches the official Metrobús brand logo color (#F59E0B) defined in logo_metrobus.xml.
 */
object MetrobusLineColorResolver {
    /** Official Metrobús brand color (#F59E0B) matching logo_metrobus.xml */
    const val BRAND_HEX = "#F59E0B"
    val BRAND_COLOR = Color(0xFFF59E0B)
    val ON_BRAND_COLOR = Color.White

    /**
     * Resolves Compose Color for a Metrobús line.
     */
    fun getLineColor(lineId: String? = null): Color = BRAND_COLOR

    /**
     * Resolves HEX color string for a Metrobús line.
     */
    fun getLineColorHex(lineId: String? = null): String = BRAND_HEX

    /**
     * Returns neutral chip background color for multi-line stop list previews (coordinating with EMT neutral chips).
     */
    fun getNeutralChipBackground(isDarkMode: Boolean): Color {
        return if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
    }

    /**
     * Returns neutral chip text color for multi-line stop list previews.
     */
    fun getNeutralChipTextColor(isDarkMode: Boolean): Color {
        return if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF475569)
    }
}
