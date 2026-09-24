package com.example.ui.bus

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val PAREN_SPLIT_REGEX = Regex("(?=\\()|(?<=\\))")

/**
 * Normalizes stop name capitalization if it comes entirely in UPPERCASE.
 */
fun normalizeStopCapitalization(name: String): String {
    val trimmed = name.trim()
    val lettersOnly = trimmed.filter { it.isLetter() }
    if (lettersOnly.isNotEmpty() && lettersOnly == lettersOnly.uppercase()) {
        // Convert to Title Case keeping common prepositions/conjunctions lowercase
        val lowerExceptions = setOf("de", "del", "d'", "d’", "la", "el", "les", "los", "las", "i", "y", "en", "a", "al", "als")
        val words = trimmed.lowercase().split(" ")
        return words.mapIndexed { idx, word ->
            if (idx > 0 && lowerExceptions.contains(word)) {
                word
            } else if (word.startsWith("d'") || word.startsWith("d’")) {
                "d'" + word.substring(2).replaceFirstChar { it.uppercase() }
            } else if (word.startsWith("l'") || word.startsWith("l’")) {
                "l'" + word.substring(2).replaceFirstChar { it.uppercase() }
            } else {
                word.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }
    return trimmed
}

/**
 * Builds an AnnotatedString for bus stop names where:
 * - The main street / name is bold with primary contrast color.
 * - Parenthesized descriptors like (parell), (imparell), (par), (impar) are styled with normal weight and secondary muted color.
 */
@Composable
fun buildFormattedStopName(
    rawName: String,
    primaryColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
): AnnotatedString {
    val normalized = normalizeStopCapitalization(rawName)
    val parts = normalized.split(PAREN_SPLIT_REGEX)

    return buildAnnotatedString {
        for (part in parts) {
            if (part.startsWith("(") && part.endsWith(")")) {
                val inner = part.substring(1, part.length - 1).trim()
                if (inner.all { it.isDigit() }) continue // Skip pure numeric identifiers like (123)

                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Normal,
                        color = secondaryColor.copy(alpha = 0.85f)
                    )
                ) {
                    append(part)
                }
            } else {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                ) {
                    append(part)
                }
            }
        }
    }
}
