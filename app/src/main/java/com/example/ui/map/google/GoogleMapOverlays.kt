package com.example.ui.map.google

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BoxScope.GoogleMapOpticalCenterGuide(
    maxHeight: Dp,
    bottomPaddingDp: Dp
) {
    val opticalCenterOffsetY = (maxHeight - bottomPaddingDp) / 2
    val greenColor = Color(0xFF10B981)

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = opticalCenterOffsetY - 12.dp)
            .size(24.dp)
            .background(greenColor.copy(alpha = 0.18f), CircleShape)
            .border(1.5.dp, greenColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(greenColor, CircleShape)
                .border(1.dp, Color.White, CircleShape)
        )
    }
}

@Composable
fun BoxScope.GoogleMapLoadingOverlay(
    bottomPaddingDp: Dp,
    isLinesLoading: Boolean,
    isFootprintsLoading: Boolean
) {
    if (isLinesLoading || isFootprintsLoading) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPaddingDp + 16.dp, end = 16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isLinesLoading) "Cargando líneas..." else "Cargando huellas...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
