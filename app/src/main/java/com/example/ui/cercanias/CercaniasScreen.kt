package com.example.ui.cercanias

import androidx.activity.compose.BackHandler
import com.example.ui.components.CercaniasDepartureSkeletonCard
import com.example.ui.components.LinkifiedText
import com.example.ui.components.SkeletonCardItem
import com.example.ui.components.TransitPullRefreshIndicator
import com.example.ui.theme.appCardBorder

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.util.StationAccessibilityHelper
import com.example.ui.theme.ScreenHeader
import com.example.ui.theme.UnifiedTabRow
import com.example.ui.theme.UnifiedAppCard
import com.example.ui.theme.LiveTimerStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CercaniasScreen(
    viewModel: CercaniasViewModel,
    isDarkMode: Boolean,
    initialPage: Int = 0,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onBackGesture: (() -> Unit)? = null,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val backHandlerAction = onBackClick ?: onBackGesture
    if (backHandlerAction != null) {
        BackHandler(enabled = true) {
            backHandlerAction()
        }
    }
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val selectedStationId by viewModel.cercaniasSelectedStationId.collectAsState()
    val departures by viewModel.cercaniasDepartures.collectAsState()
    val isLoading by viewModel.cercaniasLoading.collectAsState()
    val error by viewModel.cercaniasError.collectAsState()
    val favoriteStations by viewModel.cercaniasFavoriteStations.collectAsState()
    val isBottomSheetVisible by viewModel.isCercaniasBottomSheetVisible.collectAsState()
    val selectedDeparture by viewModel.selectedCercaniasDeparture.collectAsState()
    val cercaniasAlerts by viewModel.cercaniasAlerts.collectAsState()
    val isCercaniasAlertsLoading by viewModel.isCercaniasAlertsLoading.collectAsState()
    val activeAlerts by viewModel.activeCercaniasAlerts.collectAsState()
    val generalNotices by viewModel.generalCercaniasNotices.collectAsState()
    val accessibilityAlerts by viewModel.accessibilityCercaniasAlerts.collectAsState()
    val groupedAccessibilityAlerts by viewModel.groupedAccessibilityAlerts.collectAsState()
    var isAccessibilityExpanded by remember { mutableStateOf(false) }
    
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allCercaniasStations by viewModel.allCercaniasStations.collectAsState()

    val displayStations = remember(favoriteStations, selectedStationId, allCercaniasStations) {
        if (selectedStationId.isNotEmpty() && favoriteStations.none { it.id == selectedStationId }) {
            val tempStation = allCercaniasStations.find { it.id == selectedStationId }
            if (tempStation != null) {
                listOf(tempStation) + favoriteStations
            } else {
                favoriteStations
            }
        } else {
            favoriteStations
        }
    }
    
    val scope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    // Auto-select nearest station on load if no station is selected
    LaunchedEffect(selectedStationId, favoriteStations, allCercaniasStations) {
        if (selectedStationId.isBlank()) {
            viewModel.autoSelectNearestCercaniasStationIfNeeded()
        }
    }

    // Ciclo de vida: refresco adaptativo activo solo cuando la pantalla es visible (evita drenaje de batería en segundo plano)
    val context = androidx.compose.ui.platform.LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.fetchCercaniasDepartures()
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopCercaniasPolling()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopCercaniasPolling()
        }
    }

    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // TabRow Unificado (Material 3 con tipografía titleSmall y Capitalizado)
        UnifiedTabRow(
            selectedTabIndex = pagerState.currentPage,
            tabs = if (appLanguage == AppLanguage.CA) listOf("Eixides", "Avisos") else listOf("Salidas", "Avisos"),
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (showQuickPicker) {
                            CercaniasQuickStationPickerDialog(
                                viewModel = viewModel,
                                onDismiss = { showQuickPicker = false }
                            )
                        }

                        // 3. Favoritos en la Regla del Tercio Superior (Justo debajo de la cabecera/tabs)
                        Column {
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
                            
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Square Search Button
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
                                        val isSelected = station.id == selectedStationId
                                        val cardBgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        val borderStroke = if (isSelected) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            null
                                        }
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
                                                .clickable { viewModel.selectCercaniasStation(station.id) }
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
                                                val hasFavAccessibilityIssue = remember(station.id, station.nombre, station.displayName, accessibilityAlerts) {
                                                    accessibilityAlerts.any { alert ->
                                                        StationAccessibilityHelper.isCercaniasStationAffected(station.id, station.nombre, station.displayName, alert)
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
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
                                                        if (hasFavAccessibilityIssue) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.NotAccessible,
                                                                contentDescription = "No accesible",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
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
                                                
                                                // Líneas
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val linesList = station.lines.split(",").filter { it.isNotBlank() }
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
                                                        val lineText = if (line.matches(Regex("C\\d"))) "C-${line.substring(1)}" else line
                                                        Box(
                                                            modifier = Modifier
                                                                .height(14.dp)
                                                                .widthIn(min = 24.dp)
                                                                .clip(RoundedCornerShape(7.dp))
                                                                .background(Color(android.graphics.Color.parseColor(colorHex)))
                                                                .padding(horizontal = 4.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = lineText,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 9.sp,
                                                                textAlign = TextAlign.Center,
                                                                style = androidx.compose.ui.text.TextStyle(
                                                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Search Dialog for selecting any station and managing favorites
                        if (showSearchDialog) {
                            CercaniasStationSelectionDialog(
                                viewModel = viewModel,
                                onDismiss = { showSearchDialog = false }
                            )
                        }

                        // Título de la estación seleccionada
                        val selectedStationEntity = allCercaniasStations.find { it.stop_id == selectedStationId }
                        val stationName = selectedStationEntity?.nombre 
                            ?: favoriteStations.find { it.id == selectedStationId }?.displayName 
                            ?: if (appLanguage == AppLanguage.CA) "Selecciona una estació" else "Selecciona una estación"

                        val matchingCercaniasAccessibilityAlerts = remember(selectedStationEntity, stationName, accessibilityAlerts) {
                            if (selectedStationEntity == null && favoriteStations.none { it.id == selectedStationId }) {
                                emptyList()
                            } else {
                                val stopId = selectedStationEntity?.stop_id ?: selectedStationId
                                val rawNombre = selectedStationEntity?.nombre ?: stationName
                                accessibilityAlerts.filter { alert ->
                                    StationAccessibilityHelper.isCercaniasStationAffected(stopId, rawNombre, stationName, alert)
                                }
                            }
                        }
                        val hasCercaniasAccessibilityIssue = matchingCercaniasAccessibilityAlerts.isNotEmpty()
                        var showCercaniasAccessibilityDialog by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stationName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                if (selectedStationEntity != null || favoriteStations.any { it.id == selectedStationId }) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            if (hasCercaniasAccessibilityIssue) {
                                                showCercaniasAccessibilityDialog = true
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("cercanias_accessibility_icon_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (hasCercaniasAccessibilityIssue) Icons.Default.NotAccessible else Icons.Default.Accessible,
                                            contentDescription = if (hasCercaniasAccessibilityIssue) {
                                                if (appLanguage == AppLanguage.CA) "Incidència d'accessibilitat" else "Incidencia de accesibilidad"
                                            } else {
                                                if (appLanguage == AppLanguage.CA) "Estació accessible" else "Estación accesible"
                                            },
                                            tint = if (hasCercaniasAccessibilityIssue) Color(0xFFE53935) else (if (isDarkMode) Color(0xFF4CAF50) else Color(0xFF2E7D32)),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (showCercaniasAccessibilityDialog && hasCercaniasAccessibilityIssue) {
                            val rawAlerts = matchingCercaniasAccessibilityAlerts.map {
                                it.descriptionEs.ifBlank { it.headerEs }
                            }
                            com.example.ui.components.AccessibilityAlertsDialog(
                                stationName = stationName,
                                rawAlerts = rawAlerts,
                                appLanguage = appLanguage,
                                onDismiss = { showCercaniasAccessibilityDialog = false }
                            )
                        }
                        
                        val pullToRefreshState = rememberPullToRefreshState()
                        val isInitialDeparturesLoad = departures.isEmpty()
                        PullToRefreshBox(
                            isRefreshing = isLoading,
                            onRefresh = { viewModel.fetchCercaniasDepartures() },
                            state = pullToRefreshState,
                            indicator = {
                                TransitPullRefreshIndicator(
                                    state = pullToRefreshState,
                                    isRefreshing = isLoading,
                                    stationName = stationName,
                                    operatorName = "Cercanías",
                                    appLanguage = appLanguage,
                                    isInitialLoad = isInitialDeparturesLoad
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            if (isLoading && isInitialDeparturesLoad && error == null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    repeat(5) {
                                        CercaniasDepartureSkeletonCard(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + activeTripBottomPadding),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (error != null) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isDarkMode) Color(0xFF2A1C1C) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                                    ),
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(16.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Error,
                                                            contentDescription = "Error",
                                                            tint = if (isDarkMode) Color(0xFFE53935) else MaterialTheme.colorScheme.error
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Text(
                                                            text = error!!,
                                                            color = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onErrorContainer,
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else if (!isLoading && departures.isEmpty()) {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 40.dp, horizontal = 20.dp)
                                                    .testTag("no_cercanias_departures_view"),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .background(
                                                            if (!isOnline) {
                                                                if (isDarkMode) Color(0xFF2A1C1C) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                            } else {
                                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                            },
                                                            CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (!isOnline) Icons.Default.WifiOff else Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(36.dp),
                                                        tint = if (!isOnline) {
                                                            if (isDarkMode) Color(0xFFEF5350) else MaterialTheme.colorScheme.error
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        }
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = if (!isOnline) {
                                                        if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet"
                                                    } else {
                                                        if (appLanguage == AppLanguage.CA) "Sense eixides pròximes" else "Sin salidas próximas"
                                                    },
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = if (!isOnline) {
                                                        if (appLanguage == AppLanguage.CA) "Els horaris de rodalia necessiten connexió a internet. Comprova la teua connexió per a vore les pròximes eixides." else "Los horarios de cercanías necesitan conexión a internet. Comprueba tu conexión para ver las próximas salidas."
                                                    } else {
                                                        if (appLanguage == AppLanguage.CA) "No hi ha trens de rodalia programats pròximament en esta estació." else "No hay trenes de cercanías programados próximamente en esta estación."
                                                    },
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                                Spacer(modifier = Modifier.height(24.dp))
                                                Button(
                                                    onClick = { 
                                                        if (isOnline) {
                                                            viewModel.forceSyncCercaniasSchedule() 
                                                        } else {
                                                            viewModel.fetchCercaniasDepartures()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .testTag("cercanias_btn_sync_schedule")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (!isOnline) {
                                                            if (appLanguage == AppLanguage.CA) "Reintentar connexió" else "Reintentar conexión"
                                                        } else {
                                                            if (appLanguage == AppLanguage.CA) "Actualitzar horaris de rodalia" else "Actualizar horarios de cercanías"
                                                        },
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val todayDepartures = departures.filter { !it.isTomorrow }
                                        val tomorrowDepartures = departures.filter { it.isTomorrow }

                                        items(
                                            items = todayDepartures,
                                            key = { "today_${it.tripId}_${it.departureTime}_${it.routeId}" }
                                        ) { departure ->
                                            CercaniasDepartureCard(
                                                departure = departure,
                                                alerts = cercaniasAlerts,
                                                isDarkMode = isDarkMode,
                                                appLanguage = appLanguage,
                                                onClick = { viewModel.selectCercaniasDepartureDetails(departure) }
                                            )
                                        }

                                        if (tomorrowDepartures.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = if (appLanguage == AppLanguage.CA) "DEMÀ" else "MAÑANA",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 8.dp)
                                                )
                                            }
                                            items(
                                                items = tomorrowDepartures,
                                                key = { "tomorrow_${it.tripId}_${it.departureTime}_${it.routeId}" }
                                            ) { departure ->
                                                CercaniasDepartureCard(
                                                    departure = departure,
                                                    alerts = cercaniasAlerts,
                                                    isDarkMode = isDarkMode,
                                                    appLanguage = appLanguage,
                                                    onClick = { viewModel.selectCercaniasDepartureDetails(departure) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + activeTripBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // --- SECCIÓN 1: INCIDENCIAS DE LA RED ---
                        item {
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "INCIDÈNCIES DE LA XARXA" else "INCIDENCIAS DE LA RED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        if (isCercaniasAlertsLoading && activeAlerts.isEmpty()) {
                            item {
                                SkeletonCardItem(modifier = Modifier.fillMaxWidth())
                            }
                        } else if (activeAlerts.isEmpty()) {
                            item {
                                if (!isOnline) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDarkMode) Color(0xFF2A1C1C) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WifiOff,
                                                contentDescription = null,
                                                tint = if (isDarkMode) Color(0xFFE53935) else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (isDarkMode) Color(0xFFE53935) else MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = if (appLanguage == AppLanguage.CA) "No s'han pogut sincronitzar les incidències de Rodalia. Comprova la teua connexió." else "No se han podido sincronizar las incidencias de Cercanías. Comprueba tu conexión a internet.",
                                                    fontSize = 12.sp,
                                                    color = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDarkMode) Color(0xFF132219) else Color(0xFF2ECC71).copy(alpha = 0.1f)
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Normal",
                                                tint = Color(0xFF2ECC71),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = if (appLanguage == AppLanguage.CA) "Xarxa sense incidències" else "Red sin incidencias",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF2ECC71)
                                                )
                                                Text(
                                                    text = if (appLanguage == AppLanguage.CA) "Totes les línies de Rodalia Renfe de València operen amb normalitat." else "Todas las líneas de Cercanías Renfe de Valencia operan con normalidad.",
                                                    fontSize = 12.sp,
                                                    color = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(
                                items = activeAlerts,
                                key = { "active_${it.id}_${it.routeIds.joinToString()}_${it.headerEs.hashCode()}" }
                            ) { alert ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDarkMode) Color(0xFF2A1C1C) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Aviso",
                                                tint = if (isDarkMode) Color(0xFFE53935) else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            
                                            val lineLabel = if (alert.routeIds.isNotEmpty()) {
                                                if (alert.routeIds.size == 1) {
                                                    if (appLanguage == AppLanguage.CA) "Línia ${alert.routeIds.first()}" else "Línea ${alert.routeIds.first()}"
                                                } else {
                                                    if (appLanguage == AppLanguage.CA) "Línies ${alert.routeIds.joinToString(", ")}" else "Líneas ${alert.routeIds.joinToString(", ")}"
                                                }
                                            } else {
                                                if (appLanguage == AppLanguage.CA) "Avisos actius" else "Avisos activos"
                                            }
                                            Text(
                                                text = lineLabel,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isDarkMode) Color(0xFFE53935) else MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (alert.headerEs.isNotBlank()) {
                                            Text(
                                                text = alert.headerEs,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                        LinkifiedText(
                                            text = alert.descriptionEs,
                                            fontSize = 13.sp,
                                            textColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // --- SECCIÓN 2: AVISOS GENERALES Y NUEVOS HORARIOS ---
                        if (generalNotices.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "INFORMACIÓ I HORARIS" else "INFORMACIÓN Y HORARIOS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(
                                items = generalNotices,
                                key = { "general_${it.id}_${it.headerEs.hashCode()}" }
                            ) { notice ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Información",
                                                tint = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = if (appLanguage == AppLanguage.CA) "Avís de servei" else "Aviso de servicio",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (notice.headerEs.isNotBlank()) {
                                            Text(
                                                text = notice.headerEs,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                        LinkifiedText(
                                            text = notice.descriptionEs,
                                            fontSize = 13.sp,
                                            textColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // --- SECCIÓN 3: ACCESIBILIDAD ---
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { isAccessibilityExpanded = !isAccessibilityExpanded },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF222222) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (isAccessibilityExpanded) "▲" else "▼",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = texts.accessibilityAndLifts,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.8.sp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (accessibilityAlerts.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                            contentColor = if (accessibilityAlerts.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                        ) {
                                            Text(
                                                text = accessibilityAlerts.size.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isAccessibilityExpanded) {
                            if (accessibilityAlerts.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2ECC71).copy(alpha = 0.1f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Accesible",
                                                tint = Color(0xFF27AE60),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Accesibilidad sin incidencias",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF27AE60)
                                                )
                                                Text(
                                                    text = "No se han detectado problemas en escaleras mecánicas o ascensores.",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                groupedAccessibilityAlerts.forEach { (stationName, alertsInStation) ->
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDarkMode) Color(0xFF222222) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(18.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = stationName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                
                                                alertsInStation.forEachIndexed { index, alert ->
                                                    if (index > 0) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(vertical = 12.dp),
                                                            color = if (isDarkMode) Color(0xFF2C3548) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                    Column {
                                                        if (alert.headerEs.isNotBlank() && alert.headerEs != alert.descriptionEs) {
                                                            Text(
                                                                text = alert.headerEs,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                color = textColor,
                                                                modifier = Modifier.padding(bottom = 2.dp)
                                                            )
                                                        }
                                                        LinkifiedText(
                                                            text = alert.descriptionEs,
                                                            fontSize = 13.sp,
                                                            textColor = subtextColor
                                                        )
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
            }
        }
    }
    
    // Bottom Sheet
    if (isBottomSheetVisible && selectedDeparture != null) {
        val sheetState = rememberModalBottomSheetState()
        val selectedStationId by viewModel.cercaniasSelectedStationId.collectAsState()
        val allStations by viewModel.allCercaniasStations.collectAsState()
        val currentStationEntity = remember(allStations, selectedStationId) {
            allStations.find { it.stop_id == selectedStationId }
        }
        val currentStationName = currentStationEntity?.displayName ?: currentStationEntity?.nombre ?: ""

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissCercaniasDepartureDetails() },
            sheetState = sheetState,
            containerColor = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA),
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            CercaniasDepartureDetails(
                departure = selectedDeparture!!,
                alerts = cercaniasAlerts,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                originStationId = selectedStationId,
                originStationName = currentStationName
            )
        }
    }
}








