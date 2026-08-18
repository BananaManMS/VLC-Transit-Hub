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
                text = if (appLanguage == AppLanguage.CA) "Nom personalitzat" else "Nombre personalizado",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (appLanguage == AppLanguage.CA)
                        "Assigna un nom per identificar la Parada ${stopToEdit.id_parada} més fàcilment:"
                    else "Asigna un nombre para identificar la Parada ${stopToEdit.id_parada} más fácilmente:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { if (it.length <= 32) aliasInput = it },
                    label = { Text(if (appLanguage == AppLanguage.CA) "Nom/Alias (màx. 32 lletres)" else "Nombre/Alias (máx. 32 letras)") },
                    placeholder = { Text("Ej: Casa, Trabajo, Universidad...") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "${aliasInput.length}/32 ${if (appLanguage == AppLanguage.CA) "lletres" else "letras"}",
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
                Text(if (appLanguage == AppLanguage.CA) "Desar" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(if (appLanguage == AppLanguage.CA) "Cancel·lar" else "Cancelar")
            }
        }
    )
}
