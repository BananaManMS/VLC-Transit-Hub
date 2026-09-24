package com.example.data.repository.emt

import android.content.Context
import java.io.File

class EmtDataSyncManager(private val context: Context) {
    suspend fun syncEmtData() {}

    companion object {
        fun getLocalStopsFile(context: Context): File {
            return File(context.filesDir, "emt_stops.json")
        }

        fun getLocalShapesFile(context: Context): File {
            return File(context.filesDir, "emt_shapes.json")
        }
    }
}
