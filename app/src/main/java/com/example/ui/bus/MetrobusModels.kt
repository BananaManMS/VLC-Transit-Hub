package com.example.ui.bus

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

data class MetrobusStop(
    val idParada: String,
    val denominacion: String,
    val lat: Double,
    val lon: Double,
    val lineas: List<String> = emptyList(),
    val distanceText: String = ""
)

data class MetrobusDays(
    val monday: Boolean = false,
    val tuesday: Boolean = false,
    val wednesday: Boolean = false,
    val thursday: Boolean = false,
    val friday: Boolean = false,
    val saturday: Boolean = false,
    val sunday: Boolean = false
) {
    companion object {
        fun fromJson(jsonObj: JSONObject?, jsonArr: JSONArray?): MetrobusDays {
            if (jsonArr != null) {
                var m = false; var t = false; var w = false; var th = false; var f = false; var sa = false; var su = false
                for (i in 0 until jsonArr.length()) {
                    val dayStr = jsonArr.optString(i, "").lowercase().trim()
                    when {
                        dayStr.contains("lunes") -> m = true
                        dayStr.contains("martes") -> t = true
                        dayStr.contains("miércoles") || dayStr.contains("miercoles") -> w = true
                        dayStr.contains("jueves") -> th = true
                        dayStr.contains("viernes") -> f = true
                        dayStr.contains("sábado") || dayStr.contains("sabado") -> sa = true
                        dayStr.contains("domingo") || dayStr.contains("festivo") -> su = true
                        dayStr.contains("laborable") || dayStr.contains("l-v") -> {
                            m = true; t = true; w = true; th = true; f = true
                        }
                        dayStr.contains("diario") || dayStr.contains("todos") -> {
                            m = true; t = true; w = true; th = true; f = true; sa = true; su = true
                        }
                    }
                }
                return MetrobusDays(
                    monday = m, tuesday = t, wednesday = w, thursday = th, friday = f, saturday = sa, sunday = su
                )
            }

            if (jsonObj != null) {
                return MetrobusDays(
                    monday = jsonObj.optInt("monday", 0) == 1 || jsonObj.optBoolean("monday", false) || jsonObj.optInt("lunes", 0) == 1,
                    tuesday = jsonObj.optInt("tuesday", 0) == 1 || jsonObj.optBoolean("tuesday", false) || jsonObj.optInt("martes", 0) == 1,
                    wednesday = jsonObj.optInt("wednesday", 0) == 1 || jsonObj.optBoolean("wednesday", false) || jsonObj.optInt("miercoles", 0) == 1 || jsonObj.optInt("miércoles", 0) == 1,
                    thursday = jsonObj.optInt("thursday", 0) == 1 || jsonObj.optBoolean("thursday", false) || jsonObj.optInt("jueves", 0) == 1,
                    friday = jsonObj.optInt("friday", 0) == 1 || jsonObj.optBoolean("friday", false) || jsonObj.optInt("viernes", 0) == 1,
                    saturday = jsonObj.optInt("saturday", 0) == 1 || jsonObj.optBoolean("saturday", false) || jsonObj.optInt("sabado", 0) == 1 || jsonObj.optInt("sábado", 0) == 1,
                    sunday = jsonObj.optInt("sunday", 0) == 1 || jsonObj.optBoolean("sunday", false) || jsonObj.optInt("domingo", 0) == 1
                )
            }

            // Fallback default: active all days
            return MetrobusDays(
                monday = true, tuesday = true, wednesday = true, thursday = true, friday = true, saturday = true, sunday = true
            )
        }
    }
}

data class MetrobusDeparture(
    val departureTime: String, // e.g., "05:03:00"
    val tripId: String,
    val headsign: String,
    val serviceId: String,
    val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeColor: String?,
    val agencyName: String,
    val days: MetrobusDays
) {
    companion object {
        fun fromJson(json: JSONObject): MetrobusDeparture {
            val lineCode = json.optString("line", json.optString("route_short_name", json.optString("linea", "")))
            val headsignVal = json.optString("headsign", json.optString("destine", json.optString("destino", json.optString("route_long_name", ""))))
            val departureTimeVal = json.optString("departure_time", json.optString("hora", "00:00:00"))
            val tripIdVal = json.optString("trip_id", "")
            val serviceIdVal = json.optString("service_id", "")
            val routeIdVal = json.optString("route_id", lineCode)
            val routeShortNameVal = if (lineCode.isNotBlank()) lineCode else json.optString("route_short_name", "MB")
            val routeLongNameVal = json.optString("route_long_name", headsignVal)
            val routeColorVal = if (json.isNull("route_color")) null else json.optString("route_color", null)
            val agencyNameVal = json.optString("agency_name", "Metrobús")

            val daysObj = json.optJSONObject("days")
            val daysArr = json.optJSONArray("days")

            return MetrobusDeparture(
                departureTime = departureTimeVal,
                tripId = tripIdVal,
                headsign = headsignVal,
                serviceId = serviceIdVal,
                routeId = routeIdVal,
                routeShortName = routeShortNameVal,
                routeLongName = routeLongNameVal,
                routeColor = routeColorVal,
                agencyName = agencyNameVal,
                days = MetrobusDays.fromJson(daysObj, daysArr)
            )
        }
    }
}

data class MetrobusStopDetail(
    val stopId: String,
    val stopName: String,
    val stopLat: Double,
    val stopLon: Double,
    val departures: List<MetrobusDeparture>
) {
    companion object {
        fun fromJsonString(jsonStr: String): MetrobusStopDetail {
            val trimmed = jsonStr.trim()
            val departuresList = mutableListOf<MetrobusDeparture>()
            var stopId = ""
            var stopName = ""
            var stopLat = 0.0
            var stopLon = 0.0

            if (trimmed.startsWith("[")) {
                val depsArray = JSONArray(trimmed)
                for (i in 0 until depsArray.length()) {
                    val depObj = depsArray.optJSONObject(i) ?: continue
                    departuresList.add(MetrobusDeparture.fromJson(depObj))
                }
            } else if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                stopId = json.optString("id", json.optString("stop_id", ""))
                stopName = json.optString("name", json.optString("stop_name", ""))
                stopLat = json.optDouble("lat", json.optDouble("stop_lat", 0.0))
                stopLon = json.optDouble("lon", json.optDouble("stop_lon", 0.0))

                val depsArray = json.optJSONArray("departures") ?: json.optJSONArray("salidas") ?: JSONArray()
                for (i in 0 until depsArray.length()) {
                    val depObj = depsArray.optJSONObject(i) ?: continue
                    departuresList.add(MetrobusDeparture.fromJson(depObj))
                }
            }

            return MetrobusStopDetail(
                stopId = stopId,
                stopName = stopName,
                stopLat = stopLat,
                stopLon = stopLon,
                departures = departuresList
            )
        }
    }
}

data class MetrobusDepartureUiModel(
    val lineCode: String,
    val destination: String,
    val departureTime: String,
    val minutesRemaining: Int,
    val timeLabel: String,
    val agencyName: String,
    val routeColor: String?,
    val lineName: String? = null
)

object MetrobusTimeCalculator {
    fun isDepartureActiveToday(departure: MetrobusDeparture, dayOfWeek: Int): Boolean {
        val d = departure.days
        return when (dayOfWeek) {
            Calendar.MONDAY -> d.monday
            Calendar.TUESDAY -> d.tuesday
            Calendar.WEDNESDAY -> d.wednesday
            Calendar.THURSDAY -> d.thursday
            Calendar.FRIDAY -> d.friday
            Calendar.SATURDAY -> d.saturday
            Calendar.SUNDAY -> d.sunday
            else -> false
        }
    }

    fun calculateMinutesRemaining(departureTimeStr: String, nowCalendar: Calendar): Int {
        val parts = departureTimeStr.split(":")
        if (parts.size < 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1

        val depCalendar = nowCalendar.clone() as Calendar
        depCalendar.set(Calendar.HOUR_OF_DAY, h % 24)
        depCalendar.set(Calendar.MINUTE, m)
        depCalendar.set(Calendar.SECOND, 0)
        depCalendar.set(Calendar.MILLISECOND, 0)

        if (h >= 24) {
            depCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val diffMs = depCalendar.timeInMillis - nowCalendar.timeInMillis
        var diffMinutes = (diffMs / (1000 * 60)).toInt()

        if (diffMinutes < -120 && h < 4) {
            depCalendar.add(Calendar.DAY_OF_YEAR, 1)
            val newDiffMs = depCalendar.timeInMillis - nowCalendar.timeInMillis
            diffMinutes = (newDiffMs / (1000 * 60)).toInt()
        }

        return diffMinutes
    }

    fun getActiveDeparturesForToday(
        detail: MetrobusStopDetail,
        linesMap: Map<String, String> = emptyMap()
    ): List<MetrobusDepartureUiModel> {
        val spainZone = TimeZone.getTimeZone("Europe/Madrid")
        val now = Calendar.getInstance(spainZone)
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        val departuresToday = detail.departures
            .filter { isDepartureActiveToday(it, dayOfWeek) }
            .map { dep ->
                val mins = calculateMinutesRemaining(dep.departureTime, now)
                val formattedTime = dep.departureTime.substringBeforeLast(":")
                val timeLabel = when {
                    mins < 0 -> formattedTime
                    mins == 0 -> "Ahora"
                    mins < 60 -> "$mins min"
                    else -> formattedTime
                }
                val lineCode = dep.routeShortName.ifBlank { "MB" }
                val lineFullName = linesMap[lineCode] ?: linesMap[dep.routeId]

                MetrobusDepartureUiModel(
                    lineCode = lineCode,
                    destination = dep.headsign.ifBlank { lineFullName ?: "Metrobús" },
                    departureTime = formattedTime,
                    minutesRemaining = mins,
                    timeLabel = timeLabel,
                    agencyName = dep.agencyName.ifBlank { "Metrobús" },
                    routeColor = dep.routeColor,
                    lineName = lineFullName
                )
            }

        val upcoming = departuresToday.filter { it.minutesRemaining >= -2 }.sortedBy { it.minutesRemaining }
        if (upcoming.isNotEmpty()) {
            return upcoming
        }

        // If no upcoming buses remain for today, show all scheduled buses for today sorted by departure time
        return departuresToday.sortedBy { it.departureTime }
    }
}

