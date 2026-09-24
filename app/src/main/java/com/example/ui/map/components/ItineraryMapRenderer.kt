package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.TransitMode

/**
 * Pure rendering delegate for active multimodal transit itineraries, routes,
 * polylines, origin/transfer/destination pins, intermediate stop halo dots, and bounding box auto-zoom.
 */
object ItineraryMapRenderer {

    // Helper function to format route name nicely (e.g. 3 -> L3 for subway, 1 -> C1 for rail)
    private fun formatRouteNameLocal(shortName: String?, mode: TransitMode): String {
        if (shortName.isNullOrBlank()) return mode.displayNameEs
        val trimmed = shortName.trim()
        if (trimmed.all { it.isDigit() }) {
            return when (mode) {
                TransitMode.SUBWAY, TransitMode.TRAM -> "L$trimmed"
                TransitMode.RAIL -> "C$trimmed"
                else -> trimmed
            }
        }
        return trimmed
    }

    // Helper function for local distance calculation
    private fun distanceMetersLocal(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latMid = (lat1 + lat2) / 2.0 * Math.PI / 180.0
        val dLat = (lat2 - lat1) * 111139.0
        val dLon = (lon2 - lon1) * 111139.0 * Math.cos(latMid)
        return Math.sqrt(dLat * dLat + dLon * dLon)
    }

    // Helper functions to safely extract start/end points of a leg
    private fun getLegStartPoint(l: com.example.data.model.routing.PlannedLeg): GeoPoint? {
        return l.geometry.firstOrNull()
            ?: l.intermediateStops.firstOrNull()?.let { GeoPoint(it.lat, it.lon) }
    }

    private fun getLegEndPoint(l: com.example.data.model.routing.PlannedLeg): GeoPoint? {
        return l.geometry.lastOrNull()
            ?: l.intermediateStops.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
    }

    /**
     * Renders an active multimodal itinerary on the MapView.
     * Returns the updated lastZoomedItineraryId.
     */
    fun renderItinerary(
        context: Context,
        mapView: MapView,
        itinerary: PlannedItinerary,
        currentZoom: Double,
        isDarkMode: Boolean,
        lastZoomedItineraryId: String?
    ): String? {
        var updatedZoomedId = lastZoomedItineraryId
        val allPoints = mutableListOf<GeoPoint>()

        itinerary.legs.forEach { leg ->
            val pts = if (leg.geometry.isNotEmpty()) {
                leg.geometry
            } else if (leg.intermediateStops.isNotEmpty()) {
                leg.intermediateStops.map { GeoPoint(it.lat, it.lon) }
            } else {
                val fromPt = GeoPoint(leg.fromLat, leg.fromLon)
                val toPt = GeoPoint(leg.toLat, leg.toLon)
                if (fromPt.latitude != 0.0 && toPt.latitude != 0.0) listOf(fromPt, toPt) else emptyList()
            }

            if (pts.isNotEmpty()) {
                allPoints.addAll(pts)
                val polyline = Polyline(mapView).apply {
                    setPoints(pts)
                    infoWindow = null
                    setOnClickListener { _, _, _ -> true }
                    if (leg.mode == TransitMode.WALK) {
                        outlinePaint.color = Color.parseColor("#64748B")
                        outlinePaint.alpha = 220
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 12f), 0f)
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    } else {
                        val hexColorStr = com.example.util.LineColorResolver.resolveRouteColorHex(
                            leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName
                        )
                        outlinePaint.color = Color.parseColor(hexColorStr)
                        outlinePaint.alpha = 255
                        outlinePaint.strokeWidth = 14f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                }
                mapView.overlays.add(polyline)
            }
        }

        // Draw intermediate stop white dots with smart halo text for transit legs (BUS, SUBWAY, TRAM, RAIL)
        if (currentZoom >= 13.0) {
            itinerary.legs.forEach { leg ->
                if (leg.mode != TransitMode.WALK && leg.mode != TransitMode.BICYCLE) {
                    // 1. Draw intermediate stops
                    leg.intermediateStops.forEach { stop ->
                        if (stop.lat in 38.0..41.0 && stop.lon in -2.0..1.0) {
                            val stopBitmap = createStopDotWithTextIcon(context, stop.name, currentZoom, isDarkMode)
                            val dotMarker = Marker(mapView).apply {
                                position = GeoPoint(stop.lat, stop.lon)
                                icon = android.graphics.drawable.BitmapDrawable(context.resources, stopBitmap)

                                val density = context.resources.displayMetrics.density
                                val dotRadius = 4f * density
                                val borderSize = 1.2f * density
                                setAnchor((dotRadius + borderSize) / stopBitmap.width.toFloat(), 0.5f)

                                title = stop.name
                                subDescription = stop.formattedTime
                                infoWindow = null
                                setOnMarkerClickListener { _, _ -> true }
                            }
                            mapView.overlays.add(dotMarker)
                        }
                    }
                    // 2. Draw final station of this transit leg (equally important)
                    val endPt = getLegEndPoint(leg)
                    if (endPt != null && endPt.latitude in 38.0..41.0 && endPt.longitude in -2.0..1.0 && leg.toName.isNotBlank()) {
                        val stopBitmap = createStopDotWithTextIcon(context, leg.toName, currentZoom, isDarkMode)
                        val dotMarker = Marker(mapView).apply {
                            position = endPt
                            icon = android.graphics.drawable.BitmapDrawable(context.resources, stopBitmap)
                            val density = context.resources.displayMetrics.density
                            val dotRadius = 4f * density
                            val borderSize = 1.2f * density
                            setAnchor((dotRadius + borderSize) / stopBitmap.width.toFloat(), 0.5f)
                            title = leg.toName
                            infoWindow = null
                            setOnMarkerClickListener { _, _ -> true }
                        }
                        mapView.overlays.add(dotMarker)
                    }
                }
            }
        }

        // Draw Origin, Destination, and Transfer markers for the route
        if (itinerary.legs.isNotEmpty()) {
            // 1. Origin Marker
            val firstLeg = itinerary.legs.first()
            val originPt = getLegStartPoint(firstLeg)
            if (originPt != null) {
                val originResult = getOriginMarkerIcon(context)
                val originMarker = Marker(mapView).apply {
                    position = originPt
                    title = firstLeg.fromName.ifBlank { "Origen" }
                    snippet = "Punto de origen"
                    icon = originResult.drawable
                    setAnchor(originResult.anchorU, originResult.anchorV)
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> true }
                }
                mapView.overlays.add(originMarker)
            }

            // 2. Transfer / Connection Markers
            for (i in 1 until itinerary.legs.size) {
                val leg = itinerary.legs[i]

                // Skip if the current leg is WALK (redundant walking label filtered out)
                if (leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE) continue

                // Look back for the previous active transit leg to detect direct transfers
                var prevTransitLeg: com.example.data.model.routing.PlannedLeg? = null
                for (j in i - 1 downTo 0) {
                    if (itinerary.legs[j].mode != TransitMode.WALK && itinerary.legs[j].mode != TransitMode.BICYCLE) {
                        prevTransitLeg = itinerary.legs[j]
                        break
                    }
                }

                val transferPt = getLegStartPoint(leg)
                if (transferPt != null) {
                    val isDirectTransfer = if (prevTransitLeg != null) {
                        val prevEnd = getLegEndPoint(prevTransitLeg)
                        val currStart = getLegStartPoint(leg)
                        if (prevEnd != null && currStart != null) {
                            val dist = distanceMetersLocal(prevEnd.latitude, prevEnd.longitude, currStart.latitude, currStart.longitude)
                            dist < 100.0 // extremely close stations -> direct in-station transfer
                        } else {
                            false
                        }
                    } else {
                        false
                    }

                    if (isDirectTransfer && prevTransitLeg != null) {
                        val stationName = leg.fromName.ifBlank { prevTransitLeg.toName }
                        val fromFormatted = formatRouteNameLocal(prevTransitLeg.routeShortName, prevTransitLeg.mode)
                        val toFormatted = formatRouteNameLocal(leg.routeShortName, leg.mode)
                        val transitionLabel = "$stationName ($fromFormatted) ➔ ($toFormatted)"

                        val transferBitmap = createTransferLabelIcon(context, transitionLabel, currentZoom, isDarkMode)
                        val transferMarker = Marker(mapView).apply {
                            position = transferPt
                            icon = android.graphics.drawable.BitmapDrawable(context.resources, transferBitmap)
                            val density = context.resources.displayMetrics.density
                            val dotRadius = 5.5f * density
                            val borderSize = 1.5f * density
                            setAnchor((dotRadius + borderSize) / transferBitmap.width.toFloat(), 0.5f)
                            title = transitionLabel
                            snippet = "Transbordo directo"
                            infoWindow = null
                            setOnMarkerClickListener { _, _ -> true }
                        }
                        mapView.overlays.add(transferMarker)
                    } else {
                        val label = leg.routeShortName ?: leg.mode.displayNameEs
                        val transferResult = getTransferMarkerIcon(context, leg.routeColorHex, label)
                        val transferMarker = Marker(mapView).apply {
                            position = transferPt
                            title = leg.fromName.ifBlank { "Transbordo" }
                            snippet = "Transbordo • ${leg.mode.displayNameEs}"
                            icon = transferResult.drawable
                            setAnchor(transferResult.anchorU, transferResult.anchorV)
                            infoWindow = null
                            setOnMarkerClickListener { _, _ -> true }
                        }
                        mapView.overlays.add(transferMarker)
                    }
                }
            }

            // 3. Destination Marker
            val lastLeg = itinerary.legs.last()
            val destPt = lastLeg.geometry.lastOrNull()
                ?: lastLeg.intermediateStops.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
            if (destPt != null) {
                val destResult = getDestinationMarkerIcon(context, isDarkMode)
                val routeDestMarker = Marker(mapView).apply {
                    position = destPt
                    title = lastLeg.toName.ifBlank { "Destino" }
                    snippet = "Llegada al destino"
                    icon = destResult.drawable
                    setAnchor(destResult.anchorU, destResult.anchorV)
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> true }
                }
                mapView.overlays.add(routeDestMarker)
            }
        }

        // Auto-fit bounding box to entire route ONLY the first time an itinerary is loaded or selected
        if (allPoints.isNotEmpty() && lastZoomedItineraryId != itinerary.id) {
            updatedZoomedId = itinerary.id
            val minLat = allPoints.minOf { it.latitude }
            val maxLat = allPoints.maxOf { it.latitude }
            val minLon = allPoints.minOf { it.longitude }
            val maxLon = allPoints.maxOf { it.longitude }
            if (maxLat - minLat > 0.0001 && maxLon - minLon > 0.0001) {
                val box = org.osmdroid.util.BoundingBox(maxLat + 0.002, maxLon + 0.002, minLat - 0.002, minLon - 0.002)
                mapView.post {
                    try {
                        mapView.zoomToBoundingBox(box, true, 80)
                    } catch (e: Exception) {
                        android.util.Log.w("ItineraryMapRenderer", "Could not zoom to itinerary bounding box", e)
                    }
                }
            }
        }

        return updatedZoomedId
    }
}
