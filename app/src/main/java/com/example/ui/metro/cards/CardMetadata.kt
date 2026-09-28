package com.example.ui.metro.cards

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.database.TransitCardEntity
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.dashboard.TransitTripUiModel

data class CardMetadata(
    val defaultName: String = "",
    val titleLower: String = "",
    val classLower: String = "",
    val cardType: String = "",
    val isMonthly: Boolean = false,
    val isTuiN: Boolean = false
) {
    companion object {
        fun create(defaultName: String, json: JSONObject, cardType: String): CardMetadata {
            val titleLower = defaultName.lowercase()
            val classLower = cardType.lowercase()
            val isMonthly = titleLower.contains("mensual") || classLower.contains("mensual")
            val isTuiN = titleLower.contains("tuin") || classLower.contains("tuin")
            return CardMetadata(
                defaultName = defaultName,
                titleLower = titleLower,
                classLower = classLower,
                cardType = cardType,
                isMonthly = isMonthly,
                isTuiN = isTuiN
            )
        }
    }
}

object MetroCardMapper {
    fun parseDateDefensively(rawFecha: String): Date? {
        if (rawFecha.isBlank()) return null
        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                return fmt.parse(rawFecha)
            } catch (_: Exception) {}
        }
        return null
    }

    fun getLatestInteractionDate(json: JSONObject): String {
        val trips = json.optJSONArray("viajes") ?: json.optJSONArray("movimientos")
        if (trips != null && trips.length() > 0) {
            val firstTrip = trips.optJSONObject(0)
            val f = firstTrip?.optString("fecha", firstTrip.optString("fechaHora", "")) ?: ""
            if (f.isNotBlank()) return f
        }
        val recarga = json.optString("fechaRecarga", json.optString("fecha_recarga", ""))
        if (recarga.isNotBlank()) return recarga
        return ""
    }

    fun getRemainingValueForCard(defaultName: String, json: JSONObject, cardType: String): String {
        val titleLower = defaultName.lowercase()
        val classLower = json.optString("clase", cardType).lowercase()
        val isTuiN = titleLower.contains("tuin") || classLower.contains("tuin") || titleLower.contains("monedero") || classLower.contains("monedero")
        val isMonthly = (titleLower.contains("mensual") || classLower.contains("mensual") || titleLower.contains("jove") || classLower.contains("jove") || titleLower.contains("abono") || classLower.contains("abono")) && !isTuiN

        val rawSaldoStr = json.optString("saldo", json.optString("saldoFormateado", json.optString("saldo_formatted", "")))

        if (isTuiN || cardType == "saldo") {
            if (rawSaldoStr.contains("€")) return rawSaldoStr
            val doubleVal = json.optDouble("saldo_restante", json.optDouble("saldo", -1.0))
            if (doubleVal >= 0) {
                val euros = if (doubleVal > 100) doubleVal / 100.0 else doubleVal
                return String.format(Locale.getDefault(), "%.2f €", euros)
            }
            if (rawSaldoStr.isNotBlank()) return rawSaldoStr
            return "0.00 €"
        } else if (isMonthly || cardType == "mensual") {
            val cad = json.optString("fechaCaducidad", json.optString("caducidad", json.optString("fecha_caducidad", "")))
            if (cad.isNotBlank()) return "Hasta $cad"
            if (rawSaldoStr.isNotBlank()) return rawSaldoStr
            return "Abono Activo"
        } else {
            if (rawSaldoStr.isNotBlank() && (rawSaldoStr.contains("viaje", ignoreCase = true) || rawSaldoStr.contains("viajes", ignoreCase = true))) {
                return rawSaldoStr
            }
            if (json.has("viajes_restantes")) {
                val v = json.optInt("viajes_restantes", 0)
                return if (v == 1) "1 viaje" else "$v viajes"
            }
            val doubleVal = json.optDouble("saldo_restante", json.optDouble("saldo", -1.0))
            if (doubleVal >= 0) {
                val trips = doubleVal.toInt()
                return if (trips == 1) "1 viaje" else "$trips viajes"
            }
            if (rawSaldoStr.isNotBlank()) return rawSaldoStr
            return "0 viajes"
        }
    }

    fun getRemainingValueForCard(meta: CardMetadata): String {
        return getRemainingValueForCard(meta.defaultName, JSONObject(), meta.cardType)
    }

    fun getCardCategory(defaultName: String, titleLower: String, classLower: String, cardType: String): String {
        val combined = "$defaultName $titleLower $classLower $cardType".lowercase()
        return when {
            combined.contains("tuin") && combined.contains("jove") -> "TUIN_JOVE"
            combined.contains("tuin") || combined.contains("tui n") -> "TUIN"
            combined.contains("suma") && combined.contains("mensual") && combined.contains("jove") -> "SUMA_MENSUAL_JOVE"
            combined.contains("suma mensual") || combined.contains("mensual") -> "SUMA_MENSUAL"
            combined.contains("suma t") || combined.contains("t-1") || combined.contains("t-2") || combined.contains("t-3") -> "SUMA_TSERIES"
            combined.contains("suma") -> "SUMA_SENCILLO"
            combined.contains("mobilis") || combined.contains("móbilis") -> "MOBILIS"
            else -> "OTHER"
        }
    }

    fun getCardCategory(meta: CardMetadata): String {
        return getCardCategory(
            defaultName = meta.defaultName,
            titleLower = meta.titleLower,
            classLower = meta.classLower,
            cardType = meta.cardType
        )
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
        val manuallyInactive = detailsObj.optBoolean("manually_inactive", false)
        if (manuallyInactive) return true

        val valStr = getRemainingValueForCard(defaultName, detailsObj, cardType)
        if (valStr.contains("0 viajes") || valStr.contains("0.00 €") || valStr.contains("0,00 €")) return true

        val fechaCaducidad = detailsObj.optString("fechaCaducidad", detailsObj.optString("fecha_caducidad", ""))
        val cadDate = parseDateDefensively(fechaCaducidad)
        if (cadDate != null && cadDate.before(Date())) return true

        return false
    }

    fun isCardFaded(meta: CardMetadata): Boolean {
        return isCardFaded(meta.defaultName, meta.titleLower, meta.classLower, meta.cardType, JSONObject(), meta.isMonthly, meta.isTuiN)
    }

    fun mapToUiModel(card: TransitCardEntity): TransitCardUiModel {
        val detailsObj = try { JSONObject(card.detailsJson) } catch (_: Exception) { JSONObject() }
        val showOnHome = detailsObj.optBoolean("show_on_home", true)
        val customOrder = detailsObj.optInt("custom_order", 0)
        val isManuallyInactive = detailsObj.optBoolean("manually_inactive", false)
        val title = detailsObj.optString("titulo", detailsObj.optString("nombre", card.defaultName)).ifEmpty { card.defaultName }
        val clase = detailsObj.optString("clase", card.cardType)
        val titleLower = title.lowercase()
        val classLower = clase.lowercase()
        val isTuiN = titleLower.contains("tuin") || classLower.contains("tuin") || titleLower.contains("monedero") || classLower.contains("monedero")
        val isMonthly = (titleLower.contains("mensual") || classLower.contains("mensual") || titleLower.contains("jove") || classLower.contains("jove") || titleLower.contains("abono") || classLower.contains("abono")) && !isTuiN

        val cat = getCardCategory(title, titleLower, classLower, card.cardType)

        var calcValue = getRemainingValueForCard(title, detailsObj, card.cardType)
        if (calcValue.isBlank() || calcValue == "0 viajes") {
            if (card.remainingValue.isNotBlank() && card.remainingValue != "0 viajes") {
                calcValue = card.remainingValue
            }
        }

        val faded = isCardFaded(title, titleLower, classLower, card.cardType, detailsObj, isMonthly, isTuiN)

        val viajesArr = detailsObj.optJSONArray("viajes")
            ?: detailsObj.optJSONArray("movimientos")
            ?: detailsObj.optJSONArray("validaciones")
        val viajesList = mutableListOf<TransitTripUiModel>()
        if (viajesArr != null) {
            for (i in 0 until viajesArr.length()) {
                val item = viajesArr.optJSONObject(i) ?: continue
                val estacion = item.optString("estacion", item.optString("estacio", item.optString("parada", item.optString("title", ""))))
                val fecha = item.optString("fecha", item.optString("fechaHora", item.optString("date", "")))
                val tipoValidacion = item.optString("tipoValidacion", item.optString("tipo", item.optString("operacion", "Validación")))
                val zona = item.optString("zona", item.optString("zonas", ""))
                if (estacion.isNotBlank() || fecha.isNotBlank()) {
                    viajesList.add(TransitTripUiModel(estacion, fecha, tipoValidacion, zona))
                }
            }
        }

        val fechaCaducidad = detailsObj.optString("fechaCaducidad", detailsObj.optString("fecha_caducidad", detailsObj.optString("caducidad", "")))
        val fechaRecarga = detailsObj.optString("fechaRecarga", detailsObj.optString("fecha_recarga", detailsObj.optString("recarga", "")))

        return TransitCardUiModel(
            entity = card,
            cardNumber = card.cardNumber,
            assignedName = card.assignedName,
            defaultName = card.defaultName,
            cardType = card.cardType,
            remainingValue = calcValue,
            detailsJson = card.detailsJson,
            isFaded = faded,
            isManuallyInactive = isManuallyInactive,
            showOnHome = showOnHome,
            customOrder = customOrder,
            category = cat,
            title = title,
            clase = clase,
            operador = detailsObj.optString("operador", detailsObj.optString("operadora", "ATMVALENCIA")),
            zonas = detailsObj.optString("zonas", detailsObj.optString("zona", "")),
            ampliado = detailsObj.optString("ampliado", detailsObj.optString("ampliadoFormateado", "")),
            fechaCaducidad = fechaCaducidad,
            fechaRecarga = fechaRecarga,
            isCurrentlyActive = !faded,
            viajesList = viajesList
        )
    }
}
