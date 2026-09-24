package com.example.util

import com.example.ui.metro.AccessibilityIncident

object AccessibilityNoticeFormatter {

    fun cleanAccessibilityNoticeText(text: String, stationName: String? = null): String {
        var result = text.trim()
        if (stationName != null && stationName.isNotBlank()) {
            val escaped = Regex.escape(stationName)
            result = result.replace(Regex("(?i)^$escaped\\s*[-:]\\s*"), "")
        }
        return result
    }

    fun deduplicateAccessibilityIncidents(
        incidents: List<AccessibilityIncident>,
        stationName: String? = null
    ): List<AccessibilityIncident> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<AccessibilityIncident>()
        for (item in incidents) {
            val key = "${item.id}_${cleanAccessibilityNoticeText(item.name, stationName)}"
            if (seen.add(key)) {
                result.add(item)
            }
        }
        return result
    }
}
