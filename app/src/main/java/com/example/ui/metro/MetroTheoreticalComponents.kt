package com.example.ui.metro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetroScheduledDeparture
import com.example.ui.dashboard.AppLanguage

/**
 * High-craftsmanship scheduled departure card using the official Metrovalencia line logo
 * and styling consistent with live departures (MetroLineDepartureCard).
 */
@Composable
fun MetroScheduledDepartureCard(
    item: MetroScheduledDeparture,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = AppLanguage.ES,
    isDarkMode: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    onClick: (() -> Unit)? = null
) {
    val badgeColor = remember(item.line) {
        TransitLogoUtils.getMetroLineColor(item.line)
    }

    val containerBgColor = if (isDarkMode) {
        badgeColor.copy(alpha = 0.16f).compositeOver(Color(0xFF23252E))
    } else {
        badgeColor.copy(alpha = 0.08f)
    }

    val cardTextColor = MaterialTheme.colorScheme.onSurface
    val timeTextColor = if (isDarkMode) Color.White else Color(0xFF1E293B)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scheduled_dep_${item.timeMinutes}_${item.line}")
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerBgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Official Line Logo (SVG/vector)
            MetroLineBadge(
                lineId = item.line,
                size = 36.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Destination Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.destinationName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                    color = cardTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Time & Scheduled Tag
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.timeFormatted,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
                    color = timeTextColor,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Programat" else "Programado",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Backward-compatible alias for existing calls in dialogs and sheets.
 */
@Composable
fun ScheduledDepartureRow(
    item: MetroScheduledDeparture,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = AppLanguage.ES,
    isDarkMode: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    onClick: (() -> Unit)? = null
) {
    MetroScheduledDepartureCard(
        item = item,
        modifier = modifier,
        appLanguage = appLanguage,
        isDarkMode = isDarkMode,
        onClick = onClick
    )
}

/**
 * Section displayed at the bottom of live departures:
 * Activated by pull-up gesture or tapping prompt, showing subsequent theoretical trains
 * formatted cleanly with official Metrovalencia logos.
 */
@Composable
fun MetroInlineTheoreticalSection(
    inlineTheoreticalDepartures: List<MetroScheduledDeparture>,
    isLoading: Boolean,
    isLoaded: Boolean,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onTriggerLoad: () -> Unit,
    modifier: Modifier = Modifier,
    onDepartureClick: ((MetroScheduledDeparture) -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkMode) Color(0xFF1E212A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("inline_theoretical_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (!isLoaded && !isLoading) {
                // Pull-up prompt / banner
                Surface(
                    onClick = onTriggerLoad,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA)
                                "Llisca cap amunt per veure següents trens (Programat)"
                            else
                                "Desliza hacia arriba para ver siguientes trenes (Programado)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Carregant següents trens..." else "Cargando siguientes trenes...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Loaded state: Header + list of upcoming trains formatted as MetroScheduledDepartureCard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Següents trens (Programat)" else "Siguientes trenes (Programado)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (inlineTheoreticalDepartures.isEmpty()) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA)
                            "No hi ha més trens programats per a hui."
                        else
                            "No hay más trenes programados para hoy.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        inlineTheoreticalDepartures.forEach { item ->
                            MetroScheduledDepartureCard(
                                item = item,
                                appLanguage = appLanguage,
                                isDarkMode = isDarkMode,
                                onClick = { onDepartureClick?.invoke(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
