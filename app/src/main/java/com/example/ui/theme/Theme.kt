package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

private val DarkColorScheme = darkColorScheme(
    background = Color(0xFF171717),
    surface = Color(0xFF222222),
    surfaceVariant = Color(0xFF2C2C2C),
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFFFFFFF), // Pure white text/icons on primaryContainer for crisp contrast
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF222222),
    onSecondaryContainer = Color(0xFFF8FAFC),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF171717),
    tertiaryContainer = Color(0xFF0369A1),
    onTertiaryContainer = Color(0xFFFFFFFF),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFA3A3A3),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF3F1F1F),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFCA5A5),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF333333)
)

private val LightColorScheme = lightColorScheme(
    background = Color(0xFFFAFAFA), // Blanco roto #FAFAFA neutral background
    surface = Color(0xFFFFFFFF),    // Pure white cards
    surfaceVariant = Color(0xFFF1F5F9),
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
    outlineVariant = Color(0xFFE2E8F0)
)

// ============================================================================
// 2. TIPOGRAFÍA UNIFICADA (Fuentes Locales TTF con Pesos Específicos)
// ============================================================================
// Space Grotesk para títulos, encabezados y displays
val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk_w300, weight = FontWeight.Light),
    Font(R.font.space_grotesk_w400, weight = FontWeight.Normal),
    Font(R.font.space_grotesk_w500, weight = FontWeight.Medium),
    Font(R.font.space_grotesk_w600, weight = FontWeight.SemiBold),
    Font(R.font.space_grotesk_w700, weight = FontWeight.Bold),
    Font(R.font.space_grotesk_w700, weight = FontWeight.ExtraBold)
)

// Plus Jakarta Sans para cuerpo de texto y etiquetas
val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_w400, weight = FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_w500, weight = FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_w600, weight = FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_w700, weight = FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_w800, weight = FontWeight.ExtraBold)
)

val AppTypography = Typography(
    // Large -> Bold / ExtraBold con máxima presencia visual
    displayLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-1.0).sp
    ),
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = (-0.25).sp
    ),
    // Titles: Large -> Bold, Medium -> Medium, Small -> Normal
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = (-0.15).sp
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    // Cuerpo de texto (Plus Jakarta Sans)
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    ),
    // Etiquetas (Plus Jakarta Sans)
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp
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
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    if (title.isBlank() && subtitle.isNullOrBlank()) {
        if (onBackClick != null) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, top = 2.dp, end = 0.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    } else {
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
                    fontFamily = SpaceGroteskFontFamily,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

/**
 * Standard solid high-contrast border for app cards (WCAG 3:1 compliant).
 * In clean design mode, natural cards have no border; subtle contrast and elevation
 * separate content cleanly without decorative clutter.
 */
@Composable
fun appCardBorder(): BorderStroke? {
    return null
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
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
            // Start: Icono o Badge (fijado con altura de 36dp y ancho adaptable)
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 36.dp, max = 56.dp),
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
// 4.5. PALETA UNIFICADA DE SUPERFICIES Y TARJETAS
// ============================================================================
object AppThemeColors {
    fun sheetBackground(isDark: Boolean): Color = if (isDark) Color(0xFF171717) else Color(0xFFFAFAFA)
    fun cardBackground(isDark: Boolean): Color = if (isDark) Color(0xFF222222) else Color(0xFFFFFFFF)
    fun elevatedCardBackground(isDark: Boolean): Color = if (isDark) Color(0xFF2C2C2C) else Color(0xFFFFFFFF)
    fun overlayBackground(isDark: Boolean, alpha: Float = 0.95f): Color =
        if (isDark) Color(0xFF171717).copy(alpha = alpha) else Color(0xFFFFFFFF).copy(alpha = alpha)
    fun floatingButtonBackground(isDark: Boolean): Color = if (isDark) Color(0xFF222222) else Color(0xFFFFFFFF)
    fun floatingButtonContent(isDark: Boolean): Color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    fun subtleBorder(isDark: Boolean): Color = if (isDark) Color(0xFF333333) else Color(0xFFE2E8F0)
    fun textPrimary(isDark: Boolean): Color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    fun textSecondary(isDark: Boolean): Color = if (isDark) Color(0xFFA3A3A3) else Color(0xFF64748B)
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
                window.statusBarColor = if (darkTheme) android.graphics.Color.parseColor("#171717") else android.graphics.Color.parseColor("#FAFAFA")
                window.navigationBarColor = if (darkTheme) android.graphics.Color.parseColor("#000000") else android.graphics.Color.parseColor("#FFFFFF")
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
