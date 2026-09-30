package com.example.ui.cercanias

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.map.components.DetailSheetState
import com.example.ui.metro.CercaniasLineBadge
import com.example.ui.metro.TransitLogoUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrainBottomSheet(
    vehicle: LiveVehicleInfo,
    stationNameMap: Map<String, String>,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxExpandedHeight: Dp = 640.dp,
    sheetState: DetailSheetState = DetailSheetState.HALF_EXPANDED,
    onSheetStateChanged: (DetailSheetState) -> Unit = {}
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val sheetBg = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA)
    val sheetTextColor = if (isDarkMode) Color(0xFFF2F4F8) else MaterialTheme.colorScheme.onSurface
    val sheetSubtextColor = if (isDarkMode) Color(0xFF8791A6) else MaterialTheme.colorScheme.onSurfaceVariant

    // Normalize Line Info
    val cleanNum = CercaniasRouteUtils.getCanonicalLineNumber(vehicle.routeId, vehicle.tripId)
    val lineCode = "C$cleanNum"
    val lineColor = getCercaniasLineColor(lineCode)

    // Fallback terminals based on line
    val defaultTerminalPair = remember(lineCode) {
        when (lineCode) {
            "C1" -> Pair("València Nord", "Gandia")
            "C2" -> Pair("València Nord", "Moixent")
            "C3" -> Pair("València Nord", "Utiel")
            "C5" -> Pair("Sagunt", "Caudiel")
            "C6" -> Pair("València Nord", "Castelló de la Plana")
            else -> Pair("València Nord", "Destino")
        }
    }

    // Resolve robust Origin and Destination names (avoiding literal placeholder "Origen - Destino")
    val resolvedOrigin = remember(vehicle, stationNameMap) {
        val raw = vehicle.originName.trim()
        if (raw.isNotBlank() && !raw.equals("Origen", ignoreCase = true) && !raw.equals("Origin", ignoreCase = true)) {
            stationNameMap[raw] ?: raw
        } else {
            val schedPair = CercaniasRouteUtils.getTripOriginAndDestination(vehicle.tripId, lineCode, vehicle.trainNum)
            schedPair?.first ?: defaultTerminalPair.first
        }
    }

    val resolvedDest = remember(vehicle, stationNameMap) {
        val raw = vehicle.destinationName.trim()
        if (raw.isNotBlank() && !raw.equals("Destino", ignoreCase = true) && !raw.equals("Destination", ignoreCase = true)) {
            stationNameMap[raw] ?: raw
        } else {
            val schedPair = CercaniasRouteUtils.getTripOriginAndDestination(vehicle.tripId, lineCode, vehicle.trainNum)
            schedPair?.second ?: defaultTerminalPair.second
        }
    }

    val delayMin = vehicle.delayMinutes
    val (statusText, statusBgColor, statusTextColor) = when {
        delayMin <= 1 -> Triple("En hora", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        delayMin <= 4 -> Triple("+$delayMin min de retraso", Color(0xFFFFF3E0), Color(0xFFE65100))
        else -> Triple("+$delayMin min de retraso", Color(0xFFFFEBEE), Color(0xFFC62828))
    }

    // Draggable bottom sheet height logic
    var measuredHeaderPx by remember { mutableFloatStateOf(0f) }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val defaultCollapsedPx = with(density) { (118.dp + navBarBottom).toPx() }
    val collapsedPx = if (measuredHeaderPx > 0f) {
        measuredHeaderPx + with(density) { (20.dp + navBarBottom).toPx() }
    } else {
        defaultCollapsedPx
    }

    // Dynamic height calculation according to available content (fits generously with ample breathing room)
    val estimatedContentHeight = remember(vehicle, navBarBottom) {
        val base = 100.dp
        val statusPill = 58.dp // Delay status pill
        val cards = 96.dp // Vía/andén and circulación cards
        val timeline = 164.dp // Real-time tracking current stop & next stop box
        val bottomBreathingRoom = 48.dp + navBarBottom // Ample bottom space to eliminate collision with system UI and bottom bar
        
        base + statusPill + cards + timeline + bottomBreathingRoom
    }
    
    val halfExpandedDp = estimatedContentHeight.coerceAtMost(maxExpandedHeight)
    val fullyExpandedDp = maxExpandedHeight.coerceAtLeast(halfExpandedDp)

    val halfExpandedPx = with(density) { halfExpandedDp.toPx() }
    val fullyExpandedPx = with(density) { fullyExpandedDp.toPx() }

    val heightAnimatable = remember {
        Animatable(
            when (sheetState) {
                DetailSheetState.COLLAPSED -> collapsedPx
                DetailSheetState.HALF_EXPANDED -> halfExpandedPx
                DetailSheetState.FULLY_EXPANDED -> fullyExpandedPx
            }
        )
    }

    LaunchedEffect(sheetState, collapsedPx, halfExpandedPx, fullyExpandedPx) {
        val target = when (sheetState) {
            DetailSheetState.COLLAPSED -> collapsedPx
            DetailSheetState.HALF_EXPANDED -> halfExpandedPx
            DetailSheetState.FULLY_EXPANDED -> fullyExpandedPx
        }
        if (kotlin.math.abs(heightAnimatable.value - target) > 1f && heightAnimatable.targetValue != target) {
            heightAnimatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val currentHeightPx = heightAnimatable.value
    val panelHeight = with(density) { currentHeightPx.toDp() }

    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    val headerDragModifier = Modifier.pointerInput(collapsedPx, halfExpandedPx, fullyExpandedPx) {
        detectVerticalDragGestures(
            onDragStart = { totalDragAmount = 0f },
            onDragEnd = {
                val dragDistance = totalDragAmount
                val currentH = heightAnimatable.value
                val isUp = dragDistance < -15f
                val isDown = dragDistance > 15f

                val targetState = when {
                    isUp -> {
                        if (currentH < halfExpandedPx) DetailSheetState.HALF_EXPANDED else DetailSheetState.FULLY_EXPANDED
                    }
                    isDown -> {
                        if (currentH > halfExpandedPx) DetailSheetState.HALF_EXPANDED else DetailSheetState.COLLAPSED
                    }
                    else -> {
                        val upperMid = (halfExpandedPx + fullyExpandedPx) * 0.5f
                        val lowerMid = (collapsedPx + halfExpandedPx) * 0.5f
                        when {
                            currentH >= upperMid -> DetailSheetState.FULLY_EXPANDED
                            currentH >= lowerMid -> DetailSheetState.HALF_EXPANDED
                            else -> DetailSheetState.COLLAPSED
                        }
                    }
                }
                
                // If the target state is COLLAPSED (meaning the user dragged it down to dismiss), 
                // dismiss completely! This frees up the map and prevents any invisible blocking layers.
                if (targetState == DetailSheetState.COLLAPSED) {
                    onDismiss()
                } else {
                    onSheetStateChanged(targetState)
                    coroutineScope.launch {
                        val targetPx = when (targetState) {
                            DetailSheetState.COLLAPSED -> collapsedPx
                            DetailSheetState.HALF_EXPANDED -> halfExpandedPx
                            DetailSheetState.FULLY_EXPANDED -> fullyExpandedPx
                        }
                        heightAnimatable.animateTo(
                            targetValue = targetPx,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                totalDragAmount += dragAmount
                coroutineScope.launch {
                    val newTarget = (heightAnimatable.value - dragAmount).coerceIn(collapsedPx, fullyExpandedPx)
                    heightAnimatable.snapTo(newTarget)
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = sheetBg,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 0.dp)
        ) {
            // Drag handle decoration & Gestures
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp)
                    .then(headerDragModifier),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF475569) else Color(0xFFCBD5E1))
                )
            }

            // Header Row: Official Line Badge, Train Info, Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        measuredHeaderPx = size.height.toFloat()
                    }
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Official Cercanías Line Logo (Vector/Webp)
                    CercaniasLineBadge(
                        routeId = lineCode,
                        size = 40.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        val displayTrainNum = remember(vehicle) {
                            CercaniasRouteUtils.getEffectiveTrainNumber(vehicle.trainNum, vehicle.tripId)
                        }
                        Text(
                            text = if (displayTrainNum.isNotBlank()) "Tren $displayTrainNum" else "Cercanías $lineCode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sheetTextColor
                        )
                        Text(
                            text = "$resolvedOrigin ➔ $resolvedDest",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sheetTextColor.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = sheetTextColor
                    )
                }
            }

            // Expanded-Only Contents (Shows when NOT collapsed)
            if (sheetState != DetailSheetState.COLLAPSED) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 36.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Delay Status Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusBgColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Estado del servicio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusTextColor,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = statusTextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Telemetry Grid Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Platform / Vía Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkMode) Color(0xFF262626) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "VÍA / ANDÉN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sheetSubtextColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (vehicle.platform.isNotBlank()) "Vía ${vehicle.platform}" else "Por asignar",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (vehicle.nextPlatform.isNotBlank()) {
                                    Text(
                                        text = "Siguiente: Vía ${vehicle.nextPlatform}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sheetSubtextColor
                                    )
                                }
                            }
                        }

                        // Circulation Status Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkMode) Color(0xFF262626) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "CIRCULACIÓN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sheetSubtextColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val statusDesc = when (vehicle.status) {
                                    "STOPPED_AT" -> "Parado en estación"
                                    "INCOMING_AT" -> "Llegando a estación"
                                    else -> "En trayectoria"
                                }
                                Text(
                                    text = statusDesc,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sheetTextColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Timeline Stepper
                    Text(
                        text = "Seguimiento en tiempo real",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = sheetTextColor
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDarkMode) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Current Station Item
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(lineColor, CircleShape)
                                )
                                Column {
                                    Text(
                                        text = "Estación actual / ÚLTIMA PARADA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sheetSubtextColor
                                    )
                                    val resolvedCurrent = stationNameMap[vehicle.currentStopId] ?: vehicle.currentStopId.ifBlank { "Estación de origen" }
                                    Text(
                                        text = resolvedCurrent,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = sheetTextColor
                                    )
                                }
                            }

                            // Connecting Line
                            Box(
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .width(2.dp)
                                    .height(16.dp)
                                    .background(if (isDarkMode) Color(0xFF424242) else MaterialTheme.colorScheme.outlineVariant)
                            )

                            // Next Station Item
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = "PRÓXIMA ESTACIÓN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val resolvedNext = stationNameMap[vehicle.nextStopId] ?: vehicle.nextStopId.ifBlank { "En trayecto" }
                                    Text(
                                        text = resolvedNext,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (vehicle.nextArrivalTime.isNotBlank()) {
                                        val formattedArrivalTime = formatEstimatedArrivalTime(vehicle.nextArrivalTime)
                                        Text(
                                            text = "Llegada estimada: $formattedArrivalTime",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sheetSubtextColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Extra bottom clearance so content never collides with navigation bars or bottom edges
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

fun formatEstimatedArrivalTime(rawTime: String): String {
    if (rawTime.isBlank()) return ""
    val trimmed = rawTime.trim()

    // 1. Numeric timestamp in epoch seconds or milliseconds
    val numericTs = trimmed.toLongOrNull()
    if (numericTs != null && numericTs > 1_000_000_000L) {
        val millis = if (numericTs < 10_000_000_000L) numericTs * 1000L else numericTs
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }

    // 2. Extract the time part after 'T' or space if date prefix is present
    val timePart = when {
        trimmed.contains("T", ignoreCase = true) -> trimmed.split(Regex("(?i)T")).getOrNull(1) ?: trimmed
        trimmed.contains(" ") -> trimmed.split(" ").getOrNull(1) ?: trimmed
        else -> trimmed
    }.trim()

    // 3. Match HH.mm or HH:mm with regex, capturing 1-2 hour digits, separator (dot or colon), and 2 minute digits
    val match = Regex("""^(\d{1,2})[.:](\d{2})(?:[.:]\d{2})?""").find(timePart)
    if (match != null) {
        val (h, m) = match.destructured
        val hh = h.padStart(2, '0')
        return "$hh:$m"
    }

    // 4. Fallback search anywhere in timePart for hours and minutes separated by dot or colon
    val matchAnywhere = Regex("""(\d{1,2})[.:](\d{2})""").find(timePart)
    if (matchAnywhere != null) {
        val (h, m) = matchAnywhere.destructured
        val hh = h.padStart(2, '0')
        return "$hh:$m"
    }

    return trimmed
}

private fun getCercaniasLineColor(lineCode: String): Color {
    val clean = lineCode.uppercase().replace("C-", "").replace("C_", "").replace("C", "").trim()
    return when (clean) {
        "1", "10" -> Color(0xFF00A3E0) // Cyan (C1)
        "2", "20" -> Color(0xFFFF6A00) // Orange (C2)
        "3", "30" -> Color(0xFF7A287B) // Purple (C3)
        "4", "40" -> Color(0xFFE52321) // Red (C4)
        "5", "50" -> Color(0xFF009639) // Green (C5)
        "6", "60" -> Color(0xFF002F6C) // Dark Blue (C6)
        else -> Color(0xFF00A3E0)
    }
}
