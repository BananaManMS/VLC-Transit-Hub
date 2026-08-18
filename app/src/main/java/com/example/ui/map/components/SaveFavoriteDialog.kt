package com.example.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.example.ui.dashboard.AppLanguage

@Composable
fun SaveFavoriteDialog(
    initialAlias: String,
    appLanguage: AppLanguage,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var aliasInput by remember(initialAlias) { mutableStateOf(initialAlias) }
    val isValencian = appLanguage == AppLanguage.CA

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isValencian) "Desar lloc preferit" else "Guardar lugar favorito",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (isValencian)
                        "Com vols anomenar aquest lloc?"
                    else "¿Cómo quieres llamar a este lugar?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { if (it.length <= 32) aliasInput = it },
                    label = { Text(if (isValencian) "Nom / Àlies" else "Nombre / Alias") },
                    placeholder = { Text("Ej: Oficina de papá, Gimnasio Mestalla") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "${aliasInput.length}/32",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_favorite_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (aliasInput.isNotBlank()) {
                        onSave(aliasInput.trim())
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_save_favorite_button"),
                enabled = aliasInput.isNotBlank()
            ) {
                Text(if (isValencian) "Desar" else "Guardar")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("delete_favorite_button")
                    ) {
                        Text(
                            text = if (isValencian) "Eliminar" else "Eliminar",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_save_favorite_button")
                ) {
                    Text(if (isValencian) "Cancel·lar" else "Cancelar")
                }
            }
        }
    )
}
