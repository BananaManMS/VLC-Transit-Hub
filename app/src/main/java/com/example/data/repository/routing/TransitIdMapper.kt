package com.example.data.repository.routing

import java.text.Normalizer
import java.util.Locale

object TransitIdMapper {

    /**
     * Checks whether a bus route/leg belongs to EMT Valencia.
     * Returns false for interurban buses (Metrobus, València Metropolitana, Fernanbús, AVSA, Monbus, Alsa, 3-digit lines >= 100, etc.)
     * to prevent wasteful network requests to EMT real-time servers that cause planner delays.
     */
    fun isEmtBus(
        agencyName: String? = null,
        routeShortName: String? = null,
        routeLongName: String? = null,
        fromStopId: String? = null,
        fromName: String? = null
    ): Boolean {
        // 1. Check agency name if explicitly present
        if (!agencyName.isNullOrBlank()) {
            val agencyUpper = agencyName.uppercase(Locale.ROOT)
            if (agencyUpper.contains("EMT") && !agencyUpper.contains("METROBUS") && !agencyUpper.contains("METROPOLITANA")) {
                return true
            }
            if (agencyUpper.contains("METROBUS") || agencyUpper.contains("METROBÚS") ||
                agencyUpper.contains("METROPOLITANA") || agencyUpper.contains("CONSORCI") ||
                agencyUpper.contains("ATMV") || agencyUpper.contains("FERNAN") ||
                agencyUpper.contains("AVSA") || agencyUpper.contains("BUÑOL") ||
                agencyUpper.contains("BUNYOL") || agencyUpper.contains("HERCA") ||
                agencyUpper.contains("HORTA") || agencyUpper.contains("ALSA") ||
                agencyUpper.contains("MONBUS") || agencyUpper.contains("AUVACA") ||
                agencyUpper.contains("EDETANIA") || agencyUpper.contains("TORRENT") ||
                agencyUpper.contains("PATERNA") || agencyUpper.contains("SAGUNT") ||
                agencyUpper.contains("INTERURBAN") || agencyUpper.contains("SUBURBAN") ||
                agencyUpper.contains("AUTOCAR") || agencyUpper.contains("CEVESA") ||
                agencyUpper.contains("YESTE") || agencyUpper.contains("RIBERA")
            ) {
                return false
            }
        }

        // 2. Check stop ID if explicitly present
        if (!fromStopId.isNullOrBlank()) {
            val stopIdUpper = fromStopId.uppercase(Locale.ROOT)
            if (stopIdUpper.contains("EMT")) {
                return true
            }
            if (stopIdUpper.contains("METROBUS") || stopIdUpper.contains("CONSORCI") ||
                stopIdUpper.contains("ATMV") || stopIdUpper.contains("CTFV") ||
                stopIdUpper.contains("AVSA") || stopIdUpper.contains("FERNAN")) {
                return false
            }
        }

        // 3. Check route short name
        val lineClean = routeShortName?.trim()?.replace(Regex("""^(?:Línea|Linia|L)\s*""", RegexOption.IGNORE_CASE), "") ?: ""
        if (lineClean.isNotBlank()) {
            val digitsOnly = lineClean.filter { it.isDigit() }
            if (digitsOnly.length >= 3 || (digitsOnly.toIntOrNull() ?: 0) >= 100) {
                return false
            }
        }

        // 4. Check route long name
        val longNameUpper = routeLongName?.uppercase(Locale.ROOT) ?: ""
        if (longNameUpper.contains("METROBUS") || longNameUpper.contains("METROPOLITANA") ||
            longNameUpper.contains("INTERURBAN") || longNameUpper.contains("FERNAN") ||
            longNameUpper.contains("AVSA") || longNameUpper.contains("LINEA INTERURBANA")) {
            return false
        }

        return true
    }

    /**
     * Checks whether an agency or line is Metrobús / Interurban bus (Generalitat Valenciana).
     */
    fun isMetrobus(
        agencyName: String? = null,
        routeShortName: String? = null,
        routeLongName: String? = null
    ): Boolean {
        val agencyUpper = agencyName?.uppercase(Locale.ROOT) ?: ""
        if (agencyUpper.contains("METROBUS") || agencyUpper.contains("METROBÚS") ||
            agencyUpper.contains("METROPOLITANA") || agencyUpper.contains("CONSORCI") ||
            agencyUpper.contains("ATMV") || agencyUpper.contains("FERNAN") ||
            agencyUpper.contains("AVSA") || agencyUpper.contains("BUÑOL") ||
            agencyUpper.contains("BUNYOL") || agencyUpper.contains("HERCA") ||
            agencyUpper.contains("EDETANIA") || agencyUpper.contains("AUVACA") ||
            agencyUpper.contains("RIBERA") || agencyUpper.contains("INTERURBAN") ||
            agencyUpper.contains("SUBURBAN")
        ) {
            return true
        }

        val shortUpper = routeShortName?.trim()?.uppercase(Locale.ROOT) ?: ""
        if (shortUpper.startsWith("L1") || shortUpper.startsWith("L2") || shortUpper.startsWith("L3") ||
            (shortUpper.startsWith("1") && shortUpper.filter { it.isDigit() }.length >= 3)
        ) {
            return true
        }

        val longUpper = routeLongName?.uppercase(Locale.ROOT) ?: ""
        return longUpper.contains("METROBUS") || longUpper.contains("METROPOLITANA") || longUpper.contains("INTERURBAN")
    }

    /**
     * Normalizes GTFS route short names (e.g. T5 -> L5, T3 -> L3, METRO_1 -> L1).
     */
    fun normalizeRouteShortName(mode: com.example.data.model.routing.TransitMode, rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""
        val trimmed = rawName.trim()
        return when (mode) {
            com.example.data.model.routing.TransitMode.SUBWAY,
            com.example.data.model.routing.TransitMode.TRAM -> {
                val clean = trimmed.uppercase(Locale.ROOT)
                when {
                    clean.matches(Regex("""^T(\d+)$""")) -> {
                        "L" + clean.removePrefix("T")
                    }
                    clean.matches(Regex("""^METRO[_\s]?(\d+)$""")) -> {
                        val match = Regex("""^METRO[_\s]?(\d+)$""").find(clean)
                        "L${match?.groupValues?.get(1)}"
                    }
                    clean.matches(Regex("""^L[IÍ]NEA\s*(\d+)$""")) -> {
                        val match = Regex("""^L[IÍ]NEA\s*(\d+)$""").find(clean)
                        "L${match?.groupValues?.get(1)}"
                    }
                    clean.matches(Regex("""^\d+$""")) -> {
                        "L$clean"
                    }
                    clean.startsWith("L") && clean.substring(1).all { it.isDigit() } -> {
                        clean
                    }
                    else -> trimmed
                }
            }
            com.example.data.model.routing.TransitMode.RAIL -> {
                extractCercaniasLine(trimmed, null) ?: trimmed
            }
            com.example.data.model.routing.TransitMode.BUS -> {
                trimmed.replace(Regex("""^(?:Línea|Linia|L)\s*""", RegexOption.IGNORE_CASE), "").trim()
            }
            else -> trimmed
        }
    }

    /**
     * Extracts EMT Stop number (e.g. "es-EMT-Valencia_1234" -> "1234", "1234", "Plaça (1234)").
     */
    fun extractEmtStopNumber(stopId: String?, name: String? = null): String? {
        if (!stopId.isNullOrBlank()) {
            val suffix = stopId.substringAfterLast(":").substringAfterLast("/").substringAfterLast("_").trim()
            val digits = suffix.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                return digits
            }
        }
        if (!name.isNullOrBlank()) {
            val parenMatch = Regex("""\((\d{1,5})\)""").find(name)
            if (parenMatch != null) {
                return parenMatch.groupValues[1]
            }
            val match = Regex("""(?:Parada|Poste|Stop|nº?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(name)
            if (match != null) {
                return match.groupValues[1]
            }
            val prefixMatch = Regex("""^(\d{1,5})\s*[-–—]""").find(name.trim())
            if (prefixMatch != null) {
                return prefixMatch.groupValues[1]
            }
        }
        return null
    }

    /**
     * Extracts Metrovalencia station ID (e.g. "es-Metro-de-Valencia_16" -> 16, "Xàtiva" -> 16, "Pl. Espanya" -> 51).
     */
    fun extractMetroStationId(stopId: String?, name: String? = null): Int? {
        if (!name.isNullOrBlank()) {
            val normName = normalizeStationName(name)
            val exactMatch = KNOWN_METRO_STATION_NAMES[normName]
            if (exactMatch != null) return exactMatch
            for ((key, id) in KNOWN_METRO_STATION_NAMES) {
                if (normName == key || normName.startsWith(key) || key.startsWith(normName)) {
                    return id
                }
            }
            for ((key, id) in KNOWN_METRO_STATION_NAMES) {
                if (normName.contains(key) || key.contains(normName)) {
                    return id
                }
            }
        }
        if (!stopId.isNullOrBlank()) {
            val tokens = stopId.split(":", "_", "-", "/", " ").mapNotNull { it.trim().toIntOrNull() }
            val validId = tokens.firstOrNull { it in 1..250 }
            if (validId != null) {
                return validId
            }
            val normStopId = normalizeStationName(stopId)
            val exactStopMatch = KNOWN_METRO_STATION_NAMES[normStopId]
            if (exactStopMatch != null) return exactStopMatch
            for ((key, id) in KNOWN_METRO_STATION_NAMES) {
                if (normStopId.contains(key) || key.contains(normStopId)) {
                    return id
                }
            }
        }
        return null
    }

    /**
     * Extracts Metro line numbers from route identifiers (e.g. "3", "5", "9").
     */
    fun extractMetroLine(routeShortName: String?, routeLongName: String?): String? {
        if (!routeShortName.isNullOrBlank()) {
            val clean = routeShortName.trim()
            if (clean.all { it.isDigit() }) return clean
            val match = Regex("""(?:L|Línea|Linia)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(clean)
            if (match != null) return match.groupValues[1]
        }
        if (!routeLongName.isNullOrBlank()) {
            val match = Regex("""(?:L|Línea|Linia)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(routeLongName)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /**
     * Flexible line comparison for EMT Valencia buses (e.g., "10" vs "010", "C1" vs "C 1" or "C-1").
     */
    fun isSameEmtLine(rawLineA: String?, rawLineB: String?): Boolean {
        if (rawLineA.isNullOrBlank() || rawLineB.isNullOrBlank()) return false
        val a = rawLineA.trim()
        val b = rawLineB.trim()
        if (a.equals(b, ignoreCase = true)) return true

        val normA = normalizeRouteShortName(com.example.data.model.routing.TransitMode.BUS, a)
        val normB = normalizeRouteShortName(com.example.data.model.routing.TransitMode.BUS, b)
        if (normA.equals(normB, ignoreCase = true)) return true

        fun stripPunctAndLeadingZeros(s: String): String {
            val clean = s.replace(Regex("""[^A-Za-z0-9]"""), "").uppercase(Locale.ROOT)
            return clean.dropWhile { it == '0' }
        }

        val stripA = stripPunctAndLeadingZeros(normA)
        val stripB = stripPunctAndLeadingZeros(normB)
        if (stripA.isNotEmpty() && stripA == stripB) return true

        val digitsA = normA.filter { it.isDigit() }
        val digitsB = normB.filter { it.isDigit() }
        if (digitsA.isNotEmpty() && digitsA == digitsB) {
            val lettersA = normA.filter { it.isLetter() }.uppercase(Locale.ROOT)
            val lettersB = normB.filter { it.isLetter() }.uppercase(Locale.ROOT)
            if (lettersA.isEmpty() || lettersB.isEmpty() || lettersA == lettersB) {
                return true
            }
        }
        return false
    }

    /**
     * Checks whether a stop's 'lineas' string (e.g. "10, 11, C1") contains the target line.
     */
    fun stopPassesLine(stopLineas: String?, targetLine: String): Boolean {
        if (stopLineas.isNullOrBlank() || targetLine.isBlank()) return false
        val linesList = stopLineas.split(Regex("""[,;\s]+"""))
        return linesList.any { isSameEmtLine(it, targetLine) }
    }

    /**
     * Extracts Renfe Cercanías line (e.g. "C-1", "C-2", "C-6", "ER-02").
     * Uses strict word boundary matching to prevent matching Metrobús lines (e.g. L106, 160) or Metrovalencia lines.
     */
    fun extractCercaniasLine(
        routeShortName: String?,
        routeLongName: String?,
        agencyName: String? = null,
        mode: com.example.data.model.routing.TransitMode? = null
    ): String? {
        // If agency is explicitly Metrovalencia / FGV or mode is subway/tram, it is NOT Cercanías Renfe
        if (mode == com.example.data.model.routing.TransitMode.SUBWAY || mode == com.example.data.model.routing.TransitMode.TRAM) {
            return null
        }
        val agencyUpper = agencyName?.uppercase(Locale.ROOT) ?: ""
        if ((agencyUpper.contains("METRO") && agencyUpper.contains("VALENCIA")) || agencyUpper.contains("FGV")) {
            return null
        }

        // If mode is BUS and agency does not mention Renfe, it's not Cercanías
        if (mode == com.example.data.model.routing.TransitMode.BUS && !agencyUpper.contains("RENFE")) {
            return null
        }

        val target = "${routeShortName.orEmpty()} ${routeLongName.orEmpty()}"
        val match = Regex("""\b(C-?[1-6]|ER-?02)\b""", RegexOption.IGNORE_CASE).find(target)
        return match?.value?.uppercase(Locale.ROOT)?.replace("-", "")?.let {
            if (it.startsWith("C") && it.length == 2) "C-${it.substring(1)}" else it
        }
    }

    fun normalizeName(raw: String): String {
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    fun normalizeStationName(raw: String): String {
        val base = normalizeName(raw)
        return base
            .replace(Regex("""^(?:estacio|estacion|parada|halte)\s+(?:de\s+|d['’])?"""), "")
            .replace(Regex("""^(?:metro|tram|metrovalencia)\s+"""), "")
            .trim()
    }

    private val KNOWN_METRO_STATION_NAMES = mapOf(
        "xativa" to 16,
        "colon" to 15,
        "alameda" to 14,
        "facultats" to 13,
        "facultats - manuel broseta" to 13,
        "facultats manuel broseta" to 13,
        "angel guimera" to 17,
        "pl. espanya" to 51,
        "plaza espana" to 51,
        "placa espanya" to 51,
        "espanya" to 51,
        "jesus" to 25,
        "joaquin sorolla - jesus" to 25,
        "patraix" to 26,
        "safranar" to 27,
        "sant isidre" to 28,
        "turia" to 52,
        "campanar" to 53,
        "beniferri" to 54,
        "empalme" to 55,
        "benimaclet" to 12,
        "machado" to 11,
        "maritim" to 115,
        "maritim - serreria" to 115,
        "maritim serreria" to 115,
        "ayora" to 22,
        "amistat" to 23,
        "amistat - conservatori" to 23,
        "arago" to 24,
        "aragon" to 24,
        "alacant" to 190,
        "alicante" to 190,
        "russafa" to 191,
        "ruzafa" to 191,
        "amado granell" to 192,
        "amado granell - montolivet" to 192,
        "amado granell montolivet" to 192,
        "quatre carreres" to 193,
        "ciutat arts i ciencies - justicia" to 194,
        "ciutat arts i ciencies justicia" to 194,
        "ciutat arts i ciencies" to 194,
        "oceanografic" to 195,
        "moreres" to 196,
        "natzaret" to 197,
        "aeroport" to 121,
        "aeropuerto" to 121,
        "rosas" to 120,
        "roses" to 120,
        "manises" to 119,
        "quart de poblet" to 117,
        "faitanar" to 200,
        "mislata almassil" to 21,
        "mislata" to 20,
        "nou d'octubre" to 19,
        "nou doctubre" to 19,
        "av. del cid" to 18,
        "avenida del cid" to 18,
        "bailen" to 109,
        "torrent" to 33,
        "torrent avinguda" to 34,
        "picanya" to 32,
        "paiporta" to 31,
        "valencia sud" to 30,
        "paterna" to 60,
        "burjassot" to 72,
        "burjassot - godella" to 73,
        "godella" to 74,
        "rocafort" to 75,
        "massarrojos" to 76,
        "moncada - alfara" to 77,
        "seminari - ceu" to 78,
        "masies" to 79,
        "horta vella" to 80,
        "betera" to 107,
        "lliria" to 71,
        "benaguasil" to 70,
        "la pobla de vallbona" to 68,
        "l'eliana" to 67,
        "leliana" to 67,
        "la canyada" to 63,
        "rafelbunyol" to 1,
        "la pobla de farnals" to 2,
        "massamagrell" to 3,
        "museros" to 4,
        "albalat dels sorells" to 5,
        "foios" to 6,
        "meliana" to 7,
        "almassera" to 8,
        "alboraia peris arago" to 9,
        "alboraia palmaret" to 10,
        "castello" to 35,
        "alberic" to 36,
        "massalaves" to 37,
        "montortal" to 38,
        "l'alcudia" to 39,
        "lalcudia" to 39,
        "benimodo" to 40,
        "carlet" to 41,
        "ausias march" to 42,
        "alginet" to 43,
        "font almaguer" to 44,
        "espioca" to 45,
        "omet" to 46,
        "picassent" to 47,
        "sant ramon" to 48,
        "realon" to 49,
        "colegi el vedat" to 50,
        "campament" to 59,
        "fuente del jarro" to 62,
        "les carolines - fira" to 58,
        "les carolines fira" to 58,
        "cantereria" to 56,
        "la vallesa" to 64,
        "entrepins" to 65,
        "montesol" to 66,
        "fondo de benaguasil" to 69,
        "gallipont - torre del virrei" to 201,
        "el clot" to 108,
        "salt de l'aigua" to 118,
        "salt de laigua" to 118,
        "masia de traver" to 185,
        "riba-roja de turia" to 186,
        "riba roja de turia" to 186,
        "valencia la vella" to 188,
        "la presa" to 184,
        "la cova" to 183,
        "fira valencia" to 106,
        "platja malva-rosa" to 81,
        "platja malvarrosa" to 81,
        "platja les arenes" to 82,
        "dr. lluch" to 83,
        "cabanyal" to 84,
        "la cadena" to 85,
        "betero" to 86,
        "tarongers - ernest lluch" to 87,
        "tarongers ernest lluch" to 87,
        "la carrasca" to 88,
        "universitat politecnica" to 89,
        "vicente zaragoza" to 90,
        "trinitat" to 91,
        "pont de fusta" to 92,
        "sagunt" to 93,
        "reus" to 94,
        "marxalenes" to 95,
        "transits" to 96,
        "benicalap" to 97,
        "garbi" to 98,
        "florista" to 99,
        "palau de congressos" to 100,
        "la granja" to 101,
        "sant joan" to 102,
        "campus" to 103,
        "vicent andres estelles" to 104,
        "a punt" to 105,
        "mas del rosari" to 110,
        "la coma" to 111,
        "tomas y valiente" to 112,
        "parc cientific" to 113,
        "ll. llarga - terramelar" to 114,
        "ll llarga terramelar" to 114,
        "francesc cubells" to 122,
        "grau - la marina" to 123,
        "grau la marina" to 123,
        "neptu" to 126,
        "canyamelar" to 127,
        "alfauir" to 128,
        "orriols" to 129,
        "estadi ciutat de valencia" to 130,
        "sant miquel dels reis" to 131,
        "tossal del rei" to 132
    )

    /**
     * Centralized matching function to determine if a real-time candidate destination
     * corresponds to the planned leg direction.
     */
    fun isDestinationMatch(depDestination: String, leg: com.example.data.model.routing.PlannedLeg): Boolean {
        if (depDestination.isBlank()) return true
        val depNorm = normalizeStationName(depDestination)
        val headsignNorm = normalizeStationName(leg.headsign ?: "")
        val toNameNorm = normalizeStationName(leg.toName)
        val fromNameNorm = normalizeStationName(leg.fromName)

        // 1. Direct or bidirectional match with headsign
        if (headsignNorm.isNotBlank()) {
            if (depNorm == headsignNorm || depNorm.contains(headsignNorm) || headsignNorm.contains(depNorm)) {
                return true
            }
        }

        // 2. Direct or bidirectional match with leg destination name
        if (toNameNorm.isNotBlank()) {
            if (depNorm == toNameNorm || depNorm.contains(toNameNorm) || toNameNorm.contains(depNorm)) {
                return true
            }
        }

        // 3. Match against any intermediate stop along the leg
        for (stop in leg.intermediateStops) {
            val stopNorm = normalizeStationName(stop.name)
            if (stopNorm.isNotBlank() && (depNorm == stopNorm || depNorm.contains(stopNorm) || stopNorm.contains(depNorm))) {
                return true
            }
        }

        // 4. Token-level matching for compound destination names (e.g. "Marítim - Serrería" vs "Marítim")
        val depWords = depNorm.split(" ", "/", "-").map { it.trim() }.filter { it.length >= 3 }
        val headsignWords = headsignNorm.split(" ", "/", "-").map { it.trim() }.filter { it.length >= 3 }
        val toWords = toNameNorm.split(" ", "/", "-").map { it.trim() }.filter { it.length >= 3 }

        if (headsignWords.isNotEmpty() && depWords.any { dw -> headsignWords.any { hw -> dw == hw || dw.contains(hw) || hw.contains(dw) } }) {
            return true
        }

        if (toWords.isNotEmpty() && depWords.any { dw -> toWords.any { tw -> dw == tw || dw.contains(tw) || tw.contains(dw) } }) {
            return true
        }

        // 5. If departure matches origin stop name (heading backwards towards start), explicitly reject
        if (fromNameNorm.isNotBlank() && (depNorm == fromNameNorm || depNorm.contains(fromNameNorm))) {
            return false
        }

        return false
    }
}
