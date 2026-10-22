package com.example.ui.bus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.components.ValenbisiStation
import com.example.util.LocationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValenbisiTab(
    appLanguage: AppLanguage,
    valenbisiStations: List<ValenbisiStation>,
    favoriteValenbisi: List<String>,
    valenbisiAliases: Map<String, String>,
    searchQuery: String,
    isLoading: Boolean,
    userLocation: Pair<Double, Double>? = null,
    isDarkMode: Boolean = false,
    onSearchQueryChanged: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSaveAlias: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onUpdateLocation: ((Double, Double) -> Unit)? = null,
    activeTripBottomPadding: Dp = 0.dp
) {
    var stationToEditAlias by remember { mutableStateOf<ValenbisiStation?>(null) }
    var selectedStationForSheet by remember { mutableStateOf<ValenbisiStation?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    val brandColor = getValenbisiBrandColor(isDarkMode)

    LaunchedEffect(searchQuery) {
        listState.scrollToItem(0)
    }

    if (stationToEditAlias != null) {
        val st = stationToEditAlias!!
        EditValenbisiAliasDialog(
            stationNumber = st.number.toString(),
            stationDefaultName = st.name,
            currentAlias = valenbisiAliases[st.number.toString()] ?: "",
            appLanguage = appLanguage,
            onSaveAlias = { alias ->
                onSaveAlias(st.number.toString(), alias)
            },
            onDismiss = { stationToEditAlias = null }
        )
    }

    if (selectedStationForSheet != null) {
        val st = selectedStationForSheet!!
        val isFav = favoriteValenbisi.contains(st.number.toString())
        val alias = valenbisiAliases[st.number.toString()]

        ValenbisiModalBottomSheet(
            station = st,
            isDarkMode = isDarkMode,
            appLanguage = appLanguage,
            isFavorite = isFav,
            alias = alias,
            onToggleFavorite = { onToggleFavorite(st.number.toString()) },
            onEditAlias = { stationToEditAlias = st },
            onDismissRequest = { selectedStationForSheet = null }
        )
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Search TextField matching EMT/Metrobus style
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("valenbisi_search_bar"),
                placeholder = {
                    Text(
                        text = stringResource(com.example.R.string.valenbisi_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(com.example.R.string.search_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChanged("")
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(com.example.R.string.clear_search_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface,
                    focusedBorderColor = brandColor,
                    unfocusedBorderColor = if (isDarkMode) Color(0xFF262C3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            )

            // Section 1: FAVORITAS GUARDADAS (visible when no active search query)
            if (searchQuery.isEmpty()) {
                val favoriteStationsList = remember(valenbisiStations, favoriteValenbisi, userLocation, valenbisiAliases) {
                    val refLocation = userLocation ?: Pair(39.46975, -0.37739)
                    val list = valenbisiStations.filter { favoriteValenbisi.contains(it.number.toString()) }.map { st ->
                        val dist = LocationUtils.calculateDistanceMeters(st.latitude, st.longitude, refLocation.first, refLocation.second)
                        st.copy(distanceMeters = dist, distanceText = LocationUtils.formatDistance(dist))
                    }.sortedBy { dr -> dr.distanceMeters }
                    list
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(com.example.R.string.valenbisi_section_favorites),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = brandColor
                    )
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("favorite_valenbisi_carousel"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (favoriteStationsList.isEmpty()) {
                        item {
                            EmptyFavoriteValenbisiCard(isDarkMode = isDarkMode)
                        }
                    } else {
                        items(favoriteStationsList, key = { "fav_vb_${it.number}" }) { station ->
                            val alias = valenbisiAliases[station.number.toString()]
                            FavoriteValenbisiStationCard(
                                station = station,
                                alias = alias,
                                isDarkMode = isDarkMode,
                                onCardClick = { selectedStationForSheet = station }
                            )
                        }
                    }
                }
            }

            // Section 2 Header: ESTACIONES CERCANAS / RESULTADOS DE BÚSQUEDA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sectionTitle = if (searchQuery.isNotEmpty()) {
                    stringResource(com.example.R.string.valenbisi_section_search_results)
                } else {
                    stringResource(com.example.R.string.valenbisi_section_nearby)
                }
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = brandColor
                )
            }

            // Filtered stations list logic
            val filteredList = remember(valenbisiStations, favoriteValenbisi, searchQuery, userLocation, valenbisiAliases) {
                val refLocation = userLocation ?: Pair(39.46975, -0.37739)
                val list = valenbisiStations.map { st ->
                    val dist = LocationUtils.calculateDistanceMeters(
                        st.latitude, st.longitude,
                        refLocation.first, refLocation.second
                    )
                    st.copy(
                        distanceMeters = dist,
                        distanceText = LocationUtils.formatDistance(dist)
                    )
                }

                if (searchQuery.isNotBlank()) {
                    list.mapNotNull { st ->
                        val alias = valenbisiAliases[st.number.toString()]
                        val score = computeSearchScore(st.number.toString(), "${st.name} ${st.address}", searchQuery, alias)
                        if (score > 0.0) Pair(st, score) else null
                    }.sortedWith(
                        compareByDescending<Pair<ValenbisiStation, Double>> { favoriteValenbisi.contains(it.first.number.toString()) }
                            .thenByDescending { it.second }
                            .thenBy { it.first.distanceMeters }
                    ).map { it.first }
                } else {
                    list.sortedBy { it.distanceMeters }.take(25)
                }
            }

            // Section 2 List Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && valenbisiStations.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(4) {
                            SkeletonCardItem()
                        }
                    }
                } else if (filteredList.isEmpty()) {
                    val emptyMsg = if (searchQuery.isNotEmpty()) {
                        stringResource(com.example.R.string.valenbisi_search_not_found, searchQuery)
                    } else {
                        stringResource(com.example.R.string.nearby_no_valenbisi_found)
                    }
                    EmptyStateCard(
                        title = stringResource(com.example.R.string.valenbisi_no_stations_title),
                        message = emptyMsg,
                        icon = Icons.Default.DirectionsBike,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp + activeTripBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredList,
                            key = { it.number }
                        ) { station ->
                            val isFav = favoriteValenbisi.contains(station.number.toString())
                            val alias = valenbisiAliases[station.number.toString()]

                            ValenbisiStationCard(
                                station = station,
                                alias = alias,
                                isFavorite = isFav,
                                appLanguage = appLanguage,
                                isDarkMode = isDarkMode,
                                onToggleFavorite = { onToggleFavorite(station.number.toString()) },
                                onCardClick = { selectedStationForSheet = station }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditValenbisiAliasDialog(
    stationNumber: String,
    stationDefaultName: String,
    currentAlias: String,
    appLanguage: AppLanguage,
    onSaveAlias: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var aliasInput by remember(stationNumber, currentAlias) { mutableStateOf(currentAlias) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (appLanguage == AppLanguage.CA) "Nom personalitzat" else "Nombre personalizado",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (appLanguage == AppLanguage.CA)
                        "Assigna un nom per identificar l'estació $stationNumber ($stationDefaultName) més fàcilment:"
                    else "Asigna un nombre para identificar la estación $stationNumber ($stationDefaultName) más fácilmente:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { if (it.length <= 32) aliasInput = it },
                    label = { Text(stringResource(com.example.R.string.alias_dialog_field_label)) },
                    placeholder = { Text(stringResource(com.example.R.string.alias_placeholder)) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = stringResource(com.example.R.string.alias_dialog_length_format, aliasInput.length),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("valenbisi_alias_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAlias(aliasInput)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_valenbisi_alias_button")
            ) {
                Text(if (appLanguage == AppLanguage.CA) "Desar" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(if (appLanguage == AppLanguage.CA) "Cancel·lar" else "Cancelar")
            }
        }
    )
}
