package com.example.data.repository

import android.content.Context
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData

data class LineStationInfo(
    val id: String = "",
    val name: String = "",
    val stationName: String = name,
    val line: String = "",
    val zone: String = "A",
    val timeMinutes: Int = 0,
    val timeFormatted: String = "",
    val isCurrentStation: Boolean = false,
    val isOrigin: Boolean = false,
    val isDestination: Boolean = false,
    val order: Int = 0,
    val stops: List<LineStationInfo> = emptyList()
) : Comparable<LineStationInfo> {
    override fun compareTo(other: LineStationInfo): Int {
        return timeMinutes.compareTo(other.timeMinutes)
    }
}

class MetroRepository(private val context: Context) {
    fun loadMetroStations(): List<MetroStation> {
        return ValenciaMetroData.mainMetroStations
    }

    suspend fun getLineStationsMap(): Map<String, List<LineStationInfo>> {
        return emptyMap()
    }
}
