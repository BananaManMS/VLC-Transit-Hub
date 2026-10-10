package com.example.ui.dashboard.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.model.MetroStation
import com.example.ui.dashboard.AppLanguage
import com.example.ui.metro.MetroLineBadge
import com.example.util.LocationUtils

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
    refLat: Double,
    refLon: Double,
    onMetroStationClick: (String) -> Unit,
    onCercaniasStationClick: (String) -> Unit,
    onBusStopClick: (GeoportalStopEntity) -> Unit,
    onMetrobusStopClick: ((com.example.data.database.MetrobusStopEntity) -> Unit)? = null,
    onRequestLocationPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    val lines = metrobusStop.lineas?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                    if (hasPreviousItem) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                    }
                    hasPreviousItem = true

                    NearbyTransitItemRow(
                        iconBox = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(com.example.util.MetrobusLineColorResolver.BRAND_COLOR.copy(alpha = 0.12f)),
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
                        badgeRenderer = { line ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = com.example.util.MetrobusLineColorResolver.BRAND_COLOR
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
    iconBox: @Composable () -> Unit,
    name: String,
    isFav: Boolean,
    baseSubtitle: String,
    lines: List<String>,
    appLanguage: AppLanguage,
    badgeRenderer: @Composable (line: String) -> Unit,
    calculateBadgeWidthPx: (line: String, textMeasurer: androidx.compose.ui.text.TextMeasurer, density: Density) -> Float,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        // 36dp logo + 10dp gap + (20dp if fav) + 12dp safety gap between name and badges
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isFav) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.favorite_badge_desc),
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
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
}

