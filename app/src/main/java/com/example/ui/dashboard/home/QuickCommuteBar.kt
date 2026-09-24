package com.example.ui.dashboard.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage

@Composable
fun QuickCommuteBar(
    appLanguage: AppLanguage,
    homeName: String,
    workName: String,
    isDarkMode: Boolean = false,
    onHomeClick: () -> Unit,
    onWorkClick: () -> Unit,
    onPlanRouteClick: () -> Unit,
    onExploreMapClick: () -> Unit,
    onEditCommute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldBg = if (isDarkMode) Color(0xFF2C2C2C) else MaterialTheme.colorScheme.surfaceVariant
    val fieldBorder = BorderStroke(1.dp, if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0))

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Interactive Search Affordance Bar with crisp contrast
        Surface(
            onClick = onPlanRouteClick,
            shape = RoundedCornerShape(12.dp),
            color = fieldBg,
            border = fieldBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Cercar ruta..." else "Buscar ruta...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color(0xFFCBD5E1) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Planifica" else "Ruta",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        val hasHome = homeName.isNotBlank()
        val hasWork = workName.isNotBlank()

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Commute Destinations (Casa & Trabajo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommuteDestinationCard(
                icon = Icons.Default.Home,
                iconColor = Color(0xFFF59E0B),
                title = if (appLanguage == AppLanguage.CA) "Casa" else "Casa",
                subtitle = if (hasHome) homeName else (if (appLanguage == AppLanguage.CA) "+ Configurar" else "+ Configurar"),
                isConfigured = hasHome,
                isDarkMode = isDarkMode,
                onClick = if (hasHome) onHomeClick else { { onEditCommute("HOME") } },
                onEdit = { onEditCommute("HOME") },
                modifier = Modifier.weight(1f)
            )

            CommuteDestinationCard(
                icon = Icons.Default.Work,
                iconColor = Color(0xFF3B82F6),
                title = if (appLanguage == AppLanguage.CA) "Treball" else "Trabajo",
                subtitle = if (hasWork) workName else (if (appLanguage == AppLanguage.CA) "+ Configurar" else "+ Configurar"),
                isConfigured = hasWork,
                isDarkMode = isDarkMode,
                onClick = if (hasWork) onWorkClick else { { onEditCommute("WORK") } },
                onEdit = { onEditCommute("WORK") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CommuteDestinationCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    isDarkMode: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanSubtitle = subtitle.trim()
    val hasDistinctSubtitle = cleanSubtitle.isNotBlank() &&
            !cleanSubtitle.equals(title, ignoreCase = true) &&
            !cleanSubtitle.equals("Casa", ignoreCase = true) &&
            !cleanSubtitle.equals("Trabajo", ignoreCase = true) &&
            !cleanSubtitle.equals("Treball", ignoreCase = true)

    val fieldBg = if (isDarkMode) Color(0xFF2C2C2C) else MaterialTheme.colorScheme.surfaceVariant
    val fieldBorder = BorderStroke(1.dp, if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0))

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = fieldBg,
        border = fieldBorder,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = if (isDarkMode) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (hasDistinctSubtitle) {
                    Text(
                        text = cleanSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!isConfigured) {
                    Text(
                        text = cleanSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isConfigured) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = if (isConfigured) "Editar" else "Configurar",
                    tint = if (isConfigured) (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isConfigured) 15.dp else 18.dp)
                )
            }
        }
    }
}
