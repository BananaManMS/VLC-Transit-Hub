package com.example.ui.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.dashboard.AppLanguage
import kotlinx.coroutines.launch

@Composable
fun AddressDestinationBottomSheet(
    address: NominatimResult,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean = false,
    favoriteColorHex: String? = null,
    onSaveFavorite: (() -> Unit)? = null,
    onNavigate: ((lat: Double, lon: Double, name: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    maxExpandedHeight: Dp = 560.dp,
    sheetState: DetailSheetState = DetailSheetState.HALF_EXPANDED,
    onSheetStateChanged: (DetailSheetState) -> Unit = {},
    onHeightPxChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeTripBottomPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val isValencian = appLanguage == AppLanguage.CA

    val rawDisplayName = remember(address.displayName) {
        address.displayName.replace(Regex("\\s*\\([0-9.,\\-\\s]+\\)"), "").trim()
    }

    val parts = remember(rawDisplayName) {
        rawDisplayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val title = remember(address, isValencian, rawDisplayName) {
        if (!address.placeName.isNullOrBlank()) {
            address.placeName.replace(Regex("\\s*\\([0-9.,\\-\\s]+\\)"), "").trim()
        } else if (rawDisplayName.startsWith("Ubicación seleccionada", ignoreCase = true) ||
            rawDisplayName.startsWith("Ubicació seleccionada", ignoreCase = true) ||
            rawDisplayName.startsWith("Ubicación en el mapa", ignoreCase = true) ||
            rawDisplayName.startsWith("Punt al mapa", ignoreCase = true) ||
            rawDisplayName.startsWith("Punt seleccionat", ignoreCase = true)
        ) {
            if (isValencian) "Punt seleccionat" else "Ubicación seleccionada"
        } else {
            parts.firstOrNull() ?: if (isValencian) "Punt seleccionat" else "Ubicación seleccionada"
        }
    }

    val subtitle = remember(address, isValencian, parts, title) {
        val road = address.road
        val hn = if (!address.houseNumber.isNullOrBlank()) " ${address.houseNumber}" else ""
        val area = address.suburb ?: address.city
        val pc = if (!address.postcode.isNullOrBlank()) " (${address.postcode})" else ""

        if (!road.isNullOrBlank()) {
            if (!area.isNullOrBlank() && !area.equals(road, ignoreCase = true)) {
                "$road$hn • $area$pc"
            } else {
                "$road$hn$pc"
            }
        } else if (parts.size > 1) {
            parts.drop(1).joinToString(", ")
        } else if (title == "Ubicación seleccionada" || title == "Punt seleccionat") {
            if (isValencian) "Punt al mapa" else "Punto en el mapa"
        } else {
            ""
        }
    }

    val isFavItem = isFavorite || address.category == "favorite" || address.type == "favorite" || address.placeCategory == PlaceCategory.FAVORITE
    val category = if (isFavItem) PlaceCategory.FAVORITE else address.placeCategory

    val sheetBgColor = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA)
    val textPrimaryColor = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondaryColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Subcategory display resolution
    val subcategoryLabel = remember(address) {
        val ignoredSubtypes = setOf(
            "yes", "no", "unclassified", "point", "ticket", "ticket_validator", "road", "street", "footway",
            "pedestrian", "path", "living_street", "residential", "service", "house", "building", "address",
            "node", "way", "relation", "city_gate", "monument", "castle", "ruins", "memorial",
            "archaeological_site", "tower", "citywalls"
        )
        val rawType = address.type.lowercase().trim()
        if (rawType.isNotBlank() && !ignoredSubtypes.contains(rawType)) {
            address.type.replace('_', ' ').replaceFirstChar { it.uppercase() }
        } else null
    }

    val hasRichDetails = remember(address) {
        !address.wikipedia.isNullOrBlank() ||
        !address.wikidata.isNullOrBlank() ||
        !address.openingHours.isNullOrBlank() ||
        !address.phone.isNullOrBlank() ||
        !address.email.isNullOrBlank() ||
        !address.website.isNullOrBlank() ||
        !address.startDate.isNullOrBlank() ||
        !address.historicType.isNullOrBlank() ||
        !address.fee.isNullOrBlank() ||
        !address.charge.isNullOrBlank()
    }

    var measuredContentHeightPx by remember { mutableFloatStateOf(0f) }

    val minCollapsedDp = 110.dp + activeTripBottomPadding
    val defaultHalfExpandedDp = 300.dp + activeTripBottomPadding
    val maxExpandedDp = (maxExpandedHeight + activeTripBottomPadding).coerceAtLeast(defaultHalfExpandedDp)

    val collapsedPx = with(density) { minCollapsedDp.toPx() }
    val maxExpandedPx = with(density) { maxExpandedDp.toPx() }

    // Fit snugly to measured content height (which already includes activeTripBottomPadding)
    val effectiveTargetHeightPx = remember(measuredContentHeightPx, hasRichDetails, maxExpandedPx, collapsedPx, activeTripBottomPadding) {
        if (measuredContentHeightPx > 0f) {
            // Content height already contains the 14.dp + activeTripBottomPadding spacer; add 6.dp safety padding
            val paddedContent = measuredContentHeightPx + with(density) { 6.dp.toPx() }
            if (hasRichDetails) {
                paddedContent.coerceIn(collapsedPx, maxExpandedPx)
            } else {
                // For a simple map point, snug fit directly to the measured content (no empty void!)
                paddedContent.coerceIn(collapsedPx, maxExpandedPx)
            }
        } else {
            // Initial fallback while measure hasn't completed yet: snug compact initial height
            with(density) { (170.dp + activeTripBottomPadding).toPx() }
        }
    }

    val halfExpandedPx = remember(effectiveTargetHeightPx, hasRichDetails, defaultHalfExpandedDp, density) {
        if (!hasRichDetails) {
            effectiveTargetHeightPx
        } else {
            val defaultHalfPx = with(density) { defaultHalfExpandedDp.toPx() }
            effectiveTargetHeightPx.coerceAtMost(defaultHalfPx)
        }
    }

    val fullyExpandedPx = remember(effectiveTargetHeightPx, maxExpandedPx, halfExpandedPx, hasRichDetails) {
        if (!hasRichDetails) {
            effectiveTargetHeightPx
        } else {
            effectiveTargetHeightPx.coerceIn(halfExpandedPx, maxExpandedPx)
        }
    }

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
                // Si movemos el dedo hacia abajo (delta > 0) y el contenido ya llegó al tope superior -> contraer panel
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

                if (velocityY < 0f && currentHeight < fullyExpandedPx - 1f) {
                    settleSheetState(velocityY)
                    return available
                }
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
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .testTag("address_destination_bottom_sheet"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = sheetBgColor,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        if (size.height > 0) {
                            measuredContentHeightPx = size.height.toFloat()
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Drag Handle Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(headerDragModifier)
                        .padding(top = 4.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = if (isDarkMode) Color(0xFF475569) else Color(0xFFCBD5E1),
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                    ) {}
                }

                // Header Row
                AddressDestinationHeader(
                    address = address,
                    title = title,
                    subtitle = subtitle,
                    category = category,
                    subcategoryLabel = subcategoryLabel,
                    isDarkMode = isDarkMode,
                    appLanguage = appLanguage,
                    onDismiss = dismissSheet,
                    modifier = headerDragModifier
                )

                // Detail sections (Wikipedia, Opening Hours, Fee, History, Contacts, Coordinates)
                AddressDestinationDetailSections(
                    address = address,
                    title = title,
                    category = category,
                    subcategoryLabel = subcategoryLabel,
                    appLanguage = appLanguage,
                    isDarkMode = isDarkMode,
                    context = context,
                    textSecondaryColor = textSecondaryColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row (Save Favorite, Navigate)
                AddressDestinationActions(
                    address = address,
                    title = title,
                    isFavorite = isFavorite,
                    favoriteColorHex = favoriteColorHex,
                    isValencian = isValencian,
                    onSaveFavorite = onSaveFavorite,
                    onNavigate = onNavigate
                )

                Spacer(modifier = Modifier.height(14.dp + activeTripBottomPadding))
            }
        }
    }
}
