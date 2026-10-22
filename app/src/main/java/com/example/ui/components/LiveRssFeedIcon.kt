package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Animated RSS Feed live indicator icon.
 * Features a continuous, ultra-smooth radial wave expansion from inside out
 * followed by a calm 1-second resting pause between cycles.
 */
@Composable
fun LiveRssFeedIcon(
    modifier: Modifier = Modifier.size(16.dp),
    tint: Color = Color(0xFF2ECC71),
    contentDescription: String? = "En vivo",
    isAnimated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "LiveRssTransition")
    val progress by if (isAnimated) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "LiveRssProgress"
        )
    } else {
        rememberUpdatedState(0.5f)
    }

    val semanticsModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Canvas(modifier = semanticsModifier) {
        val w = size.width
        val h = size.height
        val scale = minOf(w, h) / 24f

        // Geometry based on standard Material 24x24 RssFeed icon
        val ox = 4f * scale
        val oy = 20f * scale

        val dotCenterX = 6.18f * scale
        val dotCenterY = 17.82f * scale
        val baseDotRadius = 2.18f * scale
        val dotCenterDist = 3.08f * scale

        val baseRInner = 8.485f * scale
        val baseROuter = 14.145f * scale
        val baseStrokeWidth = 2.83f * scale

        // Active wave takes 2800ms (0.0 .. 0.667f), followed by 1400ms rest pause (0.667f .. 1.0f)
        val activeFraction = 2800f / 4200f // ~0.667f
        val isWaveActive = isAnimated && progress <= activeFraction

        val (dotIntensity, innerIntensity, outerIntensity) = if (isWaveActive) {
            val linearActive = progress / activeFraction
            // Smooth natural ease-in-out expansion
            val smoothActive = FastOutSlowInEasing.transform(linearActive.coerceIn(0f, 1f))
            val waveR = smoothActive * (22f * scale)
            val waveWidth = 9.0f * scale // Generous wide overlap for buttery smoothness

            val dInt = calculateWaveIntensity(dotCenterDist, waveR, waveWidth)
            val iInt = calculateWaveIntensity(baseRInner, waveR, waveWidth)
            val oInt = calculateWaveIntensity(baseROuter, waveR, waveWidth)
            Triple(dInt, iInt, oInt)
        } else {
            Triple(0f, 0f, 0f)
        }

        val baseAlpha = if (isAnimated) 0.35f else 1.0f

        val dotAlpha = baseAlpha + (1.0f - baseAlpha) * dotIntensity
        val innerAlpha = baseAlpha + (1.0f - baseAlpha) * innerIntensity
        val outerAlpha = baseAlpha + (1.0f - baseAlpha) * outerIntensity

        val dotRadius = baseDotRadius * (1f + 0.14f * dotIntensity)
        val rInner = baseRInner + (0.35f * scale * innerIntensity)
        val innerStroke = baseStrokeWidth * (1f + 0.12f * innerIntensity)

        val rOuter = baseROuter + (0.45f * scale * outerIntensity)
        val outerStroke = baseStrokeWidth * (1f + 0.12f * outerIntensity)

        // 1. Center origin dot
        drawCircle(
            color = tint.copy(alpha = dotAlpha),
            radius = dotRadius,
            center = Offset(dotCenterX, dotCenterY)
        )

        // 2. Inner Arc
        drawArc(
            color = tint.copy(alpha = innerAlpha),
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(ox - rInner, oy - rInner),
            size = Size(rInner * 2, rInner * 2),
            style = Stroke(width = innerStroke, cap = StrokeCap.Round)
        )

        // 3. Outer Arc
        drawArc(
            color = tint.copy(alpha = outerAlpha),
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(ox - rOuter, oy - rOuter),
            size = Size(rOuter * 2, rOuter * 2),
            style = Stroke(width = outerStroke, cap = StrokeCap.Round)
        )
    }
}

private fun calculateWaveIntensity(
    elementRadius: Float,
    currentWaveRadius: Float,
    waveWidth: Float
): Float {
    val diff = abs(elementRadius - currentWaveRadius)
    if (diff >= waveWidth) return 0f
    val factor = 1f - (diff / waveWidth)
    return 0.5f * (1f - cos(factor * PI.toFloat()))
}
