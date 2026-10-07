package com.example.ui.dashboard.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    pinnedName: String = "",
    isDarkMode: Boolean = false,
    onHomeClick: () -> Unit,
    onWorkClick: () -> Unit,
    onPinnedClick: () -> Unit,
    onPlanRouteClick: () -> Unit,
    onExploreMapClick: () -> Unit,
    onEditCommute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldBg = if (isDarkMode) Color(0xFF222222) else MaterialTheme.colorScheme.surface
    val fieldBorder = BorderStroke(1.dp, if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFE2E8F0))

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Interactive Search Affordance Bar with crisp contrast and subtle shadow in light mode
        Surface(
            onClick = onPlanRouteClick,
            shape = RoundedCornerShape(12.dp),
            color = fieldBg,
            border = fieldBorder,
            shadowElevation = if (isDarkMode) 0.dp else 3.dp,
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
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_search_route_hint),
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
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.commute_route_badge),
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
        val hasPinned = pinnedName.isNotBlank()

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Commute Destinations: 3-button row (Casa, Trabajo, Sitio Fijado) without subtitles and editable on long-press
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button 1: Casa
            CommuteDestinationCard(
                icon = Icons.Default.Home,
                iconColor = Color(0xFFF59E0B),
                title = androidx.compose.ui.res.stringResource(com.example.R.string.commute_home_title),
                isConfigured = hasHome,
                isDarkMode = isDarkMode,
                onClick = if (hasHome) onHomeClick else { { onEditCommute("HOME") } },
                onEdit = { onEditCommute("HOME") },
                modifier = Modifier.weight(1f)
            )

            // Button 2: Trabajo
            CommuteDestinationCard(
                icon = Icons.Default.Work,
                iconColor = Color(0xFF3B82F6),
                title = androidx.compose.ui.res.stringResource(com.example.R.string.commute_work_title),
                isConfigured = hasWork,
                isDarkMode = isDarkMode,
                onClick = if (hasWork) onWorkClick else { { onEditCommute("WORK") } },
                onEdit = { onEditCommute("WORK") },
                modifier = Modifier.weight(1f)
            )

            // Button 3: Sitio Destacado / Fijado
            CommuteDestinationCard(
                icon = Icons.Default.PushPin,
                iconColor = Color(0xFF8B5CF6),
                title = if (hasPinned) pinnedName else androidx.compose.ui.res.stringResource(com.example.R.string.commute_pinned_empty),
                isConfigured = hasPinned,
                isDarkMode = isDarkMode,
                onClick = if (hasPinned) onPinnedClick else { { onEditCommute("PINNED") } },
                onEdit = { onEditCommute("PINNED") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommuteDestinationCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    isConfigured: Boolean,
    isDarkMode: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val fieldBg = if (isDarkMode) Color(0xFF222222) else MaterialTheme.colorScheme.surface
    val fieldBorder = BorderStroke(1.dp, if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFE2E8F0))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = fieldBg,
        border = fieldBorder,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = if (isDarkMode) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                fontWeight = FontWeight.Bold,
                color = if (isConfigured) MaterialTheme.colorScheme.onSurface else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
