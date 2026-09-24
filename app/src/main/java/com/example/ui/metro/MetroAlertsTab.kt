package com.example.ui.metro

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.data.model.ValenciaMetroData
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.appCardBorder
import com.example.util.AccessibilityNoticeFormatter.cleanAccessibilityNoticeText
import com.example.util.AccessibilityNoticeFormatter.deduplicateAccessibilityIncidents

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
        metroViewModel.fetchAllAlerts()
    }

    val activeIncidents by metroViewModel.activeIncidents.collectAsState()
    val specialNotices by metroViewModel.specialNotices.collectAsState()
    val accessibilityIncidents by metroViewModel.accessibilityIncidents.collectAsState()
    val metroNews by metroViewModel.metroNews.collectAsState()
    val isMetroAlertsLoading by metroViewModel.isMetroAlertsLoading.collectAsState()
    val isNewsLoading by metroViewModel.isNewsLoading.collectAsState()
    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isOnline by remember { com.example.util.observeNetworkConnectivity(context) }
        .collectAsState(initial = com.example.util.isNetworkAvailable(context))

    var isAccessibilityExpanded by remember { mutableStateOf(false) }
    var selectedNewsItem by remember { mutableStateOf<MetroNewsItem?>(null) }

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
        groups.map { (key, list) ->
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

    val groupedIncidents: Map<GroupedStation, List<AccessibilityIncident>> = remember(accessibilityIncidents, allNetworkStations) {
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
                val fallbackName = if (appLanguage == AppLanguage.CA) "Estació de Metro" else "Estación de Metro"
                GroupedStation(fallbackName, null)
            }
            map.getOrPut(key) { mutableListOf() }.add(incident)
        }
        val resultMap = mutableMapOf<GroupedStation, List<AccessibilityIncident>>()
        for ((station, list) in map) {
            val deduped = deduplicateAccessibilityIncidents(list, station.name)
            if (deduped.isNotEmpty()) {
                resultMap[station] = deduped
            }
        }
        resultMap
    }

    val totalUniqueAccessibilityIncidents: Int = remember(groupedIncidents) {
        groupedIncidents.values.sumOf { it.size }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("avisos_tab_list"),
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
                color = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                                        "No s'han pogut sincronitzar les incidències en directe de Metrovalencia. Comprova la teua connexió."
                                        else "No se han podido sincronizar las incidencias en directo de Metrovalencia. Comprueba tu conexión a internet.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    text = if (appLanguage == AppLanguage.CA) "Totes les línies de Metrovalencia estan operant amb normalitat." else "Todas las líneas de Metrovalencia están operando con normalidad.",
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDarkMode) 0.35f else 0.18f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = if (appLanguage == AppLanguage.CA) "Avís" else "Aviso",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            if (incident.lineasFgv.isNotEmpty()) {
                                MetroLineBadgesRow(lineasStr = incident.lineasFgv.joinToString(", "))
                            } else {
                                val lineLabel = if (appLanguage == AppLanguage.CA) "Incidència activa" else "Incidencia activa"
                                Text(
                                    text = lineLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val displayTime = parseTimeAgo(incident.updatedAt, appLanguage, isUpdated = true)
                        if (!displayTime.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = displayTime,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

        if (isMetroAlertsLoading && sortedSpecialNotices.isEmpty()) {
            item {
                SkeletonCardItem(modifier = Modifier.fillMaxWidth())
            }
        } else if (sortedSpecialNotices.isEmpty()) {
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
                items = sortedSpecialNotices,
                key = { "${it.category}_${it.id}_${it.title.hashCode()}_${it.publicationDate}" }
            ) { notice ->
                val catEnum = remember(notice.category) { MetroNoticeCategory.fromRaw(notice.category) }
                val badgeCategoryName = catEnum.getDisplayName(appLanguage)
                val badgeColor = catEnum.getColor(isDarkMode)
                val badgeIcon = catEnum.icon

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

                            val displayTime = parseTimeAgo(notice.publicationDate, appLanguage, isUpdated = false)
                            if (!displayTime.isNullOrBlank()) {
                                Text(
                                    text = displayTime,
                                    fontSize = 11.sp,
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
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
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
                    .testTag("accessibility_header_toggle"),
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
                            color = if (totalUniqueAccessibilityIncidents > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            contentColor = if (totalUniqueAccessibilityIncidents > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = totalUniqueAccessibilityIncidents.toString(),
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
            if (groupedIncidents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
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
                                    text = if (appLanguage == AppLanguage.CA) "No s'han detectat problemes en escales mecàniques o ascensors." else "No se han detectado problemas en escaleras mecánicas o ascensores.",
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
                                .then(
                                    if (isClickable) {
                                        Modifier.clickable { onNavigateToMetroStation(station.id!!) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("accessibility_station_card_${station.name.lowercase().replace(" ", "_")}"),
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
                                        text = station.name.uppercase(),
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
                                
                                incidentsList.forEachIndexed { index, incident ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    
                                    if (!incident.lineasAfectadas.isNullOrBlank()) {
                                        MetroLineBadgesRow(lineasStr = incident.lineasAfectadas)
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    val rawText = when (appLanguage) {
                                        AppLanguage.CA -> incident.descripcionCa.ifBlank { incident.descripcionEs.ifBlank { incident.tituloCa.ifBlank { incident.tituloEs } } }
                                        else -> incident.descripcionEs.ifBlank { incident.tituloEs }
                                    }
                                    val cleanText = cleanAccessibilityNoticeText(rawText, station.name)
                                    if (cleanText.isNotBlank()) {
                                        Text(
                                            text = cleanText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }

                                    val displayTime = parseTimeAgo(incident.creadoEl, appLanguage, isUpdated = false)
                                    if (!displayTime.isNullOrBlank()) {
                                        Text(
                                            text = displayTime,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(top = 4.dp)
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
            Text(
                text = if (appLanguage == AppLanguage.CA) "NOTÍCIES DE METROVALENCIA" else "NOTICIAS DE METROVALENCIA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
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
                    text = if (appLanguage == AppLanguage.CA) "No hi ha notícies disponibles en aquest moment." else "No hay noticias disponibles en este momento.",
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
                        .clickable {
                            selectedNewsItem = newsItem
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF232630) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Newspaper,
                                contentDescription = "Noticias",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
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
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (appLanguage == AppLanguage.CA) "NOTÍCIA" else "NOTICIA",
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
                                    text = if (appLanguage == AppLanguage.CA) "Llegir la notícia completa" else "Leer noticia completa",
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

    MetroNewsDetailBottomSheet(
        newsItem = selectedNewsItem,
        appLanguage = appLanguage,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismiss = { selectedNewsItem = null }
    )
}

