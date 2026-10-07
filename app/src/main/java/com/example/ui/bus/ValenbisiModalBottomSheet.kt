package com.example.ui.bus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.map.components.ValenbisiStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValenbisiModalBottomSheet(
    station: ValenbisiStation,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean,
    alias: String?,
    onToggleFavorite: () -> Unit,
    onEditAlias: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sheetBg = if (isDarkMode) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFFAFAFA)
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = if (isDarkMode) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val valenbisiRed = Color(0xFFDC2626)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
            .statusBarsPadding()
            .testTag("valenbisi_modal_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(2.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = valenbisiRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBike,
                                    contentDescription = "Valenbisi",
                                    tint = valenbisiRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Valenbisi",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = valenbisiRed
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = valenbisiRed.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "#${station.number}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = valenbisiRed,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = if (!alias.isNullOrBlank()) alias else station.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action buttons (Edit alias & Favorite)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEditAlias,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.edit_alias_label),
                            tint = subtextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                            tint = if (isFavorite) Color(0xFFFFB300) else subtextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Address if available and distinct
            if (station.address.isNotBlank() && station.address != station.name) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = station.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 50.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Realtime Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bikes available
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = station.available.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = if (station.available == 0) subtextColor else Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.valenbisi_bikes_available),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Free slots
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = station.free.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.valenbisi_docks_available),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extra Info (Capacity, Distance)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (station.distanceText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = subtextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = station.distanceText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtextColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Cap: ${station.total}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
