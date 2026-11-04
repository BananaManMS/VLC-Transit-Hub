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
fun BoxScope.GoogleMapStatusBadge(
    bottomPaddingDp: Dp,
    settledZoom: Float,
    metroLinesCount: Int,
    cercaniasLinesCount: Int,
    showBusStops: Boolean,
    showMetrobusStops: Boolean,
    viewportState: GoogleMapViewportTransitState
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(bottom = bottomPaddingDp + 12.dp, start = 12.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val busStatus = when {
            !showBusStops && !showMetrobusStops -> "Bus: Apagado"
            !viewportState.showBusLayer && !viewportState.showMetrobusLayer -> "Bus: Oculto por zoom (<15.2)"
            viewportState.isZoomForClusters -> "Bus: Clústeres (${viewportState.busClusters.size + viewportState.metrobusClusters.size})"
            viewportState.isZoomForBusDots -> "Bus: Puntos (${viewportState.sampledDotsBus.size + viewportState.sampledDotsMb.size + viewportState.currentMergedGroups.size})"
            viewportState.isZoomForBusBadges -> "Bus: Insignias (${viewportState.currentMergedGroups.size} fus., ${viewportState.standaloneBusDetailed.size} EMT, ${viewportState.standaloneMbDetailed.size} MB)"
            viewportState.isZoomForBusDetailed -> "Bus: Fichas Detalladas (${viewportState.currentMergedGroups.size} fus., ${viewportState.standaloneBusDetailed.size} EMT, ${viewportState.standaloneMbDetailed.size} MB)"
            else -> "Bus: Apagado"
        }
        Text(
            text = "Z ${String.format("%.1f", settledZoom)} • Líneas: $metroLinesCount M, $cercaniasLinesCount C • $busStatus",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp
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
