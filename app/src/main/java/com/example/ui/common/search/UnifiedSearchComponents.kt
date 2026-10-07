package com.example.ui.common.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.data.model.PlaceCategory
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NominatimResult
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.MapSearchResult
import com.example.ui.map.RecentSearch
import com.example.ui.routing.PlannerLocation

/**
 * Unified Search Suggestions and Zero-State Dropdown Panel.
 * Used globally across the main Map search bar and the Route Planner search fields.
 */
@Composable
fun UnifiedSearchSuggestionsPanel(
    searchQuery: String,
    searchResults: List<MapSearchResult>,
    isSearching: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage = AppLanguage.CA,
    recentSearches: List<RecentSearch> = emptyList(),
    homeLocation: RecentSearch? = null,
    workLocation: RecentSearch? = null,
    customFavorites: List<RecentSearch> = emptyList(),
    unifiedTransitFavorites: List<RecentSearch> = emptyList(),
    onSearchResultClick: (MapSearchResult) -> Unit,
    onClearRecentSearches: () -> Unit = {},
    onRemoveRecentSearch: (String) -> Unit = {},
    onElegirEnMapaClick: (() -> Unit)? = null,
    onSaveLocationShortcutClick: ((isHome: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visibleCustomFavorites = remember(customFavorites) { customFavorites.filter { it.showOnMap } }
    val topRecentSearches = remember(recentSearches) { recentSearches.take(8) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = com.example.ui.theme.AppThemeColors.cardBackground(isDarkMode),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .heightIn(max = 420.dp)
            .testTag("search_dropdown_card")
    ) {
        if (searchQuery.isNotEmpty()) {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSearching) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.search_searching_addresses),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    } else {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    items(
                        items = searchResults,
                        key = { result ->
                            when (result) {
                                is MapSearchResult.BusStop -> "bus_${result.stop.id_parada}"
                                is MapSearchResult.MetrobusStop -> "metrobus_${result.stop.id_parada}"
                                is MapSearchResult.Metro -> "metro_${result.station.id}"
                                is MapSearchResult.Cercanias -> "cercanias_${result.station.stop_id}"
                                is MapSearchResult.Address -> "addr_${result.result.latitude}_${result.result.longitude}_${result.result.displayName.hashCode()}"
                            }
                        }
                    ) { result ->
                        SearchResultRow(
                            result = result,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            onClick = { onSearchResultClick(result) }
                        )
                    }
                }
            }
        } else {
            // Zero-State Panel: Shortcuts + Recent Searches + Favorites
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                // 1. Shortcuts Block
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.search_quick_access),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                ShortcutPill(
                                    icon = Icons.Default.Home,
                                    label = if (homeLocation != null) {
                                        androidx.compose.ui.res.stringResource(com.example.R.string.search_home)
                                    } else {
                                        androidx.compose.ui.res.stringResource(com.example.R.string.search_configure_home)
                                    },
                                    onClick = {
                                        if (homeLocation != null) {
                                            val searchResult = recentSearchToSearchResult(homeLocation)
                                            onSearchResultClick(searchResult)
                                        } else {
                                            onSaveLocationShortcutClick?.invoke(true)
                                        }
                                    },
                                    isDarkMode = isDarkMode
                                )
                            }
                            item {
                                ShortcutPill(
                                    icon = Icons.Default.Work,
                                    label = if (workLocation != null) {
                                        androidx.compose.ui.res.stringResource(com.example.R.string.search_work)
                                    } else {
                                        androidx.compose.ui.res.stringResource(com.example.R.string.search_configure_work)
                                    },
                                    onClick = {
                                        if (workLocation != null) {
                                            val searchResult = recentSearchToSearchResult(workLocation)
                                            onSearchResultClick(searchResult)
                                        } else {
                                            onSaveLocationShortcutClick?.invoke(false)
                                        }
                                    },
                                    isDarkMode = isDarkMode
                                )
                            }
                            if (onElegirEnMapaClick != null) {
                                item {
                                    ShortcutPill(
                                        icon = Icons.Default.Map,
                                        label = androidx.compose.ui.res.stringResource(com.example.R.string.search_choose_on_map),
                                        onClick = onElegirEnMapaClick,
                                        isDarkMode = isDarkMode
                                    )
                                }
                            }
                            items(
                                items = visibleCustomFavorites,
                                key = { "custom_fav_${it.id}" }
                            ) { fav ->
                                val favColor = remember(fav.colorHex) {
                                    fav.colorHex?.let {
                                        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
                                    } ?: Color(0xFFF59E0B)
                                }
                                ShortcutPill(
                                    icon = Icons.Default.Star,
                                    iconTint = favColor,
                                    label = fav.title,
                                    onClick = {
                                        val searchResult = recentSearchToSearchResult(fav)
                                        onSearchResultClick(searchResult)
                                    },
                                    isDarkMode = isDarkMode
                                )
                            }
                        }
                    }
                }

                // 1.5 Recent Searches Block (Search History - Priority #1)
                if (recentSearches.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = androidx.compose.ui.res.stringResource(com.example.R.string.search_recent_title),
                            isDarkMode = isDarkMode,
                            actionLabel = androidx.compose.ui.res.stringResource(com.example.R.string.search_clear_all),
                            onActionClick = onClearRecentSearches
                        )
                    }
                    items(
                        items = topRecentSearches,
                        key = { "recent_${it.type}_${it.id}" }
                    ) { item ->
                        RecentSearchRow(
                            item = item,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            onItemClick = {
                                val searchResult = recentSearchToSearchResult(item)
                                onSearchResultClick(searchResult)
                            },
                            onDeleteClick = { onRemoveRecentSearch(item.id) }
                        )
                    }
                }

                // 2. Saved Favorite Places Block (Below History)
                if (customFavorites.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = androidx.compose.ui.res.stringResource(com.example.R.string.search_saved_favorites_title),
                            isDarkMode = isDarkMode
                        )
                    }
                    items(
                        items = customFavorites,
                        key = { "fav_saved_${it.id}_${it.latitude}_${it.longitude}" }
                    ) { fav ->
                        RecentSearchRow(
                            item = fav,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            onItemClick = {
                                val searchResult = recentSearchToSearchResult(fav)
                                onSearchResultClick(searchResult)
                            },
                            onDeleteClick = null
                        )
                    }
                }

                // 3. Favorite Transit Stops Block (Below History)
                if (unifiedTransitFavorites.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = androidx.compose.ui.res.stringResource(com.example.R.string.search_favorite_stops_title),
                            isDarkMode = isDarkMode
                        )
                    }
                    items(
                        items = unifiedTransitFavorites,
                        key = { "transit_fav_${it.type}_${it.id}" }
                    ) { item ->
                        RecentSearchRow(
                            item = item,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            onItemClick = {
                                val searchResult = recentSearchToSearchResult(item)
                                onSearchResultClick(searchResult)
                            },
                            onDeleteClick = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(
    result: MapSearchResult,
    isDarkMode: Boolean,
    appLanguage: AppLanguage = AppLanguage.CA,
    onClick: () -> Unit
) {
    val title = when (result) {
        is MapSearchResult.BusStop -> {
            val alias = result.alias
            if (!alias.isNullOrBlank()) alias else result.stop.denominacion
        }
        is MapSearchResult.MetrobusStop -> {
            val alias = result.alias
            if (!alias.isNullOrBlank()) alias else result.stop.denominacion
        }
        is MapSearchResult.Metro -> result.station.name
        is MapSearchResult.Cercanias -> result.station.displayName
        is MapSearchResult.Address -> {
            val isHome = result.result.type == "home" || result.result.placeName?.equals("Casa", ignoreCase = true) == true || result.customTitle?.equals("Casa", ignoreCase = true) == true
            val isWork = result.result.type == "work" || result.result.placeName?.equals("Trabajo", ignoreCase = true) == true || result.result.placeName?.equals("Feina", ignoreCase = true) == true || result.customTitle?.equals("Trabajo", ignoreCase = true) == true || result.customTitle?.equals("Feina", ignoreCase = true) == true
            val homeText = androidx.compose.ui.res.stringResource(com.example.R.string.search_home)
            val workText = androidx.compose.ui.res.stringResource(com.example.R.string.search_work)
            when {
                !result.customTitle.isNullOrBlank() -> result.customTitle
                isHome -> homeText
                isWork -> workText
                !result.result.placeName.isNullOrBlank() -> result.result.placeName
                else -> result.result.displayName.split(",").firstOrNull()?.trim() ?: result.result.displayName
            }
        }
    }

    val subtitle = when (result) {
        is MapSearchResult.BusStop -> {
            val alias = result.alias
            if (!alias.isNullOrBlank()) {
                "${result.stop.denominacion} • Parada ${result.stop.id_parada}"
            } else {
                "EMT • Parada ${result.stop.id_parada}"
            }
        }
        is MapSearchResult.MetrobusStop -> {
            val alias = result.alias
            if (!alias.isNullOrBlank()) {
                "${result.stop.denominacion} • Metrobús • Parada ${result.stop.id_parada}"
            } else {
                "Metrobús • Parada ${result.stop.id_parada}"
            }
        }
        is MapSearchResult.Metro -> {
            val z = com.example.data.model.cleanZoneCode(result.station.zone)
            if (z.isNotEmpty()) "Zona $z • Metrovalencia" else "Metrovalencia"
        }
        is MapSearchResult.Cercanias -> {
            androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_renfe_title)
        }
        is MapSearchResult.Address -> {
            val addr = result.result
            val isHome = addr.type == "home" || addr.placeName?.equals("Casa", ignoreCase = true) == true || result.customTitle?.equals("Casa", ignoreCase = true) == true
            val isWork = addr.type == "work" || addr.placeName?.equals("Trabajo", ignoreCase = true) == true || addr.placeName?.equals("Feina", ignoreCase = true) == true || result.customTitle?.equals("Trabajo", ignoreCase = true) == true || result.customTitle?.equals("Feina", ignoreCase = true) == true

            val road = addr.road
            val hn = if (!addr.houseNumber.isNullOrBlank()) " ${addr.houseNumber}" else ""
            val area = addr.suburb ?: addr.city

            val locationText = if (!road.isNullOrBlank()) {
                if (!area.isNullOrBlank() && !area.equals(road, ignoreCase = true)) {
                    "$road$hn • $area"
                } else {
                    "$road$hn"
                }
            } else {
                val parts = addr.displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.drop(1).take(2).joinToString(", ")
                } else {
                    ""
                }
            }

            val homeText = androidx.compose.ui.res.stringResource(com.example.R.string.search_home)
            val workText = androidx.compose.ui.res.stringResource(com.example.R.string.search_work)

            when {
                locationText.isNotBlank() -> locationText
                isHome -> homeText
                isWork -> workText
                else -> addr.placeCategory.getLabel(appLanguage)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (result) {
            is MapSearchResult.BusStop,
            is MapSearchResult.MetrobusStop,
            is MapSearchResult.Metro,
            is MapSearchResult.Cercanias -> {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                        when (result) {
                            is MapSearchResult.BusStop -> {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_emt_valencia),
                                    contentDescription = "EMT València",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            is MapSearchResult.MetrobusStop -> {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_metrobus),
                                    contentDescription = "Metrobús",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            is MapSearchResult.Metro -> {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_metrovalencia),
                                    contentDescription = "Metrovalencia",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            is MapSearchResult.Cercanias -> {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_cercanias),
                                    contentDescription = "Cercanías",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
            is MapSearchResult.Address -> {
                val isHome = result.result.type == "home" || result.result.placeName?.equals("Casa", ignoreCase = true) == true || result.customTitle?.equals("Casa", ignoreCase = true) == true
                val isWork = result.result.type == "work" || result.result.placeName?.equals("Trabajo", ignoreCase = true) == true || result.result.placeName?.equals("Feina", ignoreCase = true) == true || result.customTitle?.equals("Trabajo", ignoreCase = true) == true || result.customTitle?.equals("Feina", ignoreCase = true) == true
                val isFavAddr = isHome || isWork || result.isFavorite || result.result.placeCategory == PlaceCategory.FAVORITE ||
                        result.result.category == "favorite" || result.result.type == "favorite"

                if (isHome) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Casa",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else if (isWork) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Work,
                                contentDescription = "Trabajo",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else if (isFavAddr) {
                    val favColor = result.result.colorHex?.let {
                        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
                    } ?: Color(0xFFEF4444)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = favColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorito",
                                tint = favColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    val category = result.result.placeCategory
                    com.example.ui.map.components.PlaceCategoryIconBox(
                        category = category,
                        isDarkMode = isDarkMode,
                        boxSize = 36.dp,
                        iconSize = 18.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (result) {
                is MapSearchResult.BusStop -> {
                    if (result.isFavorite) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 1.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorita",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    val lines = (result.stop.lineas ?: "").split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    lines.take(3).forEach { line ->
                        LineBadge(text = line, bgColor = Color(0xFF64748B))
                    }
                    if (lines.size > 3) {
                        LineBadge(text = "+${lines.size - 3}", bgColor = Color(0xFF94A3B8))
                    }
                }
                is MapSearchResult.MetrobusStop -> {
                    if (result.isFavorite) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 1.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorita",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    val lines = (result.stop.lineas ?: "").split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    lines.take(3).forEach { line ->
                        LineBadge(text = line, bgColor = Color(0xFF64748B))
                    }
                    if (lines.size > 3) {
                        LineBadge(text = "+${lines.size - 3}", bgColor = Color(0xFF94A3B8))
                    }
                }
                is MapSearchResult.Metro -> {
                    if (result.isFavorite) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 1.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorita",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    result.station.lines.take(3).forEach { line ->
                        LineBadge(text = line, bgColor = com.example.util.LineColorResolver.getMetroLineColor(line))
                    }
                    if (result.station.lines.size > 3) {
                        LineBadge(text = "+${result.station.lines.size - 3}", bgColor = Color(0xFF94A3B8))
                    }
                }
                is MapSearchResult.Cercanias -> {
                    if (result.isFavorite) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 1.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorita",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    val lines = result.station.lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    lines.take(3).forEach { line ->
                        LineBadge(text = line, bgColor = com.example.util.LineColorResolver.getCercaniasLineColor(line))
                    }
                    if (lines.size > 3) {
                        LineBadge(text = "+${lines.size - 3}", bgColor = Color(0xFF94A3B8))
                    }
                }
                is MapSearchResult.Address -> {
                    val isHome = result.result.type == "home" || result.result.placeName?.equals("Casa", ignoreCase = true) == true
                    val isWork = result.result.type == "work" || result.result.placeName?.equals("Trabajo", ignoreCase = true) == true || result.result.placeName?.equals("Feina", ignoreCase = true) == true
                    if (isHome) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.search_home),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isWork) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.search_work),
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LineBadge(
    text: String,
    bgColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = Modifier.padding(horizontal = 1.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ShortcutPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDarkMode: Boolean,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("shortcut_pill_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    isDarkMode: Boolean,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun RecentSearchRow(
    item: RecentSearch,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onItemClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    val resolvedCategory = if (item.type == "favorite") {
        PlaceCategory.FAVORITE
    } else if (item.type == "address" || item.type == "place") {
        if (!item.categoryName.isNullOrBlank()) {
            try {
                PlaceCategory.valueOf(item.categoryName)
            } catch (e: Exception) {
                val parts = item.categoryType?.split(":")
                PlaceCategory.resolveFromOsm(parts?.getOrNull(0), parts?.getOrNull(1))
            }
        } else {
            val parts = item.categoryType?.split(":")
            PlaceCategory.resolveFromOsm(parts?.getOrNull(0), parts?.getOrNull(1))
        }
    } else {
        null
    }

    val iconBgColor = when (item.type) {
        "bus", "metro", "cercanias", "valenbisi" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        "favorite" -> {
            item.colorHex?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
            } ?: Color(0xFFF59E0B)
        }
        else -> resolvedCategory?.brandColor ?: Color(0xFF64748B)
    }

    val displayTitle = if (item.type == "bus" && item.title.startsWith("Parada ") && item.subtitle.isNotBlank() && !item.subtitle.startsWith("Parada ")) {
        item.subtitle
    } else {
        item.title
    }

    val displaySubtitle = if (item.type == "bus" && item.title.startsWith("Parada ") && item.subtitle.isNotBlank() && !item.subtitle.startsWith("Parada ")) {
        item.title
    } else {
        item.subtitle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onItemClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("recent_search_row_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconBgColor,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                when (item.type) {
                    "bus" -> {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.logo_emt_valencia),
                            contentDescription = "EMT Bus",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    "metro" -> {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.logo_metrovalencia),
                            contentDescription = "Metrovalencia",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    "cercanias" -> {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.logo_cercanias),
                            contentDescription = "Cercanías",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    "valenbisi" -> {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_bike),
                            contentDescription = "Valenbisi",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    "favorite" -> {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = resolvedCategory?.icon ?: Icons.Default.Place,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displaySubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onDeleteClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove recent search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Converts a RecentSearch item into a MapSearchResult for uniform dispatching.
 */
fun recentSearchToSearchResult(item: RecentSearch, score: Double = 1.0): MapSearchResult {
    return when (item.type) {
        "bus" -> {
            val den = if (item.title.startsWith("Parada ") && item.subtitle.isNotBlank() && !item.subtitle.startsWith("Parada ")) {
                item.subtitle
            } else if (item.title.startsWith("Parada ")) {
                item.title.replace("Parada ", "")
            } else {
                item.title
            }
            val geoportalStop = com.example.data.database.GeoportalStopEntity(
                id_parada = item.id,
                denominacion = den,
                suprimida = 0,
                lat = item.latitude,
                lon = item.longitude,
                lineas = item.extraData ?: ""
            )
            MapSearchResult.BusStop(geoportalStop, null, score)
        }
        "metrobus" -> {
            val metrobusStop = com.example.data.database.MetrobusStopEntity(
                id_parada = item.id,
                denominacion = item.title,
                lat = item.latitude,
                lon = item.longitude,
                lineas = item.extraData ?: ""
            )
            MapSearchResult.MetrobusStop(metrobusStop, null, score)
        }
        "metro" -> {
            val resolvedStation = com.example.data.model.ValenciaMetroData.findStation(item.id)
                ?: com.example.data.model.ValenciaMetroData.findStation(item.title)
            val z = resolvedStation?.zone ?: com.example.data.model.cleanZoneCode(item.subtitle)
            val linesList = if (resolvedStation != null && resolvedStation.lines.isNotEmpty()) {
                resolvedStation.lines
            } else {
                item.extraData?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            }
            val metroStation = com.example.data.model.MetroStation(
                id = item.id,
                name = item.title,
                lines = linesList,
                latitude = if (resolvedStation != null && resolvedStation.latitude != 0.0) resolvedStation.latitude else item.latitude,
                longitude = if (resolvedStation != null && resolvedStation.longitude != 0.0) resolvedStation.longitude else item.longitude,
                zone = z
            )
            MapSearchResult.Metro(metroStation, score, isFavorite = true)
        }
        "cercanias" -> {
            val cercaniasStation = com.example.data.database.CercaniasStationEntity(
                stop_id = item.id,
                nombre = item.title,
                lat = item.latitude,
                lon = item.longitude
            )
            MapSearchResult.Cercanias(cercaniasStation, score, isFavorite = true)
        }
        else -> {
            val isHome = item.type == "home" || item.id == "home_location" || item.title.equals("Casa", ignoreCase = true)
            val isWork = item.type == "work" || item.id == "work_location" || item.title.equals("Trabajo", ignoreCase = true) || item.title.equals("Feina", ignoreCase = true)
            val isFav = item.type == "favorite" || isHome || isWork

            val resolvedType = when {
                isHome -> "home"
                isWork -> "work"
                isFav -> "favorite"
                else -> item.categoryType?.split(":")?.getOrNull(1) ?: item.type
            }
            val resolvedCat = if (isFav) "favorite" else (item.categoryType?.split(":")?.getOrNull(0) ?: "place")

            val fullDisplayName = if (item.subtitle.isNotBlank() && item.subtitle != "Dirección" && item.subtitle != "Ubicación" && item.subtitle != "València") {
                "${item.title}, ${item.subtitle}"
            } else {
                item.title
            }

            val resolvedPlaceCategory = if (isFav) {
                PlaceCategory.FAVORITE
            } else if (!item.categoryName.isNullOrBlank()) {
                try {
                    PlaceCategory.valueOf(item.categoryName)
                } catch (e: Exception) {
                    PlaceCategory.resolveFromOsm(resolvedCat, resolvedType)
                }
            } else {
                PlaceCategory.resolveFromOsm(resolvedCat, resolvedType)
            }

            val nomResult = NominatimResult(
                displayName = fullDisplayName,
                latitude = item.latitude,
                longitude = item.longitude,
                type = resolvedType,
                category = resolvedCat,
                isLocalStop = false,
                placeCategory = resolvedPlaceCategory,
                placeName = if (isFav) item.title else (item.placeName ?: item.title),
                road = item.road,
                houseNumber = item.houseNumber,
                suburb = item.suburb,
                city = item.city,
                postcode = item.postcode,
                openingHours = item.openingHours,
                wheelchair = item.wheelchair,
                brand = item.brand,
                operator = item.operator,
                phone = item.phone,
                email = item.email,
                website = item.website,
                wikipedia = item.wikipedia,
                wikidata = item.wikidata,
                fee = item.fee,
                charge = item.charge,
                startDate = item.startDate,
                historicType = item.historicType,
                colorHex = item.colorHex,
                showOnMap = item.showOnMap
            )
            MapSearchResult.Address(
                result = nomResult,
                score = score,
                isFavorite = isFav,
                customTitle = if (isFav) item.title else null
            )
        }
    }
}

/**
 * Converts a MapSearchResult into a PlannerLocation for Route Planning.
 */
fun mapSearchResultToPlannerLocation(result: MapSearchResult, appLanguage: AppLanguage = AppLanguage.CA): PlannerLocation {
    return when (result) {
        is MapSearchResult.BusStop -> {
            val alias = result.alias
            val displayTitle = if (!alias.isNullOrBlank()) alias else result.stop.denominacion
            val displaySubtitle = if (!alias.isNullOrBlank()) "${result.stop.denominacion} • Parada ${result.stop.id_parada}" else "Parada ${result.stop.id_parada}"
            PlannerLocation(
                title = displayTitle,
                subtitle = displaySubtitle,
                latitude = result.stop.lat,
                longitude = result.stop.lon,
                stopId = result.stop.id_parada,
                stopType = "bus"
            )
        }
        is MapSearchResult.MetrobusStop -> {
            val alias = result.alias
            val displayTitle = if (!alias.isNullOrBlank()) alias else result.stop.denominacion
            val displaySubtitle = if (!alias.isNullOrBlank()) "${result.stop.denominacion} • Metrobús • Parada ${result.stop.id_parada}" else "Metrobús • Parada ${result.stop.id_parada}"
            PlannerLocation(
                title = displayTitle,
                subtitle = displaySubtitle,
                latitude = result.stop.lat,
                longitude = result.stop.lon,
                stopId = result.stop.id_parada,
                stopType = "metrobus"
            )
        }
        is MapSearchResult.Metro -> {
            val z = com.example.data.model.cleanZoneCode(result.station.zone)
            PlannerLocation(
                title = result.station.name,
                subtitle = "Zona $z • Metrovalencia",
                latitude = result.station.latitude,
                longitude = result.station.longitude,
                stopId = result.station.id,
                stopType = "metro"
            )
        }
        is MapSearchResult.Cercanias -> {
            PlannerLocation(
                title = result.station.displayName,
                subtitle = if (appLanguage == AppLanguage.CA) "Rodalia Renfe" else "Cercanías Renfe",
                latitude = result.station.lat,
                longitude = result.station.lon,
                stopId = result.station.stop_id,
                stopType = "cercanias"
            )
        }
        is MapSearchResult.Address -> {
            val isHome = result.result.type == "home" || result.result.placeName?.equals("Casa", ignoreCase = true) == true
            val isWork = result.result.type == "work" || result.result.placeName?.equals("Trabajo", ignoreCase = true) == true || result.result.placeName?.equals("Feina", ignoreCase = true) == true
            val isFav = result.isFavorite || isHome || isWork || result.result.placeCategory == PlaceCategory.FAVORITE || result.result.category == "favorite" || result.result.type == "favorite"

            val homeTitle = if (appLanguage == AppLanguage.CA) "Casa" else "Casa"
            val workTitle = if (appLanguage == AppLanguage.CA) "Feina" else "Trabajo"

            val mainTitle = when {
                !result.customTitle.isNullOrBlank() -> result.customTitle
                isHome -> homeTitle
                isWork -> workTitle
                !result.result.placeName.isNullOrBlank() -> result.result.placeName
                else -> result.result.displayName.split(",").firstOrNull()?.trim() ?: result.result.displayName
            }
            val secondary = if (!result.result.road.isNullOrBlank()) {
                val hn = if (!result.result.houseNumber.isNullOrBlank()) " ${result.result.houseNumber}" else ""
                val area = result.result.suburb ?: result.result.city ?: ""
                if (area.isNotEmpty() && !area.equals(result.result.road, ignoreCase = true)) {
                    "${result.result.road}$hn • $area"
                } else {
                    "${result.result.road}$hn"
                }
            } else {
                result.result.displayName.split(",").drop(1).take(2).joinToString(", ").trim()
            }
            val displaySubtitle = if (secondary.isNotEmpty()) {
                secondary
            } else {
                when {
                    isHome -> homeTitle
                    isWork -> workTitle
                    else -> "València"
                }
            }
            PlannerLocation(
                title = mainTitle,
                subtitle = displaySubtitle,
                latitude = result.result.latitude,
                longitude = result.result.longitude,
                stopId = result.result.stopId,
                stopType = result.result.stopType
            )
        }
    }
}
