package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AppColorPreset
import com.example.ui.theme.AppDialogDefaults
import com.example.ui.theme.ColorLabCatalog
import com.example.ui.theme.ColorLabConfig
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.appDialogWidth
import com.example.ui.theme.buildAppColorScheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorLabDialog(
    config: ColorLabConfig,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onSelectPreset: (AppColorPreset) -> Unit,
    onUpdateConfig: (ColorLabConfig) -> Unit,
    onToggleDarkMode: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEs = appLanguage == AppLanguage.ES

    Dialog(
        onDismissRequest = onDismiss,
        properties = AppDialogDefaults.Properties
    ) {
        Card(
            shape = AppDialogDefaults.Shape,
            colors = CardDefaults.cardColors(containerColor = AppDialogDefaults.containerColor()),
            border = AppDialogDefaults.border(),
            modifier = Modifier
                .appDialogWidth()
                .heightIn(max = 740.dp)
                .padding(vertical = 16.dp)
                .testTag("color_lab_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isEs) "Personalizar app" else "Personalitzar app",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isEs) "Elige los colores de la interfaz" else "Tria els colors de la interfície",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleDarkMode,
                            modifier = Modifier.testTag("color_lab_toggle_dark")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isEs) "Alternar modo oscuro" else "Alternar mode fosc",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("color_lab_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isEs) "Cerrar" else "Tancar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Live Preview Card
                    ColorLabLivePreview(isEs = isEs)

                    // 2. Curated Palettes
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(text = if (isEs) "Paletas recomendadas" else "Paletes recomanades")
                        AppColorPreset.entries.forEach { preset ->
                            val isSelected = config.activePreset == preset
                            PresetPaletteRow(
                                preset = preset,
                                isDarkMode = isDarkMode,
                                isEs = isEs,
                                isSelected = isSelected,
                                onClick = { onSelectPreset(preset) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 3. Free Mixer: Primary tone (de más suave a más fuerte)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel(text = if (isEs) "Color principal (CTA y marca)" else "Color principal (CTA i marca)")
                            Text(
                                text = if (isEs) "Suave → Fuerte" else "Suau → Fort",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorLabCatalog.primaryOptions.forEach { option ->
                                val swatchColor = if (isDarkMode) option.darkPrimary else option.lightPrimary
                                val isSelected = config.primaryId == option.id
                                SwatchChip(
                                    label = if (isEs) option.labelEs else option.labelCa,
                                    color = swatchColor,
                                    isSelected = isSelected,
                                    testTag = "color_lab_primary_${option.id}",
                                    onClick = {
                                        onUpdateConfig(
                                            config.copy(
                                                primaryId = option.id,
                                                darkOnPrimaryWhite = !option.prefersDarkTextInDarkMode
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // 4. Free Mixer: Secondary complementary tone
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(text = if (isEs) "Color complementario (secundarios y enlaces)" else "Color complementari (secundaris i enllaços)")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorLabCatalog.secondaryOptions.forEach { option ->
                                val swatchColor = if (isDarkMode) option.darkSecondary else option.lightSecondary
                                val isSelected = config.secondaryId == option.id
                                SwatchChip(
                                    label = if (isEs) option.labelEs else option.labelCa,
                                    color = swatchColor,
                                    isSelected = isSelected,
                                    testTag = "color_lab_secondary_${option.id}",
                                    onClick = {
                                        onUpdateConfig(config.copy(secondaryId = option.id))
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 5. Fine-tuning switches
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel(text = if (isEs) "Acabado y contraste" else "Acabat i contrast")

                        OptionToggleRow(
                            title = if (isEs) "Superficies cálidas" else "Superfícies càlides",
                            subtitle = if (config.warmSurfaces) {
                                if (isEs) "Carbón cálido y piedra caliza" else "Carbó càlid i pedra calcària"
                            } else {
                                if (isEs) "Gris neutro estándar" else "Gris neutre estàndard"
                            },
                            checked = config.warmSurfaces,
                            testTag = "color_lab_warm_surfaces_switch",
                            onCheckedChange = { warm ->
                                onUpdateConfig(config.copy(warmSurfaces = warm))
                            }
                        )

                        OptionToggleRow(
                            title = if (isEs) "Texto blanco en botón oscuro" else "Text blanc en botó fosc",
                            subtitle = if (config.darkOnPrimaryWhite) {
                                if (isEs) "Texto blanco sobre botones primarios" else "Text blanc sobre botons primaris"
                            } else {
                                if (isEs) "Texto oscuro de alto contraste (recomendado)" else "Text fosc d'alt contrast (recomanat)"
                            },
                            checked = config.darkOnPrimaryWhite,
                            testTag = "color_lab_dark_on_primary_switch",
                            onCheckedChange = { whiteText ->
                                onUpdateConfig(config.copy(darkOnPrimaryWhite = whiteText))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onReset,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.testTag("color_lab_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEs) "Restablecer" else "Restablir",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("color_lab_done_button")
                    ) {
                        Text(
                            text = if (isEs) "Aplicar" else "Aplicar",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorLabLivePreview(isEs: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isEs) "En vivo" else "En directe",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (isEs) "Secundario" else "Secundari",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = if (isEs) "Ver ruta" else "Veure ruta",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {},
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEs) "Mapa" else "Mapa",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isEs) "Planificar" else "Planificar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetPaletteRow(
    preset: AppColorPreset,
    isDarkMode: Boolean,
    isEs: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val previewScheme = buildAppColorScheme(ColorLabConfig.fromPreset(preset), darkTheme = isDarkMode)
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.background
        },
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("color_lab_preset_${preset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 4-dot swatch preview: bg, surface, primary, secondary
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwatchDot(color = previewScheme.background, ring = previewScheme.outlineVariant)
                SwatchDot(color = previewScheme.primary, ring = Color.Transparent)
                SwatchDot(color = previewScheme.secondary, ring = Color.Transparent)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEs) preset.nameEs else preset.nameCa,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isEs) preset.subtitleEs else preset.subtitleCa,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SwatchDot(color: Color, ring: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, if (ring == Color.Transparent) color.copy(alpha = 0.25f) else ring, CircleShape)
    )
}

@Composable
private fun SwatchChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun OptionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
