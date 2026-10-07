package com.example.ui.dashboard

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
    onToggleFavoriteMetroStation: (String) -> Unit = {},
    metroViewModel: MetroViewModel? = null,
    cercaniasViewModel: CercaniasViewModel? = null,
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
    if (showStationConfigDialog && metroViewModel != null) {
        MetroStationSelectionDialog(
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            metroViewModel = metroViewModel,
            onDismiss = onDismissStationConfigDialog
        )
    }

    // 3. DIALOG FOR CONFIGURING FAVOURITE CERCANIAS STATIONS
    if (showCercaniasStationConfigDialog && cercaniasViewModel != null) {
        CercaniasStationSelectionDialog(
            viewModel = cercaniasViewModel,
            onDismiss = onDismissCercaniasStationConfigDialog
        )
    }

    // 4. DIALOG FOR SEARCHING METRO STATIONS
    if (showMetroSearchDialog && metroViewModel != null) {
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
        val currentDest = activeTrip?.destinationName ?: ""
        AlertDialog(
            onDismissRequest = onDismissReplaceActiveTrip,
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.trip_replace_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.example.R.string.trip_replace_confirm_body,
                        currentDest,
                        pending.third
                    )
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
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_replace_and_start))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissReplaceActiveTrip) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_cancel))
                }
            }
        )
    }

    // 7. FLOATING DIALOG FOR TRANSFER RISK RECALCULATION CONFIRMATION
    if (showTransferRiskDialog && activeTrip != null) {
        val currentLeg = activeTrip.itinerary.legs.getOrNull(activeTrip.currentLegIndex)
        val transferStopName = currentLeg?.toName?.ifBlank { "la estación de transbordo" } ?: "el transbordo"
        val destName = activeTrip.destinationName.ifBlank { "tu destino" }
        val isDark = isDarkMode

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
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.transfer_risk_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFFFD180) else Color(0xFFE65100)
                    )
                )
            },
            text = {
                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            com.example.R.string.transfer_risk_body,
                            transferStopName,
                            destName
                        ),
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
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.commute_searching))
                    } else {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_search_alternatives),
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
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_keep_current_route)
                    )
                }
            }
        )
    }
}
