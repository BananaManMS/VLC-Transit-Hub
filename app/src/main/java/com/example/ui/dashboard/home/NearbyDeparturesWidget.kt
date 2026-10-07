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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMetroStationClick(nearestMetroStation.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = nearestMetroStation.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isMetroFav) {
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
                                    text = if (nearestMetroDistance != null) {
                                        "${LocationUtils.formatDistance(nearestMetroDistance)} · Metrovalencia"
                                    } else "Metrovalencia",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val maxMetroLines = 3
                        val visibleMetroLines = nearestMetroStation.lines.take(maxMetroLines)
                        val remainingMetroLines = nearestMetroStation.lines.size - maxMetroLines

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleMetroLines.forEach { line ->
                                MetroLineBadge(lineId = line, size = 22.dp)
                            }
                            if (remainingMetroLines > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCercaniasStationClick(nearestCercaniasStation.stop_id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF702B7B).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val cercaniasBrand = androidx.compose.ui.res.stringResource(com.example.R.string.header_cercanias_title)
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.logo_cercanias),
                                    contentDescription = cercaniasBrand,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = nearestCercaniasStation.nombre,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCercaniasFav) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.favorite_badge_desc),
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                val cercaniasBrand = androidx.compose.ui.res.stringResource(com.example.R.string.header_cercanias_title)
                                Text(
                                    text = if (nearestCercaniasDistance != null) {
                                        "${LocationUtils.formatDistance(nearestCercaniasDistance)} · $cercaniasBrand"
                                    } else cercaniasBrand,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val cercaniasLines = nearestCercaniasStation.lineas
                        if (cercaniasLines.isNotEmpty()) {
                            val maxCercaniasLines = 3
                            val visibleCercaniasLines = cercaniasLines.take(maxCercaniasLines)
                            val remainingCercaniasLines = cercaniasLines.size - maxCercaniasLines
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                visibleCercaniasLines.forEach { line ->
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
                                }
                                if (remainingCercaniasLines > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF702B7B).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "...",
                                            color = Color(0xFF702B7B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF702B7B)
                            )
                        }
                    }
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBusStopClick(busStop) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isBusFav) {
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
                                    text = "${LocationUtils.formatDistance(dist)} · EMT Parada #${busStop.id_parada}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Line Chips for Bus (EMT Red)
                        if (lines.isNotEmpty()) {
                            val maxBusLines = 3
                            val visibleBusLines = lines.take(maxBusLines)
                            val remainingBusLines = lines.size - maxBusLines
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                visibleBusLines.forEach { line ->
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
                                }
                                if (remainingBusLines > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE53935).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "...",
                                            color = Color(0xFFE53935),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMetrobusStopClick?.invoke(metrobusStop) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isMetrobusFav) {
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
                                    text = "${LocationUtils.formatDistance(dist)} · Metrobús Parada #${metrobusStop.id_parada}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Line Chips for Metrobus (Amber #F59E0B)
                        if (lines.isNotEmpty()) {
                            val maxMetrobusLines = 3
                            val visibleMetrobusLines = lines.take(maxMetrobusLines)
                            val remainingMetrobusLines = lines.size - maxMetrobusLines
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                visibleMetrobusLines.forEach { line ->
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
                                }
                                if (remainingMetrobusLines > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = com.example.util.MetrobusLineColorResolver.BRAND_COLOR.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "...",
                                            color = com.example.util.MetrobusLineColorResolver.BRAND_COLOR,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
