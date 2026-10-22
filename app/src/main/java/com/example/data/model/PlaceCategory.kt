package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.dashboard.AppLanguage

enum class PlaceCategory(
    val icon: ImageVector,
    val brandColor: Color
) {
    TRANSIT(Icons.Default.DirectionsBus, Color(0xFF0284C7)),
    HERITAGE(Icons.Default.Place, Color(0xFF8B5CF6)),
    ATTRACTION_CULTURE(Icons.Default.Place, Color(0xFF8B5CF6)),
    COMMERCIAL(Icons.Default.ShoppingCart, Color(0xFFEC4899)),
    PARK(Icons.Default.Map, Color(0xFF10B981)),
    GENERAL(Icons.Default.Place, Color(0xFF64748B)),
    FAVORITE(Icons.Default.Favorite, Color(0xFFEF4444));

    fun getLabel(appLanguage: AppLanguage): String {
        val isValencian = appLanguage == AppLanguage.CA
        return when (this) {
            TRANSIT -> if (isValencian) "Trànsit" else "Tránsito"
            HERITAGE -> if (isValencian) "Patrimoni" else "Patrimonio"
            ATTRACTION_CULTURE -> if (isValencian) "Cultura" else "Cultura"
            COMMERCIAL -> if (isValencian) "Comerç" else "Comercio"
            PARK -> if (isValencian) "Parc" else "Parque"
            GENERAL -> if (isValencian) "General" else "General"
            FAVORITE -> if (isValencian) "Preferit" else "Favorito"
        }
    }

    companion object {
        fun resolveFromOsm(category: String?, type: String?): PlaceCategory {
            val cat = category?.lowercase() ?: ""
            val typ = type?.lowercase() ?: ""
            return when {
                cat == "favorite" || typ == "favorite" -> FAVORITE
                cat == "public_transport" || cat == "railway" || (cat == "highway" && (typ == "bus_stop" || typ == "platform")) -> TRANSIT
                cat == "historic" || cat == "tourism" || cat == "heritage" || typ.contains("museum") || typ.contains("monument") -> ATTRACTION_CULTURE
                cat == "shop" || cat == "commercial" || typ == "supermarket" || typ == "mall" -> COMMERCIAL
                cat == "leisure" || typ == "park" || typ == "garden" -> PARK
                else -> GENERAL
            }
        }
    }
}
