package com.example.ui.cercanias

import com.example.data.database.CercaniasStationEntity
import com.example.util.isSubsequence
import com.example.util.levenshteinDistance
import com.example.util.normalizeForSearch
import java.util.Locale

private fun getStationAliases(nombre: String): List<String> {
    val lower = nombre.lowercase(Locale.getDefault())
    val aliases = mutableListOf<String>()
    if (lower.contains("f. s. lluis") || lower.contains("lluís") || lower.contains("lluis")) {
        aliases.add("Valencia-La Font de Sant Lluis")
        aliases.add("Font de Sant Lluis")
        aliases.add("Font de Sant")
        aliases.add("La Font de Sant Lluis")
        aliases.add("Valencia Font de Sant Lluis")
    }
    if (lower.contains("nord")) {
        aliases.add("Valencia-Estacio del nord")
        aliases.add("Estacio del Nord")
        aliases.add("Estacion del Norte")
    }
    if (lower.contains("cabanyal")) {
        aliases.add("València-Cabanyal")
        aliases.add("Valencia-Cabanyal")
    }
    if (lower.contains("sant isidre")) {
        aliases.add("València Sant Isidre")
        aliases.add("Valencia San Isidro")
    }
    return aliases
}

private fun computeScoreForTarget(normalizedTarget: String, normalizedQuery: String, isIdMatch: Boolean): Double {
    // 1. Direct ID matches
    if (isIdMatch) return 1000.0

    // 2. Direct name matches
    if (normalizedTarget == normalizedQuery) return 500.0
    if (normalizedTarget.startsWith(normalizedQuery)) return 400.0 + (normalizedQuery.length.toDouble() / normalizedTarget.length)
    if (normalizedTarget.contains(normalizedQuery)) return 300.0

    // 3. Token-based word match
    val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val targetTokens = normalizedTarget.split(Regex("\\s+")).filter { it.isNotEmpty() }
    
    if (queryTokens.isNotEmpty()) {
        var matchedTokensCount = 0
        var totalTokenScore = 0.0
        for (qToken in queryTokens) {
            var bestTokenScore = 0.0
            for (nToken in targetTokens) {
                if (nToken == qToken) {
                    bestTokenScore = maxOf(bestTokenScore, 100.0)
                } else if (nToken.startsWith(qToken)) {
                    bestTokenScore = maxOf(bestTokenScore, 80.0 * qToken.length / nToken.length)
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
    if (isSubsequence(normalizedQuery, normalizedTarget)) {
        return 10.0 + (normalizedQuery.length.toDouble() / normalizedTarget.length) * 10.0
    }

    // 5. Global Levenshtein distance for the entire string
    val globalDist = levenshteinDistance(normalizedQuery, normalizedTarget)
    val maxGlobalLength = maxOf(normalizedQuery.length, normalizedTarget.length)
    if (maxGlobalLength > 0) {
        val globalSimilarity = 1.0 - (globalDist.toDouble() / maxGlobalLength)
        if (globalSimilarity >= 0.5) {
            return globalSimilarity * 10.0
        }
    }

    return 0.0
}

fun computeCercaniasSearchScore(station: CercaniasStationEntity, query: String): Double {
    val normalizedQuery = query.normalizeForSearch()
    val targets = mutableListOf<String>()
    targets.add(station.nombre)
    targets.add(station.id)
    for (alias in getStationAliases(station.nombre)) {
        targets.add(alias)
    }

    var maxScore = 0.0
    val isIdMatch = station.id.normalizeForSearch() == normalizedQuery
    for (target in targets) {
        val score = computeScoreForTarget(target.normalizeForSearch(), normalizedQuery, isIdMatch)
        if (score > maxScore) {
            maxScore = score
        }
    }
    return maxScore
}
