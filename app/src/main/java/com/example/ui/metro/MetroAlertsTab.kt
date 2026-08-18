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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.data.model.ValenciaMetroData
import com.example.ui.components.SkeletonCardItem
import com.example.ui.theme.appCardBorder
import com.example.ui.dashboard.AppLanguage

@Composable
fun AvisosTab(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    isDarkMode: Boolean,
    onNavigateToMetroStation: (String) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        metroViewModel.fetchAllAlerts()
    }

    val activeIncidents by metroViewModel.activeIncidents.collectAsState()
    val isMetroAlertsLoading by metroViewModel.isMetroAlertsLoading.collectAsState()
    val accessibilityIncidents by metroViewModel.accessibilityIncidents.collectAsState()
    val twitterIncidents by metroViewModel.twitterIncidents.collectAsState()
    val twitterLoading by metroViewModel.twitterLoading.collectAsState()
    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    var isAccessibilityExpanded by remember { mutableStateOf(false) }

    data class MergedActiveIncident(
        val id: String,
        val descriptionEs: String,
        val descriptionCa: String,
        val descriptionEn: String,
        val lineasFgv: List<String>,
        val updatedAt: String?
    )

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
            // Extract distinct line strings, ignore blank
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

    val groupedIncidents = remember(accessibilityIncidents, allNetworkStations) {
        val map = mutableMapOf<GroupedStation, MutableList<AccessibilityIncident>>()
        for (incident in accessibilityIncidents) {
            val station = allNetworkStations.find { it.id == incident.estacionId?.toString() }
            val key = if (station != null) {
                GroupedStation(station.name, station.id)
            } else {
                val fallbackName = if (appLanguage == AppLanguage.CA) "Estació de Metro" else "Estación de Metro"
                GroupedStation(fallbackName, null)
            }
            map.getOrPut(key) { mutableListOf() }.add(incident)
        }
        map
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("avisos_tab_list"),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkMode) 0.2f else 0.1f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
        } else {
            items(groupedActiveIncidents) { incident ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDarkMode) 0.4f else 0.2f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
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
                                incident.lineasFgv.forEach { rawLine ->
                                    val lineId = if (rawLine.startsWith("L", ignoreCase = true)) {
                                        rawLine
                                    } else {
                                        "L$rawLine"
                                    }

                                    val metroLine = remember(lineId) {
                                        ValenciaMetroData.lines.find { it.id.equals(lineId, ignoreCase = true) }
                                    }

                                    if (metroLine != null) {
                                        val lineBgColor = remember(metroLine.colorHex) {
                                            try {
                                                metroLine.colorHex.toColorInt()
                                            } catch (e: Exception) {
                                                0xFF64748B.toInt()
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(lineBgColor), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = metroLine.id,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(MaterialTheme.colorScheme.error, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = lineId,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            } else {
                                val lineLabel = if (appLanguage == AppLanguage.CA) "Avisos actius" else "Avisos activos"
                                Text(
                                    text = lineLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val displayDesc = when (appLanguage) {
                            AppLanguage.CA -> if (incident.descriptionCa.isNotBlank()) incident.descriptionCa else incident.descriptionEs
                            else -> incident.descriptionEs
                        }
                        Text(
                            text = displayDesc,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!incident.updatedAt.isNullOrBlank()) {
                            val relTime = parseTimeAgo(incident.updatedAt, appLanguage)
                            val displayTime = if (relTime.isNotEmpty()) relTime else incident.updatedAt
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.CA) "Actualitzat: $displayTime" else "Actualizado: $displayTime",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // --- SECCIÓN 2: ACCESIBILIDAD ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAccessibilityExpanded = !isAccessibilityExpanded }
                    .testTag("accessibility_header_toggle"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(18.dp),
                border = appCardBorder()
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
                            color = if (accessibilityIncidents.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            contentColor = if (accessibilityIncidents.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = accessibilityIncidents.size.toString(),
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
                        border = appCardBorder(),
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
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            border = appCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 1. ESTACIÓN
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
                                    
                                    // 2. Descripción si la hay
                                    if (incident.tituloEs.isNotBlank()) {
                                        Text(
                                            text = incident.tituloEs,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    
                                    // 3. Qué se ha estropeado
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = if (appLanguage == AppLanguage.CA) "Avís" else "Aviso",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = incident.descripcionEs,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    
                                    if (incident.creadoEl.isNotBlank()) {
                                        val relTime = parseTimeAgo(incident.creadoEl, appLanguage)
                                        val displayTime = if (relTime.isNotEmpty()) relTime else incident.creadoEl
                                        Text(
                                            text = if (appLanguage == AppLanguage.CA) "Publicat: $displayTime" else "Publicado: $displayTime",
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

        // --- SECCIÓN 3: TWEETS DE METROVALENCIA ---
        item {
            Text(
                text = "TWEETS DE METROVALENCIA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        if (twitterLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (twitterIncidents.isEmpty()) {
            item {
                Text(
                    text = if (appLanguage == AppLanguage.CA) "No hi ha tuits d'incidències disponibles." else "No hay tweets de incidencias disponibles.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        } else {
            items(twitterIncidents, key = { it.id }) { tweet ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = appCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Metrovalencia Photo Placeholder
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subway,
                                contentDescription = "Metrovalencia",
                                tint = MaterialTheme.colorScheme.onPrimary,
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
                                Column {
                                    Text(
                                        text = "Metrovalencia",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "@metrovalencia",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                val timeAgoStr = tweet.updatedAt?.let { parseTimeAgo(it, appLanguage) } ?: ""
                                if (timeAgoStr.isNotEmpty()) {
                                    Text(
                                        text = timeAgoStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = tweet.descriptionEs,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
