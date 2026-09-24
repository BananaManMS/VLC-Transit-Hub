package com.example.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.AppThemeColors

@Composable
fun ActiveItineraryBanner(
    modifier: Modifier = Modifier,
    itinerary: PlannedItinerary,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onClearItinerary: (() -> Unit)?,
    onOpenRouteDetail: () -> Unit
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 10.dp, start = 14.dp, end = 14.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppThemeColors.cardBackground(isDarkMode),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClearItinerary != null) {
                IconButton(
                    onClick = onClearItinerary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (appLanguage == AppLanguage.ES) "Volver a rutas" else "Tornar a rutes",
                        tint = if (isDarkMode) Color.White else Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${itinerary.formattedDuration} • " + (if (appLanguage == AppLanguage.CA) "Arribada " else "Llegada ") + itinerary.formattedArrivalTime,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${itinerary.transfersCount} " + (if (appLanguage == AppLanguage.CA) "transbords" else "transbordos"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }

            TextButton(
                onClick = onOpenRouteDetail
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.CA) "Detalls" else "Detalles",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0284C7)
                )
            }
        }
    }
}
