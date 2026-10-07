package com.example.ui.map.networkmaps

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val MIN_ZOOM = 1.0f
private const val MAX_ZOOM = 8.0f
private const val DOUBLE_TAP_ZOOM = 3.0f
private const val BASE_TARGET_DIMENSION = 2600f
private const val MIN_FLING_VELOCITY = 350f
private const val FLING_FRICTION = 2.8f

private data class ViewportRenderRequest(
    val scale: Float,
    val offset: Offset
)

@Composable
fun NetworkPdfViewerScreen(
    mapItem: MapPlanItem,
    file: File,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val coroutineScope = rememberCoroutineScope()

    var pageCount by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detailScale by remember { mutableFloatStateOf(1f) }
    var detailOffset by remember { mutableStateOf(Offset.Zero) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var pdfWidth by remember { mutableIntStateOf(0) }
    var pdfHeight by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableFloatStateOf(1000f) }
    var containerHeight by remember { mutableFloatStateOf(1000f) }

    val baseFit = if (pdfWidth > 0 && pdfHeight > 0 && containerWidth > 1f && containerHeight > 1f) {
        min(containerWidth / pdfWidth, containerHeight / pdfHeight)
    } else 1f
    val fitWidth = pdfWidth * baseFit
    val fitHeight = pdfHeight * baseFit

    val animScale = remember { Animatable(MIN_ZOOM) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var dragDebounceJob by remember { mutableStateOf<Job?>(null) }

    var renderRequest by remember { mutableStateOf<ViewportRenderRequest?>(null) }

    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }

    val scale = animScale.value
    val offset = Offset(animOffsetX.value, animOffsetY.value)

    LaunchedEffect(file, currentPageIndex) {
        isLoading = true
        errorMessage = null
        flingJob?.cancel()
        dragDebounceJob?.cancel()
        animScale.snapTo(MIN_ZOOM)
        animOffsetX.snapTo(0f)
        animOffsetY.snapTo(0f)
        detailBitmap?.recycle()
        detailBitmap = null
        renderRequest = null

        withContext(Dispatchers.IO) {
            try {
                val result = NetworkPdfRendererHelper.renderBasePage(
                    file = file,
                    pageIndex = currentPageIndex,
                    targetDimension = BASE_TARGET_DIMENSION
                )
                withContext(Dispatchers.Main) {
                    pageCount = result.pageCount
                    pdfWidth = result.pdfWidth
                    pdfHeight = result.pdfHeight
                    baseBitmap?.recycle()
                    baseBitmap = result.bitmap
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Error al abrir el plano"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(file, currentPageIndex, renderRequest, containerWidth, containerHeight) {
        val currentReq = renderRequest ?: return@LaunchedEffect
        val reqScale = currentReq.scale
        val reqOffset = currentReq.offset

        if (reqScale <= 1.25f || pdfWidth <= 0 || pdfHeight <= 0 || containerWidth <= 10f || containerHeight <= 10f) {
            if (reqScale <= 1.1f && detailBitmap != null) {
                detailBitmap?.recycle()
                detailBitmap = null
            }
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val bmp = NetworkPdfRendererHelper.renderDetailTile(
                    file = file,
                    pageIndex = currentPageIndex,
                    scale = reqScale,
                    offset = reqOffset,
                    baseFit = baseFit,
                    pdfWidth = pdfWidth,
                    pdfHeight = pdfHeight,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight
                )
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        val oldBmp = detailBitmap
                        detailBitmap = bmp
                        detailScale = reqScale
                        detailOffset = reqOffset
                        oldBmp?.recycle()
                    }
                }
            } catch (_: Exception) {
                // Ignore transient interruptions
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            flingJob?.cancel()
            dragDebounceJob?.cancel()
            baseBitmap?.recycle()
            baseBitmap = null
            detailBitmap?.recycle()
            detailBitmap = null
        }
    }

    val mapTitle = mapItem.getTitle(appLanguage)
    val brandColor = mapItem.getColor(isDarkMode)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NetworkPdfBottomBar(
                mapItem = mapItem,
                title = mapTitle,
                pageCount = pageCount,
                currentPageIndex = currentPageIndex,
                currentScale = scale,
                isDarkMode = isDarkMode,
                brandColor = brandColor,
                appLanguage = appLanguage,
                onBack = onBack,
                onPreviousPage = {
                    if (currentPageIndex > 0) {
                        currentPageIndex--
                        flingJob?.cancel()
                        dragDebounceJob?.cancel()
                        coroutineScope.launch {
                            animScale.snapTo(MIN_ZOOM)
                            animOffsetX.snapTo(0f)
                            animOffsetY.snapTo(0f)
                        }
                    }
                },
                onNextPage = {
                    if (currentPageIndex < pageCount - 1) {
                        currentPageIndex++
                        flingJob?.cancel()
                        dragDebounceJob?.cancel()
                        coroutineScope.launch {
                            animScale.snapTo(MIN_ZOOM)
                            animOffsetX.snapTo(0f)
                            animOffsetY.snapTo(0f)
                        }
                    }
                },
                onSetScale = { targetScale ->
                    flingJob?.cancel()
                    dragDebounceJob?.cancel()
                    coroutineScope.launch {
                        val maxPanX = max(0f, (fitWidth * targetScale - containerWidth) / 2f)
                        val maxPanY = max(0f, (fitHeight * targetScale - containerHeight) / 2f)
                        animOffsetX.updateBounds(-maxPanX, maxPanX)
                        animOffsetY.updateBounds(-maxPanY, maxPanY)

                        val targetX = if (targetScale <= 1.05f) 0f else animOffsetX.value.coerceIn(-maxPanX, maxPanX)
                        val targetY = if (targetScale <= 1.05f) 0f else animOffsetY.value.coerceIn(-maxPanY, maxPanY)

                        val jScale = launch { animScale.animateTo(targetScale, tween(220)) }
                        val jX = launch { animOffsetX.animateTo(targetX, tween(220)) }
                        val jY = launch { animOffsetY.animateTo(targetY, tween(220)) }
                        joinAll(jScale, jX, jY)
                        renderRequest = ViewportRenderRequest(targetScale, Offset(targetX, targetY))
                    }
                }
            )
        },
        containerColor = if (isDarkMode) Color(0xFF141720) else Color(0xFFE5E7EB),
        modifier = Modifier
            .fillMaxSize()
            .testTag("network_pdf_viewer_screen")
    ) { innerPadding ->
        // No top status-bar padding: plan starts right at the top edge of the screen
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .clipToBounds()
                .background(if (isDarkMode) Color(0xFF141720) else Color(0xFFE5E7EB))
        ) {
            val localW = constraints.maxWidth.toFloat()
            val localH = constraints.maxHeight.toFloat()
            if (containerWidth != localW || containerHeight != localH) {
                containerWidth = localW
                containerHeight = localH
            }

            if (isLoading && baseBitmap == null) {
                PdfLoadingOverlay(
                    corporateColor = brandColor,
                    appLanguage = appLanguage
                )
            } else if (errorMessage != null && baseBitmap == null) {
                PdfErrorOverlay(
                    errorMessage = errorMessage ?: "",
                    appLanguage = appLanguage
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                flingJob?.cancel()
                                dragDebounceJob?.cancel()

                                val downTime = down.uptimeMillis
                                val downPos = down.position
                                val isDoubleTapCandidate = (downTime - lastTapTime < 350L) &&
                                        (hypot(downPos.x - lastTapPos.x, downPos.y - lastTapPos.y) < 90f)

                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)

                                var isMultiTouch = false
                                var hasMovedPastSlop = false
                                var isOneFingerZooming = false
                                var lastDragY = downPos.y

                                do {
                                    val event = awaitPointerEvent()
                                    val activePointers = event.changes.filter { it.pressed }

                                    if (activePointers.size > 1) {
                                        isMultiTouch = true
                                        hasMovedPastSlop = true
                                        isOneFingerZooming = false
                                        velocityTracker.resetTracking()

                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = true)

                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            val currentScale = animScale.value
                                            val newScale = (currentScale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)

                                            val maxPanX = max(0f, (fitWidth * newScale - containerWidth) / 2f)
                                            val maxPanY = max(0f, (fitHeight * newScale - containerHeight) / 2f)

                                            animOffsetX.updateBounds(-maxPanX, maxPanX)
                                            animOffsetY.updateBounds(-maxPanY, maxPanY)

                                            val scaleFactor = newScale / currentScale
                                            val newX = ((animOffsetX.value - (centroid.x - containerWidth / 2f)) * scaleFactor + (centroid.x - containerWidth / 2f) + panChange.x).coerceIn(-maxPanX, maxPanX)
                                            val newY = ((animOffsetY.value - (centroid.y - containerHeight / 2f)) * scaleFactor + (centroid.y - containerHeight / 2f) + panChange.y).coerceIn(-maxPanY, maxPanY)

                                            coroutineScope.launch {
                                                animScale.snapTo(newScale)
                                                animOffsetX.snapTo(newX)
                                                animOffsetY.snapTo(newY)
                                            }
                                            event.changes.forEach { it.consume() }

                                            dragDebounceJob?.cancel()
                                            dragDebounceJob = coroutineScope.launch {
                                                delay(140)
                                                renderRequest = ViewportRenderRequest(newScale, Offset(newX, newY))
                                            }
                                        }
                                    } else if (activePointers.size == 1 && !isMultiTouch) {
                                        val change = activePointers.first()
                                        val distMoved = hypot(change.position.x - downPos.x, change.position.y - downPos.y)

                                        if (distMoved > 14f) {
                                            hasMovedPastSlop = true
                                        }

                                        if (isDoubleTapCandidate && hasMovedPastSlop) {
                                            isOneFingerZooming = true
                                            val deltaY = change.position.y - lastDragY
                                            lastDragY = change.position.y

                                            val zoomFactor = 1f + (deltaY / 220f)
                                            val oldScale = animScale.value
                                            val newScale = (oldScale * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                            val scaleRatio = newScale / oldScale

                                            val maxPanX = max(0f, (fitWidth * newScale - containerWidth) / 2f)
                                            val maxPanY = max(0f, (fitHeight * newScale - containerHeight) / 2f)
                                            animOffsetX.updateBounds(-maxPanX, maxPanX)
                                            animOffsetY.updateBounds(-maxPanY, maxPanY)

                                            val focalX = downPos.x - containerWidth / 2f
                                            val focalY = downPos.y - containerHeight / 2f
                                            val targetX = (animOffsetX.value * scaleRatio + focalX * (1f - scaleRatio)).coerceIn(-maxPanX, maxPanX)
                                            val targetY = (animOffsetY.value * scaleRatio + focalY * (1f - scaleRatio)).coerceIn(-maxPanY, maxPanY)

                                            coroutineScope.launch {
                                                animScale.snapTo(newScale)
                                                animOffsetX.snapTo(targetX)
                                                animOffsetY.snapTo(targetY)
                                            }
                                            change.consume()

                                            dragDebounceJob?.cancel()
                                            dragDebounceJob = coroutineScope.launch {
                                                delay(140)
                                                renderRequest = ViewportRenderRequest(newScale, Offset(targetX, targetY))
                                            }
                                        } else if (!isDoubleTapCandidate) {
                                            val pan = change.position - change.previousPosition
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                                            if (pan != Offset.Zero && hasMovedPastSlop) {
                                                val currentScale = animScale.value
                                                if (currentScale > 1.05f) {
                                                    val maxPanX = max(0f, (fitWidth * currentScale - containerWidth) / 2f)
                                                    val maxPanY = max(0f, (fitHeight * currentScale - containerHeight) / 2f)

                                                    animOffsetX.updateBounds(-maxPanX, maxPanX)
                                                    animOffsetY.updateBounds(-maxPanY, maxPanY)

                                                    val newX = (animOffsetX.value + pan.x).coerceIn(-maxPanX, maxPanX)
                                                    val newY = (animOffsetY.value + pan.y).coerceIn(-maxPanY, maxPanY)

                                                    coroutineScope.launch {
                                                        animOffsetX.snapTo(newX)
                                                        animOffsetY.snapTo(newY)
                                                    }

                                                    dragDebounceJob?.cancel()
                                                    dragDebounceJob = coroutineScope.launch {
                                                        delay(140)
                                                        renderRequest = ViewportRenderRequest(currentScale, Offset(newX, newY))
                                                    }
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                dragDebounceJob?.cancel()

                                if (isDoubleTapCandidate && !isOneFingerZooming && !hasMovedPastSlop) {
                                    lastTapTime = 0L
                                    coroutineScope.launch {
                                        val currentScale = animScale.value
                                        if (currentScale > 1.8f) {
                                            val jScale = launch { animScale.animateTo(MIN_ZOOM, tween(250)) }
                                            val jX = launch { animOffsetX.animateTo(0f, tween(250)) }
                                            val jY = launch { animOffsetY.animateTo(0f, tween(250)) }
                                            joinAll(jScale, jX, jY)
                                            renderRequest = ViewportRenderRequest(MIN_ZOOM, Offset.Zero)
                                        } else {
                                            val targetScale = DOUBLE_TAP_ZOOM
                                            val scaleRatio = targetScale / currentScale
                                            val maxPanX = max(0f, (fitWidth * targetScale - containerWidth) / 2f)
                                            val maxPanY = max(0f, (fitHeight * targetScale - containerHeight) / 2f)

                                            val focalX = downPos.x - containerWidth / 2f
                                            val focalY = downPos.y - containerHeight / 2f
                                            val targetX = (animOffsetX.value * scaleRatio + focalX * (1f - scaleRatio)).coerceIn(-maxPanX, maxPanX)
                                            val targetY = (animOffsetY.value * scaleRatio + focalY * (1f - scaleRatio)).coerceIn(-maxPanY, maxPanY)

                                            animOffsetX.updateBounds(-maxPanX, maxPanX)
                                            animOffsetY.updateBounds(-maxPanY, maxPanY)

                                            val jScale = launch { animScale.animateTo(targetScale, tween(260)) }
                                            val jX = launch { animOffsetX.animateTo(targetX, tween(260)) }
                                            val jY = launch { animOffsetY.animateTo(targetY, tween(260)) }
                                            joinAll(jScale, jX, jY)
                                            renderRequest = ViewportRenderRequest(targetScale, Offset(targetX, targetY))
                                        }
                                    }
                                } else if (isOneFingerZooming) {
                                    lastTapTime = 0L
                                    renderRequest = ViewportRenderRequest(
                                        animScale.value,
                                        Offset(animOffsetX.value, animOffsetY.value)
                                    )
                                } else if (!isMultiTouch && !hasMovedPastSlop) {
                                    lastTapTime = downTime
                                    lastTapPos = downPos
                                } else if (!isMultiTouch && hasMovedPastSlop && animScale.value > 1.05f) {
                                    lastTapTime = 0L
                                    val velocity = velocityTracker.calculateVelocity()
                                    val vx = velocity.x
                                    val vy = velocity.y
                                    val speed = hypot(vx, vy)

                                    if (speed >= MIN_FLING_VELOCITY) {
                                        flingJob = coroutineScope.launch {
                                            val currentScale = animScale.value
                                            val maxPanX = max(0f, (fitWidth * currentScale - containerWidth) / 2f)
                                            val maxPanY = max(0f, (fitHeight * currentScale - containerHeight) / 2f)

                                            animOffsetX.updateBounds(-maxPanX, maxPanX)
                                            animOffsetY.updateBounds(-maxPanY, maxPanY)

                                            val decay = exponentialDecay<Float>(frictionMultiplier = FLING_FRICTION)
                                            val jX = launch { animOffsetX.animateDecay(vx, decay) }
                                            val jY = launch { animOffsetY.animateDecay(vy, decay) }
                                            joinAll(jX, jY)

                                            renderRequest = ViewportRenderRequest(
                                                currentScale,
                                                Offset(animOffsetX.value, animOffsetY.value)
                                            )
                                        }
                                    } else {
                                        renderRequest = ViewportRenderRequest(
                                            animScale.value,
                                            Offset(animOffsetX.value, animOffsetY.value)
                                        )
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    baseBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = mapTitle,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                    clip = true
                                }
                        )
                    }

                    detailBitmap?.let { detail ->
                        val scaleRatio = if (detailScale > 0.01f) scale / detailScale else 1f
                        val deltaX = offset.x - detailOffset.x * scaleRatio
                        val deltaY = offset.y - detailOffset.y * scaleRatio

                        Image(
                            bitmap = detail.asImageBitmap(),
                            contentDescription = null,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scaleRatio
                                    scaleY = scaleRatio
                                    translationX = deltaX
                                    translationY = deltaY
                                    clip = true
                                }
                        )
                    }
                }
            }
        }
    }
}
