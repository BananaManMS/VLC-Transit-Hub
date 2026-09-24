package com.example.ui.metro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import com.example.ui.metro.cards.CardDisplayFormat
import com.example.ui.metro.cards.TransitCardAlertManager
import com.example.ui.metro.cards.UnifiedTransitCardView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.theme.appCardBorder

@Composable
fun CardDetailDialog(
    card: TransitCardUiModel,
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(card.assignedName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var isManuallyInactive by remember(card) {
        mutableStateOf(card.isManuallyInactive)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = texts.cardDetailTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row {
                    IconButton(
                        onClick = {
                            if (isEditingName) {
                                metroViewModel.updateTransitCardName(card.cardNumber, editedName)
                                metroViewModel.updateTransitCardManualStatus(card.cardNumber, isManuallyInactive)
                            }
                            isEditingName = !isEditingName
                        },
                        modifier = Modifier.size(36.dp).testTag("edit_card_name_btn")
                    ) {
                        Icon(
                            imageVector = if (isEditingName) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditingName) texts.saveBtnDesc else texts.editNameAssignedLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp).testTag("delete_card_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = texts.deleteCardBtnDesc,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val cardToDisplay = if (isEditingName) card.copy(assignedName = editedName) else card
                UnifiedTransitCardView(
                    card = cardToDisplay,
                    appLanguage = appLanguage,
                    format = CardDisplayFormat.HERO,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEditingName) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text(texts.editNameAssignedLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_card_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isManuallyInactive = !isManuallyInactive }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = texts.markAsInactiveLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = texts.markAsInactiveDesc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isManuallyInactive,
                            onCheckedChange = { isManuallyInactive = it },
                            modifier = Modifier.testTag("inactive_switch")
                        )
                    }
                    val context = LocalContext.current
                    var isAlertMuted by remember(card) {
                        mutableStateOf(TransitCardAlertManager.isCardMuted(context, card.cardNumber))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val newMuted = !isAlertMuted
                                isAlertMuted = newMuted
                                TransitCardAlertManager.setCardMuted(context, card.cardNumber, newMuted)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Avisos de saldo / caducitat" else "Avisos de saldo / caducidad",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Avisar quan queden pocs viatges o saldo" else "Avisar cuando queden pocos viajes o saldo",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = !isAlertMuted,
                            onCheckedChange = { isChecked ->
                                isAlertMuted = !isChecked
                                TransitCardAlertManager.setCardMuted(context, card.cardNumber, !isChecked)
                            },
                            modifier = Modifier.testTag("alerts_switch")
                        )
                    }

                    var showOnHomeState by remember(card) {
                        mutableStateOf(card.showOnHome)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val newValue = !showOnHomeState
                                showOnHomeState = newValue
                                metroViewModel.updateCardHomeVisibility(card.cardNumber, newValue)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Mostrar a l'inici" else "Mostrar en inicio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "Mostrar aquesta targeta a la pantalla principal" else "Mostrar esta tarjeta en la pantalla de inicio",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = showOnHomeState,
                            onCheckedChange = { isChecked ->
                                showOnHomeState = isChecked
                                metroViewModel.updateCardHomeVisibility(card.cardNumber, isChecked)
                            },
                            modifier = Modifier.testTag("home_visibility_switch")
                        )
                    }
                }

                Text(
                    text = texts.detailInfoLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val category = try { CardCategory.valueOf(card.category) } catch (_: Exception) { CardCategory.OTHER }
                    val isMonthly = category == CardCategory.SUMA_MENSUAL
                    val isTuiN = category == CardCategory.TUIN

                    DetailRow(label = texts.transportTitleLabel, value = card.title)
                    DetailRow(label = texts.cardClassLabel, value = card.clase)
                    DetailRow(label = texts.extensionLabel, value = card.ampliado)

                    // A) CORRECCIÓN DE FECHA (Última Operación vs Caducidad)
                    if (isMonthly) {
                        DetailRow(label = texts.expiryDateLabel, value = card.fechaCaducidad)
                    } else {
                        DetailRow(label = texts.lastTopupLabel, value = card.fechaRecarga.ifEmpty { texts.notAvailableValue })
                    }

                    // B) CORRECCIÓN DE SALDO Y VIAJES RESTANTES
                    if (isTuiN) {
                        DetailRow(label = texts.remainingBalanceLabel, value = card.remainingValue)
                    } else if (isMonthly) {
                        DetailRow(label = texts.remainingTripsLabel, value = texts.unlimitedTripsValue)
                    } else {
                        DetailRow(label = texts.remainingTripsLabel, value = card.remainingValue)
                    }

                    DetailRow(label = texts.validityZonesLabel, value = card.zonas)
                }

                Spacer(modifier = Modifier.height(16.dp))

                CardDetailTripHistory(
                    viajesList = card.viajesList,
                    appLanguage = appLanguage,
                    texts = texts
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isEditingName) {
                        metroViewModel.updateTransitCardName(card.cardNumber, editedName)
                        metroViewModel.updateTransitCardManualStatus(card.cardNumber, isManuallyInactive)
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("detail_dialog_close_btn")
            ) {
                Text(if (isEditingName) texts.saveAndCloseBtn else texts.closeBtn)
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = texts.deleteCardConfirmTitle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(texts.deleteCardConfirmDesc)
            },
            confirmButton = {
                Button(
                    onClick = {
                        metroViewModel.deleteTransitCard(card.cardNumber)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_card_confirm_btn")
                ) {
                    Text(texts.deleteBtn)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.testTag("delete_card_cancel_btn")
                ) {
                    Text(texts.cancelBtn)
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
