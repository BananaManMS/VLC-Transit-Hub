package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Discreet persistent banner at the bottom of the dashboard that appears
 * only when network connectivity is lost, informing the user that information
 * is loaded from cache/offline. Can be dismissed by the user.
 */
@Composable
fun OfflineBanner(
    isOnline: Boolean,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    bottomPadding: Dp = 0.dp,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            isDismissed = false
        }
    }

    AnimatedVisibility(
        visible = !isOnline && !isDismissed,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding + 8.dp)
    ) {
        val containerColor = if (isDarkMode) Color(0xFF23272F) else Color(0xFFF8FAFC)
        val textPrimary = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF1E293B)
        val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
        val iconTint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706)
        val iconBadgeBg = if (isDarkMode) Color(0xFF3B3322) else Color(0xFFFEF3C7)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("offline_status_banner"),
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBadgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi_off),
                        contentDescription = "Offline indicator",
                        tint = iconTint,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Sense connexió a internet" else "Sin conexión a internet",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Mostrant dades en memòria cau" else "Mostrando datos en caché local",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = textSecondary,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = {
                        isDismissed = true
                        onDismiss?.invoke()
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("offline_banner_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = if (appLanguage == AppLanguage.CA) "Tancar" else "Cerrar",
                        tint = textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

