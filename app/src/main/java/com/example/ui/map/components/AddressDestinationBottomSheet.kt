package com.example.ui.map.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NominatimResult
import com.example.ui.dashboard.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressDestinationBottomSheet(
    address: NominatimResult,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    isFavorite: Boolean = false,
    onSaveFavorite: (() -> Unit)? = null,
    onNavigate: ((lat: Double, lon: Double, name: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parts = remember(address.displayName) {
        address.displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val title = parts.firstOrNull() ?: address.displayName
    val subtitle = if (parts.size > 1) parts.drop(1).joinToString(", ") else ""

    val isValencian = appLanguage == AppLanguage.CA

    val sheetBgColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val textPrimaryColor = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondaryColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBgColor,
        tonalElevation = 6.dp,
        modifier = Modifier.testTag("address_destination_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val isFavItem = isFavorite || address.category == "favorite" || address.type == "favorite"
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFavItem) Color(0xFFF59E0B).copy(alpha = 0.15f)
                                else Color(0xFFE53935).copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavItem) Icons.Default.Star else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isFavItem) Color(0xFFF59E0B) else Color(0xFFE53935),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = textPrimaryColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondaryColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("address_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tancar",
                        tint = textSecondaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Coordinates info chip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isValencian) "Destí marcat al mapa" else "Destino marcado en el mapa",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "%.4f, %.4f".format(address.latitude, address.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSaveFavorite != null) {
                    FilledTonalButton(
                        onClick = onSaveFavorite,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("address_save_favorite_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = if (isFavorite) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFavorite) {
                                if (isValencian) "Desat" else "Guardado"
                            } else {
                                if (isValencian) "Desar" else "Guardar"
                            },
                            maxLines = 1,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        if (onNavigate != null) {
                            onNavigate(address.latitude, address.longitude, title)
                        } else {
                            val gmmIntentUri = Uri.parse("google.navigation:q=${address.latitude},${address.longitude}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                val fallbackUri = Uri.parse("geo:0,0?q=${address.latitude},${address.longitude}(${Uri.encode(title)})")
                                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                                try {
                                    context.startActivity(fallbackIntent)
                                } catch (e2: Exception) {
                                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${address.latitude},${address.longitude}"))
                                    context.startActivity(webIntent)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("address_navigate_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isValencian) "Arribar" else "Llegar",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
