package com.example.ui.metro

import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight


import com.example.ui.metro.cards.CardMetadata
import com.example.ui.metro.cards.MetroCardMapper
import com.example.data.database.TransitCardEntity
import com.example.data.model.MetroStation
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.dashboard.TransitTripUiModel
import org.json.JSONObject
import java.text.Normalizer
import java.util.Date

typealias CardMetadata = com.example.ui.metro.cards.CardMetadata

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

    fun parseDateDefensively(rawFecha: String): Date? = MetroCardMapper.parseDateDefensively(rawFecha)

    fun getLatestInteractionDate(json: JSONObject): String = MetroCardMapper.getLatestInteractionDate(json)

    fun getRemainingValueForCard(meta: CardMetadata): String = MetroCardMapper.getRemainingValueForCard(meta)

    fun getRemainingValueForCard(defaultName: String, json: JSONObject, cardType: String): String =
        MetroCardMapper.getRemainingValueForCard(defaultName, json, cardType)

    fun getCardCategory(meta: CardMetadata): String = MetroCardMapper.getCardCategory(meta)

    fun getCardCategory(defaultName: String, titleLower: String, classLower: String, cardType: String): String =
        MetroCardMapper.getCardCategory(defaultName, titleLower, classLower, cardType)

    fun isCardFaded(meta: CardMetadata): Boolean = MetroCardMapper.isCardFaded(meta)

    fun isCardFaded(
        defaultName: String,
        titleLower: String,
        classLower: String,
        cardType: String,
        detailsObj: JSONObject,
        isMonthly: Boolean,
        isTuiN: Boolean
    ): Boolean = MetroCardMapper.isCardFaded(defaultName, titleLower, classLower, cardType, detailsObj, isMonthly, isTuiN)

    fun mapToUiModel(card: TransitCardEntity): TransitCardUiModel = MetroCardMapper.mapToUiModel(card)

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
        val shouldBlink = isNow || seconds <= 30

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

    /**
     * Agrupa todas las salidas de metro de la estación por línea y sentido/dirección en cuadros individuales (LineDeparturesGroupUiModel),
     * mostrando para cada grupo la salida más inmediata como principal y las siguientes salidas en orden cronológico.
     * La lista resultante se ordena por tiempo de salida inminente.
     */
    fun groupDeparturesByLineAndDirection(
        departures: List<RealTimeDeparture>,
        currentStationId: String,
        currentStationName: String,
        lineStationsMap: Map<String, List<com.example.data.repository.LineStationInfo>>,
        appLanguage: com.example.ui.dashboard.AppLanguage,
        texts: com.example.ui.dashboard.Translation,
        isDarkMode: Boolean,
        sharedLineDigitsGetter: (String) -> List<String>
    ): List<LineDeparturesGroupUiModel> {
        if (departures.isEmpty()) return emptyList()

        // 1. Clasificamos cada salida con su sentido/dirección
        val classified = departures.map { departure ->
            val cleanLine = departure.lineId.filter { it.isDigit() }
            val stationsForLine = lineStationsMap["L$cleanLine"]
                ?: lineStationsMap[cleanLine]
                ?: lineStationsMap[departure.lineId]
                ?: emptyList()
            val direction = MetroDirectionClassifier.classifyDepartureDirection(
                lineId = departure.lineId,
                destination = departure.destination,
                currentStationId = currentStationId,
                currentStationName = currentStationName,
                lineStations = stationsForLine
            )
            Triple(departure.lineId, direction, departure)
        }

        // 2. Agrupamos por par (lineId, direction)
        val groupedByLineAndDir = classified.groupBy { Pair(it.first, it.second) }

        // 3. Convertimos cada grupo a LineDeparturesGroupUiModel
        return groupedByLineAndDir.values.map { itemsInGroup ->
            val sortedDeps = itemsInGroup.map { it.third }.sortedBy { it.secondsRemaining }
            val primaryDep = sortedDeps.first()
            val primaryUiModel = toDepartureUiModel(
                primaryDep,
                primaryDep.secondsRemaining,
                appLanguage,
                texts,
                isDarkMode,
                sharedLineDigitsGetter
            )

            val subsequent = sortedDeps.drop(1).map { dep ->
                val mins = maxOf(0, dep.secondsRemaining / 60)
                UpcomingDepartureSummary(
                    id = dep.id,
                    destination = dep.destination,
                    minutesRemaining = mins,
                    estimatedTime = dep.estimatedTime,
                    status = dep.status,
                    track = dep.track,
                    originalDeparture = dep
                )
            }

            val cleanDigit = primaryDep.lineId.filter { it.isDigit() }
            val sharedDigits = sharedLineDigitsGetter(cleanDigit).filter { it != cleanDigit }

            val groupDirection = itemsInGroup.first().second
            val terminus = MetroDirectionClassifier.getCanonicalLineTerminus(primaryDep.lineId, groupDirection, currentStationId)

            LineDeparturesGroupUiModel(
                lineId = primaryDep.lineId,
                primaryDestination = primaryDep.destination,
                colorHex = primaryDep.colorHex,
                primaryDeparture = primaryUiModel,
                subsequentDepartures = subsequent,
                sharedDigits = sharedDigits,
                direction = groupDirection,
                directionTerminusName = terminus,
                stationId = currentStationId,
                stationName = currentStationName
            )
        }.sortedBy { it.primaryDeparture.originalDeparture.secondsRemaining }
    }

    /**
     * Agrupa una lista de salidas de metro en dos sentidos topológicos (0 y 1) y dentro de cada sentido
     * agrupa las salidas por línea (L1, L2, L3...) mostrando la salida más inmediata como principal
     * y las siguientes salidas en orden cronológico con sus minutos restantes.
     */
    fun groupDeparturesByDirection(
        departures: List<RealTimeDeparture>,
        currentStationId: String,
        currentStationName: String,
        lineStationsMap: Map<String, List<com.example.data.repository.LineStationInfo>>,
        appLanguage: com.example.ui.dashboard.AppLanguage,
        texts: com.example.ui.dashboard.Translation,
        isDarkMode: Boolean,
        sharedLineDigitsGetter: (String) -> List<String>
    ): Map<Int, List<LineDeparturesGroupUiModel>> {
        if (departures.isEmpty()) return emptyMap()

        // 1. Clasificamos cada salida en dirección 0 o 1
        val classified = departures.map { departure ->
            val cleanLine = departure.lineId.filter { it.isDigit() }
            val stationsForLine = lineStationsMap["L$cleanLine"]
                ?: lineStationsMap[cleanLine]
                ?: lineStationsMap[departure.lineId]
                ?: emptyList()
            val direction = MetroDirectionClassifier.classifyDepartureDirection(
                lineId = departure.lineId,
                destination = departure.destination,
                currentStationId = currentStationId,
                currentStationName = currentStationName,
                lineStations = stationsForLine
            )
            direction to departure
        }

        // 2. Agrupamos por dirección
        val byDirection = classified.groupBy({ it.first }, { it.second })

        // 3. Para cada dirección, agrupamos por línea
        return byDirection.mapValues { (_, depsInDirection) ->
            // Ordenar por tiempo de salida
            val sortedDeps = depsInDirection.sortedBy { it.secondsRemaining }

            // Agrupar por línea
            val byLine = sortedDeps.groupBy { it.lineId }

            byLine.map { (lineId, lineDeps) ->
                val primaryDep = lineDeps.first()
                val primaryUiModel = toDepartureUiModel(
                    primaryDep,
                    primaryDep.secondsRemaining,
                    appLanguage,
                    texts,
                    isDarkMode,
                    sharedLineDigitsGetter
                )

                val subsequent = lineDeps.drop(1).map { dep ->
                    val mins = maxOf(0, dep.secondsRemaining / 60)
                    UpcomingDepartureSummary(
                        id = dep.id,
                        destination = dep.destination,
                        minutesRemaining = mins,
                        originalDeparture = dep
                    )
                }

                val cleanDigit = lineId.filter { it.isDigit() }
                val sharedDigits = sharedLineDigitsGetter(cleanDigit).filter { it != cleanDigit }

                LineDeparturesGroupUiModel(
                    lineId = lineId,
                    primaryDestination = primaryDep.destination,
                    colorHex = primaryDep.colorHex,
                    primaryDeparture = primaryUiModel,
                    subsequentDepartures = subsequent,
                    sharedDigits = sharedDigits,
                    stationId = currentStationId,
                    stationName = currentStationName
                )
            }.sortedBy { it.primaryDeparture.originalDeparture.secondsRemaining }
        }
    }
}
