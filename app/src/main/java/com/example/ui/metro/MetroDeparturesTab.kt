package com.example.ui.metro
import com.example.ui.components.SkeletonCardItem
import com.example.ui.components.EmptyStateCard
import androidx.compose.material3.HorizontalDivider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ValenciaMetroData
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation
import com.example.ui.theme.LiveTimerStyle
import com.example.ui.theme.UnifiedAppCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroDeparturesTab(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    favoriteStations: List<String>,
    selectedStationId: String,
    departures: List<RealTimeDeparture>,
    isLoading: Boolean,
    error: String?,
    isDarkMode: Boolean
) {
    ProximosTrenesScreen(
        appLanguage = appLanguage,
        metroViewModel = metroViewModel,
        favoriteStations = favoriteStations,
        selectedStationId = selectedStationId,
        departures = departures,
        isLoading = isLoading,
        error = error,
        isDarkMode = isDarkMode
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProximosTrenesScreen(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    favoriteStations: List<String>,
    selectedStationId: String,
    departures: List<RealTimeDeparture>,
    isLoading: Boolean,
    error: String?,
    isDarkMode: Boolean
) {
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val isBottomSheetVisible by metroViewModel.isBottomSheetVisible.collectAsState()
    val selectedDepartureDetails by metroViewModel.selectedDepartureForDetails.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val lazyListState = rememberLazyListState()
    LaunchedEffect(selectedStationId) {
        lazyListState.scrollToItem(0)
    }
    val scope = rememberCoroutineScope()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickPicker by remember { mutableStateOf(false) }
    var expiredDepartureIds by remember(departures) { mutableStateOf(setOf<String>()) }
    val visibleDepartures = remember(departures, expiredDepartureIds) {
        departures.filter { it.id !in expiredDepartureIds }
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (showSearchDialog) {
        MetroStationSelectionDialog(
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            metroViewModel = metroViewModel,
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showQuickPicker) {
        MetroQuickStationPickerDialog(
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            metroViewModel = metroViewModel,
            onDismiss = { showQuickPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("proximos_trenes_screen")
    ) {
        // Mis estaciones favoritas - Cercanías unified style with Search button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = texts.favoritesTitle,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { showSearchDialog = true },
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

        FavoriteStationsRow(
            favoriteStations = favoriteStations,
            selectedStationId = selectedStationId,
            metroViewModel = metroViewModel,
            isDarkMode = isDarkMode,
            accentColor = accentColor,
            subtextColor = subtextColor,
            texts = texts,
            onSearchClick = { showQuickPicker = true }
        )

        // 2. LIVE SCHEDULES CONTAINER ("pastillas" cards)
        val selectedStation = metroViewModel.getStationInfo(selectedStationId)
        val stationDisplayName = selectedStation?.name ?: "Selecciona una estación"
        val isStationInfoExpanded by metroViewModel.isStationInfoExpanded.collectAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stationDisplayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .testTag("proximas_salidas_header_text")
            )
            IconButton(
                onClick = { 
                    metroViewModel.toggleStationInfoExpanded() 
                    scope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("toggle_station_info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Información de la estación",
                    tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { metroViewModel.fetchRealTimeDepartures(selectedStationId) },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading && visibleDepartures.isEmpty() && error == null && pullToRefreshState.distanceFraction == 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(4) {
                        SkeletonCardItem()
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("realtime_departures_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SelectedStationInfoCard(
                                isStationInfoExpanded = isStationInfoExpanded,
                                selectedStation = selectedStation
                            )
                        }
                    }

                    if (error != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Error",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = error,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { metroViewModel.fetchRealTimeDepartures(selectedStationId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("retry_realtime_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reintentar", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    
                                    val context = LocalContext.current
                                    Button(
                                        onClick = {
                                            val stationName = selectedStation?.name ?: "Estación"
                                            val uriString = "https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode("$stationName Metrovalencia")}"
                                            try {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(uriString)
                                                )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "No se pudo abrir Google Maps", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("open_maps_error_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ver en Maps", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    } else if (visibleDepartures.isEmpty()) {
                        item {
                            val context = LocalContext.current
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp, horizontal = 20.dp)
                                    .testTag("no_realtime_departures_view"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = texts.noLiveDepartures,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = texts.noLiveDepartures,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = texts.noLiveDeparturesDesc,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Button(
                                    onClick = {
                                        val stationName = selectedStation?.name ?: "Estación"
                                        val uriString = "https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode("$stationName Metrovalencia")}"
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(uriString)
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "No se pudo abrir Google Maps", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .testTag("open_google_maps_departures_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Ver salidas en Google Maps", fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        items(visibleDepartures, key = { it.id }) { departure ->
                            DepartureListItem(
                                departure = departure,
                                metroViewModel = metroViewModel,
                                appLanguage = appLanguage,
                                texts = texts,
                                isDarkMode = isDarkMode,
                                onExpired = { expiredId ->
                                    expiredDepartureIds = expiredDepartureIds + expiredId
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    DepartureDetailsBottomSheet(
        isBottomSheetVisible = isBottomSheetVisible,
        selectedDepartureDetails = selectedDepartureDetails,
        metroViewModel = metroViewModel,
        appLanguage = appLanguage,
        texts = texts,
        isDarkMode = isDarkMode,
        sheetState = sheetState,
        onDismiss = { metroViewModel.dismissDepartureDetails() }
    )
    }
