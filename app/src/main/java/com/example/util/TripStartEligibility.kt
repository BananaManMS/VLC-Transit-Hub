package com.example.util

import android.location.Location
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.routing.PlannerLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object TripStartEligibility {

    /**
     * Evaluates if a trip can be started in active navigation mode right now.
     * Requires BOTH:
     * 1. Distance between user GPS location and trip origin is <= 300 meters.
     * 2. Trip departure time is within <= 30 minutes from current time.
     */
    fun canStartTrip(
        itinerary: PlannedItinerary,
        userLocation: Location?,
        originLocation: PlannerLocation?
    ): Boolean {
        // Condition 1: Distance <= 300 meters
        val isDistanceOk = isOriginNearUser(itinerary, userLocation, originLocation)
        if (!isDistanceOk) return false

        // Condition 2: Departure time <= 30 minutes
        val isTimeOk = isDepartureTimeWithinWindow(itinerary)
        return isTimeOk
    }

    /**
     * Checks if trip origin is within 300 meters of the user's current GPS location.
     */
    fun isOriginNearUser(
        itinerary: PlannedItinerary,
        userLocation: Location?,
        originLocation: PlannerLocation?
    ): Boolean {
        // If origin is explicitly user's current location, distance is 0m
        if (originLocation?.isUserGps == true) return true
        val titleLower = originLocation?.title?.lowercase(Locale.getDefault()) ?: ""
        if (titleLower.contains("ubicaci") || titleLower.contains("location")) return true

        if (userLocation == null) return false

        val origLat = if (originLocation != null && originLocation.latitude != 0.0) {
            originLocation.latitude
        } else {
            itinerary.legs.firstOrNull { it.fromLat != 0.0 }?.fromLat ?: 0.0
        }

        val origLon = if (originLocation != null && originLocation.longitude != 0.0) {
            originLocation.longitude
        } else {
            itinerary.legs.firstOrNull { it.fromLon != 0.0 }?.fromLon ?: 0.0
        }

        if (origLat == 0.0 || origLon == 0.0) return false

        val results = FloatArray(1)
        Location.distanceBetween(
            userLocation.latitude,
            userLocation.longitude,
            origLat,
            origLon,
            results
        )
        val distanceMeters = results[0]
        return distanceMeters <= 300.0
    }

    /**
     * Checks if trip departure time is within 30 minutes from now (range: -15 mins to +30 mins).
     */
    fun isDepartureTimeWithinWindow(itinerary: PlannedItinerary): Boolean {
        val diffMinutes = getMinutesUntilDeparture(itinerary) ?: return true // Default pass if unparseable
        return diffMinutes in -15..30
    }

    /**
     * Returns difference in minutes between current time and trip departure time.
     * Positive = future departure, Negative = past departure.
     */
    fun getMinutesUntilDeparture(itinerary: PlannedItinerary): Int? {
        val startIso = itinerary.startTime
        if (!startIso.isNullOrBlank()) {
            val epochMs = parseIsoToEpochMs(startIso)
            if (epochMs > 0) {
                val nowMs = System.currentTimeMillis()
                return ((epochMs - nowMs) / 60000L).toInt()
            }
        }

        val depStr = itinerary.formattedDepartureTime
        if (depStr.contains(":")) {
            return try {
                val parts = depStr.trim().split(":")
                val depHours = parts[0].toInt()
                val depMins = parts[1].toInt()
                val depTotalMins = depHours * 60 + depMins

                val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"))
                val nowHours = cal.get(Calendar.HOUR_OF_DAY)
                val nowMins = cal.get(Calendar.MINUTE)
                val nowTotalMins = nowHours * 60 + nowMins

                var diff = depTotalMins - nowTotalMins
                if (diff < -1200) diff += 1440
                if (diff > 1200) diff -= 1440
                diff
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun parseIsoToEpochMs(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
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
                inputFormat.parse(cleanIso)?.time ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
