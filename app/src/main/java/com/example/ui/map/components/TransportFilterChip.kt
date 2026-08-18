package com.example.ui.map.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TransportFilterChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "filterChipBg"
    )

    val contentColor = if (isSelected) {
        Color.White
    } else {
        if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = animatedBgColor,
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = if (label.isEmpty()) 12.dp else 14.dp,
                vertical = 8.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (label.isEmpty()) "Favoritos" else label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            if (label.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = contentColor
                )
            }
        }
    }
}
