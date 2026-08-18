package com.example.ui.dashboard

import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.cercanias.CercaniasStationSelectionDialog
import com.example.util.LocationUtils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.ui.metro.MetroViewModel
import com.example.ui.metro.MetroStationSelectionDialog
import com.example.ui.metro.computeMetroSearchScore
import com.example.ui.theme.ScreenHeader
import com.example.ui.theme.UnifiedAppCard
import com.example.ui.theme.appCardBorder
import kotlinx.coroutines.launch
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    viewModel: DashboardViewModel,
    cercaniasViewModel: CercaniasViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    metroViewModel: MetroViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useGpsOnOpen by viewModel.useGpsOnOpen.collectAsState()
    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
    val cercaniasFavoriteStations by cercaniasViewModel.cercaniasFavoriteStations.collectAsState()

    var showStationSelectionPage by remember { mutableStateOf(false) }
    var showCercaniasStationSelectionPage by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isCalendarConnected by remember { mutableStateOf(false) }

    val cardBg = if (isDarkMode) Color(0xFF171D2C) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

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
            Toast.makeText(context, "Sincronizando eventos de Google Calendar...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permiso de calendario denegado.", Toast.LENGTH_LONG).show()
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
    } else if (showCercaniasStationSelectionPage) {
        CercaniasStationSelectionDialog(
            viewModel = cercaniasViewModel,
            onDismiss = { showCercaniasStationSelectionPage = false }
        )
    } else {
        // --- VISTA PRINCIPAL DE AJUSTES ---
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("ajustes_screen")
        ) {
            ScreenHeader(
                title = texts.headerAjustesTitle,
                subtitle = texts.headerAjustesSubtitle,
                onBackClick = onBackClick
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 0. Language Setting Card
                item {
                    UnifiedAppCard(
                        modifier = Modifier,
                        startContent = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF1D283F) else Color(0xFFE8EAF6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        centerContent = {
                            Text(
                                text = texts.settingLanguageTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor
                            )
                        },
                        endContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppLanguage.values().forEach { lang ->
                                    val isSelected = appLanguage == lang
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) accentColor.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) accentColor else borderColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.setAppLanguage(lang) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (lang == AppLanguage.ES) "ES" else "VAL",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) accentColor else textColor
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // 1. Location Permission Card
                val hasLocationPermission = LocationUtils.hasLocationPermission(context)
                val isFineLocation = LocationUtils.hasFineLocationPermission(context)
                val isOnlyCoarse = LocationUtils.hasOnlyCoarseLocationPermission(context)

                item {
                    UnifiedAppCard(
                        modifier = Modifier,
                        startContent = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF221F13) else Color(0xFFFFF9C4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isDarkMode) Color(0xFFFFC107) else Color(0xFFF57F17),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        centerContent = {
                            Column {
                                Text(
                                    text = texts.settingGpsPermissionTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        isOnlyCoarse -> if (appLanguage == AppLanguage.CA) "Ubicació no precisa (aproximada). Es recomana la precisa." else "Ubicación no precisa (aproximada). Se recomienda la precisa."
                                        isFineLocation -> if (appLanguage == AppLanguage.CA) "Ubicació precisa activada" else "Ubicación precisa activada"
                                        else -> texts.settingGpsPermissionSubtitle
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOnlyCoarse) MaterialTheme.colorScheme.error else subtextColor
                                )
                            }
                        },
                        endContent = {
                            Button(
                                onClick = {
                                    if (!isFineLocation) {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isFineLocation -> MaterialTheme.colorScheme.primaryContainer
                                        isOnlyCoarse -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> accentColor
                                    },
                                    contentColor = when {
                                        isFineLocation -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isOnlyCoarse -> MaterialTheme.colorScheme.onSecondaryContainer
                                        else -> Color.White
                                    }
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                if (isFineLocation) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = when {
                                            isOnlyCoarse -> if (appLanguage == AppLanguage.CA) "Millorar ubicació" else "Mejorar ubicación"
                                            else -> texts.settingGpsPermissionAction
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    )
                }

                // 2. Light/Dark Mode Setting Card
                item {
                    UnifiedAppCard(
                        modifier = Modifier,
                        startContent = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF1D283F) else Color(0xFFE8EAF6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        centerContent = {
                            Text(
                                text = texts.settingThemeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor
                            )
                        },
                        endContent = {
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.toggleDarkMode() },
                                modifier = Modifier.testTag("dark_mode_switch")
                            )
                        }
                    )
                }

                // 3. Select Metrovalencia Stations Card
                item {
                    UnifiedAppCard(
                        modifier = Modifier,
                        onClick = { showStationSelectionPage = true },
                        startContent = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF1D3F2D) else Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subway,
                                    contentDescription = null,
                                    tint = if (isDarkMode) Color(0xFF2ECC71) else Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        centerContent = {
                            Column {
                                Text(
                                    text = texts.settingMetroStationsTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${favoriteStations.size} ${texts.settingStationsSelectedSuffix}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtextColor
                                )
                            }
                        },
                        endContent = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Configurar",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
                
                // 3.5 Select Cercanías Stations Card
                item {
                    UnifiedAppCard(
                        modifier = Modifier,
                        onClick = { showCercaniasStationSelectionPage = true },
                        startContent = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF3F1D1D) else Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsRailway,
                                    contentDescription = null,
                                    tint = if (isDarkMode) Color(0xFFE53935) else Color(0xFFC62828),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        centerContent = {
                            Column {
                                Text(
                                    text = texts.settingCercaniasStationsTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${cercaniasFavoriteStations.size} ${texts.settingStationsSelectedSuffix}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtextColor
                                )
                            }
                        },
                        endContent = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Configurar",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }

                // 4. Google Calendar Connection Card
                item {
                    Card(
                        border = appCardBorder(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(if (isDarkMode) Color(0xFF1D3B3F) else Color(0xFFE0F7FA), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFF00ACC1) else Color(0xFF006064),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = texts.settingGoogleCalendarTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = texts.settingGoogleCalendarSubtitle,
                                        fontSize = 11.sp,
                                        color = subtextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            val buttonContainerColor = if (isCalendarConnected) {
                                if (isDarkMode) Color(0xFF132219) else Color(0xFFE8F5E9)
                            } else {
                                accentColor
                            }
                            val buttonContentColor = if (isCalendarConnected) {
                                Color(0xFF2ECC71)
                            } else {
                                Color.White
                            }

                            Button(
                                onClick = {
                                    if (isCalendarConnected) {
                                        viewModel.syncGoogleCalendarEvents()
                                        Toast.makeText(context, if (appLanguage == AppLanguage.ES) "Sincronizando eventos..." else "Sincronitzant esdeveniments...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_google_calendar_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonContainerColor,
                                    contentColor = buttonContentColor
                                ),
                                shape = RoundedCornerShape(18.dp),
                                border = if (isCalendarConnected) BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.3f)) else null
                            ) {
                                Text(
                                    text = if (isCalendarConnected) texts.settingGoogleCalendarConnected else texts.settingGoogleCalendarConnect,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 5. About the App Card
                item {
                    UnifiedAppCard(
                        modifier = Modifier,
                        onClick = { showAboutDialog = true },
                        startContent = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF1E283F) else Color(0xFFE8EAF6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        centerContent = {
                            Column {
                                Text(
                                    text = texts.settingAboutTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = texts.settingAboutSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtextColor
                                )
                            }
                        },
                        endContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = if (appLanguage == AppLanguage.ES) "Ver detalles" else "Veure detalls",
                                tint = subtextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            Text(
                text = if (appLanguage == AppLanguage.CA) "Versió ${BuildConfig.VERSION_NAME}" else "Versión ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = subtextColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
            )
        }
    }
}

