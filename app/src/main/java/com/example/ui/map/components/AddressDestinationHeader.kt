package com.example.ui.map.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.dashboard.AppLanguage

@Composable
fun AddressDestinationHeader(
    address: NominatimResult,
    title: String,
    subtitle: String,
    category: PlaceCategory,
    subcategoryLabel: String?,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textPrimaryColor = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondaryColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    val isHome = address.type.equals("home", ignoreCase = true) || title.equals("Casa", ignoreCase = true)
    val isWork = address.type.equals("work", ignoreCase = true) || title.equals("Trabajo", ignoreCase = true) || title.equals("Feina", ignoreCase = true)

    val customIcon = when {
        isHome -> Icons.Default.Home
        isWork -> Icons.Default.Work
        category == PlaceCategory.FAVORITE -> Icons.Default.Star
        else -> null
    }

    val customColor = when {
        isHome -> Color(0xFF4F8CFF)
        isWork -> Color(0xFFF59E0B)
        category == PlaceCategory.FAVORITE -> {
            address.colorHex?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
            } ?: Color(0xFFEAB308)
        }
        else -> null
    }

    val customBadgeLabel = when {
        isHome -> if (appLanguage == AppLanguage.CA) "Casa" else "Casa"
        isWork -> if (appLanguage == AppLanguage.CA) "Feina" else "Trabajo"
        category == PlaceCategory.FAVORITE -> if (appLanguage == AppLanguage.CA) "Lloc preferit" else "Sitio favorito"
        else -> null
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            PlaceCategoryIconBox(
                category = category,
                isDarkMode = isDarkMode,
                boxSize = 46.dp,
                iconSize = 24.dp,
                customIcon = customIcon,
                customColor = customColor
            )

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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondaryColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlaceCategoryBadge(
                        category = category,
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode,
                        customIcon = customIcon,
                        customColor = customColor,
                        customLabel = customBadgeLabel
                    )

                    if (!address.wheelchair.isNullOrBlank()) {
                        WheelchairAccessibilityChip(
                            wheelchair = address.wheelchair,
                            appLanguage = appLanguage,
                            isDarkMode = isDarkMode
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("address_close_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = textSecondaryColor
            )
        }
    }
}

@Composable
fun AddressDestinationActions(
    address: NominatimResult,
    title: String,
    isFavorite: Boolean,
    favoriteColorHex: String?,
    isValencian: Boolean,
    onSaveFavorite: (() -> Unit)?,
    onNavigate: ((lat: Double, lon: Double, name: String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onSaveFavorite != null) {
            val parsedFavColor = remember(favoriteColorHex) {
                if (!favoriteColorHex.isNullOrBlank()) {
                    try {
                        Color(android.graphics.Color.parseColor(favoriteColorHex))
                    } catch (_: Exception) {
                        Color(0xFFEAB308)
                    }
                } else {
                    Color(0xFFEAB308)
                }
            }

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
                    tint = if (isFavorite) parsedFavColor else MaterialTheme.colorScheme.onSurfaceVariant
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
                text = if (isValencian) "Com arribar" else "Cómo llegar",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                fontSize = 12.sp
            )
        }
    }
}
