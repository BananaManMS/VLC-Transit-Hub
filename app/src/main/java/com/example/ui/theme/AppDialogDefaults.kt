package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Estándar visual unificado para todos los recuadros de contexto (Dialog y AlertDialog) de la app:
 * mismo fondo neutro sin tinte tonal, misma anchura adaptable y mismo radio/borde.
 */
object AppDialogDefaults {
    const val WidthFraction: Float = 0.92f
    val MaxWidth: Dp = 500.dp
    val Shape: RoundedCornerShape = RoundedCornerShape(24.dp)
    val TonalElevation: Dp = 0.dp
    val ShadowElevation: Dp = 8.dp

    val Properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    )

    val NonDismissableProperties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = false,
        dismissOnClickOutside = false
    )

    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surface

    @Composable
    fun subContainerColor(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    @Composable
    fun border(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
}

fun Modifier.appDialogWidth(): Modifier = this
    .fillMaxWidth(AppDialogDefaults.WidthFraction)
    .widthIn(max = AppDialogDefaults.MaxWidth)
