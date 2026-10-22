package com.example.data.model

data class WikipediaSummary(
    val title: String = "",
    val extract: String = "",
    val description: String? = extract,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = imageUrl,
    val pageUrl: String? = null
)
