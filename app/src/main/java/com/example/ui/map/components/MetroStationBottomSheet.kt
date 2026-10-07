package com.example.ui.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.ui.components.MetroDepartureSkeletonCard
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import com.example.ui.map.SelectedMapItem
import com.example.ui.metro.MetroViewModel
import com.example.ui.metro.RealTimeDeparture
import kotlinx.coroutines.launch

@Composable
fun MetroStationBottomSheet(
    station: MetroStation,
    departures: List<RealTimeDeparture>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    texts: Translation,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onNavigateToMetro: ((String) -> Unit)? = null,
    metroViewModel: MetroViewModel? = null,
    onDirectionsClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    maxExpandedHeight: Dp = 560.dp,
    sheetState: DetailSheetState = DetailSheetState.HALF_EXPANDED,
    onSheetStateChanged: (DetailSheetState) -> Unit = {},
    onHeightPxChanged: ((Float) -> Unit)? = null,
    activeTripBottomPadding: Dp = 0.dp
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))
    val coroutineScope = rememberCoroutineScope()

    val sheetBg = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA)
    val sheetTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val sheetSubtextColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    var measuredHeaderPx by remember { mutableFloatStateOf(0f) }
    val defaultCollapsedPx = with(density) { (96.dp + activeTripBottomPadding).toPx() }
    val collapsedPx = if (measuredHeaderPx > 0f) {
        measuredHeaderPx + with(density) { (26.dp + activeTripBottomPadding).toPx() }
    } else {
        defaultCollapsedPx
    }

    val halfExpandedDp = 340.dp + activeTripBottomPadding
    val fullyExpandedDp = (maxExpandedHeight + activeTripBottomPadding).coerceAtLeast(halfExpandedDp)

    val halfExpandedPx = with(density) { halfExpandedDp.toPx() }
    val fullyExpandedPx = with(density) { fullyExpandedDp.toPx() }

    val heightAnimatable = remember {
        Animatable(
            when (sheetState) {
                DetailSheetState.COLLAPSED -> collapsedPx
                DetailSheetState.HALF_EXPANDED -> halfExpandedPx
                DetailSheetState.FULLY_EXPANDED -> fullyExpandedPx
            }
        )
    }

    LaunchedEffect(sheetState, collapsedPx, halfExpandedPx, fullyExpandedPx) {
        val target = when (sheetState) {
            DetailSheetState.COLLAPSED -> collapsedPx
            DetailSheetState.HALF_EXPANDED -> halfExpandedPx
            DetailSheetState.FULLY_EXPANDED -> fullyExpandedPx
        }
        if (kotlin.math.abs(heightAnimatable.value - target) > 1f && heightAnimatable.targetValue != target) {
            heightAnimatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val currentHeightPx = heightAnimatable.value
    val panelHeight = with(density) { currentHeightPx.toDp() }

    SideEffect {
        onHeightPxChanged?.invoke(currentHeightPx)
    }

    DisposableEffect(Unit) {
        onDispose {
            onHeightPxChanged?.invoke(0f)
        }
    }

    val dismissSheet: () -> Unit = {
        onDismiss()
    }

    val listState = rememberLazyListState()

    val settleSheetState = remember(collapsedPx, halfExpandedPx, fullyExpandedPx) {
        { velocity: Float ->
            val currentHeight = heightAnimatable.value
            val targetState = if (kotlin.math.abs(velocity) > 300f) {
                if (velocity < 0f) {
                    if (currentHeight < halfExpandedPx) DetailSheetState.HALF_EXPANDED else DetailSheetState.FULLY_EXPANDED
                } else {
                    if (currentHeight > halfExpandedPx) DetailSheetState.HALF_EXPANDED else DetailSheetState.COLLAPSED
                }
            } else {
                val upperMid = (halfExpandedPx + fullyExpandedPx) * 0.5f
                val lowerMid = (collapsedPx + halfExpandedPx) * 0.5f
                when {
                    currentHeight >= upperMid -> DetailSheetState.FULLY_EXPANDED
                    currentHeight >= lowerMid -> DetailSheetState.HALF_EXPANDED
                    else -> DetailSheetState.COLLAPSED
                }
            }

            onSheetStateChanged(targetState)
            coroutineScope.launch {
                val targetPx = when (targetState) {
                    DetailSheetState.COLLAPSED -> collapsedPx
                    DetailSheetState.HALF_EXPANDED -> halfExpandedPx
                    DetailSheetState.FULLY_EXPANDED -> fullyExpandedPx
                }
                heightAnimatable.animateTo(
                    targetValue = targetPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && !heightAnimatable.isRunning) {
            val currentHeight = heightAnimatable.value
            val isStable = kotlin.math.abs(currentHeight - collapsedPx) < 2f ||
                           kotlin.math.abs(currentHeight - halfExpandedPx) < 2f ||
                           kotlin.math.abs(currentHeight - fullyExpandedPx) < 2f
            if (!isStable && currentHeight > 0f) {
                settleSheetState(0f)
            }
        }
    }

    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    val headerDragModifier = Modifier.pointerInput(collapsedPx, halfExpandedPx, fullyExpandedPx) {
        detectVerticalDragGestures(
            onDragStart = { totalDragAmount = 0f },
            onDragEnd = {
                val dragDistance = totalDragAmount
                val currentH = heightAnimatable.value
                val isUp = dragDistance < -15f
                val isDown = dragDistance > 15f

                val targetState = when {
                    isUp -> {
                        if (currentH < halfExpandedPx) DetailSheetState.HALF_EXPANDED else DetailSheetState.FULLY_EXPANDED
                    }
                    isDown -> {
                        if (currentH > halfExpandedPx) DetailSheetState.HALF_EXPANDED else DetailSheetState.COLLAPSED
                    }
                    else -> {
                        val upperMid = (halfExpandedPx + fullyExpandedPx) * 0.5f
                        val lowerMid = (collapsedPx + halfExpandedPx) * 0.5f
                        when {
                            currentH >= upperMid -> DetailSheetState.FULLY_EXPANDED
                            currentH >= lowerMid -> DetailSheetState.HALF_EXPANDED
                            else -> DetailSheetState.COLLAPSED
                        }
                    }
                }
                onSheetStateChanged(targetState)
                coroutineScope.launch {
                    val targetPx = when (targetState) {
                        DetailSheetState.COLLAPSED -> collapsedPx
                        DetailSheetState.HALF_EXPANDED -> halfExpandedPx
                        DetailSheetState.FULLY_EXPANDED -> fullyExpandedPx
                    }
                    heightAnimatable.animateTo(
                        targetValue = targetPx,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                totalDragAmount += dragAmount
                coroutineScope.launch {
                    val newTarget = (heightAnimatable.value - dragAmount).coerceIn(collapsedPx, fullyExpandedPx)
                    heightAnimatable.snapTo(newTarget)
                }
            }
        )
    }

    val nestedScrollConnection = remember(collapsedPx, halfExpandedPx, fullyExpandedPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y

                // Si movemos el dedo hacia arriba (delta < 0), expandimos el panel primero
                if (delta < 0f && heightAnimatable.value < fullyExpandedPx - 0.5f) {
                    val newHeightToSet = (heightAnimatable.value - delta).coerceIn(collapsedPx, fullyExpandedPx)
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
                // Si movemos el dedo hacia abajo (delta > 0) y la lista ya ha alcanzado el tope superior -> contraer panel
                if (delta > 0f && heightAnimatable.value > collapsedPx) {
                    val newHeightToSet = (heightAnimatable.value - delta).coerceIn(collapsedPx, fullyExpandedPx)
                    val consumedHeight = heightAnimatable.value - newHeightToSet
                    coroutineScope.launch {
                        heightAnimatable.snapTo(newHeightToSet)
                    }
                    return Offset(0f, consumedHeight)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val velocityY = available.y
                val currentHeight = heightAnimatable.value

                // Fling UP para expandir el panel antes de que la lista interna haga fling
                if (velocityY < 0f && currentHeight < fullyExpandedPx - 1f) {
                    settleSheetState(velocityY)
                    return available
                }
                // Fling DOWN cuando el panel no está completamente expandido
                if (velocityY > 0f && currentHeight < fullyExpandedPx - 1f) {
                    settleSheetState(velocityY)
                    return available
                }

                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val velocityY = available.y
                val currentHeight = heightAnimatable.value
                if (velocityY > 0f && currentHeight > collapsedPx) {
                    settleSheetState(velocityY)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = sheetBg,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .testTag("metro_station_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 0.dp)
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .then(headerDragModifier),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF475569) else Color(0xFFCBD5E1))
                )
            }

            // Header Row (Station Info + Action Buttons + Badges)
            MetroStationHeader(
                station = station,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                isFavorite = isFavorite,
                onDirectionsClick = onDirectionsClick,
                onToggleFavorite = onToggleFavorite,
                onDismiss = dismissSheet,
                onNavigateToMetro = onNavigateToMetro,
                headerDragModifier = headerDragModifier,
                modifier = Modifier.onSizeChanged { size ->
                    measuredHeaderPx = size.height.toFloat()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            val aforo = departures.firstOrNull { it.aforoBloqueado != null }?.aforoBloqueado
                ?: com.example.data.repository.RealTimeTransitRepository.getAforoBloqueadoForStation(station.id)

            if (aforo != null && (aforo.activo || !aforo.getFormattedTimeSpan().isNullOrEmpty())) {
                com.example.ui.metro.AforoBloqueadoCard(
                    aforo = aforo,
                    appLanguage = appLanguage,
                    isDarkMode = isDarkMode,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Text(
                text = "Próximas Salidas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = sheetTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(3) {
                        MetroDepartureSkeletonCard()
                    }
                }
            } else if (departures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 20.dp + activeTripBottomPadding),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isOnline) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isDarkMode) Color(0xFFEF5350) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = sheetTextColor
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "No es poden consultar les eixides en temps real." else "No se pueden consultar las salidas en tiempo real.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sheetSubtextColor,
                                textAlign = TextAlign.Center
                            )
                            if (onNavigateToMetro != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { onNavigateToMetro.invoke(station.id) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Veure eixides programades" else "Ver salidas programadas",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "No hi ha eixides en temps real en este moment" else "No hay salidas en tiempo real en este momento",
                                style = MaterialTheme.typography.bodyMedium,
                                color = sheetSubtextColor
                            )
                            if (onNavigateToMetro != null) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { onNavigateToMetro.invoke(station.id) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Veure eixides programades" else "Ver salidas programadas",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val resolvedMetroViewModel = metroViewModel ?: androidx.lifecycle.viewmodel.compose.viewModel<MetroViewModel>()
                val lineStationsMap by resolvedMetroViewModel.lineStationsState.collectAsState()
                val lineDepartureGroups = remember(
                    departures,
                    station.id,
                    station.name,
                    lineStationsMap,
                    appLanguage,
                    texts,
                    isDarkMode
                ) {
                    com.example.ui.metro.MetroMapper.groupDeparturesByLineAndDirection(
                        departures = departures,
                        currentStationId = station.id,
                        currentStationName = station.name,
                        lineStationsMap = lineStationsMap,
                        appLanguage = appLanguage,
                        texts = texts,
                        isDarkMode = isDarkMode,
                        sharedLineDigitsGetter = { digit -> resolvedMetroViewModel.getSharedLineDigits(digit) }
                    )
                }

                val isMixedMetroTramStation = remember(station) {
                    val lines = station.lines
                    val hasMetro = lines.any { l ->
                        val digit = l.filter { it.isDigit() }
                        digit in setOf("1", "2", "3", "5", "7", "9")
                    }
                    val hasTram = lines.any { l ->
                        val digit = l.filter { it.isDigit() }
                        digit in setOf("4", "6", "8", "10", "11", "12")
                    }
                    hasMetro && hasTram
                }

                val (metroLineGroups, tramLineGroups) = remember<Pair<List<com.example.ui.metro.LineDeparturesGroupUiModel>, List<com.example.ui.metro.LineDeparturesGroupUiModel>>>(
                    lineDepartureGroups,
                    isMixedMetroTramStation
                ) {
                    if (!isMixedMetroTramStation) {
                        Pair(lineDepartureGroups, emptyList())
                    } else {
                        val metro = mutableListOf<com.example.ui.metro.LineDeparturesGroupUiModel>()
                        val tram = mutableListOf<com.example.ui.metro.LineDeparturesGroupUiModel>()
                        lineDepartureGroups.forEach { group ->
                            val digit = group.lineId.filter { it.isDigit() }
                            if (digit in setOf("4", "6", "8", "10", "11", "12")) {
                                tram.add(group)
                            } else {
                                metro.add(group)
                            }
                        }
                        Pair(metro.toList(), tram.toList())
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp + activeTripBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isMixedMetroTramStation) {
                        if (metroLineGroups.isNotEmpty()) {
                            item(key = "header_metro_section") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 2.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subway,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Metro",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
                                    )
                                }
                            }
                            items(
                                items = metroLineGroups,
                                key = { group -> "metro_group_${group.lineId}_${group.primaryDestination}" }
                            ) { group ->
                                com.example.ui.metro.MetroLineDepartureCard(
                                    group = group,
                                    metroViewModel = resolvedMetroViewModel,
                                    appLanguage = appLanguage,
                                    texts = texts,
                                    isDarkMode = isDarkMode,
                                    onExpired = {}
                                )
                            }
                        }

                        if (tramLineGroups.isNotEmpty()) {
                            item(key = "header_tram_section") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 2.dp, top = 6.dp, bottom = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tram,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Tramvia" else "Tranvía",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
                                    )
                                }
                            }
                            items(
                                items = tramLineGroups,
                                key = { group -> "tram_group_${group.lineId}_${group.primaryDestination}" }
                            ) { group ->
                                com.example.ui.metro.MetroLineDepartureCard(
                                    group = group,
                                    metroViewModel = resolvedMetroViewModel,
                                    appLanguage = appLanguage,
                                    texts = texts,
                                    isDarkMode = isDarkMode,
                                    onExpired = {}
                                )
                            }
                        }
                    } else {
                        items(
                            items = lineDepartureGroups,
                            key = { group -> "group_${group.lineId}_${group.primaryDestination}" }
                        ) { group ->
                            com.example.ui.metro.MetroLineDepartureCard(
                                group = group,
                                metroViewModel = resolvedMetroViewModel,
                                appLanguage = appLanguage,
                                texts = texts,
                                isDarkMode = isDarkMode,
                                onExpired = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MapMetroDepartureListItem(
    departure: com.example.ui.metro.RealTimeDeparture,
    appLanguage: com.example.ui.dashboard.AppLanguage,
    texts: com.example.ui.dashboard.Translation,
    isDarkMode: Boolean,
    metroViewModel: com.example.ui.metro.MetroViewModel?
) {
    val cardTextColor = if (isDarkMode) Color.White else Color.Black
    val lineIncidents = remember(departure.lineId, metroViewModel) {
        metroViewModel?.getIncidentsForLine(departure.lineId) ?: emptyList()
    }

    com.example.ui.theme.UnifiedAppCard(
        modifier = Modifier.testTag("map_metro_dep_${departure.lineId}_${departure.destination}"),
        onClick = {
            metroViewModel?.selectDepartureDetails(departure)
        },
        startContent = {
            com.example.ui.metro.MetroLineBadge(
                lineId = departure.lineId,
                fallbackColorHex = departure.colorHex,
                size = 36.dp
            )
        },
        centerContent = {
            Column {
                Text(
                    text = departure.destination,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = cardTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lineIncidents.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (appLanguage == com.example.ui.dashboard.AppLanguage.CA) "${lineIncidents.size} avisos" else "${lineIncidents.size} avisos",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = if (appLanguage == com.example.ui.dashboard.AppLanguage.CA) "Sense avisos" else "Sin avisos",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        },
        endContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (departure.minutesRemaining <= 0) "En estación" else "${departure.minutesRemaining} min",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (departure.minutesRemaining <= 2) MaterialTheme.colorScheme.error else cardTextColor
                )
            }
        }
    )
}
