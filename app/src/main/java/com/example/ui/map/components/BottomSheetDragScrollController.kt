package com.example.ui.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun computeNearbyRestingHeightPx(
    isSearchActive: Boolean,
    nearbySheetState: SheetState,
    collapsedHeightPx: Float,
    expandedHeightPx: Float
): Float {
    if (isSearchActive) return 0f
    return when (nearbySheetState) {
        SheetState.MINIMIZED -> 0f
        SheetState.COLLAPSED -> collapsedHeightPx
        SheetState.EXPANDED -> expandedHeightPx
    }
}

internal fun computeMonotonicDismissReportedHeightPx(
    currentSheetHeightPx: Float,
    dismissStartHeightPx: Float,
    dismissTargetHeightPx: Float
): Float {
    val safeTarget = dismissTargetHeightPx.coerceAtLeast(0f)
    if (dismissStartHeightPx >= safeTarget) {
        return currentSheetHeightPx
    }
    if (dismissStartHeightPx <= 1f) {
        return safeTarget
    }
    val progress = (1f - (currentSheetHeightPx / dismissStartHeightPx)).coerceIn(0f, 1f)
    return dismissStartHeightPx + (safeTarget - dismissStartHeightPx) * progress
}

@Stable
class BottomSheetDismissController(
    private val heightAnimatable: Animatable<Float, AnimationVector1D>,
    private val coroutineScope: CoroutineScope,
    dismissTargetHeightPx: Float,
    onDismiss: () -> Unit
) {
    var isDismissing by mutableStateOf(false)
        private set
    private var dismissStartHeightPx by mutableFloatStateOf(0f)
    internal var dismissTargetHeightPx by mutableFloatStateOf(dismissTargetHeightPx)
    internal var latestOnDismiss: () -> Unit = onDismiss

    fun resetDismissState() {
        isDismissing = false
        dismissStartHeightPx = 0f
    }

    fun reportedHeightPx(currentHeightPx: Float): Float {
        if (!isDismissing) return currentHeightPx
        return computeMonotonicDismissReportedHeightPx(
            currentSheetHeightPx = currentHeightPx,
            dismissStartHeightPx = dismissStartHeightPx,
            dismissTargetHeightPx = dismissTargetHeightPx
        )
    }

    fun triggerDismiss() {
        if (isDismissing) return
        val startHeight = heightAnimatable.value
        val targetRestingHeight = dismissTargetHeightPx.coerceAtLeast(0f)
        val visualTargetHeight = if (startHeight >= targetRestingHeight) targetRestingHeight else 0f

        if (abs(startHeight - visualTargetHeight) <= 2f &&
            abs(startHeight - targetRestingHeight) <= 2f
        ) {
            latestOnDismiss()
            return
        }

        isDismissing = true
        dismissStartHeightPx = startHeight
        coroutineScope.launch {
            var cancelled = false
            try {
                heightAnimatable.animateTo(
                    targetValue = visualTargetHeight,
                    animationSpec = tween(
                        durationMillis = 190,
                        easing = FastOutSlowInEasing
                    )
                )
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } finally {
                isDismissing = false
                if (!cancelled) {
                    latestOnDismiss()
                }
            }
        }
    }
}

@Composable
fun rememberBottomSheetDismissController(
    heightAnimatable: Animatable<Float, AnimationVector1D>,
    dismissTargetHeightPx: Float,
    onDismiss: () -> Unit,
    resetKey: Any? = Unit,
    dismissSignal: Int = 0
): BottomSheetDismissController {
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(heightAnimatable) {
        BottomSheetDismissController(
            heightAnimatable = heightAnimatable,
            coroutineScope = coroutineScope,
            dismissTargetHeightPx = dismissTargetHeightPx,
            onDismiss = onDismiss
        )
    }
    controller.dismissTargetHeightPx = dismissTargetHeightPx
    controller.latestOnDismiss = onDismiss
    LaunchedEffect(resetKey) {
        controller.resetDismissState()
    }
    var lastHandledSignal by remember { mutableIntStateOf(dismissSignal) }
    LaunchedEffect(dismissSignal) {
        if (dismissSignal > lastHandledSignal) {
            lastHandledSignal = dismissSignal
            controller.triggerDismiss()
        }
    }
    return controller
}

/**
 * Reusable controller providing unified nested scrolling and header drag gestures for bottom sheets.
 * Allows expanding/collapsing the sheet from both the header and any scrollable content.
 */
class BottomSheetDragScrollController(
    val heightAnimatable: Animatable<Float, AnimationVector1D>,
    val collapsedPx: Float,
    val halfExpandedPx: Float,
    val fullyExpandedPx: Float,
    val dismissOnCollapse: Boolean,
    val onSheetStateChanged: (DetailSheetState) -> Unit,
    val onDismiss: () -> Unit,
    val coroutineScope: CoroutineScope
) {
    fun settleSheetState(velocity: Float) {
        val currentHeight = heightAnimatable.value
        val targetState = if (abs(velocity) > 300f) {
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

        if (targetState == DetailSheetState.COLLAPSED && dismissOnCollapse) {
            onDismiss()
        } else {
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

    fun createNestedScrollConnection(isContentAtTop: () -> Boolean): NestedScrollConnection {
        return object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y

                // Drag UP (delta < 0): expand panel first if not fully expanded
                if (delta < 0f && heightAnimatable.value < fullyExpandedPx - 0.5f) {
                    val newHeightToSet = (heightAnimatable.value - delta).coerceIn(collapsedPx, fullyExpandedPx)
                    val consumed = heightAnimatable.value - newHeightToSet
                    coroutineScope.launch {
                        heightAnimatable.snapTo(newHeightToSet)
                    }
                    return Offset(0f, consumed)
                }

                // Drag DOWN (delta > 0) and content is at top: contract panel immediately
                if (delta > 0f && isContentAtTop() && heightAnimatable.value > collapsedPx) {
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

    fun createHeaderDragModifier(): Modifier {
        return Modifier.pointerInput(collapsedPx, halfExpandedPx, fullyExpandedPx) {
            var totalDragAmount = 0f
            detectVerticalDragGestures(
                onDragStart = { totalDragAmount = 0f },
                onDragEnd = {
                    val dragDistance = totalDragAmount
                    val isUp = dragDistance < -15f
                    val isDown = dragDistance > 15f
                    val velocity = when {
                        isUp -> -600f
                        isDown -> 600f
                        else -> 0f
                    }
                    settleSheetState(velocity)
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
    }
}
