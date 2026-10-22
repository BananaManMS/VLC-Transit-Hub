package com.example.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage

val STAR_PALETTE = listOf(
    "#F59E0B", // Ámbar / Dorado (Clásico)
    "#EF4444", // Rojo Coral
    "#F97316", // Naranja
    "#10B981", // Verde Esmeralda
    "#06B6D4", // Turquesa / Cyan
    "#0284C7", // Azul Océano
    "#6366F1", // Índigo
    "#8B5CF6", // Violeta
    "#EC4899"  // Rosa
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveFavoriteDialog(
    initialAlias: String,
    initialShowOnMap: Boolean = true,
    initialColorHex: String = "#F59E0B",
    appLanguage: AppLanguage,
    onSave: (alias: String, showOnMap: Boolean, colorHex: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var aliasInput by remember(initialAlias) { mutableStateOf(initialAlias) }
    var showOnMap by remember(initialShowOnMap) { mutableStateOf(initialShowOnMap) }
    var selectedColorHex by remember(initialColorHex) {
        mutableStateOf(if (initialColorHex in STAR_PALETTE) initialColorHex else "#F59E0B")
    }

    val isValencian = appLanguage == AppLanguage.CA
    val parsedSelectedColor = remember(selectedColorHex) {
        try {
            Color(android.graphics.Color.parseColor(selectedColorHex))
        } catch (_: Exception) {
            Color(0xFFF59E0B)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = parsedSelectedColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (onDelete != null) {
                        if (isValencian) "Editar lloc preferit" else "Editar lugar favorito"
                    } else {
                        if (isValencian) "Desar lloc preferit" else "Guardar lugar favorito"
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name / Alias Text Field
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { if (it.length <= 32) aliasInput = it },
                    label = { Text(if (isValencian) "Nom / Àlies" else "Nombre / Alias") },
                    placeholder = { Text(if (isValencian) "Ex: Gimnàs, Feina..." else "Ej: Gimnasio, Trabajo...") },
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

                // Star Color Selector Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isValencian) "Color de l'estrella" else "Color de la estrella",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        STAR_PALETTE.forEach { colorHex ->
                            val isSelected = selectedColorHex.equals(colorHex, ignoreCase = true)
                            val itemColor = remember(colorHex) {
                                try {
                                    Color(android.graphics.Color.parseColor(colorHex))
                                } catch (_: Exception) {
                                    Color(0xFFF59E0B)
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(itemColor)
                                    .clickable { selectedColorHex = colorHex }
                                    .testTag("color_picker_${colorHex.replace("#", "")}")
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Show on Map Toggle Row
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showOnMap = !showOnMap }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (showOnMap) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (showOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isValencian) "Mostrar al mapa" else "Mostrar en el mapa",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (showOnMap) {
                                    if (isValencian) "La icona serà visible sobre el mapa" else "El icono será visible sobre el mapa"
                                } else {
                                    if (isValencian) "Desat com a preferit sense icona al mapa" else "Guardado como favorito sin icono en el mapa"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = showOnMap,
                            onCheckedChange = { showOnMap = it },
                            modifier = Modifier.testTag("save_favorite_show_on_map_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (aliasInput.isNotBlank()) {
                        onSave(aliasInput.trim(), showOnMap, selectedColorHex)
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
