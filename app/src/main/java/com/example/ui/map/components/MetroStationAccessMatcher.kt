package com.example.ui.map.components

import com.example.data.model.MetroStation

/**
 * Deterministic matcher that correlates Metrovalencia station areas (id_stationarea),
 * station access points, and polygon footprint geometries with real MetroStation entities.
 */
object MetroStationAccessMatcher {

    /**
     * Map connecting all official FGV station area codes (id_stationarea)
     * in 'Accesos metrovalencia.geojson' directly to the canonical Metro station name.
     */
    val STATION_AREA_TO_STATION: Map<String, String> = mapOf(
        "VT-002a_03" to "Paiporta",
        "VT-002a_05" to "Sant Isidre",
        "VT-002a_06" to "Safranar",
        "VT-002a_07" to "Patraix",
        "VT-002a_08" to "Jesús",
        "VT-002b_01" to "Pl. Espanya",
        "VT-002b_02" to "Àngel Guimerà",
        "VT-002b_03" to "Túria",
        "VT-002b_04" to "Campanar",
        "VT-002b_05" to "Beniferri",
        "VT-002b_06" to "Empalme",
        "VT-004_01" to "Torrent Avinguda",
        "VT-005_02" to "Benimàmet",
        "VT-005_03" to "Les Carolines - Fira",
        "VT-006_01" to "Bailén",
        "VT-007a_01" to "Aeroport",
        "VT-008_01" to "Roses",
        "VT-008_02" to "Manises",
        "VT-008_03" to "Salt de l'Aigua",
        "VT-008_04" to "Quart de Poblet",
        "VT-008_05" to "Faitanar",
        "VT-008_06" to "Mislata Almassil",
        "VT-008_07" to "Mislata",
        "VT-008_08" to "Nou d'Octubre",
        "VT-008_09" to "Av. del Cid",
        "VT-009a_01" to "Àngel Guimerà",
        "VT-009a_02" to "Xàtiva",
        "VT-009b_01" to "Colón",
        "VT-009b_02" to "Alameda",
        "VT-010a_01" to "Facultats - Manuel Broseta",
        "VT-010a_02" to "Benimaclet",
        "VT-010a_03" to "Machado",
        "VT-010a_04" to "Alboraia Palmaret",
        "VT-010a_05" to "Alboraia Peris Aragó",
        "VT-011_01" to "Aragó",
        "VT-011_02" to "Amistat",
        "VT-011_03" to "Ayora",
        "VT-011_04" to "Marítim",
        "VT-029_01" to "Alacant",
        "VT-029_02" to "Russafa",
        "VT-029_03" to "Amado Granell - Montolivet"
    )

    private val FOOTPRINT_NAME_ALIASES: Map<String, String> = mapOf(
        "Plaça Espanya" to "Pl. Espanya",
        "Seminari-CEU" to "Seminari - CEU",
        "Burjassot-Godella" to "Burjassot - Godella",
        "Font d'Almaguer" to "Font Almaguer",
        "Amado Granell Monteolivet" to "Amado Granell - Montolivet",
        "V. Zaragozá" to "Vicente Zaragozá",
        "Mislata-Almassil" to "Mislata Almassil",
        "Les Carolines-Fira" to "Les Carolines - Fira",
        "Moncada-Alfara" to "Moncada - Alfara",
        "Sant Ramón" to "Sant Ramon",
        "U. Politècnica" to "Universitat Politècnica",
        "L'Alcudia" to "L'Alcúdia",
        "Lloma Llarga Terramelar" to "Ll. Llarga - Terramelar",
        "V.A. Estellés" to "Vicent Andrés Estellés",
        "Palau de Congresos" to "Palau de Congressos",
        "Col.legi El Vedat" to "Col·legi El Vedat"
    )

    fun resolveAccessStationName(areaId: String, lat: Double, lon: Double): String {
        STATION_AREA_TO_STATION[areaId]?.let { return it }
        return ""
    }

    fun resolveFootprintStationName(rawNombre: String): String {
        val trimmed = rawNombre.trim()
        FOOTPRINT_NAME_ALIASES[trimmed]?.let { return it }
        return trimmed
    }

    fun normalize(name: String): String {
        return name.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("à", "a")
            .replace("è", "e")
            .replace("ò", "o")
            .replace("·", "")
            .replace("-", "")
            .replace(".", "")
            .replace("'", "")
            .replace(" ", "")
            .replace("plaça", "pl")
            .trim()
    }

    fun matchesStation(candidateStationName: String, selectedStation: MetroStation): Boolean {
        if (candidateStationName.isBlank() || selectedStation.name.isBlank()) return false
        val n1 = normalize(candidateStationName)
        val n2 = normalize(selectedStation.name)
        return n1 == n2 || n1.contains(n2) || n2.contains(n1)
    }
}
