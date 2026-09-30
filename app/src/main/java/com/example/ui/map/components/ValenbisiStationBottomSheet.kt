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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.dashboard.AppLanguage
import kotlinx.coroutines.launch

@Composable
fun ValenbisiStationBottomSheet(
    station: ValenbisiStation,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    alias: String? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onEditAlias: (() -> Unit)? = null,
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
    val textColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val cardBg = if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFE2E8F0)
    val actionBtnBg = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFF1F5F9)

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

    val scrollState = rememberScrollState()

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

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress && !heightAnimatable.isRunning) {
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
                // Si movemos el dedo hacia abajo (delta > 0) y el scroll interno ya llegó arriba -> contraer panel
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

                // Fling UP para expandir el panel ANTES de que el scroll interno haga fling
                if (velocityY < 0f && currentHeight < fullyExpandedPx - 1f) {
                    settleSheetState(velocityY)
                    return available
                }
                // Fling DOWN cuando el panel no está expandido al máximo
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
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFE2E8F0)),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .testTag("valenbisi_station_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 10.dp)
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

            // Header (Logo, title, action buttons, station display name, badges, address)
            ValenbisiStationHeader(
                station = station,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                isFavorite = isFavorite,
                alias = alias,
                onToggleFavorite = onToggleFavorite,
                onEditAlias = onEditAlias,
                onDirectionsClick = onDirectionsClick,
                onDismiss = dismissSheet,
                headerDragModifier = headerDragModifier,
                modifier = Modifier.onSizeChanged { size ->
                    measuredHeaderPx = size.height.toFloat()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Realtime Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bikes available
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = station.available.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = if (station.available == 0) subtextColor else Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Bicicletes" else "Bicicletas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Disponibles" else "Disponibles",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextColor
                        )
                    }
                }

                // Free slots
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = station.free.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Buits" else "Huecos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Lliures" else "Libres",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extra Info (Capacity, Distance)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (station.distanceText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = subtextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Distància: ${station.distanceText}" else "Distancia: ${station.distanceText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtextColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Capacitat: ${station.total}" else "Capacidad: ${station.total}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp + activeTripBottomPadding))
        }
    }
}
