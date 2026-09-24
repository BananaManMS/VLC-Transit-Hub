package com.example.data.model

data class MetroScheduleQueryResult(
    val stationId: String,
    val date: String,
    val departures: List<MetroScheduledDeparture> = emptyList()
)
