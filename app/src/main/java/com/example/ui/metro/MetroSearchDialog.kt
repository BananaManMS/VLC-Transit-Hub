package com.example.ui.metro

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.NotAccessible
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.example.data.model.MetroStation
import com.example.ui.dashboard.AppLanguage
import com.example.util.LocationUtils
import com.example.util.normalizeForSearch
import com.example.util.StationAccessibilityHelper
import java.util.Locale

@Composable
fun MetroSearchDialog(
    searchQuery: String,
    filteredStations: List<MetroStation>,
    favoriteStationIds: List<String>,
    isDarkMode: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectStation: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
    metroViewModel: MetroViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier
) {
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val cardBg = if (isDarkMode) Color(0xFF171717) else Color.White
    val borderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    val accessibilityIncidents by metroViewModel.accessibilityIncidents.collectAsState()

    val dialogListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            color = if (isDarkMode) Color(0xFF131824) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_search_dialog_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_search_dialog_subtitle),
                            fontSize = 12.sp,
                            color = subtextColor
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.btn_close), tint = subtextColor)
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.search_station_label), color = subtextColor) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = subtextColor) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    )
                )

                LazyColumn(
                    state = dialogListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredStations, key = { it.id }) { station ->
                        val isFav = favoriteStationIds.contains(station.id)
                        val hasAccessibilityIssue = remember(station.id, station.name, accessibilityIncidents) {
                            accessibilityIncidents.any { incident ->
                                StationAccessibilityHelper.isMetroStationAffected(station.id, station.name, incident)
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    onSelectStation(station.id)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = station.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor
                                        )
                                        if (hasAccessibilityIssue) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.NotAccessible,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (station.lines.isNotEmpty()) {
                                        MetroLineBadgesRow(lineasStr = station.lines.joinToString(","))
                                    }
                                }
                                IconButton(onClick = { onToggleFavorite(station.id) }) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorito",
                                        tint = if (isFav) Color(0xFFFFC107) else subtextColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetroStationSelectionDialog(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    metroViewModel: MetroViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allStations by metroViewModel.allNetworkStations.collectAsState()
    val favoriteStationIds by metroViewModel.favoriteStations.collectAsState()
    val lastLocation by metroViewModel.lastLocation.collectAsState()
    val accessibilityIncidents by metroViewModel.accessibilityIncidents.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStations by remember(favoriteStationIds) { mutableStateOf(favoriteStationIds) }

    val dialogListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    val (closestStations, alphabeticalStations, searchResults) = remember(searchQuery, allStations, lastLocation) {
        if (searchQuery.isBlank()) {
            val refLat = lastLocation?.latitude ?: 39.4697
            val refLon = lastLocation?.longitude ?: -0.3734
            val sortedByDist = allStations.sortedBy { station ->
                LocationUtils.calculateDistanceMeters(refLat, refLon, station.latitude, station.longitude)
            }
            val top3Closest = sortedByDist.take(3)
            val remainingAlphabetical = sortedByDist.drop(3)
                .sortedBy { it.name.normalizeForSearch() }
            Triple(top3Closest, remainingAlphabetical, emptyList<MetroStation>())
        } else {
            val filtered = allStations.filter { station ->
                station.name.normalizeForSearch().contains(searchQuery.normalizeForSearch()) ||
                station.lines.any { it.contains(searchQuery, ignoreCase = true) }
            }.sortedBy { it.name.normalizeForSearch() }
            Triple(emptyList<MetroStation>(), emptyList<MetroStation>(), filtered)
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF171717) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            color = if (isDarkMode) Color(0xFF131824) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_favs_dialog_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_favs_dialog_subtitle),
                            fontSize = 12.sp,
                            color = subtextColor
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.btn_close),
                            tint = subtextColor
                        )
                    }
                }

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.search_station_label), color = subtextColor) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = subtextColor) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    )
                )

                LazyColumn(
                    state = dialogListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val renderMetroCard = @Composable { station: MetroStation, isClosest: Boolean ->
                        val isChecked = selectedStations.contains(station.id)
                        val bgCol = if (isChecked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            cardBg
                        }
                        val bord = if (isChecked) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, borderColor)
                        }

                        val hasAccessibilityIssue = remember(station.id, station.name, accessibilityIncidents) {
                            accessibilityIncidents.any { incident ->
                                StationAccessibilityHelper.isMetroStationAffected(station.id, station.name, incident)
                            }
                        }

                        val maxLimitToast = androidx.compose.ui.res.stringResource(com.example.R.string.metro_favs_max_limit_toast)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    if (!isChecked) {
                                        if (selectedStations.size >= 10) {
                                            Toast.makeText(context, maxLimitToast, Toast.LENGTH_SHORT).show()
                                        } else {
                                            selectedStations = selectedStations + station.id
                                        }
                                    } else {
                                        selectedStations = selectedStations - station.id
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = bgCol),
                            shape = RoundedCornerShape(18.dp),
                            border = bord
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = station.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (hasAccessibilityIssue) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.NotAccessible,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (isClosest) {
                                            val refLat = lastLocation?.latitude ?: 39.4697
                                            val refLon = lastLocation?.longitude ?: -0.3734
                                            val distMeters = LocationUtils.calculateDistanceMeters(refLat, refLon, station.latitude, station.longitude)
                                            val distText = if (distMeters >= 1000) String.format(Locale.US, "%.1f km", distMeters / 1000.0) else "${distMeters.toInt()} m"
                                            Surface(
                                                color = accentColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.padding(start = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Place,
                                                        contentDescription = null,
                                                        tint = accentColor,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = distText,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = accentColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (station.lines.isNotEmpty()) {
                                        MetroLineBadgesRow(lineasStr = station.lines.joinToString(","))
                                    }
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = accentColor)
                                )
                            }
                        }
                    }

                    if (searchQuery.isBlank()) {
                        if (closestStations.isNotEmpty()) {
                            item(key = "header_closest_metro") {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_search_nearest_stations),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 2.dp)
                                )
                            }
                            items(closestStations, key = { "closest_${it.id}" }) { station ->
                                renderMetroCard(station, true)
                            }
                        }

                        if (alphabeticalStations.isNotEmpty()) {
                            item(key = "header_alphabetical_metro") {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_search_all_stations),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 2.dp)
                                )
                            }
                            items(alphabeticalStations, key = { "all_${it.id}" }) { station ->
                                renderMetroCard(station, false)
                            }
                        }
                    } else {
                        items(searchResults, key = { it.id }) { station ->
                            renderMetroCard(station, false)
                        }
                    }
                }

                val minSelectionToast = androidx.compose.ui.res.stringResource(com.example.R.string.metro_favs_min_selection_toast)
                val favsUpdatedToast = androidx.compose.ui.res.stringResource(com.example.R.string.metro_favs_updated_toast)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_favs_selected_count, selectedStations.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedStations.isNotEmpty() && selectedStations.size <= 10) accentColor else MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            if (selectedStations.isEmpty() || selectedStations.size > 10) {
                                Toast.makeText(context, minSelectionToast, Toast.LENGTH_SHORT).show()
                            } else {
                                metroViewModel.updateFavoriteStations(selectedStations)
                                Toast.makeText(context, favsUpdatedToast, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        enabled = selectedStations.isNotEmpty() && selectedStations.size <= 10,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_save_changes), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MetroQuickStationPickerDialog(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    metroViewModel: MetroViewModel,
    onDismiss: () -> Unit
) {
    val allStations by metroViewModel.allNetworkStations.collectAsState()
    val lastLocation by metroViewModel.lastLocation.collectAsState()
    val accessibilityIncidents by metroViewModel.accessibilityIncidents.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val dialogListState = rememberLazyListState()

    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    val (closestStations, alphabeticalStations, searchResults) = remember(searchQuery, allStations, lastLocation) {
        if (searchQuery.isBlank()) {
            val refLat = lastLocation?.latitude ?: 39.4697
            val refLon = lastLocation?.longitude ?: -0.3734
            val sortedByDist = allStations.sortedBy { station ->
                LocationUtils.calculateDistanceMeters(refLat, refLon, station.latitude, station.longitude)
            }
            val top3Closest = sortedByDist.take(3)
            val remainingAlphabetical = sortedByDist.drop(3)
                .sortedBy { it.name.normalizeForSearch() }
            Triple(top3Closest, remainingAlphabetical, emptyList<MetroStation>())
        } else {
            val filtered = allStations.filter { station ->
                station.name.normalizeForSearch().contains(searchQuery.normalizeForSearch()) ||
                station.lines.any { it.contains(searchQuery, ignoreCase = true) }
            }.sortedBy { it.name.normalizeForSearch() }
            Triple(emptyList<MetroStation>(), emptyList<MetroStation>(), filtered)
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF171717) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            color = if (isDarkMode) Color(0xFF131824) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .testTag("quick_metro_station_picker")
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_quick_picker_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_quick_picker_subtitle),
                            fontSize = 12.sp,
                            color = subtextColor
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_quick_metro_picker")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.btn_close),
                            tint = subtextColor
                        )
                    }
                }

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.search_station_label), fontSize = 14.sp, color = subtextColor) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.search_label),
                            tint = subtextColor
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                LazyColumn(
                    state = dialogListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val renderMetroQuickCard = @Composable { station: MetroStation, isClosest: Boolean ->
                        val hasAccessibilityIssue = remember(station.id, station.name, accessibilityIncidents) {
                            accessibilityIncidents.any { incident ->
                                StationAccessibilityHelper.isMetroStationAffected(station.id, station.name, incident)
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    metroViewModel.selectStation(station.id)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = station.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (hasAccessibilityIssue) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.NotAccessible,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (isClosest) {
                                            val refLat = lastLocation?.latitude ?: 39.4697
                                            val refLon = lastLocation?.longitude ?: -0.3734
                                            val distMeters = LocationUtils.calculateDistanceMeters(refLat, refLon, station.latitude, station.longitude)
                                            val distText = if (distMeters >= 1000) String.format(Locale.US, "%.1f km", distMeters / 1000.0) else "${distMeters.toInt()} m"
                                            Surface(
                                                color = accentColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.padding(start = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Place,
                                                        contentDescription = null,
                                                        tint = accentColor,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = distText,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = accentColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (station.lines.isNotEmpty()) {
                                        MetroLineBadgesRow(lineasStr = station.lines.joinToString(","))
                                    }
                                }
                            }
                        }
                    }

                    if (searchQuery.isBlank()) {
                        if (closestStations.isNotEmpty()) {
                            item(key = "header_closest_metro_quick") {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_search_nearest_stations),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 2.dp)
                                )
                            }
                            items(closestStations, key = { "closest_${it.id}" }) { station ->
                                renderMetroQuickCard(station, true)
                            }
                        }

                        if (alphabeticalStations.isNotEmpty()) {
                            item(key = "header_alphabetical_metro_quick") {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_search_all_stations),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 2.dp)
                                )
                            }
                            items(alphabeticalStations, key = { "all_${it.id}" }) { station ->
                                renderMetroQuickCard(station, false)
                            }
                        }
                    } else {
                        items(searchResults, key = { it.id }) { station ->
                            renderMetroQuickCard(station, false)
                        }
                    }
                }
            }
        }
    }
}
