package com.example.ui.cercanias

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CercaniasStationEntity
import com.example.ui.components.LinkifiedText
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage
import com.example.ui.metro.CercaniasLineBadge

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
    var selectedGroupFilter by remember { mutableStateOf(CercaniasAlertGroup.TODOS) }
    var isAccessibilityExpanded by remember { mutableStateOf(true) }
    var selectedDetailNotice by remember { mutableStateOf<CercaniasAlert?>(null) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val totalAccessibilityCount = remember(groupedAccessibilityAlerts) {
        groupedAccessibilityAlerts.values.sumOf { it.size }
    }

    // Combine all non-accessibility notices to avoid duplication and allow robust classification
    val allNonAccNotices = remember(activeAlerts, generalNotices) {
        (activeAlerts + generalNotices)
            .distinctBy { "${it.headerEs.trim()}_${it.descriptionEs.trim()}" }
    }

    // Segregate cleanly into classified lists based on resolved category groups
    val classifiedIncidents = remember(allNonAccNotices) {
        allNonAccNotices.filter {
            val cat = CercaniasNoticeCategory.resolveFromText(it.headerEs, it.descriptionEs)
            cat.group == CercaniasAlertGroup.INCIDENCIAS
        }.sortedWith(
            compareBy<CercaniasAlert> { alert ->
                getLineSortKey(alert.routeIds)
            }.thenBy { alert ->
                getCategorySortOrder(CercaniasNoticeCategory.resolveFromText(alert.headerEs, alert.descriptionEs))
            }.thenBy { alert ->
                alert.headerEs
            }
        )
    }

    val classifiedPlanesAndObras = remember(allNonAccNotices) {
        allNonAccNotices.filter {
            val cat = CercaniasNoticeCategory.resolveFromText(it.headerEs, it.descriptionEs)
            cat.group == CercaniasAlertGroup.PLANES_Y_OBRAS
        }.sortedWith(
            compareBy<CercaniasAlert> { alert ->
                getLineSortKey(alert.routeIds)
            }.thenBy { alert ->
                getCategorySortOrder(CercaniasNoticeCategory.resolveFromText(alert.headerEs, alert.descriptionEs))
            }.thenBy { alert ->
                alert.headerEs
            }
        )
    }

    val classifiedHorariosAndInfo = remember(allNonAccNotices) {
        allNonAccNotices.filter {
            val cat = CercaniasNoticeCategory.resolveFromText(it.headerEs, it.descriptionEs)
            cat.group == CercaniasAlertGroup.HORARIOS_E_INFO
        }.sortedWith(
            compareBy<CercaniasAlert> { alert ->
                getLineSortKey(alert.routeIds)
            }.thenBy { alert ->
                getCategorySortOrder(CercaniasNoticeCategory.resolveFromText(alert.headerEs, alert.descriptionEs))
            }.thenBy { alert ->
                alert.headerEs
            }
        )
    }

    val totalAllCount = classifiedIncidents.size + classifiedPlanesAndObras.size + classifiedHorariosAndInfo.size + totalAccessibilityCount

    Column(modifier = Modifier.fillMaxSize()) {
        // --- CATEGORY FILTER CHIPS ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CercaniasAlertGroup.values().forEach { group ->
                val isSelected = selectedGroupFilter == group
                val count = when (group) {
                    CercaniasAlertGroup.TODOS -> totalAllCount
                    CercaniasAlertGroup.INCIDENCIAS -> classifiedIncidents.size
                    CercaniasAlertGroup.PLANES_Y_OBRAS -> classifiedPlanesAndObras.size
                    CercaniasAlertGroup.HORARIOS_E_INFO -> classifiedHorariosAndInfo.size
                    CercaniasAlertGroup.ACCESIBILIDAD -> totalAccessibilityCount
                }

                val chipLabel = group.getDisplayName(appLanguage)
                val badgeColor = if (isDarkMode) group.colorDark else group.colorLight

                FilterChip(
                    selected = isSelected,
                    onClick = { selectedGroupFilter = group },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = chipLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                } else if (count > 0 && group == CercaniasAlertGroup.INCIDENCIAS) {
                                    badgeColor.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = count.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else if (count > 0 && group == CercaniasAlertGroup.INCIDENCIAS) {
                                        badgeColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = group.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else badgeColor
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = if (isDarkMode) Color(0xFF1F222A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("cercanias_alert_filter_${group.id}")
                )
            }
        }

        // --- CONTENT LIST ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("cercanias_avisos_tab_list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp + activeTripBottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Offline Banner
            if (!isOnline) {
                item {
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
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.offline_banner_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_check_connection_alerts),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (hasCercaniasAlertsError && allNonAccNotices.isEmpty()) {
                item {
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
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.incidencias_info_unavailable),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFB45309)
                                    )
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_server_error_desc),
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
                }
            }

            if (isCercaniasAlertsLoading && allNonAccNotices.isEmpty()) {
                item {
                    SkeletonCardItem(modifier = Modifier.fillMaxWidth())
                }
                item {
                    SkeletonCardItem(modifier = Modifier.fillMaxWidth())
                }
            }

            // ==========================================
            // SECCIÓN 1: INCIDENCIAS Y RETRASOS EN DIRECTO
            // ==========================================
            if (selectedGroupFilter == CercaniasAlertGroup.TODOS || selectedGroupFilter == CercaniasAlertGroup.INCIDENCIAS) {
                item {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_live_incidents_header),
                        color = if (isDarkMode) Color(0xFFEF5350) else Color(0xFFC62828),
                        count = classifiedIncidents.size
                    )
                }

                if (classifiedIncidents.isEmpty()) {
                    item {
                        NormalOperationCard(
                            title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_incidents_title),
                            subtitle = if (appLanguage == AppLanguage.CA)
                                "No hi ha retards greus ni trens suprimits a la xarxa de València."
                                else "No hay retrasos graves ni trenes suprimidos en la red de Valencia.",
                            isDarkMode = isDarkMode
                        )
                    }
                } else {
                    items(classifiedIncidents, key = { "inc_${it.id}_${it.headerEs.hashCode()}" }) { alert ->
                        CercaniasNoticeCard(
                            alert = alert,
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode,
                            onClick = { selectedDetailNotice = alert }
                        )
                    }
                }
            }

            // ==========================================
            // SECCIÓN 2: PLANES ALTERNATIVOS Y OBRAS
            // ==========================================
            if (selectedGroupFilter == CercaniasAlertGroup.TODOS || selectedGroupFilter == CercaniasAlertGroup.PLANES_Y_OBRAS) {
                item {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_alternative_plans_header),
                        color = if (isDarkMode) Color(0xFFCE93D8) else Color(0xFF7B1FA2),
                        count = classifiedPlanesAndObras.size
                    )
                }

                if (classifiedPlanesAndObras.isEmpty()) {
                    if (selectedGroupFilter == CercaniasAlertGroup.PLANES_Y_OBRAS) {
                        item {
                            NormalOperationCard(
                                title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_works_title),
                                subtitle = if (appLanguage == AppLanguage.CA)
                                    "No s'han registrat serveis sustitutoris per autobús ni treballs en via."
                                    else "No se han registrado servicios sustitutorios por autobús ni trabajos en vía.",
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                } else {
                    items(classifiedPlanesAndObras, key = { "plan_${it.id}_${it.headerEs.hashCode()}" }) { alert ->
                        CercaniasNoticeCard(
                            alert = alert,
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode,
                            onClick = { selectedDetailNotice = alert }
                        )
                    }
                }
            }

            // ==========================================
            // SECCIÓN 3: HORARIOS E INFORMACIÓN GENERAL
            // ==========================================
            if (selectedGroupFilter == CercaniasAlertGroup.TODOS || selectedGroupFilter == CercaniasAlertGroup.HORARIOS_E_INFO) {
                item {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_schedules_info_header),
                        color = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        count = classifiedHorariosAndInfo.size
                    )
                }

                if (classifiedHorariosAndInfo.isEmpty()) {
                    if (selectedGroupFilter == CercaniasAlertGroup.HORARIOS_E_INFO) {
                        item {
                            NormalOperationCard(
                                title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_schedule_notices_title),
                                subtitle = if (appLanguage == AppLanguage.CA)
                                    "No hi ha avisos generals d'horaris ni de tarifes en aquest moment."
                                    else "No hay avisos generales de horarios ni de tarifas en este momento.",
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                } else {
                    items(classifiedHorariosAndInfo, key = { "info_${it.id}_${it.headerEs.hashCode()}" }) { alert ->
                        CercaniasNoticeCard(
                            alert = alert,
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode,
                            onClick = { selectedDetailNotice = alert }
                        )
                    }
                }
            }

            // ==========================================
            // SECCIÓN 4: ACCESIBILIDAD Y ASCENSORES
            // ==========================================
            if (selectedGroupFilter == CercaniasAlertGroup.TODOS || selectedGroupFilter == CercaniasAlertGroup.ACCESIBILIDAD) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAccessibilityExpanded = !isAccessibilityExpanded }
                            .testTag("cercanias_accessibility_header_toggle"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF1E222B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                    color = if (isDarkMode) Color(0xFF4DD0E1) else Color(0xFF00838F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_accessibility_header),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDarkMode) Color(0xFF4DD0E1) else Color(0xFF00838F),
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

                if (isAccessibilityExpanded || selectedGroupFilter == CercaniasAlertGroup.ACCESIBILIDAD) {
                    if (groupedAccessibilityAlerts.isEmpty()) {
                        item {
                            NormalOperationCard(
                                title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_accessibility_incidents_title),
                                subtitle = if (appLanguage == AppLanguage.CA)
                                    "No s'han detectat problemes en ascensors o accessos de Renfe Cercanies."
                                    else "No se han detectado problemas en ascensores o accesos de Renfe Cercanías.",
                                isDarkMode = isDarkMode
                            )
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
                                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_view_real_time),
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
    }

    // Detail Bottom Sheet when tapping a card
    if (selectedDetailNotice != null) {
        CercaniasNoticeDetailBottomSheet(
            alert = selectedDetailNotice,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            sheetState = detailSheetState,
            onDismiss = { selectedDetailNotice = null }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    color: Color,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            letterSpacing = 1.8.sp
        )
        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun CercaniasNoticeCard(
    alert: CercaniasAlert,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val catEnum = remember(alert.headerEs, alert.descriptionEs) {
        CercaniasNoticeCategory.resolveFromText(alert.headerEs, alert.descriptionEs)
    }
    val badgeCategoryName = catEnum.getDisplayName(appLanguage)
    val badgeColor = catEnum.getColor(isDarkMode)
    val badgeIcon = catEnum.icon
    val isUrgent = catEnum == CercaniasNoticeCategory.SUPRESION || catEnum == CercaniasNoticeCategory.INCIDENCIA

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("cercanias_notice_card_${alert.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDarkMode) 0.35f else 0.18f)
            } else {
                if (isDarkMode) Color(0xFF232630) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
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

                if (alert.routeIds.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        alert.routeIds.forEach { lineId ->
                            CercaniasLineBadge(routeId = lineId, size = 20.dp)
                        }
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

@Composable
private fun NormalOperationCard(
    title: String,
    subtitle: String,
    isDarkMode: Boolean
) {
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
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getLineSortKey(routeIds: List<String>): Int {
    if (routeIds.isEmpty()) return 0
    val lineNumbers = routeIds.mapNotNull {
        it.replace("C", "", ignoreCase = true).replace("-", "").trim().toIntOrNull()
    }
    return if (lineNumbers.isNotEmpty()) {
        if (lineNumbers.size > 2) 0 else lineNumbers.minOrNull() ?: 99
    } else {
        99
    }
}

private fun getCategorySortOrder(cat: CercaniasNoticeCategory): Int {
    return when (cat) {
        CercaniasNoticeCategory.SUPRESION -> 0
        CercaniasNoticeCategory.INCIDENCIA -> 1
        CercaniasNoticeCategory.ALERTA_METEOROLOGICA -> 2
        CercaniasNoticeCategory.PLAN_ALTERNATIVO -> 3
        CercaniasNoticeCategory.OBRAS -> 4
        CercaniasNoticeCategory.HORARIOS -> 5
        CercaniasNoticeCategory.TARIFAS -> 6
        CercaniasNoticeCategory.AVISO -> 7
        CercaniasNoticeCategory.INFORMACION -> 8
        CercaniasNoticeCategory.ACCESIBILIDAD -> 9
    }
}
