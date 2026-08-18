package com.example.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
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
    activeFilter: MapFilter,
    cameraZoom: Double,
    isDarkMode: Boolean,
    onFilterToggle: (MapFilterType) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<MapSearchResult>,
    onSearchResultClick: (MapSearchResult) -> Unit,
    onSearchClick: () -> Unit = {},
    isSearching: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.CA,
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
        // 1. FLOATING SEARCH BAR CARD
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDarkMode) Color(0xFF1E293B) else Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(
                1.dp,
                if (isDarkMode) Color(0xFF334155).copy(alpha = 0.8f) else Color(0xFFE2E8F0)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
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
                                text = if (appLanguage == AppLanguage.CA) "Buscar adreces, parades o línies..." else "Buscar direcciones, paradas o líneas...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
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

        // 2. UNIFIED SEARCH RESULTS / ZERO STATE CARD
        val showDropdown = searchQuery.isNotEmpty() || isSearchFocused
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

        // 3. SEGMENTED FILTER CHIPS
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            border = BorderStroke(
                1.dp,
                if (isDarkMode) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFE2E8F0)
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TransportFilterChip(
                    label = "",
                    icon = Icons.Default.Star,
                    isSelected = activeFilter.isFavorites,
                    activeColor = Color(0xFFEAB308), // Warm Gold
                    isDarkMode = isDarkMode,
                    onClick = { onFilterToggle(MapFilterType.FAVORITES) }
                )
                TransportFilterChip(
                    label = "",
                    icon = Icons.Default.DirectionsBus,
                    isSelected = !activeFilter.isFavorites && activeFilter.showBus,
                    activeColor = Color(0xFFE53935), // EMT Red
                    isDarkMode = isDarkMode,
                    onClick = { onFilterToggle(MapFilterType.BUS) }
                )
                TransportFilterChip(
                    label = "",
                    icon = Icons.Default.Subway,
                    isSelected = !activeFilter.isFavorites && activeFilter.showMetro,
                    activeColor = Color(0xFF0284C7), // Metro Blue
                    isDarkMode = isDarkMode,
                    onClick = { onFilterToggle(MapFilterType.METRO) }
                )
                TransportFilterChip(
                    label = "",
                    icon = Icons.Default.Train,
                    isSelected = !activeFilter.isFavorites && activeFilter.showCercanias,
                    activeColor = Color(0xFF702B7B), // Rodalia Purple
                    isDarkMode = isDarkMode,
                    onClick = { onFilterToggle(MapFilterType.CERCANIAS) }
                )
                TransportFilterChip(
                    label = "",
                    icon = Icons.Default.DirectionsBike,
                    isSelected = !activeFilter.isFavorites && activeFilter.showValenbisi,
                    activeColor = Color(0xFF10B981), // Emerald Green
                    isDarkMode = isDarkMode,
                    onClick = { onFilterToggle(MapFilterType.VALENBISI) }
                )
            }
        }

        // 4. ACTIVE FILTER HELP LABELS
        val isOnlyBus = !activeFilter.isFavorites && activeFilter.showBus && !activeFilter.showMetro && !activeFilter.showCercanias
        if (isOnlyBus && searchQuery.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
                border = BorderStroke(
                    0.5.dp,
                    if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
                )
            ) {
                val (hintText, hintColor) = when {
                    cameraZoom < 13.5 -> (if (appLanguage == AppLanguage.CA) "🔹 Parades agrupades (fes zoom o polsa un grup)" else "🔹 Paradas agrupadas (haz zoom o pulsa un grupo)") to Color(0xFF1E88E5)
                    cameraZoom < 15.2 -> (if (appLanguage == AppLanguage.CA) "🟡 Vista simplificada (fes zoom per a veure detalls)" else "🟡 Vista simplificada (haz zoom para ver detalles)") to Color(0xFFD97706)
                    else -> (if (appLanguage == AppLanguage.CA) "📍 Parades detallades a la zona visible" else "📍 Paradas detalladas en la zona visible") to (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569))
                }
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = hintColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
