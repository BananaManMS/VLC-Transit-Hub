package com.example.ui.dashboard

import com.example.ui.cercanias.CercaniasViewModel
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


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    cercaniasViewModel: CercaniasViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    metroViewModel: MetroViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    viewModel: DashboardViewModel,
    onConfigureStations: () -> Unit,
    onConfigureCercaniasStations: () -> Unit,
    onLaunchLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 7 })
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val cercaniasFavoriteStations by cercaniasViewModel.cercaniasFavoriteStations.collectAsState()
    val useGpsOnOpen by viewModel.useGpsOnOpen.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val context = LocalContext.current

    // Check if permissions are already granted dynamically
    var isLocationConnected by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isLocationConnected = LocationUtils.hasLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isLocationConnected = LocationUtils.hasLocationPermission(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main sliding cards content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (page) {
                    0 -> {
                        // PASO 1: Idioma
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(90.dp)
                                .padding(bottom = 24.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Idioma de l'aplicació" else "Idioma de la aplicación",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Tria l'idioma preferit per a utilitzar l'aplicació." else "Elige el idioma preferido para utilizar la aplicación.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppLanguage.values().forEach { lang ->
                                val isSelected = appLanguage == lang
                                val accentColor = MaterialTheme.colorScheme.primary
                                val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                val textColor = MaterialTheme.colorScheme.onSurface
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) accentColor.copy(alpha = 0.15f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isSelected) accentColor else borderColor,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.setAppLanguage(lang) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = if (lang == AppLanguage.ES) "Español" else "Valencià",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isSelected) accentColor else textColor
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // PASO 2: Bienvenida
                        Icon(
                            imageVector = Icons.Default.Subway,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(100.dp)
                                .padding(bottom = 24.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Benvingut a VLC Transit!" else "¡Bienvenido a VLC Transit!",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "El teu company intel·ligent per a viatjar per la xarxa de Metrovalencia. Consulta horaris en directe, alertes de servei i planifica els teus viatges fàcilment." else "Tu compañero inteligente para viajar por la red de Metrovalencia. Consulta horarios en directo, alertas de servicio y planifica tus viajes con facilidad.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    2 -> {
                        // PASO 3: Estaciones favoritas metro
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = Color(0xFFFFB300), // Lovely amber map icon
                            modifier = Modifier
                                .size(90.dp)
                                .padding(bottom = 24.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Estacions Favorites de Metro" else "Estaciones Favoritas de Metro",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Configura les teues estacions favorites de Metrovalencia per a accedir a les pròximes eixides en temps real." else "Configura tus estaciones preferidas de Metrovalencia para acceder al tiempo real.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        
                        Button(
                            onClick = onConfigureStations,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("onboarding_btn_favorites")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (appLanguage == AppLanguage.CA) "Configurar Metro" else "Configurar Metro")
                        }
                        
                        if (favoriteStations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "✓ ${favoriteStations.size} estacions seleccionades" else "✓ ${favoriteStations.size} estaciones seleccionadas",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    3 -> {
                        // PASO 4: Estaciones favoritas Cercanías
                        Icon(
                            imageVector = Icons.Default.DirectionsRailway,
                            contentDescription = null,
                            tint = Color(0xFF702B7B), // Renfe Purple
                            modifier = Modifier
                                .size(90.dp)
                                .padding(bottom = 24.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Estacions Favorites de Rodalia" else "Estaciones Favoritas de Cercanías",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Configura les teues estacions de Rodalia preferides per a consultar els horaris en directe i pròximes eixides." else "Configura tus estaciones de Cercanías preferidas para consultar los horarios en directo.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = onConfigureCercaniasStations,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("onboarding_btn_cercanias_favorites")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (appLanguage == AppLanguage.CA) "Configuració de Rodalia" else "Configuración de Cercanías")
                        }

                        if (cercaniasFavoriteStations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "✓ ${cercaniasFavoriteStations.size} estacions seleccionades" else "✓ ${cercaniasFavoriteStations.size} estaciones seleccionadas",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    4 -> {
                        // PASO 5: Tema de la aplicación
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(90.dp)
                                .padding(bottom = 24.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Tria el teu Aspecte / Tema" else "Elige tu Estilo",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Prefereixes un aspecte clar o un disseny fosc optimitzat per a condicions de poca llum?" else "¿Prefieres un aspecto claro o un diseño oscuro optimizado para condiciones de poca luz?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        Card(
                            border = appCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Modo fosc" else "Modo oscuro",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) {
                                            if (isDarkMode) "Mode Fosc activat" else "Mode Clar activat"
                                        } else {
                                            if (isDarkMode) "Modo Oscuro activado" else "Modo Claro activado"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = { viewModel.toggleDarkMode() },
                                    modifier = Modifier.testTag("onboarding_dark_mode_switch")
                                )
                            }
                        }
                    }
                    5 -> {
                        // PASO 6: Ubicación inteligente
                        val isFineLocation = LocationUtils.hasFineLocationPermission(context)
                        val isOnlyCoarse = LocationUtils.hasOnlyCoarseLocationPermission(context)

                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isLocationConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(90.dp)
                                .padding(bottom = 24.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Ubicació Intel·ligent" else "Ubicación Inteligente",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Permet consultar el clima i les estacions més properes en directe." else "Permite consultar el clima y las estaciones más cercanas en directo.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        if (isOnlyCoarse) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA)
                                            "Has donat ubicació no precisa. Algunes funcions (com la cerca de parades pròximes i les distàncies) poden no ser exactes."
                                        else
                                            "Has concedido ubicación no precisa. Algunas funciones (como la búsqueda de paradas cercanas y las distancias) pueden no ser exactas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        if (isLocationConnected) {
                            Button(
                                onClick = {
                                    if (isOnlyCoarse) {
                                        onLaunchLocationPermission()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOnlyCoarse) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (isOnlyCoarse) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("onboarding_btn_gps")
                            ) {
                                Icon(
                                    imageVector = if (isFineLocation) Icons.Default.Check else Icons.Default.LocationOn,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isOnlyCoarse -> if (appLanguage == AppLanguage.CA) "Ubicació activada (no precisa)" else "Ubicación activada (no precisa)"
                                        else -> if (appLanguage == AppLanguage.CA) "Ubicació activada" else "Ubicación activada"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    onLaunchLocationPermission()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("onboarding_btn_gps")
                            ) {
                                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (appLanguage == AppLanguage.CA) "Activar Ubicació" else "Activar Ubicación")
                            }
                        }
                    }
                    6 -> {
                        // PASO 7: Finalización
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32), // Custom green
                            modifier = Modifier
                                .size(100.dp)
                                .padding(bottom = 24.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Tot Llest!" else "¡Todo Listo!",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Has configurat els accessos i preferències inicials de VLC Transit amb èxit. Ja pots planificar les teues rutes i viatjar còmodament." else "Has configurado los accesos y preferencias iniciales de VLC Transit con éxito. Ya puedes planificar tus rutas y viajar cómodamente.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { viewModel.completeOnboarding() },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(56.dp)
                                .testTag("onboarding_finish_button")
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Començar" else "Empezar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Navigation Area (Indicators & Prev/Next Buttons)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pager indicator dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                repeat(7) { index ->
                    val color = if (pagerState.currentPage == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                    val width = if (pagerState.currentPage == index) 20.dp else 8.dp
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(height = 8.dp, width = width)
                            .background(color, CircleShape)
                    )
                }
            }

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Backward button
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.testTag("onboarding_btn_prev")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (appLanguage == AppLanguage.CA) "Enrere" else "Atrás")
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // Omit / Skip option
                if (pagerState.currentPage < 6) {
                    TextButton(
                        onClick = {
                            viewModel.completeOnboarding()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("onboarding_btn_skip")
                    ) {
                        Text(if (appLanguage == AppLanguage.CA) "Ometre" else "Saltar")
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("onboarding_btn_next")
                    ) {
                        Text(if (appLanguage == AppLanguage.CA) "Següent" else "Siguiente")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

