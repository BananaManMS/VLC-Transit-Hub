package com.example.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlaceCategory
import com.example.ui.dashboard.AppLanguage

@Composable
fun PlaceCategoryIconBox(
    category: PlaceCategory,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    boxSize: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    customIcon: ImageVector? = null,
    customColor: Color? = null
) {
    val effectiveColor = customColor ?: category.brandColor
    val effectiveIcon = customIcon ?: category.icon

    val bgColor = if (isDarkMode) {
        effectiveColor.copy(alpha = 0.25f)
    } else {
        effectiveColor
    }

    val iconTint = if (isDarkMode) {
        effectiveColor
    } else {
        Color.White
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier.size(boxSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = effectiveIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun PlaceCategoryBadge(
    category: PlaceCategory,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    customIcon: ImageVector? = null,
    customColor: Color? = null,
    customLabel: String? = null
) {
    val label = customLabel ?: category.getLabel(appLanguage)
    val effectiveColor = customColor ?: category.brandColor
    val effectiveIcon = customIcon ?: category.icon

    val bgColor = if (isDarkMode) effectiveColor.copy(alpha = 0.20f) else effectiveColor.copy(alpha = 0.12f)
    val textColor = effectiveColor

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = effectiveIcon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp
                ),
                color = textColor
            )
        }
    }
}

@Composable
fun PlaceActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardBg = if (isDarkMode) Color(0xFF262626) else Color(0xFFF8FAFC)
    val borderCol = if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = textPrimary,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = textSecondary,
                        maxLines = 1
                    )
                }
            }

            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun WheelchairAccessibilityChip(
    wheelchair: String,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val isValencian = appLanguage == AppLanguage.CA
    val (label, tint) = when (wheelchair.lowercase()) {
        "yes" -> Pair(
            if (isValencian) "Accessible PMR" else "Accesible PMR",
            Color(0xFF10B981)
        )
        "limited" -> Pair(
            if (isValencian) "Accés limitat" else "Acceso limitado",
            Color(0xFFF59E0B)
        )
        "no" -> Pair(
            if (isValencian) "No accessible" else "No accesible",
            Color(0xFFEF4444)
        )
        else -> return
    }

    val bgColor = if (isDarkMode) Color(0xFF262626) else Color(0xFFF8FAFC)
    val borderColor = if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Accessible,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = tint
            )
        }
    }
}
