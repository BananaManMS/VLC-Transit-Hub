package com.example.ui.cercanias

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.example.ui.components.TransitPullRefreshIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

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
    val totalAccessibilityCount = remember(groupedAccessibilityAlerts) {
        groupedAccessibilityAlerts.values.sumOf { it.size }
    }

    var isAccessibilityExpanded by remember(totalAccessibilityCount > 0) {
        mutableStateOf(totalAccessibilityCount > 0)
    }
    var selectedDetailNotice by remember { mutableStateOf<CercaniasAlert?>(null) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cardBorder = BorderStroke(
        1.dp,
        AppThemeColors.subtleBorder(isDarkMode).copy(alpha = if (isDarkMode) 1f else 0.6f)
    )
    val statusOk = AppThemeColors.statusOk(isDarkMode)
    val statusOkContainer = AppThemeColors.statusOkContainer(isDarkMode)
    val statusWarning = AppThemeColors.statusWarning(isDarkMode)
    val statusWarningContainer = AppThemeColors.statusWarningContainer(isDarkMode)
    val statusError = AppThemeColors.statusError(isDarkMode)
    val statusErrorContainer = AppThemeColors.statusErrorContainer(isDarkMode)
    val statusAcc = AppThemeColors.statusAccessibility(isDarkMode)

    val headerTimestamp = remember(activeAlerts, generalNotices) {
        com.example.data.repository.renfe.RenfeAlertsRepository.getCachedHeaderTimestamp().takeIf { it > 0L }
            ?: ((activeAlerts + generalNotices).maxOfOrNull { it.timestamp } ?: 0L)
    }
    val updateTimeString = remember(headerTimestamp) {
        if (headerTimestamp > 0L) {
            val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(headerTimestamp * 1000L))
        } else ""
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

    val pullToRefreshState = rememberPullToRefreshState()
    val (isPullRefreshing, triggerAlertsRefresh) = com.example.ui.components.rememberPullRefreshFeedback(
        isLoading = isCercaniasAlertsLoading,
        onRefresh = onRetry
    )

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = triggerAlertsRefresh,
        state = pullToRefreshState,
        indicator = {
            TransitPullRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = isPullRefreshing,
                appLanguage = appLanguage,
                refreshingText = if (appLanguage == AppLanguage.CA) "Actualitzant avisos..." else "Actualizando avisos..."
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("cercanias_avisos_tab_list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp + activeTripBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- RESUMEN COMPACTO DE ESTADO + MARCA TEMPORAL RENFE ---
            if (isOnline && !(hasCercaniasAlertsError && allNonAccNotices.isEmpty()) && !(isCercaniasAlertsLoading && allNonAccNotices.isEmpty())) {
                item(key = "cercanias_status_summary_bar") {
                    val hasIncidents = classifiedIncidents.isNotEmpty()
                    val summaryAccent = if (hasIncidents) statusError else statusOk
                    val summaryBg = if (hasIncidents) statusErrorContainer else statusOkContainer
                    val summaryLabel = if (hasIncidents) {
                        if (appLanguage == AppLanguage.CA) {
                            if (classifiedIncidents.size == 1) "1 incidència activa" else "${classifiedIncidents.size} incidències actives"
                        } else {
                            if (classifiedIncidents.size == 1) "1 incidencia activa" else "${classifiedIncidents.size} incidencias activas"
                        }
                    } else {
                        if (appLanguage == AppLanguage.CA) "Servei normal a Rodalies" else "Servicio normal en Cercanías"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = summaryBg,
                        border = BorderStroke(1.dp, summaryAccent.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (hasIncidents) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = summaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = summaryLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = summaryAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (updateTimeString.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Renfe · $updateTimeString",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Offline Banner
            if (!isOnline) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = statusErrorContainer),
                        border = BorderStroke(1.dp, statusError.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = statusError,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.offline_banner_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = statusError
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
                        colors = CardDefaults.cardColors(containerColor = statusWarningContainer),
                        border = BorderStroke(1.dp, statusWarning.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp)
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
                                    tint = statusWarning,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.incidencias_info_unavailable),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = statusWarning
                                    )
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_server_error_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
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
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_retry),
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
            item {
                SectionHeader(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_live_incidents_header),
                    count = classifiedIncidents.size,
                    badgeColor = if (classifiedIncidents.isNotEmpty()) statusError else null
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

            // ==========================================
            // SECCIÓN 2: PLANES ALTERNATIVOS Y OBRAS
            // ==========================================
            item {
                SectionHeader(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_alternative_plans_header),
                    count = classifiedPlanesAndObras.size,
                    topPadding = 8.dp
                )
            }

            if (classifiedPlanesAndObras.isEmpty()) {
                item {
                    NormalOperationCard(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_works_title),
                        subtitle = if (appLanguage == AppLanguage.CA)
                            "No s'han registrat serveis sustitutoris per autobús ni treballs en via."
                        else "No se han registrado servicios sustitutorios por autobús ni trabajos en vía.",
                        isDarkMode = isDarkMode
                    )
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

            // ==========================================
            // SECCIÓN 3: HORARIOS E INFORMACIÓN GENERAL
            // ==========================================
            item {
                SectionHeader(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_schedules_info_header),
                    count = classifiedHorariosAndInfo.size,
                    topPadding = 8.dp
                )
            }

            if (classifiedHorariosAndInfo.isEmpty()) {
                item {
                    NormalOperationCard(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_schedule_notices_title),
                        subtitle = if (appLanguage == AppLanguage.CA)
                            "No hi ha avisos generals d'horaris ni de tarifes en este moment."
                        else "No hay avisos generales de horarios ni de tarifas en este momento.",
                        isDarkMode = isDarkMode
                    )
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

            // ==========================================
            // SECCIÓN 4: ACCESIBILIDAD Y ASCENSORES
            // ==========================================
            item {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isAccessibilityExpanded) 180f else 0f,
                    label = "cercanias_acc_chevron"
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isAccessibilityExpanded = !isAccessibilityExpanded }
                        .testTag("cercanias_accessibility_header_toggle"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = cardBorder,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(statusAcc.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Accessible,
                                    contentDescription = null,
                                    tint = statusAcc,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_accessibility_header),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (totalAccessibilityCount > 0) statusError.copy(alpha = 0.16f) else statusOk.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = totalAccessibilityCount.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalAccessibilityCount > 0) statusError else statusOk,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(chevronRotation)
                            )
                        }
                    }
                }
            }

            if (isAccessibilityExpanded) {
                if (groupedAccessibilityAlerts.isEmpty()) {
                    item {
                        NormalOperationCard(
                            title = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_no_accessibility_incidents_title),
                            subtitle = if (appLanguage == AppLanguage.CA)
                                "No s'han detectat problemes en ascensors o accessos de Rodalies Renfe."
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
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(
                                        if (isClickable) {
                                            Modifier.clickable { onNavigateToStation?.invoke(matchedStation!!.stop_id) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .testTag("cercanias_accessibility_station_card_${stationName.lowercase().replace(" ", "_")}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = cardBorder,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stationName.uppercase(),
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            letterSpacing = 0.4.sp,
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
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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

                                        val rawTitle = sanitizeRenfeNoticeText(alert.headerEs.ifBlank { alert.descriptionEs })
                                        val displayTitle = com.example.ui.components.cleanAccessibilityText(rawTitle, stationName)
                                        if (displayTitle.isNotBlank()) {
                                            Text(
                                                text = displayTitle,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }

                                        val cleanDesc = sanitizeRenfeNoticeText(alert.descriptionEs)
                                        if (cleanDesc.isNotBlank() && !cleanDesc.equals(rawTitle, ignoreCase = true) && !cleanDesc.equals(displayTitle, ignoreCase = true)) {
                                            LinkifiedText(
                                                text = cleanDesc,
                                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp
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
    count: Int,
    badgeColor: Color? = null,
    topPadding: Dp = 4.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 2.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(),
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )
        if (count > 0) {
            val pillBg = badgeColor?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceVariant
            val pillFg = badgeColor ?: MaterialTheme.colorScheme.onSurface
            Surface(
                shape = CircleShape,
                color = pillBg
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = pillFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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

    val cleanHeader = remember(alert.headerEs) { sanitizeRenfeNoticeText(alert.headerEs) }
    val cleanDescription = remember(alert.descriptionEs) { sanitizeRenfeNoticeText(alert.descriptionEs) }
    val hasDistinctBody = cleanDescription.isNotBlank() && !cleanDescription.equals(cleanHeader, ignoreCase = true)

    val statusError = AppThemeColors.statusError(isDarkMode)
    val statusErrorContainer = AppThemeColors.statusErrorContainer(isDarkMode)
    val cardBorder = if (isUrgent) {
        BorderStroke(1.dp, statusError.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, AppThemeColors.subtleBorder(isDarkMode).copy(alpha = if (isDarkMode) 1f else 0.6f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("cercanias_notice_card_${alert.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) statusErrorContainer else MaterialTheme.colorScheme.surface
        ),
        border = cardBorder,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkMode) 0.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeCategoryName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            letterSpacing = 0.4.sp
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

            if (cleanHeader.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = cleanHeader,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (hasDistinctBody) 2 else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasDistinctBody) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = cleanDescription,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.CA) "Llegir avís complet" else "Leer aviso completo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
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
    val statusOk = AppThemeColors.statusOk(isDarkMode)
    val statusOkContainer = AppThemeColors.statusOkContainer(isDarkMode)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusOkContainer),
        border = BorderStroke(1.dp, statusOk.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Normal",
                tint = statusOk,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = statusOk
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

/**
 * Cleans raw Renfe GTFS-RT alert markup such as `#AVISO#`, `#LÍNEA C6#`, `#LÍNEA C3.`,
 * repeated hashes/separators, and ALL-CAPS shouting blocks while preserving transit codes.
 */
internal fun sanitizeRenfeNoticeText(raw: String): String {
    if (raw.isBlank()) return ""
    var text = raw
        .replace(Regex("#\\s*(?:AVISO|AVÍS|INFO|INFORMACIÓN|IMPORTANTE|ATENCIÓN|INCIDENCIA)\\s*#?:?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("#\\s*L[IÍ]NEAS?\\s+[C0-9,\\s\\-yY/]+[#.:\\-]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("#\\s*C[1-6]\\s*#?:?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\s*[#\\-*|:\\s]+"), "")
        .replace(Regex("\\s*#+\\s*"), " ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    if (text.isBlank()) return raw.trim()

    // Tame shouting ALL-CAPS blocks (>25 letters, >80% uppercase) into readable sentence case
    val letters = text.filter { it.isLetter() }
    if (letters.length > 25 && letters.count { it.isUpperCase() }.toFloat() / letters.length > 0.8f) {
        val esLocale = java.util.Locale.forLanguageTag("es-ES")
        text = text.lowercase(esLocale)
            .replaceFirstChar { it.titlecase(esLocale) }
            .replace(Regex("([.!?]\\s+)([a-záéíóúüñç])")) { match ->
                match.groupValues[1] + match.groupValues[2].uppercase(esLocale)
            }
            .replace(Regex("\\b(c[1-6])\\b", RegexOption.IGNORE_CASE)) { it.value.uppercase(esLocale) }
            .replace(Regex("\\b(renfe|adif|emt|fgv|atmv|suma|ave|euromed|intercity|md|r\\d+)\\b", RegexOption.IGNORE_CASE)) { match ->
                when (match.value.lowercase(esLocale)) {
                    "renfe" -> "Renfe"
                    "euromed" -> "Euromed"
                    "intercity" -> "Intercity"
                    else -> match.value.uppercase(esLocale)
                }
            }
            .replace(Regex("\\bval[eè]ncia\\b", RegexOption.IGNORE_CASE)) { "València" }
            .replace(Regex("\\bcastell[oó]\\b", RegexOption.IGNORE_CASE)) { "Castelló" }
            .replace(Regex("\\bx[aà]tiva\\b", RegexOption.IGNORE_CASE)) { "Xàtiva" }
            .replace(Regex("\\bplatja\\s+i\\s+grau\\s+de\\s+gand[ií]a\\b", RegexOption.IGNORE_CASE)) { "Platja i Grau de Gandia" }
            .replace(Regex("\\bplatja\\s+de\\s+gand[ií]a\\b", RegexOption.IGNORE_CASE)) { "Platja de Gandia" }
            .replace(Regex("\\bgand[ií]a\\b", RegexOption.IGNORE_CASE)) { "Gandia" }
            .replace(Regex("\\bsagunt\\b", RegexOption.IGNORE_CASE)) { "Sagunt" }
            .replace(Regex("\\bsagunto\\b", RegexOption.IGNORE_CASE)) { "Sagunto" }
            .replace(Regex("\\balzira\\b", RegexOption.IGNORE_CASE)) { "Alzira" }
            .replace(Regex("\\bsueca\\b", RegexOption.IGNORE_CASE)) { "Sueca" }
            .replace(Regex("\\bcullera\\b", RegexOption.IGNORE_CASE)) { "Cullera" }
            .replace(Regex("\\bmoixent\\b", RegexOption.IGNORE_CASE)) { "Moixent" }
            .replace(Regex("\\butiel\\b", RegexOption.IGNORE_CASE)) { "Utiel" }
            .replace(Regex("\\brequena\\b", RegexOption.IGNORE_CASE)) { "Requena" }
            .replace(Regex("\\bbuñol\\b", RegexOption.IGNORE_CASE)) { "Buñol" }
            .replace(Regex("\\bcaudiel\\b", RegexOption.IGNORE_CASE)) { "Caudiel" }
    }

    return text
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
