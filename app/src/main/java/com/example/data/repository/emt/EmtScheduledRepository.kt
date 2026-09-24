package com.example.data.repository.emt

import android.content.Context
import com.example.ui.bus.EmtBusTime

class EmtScheduledRepository(private val context: Context) {
    suspend fun getEmtSchedules(): List<String> {
        return emptyList()
    }

    companion object {
        suspend fun fetchScheduledDepartures(
            stopId: String,
            stopName: String? = null,
            limitPerLine: Int = 5
        ): List<EmtBusTime> {
            return emptyList()
        }
    }
}
