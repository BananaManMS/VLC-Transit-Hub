package com.example.ui.dashboard.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.ui.dashboard.AppLanguage
import com.example.ui.metro.MetroLineBadge
import com.example.util.LineColorResolver
import com.example.util.LocationUtils
import com.example.util.MetrobusLineColorResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val widgetRawCache = ConcurrentHashMap<String, RawWidgetCacheEntry>()

private data class RawWidgetCacheEntry(
    val timestamp: Long,
    val rawList: List<RawWidgetDeparture>
)

data class WidgetGroupedDeparture(
    val lineId: String,
    val destination: String,
    val timesFormatted: String,
    val colorHex: String,
    val isHabitual: Boolean
)

private data class RawWidgetDeparture(
    val lineId: String,
    val destination: String,
    val minutes: Int,
    val colorHex: String
)

@Composable
fun NearbyDeparturesWidget(
    appLanguage: AppLanguage,
    hasLocation: Boolean,
    nearestMetroStation: MetroStation?,
    isMetroFav: Boolean,
    nearestMetroDistance: Double?,
    nearestCercaniasStation: CercaniasStationEntity?,
    isCercaniasFav: Boolean,
    nearestCercaniasDistance: Double?,
    nearbyBusStops: List<Pair<GeoportalStopEntity, Boolean>>,
    busStopAliases: Map<String, String>,
    nearbyMetrobusStops: List<Pair<com.example.data.database.MetrobusStopEntity, Boolean>> = emptyList(),
    metrobusStopAliases: Map<String, String> = emptyMap(),
    habitualLines: Set<String> = emptySet(),
    refLat: Double,
    refLon: Double,
    onMetroStationClick: (String) -> Unit,
    onCercaniasStationClick: (String) -> Unit,
    onBusStopClick: (GeoportalStopEntity) -> Unit,
    onMetrobusStopClick: ((com.example.data.database.MetrobusStopEntity) -> Unit)? = null,
    onOpenHabitualLines: (() -> Unit)? = null,
    onRequestLocationPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("nearby_departures_widget"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row with Title and Settings Icon
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.nearby_departures_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (onOpenHabitualLines != null) {
                    IconButton(
                        onClick = onOpenHabitualLines,
                        modifier = Modifier.size(32.dp).testTag("nearby_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configurar líneas habituales",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!hasLocation) {
                // Location prompt banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.nearby_gps_banner_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestLocationPermission,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.setting_gps_permission_action), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Column {
                var hasPreviousItem = false

                // 1. METROVALENCIA STATION (MAX 1)
                if (nearestMetroStation != null) {
                    hasPreviousItem = true
                    NearbyTransitItemRow(
                        itemType = "METRO",
                        itemId = nearestMetroStation.id,
                        iconBox = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_metrovalencia),
                                    contentDescription = "Metrovalencia",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        name = nearestMetroStation.name,
                        isFav = isMetroFav,
                        baseSubtitle = if (nearestMetroDistance != null) {
                            "${LocationUtils.formatDistance(nearestMetroDistance)} · Metrovalencia"
                        } else "Metrovalencia",
                        lines = nearestMetroStation.lines,
                        appLanguage = appLanguage,
                        habitualLines = habitualLines,
                        badgeRenderer = { line ->
                            MetroLineBadge(lineId = line, size = 22.dp)
                        },
                        calculateBadgeWidthPx = { _, _, density ->
                            with(density) { 22.dp.toPx() }
                        },
                        onClick = { onMetroStationClick(nearestMetroStation.id) }
                    )
                }

                // 2. RENFE CERCANÍAS STATION (MAX 1)
                if (nearestCercaniasStation != null) {
                    if (hasPreviousItem) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                    }
                    hasPreviousItem = true
                    val cercaniasBrand = androidx.compose.ui.res.stringResource(com.example.R.string.header_cercanias_title)
                    NearbyTransitItemRow(
                        itemType = "CERCANIAS",
                        itemId = nearestCercaniasStation.stop_id,
                        iconBox = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF702B7B).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_cercanias),
                                    contentDescription = cercaniasBrand,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        name = nearestCercaniasStation.nombre,
                        isFav = isCercaniasFav,
                        baseSubtitle = if (nearestCercaniasDistance != null) {
                            "${LocationUtils.formatDistance(nearestCercaniasDistance)} · $cercaniasBrand"
                        } else cercaniasBrand,
                        lines = nearestCercaniasStation.lineas,
                        appLanguage = appLanguage,
                        habitualLines = habitualLines,
                        badgeRenderer = { line ->
                            val logoRes = com.example.ui.metro.TransitLogoUtils.getCercaniasLineLogoRes(line)
                            if (logoRes != null) {
                                Image(
                                    painter = painterResource(id = logoRes),
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.line_format_desc, line),
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF702B7B)
                                ) {
                                    Text(
                                        text = line,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        calculateBadgeWidthPx = { line, textMeasurer, density ->
                            val logoRes = com.example.ui.metro.TransitLogoUtils.getCercaniasLineLogoRes(line)
                            if (logoRes != null) {
                                with(density) { 22.dp.toPx() }
                            } else {
                                val tw = textMeasurer.measure(
                                    text = line,
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                ).size.width
                                tw + with(density) { 12.dp.toPx() }
                            }
                        },
                        onClick = { onCercaniasStationClick(nearestCercaniasStation.stop_id) }
                    )
                }

                // 3 & 4. EMT BUS STOPS (MAX 2)
                nearbyBusStops.forEach { (busStop, isBusFav) ->
                    val alias = busStopAliases[busStop.id_parada]
                    val displayName = alias ?: busStop.denominacion
                    val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, busStop.lat, busStop.lon)
                    val lines = busStop.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                    if (hasPreviousItem) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                    }
                    hasPreviousItem = true

                    NearbyTransitItemRow(
                        itemType = "EMT",
                        itemId = busStop.id_parada,
                        iconBox = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE53935).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_emt_valencia),
                                    contentDescription = "EMT València",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        name = displayName,
                        isFav = isBusFav,
                        baseSubtitle = "${LocationUtils.formatDistance(dist)} · EMT Parada #${busStop.id_parada}",
                        lines = lines,
                        appLanguage = appLanguage,
                        habitualLines = habitualLines,
                        badgeRenderer = { line ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE53935)
                            ) {
                                Text(
                                    text = line,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        },
                        calculateBadgeWidthPx = { line, textMeasurer, density ->
                            val tw = textMeasurer.measure(
                                text = line,
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            ).size.width
                            tw + with(density) { 12.dp.toPx() }
                        },
                        onClick = { onBusStopClick(busStop) }
                    )
                }

                // 5 & 6. METROBÚS STOPS (MAX 2)
                nearbyMetrobusStops.forEach { (metrobusStop, isMetrobusFav) ->
                    val alias = metrobusStopAliases[metrobusStop.id_parada]
                    val displayName = alias ?: metrobusStop.denominacion
                    val dist = LocationUtils.calculateDistanceMeters(refLat, refLon, metrobusStop.lat, metrobusStop.lon)
                    val lines = metrobusStop.lineas.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    if (hasPreviousItem) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                    }
                    hasPreviousItem = true

                    NearbyTransitItemRow(
                        itemType = "METROBUS",
                        itemId = metrobusStop.id_parada,
                        iconBox = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MetrobusLineColorResolver.BRAND_COLOR.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_metrobus),
                                    contentDescription = "Metrobús",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        name = displayName,
                        isFav = isMetrobusFav,
                        baseSubtitle = "${LocationUtils.formatDistance(dist)} · Metrobús Parada #${metrobusStop.id_parada}",
                        lines = lines,
                        appLanguage = appLanguage,
                        habitualLines = habitualLines,
                        badgeRenderer = { line ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MetrobusLineColorResolver.BRAND_COLOR
                            ) {
                                Text(
                                    text = line,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        },
                        calculateBadgeWidthPx = { line, textMeasurer, density ->
                            val tw = textMeasurer.measure(
                                text = line,
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            ).size.width
                            tw + with(density) { 12.dp.toPx() }
                        },
                        onClick = { onMetrobusStopClick?.invoke(metrobusStop) }
                    )
                }

                // Empty state if nothing is within 1 km
                if (nearestMetroStation == null && nearestCercaniasStation == null && nearbyBusStops.isEmpty() && nearbyMetrobusStops.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.nearby_no_stops_within_1km),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyTransitItemRow(
    itemType: String,
    itemId: String,
    iconBox: @Composable () -> Unit,
    name: String,
    isFav: Boolean,
    baseSubtitle: String,
    lines: List<String>,
    appLanguage: AppLanguage,
    habitualLines: Set<String>,
    badgeRenderer: @Composable (line: String) -> Unit,
    calculateBadgeWidthPx: (line: String, textMeasurer: androidx.compose.ui.text.TextMeasurer, density: Density) -> Float,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val fixedLeftWidthDp = 36.dp + 10.dp + (if (isFav) 20.dp else 0.dp) + 12.dp
            val fixedLeftWidthPx = with(density) { fixedLeftWidthDp.toPx() }

            val nameTextStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            val nameWidthPx = textMeasurer.measure(text = name, style = nameTextStyle).size.width

            val spacingPx = if (lines.size > 1) with(density) { 4.dp.toPx() } * (lines.size - 1) else 0f
            val badgesWidthPx = if (lines.isNotEmpty()) {
                lines.sumOf { calculateBadgeWidthPx(it, textMeasurer, density).toDouble() }.toFloat() + spacingPx
            } else 0f

            val availableForContentPx = totalWidthPx - fixedLeftWidthPx
            val canFitAllLines = lines.isNotEmpty() && (nameWidthPx + badgesWidthPx <= availableForContentPx)

            val finalSubtitle = if (!canFitAllLines && lines.isNotEmpty()) {
                val linesCountText = if (appLanguage == AppLanguage.CA) {
                    if (lines.size == 1) "1 línia" else "${lines.size} línies"
                } else {
                    if (lines.size == 1) "1 línea" else "${lines.size} líneas"
                }
                if (baseSubtitle.isNotEmpty()) "$baseSubtitle · $linesCountText" else linesCountText
            } else {
                baseSubtitle
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    iconBox()
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isFav) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.favorite_badge_desc),
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = finalSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (canFitAllLines) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        lines.forEach { line ->
                            badgeRenderer(line)
                        }
                    }
                }
            }
        }

        // Live Departures Summary Row At-a-Glance
        Spacer(modifier = Modifier.height(6.dp))
        WidgetLiveDeparturesSection(
            itemType = itemType,
            itemId = itemId,
            habitualLines = habitualLines,
            appLanguage = appLanguage
        )
    }
}

@Composable
private fun WidgetLiveDeparturesSection(
    itemType: String,
    itemId: String,
    habitualLines: Set<String>,
    appLanguage: AppLanguage
) {
    val context = LocalContext.current
    val cacheKey = "widget_${itemType}_$itemId"
    val now = System.currentTimeMillis()

    val cached = widgetRawCache[cacheKey]
    val initialRaw = if (cached != null && (now - cached.timestamp) < 30_000L) {
        cached.rawList
    } else emptyList()

    var rawDepartures by remember(cacheKey) { mutableStateOf(initialRaw) }
    var isLoading by remember(cacheKey) { mutableStateOf(initialRaw.isEmpty()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(cacheKey) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val currentCached = widgetRawCache[cacheKey]
                val currentTime = System.currentTimeMillis()
                if (currentCached != null && (currentTime - currentCached.timestamp) < 30_000L) {
                    rawDepartures = currentCached.rawList
                    isLoading = false
                    delay(10_000L)
                    continue
                }

                try {
                    val rawList = withContext(Dispatchers.IO) {
                        fetchRawDepartures(itemType, itemId, context)
                    }

                    widgetRawCache[cacheKey] = RawWidgetCacheEntry(System.currentTimeMillis(), rawList)
                    rawDepartures = rawList
                } catch (_: Exception) {
                } finally {
                    isLoading = false
                }
                delay(30_000L)
            }
        }
    }

    val departures = remember(rawDepartures, habitualLines, appLanguage) {
        groupWidgetDepartures(itemType, rawDepartures, habitualLines, appLanguage)
    }

    if (isLoading && departures.isEmpty()) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            trackColor = Color.Transparent
        )
    } else if (departures.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 46.dp)
        ) {
            departures.forEach { dep ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Badge / Line label
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(android.graphics.Color.parseColor(dep.colorHex))
                        ) {
                            Text(
                                text = if (itemType == "METRO" && !dep.lineId.startsWith("L", ignoreCase = true)) "L${dep.lineId}" else dep.lineId,
                                fontSize = 10.5.sp,
                                fontWeight = if (dep.isHabitual) FontWeight.ExtraBold else FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = dep.destination,
                            fontSize = 12.sp,
                            fontWeight = if (dep.isHabitual) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = dep.timesFormatted,
                        fontSize = 12.sp,
                        fontWeight = if (dep.isHabitual) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (dep.isHabitual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private suspend fun fetchRawDepartures(
    itemType: String,
    itemId: String,
    context: android.content.Context
): List<RawWidgetDeparture> {
    val results = mutableListOf<RawWidgetDeparture>()
    when (itemType) {
        "METRO" -> {
            val list = com.example.data.repository.RealTimeTransitRepository.getMetroLiveArrivals(itemId)
            list.forEach { arr ->
                val lineCode = arr.line
                val colorHex = LineColorResolver.getMetroLineColorHex(lineCode)
                results.add(
                    RawWidgetDeparture(
                        lineId = lineCode,
                        destination = arr.destination,
                        minutes = arr.minutes,
                        colorHex = colorHex
                    )
                )
            }
        }
        "EMT" -> {
            val list = com.example.data.repository.RealTimeTransitRepository.getEmtLiveArrivals(itemId)
            list.forEach { bus ->
                val mins = bus.minutos.toIntOrNull() ?: if (bus.minutos.contains(":")) 0 else -1
                if (mins >= 0) {
                    results.add(
                        RawWidgetDeparture(
                            lineId = bus.linea,
                            destination = bus.destino,
                            minutes = mins,
                            colorHex = "#E53935"
                        )
                    )
                }
            }
        }
        "METROBUS" -> {
            val db = com.example.data.database.AppDatabase.getDatabase(context)
            val repo = com.example.data.repository.MetrobusRepository(
                database = db,
                client = com.example.data.network.NetworkModule.okHttpClient,
                context = context
            )
            val list = repo.fetchRealTimeEstimations(itemId)
            list.forEach { dep ->
                results.add(
                    RawWidgetDeparture(
                        lineId = dep.lineCode,
                        destination = dep.destination,
                        minutes = dep.minutesRemaining,
                        colorHex = "#F59E0B"
                    )
                )
            }
        }
        "CERCANIAS" -> {
            val db = com.example.data.database.AppDatabase.getDatabase(context)
            val repo = com.example.data.repository.renfe.RenfeRepository(context, db)
            val list = repo.getDeparturesForStation(itemId)
            list.forEach { dep ->
                val mins = dep.minutesRemaining
                val colorHex = LineColorResolver.getCercaniasLineColorHex(dep.routeId)
                results.add(
                    RawWidgetDeparture(
                        lineId = dep.routeId,
                        destination = dep.destination,
                        minutes = mins,
                        colorHex = colorHex
                    )
                )
            }
        }
    }
    return results
}

private fun groupWidgetDepartures(
    itemType: String,
    rawList: List<RawWidgetDeparture>,
    habitualLines: Set<String>,
    appLanguage: AppLanguage
): List<WidgetGroupedDeparture> {
    // Filter out departures exceeding 60 minutes or with invalid negative time
    val validRaw = rawList.filter { it.minutes in 0..60 }
    if (validRaw.isEmpty()) return emptyList()

    val map = LinkedHashMap<Pair<String, String>, MutableList<RawWidgetDeparture>>()
    for (dep in validRaw) {
        val key = Pair(dep.lineId, dep.destination)
        map.getOrPut(key) { mutableListOf() }.add(dep)
    }

    val groupedList = mutableListOf<WidgetGroupedDeparture>()
    for ((key, deps) in map) {
        val (lineId, destination) = key
        val sortedDeps = deps.sortedBy { it.minutes }
        val topTwo = sortedDeps.take(2)

        val timesFormatted = topTwo.mapIndexed { index, dep ->
            val isLast = index == topTwo.lastIndex
            val minStr = if (dep.minutes <= 0) {
                if (appLanguage == AppLanguage.CA) "Ara" else "Ahora"
            } else {
                "${dep.minutes}"
            }
            if (isLast && dep.minutes > 0) "$minStr min" else minStr
        }.joinToString(", ")

        val isHabitual = isHabitualLineSelected(itemType, lineId, habitualLines)

        groupedList.add(
            WidgetGroupedDeparture(
                lineId = lineId,
                destination = destination,
                timesFormatted = timesFormatted,
                colorHex = deps.first().colorHex,
                isHabitual = isHabitual
            )
        )
    }

    // Sort: Habitual lines FIRST, then non-habitual
    val sorted = groupedList.sortedWith(
        compareByDescending<WidgetGroupedDeparture> { it.isHabitual }
    )

    return sorted.take(4)
}
