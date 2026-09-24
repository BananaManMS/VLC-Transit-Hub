package com.example.ui.metro

import com.example.data.model.MetroStation
import com.example.util.isBilingualTokenMatch
import com.example.util.isSubsequence
import com.example.util.levenshteinDistance
import com.example.util.normalizeForSearch

fun computeMetroSearchScore(station: MetroStation, query: String): Double {
    val normalizedQuery = query.normalizeForSearch()
    val normalizedName = station.name.normalizeForSearch()
    val normalizedId = station.id.normalizeForSearch()

    // 1. Direct ID matches
    if (normalizedId == normalizedQuery) return 1000.0
    if (normalizedId.startsWith(normalizedQuery)) return 900.0 + (normalizedQuery.length.toDouble() / normalizedId.length)
    if (normalizedId.contains(normalizedQuery)) return 800.0

    // 2. Direct name matches
    if (normalizedName == normalizedQuery) return 500.0
    if (normalizedName.startsWith(normalizedQuery)) return 400.0 + (normalizedQuery.length.toDouble() / normalizedName.length)
    if (normalizedName.contains(normalizedQuery)) return 300.0

    // 2.5 Line matches
    for (line in station.lines) {
        val normalizedLine = line.normalizeForSearch()
        if (normalizedLine == normalizedQuery) return 250.0
        if (normalizedLine.contains(normalizedQuery)) return 200.0
    }

    // 3. Token-based word match (e.g. "colon" in "Plaza de Colon")
    val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val nameTokens = normalizedName.split(Regex("\\s+")).filter { it.isNotEmpty() }

    if (queryTokens.isNotEmpty()) {
        var matchedTokensCount = 0
        var totalTokenScore = 0.0
        for (qToken in queryTokens) {
            var bestTokenScore = 0.0
            for (nToken in nameTokens) {
                if (isBilingualTokenMatch(qToken, nToken)) {
                    bestTokenScore = maxOf(bestTokenScore, 100.0)
                } else if (nToken.startsWith(qToken) || qToken.startsWith(nToken)) {
                    bestTokenScore = maxOf(bestTokenScore, 80.0 * minOf(qToken.length, nToken.length) / maxOf(qToken.length, nToken.length))
                } else if (nToken.contains(qToken)) {
                    bestTokenScore = maxOf(bestTokenScore, 60.0 * qToken.length / nToken.length)
                } else {
                    val dist = levenshteinDistance(qToken, nToken)
                    val maxLength = maxOf(qToken.length, nToken.length)
                    if (maxLength > 0) {
                        val similarity = 1.0 - (dist.toDouble() / maxLength)
                        if (similarity >= 0.6) {
                            bestTokenScore = maxOf(bestTokenScore, similarity * 50.0)
                        }
                    }
                }
            }
            if (bestTokenScore > 0) {
                matchedTokensCount++
                totalTokenScore += bestTokenScore
            }
        }
        if (matchedTokensCount > 0) {
            val completenessBonus = if (matchedTokensCount == queryTokens.size) 50.0 else 0.0
            return (totalTokenScore / queryTokens.size) + completenessBonus
        }
    }

    // 4. Character subsequence matching (fuzzy search)
    if (isSubsequence(normalizedQuery, normalizedName)) {
        return 10.0 + (normalizedQuery.length.toDouble() / normalizedName.length) * 10.0
    }

    // 5. Global Levenshtein distance for the entire string
    val globalDist = levenshteinDistance(normalizedQuery, normalizedName)
    val maxGlobalLength = maxOf(normalizedQuery.length, normalizedName.length)
    if (maxGlobalLength > 0) {
        val globalSimilarity = 1.0 - (globalDist.toDouble() / maxGlobalLength)
        if (globalSimilarity >= 0.5) {
            return globalSimilarity * 10.0
        }
    }

    return 0.0
}
