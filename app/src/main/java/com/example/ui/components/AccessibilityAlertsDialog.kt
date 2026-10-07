package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotAccessible
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.util.StationAccessibilityHelper

@Composable
fun AccessibilityAlertsDialog(
    stationName: String,
    rawAlerts: List<String>,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit
) {
    val cleanedAlerts = rawAlerts
        .map { StationAccessibilityHelper.cleanAccessibilityAlertText(it) }
        .filter { it.isNotBlank() }
        .distinct()

    if (cleanedAlerts.isEmpty()) {
        return
    }

    val grouped = groupAccessibilityAlerts(cleanedAlerts)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotAccessible,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stationName,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                grouped.forEach { group ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (group.header.isNotEmpty()) {
                            Text(
                                text = group.header,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        group.details.forEach { detail ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (group.header.isNotEmpty()) 8.dp else 0.dp, top = 2.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (group.header.isNotEmpty()) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .testTag("accessibility_dialog_ok_btn")
                    .padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_accept),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

data class AccessibilityGroup(
    val header: String,
    val details: List<String>
)

private fun groupAccessibilityAlerts(alerts: List<String>): List<AccessibilityGroup> {
    if (alerts.isEmpty()) return emptyList()

    val groups = mutableMapOf<String, MutableList<String>>()
    val rawSingleAlerts = mutableListOf<String>()

    for (alert in alerts) {
        val trimmed = alert.trim().trimEnd('.')
        val separator = when {
            trimmed.contains(" - ") -> " - "
            trimmed.contains(" : ") -> " : "
            trimmed.contains(": ") -> ": "
            else -> null
        }

        if (separator != null) {
            val parts = trimmed.split(separator, limit = 2)
            val header = parts[0].trim().replaceFirstChar { it.uppercase() }
            val detail = parts[1].trim().replaceFirstChar { it.uppercase() }
            if (header.isNotEmpty() && detail.isNotEmpty()) {
                groups.getOrPut(header) { mutableListOf() }.add(detail)
            } else {
                rawSingleAlerts.add(trimmed)
            }
        } else {
            rawSingleAlerts.add(trimmed)
        }
    }

    val result = mutableListOf<AccessibilityGroup>()
    for ((header, details) in groups) {
        result.add(AccessibilityGroup(header, details.distinct()))
    }
    if (rawSingleAlerts.isNotEmpty()) {
        result.add(AccessibilityGroup("", rawSingleAlerts.distinct()))
    }

    return result
}
