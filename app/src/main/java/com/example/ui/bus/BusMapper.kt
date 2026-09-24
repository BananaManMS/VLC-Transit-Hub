package com.example.ui.bus

import android.content.Context
import android.util.Log
import com.example.data.database.GeoportalStopEntity
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object BusMapper {

    fun loadStopsFromAssets(context: Context): List<GeoportalStopEntity> {
        return com.example.data.repository.StaticTransitDataCache.getOrLoadEmtStops(context)
    }

    internal fun parseStopsFromJsonDirect(context: Context): List<GeoportalStopEntity> {
        val list = mutableListOf<GeoportalStopEntity>()
        try {
            val jsonString = try {
                val localFile = com.example.data.repository.emt.EmtDataSyncManager.getLocalStopsFile(context)
                if (localFile.exists() && localFile.length() > 100) {
                    localFile.readText(Charsets.UTF_8)
                } else {
                    try {
                        context.assets.open("stops.json").bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        context.assets.open("emt_paradas_lineas.json").bufferedReader().use { it.readText() }
                    }
                }
            } catch (e: Exception) {
                Log.e("BusMapper", "Error reading EMT stops JSON text: ${e.message}")
                return emptyList()
            }

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val idParada = obj.optString("id_parada", "")
                    .ifBlank { obj.optString("stop_id", "") }
                    .ifBlank { obj.optString("id", "") }
                val denominacion = obj.optString("denominacion", "")
                    .ifBlank { obj.optString("nombre", "") }
                    .ifBlank { obj.optString("name", "") }
                val lat = obj.optDouble("lat", 0.0)
                val lon = obj.optDouble("lon", 0.0)
                val suprimida = obj.optInt("suprimida", 0)

                val lineas = if (obj.has("lineas") || obj.has("lines")) {
                    val l = if (obj.has("lineas")) obj.get("lineas") else obj.get("lines")
                    if (l is JSONArray) {
                        (0 until l.length()).joinToString(", ") { l.getString(it) }
                    } else {
                        l.toString()
                    }
                } else ""

                if (idParada.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                    list.add(
                        GeoportalStopEntity(
                            id_parada = idParada,
                            denominacion = denominacion,
                            suprimida = suprimida,
                            lat = lat,
                            lon = lon,
                            lineas = lineas
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("BusMapper", "Error loading stops from JSON: ${e.message}", e)
        }
        return list
    }

    fun getLinesForStop(stop: GeoportalStopEntity): List<EmtRoute> {
        return parseRoutes(stop.lineas)
    }

    fun parseRoutes(lineasStr: String?): List<EmtRoute> {
        if (!lineasStr.isNullOrEmpty()) {
            val lines = lineasStr.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { EmtRoute(id_linea = it, SN = it) }
            if (lines.isNotEmpty()) return lines
        }
        return emptyList()
    }

    fun getCoordinatesForStation(context: Context, stationId: String): Pair<Double, Double> {
        val mainStation = com.example.data.model.ValenciaMetroData.mainMetroStations.find { it.id == stationId }
        if (mainStation != null) {
            return Pair(mainStation.latitude, mainStation.longitude)
        }
        return try {
            val jsonString = context.assets.open("stations_coords.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            var foundLat = 39.4697
            var foundLon = -0.3734
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val idStr = obj.optString("id", "")
                val idInt = obj.optInt("id", -1)
                if (idStr == stationId || (idInt != -1 && idInt.toString() == stationId)) {
                    foundLat = obj.optDouble("latitude", foundLat)
                    foundLon = obj.optDouble("longitude", foundLon)
                    return Pair(foundLat, foundLon)
                }
            }
            Pair(foundLat, foundLon)
        } catch (e: Exception) {
            Pair(39.4697, -0.3734)
        }
    }

    fun getCoordinatesForStation(context: Context, stationId: Int): Pair<Double, Double> {
        return getCoordinatesForStation(context, stationId.toString())
    }

    fun parseEmtXml(xmlString: String): List<EmtBusTime> {
        if (xmlString.isBlank()) return emptyList()
        val list = mutableListOf<EmtBusTime>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val inputStream = ByteArrayInputStream(xmlString.toByteArray(Charsets.UTF_8))
            val document = builder.parse(inputStream)
            document.documentElement.normalize()

            val tagNames = listOf("bus", "estimacion", "Estimacion", "Bus", "item", "prevision")
            var candidateNodes = document.getElementsByTagName("bus")
            for (tag in tagNames) {
                val nodes = document.getElementsByTagName(tag)
                if (nodes.length > 0) {
                    candidateNodes = nodes
                    break
                }
            }

            for (i in 0 until candidateNodes.length) {
                val node = candidateNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element

                    val linea = getFirstElementValue(element, "linea", "line", "id_linea", "num_linea") ?: ""
                    val destino = getFirstElementValue(element, "destino", "destination", "dest") ?: ""
                    val minutosRaw = getFirstElementValue(element, "minutos", "min", "minutes", "tiempo") ?: ""
                    val horaLlegadaRaw = getFirstElementValue(element, "horaLlegada", "horallegada", "hora", "arrival") ?: ""
                    val error = getFirstElementValue(element, "error") ?: ""

                    if (linea.isNotEmpty()) {
                        val isDiverted = error.contains("desvia", ignoreCase = true) ||
                                error.contains("desvío", ignoreCase = true) ||
                                error.contains("desvio", ignoreCase = true)
                        if (isDiverted) {
                            list.add(
                                EmtBusTime(
                                    linea = linea,
                                    destino = destino,
                                    minutos = "",
                                    horaLlegada = "",
                                    secondsRemaining = -1,
                                    isRealTime = true,
                                    isDiverted = true,
                                    divertedMessage = error.ifBlank { "LÍNIA DESVIADA" }
                                )
                            )
                        } else if (error.isEmpty()) {
                            val parsed = buildEmtBusTime(linea, destino, minutosRaw, horaLlegadaRaw)
                            if (parsed != null) {
                                list.add(parsed)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BusMapper", "DOM Parsing error, falling back to regex: ${e.message}")
        }

        // Regex Fallback in case DOM parser returned 0 items due to malformed XML/encoding
        if (list.isEmpty()) {
            try {
                val itemRegex = Regex("""<(?:bus|estimacion|item)>([\s\S]*?)</(?:bus|estimacion|item)>""", RegexOption.IGNORE_CASE)
                val matches = itemRegex.findAll(xmlString)
                for (match in matches) {
                    val block = match.groupValues[1]
                    val linea = Regex("""<linea>([\s\S]*?)</linea>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim() ?: ""
                    val rawDest = Regex("""<destino>([\s\S]*?)</destino>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim() ?: ""
                    val destino = rawDest.replace("<![CDATA[", "").replace("]]>", "").trim()
                    val minutos = Regex("""<minutos>([\s\S]*?)</minutos>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim() ?: ""
                    val hora = Regex("""<horaLlegada>([\s\S]*?)</horaLlegada>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim() ?: ""
                    val error = Regex("""<error>([\s\S]*?)</error>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim() ?: ""

                    if (linea.isNotEmpty()) {
                        val isDiverted = error.contains("desvia", ignoreCase = true) ||
                                error.contains("desvío", ignoreCase = true) ||
                                error.contains("desvio", ignoreCase = true)
                        if (isDiverted) {
                            list.add(
                                EmtBusTime(
                                    linea = linea,
                                    destino = destino,
                                    minutos = "",
                                    horaLlegada = "",
                                    secondsRemaining = -1,
                                    isRealTime = true,
                                    isDiverted = true,
                                    divertedMessage = error.ifBlank { "LÍNIA DESVIADA" }
                                )
                            )
                        } else if (error.isEmpty()) {
                            val parsed = buildEmtBusTime(linea, destino, minutos, hora)
                            if (parsed != null) {
                                list.add(parsed)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BusMapper", "Regex fallback error", e)
            }
        }

        return list
    }

    private fun buildEmtBusTime(
        linea: String,
        destino: String,
        minutosRaw: String,
        horaLlegadaRaw: String
    ): EmtBusTime? {
        var minutesClean = minutosRaw.trim()
        val now = System.currentTimeMillis()

        if (minutesClean.lowercase(Locale.ROOT).startsWith("pr")) {
            minutesClean = "1"
        } else if (minutesClean.contains(":")) {
            // It's already an "HH:mm" time! Calculate remaining minutes
            val parts = minutesClean.split(":")
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
            cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
            cal.set(Calendar.SECOND, 0)
            val diffMins = ((cal.timeInMillis - now) / 60000L).toInt()
            if (diffMins in 0..180) {
                minutesClean = diffMins.coerceAtLeast(1).toString()
            }
        } else {
            val digits = minutesClean.filter { it.isDigit() }
            if (digits.length == 4 || digits.length == 6) {
                // Formatted like HHmm or HHmmss
                val hh = digits.substring(0, 2).toIntOrNull() ?: 0
                val mm = digits.substring(2, 4).toIntOrNull() ?: 0
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, hh)
                cal.set(Calendar.MINUTE, mm)
                cal.set(Calendar.SECOND, 0)
                val diffMins = ((cal.timeInMillis - now) / 60000L).toInt()
                if (diffMins in 0..180) {
                    minutesClean = diffMins.coerceAtLeast(1).toString()
                } else {
                    minutesClean = "$hh:$mm"
                }
            } else if (digits.isNotEmpty()) {
                minutesClean = digits
            }
        }

        var finalHoraLlegada = horaLlegadaRaw.trim()
        if (finalHoraLlegada.isEmpty() && minutesClean.isNotEmpty() && !minutesClean.contains(":")) {
            val mins = minutesClean.toIntOrNull()
            if (mins != null) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MINUTE, mins)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                finalHoraLlegada = sdf.format(cal.time)
            }
        } else if (minutesClean.contains(":")) {
            finalHoraLlegada = minutesClean
        }
        if (finalHoraLlegada.isEmpty()) {
            finalHoraLlegada = "--:--"
        }

        val minsInt = minutesClean.toIntOrNull()
        val secs = if (minsInt != null) minsInt * 60 else -1

        return EmtBusTime(
            linea = linea,
            destino = destino,
            minutos = minutesClean,
            horaLlegada = finalHoraLlegada,
            secondsRemaining = secs
        )
    }

    private fun getFirstElementValue(element: Element, vararg tagNames: String): String? {
        for (tag in tagNames) {
            val nodeList = element.getElementsByTagName(tag)
            if (nodeList.length > 0) {
                val node = nodeList.item(0)
                if (node != null) {
                    val text = node.textContent
                    if (!text.isNullOrBlank()) return text.trim()
                }
            }
        }
        return null
    }
}
