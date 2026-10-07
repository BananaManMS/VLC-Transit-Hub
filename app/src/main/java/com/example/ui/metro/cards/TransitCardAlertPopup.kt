package com.example.ui.metro.cards

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage

@Composable
fun TransitCardAlertPopup(
    alerts: List<TransitCardAlert>,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onSelectCard: ((String) -> Unit)? = null
) {
    if (alerts.isEmpty()) return

    val context = LocalContext.current
    val isCa = appLanguage == AppLanguage.CA
    val mutedMap = remember { mutableStateMapOf<String, Boolean>() }

    val isSingleAlert = alerts.size == 1
    val headerTitle = if (isSingleAlert) {
        androidx.compose.ui.res.stringResource(com.example.R.string.card_alert_single_header)
    } else {
        androidx.compose.ui.res.stringResource(com.example.R.string.card_alert_multi_header, alerts.size)
    }

    AlertDialog(
        onDismissRequest = {
            TransitCardAlertManager.markAlertsAsSeen(context, alerts)
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                alerts.forEach { alert ->
                    val isMuted = mutedMap[alert.cardNumber] == true
                    CardAlertItem(
                        alert = alert,
                        isMuted = isMuted,
                        isCa = isCa,
                        onToggleMute = {
                            val newMuted = !isMuted
                            mutedMap[alert.cardNumber] = newMuted
                            TransitCardAlertManager.setCardMuted(context, alert.cardNumber, newMuted)
                        },
                        onClick = {
                            if (onSelectCard != null) {
                                TransitCardAlertManager.markAlertsAsSeen(context, alerts)
                                onDismiss()
                                onSelectCard(alert.cardNumber)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    TransitCardAlertManager.markAlertsAsSeen(context, alerts)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("card_alert_confirm_btn")
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.card_alert_understood),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun CardAlertItem(
    alert: TransitCardAlert,
    isMuted: Boolean,
    isCa: Boolean,
    onToggleMute: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isMuted, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMuted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else if (alert.isCritical) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = alert.cardName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Badge de aviso
                if (!isMuted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (alert.isCritical) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = alert.titleText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (alert.isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isMuted) {
                    androidx.compose.ui.res.stringResource(com.example.R.string.cards_alert_muted_subtitle)
                } else {
                    alert.subtitleText
                },
                fontSize = 12.5.sp,
                color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Acción para no volver a avisar de esta tarjeta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleMute,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMuted) {
                            androidx.compose.ui.res.stringResource(com.example.R.string.cards_alert_unmute_btn)
                        } else {
                            androidx.compose.ui.res.stringResource(com.example.R.string.cards_alert_mute_btn)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
