package com.example.util

import java.text.Normalizer
import java.util.Locale

/**
 * Removes accent marks/diacritics from a string.
 */
fun String.removeAccents(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}

@JvmName("removeAccentsDirect")
fun removeAccents(str: String): String = str.removeAccents()

/**
 * Normalizes a string by removing accent marks and converting it to lowercase.
 */
fun String.normalize(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase(Locale.getDefault())
}

/**
 * Normalizes a string for search comparisons (removes accents, lowercase, trimmed).
 */
fun String.normalizeForSearch(): String {
    val unaccented = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return unaccented.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Spanish <-> Valencian / Catalan linguistic and toponym equivalents.
 */
val BILINGUAL_EQUIVALENCES = mapOf(
    "jativa" to listOf("xativa"),
    "xativa" to listOf("jativa"),
    "alicante" to listOf("alacant"),
    "alacant" to listOf("alicante"),
    "castellon" to listOf("castello"),
    "castello" to listOf("castellon"),
    "grao" to listOf("grau"),
    "grau" to listOf("grao"),
    "cabanal" to listOf("cabanyal"),
    "cabanial" to listOf("cabanyal"),
    "cabanyal" to listOf("cabanal", "cabanial"),
    "norte" to listOf("nord"),
    "nord" to listOf("norte"),
    "ayuntamiento" to listOf("ajuntament"),
    "ajuntament" to listOf("ayuntamiento"),
    "carrer" to listOf("calle", "c"),
    "calle" to listOf("carrer", "c"),
    "avinguda" to listOf("avenida", "avda", "av"),
    "avenida" to listOf("avinguda", "avda", "av"),
    "avda" to listOf("avinguda", "avenida", "av"),
    "placa" to listOf("plaza", "plza", "pza"),
    "plaza" to listOf("placa", "plza", "pza"),
    "plza" to listOf("placa", "plaza", "pza"),
    "pza" to listOf("placa", "plaza", "plza"),
    "mercat" to listOf("mercado"),
    "mercado" to listOf("mercat"),
    "pont" to listOf("puente"),
    "puente" to listOf("pont"),
    "platja" to listOf("playa"),
    "playa" to listOf("platja"),
    "estacio" to listOf("estacion"),
    "estacion" to listOf("estacio"),
    "universitat" to listOf("universidad"),
    "universidad" to listOf("universitat"),
    "castell" to listOf("castillo"),
    "castillo" to listOf("castell"),
    "generalitat" to listOf("generalidad"),
    "generalidad" to listOf("generalitat")
)

/**
 * Checks if a token [queryToken] matches a target token [targetToken] either directly,
 * via prefix/contains, or through Valencian/Spanish bilingual synonyms.
 */
fun isBilingualTokenMatch(queryToken: String, targetToken: String): Boolean {
    if (queryToken == targetToken) return true
    val synonyms = BILINGUAL_EQUIVALENCES[queryToken]
    return synonyms?.any { it == targetToken || targetToken.startsWith(it) || it.startsWith(targetToken) } == true
}

/**
 * Checks if [sub] is a subsequence of [target] (ignoring gaps).
 */
fun isSubsequence(sub: String, target: String): Boolean {
    if (sub.isEmpty()) return true
    var subIdx = 0
    for (char in target) {
        if (char == sub[subIdx]) {
            subIdx++
            if (subIdx == sub.length) return true
        }
    }
    return false
}

/**
 * Calculates Levenshtein distance between two strings.
 */
fun levenshteinDistance(s1: String, s2: String): Int {
    val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
    for (i in 0..s1.length) dp[i][0] = i
    for (j in 0..s2.length) dp[0][j] = j
    for (i in 1..s1.length) {
        for (j in 1..s2.length) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[s1.length][s2.length]
}

/**
 * Formats a time string in HH:mm:ss (or HH:mm) format to HH:mm by stripping seconds if present.
 */
fun formatTimeWithoutSeconds(timeStr: String): String {
    if (timeStr.isBlank()) return "--:--"
    val parts = timeStr.split(":")
    if (parts.size >= 2) {
        val hours = parts[0].trim()
        val minutes = parts[1].trim()
        return "$hours:$minutes"
    }
    return timeStr
}
