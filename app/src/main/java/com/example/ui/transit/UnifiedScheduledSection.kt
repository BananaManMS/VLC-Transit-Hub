package com.example.ui.transit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage

@Composable
fun UnifiedScheduledSection(
    isLoaded: Boolean,
    isLoading: Boolean,
    scheduledDepartures: List<UnifiedTransitDeparture>,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onLoadScheduled: () -> Unit,
    scrollState: ScrollState? = null,
    onLoadMoreScheduled: (() -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    isOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    val titleColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF64748B)
    val accentColor = if (isDarkMode) Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary
    val dividerColor = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)

    var visibleCount by remember(scheduledDepartures, isLoaded) { mutableIntStateOf(15) }

    // Seamless infinite scrolling in-memory pagination: reveal next batch when scrolling near bottom
    LaunchedEffect(scrollState?.value, scrollState?.maxValue, scheduledDepartures.size) {
        if (scrollState != null && scrollState.maxValue > 0) {
            if (scrollState.value >= (scrollState.maxValue - 400)) {
                if (visibleCount < scheduledDepartures.size) {
                    visibleCount = (visibleCount + 15).coerceAtMost(scheduledDepartures.size)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = dividerColor,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        if (!isLoaded && !isLoading) {
            // Initial state: Show action button to fetch/view scheduled departures
            OutlinedButton(
                onClick = onLoadScheduled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unified_load_scheduled_btn"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF3B82F6).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (appLanguage == AppLanguage.CA) "Veure eixides programades" else "Ver salidas programadas",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        } else {
            // Scheduled Header with optional Collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = subtextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Eixides programades" else "Salidas programadas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    if (isLoaded && scheduledDepartures.isNotEmpty()) {
                        Text(
                            text = "(${scheduledDepartures.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextColor
                        )
                    }
                }

                if (onCollapse != null) {
                    TextButton(
                        onClick = onCollapse,
                        modifier = Modifier.testTag("unified_collapse_scheduled_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = subtextColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Amagar" else "Ocultar",
                            style = MaterialTheme.typography.labelSmall,
                            color = subtextColor
                        )
                    }
                }
            }

            if (isLoading) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    repeat(2) {
                        SkeletonCardItem()
                    }
                }
            } else if (scheduledDepartures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isOnline) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isDarkMode) Color(0xFFEF5350) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA)
                                    "No es poden carregar els horaris programats sense connexió."
                                    else "No se pueden cargar los horarios programados sin conexión.",
                                style = MaterialTheme.typography.bodySmall,
                                color = subtextColor,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = onLoadScheduled,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Reintentar" else "Reintentar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "No hi ha més eixides programades per a hui." else "No hay más salidas programadas para hoy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtextColor
                        )
                    }
                }
            } else {
                val displayedDepartures = scheduledDepartures.take(visibleCount)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    displayedDepartures.forEach { departure ->
                        UnifiedTransitDepartureCard(
                            departure = departure,
                            isDarkMode = isDarkMode,
                            appLanguage = appLanguage
                        )
                    }
                }
            }
        }
    }
}
