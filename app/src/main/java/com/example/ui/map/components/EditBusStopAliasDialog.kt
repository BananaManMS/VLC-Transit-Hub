package com.example.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.database.GeoportalStopEntity
import com.example.ui.dashboard.AppLanguage

@Composable
fun EditBusStopAliasDialog(
    stopToEdit: GeoportalStopEntity,
    currentAlias: String,
    appLanguage: AppLanguage,
    onSaveAlias: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var aliasInput by remember(stopToEdit, currentAlias) { mutableStateOf(currentAlias) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_desc_format, stopToEdit.id_parada),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { if (it.length <= 32) aliasInput = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_field_label)) },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.alias_placeholder)) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.alias_dialog_length_format, aliasInput.length),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alias_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAlias(aliasInput)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_alias_button")
            ) {
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_cancel))
            }
        }
    )
}
