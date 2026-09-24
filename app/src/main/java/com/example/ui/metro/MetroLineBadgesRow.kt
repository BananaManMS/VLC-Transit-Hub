package com.example.ui.metro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.data.model.ValenciaMetroData

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

@Composable
fun MetroLineBadgesRow(
    lineasStr: String?,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 15.dp,
    fontSize: TextUnit = 8.5.sp
) {
    if (lineasStr.isNullOrBlank()) return
    val lines = lineasStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lines.forEach { rawLine ->
            val lineId = if (rawLine.startsWith("L", ignoreCase = true)) rawLine else "L$rawLine"
            val numOnly = rawLine.replace(Regex("[^0-9]"), "")
            val displayText = if (numOnly.isNotEmpty()) numOnly else rawLine
            val metroLine = remember(lineId) {
                ValenciaMetroData.lines.find { it.id.equals(lineId, ignoreCase = true) }
            }
            val lineBgColor = remember(metroLine?.colorHex) {
                try {
                    metroLine?.colorHex?.toColorInt() ?: 0xFF64748B.toInt()
                } catch (e: Exception) {
                    0xFF64748B.toInt()
                }
            }
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .background(Color(lineBgColor), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}
