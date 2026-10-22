package com.example.ui.map.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

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
