package com.example.ui.routing.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.routing.DepartureType
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.LiveTimerStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val ITEM_HEIGHT_DP = 48
private const val VISIBLE_ITEMS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScheduleDialog(
    currentDepartureType: DepartureType,
    currentTime: String?,
    appLanguage: AppLanguage = AppLanguage.CA,
    onDismiss: () -> Unit,
    onConfirm: (DepartureType, String?) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf(currentDepartureType) }

    val cal = remember { Calendar.getInstance() }
    val initialHour = remember(currentTime) {
        currentTime?.substringBefore(":")?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY)
    }
    val initialMinute = remember(currentTime) {
        currentTime?.substringAfter(":")?.toIntOrNull() ?: cal.get(Calendar.MINUTE)
    }

    val hourListState = rememberLazyListState(initialFirstVisibleItemIndex = initialHour)
    val minuteListState = rememberLazyListState(initialFirstVisibleItemIndex = initialMinute)

    val selectedHour by remember {
        derivedStateOf {
            val index = hourListState.firstVisibleItemIndex
            val offset = hourListState.firstVisibleItemScrollOffset
            if (offset > 50) (index + 1) % 24 else index % 24
        }
    }

    val selectedMinute by remember {
        derivedStateOf {
            val index = minuteListState.firstVisibleItemIndex
            val offset = minuteListState.firstVisibleItemScrollOffset
            if (offset > 50) (index + 1) % 60 else index % 60
        }
    }

    fun animateToTime(hour: Int, minute: Int) {
        coroutineScope.launch {
            hourListState.animateScrollToItem(hour % 24)
            minuteListState.animateScrollToItem(minute % 60)
        }
    }

    fun addMinutes(deltaMinutes: Int) {
        val tempCal = Calendar.getInstance()
        tempCal.set(Calendar.HOUR_OF_DAY, selectedHour)
        tempCal.set(Calendar.MINUTE, selectedMinute)
        tempCal.add(Calendar.MINUTE, deltaMinutes)
        animateToTime(tempCal.get(Calendar.HOUR_OF_DAY), tempCal.get(Calendar.MINUTE))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .statusBarsPadding()
            .testTag("route_schedule_sheet"),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header limpio sin cajas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ES) "Horario del trayecto" else "Horari del trajecte",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_schedule_dialog_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selector segmentado plano y ligero
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DepartureType.values().forEach { type ->
                    val isSelected = selectedType == type
                    val label = when (type) {
                        DepartureType.LEAVE_NOW -> if (appLanguage == AppLanguage.ES) "Ahora" else "Ara"
                        DepartureType.DEPART_AT -> if (appLanguage == AppLanguage.ES) "Salir a las" else "Eixir a les"
                        DepartureType.ARRIVE_BY -> if (appLanguage == AppLanguage.ES) "Llegar a las" else "Arribar a les"
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { selectedType = type }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedType != DepartureType.LEAVE_NOW) {
                // Selector de Rueda Deslizable sin recuadros toscos
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((ITEM_HEIGHT_DP * VISIBLE_ITEMS).dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Banda indicadora central transparente y sutil
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(ITEM_HEIGHT_DP.dp)
                    ) {}

                    Row(
                        modifier = Modifier.fillMaxWidth(0.75f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Columna Horas (00-23)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((ITEM_HEIGHT_DP * VISIBLE_ITEMS).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WheelNumberPicker(
                                count = 24,
                                state = hourListState,
                                isDark = isDark
                            )
                        }

                        // Separador de dos puntos
                        Text(
                            text = ":",
                            style = LiveTimerStyle.copy(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        // Columna Minutos (00-59) minuto a minuto
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((ITEM_HEIGHT_DP * VISIBLE_ITEMS).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WheelNumberPicker(
                                count = 60,
                                state = minuteListState,
                                isDark = isDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Accesos rápidos minimalistas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickPill(
                        label = "+10m",
                        onClick = { addMinutes(10) },
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    QuickPill(
                        label = "+15m",
                        onClick = { addMinutes(15) },
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    QuickPill(
                        label = "+30m",
                        onClick = { addMinutes(30) },
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    QuickPill(
                        label = "+1h",
                        onClick = { addMinutes(60) },
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Vista despejada para "Salir ahora"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (appLanguage == AppLanguage.ES) "Salida inmediata" else "Eixida immediata",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) {
                                "Muestra las combinaciones directas y más rápidas en este instante."
                            } else {
                                "Mostra les combinacions directes i més ràpides en este instant."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botones de acción principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cancel_schedule_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "Cancelar" else "Cancel·lar",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = {
                        val formattedTime = if (selectedType == DepartureType.LEAVE_NOW) {
                            null
                        } else {
                            String.format(Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute)
                        }
                        onConfirm(selectedType, formattedTime)
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                        .testTag("apply_schedule_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.ES) "Aplicar horario" else "Aplicar horari",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelNumberPicker(
    count: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    isDark: Boolean
) {
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)

    LazyColumn(
        state = state,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = (ITEM_HEIGHT_DP * (VISIBLE_ITEMS - 1) / 2).dp),
        modifier = Modifier.height((ITEM_HEIGHT_DP * VISIBLE_ITEMS).dp)
    ) {
        items(
            count = count,
            key = { it }
        ) { index ->
            val isCurrentCenter = state.firstVisibleItemIndex == index || 
                (state.firstVisibleItemIndex == index - 1 && state.firstVisibleItemScrollOffset > 50)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ITEM_HEIGHT_DP.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.ROOT, "%02d", index),
                    style = LiveTimerStyle.copy(
                        fontSize = if (isCurrentCenter) 26.sp else 20.sp,
                        fontWeight = if (isCurrentCenter) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = when {
                        isCurrentCenter -> MaterialTheme.colorScheme.onBackground
                        isDark -> Color(0xFF6B7280)
                        else -> Color(0xFF94A3B8)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuickPill(
    label: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF1F5F9),
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
