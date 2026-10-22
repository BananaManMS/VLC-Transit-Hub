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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
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
    activeTripBottomPadding: Dp = 0.dp,
    dismissOnCollapse: Boolean = false
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

    val controller = remember(collapsedPx, halfExpandedPx, fullyExpandedPx, sheetState, dismissOnCollapse) {
        BottomSheetDragScrollController(
            heightAnimatable = heightAnimatable,
            collapsedPx = collapsedPx,
            halfExpandedPx = halfExpandedPx,
            fullyExpandedPx = fullyExpandedPx,
            dismissOnCollapse = dismissOnCollapse,
            onSheetStateChanged = onSheetStateChanged,
            onDismiss = onDismiss,
            coroutineScope = coroutineScope
        )
    }

    val nestedScrollConnection = remember(controller) {
        controller.createNestedScrollConnection {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val headerDragModifier = remember(controller) {
        controller.createHeaderDragModifier()
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && !heightAnimatable.isRunning) {
            val currentHeight = heightAnimatable.value
            val isStable = kotlin.math.abs(currentHeight - collapsedPx) < 2f ||
                           kotlin.math.abs(currentHeight - halfExpandedPx) < 2f ||
                           kotlin.math.abs(currentHeight - fullyExpandedPx) < 2f
            if (!isStable && currentHeight > 0f) {
                controller.settleSheetState(0f)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = sheetBg,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
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
                text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_next_departures_title),
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

            val todayDepartures = remember(filteredDepartures) { filteredDepartures.filter { !it.isTomorrow } }
            val tomorrowDepartures = remember(filteredDepartures) { filteredDepartures.filter { it.isTomorrow } }

            LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp + activeTripBottomPadding),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    items(3) {
                        CercaniasDepartureSkeletonCard()
                    }
                } else if (filteredDepartures.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp + activeTripBottomPadding)
                                .padding(bottom = activeTripBottomPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedLineFilters.isNotEmpty()) {
                                    androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_departures_for_line)
                                } else {
                                    androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_departures_scheduled_now)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = sheetSubtextColor
                            )
                        }
                    }
                } else {
                    if (todayDepartures.isNotEmpty()) {
                        items(
                            count = todayDepartures.size,
                            key = { index ->
                                val dep = todayDepartures[index]
                                "${dep.tripId}_${dep.departureTime}_${dep.routeId}_today_$index"
                            }
                        ) { index ->
                            val departure = todayDepartures[index]
                            CercaniasDepartureCard(
                                departure = departure,
                                alerts = alerts,
                                isDarkMode = isDarkMode,
                                appLanguage = appLanguage,
                                onClick = {}
                            )
                        }
                    }

                    if (tomorrowDepartures.isNotEmpty()) {
                        item(key = "sheet_header_tomorrow_departures") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (todayDepartures.isNotEmpty()) 10.dp else 2.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_tomorrow),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    thickness = 1.dp
                                )
                            }
                        }

                        items(
                            count = tomorrowDepartures.size,
                            key = { index ->
                                val dep = tomorrowDepartures[index]
                                "${dep.tripId}_${dep.departureTime}_${dep.routeId}_tomorrow_$index"
                            }
                        ) { index ->
                            val departure = tomorrowDepartures[index]
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
}
