package com.example.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.dashboard.home.HabitualLinesSheet
import com.example.ui.dashboard.home.TransitCardsSummaryWidget
import com.example.ui.metro.CardDetailDialog
import com.example.ui.metro.MetroViewModel
import com.example.ui.metro.cards.AddTransitCardWizardDialog
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.util.LocationUtils
import com.example.util.MetrobusLineColorResolver
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGES = 3

@OptIn(ExperimentalFoundationApi::class)
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
    val habitualLines by viewModel.habitualLines.collectAsState()
    val transitCards by viewModel.transitCardsFlow.collectAsState()
    val isTransitCatalogSyncing by viewModel.isTransitCatalogSyncing.collectAsState()

    val context = LocalContext.current
    var showAddCardDialog by remember { mutableStateOf(false) }
    var selectedTransitCard by remember { mutableStateOf<TransitCardUiModel?>(null) }
    var showHabitualLinesSheet by remember { mutableStateOf(false) }
    var availableLinesGrouped by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    LaunchedEffect(showHabitualLinesSheet) {
        if (showHabitualLinesSheet) {
            availableLinesGrouped = viewModel.getAvailableLinesGrouped()
        }
    }

    var hasFineLocation by remember { mutableStateOf(LocationUtils.hasFineLocationPermission(context)) }
    var hasOnlyCoarseLocation by remember { mutableStateOf(LocationUtils.hasOnlyCoarseLocationPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(isNotificationsGranted(context)) }
    var isCalendarConnected by remember { mutableStateOf(isCalendarPermissionGranted(context)) }

    fun refreshPermissionStates() {
        hasFineLocation = LocationUtils.hasFineLocationPermission(context)
        hasOnlyCoarseLocation = LocationUtils.hasOnlyCoarseLocationPermission(context)
        hasNotificationPermission = isNotificationsGranted(context)
        isCalendarConnected = isCalendarPermissionGranted(context)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isCalendarConnected = isGranted
        if (isGranted) {
            viewModel.syncGoogleCalendarEvents()
            Toast.makeText(context, context.getString(R.string.setting_syncing_events), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_calendar_permission_denied), Toast.LENGTH_LONG).show()
        }
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

    val hasInternalModal = showAddCardDialog ||
        selectedTransitCard != null ||
        showHabitualLinesSheet

    BackHandler(enabled = !hasInternalModal && !hasActiveModalDialog) {
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
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MetroLineOnboardingHeader(
            pagerState = pagerState,
            isDarkMode = isDarkMode,
            isTransitCatalogSyncing = isTransitCatalogSyncing,
            onSelectStation = { targetPage ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(targetPage)
                }
            },
            onSkip = { viewModel.completeOnboarding() },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val viewportHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 580.dp)
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top)
                    ) {
                        when (page) {
                            0 -> WelcomeStepPage(
                                appLanguage = appLanguage,
                                isDarkMode = isDarkMode,
                                preferredModes = preferredModes,
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
                                onSelectLanguage = { viewModel.setAppLanguage(it) },
                                onToggleDarkMode = { targetDark ->
                                    if (isDarkMode != targetDark) {
                                        viewModel.toggleDarkMode()
                                    }
                                }
                            )

                            1 -> PermissionsStepPage(
                                isDarkMode = isDarkMode,
                                hasFineLocation = hasFineLocation,
                                hasOnlyCoarseLocation = hasOnlyCoarseLocation,
                                hasNotificationPermission = hasNotificationPermission,
                                isCalendarConnected = isCalendarConnected,
                                onRequestLocation = onLaunchLocationPermission,
                                onRequestNotifications = onLaunchNotificationPermission,
                                onRequestCalendar = {
                                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }
                            )

                            else -> NetworkSetupStepPage(
                                appLanguage = appLanguage,
                                isDarkMode = isDarkMode,
                                favoriteMetroCount = favoriteMetroStations.size,
                                favoriteCercaniasCount = favoriteCercaniasStations.size,
                                habitualLinesCount = habitualLines.size,
                                transitCards = transitCards,
                                onConfigureMetro = onConfigureStations,
                                onConfigureCercanias = onConfigureCercaniasStations,
                                onOpenHabitualLines = { showHabitualLinesSheet = true },
                                onAddTransitCard = { showAddCardDialog = true },
                                onCardClick = { selectedTransitCard = it }
                            )
                        }
                    }
                }
            }
        }

        OnboardingBottomBar(
            currentPage = pagerState.currentPage,
            isDarkMode = isDarkMode,
            onBack = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                }
            },
            onNextOrFinish = {
                if (pagerState.currentPage == ONBOARDING_PAGES - 1) {
                    viewModel.completeOnboarding()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
        )
    }

    if (showHabitualLinesSheet) {
        HabitualLinesSheet(
            habitualLines = habitualLines,
            availableLinesGrouped = availableLinesGrouped,
            isDarkMode = isDarkMode,
            onToggleLine = { line -> viewModel.toggleHabitualLine(line) },
            onDismiss = { showHabitualLinesSheet = false }
        )
    }

    if (selectedTransitCard != null) {
        val lazyMetroVm: MetroViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        CardDetailDialog(
            card = selectedTransitCard!!,
            appLanguage = appLanguage,
            metroViewModel = lazyMetroVm,
            isDarkMode = isDarkMode,
            onDismiss = { selectedTransitCard = null }
        )
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

@Composable
private fun WelcomeStepPage(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    preferredModes: Set<String>,
    onToggleMode: (String) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 4.dp, end = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.onboarding_modes_section).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "${preferredModes.size}/5",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        AjustesGroupCard(isDarkMode = isDarkMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        brandColor = Color(0xFFE53935),
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
                        brandColor = TransitCercaniasPurple,
                        isSelected = preferredModes.contains("CERCANIAS"),
                        isDarkMode = isDarkMode,
                        onClick = { onToggleMode("CERCANIAS") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_mode_cercanias")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransitModeBentoTile(
                        icon = Icons.Default.PedalBike,
                        label = "Valenbisi",
                        brandColor = TransitEmerald,
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
    }

    AjustesGroupCard(isDarkMode = isDarkMode) {
        AjustesItemRow(
            title = stringResource(R.string.onboarding_language_label),
            icon = Icons.Default.Language,
            iconTint = MaterialTheme.colorScheme.primary,
            trailingContent = {
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
        )
        AjustesDivider(isDarkMode = isDarkMode)
        AjustesItemRow(
            title = stringResource(R.string.onboarding_theme_label),
            icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
            iconTint = MaterialTheme.colorScheme.primary,
            trailingContent = {
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
        )
    }

    Text(
        text = stringResource(R.string.onboarding_disclaimer),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 14.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("onboarding_disclaimer_text")
    )
}

@Composable
private fun PermissionsStepPage(
    isDarkMode: Boolean,
    hasFineLocation: Boolean,
    hasOnlyCoarseLocation: Boolean,
    hasNotificationPermission: Boolean,
    isCalendarConnected: Boolean,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestCalendar: () -> Unit
) {
    val locationTint = when {
        hasFineLocation -> TransitEmerald
        hasOnlyCoarseLocation -> TransitAmber
        else -> MaterialTheme.colorScheme.primary
    }
    val locationSub = when {
        hasFineLocation -> stringResource(R.string.onboarding_location_granted_sub)
        hasOnlyCoarseLocation -> stringResource(R.string.onboarding_location_coarse_sub)
        else -> stringResource(R.string.onboarding_location_precise_sub)
    }

    AjustesGroupCard(isDarkMode = isDarkMode) {
        AjustesItemRow(
            title = stringResource(R.string.onboarding_location_precise_title),
            subtitle = locationSub,
            icon = if (hasFineLocation) Icons.Default.MyLocation else Icons.Default.LocationOn,
            iconTint = locationTint,
            iconContainerColor = locationTint.copy(alpha = 0.12f),
            onClick = if (!hasFineLocation) onRequestLocation else null,
            trailingContent = {
                PermissionActionPill(
                    isGranted = hasFineLocation,
                    label = when {
                        hasFineLocation -> stringResource(R.string.onboarding_action_active)
                        hasOnlyCoarseLocation -> stringResource(R.string.onboarding_action_improve)
                        else -> stringResource(R.string.onboarding_action_enable)
                    },
                    accentColor = if (hasOnlyCoarseLocation) TransitAmber else MaterialTheme.colorScheme.primary,
                    onClick = onRequestLocation,
                    modifier = Modifier.testTag("onboarding_btn_gps")
                )
            }
        )
        AjustesDivider(isDarkMode = isDarkMode)
        AjustesItemRow(
            title = stringResource(R.string.onboarding_notifications_title),
            subtitle = stringResource(
                if (hasNotificationPermission) R.string.onboarding_notifications_granted_sub
                else R.string.onboarding_notifications_sub
            ),
            icon = Icons.Default.NotificationsActive,
            iconTint = if (hasNotificationPermission) TransitEmerald else MaterialTheme.colorScheme.primary,
            iconContainerColor = (if (hasNotificationPermission) TransitEmerald else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
            onClick = if (!hasNotificationPermission) onRequestNotifications else null,
            trailingContent = {
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
        )
        AjustesDivider(isDarkMode = isDarkMode)
        AjustesItemRow(
            title = stringResource(R.string.setting_google_calendar_title),
            subtitle = stringResource(R.string.onboarding_calendar_sub),
            icon = Icons.Default.Event,
            iconTint = if (isCalendarConnected) TransitEmerald else MaterialTheme.colorScheme.primary,
            iconContainerColor = (if (isCalendarConnected) TransitEmerald else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
            onClick = if (!isCalendarConnected) onRequestCalendar else null,
            trailingContent = {
                PermissionActionPill(
                    isGranted = isCalendarConnected,
                    label = stringResource(
                        if (isCalendarConnected) R.string.onboarding_action_active
                        else R.string.onboarding_action_connect
                    ),
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onRequestCalendar,
                    modifier = Modifier.testTag("onboarding_btn_calendar")
                )
            }
        )
    }
}

@Composable
private fun NetworkSetupStepPage(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    favoriteMetroCount: Int,
    favoriteCercaniasCount: Int,
    habitualLinesCount: Int,
    transitCards: List<TransitCardUiModel>,
    onConfigureMetro: () -> Unit,
    onConfigureCercanias: () -> Unit,
    onOpenHabitualLines: () -> Unit,
    onAddTransitCard: () -> Unit,
    onCardClick: (TransitCardUiModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AjustesSectionHeader(
            title = stringResource(R.string.onboarding_quick_setup_section)
        )
        AjustesGroupCard(isDarkMode = isDarkMode) {
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
            AjustesDivider(isDarkMode = isDarkMode)
            QuickSetupRow(
                iconDrawable = R.drawable.logo_cercanias,
                iconTint = TransitCercaniasPurple,
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
            AjustesDivider(isDarkMode = isDarkMode)
            QuickSetupRow(
                icon = Icons.Default.Tune,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.onboarding_habitual_lines_title),
                badgeText = if (habitualLinesCount > 0) {
                    stringResource(R.string.onboarding_selected_count, habitualLinesCount)
                } else {
                    stringResource(R.string.onboarding_action_choose)
                },
                isConfigured = habitualLinesCount > 0,
                onClick = onOpenHabitualLines,
                modifier = Modifier.testTag("onboarding_btn_habitual_lines")
            )
        }
    }

    TransitCardsSummaryWidget(
        appLanguage = appLanguage,
        cards = transitCards,
        onAddCardClick = onAddTransitCard,
        onCardClick = onCardClick,
        onManageCardsClick = onAddTransitCard,
        modifier = Modifier.testTag("onboarding_btn_add_card")
    )
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onNextOrFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLastPage = currentPage == ONBOARDING_PAGES - 1
    val dividerColor = if (isDarkMode) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = 0.8.dp,
            color = dividerColor
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentPage > 0) {
                TextButton(
                    onClick = onBack,
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
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = SpaceGroteskFontFamily
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.onboarding_step_of, currentPage + 1, ONBOARDING_PAGES),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Button(
                onClick = onNextOrFinish,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 50.dp)
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

private fun isCalendarPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
}
