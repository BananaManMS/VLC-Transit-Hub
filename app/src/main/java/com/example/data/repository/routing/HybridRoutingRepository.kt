package com.example.data.repository.routing

import android.content.Context
import android.util.Log
import com.example.data.model.routing.AlternativeConnectingOption
import com.example.data.model.routing.ItineraryViability
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.PlannedStop
import com.example.data.model.routing.TransitMode
import com.example.data.model.transitous.TransitousItineraryDto
import com.example.data.model.transitous.TransitousLegDto
import com.example.data.model.transitous.TransitousPlanResponse
import com.example.data.network.NetworkModule
import com.example.data.network.TransitousApiService
import com.example.data.repository.MetroAlertsRepository
import com.example.data.repository.RealTimeTransitRepository
import com.example.data.repository.renfe.GtfsRtTripUpdate
import com.example.ui.bus.BusMapper
import com.example.ui.bus.EmtBusTime
import com.example.util.PolylineDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class HybridRoutingRepository(
    private val transitousApiService: TransitousApiService = NetworkModule.transitousApiService,
    private val okHttpClient: OkHttpClient = NetworkModule.okHttpClient,
    private val metroAlertsRepository: MetroAlertsRepository? = null,
    private val context: Context? = null
) {
    private val emtArrivalsCache = ConcurrentHashMap<String, Pair<Long, List<EmtBusTime>>>()
    private val metroDeparturesCache = ConcurrentHashMap<String, Pair<Long, List<MetroArrival>>>()
    private val nearbyStopCache = ConcurrentHashMap<String, String>()
    @Volatile
    private var cachedActiveStops: List<com.example.data.database.GeoportalStopEntity>? = null

    companion object {
        private const val TAG = "HybridRoutingRepo"
        const val ROUTE_TOLERANCE_MINUTES = 5
        private const val WALKING_SPEED_METERS_PER_SEC = 1.15 // ~4.1 km/h
        private const val MIN_BOARDING_SLACK_SECONDS = 90L // 1.5 minutes minimum margin
        private const val RECOMMENDED_BUFFER_BEFORE_BOARDING_SECONDS = 120L // 2 minutes calm buffer
        private const val CACHE_TTL_MS = 30_000L // 30s cache TTL
        private const val HTTP_TIMEOUT_MS = 4000L // 4s max HTTP call duration
        private const val REAL_TIME_ITINERARY_TIMEOUT_MS = 2500L // 2.5s per itinerary check in synchronous pass
    }

    private val realTimeHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .callTimeout(4000, TimeUnit.MILLISECONDS)
            .build()
    }

    private suspend fun executeGetRequest(url: String, headers: Map<String, String>): String? = suspendCancellableCoroutine { continuation ->
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val call = realTimeHttpClient.newCall(requestBuilder.build())

        continuation.invokeOnCancellation {
            try {
                call.cancel()
            } catch (_: Throwable) {}
        }

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    try {
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            continuation.resume(body)
                        } else {
                            continuation.resume(null)
                        }
                    } catch (e: Exception) {
                        continuation.resume(null)
                    } finally {
                        response.close()
                    }
                } else {
                    response.close()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        })
    }

    /**
     * Queries Transitous MOTIS 2 multimodal engine and enriches itineraries with local live real-time data.
     */
    suspend fun planRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        time: String? = null,
        date: String? = null,
        arriveBy: Boolean = false,
        maxTransfers: Int? = 3,
        modes: String = "WALK,SUBWAY,TRAM,BUS,COACH,REGIONAL_RAIL",
        originName: String? = null,
        destinationName: String? = null
    ): Result<List<PlannedItinerary>> = withContext(Dispatchers.IO) {
        try {
            val fromPlace = String.format(Locale.US, "%.5f,%.5f", fromLat, fromLon)
            val toPlace = String.format(Locale.US, "%.5f,%.5f", toLat, toLon)

            // Format time as ISO-8601 with local timezone offset (e.g. Europe/Madrid +02:00) required by Transitous / MOTIS 2 API
            val isoFormattedTime = if (!time.isNullOrBlank() || arriveBy) {
                val datePart = if (!date.isNullOrBlank()) {
                    date.trim()
                } else {
                    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
                }

                val timePart = if (!time.isNullOrBlank()) {
                    val t = time.trim()
                    if (t.contains(":") && t.length == 5) "$t:00" else if (t.length == 8) t else "$t:00"
                } else {
                    java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
                }

                try {
                    val localDateTimeStr = "${datePart}T${timePart}"
                    val localDateTime = java.time.LocalDateTime.parse(localDateTimeStr)
                    val madridZone = java.time.ZoneId.of("Europe/Madrid")
                    val zonedDateTime = localDateTime.atZone(madridZone)
                    zonedDateTime.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                } catch (e: Exception) {
                    "${datePart}T${timePart}+02:00"
                }
            } else null

            Log.d(TAG, "Requesting Transitous plan: from $fromPlace to $toPlace, isoTime=$isoFormattedTime, arriveBy=$arriveBy")

            val response = transitousApiService.plan(
                fromPlace = fromPlace,
                toPlace = toPlace,
                time = isoFormattedTime,
                date = null, // MOTIS 2 receives full ISO-8601 timestamp in 'time' parameter
                arriveBy = if (arriveBy) true else null,
                maxTransfers = maxTransfers,
                modes = modes,
                numItineraries = 15,
                maxWalkDuration = 45, // 45 minutes max walk duration limit (egress max)
                maxWalkDist = 3500   // 3.5 km max walk distance limit
            )

            val rawItineraries = response.itineraries
            if (rawItineraries.isNullOrEmpty()) {
                Log.w(TAG, "Transitous returned no itineraries: ${response.message ?: response.error}")
                return@withContext Result.success(emptyList())
            }

            // Filter raw itineraries strictly according to allowed transport modes
            val allowedMotisModes = modes.split(",").map { it.trim().uppercase() }.toSet()
            val filteredRawItineraries = rawItineraries.filter { itinDto ->
                val hasDisallowedLeg = itinDto.legs.any { legDto ->
                    val rawMode = (legDto.mode ?: "").uppercase().trim()
                    if (rawMode.isEmpty() || rawMode == "WALK" || rawMode == "FOOT") {
                        false
                    } else {
                        val isAllowed = when (rawMode) {
                            "SUBWAY", "METRO" -> "SUBWAY" in allowedMotisModes || "METRO" in allowedMotisModes
                            "TRAM" -> "TRAM" in allowedMotisModes
                            "BUS" -> "BUS" in allowedMotisModes
                            "COACH" -> "COACH" in allowedMotisModes || "BUS" in allowedMotisModes
                            "REGIONAL_RAIL", "SUBURBAN", "SUBURBAN_RAIL" -> "REGIONAL_RAIL" in allowedMotisModes || "SUBURBAN" in allowedMotisModes || "RAIL" in allowedMotisModes
                            "LONG_DISTANCE" -> "LONG_DISTANCE" in allowedMotisModes
                            "HIGHSPEED_RAIL" -> "HIGHSPEED_RAIL" in allowedMotisModes
                            "RAIL" -> "RAIL" in allowedMotisModes || "REGIONAL_RAIL" in allowedMotisModes
                            "BICYCLE", "BIKE" -> "BICYCLE" in allowedMotisModes || "BIKE" in allowedMotisModes
                            else -> rawMode in allowedMotisModes
                        }
                        !isAllowed
                    }
                }
                !hasDisallowedLeg
            }

            val basePlannedItineraries = filteredRawItineraries.mapIndexed { index, itinDto ->
                mapDtoToItinerary(itinDto, index, originName, destinationName)
            }.filter { itin ->
                val firstTransitIndex = itin.legs.indexOfFirst { it.mode != TransitMode.WALK }
                if (firstTransitIndex != -1) {
                    val accessWalkDuration = itin.legs.take(firstTransitIndex).sumOf { it.durationSeconds }
                    val lastTransitIndex = itin.legs.indexOfLast { it.mode != TransitMode.WALK }
                    val egressWalkDuration = itin.legs.drop(lastTransitIndex + 1).sumOf { it.durationSeconds }

                    val maxAccessSeconds = 30 * 60L // 30 minutes for access (start)
                    val maxEgressSeconds = 45 * 60L // 45 minutes for egress (end)

                    accessWalkDuration <= maxAccessSeconds && egressWalkDuration <= maxEgressSeconds
                } else {
                    // Walk-only itinerary: limit total duration to 45 minutes
                    itin.totalDurationSeconds <= 45 * 60L
                }
            }

            val toleranceEnrichedItineraries = try {
                enrichWithAlternativeConnectingBuses(
                    basePlannedItineraries,
                    toLat = toLat,
                    toLon = toLon,
                    allowedModes = modes,
                    destinationName = destinationName
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error enriching alternative connecting buses", e)
                basePlannedItineraries
            }

            val isDepartNowQuery = time.isNullOrBlank() && date.isNullOrBlank() && !arriveBy
            val enrichedItineraries = if (isDepartNowQuery) {
                try {
                    withTimeoutOrNull(REAL_TIME_ITINERARY_TIMEOUT_MS) {
                        reconcileItineraries(toleranceEnrichedItineraries, isDepartNow = true)
                    } ?: toleranceEnrichedItineraries
                } catch (e: Exception) {
                    toleranceEnrichedItineraries
                }
            } else {
                toleranceEnrichedItineraries
            }

            // Sort primarily by departure time (earliest departure first), then duration as tiebreaker
            val sortedItineraries = if (arriveBy) {
                enrichedItineraries.sortedWith(
                    compareByDescending<PlannedItinerary> { parseIsoToEpochMs(it.endTime) }
                        .thenBy { it.totalDurationSeconds }
                )
            } else {
                enrichedItineraries.sortedWith(
                    compareBy<PlannedItinerary> { parseIsoToEpochMs(it.startTime) }
                        .thenBy { it.totalDurationSeconds }
                )
            }

            Result.success(sortedItineraries)
        } catch (e: Exception) {
            Log.e(TAG, "Error planning route with Transitous", e)
            Result.failure(e)
        }
    }

    private fun mapDtoToItinerary(
        dto: TransitousItineraryDto,
        index: Int,
        originName: String? = null,
        destinationName: String? = null
    ): PlannedItinerary {
        val legs = mutableListOf<PlannedLeg>()
        var totalWalkDistance = 0.0
        var totalWalkDuration = 0L

        val genericNames = setOf("END", "End", "end", "START", "Start", "start", "Destination", "DESTINATION", "destination", "Origin", "ORIGIN", "origen", "Origen", "destino", "Destino")

        dto.legs.forEachIndexed { legIndex, legDto ->
            val mode = TransitMode.fromString(legDto.mode)
            val durationSec = legDto.duration
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
                    stopId = stopDto.stopId,
                    lat = stopDto.lat,
                    lon = stopDto.lon,
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
            val routeColorHex = resolveRouteColor(mode, legDto.routeColor, normalizedShortName, legDto.agencyName)

            val rawFromName = legDto.from?.name?.trim() ?: ""
            val rawToName = legDto.to?.name?.trim() ?: ""

            var cleanFromName = if (rawFromName in genericNames || rawFromName.isBlank() || rawFromName.matches(Regex("^[0-9.]+,[0-9.]+$"))) {
                if (!originName.isNullOrBlank()) originName else "Origen"
            } else rawFromName

            var cleanToName = if (rawToName in genericNames || rawToName.isBlank() || rawToName.matches(Regex("^[0-9.]+,[0-9.]+$"))) {
                if (!destinationName.isNullOrBlank()) destinationName else "Destino"
            } else rawToName

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
                    mode = mode,
                    durationSeconds = durationSec,
                    distanceMeters = distanceM,
                    formattedDuration = formatSecondsToDuration(durationSec),
                    startTime = legDto.startTime ?: "",
                    endTime = legDto.endTime ?: "",
                    formattedStartTime = formatIsoToTime(legDto.startTime),
                    formattedEndTime = formatIsoToTime(legDto.endTime),
                    agencyName = legDto.agencyName,
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
        val totalDurationSec = calculateTotalDurationSec(legs, if (dto.duration > 0) dto.duration else legs.sumOf { it.durationSeconds })

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

    /**
     * Checks for connecting bus alternatives within ROUTE_TOLERANCE_MINUTES margin (+/- 5 min)
     * at transfer hubs and attaches them as alternative connections or multimodal alternatives.
     */
    private suspend fun enrichWithAlternativeConnectingBuses(
        itineraries: List<PlannedItinerary>,
        toLat: Double,
        toLon: Double,
        allowedModes: String,
        destinationName: String?
    ): List<PlannedItinerary> {
        if (itineraries.isEmpty()) return itineraries
        val allowedUpper = allowedModes.uppercase()
        val allowsBusOrTram = "BUS" in allowedUpper || "TRAM" in allowedUpper
        if (!allowsBusOrTram) return itineraries

        return coroutineScope {
            val enrichedList = mutableListOf<PlannedItinerary>()
            val addedMultimodalRoutes = mutableListOf<PlannedItinerary>()

            itineraries.forEach { itin ->
                // Case A: Itinerary already transfers to a BUS or TRAM leg
                val busLegIndex = itin.legs.indexOfFirst { it.mode == TransitMode.BUS || it.mode == TransitMode.TRAM }
                if (busLegIndex > 0) {
                    val prevLeg = itin.legs[busLegIndex - 1]
                    val primaryBusLeg = itin.legs[busLegIndex]
                    val primaryBusLine = primaryBusLeg.routeShortName ?: ""

                    val transferLat = if (primaryBusLeg.fromLat != 0.0) primaryBusLeg.fromLat else prevLeg.toLat
                    val transferLon = if (primaryBusLeg.fromLon != 0.0) primaryBusLeg.fromLon else prevLeg.toLon
                    val transferTimeIso = if (prevLeg.endTime.isNotBlank()) prevLeg.endTime else primaryBusLeg.startTime

                    if (transferLat != 0.0 && transferLon != 0.0 && transferTimeIso.isNotBlank()) {
                        try {
                            val connResponse = withTimeoutOrNull(2000L) {
                                transitousApiService.plan(
                                    fromPlace = "$transferLat,$transferLon",
                                    toPlace = "$toLat,$toLon",
                                    time = transferTimeIso,
                                    date = null,
                                    arriveBy = null,
                                    maxTransfers = 1,
                                    modes = "WALK,BUS,TRAM",
                                    numItineraries = 8,
                                    maxWalkDuration = 20,
                                    maxWalkDist = 1500
                                )
                            }

                            val connItineraries = connResponse?.itineraries ?: emptyList()
                            val primaryDurationSeconds = primaryBusLeg.durationSeconds

                            val alternativeOptions = mutableListOf<AlternativeConnectingOption>()
                            val seenLines = mutableSetOf(primaryBusLine)

                            for (connItin in connItineraries) {
                                val altLeg = connItin.legs.firstOrNull { it.mode.uppercase() == "BUS" || it.mode.uppercase() == "TRAM" } ?: continue
                                val lineName = altLeg.routeShortName ?: continue
                                if (lineName in seenLines) continue

                                val deltaMinutes = ((connItin.duration - primaryDurationSeconds) / 60).toInt()
                                if (Math.abs(deltaMinutes) <= ROUTE_TOLERANCE_MINUTES) {
                                    seenLines.add(lineName)
                                    val altColor = if (!altLeg.routeColor.isNullOrBlank()) "#${altLeg.routeColor}" else if (altLeg.mode.uppercase() == "TRAM") "#9B51E0" else "#DA291C"
                                    alternativeOptions.add(
                                        AlternativeConnectingOption(
                                            lineName = lineName,
                                            mode = when (altLeg.mode.uppercase()) {
                                                "TRAM" -> TransitMode.TRAM
                                                else -> TransitMode.BUS
                                            },
                                            routeColorHex = altColor,
                                            departureTime = formatIsoToTime(altLeg.startTime),
                                            arrivalTime = formatIsoToTime(altLeg.endTime),
                                            durationMinutes = connItin.duration / 60,
                                            deltaDurationMinutes = deltaMinutes,
                                            fromStopName = altLeg.from?.name ?: "",
                                            toStopName = altLeg.to?.name ?: "",
                                            headsign = altLeg.headsign
                                        )
                                    )
                                }
                            }

                            if (alternativeOptions.isNotEmpty()) {
                                enrichedList.add(itin.copy(alternativeConnections = alternativeOptions))
                                return@forEach
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed connecting bus query", e)
                        }
                    }
                    enrichedList.add(itin)
                } else if (itin.legs.any { it.mode == TransitMode.SUBWAY } && addedMultimodalRoutes.size < 3) {
                    // Case B: Metro itinerary - check if transferring to a bus at an interchange station provides a viable alternative
                    val subwayLeg = itin.legs.first { it.mode == TransitMode.SUBWAY }
                    val interchangeStations = subwayLeg.intermediateStops.filter { stop ->
                        stop.name.contains("Colón", ignoreCase = true) ||
                        stop.name.contains("Xàtiva", ignoreCase = true) ||
                        stop.name.contains("Alameda", ignoreCase = true) ||
                        stop.name.contains("Espanya", ignoreCase = true) ||
                        stop.name.contains("Guimerà", ignoreCase = true)
                    }

                    val hubStop = interchangeStations.lastOrNull()
                    if (hubStop != null && hubStop.scheduledTime != null) {
                        try {
                            val hubTimeIso = subwayLeg.startTime.replace(Regex("T.*"), "T${hubStop.scheduledTime}:00+02:00")
                            val connResponse = withTimeoutOrNull(2000L) {
                                transitousApiService.plan(
                                    fromPlace = "${hubStop.lat},${hubStop.lon}",
                                    toPlace = "$toLat,$toLon",
                                    time = hubTimeIso,
                                    date = null,
                                    arriveBy = null,
                                    maxTransfers = 1,
                                    modes = "WALK,BUS,TRAM",
                                    numItineraries = 6,
                                    maxWalkDuration = 15,
                                    maxWalkDist = 1200
                                )
                            }

                            val connItineraries = connResponse?.itineraries ?: emptyList()
                            if (connItineraries.isNotEmpty()) {
                                val primaryConn = connItineraries.first()
                                val primaryBusLeg = primaryConn.legs.firstOrNull { it.mode.uppercase() == "BUS" || it.mode.uppercase() == "TRAM" }
                                if (primaryBusLeg != null) {
                                    val primaryLine = primaryBusLeg.routeShortName ?: ""
                                    val alternativeOptions = mutableListOf<AlternativeConnectingOption>()
                                    val seenLines = mutableSetOf(primaryLine)

                                    for (otherConn in connItineraries.drop(1)) {
                                        val otherLeg = otherConn.legs.firstOrNull { it.mode.uppercase() == "BUS" || it.mode.uppercase() == "TRAM" } ?: continue
                                        val otherLine = otherLeg.routeShortName ?: continue
                                        if (otherLine in seenLines) continue

                                        val deltaMin = ((otherConn.duration - primaryConn.duration) / 60).toInt()
                                        if (Math.abs(deltaMin) <= ROUTE_TOLERANCE_MINUTES) {
                                            seenLines.add(otherLine)
                                            val otherColor = if (!otherLeg.routeColor.isNullOrBlank()) "#${otherLeg.routeColor}" else if (otherLeg.mode.uppercase() == "TRAM") "#9B51E0" else "#DA291C"
                                            alternativeOptions.add(
                                                AlternativeConnectingOption(
                                                    lineName = otherLine,
                                                    mode = when (otherLeg.mode.uppercase()) {
                                                        "TRAM" -> TransitMode.TRAM
                                                        else -> TransitMode.BUS
                                                    },
                                                    routeColorHex = otherColor,
                                                    departureTime = formatIsoToTime(otherLeg.startTime),
                                                    arrivalTime = formatIsoToTime(otherLeg.endTime),
                                                    durationMinutes = otherConn.duration / 60,
                                                    deltaDurationMinutes = deltaMin,
                                                    fromStopName = otherLeg.from?.name ?: "",
                                                    toStopName = otherLeg.to?.name ?: "",
                                                    headsign = otherLeg.headsign
                                                )
                                            )
                                        }
                                    }

                                    // Build composed multimodal PlannedItinerary
                                    val composedLegs = mutableListOf<PlannedLeg>()
                                    
                                    // 1. Initial walk to subway
                                    itin.legs.firstOrNull { it.mode == TransitMode.WALK }?.let { composedLegs.add(it) }

                                    // 2. Subway leg up to hub stop
                                    composedLegs.add(
                                        subwayLeg.copy(
                                            toName = hubStop.name,
                                            toStopId = hubStop.stopId,
                                            toLat = hubStop.lat,
                                            toLon = hubStop.lon,
                                            endTime = hubTimeIso,
                                            formattedEndTime = hubStop.scheduledTime ?: subwayLeg.formattedEndTime
                                        )
                                    )

                                    // 3. Connecting legs mapped from primaryConn
                                    val mappedConnLegs = primaryConn.legs.map { legDto ->
                                        val mode = when (legDto.mode.uppercase()) {
                                            "BUS" -> TransitMode.BUS
                                            "TRAM" -> TransitMode.TRAM
                                            else -> TransitMode.WALK
                                        }
                                        val legColor = if (!legDto.routeColor.isNullOrBlank()) "#${legDto.routeColor}" else if (mode == TransitMode.BUS) "#DA291C" else "#9B51E0"
                                        PlannedLeg(
                                            mode = mode,
                                            durationSeconds = legDto.duration,
                                            distanceMeters = legDto.distance ?: 0.0,
                                            formattedDuration = "${legDto.duration / 60} min",
                                            startTime = legDto.startTime ?: "",
                                            endTime = legDto.endTime ?: "",
                                            formattedStartTime = formatIsoToTime(legDto.startTime),
                                            formattedEndTime = formatIsoToTime(legDto.endTime),
                                            agencyName = legDto.agencyName ?: if (mode == TransitMode.BUS) "EMT Valencia" else null,
                                            routeShortName = legDto.routeShortName,
                                            routeLongName = legDto.routeLongName,
                                            headsign = legDto.headsign,
                                            routeColorHex = legColor,
                                            fromName = legDto.from?.name ?: hubStop.name,
                                            toName = legDto.to?.name ?: (destinationName ?: "Destino"),
                                            fromStopId = legDto.from?.stopId,
                                            toStopId = legDto.to?.stopId,
                                            fromLat = legDto.from?.lat ?: 0.0,
                                            fromLon = legDto.from?.lon ?: 0.0,
                                            toLat = legDto.to?.lat ?: 0.0,
                                            toLon = legDto.to?.lon ?: 0.0
                                        )
                                    }
                                    composedLegs.addAll(mappedConnLegs)

                                    val finalDeparture = composedLegs.first().formattedStartTime
                                    val finalArrival = composedLegs.last().formattedEndTime
                                    val totalDurationSec = calculateTotalDurationSec(composedLegs, composedLegs.sumOf { it.durationSeconds })

                                    val multimodalItin = PlannedItinerary(
                                        id = "multimodal_hub_${itin.id}_${primaryLine}",
                                        totalDurationSeconds = totalDurationSec,
                                        startTime = itin.startTime,
                                        endTime = composedLegs.last().endTime,
                                        recommendedStartTime = finalDeparture,
                                        formattedDuration = "${totalDurationSec / 60} min",
                                        formattedDepartureTime = finalDeparture,
                                        formattedArrivalTime = finalArrival,
                                        transfersCount = 1,
                                        legs = composedLegs,
                                        viability = ItineraryViability.THEORETICAL_SCHEDULE,
                                        alternativeConnections = alternativeOptions
                                    )
                                    addedMultimodalRoutes.add(multimodalItin)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed multimodal hub generation", e)
                        }
                    }
                    enrichedList.add(itin)
                } else {
                    enrichedList.add(itin)
                }
            }

            if (addedMultimodalRoutes.isNotEmpty()) {
                enrichedList.addAll(addedMultimodalRoutes)
            }

            enrichedList
        }
    }

    /**
     * Public reconciliation method allowing RoutePlannerViewModel to enrich itineraries asynchronously in background
     * without blocking UI response. Supports sequential allocation to prevent vehicle duplication across multiple departures.
     */
    suspend fun reconcileItineraries(
        baseItineraries: List<PlannedItinerary>,
        isDepartNow: Boolean = true
    ): List<PlannedItinerary> = coroutineScope {
        if (!isDepartNow || baseItineraries.isEmpty()) {
            return@coroutineScope baseItineraries
        }
        val claimedVehicleKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        baseItineraries.map { itinerary ->
            async {
                try {
                    reconcileItineraryWithLiveData(itinerary, isDepartNow, claimedVehicleKeys)
                } catch (e: Exception) {
                    itinerary
                }
            }
        }.awaitAll().sortedWith(
            compareBy<PlannedItinerary> { parseIsoToEpochMs(it.startTime) }
                .thenBy { it.totalDurationSeconds }
        )
    }

    suspend fun reconcileItinerary(
        baseItinerary: PlannedItinerary,
        isDepartNow: Boolean = true
    ): PlannedItinerary {
        val claimedVehicleKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        return reconcileItineraryWithLiveData(baseItinerary, isDepartNow, claimedVehicleKeys)
    }

    private suspend fun reconcileItineraryWithLiveData(
        baseItinerary: PlannedItinerary,
        isDepartNow: Boolean,
        claimedVehicleKeys: MutableSet<String>
    ): PlannedItinerary {
        val legs = baseItinerary.legs.map { it.copy() }.toMutableList()
        val (viability, viabilityNotice, recommendedLeaveTime, activeAlerts) = evaluateLiveReconciliation(
            legs = legs,
            theoreticalStartTimeIso = baseItinerary.startTime,
            isDepartNow = isDepartNow,
            claimedVehicleKeys = claimedVehicleKeys
        )

        val finalFormattedDeparture = legs.firstOrNull()?.formattedStartTime ?: baseItinerary.formattedDepartureTime
        val finalFormattedArrival = legs.lastOrNull()?.formattedEndTime ?: baseItinerary.formattedArrivalTime
        val totalDurationSec = calculateTotalDurationSec(legs, baseItinerary.totalDurationSeconds)
        val walkLegs = legs.filter { it.mode == TransitMode.WALK }
        val finalWalkDistance = walkLegs.sumOf { it.distanceMeters }
        val finalWalkDuration = walkLegs.sumOf { it.durationSeconds }
        val allPoints = legs.flatMap { it.geometry }
        val finalTransfersCount = legs.count { it.mode != TransitMode.WALK }.minus(1).coerceAtLeast(0)

        return baseItinerary.copy(
            legs = legs,
            viability = viability,
            viabilityNotice = viabilityNotice,
            recommendedStartTime = recommendedLeaveTime ?: finalFormattedDeparture,
            formattedDuration = formatSecondsToDuration(totalDurationSec),
            formattedDepartureTime = finalFormattedDeparture,
            formattedArrivalTime = finalFormattedArrival,
            totalDurationSeconds = totalDurationSec,
            transfersCount = finalTransfersCount,
            totalWalkDistanceMeters = finalWalkDistance,
            totalWalkDurationSeconds = finalWalkDuration,
            allRoutePolyline = if (allPoints.isNotEmpty()) allPoints else baseItinerary.allRoutePolyline,
            activeAlerts = activeAlerts
        )
    }

    private fun calculateTotalDurationSec(legs: List<PlannedLeg>, fallbackDurationSec: Long): Long {
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

    /**
     * Conciliates theoretical departure with live EMT / Metro / Cercanías feeds.
     */
    private suspend fun evaluateLiveReconciliation(
        legs: MutableList<PlannedLeg>,
        theoreticalStartTimeIso: String?,
        isDepartNow: Boolean,
        claimedVehicleKeys: MutableSet<String>
    ): ReconciliationData {
        val transitLegIndices = legs.indices.filter { legs[it].mode != TransitMode.WALK }
        if (transitLegIndices.isEmpty()) {
            return ReconciliationData(
                viability = ItineraryViability.THEORETICAL_SCHEDULE,
                notice = "Ruta directa a pie",
                recommendedStartTime = null,
                activeAlerts = emptyList()
            )
        }

        val firstTransitLegIdx = transitLegIndices.first()
        val activeAlerts = mutableListOf<String>()

        // Check service alerts for Metro / Cercanías
        transitLegIndices.forEach { legIndex ->
            val transitLeg = legs[legIndex]
            if (transitLeg.mode == TransitMode.SUBWAY || transitLeg.mode == TransitMode.TRAM) {
                val metroLine = TransitIdMapper.extractMetroLine(transitLeg.routeShortName, transitLeg.routeLongName)
                val stationName = transitLeg.fromName
                val alerts = checkMetroAlerts(metroLine, stationName)
                if (alerts.isNotEmpty()) {
                    activeAlerts.addAll(alerts)
                }
            }
        }

        // Determine if query is happening in real-time window
        val isCurrentRealTimeWindow = if (isDepartNow) {
            true
        } else {
            val startEpochMs = parseIsoToEpochMs(theoreticalStartTimeIso)
            if (startEpochMs > 0) {
                val diffMs = Math.abs(startEpochMs - System.currentTimeMillis())
                diffMs <= 60 * 60 * 1000L
            } else {
                false
            }
        }

        var reconciledNotice: String? = null
        var reconciledStartTime: String? = null
        var reconciledViability: ItineraryViability? = null
        val nowMs = System.currentTimeMillis()

        if (isCurrentRealTimeWindow) {
            // Reconcile legs sequentially so each transfer leg has the precise, real arrival time from the previous leg
            var currentAccumulatedTripMs = if (parseIsoToEpochMs(legs.firstOrNull()?.startTime) > nowMs) {
                parseIsoToEpochMs(legs.firstOrNull()?.startTime)
            } else {
                nowMs
            }

            var priorWalkBufferSec = 0L
            val legReconciliationResults = mutableListOf<Triple<Int, Int, String>>() // legIndex, delayMinutes, label
            var transferBrokenAndUnrecoverable = false
            var transferSpliced = false

            for (i in legs.indices) {
                val leg = legs[i]
                if (leg.mode == TransitMode.WALK) {
                    priorWalkBufferSec += leg.durationSeconds
                    continue
                }

                // We reached a transit leg. Earliest time user can physically reach this boarding stop:
                // Walking flexibility: 5 min backwards buffer at platform or 2.5 min (150s) faster walking leeway
                val platformBufferMs = if (i == 0 || (i > 0 && legs[i-1].mode == TransitMode.WALK && i <= 1)) 300_000L else 0L
                val earliestReachableUserArrivalMs = currentAccumulatedTripMs + (Math.max(0L, priorWalkBufferSec - 150L) * 1000L) - platformBufferMs
                val exactUserArrivalAtStopMs = currentAccumulatedTripMs + (priorWalkBufferSec * 1000L)
                val scheduledLegStartEpochMs = parseIsoToEpochMs(leg.startTime)

                var matchedLiveDelayMins: Int? = null
                var matchedLiveLabel: String? = null

                if (leg.mode == TransitMode.BUS) {
                    val isEmt = TransitIdMapper.isEmtBus(
                        agencyName = leg.agencyName,
                        routeShortName = leg.routeShortName,
                        routeLongName = leg.routeLongName,
                        fromStopId = leg.fromStopId,
                        fromName = leg.fromName
                    )

                    if (isEmt) {
                        val lineTarget = leg.routeShortName?.trim() ?: ""
                        var stopNumber = TransitIdMapper.extractEmtStopNumber(leg.fromStopId, leg.fromName)

                        if (stopNumber.isNullOrEmpty() && lineTarget.isNotEmpty()) {
                            val stopLat = if (leg.fromLat != 0.0) leg.fromLat else leg.geometry.firstOrNull()?.latitude ?: 0.0
                            val stopLon = if (leg.fromLon != 0.0) leg.fromLon else leg.geometry.firstOrNull()?.longitude ?: 0.0
                            stopNumber = findNearbyEmtStopId(stopLat, stopLon, lineTarget)
                        }

                        if (!stopNumber.isNullOrEmpty() && lineTarget.isNotEmpty()) {
                            val liveArrivals = fetchEmtLiveArrivals(stopNumber)
                            val targetArrivals = liveArrivals.filter {
                                TransitIdMapper.isSameEmtLine(it.linea, lineTarget)
                            }

                            if (targetArrivals.isNotEmpty()) {
                                val maxHorizonSeconds = targetArrivals.maxOfOrNull { it.secondsRemaining } ?: 0
                                val maxLiveHorizonMs = nowMs + (maxHorizonSeconds * 1000L) + (3 * 60 * 1000L)

                                val validCandidates = targetArrivals.filter { arrival ->
                                    val liveArrivalMs = nowMs + (arrival.secondsRemaining * 1000L)
                                    val vehicleKey = "EMT_${stopNumber}_${arrival.linea}_${arrival.secondsRemaining / 60}"
                                    liveArrivalMs >= earliestReachableUserArrivalMs && !claimedVehicleKeys.contains(vehicleKey)
                                }

                                val chosenCandidate = if (validCandidates.isNotEmpty()) {
                                    val firstReachable = validCandidates.minByOrNull { it.secondsRemaining }
                                    if (firstReachable != null) {
                                        val liveArrivalMs = nowMs + (firstReachable.secondsRemaining * 1000L)
                                        val delaySec = if (scheduledLegStartEpochMs > 0) ((liveArrivalMs - scheduledLegStartEpochMs) / 1000).toInt() else 0
                                        val delayMins = if (delaySec < -240) 0 else (delaySec / 60)
                                        Triple(firstReachable, liveArrivalMs, delayMins)
                                    } else null
                                } else null

                                if (chosenCandidate != null) {
                                    val (arrival, liveArrivalMs, delayMins) = chosenCandidate
                                    val vehicleKey = "EMT_${stopNumber}_${arrival.linea}_${arrival.secondsRemaining / 60}"
                                    val lineClean = lineTarget.removePrefix("L").removePrefix("l")
                                    val busLabel = if (lineClean.startsWith("C") || lineClean.startsWith("N") || lineClean.length >= 3) "Bus $lineClean" else "Bus $lineClean"
                                    claimedVehicleKeys.add(vehicleKey)
                                    matchedLiveDelayMins = delayMins
                                    matchedLiveLabel = busLabel
                                }
                            }
                        }
                    }
                } else if (leg.mode == TransitMode.SUBWAY || leg.mode == TransitMode.TRAM) {
                    val stationIdInt = TransitIdMapper.extractMetroStationId(leg.fromStopId, leg.fromName)
                    val metroLine = TransitIdMapper.extractMetroLine(leg.routeShortName, leg.routeLongName) ?: leg.routeShortName ?: ""

                    if (stationIdInt != null) {
                        val liveDepartures = fetchMetroLiveArrivals(stationIdInt.toString())
                        val targetDepartures = liveDepartures.filter { dep ->
                            val digits = metroLine.filter { it.isDigit() }
                            if (digits.isBlank()) {
                                true
                            } else {
                                dep.line.equals(metroLine, ignoreCase = true) ||
                                dep.line.filter { it.isDigit() } == digits
                            }
                        }

                        if (targetDepartures.isNotEmpty()) {
                            val maxHorizonSeconds = targetDepartures.maxOfOrNull { it.seconds } ?: 0
                            val maxLiveHorizonMs = nowMs + (maxHorizonSeconds * 1000L) + (3 * 60 * 1000L)

                            val validCandidates = targetDepartures.filter { dep ->
                                val liveArrivalMs = nowMs + (dep.seconds * 1000L)
                                val vehicleKey = "METRO_${stationIdInt}_${dep.line}_${dep.destination}_${dep.seconds / 60}"
                                liveArrivalMs >= earliestReachableUserArrivalMs && !claimedVehicleKeys.contains(vehicleKey)
                            }

                            val chosenCandidate = if (validCandidates.isNotEmpty()) {
                                val firstReachable = validCandidates.minByOrNull { it.seconds }
                                if (firstReachable != null) {
                                    val liveArrivalMs = nowMs + (firstReachable.seconds * 1000L)
                                    val delaySec = if (scheduledLegStartEpochMs > 0) ((liveArrivalMs - scheduledLegStartEpochMs) / 1000).toInt() else 0
                                    val delayMins = if (delaySec < -240) 0 else (delaySec / 60)
                                    Triple(firstReachable, liveArrivalMs, delayMins)
                                } else null
                            } else null

                            if (chosenCandidate != null) {
                                val (dep, _, delayMins) = chosenCandidate
                                val vehicleKey = "METRO_${stationIdInt}_${dep.line}_${dep.destination}_${dep.seconds / 60}"
                                claimedVehicleKeys.add(vehicleKey)
                                val lineDisp = if (metroLine.startsWith("L") || metroLine.isBlank()) metroLine else "L$metroLine"
                                matchedLiveDelayMins = delayMins
                                matchedLiveLabel = "Metro $lineDisp"
                            }
                        }
                    }
                } else if (leg.mode == TransitMode.RAIL) {
                    val cercaniasLine = TransitIdMapper.extractCercaniasLine(leg.routeShortName, leg.routeLongName, leg.agencyName, leg.mode) ?: leg.routeShortName ?: ""
                    val tripUpdates = RealTimeTransitRepository.getCercaniasTripUpdates()
                    val stopIdDigits = leg.fromStopId?.filter { it.isDigit() }

                    if (tripUpdates.isNotEmpty()) {
                        val matchingTripUpdate = tripUpdates.values.firstOrNull { update ->
                            val tripId = update.tripId
                            val lineMatches = cercaniasLine.isNotBlank() && tripId.contains(cercaniasLine.replace("-", ""), ignoreCase = true)
                            val stopMatches = !stopIdDigits.isNullOrBlank() && (update.stopDelays.containsKey(stopIdDigits) || update.stopEstimatedTimes.containsKey(stopIdDigits))
                            lineMatches || stopMatches
                        }

                        if (matchingTripUpdate != null) {
                            val stopEstimatedEpochSec = if (!stopIdDigits.isNullOrBlank()) matchingTripUpdate.stopEstimatedTimes[stopIdDigits] else null
                            val delaySec = if (!stopIdDigits.isNullOrBlank()) {
                                matchingTripUpdate.stopDelays[stopIdDigits] ?: matchingTripUpdate.delaySeconds
                            } else {
                                matchingTripUpdate.delaySeconds
                            }

                            val delayMins = delaySec / 60
                            val liveArrivalMs = if (stopEstimatedEpochSec != null && stopEstimatedEpochSec > 0) {
                                stopEstimatedEpochSec * 1000L
                            } else if (scheduledLegStartEpochMs > 0) {
                                scheduledLegStartEpochMs + (delayMins * 60 * 1000L)
                            } else {
                                nowMs + (delayMins * 60 * 1000L)
                            }

                            val vehicleKey = "RENFE_${matchingTripUpdate.tripId}"
                            if (liveArrivalMs >= (earliestReachableUserArrivalMs - 120_000L) && !claimedVehicleKeys.contains(vehicleKey)) {
                                claimedVehicleKeys.add(vehicleKey)
                                matchedLiveDelayMins = delayMins
                                val lineLabel = if (cercaniasLine.isNotBlank()) "Cercanías $cercaniasLine" else "Cercanías Renfe"
                                matchedLiveLabel = lineLabel
                            }
                        }
                    }
                }

                // If live GPS vehicle found:
                if (matchedLiveDelayMins != null && matchedLiveLabel != null) {
                    legReconciliationResults.add(Triple(i, matchedLiveDelayMins, matchedLiveLabel))

                    val origStart = leg.scheduledStartTime ?: leg.formattedStartTime
                    val origEnd = leg.scheduledEndTime ?: leg.formattedEndTime
                    val updatedStart = if (matchedLiveDelayMins != 0) shiftFormattedTime(origStart, matchedLiveDelayMins) else origStart
                    val updatedEnd = if (matchedLiveDelayMins != 0) shiftFormattedTime(origEnd, matchedLiveDelayMins) else origEnd

                    legs[i] = leg.copy(
                        scheduledStartTime = origStart,
                        scheduledEndTime = origEnd,
                        formattedStartTime = updatedStart,
                        formattedEndTime = updatedEnd,
                        isRealTimeVerified = true,
                        realTimeDelayMinutes = matchedLiveDelayMins
                    )

                    // Advance accumulated trip time to the end of this leg
                    val legEndEpochMs = parseIsoToEpochMs(leg.endTime)
                    val newLegEndMs = if (legEndEpochMs > 0) legEndEpochMs + (matchedLiveDelayMins * 60 * 1000L) else (nowMs + (leg.durationSeconds * 1000L))
                    currentAccumulatedTripMs = newLegEndMs
                    priorWalkBufferSec = 0L
                } else {
                    // Check if scheduled departure is broken/unreachable due to delay from previous legs
                    if (scheduledLegStartEpochMs > 0 && scheduledLegStartEpochMs < earliestReachableUserArrivalMs) {
                        val fromLat = if (leg.fromLat != 0.0) leg.fromLat else leg.geometry.firstOrNull()?.latitude ?: 0.0
                        val fromLon = if (leg.fromLon != 0.0) leg.fromLon else leg.geometry.firstOrNull()?.longitude ?: 0.0
                        val toLat = legs.last().toLat.takeIf { it != 0.0 } ?: legs.last().geometry.lastOrNull()?.latitude ?: 0.0
                        val toLon = legs.last().toLon.takeIf { it != 0.0 } ?: legs.last().geometry.lastOrNull()?.longitude ?: 0.0

                        val nextOfficialSubPlan = withTimeoutOrNull(3500) {
                            trySubqueryTransitousNext(fromLat, fromLon, toLat, toLon, exactUserArrivalAtStopMs)
                        }

                        if (nextOfficialSubPlan != null && nextOfficialSubPlan.legs.isNotEmpty()) {
                            val newWalkDist = nextOfficialSubPlan.legs.filter { it.mode == TransitMode.WALK }.sumOf { it.distanceMeters }
                            val oldRemainingWalkDist = legs.subList(i, legs.size).filter { it.mode == TransitMode.WALK }.sumOf { it.distanceMeters }

                            if (newWalkDist <= oldRemainingWalkDist + 600) {
                                while (legs.size > i) {
                                    legs.removeAt(legs.size - 1)
                                }
                                legs.addAll(nextOfficialSubPlan.legs)
                                transferSpliced = true
                                break
                            } else {
                                transferBrokenAndUnrecoverable = true
                            }
                        } else {
                            transferBrokenAndUnrecoverable = true
                        }
                    } else {
                        // Scheduled time is in the future and reachable
                        val legEndEpochMs = parseIsoToEpochMs(leg.endTime)
                        currentAccumulatedTripMs = if (legEndEpochMs > 0) legEndEpochMs else (currentAccumulatedTripMs + (leg.durationSeconds * 1000L))
                        priorWalkBufferSec = 0L
                    }
                }
            }

            var transferWarningNotice: String? = null

            if (legReconciliationResults.isNotEmpty()) {
                val notices = legReconciliationResults.map { (_, delayMinutes, label) ->
                    if (delayMinutes > 0) {
                        "$label con +$delayMinutes min de retraso"
                    } else if (delayMinutes < 0) {
                        val absDelay = Math.abs(delayMinutes)
                        "$label con $absDelay min de adelanto"
                    } else {
                        "$label en hora"
                    }
                }
                reconciledNotice = notices.joinToString(" • ")
                reconciledViability = if (activeAlerts.isNotEmpty()) ItineraryViability.SERVICE_ALERT else ItineraryViability.VIABLE_ON_TIME
            }

            for ((legIndex, _, _) in legReconciliationResults) {
                val warning = adjustTransferWalkAndCheckSlack(legs, legIndex)
                if (warning != null && transferWarningNotice == null) {
                    transferWarningNotice = warning
                }
            }

            if (transferSpliced) {
                reconciledViability = ItineraryViability.ADJUSTED_NEXT_DEPARTURE
                val firstLegLabel = legReconciliationResults.firstOrNull()?.third ?: "Vehículo"
                val firstLegDelay = legReconciliationResults.firstOrNull()?.second ?: 0
                val nextTransit = legs.subList(firstTransitLegIdx.coerceAtMost(legs.size - 1), legs.size).firstOrNull { it.mode != TransitMode.WALK }
                val nextStart = nextTransit?.formattedStartTime ?: ""
                val nextLine = nextTransit?.routeShortName ?: ""
                reconciledNotice = if (firstLegDelay > 0) {
                    "GPS en directo: $firstLegLabel (+${firstLegDelay} min) • Siguiente salida $nextLine ($nextStart)"
                } else {
                    "Transbordo ajustado con el siguiente servicio ($nextStart)"
                }
            } else if (transferWarningNotice != null || transferBrokenAndUnrecoverable) {
                reconciledViability = ItineraryViability.ADJUSTED_NEXT_DEPARTURE
                val firstLegLabel = legReconciliationResults.firstOrNull()?.third ?: "Vehículo"
                val firstLegDelay = legReconciliationResults.firstOrNull()?.second ?: 0
                reconciledNotice = if (firstLegDelay > 0) {
                    "GPS en directo: $firstLegLabel (+${firstLegDelay} min) • Transbordo ajustado"
                } else {
                    "Transbordo ajustado con el siguiente servicio"
                }
            }

            // Set recommended departure time strictly based on first transit leg
            val firstTransitLeg = legs[firstTransitLegIdx]
            val priorWalkDurationSec = legs.take(firstTransitLegIdx).sumOf { it.durationSeconds }
            val priorWalkMins = (priorWalkDurationSec / 60).toInt()

            val firstTransitStartTime = firstTransitLeg.formattedStartTime
            reconciledStartTime = shiftFormattedTime(firstTransitStartTime, -priorWalkMins)

            // Also synchronize the start and end times of the initial walk leg(s)
            if (firstTransitLegIdx > 0 && reconciledStartTime != null) {
                var currentWalkStart = reconciledStartTime
                for (wIdx in 0 until firstTransitLegIdx) {
                    val wLeg = legs[wIdx]
                    val wMins = (wLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
                    val wEnd = shiftFormattedTime(currentWalkStart!!, wMins)
                    legs[wIdx] = wLeg.copy(
                        formattedStartTime = currentWalkStart,
                        formattedEndTime = wEnd
                    )
                    currentWalkStart = wEnd
                }
            }

            if (reconciledNotice == null) {
                reconciledNotice = "En hora según horario oficial"
            }
        }

        val finalViability = reconciledViability ?: if (activeAlerts.isNotEmpty()) {
            ItineraryViability.SERVICE_ALERT
        } else {
            ItineraryViability.THEORETICAL_SCHEDULE
        }

        return ReconciliationData(
            viability = finalViability,
            notice = reconciledNotice ?: if (activeAlerts.isNotEmpty()) "Avisos activos en las líneas del trayecto" else null,
            recommendedStartTime = reconciledStartTime,
            activeAlerts = activeAlerts
        )
    }

    private fun adjustTransferWalkAndCheckSlack(legs: MutableList<PlannedLeg>, legIndex: Int): String? {
        val leg = legs[legIndex]
        val legEndTime = leg.formattedEndTime

        if (legIndex + 1 < legs.size && legs[legIndex + 1].mode == TransitMode.WALK) {
            val walkLeg = legs[legIndex + 1]
            val walkMins = (walkLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
            val newWalkStart = legEndTime
            val newWalkEnd = shiftFormattedTime(newWalkStart, walkMins)
            legs[legIndex + 1] = walkLeg.copy(
                formattedStartTime = newWalkStart,
                formattedEndTime = newWalkEnd
            )
        }

        var warningNotice: String? = null
        for (j in (legIndex + 1) until legs.size) {
            val currLeg = legs[j]
            val prevEnd = legs[j - 1].formattedEndTime
            if (currLeg.mode != TransitMode.WALK) {
                val scheduledDeparture = currLeg.formattedStartTime
                val slackMinutes = timeToMinutes(scheduledDeparture) - timeToMinutes(prevEnd)

                if (slackMinutes < 0) {
                    val modeName = if (currLeg.mode == TransitMode.SUBWAY || currLeg.mode == TransitMode.TRAM) "Metro" else "Bus"
                    val lineName = currLeg.routeShortName ?: ""
                    warningNotice = "Transbordo ajustado con $modeName $lineName"
                }
            } else {
                val walkMins = (currLeg.durationSeconds / 60).toInt().coerceAtLeast(1)
                val newWalkEnd = shiftFormattedTime(prevEnd, walkMins)
                legs[j] = currLeg.copy(
                    formattedStartTime = prevEnd,
                    formattedEndTime = newWalkEnd
                )
            }
        }
        return warningNotice
    }

    private fun timeToMinutes(timeStr: String): Int {
        if (timeStr.isBlank() || !timeStr.contains(":")) return 0
        return try {
            val parts = timeStr.trim().split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun shiftFormattedTime(timeStr: String, minutesToAdd: Int): String {
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

    private suspend fun trySubqueryTransitousNext(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        arrivalEpochMs: Long
    ): PlannedItinerary? {
        return try {
            val fromPlace = String.format(Locale.US, "%.5f,%.5f", fromLat, fromLon)
            val toPlace = String.format(Locale.US, "%.5f,%.5f", toLat, toLon)
            val isoTime = java.time.Instant.ofEpochMilli(arrivalEpochMs)
                .atZone(java.time.ZoneId.of("Europe/Madrid"))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            val resp = transitousApiService.plan(
                fromPlace = fromPlace,
                toPlace = toPlace,
                time = isoTime,
                date = null,
                arriveBy = null,
                maxTransfers = 1,
                modes = "WALK,SUBWAY,TRAM,BUS,COACH,REGIONAL_RAIL",
                maxWalkDuration = 30,
                maxWalkDist = 2000
            )
            val firstItin = resp.itineraries?.firstOrNull() ?: return null
            mapDtoToItinerary(firstItin, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Subquery to Transitous failed: ${e.message}")
            null
        }
    }

    private data class MetroArrival(val line: String, val destination: String, val minutes: Int, val seconds: Int)

    private suspend fun fetchMetroLiveArrivals(stationId: String): List<com.example.data.repository.MetroArrival> {
        return com.example.data.repository.RealTimeTransitRepository.getMetroLiveArrivals(stationId)
    }

    /**
     * Fetches live SAE arrivals for an EMT stop directly from Geoportal.
     */
    private suspend fun fetchEmtLiveArrivals(stopNumber: String): List<EmtBusTime> {
        return com.example.data.repository.RealTimeTransitRepository.getEmtLiveArrivals(stopNumber, useFastTimeout = true)
    }

    /**
     * Cross-checks active Metrovalencia disruptions.
     */
    private fun checkMetroAlerts(metroLine: String?, stationName: String): List<String> {
        val alerts = mutableListOf<String>()
        val incidents = metroAlertsRepository?.activeIncidents?.value ?: emptyList()

        for (inc in incidents) {
            val text = "${inc.descriptionEs} ${inc.descriptionCa}"
            if (!metroLine.isNullOrEmpty() && (inc.lineaFgv?.contains(metroLine, ignoreCase = true) == true || text.contains("L$metroLine", ignoreCase = true) || text.contains("Línea $metroLine", ignoreCase = true))) {
                alerts.add("Aviso Metrovalencia L$metroLine: ${inc.descriptionEs.take(80)}")
            } else if (text.contains(stationName, ignoreCase = true)) {
                alerts.add("Incidencia en estación $stationName: ${inc.descriptionEs.take(80)}")
            }
        }
        return alerts
    }

    private fun resolveRouteColor(mode: TransitMode, routeColorHex: String?, routeShortName: String?, agencyName: String?): String {
        return com.example.util.LineColorResolver.resolveRouteColorHex(mode, routeShortName, routeColorHex, agencyName).removePrefix("#")
    }

    private fun formatSecondsToDuration(seconds: Long): String {
        val minutes = (seconds / 60).coerceAtLeast(1)
        return if (minutes < 60) {
            "$minutes min"
        } else {
            val hours = minutes / 60
            val remainingMins = minutes % 60
            if (remainingMins == 0L) "$hours h" else "${hours}h ${remainingMins}m"
        }
    }

    private fun formatIsoToTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "--:--"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(isoString.substringBefore("Z").substringBefore("+"))
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("Europe/Madrid")
            }
            if (date != null) outputFormat.format(date) else isoString.takeLast(5)
        } catch (e: Exception) {
            isoString.takeLast(5)
        }
    }

    private fun addMinutesToCurrentTime(minutesToAdd: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
        cal.add(Calendar.MINUTE, minutesToAdd)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(cal.time)
    }

    private fun calculateItineraryScore(itin: PlannedItinerary, requestTimeMs: Long): Double {
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

    private fun parseIsoToEpochMs(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    java.time.Instant.parse(isoString).toEpochMilli()
                } catch (e: Exception) {
                    java.time.OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
                }
            } else {
                val cleanIso = isoString.substringBefore("Z").substringBefore("+")
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                inputFormat.parse(cleanIso)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private data class ReconciliationData(
        val viability: ItineraryViability,
        val notice: String?,
        val recommendedStartTime: String?,
        val activeAlerts: List<String>
    )

    /**
     * Finds the closest EMT stop to (stopLat, stopLon) and verifies that lineTarget passes through it.
     */
    private suspend fun findNearbyEmtStopId(stopLat: Double, stopLon: Double, lineTarget: String): String? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (stopLat == 0.0 || stopLon == 0.0) return@withContext null

        val cacheKey = String.format(Locale.US, "%.4f,%.4f,%s", stopLat, stopLon, lineTarget)
        nearbyStopCache[cacheKey]?.let { return@withContext it }

        try {
            var activeStops = cachedActiveStops
            if (activeStops == null) {
                try {
                    val db = com.example.data.database.AppDatabase.getDatabase(ctx)
                    activeStops = db.geoportalStopDao().getAllActiveStops()
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching stops from DB: ${e.message}")
                }

                if (activeStops.isNullOrEmpty()) {
                    activeStops = BusMapper.loadStopsFromAssets(ctx)
                }
                cachedActiveStops = activeStops
            }

            if (activeStops.isNullOrEmpty()) return@withContext null

            var closestStop: com.example.data.database.GeoportalStopEntity? = null
            var minDistSq = Double.MAX_VALUE

            for (stop in activeStops) {
                if (!TransitIdMapper.stopPassesLine(stop.lineas, lineTarget)) continue

                val dLat = stop.lat - stopLat
                val dLon = stop.lon - stopLon
                val distSq = dLat * dLat + dLon * dLon
                if (distSq < minDistSq) {
                    minDistSq = distSq
                    closestStop = stop
                }
            }

            if (closestStop == null) {
                val assetStops = BusMapper.loadStopsFromAssets(ctx)
                for (stop in assetStops) {
                    if (!TransitIdMapper.stopPassesLine(stop.lineas, lineTarget)) continue
                    val dLat = stop.lat - stopLat
                    val dLon = stop.lon - stopLon
                    val distSq = dLat * dLat + dLon * dLon
                    if (distSq < minDistSq) {
                        minDistSq = distSq
                        closestStop = stop
                    }
                }
            }

            val resultStopId = closestStop?.id_parada
            if (resultStopId != null) {
                nearbyStopCache[cacheKey] = resultStopId
            }
            resultStopId
        } catch (e: Exception) {
            Log.w(TAG, "Error finding nearby EMT stop: ${e.message}")
            null
        }
    }
}
