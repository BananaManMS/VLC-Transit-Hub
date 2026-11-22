package com.example.ui.dashboard.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Gently snaps [scrollState] to either fully expanded (0) or fully collapsed ([collapseRangePx])
 * if the user lifts their finger mid-transition inside the header's collapse zone.
 */
@Composable
fun HomeHeaderSnapEffect(
    scrollState: ScrollState,
    collapseRangePx: Int
) {
    LaunchedEffect(scrollState.isScrollInProgress, collapseRangePx) {
        if (!scrollState.isScrollInProgress && collapseRangePx > 0) {
            val current = scrollState.value
            if (current in 1 until collapseRangePx && scrollState.maxValue >= collapseRangePx) {
                val target = if (current >= collapseRangePx * 0.45f) collapseRangePx else 0
                scrollState.animateScrollTo(
                    value = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }
}

/**
 * Dynamic, scroll-driven collapsible top header for the Inicio tab.
 *
 * When scrolled to the top (`scrollState.value == 0`), it displays the full clock, date,
 * settings action, and route/commute quick bar. As the user scrolls down, the date, settings
 * button, and commute bar smoothly squish upward and fade away, leaving a compact centered clock
 * pinned at the top. Scrolling back to the top smoothly unfurls the full header again; tapping
 * the compact clock bar also scrolls smoothly back to the top.
 */
@Composable
fun HomeCollapsibleHeader(
    scrollState: ScrollState,
    currentTimeFlow: StateFlow<String>,
    appLanguage: AppLanguage,
    settingsContentDescription: String,
    isDarkMode: Boolean,
    isTablet: Boolean,
    homeName: String,
    workName: String,
    pinnedName: String,
    onOpenSettings: () -> Unit,
    onHomeClick: () -> Unit,
    onWorkClick: () -> Unit,
    onPinnedClick: () -> Unit,
    onPlanRouteClick: () -> Unit,
    onExploreMapClick: () -> Unit,
    onEditCommute: (String) -> Unit,
    onExpandToTop: () -> Unit,
    onExpandedTotalHeightMeasured: (Dp) -> Unit,
    onCollapseRangeMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var commuteNaturalHeightPx by remember { mutableIntStateOf(0) }
    var dateNaturalHeightPx by remember { mutableIntStateOf(0) }

    val fallbackRangePx = remember(density, isTablet) {
        with(density) { (if (isTablet) 172.dp else 160.dp).roundToPx() }
    }
    val clockAndPaddingDeltaPx = remember(density, isTablet) {
        with(density) { (if (isTablet) 36.dp else 28.dp).roundToPx() }
    }

    val collapseRangePx = remember(commuteNaturalHeightPx, dateNaturalHeightPx, clockAndPaddingDeltaPx, fallbackRangePx) {
        if (commuteNaturalHeightPx > 0) {
            (commuteNaturalHeightPx + dateNaturalHeightPx + clockAndPaddingDeltaPx).coerceAtLeast(1)
        } else {
            fallbackRangePx
        }
    }

    LaunchedEffect(collapseRangePx) {
        onCollapseRangeMeasured(collapseRangePx)
    }

    val collapseFraction by remember(scrollState, collapseRangePx) {
        derivedStateOf {
            (scrollState.value.toFloat() / collapseRangePx.toFloat()).coerceIn(0f, 1f)
        }
    }

    val isCollapsed by remember {
        derivedStateOf { collapseFraction >= 0.80f }
    }

    val eased = FastOutSlowInEasing.transform(collapseFraction)
    val settingsCollapse = (collapseFraction / 0.45f).coerceIn(0f, 1f)
    val dateCollapse = (collapseFraction / 0.50f).coerceIn(0f, 1f)
    val commuteCollapse = (collapseFraction / 0.88f).coerceIn(0f, 1f)
    val clockCenterProgress = FastOutSlowInEasing.transform(
        ((collapseFraction - 0.15f) / 0.85f).coerceIn(0f, 1f)
    )

    val bottomCorner = lerp(24.dp, 18.dp, eased)
    val headerShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner
    )
    val headerColor = if (isDarkMode) Color(0xFF131313) else MaterialTheme.colorScheme.surface
    val headerBorder = when {
        !isDarkMode -> BorderStroke(1.dp, Color(0xFFE2E8F0))
        eased > 0.02f -> BorderStroke(1.dp, Color(0xFF262626).copy(alpha = eased))
        else -> null
    }
    val headerElevation = if (isDarkMode) 0.dp else lerp(0.dp, 4.dp, eased)
    val expandDesc = stringResource(R.string.home_header_expand_desc)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_collapsible_header")
            .onSizeChanged { size ->
                if (scrollState.value == 0 && size.height > 0) {
                    onExpandedTotalHeightMeasured(with(density) { size.height.toDp() })
                }
            }
            .then(
                if (isCollapsed) {
                    Modifier
                        .semantics { contentDescription = expandDesc }
                        .clickable(onClick = onExpandToTop)
                } else {
                    Modifier
                }
            ),
        shape = headerShape,
        color = headerColor,
        border = headerBorder,
        shadowElevation = headerElevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = lerp(8.dp, 6.dp, eased),
                    bottom = lerp(16.dp, 10.dp, eased)
                )
        ) {
            // Top section: Clock (shrinks & centers smoothly) + Date (squishes out) + Settings (fades out)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = lerp(4.dp, 0.dp, eased))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clock_widget")
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CollapsingClockTimeRow(
                            currentTimeFlow = currentTimeFlow,
                            isTablet = isTablet,
                            easedFraction = eased,
                            modifier = Modifier.align(
                                BiasAlignment(
                                    horizontalBias = lerpFloat(-1f, 0f, clockCenterProgress),
                                    verticalBias = 0f
                                )
                            )
                        )
                    }

                    // Collapsible Date subtitle with vertical squish
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                if (dateNaturalHeightPx != placeable.height && placeable.height > 0) {
                                    dateNaturalHeightPx = placeable.height
                                }
                                val currentH = (placeable.height * (1f - dateCollapse))
                                    .roundToInt()
                                    .coerceAtLeast(0)
                                layout(placeable.width, currentH) {
                                    if (currentH > 0) {
                                        placeable.placeRelative(0, 0)
                                    }
                                }
                            }
                            .graphicsLayer {
                                alpha = (1f - dateCollapse * 1.15f).coerceIn(0f, 1f)
                                scaleY = lerpFloat(1f, 0.55f, dateCollapse)
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        CollapsingClockDateText(
                            currentTimeFlow = currentTimeFlow,
                            appLanguage = appLanguage
                        )
                    }
                }

                // Settings action button (scales down and fades away cleanly during first half of collapse)
                if (settingsCollapse < 0.99f) {
                    val settingsAlpha = (1f - settingsCollapse).coerceIn(0f, 1f)
                    val settingsScale = lerpFloat(1f, 0.55f, settingsCollapse)
                    val settingsOffsetPx = with(density) { lerp(0.dp, 10.dp, settingsCollapse).toPx() }
                    IconButton(
                        onClick = onOpenSettings,
                        enabled = settingsCollapse < 0.5f,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(start = 8.dp)
                            .graphicsLayer {
                                alpha = settingsAlpha
                                scaleX = settingsScale
                                scaleY = settingsScale
                                translationX = settingsOffsetPx
                            }
                            .then(
                                if (settingsCollapse >= 0.5f) {
                                    Modifier.clearAndSetSemantics {}
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("settings_button_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = settingsContentDescription,
                            tint = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Collapsible Route Search & Quick Commute Bar with animated upward squish
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        if (commuteNaturalHeightPx != placeable.height && placeable.height > 0) {
                            commuteNaturalHeightPx = placeable.height
                        }
                        val currentH = (placeable.height * (1f - commuteCollapse))
                            .roundToInt()
                            .coerceAtLeast(0)
                        layout(placeable.width, currentH) {
                            if (currentH > 0) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                    }
                    .graphicsLayer {
                        alpha = (1f - commuteCollapse * 1.25f).coerceIn(0f, 1f)
                        scaleY = lerpFloat(1f, 0.72f, commuteCollapse)
                        scaleX = lerpFloat(1f, 0.94f, commuteCollapse)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
            ) {
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    QuickCommuteBar(
                        appLanguage = appLanguage,
                        homeName = homeName,
                        workName = workName,
                        pinnedName = pinnedName,
                        isDarkMode = isDarkMode,
                        onHomeClick = onHomeClick,
                        onWorkClick = onWorkClick,
                        onPinnedClick = onPinnedClick,
                        onPlanRouteClick = onPlanRouteClick,
                        onExploreMapClick = onExploreMapClick,
                        onEditCommute = onEditCommute
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsingClockTimeRow(
    currentTimeFlow: StateFlow<String>,
    isTablet: Boolean,
    easedFraction: Float,
    modifier: Modifier = Modifier
) {
    val currentTime by currentTimeFlow.collectAsState()
    val (hoursMinutes, seconds) = remember(currentTime) {
        if (currentTime.length >= 8 && currentTime[2] == ':' && currentTime[5] == ':') {
            Pair(currentTime.substring(0, 5), currentTime.substring(6))
        } else if (currentTime.count { it == ':' } == 2) {
            val parts = currentTime.split(":")
            Pair("${parts[0]}:${parts[1]}", parts[2])
        } else {
            val clean = if (currentTime.isEmpty()) "09:12" else currentTime
            Pair(clean, "00")
        }
    }

    val hoursSize = lerpFloat(if (isTablet) 54f else 44f, if (isTablet) 28f else 24f, easedFraction).sp
    val secondsSize = lerpFloat(if (isTablet) 24f else 20f, if (isTablet) 15f else 14f, easedFraction).sp
    val letterSpacing = lerpFloat(-1.5f, -0.5f, easedFraction).sp
    val gapWidth = lerp(6.dp, 4.dp, easedFraction)

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        Text(
            text = hoursMinutes,
            fontSize = hoursSize,
            fontWeight = FontWeight.Bold,
            fontFamily = SpaceGroteskFontFamily,
            style = TextStyle(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = letterSpacing,
            modifier = Modifier.alignByBaseline()
        )
        Spacer(modifier = Modifier.width(gapWidth))
        Text(
            text = seconds,
            fontSize = secondsSize,
            fontWeight = FontWeight.Medium,
            fontFamily = SpaceGroteskFontFamily,
            style = TextStyle(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
private fun CollapsingClockDateText(
    currentTimeFlow: StateFlow<String>,
    appLanguage: AppLanguage
) {
    val currentTime by currentTimeFlow.collectAsState()
    val isMidnightCross = currentTime.startsWith("00:00")
    val formattedDate = remember(appLanguage, isMidnightCross) {
        try {
            val locale = if (appLanguage == AppLanguage.CA) {
                Locale.forLanguageTag("ca-ES")
            } else {
                Locale.forLanguageTag("es-ES")
            }
            val rawDate = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", locale).apply {
                timeZone = TimeZone.getTimeZone("Europe/Madrid")
            }.format(Date())
            rawDate
                .replace(" de de ", " de ")
                .replace(" de d'", " d'")
                .replace(" de d’", " d’")
        } catch (_: Exception) {
            if (appLanguage == AppLanguage.CA) "diumenge, 19 de juliol de 2026" else "domingo, 19 de julio de 2026"
        }
    }

    Text(
        text = formattedDate,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 2.dp)
    )
}
