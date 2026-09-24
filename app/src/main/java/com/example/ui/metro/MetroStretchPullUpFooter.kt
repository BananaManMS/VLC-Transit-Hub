package com.example.ui.metro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.data.model.MetroScheduledDeparture
import com.example.ui.dashboard.AppLanguage
import com.example.util.LineColorResolver
import kotlin.math.min

/**
 * Extension for LazyListScope to virtualize the scheduled departures section.
 * This ensures that when scheduled departures are shown, each item is independently recycled and
 * composed only when it scrolls into view, completely eliminating UI stutter and frame drops.
 */
fun LazyListScope.metroScheduledDeparturesLazySection(
    scheduledDepartures: List<MetroScheduledDeparture>,
    isLoading: Boolean,
    isLoaded: Boolean,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onTriggerLoad: () -> Unit,
    onReset: () -> Unit,
    stretchState: MetroPullUpStretchState? = null,
    availableLines: List<String> = emptyList(),
    selectedLineFilter: String? = null,
    onLineFilterSelected: ((String?) -> Unit)? = null,
    onDepartureClick: ((MetroScheduledDeparture) -> Unit)? = null
) {
    if (!isLoaded && !isLoading) {
        item(key = "stretch_pullup_prompt_item") {
            MetroStretchPullPromptCard(
                isLoading = false,
                stretchState = stretchState,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onTriggerLoad = onTriggerLoad
            )
        }
    } else if (isLoading) {
        item(key = "stretch_pullup_loading_item") {
            MetroStretchPullPromptCard(
                isLoading = true,
                stretchState = stretchState,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onTriggerLoad = onTriggerLoad
            )
        }
    } else {
        item(key = "scheduled_departures_header_item") {
            MetroScheduledHeaderAndFilters(
                scheduledDeparturesCount = scheduledDepartures.size,
                availableLines = availableLines,
                selectedLineFilter = selectedLineFilter,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onReset = onReset,
                onLineFilterSelected = onLineFilterSelected
            )
        }

        if (scheduledDepartures.isEmpty()) {
            item(key = "scheduled_departures_empty_item") {
                MetroScheduledEmptyCard(
                    appLanguage = appLanguage,
                    isDarkMode = isDarkMode
                )
            }
        } else {
            items(
                items = scheduledDepartures,
                key = { "sched_${it.trainServiceId}_${it.timeMinutes}_${it.destinationWebId}_${it.line}" }
            ) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
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

@Composable
fun MetroStretchPullPromptCard(
    isLoading: Boolean,
    stretchState: MetroPullUpStretchState?,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onTriggerLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val stretchOffsetVal = stretchState?.stretchOffsetPx ?: 0f
    val thresholdPx = stretchState?.thresholdPx ?: with(density) { 68.dp.toPx() }
    val isThresholdReached = stretchState?.isThresholdReached ?: (-stretchOffsetVal >= thresholdPx)

    val visualStretchDp = with(density) {
        ((-stretchOffsetVal).coerceAtLeast(0f) * 0.35f).coerceAtMost(50f).toDp()
    }

    if (isLoading) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDarkMode) Color(0xFF1E212A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("stretch_pullup_loading")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (appLanguage == AppLanguage.CA) "Carregant següents trens programats..."
                    else "Cargando siguientes trenes programados...",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Surface(
            onClick = onTriggerLoad,
            shape = RoundedCornerShape(16.dp),
            color = if (isThresholdReached) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkMode) 0.55f else 0.85f)
            } else {
                if (isDarkMode) Color(0xFF1E212A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationY = -visualStretchDp.toPx() * 0.5f
                }
                .testTag("stretch_pullup_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isThresholdReached) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isThresholdReached) Icons.Default.CheckCircle else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isThresholdReached) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                rotationZ = if (isThresholdReached) 0f else min(180f, ((-stretchOffsetVal) / thresholdPx) * 180f)
                            }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val promptText = if (isThresholdReached) {
                    if (appLanguage == AppLanguage.CA) "Deixa anar per mostrar trens programats"
                    else "Suelta para mostrar trenes programados"
                } else {
                    if (appLanguage == AppLanguage.CA) "Estira cap amunt per veure trens programats"
                    else "Estira hacia arriba para ver trenes programados"
                }

                Text(
                    text = promptText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isThresholdReached) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MetroScheduledHeaderAndFilters(
    scheduledDeparturesCount: Int,
    availableLines: List<String>,
    selectedLineFilter: String?,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onReset: () -> Unit,
    onLineFilterSelected: ((String?) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("inline_theoretical_loaded_list")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (appLanguage == AppLanguage.CA)
                        "Següents trens programats ($scheduledDeparturesCount)"
                    else
                        "Siguientes trenes programados ($scheduledDeparturesCount)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onReset,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = "Plegar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Line filter selector buttons (only shown when station has 2 or more lines)
        if (availableLines.size > 1) {
            val horizontalScrollBoundaryLock = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        return Offset(x = available.x, y = 0f)
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(horizontalScrollBoundaryLock)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    val isAllSelected = selectedLineFilter == null || selectedLineFilter.isBlank() || selectedLineFilter.equals("ALL", ignoreCase = true)
                    Surface(
                        onClick = { onLineFilterSelected?.invoke(null) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isAllSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkMode) 0.6f else 0.9f)
                        } else {
                            if (isDarkMode) Color(0xFF1E212A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (isAllSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("filter_chip_all_lines")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Totes (Pròx. 3h)" else "Todas (Próx. 3h)",
                                fontSize = 12.5.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                items(availableLines) { line ->
                    val isSelected = selectedLineFilter?.replace("L", "", ignoreCase = true)?.trim()
                        .equals(line.replace("L", "", ignoreCase = true).trim(), ignoreCase = true)
                    val lineHex = LineColorResolver.getMetroLineColorHex("L$line")
                    val lineColor = remember(lineHex) {
                        try {
                            Color(lineHex.toColorInt())
                        } catch (e: Exception) {
                            Color(0xFF64748B)
                        }
                    }

                    Surface(
                        onClick = {
                            if (isSelected) {
                                onLineFilterSelected?.invoke(null)
                            } else {
                                onLineFilterSelected?.invoke(line)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            lineColor.copy(alpha = if (isDarkMode) 0.28f else 0.15f)
                        } else {
                            if (isDarkMode) Color(0xFF1E212A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, lineColor)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("filter_chip_line_$line")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetroLineBadge(
                                lineId = line,
                                fallbackColorHex = lineHex,
                                size = 24.dp
                            )

                            Text(
                                text = "L$line",
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    if (isDarkMode) Color.White else lineColor
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            val scopeCaption = if (selectedLineFilter == null || selectedLineFilter.isBlank() || selectedLineFilter.equals("ALL", ignoreCase = true)) {
                if (appLanguage == AppLanguage.CA) "Mostrant eixides de les pròximes 3 hores" else "Mostrando salidas de las próximas 3 horas"
            } else {
                val cleanLine = selectedLineFilter.replace("L", "", ignoreCase = true).trim()
                if (appLanguage == AppLanguage.CA) "Mostrant tots els trens programats de la L$cleanLine de hui"
                else "Mostrando todos los trenes programados de la L$cleanLine de hoy"
            }

            Text(
                text = scopeCaption,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        } else if (availableLines.size == 1) {
            val singleLine = availableLines.first()
            val singleLineCaption = if (appLanguage == AppLanguage.CA) {
                "Mostrant tots els trens programats de la L$singleLine de hui"
            } else {
                "Mostrando todos los trenes programados de la L$singleLine de hoy"
            }
            Text(
                text = singleLineCaption,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun MetroScheduledEmptyCard(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDarkMode) Color(0xFF1E212A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = if (appLanguage == AppLanguage.CA)
                "No hi ha més trens programats per a hui que no estiguen ja en circulació."
            else
                "No hay más trenes programados para hoy que no estén ya en circulación.",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Convenience wrapper for non-lazy standalone layouts
 */
@Composable
fun MetroStretchPullUpFooter(
    scheduledDepartures: List<MetroScheduledDeparture>,
    isLoading: Boolean,
    isLoaded: Boolean,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onTriggerLoad: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    stretchState: MetroPullUpStretchState? = null,
    availableLines: List<String> = emptyList(),
    selectedLineFilter: String? = null,
    onLineFilterSelected: ((String?) -> Unit)? = null,
    onDepartureClick: ((MetroScheduledDeparture) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("metro_stretch_pullup_container"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLoaded && !isLoading) {
            MetroStretchPullPromptCard(
                isLoading = false,
                stretchState = stretchState,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onTriggerLoad = onTriggerLoad
            )
        } else if (isLoading) {
            MetroStretchPullPromptCard(
                isLoading = true,
                stretchState = stretchState,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onTriggerLoad = onTriggerLoad
            )
        } else {
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    MetroScheduledHeaderAndFilters(
                        scheduledDeparturesCount = scheduledDepartures.size,
                        availableLines = availableLines,
                        selectedLineFilter = selectedLineFilter,
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode,
                        onReset = onReset,
                        onLineFilterSelected = onLineFilterSelected
                    )

                    if (scheduledDepartures.isEmpty()) {
                        MetroScheduledEmptyCard(
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            scheduledDepartures.forEach { item ->
                                androidx.compose.runtime.key(item.trainServiceId, item.timeMinutes, item.destinationWebId, item.line) {
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
    }
}
