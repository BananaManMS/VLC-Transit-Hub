package com.example.data.repository.routing

import android.content.Context
import android.util.Log
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.TransitMode
import com.example.data.network.NetworkModule
import com.example.data.network.TransitousApiService
import com.example.data.repository.MetroAlertsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Hybrid multimodal routing repository connecting to Transitous / MOTIS 2 API
 * and orchestrating real-time reconciliation via [LiveReconciliationEngine].
 */
class HybridRoutingRepository(
    private val transitousApiService: TransitousApiService = NetworkModule.transitousApiService,
    private val okHttpClient: OkHttpClient = NetworkModule.okHttpClient,
    private val metroAlertsRepository: MetroAlertsRepository? = null,
    private val context: Context? = null
) {
    private val liveReconciliationEngine = LiveReconciliationEngine(
        context = context,
        metroAlertsRepository = metroAlertsRepository,
        transitousApiService = transitousApiService
    )

    companion object {
        private const val TAG = "HybridRoutingRepo"
        const val ROUTE_TOLERANCE_MINUTES = 5
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
                    val offset = try {
                        java.time.ZoneId.of("Europe/Madrid").rules.getOffset(java.time.Instant.now()).id
                    } catch (_: Exception) {
                        "+01:00"
                    }
                    "${datePart}T${timePart}${offset}"
                }
            } else {
                // "Depart NOW" query: subtract 3 minutes so MOTIS includes immediate departures (0-2 min from now)
                try {
                    val madridZone = java.time.ZoneId.of("Europe/Madrid")
                    val zonedDateTime = java.time.ZonedDateTime.now(madridZone).minusMinutes(3)
                    zonedDateTime.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                } catch (e: Exception) {
                    null
                }
            }

            Log.d(TAG, "Requesting Transitous plan: from $fromPlace to $toPlace, isoTime=$isoFormattedTime, arriveBy=$arriveBy")

            val response = transitousApiService.plan(
                fromPlace = fromPlace,
                toPlace = toPlace,
                time = isoFormattedTime,
                date = null, // MOTIS 2 receives full ISO-8601 timestamp in 'time' parameter
                arriveBy = if (arriveBy) true else null,
                maxTransfers = maxTransfers,
                modes = modes,
                numItineraries = 6,
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
                RoutingDataMapper.mapDtoToItinerary(itinDto, index, originName, destinationName)
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

            val isDepartNowQuery = time.isNullOrBlank() && date.isNullOrBlank() && !arriveBy
            val enrichedItineraries = if (isDepartNowQuery) {
                try {
                    withTimeoutOrNull(REAL_TIME_ITINERARY_TIMEOUT_MS) {
                        reconcileItineraries(basePlannedItineraries, isDepartNow = true)
                    } ?: basePlannedItineraries
                } catch (e: Exception) {
                    basePlannedItineraries
                }
            } else {
                basePlannedItineraries
            }

            // Sort primarily by arrival time (earliest arrival first), then duration as tiebreaker
            val sortedItineraries = enrichedItineraries.sortedWith(
                compareBy<PlannedItinerary> { RoutingDataMapper.getEffectiveArrivalEpochMs(it) }
                    .thenBy { it.totalDurationSeconds }
                    .thenBy { RoutingDataMapper.getEffectiveDepartureEpochMs(it) }
            ).take(6)

            Result.success(sortedItineraries)
        } catch (e: Exception) {
            Log.e(TAG, "Error planning route with Transitous", e)
            Result.failure(e)
        }
    }

    /**
     * Public reconciliation method allowing RoutePlannerViewModel to enrich itineraries asynchronously in background
     * without blocking UI response. Supports sequential allocation to prevent vehicle duplication across multiple departures.
     */
    suspend fun reconcileItineraries(
        baseItineraries: List<PlannedItinerary>,
        isDepartNow: Boolean = true
    ): List<PlannedItinerary> {
        return liveReconciliationEngine.reconcileItineraries(baseItineraries, isDepartNow)
    }

    suspend fun reconcileItinerary(
        baseItinerary: PlannedItinerary,
        isDepartNow: Boolean = true
    ): PlannedItinerary {
        return liveReconciliationEngine.reconcileItinerary(baseItinerary, isDepartNow)
    }
}
