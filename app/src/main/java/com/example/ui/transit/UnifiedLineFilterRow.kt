package com.example.ui.transit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.dashboard.AppLanguage

@Composable
fun UnifiedLineFilterRow(
    availableLines: List<String>,
    selectedLineFilters: Set<String>,
    operator: TransitOperator,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onToggleLineFilter: (String) -> Unit,
    onClearLineFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableLines.size <= 1) return

    val isAllSelected = selectedLineFilters.isEmpty()
    val subtextColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF64748B)
    val opPrimaryColor = Color(operator.colorHex)
    val inactiveBgColor = if (isDarkMode) Color(0xFF242424) else Color(0xFFF1F5F9)
    val inactiveBorderColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFE2E8F0)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        // "Todas" / "Totes" Chip
        Surface(
            onClick = onClearLineFilters,
            shape = RoundedCornerShape(8.dp),
            color = if (isAllSelected) opPrimaryColor else inactiveBgColor,
            border = if (isAllSelected) null else BorderStroke(1.dp, inactiveBorderColor),
            modifier = Modifier
                .defaultMinSize(minHeight = 28.dp)
                .testTag("unified_line_filter_all")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.CA) "Totes" else "Todas",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isAllSelected) Color.White else subtextColor
                )
            }
        }

        // Individual Line Chips
        availableLines.forEach { line ->
            val isSelected = selectedLineFilters.any { it.equals(line, ignoreCase = true) }
            val isNoFilterActive = selectedLineFilters.isEmpty()

            val bgColor = when {
                isSelected -> opPrimaryColor
                isNoFilterActive -> opPrimaryColor
                else -> inactiveBgColor
            }

            val textColor = when {
                isSelected || isNoFilterActive -> Color.White
                else -> subtextColor
            }

            val border = when {
                isSelected && !isNoFilterActive -> BorderStroke(2.dp, if (isDarkMode) Color.White else opPrimaryColor)
                !isSelected && !isNoFilterActive -> BorderStroke(1.dp, inactiveBorderColor)
                else -> null
            }

            Surface(
                onClick = { onToggleLineFilter(line) },
                shape = RoundedCornerShape(8.dp),
                color = bgColor,
                border = border,
                modifier = Modifier
                    .defaultMinSize(minWidth = 38.dp, minHeight = 28.dp)
                    .testTag("unified_line_filter_$line")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}
