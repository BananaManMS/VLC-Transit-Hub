package com.example.ui.dashboard

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.ui.cercanias.CercaniasStationSelectionDialog
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.components.ActiveAnnouncementsDialog
import com.example.ui.metro.MetroStationSelectionDialog
import com.example.ui.metro.MetroViewModel
import com.example.ui.theme.ScreenHeader
import com.example.ui.update.AppUpdateSettingCard
import com.example.ui.update.AppUpdateViewModel
import com.example.util.LocationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    viewModel: DashboardViewModel,
    cercaniasViewModel: CercaniasViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    metroViewModel: MetroViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    appUpdateViewModel: AppUpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val cercaniasFavoriteStations by cercaniasViewModel.cercaniasFavoriteStations.collectAsState()
    val preferredTransitModes by viewModel.favoriteTransitModes.collectAsState()

    var showStationSelectionPage by remember { mutableStateOf(false) }
    var showCercaniasStationSelectionPage by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isCalendarConnected by remember { mutableStateOf(false) }

    val allActiveAnnouncements by viewModel.allActiveAnnouncements.collectAsState()
    val isAnnouncementsLoading by viewModel.isAnnouncementsLoading.collectAsState()
    var showActiveAnnouncementsDialog by remember { mutableStateOf(false) }

    val hasFineLocation = LocationUtils.hasFineLocationPermission(context)
    val isOnlyCoarse = LocationUtils.hasOnlyCoarseLocationPermission(context)

    LaunchedEffect(Unit) {
        isCalendarConnected = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCalendarConnected = true
            viewModel.syncGoogleCalendarEvents()
            Toast.makeText(context, context.getString(R.string.setting_syncing_events), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_calendar_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            LocationUtils.requestDeviceLocation(context) { lat, lng ->
                viewModel.updateWeatherByLocation(lat, lng, context)
            }
        }
    }

    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("ajustes_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ScreenHeader(
                title = texts.headerAjustesTitle,
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = 2.dp,
                    end = 0.dp,
                    bottom = 24.dp + activeTripBottomPadding
                ),
                modifier = Modifier.weight(1f)
            ) {
                // 1. APARIENCIA Y GENERAL
                item {
                    AjustesSectionHeader(
                        title = if (appLanguage == AppLanguage.ES) "Apariencia y general" else "Aparència i general"
                    )
                    AjustesGroupCard(isDarkMode = isDarkMode) {
                        // Idioma
                        AjustesItemRow(
                            title = texts.settingLanguageTitle,
                            subtitle = if (appLanguage == AppLanguage.ES) "Español (Castellano)" else "Valencià",
                            icon = Icons.Default.Language,
                            iconTint = MaterialTheme.colorScheme.primary,
                            trailingContent = {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDarkMode) Color(0xFF282F3E) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppLanguage.values().forEach { lang ->
                                        val isSelected = appLanguage == lang
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else Color.Transparent
                                                )
                                                .clickable { viewModel.setAppLanguage(lang) }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = if (lang == AppLanguage.ES) "ES" else "VAL",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        AjustesDivider(isDarkMode = isDarkMode)

                        // Modo Oscuro
                        AjustesItemRow(
                            title = texts.settingThemeTitle,
                            subtitle = if (isDarkMode) {
                                if (appLanguage == AppLanguage.ES) "Activado" else "Activat"
                            } else {
                                if (appLanguage == AppLanguage.ES) "Desactivado" else "Desactivat"
                            },
                            icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            iconTint = MaterialTheme.colorScheme.primary,
                            trailingContent = {
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = { viewModel.toggleDarkMode() },
                                    modifier = Modifier.testTag("dark_mode_switch")
                                )
                            }
                        )
                    }
                }

                // 2. TRANSPORTE Y FAVORITOS
                item {
                    AjustesSectionHeader(
                        title = if (appLanguage == AppLanguage.ES) "Transporte y favoritos" else "Transport i favorits"
                    )
                    AjustesGroupCard(isDarkMode = isDarkMode) {
                        // Estaciones Metrovalencia
                        AjustesItemRow(
                            title = texts.settingMetroStationsTitle,
                            subtitle = "${favoriteStations.size} ${texts.settingStationsSelectedSuffix}",
                            icon = Icons.Default.Subway,
                            iconTint = Color(0xFFE53935),
                            iconContainerColor = Color(0xFFE53935).copy(alpha = 0.12f),
                            onClick = { showStationSelectionPage = true },
                            trailingContent = {
                                StationCountBadge(
                                    count = favoriteStations.size,
                                    isDarkMode = isDarkMode
                                )
                            }
                        )

                        AjustesDivider(isDarkMode = isDarkMode)

                        // Estaciones Cercanías
                        AjustesItemRow(
                            title = texts.settingCercaniasStationsTitle,
                            subtitle = "${cercaniasFavoriteStations.size} ${texts.settingStationsSelectedSuffix}",
                            icon = Icons.Default.DirectionsRailway,
                            iconTint = Color(0xFFC62828),
                            iconContainerColor = Color(0xFFC62828).copy(alpha = 0.12f),
                            onClick = { showCercaniasStationSelectionPage = true },
                            trailingContent = {
                                StationCountBadge(
                                    count = cercaniasFavoriteStations.size,
                                    isDarkMode = isDarkMode
                                )
                            }
                        )
                    }
                }

                // 2.5 Selector de Medios de Transporte Preferidos
                item {
                    PreferredTransitModesCard(
                        preferredModes = preferredTransitModes,
                        onToggleMode = { viewModel.togglePreferredTransitMode(it) },
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode
                    )
                }

                // 3. PERMISOS Y SERVICIOS (Solo visible si falta otorgar ubicación precisa o calendario)
                val needsLocationPermission = !hasFineLocation
                val needsCalendarPermission = !isCalendarConnected
                if (needsLocationPermission || needsCalendarPermission) {
                    item {
                        AjustesSectionHeader(
                            title = if (appLanguage == AppLanguage.ES) "Permisos y servicios" else "Permisos i servicis"
                        )
                        AjustesGroupCard(isDarkMode = isDarkMode) {
                            if (needsLocationPermission) {
                                AjustesItemRow(
                                    title = texts.settingGpsPermissionTitle,
                                    subtitle = if (isOnlyCoarse) {
                                        stringResource(R.string.setting_gps_coarse_warning)
                                    } else {
                                        texts.settingGpsPermissionSubtitle
                                    },
                                    icon = Icons.Default.LocationOn,
                                    iconTint = Color(0xFFF59E0B),
                                    iconContainerColor = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                    trailingContent = {
                                        Button(
                                            onClick = {
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text(
                                                text = if (isOnlyCoarse) {
                                                    stringResource(R.string.setting_gps_improve_btn)
                                                } else {
                                                    texts.settingGpsPermissionAction
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                )
                            }

                            if (needsLocationPermission && needsCalendarPermission) {
                                AjustesDivider(isDarkMode = isDarkMode)
                            }

                            if (needsCalendarPermission) {
                                AjustesItemRow(
                                    title = texts.settingGoogleCalendarTitle,
                                    subtitle = texts.settingGoogleCalendarSubtitle,
                                    icon = Icons.Default.Event,
                                    iconTint = Color(0xFF00ACC1),
                                    iconContainerColor = Color(0xFF00ACC1).copy(alpha = 0.12f),
                                    trailingContent = {
                                        Button(
                                            onClick = {
                                                calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(34.dp)
                                                .testTag("connect_google_calendar_button")
                                        ) {
                                            Text(
                                                text = texts.settingGoogleCalendarConnect,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. INFORMACIÓN Y SISTEMA
                item {
                    AjustesSectionHeader(
                        title = if (appLanguage == AppLanguage.ES) "Información y ayuda" else "Informació i ajuda"
                    )
                    AjustesGroupCard(isDarkMode = isDarkMode) {
                        // Avisos del servicio
                        AjustesItemRow(
                            title = if (appLanguage == AppLanguage.ES) "Avisos e incidencias activas" else "Avisos i incidències actives",
                            subtitle = if (allActiveAnnouncements.isNotEmpty()) {
                                if (appLanguage == AppLanguage.ES) "${allActiveAnnouncements.size} avisos activos en la red" else "${allActiveAnnouncements.size} avisos actius a la xarxa"
                            } else {
                                if (appLanguage == AppLanguage.ES) "Sin incidencias importantes reportadas" else "Sense incidències importants reportades"
                            },
                            icon = Icons.Default.NotificationsActive,
                            iconTint = if (allActiveAnnouncements.isNotEmpty()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                            iconContainerColor = (if (allActiveAnnouncements.isNotEmpty()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                            onClick = {
                                viewModel.loadAllActiveAnnouncements()
                                showActiveAnnouncementsDialog = true
                            },
                            modifier = Modifier.testTag("ajustes_bell_button"),
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (allActiveAnnouncements.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFEF3C7)
                                        ) {
                                            Text(
                                                text = "${allActiveAnnouncements.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )

                        AjustesDivider(isDarkMode = isDarkMode)

                        // Guía interactiva de bienvenida (Onboarding)
                        AjustesItemRow(
                            title = stringResource(R.string.setting_onboarding_title),
                            subtitle = stringResource(R.string.setting_onboarding_sub),
                            icon = Icons.Default.AutoAwesome,
                            iconTint = Color(0xFF10B981),
                            iconContainerColor = Color(0xFF10B981).copy(alpha = 0.12f),
                            onClick = { viewModel.restartOnboarding() },
                            modifier = Modifier.testTag("setting_onboarding_card")
                        )

                        AjustesDivider(isDarkMode = isDarkMode)

                        // Acerca de la aplicación
                        AjustesItemRow(
                            title = texts.settingAboutTitle,
                            subtitle = "${texts.settingAboutSubtitle} • v${BuildConfig.VERSION_NAME}",
                            icon = Icons.Default.Info,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = { showAboutDialog = true }
                        )
                    }
                }

                // 5. ACTUALIZACIONES DE LA APLICACIÓN
                item {
                    AjustesSectionHeader(
                        title = if (appLanguage == AppLanguage.ES) "Actualizaciones" else "Actualitzacions"
                    )
                    AppUpdateSettingCard(
                        viewModel = appUpdateViewModel,
                        isDarkMode = isDarkMode
                    )
                }

                // Versión de la app al final
                item {
                    Text(
                        text = stringResource(R.string.setting_version_format, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        if (showAboutDialog) {
            AboutAppDialog(
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                onDismiss = { showAboutDialog = false }
            )
        }

        if (showStationSelectionPage) {
            MetroStationSelectionDialog(
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                metroViewModel = metroViewModel,
                onDismiss = { showStationSelectionPage = false }
            )
        }

        if (showCercaniasStationSelectionPage) {
            CercaniasStationSelectionDialog(
                viewModel = cercaniasViewModel,
                onDismiss = { showCercaniasStationSelectionPage = false }
            )
        }

        if (showActiveAnnouncementsDialog) {
            ActiveAnnouncementsDialog(
                announcements = allActiveAnnouncements,
                isLoading = isAnnouncementsLoading,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onDismiss = { showActiveAnnouncementsDialog = false }
            )
        }
    }
}
