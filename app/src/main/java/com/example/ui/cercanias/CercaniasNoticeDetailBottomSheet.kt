package com.example.ui.cercanias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LinkifiedText
import com.example.ui.dashboard.AppLanguage
import com.example.ui.metro.CercaniasLineBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CercaniasNoticeDetailBottomSheet(
    alert: CercaniasAlert?,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    if (alert == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .statusBarsPadding()
            .testTag("cercanias_notice_detail_bottom_sheet")
    ) {
        CercaniasNoticeDetailContent(
            alert = alert,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode
        )
    }
}

@Composable
fun CercaniasNoticeDetailContent(
    alert: CercaniasAlert,
    appLanguage: AppLanguage,
    isDarkMode: Boolean
) {
    val catEnum = remember(alert.headerEs, alert.descriptionEs) {
        CercaniasNoticeCategory.resolveFromText(alert.headerEs, alert.descriptionEs)
    }
    val badgeCategoryName = catEnum.getDisplayName(appLanguage)
    val badgeColor = catEnum.getColor(isDarkMode)
    val badgeIcon = catEnum.icon

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Category Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeCategoryName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (alert.routeIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cercanias_affected_lines_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                alert.routeIds.forEach { lineId ->
                    CercaniasLineBadge(routeId = lineId, size = 22.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (alert.headerEs.isNotBlank()) {
            Text(
                text = alert.headerEs,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        val fullBodyText = if (alert.descriptionEs.isNotBlank() && !alert.descriptionEs.equals(alert.headerEs, ignoreCase = true)) {
            alert.descriptionEs
        } else {
            alert.headerEs
        }

        LinkifiedText(
            text = fullBodyText,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}
