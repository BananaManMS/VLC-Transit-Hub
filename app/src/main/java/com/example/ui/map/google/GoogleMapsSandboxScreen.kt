package com.example.ui.map.google

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.map.MapViewModel
import com.example.ui.metro.MetroViewModel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleMapsSandboxScreen(
    metroViewModel: MetroViewModel,
    cercaniasViewModel: CercaniasViewModel = viewModel(),
    mapViewModel: MapViewModel = viewModel(),
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current

    // Transit Datasets
    val networkStations by metroViewModel.allNetworkStations.collectAsState()
    val allMetroStations = remember(networkStations) {
        networkStations.ifEmpty {
            com.example.data.model.ValenciaMetroData.allNetworkStationsCache.ifEmpty {
                com.example.data.model.ValenciaMetroData.mainMetroStations
            }
        }
    }

    val cercaniasStationsFromVm by cercaniasViewModel.allCercaniasStations.collectAsState()
    val cercaniasStationsFromMap by mapViewModel.cercaniasStations.collectAsState()
    val allCercaniasStations = remember(cercaniasStationsFromVm, cercaniasStationsFromMap) {
        cercaniasStationsFromVm.ifEmpty { cercaniasStationsFromMap }
    }

    val allBusStops by mapViewModel.busStops.collectAsState()
    val allMetrobusStops by mapViewModel.metrobusStops.collectAsState()

    var busStopsFallback by remember { mutableStateOf<List<GeoportalStopEntity>>(emptyList()) }
    var metrobusStopsFallback by remember { mutableStateOf<List<MetrobusStopEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.example.data.database.AppDatabase.getDatabase(context)
                if (allBusStops.isEmpty()) {
                    val dbStops = db.geoportalStopDao().getAllActiveStops()
                    val stops = if (dbStops.isNotEmpty()) dbStops else com.example.ui.bus.BusMapper.parseStopsFromJsonDirect(context.applicationContext as android.app.Application)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        busStopsFallback = stops
                    }
                }
                if (allMetrobusStops.isEmpty()) {
                    val mbStops = db.metrobusStopDao().getAllActiveStops()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        metrobusStopsFallback = mbStops
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val effectiveBusStops = remember(allBusStops, busStopsFallback) {
        allBusStops.ifEmpty { busStopsFallback }
    }
    val effectiveMetrobusStops = remember(allMetrobusStops, metrobusStopsFallback) {
        allMetrobusStops.ifEmpty { metrobusStopsFallback }
    }

    // Layer Controls
    var isDarkMode by remember { mutableStateOf(false) }
    var isSatelliteMode by remember { mutableStateOf(false) }
    var showMetroLines by remember { mutableStateOf(true) }
    var showCercaniasLines by remember { mutableStateOf(true) }
    var showMetroFootprints by remember { mutableStateOf(true) }
    var showMetroStations by remember { mutableStateOf(true) }
    var showCercaniasStations by remember { mutableStateOf(true) }
    var showBusStops by remember { mutableStateOf(true) }
    var showMetrobusStops by remember { mutableStateOf(true) }

    // Map Interaction & Centering
    var bottomPaddingDp by remember { mutableFloatStateOf(0f) }
    var selectedItem by remember { mutableStateOf<GoogleMapTransitItem?>(null) }
    var showOpticalCenterGuide by remember { mutableStateOf(true) }
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }
    var mapLoadError by remember { mutableStateOf<String?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // API Key & Google Play Services checks
    val rawApiKey = BuildConfig.GOOGLE_MAPS_API_KEY
    val isApiKeyConfigured = rawApiKey.isNotBlank() && !rawApiKey.contains("YOUR_")
    val playServicesCode = remember {
        try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        } catch (_: Exception) {
            -1
        }
    }
    val isPlayServicesAvailable = playServicesCode == ConnectionResult.SUCCESS

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🧪 Google Maps Lab (Beta)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pruebas de migración y paridad completa de capas",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isDiagnosticsExpanded = !isDiagnosticsExpanded }) {
                        Icon(
                            imageVector = if (isDiagnosticsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.Info,
                            contentDescription = "Diagnóstico"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Google Maps Test Component with full transit parity
            GoogleMapsTestMapView(
                modifier = Modifier.fillMaxSize(),
                isDarkMode = isDarkMode,
                isSatelliteMode = isSatelliteMode,
                showMetroLines = showMetroLines,
                showCercaniasLines = showCercaniasLines,
                showMetroFootprints = showMetroFootprints,
                showMetroStations = showMetroStations,
                showCercaniasStations = showCercaniasStations,
                showBusStops = showBusStops,
                showMetrobusStops = showMetrobusStops,
                metroStations = allMetroStations,
                cercaniasStations = allCercaniasStations,
                busStops = effectiveBusStops,
                metrobusStops = effectiveMetrobusStops,
                selectedItem = selectedItem,
                bottomPaddingDp = bottomPaddingDp.dp,
                showOpticalCenterGuide = showOpticalCenterGuide,
                onItemClick = { item ->
                    selectedItem = item
                    if (bottomPaddingDp == 0f) {
                        bottomPaddingDp = 150f
                    }
                },
                onMapReady = {
                    isMapLoaded = true
                },
                onMapError = { errorMsg ->
                    mapLoadError = errorMsg
                }
            )

            // 2. Control & Diagnostics floating overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Diagnostics Panel
                AnimatedVisibility(
                    visible = isDiagnosticsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (!isApiKeyConfigured || !isPlayServicesAvailable) Icons.Default.Warning else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (!isApiKeyConfigured || !isPlayServicesAvailable) Color(0xFFF59E0B) else Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Diagnóstico Google Maps SDK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = if (isMapLoaded) "Mapa: Conectado" else "Mapa: Esperando...",
                                    fontSize = 11.sp,
                                    color = if (isMapLoaded) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // API Key Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "API Key:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isApiKeyConfigured) "Configurada (${rawApiKey.take(6)}...)" else "❌ No configurada",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isApiKeyConfigured) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }

                            // Play Services Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Play Services:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isPlayServicesAvailable) "✅ Disponible" else "⚠️ Código: $playServicesCode",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isPlayServicesAvailable) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }

                            // Bottom padding simulation slider
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recentrado nativo (Padding inferior):",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${bottomPaddingDp.toInt()} dp",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = bottomPaddingDp,
                                    onValueChange = { bottomPaddingDp = it },
                                    valueRange = 0f..260f,
                                    modifier = Modifier.height(24.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val presets = listOf(0 to "0 dp", 140 to "140 dp (Ficha)", 240 to "240 dp (Horarios)")
                                    presets.forEach { (value, label) ->
                                        val isSelected = bottomPaddingDp.toInt() == value
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { bottomPaddingDp = value.toFloat() },
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Horizontal Filter Chips for toggling layers, styles, and optical center guide
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showOpticalCenterGuide,
                        onClick = { showOpticalCenterGuide = !showOpticalCenterGuide },
                        label = { Text("🎯 Visor Centro", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showOpticalCenterGuide) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )

                    FilterChip(
                        selected = isDarkMode,
                        onClick = { isDarkMode = !isDarkMode },
                        label = { Text(if (isDarkMode) "Oscuro" else "Claro", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )

                    FilterChip(
                        selected = isSatelliteMode,
                        onClick = { isSatelliteMode = !isSatelliteMode },
                        label = { Text("Satélite", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Satellite,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )

                    FilterChip(
                        selected = showMetroLines,
                        onClick = { showMetroLines = !showMetroLines },
                        label = { Text("🚇 Líneas Metro", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showMetroLines) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )

                    FilterChip(
                        selected = showCercaniasLines,
                        onClick = { showCercaniasLines = !showCercaniasLines },
                        label = { Text("🚆 Líneas Cercanías", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showCercaniasLines) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )

                    FilterChip(
                        selected = showMetroStations,
                        onClick = { showMetroStations = !showMetroStations },
                        label = { Text("🔴 Metro (${allMetroStations.size})", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showMetroStations) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )

                    FilterChip(
                        selected = showCercaniasStations,
                        onClick = { showCercaniasStations = !showCercaniasStations },
                        label = { Text("🚆 Cercanías (${allCercaniasStations.size})", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showCercaniasStations) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )

                    FilterChip(
                        selected = showBusStops,
                        onClick = { showBusStops = !showBusStops },
                        label = { Text("🚌 EMT (${effectiveBusStops.size})", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showBusStops) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )

                    FilterChip(
                        selected = showMetrobusStops,
                        onClick = { showMetrobusStops = !showMetrobusStops },
                        label = { Text("🚍 Metrobús (${effectiveMetrobusStops.size})", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showMetrobusStops) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )

                    FilterChip(
                        selected = showMetroFootprints,
                        onClick = { showMetroFootprints = !showMetroFootprints },
                        label = { Text("🏢 Huellas", fontSize = 12.sp) },
                        leadingIcon = {
                            if (showMetroFootprints) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )
                }
            }

            // 3. Selected Item Preview Sheet / Card (anchored cleanly at the bottom)
            selectedItem?.let { item ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                when (item) {
                                    is GoogleMapTransitItem.Metro -> {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = Color(0xFFE2001A)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "m",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.station.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "Metrovalencia • Zona ${item.station.zone} • ${item.station.lines.joinToString(", ") { "L$it" }}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    is GoogleMapTransitItem.Cercanias -> {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = Color(0xFF702B7B)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "C",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.station.nombre,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "Cercanías Renfe • ${item.station.lineas.joinToString(", ") { if (it.startsWith("C")) it else "C$it" }}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    is GoogleMapTransitItem.Bus -> {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFE52320)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "EMT",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.stop.denominacion,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Parada #${item.stop.id_parada} • Líneas: ${item.stop.lineas ?: "N/D"}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    is GoogleMapTransitItem.Metrobus -> {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFF59E0B)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "MB",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.stop.denominacion,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Metrobús #${item.stop.id} • ${item.stop.municipio} • L: ${item.stop.lineas}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    is GoogleMapTransitItem.Merged -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                modifier = Modifier.size(24.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFE52320)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("E", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.size(24.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFF59E0B)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.busStop.denominacion,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Parada Fusionada EMT #${item.busStop.id_parada} + Metrobús #${item.mbStop.id}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    is GoogleMapTransitItem.Cluster -> {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = if (item.isMetrobus) Color(0xFFF59E0B) else Color(0xFFE52320)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${item.count}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Zona agrupada por nivel de zoom • Toca para ampliar",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    is GoogleMapTransitItem.AccessPoint -> {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = Color(0xFFDC2626)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = item.access.labelText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "${item.access.stationName} • ${item.access.typeCas}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    else -> {
                                        Column {
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = {
                                selectedItem = null
                                bottomPaddingDp = 0f
                            }) {
                                Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Explanation pill of native recentering
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Offset visible: ${bottomPaddingDp.toInt()} dp • Elemento centrado sobre la ficha",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (bottomPaddingDp == 0f) {
                                    Text(
                                        text = "Subir a 140dp",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { bottomPaddingDp = 140f }
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
