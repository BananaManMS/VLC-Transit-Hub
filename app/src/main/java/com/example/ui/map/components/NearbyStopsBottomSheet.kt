package com.example.ui.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.example.data.database.AppDatabase
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.SelectedMapItem
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

enum class SheetState {
    MINIMIZED,
    COLLAPSED,
    EXPANDED
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyStopsBottomSheet(
    nearbyItems: List<NearbyTransitItem>,
    nearbyValenbisiStations: List<ValenbisiStation> = emptyList(),
    cameraCenterLat: Double,
    cameraCenterLon: Double,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    busStopAliases: Map<String, String>,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    onSelectItem: (SelectedMapItem) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = SheetState.COLLAPSED,
    onSheetStateChanged: (SheetState) -> Unit = {},
    onHeightChanged: (Dp) -> Unit = {},
    onHeightPxChanged: ((Float) -> Unit)? = null,
    maxExpandedHeight: Dp = 560.dp,
    activeTripBottomPadding: Dp = 0.dp,
    valenbisiEnabled: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val minimizedHeightDp = 0.dp
    val collapsedHeightDp = 240.dp
    val expandedHeightDp = maxExpandedHeight

    val minimizedHeightPx = with(density) { minimizedHeightDp.toPx() }
    val collapsedHeightPx = with(density) { (collapsedHeightDp + activeTripBottomPadding).toPx() }
    val expandedHeightPx = with(density) { (expandedHeightDp + activeTripBottomPadding).toPx() }

    val heightAnimatable = remember { Animatable(collapsedHeightPx) }

    LaunchedEffect(sheetState, expandedHeightPx, collapsedHeightPx) {
        val target = when (sheetState) {
            SheetState.MINIMIZED -> minimizedHeightPx
            SheetState.COLLAPSED -> collapsedHeightPx
            SheetState.EXPANDED -> expandedHeightPx
        }
        if (heightAnimatable.value != target) {
            heightAnimatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val currentHeightPx = heightAnimatable.value
    val panelHeight = with(density) { currentHeightPx.toDp() }

    SideEffect {
        onHeightPxChanged?.invoke(currentHeightPx)
    }

    LaunchedEffect(panelHeight) {
        onHeightChanged(panelHeight)
    }

    val backgroundColor = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA)
    val borderColor = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFE2E8F0)
    val textColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val renfeRepository = remember { RenfeRepository(context, db) }
    val okHttpClient = remember { com.example.data.network.NetworkModule.okHttpClient }
    val metrobusRepository = remember { com.example.data.repository.MetrobusRepository(db, okHttpClient) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val valenbisiListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val pageCount = if (valenbisiEnabled) 2 else 1
    val pagerState = rememberPagerState(initialPage = if (valenbisiEnabled) (selectedTab.coerceIn(0, pageCount - 1)) else 0) { pageCount }

    LaunchedEffect(selectedTab, valenbisiEnabled) {
        val targetPage = if (valenbisiEnabled) (selectedTab.coerceIn(0, pageCount - 1)) else 0
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (valenbisiEnabled && pagerState.currentPage != selectedTab) {
            onTabSelected(pagerState.currentPage)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isAppResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            isAppResumed = event == androidx.lifecycle.Lifecycle.Event.ON_RESUME ||
                    lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val visibleTransitKeys by remember(sheetState, valenbisiEnabled, panelHeight, isAppResumed) {
        derivedStateOf {
            val isTransitPage = !valenbisiEnabled || pagerState.currentPage == 0
            if (!isAppResumed || sheetState == SheetState.MINIMIZED || panelHeight < 120.dp || !isTransitPage) {
                emptySet()
            } else {
                listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
            }
        }
    }

    LaunchedEffect(cameraCenterLat, cameraCenterLon, nearbyItems) {
        if (nearbyItems.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    var totalDragAmount by remember { mutableStateOf(0f) }
    var gestureStartState by remember { mutableStateOf(SheetState.COLLAPSED) }

    val settleSheetState = remember(minimizedHeightPx, collapsedHeightPx, expandedHeightPx) {
        { velocity: Float ->
            val currentHeight = heightAnimatable.value
            val targetState = if (kotlin.math.abs(velocity) > 400f) {
                if (velocity < 0f) {
                    // Fling UP
                    if (currentHeight < collapsedHeightPx) SheetState.COLLAPSED else SheetState.EXPANDED
                } else {
                    // Fling DOWN
                    if (currentHeight > collapsedHeightPx) SheetState.COLLAPSED else SheetState.MINIMIZED
                }
            } else {
                // Settle to closest state by absolute distance
                val distMinimized = kotlin.math.abs(currentHeight - minimizedHeightPx)
                val distCollapsed = kotlin.math.abs(currentHeight - collapsedHeightPx)
                val distExpanded = kotlin.math.abs(currentHeight - expandedHeightPx)
                when {
                    distMinimized <= distCollapsed && distMinimized <= distExpanded -> SheetState.MINIMIZED
                    distCollapsed <= distMinimized && distCollapsed <= distExpanded -> SheetState.COLLAPSED
                    else -> SheetState.EXPANDED
                }
            }
            onSheetStateChanged(targetState)
            coroutineScope.launch {
                val targetPx = when (targetState) {
                    SheetState.MINIMIZED -> minimizedHeightPx
                    SheetState.COLLAPSED -> collapsedHeightPx
                    SheetState.EXPANDED -> expandedHeightPx
                }
                heightAnimatable.animateTo(
                    targetValue = targetPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    // Auto-settle sheet state when user finishes dragging/scrolling the lists
    val isAnyScrollInProgress = listState.isScrollInProgress || valenbisiListState.isScrollInProgress
    LaunchedEffect(isAnyScrollInProgress) {
        if (!isAnyScrollInProgress) {
            val currentHeight = heightAnimatable.value
            val isStable = kotlin.math.abs(currentHeight - minimizedHeightPx) < 1f ||
                           kotlin.math.abs(currentHeight - collapsedHeightPx) < 1f ||
                           kotlin.math.abs(currentHeight - expandedHeightPx) < 1f
            if (!isStable) {
                settleSheetState(0f)
            }
        }
    }

    val nestedScrollConnection = remember(minimizedHeightPx, collapsedHeightPx, expandedHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y

                // Si movemos el dedo hacia arriba (delta < 0), expandimos el panel primero
                if (delta < 0f && heightAnimatable.value < expandedHeightPx) {
                    val newHeightToSet = (heightAnimatable.value - delta).coerceIn(minimizedHeightPx, expandedHeightPx)
                    val consumed = heightAnimatable.value - newHeightToSet
                    coroutineScope.launch {
                        heightAnimatable.snapTo(newHeightToSet)
                    }
                    return Offset(0f, consumed)
                }

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y

                // Hacia abajo (dedo baja, delta > 0) y la lista ya no puede hacer scroll
                // Solo reaccionamos a los gestos del usuario (UserInput/Drag), no a los flings residuales (SideEffect/Fling)
                val isUserInput = source == NestedScrollSource.UserInput || source == NestedScrollSource.Drag
                
                if (isUserInput && delta > 0f) {
                    val minHeight = if (sheetState == SheetState.EXPANDED) collapsedHeightPx else minimizedHeightPx
                    if (heightAnimatable.value > minHeight) {
                        val newHeightToSet = (heightAnimatable.value - delta).coerceIn(minHeight, expandedHeightPx)
                        val consumedHeight = heightAnimatable.value - newHeightToSet
                        coroutineScope.launch {
                            heightAnimatable.snapTo(newHeightToSet)
                        }
                        return Offset(0f, consumedHeight)
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val velocityY = available.y
                val currentHeight = heightAnimatable.value
                val isStable = kotlin.math.abs(currentHeight - minimizedHeightPx) < 1f ||
                               kotlin.math.abs(currentHeight - collapsedHeightPx) < 1f ||
                               kotlin.math.abs(currentHeight - expandedHeightPx) < 1f

                // Fling UP para expandir el panel ANTES de que la lista haga fling
                if (velocityY < 0f && currentHeight < expandedHeightPx - 1f) {
                    settleSheetState(velocityY)
                    return available
                }
                
                // Si el panel quedó flotando a la mitad, interceptamos el fling para asentarlo
                if (!isStable) {
                    settleSheetState(velocityY)
                    return available
                } else {
                    // Si está en una posición estable físicamente pero el estado no coincide, sincronizamos el estado
                    when {
                        kotlin.math.abs(currentHeight - minimizedHeightPx) < 1f && sheetState != SheetState.MINIMIZED -> {
                            onSheetStateChanged(SheetState.MINIMIZED)
                        }
                        kotlin.math.abs(currentHeight - collapsedHeightPx) < 1f && sheetState != SheetState.COLLAPSED -> {
                            onSheetStateChanged(SheetState.COLLAPSED)
                        }
                        kotlin.math.abs(currentHeight - expandedHeightPx) < 1f && sheetState != SheetState.EXPANDED -> {
                            onSheetStateChanged(SheetState.EXPANDED)
                        }
                    }
                }

                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // No transferimos el fling sobrante al panel para evitar que se colapse
                // inesperadamente cuando la lista llega al tope.
                return Velocity.Zero
            }
        }
    }

    val showPill = sheetState == SheetState.MINIMIZED || (panelHeight <= 30.dp && !heightAnimatable.isRunning)

    if (showPill) {
        Surface(
            onClick = {
                onSheetStateChanged(SheetState.COLLAPSED)
                coroutineScope.launch {
                    heightAnimatable.animateTo(
                        targetValue = collapsedHeightPx,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            color = backgroundColor,
            shadowElevation = 4.dp,
            modifier = modifier
                .padding(bottom = 16.dp + activeTripBottomPadding)
                .testTag("nearby_stops_reopen_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.nearby_transit_header),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = "Expandir",
                    tint = subtextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = backgroundColor,
            shadowElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth()
                .height(panelHeight)
                .testTag("nearby_stops_bottom_sheet")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: draggable to expand/collapse and clickable as fallback
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(sheetState) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    totalDragAmount = 0f
                                    gestureStartState = sheetState
                                },
                                onDragEnd = {
                                    val dragDistance = totalDragAmount
                                    val currentH = heightAnimatable.value
                                    val thresholdPx = with(density) { 50.dp.toPx() }

                                    val upperMid = (collapsedHeightPx + expandedHeightPx) * 0.55f
                                    val lowerMid = (minimizedHeightPx + collapsedHeightPx) * 0.55f

                                    val targetState = when {
                                        currentH >= upperMid -> {
                                            if (dragDistance > thresholdPx) SheetState.COLLAPSED else SheetState.EXPANDED
                                        }
                                        currentH <= lowerMid -> {
                                            if (dragDistance < -thresholdPx) SheetState.COLLAPSED else SheetState.MINIMIZED
                                        }
                                        else -> {
                                            when {
                                                dragDistance < -thresholdPx || currentH > collapsedHeightPx + with(density) { 40.dp.toPx() } -> SheetState.EXPANDED
                                                dragDistance > thresholdPx || currentH < collapsedHeightPx - with(density) { 40.dp.toPx() } -> SheetState.MINIMIZED
                                                else -> SheetState.COLLAPSED
                                            }
                                        }
                                    }

                                    onSheetStateChanged(targetState)
                                    coroutineScope.launch {
                                        val target = when (targetState) {
                                            SheetState.MINIMIZED -> minimizedHeightPx
                                            SheetState.COLLAPSED -> collapsedHeightPx
                                            SheetState.EXPANDED -> expandedHeightPx
                                        }
                                        heightAnimatable.animateTo(
                                            targetValue = target,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        val target = when (sheetState) {
                                            SheetState.MINIMIZED -> minimizedHeightPx
                                            SheetState.COLLAPSED -> collapsedHeightPx
                                            SheetState.EXPANDED -> expandedHeightPx
                                        }
                                        heightAnimatable.animateTo(
                                            targetValue = target,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragAmount += dragAmount
                                    // dragAmount is negative when dragging up (increases height), positive when dragging down (decreases height)
                                    val newHeight = (heightAnimatable.value - dragAmount).coerceIn(minimizedHeightPx, expandedHeightPx)
                                    coroutineScope.launch {
                                        heightAnimatable.snapTo(newHeight)
                                    }
                                }
                            )
                        }
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable {
                            onSheetStateChanged(
                                when (sheetState) {
                                    SheetState.MINIMIZED -> SheetState.COLLAPSED
                                    SheetState.COLLAPSED -> SheetState.EXPANDED
                                    SheetState.EXPANDED -> SheetState.COLLAPSED
                                }
                            )
                        }
                        .padding(top = 10.dp, bottom = 4.dp, start = 20.dp, end = 20.dp)
                ) {
                    // Drag handle pill
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .background(
                                color = if (isDarkMode) Color(0xFF475569) else Color(0xFFCBD5E1),
                                shape = CircleShape
                            )
                    )
                }

                if (panelHeight >= 120.dp) {
                    if (valenbisiEnabled) {
                        TabRow(
                            selectedPageIndex = pagerState.currentPage,
                            onTabSelected = { page ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(page)
                                }
                            },
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        if (page == 0) {
                            // Page 0: Nearby Transit List
                            if (nearbyItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 24.dp + activeTripBottomPadding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.nearby_no_stops_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = subtextColor
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(nestedScrollConnection)
                                        .padding(horizontal = 16.dp),
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + activeTripBottomPadding),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(nearbyItems, key = { it.key }) { item ->
                                        NearbyTransitCard(
                                            item = item,
                                            isVisible = item.key in visibleTransitKeys,
                                            cameraCenterLat = cameraCenterLat,
                                            cameraCenterLon = cameraCenterLon,
                                            isDarkMode = isDarkMode,
                                            appLanguage = appLanguage,
                                            busStopAliases = busStopAliases,
                                            renfeRepository = renfeRepository,
                                            metrobusRepository = metrobusRepository,
                                            okHttpClient = okHttpClient,
                                            onClick = {
                                                when (item) {
                                                    is NearbyTransitItem.Bus -> {
                                                        onSelectItem(SelectedMapItem.BusStop(item.stop, item.emtStopModel))
                                                    }
                                                    is NearbyTransitItem.Metro -> {
                                                        onSelectItem(SelectedMapItem.Metro(item.station))
                                                    }
                                                    is NearbyTransitItem.Cercanias -> {
                                                        onSelectItem(SelectedMapItem.Cercanias(item.station))
                                                    }
                                                    is NearbyTransitItem.Metrobus -> {
                                                        onSelectItem(SelectedMapItem.MetrobusStopItem(item.stop, item.metrobusModel))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            // Page 1: Nearby Valenbisi Stations List (Limited to 30)
                            val limitedStations = remember(nearbyValenbisiStations) {
                                nearbyValenbisiStations.take(30)
                            }
                            if (limitedStations.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 24.dp + activeTripBottomPadding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.nearby_no_valenbisi_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = subtextColor
                                    )
                                }
                            } else {
                                LaunchedEffect(limitedStations) {
                                    if (limitedStations.isNotEmpty()) {
                                        valenbisiListState.scrollToItem(0)
                                    }
                                }
                                LazyColumn(
                                    state = valenbisiListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(nestedScrollConnection)
                                        .padding(horizontal = 16.dp),
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + activeTripBottomPadding),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(limitedStations, key = { "VALENBISI_${it.gid}" }) { station ->
                                        ValenbisiStationCard(
                                            station = station,
                                            isDarkMode = isDarkMode,
                                            onClick = {
                                                onSelectItem(SelectedMapItem.Valenbisi(station))
                                            }
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

@Composable
private fun TabRow(
    selectedPageIndex: Int,
    onTabSelected: (Int) -> Unit,
    isDarkMode: Boolean,
    appLanguage: AppLanguage
) {
    val containerBg = if (isDarkMode) Color(0xFF222222) else Color(0xFFE5E7EB)
    val activeBg = if (isDarkMode) Color(0xFF333333) else Color.White
    val textColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val unselectedColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp)
            .background(containerBg, shape = RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab 1: Transporte Cercano
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    color = if (selectedPageIndex == 0) activeBg else Color.Transparent
                )
                .clickable { onTabSelected(0) }
                .padding(horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = if (selectedPageIndex == 0) MaterialTheme.colorScheme.primary else unselectedColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.header_transport_tab),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedPageIndex == 0) textColor else unselectedColor
                )
            }
        }

        // Tab 2: Valenbisi
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    color = if (selectedPageIndex == 1) activeBg else Color.Transparent
                )
                .clickable { onTabSelected(1) }
                .padding(horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBike,
                    contentDescription = null,
                    tint = if (selectedPageIndex == 1) Color(0xFF10B981) else unselectedColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Valenbisi",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedPageIndex == 1) textColor else unselectedColor
                )
            }
        }
    }
}
