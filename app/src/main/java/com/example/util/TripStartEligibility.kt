package com.example.util

import android.location.Location
import com.example.data.model.routing.PlannedItinerary
import com.example.ui.routing.PlannerLocation
import java.util.Locale

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

        val distanceMeters = TripStepProgressionEngine.calculateDistanceMeters(
            userLocation.latitude,
            userLocation.longitude,
            origLat,
            origLon
        )
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
        val effectiveDepartureMs = com.example.data.repository.routing.RoutingDataMapper.getEffectiveDepartureEpochMs(itinerary)
        val departureMillis = if (effectiveDepartureMs > 0L) {
            effectiveDepartureMs
        } else {
            TripTimeParser.parseTimeToMillis(itinerary.startTime)
                ?: TripTimeParser.parseTimeToMillis(itinerary.formattedDepartureTime)
                ?: return null
        }

        val nowMs = System.currentTimeMillis()
        return ((departureMillis - nowMs) / 60000L).toInt()
    }

    /**
     * Checks if two PlannedItinerary instances describe the exact same journey.
     */
    fun isSameItinerary(a: PlannedItinerary?, b: PlannedItinerary?): Boolean {
        if (a == null || b == null) return false
        if (a.id.isNotBlank() && b.id.isNotBlank() && a.id == b.id) return true
        if (a.formattedDepartureTime == b.formattedDepartureTime &&
            a.formattedArrivalTime == b.formattedArrivalTime &&
            a.legs.size == b.legs.size) {
            val legsMatch = a.legs.indices.all { i ->
                val legA = a.legs[i]
                val legB = b.legs[i]
                legA.mode == legB.mode &&
                legA.routeShortName == legB.routeShortName &&
                legA.fromName == legB.fromName &&
                legA.toName == legB.toName
            }
            if (legsMatch) return true
        }
        return false
    }

    /**
     * Checks if the given itinerary corresponds to the active trip currently in progress.
     */
    fun isItineraryCurrentlyActive(
        itinerary: PlannedItinerary?,
        activeTrip: com.example.data.repository.ActiveTripState?
    ): Boolean {
        if (itinerary == null || activeTrip == null) return false
        return isSameItinerary(itinerary, activeTrip.itinerary)
    }
}
