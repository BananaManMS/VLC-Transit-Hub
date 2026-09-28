package com.example.ui.metro

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

object TransitLogoUtils {

    @DrawableRes
    fun getMetroLineLogoRes(lineId: String): Int? {
        val clean = lineId.trim().uppercase().removePrefix("L").removePrefix("LÍNEA ").removePrefix("LINEA ")
        return when (clean) {
            "1" -> R.drawable.ic_line_metro_1
            "2" -> R.drawable.ic_line_metro_2
            "3" -> R.drawable.ic_line_metro_3
            "4" -> R.drawable.ic_line_metro_4
            "5" -> R.drawable.ic_line_metro_5
            "6" -> R.drawable.ic_line_metro_6
            "7" -> R.drawable.ic_line_metro_7
            "8" -> R.drawable.ic_line_metro_8
            "9" -> R.drawable.ic_line_metro_9
            "10" -> R.drawable.ic_line_metro_10
            else -> null
        }
    }

    @DrawableRes
    fun getCercaniasLineLogoRes(routeId: String): Int? {
        val clean = routeId.trim().uppercase().replace("-", "").removePrefix("LINEA").trim()
        return when (clean) {
            "C1", "1" -> R.drawable.ic_line_cercanias_c1
            "C2", "2" -> R.drawable.ic_line_cercanias_c2
            "C3", "3" -> R.drawable.ic_line_cercanias_c3
            "C5", "5" -> R.drawable.ic_line_cercanias_c5
            "C6", "6" -> R.drawable.ic_line_cercanias_c6
            else -> null
        }
    }

    fun getMetroLineColor(lineId: String, fallbackColorHex: String? = null): Color {
        val clean = lineId.trim().uppercase().removePrefix("L").removePrefix("LÍNEA ").removePrefix("LINEA ")
        return when (clean) {
            "1" -> Color(0xFFE1A92A) // L1 Amarillo oro
            "2" -> Color(0xFFB3257D) // L2 Magenta / Rosa
            "3" -> Color(0xFFC41833) // L3 Rojo
            "4" -> Color(0xFF1E4B90) // L4 Azul oscuro tranvía
            "5" -> Color(0xFF068E63) // L5 Verde
            "6" -> Color(0xFF7657AA) // L6 Morado tranvía
            "7" -> Color(0xFFDA7A18) // L7 Naranja
            "8" -> Color(0xFF52BACC) // L8 Azul cian tranvía
            "9" -> Color(0xFFA16E42) // L9 Marrón
            "10" -> Color(0xFFB3CB6D) // L10 Verde lima tranvía
            else -> {
                if (!fallbackColorHex.isNullOrBlank()) {
                    try {
                        Color(android.graphics.Color.parseColor(fallbackColorHex))
                    } catch (_: Exception) {
                        Color(0xFFE53935)
                    }
                } else {
                    Color(0xFFE53935)
                }
            }
        }
    }

    fun getCercaniasLineColor(routeId: String): Color {
        val clean = routeId.trim().uppercase().replace("-", "").removePrefix("LINEA").trim()
        return when (clean) {
            "C1", "1" -> Color(0xFF0082C9) // Azul C1
            "C2", "2" -> Color(0xFF00A859) // Verde C2
            "C3", "3" -> Color(0xFFF39200) // Naranja C3
            "C5", "5" -> Color(0xFF95C11E) // Verde Lima C5
            "C6", "6" -> Color(0xFF9B26B6) // Morado C6
            else -> Color(0xFFE51A2E) // Renfe Cercanías Rojo estándar
        }
    }

    fun getCercaniasDisplayName(routeId: String): String {
        val clean = routeId.trim().uppercase().replace("-", "").removePrefix("LINEA").trim()
        return when {
            clean.startsWith("C") -> "C-${clean.removePrefix("C")}"
            clean.all { it.isDigit() } -> "C-$clean"
            else -> routeId
        }
    }
}

@Composable
fun MetroLineBadge(
    lineId: String,
    modifier: Modifier = Modifier,
    fallbackColorHex: String? = null,
    size: Dp = 36.dp
) {
    val logoRes = TransitLogoUtils.getMetroLineLogoRes(lineId)
    if (logoRes != null) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = "Línea $lineId",
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        val clean = lineId.trim().uppercase().removePrefix("L").removePrefix("LÍNEA ").removePrefix("LINEA ")
        val color = TransitLogoUtils.getMetroLineColor(clean, fallbackColorHex)
        val displayText = clean.ifEmpty { lineId }
        val fontSize = if (displayText.length > 2) (size.value * 0.36f).sp else (size.value * 0.46f).sp

        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.25f))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                letterSpacing = if (displayText.length > 1) (-0.5).sp else 0.sp
            )
        }
    }
}

@Composable
fun CercaniasLineBadge(
    routeId: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val logoRes = TransitLogoUtils.getCercaniasLineLogoRes(routeId)
    if (logoRes != null) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = "Línea $routeId",
            modifier = modifier
                .height(size)
                .widthIn(min = size, max = size * 1.6f),
            contentScale = ContentScale.Fit
        )
    } else {
        val color = TransitLogoUtils.getCercaniasLineColor(routeId)
        val displayText = TransitLogoUtils.getCercaniasDisplayName(routeId)
        val fontSize = if (displayText.length > 3) (size.value * 0.32f).sp else (size.value * 0.38f).sp

        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.25f))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )
        }
    }
}
