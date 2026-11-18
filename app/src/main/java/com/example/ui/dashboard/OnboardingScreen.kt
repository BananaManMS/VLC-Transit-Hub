package com.example.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.metro.MetroViewModel
import com.example.ui.metro.cards.AddTransitCardWizardDialog
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.util.LocationUtils
import com.example.util.MetrobusLineColorResolver
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGES = 3

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: DashboardViewModel,
    onConfigureStations: () -> Unit,
    onConfigureCercaniasStations: () -> Unit,
    onLaunchLocationPermission: () -> Unit,
    onLaunchNotificationPermission: () -> Unit,
    hasActiveModalDialog: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES })

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val preferredModes by viewModel.favoriteTransitModes.collectAsState()
    val favoriteMetroStations by viewModel.favoriteMetroStations.collectAsState()
    val favoriteCercaniasStations by viewModel.favoriteCercaniasStations.collectAsState()
    val transitCards by viewModel.transitCardsFlow.collectAsState()
    val isTransitCatalogSyncing by viewModel.isTransitCatalogSyncing.collectAsState()

    val context = LocalContext.current
    var showAddCardDialog by remember { mutableStateOf(false) }

    var hasFineLocation by remember { mutableStateOf(LocationUtils.hasFineLocationPermission(context)) }
    var hasOnlyCoarseLocation by remember { mutableStateOf(LocationUtils.hasOnlyCoarseLocationPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(isNotificationsGranted(context)) }

    fun refreshPermissionStates() {
        hasFineLocation = LocationUtils.hasFineLocationPermission(context)
        hasOnlyCoarseLocation = LocationUtils.hasOnlyCoarseLocationPermission(context)
        hasNotificationPermission = isNotificationsGranted(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pagerState.currentPage) {
        refreshPermissionStates()
    }

    BackHandler(enabled = !showAddCardDialog && !hasActiveModalDialog) {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            viewModel.completeOnboarding()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shared Persistent Metro Line Visor Header
        MetroLineVisorHeader(
            pagerState = pagerState,
            isTransitCatalogSyncing = isTransitCatalogSyncing,
            onSelectStation = { targetPage ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(targetPage)
                }
            },
            onSkip = { viewModel.completeOnboarding() },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )

        // Thumb-Zone Interactive Pager (anchored toward bottom so controls sit right above CTA)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val viewportHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 580.dp)
                        .verticalScroll(rememberScrollState())
                        .defaultMinSize(minHeight = viewportHeight)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    when (page) {
                        0 -> WelcomeThumbControls(
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode,
                            onSelectLanguage = { viewModel.setAppLanguage(it) },
                            onToggleDarkMode = { targetDark ->
                                if (isDarkMode != targetDark) {
                                    viewModel.toggleDarkMode()
                                }
                            }
                        )

                        1 -> PermissionsThumbControls(
                            isDarkMode = isDarkMode,
                            hasFineLocation = hasFineLocation,
                            hasOnlyCoarseLocation = hasOnlyCoarseLocation,
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestLocation = onLaunchLocationPermission,
                            onRequestNotifications = onLaunchNotificationPermission
                        )

                        2 -> NetworkSetupThumbControls(
                            isDarkMode = isDarkMode,
                            preferredModes = preferredModes,
                            favoriteMetroCount = favoriteMetroStations.size,
                            favoriteCercaniasCount = favoriteCercaniasStations.size,
                            transitCardCount = transitCards.size,
                            onToggleMode = { code ->
                                val isCurrentlySelected = preferredModes.contains(code)
                                if (isCurrentlySelected && preferredModes.size <= 1) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.onboarding_min_one_mode),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    viewModel.togglePreferredTransitMode(code)
                                }
                            },
                            onConfigureMetro = onConfigureStations,
                            onConfigureCercanias = onConfigureCercaniasStations,
                            onAddTransitCard = { showAddCardDialog = true }
                        )
                    }
                }
            }
        }

        // Ergonomic Bottom Navigation Row (directly beneath interactive thumb controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_btn_prev")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            val isLastPage = pagerState.currentPage == ONBOARDING_PAGES - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.completeOnboarding()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 52.dp)
                    .testTag(if (isLastPage) "onboarding_finish_button" else "onboarding_btn_next")
            ) {
                Text(
                    text = stringResource(
                        if (isLastPage) R.string.onboarding_start_traveling else R.string.onboarding_next
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showAddCardDialog) {
        val lazyMetroVm: MetroViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        AddTransitCardWizardDialog(
            appLanguage = appLanguage,
            metroViewModel = lazyMetroVm,
            onDismiss = { showAddCardDialog = false },
            onCardAdded = { showAddCardDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetroLineVisorHeader(
    pagerState: PagerState,
    isTransitCatalogSyncing: Boolean,
    onSelectStation: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Lightweight entrance draw (~700ms) while Phase 1 preloads local stops
    val introReveal = remember { Animatable(0f) }
    val headerAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { headerAlpha.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing)) }
        introReveal.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    val pulseTransition = rememberInfiniteTransition(label = "station_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
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

    Surface(
        modifier = modifier.alpha(headerAlpha.value),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF131313),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2563EB).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(180f, 50f),
                        radius = 780f
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Brand Pill + Sync Indicator + Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color(0xFF93C5FD)
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_syncing_stops),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onSkip,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .testTag("onboarding_btn_skip")
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Dynamic Step Title & 1-line Subtitle
                AnimatedContent(
                    targetState = pagerState.currentPage,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    },
                    label = "visor_step_copy"
                ) { _ ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stepTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stepSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Metro Line Diagram Progress (completes smoothly from Station 1 -> 2 -> 3)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pagerFraction = (
                        (pagerState.currentPage + pagerState.currentPageOffsetFraction) /
                            (ONBOARDING_PAGES - 1).toFloat()
                        ).coerceIn(0f, 1f)
                    val introFactor = introReveal.value

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    ) {
                        val y = size.height / 2f
                        val edgeInset = 24.dp.toPx()
                        val startX = edgeInset
                        val endX = (size.width - edgeInset).coerceAtLeast(startX + 1f)
                        val span = endX - startX

                        // Base Metro Rail
                        drawLine(
                            color = Color.White.copy(alpha = 0.16f),
                            start = Offset(startX, y),
                            end = Offset(endX, y),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Completed / Live-Interpolated Metro Track
                        val activeEndX = if (pagerFraction <= 0.001f) {
                            startX + (span * 0.06f * (1f - introFactor)) // subtle entry glide into Station 1
                        } else {
                            startX + span * pagerFraction * introFactor
                        }
                        if (pagerFraction > 0.001f) {
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFF60A5FA),
                                        Color(0xFF10B981)
                                    )
                                ),
                                start = Offset(startX, y),
                                end = Offset(startX + span * pagerFraction, y),
                                strokeWidth = 4.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        } else if (introFactor < 1f) {
                            drawLine(
                                color = Color(0xFF60A5FA).copy(alpha = 0.7f),
                                start = Offset(startX, y),
                                end = Offset(activeEndX, y),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // 3 Metro Station Nodes
                        repeat(ONBOARDING_PAGES) { index ->
                            val nodeFraction = index / (ONBOARDING_PAGES - 1).toFloat()
                            val cx = startX + span * nodeFraction
                            val isCurrent = index == pagerState.currentPage
                            val isCompleted = index < pagerState.currentPage
                            val revealAlpha = ((introFactor - index * 0.15f) / 0.35f).coerceIn(0.25f, 1f)

                            when {
                                isCurrent -> {
                                    // Soft pulsing metro arrival halo
                                    drawCircle(
                                        color = Color(0xFF60A5FA).copy(alpha = 0.28f * revealAlpha),
                                        radius = 10.dp.toPx() * pulseScale,
                                        center = Offset(cx, y)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 7.dp.toPx(),
                                        center = Offset(cx, y)
                                    )
                                    drawCircle(
                                        color = Color(0xFF2563EB),
                                        radius = 3.5.dp.toPx(),
                                        center = Offset(cx, y)
                                    )
                                }

                                isCompleted -> {
                                    drawCircle(
                                        color = Color(0xFF10B981),
                                        radius = 6.5.dp.toPx(),
                                        center = Offset(cx, y)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(cx, y)
                                    )
                                }

                                else -> {
                                    drawCircle(
                                        color = Color(0xFF1E293B),
                                        radius = 6.dp.toPx(),
                                        center = Offset(cx, y)
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.32f * revealAlpha),
                                        radius = 6.dp.toPx(),
                                        center = Offset(cx, y),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Station Labels aligned under the 3 metro dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        stationLabels.forEachIndexed { index, label ->
                            val isCurrent = index == pagerState.currentPage
                            val isCompleted = index < pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectStation(index) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = when (index) {
                                    0 -> Alignment.CenterStart
                                    ONBOARDING_PAGES - 1 -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isCurrent -> Color.White
                                        isCompleted -> Color(0xFF6EE7B7)
                                        else -> Color(0xFF64748B)
                                    },
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeThumbControls(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeatureMicroChip(
                icon = Icons.Default.Subway,
                label = stringResource(R.string.onboarding_pill_live),
                isDarkMode = isDarkMode
            )
            FeatureMicroChip(
                icon = Icons.Default.DirectionsBus,
                label = stringResource(R.string.onboarding_pill_routes),
                isDarkMode = isDarkMode
            )
            FeatureMicroChip(
                icon = Icons.Default.CreditCard,
                label = stringResource(R.string.onboarding_pill_suma),
                isDarkMode = isDarkMode
            )
        }

        OnboardingGroupCard(isDarkMode = isDarkMode) {
            // Language Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingIconBadge(
                    icon = Icons.Default.Language,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.onboarding_language_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                SegmentedTogglePill(
                    isDarkMode = isDarkMode,
                    firstLabel = "VAL",
                    secondLabel = "ES",
                    isFirstSelected = appLanguage == AppLanguage.CA,
                    onSelectFirst = { onSelectLanguage(AppLanguage.CA) },
                    onSelectSecond = { onSelectLanguage(AppLanguage.ES) },
                    firstTag = "onboarding_lang_ca",
                    secondTag = "onboarding_lang_es"
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                thickness = 0.8.dp,
                color = onboardingDividerColor(isDarkMode)
            )

            // Theme Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingIconBadge(
                    icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.onboarding_theme_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                SegmentedTogglePill(
                    isDarkMode = isDarkMode,
                    firstLabel = stringResource(R.string.onboarding_theme_light),
                    secondLabel = stringResource(R.string.onboarding_theme_dark),
                    isFirstSelected = !isDarkMode,
                    onSelectFirst = { onToggleDarkMode(false) },
                    onSelectSecond = { onToggleDarkMode(true) },
                    firstTag = "onboarding_light_mode_btn",
                    secondTag = "onboarding_dark_mode_switch"
                )
            }
        }
    }
}

@Composable
private fun PermissionsThumbControls(
    isDarkMode: Boolean,
    hasFineLocation: Boolean,
    hasOnlyCoarseLocation: Boolean,
    hasNotificationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val locationTint = when {
            hasFineLocation -> Color(0xFF10B981)
            hasOnlyCoarseLocation -> Color(0xFFF59E0B)
            else -> MaterialTheme.colorScheme.primary
        }
        val locationSubtitle = when {
            hasFineLocation -> stringResource(R.string.onboarding_location_granted_sub)
            hasOnlyCoarseLocation -> stringResource(R.string.onboarding_location_coarse_sub)
            else -> stringResource(R.string.onboarding_location_precise_sub)
        }

        OnboardingGroupCard(isDarkMode = isDarkMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!hasFineLocation) {
                            Modifier.clickable(onClick = onRequestLocation)
                        } else Modifier
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingIconBadge(
                    icon = if (hasFineLocation) Icons.Default.MyLocation else Icons.Default.LocationOn,
                    tint = locationTint
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_location_precise_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = locationSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasFineLocation) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                PermissionActionPill(
                    isGranted = hasFineLocation,
                    label = when {
                        hasFineLocation -> stringResource(R.string.onboarding_action_active)
                        hasOnlyCoarseLocation -> stringResource(R.string.onboarding_action_improve)
                        else -> stringResource(R.string.onboarding_action_enable)
                    },
                    accentColor = if (hasOnlyCoarseLocation) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                    onClick = onRequestLocation,
                    modifier = Modifier.testTag("onboarding_btn_gps")
                )
            }
        }

        OnboardingGroupCard(isDarkMode = isDarkMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!hasNotificationPermission) {
                            Modifier.clickable(onClick = onRequestNotifications)
                        } else Modifier
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingIconBadge(
                    icon = Icons.Default.NotificationsActive,
                    tint = if (hasNotificationPermission) Color(0xFF10B981) else Color(0xFF0284C7)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_notifications_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            if (hasNotificationPermission) R.string.onboarding_notifications_granted_sub
                            else R.string.onboarding_notifications_sub
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasNotificationPermission) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                PermissionActionPill(
                    isGranted = hasNotificationPermission,
                    label = stringResource(
                        if (hasNotificationPermission) R.string.onboarding_action_active
                        else R.string.onboarding_action_enable
                    ),
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onRequestNotifications,
                    modifier = Modifier.testTag("onboarding_btn_notifications")
                )
            }
        }
    }
}

@Composable
private fun NetworkSetupThumbControls(
    isDarkMode: Boolean,
    preferredModes: Set<String>,
    favoriteMetroCount: Int,
    favoriteCercaniasCount: Int,
    transitCardCount: Int,
    onToggleMode: (String) -> Unit,
    onConfigureMetro: () -> Unit,
    onConfigureCercanias: () -> Unit,
    onAddTransitCard: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Unified Medios Activos Bento Card
        OnboardingGroupCard(isDarkMode = isDarkMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingIconBadge(
                    icon = Icons.Default.Layers,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_modes_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${preferredModes.size}/5",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.8.dp,
                color = onboardingDividerColor(isDarkMode)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Metro · EMT · Rodalia/Cercanías
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransitModeBentoTile(
                        iconDrawable = R.drawable.logo_metrovalencia,
                        label = "Metro",
                        brandColor = Color(0xFFEF4444),
                        isSelected = preferredModes.contains("METRO"),
                        isDarkMode = isDarkMode,
                        onClick = { onToggleMode("METRO") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_mode_metro")
                    )
                    TransitModeBentoTile(
                        iconDrawable = R.drawable.logo_emt_valencia,
                        label = "EMT",
                        brandColor = Color(0xFF0284C7),
                        isSelected = preferredModes.contains("EMT"),
                        isDarkMode = isDarkMode,
                        onClick = { onToggleMode("EMT") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_mode_emt")
                    )
                    TransitModeBentoTile(
                        iconDrawable = R.drawable.logo_cercanias,
                        label = stringResource(R.string.onboarding_mode_cercanias),
                        brandColor = Color(0xFF702B7B),
                        isSelected = preferredModes.contains("CERCANIAS"),
                        isDarkMode = isDarkMode,
                        onClick = { onToggleMode("CERCANIAS") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_mode_cercanias")
                    )
                }

                // Row 2: Valenbisi · Metrobús
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransitModeBentoTile(
                        icon = Icons.Default.PedalBike,
                        label = "Valenbisi",
                        brandColor = Color(0xFF10B981),
                        isSelected = preferredModes.contains("VALENBISI"),
                        isDarkMode = isDarkMode,
                        onClick = { onToggleMode("VALENBISI") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_mode_valenbisi")
                    )
                    TransitModeBentoTile(
                        iconDrawable = R.drawable.logo_metrobus,
                        label = "Metrobús",
                        brandColor = MetrobusLineColorResolver.BRAND_COLOR,
                        isSelected = preferredModes.contains("METROBUS"),
                        isDarkMode = isDarkMode,
                        onClick = { onToggleMode("METROBUS") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_mode_metrobus")
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.onboarding_quick_setup_section),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = SpaceGroteskFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OnboardingGroupCard(isDarkMode = isDarkMode) {
                QuickSetupRow(
                    iconDrawable = R.drawable.logo_metrovalencia,
                    iconTint = Color(0xFFE53935),
                    title = "Metrovalencia",
                    badgeText = if (favoriteMetroCount > 0) {
                        stringResource(R.string.onboarding_selected_count, favoriteMetroCount)
                    } else {
                        stringResource(R.string.onboarding_action_choose)
                    },
                    isConfigured = favoriteMetroCount > 0,
                    onClick = onConfigureMetro,
                    modifier = Modifier.testTag("onboarding_btn_favorites")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    thickness = 0.8.dp,
                    color = onboardingDividerColor(isDarkMode)
                )

                QuickSetupRow(
                    iconDrawable = R.drawable.logo_cercanias,
                    iconTint = Color(0xFF702B7B),
                    title = stringResource(R.string.cercanias_renfe_title),
                    badgeText = if (favoriteCercaniasCount > 0) {
                        stringResource(R.string.onboarding_selected_count, favoriteCercaniasCount)
                    } else {
                        stringResource(R.string.onboarding_action_choose)
                    },
                    isConfigured = favoriteCercaniasCount > 0,
                    onClick = onConfigureCercanias,
                    modifier = Modifier.testTag("onboarding_btn_cercanias_favorites")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                    thickness = 0.8.dp,
                    color = onboardingDividerColor(isDarkMode)
                )

                QuickSetupRow(
                    icon = Icons.Default.AddCard,
                    iconTint = Color(0xFFF59E0B),
                    title = stringResource(R.string.onboarding_card_suma_title),
                    badgeText = if (transitCardCount > 0) {
                        stringResource(R.string.onboarding_cards_count, transitCardCount)
                    } else {
                        stringResource(R.string.onboarding_action_add)
                    },
                    isConfigured = transitCardCount > 0,
                    onClick = onAddTransitCard,
                    modifier = Modifier.testTag("onboarding_btn_add_card")
                )
            }
        }
    }
}

@Composable
private fun FeatureMicroChip(
    icon: ImageVector,
    label: String,
    isDarkMode: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDarkMode) Color(0xFF1E222B) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDarkMode) Color(0xFF282F3E) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun OnboardingGroupCard(
    isDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    val containerColor = if (isDarkMode) Color(0xFF1E222B) else MaterialTheme.colorScheme.surface
    val borderColor = if (isDarkMode) Color(0xFF282F3E) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkMode) 0.dp else 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun OnboardingIconBadge(
    icon: ImageVector,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SegmentedTogglePill(
    isDarkMode: Boolean,
    firstLabel: String,
    secondLabel: String,
    isFirstSelected: Boolean,
    onSelectFirst: () -> Unit,
    onSelectSecond: () -> Unit,
    firstTag: String,
    secondTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDarkMode) Color(0xFF282F3E)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isFirstSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(onClick = onSelectFirst)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag(firstTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLabel,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (isFirstSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (!isFirstSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(onClick = onSelectSecond)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag(secondTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = secondLabel,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (!isFirstSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionActionPill(
    isGranted: Boolean,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGranted) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF10B981).copy(alpha = 0.14f),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            modifier = modifier.defaultMinSize(minHeight = 44.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TransitModeBentoTile(
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
            brandColor.copy(alpha = if (isDarkMode) 0.18f else 0.10f)
        } else {
            if (isDarkMode) Color(0xFF151820) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "mode_tile_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            brandColor.copy(alpha = 0.85f)
        } else {
            if (isDarkMode) Color(0xFF282F3E) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
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
            // Selection indicator dot in top-right corner
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
private fun QuickSetupRow(
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
        if (iconDrawable != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconDrawable),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else if (icon != null) {
            OnboardingIconBadge(icon = icon, tint = iconTint)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isConfigured) {
                Color(0xFF10B981).copy(alpha = 0.14f)
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
                    fontWeight = FontWeight.Bold,
                    color = if (isConfigured) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isConfigured) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun onboardingDividerColor(isDarkMode: Boolean): Color {
    return if (isDarkMode) Color(0xFF282F3E) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
}

private fun isNotificationsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
