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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenRouteDetail: () -> Unit,
    onStartTrip: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 10.dp, start = 12.dp, end = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppThemeColors.cardBackground(isDarkMode),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClearItinerary != null) {
                IconButton(
                    onClick = onClearItinerary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (appLanguage == AppLanguage.ES) "Volver a rutas" else "Tornar a rutes",
                        tint = if (isDarkMode) Color.White else Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${itinerary.formattedDuration} • " + (if (appLanguage == AppLanguage.CA) "Arribada " else "Llegada ") + itinerary.formattedArrivalTime,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${itinerary.transfersCount} " + (if (appLanguage == AppLanguage.CA) "transbords" else "transbordos") +
                            if (itinerary.totalWalkDistanceMeters > 0) " • ${itinerary.totalWalkDistanceMeters.toInt()}m ${if (appLanguage == AppLanguage.CA) "a peu" else "a pie"}" else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    maxLines = 1
                )
            }

            TextButton(
                onClick = onOpenRouteDetail,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.CA) "Detalls" else "Detalles",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF0284C7)
                )
            }

            if (onStartTrip != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onStartTrip,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("active_banner_start_trip_button"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A86B),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Iniciar" else "Iniciar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
