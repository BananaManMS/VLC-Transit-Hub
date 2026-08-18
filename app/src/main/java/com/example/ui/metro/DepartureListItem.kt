package com.example.ui.metro

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation
import com.example.ui.theme.UnifiedAppCard
import androidx.compose.material.icons.filled.RssFeed
import kotlinx.coroutines.delay

@Composable
fun DepartureListItem(
    departure: RealTimeDeparture,
    metroViewModel: MetroViewModel,
    appLanguage: AppLanguage,
    texts: Translation,
    isDarkMode: Boolean,
    onExpired: (String) -> Unit
) {
                            var secondsRemaining by remember(departure.id, departure.secondsRemaining) { mutableIntStateOf(departure.secondsRemaining) }
                            LaunchedEffect(departure.id, departure.secondsRemaining) {
                                secondsRemaining = departure.secondsRemaining
                                while (secondsRemaining > -10) {
                                    delay(1000)
                                    secondsRemaining--
                                }
                                onExpired(departure.id)
                            }
                            
                            val uiModel = remember(departure, secondsRemaining, appLanguage, texts, isDarkMode) {
                                MetroMapper.toDepartureUiModel(
                                    departure,
                                    secondsRemaining,
                                    appLanguage,
                                    texts,
                                    isDarkMode
                                ) { digit -> metroViewModel.getSharedLineDigits(digit) }
                            }
                            
                            val cardTextColor = MaterialTheme.colorScheme.onSurface

                            UnifiedAppCard(
                                modifier = Modifier
                                    .testTag("departure_card_${departure.lineId}_${departure.destination}"),
                                onClick = {
                                    metroViewModel.selectDepartureDetails(departure)
                                },
                                startContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(departure.colorHex))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = departure.lineId,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                },
                                centerContent = {
                                    Column {
                                        Text(
                                            text = departure.destination,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = cardTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val lineIncidents = metroViewModel.getIncidentsForLine(departure.lineId)
                                        val directKeys = lineIncidents.flatMap { incident ->
                                            listOfNotNull(
                                                incident.id.trim().ifEmpty { null },
                                                incident.descriptionEs.trim().ifEmpty { null }
                                            )
                                        }.toSet()

                                        val affectedSharedLines = uiModel.sharedDigits.filter { sharedDigit ->
                                            val sharedIncidents = metroViewModel.getIncidentsForLine("L$sharedDigit")
                                            sharedIncidents.any { incident ->
                                                val idKey = incident.id.trim()
                                                val descKey = incident.descriptionEs.trim()
                                                val matchesDirect = (idKey.isNotEmpty() && directKeys.contains(idKey)) ||
                                                        (descKey.isNotEmpty() && directKeys.contains(descKey))
                                                !matchesDirect
                                            }
                                        }.map { "L$it" }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        if (lineIncidents.isNotEmpty()) {
                                            val text = if (lineIncidents.size == 1) {
                                                if (appLanguage == AppLanguage.CA) "1 avís" else "1 aviso"
                                            } else {
                                                if (appLanguage == AppLanguage.CA) "${lineIncidents.size} avisos" else "${lineIncidents.size} avisos"
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = text,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        } else if (affectedSharedLines.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val sharedLinesStr = affectedSharedLines.joinToString(", ")
                                                val text = if (appLanguage == AppLanguage.CA) "Possible afectació ($sharedLinesStr)" else "Posible afectación ($sharedLinesStr)"
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF9800),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = text,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFFF9800),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val text = if (appLanguage == AppLanguage.CA) "Sense avisos" else "Sin avisos"
                                                Text(
                                                    text = text,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                },
                                endContent = {
                                    val timeColor = when {
                                        uiModel.isWarningColor -> MaterialTheme.colorScheme.error
                                        uiModel.isSecondaryColor -> MaterialTheme.colorScheme.secondary
                                        else -> if (isDarkMode) Color.White else Color.Black
                                    }
                                    
                                    val infiniteTransition = rememberInfiniteTransition(label = "timer_blink")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.2f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(durationMillis = 800, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "alpha_anim"
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = uiModel.timeAnnotated,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = timeColor,
                                            textAlign = TextAlign.End,
                                            modifier = if (uiModel.shouldBlink) Modifier.graphicsLayer(alpha = alpha) else Modifier
                                        )
                                        Icon(
                                            imageVector = Icons.Default.RssFeed,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier
                                                .size(16.dp)
                                        )
                                    }
                                }
                            )
}
