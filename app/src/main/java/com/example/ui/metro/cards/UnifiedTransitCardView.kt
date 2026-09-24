package com.example.ui.metro.cards

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.metro.CardCategory
import com.example.ui.metro.formatCardNumber
import kotlin.math.abs

enum class CardDisplayFormat {
    HERO,     // Large card used in Onboarding & Detail Dialog
    LIST,     // Full width list item used in MetroCardsTab
    COMPACT   // Carousel item used in DashboardHomeTab
}

data class CardTilt2D(val x: Float = 0f, val y: Float = 0f)

/**
 * Ultra-lightweight low-pass filtered sensor hook to track 2D phone tilt (Roll + Pitch).
 * Uses Exponential Moving Average smoothing to eliminate jitter with zero CPU lag.
 */
@Composable
fun rememberLightweightCardTilt2D(): State<CardTilt2D> {
    val context = LocalContext.current
    val tiltState = remember { mutableStateOf(CardTilt2D(0f, 0f)) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || rotationSensor == null) {
            return@DisposableEffect onDispose {}
        }

        var smoothX = 0f
        var smoothY = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val targetX: Float
                val targetY: Float

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)

                    // pitch (orientation[1]) and roll (orientation[2]) in [-1, 1] range
                    targetX = orientation[2].coerceIn(-1f, 1f)
                    targetY = orientation[1].coerceIn(-1f, 1f)
                } else {
                    targetX = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    targetY = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                }

                // Low-pass exponential moving average filter for liquid smooth movement
                smoothX += (targetX - smoothX) * 0.12f
                smoothY += (targetY - smoothY) * 0.12f

                if (abs(smoothX - tiltState.value.x) > 0.005f || abs(smoothY - tiltState.value.y) > 0.005f) {
                    tiltState.value = CardTilt2D(smoothX, smoothY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return tiltState
}

data class CardStyleDefinition(
    val backgroundColors: List<Color>,
    val badgeLabel: String,
    val badgeBgColor: Color,
    val badgeTextColor: Color,
    val sheenColor: Color
)

fun getStyleForCategory(category: CardCategory): CardStyleDefinition {
    return when (category) {
        CardCategory.SUMA_SENCILLO,
        CardCategory.SUMA_MENSUAL,
        CardCategory.SUMA_TSERIES -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            badgeLabel = "SUMA",
            badgeBgColor = Color(0xFFEF4444),
            badgeTextColor = Color.White,
            sheenColor = Color(0x30FFFFFF)
        )
        CardCategory.MOBILIS -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF0A2540), Color(0xFF00529B), Color(0xFF0072CE)),
            badgeLabel = "Móbilis",
            badgeBgColor = Color(0xFF00A8E8),
            badgeTextColor = Color.White,
            sheenColor = Color(0x38E0F7FA)
        )
        CardCategory.TUIN -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF3D1200), Color(0xFFB33600), Color(0xFFFF5500)),
            badgeLabel = "TuIN",
            badgeBgColor = Color(0xFFFF6B00),
            badgeTextColor = Color.White,
            sheenColor = Color(0x38FFE0B2)
        )
        CardCategory.OTHER -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF1C1600), Color(0xFF66520E), Color(0xFFA88B24)),
            badgeLabel = "Móbilis",
            badgeBgColor = Color(0xFFD4AF37),
            badgeTextColor = Color(0xFF1C1600),
            sheenColor = Color(0x30FFF8E1)
        )
    }
}

/**
 * 100% 2D Flat, ultra-lightweight Card View.
 * Employs a subtle diagonal metallic sheen sweep calculated in the Draw phase
 * (zero recomposition overhead for zero battery/CPU drain).
 */
@Composable
fun UnifiedTransitCardView(
    card: TransitCardUiModel,
    appLanguage: AppLanguage = AppLanguage.ES,
    format: CardDisplayFormat = CardDisplayFormat.LIST,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val category = remember(card.category) {
        try {
            CardCategory.valueOf(card.category)
        } catch (_: Exception) {
            CardCategory.OTHER
        }
    }
    val style = remember(category) { getStyleForCategory(category) }
    val isFaded = card.isFaded

    // Lightweight 2D tilt state for specular sheen sweep
    val tiltState = rememberLightweightCardTilt2D()

    val backgroundBrush = remember(style.backgroundColors, isFaded) {
        Brush.linearGradient(
            colors = if (isFaded) {
                listOf(Color(0xFF2B2D33), Color(0xFF383B42), Color(0xFF1F2126))
            } else {
                style.backgroundColors
            }
        )
    }

    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .border(
                width = 1.dp,
                color = if (isFaded) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.22f),
                shape = cardShape
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (format == CardDisplayFormat.HERO) 4.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when (format) {
                        CardDisplayFormat.HERO -> Modifier.height(165.dp)
                        CardDisplayFormat.LIST -> Modifier.height(115.dp)
                        CardDisplayFormat.COMPACT -> Modifier.width(185.dp).height(115.dp)
                    }
                )
                .background(backgroundBrush)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        if (!isFaded) {
                            // Inverted optical response: tilting right moves light left; tilting down moves light up
                            val tiltX = -tiltState.value.x
                            val tiltY = -tiltState.value.y

                            // Fresnel Falloff magnitude: light specular reflection grows brighter at glancing angles
                            val tiltMagnitude = kotlin.math.sqrt(tiltX * tiltX + tiltY * tiltY).coerceIn(0f, 1f)
                            val peakAlpha = (0.08f + tiltMagnitude * 0.16f).coerceIn(0.08f, 0.24f)
                            val ambientAlpha = peakAlpha * 0.25f

                            val w = size.width
                            val h = size.height

                            // 2D Vector offset for the specular light beam center
                            val centerX = (0.5f + tiltX * 0.8f) * w
                            val centerY = (0.5f + tiltY * 0.8f) * h

                            // Multi-stop 5-point specular gradient with soft ambient halo and sharp core peak
                            val sheenBrush = Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.35f to style.sheenColor.copy(alpha = style.sheenColor.alpha * ambientAlpha),
                                    0.50f to style.sheenColor.copy(alpha = style.sheenColor.alpha * peakAlpha),
                                    0.65f to style.sheenColor.copy(alpha = style.sheenColor.alpha * ambientAlpha),
                                    1.0f to Color.Transparent
                                ),
                                start = Offset(centerX - w * 0.45f, centerY - h * 0.6f),
                                end = Offset(centerX + w * 0.45f, centerY + h * 0.6f)
                            )

                            drawRect(
                                brush = sheenBrush,
                                blendMode = BlendMode.Screen
                            )
                        }
                    }
                }
                .padding(if (format == CardDisplayFormat.HERO) 16.dp else 12.dp)
        ) {
            when (format) {
                CardDisplayFormat.HERO -> {
                    HeroCardContent(card = card, category = category, style = style, isFaded = isFaded)
                }
                CardDisplayFormat.LIST -> {
                    ListCardContent(card = card, category = category, style = style, isFaded = isFaded, appLanguage = appLanguage)
                }
                CardDisplayFormat.COMPACT -> {
                    CompactCardContent(card = card, category = category, style = style, isFaded = isFaded, appLanguage = appLanguage)
                }
            }
        }
    }
}

@Composable
private fun HeroCardContent(
    card: TransitCardUiModel,
    category: CardCategory,
    style: CardStyleDefinition,
    isFaded: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isFaded) Color.Gray else style.badgeBgColor
                ) {
                    Text(
                        text = style.badgeLabel,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = style.badgeTextColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.label,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFE5C158), Color(0xFFBE972C), Color(0xFFF3DF95))
                        )
                    )
                    .border(0.5.dp, Color(0xFF8B6B1B), RoundedCornerShape(4.dp))
            )

            Text(
                text = formatCardNumber(card.cardNumber),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 1.5.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.assignedName.ifBlank { card.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.zonas.isNotBlank()) {
                    Text(
                        text = card.zonas,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Text(
                text = if (isFaded) "Inactiva" else card.remainingValue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = if (isFaded) Color.White.copy(alpha = 0.5f) else Color.White
            )
        }
    }
}

@Composable
private fun ListCardContent(
    card: TransitCardUiModel,
    category: CardCategory,
    style: CardStyleDefinition,
    isFaded: Boolean,
    appLanguage: AppLanguage
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isFaded) Color.Gray else style.badgeBgColor
                ) {
                    Text(
                        text = style.badgeLabel,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        color = style.badgeTextColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = card.assignedName.ifBlank { card.title },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.18f)
            ) {
                Text(
                    text = if (isFaded) "${category.label} (Inactiva)" else category.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = formatCardNumber(card.cardNumber),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                if (card.zonas.isNotBlank()) {
                    Text(
                        text = card.zonas,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val cardAlert = remember(card, appLanguage) {
                    TransitCardAlertManager.evaluateCardAlert(card, appLanguage)
                }
                if (cardAlert != null && !isFaded) {
                    Text(
                        text = cardAlert.titleText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (cardAlert.isCritical) Color(0xFFFF8A80) else Color(0xFFFFD180),
                        modifier = Modifier
                            .background(
                                color = if (cardAlert.isCritical) Color(0xFF8C1D18) else Color(0xFF6B4700),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = if (isFaded) "Inactiva" else card.remainingValue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isFaded) Color.White.copy(alpha = 0.5f) else Color.White
                )
            }
        }
    }
}

@Composable
private fun CompactCardContent(
    card: TransitCardUiModel,
    category: CardCategory,
    style: CardStyleDefinition,
    isFaded: Boolean,
    appLanguage: AppLanguage
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isFaded) Color.Gray else style.badgeBgColor
            ) {
                Text(
                    text = style.badgeLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = style.badgeTextColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }

        Column {
            Text(
                text = card.assignedName.ifBlank { card.title },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (isFaded) "Inactiva" else card.remainingValue.ifBlank { "Consultar saldo" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFaded) Color.White.copy(alpha = 0.5f) else Color.White,
                maxLines = 1
            )

            if (card.zonas.isNotBlank()) {
                Text(
                    text = card.zonas,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}
