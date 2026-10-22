package com.example.data.repository

import com.example.data.model.WikipediaSummary
import com.example.data.network.NetworkModule
import okhttp3.OkHttpClient

object WikipediaRepository {
    const val USER_AGENT = "Mozilla/5.0 (Android; Valencia Transit)"
    val okHttpClient: OkHttpClient = NetworkModule.okHttpClient

    suspend fun getSummary(title: String): WikipediaSummary? {
        return WikipediaSummary(title = title, extract = "Información sobre $title", pageUrl = "https://es.wikipedia.org/wiki/$title")
    }

    suspend fun getPlaceSummary(wikipediaTag: String?, wikidataTag: String?, preferredLang: String? = "es"): WikipediaSummary? {
        val title = wikipediaTag?.substringAfter(":") ?: return null
        return WikipediaSummary(
            title = title,
            extract = "Información sobre $title",
            description = "Lugar de interés en Valencia",
            pageUrl = "https://${preferredLang ?: "es"}.wikipedia.org/wiki/$title"
        )
    }
}
