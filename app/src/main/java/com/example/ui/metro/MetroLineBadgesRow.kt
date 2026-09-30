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
import androidx.compose.ui.draw.clip
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
    badgeSize: Dp = 18.dp,
    fontSize: TextUnit = 10.sp
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
            val cleanLine = rawLine.replace("L", "", ignoreCase = true).trim()
            val lineId = "L$cleanLine"
            val lineBgColor = com.example.util.LineColorResolver.getMetroLineColor(lineId)
            
            Box(
                modifier = Modifier
                    .height(badgeSize)
                    .widthIn(min = badgeSize)
                    .clip(RoundedCornerShape(5.dp))
                    .background(lineBgColor)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cleanLine.ifEmpty { rawLine },
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
