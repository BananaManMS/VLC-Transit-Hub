package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import com.example.R
import com.example.ui.map.SelectedMapItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modifier modifier extension for adding an animated Material 3 shimmer loading gradient effect.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(12.dp)
): Modifier = composed {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1300,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translation"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(translateAnim.value, 0f),
        end = Offset(translateAnim.value + 250f, 250f)
    )

    this
        .clip(shape)
        .background(brush)
}

/**
 * Reusable skeleton card for list items while loading.
 */
@Composable
fun SkeletonCardItem(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shimmerEffect(shape = CircleShape)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(16.dp)
                        .shimmerEffect(shape = RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.40f)
                        .height(12.dp)
                        .shimmerEffect(shape = RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

/**
 * Dedicated high-fidelity Skeleton Loading card for Metro departures.
 */
@Composable
fun MetroDepartureSkeletonCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Metro line badge placeholder
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shimmerEffect(shape = RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Destination and sub-info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(16.dp)
                        .shimmerEffect(shape = RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(11.dp)
                        .shimmerEffect(shape = RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Time / Countdown pill placeholder
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(22.dp)
                        .shimmerEffect(shape = RoundedCornerShape(6.dp))
                )
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(10.dp)
                        .shimmerEffect(shape = RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/**
 * Dedicated high-fidelity Skeleton Loading card for Cercanías departures.
 */
@Composable
fun CercaniasDepartureSkeletonCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cercanías Line Badge (C1, C2...)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shimmerEffect(shape = RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Destination and delay/status info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(16.dp)
                        .shimmerEffect(shape = RoundedCornerShape(4.dp))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(12.dp)
                            .shimmerEffect(shape = RoundedCornerShape(3.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(45.dp)
                            .height(12.dp)
                            .shimmerEffect(shape = RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Departure time countdown badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(22.dp)
                        .shimmerEffect(shape = RoundedCornerShape(6.dp))
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(11.dp)
                        .shimmerEffect(shape = RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/**
 * Standardized Material 3 Empty State visual card.
 */
@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = actionText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OperatorLogo(
    item: SelectedMapItem,
    modifier: Modifier = Modifier
) {
    when (item) {
        is SelectedMapItem.Metro -> {
            Image(
                painter = painterResource(id = R.drawable.logo_metrovalencia),
                contentDescription = "Metrovalencia",
                contentScale = ContentScale.Fit,
                modifier = modifier.clip(CircleShape)
            )
        }
        is SelectedMapItem.Cercanias, is SelectedMapItem.LiveTrain -> {
            Image(
                painter = painterResource(id = R.drawable.logo_cercanias),
                contentDescription = "Cercanías",
                contentScale = ContentScale.Fit,
                modifier = modifier.clip(CircleShape)
            )
        }
        is SelectedMapItem.MetrobusStopItem -> {
            Image(
                painter = painterResource(id = R.drawable.logo_metrobus),
                contentDescription = "Metrobús",
                contentScale = ContentScale.Fit,
                modifier = modifier.clip(RoundedCornerShape(8.dp))
            )
        }
        is SelectedMapItem.BusStop -> {
            Image(
                painter = painterResource(id = R.drawable.logo_emt_valencia),
                contentDescription = "EMT València",
                contentScale = ContentScale.Fit,
                modifier = modifier
            )
        }
        is SelectedMapItem.Valenbisi -> {
            Surface(
                modifier = modifier,
                shape = CircleShape,
                color = Color(0xFF009688),
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_bike),
                        contentDescription = "Valenbisi",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp)
                    )
                }
            }
        }
        is SelectedMapItem.Address -> {
            Surface(
                modifier = modifier,
                shape = CircleShape,
                color = Color(0xFF3B82F6),
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Dirección",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
