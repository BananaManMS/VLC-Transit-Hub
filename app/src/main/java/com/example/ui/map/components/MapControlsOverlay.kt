package com.example.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import com.example.ui.map.RecentSearch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.map.MapFilter
import com.example.ui.map.MapFilterType
import com.example.ui.map.MapSearchResult
import com.example.ui.map.SelectedMapItem
import com.example.ui.dashboard.AppLanguage

@Composable
fun MapControlsOverlay(
    modifier: Modifier = Modifier,
    activeFilter: MapFilter,
    busCount: Int,
    metroCount: Int,
    cameraZoom: Double = 14.5,
    isDarkMode: Boolean,
    onFilterToggle: (MapFilterType) -> Unit,
    onRecenterUser: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onSearchClick: () -> Unit = {},
    // Unified Search properties
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<MapSearchResult>,
    onSearchResultClick: (MapSearchResult) -> Unit,
    isSearching: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.CA,
    isSatelliteMode: Boolean = false,
    onToggleSatelliteMode: () -> Unit = {},
    hasPersistentBottomPanel: Boolean = false,
    currentNearbySheetHeight: androidx.compose.ui.unit.Dp = 240.dp,
    bottomPanelHeightPx: Float = 0f,
    isFollowingUser: Boolean = false,
    selectedItem: SelectedMapItem? = null,
    onDirectionsClick: ((SelectedMapItem?) -> Unit)? = null,
    isItineraryActive: Boolean = false,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    // Phase 2 Zero-State parameters
    recentSearches: List<RecentSearch> = emptyList(),
    homeLocation: RecentSearch? = null,
    workLocation: RecentSearch? = null,
    customFavorites: List<RecentSearch> = emptyList(),
    unifiedTransitFavorites: List<RecentSearch> = emptyList(),
    isSearchFocused: Boolean = false,
    onSearchFocusChange: (Boolean) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onRemoveRecentSearch: (String) -> Unit = {},
    onElegirEnMapaClick: () -> Unit = {},
    onSaveLocationShortcutClick: (isHome: Boolean) -> Unit = {}
) {
    val isDirectionsVisible = selectedItem != null && onDirectionsClick != null
    val localFocusManager = LocalFocusManager.current

    var isFilterExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedItem, isItineraryActive) {
        if (selectedItem != null || isItineraryActive) {
            isFilterExpanded = false
        }
    }

    LaunchedEffect(searchQuery, isSearchFocused) {
        if (searchQuery.isNotEmpty() || isSearchFocused) {
            isFilterExpanded = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // BACKDROP ON MAP WHEN FILTERS ARE EXPANDED
        if (isFilterExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isFilterExpanded = false
                    }
                    .testTag("filter_expanded_backdrop")
            )
        }

        // BACKDROP ON MAP WHEN SEARCH IS FOCUSED
        if (isSearchFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSearchQueryChange("")
                        onSearchFocusChange(false)
                        localFocusManager.clearFocus()
                    }
                    .testTag("search_focus_backdrop")
            )
        }

        // TOP LAYOUT (SEARCH BAR + RESULTS)
        if (!isItineraryActive) {
            MapTopBar(
                isDarkMode = isDarkMode,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchResults = searchResults,
                onSearchResultClick = onSearchResultClick,
                onSearchClick = onSearchClick,
                isSearching = isSearching,
                appLanguage = appLanguage,
                isSatelliteMode = isSatelliteMode,
                onToggleSatelliteMode = onToggleSatelliteMode,
                recentSearches = recentSearches,
                homeLocation = homeLocation,
                workLocation = workLocation,
                customFavorites = customFavorites,
                unifiedTransitFavorites = unifiedTransitFavorites,
                isSearchFocused = isSearchFocused,
                onSearchFocusChange = onSearchFocusChange,
                onClearRecentSearches = onClearRecentSearches,
                onRemoveRecentSearch = onRemoveRecentSearch,
                onElegirEnMapaClick = onElegirEnMapaClick,
                onSaveLocationShortcutClick = onSaveLocationShortcutClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            )
        }

        val density = LocalDensity.current
        val bottomPanelDp = with(density) { bottomPanelHeightPx.toDp() }
        val effectivePanelDp = if (bottomPanelDp > 0.dp) {
            bottomPanelDp
        } else if (hasPersistentBottomPanel) {
            currentNearbySheetHeight
        } else {
            0.dp
        }
        val clampedPanelHeight = if (effectivePanelDp > 340.dp) 340.dp else effectivePanelDp
        val effectiveBottomPadding = if (clampedPanelHeight > 44.dp) {
            clampedPanelHeight + 16.dp
        } else {
            16.dp + activeTripBottomPadding
        }

        // FLOATING "CÓMO LLEGAR / COM ARRIBAR" FAB
        // Shown on the right side floating above map when no item sheet is selected
        AnimatedVisibility(
            visible = isDirectionsVisible && selectedItem == null && !isSearchFocused,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = effectiveBottomPadding,
                    end = 20.dp
                )
        ) {
            if (onDirectionsClick != null) {
                ExtendedFloatingActionButton(
                    onClick = { onDirectionsClick(selectedItem) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Com arribar" else "Cómo llegar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    containerColor = Color(0xFF0284C7),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("map_fab_how_to_get_there")
                )
            }
        }

        // RIGHT SIDE FABs (GPS + Filter Expander) positioned near bottom right
        if (!isSearchFocused) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = effectiveBottomPadding,
                        end = 20.dp
                    )
                    .width(56.dp) // Maintain a constant width to prevent elements from shifting horizontally, applied after padding to keep inner width full 56.dp
            ) {
            // Only show Filters when NOT in an active itinerary (ver ruta) and NOT viewing details (selectedItem == null)
            if (!isItineraryActive && selectedItem == null) {
                // Expanded Filter Options
                AnimatedVisibility(
                    visible = isFilterExpanded,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .background(
                                color = com.example.ui.theme.AppThemeColors.overlayBackground(isDarkMode, 0.95f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(8.dp)
                    ) {
                        // Favorites Filter
                        val isFavSelected = activeFilter.isFavorites
                        FloatingActionButton(
                            onClick = { onFilterToggle(MapFilterType.FAVORITES) },
                            shape = CircleShape,
                            containerColor = if (isFavSelected) Color(0xFFEAB308) else com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                            contentColor = if (isFavSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorites",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Bus Filter
                        val isBusSelected = !activeFilter.isFavorites && activeFilter.showBus
                        FloatingActionButton(
                            onClick = { onFilterToggle(MapFilterType.BUS) },
                            shape = CircleShape,
                            containerColor = if (isBusSelected) Color(0xFFE53935) else com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                            contentColor = if (isBusSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = "EMT Bus",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Metrobus Filter
                        val isMetrobusSelected = !activeFilter.isFavorites && activeFilter.showMetrobus
                        FloatingActionButton(
                            onClick = { onFilterToggle(MapFilterType.METROBUS) },
                            shape = CircleShape,
                            containerColor = if (isMetrobusSelected) Color(0xFFD97706) else com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                            contentColor = if (isMetrobusSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                            modifier = Modifier.size(40.dp).testTag("map_filter_metrobus")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = "Metrobús",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Metro Filter
                        val isMetroSelected = !activeFilter.isFavorites && activeFilter.showMetro
                        FloatingActionButton(
                            onClick = { onFilterToggle(MapFilterType.METRO) },
                            shape = CircleShape,
                            containerColor = if (isMetroSelected) Color(0xFF0284C7) else com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                            contentColor = if (isMetroSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subway,
                                contentDescription = "Metro",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Cercanias Filter
                        val isCercaniasSelected = !activeFilter.isFavorites && activeFilter.showCercanias
                        FloatingActionButton(
                            onClick = { onFilterToggle(MapFilterType.CERCANIAS) },
                            shape = CircleShape,
                            containerColor = if (isCercaniasSelected) Color(0xFF702B7B) else com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                            contentColor = if (isCercaniasSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Train,
                                contentDescription = "Cercanías",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Valenbisi Filter
                        val isValenbisiSelected = !activeFilter.isFavorites && activeFilter.showValenbisi
                        FloatingActionButton(
                            onClick = { onFilterToggle(MapFilterType.VALENBISI) },
                            shape = CircleShape,
                            containerColor = if (isValenbisiSelected) Color(0xFF10B981) else com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                            contentColor = if (isValenbisiSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBike,
                                contentDescription = "Valenbisi",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Main Filter Toggle Button
                FloatingActionButton(
                    onClick = { isFilterExpanded = !isFilterExpanded },
                    shape = CircleShape,
                    containerColor = if (isFilterExpanded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode)
                    },
                    contentColor = if (isFilterExpanded) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        if (isDarkMode) Color(0xFFF8FAFC) else MaterialTheme.colorScheme.primary
                    },
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_fab_filters_toggle")
                ) {
                    Icon(
                        imageVector = if (isFilterExpanded) Icons.Default.Close else Icons.Default.Tune,
                        contentDescription = if (appLanguage == AppLanguage.CA) "Filtres" else "Filtros",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Recenter GPS FAB
            FloatingActionButton(
                onClick = onRecenterUser,
                shape = CircleShape,
                containerColor = if (isFollowingUser) {
                    if (isDarkMode) Color(0xFF155E75) else Color(0xFFECFDF5) // light background when active
                } else {
                    com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode)
                },
                contentColor = if (isFollowingUser) {
                    if (isDarkMode) Color(0xFF22D3EE) else Color(0xFF10B981) // cyan in dark mode, emerald in light mode
                } else {
                    if (isDarkMode) Color(0xFFF8FAFC) else MaterialTheme.colorScheme.primary
                },
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_fab_my_location")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = if (appLanguage == AppLanguage.CA) "La meua ubicació" else "Mi ubicación",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        }
    }
}

