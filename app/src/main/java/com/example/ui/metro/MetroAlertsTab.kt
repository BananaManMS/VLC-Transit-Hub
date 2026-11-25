package com.example.ui.metro

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.example.ui.components.TransitPullRefreshIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LinkifiedText
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvisosTab(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    isDarkMode: Boolean,
    onNavigateToMetroStation: (String) -> Unit = {},
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    LaunchedEffect(Unit) {
        metroViewModel.fetchAllAlerts(includeNews = true)
    }

    val activeIncidents by metroViewModel.activeIncidents.collectAsState()
    val specialNotices by metroViewModel.specialNotices.collectAsState()
    val accessibilityIncidents by metroViewModel.accessibilityIncidents.collectAsState()
    val metroNews by metroViewModel.metroNews.collectAsState()
    val isMetroAlertsLoading by metroViewModel.isMetroAlertsLoading.collectAsState()
    val hasMetroAlertsError by metroViewModel.hasMetroAlertsError.collectAsState()
    val metroAlertsErrorDetail by metroViewModel.metroAlertsErrorDetail.collectAsState()
    val isNewsLoading by metroViewModel.isNewsLoading.collectAsState()
    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    var isAccessibilityExpanded by remember { mutableStateOf(false) }
    var selectedNewsItem by remember { mutableStateOf<MetroNewsItem?>(null) }
    var selectedSpecialNotice by remember { mutableStateOf<MetroNotice?>(null) }

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

    data class MergedActiveIncident(
        val id: String,
        val descriptionEs: String,
        val descriptionCa: String,
        val descriptionEn: String,
        val lineasFgv: List<String>,
        val updatedAt: String?
    )

    val sortedSpecialNotices = remember(specialNotices) {
        specialNotices
            .distinctBy { "${it.category}_${it.title}_${it.publicationDate}" }
            .sortedWith(
                compareBy<MetroNotice> { notice ->
                    val catEnum = MetroNoticeCategory.fromRaw(notice.category)
                    when (catEnum) {
                        MetroNoticeCategory.OBRAS -> 0
                        MetroNoticeCategory.SERVICIO_ESPECIAL -> 1
                        MetroNoticeCategory.ALERTA_METEOROLOGICA -> 2
                        MetroNoticeCategory.AVISO, MetroNoticeCategory.INCIDENCIA -> 3
                        MetroNoticeCategory.ACCESIBILIDAD -> 4
                        MetroNoticeCategory.PROMOCION -> 5
                        MetroNoticeCategory.OTRO -> 6
                    }
                }.thenByDescending { it.publicationDate ?: "" }
            )
    }

    val groupedActiveIncidents = remember(activeIncidents) {
        data class ActiveIncidentKey(
            val descEs: String,
            val descCa: String,
            val descEn: String
        )
        val groups = LinkedHashMap<ActiveIncidentKey, MutableList<MetroIncident>>()
        for (incident in activeIncidents) {
            val key = ActiveIncidentKey(
                descEs = incident.descriptionEs.trim(),
                descCa = incident.descriptionCa.trim(),
                descEn = incident.descriptionEn.trim()
            )
            groups.getOrPut(key) { mutableListOf() }.add(incident)
        }
        groups.map { (_, list) ->
            val lines = list.mapNotNull { it.lineaFgv }.filter { it.isNotBlank() }.distinct()
            val representative = list.first()
            MergedActiveIncident(
                id = representative.id,
                descriptionEs = representative.descriptionEs,
                descriptionCa = representative.descriptionCa,
                descriptionEn = representative.descriptionEn,
                lineasFgv = lines,
                updatedAt = list.mapNotNull { it.updatedAt }.firstOrNull()
            )
        }
    }

    data class GroupedStation(val name: String, val id: String?)

    val fallbackStationName = androidx.compose.ui.res.stringResource(com.example.R.string.metro_station_fallback_name)
    val groupedIncidents = remember(accessibilityIncidents, allNetworkStations, fallbackStationName) {
        val map = mutableMapOf<GroupedStation, MutableList<AccessibilityIncident>>()
        for (incident in accessibilityIncidents) {
            val rawName = incident.estacionNombre?.trim()
            val key = if (!rawName.isNullOrEmpty()) {
                val matchedStation = allNetworkStations.find { station ->
                    station.name.equals(rawName, ignoreCase = true) ||
                        station.name.replace(" ", "").equals(rawName.replace(" ", ""), ignoreCase = true)
                }
                GroupedStation(rawName, matchedStation?.id)
            } else {
                GroupedStation(fallbackStationName, null)
            }
            map.getOrPut(key) { mutableListOf() }.add(incident)
        }
        map
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val (isPullRefreshing, triggerAlertsRefresh) = com.example.ui.components.rememberPullRefreshFeedback(
        isLoading = isMetroAlertsLoading,
        onRefresh = { metroViewModel.fetchAllAlerts(force = false, includeNews = true) }
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
                .testTag("avisos_tab_list"),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 16.dp + activeTripBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- RESUMEN COMPACTO DE ESTADO ---
            if (isOnline && !hasMetroAlertsError && !isMetroAlertsLoading) {
                item(key = "metro_status_summary_bar") {
                    val hasIncidents = groupedActiveIncidents.isNotEmpty()
                    val summaryAccent = if (hasIncidents) statusError else statusOk
                    val summaryBg = if (hasIncidents) statusErrorContainer else statusOkContainer
                    val summaryLabel = if (hasIncidents) {
                        if (appLanguage == AppLanguage.CA) {
                            if (groupedActiveIncidents.size == 1) "1 incidència activa a la xarxa" else "${groupedActiveIncidents.size} incidències actives a la xarxa"
                        } else {
                            if (groupedActiveIncidents.size == 1) "1 incidencia activa en la red" else "${groupedActiveIncidents.size} incidencias activas en la red"
                        }
                    } else {
                        if (appLanguage == AppLanguage.CA) "Servei normal a Metrovalencia" else "Servicio normal en Metrovalencia"
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- SECCIÓN 1: INCIDENCIAS DE LA RED ---
            item {
                MetroAlertSectionHeader(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.metro_alerts_network_incidents_header),
                    count = groupedActiveIncidents.size,
                    badgeColor = if (groupedActiveIncidents.isNotEmpty()) statusError else null
                )
            }

            if (isMetroAlertsLoading && groupedActiveIncidents.isEmpty()) {
                item {
                    SkeletonCardItem(modifier = Modifier.fillMaxWidth())
                }
            } else if (groupedActiveIncidents.isEmpty()) {
                item {
                    if (!isOnline) {
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
                    } else if (hasMetroAlertsError) {
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
                                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_error_server_connection),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!metroAlertsErrorDetail.isNullOrBlank()) {
                                            Text(
                                                text = metroAlertsErrorDetail!!,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { metroViewModel.fetchAllAlerts(force = true, includeNews = true) },
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
                    } else {
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
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.status_normal),
                                    tint = statusOk,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.no_avisos_red),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = statusOk
                                    )
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.no_avisos_red_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    items = groupedActiveIncidents,
                    key = { "${it.id}_${it.lineasFgv.joinToString()}" }
                ) { incident ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = statusErrorContainer),
                        border = BorderStroke(1.dp, statusError.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.aviso_singular),
                                    tint = statusError,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (incident.lineasFgv.isNotEmpty()) {
                                    MetroLineBadgesRow(lineasStr = incident.lineasFgv.joinToString(", "))
                                } else {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_active_incident_label),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = statusError
                                    )
                                }
                            }

                            val displayDesc = when (appLanguage) {
                                AppLanguage.CA -> incident.descriptionCa.ifEmpty { incident.descriptionEs }
                                else -> incident.descriptionEs
                            }
                            if (displayDesc.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = displayDesc,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // --- SECCIÓN 2: AVISOS ESPECIALES Y OBRAS ---
            item {
                MetroAlertSectionHeader(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.metro_alerts_special_works_header),
                    count = sortedSpecialNotices.size,
                    topPadding = 8.dp
                )
            }

            if (isMetroAlertsLoading && sortedSpecialNotices.isEmpty()) {
                item {
                    SkeletonCardItem(modifier = Modifier.fillMaxWidth())
                }
            } else if (sortedSpecialNotices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorder,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.no_avisos),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_no_special_notices),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = sortedSpecialNotices,
                    key = { "${it.category}_${it.id}_${it.title.hashCode()}_${it.publicationDate}" }
                ) { notice ->
                    val catEnum = remember(notice.category) { MetroNoticeCategory.fromRaw(notice.category) }
                    val badgeCategoryName = catEnum.getDisplayName(appLanguage)
                    val badgeColor = catEnum.getColor(isDarkMode)
                    val badgeIcon = catEnum.icon
                    val hasExtraBody = notice.description.isNotBlank() && !notice.description.equals(notice.title, ignoreCase = true)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedSpecialNotice = notice },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

                                val displayTime = parseTimeAgo(notice.publicationDate, appLanguage, isUpdated = false)
                                if (!displayTime.isNullOrBlank()) {
                                    Text(
                                        text = displayTime,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!notice.lineasAfectadas.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                MetroLineBadgesRow(lineasStr = notice.lineasAfectadas)
                            }

                            if (notice.title.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = notice.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (hasExtraBody) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notice.description,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (hasExtraBody || notice.title.length > 110) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "Veure detall" else "Ver detalle",
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
                }
            }

            // --- SECCIÓN 3: ACCESIBILIDAD Y ASCENSORES ---
            item {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isAccessibilityExpanded) 180f else 0f,
                    label = "metro_acc_chevron"
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isAccessibilityExpanded = !isAccessibilityExpanded }
                        .testTag("accessibility_header_toggle"),
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
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.accessibility_and_lifts),
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
                                color = if (accessibilityIncidents.isNotEmpty()) statusError.copy(alpha = 0.16f) else statusOk.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = accessibilityIncidents.size.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (accessibilityIncidents.isNotEmpty()) statusError else statusOk,
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
                if (groupedIncidents.isEmpty()) {
                    item {
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
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.accessible_label),
                                    tint = statusOk,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.no_accessibility_avisos),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = statusOk
                                    )
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.no_accessibility_avisos_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    groupedIncidents.forEach { (station, incidentsList) ->
                        item {
                            val isClickable = station.id != null
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(
                                        if (isClickable) {
                                            Modifier.clickable { onNavigateToMetroStation(station.id!!) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .testTag("accessibility_station_card_${station.name.lowercase().replace(" ", "_")}"),
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
                                            text = station.name.uppercase(),
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
                                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_alerts_view_real_time),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.go_to_station_desc),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    incidentsList.forEachIndexed { index, incident ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        if (!incident.lineasAfectadas.isNullOrBlank()) {
                                            MetroLineBadgesRow(lineasStr = incident.lineasAfectadas)
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        val rawTitle = incident.tituloEs.ifBlank { incident.descripcionEs }
                                        val displayTitle = com.example.ui.components.cleanAccessibilityText(rawTitle, station.name)
                                        if (displayTitle.isNotBlank()) {
                                            Text(
                                                text = displayTitle,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SECCIÓN 4: NOTICIAS DE METROVALENCIA ---
            item {
                MetroAlertSectionHeader(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.metro_alerts_news_header),
                    count = metroNews.size,
                    topPadding = 8.dp
                )
            }

            if (isNewsLoading && metroNews.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (metroNews.isEmpty()) {
                item {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_no_news_available),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                items(
                    items = metroNews,
                    key = { "${it.id}_${it.title.hashCode()}_${it.publicationDate}" }
                ) { newsItem ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedNewsItem = newsItem },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorder,
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkMode) 0.dp else 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Newspaper,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.metro_news_desc),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_news_badge),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    val displayTime = parseTimeAgo(newsItem.publicationDate, appLanguage, isUpdated = false)
                                    if (!displayTime.isNullOrBlank()) {
                                        Text(
                                            text = displayTime,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (newsItem.title.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = newsItem.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (newsItem.description.isNotBlank() && !newsItem.description.equals(newsItem.title, ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = newsItem.description,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_read_full_news),
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
                }
            }
        }
    }

    MetroNewsDetailBottomSheet(
        newsItem = selectedNewsItem,
        appLanguage = appLanguage,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismiss = { selectedNewsItem = null }
    )

    if (selectedSpecialNotice != null) {
        MetroSpecialNoticeDetailBottomSheet(
            notice = selectedSpecialNotice!!,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { selectedSpecialNotice = null }
        )
    }
}

@Composable
private fun MetroAlertSectionHeader(
    title: String,
    count: Int,
    badgeColor: Color? = null,
    topPadding: androidx.compose.ui.unit.Dp = 4.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetroSpecialNoticeDetailBottomSheet(
    notice: MetroNotice,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val catEnum = remember(notice.category) { MetroNoticeCategory.fromRaw(notice.category) }
    val badgeColor = catEnum.getColor(isDarkMode)
    val formattedTime = remember(notice.publicationDate, appLanguage) {
        parseTimeAgo(notice.publicationDate, appLanguage, isUpdated = false) ?: notice.publicationDate
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .statusBarsPadding()
            .testTag("metro_notice_detail_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.14f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = catEnum.icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = catEnum.getDisplayName(appLanguage),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            if (!notice.lineasAfectadas.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                MetroLineBadgesRow(lineasStr = notice.lineasAfectadas)
            }

            if (notice.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = notice.title,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }

            if (!formattedTime.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (notice.description.isNotBlank() && !notice.description.equals(notice.title, ignoreCase = true)) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                LinkifiedText(
                    text = notice.description,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}
