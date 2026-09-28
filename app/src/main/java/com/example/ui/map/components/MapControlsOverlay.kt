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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
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

    var showLayersSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // BACKDROP ON MAP WHEN FILTERS ARE EXPANDED
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
                onToggleSatelliteMode = { showLayersSheet = true },
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

        // RIGHT SIDE FABs (GPS Only) positioned near bottom right (Filters moved to unified Top Layers Sheet)
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
                    .width(56.dp)
            ) {
                // Recenter GPS FAB
                FloatingActionButton(
                    onClick = onRecenterUser,
                    shape = CircleShape,
                    containerColor = if (isFollowingUser) {
                        if (isDarkMode) Color(0xFF155E75) else Color(0xFFECFDF5)
                    } else {
                        com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode)
                    },
                    contentColor = if (isFollowingUser) {
                        if (isDarkMode) Color(0xFF22D3EE) else Color(0xFF10B981)
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

        // UNIFIED GOOGLE MAPS STYLE LAYERS BOTTOM SHEET
        if (showLayersSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLayersSheet = false },
                containerColor = com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                modifier = Modifier.testTag("map_layers_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 36.dp)
                ) {
                    // Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Capes del mapa" else "Detalles del mapa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                        IconButton(
                            onClick = { showLayersSheet = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Section 1: Tipo de mapa
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "TIPUS DE MAPA" else "TIPO DE MAPA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp)
                    ) {
                        // Standard Option
                        val isStandardSelected = !isSatelliteMode
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (isSatelliteMode) {
                                        onToggleSatelliteMode()
                                    }
                                }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isStandardSelected) 2.dp else 1.dp,
                                    color = if (isStandardSelected) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0))
                                ),
                                color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Soft classic sand/beige background of a physical street map
                                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F3E9)))
                                    
                                    // Stylized Park (flat sage green block)
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp, 40.dp)
                                            .align(Alignment.TopStart)
                                            .background(
                                                Color(0xFFCBE5CB),
                                                RoundedCornerShape(bottomEnd = 14.dp)
                                            )
                                    )
                                    
                                    // Stylized Water (flat sky blue block)
                                    Box(
                                        modifier = Modifier
                                            .size(55.dp, 35.dp)
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                Color(0xFFAFD6EA),
                                                RoundedCornerShape(topStart = 14.dp)
                                            )
                                    )
                                    
                                    // Main highway (light yellow road)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .align(Alignment.Center)
                                            .background(Color(0xFFFEF08A))
                                    )
                                    
                                    // Stylized local streets (white flat lines)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(4.dp)
                                            .align(Alignment.Center)
                                            .background(Color.White)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 12.dp)
                                            .background(Color.White)
                                    )

                                    // Central Icon Container
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isStandardSelected) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color(0xFF334155) else Color.White),
                                        shadowElevation = 3.dp,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Map,
                                                contentDescription = null,
                                                tint = if (isStandardSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Selection micro-dot indicator in top right
                                    if (isStandardSelected) {
                                        Box(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(14.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                .align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(Color.White, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Estàndard" else "Estándar",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                fontWeight = if (isStandardSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isStandardSelected) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569))
                            )
                        }

                        // Satellite Option
                        val isSateliteSelected = isSatelliteMode
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isSatelliteMode) {
                                        onToggleSatelliteMode()
                                    }
                                }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isSateliteSelected) 2.dp else 1.dp,
                                    color = if (isSateliteSelected) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0))
                                ),
                                color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFF0F2519),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Satellite-like terrain background (dark forest green, independent of theme)
                                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF143021)))
                                    
                                    // Deep ocean/lake water (dark navy blue)
                                    Box(
                                        modifier = Modifier
                                            .size(45.dp, 40.dp)
                                            .align(Alignment.BottomStart)
                                            .background(
                                                Color(0xFF0F1E36),
                                                RoundedCornerShape(topEnd = 14.dp)
                                            )
                                    )
                                    
                                    // Light urban layout grids (low opacity white lines)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .align(Alignment.Center)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .align(Alignment.Center)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    )
                                    
                                    // Atmospheric Cloud layer (translucent white block representing satellite photo)
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp, 20.dp)
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 4.dp)
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    )

                                    // Central Icon Container
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSateliteSelected) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color(0xFF334155) else Color.White),
                                        shadowElevation = 3.dp,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Layers,
                                                contentDescription = null,
                                                tint = if (isSateliteSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Selection micro-dot indicator in top right
                                    if (isSateliteSelected) {
                                        Box(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(14.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                .align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(Color.White, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Satèl·lit" else "Satélite",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                fontWeight = if (isSateliteSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSateliteSelected) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569))
                            )
                        }
                    }

                    // Section 2: Modos de transporte / Capas
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "MODOS DE TRANSPORT" else "MODOS DE TRANSPORTE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Grid of 6 Filters
                    val filtersList = listOf(
                        MapLayerFilterItem(
                            type = MapFilterType.METRO,
                            label = if (appLanguage == AppLanguage.CA) "Metro" else "Metro",
                            activeColor = Color(0xFFEA1D24), // Official Metrovalencia Red
                            logoRes = R.drawable.logo_metrovalencia,
                            fallbackVector = Icons.Default.Subway,
                            padding = 0.dp
                        ),
                        MapLayerFilterItem(
                            type = MapFilterType.CERCANIAS,
                            label = if (appLanguage == AppLanguage.CA) "Rodalia" else "Cercanías",
                            activeColor = Color(0xFFE0001A), // Official Cercanías Red
                            logoRes = R.drawable.logo_cercanias,
                            fallbackVector = Icons.Default.Train,
                            padding = 0.dp
                        ),
                        MapLayerFilterItem(
                            type = MapFilterType.BUS,
                            label = if (appLanguage == AppLanguage.CA) "Bus EMT" else "Bus EMT",
                            activeColor = Color(0xFFE53935), // Official EMT Red
                            logoRes = R.drawable.logo_emt_valencia,
                            fallbackVector = Icons.Default.DirectionsBus,
                            padding = 0.dp
                        ),
                        MapLayerFilterItem(
                            type = MapFilterType.METROBUS,
                            label = if (appLanguage == AppLanguage.CA) "Metrobús" else "Metrobús",
                            activeColor = Color(0xFFD97706), // Official Metrobús Amber
                            logoRes = R.drawable.logo_metrobus,
                            fallbackVector = Icons.Default.DirectionsBus,
                            padding = 0.dp
                        ),
                        MapLayerFilterItem(
                            type = MapFilterType.VALENBISI,
                            label = if (appLanguage == AppLanguage.CA) "Valenbisi" else "Valenbisi",
                            activeColor = Color(0xFF10B981), // Valenbisi Green
                            logoRes = R.drawable.ic_bike,
                            fallbackVector = Icons.Default.DirectionsBike,
                            padding = 0.dp,
                            isMonochromeIcon = true
                        ),
                        MapLayerFilterItem(
                            type = MapFilterType.FAVORITES,
                            label = if (appLanguage == AppLanguage.CA) "Favorits" else "Favoritos",
                            activeColor = Color(0xFFEAB308), // Favorites Gold
                            fallbackVector = Icons.Default.Star,
                            padding = 0.dp,
                            isMonochromeIcon = true
                        )
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (rowIndex in 0..1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (colIndex in 0..2) {
                                    val itemIndex = rowIndex * 3 + colIndex
                                    val filterItem = filtersList.getOrNull(itemIndex)
                                    if (filterItem != null) {
                                        val isSelected = when (filterItem.type) {
                                            MapFilterType.FAVORITES -> activeFilter.isFavorites
                                            MapFilterType.BUS -> !activeFilter.isFavorites && activeFilter.showBus
                                            MapFilterType.METROBUS -> !activeFilter.isFavorites && activeFilter.showMetrobus
                                            MapFilterType.METRO -> !activeFilter.isFavorites && activeFilter.showMetro
                                            MapFilterType.CERCANIAS -> !activeFilter.isFavorites && activeFilter.showCercanias
                                            MapFilterType.VALENBISI -> !activeFilter.isFavorites && activeFilter.showValenbisi
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    onFilterToggle(filterItem.type)
                                                }
                                        ) {
                                            val outerSize = 54.dp
                                            val logoSize = 36.dp // Reduced to 36.dp to leave a beautiful breathing space of padding!

                                            // Circular button with perfect circular clip & click ripple
                                            Box(
                                                modifier = Modifier
                                                    .size(outerSize)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        onFilterToggle(filterItem.type)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (filterItem.logoRes != null && !filterItem.isMonochromeIcon) {
                                                    if (isSelected) {
                                                        // Selection brand ring (thin and clean, matching the brand color)
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color.Transparent,
                                                            border = BorderStroke(2.dp, filterItem.activeColor),
                                                            modifier = Modifier.fillMaxSize()
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Image(
                                                                    painter = painterResource(id = filterItem.logoRes),
                                                                    contentDescription = filterItem.label,
                                                                    contentScale = ContentScale.Fit,
                                                                    modifier = Modifier.size(logoSize)
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        // Clean unselected logo with reduced opacity
                                                        Image(
                                                            painter = painterResource(id = filterItem.logoRes),
                                                            contentDescription = filterItem.label,
                                                            contentScale = ContentScale.Fit,
                                                            alpha = 0.45f,
                                                            modifier = Modifier.size(logoSize)
                                                        )
                                                    }
                                                } else {
                                                    // Render monochrome vector icon (Valenbisi or Star)
                                                    val surfaceColor = if (isSelected) {
                                                        filterItem.activeColor
                                                    } else {
                                                        if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                                    }

                                                    val borderStroke = if (isSelected) {
                                                        null
                                                    } else {
                                                        BorderStroke(1.dp, if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0))
                                                    }

                                                    Surface(
                                                        shape = CircleShape,
                                                        color = surfaceColor,
                                                        border = borderStroke,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            val tintColor = if (isSelected) {
                                                                Color.White
                                                            } else {
                                                                if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                                                            }

                                                            val iconSize = 24.dp // Enlarged to fill the circle beautifully with perfect spacing

                                                            if (filterItem.logoRes != null) {
                                                                Icon(
                                                                    painter = painterResource(id = filterItem.logoRes),
                                                                    contentDescription = filterItem.label,
                                                                    tint = tintColor,
                                                                    modifier = Modifier.size(iconSize)
                                                                )
                                                            } else if (filterItem.fallbackVector != null) {
                                                                Icon(
                                                                    imageVector = filterItem.fallbackVector,
                                                                    contentDescription = filterItem.label,
                                                                    tint = tintColor,
                                                                    modifier = Modifier.size(iconSize)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = filterItem.label,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                ),
                                                color = if (isSelected) filterItem.activeColor else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569)),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MapLayerFilterItem(
    val type: MapFilterType,
    val label: String,
    val activeColor: Color,
    val logoRes: Int? = null,
    val fallbackVector: ImageVector? = null,
    val padding: androidx.compose.ui.unit.Dp = 8.dp,
    val isMonochromeIcon: Boolean = false
)

