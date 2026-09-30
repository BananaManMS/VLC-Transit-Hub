package com.example.ui.dashboard

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.CalendarItemEntity
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.data.model.WeatherData
import com.example.data.repository.StaticTransitDataCache
import com.example.ui.bus.BusMapper
import com.example.ui.bus.BusViewModel
import com.example.ui.bus.EmtBusStop
import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.map.MapSelectionMode
import com.example.ui.map.RecentSearch
import com.example.ui.metro.*
import com.example.ui.metro.cards.AddTransitCardWizardDialog
import com.example.ui.routing.PlannerLocation
import com.example.ui.dashboard.home.*
import com.example.util.LocationUtils
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHomeTab(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    currentTimeFlow: StateFlow<String>,
    appLanguage: AppLanguage,
    texts: Translation,
    isDarkMode: Boolean,
    isFahrenheit: Boolean,
    isTablet: Boolean,
    localMaxHeight: Dp,
    dynamicBottomTripPadding: Dp,
    metroIncidents: List<MetroIncident>,
    metroSpecialNotices: List<MetroNotice>,
    cercaniasAlerts: List<CercaniasAlert>,
    isMetroAlertsLoading: Boolean,
    isCercaniasAlertsLoading: Boolean,
    weatherData: WeatherData?,
    calendarItems: List<CalendarItemEntity>,
    isOnline: Boolean = true,
    dashboardViewModel: DashboardViewModel,
    metroViewModel: MetroViewModel,
    cercaniasViewModel: CercaniasViewModel,
    busViewModel: BusViewModel? = null,
    onNavigateToTab: (DashboardTab, Int) -> Unit,
    onOpenRoutePlanner: (PlannerLocation?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMetroAvisos: () -> Unit,
    onOpenCercaniasAvisos: () -> Unit,
    onSyncCalendarClick: () -> Unit,
    onAddCalendarClick: () -> Unit,
    onDeleteCalendarItem: (CalendarItemEntity) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onConfigureLocationOnMap: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Commute locations synchronized with Map & RoutePlanner
    val homeLocationState by dashboardViewModel.homeLocation.collectAsState()
    val workLocationState by dashboardViewModel.workLocation.collectAsState()

    val homeName = homeLocationState?.subtitle?.ifBlank { homeLocationState?.title } ?: ""
    val homeLat = homeLocationState?.latitude ?: 0.0
    val homeLon = homeLocationState?.longitude ?: 0.0

    val workName = workLocationState?.subtitle?.ifBlank { workLocationState?.title } ?: ""
    val workLat = workLocationState?.latitude ?: 0.0
    val workLon = workLocationState?.longitude ?: 0.0

    var showCommuteDialogFor by remember { mutableStateOf<String?>(null) } // "HOME" or "WORK"

    // Card details & add card dialog
    var selectedTransitCard by remember { mutableStateOf<TransitCardUiModel?>(null) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    // Transit Cards from metro repository
    val transitCards by metroViewModel.transitCardsFlow.collectAsState()
    val homeVisibleCards = remember(transitCards) {
        transitCards.filter { it.showOnHome }.sortedWith(
            compareBy<TransitCardUiModel> { it.customOrder }
                .thenBy { try { com.example.ui.metro.CardCategory.valueOf(it.category).orderIndex } catch (_: Exception) { 99 } }
                .thenBy { it.cardNumber }
        )
    }

    // Stations & Real-Time
    val dashLocation by dashboardViewModel.lastLocation.collectAsState()
    val metroLocation by metroViewModel.lastLocation.collectAsState()
    val allMetroStations by metroViewModel.allNetworkStations.collectAsState()
    val allCercaniasStations by cercaniasViewModel.allCercaniasStations.collectAsState()
    val hasMetroAlertsError by metroViewModel.hasMetroAlertsError.collectAsState()
    val hasCercaniasAlertsError by cercaniasViewModel.hasCercaniasAlertsError.collectAsState()

    // Favorites & Custom Aliases
    val favoriteMetroStations by metroViewModel.favoriteStations.collectAsState()
    val favoriteCercaniasStations by cercaniasViewModel.cercaniasFavoriteStations.collectAsState()
    val favoriteBusStops by (busViewModel?.favoriteBusStops ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val busStopAliases by (busViewModel?.busStopAliases ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    val favoriteMetrobusStops by (busViewModel?.favoriteMetrobusStops ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val metrobusStopAliases by (busViewModel?.metrobusStopAliases ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    val favoriteTransitModes by dashboardViewModel.favoriteTransitModes.collectAsState()

    val showMetroNearby = favoriteTransitModes.isEmpty() || favoriteTransitModes.contains("METRO")
    val showCercaniasNearby = favoriteTransitModes.isEmpty() || favoriteTransitModes.contains("CERCANIAS")
    val showEmtNearby = favoriteTransitModes.isEmpty() || favoriteTransitModes.contains("EMT")
    val showMetrobusNearby = favoriteTransitModes.isEmpty() || favoriteTransitModes.contains("METROBUS")

    val userCoords = remember(metroLocation, dashLocation) {
        val loc = metroLocation
        if (loc != null) {
            Pair(loc.latitude, loc.longitude)
        } else if (dashLocation != null) {
            dashLocation
        } else {
            null
        }
    }

    val refLat = userCoords?.first ?: 0.0
    val refLon = userCoords?.second ?: 0.0

    val hasLocation = LocationUtils.hasLocationPermission(context)

    val maxNearbyDistanceMeters = 1000.0 // Delimitar a lo realmente cercano (< 1 km)

    // Metro Station (Max 1) - Nearest station within 1km, favorite only if closest or <= 500m
    val (nearestMetroStation, isMetroFav) = remember(userCoords, allMetroStations, favoriteMetroStations) {
        if (userCoords == null || allMetroStations.isEmpty()) Pair<MetroStation?, Boolean>(null, false)
        else {
            val (refLat, refLon) = userCoords
            val closest = allMetroStations.minByOrNull {
                LocationUtils.calculateDistanceMeters(refLat, refLon, it.latitude, it.longitude)
            }
            val closestDist = closest?.let { LocationUtils.calculateDistanceMeters(refLat, refLon, it.latitude, it.longitude) } ?: Double.MAX_VALUE

            val favList = allMetroStations.filter { favoriteMetroStations.contains(it.id) }
            val closestFav = if (favList.isNotEmpty()) {
                favList.minByOrNull {
                    LocationUtils.calculateDistanceMeters(refLat, refLon, it.latitude, it.longitude)
                }
            } else null
            val favDist = closestFav?.let { LocationUtils.calculateDistanceMeters(refLat, refLon, it.latitude, it.longitude) } ?: Double.MAX_VALUE

            if (closestFav != null && favDist <= maxNearbyDistanceMeters && (closestFav.id == closest?.id || favDist <= 500.0)) {
                Pair<MetroStation?, Boolean>(closestFav, true)
            } else if (closest != null && closestDist <= maxNearbyDistanceMeters) {
                Pair<MetroStation?, Boolean>(closest, false)
            } else {
                Pair<MetroStation?, Boolean>(null, false)
            }
        }
    }

    val nearestMetroDistance = remember(userCoords, nearestMetroStation) {
        if (userCoords != null && nearestMetroStation != null) {
            LocationUtils.calculateDistanceMeters(userCoords.first, userCoords.second, nearestMetroStation.latitude, nearestMetroStation.longitude)
        } else null
    }

    // Cercanías Station (Max 1) - Nearest station within 1km, favorite only if closest or <= 500m
    val (nearestCercaniasStation, isCercaniasFav) = remember(userCoords, allCercaniasStations, favoriteCercaniasStations) {
        if (userCoords == null || allCercaniasStations.isEmpty()) Pair<CercaniasStationEntity?, Boolean>(null, false)
        else {
            val (refLat, refLon) = userCoords
            val closest = allCercaniasStations.minByOrNull {
                LocationUtils.calculateDistanceMeters(refLat, refLon, it.lat, it.lon)
            }
            val closestDist = closest?.let { LocationUtils.calculateDistanceMeters(refLat, refLon, it.lat, it.lon) } ?: Double.MAX_VALUE

            val closestFav = if (favoriteCercaniasStations.isNotEmpty()) {
                favoriteCercaniasStations.minByOrNull {
                    LocationUtils.calculateDistanceMeters(refLat, refLon, it.lat, it.lon)
                }
            } else null
            val favDist = closestFav?.let { LocationUtils.calculateDistanceMeters(refLat, refLon, it.lat, it.lon) } ?: Double.MAX_VALUE

            if (closestFav != null && favDist <= maxNearbyDistanceMeters && (closestFav.stop_id == closest?.stop_id || favDist <= 500.0)) {
                Pair<CercaniasStationEntity?, Boolean>(closestFav, true)
            } else if (closest != null && closestDist <= maxNearbyDistanceMeters) {
                Pair<CercaniasStationEntity?, Boolean>(closest, false)
            } else {
                Pair<CercaniasStationEntity?, Boolean>(null, false)
            }
        }
    }

    val nearestCercaniasDistance = remember(userCoords, nearestCercaniasStation) {
        if (userCoords != null && nearestCercaniasStation != null) {
            LocationUtils.calculateDistanceMeters(userCoords.first, userCoords.second, nearestCercaniasStation.lat, nearestCercaniasStation.lon)
        } else null
    }

    // EMT Bus & Metrobús Stops - Asynchronously calculated in ViewModel
    val nearbyBusStops by dashboardViewModel.nearbyBusStops.collectAsState()
    val nearbyMetrobusStops by dashboardViewModel.nearbyMetrobusStops.collectAsState()

    LaunchedEffect(userCoords, favoriteBusStops, favoriteMetrobusStops, showEmtNearby, showMetrobusNearby) {
        dashboardViewModel.computeNearbyStops(
            userCoords = userCoords,
            favoriteBusStops = favoriteBusStops,
            favoriteMetrobusStops = favoriteMetrobusStops,
            showEmtNearby = showEmtNearby,
            showMetrobusNearby = showMetrobusNearby
        )
    }

    val calendarEvents = remember(calendarItems) {
        calendarItems.filter { it.itemType == "EVENT" }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("inicio_pull_to_refresh")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp + dynamicBottomTripPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Unified Top Header Box (Clock, Date, Settings + Route Search & Commute Destinations)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                ),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Clock & Settings Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DashboardClockWidget(
                            currentTimeFlow = currentTimeFlow,
                            appLanguage = appLanguage,
                            isTablet = isTablet,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag("settings_button_top")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = texts.headerAjustesTitle,
                                tint = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Quick Commute Navigation Bar
                    QuickCommuteBar(
                        appLanguage = appLanguage,
                        homeName = homeName,
                        workName = workName,
                        isDarkMode = isDarkMode,
                        onHomeClick = {
                            if (homeLat != 0.0 && homeLon != 0.0) {
                                onOpenRoutePlanner(PlannerLocation(title = homeName.ifBlank { "Casa" }, latitude = homeLat, longitude = homeLon))
                            } else {
                                showCommuteDialogFor = "HOME"
                            }
                        },
                        onWorkClick = {
                            if (workLat != 0.0 && workLon != 0.0) {
                                onOpenRoutePlanner(PlannerLocation(title = workName.ifBlank { "Trabajo" }, latitude = workLat, longitude = workLon))
                            } else {
                                showCommuteDialogFor = "WORK"
                            }
                        },
                        onPlanRouteClick = { onOpenRoutePlanner(null) },
                        onExploreMapClick = { onNavigateToTab(DashboardTab.Mapa, 0) },
                        onEditCommute = { showCommuteDialogFor = it }
                    )
                }
            }

            // Lower content section with standard horizontal padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2. Modos de Transporte (Quick Access Chips)
                QuickTransportModesRow(
                    appLanguage = appLanguage,
                    onSelectMode = { tab, page -> onNavigateToTab(tab, page) }
                )

                // Tablet vs Mobile content split
                if (isTablet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Columna Izquierda: Tiempo Real & Tarjetas
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Próximas Salidas Cercanas Multimodal
                            NearbyDeparturesWidget(
                                appLanguage = appLanguage,
                                hasLocation = hasLocation,
                                nearestMetroStation = if (showMetroNearby) nearestMetroStation else null,
                                isMetroFav = isMetroFav,
                                nearestMetroDistance = if (showMetroNearby) nearestMetroDistance else null,
                                nearestCercaniasStation = if (showCercaniasNearby) nearestCercaniasStation else null,
                                isCercaniasFav = isCercaniasFav,
                                nearestCercaniasDistance = if (showCercaniasNearby) nearestCercaniasDistance else null,
                                nearbyBusStops = if (showEmtNearby) nearbyBusStops else emptyList(),
                                busStopAliases = busStopAliases,
                                nearbyMetrobusStops = if (showMetrobusNearby) nearbyMetrobusStops else emptyList(),
                                metrobusStopAliases = metrobusStopAliases,
                                refLat = refLat,
                                refLon = refLon,
                                onMetroStationClick = { stationId ->
                                    metroViewModel.selectRealTimeStation(stationId, isUserAction = true)
                                    onNavigateToTab(DashboardTab.Metro, 0)
                                },
                                onCercaniasStationClick = { stationId ->
                                    cercaniasViewModel.selectCercaniasStation(stationId)
                                    onNavigateToTab(DashboardTab.Cercanias, 0)
                                },
                                onBusStopClick = { busStop ->
                                    busViewModel?.selectBusStopFromEntity(busStop)
                                    onNavigateToTab(DashboardTab.Bus, 0)
                                },
                                onMetrobusStopClick = { metrobusStop ->
                                    busViewModel?.selectMetrobusStopFromEntity(metrobusStop)
                                    onNavigateToTab(DashboardTab.Bus, 1)
                                },
                                onRequestLocationPermission = onRequestLocationPermission
                            )

                            // Tarjetas de Transporte
                            TransitCardsSummaryWidget(
                                appLanguage = appLanguage,
                                cards = homeVisibleCards,
                                onAddCardClick = { showAddCardDialog = true },
                                onCardClick = { selectedTransitCard = it },
                                onManageCardsClick = { onNavigateToTab(DashboardTab.Metro, 0) }
                            )
                        }

                        // Columna Derecha: Incidencias, Clima y Calendario
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IncidenciasWidget(
                                isDarkMode = isDarkMode,
                                appLanguage = appLanguage,
                                metroIncidents = metroIncidents,
                                cercaniasAlerts = cercaniasAlerts,
                                isMetroLoading = isMetroAlertsLoading,
                                isCercaniasLoading = isCercaniasAlertsLoading,
                                hasMetroError = hasMetroAlertsError,
                                hasCercaniasError = hasCercaniasAlertsError,
                                onOpenMetroAvisos = onOpenMetroAvisos,
                                onOpenCercaniasAvisos = onOpenCercaniasAvisos,
                                isOnline = isOnline,
                                preferredTransitModes = favoriteTransitModes,
                                modifier = Modifier.fillMaxWidth()
                            )

                            WeatherCard(
                                data = weatherData,
                                isFahrenheit = isFahrenheit,
                                isDarkMode = isDarkMode,
                                appLanguage = appLanguage,
                                isOnline = isOnline
                            )

                            CalendarSummaryWidget(
                                isTablet = isTablet,
                                isDarkMode = isDarkMode,
                                calendarTitle = texts.calendarTitle,
                                noEventsTodayText = texts.noEventsToday,
                                events = calendarEvents,
                                onSyncClick = onSyncCalendarClick,
                                onAddClick = onAddCalendarClick,
                                onEventDelete = onDeleteCalendarItem,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // Móvil: Orden secuencial centrado en transporte
                    NearbyDeparturesWidget(
                        appLanguage = appLanguage,
                        hasLocation = hasLocation,
                        nearestMetroStation = if (showMetroNearby) nearestMetroStation else null,
                        isMetroFav = isMetroFav,
                        nearestMetroDistance = if (showMetroNearby) nearestMetroDistance else null,
                        nearestCercaniasStation = if (showCercaniasNearby) nearestCercaniasStation else null,
                        isCercaniasFav = isCercaniasFav,
                        nearestCercaniasDistance = if (showCercaniasNearby) nearestCercaniasDistance else null,
                        nearbyBusStops = if (showEmtNearby) nearbyBusStops else emptyList(),
                        busStopAliases = busStopAliases,
                        nearbyMetrobusStops = if (showMetrobusNearby) nearbyMetrobusStops else emptyList(),
                        metrobusStopAliases = metrobusStopAliases,
                        refLat = refLat,
                        refLon = refLon,
                        onMetroStationClick = { stationId ->
                            metroViewModel.selectRealTimeStation(stationId, isUserAction = true)
                            onNavigateToTab(DashboardTab.Metro, 0)
                        },
                        onCercaniasStationClick = { stationId ->
                            cercaniasViewModel.selectCercaniasStation(stationId)
                            onNavigateToTab(DashboardTab.Cercanias, 0)
                        },
                        onBusStopClick = { busStop ->
                            busViewModel?.selectBusStopFromEntity(busStop)
                            onNavigateToTab(DashboardTab.Bus, 0)
                        },
                        onMetrobusStopClick = { metrobusStop ->
                            busViewModel?.selectMetrobusStopFromEntity(metrobusStop)
                            onNavigateToTab(DashboardTab.Bus, 1)
                        },
                        onRequestLocationPermission = onRequestLocationPermission
                    )

                    IncidenciasWidget(
                        isDarkMode = isDarkMode,
                        appLanguage = appLanguage,
                        metroIncidents = metroIncidents,
                        cercaniasAlerts = cercaniasAlerts,
                        isMetroLoading = isMetroAlertsLoading,
                        isCercaniasLoading = isCercaniasAlertsLoading,
                        hasMetroError = hasMetroAlertsError,
                        hasCercaniasError = hasCercaniasAlertsError,
                        onOpenMetroAvisos = onOpenMetroAvisos,
                        onOpenCercaniasAvisos = onOpenCercaniasAvisos,
                        isOnline = isOnline,
                        preferredTransitModes = favoriteTransitModes,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TransitCardsSummaryWidget(
                        appLanguage = appLanguage,
                        cards = homeVisibleCards,
                        onAddCardClick = { showAddCardDialog = true },
                        onCardClick = { selectedTransitCard = it },
                        onManageCardsClick = { onNavigateToTab(DashboardTab.Metro, 0) }
                    )

                    WeatherCard(
                        data = weatherData,
                        isFahrenheit = isFahrenheit,
                        isDarkMode = isDarkMode,
                        appLanguage = appLanguage,
                        isOnline = isOnline
                    )

                    CalendarSummaryWidget(
                        isTablet = isTablet,
                        isDarkMode = isDarkMode,
                        calendarTitle = texts.calendarTitle,
                        noEventsTodayText = texts.noEventsToday,
                        events = calendarEvents,
                        onSyncClick = onSyncCalendarClick,
                        onAddClick = onAddCalendarClick,
                        onEventDelete = onDeleteCalendarItem,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Modal para configurar Casa o Trabajo
    if (showCommuteDialogFor != null) {
        val isHome = showCommuteDialogFor == "HOME"
        CommuteSetupDialog(
            isHome = isHome,
            appLanguage = appLanguage,
            currentName = if (isHome) homeName else workName,
            currentLat = if (isHome) homeLat else workLat,
            currentLon = if (isHome) homeLon else workLon,
            dashboardViewModel = dashboardViewModel,
            onDismiss = { showCommuteDialogFor = null },
            onSelectOnMap = {
                showCommuteDialogFor = null
                onConfigureLocationOnMap?.invoke(isHome)
            },
            onSave = { name, lat, lon ->
                val recent = RecentSearch(
                    type = if (isHome) "home" else "work",
                    id = if (isHome) "home_location" else "work_location",
                    title = if (isHome) "Casa" else "Trabajo",
                    subtitle = name,
                    latitude = lat,
                    longitude = lon
                )
                if (isHome) {
                    dashboardViewModel.saveHomeLocation(recent)
                } else {
                    dashboardViewModel.saveWorkLocation(recent)
                }
                showCommuteDialogFor = null
            }
        )
    }

    // Modal para detalle de tarjeta
    if (selectedTransitCard != null) {
        CardDetailDialog(
            card = selectedTransitCard!!,
            appLanguage = appLanguage,
            metroViewModel = metroViewModel,
            isDarkMode = isDarkMode,
            onDismiss = { selectedTransitCard = null }
        )
    }

    // Modal para añadir tarjeta
    if (showAddCardDialog) {
        AddTransitCardWizardDialog(
            appLanguage = appLanguage,
            metroViewModel = metroViewModel,
            onDismiss = { showAddCardDialog = false },
            onCardAdded = { showAddCardDialog = false }
        )
    }
}
