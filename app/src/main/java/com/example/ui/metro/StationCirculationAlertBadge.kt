package com.example.ui.metro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.dashboard.AppLanguage
import com.example.util.LineColorResolver

@Composable
fun StationCirculationAlertBadge(
    stationName: String,
    affectedLines: List<String>,
    incidents: List<MetroIncident>,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (affectedLines.isEmpty()) return

    var showDialog by remember { mutableStateOf(false) }

    val redColor = if (isDarkMode) Color(0xFFF87171) else Color(0xFFDC2626)
    val activeBg = redColor.copy(alpha = 0.16f)
    val activeBorder = redColor.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .wrapContentSize()
            .testTag("station_circulation_alert_badge")
    ) {
        Surface(
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { showDialog = true },
            shape = RoundedCornerShape(6.dp),
            color = activeBg,
            border = BorderStroke(1.dp, activeBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.metro_circulation_alerts_title),
                    tint = redColor,
                    modifier = Modifier.size(15.dp)
                )

                affectedLines.forEach { lineId ->
                    val cleanLine = lineId.replace("L", "", ignoreCase = true).trim()
                    val lineColor = LineColorResolver.getMetroLineColor("L$cleanLine")
                    Box(
                        modifier = Modifier
                            .height(17.dp)
                            .widthIn(min = 17.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(lineColor)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cleanLine,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }
        }

        if (showDialog && incidents.isNotEmpty()) {
            StationCirculationIncidentsDialog(
                stationName = stationName,
                incidents = incidents,
                appLanguage = appLanguage,
                isDarkMode = isDarkMode,
                onDismiss = { showDialog = false }
            )
        }
    }
}

@Composable
fun StationCirculationIncidentsDialog(
    stationName: String,
    incidents: List<MetroIncident>,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val redColor = if (isDarkMode) Color(0xFFF87171) else Color(0xFFDC2626)
    val cardBg = if (isDarkMode) Color(0xFF1B2232) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF4B5563)
    val borderCol = redColor.copy(alpha = 0.5f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(22.dp))
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(22.dp))
                .testTag("station_circulation_incidents_dialog"),
            color = cardBg,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, borderCol),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabecera del diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = redColor.copy(alpha = 0.16f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = redColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stationName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1
                            )
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.metro_circulation_alerts_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = redColor
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_close_circulation_incidents_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.btn_close),
                            tint = subtextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE5E7EB))

                // Lista de avisos con scroll si son varios
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    incidents.forEach { incident ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkMode) Color(0xFF221717) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!incident.lineaFgv.isNullOrBlank()) {
                                    MetroLineBadgesRow(
                                        lineasStr = incident.lineaFgv,
                                        badgeSize = 18.dp,
                                        fontSize = 10.sp
                                    )
                                }

                                val displayDesc = when (appLanguage) {
                                    AppLanguage.CA -> incident.descriptionCa.ifEmpty { incident.descriptionEs }
                                    else -> incident.descriptionEs
                                }
                                if (displayDesc.isNotBlank()) {
                                    Text(
                                        text = displayDesc,
                                        fontSize = 13.sp,
                                        color = textColor,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Botón de cierre
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = redColor
                    )
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.card_alert_understood),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
