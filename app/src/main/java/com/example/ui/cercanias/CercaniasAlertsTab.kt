package com.example.ui.cercanias

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.components.LinkifiedText
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage
import com.example.ui.metro.CercaniasLineBadge
import com.example.ui.metro.parseTimeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CercaniasAlertsTab(
    activeAlerts: List<CercaniasAlert>,
    generalNotices: List<CercaniasAlert>,
    groupedAccessibilityAlerts: Map<String, List<CercaniasAlert>>,
    allCercaniasStations: List<CercaniasStationEntity> = emptyList(),
    isCercaniasAlertsLoading: Boolean,
    isOnline: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    activeTripBottomPadding: Dp = 0.dp,
    hasCercaniasAlertsError: Boolean = false,
    onRetry: () -> Unit = {},
    onNavigateToStation: ((String) -> Unit)? = null
) {
    var isAccessibilityExpanded by remember { mutableStateOf(false) }

    val totalAccessibilityCount = remember(groupedAccessibilityAlerts) {
        groupedAccessibilityAlerts.values.sumOf { it.size }
    }

    val sortedGeneralNotices = remember(generalNotices) {
        generalNotices
            .distinctBy { "${it.headerEs}_${it.descriptionEs}" }
            .sortedWith(
                compareBy<CercaniasAlert> { alert ->
                    val catEnum = CercaniasNoticeCategory.resolveFromText(alert.headerEs, alert.descriptionEs)
                    when (catEnum) {
                        CercaniasNoticeCategory.OBRAS -> 0
                        CercaniasNoticeCategory.PLAN_ALTERNATIVO -> 1
                        CercaniasNoticeCategory.ALERTA_METEOROLOGICA -> 2
                        CercaniasNoticeCategory.AVISO, CercaniasNoticeCategory.INCIDENCIA -> 3
                        CercaniasNoticeCategory.HORARIOS -> 4
                        CercaniasNoticeCategory.TARIFAS -> 5
                        CercaniasNoticeCategory.ACCESIBILIDAD -> 6
                        CercaniasNoticeCategory.INFORMACION -> 7
                    }
                }.thenByDescending { it.timestamp }
            )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cercanias_avisos_tab_list"),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 16.dp + activeTripBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SECCIÓN 1: INCIDENCIAS DE LA RED ---
        item {
            Text(
                text = if (appLanguage == AppLanguage.CA) "INCIDÈNCIES DE LA XARXA" else "INCIDENCIAS DE LA RED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isDarkMode) Color(0xFFEF5350) else MaterialTheme.colorScheme.error,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (isCercaniasAlertsLoading && activeAlerts.isEmpty()) {
            item {
                SkeletonCardItem(modifier = Modifier.fillMaxWidth())
            }
        } else if (activeAlerts.isEmpty()) {
            item {
                if (!isOnline) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDarkMode) 0.3f else 0.18f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.CA)
                                        "Comprova la teua connexió per a actualitzar els avisos en directe."
                                        else "Comprueba tu conexión para actualizar los avisos en directo.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (hasCercaniasAlertsError) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF262422) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF78592A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Informació no disponible" else "Información no disponible",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFB45309)
                                    )
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA)
                                            "No s'ha pogut connectar amb el servidor de Renfe."
                                            else "No se ha podido conectar con el servidor de Renfe.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = onRetry,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Reintentar" else "Reintentar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkMode) 0.25f else 0.15f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Normal",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Xarxa sense incidències" else "Red sin incidencias",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.CA)
                                        "Totes les línies de Renfe Cercanies estan operant amb normalitat."
                                        else "Todas las líneas de Renfe Cercanías están operando con normalidad.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(activeAlerts, key = { it.id }) { alert ->
                val timeAgo = remember(alert.timestamp, appLanguage) {
                    val formatted = parseTimeAgo(alert.timestamp, appLanguage, isUpdated = false)
                    if (formatted != null && (formatted.contains("momentos", ignoreCase = true) || formatted.contains("moments", ignoreCase = true))) {
                        null
                    } else {
                        formatted
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDarkMode) 0.35f else 0.18f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = if (appLanguage == AppLanguage.CA) "Avís" else "Aviso",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (alert.routeIds.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        alert.routeIds.forEach { lineId ->
                                            CercaniasLineBadge(routeId = lineId, size = 20.dp)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Incidència activa" else "Incidencia activa",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (!timeAgo.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = timeAgo,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }

                        if (alert.headerEs.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = alert.headerEs,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (alert.descriptionEs.isNotBlank() && !alert.descriptionEs.equals(alert.headerEs, ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinkifiedText(
                                text = alert.descriptionEs,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // --- SECCIÓN 2: AVISOS ESPECIALES Y OBRAS ---
        item {
            Text(
                text = if (appLanguage == AppLanguage.CA) "AVISOS ESPECIALS I OBRES" else "AVISOS ESPECIALES Y OBRAS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isDarkMode) Color(0xFFFFA726) else Color(0xFFE65100),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        if (isCercaniasAlertsLoading && sortedGeneralNotices.isEmpty()) {
            item {
                SkeletonCardItem(modifier = Modifier.fillMaxWidth())
            }
        } else if (sortedGeneralNotices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF232630) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Sin avisos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "No hi ha avisos especials ni obres actives." else "No hay avisos especiales ni obras activas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = sortedGeneralNotices,
                key = { "${it.id}_${it.headerEs.hashCode()}" }
            ) { notice ->
                val catEnum = remember(notice.headerEs, notice.descriptionEs) {
                    CercaniasNoticeCategory.resolveFromText(notice.headerEs, notice.descriptionEs)
                }
                val badgeCategoryName = catEnum.getDisplayName(appLanguage)
                val badgeColor = catEnum.getColor(isDarkMode)
                val badgeIcon = catEnum.icon
                val timeAgo = remember(notice.timestamp, appLanguage) {
                    val formatted = parseTimeAgo(notice.timestamp, appLanguage, isUpdated = false)
                    if (formatted != null && (formatted.contains("momentos", ignoreCase = true) || formatted.contains("moments", ignoreCase = true))) {
                        null
                    } else {
                        formatted
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF232630) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = badgeCategoryName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            if (!timeAgo.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = timeAgo,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (notice.routeIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                notice.routeIds.forEach { lineId ->
                                    CercaniasLineBadge(routeId = lineId, size = 20.dp)
                                }
                            }
                        }

                        if (notice.headerEs.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = notice.headerEs,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (notice.descriptionEs.isNotBlank() && !notice.descriptionEs.equals(notice.headerEs, ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinkifiedText(
                                text = notice.descriptionEs,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // --- SECCIÓN 3: ACCESIBILIDAD Y ASCENSORES ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAccessibilityExpanded = !isAccessibilityExpanded }
                    .testTag("cercanias_accessibility_header_toggle"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) Color(0xFF232630) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isAccessibilityExpanded) "▲" else "▼",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "ACCESSIBILITAT I ASCENSORS" else "ACCESIBILIDAD Y ASCENSORES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (totalAccessibilityCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            contentColor = if (totalAccessibilityCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = totalAccessibilityCount.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isAccessibilityExpanded) {
            if (groupedAccessibilityAlerts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = if (appLanguage == AppLanguage.CA) "Accessible" else "Accesible",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Accessibilitat sense incidències" else "Accesibilidad sin incidencias",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.CA)
                                        "No s'han detectat problemes en ascensors o accessos de Renfe Cercanies."
                                        else "No se han detectado problemas en ascensores o accesos de Renfe Cercanías.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                groupedAccessibilityAlerts.forEach { (stationName, alertsList) ->
                    item(key = "cercanias_acc_$stationName") {
                        val matchedStation = remember(stationName, allCercaniasStations) {
                            allCercaniasStations.find { station ->
                                station.nombre.equals(stationName, ignoreCase = true) ||
                                station.nombre.replace(" ", "").equals(stationName.replace(" ", ""), ignoreCase = true)
                            }
                        }
                        val isClickable = matchedStation != null && onNavigateToStation != null

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isClickable) {
                                        Modifier.clickable { onNavigateToStation?.invoke(matchedStation!!.stop_id) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("cercanias_accessibility_station_card_${stationName.lowercase().replace(" ", "_")}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkMode) Color(0xFF232630) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stationName.uppercase(),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isClickable) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (appLanguage == AppLanguage.CA) "Veure temps real" else "Ver tiempo real",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Ir a estación",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                alertsList.forEachIndexed { index, alert ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    if (alert.routeIds.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        ) {
                                            alert.routeIds.forEach { lineId ->
                                                CercaniasLineBadge(routeId = lineId, size = 20.dp)
                                            }
                                        }
                                    }

                                    val rawTitle = alert.headerEs.ifBlank { alert.descriptionEs }
                                    val displayTitle = com.example.ui.components.cleanAccessibilityText(rawTitle, stationName)
                                    if (displayTitle.isNotBlank()) {
                                        Text(
                                            text = displayTitle,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }

                                    if (alert.descriptionEs.isNotBlank() && !alert.descriptionEs.equals(alert.headerEs, ignoreCase = true) && !alert.descriptionEs.equals(displayTitle, ignoreCase = true)) {
                                        LinkifiedText(
                                            text = alert.descriptionEs,
                                            textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            fontSize = 12.sp
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
}
