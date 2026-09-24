package com.example.data.repository.metrobus

import android.content.Context
import com.example.ui.bus.MetrobusDepartureUiModel
import okhttp3.OkHttpClient

class MetrobusScheduledRepository(private val context: Context) {
    suspend fun getMetrobusSchedules(): List<String> {
        return emptyList()
    }

    companion object {
        suspend fun fetchScheduledDepartures(
            stopId: String,
            limitPerLine: Int = 5,
            linesMap: Map<String, Any?> = emptyMap(),
            client: OkHttpClient? = null
        ): List<MetrobusDepartureUiModel> {
            return emptyList()
        }
    }
}
