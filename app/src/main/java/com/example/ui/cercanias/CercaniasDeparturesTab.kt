package com.example.ui.cercanias

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.components.CercaniasDepartureSkeletonCard
import com.example.ui.components.TransitPullRefreshIndicator
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation

private val TIME_REGEX = Regex("""\b(\d{1,2})[:.](\d{2})\s*h?\b""")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CercaniasDeparturesTab(
    viewModel: CercaniasViewModel,
    selectedStationId: String,
    departures: List<CercaniasDeparture>,
    isLoading: Boolean,
    error: String?,
    favoriteStations: List<CercaniasStationEntity>,
    allCercaniasStations: List<CercaniasStationEntity>,
    cercaniasAlerts: List<CercaniasAlert>,
    accessibilityAlerts: List<CercaniasAlert>,
    isOnline: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    texts: Translation,
    activeTripBottomPadding: Dp = 0.dp,
    onOpenSearchDialog: () -> Unit,
    onSelectDeparture: (CercaniasDeparture) -> Unit
) {
    var showQuickPicker by remember { mutableStateOf(false) }

    val displayStations = remember(favoriteStations, selectedStationId, allCercaniasStations) {
        if (selectedStationId.isNotEmpty() && favoriteStations.none { it.id == selectedStationId || it.stop_id == selectedStationId }) {
            val tempStation = allCercaniasStations.find { it.id == selectedStationId || it.stop_id == selectedStationId }
            if (tempStation != null) {
                listOf(tempStation) + favoriteStations
            } else {
                favoriteStations
            }
        } else {
            favoriteStations
        }
    }

    val selectedStationEntity = remember(allCercaniasStations, selectedStationId) {
        allCercaniasStations.find { it.stop_id == selectedStationId || it.id == selectedStationId }
    }
    val stationDisplayName = selectedStationEntity?.displayName ?: selectedStationEntity?.nombre ?: "Estación"

    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary

    if (showQuickPicker) {
        CercaniasQuickStationPickerDialog(
            viewModel = viewModel,
            onDismiss = { showQuickPicker = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Favoritos Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (appLanguage == AppLanguage.CA) "Les meues estacions favorites" else "Mis estaciones favoritas",
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onOpenSearchDialog,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Editar favoritas",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Horizontal Row of Favorite Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                val cardBgColor = if (isDarkMode) Color(0xFF222222) else MaterialTheme.colorScheme.surface
                Card(
                    modifier = Modifier
                        .width(48.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showQuickPicker = true }
                        .testTag("square_cercanias_picker_button"),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Cercar estació" else "Buscar estación",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (displayStations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showQuickPicker = true }
                            .testTag("empty_favorite_cercanias_chip"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = texts.searchPlaceholder,
                                fontSize = 12.sp,
                                color = subtextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                items(displayStations, key = { it.id }) { station ->
                    val isSelected = station.stop_id == selectedStationId || station.id == selectedStationId
                    val cardBgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    val borderStroke = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    val animatedCornerRadius by animateDpAsState(
                        targetValue = if (isSelected) 8.dp else 18.dp,
                        animationSpec = tween(durationMillis = 500),
                        label = "fav_cercanias_selector_corner"
                    )

                    Card(
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 160.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(animatedCornerRadius))
                            .clickable { viewModel.selectCercaniasStation(station.stop_id.ifBlank { station.id }) }
                            .testTag("favorite_cercanias_chip_${station.id}"),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = borderStroke,
                        shape = RoundedCornerShape(animatedCornerRadius)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = station.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else textColor,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                val distanceText = viewModel.getCercaniasStationDistanceText(station)
                                if (distanceText != null) {
                                    Text(
                                        text = distanceText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) (if (isDarkMode) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary) else subtextColor,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val linesList = station.lines.filter { it.isNotBlank() }
                                linesList.forEach { line ->
                                    val colorHex = when (line) {
                                        "C1" -> "#00A3E0"
                                        "C2" -> "#FF6A00"
                                        "C3" -> "#7A287B"
                                        "C4" -> "#E52321"
                                        "C5" -> "#009639"
                                        "C6" -> "#002F6C"
                                        else -> "#7F8C8D"
                                    }
                                    Surface(
                                        color = Color(android.graphics.Color.parseColor(colorHex)),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(14.dp)
                                    ) {
                                        Text(
                                            text = line,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pull to refresh + List of Departures
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.fetchCercaniasDepartures() },
            state = pullToRefreshState,
            indicator = {
                TransitPullRefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isLoading,
                    stationName = stationDisplayName,
                    operatorName = "Renfe Cercanías",
                    appLanguage = appLanguage,
                    isInitialLoad = departures.isEmpty()
                )
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (isLoading && departures.isEmpty() && error == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(5) {
                        CercaniasDepartureSkeletonCard()
                    }
                }
            } else if (error != null && departures.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.fetchCercaniasDepartures() },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (appLanguage == AppLanguage.CA) "Reintentar" else "Reintentar")
                    }
                }
            } else if (departures.isEmpty() && !isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRailway,
                        contentDescription = null,
                        tint = subtextColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "No hi ha eixides pròximes per a esta estació" else "No hay salidas próximas para esta estación",
                        color = subtextColor,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp + activeTripBottomPadding)
                ) {
                    items(departures, key = { "${it.tripId}_${it.routeId}_${it.departureTime}" }) { departure ->
                        CercaniasDepartureCard(
                            departure = departure,
                            alerts = cercaniasAlerts,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            onClick = { onSelectDeparture(departure) }
                        )
                    }
                }
            }
        }
    }
}
