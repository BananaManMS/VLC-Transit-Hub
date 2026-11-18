package com.example.ui.map.google

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.ui.map.components.MarkerLogoBitmapCache
import com.example.util.LineColorResolver
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberMarkerState
import org.osmdroid.util.GeoPoint

private val ITIN_ROUND_CAP = RoundCap()
private val ITIN_WALK_COLOR = Color(0xFF64748B)
private val ITIN_FALLBACK_COLOR = Color(0xFF0284C7)
private val ITIN_WALK_PATTERN = listOf(Dash(20f), Gap(15f))

@Composable
internal fun GoogleMapItineraryLayer(
    itinerary: PlannedItinerary,
    settledZoom: Float,
    isDarkMode: Boolean,
    context: Context
) {
    // A. Route Polylines
    itinerary.legs.forEachIndexed { index, leg ->
        val pts = remember(itinerary.id, index, leg) {
            if (leg.geometry.isNotEmpty()) {
                leg.geometry.map { LatLng(it.latitude, it.longitude) }
            } else if (leg.intermediateStops.isNotEmpty()) {
                leg.intermediateStops.map { LatLng(it.lat, it.lon) }
            } else {
                val fromPt = LatLng(leg.fromLat, leg.fromLon)
                val toPt = LatLng(leg.toLat, leg.toLon)
                if (fromPt.latitude != 0.0 && toPt.latitude != 0.0) listOf(fromPt, toPt) else emptyList()
            }
        }

        if (pts.isNotEmpty()) {
            key("ITINERARY_LEG_${itinerary.id}_${index}_${leg.mode}") {
                if (leg.mode == TransitMode.WALK) {
                    Polyline(
                        points = pts,
                        color = ITIN_WALK_COLOR,
                        width = 11f,
                        pattern = ITIN_WALK_PATTERN,
                        jointType = JointType.ROUND,
                        startCap = ITIN_ROUND_CAP,
                        endCap = ITIN_ROUND_CAP,
                        zIndex = 22f
                    )
                } else {
                    val legColor = remember(leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName) {
                        val hexColorStr = LineColorResolver.resolveRouteColorHex(
                            leg.mode, leg.routeShortName, leg.routeColorHex, leg.agencyName
                        )
                        try { Color(MarkerLogoBitmapCache.parseColorCached(hexColorStr)) } catch (_: Exception) { ITIN_FALLBACK_COLOR }
                    }
                    Polyline(
                        points = pts,
                        color = legColor,
                        width = 14f,
                        jointType = JointType.ROUND,
                        startCap = ITIN_ROUND_CAP,
                        endCap = ITIN_ROUND_CAP,
                        zIndex = 23f
                    )
                }
            }
        }
    }

    // B. Intermediate Stop Dots with Text (Zoom >= 13.0)
    if (settledZoom >= 13.0f) {
        itinerary.legs.forEachIndexed { legIdx, leg ->
            if (leg.mode != TransitMode.WALK && leg.mode != TransitMode.BICYCLE) {
                leg.intermediateStops.forEachIndexed { stopIdx, stop ->
                    if (stop.lat in 38.0..41.0 && stop.lon in -2.0..1.0) {
                        val markerKey = "ITIN_STOP_${itinerary.id}_${legIdx}_$stopIdx"
                        key(markerKey) {
                            val stopIcon = remember(stop.name, settledZoom, isDarkMode) {
                                GoogleMapsMarkerCache.getOrCreateStopDotWithTextIcon(context, stop.name, settledZoom.toDouble(), isDarkMode)
                            }
                            Marker(
                                state = rememberMarkerState(key = markerKey, position = LatLng(stop.lat, stop.lon)),
                                icon = stopIcon.descriptor,
                                anchor = stopIcon.anchor,
                                title = stop.name,
                                snippet = stop.formattedTime,
                                zIndex = 24f
                            )
                        }
                    }
                }
                // Leg Destination Dot
                val endPt = leg.geometry.lastOrNull()
                    ?: leg.intermediateStops.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
                if (endPt != null && leg.toName.isNotBlank()) {
                    val markerKey = "ITIN_LEG_END_${itinerary.id}_$legIdx"
                    key(markerKey) {
                        val endIcon = remember(leg.toName, settledZoom, isDarkMode) {
                            GoogleMapsMarkerCache.getOrCreateStopDotWithTextIcon(context, leg.toName, settledZoom.toDouble(), isDarkMode)
                        }
                        Marker(
                            state = rememberMarkerState(key = markerKey, position = LatLng(endPt.latitude, endPt.longitude)),
                            icon = endIcon.descriptor,
                            anchor = endIcon.anchor,
                            title = leg.toName,
                            zIndex = 24f
                        )
                    }
                }
            }
        }
    }

    // C. Origin Marker
    val firstLeg = itinerary.legs.firstOrNull()
    val originPt = firstLeg?.geometry?.firstOrNull()
        ?: firstLeg?.intermediateStops?.firstOrNull()?.let { GeoPoint(it.lat, it.lon) }
        ?: firstLeg?.let { GeoPoint(it.fromLat, it.fromLon) }
    if (originPt != null && originPt.latitude != 0.0) {
        val originKey = "ITIN_ORIGIN_${itinerary.id}"
        key(originKey) {
            val originIcon = remember {
                GoogleMapsMarkerCache.getOrCreateOriginMarkerIcon(context)
            }
            Marker(
                state = rememberMarkerState(key = originKey, position = LatLng(originPt.latitude, originPt.longitude)),
                icon = originIcon.descriptor,
                anchor = originIcon.anchor,
                title = firstLeg?.fromName?.ifBlank { "Origen" } ?: "Origen",
                zIndex = 28f
            )
        }
    }

    // D. Transfer Markers
    for (i in 1 until itinerary.legs.size) {
        val leg = itinerary.legs[i]
        if (leg.mode == TransitMode.WALK || leg.mode == TransitMode.BICYCLE) continue

        var prevTransitLeg: PlannedLeg? = null
        for (j in i - 1 downTo 0) {
            if (itinerary.legs[j].mode != TransitMode.WALK && itinerary.legs[j].mode != TransitMode.BICYCLE) {
                prevTransitLeg = itinerary.legs[j]
                break
            }
        }

        val transferPt = leg.geometry.firstOrNull()
            ?: leg.intermediateStops.firstOrNull()?.let { GeoPoint(it.lat, it.lon) }
            ?: GeoPoint(leg.fromLat, leg.fromLon)

        if (transferPt.latitude != 0.0) {
            val isDirect = prevTransitLeg != null && run {
                val prevEnd = prevTransitLeg.geometry.lastOrNull()
                    ?: prevTransitLeg.intermediateStops.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
                if (prevEnd != null) {
                    val dLat = prevEnd.latitude - transferPt.latitude
                    val dLon = prevEnd.longitude - transferPt.longitude
                    (dLat * dLat + dLon * dLon) < 0.001 * 0.001
                } else false
            }

            val transferKey = "ITIN_TRANSFER_${itinerary.id}_$i"
            key(transferKey) {
                if (isDirect && prevTransitLeg != null) {
                    val stationName = leg.fromName.ifBlank { prevTransitLeg.toName }
                    val fromFmt = prevTransitLeg.routeShortName ?: prevTransitLeg.mode.displayNameEs
                    val toFmt = leg.routeShortName ?: leg.mode.displayNameEs
                    val transitionLabel = "$stationName ($fromFmt) ➔ ($toFmt)"
                    val transferIcon = remember(transitionLabel, settledZoom, isDarkMode) {
                        GoogleMapsMarkerCache.getOrCreateTransferLabelIcon(context, transitionLabel, settledZoom.toDouble(), isDarkMode)
                    }
                    Marker(
                        state = rememberMarkerState(key = transferKey, position = LatLng(transferPt.latitude, transferPt.longitude)),
                        icon = transferIcon.descriptor,
                        anchor = transferIcon.anchor,
                        title = transitionLabel,
                        snippet = "Transbordo directo",
                        zIndex = 27f
                    )
                } else {
                    val label = leg.routeShortName ?: leg.mode.displayNameEs
                    val transferIcon = remember(leg.routeColorHex, label) {
                        GoogleMapsMarkerCache.getOrCreateTransferMarkerIcon(context, leg.routeColorHex ?: "#0284C7", label)
                    }
                    Marker(
                        state = rememberMarkerState(key = transferKey, position = LatLng(transferPt.latitude, transferPt.longitude)),
                        icon = transferIcon.descriptor,
                        anchor = transferIcon.anchor,
                        title = leg.fromName.ifBlank { "Transbordo" },
                        snippet = "Transbordo • ${leg.mode.displayNameEs}",
                        zIndex = 27f
                    )
                }
            }
        }
    }

    // E. Destination Marker
    val lastLeg = itinerary.legs.lastOrNull()
    val destPt = lastLeg?.geometry?.lastOrNull()
        ?: lastLeg?.intermediateStops?.lastOrNull()?.let { GeoPoint(it.lat, it.lon) }
        ?: lastLeg?.let { GeoPoint(it.toLat, it.toLon) }
    if (destPt != null && destPt.latitude != 0.0) {
        val destKey = "ITIN_DEST_${itinerary.id}"
        key(destKey) {
            val destIcon = remember(isDarkMode) {
                GoogleMapsMarkerCache.getOrCreateDestinationMarkerIcon(context, isDarkMode)
            }
            Marker(
                state = rememberMarkerState(key = destKey, position = LatLng(destPt.latitude, destPt.longitude)),
                icon = destIcon.descriptor,
                anchor = destIcon.anchor,
                title = lastLeg?.toName?.ifBlank { "Destino" } ?: "Destino",
                zIndex = 28f
            )
        }
    }
}
