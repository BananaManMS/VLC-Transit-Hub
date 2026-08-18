package com.example.ui.routing

import com.example.data.model.routing.PlannedItinerary

/**
 * Filter for transit modes in multimodal planner.
 */
enum class RouteModeFilter(val labelEs: String, val labelCa: String, val modes: List<String>) {
    METRO("Metro", "Metro", listOf("SUBWAY", "TRAM")),
    BUS("Autobús", "Autobús", listOf("BUS", "COACH")),
    TRAIN("Cercanías", "Rodalia", listOf("REGIONAL_RAIL"));
}

/**
 * Departure time preferences.
 */
enum class DepartureType(val labelEs: String, val labelCa: String) {
    LEAVE_NOW("Salir ahora", "Eixir ara"),
    DEPART_AT("Salir a las...", "Eixir a les..."),
    ARRIVE_BY("Llegar antes de...", "Arribar abans de...");
}

/**
 * Location descriptor for origin / destination in route planner.
 */
data class PlannerLocation(
    val title: String,
    val subtitle: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isUserGps: Boolean = false,
    val stopId: String? = null,
    val stopType: String? = null
)

/**
 * Progression stages during route search and real-time reconciliation.
 */
enum class PlannerLoadingStage(
    val titleEs: String,
    val titleCa: String,
    val progress: Float
) {
    SCHEDULED_TRIPS("Extrayendo salidas programadas", "Extraient eixides programades", 0.35f),
    REAL_TIME_CROSS("Cruzando datos en tiempo real", "Creuant dades en temps real", 0.75f),
    BUILDING_ROUTES("Construyendo trayectos", "Construint trajectes", 0.95f)
}

/**
 * UI State for Route Planner.
 */
sealed interface RoutePlannerUiState {
    object Idle : RoutePlannerUiState
    data class Loading(val stage: PlannerLoadingStage = PlannerLoadingStage.SCHEDULED_TRIPS) : RoutePlannerUiState
    data class Success(val itineraries: List<PlannedItinerary>) : RoutePlannerUiState
    data class Error(val message: String) : RoutePlannerUiState
}
