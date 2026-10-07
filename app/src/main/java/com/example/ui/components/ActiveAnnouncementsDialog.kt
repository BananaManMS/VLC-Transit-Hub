package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.announcement.RemoteAnnouncement
import com.example.ui.dashboard.AppLanguage

@Composable
fun ActiveAnnouncementsDialog(
    announcements: List<RemoteAnnouncement>,
    isLoading: Boolean = false,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isEs = appLanguage == AppLanguage.ES

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .widthIn(max = 540.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDarkMode) Color(0xFF1C1E2A) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
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
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                        Text(
                            text = if (isEs) "Avisos activos (${announcements.size})" else "Avís actius (${announcements.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Text(
                                text = if (isEs) "Cargando avisos..." else "Carregant avisos...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (announcements.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isEs) "No hay avisos activos" else "No hi ha avís actius",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isEs) "Todo funciona con normalidad en este momento." else "Tot funciona amb normalitat en aquest moment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    // List of borderless announcement cards
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        announcements.forEach { ann ->
                            ActiveAnnouncementCard(
                                announcement = ann,
                                isEs = isEs,
                                isDarkMode = isDarkMode,
                                context = context
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveAnnouncementCard(
    announcement: RemoteAnnouncement,
    isEs: Boolean,
    isDarkMode: Boolean,
    context: android.content.Context
) {
    val themeColor = remember(announcement.type) {
        when (announcement.type) {
            "rojo", "critical", "danger", "error" -> Color(0xFFE53935)
            "naranja", "warning", "amber" -> Color(0xFFFB8C00)
            "verde", "feature", "success" -> Color(0xFF43A047)
            else -> Color(0xFF1E88E5)
        }
    }

    val iconVector: ImageVector = remember(announcement.type) {
        when (announcement.type) {
            "rojo", "critical", "danger", "error" -> Icons.Default.Warning
            "naranja", "warning", "amber" -> Icons.Default.NotificationsActive
            "verde", "feature", "success" -> Icons.Default.CheckCircle
            else -> Icons.Default.Info
        }
    }

    val titleText = if (isEs) announcement.titleEs else announcement.titleCa
    val messageText = if (isEs) announcement.messageEs else announcement.messageCa
    val actionText = if (isEs) {
        announcement.actionTextEs ?: "Ver más"
    } else {
        announcement.actionTextCa ?: "Veure més"
    }

    // Borderless card with style of startup announcement elements
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isDarkMode) Color(0xFF262938) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = themeColor.copy(alpha = if (isDarkMode) 0.25f else 0.12f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (announcement.type) {
                                "rojo", "critical" -> if (isEs) "Aviso importante" else "Avís important"
                                "naranja", "warning" -> if (isEs) "Atención" else "Atenció"
                                "verde", "feature" -> if (isEs) "Novedad" else "Novetat"
                                else -> if (isEs) "Información" else "Informació"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = themeColor
                            )
                        )
                    }
                }

                if (!announcement.endDate.isNullOrBlank()) {
                    Text(
                        text = "Hasta: ${announcement.endDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            if (titleText.isNotBlank()) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Image Banner if present
            if (!announcement.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = announcement.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Message Body
            if (messageText.isNotBlank()) {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 19.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
                )
            }

            // Action Button if present
            if (!announcement.actionUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val rawUrl = announcement.actionUrl.trim()
                            val cleanUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                                "https://$rawUrl"
                            } else rawUrl

                            val parsedUri = Uri.parse(cleanUrl)
                            val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("ActiveAnnouncementCard", "Error opening URL: ${e.message}", e)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = themeColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = actionText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
