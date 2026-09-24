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
                                text = if (appLanguage == AppLanguage.CA) "Cercant adreces i parades..." else "Buscando direcciones y paradas...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    } else {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "No s'han trobat resultats" else "No se encontraron resultados",
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
                            text = if (appLanguage == AppLanguage.CA) "ACCESOS RÀPIDS" else "ACCESOS RÁPIDOS",
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
                                    label = if (homeLocation != null) "Casa" else (if (appLanguage == AppLanguage.CA) "Configurar Casa" else "Configurar Casa"),
                                    onClick = {
                                        if (homeLocation != null) {
                                            val nominatimResult = NominatimResult(
                                                displayName = homeLocation.subtitle.ifEmpty { homeLocation.title },
                                                latitude = homeLocation.latitude,
                                                longitude = homeLocation.longitude,
                                                type = "address",
                                                category = "place"
                                            )
                                            onSearchResultClick(MapSearchResult.Address(nominatimResult, 1.0))
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
                                    label = if (workLocation != null) (if (appLanguage == AppLanguage.CA) "Feina" else "Trabajo") else (if (appLanguage == AppLanguage.CA) "Configurar Feina" else "Configurar Trabajo"),
                                    onClick = {
                                        if (workLocation != null) {
                                            val nominatimResult = NominatimResult(
                                                displayName = workLocation.subtitle.ifEmpty { workLocation.title },
                                                latitude = workLocation.latitude,
                                                longitude = workLocation.longitude,
                                                type = "address",
                                                category = "place"
                                            )
                                            onSearchResultClick(MapSearchResult.Address(nominatimResult, 1.0))
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
                                        label = if (appLanguage == AppLanguage.CA) "Triar al mapa" else "Elegir en el mapa",
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
                                        val nominatimResult = NominatimResult(
                                            displayName = if (fav.subtitle.isNotEmpty()) "${fav.title}, ${fav.subtitle}" else fav.title,
                                            latitude = fav.latitude,
                                            longitude = fav.longitude,
                                            type = "favorite",
                                            category = "favorite"
                                        )
                                        onSearchResultClick(MapSearchResult.Address(nominatimResult, 1.0))
                                    },
                                    isDarkMode = isDarkMode
                                )
                            }
                        }
                    }
                }

                // 2. Recent Searches Block
                if (recentSearches.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = if (appLanguage == AppLanguage.CA) "Cerques recents" else "Búsquedas recientes",
                            isDarkMode = isDarkMode,
                            actionLabel = if (appLanguage == AppLanguage.CA) "Esborrar" else "Borrar todo",
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

                // 3. Favorite Transit Stops Block
                if (unifiedTransitFavorites.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = if (appLanguage == AppLanguage.CA) "Estacions i parades preferides" else "Estaciones y paradas favoritas",
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
            val pName = result.result.placeName
            if (!pName.isNullOrBlank()) {
                pName
            } else {
                result.result.displayName.split(",").firstOrNull()?.trim() ?: result.result.displayName
            }
        }
    }

    val subtitle = when (result) {
        is MapSearchResult.BusStop -> {
            val alias = result.alias
            if (!alias.isNullOrBlank()) {
                "${result.stop.denominacion} • Parada ${result.stop.id_parada}"
            } else {
                "Parada ${result.stop.id_parada}"
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
            if (z.isNotEmpty()) "Zona $z" else "Estación Metro"
        }
        is MapSearchResult.Cercanias -> "Estación Renfe"
        is MapSearchResult.Address -> {
            val addr = result.result
            val road = addr.road
            val hn = if (!addr.houseNumber.isNullOrBlank()) " ${addr.houseNumber}" else ""
            val area = addr.suburb ?: addr.city

            if (!road.isNullOrBlank()) {
                if (!area.isNullOrBlank() && !area.equals(road, ignoreCase = true)) {
                    "$road$hn • $area"
                } else {
                    "$road$hn"
                }
            } else {
                val parts = result.result.displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) parts.drop(1).take(2).joinToString(", ") else result.result.placeCategory.getLabel(appLanguage)
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
                val isFavAddr = result.result.placeCategory == com.example.data.model.PlaceCategory.FAVORITE ||
                        result.result.category == "favorite" || result.result.type == "favorite"
                val category = if (isFavAddr) com.example.data.model.PlaceCategory.FAVORITE else result.result.placeCategory
                com.example.ui.map.components.PlaceCategoryIconBox(
                    category = category,
                    isDarkMode = isDarkMode,
                    boxSize = 36.dp,
                    iconSize = 18.dp
                )
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
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (result) {
                is MapSearchResult.BusStop -> {
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
                    result.station.lines.take(3).forEach { line ->
                        LineBadge(text = line, bgColor = com.example.util.LineColorResolver.getMetroLineColor(line))
                    }
                    if (result.station.lines.size > 3) {
                        LineBadge(text = "+${result.station.lines.size - 3}", bgColor = Color(0xFF94A3B8))
                    }
                }
                is MapSearchResult.Cercanias -> {
                    val lines = result.station.lines.split(",")
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
                    // Category icon is already shown on the left leading icon box with dedicated color
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
fun recentSearchToSearchResult(item: RecentSearch): MapSearchResult {
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
            MapSearchResult.BusStop(geoportalStop, null, 1.0)
        }
        "metrobus" -> {
            val metrobusStop = com.example.data.database.MetrobusStopEntity(
                id_parada = item.id,
                denominacion = item.title,
                lat = item.latitude,
                lon = item.longitude,
                lineas = item.extraData ?: ""
            )
            MapSearchResult.MetrobusStop(metrobusStop, null, 1.0)
        }
        "metro" -> {
            val z = com.example.data.model.cleanZoneCode(item.subtitle)
            val metroStation = com.example.data.model.MetroStation(
                id = item.id,
                name = item.title,
                lines = item.extraData?.split(",") ?: emptyList(),
                latitude = item.latitude,
                longitude = item.longitude,
                zone = z
            )
            MapSearchResult.Metro(metroStation, 1.0)
        }
        "cercanias" -> {
            val cercaniasStation = com.example.data.database.CercaniasStationEntity(
                stop_id = item.id,
                nombre = item.title,
                lat = item.latitude,
                lon = item.longitude
            )
            MapSearchResult.Cercanias(cercaniasStation, 1.0)
        }
        else -> {
            val isFav = item.type == "favorite"
            val fullDisplayName = if (item.subtitle.isNotBlank() && item.subtitle != "Dirección" && item.subtitle != "Ubicación" && item.subtitle != "València") {
                "${item.title}, ${item.subtitle}"
            } else {
                item.title
            }
            val parts = item.categoryType?.split(":")
            val resolvedCat = parts?.getOrNull(0) ?: (if (isFav) "favorite" else "place")
            val resolvedType = parts?.getOrNull(1) ?: (if (isFav) "favorite" else item.type)

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
                display_name = fullDisplayName,
                lat = item.latitude.toString(),
                lon = item.longitude.toString(),
                type = resolvedType,
                category = resolvedCat,
                isLocalStop = false,
                placeCategory = resolvedPlaceCategory,
                placeName = item.placeName ?: item.title,
                road = item.road ?: "",
                houseNumber = item.houseNumber ?: "",
                suburb = item.suburb ?: "",
                city = item.city ?: "",
                postcode = item.postcode ?: "",
                openingHours = item.openingHours ?: "",
                wheelchair = item.wheelchair ?: "",
                brand = item.brand ?: "",
                operator = item.operator ?: "",
                phone = item.phone ?: "",
                email = item.email ?: "",
                website = item.website ?: "",
                wikipedia = item.wikipedia ?: "",
                wikidata = item.wikidata ?: "",
                fee = item.fee ?: "",
                charge = item.charge ?: "",
                startDate = item.startDate ?: "",
                historicType = item.historicType ?: ""
            )
            MapSearchResult.Address(nomResult, 1.0)
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
            PlannerLocation(
                title = result.station.name,
                subtitle = "Metrovalencia",
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
            val mainTitle = result.result.placeName ?: (result.result.displayName.split(",").firstOrNull()?.trim() ?: result.result.displayName)
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
            PlannerLocation(
                title = mainTitle,
                subtitle = secondary.ifEmpty { "València" },
                latitude = result.result.latitude,
                longitude = result.result.longitude,
                stopId = result.result.stopId,
                stopType = result.result.stopType
            )
        }
    }
}
