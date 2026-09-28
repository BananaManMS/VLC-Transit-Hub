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

    fun deduplicateAccessibilityTexts(texts: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<String>()
        val sorted = texts.sortedByDescending { it.length }
        for (text in sorted) {
            val trimmed = text.trim()
            if (trimmed.isBlank()) continue
            val normalized = trimmed.lowercase().removeSuffix(".")
            val alreadyCovered = seen.any { it.contains(normalized) }
            if (!alreadyCovered) {
                seen.add(normalized)
                result.add(trimmed)
            }
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

    fun deduplicateCercaniasAccessibilityAlerts(
        alerts: List<com.example.ui.cercanias.CercaniasAlert>,
        stationName: String? = null
    ): List<com.example.ui.cercanias.CercaniasAlert> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<com.example.ui.cercanias.CercaniasAlert>()
        for (alert in alerts) {
            val cleanHeader = cleanAccessibilityNoticeText(alert.headerEs, stationName)
            if (seen.add(cleanHeader)) {
                result.add(alert)
            }
        }
        return result
    }

    data class AccessibilityNoticeGroup(
        val category: String,
        val details: List<String>
    )

    fun groupAccessibilityTexts(texts: List<String>): List<AccessibilityNoticeGroup> {
        val groups = LinkedHashMap<String, MutableList<String>>()
        for (text in texts) {
            val parts = text.split(" - ", limit = 2)
            val category = parts[0].trim()
            val detail = if (parts.size > 1) parts[1].trim() else ""
            groups.getOrPut(category) { mutableListOf() }.add(detail)
        }
        return groups.map { (cat, details) -> AccessibilityNoticeGroup(cat, details) }
    }
}
