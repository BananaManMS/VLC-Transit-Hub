package com.example.ui.map.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.map.MapSelectionMode
import com.example.ui.theme.AppThemeColors
import org.osmdroid.util.GeoPoint

@Composable
fun BoxScope.MapLocationSelectionOverlay(
    selectionMode: MapSelectionMode,
    isMapMoving: Boolean,
    isSearching: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    cameraTarget: GeoPoint,
    onCancelSelection: () -> Unit,
    onConfirmSelection: (GeoPoint, Boolean) -> Unit
) {
    val pinOffset by animateDpAsState(
        targetValue = if (isMapMoving) (-42).dp else (-24).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pinOffset"
    )

    val shadowScale by animateFloatAsState(
        targetValue = if (isMapMoving) 0.5f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "shadowScale"
    )

    val shadowAlpha by animateFloatAsState(
        targetValue = if (isMapMoving) 0.2f else 0.45f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "shadowAlpha"
    )

    val pinRotation by animateFloatAsState(
        targetValue = if (isMapMoving) -6f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pinRotation"
    )

    // Shadow dot exactly at the center with dynamic scale & alpha
    Box(
        modifier = Modifier
            .size(width = 16.dp, height = 4.dp)
            .align(Alignment.Center)
            .graphicsLayer {
                scaleX = shadowScale
                scaleY = shadowScale
                alpha = shadowAlpha
            }
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
    )

    // Bouncing/floating Pin above center with realistic sway (balanceo)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                translationY = pinOffset.toPx()
                rotationZ = pinRotation
                transformOrigin = TransformOrigin(0.5f, 1f) // bottom center pivot
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = "Selection Center Pin",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
    }

    // Top Floating Instructions Card
    val titleText = when (selectionMode) {
        MapSelectionMode.SELECTING_LOCATION -> if (appLanguage == AppLanguage.CA) "Triar ubicació al mapa" else "Elegir ubicación en el mapa"
        MapSelectionMode.SELECTING_HOME -> if (appLanguage == AppLanguage.CA) "Establir ubicació de Casa" else "Establecer ubicación de Casa"
        MapSelectionMode.SELECTING_WORK -> if (appLanguage == AppLanguage.CA) "Establir ubicació de Feina" else "Establecer ubicación de Trabajo"
        MapSelectionMode.SELECTING_PINNED -> if (appLanguage == AppLanguage.CA) "Establir lloc destacat" else "Establecer sitio destacado"
        MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN -> if (appLanguage == AppLanguage.CA) "Triar origen al mapa" else "Elegir origen en el mapa"
        MapSelectionMode.SELECTING_FOR_PLANNER_DESTINATION -> if (appLanguage == AppLanguage.CA) "Triar destí al mapa" else "Elegir destino en el mapa"
        else -> ""
    }
    val subtitleText = if (appLanguage == AppLanguage.CA) "Mou el mapa per a situar el marcador al centre" else "Arrastra el mapa para situar el marcador en el centro"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppThemeColors.cardBackground(isDarkMode),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, AppThemeColors.subtleBorder(isDarkMode)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(16.dp)
            .fillMaxWidth()
            .widthIn(max = 500.dp)
            .statusBarsPadding()
            .testTag("selection_mode_instruction_card")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color.White else Color.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onCancelSelection,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelar selección",
                    tint = if (isDarkMode) Color.White else Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Bottom Confirm Selection FAB
    ExtendedFloatingActionButton(
        onClick = {
            val isOrigin = (selectionMode == MapSelectionMode.SELECTING_FOR_PLANNER_ORIGIN)
            onConfirmSelection(cameraTarget, isOrigin)
        },
        icon = {
            if (isSearching) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        text = {
            Text(
                text = if (isSearching) {
                    if (appLanguage == AppLanguage.CA) "Processant..." else "Procesando..."
                } else {
                    if (appLanguage == AppLanguage.CA) "Confirmar ubicació" else "Confirmar ubicación"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 32.dp)
            .testTag("confirm_selection_fab")
    )
}
