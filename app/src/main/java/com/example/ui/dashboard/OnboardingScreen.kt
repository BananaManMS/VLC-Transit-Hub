package com.example.ui.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.metro.MetroViewModel
import com.example.data.database.TransitCardEntity
import com.example.ui.metro.cards.AddTransitCardWizardDialog
import com.example.ui.metro.cards.CardDisplayFormat
import com.example.ui.metro.cards.UnifiedTransitCardView
import com.example.util.LocationUtils
import kotlinx.coroutines.launch

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
    val pagerState = rememberPagerState(pageCount = { 5 })
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val cercaniasFavoriteStations by cercaniasViewModel.cercaniasFavoriteStations.collectAsState()
    val transitCards by metroViewModel.transitCardsFlow.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val context = LocalContext.current

    val preferredModes by viewModel.favoriteTransitModes.collectAsState()

    fun toggleMode(mode: String) {
        viewModel.togglePreferredTransitMode(mode)
    }

    var showAddCardDialog by remember { mutableStateOf(false) }

    // Check location permission state
    var isLocationConnected by remember { mutableStateOf(LocationUtils.hasLocationPermission(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isLocationConnected = LocationUtils.hasLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pagerState.currentPage) {
        isLocationConnected = LocationUtils.hasLocationPermission(context)
    }

    val stepTitles = remember(appLanguage) {
        if (appLanguage == AppLanguage.CA) {
            listOf("Benvinguda", "Mitjans i parades", "Targetes", "Ajustos", "Resum")
        } else {
            listOf("Bienvenida", "Medios y paradas", "Tarjetas", "Ajustes", "Resumen")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle background gradient atmosphere
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkMode) 0.15f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Top Navigation & Step Indicator Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Step Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${pagerState.currentPage + 1}/5 · ${stepTitles[pagerState.currentPage]}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Close / Exit button
            IconButton(
                onClick = { viewModel.completeOnboarding() },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("onboarding_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = if (appLanguage == AppLanguage.CA) "Tancar" else "Cerrar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Pager with main onboarding steps
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 52.dp, bottom = 90.dp)
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (page) {
                        0 -> {
                            // PASO 1: Bienvenida, Selector de Idioma y Funciones Clave
                            // Segmented Language Switcher
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppLanguage.values().forEach { lang ->
                                    val isSelected = appLanguage == lang
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            )
                                            .clickable { viewModel.setAppLanguage(lang) }
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (lang == AppLanguage.ES) "Español" else "Valencià",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Benvingut a VLC Transit" else "Bienvenido a VLC Transit",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA)
                                "La teua guia integral de mobilitat metropolitana a València."
                            else
                                "Tu guía integral de movilidad metropolitana en Valencia.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Features Grid with high visual craft
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FeatureHighlightRow(
                                icon = Icons.Default.AltRoute,
                                iconColor = Color(0xFF0284C7),
                                title = if (appLanguage == AppLanguage.CA) "Xarxa unificada multimodal" else "Red unificada multimodal",
                                description = if (appLanguage == AppLanguage.CA)
                                    "Metrovalencia, EMT, Renfe Rodalia, Metrobús i Valenbisi connectats."
                                else
                                    "Metrovalencia, EMT, Renfe Cercanías, Metrobús y Valenbisi conectados."
                            )

                            FeatureHighlightRow(
                                icon = Icons.Default.NearMe,
                                iconColor = Color(0xFF10B981),
                                title = if (appLanguage == AppLanguage.CA) "Copilot i eixides en directe" else "Copiloto y salidas en vivo",
                                description = if (appLanguage == AppLanguage.CA)
                                    "Horaris en temps real i notificacions d'arribada pas a pas."
                                else
                                    "Horarios en tiempo real y notificaciones de transbordo paso a paso."
                            )

                            FeatureHighlightRow(
                                icon = Icons.Default.CreditCard,
                                iconColor = Color(0xFFF59E0B),
                                title = if (appLanguage == AppLanguage.CA) "Lector NFC de targetes SUMA" else "Lector NFC de tarjetas SUMA",
                                description = if (appLanguage == AppLanguage.CA)
                                    "Consulta viatges restants, saldo i caducitat amb el mòbil."
                                else
                                    "Consulta viajes restantes, saldo y caducidad con el móvil."
                            )

                            FeatureHighlightRow(
                                icon = Icons.Default.WarningAmber,
                                iconColor = Color(0xFFEF4444),
                                title = if (appLanguage == AppLanguage.CA) "Incidències i alertes" else "Incidencias y alertas",
                                description = if (appLanguage == AppLanguage.CA)
                                    "Avisos oficials d'interrupcions i retards al moment."
                                else
                                    "Avisos oficiales de interrupciones y retrasos al momento."
                            )
                        }
                    }

                    1 -> {
                        // PASO 2: Medios y Paradas Favoritas
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Mitjans i parades clau" else "Medios y paradas clave",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA)
                                "Personalitza els transports habituals i les teues estacions favorites per a tindre eixides directes al Dashboard."
                            else
                                "Personaliza tus medios habituales y tus estaciones favoritas para tener salidas directas en el Dashboard.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Modes Selector Grid
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Medis de transport actius:" else "Medios de transporte activos:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TransitModeTile(
                                icon = Icons.Default.Subway,
                                label = "Metro",
                                color = Color(0xFFEF4444),
                                isSelected = preferredModes.contains("METRO"),
                                onClick = { toggleMode("METRO") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_mode_metro")
                            )
                            TransitModeTile(
                                icon = Icons.Default.DirectionsBus,
                                label = "EMT Bus",
                                color = Color(0xFF0284C7),
                                isSelected = preferredModes.contains("EMT"),
                                onClick = { toggleMode("EMT") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_mode_emt")
                            )
                            TransitModeTile(
                                icon = Icons.Default.DirectionsRailway,
                                label = if (appLanguage == AppLanguage.CA) "Rodalia" else "Cercanías",
                                color = Color(0xFF702B7B),
                                isSelected = preferredModes.contains("CERCANIAS"),
                                onClick = { toggleMode("CERCANIAS") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_mode_cercanias")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TransitModeTile(
                                icon = Icons.Default.PedalBike,
                                label = "Valenbisi",
                                color = Color(0xFF10B981),
                                isSelected = preferredModes.contains("VALENBISI"),
                                onClick = { toggleMode("VALENBISI") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_mode_valenbisi")
                            )
                            TransitModeTile(
                                icon = Icons.Default.AirportShuttle,
                                label = "Metrobús",
                                color = Color(0xFFF59E0B),
                                isSelected = preferredModes.contains("METROBUS"),
                                onClick = { toggleMode("METROBUS") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_mode_metrobus")
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Configure Stations Cards
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Estacions favorites:" else "Estaciones favoritas:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StationConfigCard(
                            icon = Icons.Default.Subway,
                            iconColor = Color(0xFFEF4444),
                            title = "Metrovalencia",
                            count = favoriteStations.size,
                            emptyText = if (appLanguage == AppLanguage.CA) "Cap estació triada" else "Ninguna estación elegida",
                            appLanguage = appLanguage,
                            testTag = "onboarding_btn_favorites",
                            onClick = onConfigureStations
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        StationConfigCard(
                            icon = Icons.Default.DirectionsRailway,
                            iconColor = Color(0xFF702B7B),
                            title = if (appLanguage == AppLanguage.CA) "Rodalia Renfe" else "Cercanías Renfe",
                            count = cercaniasFavoriteStations.size,
                            emptyText = if (appLanguage == AppLanguage.CA) "Cap estació triada" else "Ninguna estación elegida",
                            appLanguage = appLanguage,
                            testTag = "onboarding_btn_cercanias_favorites",
                            onClick = onConfigureCercaniasStations
                        )
                    }

                    2 -> {
                        // PASO 3: Tarjetas de Transporte SUMA / Móbilis
                        // Realistic SUMA Card Preview
                        SumaCardGraphic(isDarkMode = isDarkMode)

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Targetes de transport" else "Tarjetas de transporte",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA)
                                "Afegeix la teua targeta física per NFC o codi numèric per a conéixer viatges restants, saldo i caducitat en temps real."
                            else
                                "Añade tu tarjeta física por NFC o código numérico para conocer viajes restantes, saldo y caducidad en tiempo real.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Nfc,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Lectura NFC i codi de suport" else "Lectura NFC y código de soporte",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = if (appLanguage == AppLanguage.CA)
                                        "Acosta la teua targeta a la part posterior del telèfon per a llegir el xip Mifare o escriu el codi de 10 dígits."
                                    else
                                        "Acerca tu tarjeta a la parte trasera del móvil para leer el chip Mifare o escribe el código de 10 dígitos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { showAddCardDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.AddCard, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (appLanguage == AppLanguage.CA) "Registrar targeta ara" else "Registrar tarjeta ahora")
                                }

                                if (transitCards.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "✓ ${transitCards.size} " + if (appLanguage == AppLanguage.CA) "targetes guardades" else "tarjetas guardadas",
                                            color = Color(0xFF047857),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // PASO 4: Permisos y Personalización
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Permisos i aparença" else "Permisos y apariencia",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA)
                                "Configura la ubicació per a trobar parades al teu voltant i tria el teu tema preferit."
                            else
                                "Configura la ubicación para encontrar paradas a tu alrededor y elige tu tema preferido.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Location Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isLocationConnected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = if (isLocationConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (appLanguage == AppLanguage.CA) "Ubicació GPS" else "Ubicación GPS",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = if (isLocationConnected) {
                                                if (appLanguage == AppLanguage.CA) "Permís concedit · Estacions a prop actives" else "Permiso concedido · Estaciones cercanas activas"
                                            } else {
                                                if (appLanguage == AppLanguage.CA) "Requerit per a trobar parades i rutes en 1 toc" else "Requerido para paradas y rutas en 1 toque"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLocationConnected) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = onLaunchLocationPermission,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_btn_gps"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isLocationConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isLocationConnected) Icons.Default.Check else Icons.Default.MyLocation,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isLocationConnected) {
                                            if (appLanguage == AppLanguage.CA) "Ubicació activada" else "Ubicación activada"
                                        } else {
                                            if (appLanguage == AppLanguage.CA) "Permetre ubicació" else "Permitir ubicación"
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Theme Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
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
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Mode fosc" else "Modo oscuro",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA)
                                            if (isDarkMode) "Tema fosc activat" else "Tema clar activat"
                                        else
                                            if (isDarkMode) "Tema oscuro activado" else "Tema claro activado",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = { viewModel.toggleDarkMode() },
                                    modifier = Modifier.testTag("onboarding_dark_mode_switch")
                                )
                            }
                        }
                    }

                    4 -> {
                        // PASO 5: Resumen y Listo para Viajar
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Tot llest per a viatjar!" else "¡Todo listo para moverte!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.CA)
                                "Ja pots consultar línies, horaris en viu, saldo SUMA i rutes multimodals."
                            else
                                "Ya puedes consultar líneas, horarios en vivo, saldo SUMA y rutas multimodales.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Mobility Boarding Pass Style Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ConfirmationNumber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (appLanguage == AppLanguage.CA) "Passi de mobilitat València" else "Pase de movilidad Valencia",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "ACTIU",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                Spacer(modifier = Modifier.height(14.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SummaryRowItem(
                                        icon = Icons.Default.Language,
                                        title = if (appLanguage == AppLanguage.CA) "Idioma configurat" else "Idioma configurado",
                                        value = if (appLanguage == AppLanguage.ES) "Español" else "Valencià"
                                    )
                                    SummaryRowItem(
                                        icon = Icons.Default.AltRoute,
                                        title = if (appLanguage == AppLanguage.CA) "Medis actius" else "Medios activos",
                                        value = "${preferredModes.size} " + if (appLanguage == AppLanguage.CA) "transports" else "transportes"
                                    )
                                    SummaryRowItem(
                                        icon = Icons.Default.Star,
                                        title = if (appLanguage == AppLanguage.CA) "Estacions favorites" else "Estaciones favoritas",
                                        value = "${favoriteStations.size + cercaniasFavoriteStations.size} " + if (appLanguage == AppLanguage.CA) "guardades" else "guardadas"
                                    )
                                    SummaryRowItem(
                                        icon = Icons.Default.LocationOn,
                                        title = if (appLanguage == AppLanguage.CA) "Ubicació GPS" else "Ubicación GPS",
                                        value = if (isLocationConnected) "Activada" else (if (appLanguage == AppLanguage.CA) "Sense concedir" else "Sin conceder")
                                    )
                                    SummaryRowItem(
                                        icon = Icons.Default.CreditCard,
                                        title = if (appLanguage == AppLanguage.CA) "Targetes" else "Tarjetas",
                                        value = "${transitCards.size} " + if (appLanguage == AppLanguage.CA) "registrades" else "registradas"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.completeOnboarding() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("onboarding_finish_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.onboarding_start_traveling),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
        }

        // Floating Bottom Navigation (Dots & Next/Prev Actions)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    repeat(5) { index ->
                        val isCurrent = pagerState.currentPage == index
                        val animatedWidth by animateDpAsState(
                            targetValue = if (isCurrent) 24.dp else 8.dp,
                            animationSpec = spring(),
                            label = "dot_width"
                        )
                        val dotColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(8.dp)
                                .width(animatedWidth)
                                .background(dotColor, CircleShape)
                        )
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.testTag("onboarding_btn_prev")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.onboarding_back), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(60.dp))
                    }

                    if (pagerState.currentPage < 4) {
                        TextButton(
                            onClick = { viewModel.completeOnboarding() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.testTag("onboarding_btn_skip")
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.onboarding_skip))
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
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.onboarding_next), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(60.dp))
                    }
                }
            }
        }
    }

    if (showAddCardDialog) {
        AddTransitCardWizardDialog(
            appLanguage = appLanguage,
            metroViewModel = metroViewModel,
            onDismiss = { showAddCardDialog = false },
            onCardAdded = { showAddCardDialog = false }
        )
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TransitModeTile(
    icon: ImageVector,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isSelected) "✓" else "+",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) color else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun StationConfigCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    count: Int,
    emptyText: String,
    appLanguage: AppLanguage,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (count > 0) {
                            "$count " + if (appLanguage == AppLanguage.CA) "seleccionades" else "seleccionadas"
                        } else emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (count > 0) (if (appLanguage == AppLanguage.CA) "Canviar" else "Cambiar") else (if (appLanguage == AppLanguage.CA) "Triar" else "Elegir"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SumaCardGraphic(isDarkMode: Boolean) {
    val entity = TransitCardEntity(
        cardNumber = "094852801234",
        assignedName = "SUMA Zona AB",
        defaultName = "Targeta SUMA",
        cardType = "viajes",
        remainingValue = "8 Viatges",
        detailsJson = "{}"
    )
    val sampleCard = TransitCardUiModel(
        entity = entity,
        cardNumber = entity.cardNumber,
        assignedName = entity.assignedName,
        defaultName = entity.defaultName,
        cardType = entity.cardType,
        remainingValue = entity.remainingValue,
        detailsJson = entity.detailsJson,
        isFaded = false,
        isManuallyInactive = false,
        category = "SUMA_SENCILLO",
        title = "Targeta SUMA",
        clase = "Títol Integrat",
        operador = "ATMV",
        zonas = "Zona AB",
        ampliado = "No",
        fechaCaducidad = "31/12/2026",
        fechaRecarga = "01/01/2026",
        isCurrentlyActive = true,
        viajesList = emptyList()
    )

    UnifiedTransitCardView(
        card = sampleCard,
        format = CardDisplayFormat.HERO,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SummaryRowItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
