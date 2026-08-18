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
import androidx.compose.material.icons.filled.Warning
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
                val category = CardCategory.valueOf(card.category)
                val isFaded = card.isFaded
                val (bgColor, contentColor, badgeBgColor) = getCardColors(category = category, isFaded = isFaded, isDarkMode = isDarkMode)

                Card(
                    border = appCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEditingName) editedName else card.assignedName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isFaded) {
                                        if (appLanguage == AppLanguage.CA) "${category.label} (Inactiva)" else "${category.label} (Inactiva)"
                                    } else category.label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .background(badgeBgColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = card.remainingValue,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatCardNumber(card.cardNumber),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

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

                    DetailRow(label = texts.operatorLabel, value = card.operador)
                    DetailRow(label = texts.validityZonesLabel, value = card.zonas)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = texts.historyLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (card.viajesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = texts.historyNotAvailableDesc,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (viaje in card.viajesList) {
                            Card(
                                border = appCardBorder(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = viaje.estacion,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (viaje.zona.isNotEmpty()) {
                                                Text(
                                                    text = viaje.zona,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .background(
                                                            color = when (viaje.zona.uppercase()) {
                                                                "A" -> Color(0xFF1976D2)
                                                                "B" -> Color(0xFF388E3C)
                                                                "C" -> Color(0xFFF57C00)
                                                                "D" -> Color(0xFFD32F2F)
                                                                else -> MaterialTheme.colorScheme.secondary
                                                            },
                                                            shape = RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = viaje.fecha,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (viaje.tipoValidacion.isNotEmpty()) {
                                            val tipoLower = viaje.tipoValidacion.lowercase()
                                            val badgeBg = when {
                                                tipoLower.contains("entrada") -> Color(0xFFE8F5E9)
                                                tipoLower.contains("salida") -> Color(0xFFEEEEEE)
                                                tipoLower.contains("transbordo") -> Color(0xFFE3F2FD)
                                                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                            }
                                            val badgeText = when {
                                                tipoLower.contains("entrada") -> Color(0xFF2E7D32)
                                                tipoLower.contains("salida") -> Color(0xFF616161)
                                                tipoLower.contains("transbordo") -> Color(0xFF1565C0)
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            }
                                            val badgeLabel = when {
                                                tipoLower.contains("entrada") -> if (appLanguage == AppLanguage.CA) "Entrada" else "Entrada"
                                                tipoLower.contains("salida") -> if (appLanguage == AppLanguage.CA) "Eixida" else "Salida"
                                                tipoLower.contains("transbordo") -> if (appLanguage == AppLanguage.CA) "Transbord" else "Transbordo"
                                                else -> viaje.tipoValidacion
                                            }
                                            Text(
                                                text = badgeLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = badgeText,
                                                modifier = Modifier
                                                    .background(badgeBg, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
