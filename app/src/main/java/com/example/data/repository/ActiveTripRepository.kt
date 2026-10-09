package com.example.data.repository

import com.example.data.database.ActiveTripDao
import com.example.data.database.ActiveTripEntity
import com.example.data.model.routing.PlannedItinerary
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.osmdroid.util.GeoPoint
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Repository responsible for managing the single active multimodal trip lifecycle,
 * persistence in Room database, state serialization, and deterministic expiration checks.
 */
class ActiveTripRepository(
    private val activeTripDao: ActiveTripDao
) {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(GeoPoint::class.java, GeoPointAdapter())
        .create()

    /**
     * Observes the active trip as a Flow, automatically deserializing the stored itinerary.
     */
    fun getActiveTripFlow(): Flow<ActiveTripState?> {
        return activeTripDao.getActiveTripFlow().map { entity ->
            if (entity == null || entity.status == ActiveTripEntity.STATUS_CANCELLED) {
                null
            } else {
                mapEntityToState(entity)
            }
        }
    }

    /**
     * Retrieves the current active trip state asynchronously.
     */
    suspend fun getActiveTrip(): ActiveTripState? {
        val entity = activeTripDao.getActiveTrip() ?: return null
        if (entity.status == ActiveTripEntity.STATUS_CANCELLED) return null
        return mapEntityToState(entity)
    }

    /**
     * Saves or replaces the single active trip using UPSERT on ACTIVE_TRIP ID.
     */
    suspend fun startTrip(
        itinerary: PlannedItinerary,
        originName: String,
        destinationName: String
    ) {
        val routeJson = gson.toJson(itinerary)

        val entity = ActiveTripEntity(
            tripId = "ACTIVE_TRIP",
            originName = originName,
            destinationName = destinationName,
            status = ActiveTripEntity.STATUS_ACTIVE,
            startTimestamp = System.currentTimeMillis(),
            currentLegIndex = 0,
            routeDataJson = routeJson
        )
        activeTripDao.insertTrip(entity)
    }

    suspend fun checkAndCleanExpiredTrip(): Boolean {
        val entity = activeTripDao.getActiveTrip() ?: return false
        val now = System.currentTimeMillis()
        val expirationThreshold = entity.startTime + 7_200_000L

        if (now > expirationThreshold) {
            activeTripDao.deleteActiveTrip()
            return true
        }
        return false
    }

    suspend fun advanceLegIndex(newIndex: Int) {
        val entity = activeTripDao.getActiveTrip() ?: return
        val updated = entity.copy(currentLegIndex = newIndex)
        activeTripDao.insertTrip(updated)
    }

    suspend fun updateItinerary(newItinerary: PlannedItinerary) {
        val entity = activeTripDao.getActiveTrip() ?: return
        val routeJson = gson.toJson(newItinerary)
        val updatedEntity = entity.copy(
            routeDataJson = routeJson
        )
        activeTripDao.insertTrip(updatedEntity)
    }

    suspend fun cancelActiveTrip() {
        activeTripDao.deleteActiveTrip()
    }

    suspend fun markTripCompleted() {
        activeTripDao.updateTripStatus("ACTIVE_TRIP", ActiveTripEntity.STATUS_COMPLETED)
    }

    suspend fun completeActiveTrip() {
        activeTripDao.deleteActiveTrip()
    }

    private fun mapEntityToState(entity: ActiveTripEntity): ActiveTripState? {
        return try {
            val itinerary = gson.fromJson(entity.rawJsonData, PlannedItinerary::class.java)
            ActiveTripState(
                originName = entity.originName.ifBlank { "Origen" },
                destinationName = entity.destinationName,
                itinerary = itinerary,
                status = entity.status,
                currentLegIndex = entity.currentStepIndex,
                startTimestamp = entity.startTime,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            android.util.Log.e("ActiveTripRepository", "Error deserializing active trip: ${e.message}", e)
            null
        }
    }

    private class GeoPointAdapter : JsonSerializer<GeoPoint>, JsonDeserializer<GeoPoint> {
        override fun serialize(src: GeoPoint?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            val obj = JsonObject()
            if (src != null) {
                obj.addProperty("lat", src.latitude)
                obj.addProperty("lon", src.longitude)
                obj.addProperty("latitude", src.latitude)
                obj.addProperty("longitude", src.longitude)
            }
            return obj
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): GeoPoint {
            if (json == null || json.isJsonNull) return GeoPoint(0.0, 0.0)
            if (json.isJsonObject) {
                val obj = json.asJsonObject
                val lat = obj.get("lat")?.asDouble
                    ?: obj.get("latitude")?.asDouble
                    ?: 0.0
                val lon = obj.get("lon")?.asDouble
                    ?: obj.get("lng")?.asDouble
                    ?: obj.get("longitude")?.asDouble
                    ?: 0.0
                return GeoPoint(lat, lon)
            } else if (json.isJsonArray) {
                val arr = json.asJsonArray
                if (arr.size() >= 2) {
                    val lat = arr[0].asDouble
                    val lon = arr[1].asDouble
                    return GeoPoint(lat, lon)
                }
            }
            return GeoPoint(0.0, 0.0)
        }
    }
}

/**
 * Domain representation of an active trip in memory.
 */
data class ActiveTripState(
    val originName: String,
    val destinationName: String,
    val itinerary: PlannedItinerary,
    val status: String,
    val currentLegIndex: Int,
    val startTimestamp: Long,
    val lastUpdatedTimestamp: Long
) {
    val tripId: String
        get() = "${originName}_${destinationName}_$startTimestamp"
}
