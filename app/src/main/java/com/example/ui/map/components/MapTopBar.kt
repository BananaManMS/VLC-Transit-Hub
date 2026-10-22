package com.example.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.common.search.UnifiedSearchSuggestionsPanel
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.MapFilter
import com.example.ui.map.MapFilterType
import com.example.ui.map.MapSearchResult
import com.example.ui.map.RecentSearch

@Composable
fun MapTopBar(
    isDarkMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<MapSearchResult>,
    onSearchResultClick: (MapSearchResult) -> Unit,
    onSearchClick: () -> Unit = {},
    isSearching: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.CA,
    isSatelliteMode: Boolean = false,
    onToggleSatelliteMode: () -> Unit = {},
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
    onSaveLocationShortcutClick: (isHome: Boolean) -> Unit = {},
    onOpenNetworkPlans: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("map_search_and_filter_column")
    ) {
        // 1. TOP BAR ROW (FLOATING SEARCH BAR + SATELLITE/MAP TOGGLE BUTTON)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
        ) {
            // FLOATING SEARCH BAR CARD (Takes full remaining width and expands to 100% when active)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable {
                        onSearchFocusChange(true)
                        onSearchClick()
                        try {
                            focusRequester.requestFocus()
                        } catch (e: Exception) {
                            // ignore if not ready
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isDarkMode) Color.White else Color.Black
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                onSearchFocusChange(focusState.isFocused)
                                if (focusState.isFocused) {
                                    onSearchClick()
                                }
                            }
                            .testTag("map_search_input"),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.map_search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty() || isSearchFocused) {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    onSearchQueryChange("")
                                    onSearchFocusChange(true)
                                    try {
                                        focusRequester.requestFocus()
                                    } catch (e: Exception) {}
                                } else {
                                    onSearchFocusChange(false)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // OFFICIAL NETWORK PLANS BUTTON (Round button to the right of search bar - without blue border)
            AnimatedVisibility(
                visible = !isSearchFocused && searchQuery.isEmpty() && onOpenNetworkPlans != null,
                enter = fadeIn(tween(200)) + expandHorizontally(tween(250)),
                exit = fadeOut(tween(150)) + shrinkHorizontally(tween(200))
            ) {
                Surface(
                    shape = CircleShape,
                    color = com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                    shadowElevation = 4.dp,
                    border = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onOpenNetworkPlans?.invoke() }
                        .testTag("map_network_plans_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Plànols de la xarxa" else "Planos de la red",
                            tint = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 2. UNIFIED SEARCH RESULTS / ZERO STATE CARD
        val showDropdown = isSearchFocused
        if (showDropdown) {
            UnifiedSearchSuggestionsPanel(
                searchQuery = searchQuery,
                searchResults = searchResults,
                isSearching = isSearching,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                recentSearches = recentSearches,
                homeLocation = homeLocation,
                workLocation = workLocation,
                customFavorites = customFavorites,
                unifiedTransitFavorites = unifiedTransitFavorites,
                onSearchResultClick = onSearchResultClick,
                onClearRecentSearches = onClearRecentSearches,
                onRemoveRecentSearch = onRemoveRecentSearch,
                onElegirEnMapaClick = onElegirEnMapaClick,
                onSaveLocationShortcutClick = onSaveLocationShortcutClick
            )
        }
    }
}
