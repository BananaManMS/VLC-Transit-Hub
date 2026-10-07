package com.example.ui.map.networkmaps

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.ui.dashboard.AppLanguage

sealed interface MapPlanItem {
    val id: String
    val fileName: String
    val url: String
    val colorLight: Color
    val colorDark: Color
    val isPdf: Boolean
    @get:DrawableRes val logoDrawableRes: Int?
    fun getTitle(appLanguage: AppLanguage): String
    fun getSubtitle(appLanguage: AppLanguage): String
    fun getColor(isDarkMode: Boolean): Color = if (isDarkMode) colorDark else colorLight
}

enum class NetworkMapId(
    override val id: String,
    val esTitle: String,
    val caTitle: String,
    val esSubtitle: String,
    val caSubtitle: String,
    override val fileName: String,
    override val url: String,
    override val colorLight: Color,
    override val colorDark: Color,
    val transitType: String,
    @DrawableRes override val logoDrawableRes: Int? = null
) : MapPlanItem {
    METROVALENCIA(
        id = "metrovalencia",
        esTitle = "Plano Metrovalencia",
        caTitle = "Plànol Metrovalencia",
        esSubtitle = "Red de metro y tranvía de Valencia",
        caSubtitle = "Xarxa de metro i tramvia de València",
        fileName = "Plano_Zonal_Metrovalencia.pdf",
        url = "https://raw.githubusercontent.com/BananaManMS/metro_valencia_schedule/mapas/Plano_Zonal_Metrovalencia.pdf",
        colorLight = Color(0xFFE51D2D),
        colorDark = Color(0xFFEF5350),
        transitType = "METRO",
        logoDrawableRes = R.drawable.logo_metrovalencia
    ),
    CERCANIAS(
        id = "cercanias",
        esTitle = "Plano Cercanías Renfe",
        caTitle = "Plànol Rodalies Renfe",
        esSubtitle = "Líneas de tren de cercanías de Valencia",
        caSubtitle = "Línies de tren de rodalies de València",
        fileName = "Plano_Zonal_Cercanias.pdf",
        url = "https://raw.githubusercontent.com/BananaManMS/metro_valencia_schedule/mapas/Plano_Zonal_Cercanias.pdf",
        colorLight = Color(0xFFEF2C30),
        colorDark = Color(0xFFF87171),
        transitType = "CERCANIAS",
        logoDrawableRes = R.drawable.logo_cercanias
    ),
    METROBUS(
        id = "metrobus",
        esTitle = "Plano Metrobús",
        caTitle = "Plànol Metrobús",
        esSubtitle = "Autobuses metropolitanos e interurbanos",
        caSubtitle = "Autobusos metropolitans i interurbans",
        fileName = "Plano_Zonal_Metrobus.pdf",
        url = "https://raw.githubusercontent.com/BananaManMS/metro_valencia_schedule/mapas/Plano_Zonal_Metrobus.pdf",
        colorLight = Color(0xFFF59E0B),
        colorDark = Color(0xFFFBBF24),
        transitType = "METROBUS",
        logoDrawableRes = R.drawable.logo_metrobus
    ),
    EMT(
        id = "emt",
        esTitle = "Plano EMT Valencia",
        caTitle = "Plànol EMT València",
        esSubtitle = "Líneas y red urbana de autobuses",
        caSubtitle = "Línies i xarxa urbana d'autobusos",
        fileName = "Plano_EMT.pdf",
        url = "https://raw.githubusercontent.com/BananaManMS/metro_valencia_schedule/mapas/Plano_EMT.pdf",
        colorLight = Color(0xFFEB0029),
        colorDark = Color(0xFFFB7185),
        transitType = "EMT",
        logoDrawableRes = R.drawable.logo_emt_valencia
    ),
    COMUN_SUMA(
        id = "comun_suma",
        esTitle = "Plano Común Integrado",
        caTitle = "Plànol Comú Integrat",
        esSubtitle = "Mapa conjunto y zonificación SUMA",
        caSubtitle = "Mapa conjunt i zonificació SUMA",
        fileName = "Plano_Comun.pdf",
        url = "https://raw.githubusercontent.com/BananaManMS/metro_valencia_schedule/mapas/Plano_Comun.pdf",
        colorLight = Color(0xFF7C3AED),
        colorDark = Color(0xFFA78BFA),
        transitType = "SUMA",
        logoDrawableRes = null
    );

    override val isPdf: Boolean get() = true

    override fun getTitle(appLanguage: AppLanguage): String = if (appLanguage == AppLanguage.CA) caTitle else esTitle
    override fun getSubtitle(appLanguage: AppLanguage): String = if (appLanguage == AppLanguage.CA) caSubtitle else esSubtitle
}

enum class InterchangeMapId(
    override val id: String,
    val esTitle: String,
    val caTitle: String,
    val esSubtitle: String,
    val caSubtitle: String,
    override val fileName: String,
    override val url: String,
    val metroLines: List<String>,
    val cercaniasLines: List<String> = emptyList(),
    override val colorLight: Color = Color(0xFF6366F1),
    override val colorDark: Color = Color(0xFF818CF8),
    @DrawableRes override val logoDrawableRes: Int? = null
) : MapPlanItem {
    XATIVA_BAILEN_ALACANT(
        id = "interchange_xativa_bailen_alacant",
        esTitle = "Xàtiva · Bailén · Alacant",
        caTitle = "Xàtiva · Bailén · Alacant",
        esSubtitle = "Metro, Tranvía y Renfe Nord",
        caSubtitle = "Metro, Tramvia i Renfe Nord",
        fileName = "xativa_bailen_alacant.png",
        url = "https://github.com/BananaManMS/metro_valencia_schedule/raw/mapas/xativa_bailen_alacant.png",
        metroLines = listOf("3", "5", "7", "9", "10"),
        cercaniasLines = listOf("C1", "C2", "C3", "C5", "C6")
    ),
    ANGEL_GUIMERA(
        id = "interchange_angel_guimera",
        esTitle = "Àngel Guimerà",
        caTitle = "Àngel Guimerà",
        esSubtitle = "5 líneas en doble nivel",
        caSubtitle = "5 línies en doble nivell",
        fileName = "angel_guimera.png",
        url = "https://github.com/BananaManMS/metro_valencia_schedule/raw/mapas/angel_guimera.png",
        metroLines = listOf("1", "2", "3", "5", "9")
    ),
    ALAMEDA(
        id = "interchange_alameda",
        esTitle = "Alameda",
        caTitle = "Alameda",
        esSubtitle = "Estación subterránea de 4 vías",
        caSubtitle = "Estació subterrània de 4 vies",
        fileName = "alameda.png",
        url = "https://github.com/BananaManMS/metro_valencia_schedule/raw/mapas/alameda.png",
        metroLines = listOf("3", "5", "7", "9")
    ),
    BENIMACLET(
        id = "interchange_benimaclet",
        esTitle = "Benimaclet",
        caTitle = "Benimaclet",
        esSubtitle = "Metro subterráneo y Tranvía",
        caSubtitle = "Metro subterrani i Tramvia",
        fileName = "benimaclet.png",
        url = "https://github.com/BananaManMS/metro_valencia_schedule/raw/mapas/benimaclet.png",
        metroLines = listOf("3", "9", "4", "6")
    ),
    EMPALME(
        id = "interchange_empalme",
        esTitle = "Empalme",
        caTitle = "Empalme",
        esSubtitle = "Bifurcación Metro y Tranvía",
        caSubtitle = "Bifurcació Metro i Tramvia",
        fileName = "empalme.png",
        url = "https://github.com/BananaManMS/metro_valencia_schedule/raw/mapas/empalme.png",
        metroLines = listOf("1", "2", "4")
    ),
    MARITIM(
        id = "interchange_maritim",
        esTitle = "Marítim",
        caTitle = "Marítim",
        esSubtitle = "Enlace Metro y Tranvía marítimo",
        caSubtitle = "Enllaç Metro i Tramvia marítim",
        fileName = "maritim_serreria.png",
        url = "https://github.com/BananaManMS/metro_valencia_schedule/raw/mapas/maritim_serreria.png",
        metroLines = listOf("5", "7", "6", "8")
    ),
    VALENCIA_SANT_ISIDRE(
        id = "interchange_sant_isidre",
        esTitle = "València Sant Isidre",
        caTitle = "València Sant Isidre",
        esSubtitle = "Metro y Cercanías Renfe C-3",
        caSubtitle = "Metro i Rodalies Renfe C-3",
        fileName = "sant_isidre.png",
        url = "https://github.com/BananaManMS/metro_valencia_schedule/raw/mapas/sant_isidre.png",
        metroLines = listOf("1", "2", "7"),
        cercaniasLines = listOf("C3")
    );

    override val isPdf: Boolean get() = false

    override fun getTitle(appLanguage: AppLanguage): String = if (appLanguage == AppLanguage.CA) caTitle else esTitle
    override fun getSubtitle(appLanguage: AppLanguage): String = if (appLanguage == AppLanguage.CA) caSubtitle else esSubtitle
}
