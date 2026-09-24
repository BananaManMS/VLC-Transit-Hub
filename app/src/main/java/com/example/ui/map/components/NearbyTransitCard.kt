package com.example.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.MetrobusRepository
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.dashboard.AppLanguage
import com.example.ui.components.OperatorLogo
import com.example.ui.map.SelectedMapItem
import com.example.util.LineColorResolver
import com.example.util.LocationUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private val departuresCache = ConcurrentHashMap<String, CacheEntry>()

private data class CacheEntry(
    val timestamp: Long,
    val departures: List<GroupedDepartureDisplay>
)

private fun putDeparturesCache(key: String, entry: CacheEntry) {
    if (departuresCache.size > 80) {
        val now = System.currentTimeMillis()
        departuresCache.entries.removeIf { now - it.value.timestamp > 300_000L }
        if (departuresCache.size > 80) {
            val oldest = departuresCache.entries.minByOrNull { it.value.timestamp }
            if (oldest != null) departuresCache.remove(oldest.key)
        }
    }
    departuresCache[key] = entry
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyTransitCard(
    item: NearbyTransitItem,
    isVisible: Boolean = true,
    cameraCenterLat: Double,
    cameraCenterLon: Double,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    busStopAliases: Map<String, String>,
    renfeRepository: RenfeRepository,
    metrobusRepository: MetrobusRepository,
    okHttpClient: OkHttpClient,
    onClick: () -> Unit
) {
    val favoriteBorderColor = if (isDarkMode) Color(0xFFFFD54F).copy(alpha = 0.7f) else Color(0xFFF59E0B).copy(alpha = 0.8f)
    val cardBg = if (item.isFavorite) {
        if (isDarkMode) Color(0xFF28251E) else Color(0xFFFFFDF5)
    } else {
        if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)
    }
    val titleColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Compute direction to stop
    val directionSymbol = remember(item, cameraCenterLat, cameraCenterLon) {
        val (destLat, destLon) = when (item) {
            is NearbyTransitItem.Bus -> Pair(item.stop.lat, item.stop.lon)
            is NearbyTransitItem.Metro -> Pair(item.station.latitude, item.station.longitude)
            is NearbyTransitItem.Cercanias -> Pair(item.station.lat, item.station.lon)
            is NearbyTransitItem.Metrobus -> Pair(item.stop.lat, item.stop.lon)
        }
        getDirectionSymbol(cameraCenterLat, cameraCenterLon, destLat, destLon)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = if (item.isFavorite) BorderStroke(1.5.dp, favoriteBorderColor) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nearby_card_${item.key}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Operator logo instead of generic icon
                val selectedMapItem = remember(item) {
                    when (item) {
                        is NearbyTransitItem.Bus -> SelectedMapItem.BusStop(item.stop, item.emtStopModel)
                        is NearbyTransitItem.Metro -> SelectedMapItem.Metro(item.station)
                        is NearbyTransitItem.Cercanias -> SelectedMapItem.Cercanias(item.station)
                        is NearbyTransitItem.Metrobus -> SelectedMapItem.MetrobusStopItem(item.stop, item.metrobusModel)
                    }
                }
                OperatorLogo(
                    item = selectedMapItem,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Info (Name + sub-badge)
                Column(modifier = Modifier.weight(1f)) {
                    val cleanName = remember(item.displayName) {
                        item.displayName.replace(Regex("\\s*\\([^)]+\\)\\s*$"), "").trim()
                    }
                    val finalTitle = when (item) {
                        is NearbyTransitItem.Bus -> busStopAliases[item.stop.id_parada] ?: cleanName
                        else -> cleanName
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = finalTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorito",
                                tint = if (isDarkMode) Color(0xFFFFD54F) else Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Line Badges Flow Row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (item) {
                            is NearbyTransitItem.Bus -> {
                                val lines = (item.stop.lineas ?: "").split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                
                                if (lines.isEmpty()) {
                                    Text(
                                        text = "Bus",
                                        fontSize = 12.sp,
                                        color = subtextColor
                                    )
                                } else {
                                    lines.take(6).forEach { line ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0),
                                            modifier = Modifier.padding(1.dp)
                                        ) {
                                            Text(
                                                text = line,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (lines.size > 6) {
                                        Text(
                                            text = "+${lines.size - 6}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = subtextColor,
                                            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                            is NearbyTransitItem.Metro -> {
                                item.station.lines.forEach { lineId ->
                                    val colorHex = LineColorResolver.getMetroLineColorHex(lineId)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(android.graphics.Color.parseColor(colorHex))
                                    ) {
                                        Text(
                                            text = lineId,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            is NearbyTransitItem.Cercanias -> {
                                val lines = item.station.lines.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                lines.forEach { lineId ->
                                    val colorHex = LineColorResolver.getCercaniasLineColorHex(lineId)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(android.graphics.Color.parseColor(colorHex))
                                    ) {
                                        Text(
                                            text = lineId,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            is NearbyTransitItem.Metrobus -> {
                                val lines = item.metrobusModel.lineas.filter { it.isNotEmpty() }
                                if (lines.isEmpty()) {
                                    Text(
                                        text = "Metrobús",
                                        fontSize = 12.sp,
                                        color = subtextColor
                                    )
                                } else {
                                    lines.take(6).forEach { line ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0),
                                            modifier = Modifier.padding(1.dp)
                                        ) {
                                            Text(
                                                text = line,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (lines.size > 6) {
                                        Text(
                                            text = "+${lines.size - 6}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = subtextColor,
                                            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Distance + Direction Arrow
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = LocationUtils.formatDistance(item.distanceMeters),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = subtextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = directionSymbol,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isDarkMode) Color(0xFF10B981) else Color(0xFF059669)
                    )
                }
            }

            // Real-Time Departures section
            Spacer(modifier = Modifier.height(8.dp))
            LiveDeparturesRow(
                item = item,
                isVisible = isVisible,
                isDarkMode = isDarkMode,
                appLanguage = appLanguage,
                renfeRepository = renfeRepository,
                metrobusRepository = metrobusRepository,
                okHttpClient = okHttpClient
            )
        }
    }
}

@Composable
fun LiveDeparturesRow(
    item: NearbyTransitItem,
    isVisible: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    renfeRepository: RenfeRepository,
    metrobusRepository: MetrobusRepository,
    okHttpClient: OkHttpClient
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val subtextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Check cache first to avoid flickering or re-fetching when scrolling or recomposing
    val now = System.currentTimeMillis()
    val cached = departuresCache[item.key]
    val initialList = if (cached != null && (now - cached.timestamp) < 30_000L) {
        cached.departures
    } else {
        emptyList()
    }

    var loading by remember(item.key) { mutableStateOf(false) }
    var arrivalsInfo by remember(item.key) { mutableStateOf(initialList) }

    LaunchedEffect(item.key, isVisible) {
        if (!isVisible) return@LaunchedEffect

        var isFirstRun = true
        while (isActive) {
            val currentCached = departuresCache[item.key]
            val currentTime = System.currentTimeMillis()
            val isCacheValid = currentCached != null && (currentTime - currentCached.timestamp) < 30_000L

            if (isFirstRun) {
                isFirstRun = false
                if (isCacheValid) {
                    arrivalsInfo = currentCached!!.departures
                    loading = false
                    val remainingCacheTime = 30_000L - (currentTime - currentCached.timestamp)
                    delay(remainingCacheTime.coerceAtLeast(1000L))
                    continue
                }
                // Debounce to avoid firing network requests during rapid scrolls or quick navigation
                delay(300L)
            }

            if (!isActive) break

            if (arrivalsInfo.isEmpty()) {
                loading = true
            }

            try {
                withContext(Dispatchers.IO) {
                    ensureActive()
                    var listResult = emptyList<GroupedDepartureDisplay>()
                    when (item) {
                        is NearbyTransitItem.Bus -> {
                            val times = com.example.data.repository.RealTimeTransitRepository.getEmtLiveArrivals(
                                stopNumber = item.stop.id_parada,
                                stopName = item.stop.denominacion
                            )
                            ensureActive()
                            val rawList = times.map { bus ->
                                val mLower = bus.minutos.lowercase().trim()
                                val destLower = bus.destino.lowercase().trim()
                                val lineLower = bus.linea.lowercase().trim()

                                val isDiverted = mLower.contains("desvia") || 
                                                 destLower.contains("desvia") || 
                                                 lineLower.contains("desvia") || 
                                                 mLower.contains("suprim") || 
                                                 destLower.contains("suprim") ||
                                                 mLower.contains("desv") ||
                                                 destLower.contains("desv")

                                val isAbsoluteTime = !isDiverted && (
                                    bus.minutos.contains(":") || 
                                    (bus.minutos.length >= 5 && bus.minutos.all { it.isDigit() || it == ':' })
                                )

                                val mins: Int
                                val displayStr: String?

                                if (isDiverted) {
                                    mins = 999
                                    displayStr = if (appLanguage == AppLanguage.CA) "Desviat" else "Desviado"
                                } else if (isAbsoluteTime) {
                                    mins = 998
                                    displayStr = bus.minutos.trim()
                                } else if (mLower == "0" || mLower == "imminent" || mLower == "inminente" || mLower == "ara" || mLower == "ahora" || mLower.startsWith("pr")) {
                                    mins = 0
                                    displayStr = if (appLanguage == AppLanguage.CA) "Ara" else "Ahora"
                                } else {
                                    val extracted = mLower.filter { c -> c.isDigit() }.toIntOrNull()
                                    if (extracted != null) {
                                        mins = extracted
                                        displayStr = null
                                    } else {
                                        mins = 997
                                        displayStr = bus.minutos.trim().ifEmpty { if (appLanguage == AppLanguage.CA) "Sense servei" else "Sin servicio" }
                                    }
                                }

                                val cleanDest = bus.destino.trim().replace(Regex("^>+\\s*"), "")

                                RawDeparture(
                                    lineId = bus.linea.trim(),
                                    destination = cleanDest.ifEmpty { "EMT" },
                                    minutes = mins,
                                    colorHex = "#EF4444",
                                    customDisplay = displayStr,
                                    isDiverted = isDiverted,
                                    isRealTime = bus.isRealTime
                                )
                            }
                            listResult = groupDepartures(rawList, appLanguage)
                        }
                        is NearbyTransitItem.Metro -> {
                            val numericId = item.station.id.toIntOrNull()
                            if (numericId != null) {
                                val arrivals = com.example.data.repository.RealTimeTransitRepository.getMetroLiveArrivals(numericId.toString())
                                ensureActive()
                                val rawList = arrivals.map { arrival ->
                                    val colorHex = LineColorResolver.getMetroLineColorHex(arrival.line)
                                    RawDeparture(
                                        lineId = arrival.line,
                                        destination = arrival.destination.trim(),
                                        minutes = arrival.minutes,
                                        colorHex = colorHex,
                                        isRealTime = arrival.isRealTime
                                    )
                                }
                                listResult = groupDepartures(rawList, appLanguage)
                            }
                        }
                        is NearbyTransitItem.Cercanias -> {
                            val rawDeps = renfeRepository.getDeparturesForStation(item.station.stop_id)
                            ensureActive()
                            val sorted = com.example.data.mapper.CercaniasDepartureMapper.sortDeparturesChronologically(rawDeps)
                            val rawList = sorted.map {
                                val colorHex = LineColorResolver.getCercaniasLineColorHex(it.routeId)
                                val mins = maxOf(0, it.minutesRemaining)
                                RawDeparture(
                                    lineId = it.routeId.trim(),
                                    destination = it.destination.trim(),
                                    minutes = mins,
                                    colorHex = colorHex,
                                    isRealTime = it.isLive && !it.isCanceled
                                )
                            }
                            listResult = groupDepartures(rawList, appLanguage)
                        }
                        is NearbyTransitItem.Metrobus -> {
                            val departures = metrobusRepository.fetchRealTimeEstimations(item.stop.id_parada)
                            ensureActive()
                            val rawList = departures.map { dep ->
                                val mins = maxOf(0, dep.minutesRemaining)
                                RawDeparture(
                                    lineId = dep.lineCode,
                                    destination = dep.destination,
                                    minutes = mins,
                                    colorHex = dep.routeColor ?: "#D97706",
                                    isRealTime = dep.isRealTime
                                )
                            }
                            listResult = groupDepartures(rawList, appLanguage)
                        }
                    }
                    ensureActive()
                    putDeparturesCache(item.key, CacheEntry(System.currentTimeMillis(), listResult))
                    arrivalsInfo = listResult
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            } finally {
                if (isActive) {
                    loading = false
                }
            }

        delay(30_000L)
    }
}

    if (loading) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = if (isDarkMode) Color(0xFF10B981) else Color(0xFF2563EB)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (appLanguage == AppLanguage.CA) "Consultant sortides..." else "Consultando salidas...",
                fontSize = 13.sp,
                color = subtextColor
            )
        }
    } else {
        if (arrivalsInfo.isEmpty()) {
            Text(
                text = if (appLanguage == AppLanguage.CA) "No hi ha sortides properes" else "No hay salidas programadas",
                fontSize = 13.sp,
                color = subtextColor,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                arrivalsInfo.forEach { dep ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(android.graphics.Color.parseColor(dep.colorHex)),
                            modifier = Modifier.size(28.dp, 18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = dep.lineId,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dep.destination,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF334155),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            dep.times.forEachIndexed { index, timeStr ->
                                if (index > 0) {
                                    Text(
                                        text = ", ",
                                        fontSize = 13.sp,
                                        color = subtextColor
                                    )
                                }
                                val isNow = timeStr == "Ara" || timeStr == "Ahora"
                                val isDivertedText = timeStr.equals("Desviado", ignoreCase = true) || 
                                                     timeStr.equals("Desviat", ignoreCase = true) || 
                                                     timeStr.contains("desvia", ignoreCase = true) ||
                                                     timeStr.contains("desv", ignoreCase = true)
                                val isFirst = index == 0
                                val textColor = when {
                                    isDivertedText || dep.isDiverted -> Color(0xFFE53935)
                                    isNow -> Color(0xFF10B981)
                                    isFirst -> if (isDarkMode) Color.White else Color(0xFF0F172A)
                                    else -> subtextColor
                                }
                                Text(
                                    text = timeStr,
                                    fontSize = 13.sp,
                                    fontWeight = if (isFirst || isDivertedText || dep.isDiverted) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                            if (dep.isRealTime) {
                                Spacer(modifier = Modifier.width(4.dp))
                                com.example.ui.components.LiveRssFeedIcon(
                                    contentDescription = "En Vivo",
                                    tint = Color(0xFF2ECC71),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class RawDeparture(
    val lineId: String,
    val destination: String,
    val minutes: Int,
    val colorHex: String,
    val customDisplay: String? = null,
    val isDiverted: Boolean = false,
    val isRealTime: Boolean = true
)

data class GroupedDepartureDisplay(
    val lineId: String,
    val destination: String,
    val times: List<String>,
    val colorHex: String,
    val isDiverted: Boolean = false,
    val isRealTime: Boolean = true
)

fun groupDepartures(
    rawList: List<RawDeparture>,
    appLanguage: AppLanguage
): List<GroupedDepartureDisplay> {
    if (rawList.isEmpty()) return emptyList()

    val map = LinkedHashMap<Pair<String, String>, MutableList<RawDeparture>>()
    for (dep in rawList) {
        val key = Pair(dep.lineId, dep.destination)
        map.getOrPut(key) { mutableListOf() }.add(dep)
    }

    val result = mutableListOf<GroupedDepartureDisplay>()
    for ((key, deps) in map) {
        val (lineId, destination) = key
        val sortedDeps = deps.sortedBy { it.minutes }
        val topTwo = sortedDeps.take(2)
        val hasDiverted = topTwo.any { it.isDiverted }
        val isRealTime = topTwo.any { it.isRealTime }

        val timesFormatted = topTwo.mapIndexed { index, dep ->
            val isLast = index == topTwo.lastIndex
            if (dep.customDisplay != null) {
                dep.customDisplay
            } else if (dep.minutes <= 0) {
                if (appLanguage == AppLanguage.CA) "Ara" else "Ahora"
            } else {
                if (isLast) {
                    "${dep.minutes} min"
                } else {
                    val nextDep = topTwo.getOrNull(index + 1)
                    if (nextDep != null && nextDep.customDisplay == null && nextDep.minutes > 0) {
                        "${dep.minutes}"
                    } else {
                        "${dep.minutes} min"
                    }
                }
            }
        }
        result.add(
            GroupedDepartureDisplay(
                lineId = lineId,
                destination = destination,
                times = timesFormatted,
                colorHex = deps.first().colorHex,
                isDiverted = hasDiverted,
                isRealTime = isRealTime
            )
        )
    }

    return result.take(4)
}

fun getDirectionSymbol(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
    val dy = toLat - fromLat
    val dx = toLon - fromLon
    val angle = Math.toDegrees(Math.atan2(dy, dx))
    val normalizedAngle = if (angle < 0) angle + 360 else angle
    return when {
        normalizedAngle >= 337.5 || normalizedAngle < 22.5 -> "→ E"
        normalizedAngle >= 22.5 && normalizedAngle < 67.5 -> "↗ NE"
        normalizedAngle >= 67.5 && normalizedAngle < 112.5 -> "↑ N"
        normalizedAngle >= 112.5 && normalizedAngle < 157.5 -> "↖ NW"
        normalizedAngle >= 157.5 && normalizedAngle < 202.5 -> "← O"
        normalizedAngle >= 202.5 && normalizedAngle < 247.5 -> "↙ SO"
        normalizedAngle >= 247.5 && normalizedAngle < 292.5 -> "↓ S"
        normalizedAngle >= 292.5 && normalizedAngle < 337.5 -> "↘ SE"
        else -> "↑"
    }
}
