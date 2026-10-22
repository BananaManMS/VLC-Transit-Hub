package com.example.ui.metro

import com.example.data.repository.LineStationInfo
import java.text.Normalizer
import java.util.Locale

object MetroDirectionClassifier {

    private fun normalize(str: String): String {
        return Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[-_.·'/]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .lowercase(Locale.ROOT)
            .trim()
    }

    private fun findStationIndex(
        queryId: String,
        queryName: String,
        lineStations: List<LineStationInfo>
    ): Int {
        if (lineStations.isEmpty()) return -1

        // 1. Coincidencia exacta por ID
        if (queryId.isNotBlank()) {
            val byId = lineStations.indexOfFirst { it.id == queryId }
            if (byId != -1) return byId
        }

        val normQuery = normalize(queryName)
        if (normQuery.isBlank()) return -1

        // 2. Coincidencia normalizada exacta de nombre
        val byExactName = lineStations.indexOfFirst { normalize(it.name) == normQuery }
        if (byExactName != -1) return byExactName

        // 3. Coincidencia de prefijo / sufijo / contención
        val byContains = lineStations.indexOfFirst {
            val normSt = normalize(it.name)
            normSt.contains(normQuery) || normQuery.contains(normSt)
        }
        if (byContains != -1) return byContains

        // 4. Coincidencia por tokens de palabras significativas
        val queryTokens = normQuery.split(" ").filter { it.length > 2 && it !in setOf("del", "les", "dels", "ave", "avinguda", "av", "pl", "plaza", "placa") }
        if (queryTokens.isNotEmpty()) {
            val byTokens = lineStations.indexOfFirst { st ->
                val stNorm = normalize(st.name)
                queryTokens.any { token -> stNorm.contains(token) }
            }
            if (byTokens != -1) return byTokens
        }

        return -1
    }

    /**
     * Determina el sentido canónico (0 o 1) de una salida de metro basándose en la estación actual,
     * el destino y la lista ordenada de estaciones de esa línea.
     *
     * Sentido 0: Convención hacia Sur / Oeste (València Sud/Castelló/Torrent, Aeroport/Riba-roja, Tossal del Rei, Alacant).
     * Sentido 1: Convención hacia Norte / Este / Playa (Bétera/Llíria/Empalme, Rafelbunyol/Marítim/Alboraia, Dr. Lluch/Neptú, Natzaret).
     */
    fun classifyDepartureDirection(
        lineId: String,
        destination: String,
        currentStationId: String,
        currentStationName: String,
        lineStations: List<LineStationInfo>
    ): Int {
        val cleanLine = lineId.filter { it.isDigit() }

        if (lineStations.isEmpty()) {
            return fallbackHeuristic(cleanLine, destination)
        }

        var currIdx = findStationIndex(currentStationId, currentStationName, lineStations)
        var destIdx = findStationIndex("", destination, lineStations)

        if (currIdx == -1 && destIdx == -1) {
            return fallbackHeuristic(cleanLine, destination)
        }

        if (currIdx == -1) {
            if (cleanLine == "3" && (
                com.example.util.MetroFilterUtils.isLaCovaToRibarrojaSection(currentStationId) ||
                com.example.util.MetroFilterUtils.isLaCovaToRibarrojaSection(currentStationName)
            )) {
                val destNorm = normalize(destination)
                return if (destNorm.contains("riba") || destNorm.contains("traver") ||
                    destNorm.contains("vella") || destNorm.contains("presa") ||
                    destNorm.contains("cova") || destNorm.contains("aeroport")
                ) 0 else 1
            }
            return fallbackHeuristic(cleanLine, destination)
        }

        if (destIdx == -1) {
            // Si el destino no se encuentra directamente, recurrir a la heurística de nombres conocidos
            return fallbackHeuristic(cleanLine, destination)
        }

        // Si la estación actual y destino coinciden (tren en término o error de destino)
        if (currIdx == destIdx) {
            return if (destIdx >= lineStations.size / 2) 0 else 1
        }

        val isAscendingInJson = destIdx > currIdx

        // Relación topológica exacta según el orden de estaciones en `metro_line_stations.json`:
        return when (cleanLine) {
            // L1: 0=Bétera (Norte) -> 10=Empalme -> 14=Àngel Guimerà -> 20=València Sud -> 39=Castelló (Sur)
            // Ascendente va al Sur -> Sentido 0
            // Descendente va al Norte (Empalme, Seminari, Bétera) -> Sentido 1
            "1" -> if (isAscendingInJson) 0 else 1

            // L2: 0=Llíria (Norte) -> 18=Empalme -> 22=Àngel Guimerà -> 28=València Sud -> 32=Torrent Av. (Sur)
            // Ascendente va al Sur -> Sentido 0
            // Descendente va al Norte (Empalme, Paterna, Llíria) -> Sentido 1
            "2" -> if (isAscendingInJson) 0 else 1

            // L3: 0=Aeroport (Oeste) -> 10=Àngel Guimerà -> 12=Colón -> 26=Rafelbunyol (Este/Norte)
            // Ascendente va al Este / Rafelbunyol -> Sentido 1
            // Descendente va al Oeste / Aeroport -> Sentido 0
            "3" -> if (isAscendingInJson) 1 else 0

            // L4: 0=Mas del Rosari (Norte/Oeste) -> 11=Empalme -> 18=Benimaclet -> 31=Dr. Lluch (Este/Playa)
            // Ascendente va al Este / Playa -> Sentido 1
            // Descendente va al Norte / Empalme / Mas del Rosari -> Sentido 0
            "4" -> if (isAscendingInJson) 1 else 0

            // L5: 0=Marítim (Este) -> 5=Colón -> 7=Àngel Guimerà -> 17=Aeroport (Oeste)
            // Ascendente va al Oeste / Aeroport -> Sentido 0
            // Descendente va al Este / Marítim -> Sentido 1
            "5" -> if (isAscendingInJson) 0 else 1

            // L6: 0=Tossal del Rei (Norte) -> 6=Benimaclet -> 20=Marítim (Este/Sur)
            // Ascendente va al Este / Marítim -> Sentido 1
            // Descendente va al Norte / Tossal del Rei -> Sentido 0
            "6" -> if (isAscendingInJson) 1 else 0

            // L7: 0=Torrent Avinguda (Sur) -> 4=València Sud -> 10=Colón -> 15=Marítim (Este)
            // Ascendente va al Este / Marítim -> Sentido 1
            // Descendente va al Sur / Torrent Avinguda -> Sentido 0
            "7" -> if (isAscendingInJson) 1 else 0

            // L8: 0=Marítim (Oeste) -> 3=Neptú (Este)
            // Ascendente va al Este / Neptú -> Sentido 1
            // Descendente va al Oeste / Marítim -> Sentido 0
            "8" -> if (isAscendingInJson) 1 else 0

            // L9: 0=Riba-roja de Túria (Oeste) -> 14=Àngel Guimerà -> 16=Colón -> 22=Alboraia (Este/Norte)
            // Ascendente va al Este / Alboraia -> Sentido 1
            // Descendente va al Oeste / Riba-roja -> Sentido 0
            "9" -> if (isAscendingInJson) 1 else 0

            // L10: 0=Alacant (Norte/Centro) -> 7=Natzaret (Sur/Este)
            // Ascendente va al Sur/Este / Natzaret -> Sentido 1
            // Descendente va al Norte / Alacant -> Sentido 0
            "10" -> if (isAscendingInJson) 1 else 0

            else -> if (isAscendingInJson) 0 else 1
        }
    }

    private fun fallbackHeuristic(cleanLine: String, destination: String): Int {
        val norm = normalize(destination)
        return when (cleanLine) {
            "1", "2" -> {
                // Sentido 0 (Sur): Castelló, Alcúdia, Picassent, Torrent, València Sud, Paiporta, Picanya
                // Sentido 1 (Norte): Bétera, Seminari, Moncada, Massarrojos, Rocafort, Godella, Burjassot, Empalme, Beniferri, Campanar, Paterna, Llíria
                if (norm.contains("betera") || norm.contains("seminari") || norm.contains("moncada") ||
                    norm.contains("massarrojos") || norm.contains("rocafort") || norm.contains("godella") ||
                    norm.contains("burjassot") || norm.contains("empalme") || norm.contains("beniferri") ||
                    norm.contains("campanar") || norm.contains("turia") || norm.contains("lliria") ||
                    norm.contains("paterna") || norm.contains("carolines") || norm.contains("benimamet") ||
                    norm.contains("cantereria") || norm.contains("santa rita") || norm.contains("canyada") ||
                    norm.contains("vallesa") || norm.contains("eliana") || norm.contains("pobla de vallbona")
                ) {
                    1
                } else {
                    0
                }
            }
            "3", "5", "9" -> {
                // Sentido 0 (Oeste): Aeroport, Roses, Manises, Quart, Faitanar, Mislata, Av del Cid, Riba-roja
                // Sentido 1 (Este/Norte): Colón, Alameda, Facultats, Benimaclet, Machado, Alboraia, Almàssera, Meliana, Foios, Albalat, Museros, Massamagrell, Pobla de Farnals, Rafelbunyol, Ayora, Amistat, Marítim
                if (norm.contains("aeroport") || norm.contains("roses") || norm.contains("manises") ||
                    norm.contains("salt") || norm.contains("quart") || norm.contains("faitanar") ||
                    norm.contains("mislata") || norm.contains("nou d'octubre") || norm.contains("octubre") ||
                    norm.contains("cid") || norm.contains("riba") || norm.contains("traver") ||
                    norm.contains("presa") || norm.contains("cova")
                ) {
                    0
                } else {
                    1
                }
            }
            "7" -> {
                // Sentido 0 (Sur): Torrent, València Sud, Paiporta, Picanya, Sant Isidre
                // Sentido 1 (Norte/Este): Bailén, Colón, Alameda, Aragó, Amistat, Ayora, Marítim
                if (norm.contains("torrent") || norm.contains("valencia sud") || norm.contains("paiporta") ||
                    norm.contains("picanya") || norm.contains("isidre") || norm.contains("safranar") ||
                    norm.contains("patraix") || norm.contains("jesus")
                ) {
                    0
                } else {
                    1
                }
            }
            "4", "6", "8" -> {
                // Sentido 1: Dr. Lluch, Neptú, Marítim, Cabanyal, Arenes, Malva-rosa, Tarongers, Carrasca, Politècnica, Benimaclet
                // Sentido 0: Mas del Rosari, Lloma Llarga, Fira, Vicent Andrés Estellés, Empalme, Tossal del Rei
                if (norm.contains("lluch") || norm.contains("neptu") || norm.contains("maritim") ||
                    norm.contains("cabanyal") || norm.contains("arenes") || norm.contains("malva") ||
                    norm.contains("tarongers") || norm.contains("carrasca") || norm.contains("politecnica") ||
                    norm.contains("grau") || norm.contains("cubells") || norm.contains("canyamelar")
                ) {
                    1
                } else {
                    0
                }
            }
            "10" -> {
                // Sentido 1: Natzaret, Moreres, Oceanogràfic, Ciutat Arts, Quatre Carreres, Amado Granell
                // Sentido 0: Alacant, Russafa
                if (norm.contains("natzaret") || norm.contains("moreres") || norm.contains("oceanografic") ||
                    norm.contains("ciutat") || norm.contains("arts") || norm.contains("carreres") || norm.contains("granell")
                ) {
                    1
                } else {
                    0
                }
            }
            else -> 0
        }
    }

    /**
     * Devuelve el nombre de la estación término canónica para una línea y sentido dados.
     */
    fun getCanonicalLineTerminus(lineId: String, direction: Int, stationIdOrName: String? = null): String {
        val cleanLine = lineId.filter { it.isDigit() }
        return when (cleanLine) {
            "1" -> if (direction == 0) "Castelló" else "Bétera"
            "2" -> if (direction == 0) "Torrent Avinguda" else "Llíria"
            "3" -> if (direction == 0) {
                if (stationIdOrName != null && com.example.util.MetroFilterUtils.isLaCovaToRibarrojaSection(stationIdOrName)) {
                    "Riba-roja de Túria"
                } else {
                    "Aeroport"
                }
            } else "Rafelbunyol"
            "4" -> if (direction == 0) "Mas del Rosari" else "Dr. Lluch"
            "5" -> if (direction == 0) "Aeroport" else "Marítim"
            "6" -> if (direction == 0) "Tossal del Rei" else "Marítim"
            "7" -> if (direction == 0) "Torrent Avinguda" else "Marítim"
            "8" -> if (direction == 0) "Marítim" else "Neptú"
            "9" -> if (direction == 0) "Riba-roja de Túria" else "Alboraia Peris Aragó"
            "10" -> if (direction == 0) "Alacant" else "Natzaret"
            else -> if (direction == 0) "Castelló" else "Bétera"
        }
    }
}
