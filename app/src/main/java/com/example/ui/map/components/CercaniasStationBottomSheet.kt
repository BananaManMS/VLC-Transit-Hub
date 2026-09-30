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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.cercanias.CercaniasAlert
import com.example.ui.cercanias.CercaniasDeparture
import com.example.ui.cercanias.CercaniasDepartureCard
import com.example.ui.components.CercaniasDepartureSkeletonCard
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.SelectedMapItem
import kotlinx.coroutines.launch

@Composable
fun CercaniasStationBottomSheet(
    station: CercaniasStationEntity,
    departures: List<CercaniasDeparture>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    selectedLineFilters: Set<String> = emptySet(),
    onToggleLineFilter: (String) -> Unit = {},
    onClearLineFilters: () -> Unit = {},
    alerts: List<CercaniasAlert> = emptyList(),
    onNavigateToCercanias: ((String) -> Unit)? = null,
    onDirectionsClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    maxExpandedHeight: Dp = 560.dp,
    sheetState: DetailSheetState = DetailSheetState.HALF_EXPANDED,
    onSheetStateChanged: (DetailSheetState) -> Unit = {},
    onHeightPxChanged: ((Float) -> Unit)? = null,
    activeTripBottomPadding: Dp = 0.dp
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val sheetBg = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA)
    val sheetTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val sheetSubtextColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    var measuredHeaderPx by remember { mutableFloatStateOf(0f) }
    val defaultCollapsedPx = with(density) { (96.dp + activeTripBottomPadding).toPx() }
    val collapsedPx = if (measuredHeaderPx > 0f) {
        measuredHeaderPx + with(density) { (24.dp + activeTripBottomPadding).toPx() }
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
            .testTag("cercanias_station_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 0.dp)
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

            // Header: Operator Logo + Name + Actions + Badges
            CercaniasStationHeader(
                station = station,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                isFavorite = isFavorite,
                onDirectionsClick = onDirectionsClick,
                onToggleFavorite = onToggleFavorite,
                onDismiss = dismissSheet,
                onNavigateToCercanias = onNavigateToCercanias,
                selectedLineFilters = selectedLineFilters,
                onToggleLineFilter = onToggleLineFilter,
                onClearLineFilters = onClearLineFilters,
                headerDragModifier = headerDragModifier,
                modifier = Modifier.onSizeChanged { size ->
                    measuredHeaderPx = size.height.toFloat()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (appLanguage == AppLanguage.CA) "Pròximes Salides" else "Próximas Salidas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = sheetTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            val filteredDepartures = remember(departures, selectedLineFilters) {
                if (selectedLineFilters.isEmpty()) {
                    departures
                } else {
                    departures.filter { dep ->
                        val normRoute = CercaniasStationHighlightManager.normalizeLineRef(dep.routeId)
                        selectedLineFilters.any { filter ->
                            CercaniasStationHighlightManager.normalizeLineRef(filter).equals(normRoute, ignoreCase = true)
                        }
                    }
                }
            }

            if (isLoading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(3) {
                        CercaniasDepartureSkeletonCard()
                    }
                }
            } else if (filteredDepartures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp + activeTripBottomPadding)
                        .padding(bottom = activeTripBottomPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedLineFilters.isNotEmpty()) {
                            if (appLanguage == AppLanguage.CA) "No hi ha eixides per a la línia seleccionada" else "No hay salidas para la línea seleccionada"
                        } else {
                            if (appLanguage == AppLanguage.CA) "No hi ha eixides programades en aquest moment" else "No hay salidas programadas en este momento"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = sheetSubtextColor
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp + activeTripBottomPadding),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = filteredDepartures.size,
                        key = { index ->
                            val dep = filteredDepartures[index]
                            "${dep.tripId}_${dep.departureTime}_${dep.routeId}_$index"
                        }
                    ) { index ->
                        val departure = filteredDepartures[index]
                        CercaniasDepartureCard(
                            departure = departure,
                            alerts = alerts,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}
