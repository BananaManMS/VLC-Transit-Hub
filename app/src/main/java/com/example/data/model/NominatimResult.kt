package com.example.data.model

data class NominatimResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val category: String,
    val isLocalStop: Boolean = false,
    val stopId: String? = null,
    val stopType: String? = null // "METRO", "EMT", "CERCANIAS", "METROBUS" etc.
)
