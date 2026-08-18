package com.example.ui.map.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.Translation
import com.example.ui.map.SelectedMapItem
import com.example.ui.metro.MetroMapper
import com.example.ui.metro.MetroViewModel
import com.example.ui.metro.RealTimeDeparture
import kotlinx.coroutines.delay

@Composable
fun OperatorLogo(
    item: SelectedMapItem,
    modifier: Modifier = Modifier.size(44.dp)
) {
    if (item is SelectedMapItem.Address) {
        val isFav = item.result.category == "favorite" || item.result.type == "favorite"
        val bgColor = if (isFav) Color(0xFFF59E0B) else Color(0xFF10B981)
        val icon = if (isFav) Icons.Default.Star else Icons.Default.LocationOn
        val desc = if (isFav) "Favorito" else "Destino"
        
        Box(
            modifier = modifier
                .background(bgColor, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        return
    }

    val (resId, contentDesc) = when (item) {
        is SelectedMapItem.Metro -> com.example.R.drawable.logo_metrovalencia to "Metrovalencia"
        is SelectedMapItem.Cercanias -> com.example.R.drawable.logo_cercanias to "Cercanías"
        is SelectedMapItem.BusStop -> com.example.R.drawable.logo_emt_valencia to "EMT València"
        is SelectedMapItem.MetrobusStopItem -> com.example.R.drawable.logo_metrobus to "Metrobús"
        is SelectedMapItem.Valenbisi -> com.example.R.drawable.ic_bike to "Valenbisi"
        else -> 0 to ""
    }

    val context = LocalContext.current
    val painter = remember(resId) {
        try {
            val drawable = ResourcesCompat.getDrawable(context.resources, resId, context.theme)
            if (drawable != null) {
                if (drawable is BitmapDrawable) {
                    BitmapPainter(drawable.bitmap.asImageBitmap())
                } else {
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    BitmapPainter(bitmap.asImageBitmap())
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (painter != null) {
            val isDark = isSystemInDarkTheme()
            Image(
                painter = painter,
                contentDescription = contentDesc,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                colorFilter = if (item is SelectedMapItem.Valenbisi) {
                    androidx.compose.ui.graphics.ColorFilter.tint(
                        if (isDark) Color.White else Color.Black
                    )
                } else null
            )
        } else {
            val icon = when (item) {
                is SelectedMapItem.Metro -> Icons.Default.Subway
                is SelectedMapItem.Cercanias -> Icons.Default.Train
                is SelectedMapItem.BusStop -> Icons.Default.DirectionsBus
                is SelectedMapItem.MetrobusStopItem -> Icons.Default.DirectionsBus
                is SelectedMapItem.Valenbisi -> Icons.Default.DirectionsBike
                is SelectedMapItem.Address -> Icons.Default.LocationOn
            }
            val tintColor = when (item) {
                is SelectedMapItem.Metro -> Color(0xFFE2001A)
                is SelectedMapItem.Cercanias -> Color(0xFFE2001A)
                is SelectedMapItem.BusStop -> Color(0xFF2563EB)
                is SelectedMapItem.MetrobusStopItem -> Color(0xFFFFB300)
                is SelectedMapItem.Valenbisi -> Color(0xFF10B981)
                is SelectedMapItem.Address -> Color(0xFFE53935)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tintColor.copy(alpha = 0.12f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = tintColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisambiguationMenuSheet(
    items: List<SelectedMapItem>,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    busStopAliases: Map<String, String>,
    onSelectItem: (SelectedMapItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDarkMode) Color(0xFF1E293B) else Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (appLanguage == AppLanguage.CA) "Parades properes" else "Paradas cercanas",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = if (appLanguage == AppLanguage.CA) "Selecciona la parada que vols veure:" else "Selecciona la parada que deseas ver:",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items) { item ->
                    val title: String
                    val subtitle: String
                    val badge: String

                    when (item) {
                        is SelectedMapItem.BusStop -> {
                            val alias = busStopAliases[item.stop.id_parada]
                            title = if (!alias.isNullOrBlank()) alias else item.stop.denominacion
                            val lines = item.stop.lineas ?: ""
                            subtitle = if (appLanguage == AppLanguage.CA) "EMT Autobús • Línies: $lines" else "EMT Autobús • Líneas: $lines"
                            badge = item.stop.id_parada
                        }
                        is SelectedMapItem.MetrobusStopItem -> {
                            val alias = busStopAliases[item.stop.id_parada]
                            title = if (!alias.isNullOrBlank()) alias else item.stop.denominacion
                            val lines = item.stop.lineas ?: ""
                            subtitle = if (appLanguage == AppLanguage.CA) "Metrobús • Línies: $lines" else "Metrobús • Líneas: $lines"
                            badge = "MB"
                        }
                        is SelectedMapItem.Metro -> {
                            title = item.station.name
                            val lines = item.station.lines.joinToString(", ")
                            subtitle = if (appLanguage == AppLanguage.CA) "Metrovalencia • Línies: $lines" else "Metrovalencia • Líneas: $lines"
                            badge = "Metro"
                        }
                        is SelectedMapItem.Cercanias -> {
                            title = item.station.displayName
                            val lines = item.station.lines
                            subtitle = if (appLanguage == AppLanguage.CA) "Rodalia Renfe • Línies: $lines" else "Rodalia Renfe • Líneas: $lines"
                            badge = "Rodalia"
                        }
                        is SelectedMapItem.Valenbisi -> {
                            title = item.station.name
                            subtitle = if (appLanguage == AppLanguage.CA) "Estació de Valenbisi • Bicis: ${item.station.available}" else "Estación de Valenbisi • Bicis: ${item.station.available}"
                            badge = "Bici"
                        }
                        is SelectedMapItem.Address -> {
                            val isFav = item.result.category == "favorite" || item.result.type == "favorite"
                            title = item.result.displayName.split(",").firstOrNull()?.trim() ?: item.result.displayName
                            subtitle = item.result.displayName
                            badge = if (isFav) {
                                if (appLanguage == AppLanguage.CA) "Preferit" else "Favorito"
                            } else {
                                if (appLanguage == AppLanguage.CA) "Destí" else "Destino"
                            }
                        }
                    }

                    Card(
                        onClick = {
                            onSelectItem(item)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disambiguation_item_${title}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFF8FAFC)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isDarkMode) Color(0xFF475569) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OperatorLogo(item = item, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEDF2F7),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF4A5568)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MapMetroDepartureListItem(
    departure: RealTimeDeparture,
    appLanguage: AppLanguage,
    texts: Translation,
    isDarkMode: Boolean,
    metroViewModel: MetroViewModel? = null
) {
    var secondsRemaining by remember(departure.id, departure.secondsRemaining) { mutableIntStateOf(departure.secondsRemaining) }
    LaunchedEffect(departure.id, departure.secondsRemaining) {
        secondsRemaining = departure.secondsRemaining
        while (secondsRemaining > -10) {
            delay(1000)
            secondsRemaining--
        }
    }

    val uiModel = remember(departure, secondsRemaining, appLanguage, texts, isDarkMode) {
        MetroMapper.toDepartureUiModel(
            departure,
            secondsRemaining,
            appLanguage,
            texts,
            isDarkMode
        ) { digit -> metroViewModel?.getSharedLineDigits(digit) ?: emptyList() }
    }

    val cardTextColor = if (isDarkMode) Color.White else Color.Black
    val timeColor = when {
        uiModel.isWarningColor -> MaterialTheme.colorScheme.error
        uiModel.isSecondaryColor -> MaterialTheme.colorScheme.secondary
        else -> if (isDarkMode) Color.White else Color.Black
    }

    val infiniteTransition = rememberInfiniteTransition(label = "map_timer_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "map_alpha_anim"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = try { Color(android.graphics.Color.parseColor(departure.colorHex)) } catch (e: Exception) { Color(0xFF1E88E5) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = departure.lineId,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = departure.destination,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = cardTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (metroViewModel != null) {
                        val lineIncidents = metroViewModel.getIncidentsForLine(departure.lineId)
                        val directKeys = lineIncidents.flatMap { incident ->
                            listOfNotNull(
                                incident.id.trim().ifEmpty { null },
                                incident.descriptionEs.trim().ifEmpty { null }
                            )
                        }.toSet()

                        val affectedSharedLines = uiModel.sharedDigits.filter { sharedDigit ->
                            val sharedIncidents = metroViewModel.getIncidentsForLine("L$sharedDigit")
                            sharedIncidents.any { incident ->
                                val idKey = incident.id.trim()
                                val descKey = incident.descriptionEs.trim()
                                val matchesDirect = (idKey.isNotEmpty() && directKeys.contains(idKey)) ||
                                        (descKey.isNotEmpty() && directKeys.contains(descKey))
                                !matchesDirect
                            }
                        }.map { "L$it" }

                        if (lineIncidents.isNotEmpty()) {
                            val text = if (lineIncidents.size == 1) {
                                if (appLanguage == AppLanguage.CA) "1 avís" else "1 aviso"
                            } else {
                                if (appLanguage == AppLanguage.CA) "${lineIncidents.size} avisos" else "${lineIncidents.size} avisos"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = text,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else if (affectedSharedLines.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val sharedLinesStr = affectedSharedLines.joinToString(", ")
                                val text = if (appLanguage == AppLanguage.CA) "Possible afectació ($sharedLinesStr)" else "Posible afectación ($sharedLinesStr)"
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = text,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Sense avisos" else "Sin avisos",
                                fontSize = 11.sp,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    } else {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Sense avisos" else "Sin avisos",
                            fontSize = 11.sp,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = uiModel.timeAnnotated,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = timeColor,
                    textAlign = TextAlign.End,
                    modifier = if (uiModel.shouldBlink) Modifier.graphicsLayer(alpha = alpha) else Modifier
                )
                Icon(
                    imageVector = Icons.Default.RssFeed,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
