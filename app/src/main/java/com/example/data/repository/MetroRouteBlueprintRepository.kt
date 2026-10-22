package com.example.data.repository

import android.content.Context
import com.example.data.model.MetroRouteBlueprint
import com.example.data.model.MetroRouteBlueprintLeg
import com.example.data.model.MetroStation

class MetroRouteBlueprintRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: MetroRouteBlueprintRepository? = null

        fun getInstance(context: Context): MetroRouteBlueprintRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MetroRouteBlueprintRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getRouteBlueprint(origin: MetroStation, destination: MetroStation): MetroRouteBlueprint {
        if (origin.id == destination.id) {
            return MetroRouteBlueprint(
                originStationId = origin.id,
                originStationName = origin.name,
                destinationStationId = destination.id,
                destinationStationName = destination.name,
                totalTransfers = 0,
                isDirect = true
            )
        }

        // Check for shared lines between origin and destination
        val sharedLines = origin.lines.intersect(destination.lines.toSet())
        if (sharedLines.isNotEmpty()) {
            val primaryLine = sharedLines.first()
            val leg = MetroRouteBlueprintLeg(
                legIndex = 0,
                line = primaryLine,
                fromStationId = origin.id,
                fromStationName = origin.name,
                toStationId = destination.id,
                toStationName = destination.name
            )
            return MetroRouteBlueprint(
                originStationId = origin.id,
                originStationName = origin.name,
                destinationStationId = destination.id,
                destinationStationName = destination.name,
                totalTransfers = 0,
                isDirect = true,
                legs = listOf(leg)
            )
        }

        // If no direct line, find 1-transfer hub (e.g. Angel Guimera, Colon, Xativa, Empalme, Benimaclet)
        val transferHubs = listOf(
            MetroStation("7", "Àngel Guimerà", lines = listOf("1", "2", "3", "5", "9")),
            MetroStation("18", "Colón", lines = listOf("3", "5", "7", "9")),
            MetroStation("17", "Xàtiva", lines = listOf("3", "5", "9")),
            MetroStation("5", "Empalme", lines = listOf("1", "2", "4")),
            MetroStation("36", "Benimaclet", lines = listOf("3", "4", "6", "9"))
        )

        val hub = transferHubs.find { h ->
            origin.lines.any { h.lines.contains(it) } && destination.lines.any { h.lines.contains(it) }
        } ?: transferHubs.first()

        val line1 = origin.lines.firstOrNull { hub.lines.contains(it) } ?: origin.lines.firstOrNull() ?: "1"
        val line2 = destination.lines.firstOrNull { hub.lines.contains(it) } ?: destination.lines.firstOrNull() ?: "3"

        val leg1 = MetroRouteBlueprintLeg(
            legIndex = 0,
            line = line1,
            fromStationId = origin.id,
            fromStationName = origin.name,
            toStationId = hub.id,
            toStationName = hub.name,
            isTransferStation = true,
            transferStationName = hub.name
        )
        val leg2 = MetroRouteBlueprintLeg(
            legIndex = 1,
            line = line2,
            fromStationId = hub.id,
            fromStationName = hub.name,
            toStationId = destination.id,
            toStationName = destination.name
        )

        return MetroRouteBlueprint(
            originStationId = origin.id,
            originStationName = origin.name,
            destinationStationId = destination.id,
            destinationStationName = destination.name,
            totalTransfers = 1,
            isDirect = false,
            legs = listOf(leg1, leg2)
        )
    }
}
