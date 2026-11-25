package com.example.ui.bus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.SkeletonCardItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.AppDialogDefaults
import com.example.ui.theme.appDialogWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.theme.UnifiedTabRow
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.theme.ScreenHeader
import com.example.util.LocationUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmtBusScreen(
    viewModel: DashboardViewModel,
    busViewModel: BusViewModel = viewModel(),
    initialPage: Int = -1,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isScreenVisible: Boolean = true
) {
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val context = LocalContext.current
    
    val currentFilter by busViewModel.currentBusFilterSource.collectAsState()
    val searchQuery by busViewModel.busSearchQuery.collectAsState()
    
    val busStopsList by busViewModel.busStopsList.collectAsState()
    val busStopsLoading by busViewModel.busStopsLoading.collectAsState()
    val favoriteBusStops by busViewModel.favoriteBusStops.collectAsState()
    val favoriteBusStopsList by busViewModel.favoriteBusStopsList.collectAsState()
    val emtQuickFilter by busViewModel.emtQuickFilter.collectAsState()
    val busStopAliases by busViewModel.busStopAliases.collectAsState()
    
    val selectedBusStop by busViewModel.selectedBusStop.collectAsState()
    val busTimes by busViewModel.busTimes.collectAsState()
    val busTimesLoading by busViewModel.busTimesLoading.collectAsState()
    val isLoadingMoreScheduled by busViewModel.isLoadingMoreScheduled.collectAsState()
    val emtScheduledTimes by busViewModel.emtScheduledTimes.collectAsState()
    val isEmtScheduledLoading by busViewModel.isEmtScheduledLoading.collectAsState()
    val isEmtScheduledLoaded by busViewModel.isEmtScheduledLoaded.collectAsState()

    // Metrobus State
    val favoriteMetrobusStops by busViewModel.favoriteMetrobusStops.collectAsState()
    val favoriteMetrobusStopsList by busViewModel.favoriteMetrobusStopsList.collectAsState()
    val metrobusStopAliases by busViewModel.metrobusStopAliases.collectAsState()
    val metrobusSearchQuery by busViewModel.metrobusSearchQuery.collectAsState()
    val metrobusStopsList by busViewModel.metrobusStopsList.collectAsState()
    val metrobusStopsLoading by busViewModel.metrobusStopsLoading.collectAsState()
    val selectedMetrobusStop by busViewModel.selectedMetrobusStop.collectAsState()
    val metrobusTimes by busViewModel.metrobusTimes.collectAsState()
    val metrobusTimesLoading by busViewModel.metrobusTimesLoading.collectAsState()
    val isLoadingMoreMetrobusScheduled by busViewModel.isLoadingMoreMetrobusScheduled.collectAsState()
    val metrobusScheduledTimes by busViewModel.metrobusScheduledTimes.collectAsState()
    val isMetrobusScheduledLoading by busViewModel.isMetrobusScheduledLoading.collectAsState()
    val isMetrobusScheduledLoaded by busViewModel.isMetrobusScheduledLoaded.collectAsState()

    // Valenbisi State
    val favoriteValenbisi by busViewModel.favoriteValenbisi.collectAsState()
    val valenbisiAliases by busViewModel.valenbisiAliases.collectAsState()
    val valenbisiFilterSource by busViewModel.currentValenbisiFilterSource.collectAsState()
    val valenbisiSearchQuery by busViewModel.valenbisiSearchQuery.collectAsState()
    val valenbisiStations by busViewModel.valenbisiStations.collectAsState()
    val valenbisiLoading by busViewModel.valenbisiLoading.collectAsState()
    
    var showTimesSheet by remember { mutableStateOf(false) }
    var showMetrobusTimesSheet by remember { mutableStateOf(false) }
    var editingStopForAlias by remember { mutableStateOf<EmtBusStop?>(null) }
    var editingMetrobusStopForAlias by remember { mutableStateOf<MetrobusStop?>(null) }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val isBusBackHandlerEnabled = showTimesSheet || showMetrobusTimesSheet || searchQuery.isNotEmpty() || metrobusSearchQuery.isNotEmpty() || valenbisiSearchQuery.isNotEmpty()

    LaunchedEffect(selectedBusStop) {
        if (selectedBusStop != null) {
            showTimesSheet = true
        }
    }

    LaunchedEffect(selectedMetrobusStop) {
        if (selectedMetrobusStop != null) {
            showMetrobusTimesSheet = true
        }
    }

    BackHandler(enabled = isBusBackHandlerEnabled) {
        when {
            showTimesSheet -> {
                showTimesSheet = false
                busViewModel.selectBusStop(null)
            }
            showMetrobusTimesSheet -> {
                showMetrobusTimesSheet = false
                busViewModel.selectMetrobusStop(null)
            }
            searchQuery.isNotEmpty() -> {
                busViewModel.setBusSearchQuery("")
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            metrobusSearchQuery.isNotEmpty() -> {
                busViewModel.setMetrobusSearchQuery("")
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            valenbisiSearchQuery.isNotEmpty() -> {
                busViewModel.setValenbisiSearchQuery("")
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
    }
    
    val dashboardLocation by viewModel.lastLocation.collectAsState()
    val busLocation by busViewModel.lastLocation.collectAsState()
    val userLocation = busLocation ?: dashboardLocation

    LaunchedEffect(Unit) {
        if (LocationUtils.hasLocationPermission(context)) {
            LocationUtils.requestDeviceLocation(context) { lat, lng ->
                viewModel.updateLocation(lat, lng)
                busViewModel.updateLocation(lat, lng)
            }
        }
    }

    LaunchedEffect(dashboardLocation) {
        dashboardLocation?.let { (lat, lon) ->
            busViewModel.updateLocation(lat, lon)
        }
    }

    LaunchedEffect(currentFilter) {
        if (currentFilter == BusFilterSource.GPS_USER) {
            LocationUtils.requestDeviceLocation(context) { lat, lng ->
                viewModel.updateLocation(lat, lng)
                busViewModel.updateLocation(lat, lng)
            }
        }
    }

    val busListState = rememberLazyListState()
    val metrobusListState = rememberLazyListState()

    LaunchedEffect(currentFilter, searchQuery) {
        busViewModel.loadBusStops()
    }

    LaunchedEffect(busStopsList) {
        if (busStopsList.isNotEmpty() && !busListState.isScrollInProgress) {
            try {
                busListState.scrollToItem(0)
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(currentFilter, metrobusSearchQuery) {
        busViewModel.loadMetrobusStops()
    }

    LaunchedEffect(metrobusStopsList) {
        if (metrobusStopsList.isNotEmpty() && !metrobusListState.isScrollInProgress) {
            try {
                metrobusListState.scrollToItem(0)
            } catch (e: Exception) {}
        }
    }

    val currentSavedTab by busViewModel.selectedBusTabIndex.collectAsState()
    val startPage = if (initialPage in 0..2) initialPage else currentSavedTab
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, 2), pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialPage) {
        if (initialPage in 0..2 && pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        busViewModel.setSelectedBusTabIndex(pagerState.currentPage)
        if (pagerState.currentPage == 0 && !busListState.isScrollInProgress) {
            try {
                busListState.scrollToItem(0)
            } catch (e: Exception) {}
        } else if (pagerState.currentPage == 1) {
            if (!metrobusListState.isScrollInProgress) {
                try {
                    metrobusListState.scrollToItem(0)
                } catch (e: Exception) {}
            }
            busViewModel.loadMetrobusStops()
        } else if (pagerState.currentPage == 2) {
            busViewModel.fetchValenbisiStations(force = false, isUserPullRefresh = false)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentSubTab by rememberUpdatedState(pagerState.currentPage)
    val currentIsScreenVisible by rememberUpdatedState(isScreenVisible)
    DisposableEffect(lifecycleOwner, busViewModel, isScreenVisible) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (currentIsScreenVisible) {
                        busViewModel.onAppForegrounded(activeSubTab = currentSubTab)
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    busViewModel.onAppBackgrounded()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!isScreenVisible) {
            busViewModel.onAppBackgrounded()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            busViewModel.onAppBackgrounded()
        }
    }

    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage && initialPage in 0..2) {
            pagerState.scrollToPage(initialPage)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("emt_bus_screen")
        ) {
        val activeBusTabAccent = when (pagerState.currentPage) {
            0 -> if (isDarkMode) Color(0xFFEF5350) else EmtRed
            1 -> MetrobusAmber
            else -> getValenbisiBrandColor(isDarkMode)
        }
        UnifiedTabRow(
            selectedTabIndex = pagerState.currentPage,
            tabs = listOf("EMT", "Metrobús", "Valenbisi"),
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier.padding(bottom = 8.dp),
            accentColor = activeBusTabAccent
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val emtHeaderColor = if (isDarkMode) Color(0xFFEF5350) else EmtRed

                        // Search bar: "Buscar parada o línea..."
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { busViewModel.setBusSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("bus_search_bar"),
                            placeholder = {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.bus_search_unified_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.search_label),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        busViewModel.setBusSearchQuery("")
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.clear_search_desc),
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
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = emtHeaderColor,
                                unfocusedBorderColor = if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )

                        // Section 1: FAVORITAS GUARDADAS (visible when no active search query)

                        if (searchQuery.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.bus_section_favorites),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = emtHeaderColor
                                )
                            }

                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                                    .testTag("favorite_bus_stops_carousel"),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (favoriteBusStopsList.isEmpty()) {
                                    item {
                                        EmptyFavoriteBusCard(isDarkMode = isDarkMode)
                                    }
                                } else {
                                    items(favoriteBusStopsList, key = { "fav_${it.opId}" }) { stop ->
                                        val alias = busStopAliases[stop.opId]
                                        FavoriteBusStopCard(
                                            stop = stop,
                                            alias = alias,
                                            isDarkMode = isDarkMode,
                                            onCardClick = {
                                                busViewModel.selectBusStop(stop)
                                                showTimesSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Section 2 Header: PARADAS CERCANAS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sectionTitle = if (searchQuery.isNotEmpty()) {
                                androidx.compose.ui.res.stringResource(com.example.R.string.bus_section_search_results)
                            } else {
                                androidx.compose.ui.res.stringResource(com.example.R.string.bus_section_nearby)
                            }
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = emtHeaderColor
                            )
                        }

                        // Section 2 List: Vertical list of stops
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (busStopsLoading && busStopsList.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    repeat(4) {
                                        SkeletonCardItem()
                                    }
                                }
                            } else if (busStopsList.isEmpty()) {
                                val emptyMsg = if (searchQuery.isNotEmpty()) {
                                    "No se encontraron paradas para «$searchQuery»"
                                } else {
                                    androidx.compose.ui.res.stringResource(com.example.R.string.bus_no_active_stops)
                                }
                                EmptyStateCard(
                                    title = androidx.compose.ui.res.stringResource(com.example.R.string.bus_no_stops_title),
                                    message = emptyMsg,
                                    icon = Icons.Default.DirectionsBus,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp + activeTripBottomPadding),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    state = busListState
                                ) {
                                    items(busStopsList, key = { it.opId }) { stop ->
                                        val isFav = favoriteBusStops.contains(stop.opId)
                                        val alias = busStopAliases[stop.opId]
                                        BusStopCard(
                                            stop = stop,
                                            isFav = isFav,
                                            alias = alias,
                                            isDarkMode = isDarkMode,
                                            onCardClick = {
                                                busViewModel.selectBusStop(stop)
                                                showTimesSheet = true
                                            },
                                            onToggleFavorite = {
                                                busViewModel.toggleFavoriteBusStop(stop.opId)
                                            },
                                            onEditAliasClick = {
                                                editingStopForAlias = stop
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    MetrobusTab(
                        appLanguage = appLanguage,
                        metrobusStopsList = metrobusStopsList,
                        favoriteMetrobusStops = favoriteMetrobusStops.toSet(),
                        favoriteMetrobusStopsList = favoriteMetrobusStopsList,
                        metrobusStopAliases = metrobusStopAliases,
                        searchQuery = metrobusSearchQuery,
                        isLoading = metrobusStopsLoading,
                        isDarkMode = isDarkMode,
                        listState = metrobusListState,
                        onSearchQueryChanged = { busViewModel.setMetrobusSearchQuery(it) },
                        onSelectStop = { stop ->
                            busViewModel.selectMetrobusStop(stop)
                            showMetrobusTimesSheet = true
                        },
                        onToggleFavorite = { busViewModel.toggleFavoriteMetrobusStop(it) },
                        onEditAliasClick = { stop -> editingMetrobusStopForAlias = stop },
                        activeTripBottomPadding = activeTripBottomPadding
                    )
                }
                2 -> {
                    ValenbisiTab(
                        appLanguage = appLanguage,
                        valenbisiStations = valenbisiStations,
                        favoriteValenbisi = favoriteValenbisi,
                        valenbisiAliases = valenbisiAliases,
                        searchQuery = valenbisiSearchQuery,
                        isLoading = valenbisiLoading,
                        userLocation = userLocation,
                        isDarkMode = isDarkMode,
                        onSearchQueryChanged = { busViewModel.setValenbisiSearchQuery(it) },
                        onToggleFavorite = { busViewModel.toggleValenbisiFavorite(it) },
                        onSaveAlias = { stationNumber, alias -> busViewModel.saveValenbisiAlias(stationNumber, alias) },
                        onRefresh = { busViewModel.fetchValenbisiStations(force = false, isUserPullRefresh = true) },
                        onUpdateLocation = { lat, lng ->
                            viewModel.updateLocation(lat, lng)
                            busViewModel.updateLocation(lat, lng)
                        },
                        activeTripBottomPadding = activeTripBottomPadding
                    )
                }
            }
        }
    }
    
    if (showTimesSheet && selectedBusStop != null) {
        BusTimesBottomSheet(
            stop = selectedBusStop!!,
            busTimes = busTimes,
            busTimesLoading = busTimesLoading,
            isDarkMode = isDarkMode,
            texts = texts,
            alias = busStopAliases[selectedBusStop!!.opId],
            isFavorite = favoriteBusStops.contains(selectedBusStop!!.opId),
            onToggleFavorite = {
                busViewModel.toggleFavoriteBusStop(selectedBusStop!!.opId)
            },
            onDismissRequest = {
                showTimesSheet = false
                busViewModel.selectBusStop(null)
            },
            onEditAliasClick = {
                editingStopForAlias = selectedBusStop
            },
            scheduledDepartures = emtScheduledTimes,
            isScheduledLoaded = isEmtScheduledLoaded,
            isScheduledLoading = isEmtScheduledLoading,
            onLoadScheduled = {
                busViewModel.fetchEmtScheduledTimes(selectedBusStop!!.opId)
            },
            onLoadMoreScheduled = {
                busViewModel.loadMoreScheduledTimes(selectedBusStop!!.opId)
            },
            isLoadingMoreScheduled = isLoadingMoreScheduled
        )
    }

    if (showMetrobusTimesSheet && selectedMetrobusStop != null) {
        MetrobusTimesBottomSheet(
            stop = selectedMetrobusStop!!,
            times = metrobusTimes,
            isLoading = metrobusTimesLoading,
            isDarkMode = isDarkMode,
            alias = metrobusStopAliases[selectedMetrobusStop!!.idParada],
            isFavorite = favoriteMetrobusStops.contains(selectedMetrobusStop!!.idParada),
            onToggleFavorite = {
                busViewModel.toggleFavoriteMetrobusStop(selectedMetrobusStop!!.idParada)
            },
            onDismissRequest = {
                showMetrobusTimesSheet = false
                busViewModel.selectMetrobusStop(null)
            },
            onEditAliasClick = {
                editingMetrobusStopForAlias = selectedMetrobusStop
            },
            onRefresh = {
                busViewModel.fetchMetrobusTimes(selectedMetrobusStop!!.idParada, forceRefresh = false)
            },
            scheduledDepartures = metrobusScheduledTimes,
            isScheduledLoaded = isMetrobusScheduledLoaded,
            isScheduledLoading = isMetrobusScheduledLoading,
            onLoadScheduled = {
                busViewModel.fetchMetrobusScheduledTimes(selectedMetrobusStop!!.idParada)
            },
            onLoadMoreScheduled = {
                busViewModel.loadMoreMetrobusScheduledTimes(selectedMetrobusStop!!.idParada)
            },
            isLoadingMoreScheduled = isLoadingMoreMetrobusScheduled,
            appLanguage = appLanguage
        )
    }

    if (editingStopForAlias != null) {
        val stopToEdit = editingStopForAlias!!
        val existingAlias = busStopAliases[stopToEdit.opId] ?: ""
        var aliasInput by remember(editingStopForAlias) { mutableStateOf(existingAlias) }

        AlertDialog(
            onDismissRequest = { editingStopForAlias = null },
            properties = AppDialogDefaults.Properties,
            modifier = Modifier.appDialogWidth(),
            shape = AppDialogDefaults.Shape,
            containerColor = AppDialogDefaults.containerColor(),
            tonalElevation = AppDialogDefaults.TonalElevation,
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_desc_format, stopToEdit.opId.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { if (it.length <= 32) aliasInput = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_field_label)) },
                        placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.alias_placeholder)) },
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_length_format, aliasInput.length),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alias_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        busViewModel.setBusStopAlias(stopToEdit.opId, aliasInput)
                        editingStopForAlias = null
                    },
                    modifier = Modifier.testTag("save_alias_button")
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existingAlias.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                busViewModel.setBusStopAlias(stopToEdit.opId, "")
                                editingStopForAlias = null
                            }
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_delete_alias),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = { editingStopForAlias = null }) {
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_cancel))
                    }
                }
            }
        )
    }

    if (editingMetrobusStopForAlias != null) {
        val stopToEdit = editingMetrobusStopForAlias!!
        val existingAlias = metrobusStopAliases[stopToEdit.idParada] ?: ""
        var aliasInput by remember(editingMetrobusStopForAlias) { mutableStateOf(existingAlias) }

        AlertDialog(
            onDismissRequest = { editingMetrobusStopForAlias = null },
            properties = AppDialogDefaults.Properties,
            modifier = Modifier.appDialogWidth(),
            shape = AppDialogDefaults.Shape,
            containerColor = AppDialogDefaults.containerColor(),
            tonalElevation = AppDialogDefaults.TonalElevation,
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_metrobus_desc_format, stopToEdit.denominacion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { if (it.length <= 32) aliasInput = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_field_label)) },
                        placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.alias_placeholder)) },
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_length_format, aliasInput.length),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("metrobus_alias_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        busViewModel.setMetrobusStopAlias(stopToEdit.idParada, aliasInput)
                        editingMetrobusStopForAlias = null
                    },
                    modifier = Modifier.testTag("save_metrobus_alias_button")
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existingAlias.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                busViewModel.setMetrobusStopAlias(stopToEdit.idParada, "")
                                editingMetrobusStopForAlias = null
                            }
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_delete_alias),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = { editingMetrobusStopForAlias = null }) {
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_cancel))
                    }
                }
            }
        )
    }
}
}



