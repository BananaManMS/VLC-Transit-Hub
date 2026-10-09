package com.example.ui.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommuteSetupDialog(
    commuteType: String = "HOME", // "HOME", "WORK", "PINNED"
    isHome: Boolean = (commuteType == "HOME"),
    appLanguage: AppLanguage,
    currentName: String,
    currentLat: Double = 0.0,
    currentLon: Double = 0.0,
    dashboardViewModel: DashboardViewModel,
    onDismiss: () -> Unit,
    onSelectOnMap: () -> Unit,
    onSave: (String, Double, Double) -> Unit
) {
    val isDarkMode by dashboardViewModel.isDarkMode.collectAsState()
    val isConfigured = currentName.isNotBlank() && currentLat != 0.0 && currentLon != 0.0

    val isWork = commuteType == "WORK"
    val isPinned = commuteType == "PINNED"
    val isHomeType = commuteType == "HOME"

    val defaultTitle = when {
        isHomeType -> androidx.compose.ui.res.stringResource(com.example.R.string.commute_home_title)
        isWork -> androidx.compose.ui.res.stringResource(com.example.R.string.commute_work_title)
        else -> ""
    }

    var name by remember { mutableStateOf(currentName.ifBlank { defaultTitle }) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.example.ui.map.MapSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var lat by remember { mutableDoubleStateOf(if (isConfigured) currentLat else 39.4697) }
    var lon by remember { mutableDoubleStateOf(if (isConfigured) currentLon else -0.3773) }
    var hasChosenLocation by remember { mutableStateOf(isConfigured) }

    val recentSearches by dashboardViewModel.recentSearches.collectAsState()
    val customFavorites by dashboardViewModel.customFavorites.collectAsState()
    val unifiedTransitFavorites by dashboardViewModel.unifiedTransitFavorites.collectAsState()

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length >= 2) {
            isSearching = true
            dashboardViewModel.searchLocations(query).collect { results ->
                searchResults = results.take(10)
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    val headerIcon = when {
        isHomeType -> Icons.Default.Home
        isWork -> Icons.Default.Work
        else -> Icons.Default.PushPin
    }
    val headerColor = when {
        isHomeType -> Color(0xFFF59E0B)
        isWork -> Color(0xFF3B82F6)
        else -> Color(0xFF8B5CF6)
    }
    val headerTitle = when {
        isHomeType -> androidx.compose.ui.res.stringResource(com.example.R.string.commute_setup_home)
        isWork -> androidx.compose.ui.res.stringResource(com.example.R.string.commute_setup_work)
        else -> androidx.compose.ui.res.stringResource(com.example.R.string.commute_setup_pinned)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(headerColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = headerColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_search_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Powerful Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_search_field_placeholder),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                // Search Results powered by UnifiedSearchEngine and SearchResultRow
                if (searchQuery.trim().length >= 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isSearching && searchResults.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_searching),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (searchResults.isEmpty()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_no_results_format, searchQuery),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(6.dp)
                        ) {
                            searchResults.forEach { result ->
                                com.example.ui.common.search.SearchResultRow(
                                    result = result,
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onClick = {
                                        val (title, selectedLat, selectedLon) = when (result) {
                                            is com.example.ui.map.MapSearchResult.BusStop -> Triple(
                                                if (!result.alias.isNullOrBlank()) result.alias else result.stop.denominacion,
                                                result.stop.lat,
                                                result.stop.lon
                                            )
                                            is com.example.ui.map.MapSearchResult.MetrobusStop -> Triple(
                                                if (!result.alias.isNullOrBlank()) result.alias else result.stop.denominacion,
                                                result.stop.lat,
                                                result.stop.lon
                                            )
                                            is com.example.ui.map.MapSearchResult.Metro -> Triple(
                                                result.station.name,
                                                result.station.latitude,
                                                result.station.longitude
                                            )
                                            is com.example.ui.map.MapSearchResult.Cercanias -> Triple(
                                                result.station.nombre,
                                                result.station.latitud,
                                                result.station.longitud
                                            )
                                            is com.example.ui.map.MapSearchResult.Address -> Triple(
                                                result.result.placeName ?: result.result.displayName.split(",").firstOrNull()?.trim() ?: result.result.displayName,
                                                result.result.latitude,
                                                result.result.longitude
                                            )
                                        }
                                        name = title
                                        lat = selectedLat
                                        lon = selectedLon
                                        hasChosenLocation = true
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Saved Favorites / Custom Places (Prominent for Pinned site!)
                    if (customFavorites.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_custom_favorites),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            customFavorites.take(6).forEach { item ->
                                com.example.ui.common.search.RecentSearchRow(
                                    item = item,
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onItemClick = {
                                        name = item.title
                                        lat = item.latitude
                                        lon = item.longitude
                                        hasChosenLocation = true
                                    }
                                )
                            }
                        }
                    }

                    if (unifiedTransitFavorites.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_favorite_stops),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            unifiedTransitFavorites.take(4).forEach { item ->
                                com.example.ui.common.search.RecentSearchRow(
                                    item = item,
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onItemClick = {
                                        name = item.title
                                        lat = item.latitude
                                        lon = item.longitude
                                        hasChosenLocation = true
                                    }
                                )
                            }
                        }
                    }

                    if (recentSearches.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_recent_searches),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            recentSearches.take(4).forEach { item ->
                                com.example.ui.common.search.RecentSearchRow(
                                    item = item,
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onItemClick = {
                                        name = item.title
                                        lat = item.latitude
                                        lon = item.longitude
                                        hasChosenLocation = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Select on Map Option
                OutlinedButton(
                    onClick = onSelectOnMap,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_select_on_map),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Selected Location Badge
                if (hasChosenLocation) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_selected_location),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.commute_custom_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isConfigured) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConfigured) {
                        TextButton(
                            onClick = {
                                when {
                                    isHomeType -> dashboardViewModel.saveHomeLocation(null)
                                    isWork -> dashboardViewModel.saveWorkLocation(null)
                                    else -> dashboardViewModel.savePinnedLocation(null)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                if (isPinned) {
                                    androidx.compose.ui.res.stringResource(com.example.R.string.commute_unpin)
                                } else {
                                    androidx.compose.ui.res.stringResource(com.example.R.string.btn_delete)
                                }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSave(name, lat, lon) },
                            enabled = hasChosenLocation || name.isNotBlank(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
