package com.example.ui.routing

import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.common.search.UnifiedSearchSuggestionsPanel
import com.example.ui.dashboard.AppLanguage
import com.example.ui.routing.components.ItineraryCard
import com.example.ui.routing.components.RouteDetailBottomSheet
import com.example.ui.routing.components.RouteScheduleDialog
import com.example.ui.routing.components.RouteSearchHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerScreen(
    userLocation: Location? = null,
    initialDestination: PlannerLocation? = null,
    onInitialDestinationConsumed: (() -> Unit)? = null,
    onNavigateBack: () -> Unit,
    onSelectItineraryForMap: (PlannedItinerary) -> Unit,
    onStartTrip: ((PlannedItinerary, String, String) -> Unit)? = null,
    onPickOnMapClick: ((isOrigin: Boolean) -> Unit)? = null,
    isDarkMode: Boolean = false,
    appLanguage: AppLanguage = AppLanguage.CA,
    viewModel: RoutePlannerViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val origin by viewModel.origin.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val originQuery by viewModel.originQuery.collectAsState()
    val destinationQuery by viewModel.destinationQuery.collectAsState()
    val activeSearchField by viewModel.activeSearchField.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isSearchingOrigin by viewModel.isSearchingOrigin.collectAsState()
    val isSearchingDestination by viewModel.isSearchingDestination.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val homeLocation by viewModel.homeLocation.collectAsState()
    val workLocation by viewModel.workLocation.collectAsState()
    val customFavorites by viewModel.customFavorites.collectAsState()
    val unifiedTransitFavorites by viewModel.unifiedTransitFavorites.collectAsState()
    val selectedModeFilters by viewModel.selectedModeFilters.collectAsState()
    val fewestTransfers by viewModel.fewestTransfers.collectAsState()
    val departureType by viewModel.departureType.collectAsState()
    val selectedTime by viewModel.selectedTime.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedItinerary by viewModel.selectedItinerary.collectAsState()

    var showScheduleDialog by remember { mutableStateOf(false) }

    // Initialize user location as origin if origin is empty (ONLY ONCE)
    var userLocationInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(userLocation) {
        if (!userLocationInitialized && origin == null && userLocation != null) {
            userLocationInitialized = true
            viewModel.setUserLocationAsOrigin(userLocation)
        }
    }

    // Set initial destination if passed (e.g. from Map "Cómo llegar" button)
    LaunchedEffect(initialDestination) {
        if (initialDestination != null) {
            if (destination == null || destination?.title == initialDestination.title) {
                viewModel.setDestination(initialDestination)
            }
            onInitialDestinationConsumed?.invoke()
        }
    }

    val handlePlannerBack = {
        if (activeSearchField != PlannerSearchField.NONE) {
            viewModel.dismissSearchPanel()
        } else if (selectedItinerary != null) {
            viewModel.selectItinerary(null)
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        handlePlannerBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("route_planner_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "Planificador Multimodal" else "Planificador Multimodal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = handlePlannerBack,
                        modifier = Modifier.testTag("route_planner_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search inputs header (Always keeps dedicated "Mi Ubicación" button on origin input)
            RouteSearchHeader(
                originQuery = originQuery,
                destinationQuery = destinationQuery,
                originLocation = origin,
                destinationLocation = destination,
                onOriginQueryChange = { viewModel.updateOriginQuery(it) },
                onDestinationQueryChange = { viewModel.updateDestinationQuery(it) },
                onUseGpsOrigin = {
                    if (userLocation != null) {
                        viewModel.setUserLocationAsOrigin(userLocation)
                    } else {
                        viewModel.useCurrentLocationAsOrigin(context)
                    }
                },
                onSwap = { viewModel.swapOriginAndDestination() },
                selectedModeFilters = selectedModeFilters,
                onToggleModeFilter = { viewModel.toggleModeFilter(it) },
                onClearModeFilters = { viewModel.clearModeFilters() },
                fewestTransfers = fewestTransfers,
                onToggleFewestTransfers = { viewModel.toggleFewestTransfers() },
                departureType = departureType,
                selectedTime = selectedTime,
                onOpenScheduleDialog = { showScheduleDialog = true },
                isSearchingOrigin = isSearchingOrigin,
                isSearchingDestination = isSearchingDestination,
                onOriginFocused = { viewModel.onOriginFocused() },
                onDestinationFocused = { viewModel.onDestinationFocused() },
                onOriginUnfocused = { viewModel.commitCurrentSearchQueryIfFieldUnfocused(PlannerSearchField.ORIGIN) },
                onDestinationUnfocused = { viewModel.commitCurrentSearchQueryIfFieldUnfocused(PlannerSearchField.DESTINATION) },
                appLanguage = appLanguage
            )

            // Unified Suggestions Panel or Routing Results
            if (activeSearchField != PlannerSearchField.NONE) {
                val currentQuery = if (activeSearchField == PlannerSearchField.ORIGIN) originQuery else destinationQuery

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    UnifiedSearchSuggestionsPanel(
                        searchQuery = currentQuery,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        isDarkMode = isDarkMode,
                        appLanguage = appLanguage,
                        recentSearches = recentSearches,
                        homeLocation = homeLocation,
                        workLocation = workLocation,
                        customFavorites = customFavorites,
                        unifiedTransitFavorites = unifiedTransitFavorites,
                        onSearchResultClick = { result ->
                            viewModel.selectSearchResult(result)
                        },
                        onClearRecentSearches = {
                            viewModel.clearRecentSearches()
                        },
                        onRemoveRecentSearch = { id ->
                            viewModel.removeRecentSearch(id)
                        },
                        onElegirEnMapaClick = if (onPickOnMapClick != null) {
                            {
                                val isOrigin = (activeSearchField == PlannerSearchField.ORIGIN)
                                onPickOnMapClick(isOrigin)
                            }
                        } else null,
                        onSaveLocationShortcutClick = { isHome ->
                            // Optional save handler
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Main Content View (Results / Loading / Error / Empty state)
                Crossfade(
                    targetState = uiState,
                    label = "planner_content_crossfade",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { state ->
                    when (state) {
                        is RoutePlannerUiState.Idle -> {
                            PlannerIdleState(
                                onSelectPreset = { title, lat, lon ->
                                    viewModel.setDestinationFromCoordinates(title, lat, lon)
                                },
                                appLanguage = appLanguage
                            )
                        }
                        is RoutePlannerUiState.Loading -> {
                            PlannerLoadingCard(
                                currentStage = state.stage,
                                appLanguage = appLanguage
                            )
                        }
                        is RoutePlannerUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.searchRoutes() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (appLanguage == AppLanguage.ES) "Reintentar" else "Reintentar")
                                    }
                                }
                            }
                        }
                        is RoutePlannerUiState.Success -> {
                            val fastestDuration = remember(state.itineraries) {
                                state.itineraries.minOfOrNull { it.totalDurationSeconds }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (state.itineraries.size > 1) {
                                    item(key = "itineraries_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (appLanguage == AppLanguage.ES) {
                                                    "${state.itineraries.size} rutas encontradas"
                                                } else {
                                                    "${state.itineraries.size} rutes trobades"
                                                },
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            Text(
                                                text = if (appLanguage == AppLanguage.ES) "Salida más próxima primero" else "Eixida més pròxima primer",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }
                                }

                                items(state.itineraries, key = { it.id }) { itinerary ->
                                    ItineraryCard(
                                        itinerary = itinerary,
                                        isSelected = selectedItinerary?.id == itinerary.id,
                                        isFastest = state.itineraries.size > 1 && itinerary.totalDurationSeconds == fastestDuration,
                                        userLocation = userLocation,
                                        originLocation = origin,
                                        appLanguage = appLanguage,
                                        onClick = {
                                            viewModel.selectItinerary(itinerary)
                                        },
                                        onStartTrip = if (onStartTrip != null) {
                                            { it ->
                                                val origName = origin?.title ?: if (appLanguage == AppLanguage.ES) "Tu ubicación" else "La teua ubicació"
                                                val destName = destination?.title ?: if (appLanguage == AppLanguage.ES) "Destino" else "Destinació"
                                                onStartTrip(it, origName, destName)
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Schedule Dialog
        if (showScheduleDialog) {
            RouteScheduleDialog(
                currentDepartureType = departureType,
                currentTime = selectedTime,
                appLanguage = appLanguage,
                onDismiss = { showScheduleDialog = false },
                onConfirm = { type, time ->
                    showScheduleDialog = false
                    viewModel.setDepartureSchedule(type, time)
                }
            )
        }

        // Detailed Itinerary Bottom Sheet
        selectedItinerary?.let { itinerary ->
            RouteDetailBottomSheet(
                itinerary = itinerary,
                userLocation = userLocation,
                originLocation = origin,
                onDismiss = { viewModel.selectItinerary(null) },
                onViewOnMap = {
                    onSelectItineraryForMap(itinerary)
                },
                onStartTrip = if (onStartTrip != null) {
                    {
                        val origName = origin?.title ?: if (appLanguage == AppLanguage.ES) "Tu ubicación" else "La teua ubicació"
                        val destName = destination?.title ?: if (appLanguage == AppLanguage.ES) "Destino" else "Destinació"
                        viewModel.selectItinerary(null)
                        onStartTrip(itinerary, origName, destName)
                    }
                } else null,
                onRecalculateFromStation = { stationName, lat, lon ->
                    viewModel.recalculateFromStation(stationName, lat, lon)
                },
                appLanguage = appLanguage
            )
        }
    }
}

@Composable
fun PlannerIdleState(
    onSelectPreset: (String, Double, Double) -> Unit,
    appLanguage: AppLanguage = AppLanguage.CA
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DirectionsTransit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (appLanguage == AppLanguage.ES) "¿A dónde te diriges hoy?" else "On et dirigeixes hui?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (appLanguage == AppLanguage.ES) 
                "Planifica rutas inteligentes combinando Metro, EMT, Cercanías y trayectos a pie en tiempo real." 
            else 
                "Planifica rutes intel·ligents combinant Metro, EMT, Rodalia i trajectes a peu en temps real.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick presets
        Text(
            text = if (appLanguage == AppLanguage.ES) "Destinos populares:" else "Destinacions populars:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                label = "Estació del Nord",
                lat = 39.4667,
                lon = -0.3772,
                onClick = onSelectPreset,
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                label = "Aeroport",
                lat = 39.4912,
                lon = -0.4746,
                onClick = onSelectPreset,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                label = "Pl. Ajuntament",
                lat = 39.4699,
                lon = -0.3763,
                onClick = onSelectPreset,
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                label = "Ciutat de les Arts",
                lat = 39.4539,
                lon = -0.3524,
                onClick = onSelectPreset,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PresetChip(
    label: String,
    lat: Double,
    lon: Double,
    onClick: (String, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick(label, lat, lon) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

@Composable
fun PlannerLoadingCard(
    currentStage: PlannerLoadingStage,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    // Continuous trickle progress: smoothly moves to target, then trickles forward so it never looks stuck
    val progressAnim = remember { Animatable(0.12f) }

    LaunchedEffect(currentStage) {
        val target = currentStage.progress
        val maxCap = when (currentStage) {
            PlannerLoadingStage.SCHEDULED_TRIPS -> 0.65f
            PlannerLoadingStage.REAL_TIME_CROSS -> 0.90f
            PlannerLoadingStage.BUILDING_ROUTES -> 0.98f
        }
        // 1. Smoothly accelerate to stage base target
        progressAnim.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        // 2. Trickle continuously towards max cap while waiting for background operation
        progressAnim.animateTo(
            targetValue = maxCap,
            animationSpec = tween(durationMillis = 6000, easing = LinearEasing)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Clean map icon container
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Main header title
            Text(
                text = if (appLanguage == AppLanguage.ES) "Calculando mejor ruta" else "Calculant millor ruta",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Continuous smooth linear progress bar
            LinearProgressIndicator(
                progress = { progressAnim.value.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                drawStopIndicator = {}
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Multi-stage status labels with checkmarks for past stages & active indicators
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(IntrinsicSize.Max)
            ) {
                val stages = PlannerLoadingStage.values()
                stages.forEach { stage ->
                    val isCurrent = stage == currentStage
                    val isPast = stage.ordinal < currentStage.ordinal
                    val label = if (appLanguage == AppLanguage.ES) stage.titleEs else stage.titleCa

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isPast) {
                            // Completed stage checkmark
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981), // Emerald green
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (isCurrent) {
                            // Active stage loading spinner
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            // Future stage placeholder circle
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        val textColor = when {
                            isCurrent -> MaterialTheme.colorScheme.onSurface
                            isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }

                        val fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal

                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = fontWeight,
                                fontSize = 13.5.sp
                            ),
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

