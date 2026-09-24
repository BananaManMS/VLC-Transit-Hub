package com.example.data.repository.routing

import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.PlannedStop
import com.example.data.model.routing.TransitMode
import com.example.data.model.transitous.TransitousItineraryDto
import com.example.util.PolylineDecoder
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pure data mapper, time utilities, and heuristic scoring for transit routing.
 */
object RoutingDataMapper {

    const val WALKING_SPEED_METERS_PER_SEC = 1.15 // ~4.1 km/h

    private val genericNames = setOf(
        "END", "End", "end",
        "START", "Start", "start",
        "Destination", "DESTINATION", "destination",
        "Origin", "ORIGIN", "origen", "Origen",
        "destino", "Destino"
    )

    fun mapDtoToItinerary(
        dto: TransitousItineraryDto,
        index: Int,
        originName: String? = null,
        destinationName: String? = null
    ): PlannedItinerary {
        val legs = mutableListOf<PlannedLeg>()
        var totalWalkDistance = 0.0
        var totalWalkDuration = 0L

        dto.legs.forEachIndexed { legIndex, legDto ->
            val mode = TransitMode.fromString(legDto.mode)
            val durationSec = legDto.duration ?: 0L
            val distanceM = legDto.distance ?: if (mode == TransitMode.WALK) durationSec * WALKING_SPEED_METERS_PER_SEC else 0.0

            if (mode == TransitMode.WALK) {
                totalWalkDistance += distanceM
                totalWalkDuration += durationSec
            }

            val decodedPolyline = mutableListOf<GeoPoint>()
            if (!legDto.legGeometry?.points.isNullOrBlank()) {
                decodedPolyline.addAll(PolylineDecoder.decode(legDto.legGeometry!!.points, precision = legDto.legGeometry?.precision ?: 6))
            } else if (!legDto.steps.isNullOrEmpty()) {
                legDto.steps.forEach { step ->
                    step.polyline?.points?.let { pts ->
                        if (pts.isNotBlank()) {
                            decodedPolyline.addAll(PolylineDecoder.decode(pts, precision = step.polyline.precision ?: 6))
                        }
                    }
                }
            }

            val fromLat = legDto.from?.lat ?: 0.0
            val fromLon = legDto.from?.lon ?: 0.0
            val toLat = legDto.to?.lat ?: 0.0
            val toLon = legDto.to?.lon ?: 0.0

            val intermediateStops = legDto.intermediateStops?.map { stopDto ->
                val scheduledArrivalIso = stopDto.scheduledArrival ?: stopDto.arrival
                val arrivalIso = stopDto.arrival ?: stopDto.scheduledArrival
                val scheduledFormatted = formatIsoToTime(scheduledArrivalIso)
                val arrivalFormatted = formatIsoToTime(arrivalIso)
                PlannedStop(
                    name = stopDto.name ?: "Parada",
                    stopId = stopDto.stopId ?: stopDto.id ?: "",
                    lat = stopDto.lat ?: 0.0,
                    lon = stopDto.lon ?: 0.0,
                    scheduledTime = scheduledFormatted,
                    formattedTime = arrivalFormatted
                )
            } ?: emptyList()

            val sanitizedPolyline = if (mode == TransitMode.SUBWAY || mode == TransitMode.TRAM || mode == TransitMode.RAIL) {
                val waypoints = mutableListOf<GeoPoint>()
                if (PolylineDecoder.isValidValenciaCoordinate(fromLat, fromLon)) waypoints.add(GeoPoint(fromLat, fromLon))
                intermediateStops.forEach { s -> if (PolylineDecoder.isValidValenciaCoordinate(s.lat, s.lon)) waypoints.add(GeoPoint(s.lat, s.lon)) }
                if (PolylineDecoder.isValidValenciaCoordinate(toLat, toLon)) waypoints.add(GeoPoint(toLat, toLon))
                waypoints
            } else {
                if (decodedPolyline.isEmpty()) {
                    val waypoints = mutableListOf<GeoPoint>()
                    if (PolylineDecoder.isValidValenciaCoordinate(fromLat, fromLon)) waypoints.add(GeoPoint(fromLat, fromLon))
                    intermediateStops.forEach { s -> if (PolylineDecoder.isValidValenciaCoordinate(s.lat, s.lon)) waypoints.add(GeoPoint(s.lat, s.lon)) }
                    if (PolylineDecoder.isValidValenciaCoordinate(toLat, toLon)) waypoints.add(GeoPoint(toLat, toLon))
                    waypoints
                } else {
                    decodedPolyline
                }
            }

            val rawShortName = legDto.routeShortName ?: legDto.displayName
            val normalizedShortName = TransitIdMapper.normalizeRouteShortName(mode, rawShortName)
            val effectiveMode = TransitIdMapper.sanitizeSubwayOrTramMode(mode, normalizedShortName)
            val routeColorHex = resolveRouteColor(effectiveMode, legDto.routeColor, normalizedShortName, legDto.agencyName)

            val rawFromName = legDto.from?.name?.trim() ?: ""
            val rawToName = legDto.to?.name?.trim() ?: ""

            var cleanFromName = if (rawFromName in genericNames || rawFromName.isBlank() || rawFromName.matches(Regex("^[0-9.]+,[0-9.]+$"))) {
                if (effectiveMode == TransitMode.WALK && legIndex > 0) {
                    dto.legs.getOrNull(legIndex - 1)?.to?.name?.takeIf { it.isNotBlank() && it !in genericNames }
                        ?: dto.legs.getOrNull(legIndex - 1)?.from?.name?.takeIf { it.isNotBlank() && it !in genericNames }
                        ?: if (!originName.isNullOrBlank()) originName else "Origen"
                } else if (!originName.isNullOrBlank()) {
                    originName
                } else {
                    "Origen"
                }
            } else rawFromName

            var cleanToName = if (rawToName in genericNames || rawToName.isBlank() || rawToName.matches(Regex("^[0-9.]+,[0-9.]+$"))) {
                if (effectiveMode == TransitMode.WALK && legIndex < dto.legs.size - 1) {
                    dto.legs.getOrNull(legIndex + 1)?.from?.name?.takeIf { it.isNotBlank() && it !in genericNames }
                        ?: if (!destinationName.isNullOrBlank()) destinationName else "Destino"
                } else if (!destinationName.isNullOrBlank()) {
                    destinationName
                } else {
                    "Destino"
                }
            } else rawToName

            // If this is a walk leg leading to a transit leg, use the transit boarding station name
            if (effectiveMode == TransitMode.WALK && legIndex < dto.legs.size - 1) {
                val nextTransitOrigin = dto.legs.getOrNull(legIndex + 1)?.from?.name?.takeIf { it.isNotBlank() && it !in genericNames }
                if (nextTransitOrigin != null) {
                    cleanToName = nextTransitOrigin
                }
            }

            if (legIndex == dto.legs.size - 1 && !destinationName.isNullOrBlank()) {
                if (cleanToName in genericNames || cleanToName == "Destino" || cleanToName == "Origen") {
                    cleanToName = destinationName
                }
            }

            if (legIndex == 0 && !originName.isNullOrBlank()) {
                if (cleanFromName in genericNames || cleanFromName == "Origen" || cleanFromName == "Destino") {
                    cleanFromName = originName
                }
            }

            legs.add(
                PlannedLeg(
                    mode = effectiveMode,
                    durationSeconds = durationSec,
                    distanceMeters = distanceM,
                    formattedDuration = formatSecondsToDuration(durationSec),
                    startTime = legDto.startTime ?: "",
                    endTime = legDto.endTime ?: "",
                    formattedStartTime = formatIsoToTime(legDto.startTime),
                    formattedEndTime = formatIsoToTime(legDto.endTime),
                    agencyName = legDto.agencyName ?: "",
                    routeShortName = normalizedShortName.ifBlank { rawShortName },
                    routeLongName = legDto.routeLongName,
                    headsign = legDto.headsign,
                    routeColorHex = routeColorHex,
                    fromName = cleanFromName,
                    toName = cleanToName,
                    fromStopId = legDto.from?.stopId,
                    toStopId = legDto.to?.stopId,
                    fromLat = fromLat,
                    fromLon = fromLon,
                    toLat = toLat,
                    toLon = toLon,
                    intermediateStops = intermediateStops,
                    geometry = sanitizedPolyline
                )
            )
        }

        if (legs.size > 1) {
            legs.removeAll { leg ->
                leg.mode == TransitMode.WALK && (leg.distanceMeters < 10.0 || leg.durationSeconds <= 0)
            }
        }

        val walkLegs = legs.filter { it.mode == TransitMode.WALK }
        val finalWalkDistance = walkLegs.sumOf { it.distanceMeters }
        val finalWalkDuration = walkLegs.sumOf { it.durationSeconds }
        val allPoints = legs.flatMap { it.geometry }

        val formattedDeparture = legs.firstOrNull()?.formattedStartTime ?: formatIsoToTime(dto.startTime)
        val formattedArrival = legs.lastOrNull()?.formattedEndTime ?: formatIsoToTime(dto.endTime)
        val totalDurationSec = calculateTotalDurationSec(legs, if ((dto.duration ?: 0L) > 0) (dto.duration ?: 0L) else legs.sumOf { it.durationSeconds })

        return PlannedItinerary(
            id = dto.id ?: "itin_$index",
            totalDurationSeconds = totalDurationSec,
            startTime = dto.startTime ?: "",
            endTime = dto.endTime ?: "",
            recommendedStartTime = formattedDeparture,
            formattedDuration = formatSecondsToDuration(totalDurationSec),
            formattedDepartureTime = formattedDeparture,
            formattedArrivalTime = formattedArrival,
            transfersCount = dto.transfers,
            legs = legs,
            viability = ItineraryViability.THEORETICAL_SCHEDULE,
            viabilityNotice = null,
            activeAlerts = emptyList(),
            totalWalkDistanceMeters = finalWalkDistance,
            totalWalkDurationSeconds = finalWalkDuration,
            allRoutePolyline = allPoints
        )
    }

    fun calculateTotalDurationSec(legs: List<PlannedLeg>, fallbackDurationSec: Long): Long {
        val startStr = legs.firstOrNull()?.formattedStartTime
        val endStr = legs.lastOrNull()?.formattedEndTime
        if (startStr.isNullOrBlank() || endStr.isNullOrBlank() || !startStr.contains(":") || !endStr.contains(":")) {
            return fallbackDurationSec
        }
        return try {
            val sParts = startStr.split(":")
            val eParts = endStr.split(":")
            val sMins = sParts[0].toInt() * 60 + sParts[1].toInt()
            val eMins = eParts[0].toInt() * 60 + eParts[1].toInt()
            var diff = eMins - sMins
            if (diff < 0) diff += 1440 // Midnight rollover
            (diff * 60).toLong()
        } catch (e: Exception) {
            fallbackDurationSec
        }
    }

    fun timeToMinutes(timeStr: String): Int {
        if (timeStr.isBlank() || !timeStr.contains(":")) return 0
        return try {
            val parts = timeStr.trim().split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }

    fun shiftFormattedTime(timeStr: String, minutesToAdd: Int): String {
        if (timeStr.isBlank() || !timeStr.contains(":")) return timeStr
        return try {
            val parts = timeStr.trim().split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val totalMinutes = (h * 60 + m + minutesToAdd + 1440) % 1440
            String.format(Locale.US, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
        } catch (e: Exception) {
            timeStr
        }
    }

    fun addMinutesToCurrentTime(minutesToAdd: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        cal.add(Calendar.MINUTE, minutesToAdd)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(cal.time)
    }

    fun formatSecondsToDuration(seconds: Long): String {
        val minutes = (seconds / 60).coerceAtLeast(1)
        return if (minutes < 60) {
            "$minutes min"
        } else {
            val hours = minutes / 60
            val remainingMins = minutes % 60
            if (remainingMins == 0L) "$hours h" else "${hours}h ${remainingMins}m"
        }
    }

    fun formatIsoToTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "--:--"
        val trimmed = isoString.trim()
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val madridZone = java.time.ZoneId.of("Europe/Madrid")
                try {
                    val odt = java.time.OffsetDateTime.parse(trimmed)
                    val zdt = odt.atZoneSameInstant(madridZone)
                    return String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
                } catch (_: Exception) {
                    try {
                        val instant = java.time.Instant.parse(trimmed)
                        val zdt = instant.atZone(madridZone)
                        return String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
                    } catch (_: Exception) {
                        try {
                            val ldt = java.time.LocalDateTime.parse(trimmed)
                            return String.format(Locale.US, "%02d:%02d", ldt.hour, ldt.minute)
                        } catch (_: Exception) {}
                    }
                }
            }

            if (trimmed.length >= 16 && trimmed[10] == 'T') {
                val timePart = trimmed.substring(11, 16)
                if (timePart.contains(":")) return timePart
            }
            trimmed.takeLast(5)
        } catch (e: Exception) {
            trimmed.takeLast(5)
        }
    }

    fun parseIsoToEpochMs(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        val trimmed = isoString.trim()
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val odt = java.time.OffsetDateTime.parse(trimmed)
                    odt.toInstant().toEpochMilli()
                } catch (_: Exception) {
                    try {
                        val instant = java.time.Instant.parse(trimmed)
                        instant.toEpochMilli()
                    } catch (_: Exception) {
                        val ldt = java.time.LocalDateTime.parse(trimmed)
                        val madridZone = java.time.ZoneId.of("Europe/Madrid")
                        ldt.atZone(madridZone).toInstant().toEpochMilli()
                    }
                }
            } else {
                val cleanIso = trimmed.substringBefore("Z").substringBefore("+")
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Europe/Madrid")
                }
                inputFormat.parse(cleanIso)?.time ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun getEffectiveArrivalEpochMs(itinerary: PlannedItinerary): Long {
        val lastLeg = itinerary.legs.lastOrNull()
        val rawIso = lastLeg?.endTime?.takeIf { it.isNotBlank() } ?: itinerary.endTime
        val isoMs = parseIsoToEpochMs(rawIso)
        if (isoMs > 0) return isoMs
        val arrivalMins = timeToMinutes(itinerary.formattedArrivalTime)
        return arrivalMins * 60 * 1000L
    }

    fun getEffectiveDepartureEpochMs(itinerary: PlannedItinerary): Long {
        val firstLeg = itinerary.legs.firstOrNull()
        val rawIso = firstLeg?.startTime?.takeIf { it.isNotBlank() } ?: itinerary.startTime
        val isoMs = parseIsoToEpochMs(rawIso)
        if (isoMs > 0) return isoMs
        val depMins = timeToMinutes(itinerary.formattedDepartureTime)
        return depMins * 60 * 1000L
    }

    fun calculateItineraryScore(itin: PlannedItinerary, requestTimeMs: Long): Double {
        val firstTransitLeg = itin.legs.firstOrNull { it.mode != TransitMode.WALK }
        val initialWaitSeconds = if (firstTransitLeg != null) {
            val depTimeMs = parseIsoToEpochMs(firstTransitLeg.startTime)
            if (depTimeMs > requestTimeMs) (depTimeMs - requestTimeMs) / 1000.0 else 0.0
        } else {
            0.0
        }

        val totalTimeFromNowSeconds = initialWaitSeconds + itin.totalDurationSeconds
        val transferPenalty = itin.transfersCount * 180.0
        val viabilityPenalty = when (itin.viability) {
            ItineraryViability.VIABLE_ON_TIME -> 0.0
            ItineraryViability.ADJUSTED_NEXT_DEPARTURE -> 120.0
            ItineraryViability.CHECKING_REAL_TIME -> 150.0
            ItineraryViability.THEORETICAL_SCHEDULE -> 300.0
            ItineraryViability.SERVICE_ALERT -> 900.0
        }

        return totalTimeFromNowSeconds + transferPenalty + viabilityPenalty
    }

    fun resolveRouteColor(mode: TransitMode, routeColorHex: String?, routeShortName: String?, agencyName: String?): String {
        return com.example.util.LineColorResolver.resolveRouteColorHex(mode, routeShortName, routeColorHex, agencyName).removePrefix("#")
    }
}
