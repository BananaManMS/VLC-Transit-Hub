package com.example.ui.metro.cards

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.metro.MetroViewModel
import com.example.ui.theme.appCardBorder

enum class AddCardFlowStep {
    SELECT_METHOD,
    MANUAL_INPUT,
    NFC_SCANNING,
    NFC_SUCCESS_PREVIEW
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun AddTransitCardWizardDialog(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    onDismiss: () -> Unit,
    onCardAdded: (TransitCardUiModel) -> Unit = {}
) {
    val context = LocalContext.current
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    var currentStep by remember { mutableStateOf(AddCardFlowStep.SELECT_METHOD) }
    
    val isNfcSupported = remember { NfcCardHelper.isNfcSupported(context) }
    var isNfcEnabled by remember { mutableStateOf(NfcCardHelper.isNfcEnabled(context)) }

    // Estado NFC
    var nfcError by remember { mutableStateOf<String?>(null) }
    var nfcProcessing by remember { mutableStateOf(false) }
    var scannedCardNumber by remember { mutableStateOf("") }
    var fetchedCardModel by remember { mutableStateOf<TransitCardUiModel?>(null) }

    LaunchedEffect(currentStep) {
        if (currentStep == AddCardFlowStep.NFC_SCANNING) {
            isNfcEnabled = NfcCardHelper.isNfcEnabled(context)
        }
    }

    when (currentStep) {
        AddCardFlowStep.SELECT_METHOD -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = texts.addCardMethodTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = texts.addCardMethodDesc,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Opción 1: NFC
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isNfcEnabled = NfcCardHelper.isNfcEnabled(context)
                                    currentStep = AddCardFlowStep.NFC_SCANNING
                                }
                                .testTag("select_nfc_method_button"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Nfc,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = texts.addCardNfcOption,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = texts.addCardNfcDesc,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Opción 2: Manual
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    currentStep = AddCardFlowStep.MANUAL_INPUT
                                }
                                .testTag("select_manual_method_button"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = texts.addCardManualOption,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = texts.addCardManualDesc,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("method_dialog_cancel_button")
                    ) {
                        Text(texts.cancelBtn)
                    }
                }
            )
        }

        AddCardFlowStep.MANUAL_INPUT -> {
            com.example.ui.metro.AddCardDialog(
                appLanguage = appLanguage,
                metroViewModel = metroViewModel,
                onDismiss = onDismiss
            )
        }

        AddCardFlowStep.NFC_SCANNING -> {
            val activity = remember(context) { context.findActivity() }
            
            DisposableEffect(isNfcEnabled) {
                if (activity != null && isNfcSupported && isNfcEnabled) {
                    NfcCardHelper.startListening(
                        activity = activity,
                        onTagDetected = { cardNumber ->
                            scannedCardNumber = cardNumber
                            nfcProcessing = true
                            nfcError = null
                            metroViewModel.addTransitCard(
                                cardNumber = cardNumber,
                                customName = null,
                                onSuccess = {
                                    nfcProcessing = false
                                    // Buscar la tarjeta recién añadida para la previsualización
                                    val newlyAdded = metroViewModel.transitCardsFlow.value.find { it.cardNumber == cardNumber }
                                    if (newlyAdded != null) {
                                        fetchedCardModel = newlyAdded
                                        onCardAdded(newlyAdded)
                                    }
                                    currentStep = AddCardFlowStep.NFC_SUCCESS_PREVIEW
                                },
                                onError = { err ->
                                    nfcProcessing = false
                                    nfcError = err
                                }
                            )
                        },
                        onError = { err ->
                            nfcError = err
                        }
                    )
                }
                onDispose {
                    if (activity != null) {
                        NfcCardHelper.stopListening(activity)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = {
                    if (!nfcProcessing) onDismiss()
                },
                title = {
                    Text(
                        text = texts.nfcScanningTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!isNfcSupported) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = texts.nfcNotSupported,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (!isNfcEnabled) {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = texts.nfcDisabled,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_open_nfc_settings))
                            }
                        } else if (nfcProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = texts.nfcReadingCard,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (scannedCardNumber.isNotEmpty()) {
                                Text(
                                    text = "Nº: $scannedCardNumber",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Nfc,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = texts.nfcScanningDesc,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (nfcError != null && !nfcProcessing) {
                            Text(
                                text = nfcError!!,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!isNfcSupported || !isNfcEnabled) {
                        Button(
                            onClick = { currentStep = AddCardFlowStep.MANUAL_INPUT }
                        ) {
                            Text(texts.addCardManualOption)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (currentStep == AddCardFlowStep.NFC_SCANNING) {
                                currentStep = AddCardFlowStep.SELECT_METHOD
                            } else {
                                onDismiss()
                            }
                        },
                        enabled = !nfcProcessing
                    ) {
                        Text(texts.cancelBtn)
                    }
                }
            )
        }

        AddCardFlowStep.NFC_SUCCESS_PREVIEW -> {
            val card = fetchedCardModel ?: metroViewModel.transitCardsFlow.value.find { it.cardNumber == scannedCardNumber }
            
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = texts.nfcSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (card != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = card.assignedName.ifBlank { card.defaultName },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Nº ${card.cardNumber}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.R.string.card_balance_trips_label),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = card.remainingValue,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Nº: $scannedCardNumber",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("nfc_preview_accept_button")
                    ) {
                        Text(texts.acceptBtn)
                    }
                }
            )
        }
    }
}
