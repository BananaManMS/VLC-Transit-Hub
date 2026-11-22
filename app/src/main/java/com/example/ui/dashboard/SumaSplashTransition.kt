package com.example.ui.dashboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.splashscreen.SplashScreenViewProvider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val WaveSweepInterpolator = PathInterpolator(0.32f, 0.0f, 0.18f, 1.0f)
private val RippleInterpolator = PathInterpolator(0.16f, 0.84f, 0.32f, 1.0f)
private val JumpLaunchInterpolator = PathInterpolator(0.20f, 0.82f, 0.28f, 1.0f)
private val BackdropFadeInterpolator = PathInterpolator(0.25f, 0.0f, 0.25f, 1.0f)
private val LogoFadeInterpolator = PathInterpolator(0.14f, 0.88f, 0.30f, 1.0f)

private const val COLOR_VLC_RED = 0xFFFF6B2C.toInt()
private const val COLOR_WAVE_CORAL = 0xFFFFB088.toInt()
private const val COLOR_WAVE_WHITE = 0xFFFFFFFF.toInt()

/**
 * Transición de salida monocroma y física sobre la SplashScreenView nativa del sistema:
 * 1) Ondas luminosas coral/blanco recorren el trazo V→ + doble onda expansiva roja de señal.
 * 2) Compresión elástica hacia abajo (0.84x) cogiendo impulso.
 * 3) Salto hacia arriba con zoom y desvanecimiento rápido del logo nada más despegar
 *    junto a la disolución del fondo para revelar Inicio directamente debajo sin fundidos a negro.
 */
fun runSumaSplashExitAnimation(
    splashScreenView: SplashScreenViewProvider,
    hasAlreadyPlayed: Boolean,
    onStarted: () -> Unit,
    onFinished: () -> Unit
) {
    if (hasAlreadyPlayed) {
        splashScreenView.remove()
        onFinished()
        return
    }
    onStarted()

    val rootView = splashScreenView.view as? ViewGroup
    if (rootView == null) {
        splashScreenView.view.animate()
            .alpha(0f)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                splashScreenView.remove()
                onFinished()
            }
            .start()
        return
    }

    val iconView = runCatching { splashScreenView.iconView }.getOrNull()
    val ribbonOverlay = SplashSignalOverlayView(context = rootView.context)
    rootView.addView(
        ribbonOverlay,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    val bgDrawable = rootView.background?.mutate()
    val density = rootView.resources.displayMetrics.density
    val crouchOffsetPx = 8f * density
    val jumpOffsetPx = -70f * density

    var cleanedUp = false
    fun finishAndRemove() {
        if (cleanedUp) return
        cleanedUp = true
        runCatching { rootView.removeView(ribbonOverlay) }
        runCatching { splashScreenView.remove() }
        onFinished()
    }

    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1080L
        interpolator = LinearInterpolator()
        addUpdateListener { va ->
            val t = (va.animatedValue as Float).coerceIn(0f, 1f)

            // 1. Ondas al terminar de cargar (0.00 -> 0.50)
            val sweepProgress = WaveSweepInterpolator.getInterpolation((t / 0.46f).coerceIn(0f, 1f))
            val ring1Progress = if (t >= 0.06f) {
                RippleInterpolator.getInterpolation(((t - 0.06f) / 0.36f).coerceIn(0f, 1f))
            } else 0f
            val ring2Progress = if (t >= 0.14f) {
                RippleInterpolator.getInterpolation(((t - 0.14f) / 0.36f).coerceIn(0f, 1f))
            } else 0f

            ribbonOverlay.sweepProgress = sweepProgress
            ribbonOverlay.ring1Progress = ring1Progress
            ribbonOverlay.ring2Progress = ring2Progress
            ribbonOverlay.hasActiveWaves = t <= 0.54f

            // 2. Anticipación: encogimiento suave (0.42 -> 0.62)
            // 3. Acción: salto hacia arriba + zoom (0.62 -> 1.00)
            val currentScale: Float
            val currentTranslationY: Float
            if (t < 0.62f) {
                val crouchT = ((t - 0.42f) / 0.20f).coerceIn(0f, 1f)
                val crouchEase = 0.5f * (1f - cos(crouchT * PI).toFloat())
                currentScale = 1f - 0.16f * crouchEase
                currentTranslationY = crouchOffsetPx * crouchEase
            } else {
                val jumpT = ((t - 0.62f) / 0.38f).coerceIn(0f, 1f)
                val jumpEase = JumpLaunchInterpolator.getInterpolation(jumpT)
                currentScale = 0.84f + 1.26f * jumpEase
                currentTranslationY = crouchOffsetPx + (jumpOffsetPx - crouchOffsetPx) * jumpEase
            }

            // Desvanecimiento rápido del logo nada más iniciar el zoom (0.62 -> 0.80 ≈ 195 ms)
            // para evitar que se vea grande o pixelado sobre la pantalla
            val logoFadeT = ((t - 0.62f) / 0.18f).coerceIn(0f, 1f)
            val logoAlpha = (1f - LogoFadeInterpolator.getInterpolation(logoFadeT)).coerceIn(0f, 1f)

            iconView?.let { icon ->
                if (icon.width > 0 && icon.height > 0) {
                    icon.pivotX = icon.width * 0.5f
                    icon.pivotY = icon.height * 0.5f
                }
                icon.scaleX = currentScale
                icon.scaleY = currentScale
                icon.translationY = currentTranslationY
                icon.alpha = logoAlpha
            }

            ribbonOverlay.pivotX = ribbonOverlay.width * 0.5f
            ribbonOverlay.pivotY = ribbonOverlay.height * 0.5f
            ribbonOverlay.scaleX = currentScale
            ribbonOverlay.scaleY = currentScale
            ribbonOverlay.translationY = currentTranslationY
            ribbonOverlay.alpha = logoAlpha
            if (ribbonOverlay.hasActiveWaves) {
                ribbonOverlay.invalidate()
            }

            if (bgDrawable != null) {
                val backdropT = ((t - 0.63f) / 0.31f).coerceIn(0f, 1f)
                val backdropAlpha = (1f - BackdropFadeInterpolator.getInterpolation(backdropT)).coerceIn(0f, 1f)
                bgDrawable.alpha = (backdropAlpha * 255f).roundToInt().coerceIn(0, 255)
                if (t > 0.94f) {
                    val tailT = ((t - 0.94f) / 0.06f).coerceIn(0f, 1f)
                    rootView.alpha = (1f - tailT).coerceIn(0f, 1f)
                } else {
                    rootView.alpha = 1f
                }
            } else {
                val fallbackFadeT = ((t - 0.63f) / 0.35f).coerceIn(0f, 1f)
                rootView.alpha = (1f - BackdropFadeInterpolator.getInterpolation(fallbackFadeT)).coerceIn(0f, 1f)
            }
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finishAndRemove()
            }

            override fun onAnimationCancel(animation: Animator) {
                finishAndRemove()
            }
        })
    }

    animator.start()
}

private class SplashSignalOverlayView(
    context: Context
) : View(context) {

    var sweepProgress: Float = 0f
    var ring1Progress: Float = 0f
    var ring2Progress: Float = 0f
    var hasActiveWaves: Boolean = true

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val vectorMatrix = Matrix()
    private val routePath108 = Path().apply {
        moveTo(31.58f, 40.58f)
        lineTo(45.26f, 69.46f)
        lineTo(57.42f, 45.90f)
        lineTo(76.42f, 45.90f)
    }
    private val topWing108 = Path().apply {
        moveTo(68.06f, 37.54f)
        lineTo(76.42f, 45.90f)
    }
    private val botWing108 = Path().apply {
        moveTo(68.06f, 54.26f)
        lineTo(76.42f, 45.90f)
    }

    private val transformedRoute = Path()
    private val transformedTopWing = Path()
    private val transformedBotWing = Path()
    private val segmentPath = Path()
    private val pathMeasure = PathMeasure()
    private val centerPoint = FloatArray(2)

    private fun buildVectorToCanvasMatrix(): Boolean {
        if (width <= 0 || height <= 0) return false
        val targetSizePx = 288f * resources.displayMetrics.density
        val scale = targetSizePx / 108f
        val dx = (width - targetSizePx) * 0.5f
        val dy = (height - targetSizePx) * 0.5f

        vectorMatrix.reset()
        vectorMatrix.setScale(scale, scale)
        vectorMatrix.postTranslate(dx, dy)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasActiveWaves) return
        if (!buildVectorToCanvasMatrix()) return

        val unitScale = vectorMatrix.mapRadius(1f)
        val strokePx = 7.5f * unitScale
        if (strokePx <= 0.5f) return

        // Centro óptico del glifo V→ (en espacio 108x108) para los anillos de onda
        centerPoint[0] = 53.8f
        centerPoint[1] = 52.5f
        vectorMatrix.mapPoints(centerPoint)
        val cx = centerPoint[0]
        val cy = centerPoint[1]

        // 1. Ondas expansivas sutiles en rojo VLC Transit alrededor del icono
        drawRippleRing(canvas, cx, cy, unitScale, ring1Progress, startAlpha = 0.36f, startRadius = 23f, endRadius = 56f, startWidth = 2.4f)
        drawRippleRing(canvas, cx, cy, unitScale, ring2Progress, startAlpha = 0.24f, startRadius = 21f, endRadius = 49f, startWidth = 1.9f)

        // 2. Pulso luminoso monocromo (coral + cresta blanca translúcida) recorriendo la V→
        if (sweepProgress > 0.002f) {
            strokePaint.strokeWidth = strokePx
            wingPaint.strokeWidth = strokePx

            transformedRoute.set(routePath108)
            transformedRoute.transform(vectorMatrix)
            pathMeasure.setPath(transformedRoute, false)
            val totalLen = pathMeasure.length
            if (totalLen <= 1f) return

            val head = sweepProgress * 1.44f
            drawMonochromeWave(head = head, backOffset = 0.44f, frontOffset = 0.08f, color = COLOR_WAVE_CORAL, alphaFloat = 0.72f, totalLen = totalLen, canvas = canvas)
            drawMonochromeWave(head = head, backOffset = 0.24f, frontOffset = 0.00f, color = COLOR_WAVE_WHITE, alphaFloat = 0.56f, totalLen = totalLen, canvas = canvas)

            val tipWave = ((head - 0.86f) / 0.48f).coerceIn(0f, 1f)
            if (tipWave > 0.01f && tipWave < 0.99f) {
                val wingAlpha = (sin(tipWave * PI).toFloat().coerceIn(0f, 1f)) * 0.52f
                wingPaint.color = Color.argb(
                    (wingAlpha * 255f).roundToInt().coerceIn(0, 255),
                    255,
                    255,
                    255
                )
                transformedTopWing.set(topWing108)
                transformedTopWing.transform(vectorMatrix)
                transformedBotWing.set(botWing108)
                transformedBotWing.transform(vectorMatrix)
                canvas.drawPath(transformedTopWing, wingPaint)
                canvas.drawPath(transformedBotWing, wingPaint)
            }
        }
    }

    private fun drawRippleRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        unitScale: Float,
        progress: Float,
        startAlpha: Float,
        startRadius: Float,
        endRadius: Float,
        startWidth: Float
    ) {
        if (progress <= 0.01f || progress >= 0.99f) return
        val radius = (startRadius + (endRadius - startRadius) * progress) * unitScale
        val widthPx = (startWidth * (1f - progress * 0.62f)) * unitScale
        val alpha = (startAlpha * (1f - progress)).coerceIn(0f, 1f)
        ripplePaint.strokeWidth = widthPx
        ripplePaint.color = Color.argb(
            (alpha * 255f).roundToInt().coerceIn(0, 255),
            Color.red(COLOR_VLC_RED),
            Color.green(COLOR_VLC_RED),
            Color.blue(COLOR_VLC_RED)
        )
        canvas.drawCircle(cx, cy, radius, ripplePaint)
    }

    private fun drawMonochromeWave(
        head: Float,
        backOffset: Float,
        frontOffset: Float,
        color: Int,
        alphaFloat: Float,
        totalLen: Float,
        canvas: Canvas
    ) {
        val startFrac = (head - backOffset).coerceIn(0f, 1f)
        val endFrac = (head - frontOffset).coerceIn(0f, 1f)
        if (endFrac <= startFrac + 0.005f) return

        segmentPath.reset()
        pathMeasure.getSegment(startFrac * totalLen, endFrac * totalLen, segmentPath, true)
        strokePaint.color = Color.argb(
            (alphaFloat * 255f).roundToInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
        canvas.drawPath(segmentPath, strokePaint)
    }
}
