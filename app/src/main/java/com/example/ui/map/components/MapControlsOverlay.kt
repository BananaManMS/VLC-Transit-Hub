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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
import com.example.ui.map.RecentSearch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    hasPersistentBottomPanel: Boolean = false,
    currentNearbySheetHeight: androidx.compose.ui.unit.Dp = 240.dp,
    isFollowingUser: Boolean = false,
    selectedItem: SelectedMapItem? = null,
    onDirectionsClick: ((SelectedMapItem?) -> Unit)? = null,
    isItineraryActive: Boolean = false,
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

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
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
                        onSearchFocusChange(false)
                        localFocusManager.clearFocus()
                    }
                    .testTag("search_focus_backdrop")
            )
        }

        // TOP LAYOUT (SEARCH BAR + RESULTS + FILTER CHIPS)
        if (!isItineraryActive) {
            MapTopBar(
                activeFilter = activeFilter,
                cameraZoom = cameraZoom,
                isDarkMode = isDarkMode,
                onFilterToggle = onFilterToggle,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchResults = searchResults,
                onSearchResultClick = onSearchResultClick,
                onSearchClick = onSearchClick,
                isSearching = isSearching,
                appLanguage = appLanguage,
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
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            )
        }

        // FLOATING "CÓMO LLEGAR / COM ARRIBAR" FAB
        // Shown only when directions are enabled and no modal bottom sheet is actively covering the screen
        AnimatedVisibility(
            visible = isDirectionsVisible && selectedItem == null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 292.dp, end = 24.dp)
        ) {
            if (onDirectionsClick != null) {
                ExtendedFloatingActionButton(
                    onClick = { onDirectionsClick(null) },
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

        // RIGHT SIDE FABs (Zoom + / - and GPS) positioned near bottom right
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = if (hasPersistentBottomPanel) {
                        val clampedHeight = if (currentNearbySheetHeight > 240.dp) 240.dp else currentNearbySheetHeight
                        clampedHeight + 24.dp
                    } else if (selectedItem != null) {
                        432.dp // Floats safely above standard detail modal sheets
                    } else {
                        44.dp
                    },
                    end = 20.dp
                )
        ) {
            // Recenter GPS FAB
            FloatingActionButton(
                onClick = onRecenterUser,
                shape = CircleShape,
                containerColor = if (isFollowingUser) {
                    if (isDarkMode) Color(0xFF155E75) else Color(0xFFECFDF5) // light background when active
                } else {
                    if (isDarkMode) Color(0xFF1E293B) else Color.White
                },
                contentColor = if (isFollowingUser) {
                    if (isDarkMode) Color(0xFF22D3EE) else Color(0xFF10B981) // cyan in dark mode, emerald in light mode
                } else {
                    if (isDarkMode) Color.White else MaterialTheme.colorScheme.primary
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

            // Zoom In
            FloatingActionButton(
                onClick = onZoomIn,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                containerColor = if (isDarkMode) Color(0xFF1E293B) else Color.White,
                contentColor = if (isDarkMode) Color.White else Color.Black,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("map_fab_zoom_in")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (appLanguage == AppLanguage.CA) "Augmentar zoom" else "Aumentar zoom",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom Out
            FloatingActionButton(
                onClick = onZoomOut,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                containerColor = if (isDarkMode) Color(0xFF1E293B) else Color.White,
                contentColor = if (isDarkMode) Color.White else Color.Black,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("map_fab_zoom_out")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = if (appLanguage == AppLanguage.CA) "Reduir zoom" else "Alejar zoom",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

