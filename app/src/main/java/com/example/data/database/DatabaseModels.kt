package com.example.data.database

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val lines: String = "",
    val zone: String = "",
    val accessibility: Boolean = true,
    val isFavorite: Boolean = false
) {
    @get:Ignore
    val latitude: Double get() = lat

    @get:Ignore
    val longitude: Double get() = lon

    @get:Ignore
    val description: String get() = zone
}

@Entity(tableName = "cercanias_stations")
data class CercaniasStationEntity(
    @PrimaryKey val stop_id: String = "",
    val nombre: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val lines: String = "",
    val zone: String = "1",
    val isFavorite: Boolean = false
) {
    @Ignore
    var lineas: List<String> = emptyList()

    @Ignore
    var horarios: List<RenfeScheduleItem> = emptyList()

    @Ignore
    constructor(
        stopId: String,
        nombre: String,
        lat: Double,
        lon: Double,
        lineas: List<String>,
        horarios: List<RenfeScheduleItem>,
        isFavorite: Boolean
    ) : this(
        stop_id = stopId,
        nombre = nombre,
        lat = lat,
        lon = lon,
        lines = lineas.joinToString(","),
        zone = "1",
        isFavorite = isFavorite
    ) {
        this.lineas = lineas
        this.horarios = horarios
    }

    @get:Ignore
    val id: String get() = stop_id

    @get:Ignore
    val code: String get() = stop_id

    @get:Ignore
    val name: String get() = nombre

    @get:Ignore
    val displayName: String get() = nombre

    @get:Ignore
    val latitud: Double get() = lat

    @get:Ignore
    val longitud: Double get() = lon
}

@Entity(tableName = "geoportal_stops")
data class GeoportalStopEntity(
    @PrimaryKey val id_parada: String,
    val denominacion: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val lineas: String = "",
    val suprimida: Int = 0
) {
    @get:Ignore
    val id: String get() = id_parada

    @get:Ignore
    val name: String get() = denominacion

    @get:Ignore
    val type: String get() = ""

    @get:Ignore
    val lineCode: String get() = ""
}

@Entity(tableName = "metrobus_stops")
data class MetrobusStopEntity(
    @PrimaryKey val id_parada: String,
    val denominacion: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val lineas: String = "",
    val suprimida: Int = 0
) {
    @get:Ignore
    val stopId: String get() = id_parada

    @get:Ignore
    val stopName: String get() = denominacion

    @get:Ignore
    val lines: String get() = lineas

    @get:Ignore
    val zone: String get() = ""

    @get:Ignore
    val id: String get() = id_parada

    @get:Ignore
    val name: String get() = denominacion
}

@Entity(tableName = "renfe_schedule_items")
data class RenfeScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val linea: String = "",
    val llegada: String = "",
    val destino: String = "",
    val tripIdsString: String = "",
    val departureTime: String = llegada,
    val origin: String = "",
    val days: String = ""
) {
    @Ignore
    var trip_ids: List<String> = emptyList()

    @Ignore
    constructor(linea: String, tripIds: List<String>, llegada: String) : this(
        id = 0,
        linea = linea,
        llegada = llegada,
        destino = "",
        tripIdsString = tripIds.joinToString(","),
        departureTime = llegada,
        origin = "",
        days = ""
    ) {
        this.trip_ids = tripIds
    }

    @Ignore
    constructor(linea: String, tripIds: List<String>, llegada: String, destino: String) : this(
        id = 0,
        linea = linea,
        llegada = llegada,
        destino = destino,
        tripIdsString = tripIds.joinToString(","),
        departureTime = llegada,
        origin = "",
        days = ""
    ) {
        this.trip_ids = tripIds
    }

    @get:Ignore
    val line: String get() = linea

    @get:Ignore
    val destination: String get() = destino
}

@Entity(tableName = "transit_cards")
data class TransitCardEntity(
    @PrimaryKey val cardUid: String,
    val alias: String,
    val cardType: String,
    val balance: Double = 0.0,
    val remainingTrips: Int = 0,
    val expiryDate: String = "",
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val defaultName: String = "Targeta",
    val remainingValue: String = "",
    val detailsJson: String = "{}"
) {
    @get:androidx.room.Ignore
    val cardNumber: String get() = cardUid

    @get:androidx.room.Ignore
    val assignedName: String get() = alias

    @Ignore
    constructor(
        cardNumber: String,
        assignedName: String,
        defaultName: String,
        cardType: String,
        remainingValue: String,
        detailsJson: String
    ) : this(
        cardUid = cardNumber,
        alias = assignedName,
        cardType = cardType,
        balance = 0.0,
        remainingTrips = 0,
        expiryDate = "",
        lastSyncTimestamp = System.currentTimeMillis(),
        defaultName = defaultName,
        remainingValue = remainingValue,
        detailsJson = detailsJson
    )
}

@Entity(tableName = "active_trips")
data class ActiveTripEntity(
    @PrimaryKey val tripId: String = ACTIVE_TRIP_ID,
    val originName: String = "",
    val destinationName: String = "",
    val routeDataJson: String = "",
    val status: String = STATUS_IN_PROGRESS,
    val currentLegIndex: Int = 0,
    val lastLegScheduledArrivalTimeMillis: Long = 0L,
    val startTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    @get:androidx.room.Ignore
    val startTime: Long get() = startTimestamp

    @get:androidx.room.Ignore
    val currentStepIndex: Int get() = currentLegIndex

    @get:androidx.room.Ignore
    val rawJsonData: String get() = routeDataJson

    companion object {
        const val ACTIVE_TRIP_ID = "single_active_trip"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_ACTIVE = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val EXPIRATION_GRACE_PERIOD_MILLIS = 30 * 60 * 1000L
    }
}

@Entity(tableName = "calendar_items")
data class CalendarItemEntity(
    @PrimaryKey val eventId: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startMillis: Long = 0L,
    val endMillis: Long = 0L,
    val itemType: String = "event", // "event", "task" etc
    val isCompleted: Boolean = false,
    val colorHex: String? = null,
    val isAllDay: Boolean = false,
    val isTransitRelevant: Boolean = true,
    val calendarEventId: String? = null,
    val dueMillis: Long = endMillis
) {
    @get:androidx.room.Ignore
    val id: String get() = eventId

    @get:androidx.room.Ignore
    val startTime: Long get() = startMillis

    @get:androidx.room.Ignore
    val endTime: Long get() = endMillis
}

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String
)

typealias PreferenceEntity = UserPreferenceEntity
