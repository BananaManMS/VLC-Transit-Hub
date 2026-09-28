package com.example.ui.cercanias

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LinkifiedText
import com.example.ui.components.SkeletonCardItem
import com.example.ui.dashboard.AppLanguage

@Composable
fun CercaniasAlertsTab(
    activeAlerts: List<CercaniasAlert>,
    generalNotices: List<CercaniasAlert>,
    groupedAccessibilityAlerts: Map<String, List<CercaniasAlert>>,
    isCercaniasAlertsLoading: Boolean,
    isOnline: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    activeTripBottomPadding: Dp = 0.dp
) {
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val cardBg = if (isDarkMode) Color(0xFF222222) else Color(0xFFFFFFFF)

    if (isCercaniasAlertsLoading && activeAlerts.isEmpty() && generalNotices.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(4) {
                SkeletonCardItem(modifier = Modifier.height(80.dp))
            }
        }
    } else if (activeAlerts.isEmpty() && generalNotices.isEmpty() && groupedAccessibilityAlerts.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (appLanguage == AppLanguage.CA) "Sense avisos o incidències actives" else "Sin avisos o incidencias activas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (appLanguage == AppLanguage.CA) "El servici de Renfe Cercanies funciona amb normalitat." else "El servicio de Renfe Cercanías funciona con normalidad.",
                style = MaterialTheme.typography.bodySmall,
                color = subtextColor
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + activeTripBottomPadding)
        ) {
            if (activeAlerts.isNotEmpty()) {
                item {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Avisos de circulació" else "Avisos de circulación",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(activeAlerts, key = { it.id }) { alert ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = alert.headerEs,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = textColor
                                )
                            }
                            if (alert.descriptionEs.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinkifiedText(
                                    text = alert.descriptionEs,
                                    textColor = subtextColor,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            if (generalNotices.isNotEmpty()) {
                item {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Informació general" else "Información general",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(generalNotices, key = { it.id }) { alert ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = alert.headerEs,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = textColor
                                )
                            }
                            if (alert.descriptionEs.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinkifiedText(
                                    text = alert.descriptionEs,
                                    textColor = subtextColor,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
