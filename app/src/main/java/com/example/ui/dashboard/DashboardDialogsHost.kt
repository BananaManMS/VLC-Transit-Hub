package com.example.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MetroStation
import com.example.data.model.routing.PlannedItinerary
import com.example.data.repository.ActiveTripState
import com.example.ui.cercanias.CercaniasStationSelectionDialog
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.metro.MetroSearchDialog
import com.example.ui.metro.MetroStationSelectionDialog
import com.example.ui.metro.MetroViewModel
import com.example.ui.routing.components.RouteDetailBottomSheet
import com.example.util.RealTimeTripStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardDialogsHost(
    showAddDialog: Boolean,
    onDismissAddDialog: () -> Unit,
    onAddCalendarEvent: (title: String, desc: String, offsetHours: Int, durationHours: Int, colorHex: String) -> Unit,
    onAddCalendarTask: (title: String, desc: String, offsetHours: Int, colorHex: String) -> Unit,
    showStationConfigDialog: Boolean,
    onDismissStationConfigDialog: () -> Unit,
    showCercaniasStationConfigDialog: Boolean,
    onDismissCercaniasStationConfigDialog: () -> Unit,
    showMetroSearchDialog: Boolean,
    onDismissMetroSearchDialog: () -> Unit,
    metroSearchQuery: String,
    filteredStations: List<MetroStation>,
    favoriteStations: List<String>,
    onMetroQueryChange: (String) -> Unit,
    onSelectMetroSearchStation: (String) -> Unit,
    onToggleFavoriteMetroStation: (String) -> Unit,
    metroViewModel: MetroViewModel,
    cercaniasViewModel: CercaniasViewModel,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    showActiveTripDetails: Boolean,
    onDismissActiveTripDetails: () -> Unit,
    onViewActiveTripOnMap: (PlannedItinerary) -> Unit,
    activeTrip: ActiveTripState?,
    realTimeTripStatus: RealTimeTripStatus,
    pendingTripToStart: Triple<PlannedItinerary, String, String>?,
    onConfirmReplaceActiveTrip: (PlannedItinerary, String, String) -> Unit,
    onDismissReplaceActiveTrip: () -> Unit,
    showTransferRiskDialog: Boolean,
    isRecalculatingTransfer: Boolean,
    recalculateError: String?,
    onRecalculateTransfer: () -> Unit,
    onDismissTransferRiskDialog: () -> Unit
) {
    // 1. DIALOG FOR ADDING CALENDAR ITEM
    if (showAddDialog) {
        AddCalendarItemDialog(
            onDismiss = onDismissAddDialog,
            onAddEvent = { title, desc, offset, dur, color ->
                onAddCalendarEvent(title, desc, offset, dur, color)
            },
            onAddTask = { title, desc, offset, color ->
                onAddCalendarTask(title, desc, offset, color)
            }
        )
    }

    // 2. DIALOG FOR CONFIGURING FAVOURITE METRO STATIONS
    if (showStationConfigDialog) {
        MetroStationSelectionDialog(
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            metroViewModel = metroViewModel,
            onDismiss = onDismissStationConfigDialog
        )
    }

    // 3. DIALOG FOR CONFIGURING FAVOURITE CERCANIAS STATIONS
    if (showCercaniasStationConfigDialog) {
        CercaniasStationSelectionDialog(
            viewModel = cercaniasViewModel,
            onDismiss = onDismissCercaniasStationConfigDialog
        )
    }

    // 4. DIALOG FOR SEARCHING METRO STATIONS
    if (showMetroSearchDialog) {
        MetroSearchDialog(
            searchQuery = metroSearchQuery,
            filteredStations = filteredStations,
            favoriteStationIds = favoriteStations,
            isDarkMode = isDarkMode,
            onQueryChange = onMetroQueryChange,
            onSelectStation = onSelectMetroSearchStation,
            onToggleFavorite = onToggleFavoriteMetroStation,
            onDismiss = onDismissMetroSearchDialog,
            metroViewModel = metroViewModel
        )
    }

    // 5. ACTIVE TRIP DETAILED MODAL BOTTOM SHEET
    if (showActiveTripDetails) {
        activeTrip?.itinerary?.let { tripItinerary ->
            RouteDetailBottomSheet(
                itinerary = tripItinerary,
                onDismiss = onDismissActiveTripDetails,
                onViewOnMap = {
                    onViewActiveTripOnMap(tripItinerary)
                },
                onStartTrip = null,
                currentLegIndex = activeTrip.currentLegIndex,
                realTimeStatus = realTimeTripStatus,
                appLanguage = appLanguage
            )
        }
    }

    // 6. CONFIRMATION DIALOG WHEN REPLACING AN EXISTING ACTIVE TRIP
    pendingTripToStart?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissReplaceActiveTrip,
            title = {
                Text(
                    text = if (appLanguage == AppLanguage.ES) "¿Iniciar nuevo viaje?" else "Iniciar nou viatge?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (appLanguage == AppLanguage.ES)
                        "Ya tienes un viaje activo hacia ${activeTrip?.destinationName ?: "tu destino"}. ¿Deseas sustituirlo por el nuevo trayecto hacia ${pending.third}?"
                    else
                        "Ja tens un viatge actiu cap a ${activeTrip?.destinationName ?: "la teua destinació"}. Vols substituir-lo per este nou trajecte cap a ${pending.third}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmReplaceActiveTrip(pending.first, pending.second, pending.third)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A86B)
                    )
                ) {
                    Text(if (appLanguage == AppLanguage.ES) "Sustituir e Iniciar" else "Substituir i Iniciar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissReplaceActiveTrip) {
                    Text(if (appLanguage == AppLanguage.ES) "Cancelar" else "Cancel·lar")
                }
            }
        )
    }

    // 7. FLOATING DIALOG FOR TRANSFER RISK RECALCULATION CONFIRMATION
    if (showTransferRiskDialog && activeTrip != null) {
        val currentLeg = activeTrip.itinerary.legs.getOrNull(activeTrip.currentLegIndex)
        val transferStopName = currentLeg?.toName?.ifBlank { "la estación de transbordo" } ?: "el transbordo"
        val destName = activeTrip.destinationName.ifBlank { "tu destino" }
        val isDark = isSystemInDarkTheme()

        AlertDialog(
            onDismissRequest = onDismissTransferRiskDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (appLanguage == AppLanguage.ES) "Posible transbordo perdido" else "Possible transbordament perdut",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)
                    )
                )
            },
            text = {
                Column {
                    Text(
                        text = if (appLanguage == AppLanguage.ES)
                            "Debido a un retraso acumulado en tu vehículo, es probable que no llegues a tiempo a tu conexión en $transferStopName.\n\n¿Quieres buscar rutas alternativas desde $transferStopName hasta $destName sin caminar más?"
                        else
                            "A causa d'un retràs acumulat en el teu transport, és probable que no arribes a temps a la connexió en $transferStopName.\n\nVols buscar rutes alternatives des de $transferStopName fins a $destName sense caminar més?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (recalculateError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = recalculateError,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onRecalculateTransfer,
                    enabled = !isRecalculatingTransfer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    if (isRecalculatingTransfer) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (appLanguage == AppLanguage.ES) "Buscando..." else "Buscant...")
                    } else {
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Buscar alternativas (sin caminar más)" else "Buscar alternatives (sense caminar més)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismissTransferRiskDialog
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "Mantener ruta actual" else "Mantindre ruta actual"
                    )
                }
            }
        )
    }
}
