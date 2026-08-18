package com.example.ui.metro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation

@Composable
fun AddCardDialog(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    onDismiss: () -> Unit
) {
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    var cardNumber by remember { mutableStateOf("") }
    var cardName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = texts.addCardLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
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
                    text = texts.addCardInstruction,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { input ->
                        if (input.length <= 12 && input.all { it.isDigit() }) {
                            cardNumber = input
                        }
                    },
                    label = { Text(texts.cardNumberLabel) },
                    placeholder = { Text(texts.cardNumberPlaceholder) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_card_number_input"),
                    enabled = !isLoading,
                    isError = cardNumber.isNotEmpty() && cardNumber.length != 10 && cardNumber.length != 12
                )

                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it },
                    label = { Text(texts.customNameLabel) },
                    placeholder = { Text(texts.customNamePlaceholder) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_card_name_input"),
                    enabled = !isLoading
                )

                if (cardNumber.isNotEmpty() && cardNumber.length != 10 && cardNumber.length != 12) {
                    Text(
                        text = texts.cardNumberDigitsError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isLoading = true
                    metroViewModel.addTransitCard(
                        cardNumber = cardNumber,
                        customName = if (cardName.isBlank()) null else cardName,
                        onSuccess = {
                            isLoading = false
                            onDismiss()
                        },
                        onError = { error ->
                            isLoading = false
                            errorMessage = error
                            showErrorDialog = true
                        }
                    )
                },
                enabled = (cardNumber.length == 10 || cardNumber.length == 12) && !isLoading,
                modifier = Modifier.testTag("add_card_confirm_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(texts.addBtn)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
                modifier = Modifier.testTag("add_card_cancel_button")
            ) {
                Text(texts.cancelBtn)
            }
        }
    )

    if (showErrorDialog && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(
                    text = texts.alertTitle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(errorMessage!!)
            },
            confirmButton = {
                TextButton(
                    onClick = { showErrorDialog = false },
                    modifier = Modifier.testTag("error_dialog_ok_button")
                ) {
                    Text(texts.acceptBtn)
                }
            }
        )
    }
}
