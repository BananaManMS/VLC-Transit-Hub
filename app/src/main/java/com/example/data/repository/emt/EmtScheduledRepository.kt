package com.example.data.repository.emt

import android.content.Context

class EmtScheduledRepository(private val context: Context) {
    suspend fun getEmtSchedules(): List<String> {
        return emptyList()
    }
}
