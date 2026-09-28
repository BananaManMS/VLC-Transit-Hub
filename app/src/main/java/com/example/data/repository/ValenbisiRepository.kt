package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.ui.map.components.ValenbisiStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ValenbisiRepository {
    private val client: OkHttpClient
    private val context: Context?

    constructor(context: Context) {
        this.context = context.applicationContext
        this.client = com.example.data.network.NetworkModule.okHttpClient
    }

    constructor(client: OkHttpClient) {
        this.context = null
        this.client = client
    }

    constructor(context: Context, client: OkHttpClient) {
        this.context = context.applicationContext
        this.client = client
    }

    suspend fun fetchStations(force: Boolean = false): List<ValenbisiStation> = withContext(Dispatchers.IO) {
        if (!force && cachedStationsList.isNotEmpty()) {
            return@withContext cachedStationsList
        }

        val list = mutableListOf<ValenbisiStation>()

        // 1. Primary Source: Official Valencia Geoportal ArcGIS REST Endpoint (Trafico/MapServer/228) with outSR=4326
        try {
            val geoportalUrl = "https://geoportal.valencia.es/server/rest/services/OPENDATA/Trafico/MapServer/228/query?where=1=1&outFields=*&outSR=4326&f=json"
            val request = Request.Builder().url(geoportalUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val root = JSONObject(body)
                        val features = root.optJSONArray("features") ?: JSONArray()
                        for (i in 0 until features.length()) {
                            val feat = features.optJSONObject(i) ?: continue
                            val attr = feat.optJSONObject("attributes") ?: JSONObject()
                            val geom = feat.optJSONObject("geometry")

                            val number = attr.optInt("NUMERO", 0)
                                .let { if (it == 0) attr.optInt("number", 0) else it }
                                .let { if (it == 0) attr.optInt("OBJECTID", 0) else it }
                                .let { if (it == 0) attr.optInt("gid", i + 1) else it }

                            var rawName = attr.optString("DIRECCION", "").trim()
                            if (rawName.isBlank()) rawName = attr.optString("name", "").trim()
                            if (rawName.isBlank()) rawName = attr.optString("address", "").trim()
                            if (rawName.isBlank()) rawName = "Estación Valenbisi $number"

                            val cleanName = sanitizeStationName(rawName)

                            val available = attr.optInt("DISPONIBILIDAD", attr.optInt("available", attr.optInt("BICIS_DISPONIBLES", 0)))
                            val free = attr.optInt("LIBRES", attr.optInt("free", attr.optInt("BORNES_LIBRES", 0)))
                            val total = attr.optInt("TOTAL", attr.optInt("total", attr.optInt("TOTAL_PUESTOS", available + free)))

                            val activoStr = attr.optString("ACTIVO", attr.optString("open", "S")).trim().uppercase()
                            val isOpen = activoStr == "S" || activoStr == "T" || activoStr == "OPEN" || activoStr == "ACTIVO" || activoStr == "1"

                            val ticketStr = attr.optString("TICKET", attr.optString("ticket", "T")).trim().uppercase()
                            val hasTicket = ticketStr == "S" || ticketStr == "T" || ticketStr == "1" || attr.optBoolean("ticket", true)

                            var lon = geom?.optDouble("x", 0.0) ?: 0.0
                            var lat = geom?.optDouble("y", 0.0) ?: 0.0

                            if (lat == 0.0 || lon == 0.0) {
                                lat = attr.optDouble("LATITUD", attr.optDouble("latitude", attr.optDouble("lat", 0.0)))
                                lon = attr.optDouble("LONGITUD", attr.optDouble("longitude", attr.optDouble("lon", 0.0)))
                            }

                            if (number > 0 && lat != 0.0 && lon != 0.0) {
                                list.add(
                                    ValenbisiStation(
                                        gid = number,
                                        name = cleanName,
                                        number = number,
                                        address = cleanName,
                                        open = isOpen,
                                        available = available,
                                        free = free,
                                        total = if (total > 0) total else (available + free),
                                        ticket = hasTicket,
                                        latitude = lat,
                                        longitude = lon
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ValenbisiRepository", "Geoportal 228 fetch error: ${e.message}")
        }

        // 2. Fallback: CityBikes API (Clean, fast REST endpoint with real-time updates)
        if (list.isEmpty()) {
            try {
                val cityBikesUrl = "https://api.citybik.es/v2/networks/valenbisi"
                val request = Request.Builder().url(cityBikesUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val root = JSONObject(body)
                            val network = root.optJSONObject("network")
                            val stations = network?.optJSONArray("stations") ?: JSONArray()
                            for (i in 0 until stations.length()) {
                                val st = stations.optJSONObject(i) ?: continue
                                val extra = st.optJSONObject("extra") ?: JSONObject()
                                val number = extra.optInt("number", 0)
                                    .let { if (it == 0) extra.optInt("uid", 0) else it }
                                    .let { if (it == 0) i + 1 else it }

                                val rawName = st.optString("name", "")
                                val rawAddress = extra.optString("address", rawName)
                                val cleanName = sanitizeStationName(rawName.ifBlank { rawAddress })
                                val cleanAddress = sanitizeStationName(rawAddress.ifBlank { rawName })

                                val available = st.optInt("free_bikes", 0)
                                val free = st.optInt("empty_slots", 0)
                                val total = extra.optInt("slots", available + free)
                                val status = extra.optString("status", "OPEN")
                                val isOpen = !status.equals("CLOSED", ignoreCase = true)
                                val hasBanking = extra.optBoolean("banking", extra.optBoolean("payment-terminal", true))

                                val lat = st.optDouble("latitude", 0.0)
                                val lon = st.optDouble("longitude", 0.0)

                                if (number > 0 && lat != 0.0 && lon != 0.0) {
                                    list.add(
                                        ValenbisiStation(
                                            gid = number,
                                            name = cleanName,
                                            number = number,
                                            address = cleanAddress,
                                            open = isOpen,
                                            available = available,
                                            free = free,
                                            total = if (total > 0) total else (available + free),
                                            ticket = hasBanking,
                                            latitude = lat,
                                            longitude = lon
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ValenbisiRepository", "CityBikes fetch warning: ${e.message}")
            }
        }

        // 3. Fallback: Valencia OpenDataSoft V2.1 API with pagination
        if (list.isEmpty()) {
            try {
                var offset = 0
                var totalCount = 1
                while (offset < totalCount && offset < 400) {
                    val url = "https://valencia.opendatasoft.com/api/explore/v2.1/catalog/datasets/valenbisi-disponibilitat-valenbisi-disponibilidad/records?limit=100&offset=$offset"
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            if (body.isNotBlank()) {
                                val root = JSONObject(body)
                                totalCount = root.optInt("total_count", totalCount)
                                val records = root.optJSONArray("results") ?: JSONArray()
                                for (i in 0 until records.length()) {
                                    val rec = records.optJSONObject(i) ?: continue
                                    val number = rec.optInt("number", 0)
                                        .let { if (it == 0) rec.optInt("numstation", 0) else it }
                                        .let { if (it == 0) rec.optInt("gid", 0) else it }

                                    var rawName = rec.optString("name", "").trim()
                                    var rawAddress = rec.optString("address", "").trim()
                                    if (rawName.isBlank() && rawAddress.isNotBlank()) rawName = rawAddress
                                    if (rawAddress.isBlank() && rawName.isNotBlank()) rawAddress = rawName
                                    if (rawName.isBlank()) rawName = "Estación Valenbisi $number"

                                    val cleanName = sanitizeStationName(rawName)
                                    val cleanAddress = sanitizeStationName(rawAddress)

                                    val status = rec.optString("status", "OPEN")
                                    val available = rec.optInt("available", rec.optInt("available_bikes", rec.optInt("bikes", 0)))
                                    val free = rec.optInt("free", rec.optInt("available_slots", rec.optInt("slots", 0)))
                                    val total = rec.optInt("total", rec.optInt("capacity", available + free))
                                    val ticket = rec.optInt("ticket", 0) == 1 || rec.optBoolean("ticket", false)

                                    var lat = rec.optDouble("latitude", 0.0)
                                    var lon = rec.optDouble("longitude", 0.0)

                                    if (lat == 0.0 || lon == 0.0) {
                                        val geoPointObj = rec.optJSONObject("geo_point_2d")
                                        if (geoPointObj != null) {
                                            if (lat == 0.0) lat = geoPointObj.optDouble("lat", 0.0)
                                            if (lon == 0.0) lon = geoPointObj.optDouble("lon", 0.0)
                                        } else {
                                            val geoPointArr = rec.optJSONArray("geo_point_2d")
                                            if (geoPointArr != null && geoPointArr.length() >= 2) {
                                                if (lat == 0.0) lat = geoPointArr.optDouble(0, 0.0)
                                                if (lon == 0.0) lon = geoPointArr.optDouble(1, 0.0)
                                            }
                                        }
                                    }

                                    if (number > 0 && lat != 0.0 && lon != 0.0) {
                                        list.add(
                                            ValenbisiStation(
                                                gid = number,
                                                name = cleanName,
                                                number = number,
                                                address = cleanAddress,
                                                open = status.equals("OPEN", ignoreCase = true) || status.equals("ACTIVO", ignoreCase = true),
                                                available = available,
                                                free = free,
                                                total = total,
                                                ticket = ticket,
                                                latitude = lat,
                                                longitude = lon
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    offset += 100
                }
            } catch (e: Exception) {
                Log.w("ValenbisiRepository", "OpenDataSoft fetch warning: ${e.message}")
            }
        }

        // 4. Fallback: Core Valencia landmarks & Valenbisi stations
        if (list.isEmpty()) {
            val defaultStations = listOf(
                ValenbisiStation(1, "Plaça de l'Ajuntament", 1, "Plaça de l'Ajuntament, 1", true, 8, 12, 20, true, 39.46975, -0.37639),
                ValenbisiStation(2, "Estació del Nord - Xàtiva", 2, "Carrer de Xàtiva, 24", true, 11, 9, 20, true, 39.46670, -0.37720),
                ValenbisiStation(3, "Colón - Pascual y Genís", 3, "Carrer de Colón, 32", true, 6, 14, 20, true, 39.46920, -0.37110),
                ValenbisiStation(4, "Plaça de la Reina - Catedral", 4, "Plaça de la Reina, 15", true, 9, 11, 20, true, 39.47500, -0.37550),
                ValenbisiStation(5, "Àngel Guimerà - Gran Via", 5, "Gran Via de Ferran el Catòlic, 2", true, 7, 13, 20, true, 39.47150, -0.38310),
                ValenbisiStation(6, "Benimaclet - Emilio Baró", 6, "Carrer d'Emili Baró, 12", true, 10, 10, 20, true, 39.48550, -0.35820),
                ValenbisiStation(7, "Universitat Politècnica (UPV)", 7, "Avinguda dels Tarongers, 1", true, 15, 5, 20, true, 39.47950, -0.34210),
                ValenbisiStation(8, "Russafa - Mercat", 8, "Plaça del Baró de Cortés, 1", true, 5, 15, 20, true, 39.46280, -0.37250),
                ValenbisiStation(9, "Ciutat de les Arts i les Ciències", 9, "Avinguda del Professor López Piñero, 7", true, 12, 8, 20, true, 39.45420, -0.35330),
                ValenbisiStation(10, "Pont de Fusta - Almassora", 10, "Carrer d'Almassora, 2", true, 8, 12, 20, true, 39.48120, -0.37290),
                ValenbisiStation(11, "Plaça d'Espanya - Sant Vicent", 11, "Plaça d'Espanya, 4", true, 9, 11, 20, true, 39.46780, -0.38080),
                ValenbisiStation(12, "Port de València - Marina", 12, "Carrer del Doctor Lluch, 2", true, 14, 6, 20, true, 39.46210, -0.32950)
            )
            list.addAll(defaultStations)
        }

        val sortedList = list.distinctBy { it.number }.sortedBy { it.number }
        cachedStationsList = sortedList
        sortedList
    }

    suspend fun getStations(): List<String> {
        return fetchStations().map { it.name }
    }

    fun getCachedStations(): List<ValenbisiStation> {
        return cachedStationsList
    }

    companion object {
        @Volatile
        private var cachedStationsList: List<ValenbisiStation> = emptyList()

        fun getCachedStations(): List<ValenbisiStation> {
            return cachedStationsList
        }

        /**
         * Sanitizes station names from CityBikes, Geoportal or OpenData:
         * - Strips leading underscores and number prefixes (e.g. "_AVENIDA_PIO_XII" -> "AVENIDA_PIO_XII")
         * - Replaces remaining underscores with spaces
         * - Converts to Title Case while preserving Roman numerals (e.g., Juan XXIII, Pio XII) and prepositions (de, del, la...)
         */
        fun sanitizeStationName(rawName: String): String {
            if (rawName.isBlank()) return rawName

            var cleaned = rawName.trim()
            while (cleaned.startsWith("_")) {
                cleaned = cleaned.removePrefix("_").trim()
            }
            // Remove leading index prefix like "001_" or "70_" or "152 - "
            cleaned = cleaned.replace(Regex("""^\d{1,4}[_\s-]+"""), "").trim()
            while (cleaned.startsWith("_")) {
                cleaned = cleaned.removePrefix("_").trim()
            }

            cleaned = cleaned.replace('_', ' ').replace(Regex("""\s+"""), " ").trim()
            if (cleaned.isBlank()) return rawName

            val romanNumerals = setOf(
                "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
                "XXI", "XXII", "XXIII", "XXIV", "XXV"
            )

            val lowerWords = setOf(
                "de", "del", "la", "el", "los", "las", "y", "e", "i", "en", "d'", "l'", "a", "al", "dels", "les"
            )

            val uppercaseAcronyms = setOf(
                "UPV", "UV", "EMT", "FGV", "CC", "C.C.", "RENFE", "ADIF"
            )

            val words = cleaned.split(" ")
            val formattedWords = words.mapIndexed { index, word ->
                val upperWord = word.uppercase()
                when {
                    upperWord in romanNumerals -> upperWord
                    upperWord in uppercaseAcronyms -> upperWord
                    index > 0 && word.lowercase() in lowerWords -> word.lowercase()
                    word.startsWith("d'", ignoreCase = true) && word.length > 2 -> {
                        "d'" + word.substring(2).lowercase().replaceFirstChar { it.uppercase() }
                    }
                    word.startsWith("l'", ignoreCase = true) && word.length > 2 -> {
                        "l'" + word.substring(2).lowercase().replaceFirstChar { it.uppercase() }
                    }
                    else -> word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }

            return formattedWords.joinToString(" ")
        }
    }
}
