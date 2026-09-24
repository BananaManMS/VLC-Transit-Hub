package com.example.ui.metro

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.util.TransitHapticUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State and NestedScrollConnection enabling natural list stretching/pull-up
 * from anywhere on the LazyColumn when reaching the bottom of the list.
 *
 * Vibration behavior:
 * - Emits a single moderately strong but short vibration exactly when reaching
 *   the unlock threshold distance.
 * - No continuous ticks while sliding.
 */
@Stable
class MetroPullUpStretchState(
    val context: Context,
    val coroutineScope: CoroutineScope,
    val thresholdPx: Float,
    var onTrigger: () -> Unit
) {
    var stretchOffsetPx by mutableFloatStateOf(0f)
        private set

    var isEnabled by mutableStateOf(true)
    var isThresholdReached by mutableStateOf(false)
        private set

    private val animatable = Animatable(0f)

    fun reset() {
        isThresholdReached = false
        stretchOffsetPx = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // If stretched and user drags back down, smoothly consume stretch synchronously
            if (stretchOffsetPx < 0f && available.y > 0f) {
                val newOffset = (stretchOffsetPx + available.y).coerceAtMost(0f)
                val consumedY = newOffset - stretchOffsetPx
                stretchOffsetPx = newOffset
                updateThreshold()
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (!isEnabled) return Offset.Zero

            // Pulling up when bottom of list is reached
            if (available.y < 0f && source == NestedScrollSource.UserInput) {
                val damping = 0.40f
                val delta = available.y * damping
                stretchOffsetPx = (stretchOffsetPx + delta).coerceAtLeast(-thresholdPx * 1.5f)
                updateThreshold()
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            handleRelease()
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            handleRelease()
            return Velocity.Zero
        }
    }

    private fun updateThreshold() {
        val reached = -stretchOffsetPx >= thresholdPx
        if (reached != isThresholdReached) {
            isThresholdReached = reached
        }
    }

    fun handleRelease() {
        val shouldTrigger = -stretchOffsetPx >= thresholdPx
        if (shouldTrigger) {
            onTrigger()
        }
        isThresholdReached = false
        val currentOffset = stretchOffsetPx
        if (currentOffset != 0f) {
            coroutineScope.launch {
                animatable.snapTo(currentOffset)
                animatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    stretchOffsetPx = value
                }
                stretchOffsetPx = 0f
            }
        }
    }
}

@Composable
fun rememberMetroPullUpStretchState(
    thresholdDp: Dp = 72.dp,
    isEnabled: Boolean = true,
    onTrigger: () -> Unit
): MetroPullUpStretchState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { thresholdDp.toPx() }
    val currentOnTrigger by rememberUpdatedState(onTrigger)

    val state = remember(context, scope, thresholdPx) {
        MetroPullUpStretchState(
            context = context,
            coroutineScope = scope,
            thresholdPx = thresholdPx,
            onTrigger = { currentOnTrigger() }
        )
    }

    state.isEnabled = isEnabled
    state.onTrigger = { currentOnTrigger() }
    return state
}
