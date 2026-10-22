package com.example.ui.bus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.SpaceGroteskFontFamily
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage

val MetrobusAmber = com.example.util.MetrobusLineColorResolver.BRAND_COLOR

@Composable
fun MetrobusTab(
    appLanguage: AppLanguage,
    metrobusStopsList: List<MetrobusStop>,
    favoriteMetrobusStops: Set<String>,
    favoriteMetrobusStopsList: List<MetrobusStop>,
    metrobusStopAliases: Map<String, String>,
    searchQuery: String,
    isLoading: Boolean,
    isDarkMode: Boolean,
    listState: LazyListState,
    onSearchQueryChanged: (String) -> Unit,
    onSelectStop: (MetrobusStop) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditAliasClick: (MetrobusStop) -> Unit,
    activeTripBottomPadding: Dp
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("metrobus_tab_screen")
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("metrobus_search_bar"),
            placeholder = {
                Text(
                    text = stringResource(com.example.R.string.metrobus_search_placeholder),
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
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            ),
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface,
                focusedBorderColor = MetrobusAmber,
                unfocusedBorderColor = if (isDarkMode) Color(0xFF262C3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        )

        // Section 1: FAVORITAS GUARDADAS
        if (searchQuery.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(com.example.R.string.metrobus_section_favorites),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MetrobusAmber
                )
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .testTag("favorite_metrobus_stops_carousel"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (favoriteMetrobusStopsList.isEmpty()) {
                    item {
                        EmptyFavoriteMetrobusCard(isDarkMode = isDarkMode)
                    }
                } else {
                    items(favoriteMetrobusStopsList, key = { "fav_mb_${it.idParada}" }) { stop ->
                        val alias = metrobusStopAliases[stop.idParada]
                        FavoriteMetrobusStopCard(
                            stop = stop,
                            alias = alias,
                            isDarkMode = isDarkMode,
                            onCardClick = { onSelectStop(stop) }
                        )
                    }
                }
            }
        }

        // Section 2 Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sectionTitle = if (searchQuery.isNotEmpty()) {
                stringResource(com.example.R.string.metrobus_section_search_results)
            } else {
                stringResource(com.example.R.string.metrobus_section_nearby)
            }
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MetrobusAmber
            )
        }

        // Section 2 List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoading && metrobusStopsList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(4) {
                        SkeletonCardItem()
                    }
                }
            } else if (metrobusStopsList.isEmpty()) {
                val emptyMsg = if (searchQuery.isNotEmpty()) {
                    stringResource(com.example.R.string.metrobus_search_not_found, searchQuery)
                } else {
                    stringResource(com.example.R.string.metrobus_no_active_stops)
                }
                EmptyStateCard(
                    title = stringResource(com.example.R.string.metrobus_no_stops_title),
                    message = emptyMsg,
                    icon = Icons.Default.DirectionsBus,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp + activeTripBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    state = listState
                ) {
                    items(metrobusStopsList, key = { it.idParada }) { stop ->
                        val isFav = favoriteMetrobusStops.contains(stop.idParada)
                        val alias = metrobusStopAliases[stop.idParada]
                        MetrobusStopCard(
                            stop = stop,
                            isFav = isFav,
                            alias = alias,
                            isDarkMode = isDarkMode,
                            onCardClick = { onSelectStop(stop) },
                            onToggleFavorite = { onToggleFavorite(stop.idParada) },
                            onEditAliasClick = { onEditAliasClick(stop) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteMetrobusStopCard(
    stop: MetrobusStop,
    alias: String?,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit
) {
    val cardBg = if (isDarkMode) Color(0xFF2B1F0F) else Color(0xFFFFF8E0)
    val cardBorder = BorderStroke(1.dp, MetrobusAmber.copy(alpha = if (isDarkMode) 0.65f else 0.45f))
    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
    val distFormatted = if (!stop.distanceText.isNullOrEmpty()) " • ${stop.distanceText}" else ""

    Card(
        onClick = onCardClick,
        modifier = modifier
            .width(170.dp)
            .height(92.dp)
            .testTag("fav_pill_mb_${stop.idParada}"),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                if (!alias.isNullOrBlank()) {
                    Text(
                        text = alias,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = SpaceGroteskFontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = buildFormattedStopName(stop.denominacion, cardTextColor, cardTextSecondaryColor),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "Parada #${stop.idParada}$distFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = cardTextSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                stop.lineas.forEach { line ->
                    Surface(
                        color = MetrobusAmber,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 24.dp, minHeight = 18.dp)
                            .height(18.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFavoriteMetrobusCard(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(250.dp)
            .height(92.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF262C3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.StarBorder,
                contentDescription = null,
                tint = MetrobusAmber,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = stringResource(com.example.R.string.metrobus_empty_favorites_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(com.example.R.string.metrobus_empty_favorites_hint),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetrobusStopCard(
    stop: MetrobusStop,
    isFav: Boolean,
    alias: String?,
    isDarkMode: Boolean,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditAliasClick: (() -> Unit)? = null
) {
    val cardBg = if (isFav) {
        if (isDarkMode) Color(0xFF2B1F0F) else Color(0xFFFFF8E0)
    } else {
        if (isDarkMode) Color(0xFF151821) else MaterialTheme.colorScheme.surface
    }
    val cardBorder = if (isFav) {
        BorderStroke(1.dp, MetrobusAmber.copy(alpha = if (isDarkMode) 0.65f else 0.45f))
    } else {
        BorderStroke(1.dp, if (isDarkMode) Color(0xFF2E3545) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    val cardTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metrobus_stop_card_${stop.idParada}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = alias,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = stop.denominacion,
                            style = MaterialTheme.typography.bodySmall,
                            color = cardTextSecondaryColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = buildFormattedStopName(
                                rawName = stop.denominacion,
                                primaryColor = cardTextColor,
                                secondaryColor = cardTextSecondaryColor
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = SpaceGroteskFontFamily,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Parada ${stop.idParada}${if (!stop.distanceText.isNullOrEmpty()) " • ${stop.distanceText}" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardTextSecondaryColor
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isDarkMode) Color(0xFF0F131E) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("fav_metrobus_${stop.idParada}").fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFav) Color(0xFFFFB300) else cardTextSecondaryColor
                        )
                    }
                }
            }

            if (stop.lineas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    color = if (isDarkMode) Color(0x1F8791A6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    stop.lineas.forEach { lineCode ->
                        Surface(
                            color = MetrobusAmber,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 26.dp)
                                .height(26.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = lineCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
