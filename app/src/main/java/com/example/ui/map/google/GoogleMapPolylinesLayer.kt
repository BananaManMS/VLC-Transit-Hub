package com.example.ui.map.google

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.example.ui.map.components.MetroStationHighlightManager
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberMarkerState

@Composable
internal fun GoogleMapPolylinesLayer(
    showCercaniasLines: Boolean,
    showMetroLines: Boolean,
    cercaniasPolylineList: List<GoogleMapPolyline>,
    metroPolylineList: List<GoogleMapPolyline>,
    busPolylineList: List<GoogleMapPolyline>,
    metrobusPolylineList: List<GoogleMapPolyline>,
    routeChevronArrows: List<RouteChevronArrow>,
    activeCercaniasLines: Set<String>,
    activeMetroLines: Set<String>,
    isCercaniasActive: Boolean,
    isMetroActive: Boolean,
    isBusActive: Boolean,
    context: Context
) {
    // 1. Draw Cercanías Line Polylines (zIndex = 2f dimmed / 10f normal / 16f highlighted)
    if (showCercaniasLines) {
        cercaniasPolylineList.forEachIndexed { index, line ->
            key("CERCANIAS_LINE_${index}_${line.lineRef}") {
                val isHighlighted = activeCercaniasLines.isNotEmpty() &&
                        activeCercaniasLines.contains(line.lineRef.trim().uppercase())
                val isDimmed = (isCercaniasActive && !isHighlighted) || (isMetroActive && !isHighlighted) || (isBusActive && !isHighlighted)

                val effectiveColor = if (isDimmed) line.color.copy(alpha = 0.20f) else line.color
                val effectiveWidth = if (isHighlighted) line.width * 1.3f else if (isDimmed) line.width * 0.85f else line.width
                val effectiveZIndex = if (isHighlighted) 16f else if (isDimmed) 2f else 10f

                Polyline(
                    points = line.points,
                    color = effectiveColor,
                    width = effectiveWidth,
                    zIndex = effectiveZIndex,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap()
                )
            }
        }
    }

    // 2. Draw Metro Line Polylines (zIndex = 2f dimmed / 12f normal / 17f highlighted)
    if (showMetroLines) {
        metroPolylineList.forEachIndexed { index, line ->
            key("METRO_LINE_${index}_${line.lineRef}") {
                val cleanRef = MetroStationHighlightManager.normalizeLineRef(line.lineRef)
                val isHighlighted = activeMetroLines.isNotEmpty() && activeMetroLines.contains(cleanRef)
                val isDimmed = (isMetroActive && !isHighlighted) || (isCercaniasActive && !isHighlighted) || (isBusActive && !isHighlighted)

                val effectiveColor = if (isDimmed) line.color.copy(alpha = 0.20f) else line.color
                val effectiveWidth = if (isHighlighted) line.width * 1.3f else if (isDimmed) line.width * 0.85f else line.width
                val effectiveZIndex = if (isHighlighted) 17f else if (isDimmed) 2f else 12f

                Polyline(
                    points = line.points,
                    color = effectiveColor,
                    width = effectiveWidth,
                    zIndex = effectiveZIndex,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap()
                )
            }
        }
    }

    // 3. Draw EMT Bus Line Polylines (zIndex = 18f)
    if (busPolylineList.isNotEmpty()) {
        busPolylineList.forEachIndexed { index, line ->
            key("BUS_LINE_${index}_${line.lineRef}") {
                Polyline(
                    points = line.points,
                    color = line.color,
                    width = line.width,
                    zIndex = 18f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap()
                )
            }
        }
    }

    // 4. Draw Metrobús Bus Line Polylines (zIndex = 18f)
    if (metrobusPolylineList.isNotEmpty()) {
        metrobusPolylineList.forEachIndexed { index, line ->
            key("METROBUS_LINE_${index}_${line.lineRef}") {
                Polyline(
                    points = line.points,
                    color = line.color,
                    width = line.width,
                    zIndex = 18f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap()
                )
            }
        }
    }

    // 5. Draw Directional Chevron Arrows along isolated bus route (zIndex = 20f)
    if (routeChevronArrows.isNotEmpty()) {
        routeChevronArrows.forEachIndexed { idx, arrow ->
            key("CHEVRON_${idx}_${arrow.position.latitude}_${arrow.position.longitude}") {
                val chevronIcon = remember(arrow.isMetrobus) {
                    val borderColor = if (arrow.isMetrobus) AndroidColor.parseColor("#F97316") else AndroidColor.parseColor("#E52320")
                    GoogleMapsMarkerCache.getOrCreateChevronArrowIcon(context, borderColor)
                }
                Marker(
                    state = rememberMarkerState(
                        key = "CHEVRON_${idx}_${arrow.position.latitude}_${arrow.position.longitude}",
                        position = arrow.position
                    ),
                    icon = chevronIcon.descriptor,
                    anchor = chevronIcon.anchor,
                    flat = true,
                    rotation = arrow.bearing,
                    zIndex = 20f
                )
            }
        }
    }
}
