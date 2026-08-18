package com.example.ui.cercanias

import androidx.activity.compose.BackHandler
import com.example.ui.components.LinkifiedText
import com.example.ui.components.SkeletonCardItem
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
import com.example.ui.theme.ScreenHeader
import com.example.ui.theme.UnifiedTabRow
import com.example.ui.theme.UnifiedAppCard
import com.example.ui.theme.LiveTimerStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CercaniasScreen(
    viewModel: CercaniasViewModel,
    isDarkMode: Boolean,
    initialPage: Int = 0,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onBackGesture: (() -> Unit)? = null
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
    val accessibilityAlerts by viewModel.accessibilityCercaniasAlerts.collectAsState()
    val groupedAccessibilityAlerts by viewModel.groupedAccessibilityAlerts.collectAsState()
    var isAccessibilityExpanded by remember { mutableStateOf(false) }
    
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var allStationsList by remember { mutableStateOf<List<com.example.data.database.CercaniasStationEntity>>(emptyList()) }
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
    
    LaunchedEffect(showSearchDialog) {
        if (showSearchDialog) {
            allStationsList = viewModel.getAllCercaniasStations()
        }
    }
    
    val scope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }
    
    // Auto-fetch on mount
    LaunchedEffect(Unit) {
        viewModel.fetchCercaniasDepartures()
    }
    
    // Auto-select nearest station on load if no station is selected
    LaunchedEffect(selectedStationId, favoriteStations, allCercaniasStations) {
        if (selectedStationId.isBlank()) {
            viewModel.autoSelectNearestCercaniasStationIfNeeded()
        }
    }
    
    // Clean up on unmount
    DisposableEffect(Unit) {
        onDispose {
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
        // 1. Cabecera Única de Pantalla (ScreenHeader)
        ScreenHeader(
            title = texts.headerCercaniasTitle,
            subtitle = texts.headerCercaniasSubtitle,
            onBackClick = onBackClick
        )

        // 2. TabRow Unificado (Material 3 con tipografía titleSmall y Capitalizado)
        UnifiedTabRow(
            selectedTabIndex = pagerState.currentPage,
            tabs = if (appLanguage == AppLanguage.CA) listOf("Pròxims trens", "Avisos") else listOf("Próximos trenes", "Avisos"),
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

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
                                contentPadding = PaddingValues(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                // 1. Square Search Button
                                item {
                                    val cardBgColor = if (isDarkMode) Color(0xFF171D2C) else MaterialTheme.colorScheme.surface
                                    Card(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 58.dp)
                                            .clickable { showQuickPicker = true }
                                            .testTag("square_cercanias_picker_button"),
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        border = appCardBorder(),
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
                                                .clickable { showQuickPicker = true }
                                                .testTag("empty_favorite_cercanias_chip"),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDarkMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
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
                                            appCardBorder()
                                        }
                                        val animatedCornerRadius by animateDpAsState(
                                            targetValue = if (isSelected) 8.dp else 18.dp,
                                            animationSpec = tween(durationMillis = 500),
                                            label = "fav_cercanias_selector_corner"
                                        )
                                        
                                        Card(
                                            modifier = Modifier
                                                .widthIn(min = 125.dp, max = 165.dp)
                                                .clickable { viewModel.selectCercaniasStation(station.id) }
                                                .testTag("favorite_cercanias_chip_${station.id}"),
                                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                            border = borderStroke,
                                            shape = RoundedCornerShape(animatedCornerRadius)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                                        modifier = Modifier.weight(1f)
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
                                                
                                                // Líneas
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stationName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        val pullToRefreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = isLoading,
                            onRefresh = { viewModel.fetchCercaniasDepartures() },
                            state = pullToRefreshState,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            if (error != null) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDarkMode) Color(0xFF2A1C1C) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFFE53935).copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
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
                            } else if (departures.isEmpty() && !isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Train,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = subtextColor.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No hay trenes programados próximamente.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = subtextColor,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.forceSyncCercaniasSchedule() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDarkMode) Color(0xFFC00000) else Color(0xFFC00000),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("cercanias_btn_sync_schedule")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (appLanguage == AppLanguage.CA) "Actualitzar horaris de rodalia" else "Actualizar horarios de cercanías",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val todayDepartures = departures.filter { !it.isTomorrow }
                                    val tomorrowDepartures = departures.filter { it.isTomorrow }

                                    items(todayDepartures) { departure ->
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
                                        items(tomorrowDepartures) { departure ->
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
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
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
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDarkMode) Color(0xFF132219) else Color(0xFF2ECC71).copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    border = if (isDarkMode) BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.2f)) else null
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
                        } else {
                            items(activeAlerts) { alert ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDarkMode) Color(0xFF2A1C1C) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, if (isDarkMode) Color(0xFFE53935).copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
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

                        // --- SECCIÓN 2: ACCESIBILIDAD ---
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isAccessibilityExpanded = !isAccessibilityExpanded },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF171D2C) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkMode) 0.3f else 0.6f))
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
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkMode) 0.3f else 0.6f)),

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
                                                containerColor = if (isDarkMode) Color(0xFF171D2C) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(18.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkMode) 0.3f else 0.6f))
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
            containerColor = if (isDarkMode) Color(0xFF171D2C) else MaterialTheme.colorScheme.surface,
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








