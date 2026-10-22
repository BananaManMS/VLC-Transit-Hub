package com.example.ui.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage

@Composable
fun UnifiedTransitStopHeader(
    stop: UnifiedTransitStop,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier,
    headerDragModifier: Modifier = Modifier,
    onDirectionsClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onEditAliasClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val titleColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF111827)
    val subtextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF64748B)
    val iconTint = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF475569)
    val opColor = Color(stop.operator.colorHex)
    val favoriteColor = Color(0xFFFFB300)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(headerDragModifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Operator Badge + Stop Code Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = opColor,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stop.operator.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.stop_id_format, stop.id),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = subtextColor
                    )

                    if (stop.distanceText.isNotEmpty()) {
                        Text(
                            text = "• ${stop.distanceText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = subtextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Stop Name or Custom Alias
                if (!stop.alias.isNullOrBlank()) {
                    Text(
                        text = stop.alias,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Top-right action icons (Directions, Favorite, Edit, Close)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onDirectionsClick != null) {
                    IconButton(
                        onClick = onDirectionsClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("unified_sheet_directions_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.bus_directions_btn),
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (onToggleFavorite != null) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("unified_sheet_fav_btn")
                    ) {
                        Icon(
                            imageVector = if (stop.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (stop.isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                            tint = if (stop.isFavorite) favoriteColor else iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (onEditAliasClick != null) {
                    IconButton(
                        onClick = onEditAliasClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("unified_sheet_edit_alias_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar nombre personalizado",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("unified_sheet_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
