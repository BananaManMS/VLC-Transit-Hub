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
import kotlin.math.sin

private val SumaSweepInterpolator = PathInterpolator(0.35f, 0.0f, 0.15f, 1.0f)
private val SumaPortalInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
private val SumaFadeInterpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)

private const val COLOR_SUMA_GREEN = 0xFF39A96B.toInt()
private const val COLOR_SUMA_YELLOW = 0xFFF6B619.toInt()
private const val COLOR_SUMA_BLUE = 0xFF0660A9.toInt()

/**
 * Ejecuta la animación de salida directamente sobre la SplashScreenView nativa del sistema.
 * Utiliza el tamaño estándar oficial de Android para iconos de splash (288dp centrado)
 * para garantizar coincidencia perfecta y alineación con el icono del sistema.
 */
fun runSumaSplashExitAnimation(
    splashScreenView: SplashScreenViewProvider,
    hasAlreadyPlayed: Boolean,
    onStarted: () -> Unit
) {
    if (hasAlreadyPlayed) {
        splashScreenView.remove()
        return
    }
    onStarted()

    val rootView = splashScreenView.view as? ViewGroup
    if (rootView == null) {
        splashScreenView.view.animate()
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { splashScreenView.remove() }
            .start()
        return
    }

    val iconView = runCatching { splashScreenView.iconView }.getOrNull()
    val ribbonOverlay = SumaSplashRibbonOverlayView(
        context = rootView.context,
        iconView = iconView
    )
    rootView.addView(
        ribbonOverlay,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    var cleanedUp = false
    fun finishAndRemove() {
        if (cleanedUp) return
        cleanedUp = true
        runCatching { rootView.removeView(ribbonOverlay) }
        runCatching { splashScreenView.remove() }
    }

    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1380L
        interpolator = LinearInterpolator()
        addUpdateListener { va ->
            val t = (va.animatedValue as Float).coerceIn(0f, 1f)

            // 1. Onda cromática SUMA por el interior del trazo (0.00 -> 0.62)
            val sweepProgress = SumaSweepInterpolator.getInterpolation((t / 0.62f).coerceIn(0f, 1f))
            ribbonOverlay.sweepProgress = sweepProgress
            ribbonOverlay.ribbonVisible = t <= 0.66f
            ribbonOverlay.invalidate()

            // 2. Micro-pulso elástico en la V→ y apertura limpia hacia Inicio (0.44 -> 1.00)
            val recoilDip = if (t in 0.42f..0.58f) {
                val r = ((t - 0.42f) / 0.16f).coerceIn(0f, 1f)
                -0.045f * sin(r * PI).toFloat()
            } else {
                0f
            }
            val exitT = ((t - 0.54f) / 0.46f).coerceIn(0f, 1f)
            val portalScale = 1f + recoilDip + SumaPortalInterpolator.getInterpolation(exitT) * 0.16f

            iconView?.let { icon ->
                if (icon.width > 0 && icon.height > 0) {
                    icon.pivotX = icon.width * 0.5f
                    icon.pivotY = icon.height * 0.5f
                }
                icon.scaleX = portalScale
                icon.scaleY = portalScale
            }
            ribbonOverlay.pivotX = ribbonOverlay.width * 0.5f
            ribbonOverlay.pivotY = ribbonOverlay.height * 0.5f
            ribbonOverlay.scaleX = portalScale
            ribbonOverlay.scaleY = portalScale

            // Disolvemos todo el splash completo junto al fondo
            val rootAlpha = (1f - SumaFadeInterpolator.getInterpolation(exitT)).coerceIn(0f, 1f)
            rootView.alpha = rootAlpha
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

private class SumaSplashRibbonOverlayView(
    context: Context,
    private val iconView: View?
) : View(context) {

    var sweepProgress: Float = 0f
    var ribbonVisible: Boolean = true

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

    private fun buildVectorToCanvasMatrix(): Boolean {
        if (width <= 0 || height <= 0) return false
        // Tamaño oficial Android para el icono del splash screen sin fondo: 288dp perfectamente centrado
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
        if (!ribbonVisible || sweepProgress <= 0.002f) return
        if (!buildVectorToCanvasMatrix()) return

        val strokePx = 7.5f * vectorMatrix.mapRadius(1f)
        if (strokePx <= 0.5f) return

        strokePaint.strokeWidth = strokePx
        wingPaint.strokeWidth = strokePx

        transformedRoute.set(routePath108)
        transformedRoute.transform(vectorMatrix)
        pathMeasure.setPath(transformedRoute, false)
        val totalLen = pathMeasure.length
        if (totalLen <= 1f) return

        val head = sweepProgress * 1.56f

        drawBand(head = head, backOffset = 0.56f, frontOffset = 0.34f, color = COLOR_SUMA_GREEN, totalLen = totalLen, canvas = canvas)
        drawBand(head = head, backOffset = 0.38f, frontOffset = 0.16f, color = COLOR_SUMA_YELLOW, totalLen = totalLen, canvas = canvas)
        drawBand(head = head, backOffset = 0.22f, frontOffset = 0.00f, color = COLOR_SUMA_BLUE, totalLen = totalLen, canvas = canvas)

        val tipWave = ((head - 0.85f) / 0.55f).coerceIn(0f, 1f)
        if (tipWave > 0.01f && tipWave < 0.99f) {
            val baseColor = when {
                tipWave < 0.33f -> COLOR_SUMA_BLUE
                tipWave < 0.66f -> COLOR_SUMA_YELLOW
                else -> COLOR_SUMA_GREEN
            }
            val alphaFloat = (sin(tipWave * PI).toFloat().coerceIn(0f, 1f)) * 0.92f
            val alphaInt = (alphaFloat * 255f).toInt().coerceIn(0, 255)
            wingPaint.color = Color.argb(
                alphaInt,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )
            transformedTopWing.set(topWing108)
            transformedTopWing.transform(vectorMatrix)
            transformedBotWing.set(botWing108)
            transformedBotWing.transform(vectorMatrix)
            canvas.drawPath(transformedTopWing, wingPaint)
            canvas.drawPath(transformedBotWing, wingPaint)
        }
    }

    private fun drawBand(
        head: Float,
        backOffset: Float,
        frontOffset: Float,
        color: Int,
        totalLen: Float,
        canvas: Canvas
    ) {
        val startFrac = (head - backOffset).coerceIn(0f, 1f)
        val endFrac = (head - frontOffset).coerceIn(0f, 1f)
        if (endFrac <= startFrac + 0.005f) return

        segmentPath.reset()
        pathMeasure.getSegment(startFrac * totalLen, endFrac * totalLen, segmentPath, true)
        strokePaint.color = color
        canvas.drawPath(segmentPath, strokePaint)
    }
}
