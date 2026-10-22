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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.announcement.RemoteAnnouncement
import com.example.ui.dashboard.AppLanguage

@Composable
fun RemoteAnnouncementDialog(
    announcements: List<RemoteAnnouncement>,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onDismissSingle: (String) -> Unit, // Called when "No volver a mostrar" is checked
    onCloseAll: () -> Unit // Called when user closes dialog
) {
    if (announcements.isEmpty()) return

    var currentIndex by remember(announcements.size) { mutableIntStateOf(0) }
    
    // Bounds safety
    val safeIndex = currentIndex.coerceIn(0, (announcements.size - 1).coerceAtLeast(0))
    val currentAnnouncement = announcements.getOrNull(safeIndex) ?: return

    var dontShowAgainChecked by remember(currentAnnouncement.id) { mutableStateOf(false) }

    val context = LocalContext.current
    val isEs = appLanguage != AppLanguage.CA

    // Custom Coil ImageLoader with OkHttpClient to handle HTTPS redirects, User-Agent and Wikipedia/GitHub URLs
    val imageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .okHttpClient(com.example.data.network.NetworkModule.okHttpClient)
            .crossfade(true)
            .build()
    }

    // Color semantic palette
    val themeColor = remember(currentAnnouncement.type) {
        when (currentAnnouncement.type) {
            "rojo", "critical", "danger", "error" -> Color(0xFFE53935)
            "naranja", "warning", "amber" -> Color(0xFFF57C00)
            "verde", "feature", "success" -> Color(0xFF10B981)
            else -> Color(0xFF0284C7) // "azul", info
        }
    }

    val iconVector: ImageVector = remember(currentAnnouncement.type) {
        when (currentAnnouncement.type) {
            "rojo", "critical", "danger", "error" -> Icons.Default.Warning
            "naranja", "warning", "amber" -> Icons.Default.NotificationsActive
            "verde", "feature", "success" -> Icons.Default.CheckCircle
            else -> Icons.Default.Info
        }
    }

    val titleText = if (isEs) currentAnnouncement.titleEs else currentAnnouncement.titleCa
    val messageText = if (isEs) currentAnnouncement.messageEs else currentAnnouncement.messageCa
    val actionText = if (isEs) {
        currentAnnouncement.actionTextEs ?: "Ver más"
    } else {
        currentAnnouncement.actionTextCa ?: "Veure més"
    }

    Dialog(
        onDismissRequest = {
            announcements.forEach { ann ->
                if (!ann.repetir) {
                    onDismissSingle(ann.id)
                }
            }
            onCloseAll()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .padding(vertical = 12.dp)
                .testTag("remote_announcement_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = if (isDarkMode) Color(0xFF1C1E2A) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, themeColor.copy(alpha = if (isDarkMode) 0.45f else 0.3f)),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Top Header: Type Badge + Counter X/X (if >1) + Close Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Type Badge
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = themeColor.copy(alpha = if (isDarkMode) 0.22f else 0.12f)
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
                                    text = when (currentAnnouncement.type) {
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

                        // Compact Counter X/X with arrows (if multiple announcements)
                        if (announcements.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(30.dp),
                                color = themeColor.copy(alpha = if (isDarkMode) 0.18f else 0.1f),
                                border = BorderStroke(1.dp, themeColor.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    if (currentIndex > 0) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Anterior",
                                            tint = themeColor,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .clickable { currentIndex-- }
                                                .padding(2.dp)
                                        )
                                    }

                                    Text(
                                        text = "${safeIndex + 1}/${announcements.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = themeColor
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    if (currentIndex < announcements.size - 1) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Siguiente",
                                            tint = themeColor,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .clickable { currentIndex++ }
                                                .padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = {
                            announcements.forEach { ann ->
                                if (!ann.repetir) {
                                    onDismissSingle(ann.id)
                                }
                            }
                            onCloseAll()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Body Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (titleText.isNotBlank()) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                lineHeight = 23.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Optional Banner Image - Natural aspect ratio without forcing croppings
                    if (!currentAnnouncement.imageUrl.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            SubcomposeAsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(currentAnnouncement.imageUrl)
                                    .crossfade(true)
                                    .addHeader("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = titleText,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp),
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = themeColor,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                },
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            )
                        }
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
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Checkbox "No volver a mostrar este aviso" (Only if repetir is true)
                if (currentAnnouncement.repetir) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgainChecked = !dontShowAgainChecked }
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowAgainChecked,
                            onCheckedChange = { dontShowAgainChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = themeColor,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEs) "No volver a mostrar este aviso" else "No tornar a mostrar este avís",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Button Row: "Saber más" on Left + "Siguiente aviso / Entendido" on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left side: Link Action Button ("Saber más" / "Veure més") if actionUrl exists
                    if (!currentAnnouncement.actionUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val rawUrl = currentAnnouncement.actionUrl.trim()
                                    val cleanUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                                        "https://$rawUrl"
                                    } else rawUrl

                                    val parsedUri = Uri.parse(cleanUrl)
                                    val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    try {
                                        context.startActivity(intent)
                                    } catch (e1: Exception) {
                                        val chooser = Intent.createChooser(
                                            Intent(Intent.ACTION_VIEW, parsedUri),
                                            if (isEs) "Abrir enlace" else "Obrir enllaç"
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(chooser)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("RemoteAnnouncementDialog", "Error opening URL: ${e.message}", e)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = themeColor),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
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

                    // Right side: Main Action Button ("Siguiente aviso" / "Entendido")
                    Button(
                        onClick = {
                            if (dontShowAgainChecked || !currentAnnouncement.repetir) {
                                onDismissSingle(currentAnnouncement.id)
                            }

                            if (announcements.size > 1 && currentIndex < announcements.size - 1) {
                                currentIndex++
                            } else {
                                announcements.forEach { ann ->
                                    if (!ann.repetir) {
                                        onDismissSingle(ann.id)
                                    }
                                }
                                onCloseAll()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        val btnText = when {
                            announcements.size > 1 && currentIndex < announcements.size - 1 -> if (isEs) "Siguiente aviso" else "Següent avís"
                            else -> if (isEs) "Entendido" else "Entés"
                        }
                        Text(
                            text = btnText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
