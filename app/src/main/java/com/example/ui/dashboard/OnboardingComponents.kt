package com.example.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.SpaceGroteskFontFamily

internal val TransitMetroCrimson = Color(0xFFEC1B4B)
internal val TransitBrandBlue = TransitMetroCrimson
internal val TransitEmerald = Color(0xFF10B981)
internal val TransitEmtSky = Color(0xFFE53935)
internal val TransitCercaniasPurple = Color(0xFF702B7B)
internal val TransitAmber = Color(0xFFF59E0B)

/**
 * Insignia oficial de VLC Transit basada en los recursos reales del icono adaptativo.
 */
@Composable
internal fun VlcTransitAppLogoBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = 1.52f,
                    scaleY = 1.52f
                )
        )
    }
}

/**
 * Cabecera superior edge-to-edge ("infinita" arriba, esquinas inferiores redondeadas 24.dp),
 * claramente separada del cuerpo inferior y con diagrama esquemático de línea de metro (sin iconos de tren).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MetroLineOnboardingHeader(
    pagerState: PagerState,
    isDarkMode: Boolean,
    isTransitCatalogSyncing: Boolean,
    onSelectStation: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "metro_station_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val stationLabels = listOf(
        stringResource(R.string.onboarding_station_welcome),
        stringResource(R.string.onboarding_station_permissions),
        stringResource(R.string.onboarding_station_network)
    )
    val stepTitle = when (pagerState.currentPage) {
        0 -> stringResource(R.string.onboarding_welcome_title)
        1 -> stringResource(R.string.onboarding_permissions_title)
        else -> stringResource(R.string.onboarding_network_title)
    }
    val stepSubtitle = when (pagerState.currentPage) {
        0 -> stringResource(R.string.onboarding_welcome_tagline)
        1 -> stringResource(R.string.onboarding_permissions_sub)
        else -> stringResource(R.string.onboarding_network_sub)
    }

    val headerShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
    val headerBg = MaterialTheme.colorScheme.surface
    val bottomOutlineColor = MaterialTheme.colorScheme.outlineVariant
    val stripBg = MaterialTheme.colorScheme.background
    val stripBorder = MaterialTheme.colorScheme.outlineVariant
    val trackInactiveColor = if (isDarkMode) MaterialTheme.colorScheme.outlineVariant else Color(0xFFCBD5E1)
    val activeAccent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(1f)
            .drawWithContent {
                drawContent()
                val radiusPx = 24.dp.toPx()
                val strokePx = 1.2.dp.toPx()
                val yOffset = size.height - strokePx * 0.5f
                val contour = Path().apply {
                    moveTo(0f, size.height - radiusPx)
                    quadraticTo(0f, yOffset, radiusPx, yOffset)
                    lineTo(size.width - radiusPx, yOffset)
                    quadraticTo(size.width, yOffset, size.width, size.height - radiusPx)
                }
                drawPath(
                    path = contour,
                    color = bottomOutlineColor,
                    style = Stroke(width = strokePx)
                )
            },
        shape = headerShape,
        color = headerBg,
        shadowElevation = if (isDarkMode) 0.dp else 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        VlcTransitAppLogoBadge()
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(
                            visible = isTransitCatalogSyncing,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_syncing_stops),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = SpaceGroteskFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedContent(
                    targetState = Pair(stepTitle, stepSubtitle),
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "metro_header_copy"
                ) { (title, subtitle) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Termómetro esquemático de línea de metro en carmesí VLC (#EC1B4B) sin iconos de vehículo
                val pagerProgress = ((pagerState.currentPage + pagerState.currentPageOffsetFraction) / 2f).coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = stripBg,
                    border = BorderStroke(1.dp, stripBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        ) {
                            val colW = size.width / 3f
                            val startX = colW * 0.5f
                            val endX = colW * 2.5f
                            val y = size.height * 0.44f
                            val span = endX - startX
                            val trackStroke = 5.dp.toPx()

                            drawLine(
                                color = trackInactiveColor,
                                start = Offset(startX, y),
                                end = Offset(endX, y),
                                strokeWidth = trackStroke,
                                cap = StrokeCap.Round
                            )

                            val activeEndX = startX + span * pagerProgress
                            if (activeEndX > startX) {
                                drawLine(
                                    color = activeAccent,
                                    start = Offset(startX, y),
                                    end = Offset(activeEndX, y),
                                    strokeWidth = trackStroke,
                                    cap = StrokeCap.Round
                                )
                            }

                            for (index in 0 until 3) {
                                val cx = colW * (index + 0.5f)
                                val isCurrent = index == pagerState.currentPage
                                val isCompleted = index < pagerState.currentPage

                                // Pequeño tick vertical hacia el nombre de estación (estética termómetro metro)
                                drawLine(
                                    color = when {
                                        isCurrent || isCompleted -> activeAccent.copy(alpha = 0.75f)
                                        else -> trackInactiveColor.copy(alpha = 0.65f)
                                    },
                                    start = Offset(cx, y + 6.dp.toPx()),
                                    end = Offset(cx, size.height),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                when {
                                    isCurrent -> {
                                        drawCircle(
                                            color = activeAccent.copy(alpha = 0.24f),
                                            radius = 10.5.dp.toPx() * pulseScale,
                                            center = Offset(cx, y)
                                        )
                                        drawCircle(
                                            color = Color.White,
                                            radius = 8.dp.toPx(),
                                            center = Offset(cx, y)
                                        )
                                        drawCircle(
                                            color = activeAccent,
                                            radius = 8.dp.toPx(),
                                            center = Offset(cx, y),
                                            style = Stroke(width = 3.dp.toPx())
                                        )
                                        drawCircle(
                                            color = activeAccent,
                                            radius = 2.5.dp.toPx(),
                                            center = Offset(cx, y)
                                        )
                                    }

                                    isCompleted -> {
                                        drawCircle(
                                            color = activeAccent,
                                            radius = 7.5.dp.toPx(),
                                            center = Offset(cx, y)
                                        )
                                        drawCircle(
                                            color = Color.White,
                                            radius = 3.2.dp.toPx(),
                                            center = Offset(cx, y)
                                        )
                                    }

                                    else -> {
                                        drawCircle(
                                            color = stripBg,
                                            radius = 6.5.dp.toPx(),
                                            center = Offset(cx, y)
                                        )
                                        drawCircle(
                                            color = trackInactiveColor,
                                            radius = 6.5.dp.toPx(),
                                            center = Offset(cx, y),
                                            style = Stroke(width = 2.5.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            stationLabels.forEachIndexed { index, label ->
                                val isCurrent = index == pagerState.currentPage
                                val isCompleted = index < pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectStation(index) }
                                        .padding(top = 4.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isCurrent -> MaterialTheme.colorScheme.onSurface
                                            isCompleted -> activeAccent
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SegmentedTogglePill(
    isDarkMode: Boolean,
    firstLabel: String,
    secondLabel: String,
    isFirstSelected: Boolean,
    onSelectFirst: () -> Unit,
    onSelectSecond: () -> Unit,
    firstTag: String,
    secondTag: String
) {
    val trackBg = if (isDarkMode) Color(0xFF2C2C2C) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(trackBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentOptionItem(
            label = firstLabel,
            selected = isFirstSelected,
            onClick = onSelectFirst,
            testTag = firstTag
        )
        SegmentOptionItem(
            label = secondLabel,
            selected = !isFirstSelected,
            onClick = onSelectSecond,
            testTag = secondTag
        )
    }
}

@Composable
private fun SegmentOptionItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
        animationSpec = tween(180),
        label = "segment_bg"
    )
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun PermissionActionPill(
    isGranted: Boolean,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGranted) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = TransitEmerald.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, TransitEmerald.copy(alpha = 0.3f)),
            modifier = modifier.defaultMinSize(minHeight = 44.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TransitEmerald,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TransitEmerald
                )
            }
        }
    } else {
        val isAmberWarning = accentColor == TransitAmber
        val containerColor = if (isAmberWarning) {
            TransitAmber.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val contentColor = if (isAmberWarning) {
            TransitAmber
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        val borderColor = if (isAmberWarning) {
            TransitAmber.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
        }
        androidx.compose.material3.FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            border = BorderStroke(1.dp, borderColor),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = modifier.defaultMinSize(minHeight = 44.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun TransitModeBentoTile(
    label: String,
    brandColor: Color,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @DrawableRes iconDrawable: Int? = null
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            brandColor.copy(alpha = if (isDarkMode) 0.14f else 0.08f).compositeOver(MaterialTheme.colorScheme.surface)
        } else {
            if (isDarkMode) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "mode_tile_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            brandColor.copy(alpha = if (isDarkMode) 0.55f else 0.45f)
        } else {
            if (isDarkMode) Color(0xFF333333) else AppThemeColors.subtleBorder(false)
        },
        animationSpec = tween(durationMillis = 180),
        label = "mode_tile_border"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.48f,
        animationSpec = tween(durationMillis = 180),
        label = "mode_tile_alpha"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.defaultMinSize(minHeight = 68.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.background(brandColor)
                        } else {
                            Modifier.border(
                                width = 1.2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (iconDrawable != null) {
                    Image(
                        painter = painterResource(id = iconDrawable),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                } else if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(brandColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun QuickSetupRow(
    iconTint: Color,
    title: String,
    badgeText: String,
    isConfigured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @DrawableRes iconDrawable: Int? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconDrawable != null) {
                Image(
                    painter = painterResource(id = iconDrawable),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isConfigured) {
                TransitEmerald.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isConfigured) TransitEmerald else MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isConfigured) TransitEmerald else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
