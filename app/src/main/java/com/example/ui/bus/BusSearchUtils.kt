package com.example.ui.bus

import com.example.data.database.GeoportalStopEntity
import com.example.data.model.NominatimResult
import com.example.util.isBilingualTokenMatch
import com.example.util.isSubsequence
import com.example.util.levenshteinDistance
import com.example.util.normalizeForSearch
import java.util.Locale

fun computeSearchScore(
    stopId: String,
    stopName: String,
    query: String,
    alias: String? = null,
    lines: List<String> = emptyList()
): Double {
    val qNorm = query.normalizeForSearch()
    if (qNorm.isEmpty()) return 0.0

    val aliasNorm = alias?.normalizeForSearch() ?: ""
    if (aliasNorm.isNotEmpty()) {
        if (aliasNorm == qNorm) return 1100.0
        if (aliasNorm.startsWith(qNorm)) return 900.0 + (100.0 / aliasNorm.length)
        if (aliasNorm.contains(qNorm)) return 650.0
    }

    val numberNorm = stopId.normalizeForSearch()
    val nameNorm = stopName.normalizeForSearch()

    if (numberNorm == qNorm) return 1000.0
    if (numberNorm.startsWith(qNorm)) return 800.0 + (100.0 / numberNorm.length)

    if (lines.isNotEmpty()) {
        for (line in lines) {
            val lineNorm = line.normalizeForSearch()
            if (lineNorm == qNorm) return 950.0
            if (lineNorm.startsWith(qNorm)) return 750.0 + (50.0 / lineNorm.length)
            if (lineNorm.contains(qNorm)) return 600.0
        }
    }

    if (nameNorm == qNorm) return 700.0
    if (nameNorm.startsWith(qNorm)) return 500.0 + (50.0 / nameNorm.length)

    val combinedText = if (aliasNorm.isNotEmpty()) "$nameNorm $aliasNorm" else nameNorm
    val nameWords = combinedText.split(" ").filter { it.isNotEmpty() }
    val queryWords = qNorm.split(" ").filter { it.isNotEmpty() }

    var wordMatchScore = 0.0
    for (qWord in queryWords) {
        val exactWordMatch = nameWords.any { isBilingualTokenMatch(qWord, it) }
        val prefixWordMatch = nameWords.any { it.startsWith(qWord) || qWord.startsWith(it) }
        if (exactWordMatch) wordMatchScore += 150.0
        else if (prefixWordMatch) wordMatchScore += 80.0
    }
    if (wordMatchScore > 0) return wordMatchScore

    if (isSubsequence(qNorm, nameNorm) || (aliasNorm.isNotEmpty() && isSubsequence(qNorm, aliasNorm))) return 40.0

    if (qNorm.length >= 4) {
        val dist = levenshteinDistance(qNorm, nameNorm)
        if (dist <= 2) return 30.0 - dist * 5.0
    }

    return 0.0
}

fun computeSearchScore(stop: GeoportalStopEntity, query: String, alias: String? = null): Double {
    val linesList = stop.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    return computeSearchScore(stop.id_parada, stop.denominacion, query, alias, linesList)
}

/**
 * Computes matching relevance score for an Address / Nominatim result.
 * Compares strictly against the short name / primary segment (before the first comma)
 * to avoid unfair score inflation from administrative hierarchy tokens.
 */
fun computeAddressSearchScore(address: NominatimResult, query: String): Double {
    val qNorm = query.normalizeForSearch()
    if (qNorm.isEmpty()) return 0.0

    val primarySegment = address.displayName.split(",").firstOrNull()?.trim() ?: address.displayName
    val mainTitle = primarySegment.normalizeForSearch()

    // 1. Direct main title exact or prefix match
    if (mainTitle == qNorm) return 700.0
    if (mainTitle.startsWith(qNorm)) return 500.0 + (50.0 / mainTitle.length)
    if (mainTitle.contains(qNorm)) return 350.0

    // 2. Token-level matching evaluated on the primary name/title
    val nameWords = mainTitle.split(" ").filter { it.isNotEmpty() }
    val queryWords = qNorm.split(" ").filter { it.isNotEmpty() }

    var wordMatchScore = 0.0
    var matchedTokens = 0
    for (qWord in queryWords) {
        val exactWordMatch = nameWords.any { isBilingualTokenMatch(qWord, it) }
        val prefixWordMatch = nameWords.any { it.startsWith(qWord) }
        if (exactWordMatch) {
            wordMatchScore += 150.0
            matchedTokens++
        } else if (prefixWordMatch) {
            wordMatchScore += 80.0
            matchedTokens++
        }
    }
    if (matchedTokens > 0 && wordMatchScore > 0) return wordMatchScore

    // 3. Check full display name for token matches (e.g. house number + street name across commas)
    val fullNorm = address.displayName.normalizeForSearch()
    if (fullNorm.contains(qNorm)) return 250.0

    val fullWords = fullNorm.split(" ", ",").map { it.trim() }.filter { it.isNotEmpty() }
    var fullWordMatchScore = 0.0
    for (qWord in queryWords) {
        if (fullWords.any { isBilingualTokenMatch(qWord, it) || it.startsWith(qWord) }) {
            fullWordMatchScore += 70.0
        }
    }
    if (fullWordMatchScore > 0) return fullWordMatchScore

    // 4. Subsequence match on primary title or full title
    if (isSubsequence(qNorm, mainTitle)) return 50.0
    if (isSubsequence(qNorm, fullNorm)) return 30.0

    // 5. Levenshtein fuzzy match on primary title
    if (qNorm.length >= 4) {
        val dist = levenshteinDistance(qNorm, mainTitle)
        if (dist <= 2) return 30.0 - dist * 5.0
    }

    // Default base score for any valid result returned by Nominatim for this query
    return 20.0
}

