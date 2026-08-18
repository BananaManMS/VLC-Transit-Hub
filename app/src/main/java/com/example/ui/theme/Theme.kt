package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

// ============================================================================
// 1. COLORES ESTÁTICOS DE TRANSPORTE Y ESTADO
// ============================================================================
object TransportTokens {
    val MetroDefault = Color(0xFF4F8CFF)
    val EmtRed = Color(0xFFE53935)
    val CercaniasOrange = Color(0xFF702B7B) // Official Renfe Purple (mapped to Orange token for backwards compatibility)

    // Líneas oficiales de Metrovalencia
    val Line1 = Color(0xFFFDCB06)
    val Line2 = Color(0xFFEC008C)
    val Line3 = Color(0xFFE2001A)
    val Line4 = Color(0xFF1FA6A0)
    val Line5 = Color(0xFF009639)
    val Line6 = Color(0xFF7B3FE4)
    val Line7 = Color(0xFFF97316)
    val Line8 = Color(0xFF14B8A6)
    val Line9 = Color(0xFF7A4A2A)
    val Line10 = Color(0xFF84CC16)

    // Estados de servicio
    val LiveStatus = Color(0xFF2ECC71)      // Verde: En directo
    val DelayStatus = Color(0xFFFF9800)     // Naranja: Retraso
    val IncidentStatus = Color(0xFFE74C3C)  // Rojo: Incidencia
}

private val DarkColorScheme = darkColorScheme(
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155),
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFFFFFFF), // Pure white text/icons on primaryContainer for crisp contrast
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFF8FAFC),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF0369A1),
    onTertiaryContainer = Color(0xFFFFFFFF),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF3F1F1F),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFCA5A5),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155) // Solid Slate 700 for accessible contrast
)

private val LightColorScheme = lightColorScheme(
    background = Color(0xFFF1F5F9), // Slate 100 (Slightly darker neutral background for high contrast with white cards)
    surface = Color(0xFFFFFFFF),    // Pure white cards
    surfaceVariant = Color(0xFFE2E8F0),
    primary = Color(0xFF2563EB),
    primaryContainer = Color(0xFFEFF6FF),
    secondary = Color(0xFF3B82F6),
    secondaryContainer = Color(0xFFDBEAFE),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF1D4ED8),
    onSecondaryContainer = Color(0xFF1E40AF),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF991B1B),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1) // Solid Slate 300 for high-contrast card borders (WCAG 3:1 compliant)
)

// ============================================================================
// 2. TIPOGRAFÍA UNIFICADA (Google Fonts)
// ============================================================================
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Space Grotesk para títulos y encabezados
val SpaceGroteskFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = fontProvider, weight = FontWeight.ExtraBold)
)

// Plus Jakarta Sans para texto plano, cuerpo y etiquetas
val PlusJakartaSansFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.ExtraBold)
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = (-1.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp
    )
)

// Estilo especial para temporizadores en directo (números tabulares) basados en Space Grotesk
val LiveTimerStyle = TextStyle(
    fontFamily = SpaceGroteskFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 15.sp,
    fontFeatureSettings = "tnum"
)

// ============================================================================
// 3. DIMENSIONES Y ESPACIADOS
// ============================================================================
data class Dimensions(
    val screenPadding: Dp = 16.dp,
    val cardPadding: Dp = 14.dp,
    val cardCorner: Dp = 16.dp,
    val badgeCorner: Dp = 8.dp,
    val containerCorner: Dp = 24.dp,
    val sheetCorner: Dp = 28.dp,
    val badgeSize: Dp = 36.dp
)

val LocalAppDimens = staticCompositionLocalOf { Dimensions() }

// ============================================================================
// 4. COMPONENTES VISUALES UNIFICADOS
// ============================================================================

/**
 * Plantilla maestra de cabecera de pantalla estándar.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 0.dp, top = 4.dp, end = 0.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * TabRow unificado de Material 3 para pantallas con pestañas secundarias (Metro, Cercanías, etc.).
 */
@Composable
fun UnifiedTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    modifier: Modifier = Modifier,
    onTabSelected: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            if (selectedTabIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, title ->
            val capitalizedTitle = when {
                title.equals("emt", ignoreCase = true) -> "EMT"
                title.all { it.isUpperCase() && it.isLetter() } -> title
                else -> title.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = capitalizedTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

/**
 * Standard solid high-contrast border for app cards (WCAG 3:1 compliant).
 */
@Composable
fun appCardBorder(): BorderStroke {
    return BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Plantilla de Tarjeta Estándar (UnifiedAppCard).
 * Estructura interna de 3 columnas: [Start: Icono/Badge] - [Center: Título + Subtítulo] - [End: Dato clave/Acción].
 */
@Composable
fun UnifiedAppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    startContent: @Composable () -> Unit,
    centerContent: @Composable () -> Unit,
    endContent: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    val isDark = isSystemInDarkTheme()
    Card(
        modifier = clickableModifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 3.dp),
        border = appCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start: Icono o Badge (fijado a un contenedor de 36dp)
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                startContent()
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Título + Subtítulo (Weight 1f)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                centerContent()
            }

            Spacer(modifier = Modifier.width(14.dp))

            // End: Dato clave (Minutos, Switches, Acciones, etc.)
            Box(
                contentAlignment = Alignment.CenterEnd
            ) {
                endContent()
            }
        }
    }
}

// ============================================================================
// 5. CONFIGURACIÓN DEL TEMA GLOBAL
// ============================================================================
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Desactivado para mantener coherencia de transporte
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalAppDimens provides Dimensions()
    ) {
        val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

@Composable
fun VlcMetroTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MyApplicationTheme(darkTheme = darkTheme, content = content)
}
