package com.example.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.map.MapScreen
import com.example.ui.map.MapViewModel
import com.example.ui.metro.MetroViewModel
import com.example.ui.routing.PlannerLocation

@Composable
fun DashboardMapTab(
    mapViewModel: MapViewModel,
    dashboardViewModel: DashboardViewModel,
    metroViewModel: MetroViewModel,
    cercaniasViewModel: CercaniasViewModel,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onNavigateToMetro: (String) -> Unit,
    onNavigateToCercanias: (String) -> Unit,
    onNavigateToRoutePlanner: (PlannerLocation) -> Unit,
    onPlannerLocationPicked: (PlannerLocation, Boolean) -> Unit,
    onCancelPlannerLocationPicking: () -> Unit,
    selectedItinerary: PlannedItinerary?,
    onClearItinerary: () -> Unit,
    activeTripBottomPadding: Dp,
    onStartTrip: ((PlannedItinerary) -> Unit)? = null,
    onCommuteLocationConfigured: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    MapScreen(
        mapViewModel = mapViewModel,
        dashboardViewModel = dashboardViewModel,
        metroViewModel = metroViewModel,
        cercaniasViewModel = cercaniasViewModel,
        isDarkMode = isDarkMode,
        appLanguage = appLanguage,
        onNavigateToMetro = onNavigateToMetro,
        onNavigateToCercanias = onNavigateToCercanias,
        onNavigateToRoutePlanner = onNavigateToRoutePlanner,
        onPlannerLocationPicked = onPlannerLocationPicked,
        onCancelPlannerLocationPicking = onCancelPlannerLocationPicking,
        onCommuteLocationConfigured = onCommuteLocationConfigured,
        selectedItinerary = selectedItinerary,
        onClearItinerary = onClearItinerary,
        onOpenRouteDetail = null,
        onStartTrip = onStartTrip,
        activeTripBottomPadding = activeTripBottomPadding,
        modifier = modifier.fillMaxSize()
    )
}
