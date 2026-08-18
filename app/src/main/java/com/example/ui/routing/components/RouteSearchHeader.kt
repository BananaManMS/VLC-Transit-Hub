package com.example.ui.routing.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NominatimResult
import com.example.ui.dashboard.AppLanguage
import com.example.ui.routing.DepartureType
import com.example.ui.routing.PlannerLocation
import com.example.ui.routing.RouteModeFilter

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun RouteSearchHeader(
    originQuery: String,
    destinationQuery: String,
    originLocation: PlannerLocation?,
    destinationLocation: PlannerLocation?,
    onOriginQueryChange: (String) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onUseGpsOrigin: () -> Unit,
    onSwap: () -> Unit,
    selectedModeFilters: Set<RouteModeFilter>,
    onToggleModeFilter: (RouteModeFilter) -> Unit,
    onClearModeFilters: (() -> Unit)? = null,
    fewestTransfers: Boolean,
    onToggleFewestTransfers: () -> Unit,
    departureType: DepartureType,
    selectedTime: String?,
    onOpenScheduleDialog: () -> Unit,
    isSearchingOrigin: Boolean = false,
    isSearchingDestination: Boolean = false,
    onOriginFocused: () -> Unit = {},
    onDestinationFocused: () -> Unit = {},
    onOriginUnfocused: () -> Unit = {},
    onDestinationUnfocused: () -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.CA,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isSwapped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (isSwapped) 180f else 0f, label = "swap_rotate")

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Inputs row with Swap button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left dots & connector line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    )
                }

                // Origin & Destination Text Fields
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val isGpsOrigin = (originLocation?.isUserGps == true) && 
                        (originQuery.trim().equals("Ubicación actual", ignoreCase = true) || originQuery.trim().equals("Ubicació actual", ignoreCase = true))

                    // Origin Input
                    OutlinedTextField(
                        value = originQuery,
                        onValueChange = onOriginQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    onOriginFocused()
                                } else {
                                    onOriginUnfocused()
                                }
                            }
                            .testTag("route_origin_input"),
                        placeholder = {
                            Text(
                                if (appLanguage == AppLanguage.ES) "Origen (ej: Tu ubicación)" else "Origen (ex: La teua ubicació)",
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        leadingIcon = if (isGpsOrigin) {
                            {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Ubicación actual",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        trailingIcon = {
                            if (isSearchingOrigin) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else if (originQuery.isNotEmpty()) {
                                IconButton(onClick = { onOriginQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Borrar", modifier = Modifier.size(18.dp))
                                }
                            } else {
                                IconButton(
                                    onClick = onUseGpsOrigin,
                                    modifier = Modifier.testTag("route_gps_button")
                                ) {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        contentDescription = "Mi ubicación",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Destination Input
                    OutlinedTextField(
                        value = destinationQuery,
                        onValueChange = onDestinationQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    onDestinationFocused()
                                } else {
                                    onDestinationUnfocused()
                                }
                            }
                            .testTag("route_destination_input"),
                        placeholder = {
                            Text(
                                if (appLanguage == AppLanguage.ES) "¿A dónde vas?" else "On vols anar?",
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        trailingIcon = {
                            if (isSearchingDestination) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else if (destinationQuery.isNotEmpty()) {
                                IconButton(onClick = { onDestinationQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Borrar", modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Destino",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }

                // Swap Button
                IconButton(
                    onClick = {
                        isSwapped = !isSwapped
                        onSwap()
                    },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                        .testTag("route_swap_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Invertir origen y destino",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Filters Horizontal Row
            val customChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Schedule Chip
                item {
                    val scheduleLabel = when (departureType) {
                        DepartureType.LEAVE_NOW -> if (appLanguage == AppLanguage.ES) "Salir ahora ▾" else "Eixir ara ▾"
                        DepartureType.DEPART_AT -> "Salir: ${selectedTime ?: "ahora"} ▾"
                        DepartureType.ARRIVE_BY -> "Llegar: ${selectedTime ?: "12:00"} ▾"
                    }
                    FilterChip(
                        selected = departureType != DepartureType.LEAVE_NOW,
                        onClick = onOpenScheduleDialog,
                        label = { Text(scheduleLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = customChipColors
                    )
                }

                // "Todos" Chip
                item {
                    val isAllSelected = selectedModeFilters.isEmpty()
                    FilterChip(
                        selected = isAllSelected,
                        onClick = { onClearModeFilters?.invoke() },
                        label = {
                            Text(
                                if (appLanguage == AppLanguage.ES) "Todos" else "Tots",
                                fontSize = 12.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = customChipColors
                    )
                }

                // Mode filters (combinable)
                items(RouteModeFilter.values()) { filter ->
                    val isSelected = selectedModeFilters.contains(filter)
                    val icon = when (filter) {
                        RouteModeFilter.METRO -> Icons.Default.DirectionsSubway
                        RouteModeFilter.BUS -> Icons.Default.DirectionsBus
                        RouteModeFilter.TRAIN -> Icons.Default.Train
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleModeFilter(filter) },
                        label = {
                            Text(
                                if (appLanguage == AppLanguage.ES) filter.labelEs else filter.labelCa,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = customChipColors
                    )
                }

                // Fewest transfers
                item {
                    FilterChip(
                        selected = fewestTransfers,
                        onClick = onToggleFewestTransfers,
                        label = {
                            Text(
                                if (appLanguage == AppLanguage.ES) "Menos transbordos" else "Menys transbords",
                                fontSize = 12.sp,
                                fontWeight = if (fewestTransfers) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = customChipColors
                    )
                }
            }
        }
    }
}
