package com.example.ui.metro

import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight


import com.example.data.database.TransitCardEntity
import com.example.data.model.MetroStation
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.dashboard.TransitTripUiModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CardMetadata(
    val defaultName: String,
    val cardType: String,
    val detailsObj: JSONObject,
    val classLower: String = detailsObj.optString("clase", "").lowercase(),
    val titleLower: String = detailsObj.optString("titulo", "").lowercase(),
    val defaultNameLower: String = defaultName.lowercase()
) {
    val isTuiN: Boolean = defaultNameLower.contains("tuin") || defaultNameLower.contains("monedero") ||
            classLower.contains("tuin") || classLower.contains("monedero") ||
            titleLower.contains("tuin") || titleLower.contains("monedero")

    val isMonthly: Boolean = (defaultNameLower.contains("mensual") || defaultNameLower.contains("men.") || defaultNameLower.contains("abono") || defaultNameLower.contains("jove") || defaultNameLower.contains("limitado") ||
            classLower.contains("mensual") || classLower.contains("men.") || classLower.contains("abono") || classLower.contains("jove") || classLower.contains("limitado") ||
            titleLower.contains("mensual") || titleLower.contains("men.") || titleLower.contains("abono") || titleLower.contains("jove") || titleLower.contains("limitado") ||
            cardType == "mensual") && !isTuiN

    companion object {
        fun create(defaultName: String, json: JSONObject, cardType: String): CardMetadata {
            return CardMetadata(
                defaultName = defaultName,
                cardType = cardType,
                detailsObj = json
            )
        }
    }
}

object MetroMapper {

    fun extractLineNumbersFromText(text: String): String? {
        val regex = Regex("L[0-9]+")
        val matches = regex.findAll(text).map { it.value }.toList()
        return if (matches.isNotEmpty()) matches.joinToString(", ") else null
    }

    fun generateStationSlug(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutAccents.lowercase().replace(" ", "_").replace("'", "").replace(".", "").replace("-", "_")
    }

    fun getStationSlug(stationId: String, stations: List<MetroStation>): String {
        val station = stations.find { it.id == stationId }
        return if (station != null) {
            generateStationSlug(station.name)
        } else {
            stationId
        }
    }

    fun String.normalize(): String {
        val accents = mapOf(
            'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
            'à' to 'a', 'è' to 'e', 'ò' to 'o',
            'ä' to 'a', 'ë' to 'e', 'ï' to 'i', 'ö' to 'o', 'ü' to 'u',
            'Á' to 'A', 'É' to 'E', 'Í' to 'I', 'Ó' to 'O', 'Ú' to 'U',
            'À' to 'A', 'È' to 'E', 'Ò' to 'O'
        )
        return this.map { accents[it] ?: it }.joinToString("").lowercase()
    }

    fun normalizeForSort(name: String): String {
        return name.normalize()
    }

    fun parseDateDefensively(rawFecha: String): Date? {
        val cleanStr = rawFecha.trim()
        if (cleanStr.isEmpty()) return null
        val formats = listOf(
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = false
                return sdf.parse(cleanStr)
            } catch (e: Exception) {
                // continue
            }
        }
        return null
    }

    fun getLatestInteractionDate(json: JSONObject): String {
        val dates = mutableListOf<String>()
        val f = json.optString("fecha", "").trim()
        if (f.isNotEmpty()) dates.add(f)

        val viajes = json.optJSONArray("viajes")
        if (viajes != null && viajes.length() > 0) {
            for (i in 0 until viajes.length()) {
                val v = viajes.optJSONObject(i)
                if (v != null) {
                    val vf = v.optString("fecha", "").trim()
                    if (vf.isNotEmpty()) dates.add(vf)
                }
            }
        }

        var newestDate: Date? = null
        var newestStr = ""
        for (dStr in dates) {
            val parsed = parseDateDefensively(dStr)
            if (parsed != null) {
                if (newestDate == null || parsed.after(newestDate)) {
                    newestDate = parsed
                    newestStr = dStr
                }
            }
        }

        return newestStr.ifEmpty { f }
    }

    fun getRemainingValueForCard(meta: CardMetadata): String {
        val json = meta.detailsObj

        if (meta.isTuiN) {
            val saldoStr = json.optString("saldo", "").trim()
            val clean = saldoStr.filter { it.isDigit() || it == '.' || it == ',' }
            val parsedVal = clean.replace(",", ".").toDoubleOrNull()
            val finalEuros = if (parsedVal != null) {
                if (clean.contains('.') || clean.contains(',')) {
                    parsedVal
                } else {
                    parsedVal / 100.0
                }
            } else {
                val rawImporte = json.optDouble("importe", -1.0)
                if (rawImporte > 0) {
                    rawImporte / 100.0
                } else {
                    val balanceVal = json.optDouble("saldo_restante", -1.0)
                    if (balanceVal >= 0) {
                        balanceVal / 100.0
                    } else {
                        0.0
                    }
                }
            }
            return String.format(Locale.US, "%.2f", finalEuros).replace('.', ',') + " €"
        }

        if (meta.isMonthly) {
            val isRecargado: Boolean = if (json.has("recargado")) {
                val rawObj = json.get("recargado")
                val explicitRecargado = when (rawObj) {
                    is Boolean -> rawObj
                    is Number -> rawObj.toDouble() > 0
                    is String -> {
                        val s = rawObj.lowercase().trim()
                        s == "si" || s == "sí" || s == "true" || s == "1"
                    }
                    else -> false
                }
                explicitRecargado || getLatestInteractionDate(json).isNotEmpty()
            } else {
                getLatestInteractionDate(json).isNotEmpty()
            }

            var remainingVal = "Sin recarga activa"
            if (isRecargado) {
                val rawFecha = getLatestInteractionDate(json)
                if (rawFecha.isNotEmpty()) {
                    val parsedDate = parseDateDefensively(rawFecha)
                    if (parsedDate != null) {
                        val calendar = Calendar.getInstance()
                        calendar.time = parsedDate
                        calendar.add(Calendar.DAY_OF_YEAR, 30)
                        val formatOut = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        remainingVal = "Validez: ${formatOut.format(calendar.time)}"
                    }
                }
            }
            return remainingVal
        }

        val saldoStr = json.optString("saldo", "").trim()
        val viajes = if (saldoStr.isNotEmpty()) {
            saldoStr.filter { it.isDigit() }
        } else {
            val trips = json.optInt("viajes_restantes", -1)
            if (trips >= 0) trips.toString() else "0"
        }
        val cleanViajes = if (viajes.isEmpty()) "0" else viajes
        return "$cleanViajes viajes restantes"
    }

    fun getRemainingValueForCard(defaultName: String, json: JSONObject, cardType: String): String {
        return getRemainingValueForCard(CardMetadata.create(defaultName, json, cardType))
    }

    fun getCardCategory(meta: CardMetadata): String {
        val defaultName = meta.defaultName
        val defaultNameLower = meta.defaultNameLower
        val titleLower = meta.titleLower
        val classLower = meta.classLower

        if (meta.isTuiN) {
            return "TUIN"
        }

        val isSuma = defaultNameLower.contains("suma") || titleLower.contains("suma")

        if (isSuma) {
            val tRegex = "(?i)\\bT[1-3](\\+)?(?![0-9])".toRegex()
            val matchesT = tRegex.containsMatchIn(defaultName) ||
                           tRegex.containsMatchIn(titleLower) ||
                           tRegex.containsMatchIn(classLower)
            if (matchesT) {
                return "SUMA_TSERIES"
            }

            if (meta.isMonthly) {
                return "SUMA_MENSUAL"
            }

            return "SUMA_SENCILLO"
        }

        val isMobilis = defaultNameLower.contains("móbilis") || defaultNameLower.contains("mobilis") ||
                        titleLower.contains("móbilis") || titleLower.contains("mobilis")
        if (isMobilis) {
            return "MOBILIS"
        }

        return "OTHER"
    }

    fun getCardCategory(defaultName: String, titleLower: String, classLower: String, cardType: String): String {
        val dummyJson = JSONObject().apply {
            put("titulo", titleLower)
            put("clase", classLower)
        }
        return getCardCategory(CardMetadata.create(defaultName, dummyJson, cardType))
    }

    fun isCardFaded(meta: CardMetadata): Boolean {
        val detailsObj = meta.detailsObj
        if (detailsObj.optBoolean("manually_inactive", false)) {
            return true
        }

        val possibleDateKeys = listOf(
            "caducidad", "fecha_caducidad", "validez", "nueva_validez", 
            "fecha_nueva_validez", "limite", "fecha_limite", "expira"
        )
        var hasPassedExpirationDate = false
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        for (key in possibleDateKeys) {
            if (detailsObj.has(key)) {
                val rawVal = detailsObj.optString(key, "").trim()
                if (rawVal.isNotEmpty()) {
                    val parsedDate = parseDateDefensively(rawVal)
                    if (parsedDate != null) {
                        val expCal = Calendar.getInstance()
                        expCal.time = parsedDate
                        expCal.set(Calendar.HOUR_OF_DAY, 23)
                        expCal.set(Calendar.MINUTE, 59)
                        expCal.set(Calendar.SECOND, 59)
                        expCal.set(Calendar.MILLISECOND, 999)
                        if (today.after(expCal)) {
                            hasPassedExpirationDate = true
                        }
                    }
                }
            }
        }

        if (hasPassedExpirationDate) {
            return true
        }

        if (meta.isMonthly) {
            val rawFecha = getLatestInteractionDate(detailsObj)
            if (rawFecha.isNotEmpty()) {
                val parsedDate = parseDateDefensively(rawFecha)
                if (parsedDate != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = parsedDate
                    calendar.add(Calendar.DAY_OF_YEAR, 30)

                    val expCal = Calendar.getInstance()
                    expCal.time = calendar.time
                    expCal.set(Calendar.HOUR_OF_DAY, 23)
                    expCal.set(Calendar.MINUTE, 59)
                    expCal.set(Calendar.SECOND, 59)
                    expCal.set(Calendar.MILLISECOND, 999)

                    if (today.after(expCal)) {
                        return true
                    }
                }
            } else {
                val isRecargadoStr = detailsObj.optString("recargado", "").lowercase().trim()
                val isRecargado = isRecargadoStr == "si" || isRecargadoStr == "sí" || isRecargadoStr == "true" || isRecargadoStr == "1" || detailsObj.optBoolean("recargado", false)
                if (!isRecargado) {
                    return true
                }
            }
            return false
        } else {
            if (meta.isTuiN) {
                val saldoStr = detailsObj.optString("saldo", "").trim()
                val clean = saldoStr.filter { it.isDigit() || it == '.' || it == ',' }
                val parsedVal = clean.replace(",", ".").toDoubleOrNull()
                val finalEuros = if (parsedVal != null) {
                    if (clean.contains('.') || clean.contains(',')) {
                        parsedVal
                    } else {
                        parsedVal / 100.0
                    }
                } else {
                    val rawImporte = detailsObj.optDouble("importe", -1.0)
                    if (rawImporte > 0) {
                        rawImporte / 100.0
                    } else {
                        val balanceVal = detailsObj.optDouble("saldo_restante", -1.0)
                        if (balanceVal >= 0) {
                            balanceVal / 100.0
                        } else {
                            0.0
                        }
                    }
                }
                return finalEuros <= 0.0
            } else {
                val saldoStr = detailsObj.optString("saldo", "").trim()
                val viajesStr = if (saldoStr.isNotEmpty()) {
                    saldoStr.filter { it.isDigit() }
                } else {
                    val trips = detailsObj.optInt("viajes_restantes", -1)
                    if (trips >= 0) trips.toString() else "0"
                }
                val trips = viajesStr.toIntOrNull() ?: 0
                return trips <= 0
            }
        }
    }

    fun isCardFaded(
        defaultName: String,
        titleLower: String,
        classLower: String,
        cardType: String,
        detailsObj: JSONObject,
        isMonthly: Boolean,
        isTuiN: Boolean
    ): Boolean {
        return isCardFaded(CardMetadata.create(defaultName, detailsObj, cardType))
    }

    fun mapToUiModel(card: TransitCardEntity): TransitCardUiModel {
        val detailsObj = try { JSONObject(card.detailsJson) } catch (e: Exception) { JSONObject() }
        val meta = CardMetadata.create(card.defaultName, detailsObj, card.cardType)

        val categoryStr = getCardCategory(meta)
        val isFaded = isCardFaded(meta)

        val clase = detailsObj.optString("clase", "No definida")
        val titulo = detailsObj.optString("titulo", card.defaultName)
        val operador = detailsObj.optString("operador", "Metrovalencia")
        val zonas = detailsObj.optString("zonas", "No definida")

        val rawFecha = getLatestInteractionDate(detailsObj)

        var calculatedCaducidad = "Sin recarga activa"
        var isCurrentlyActive = false

        if (meta.isMonthly) {
            val isRecargado: Boolean = if (detailsObj.has("recargado")) {
                val rawObj = detailsObj.get("recargado")
                val explicitRecargado = when (rawObj) {
                    is Boolean -> rawObj
                    is Number -> rawObj.toDouble() > 0
                    is String -> {
                        val s = rawObj.lowercase().trim()
                        s == "si" || s == "sí" || s == "true" || s == "1"
                    }
                    else -> false
                }
                explicitRecargado || rawFecha.isNotEmpty()
            } else {
                rawFecha.isNotEmpty()
            }
            if (isRecargado && rawFecha.isNotEmpty()) {
                val parsedDate = parseDateDefensively(rawFecha)
                if (parsedDate != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = parsedDate
                    calendar.add(Calendar.DAY_OF_YEAR, 30)
                    val sdfOut = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    calculatedCaducidad = sdfOut.format(calendar.time)

                    val today = Calendar.getInstance()
                    today.set(Calendar.HOUR_OF_DAY, 0)
                    today.set(Calendar.MINUTE, 0)
                    today.set(Calendar.SECOND, 0)
                    today.set(Calendar.MILLISECOND, 0)

                    val expCal = Calendar.getInstance()
                    expCal.time = calendar.time
                    expCal.set(Calendar.HOUR_OF_DAY, 23)
                    expCal.set(Calendar.MINUTE, 59)
                    expCal.set(Calendar.SECOND, 59)
                    expCal.set(Calendar.MILLISECOND, 999)

                    isCurrentlyActive = !today.after(expCal)
                }
            }
        }

        val ampliadoValue = if (meta.isMonthly) {
            if (isCurrentlyActive) {
                "Ampliado"
            } else {
                "No ampliado"
            }
        } else {
            val ampliadoRaw = detailsObj.optString("ampliado", "").lowercase().trim()
            if (ampliadoRaw == "s" || ampliadoRaw == "true" || ampliadoRaw == "1" || (detailsObj.has("ampliado") && detailsObj.optBoolean("ampliado"))) {
                "Ampliado"
            } else {
                "No ampliado"
            }
        }

        val tripsList = mutableListOf<TransitTripUiModel>()
        val viajesArray = detailsObj.optJSONArray("viajes")
        if (viajesArray != null) {
            for (i in 0 until viajesArray.length()) {
                val viajeObj = viajesArray.optJSONObject(i)
                if (viajeObj != null) {
                    val estacion = viajeObj.optString("estacion", "Estación")
                    val fechaViaje = viajeObj.optString("fecha", "Fecha no disponible")
                    val tipoValidacion = viajeObj.optString("tipoValidacion", "").ifEmpty {
                        viajeObj.optString("tipo_validacion", "").ifEmpty { "Validación" }
                    }
                    val zonaViaje = viajeObj.optString("zona", "")
                    tripsList.add(TransitTripUiModel(estacion, fechaViaje, tipoValidacion, zonaViaje))
                }
            }
        }

        tripsList.sortByDescending { voyage ->
            parseDateDefensively(voyage.fecha)?.time ?: 0L
        }

        return TransitCardUiModel(
            entity = card,
            cardNumber = card.cardNumber,
            assignedName = card.assignedName,
            defaultName = card.defaultName,
            cardType = card.cardType,
            remainingValue = card.remainingValue,
            detailsJson = card.detailsJson,
            isFaded = isFaded,
            isManuallyInactive = detailsObj.optBoolean("manually_inactive", false),
            category = categoryStr,
            title = titulo,
            clase = clase,
            operador = operador,
            zonas = zonas,
            ampliado = ampliadoValue,
            fechaCaducidad = calculatedCaducidad,
            fechaRecarga = rawFecha,
            isCurrentlyActive = isCurrentlyActive,
            viajesList = tripsList
        )
    }

    fun toDepartureUiModel(
        departure: RealTimeDeparture,
        secondsRemaining: Int,
        appLanguage: com.example.ui.dashboard.AppLanguage,
        texts: com.example.ui.dashboard.Translation,
        isDarkMode: Boolean = true,
        sharedLineDigitsGetter: (String) -> List<String>
    ): DepartureUiModel {
        val seconds = secondsRemaining
        val isNow = seconds <= 0
        val mins = seconds / 60
        val secs = seconds % 60

        val timeAnnotated = when {
            isNow -> buildAnnotatedString { append(texts.immediateValue) }
            seconds <= 30 -> buildAnnotatedString { append(if (appLanguage == com.example.ui.dashboard.AppLanguage.CA) "En estació" else "En estación") }
            seconds <= 50 -> buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFFFA500))) {
                    append(if (appLanguage == com.example.ui.dashboard.AppLanguage.CA) "Arribant" else "Llegando")
                }
            }
            seconds <= 180 -> buildAnnotatedString {
                append("$mins min ")
                withStyle(SpanStyle(color = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF555555), fontWeight = FontWeight.SemiBold)) {
                    append(String.format("%02d", secs))
                }
            }
            else -> buildAnnotatedString { append("$mins min") }
        }

        val isWarningColor = isNow || seconds <= 30
        val isSecondaryColor = !isWarningColor && seconds <= 50
        val shouldBlink = isNow || seconds <= 20

        val numericDigit = departure.lineId.filter { it.isDigit() }
        val sharedDigits = sharedLineDigitsGetter(numericDigit).filter { it != numericDigit }
        
        val bottomSheetIsNow = departure.minutesRemaining <= 0
        val bottomSheetText = if (bottomSheetIsNow) {
            if (appLanguage == com.example.ui.dashboard.AppLanguage.CA) "Eixint ara mateix" else "Saliendo ahora mismo"
        } else {
            if (appLanguage == com.example.ui.dashboard.AppLanguage.CA) "Pròxima eixida en ${departure.minutesRemaining} min" else "Próxima salida en ${departure.minutesRemaining} min"
        }

        return DepartureUiModel(
            id = departure.id,
            lineId = departure.lineId,
            destination = departure.destination,
            colorHex = departure.colorHex,
            secondsRemaining = seconds,
            isNow = isNow,
            timeAnnotated = timeAnnotated,
            bottomSheetText = bottomSheetText,
            isWarningColor = isWarningColor,
            isSecondaryColor = isSecondaryColor,
            shouldBlink = shouldBlink,
            numericDigit = numericDigit,
            sharedDigits = sharedDigits,
            originalDeparture = departure
        )
    }
}
