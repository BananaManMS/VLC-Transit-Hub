package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppColorPreset(
    val id: String,
    val nameEs: String,
    val nameCa: String,
    val subtitleEs: String,
    val subtitleCa: String,
    val defaultPrimaryId: String,
    val defaultSecondaryId: String,
    val defaultWarmSurfaces: Boolean,
    val defaultDarkOnPrimaryWhite: Boolean
) {
    TANGERINA_ARENA(
        id = "tangerina_arena",
        nameEs = "Tangerina y Arena",
        nameCa = "Tangerina i Arena",
        subtitleEs = "Naranja quemado con texto blanco y piedra cálida",
        subtitleCa = "Taronja torrat amb text blanc i pedra càlida",
        defaultPrimaryId = "naranja_quemado",
        defaultSecondaryId = "arena",
        defaultWarmSurfaces = true,
        defaultDarkOnPrimaryWhite = true
    ),
    TANGERINA_MEDITERRANEA(
        id = "tangerina_mediterranea",
        nameEs = "Tangerina y Petróleo",
        nameCa = "Tangerina i Petroli",
        subtitleEs = "Naranja valenciano vivo con azul cerámica mediterráneo",
        subtitleCa = "Taronja valencià viu amb blau ceràmica mediterrani",
        defaultPrimaryId = "tangerina_vivo",
        defaultSecondaryId = "petroleo",
        defaultWarmSurfaces = true,
        defaultDarkOnPrimaryWhite = false
    ),
    ALBARICOQUE_SALVIA(
        id = "albaricoque_salvia",
        nameEs = "Albaricoque y Salvia",
        nameCa = "Albercoc i Sàlvia",
        subtitleEs = "Naranja suave y relajado con verde salvia orgánico",
        subtitleCa = "Taronja suau i relaxat amb verd sàlvia orgànic",
        defaultPrimaryId = "albaricoque",
        defaultSecondaryId = "salvia",
        defaultWarmSurfaces = true,
        defaultDarkOnPrimaryWhite = false
    ),
    TERRACOTA_MARINO(
        id = "terracota_marino",
        nameEs = "Terracota y Marino",
        nameCa = "Terracota i Marí",
        subtitleEs = "Naranja fuerte intenso con azul marino elegante",
        subtitleCa = "Taronja fort intens amb blau marí elegant",
        defaultPrimaryId = "terracota",
        defaultSecondaryId = "azul_marino",
        defaultWarmSurfaces = true,
        defaultDarkOnPrimaryWhite = true
    ),
    TANGERINA_MANISES(
        id = "tangerina_manises",
        nameEs = "Tangerina e Índigo Manises",
        nameCa = "Tangerina i Índigo Manises",
        subtitleEs = "Naranja vibrante con índigo profundo de cerámica",
        subtitleCa = "Taronja vibrant amb índigo profund de ceràmica",
        defaultPrimaryId = "tangerina_vivo",
        defaultSecondaryId = "manises",
        defaultWarmSurfaces = true,
        defaultDarkOnPrimaryWhite = false
    ),
    TANGERINA_TURIA(
        id = "tangerina_turia",
        nameEs = "Tangerina y Turia",
        nameCa = "Tangerina i Túria",
        subtitleEs = "Naranja valenciano con verde botánico del cauce",
        subtitleCa = "Taronja valencià amb verd botànic del llit",
        defaultPrimaryId = "tangerina_vivo",
        defaultSecondaryId = "turia",
        defaultWarmSurfaces = true,
        defaultDarkOnPrimaryWhite = false
    ),
    ROJO_VIATGEM(
        id = "rojo_viatgem",
        nameEs = "Rojo Viatgem",
        nameCa = "Roig Viatgem",
        subtitleEs = "Carmesí clásico con acento azul cielo neutro",
        subtitleCa = "Carmesí clàssic amb accent blau cel neutre",
        defaultPrimaryId = "rojo_viatgem",
        defaultSecondaryId = "cian",
        defaultWarmSurfaces = false,
        defaultDarkOnPrimaryWhite = true
    );

    companion object {
        fun fromId(id: String?): AppColorPreset {
            return entries.firstOrNull { it.id == id } ?: TANGERINA_ARENA
        }
    }
}

data class PrimaryToneOption(
    val id: String,
    val labelEs: String,
    val labelCa: String,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val prefersDarkTextInDarkMode: Boolean
)

data class SecondaryToneOption(
    val id: String,
    val labelEs: String,
    val labelCa: String,
    val darkSecondary: Color,
    val darkOnSecondary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val lightSecondary: Color,
    val lightOnSecondary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color
)

object ColorLabCatalog {
    // Ordenados de naranja más suave a más fuerte (+ referencia carmesí)
    val primaryOptions: List<PrimaryToneOption> = listOf(
        PrimaryToneOption(
            id = "melocoton_suave",
            labelEs = "Melocotón",
            labelCa = "Préssec",
            darkPrimary = Color(0xFFFFAB76),
            darkContainer = Color(0xFF352318),
            darkOnContainer = Color(0xFFFFE6D5),
            lightPrimary = Color(0xFFD9723F),
            lightContainer = Color(0xFFFFF3EB),
            lightOnContainer = Color(0xFF7C3A1B),
            prefersDarkTextInDarkMode = true
        ),
        PrimaryToneOption(
            id = "albaricoque",
            labelEs = "Albaricoque",
            labelCa = "Albercoc",
            darkPrimary = Color(0xFFFF944D),
            darkContainer = Color(0xFF362012),
            darkOnContainer = Color(0xFFFFE0C8),
            lightPrimary = Color(0xFFE06820),
            lightContainer = Color(0xFFFFF1E6),
            lightOnContainer = Color(0xFF8A370B),
            prefersDarkTextInDarkMode = true
        ),
        PrimaryToneOption(
            id = "naranja_miel",
            labelEs = "Miel",
            labelCa = "Mel",
            darkPrimary = Color(0xFFFB923C),
            darkContainer = Color(0xFF36210F),
            darkOnContainer = Color(0xFFFFDFC2),
            lightPrimary = Color(0xFFD97706),
            lightContainer = Color(0xFFFEF3C7),
            lightOnContainer = Color(0xFF92400E),
            prefersDarkTextInDarkMode = true
        ),
        PrimaryToneOption(
            id = "naranja_sol",
            labelEs = "Sol",
            labelCa = "Sol",
            darkPrimary = Color(0xFFF97316),
            darkContainer = Color(0xFF361F0D),
            darkOnContainer = Color(0xFFFFDEC2),
            lightPrimary = Color(0xFFEA580C),
            lightContainer = Color(0xFFFFF2E5),
            lightOnContainer = Color(0xFF9A3412),
            prefersDarkTextInDarkMode = true
        ),
        PrimaryToneOption(
            id = "tangerina_vivo",
            labelEs = "Tangerina",
            labelCa = "Tangerina",
            darkPrimary = Color(0xFFFF6B2C),
            darkContainer = Color(0xFF381C10),
            darkOnContainer = Color(0xFFFFD6C2),
            lightPrimary = Color(0xFFE85012),
            lightContainer = Color(0xFFFFF0E6),
            lightOnContainer = Color(0xFF9A3412),
            prefersDarkTextInDarkMode = true
        ),
        PrimaryToneOption(
            id = "mandarina_intensa",
            labelEs = "Mandarina",
            labelCa = "Mandarina",
            darkPrimary = Color(0xFFFF551A),
            darkContainer = Color(0xFF3B180D),
            darkOnContainer = Color(0xFFFFD2C2),
            lightPrimary = Color(0xFFDC440E),
            lightContainer = Color(0xFFFFECE6),
            lightOnContainer = Color(0xFF8F2605),
            prefersDarkTextInDarkMode = false
        ),
        PrimaryToneOption(
            id = "coral_mandarina",
            labelEs = "Coral",
            labelCa = "Coral",
            darkPrimary = Color(0xFFFF5A36),
            darkContainer = Color(0xFF3B1912),
            darkOnContainer = Color(0xFFFFD4CA),
            lightPrimary = Color(0xFFDC3E1B),
            lightContainer = Color(0xFFFFECE8),
            lightOnContainer = Color(0xFF94240D),
            prefersDarkTextInDarkMode = false
        ),
        PrimaryToneOption(
            id = "naranja_quemado",
            labelEs = "Quemado",
            labelCa = "Torrat",
            darkPrimary = Color(0xFFEA580C),
            darkContainer = Color(0xFF3A1D0E),
            darkOnContainer = Color(0xFFFFDBC8),
            lightPrimary = Color(0xFFC84308),
            lightContainer = Color(0xFFFFEFE6),
            lightOnContainer = Color(0xFF822804),
            prefersDarkTextInDarkMode = false
        ),
        PrimaryToneOption(
            id = "terracota",
            labelEs = "Terracota",
            labelCa = "Terracota",
            darkPrimary = Color(0xFFD94E28),
            darkContainer = Color(0xFF361912),
            darkOnContainer = Color(0xFFFFD5C8),
            lightPrimary = Color(0xFFB83B19),
            lightContainer = Color(0xFFFCECE7),
            lightOnContainer = Color(0xFF7A220C),
            prefersDarkTextInDarkMode = false
        ),
        PrimaryToneOption(
            id = "cobre_fuerte",
            labelEs = "Cobre",
            labelCa = "Coure",
            darkPrimary = Color(0xFFC2410C),
            darkContainer = Color(0xFF33170C),
            darkOnContainer = Color(0xFFFFD4C0),
            lightPrimary = Color(0xFF9A3412),
            lightContainer = Color(0xFFFDECE4),
            lightOnContainer = Color(0xFF6B2108),
            prefersDarkTextInDarkMode = false
        ),
        PrimaryToneOption(
            id = "rojo_viatgem",
            labelEs = "Carmesí",
            labelCa = "Carmesí",
            darkPrimary = Color(0xFFF43F5E),
            darkContainer = Color(0xFF3D1120),
            darkOnContainer = Color(0xFFFFE4E9),
            lightPrimary = Color(0xFFEC1B4B),
            lightContainer = Color(0xFFFCE7EE),
            lightOnContainer = Color(0xFF9F1239),
            prefersDarkTextInDarkMode = false
        )
    )

    val secondaryOptions: List<SecondaryToneOption> = listOf(
        SecondaryToneOption(
            id = "petroleo",
            labelEs = "Petróleo",
            labelCa = "Petroli",
            darkSecondary = Color(0xFF2CC4D6),
            darkOnSecondary = Color(0xFF071F24),
            darkContainer = Color(0xFF102B31),
            darkOnContainer = Color(0xFFBFF2F8),
            lightSecondary = Color(0xFF0E7490),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFE6F6F9),
            lightOnContainer = Color(0xFF0B4F63)
        ),
        SecondaryToneOption(
            id = "mediterraneo",
            labelEs = "Mediterráneo",
            labelCa = "Mediterrani",
            darkSecondary = Color(0xFF47A3F3),
            darkOnSecondary = Color(0xFF091D30),
            darkContainer = Color(0xFF12283E),
            darkOnContainer = Color(0xFFD6ECFF),
            lightSecondary = Color(0xFF1D64A8),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFE6F2FC),
            lightOnContainer = Color(0xFF124273)
        ),
        SecondaryToneOption(
            id = "azul_marino",
            labelEs = "Marino",
            labelCa = "Marí",
            darkSecondary = Color(0xFF6B9BF5),
            darkOnSecondary = Color(0xFF0D1B38),
            darkContainer = Color(0xFF172540),
            darkOnContainer = Color(0xFFDBE8FF),
            lightSecondary = Color(0xFF1E40AF),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFE8F0FE),
            lightOnContainer = Color(0xFF172E7A)
        ),
        SecondaryToneOption(
            id = "manises",
            labelEs = "Manises",
            labelCa = "Manises",
            darkSecondary = Color(0xFF818CF8),
            darkOnSecondary = Color(0xFF11132E),
            darkContainer = Color(0xFF22254A),
            darkOnContainer = Color(0xFFE0E7FF),
            lightSecondary = Color(0xFF4338CA),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFEEF2FF),
            lightOnContainer = Color(0xFF312E81)
        ),
        SecondaryToneOption(
            id = "violeta_noche",
            labelEs = "Violeta",
            labelCa = "Violeta",
            darkSecondary = Color(0xFFA78BFA),
            darkOnSecondary = Color(0xFF1B1233),
            darkContainer = Color(0xFF261E40),
            darkOnContainer = Color(0xFFEDE9FE),
            lightSecondary = Color(0xFF6D28D9),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFF3EEFF),
            lightOnContainer = Color(0xFF4C1D95)
        ),
        SecondaryToneOption(
            id = "turia",
            labelEs = "Turia",
            labelCa = "Túria",
            darkSecondary = Color(0xFF34D399),
            darkOnSecondary = Color(0xFF072219),
            darkContainer = Color(0xFF122E24),
            darkOnContainer = Color(0xFFD1FAE5),
            lightSecondary = Color(0xFF0F766E),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFE6F7F4),
            lightOnContainer = Color(0xFF115E59)
        ),
        SecondaryToneOption(
            id = "salvia",
            labelEs = "Salvia",
            labelCa = "Sàlvia",
            darkSecondary = Color(0xFF86B89A),
            darkOnSecondary = Color(0xFF112118),
            darkContainer = Color(0xFF1C2B22),
            darkOnContainer = Color(0xFFDCEEE3),
            lightSecondary = Color(0xFF3F7054),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFEAF4EE),
            lightOnContainer = Color(0xFF274A36)
        ),
        SecondaryToneOption(
            id = "oliva",
            labelEs = "Oliva",
            labelCa = "Oliva",
            darkSecondary = Color(0xFFA3B86C),
            darkOnSecondary = Color(0xFF1A210D),
            darkContainer = Color(0xFF262C1A),
            darkOnContainer = Color(0xFFE7F0D0),
            lightSecondary = Color(0xFF5C6E2E),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFF1F5E6),
            lightOnContainer = Color(0xFF3D4A1C)
        ),
        SecondaryToneOption(
            id = "arena",
            labelEs = "Arena",
            labelCa = "Arena",
            darkSecondary = Color(0xFFD6C3B0),
            darkOnSecondary = Color(0xFF221A12),
            darkContainer = Color(0xFF332B23),
            darkOnContainer = Color(0xFFF5EBE0),
            lightSecondary = Color(0xFF7C6A58),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFEFE8DF),
            lightOnContainer = Color(0xFF4A3E33)
        ),
        SecondaryToneOption(
            id = "oro_suave",
            labelEs = "Ámbar",
            labelCa = "Ambre",
            darkSecondary = Color(0xFFFBBF24),
            darkOnSecondary = Color(0xFF261A05),
            darkContainer = Color(0xFF332710),
            darkOnContainer = Color(0xFFFEF3C7),
            lightSecondary = Color(0xFFB45309),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFFEF3C7),
            lightOnContainer = Color(0xFF78350F)
        ),
        SecondaryToneOption(
            id = "pizarra",
            labelEs = "Pizarra",
            labelCa = "Pissarra",
            darkSecondary = Color(0xFF94A3B8),
            darkOnSecondary = Color(0xFF111827),
            darkContainer = Color(0xFF232A35),
            darkOnContainer = Color(0xFFE2E8F0),
            lightSecondary = Color(0xFF475569),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFEEF2F6),
            lightOnContainer = Color(0xFF1E293B)
        ),
        SecondaryToneOption(
            id = "cian",
            labelEs = "Cian",
            labelCa = "Cian",
            darkSecondary = Color(0xFF38BDF8),
            darkOnSecondary = Color(0xFF082032),
            darkContainer = Color(0xFF0E2C3E),
            darkOnContainer = Color(0xFFE0F2FE),
            lightSecondary = Color(0xFF0284C7),
            lightOnSecondary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFE0F2FE),
            lightOnContainer = Color(0xFF075985)
        )
    )

    fun primaryById(id: String): PrimaryToneOption {
        return primaryOptions.firstOrNull { it.id == id } ?: primaryOptions.first()
    }

    fun secondaryById(id: String): SecondaryToneOption {
        return secondaryOptions.firstOrNull { it.id == id } ?: secondaryOptions.first()
    }
}

data class ColorLabConfig(
    val presetId: String = AppColorPreset.TANGERINA_ARENA.id,
    val primaryId: String = AppColorPreset.TANGERINA_ARENA.defaultPrimaryId,
    val secondaryId: String = AppColorPreset.TANGERINA_ARENA.defaultSecondaryId,
    val warmSurfaces: Boolean = AppColorPreset.TANGERINA_ARENA.defaultWarmSurfaces,
    val darkOnPrimaryWhite: Boolean = AppColorPreset.TANGERINA_ARENA.defaultDarkOnPrimaryWhite
) {
    val activePreset: AppColorPreset?
        get() {
            val candidate = AppColorPreset.entries.firstOrNull { it.id == presetId } ?: return null
            val matches = candidate.defaultPrimaryId == primaryId &&
                candidate.defaultSecondaryId == secondaryId &&
                candidate.defaultWarmSurfaces == warmSurfaces &&
                candidate.defaultDarkOnPrimaryWhite == darkOnPrimaryWhite
            return if (matches) candidate else null
        }

    fun serialize(): String {
        return listOf(
            presetId,
            primaryId,
            secondaryId,
            warmSurfaces.toString(),
            darkOnPrimaryWhite.toString()
        ).joinToString("|")
    }

    companion object {
        val Default = ColorLabConfig()

        fun fromPreset(preset: AppColorPreset): ColorLabConfig {
            return ColorLabConfig(
                presetId = preset.id,
                primaryId = preset.defaultPrimaryId,
                secondaryId = preset.defaultSecondaryId,
                warmSurfaces = preset.defaultWarmSurfaces,
                darkOnPrimaryWhite = preset.defaultDarkOnPrimaryWhite
            )
        }

        fun deserialize(raw: String?): ColorLabConfig {
            if (raw.isNullOrBlank()) return Default
            val parts = raw.split("|")
            if (parts.size < 5) return Default
            val preset = AppColorPreset.fromId(parts[0])
            val primary = ColorLabCatalog.primaryById(parts[1]).id
            val secondary = ColorLabCatalog.secondaryById(parts[2]).id
            val warm = parts[3].toBooleanStrictOrNull() ?: preset.defaultWarmSurfaces
            val onPrimaryWhite = parts[4].toBooleanStrictOrNull() ?: preset.defaultDarkOnPrimaryWhite
            return ColorLabConfig(
                presetId = preset.id,
                primaryId = primary,
                secondaryId = secondary,
                warmSurfaces = warm,
                darkOnPrimaryWhite = onPrimaryWhite
            )
        }
    }
}

fun buildAppColorScheme(
    config: ColorLabConfig,
    darkTheme: Boolean
): ColorScheme {
    val primaryOpt = ColorLabCatalog.primaryById(config.primaryId)
    val secondaryOpt = ColorLabCatalog.secondaryById(config.secondaryId)

    return if (darkTheme) {
        val bg = if (config.warmSurfaces) Color(0xFF161412) else Color(0xFF171717)
        val surfaceLow = if (config.warmSurfaces) Color(0xFF1B1916) else Color(0xFF1C1C1C)
        val surface = if (config.warmSurfaces) Color(0xFF211E1B) else Color(0xFF222222)
        val surfaceVar = if (config.warmSurfaces) Color(0xFF2C2824) else Color(0xFF2C2C2C)
        val outline = if (config.warmSurfaces) Color(0xFF47403A) else Color(0xFF404040)
        val outlineVar = if (config.warmSurfaces) Color(0xFF36302B) else Color(0xFF333333)
        val onSurfaceVar = if (config.warmSurfaces) Color(0xFFA8A098) else Color(0xFFA3A3A3)

        val onPrimaryDark = if (config.darkOnPrimaryWhite) Color(0xFFFFFFFF) else Color(0xFF1A0D05)

        darkColorScheme(
            background = bg,
            surface = surface,
            surfaceVariant = surfaceVar,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surfaceVar,
            primary = primaryOpt.darkPrimary,
            onPrimary = onPrimaryDark,
            primaryContainer = primaryOpt.darkContainer,
            onPrimaryContainer = primaryOpt.darkOnContainer,
            secondary = secondaryOpt.darkSecondary,
            onSecondary = secondaryOpt.darkOnSecondary,
            secondaryContainer = secondaryOpt.darkContainer,
            onSecondaryContainer = secondaryOpt.darkOnContainer,
            tertiary = Color(0xFFF59E0B),
            onTertiary = Color(0xFF1A1205),
            tertiaryContainer = Color(0xFF302412),
            onTertiaryContainer = Color(0xFFFEF3C7),
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF8FAFC),
            onSurfaceVariant = onSurfaceVar,
            error = Color(0xFFF87171),
            errorContainer = Color(0xFF33181A),
            onError = Color(0xFFFFFFFF),
            onErrorContainer = Color(0xFFFECACA),
            outline = outline,
            outlineVariant = outlineVar
        )
    } else {
        val bg = if (config.warmSurfaces) Color(0xFFFAF8F5) else Color(0xFFFAFAFA)
        val surfaceVar = if (config.warmSurfaces) Color(0xFFF3EFEA) else Color(0xFFF1F5F9)
        val outline = if (config.warmSurfaces) Color(0xFF9A9086) else Color(0xFF94A3B8)
        val outlineVar = if (config.warmSurfaces) Color(0xFFE6DFD7) else Color(0xFFE2E8F0)
        val onSurfaceVar = if (config.warmSurfaces) Color(0xFF574F47) else Color(0xFF475569)

        lightColorScheme(
            background = bg,
            surface = Color(0xFFFFFFFF),
            surfaceVariant = surfaceVar,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = bg,
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFFFFFFF),
            surfaceContainerHighest = surfaceVar,
            primary = primaryOpt.lightPrimary,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = primaryOpt.lightContainer,
            onPrimaryContainer = primaryOpt.lightOnContainer,
            secondary = secondaryOpt.lightSecondary,
            onSecondary = secondaryOpt.lightOnSecondary,
            secondaryContainer = secondaryOpt.lightContainer,
            onSecondaryContainer = secondaryOpt.lightOnContainer,
            tertiary = Color(0xFFD97706),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFEF3C7),
            onTertiaryContainer = Color(0xFF92400E),
            onBackground = Color(0xFF171412),
            onSurface = Color(0xFF171412),
            onSurfaceVariant = onSurfaceVar,
            error = Color(0xFFDC2626),
            errorContainer = Color(0xFFFEE2E2),
            onError = Color(0xFFFFFFFF),
            onErrorContainer = Color(0xFF991B1B),
            outline = outline,
            outlineVariant = outlineVar
        )
    }
}
