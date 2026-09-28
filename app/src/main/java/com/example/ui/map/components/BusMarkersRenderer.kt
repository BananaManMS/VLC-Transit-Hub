package com.example.ui.map.components

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.ui.bus.EmtBusStop
import com.example.ui.bus.EmtRoute
import com.example.ui.bus.MetrobusStop
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.spatial.SpatialGridSampler
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.example.util.LocationUtils

/**
 * Pure rendering delegate for EMT Bus and Metrobús stops and clusters.
 * Leverages SpatialGridSampler for LOD density reduction and centralized recycled markers.
 * Ensures selected bus stops are always rendered with their full detailed pill marker at any zoom.
 */
object BusMarkersRenderer {

    private val normalizedNameCache = LruCache<String, String>(1024)
    private val mergeMatchCache = LruCache<String, Boolean>(2048)

    data class MergedBusStopGroup(
        val busStop: GeoportalStopEntity,
        val mbStop: MetrobusStopEntity
    )

    data class RenderResult(
        val activeMarkerCount: Int,
        val activeClusterCount: Int
    )

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = 1 + minOf(prev, dp[j], dp[j - 1])
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    private fun normalizeStopTokens(name: String): String {
        val cached = normalizedNameCache.get(name)
        if (cached != null) return cached

        val accents = mapOf(
            'Á' to 'A', 'À' to 'A', 'Ä' to 'A',
            'É' to 'E', 'È' to 'E', 'Ë' to 'E',
            'Í' to 'I', 'Ì' to 'I', 'Ï' to 'I',
            'Ó' to 'O', 'Ò' to 'O', 'Ö' to 'O',
            'Ú' to 'U', 'Ù' to 'U', 'Ü' to 'U',
            'Ç' to 'C'
        )
        val sb = StringBuilder()
        for (ch in name.uppercase()) {
            sb.append(accents[ch] ?: ch)
        }
        val result = sb.toString()
            .replace("AVENIDA", "AV")
            .replace("AVDA", "AV")
            .replace("PLAZA", "PZA")
            .replace("PLAÇA", "PZA")
            .replace("ESTACION", "ESTACIO")
            .replace("EST.", "ESTACIO")
            .replace("PASSEIG", "PASEO")
            .replace("PSO", "PASEO")
            .replace("CARRETERA", "CTRA")
            .replace("DOCTOR", "DR")
            .replace("DR.", "DR")
            .replace("CARRER", "C")
            .replace("CALLE", "C")
            .replace("SANT", "SAN")
            .replace("SANTA", "SAN")
            .replace("STA.", "SAN")
            .replace("ST.", "SAN")
            .replace("INSTITUT", "INSTITUT")
            .replace("INSTITUTO", "INSTITUT")
            .replace("INSTITUC", "INSTITUT")
            .replace("HOSPITAL", "HOSPITAL")
            .replace("POLITECNIC", "POLITECNIC")
            .replace("POLITECNICO", "POLITECNIC")
            .replace("[^A-Z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        normalizedNameCache.put(name, result)
        return result
    }

    private val genericAvenueWords = setOf(
        "AV", "AVDA", "AVENIDA", "VALENCIA", "C", "CALLE", "CARRER", "PASEO",
        "PASSEIG", "PZA", "PLAZA", "PLAÇA", "CTRA", "CARRETERA", "SAN", "SANT",
        "SANTA", "DE", "DEL", "LA", "EL", "LOS", "LAS", "EN", "A", "POR", "Y",
        "CID", "BLASCO", "IBANEZ", "CERRITO", "CENTRO"
    )

    private data class ParsedStopComponents(
        val primaryNorm: String,
        val secondaryNorm: String?,
        val houseNumber: Int?
    )

    private fun parseStopComponents(rawName: String): ParsedStopComponents {
        val accents = mapOf(
            'Á' to 'A', 'À' to 'A', 'Ä' to 'A',
            'É' to 'E', 'È' to 'E', 'Ë' to 'E',
            'Í' to 'I', 'Ì' to 'I', 'Ï' to 'I',
            'Ó' to 'O', 'Ò' to 'O', 'Ö' to 'O',
            'Ú' to 'U', 'Ù' to 'U', 'Ü' to 'U',
            'Ç' to 'C'
        )
        val upper = rawName.uppercase().map { accents[it] ?: it }.joinToString("")

        val numMatch = Regex("\\b(\\d{1,3})\\b").find(upper)
        val houseNumber = numMatch?.groupValues?.get(1)?.toIntOrNull()

        val parts = upper.split(Regex("[-/,]")).map { it.trim() }.filter { it.isNotBlank() }

        val primaryRaw = if (parts.isNotEmpty()) parts[0] else upper
        val secondaryRaw = if (parts.size > 1) parts.subList(1, parts.size).joinToString(" ") else null

        val primaryNorm = normalizeStopTokens(primaryRaw)
            .replace(Regex("\\b\\d{1,3}\\b"), "")
            .trim()

        val secondaryNorm = if (secondaryRaw != null) {
            val norm = normalizeStopTokens(secondaryRaw)
                .replace(Regex("\\b(ANDEN|ANDAN|PARADA|MARQUESINA|POSTE|PLATFORM|BUS|METROBUS|MB|EMT)\\b"), "")
                .trim()
            norm.ifBlank { null }
        } else {
            null
        }

        return ParsedStopComponents(
            primaryNorm = primaryNorm,
            secondaryNorm = secondaryNorm,
            houseNumber = houseNumber
        )
    }

    private fun areStopNamesSimilar(name1: String, name2: String, distMeters: Double): Boolean {
        val norm1 = normalizeStopTokens(name1)
        val norm2 = normalizeStopTokens(name2)
        if (norm1.isBlank() || norm2.isBlank()) return false

        // 1. Exact match
        if (norm1 == norm2) return true

        val s1 = norm1.replace(" ", "")
        val s2 = norm2.replace(" ", "")
        if (s1 == s2) return true

        // 2. Parse structural parts (Primary street/location, secondary modifier/cross-street/number)
        val parsed1 = parseStopComponents(name1)
        val parsed2 = parseStopComponents(name2)

        val primary1Norm = parsed1.primaryNorm
        val primary2Norm = parsed2.primaryNorm

        if (primary1Norm.isNotBlank() && primary2Norm.isNotBlank()) {
            val primaryLev = calculateLevenshteinDistance(primary1Norm.replace(" ", ""), primary2Norm.replace(" ", ""))
            val maxPrimaryLen = maxOf(primary1Norm.length, primary2Norm.length)
            val primaryMatch = primary1Norm == primary2Norm ||
                    (primaryLev <= 2 && maxPrimaryLen > 5)
            if (!primaryMatch) {
                return false
            }
        }

        // 3. Secondary modifier and house number checks
        val sec1 = parsed1.secondaryNorm
        val sec2 = parsed2.secondaryNorm
        val num1 = parsed1.houseNumber
        val num2 = parsed2.houseNumber

        if (num1 != null && num2 != null) {
            val numDiff = Math.abs(num1 - num2)
            if (numDiff > 2) {
                return false
            }
        }

        if (sec1 != null && sec2 != null) {
            val sec1Words = sec1.split(" ").filter { it.length > 1 }
            val sec2Words = sec2.split(" ").filter { it.length > 1 }

            val secOverlap = sec1Words.count { w1 ->
                sec2Words.any { w2 ->
                    w1 == w2 || (w1.length >= 4 && w2.length >= 4 && calculateLevenshteinDistance(w1, w2) <= 1)
                }
            }

            if (secOverlap == 0) {
                return false
            }
        }

        if (sec1 != null && num2 != null) {
            if (!sec1.contains(num2.toString())) {
                return false
            }
        }
        if (sec2 != null && num1 != null) {
            if (!sec2.contains(num1.toString())) {
                return false
            }
        }

        if ((sec1 != null && sec2 == null) || (sec2 != null && sec1 == null)) {
            if (distMeters > 25.0) {
                return false
            }
        }

        if (distMeters <= 55.0) {
            return true
        }

        return false
    }

    private fun normalizeStopName(name: String): String {
        return normalizeStopTokens(name)
    }

    fun findMergedStopGroups(
        busStopsInViewport: List<GeoportalStopEntity>,
        metrobusStopsInViewport: List<MetrobusStopEntity>,
        selectedMapItem: SelectedMapItem?,
        showBus: Boolean,
        showMetrobus: Boolean,
        isFavoritesMode: Boolean = false,
        currentZoom: Double = 18.0
    ): List<MergedBusStopGroup> {
        if ((!showBus && !showMetrobus) || isFavoritesMode || (currentZoom < 13.0 && selectedMapItem == null)) return emptyList()

        val selectedBusStopId = (selectedMapItem as? SelectedMapItem.BusStop)?.stop?.id_parada
        val selectedMbStopId = (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop?.id_parada

        val result = mutableListOf<MergedBusStopGroup>()
        val matchedMbIds = mutableSetOf<String>()

        for (busStop in busStopsInViewport) {
            // When an individual EMT stop is selected, do not merge it into a joint badge
            if (selectedBusStopId != null && busStop.id_parada == selectedBusStopId) continue

            var bestCandidate: MetrobusStopEntity? = null
            var bestDistance = Double.MAX_VALUE

            for (mb in metrobusStopsInViewport) {
                if (matchedMbIds.contains(mb.id_parada)) continue
                // When an individual Metrobús stop is selected, do not merge it into a joint badge
                if (selectedMbStopId != null && mb.id_parada == selectedMbStopId) continue

                // Fast bounding-box pre-check: 85 meters in Valencia latitude is ~0.0008 deg lat, ~0.0011 deg lon
                val dLat = Math.abs(busStop.lat - mb.lat)
                if (dLat > 0.0009) continue
                val dLon = Math.abs(busStop.lon - mb.lon)
                if (dLon > 0.0012) continue

                val cacheKey = "${busStop.id_parada}_${mb.id_parada}"
                val cachedMatch = mergeMatchCache.get(cacheKey)

                val isMatch = if (cachedMatch != null) {
                    cachedMatch
                } else {
                    val distMeters = LocationUtils.calculateDistanceMeters(busStop.lat, busStop.lon, mb.lat, mb.lon)
                    val match = if (distMeters <= 85.0) {
                        areStopNamesSimilar(busStop.denominacion, mb.denominacion, distMeters)
                    } else {
                        false
                    }
                    mergeMatchCache.put(cacheKey, match)
                    match
                }

                if (isMatch) {
                    val distMeters = LocationUtils.calculateDistanceMeters(busStop.lat, busStop.lon, mb.lat, mb.lon)
                    if (distMeters < bestDistance) {
                        bestDistance = distMeters
                        bestCandidate = mb
                    }
                }
            }

            if (bestCandidate != null) {
                matchedMbIds.add(bestCandidate.id_parada)
                result.add(MergedBusStopGroup(busStop = busStop, mbStop = bestCandidate))
            }
        }
        return result
    }

    fun renderMergedBusStops(
        context: Context,
        mapView: MapView,
        mergedGroups: List<MergedBusStopGroup>,
        isDarkMode: Boolean,
        currentZoom: Double,
        showBus: Boolean,
        showMetrobus: Boolean,
        isOnlyBusOrMbSelected: Boolean,
        isFavoritesMode: Boolean,
        recycledMergedMarkers: MutableList<Marker>,
        selectedMapItem: SelectedMapItem? = null,
        selectedBusLineFilters: Set<String> = emptySet(),
        labelPositions: Map<String, String> = emptyMap(),
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onShowDisambiguationMenu: ((List<SelectedMapItem>) -> Unit)? = null,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): Int {
        var activeCount = 0

        val emtHighlight = EmtStationHighlightManager.getHighlightState(selectedMapItem, selectedBusLineFilters)
        val mbHighlight = MetrobusStationHighlightManager.getHighlightState(selectedMapItem, selectedBusLineFilters)

        val shouldRenderAny = (showBus || showMetrobus) && mergedGroups.isNotEmpty() && (
            isFavoritesMode || currentZoom >= 13.5 || emtHighlight.isHighlighted || mbHighlight.isHighlighted
        )

        if (shouldRenderAny) {
            mergedGroups.forEach { group ->
                val busStop = group.busStop
                val mbStop = group.mbStop

                val markerAlpha = when {
                    emtHighlight.isHighlighted -> {
                        val stopLines = busStop.lineas?.split(",")?.map { EmtMapOverlayLoader.normalizeLine(it) } ?: emptyList()
                        val mbLines = mbStop.lineas?.split(",")?.map { MetrobusStationHighlightManager.normalizeLine(it) } ?: emptyList()
                        val hasEmtMatch = stopLines.any { emtHighlight.effectiveLinesToDraw.contains(it) }
                        val hasMbMatch = mbLines.any { emtHighlight.effectiveLinesToDraw.contains(it) }
                        if (hasEmtMatch || hasMbMatch) 1.0f else 0.25f
                    }
                    mbHighlight.isHighlighted -> {
                        val stopLines = busStop.lineas?.split(",")?.map { EmtMapOverlayLoader.normalizeLine(it) } ?: emptyList()
                        val mbLines = mbStop.lineas?.split(",")?.map { MetrobusStationHighlightManager.normalizeLine(it) } ?: emptyList()
                        val hasEmtMatch = stopLines.any { mbHighlight.effectiveLines.contains(it) }
                        val hasMbMatch = mbLines.any { mbHighlight.effectiveLines.contains(it) }
                        if (hasEmtMatch || hasMbMatch) 1.0f else 0.25f
                    }
                    selectedMapItem is SelectedMapItem.Metro || selectedMapItem is SelectedMapItem.Cercanias -> 0.25f
                    else -> 1.0f
                }

                // Skip rendering individual merged badge if zoomed out and not favorites mode / not on highlighted route
                val isConnectedToRoute = (emtHighlight.isHighlighted || mbHighlight.isHighlighted) && markerAlpha == 1.0f
                if (currentZoom < 13.5 && !isFavoritesMode && !isConnectedToRoute) {
                    return@forEach
                }

                val avgLat = (busStop.lat + mbStop.lat) / 2.0
                val avgLon = (busStop.lon + mbStop.lon) / 2.0
                val pos = GeoPoint(avgLat, avgLon)
                val markerKey = "MERGED_${busStop.id_parada}_${mbStop.id_parada}"

                val marker = if (activeCount < recycledMergedMarkers.size) {
                    recycledMergedMarkers[activeCount]
                } else {
                    Marker(mapView).also {
                        recycledMergedMarkers.add(it)
                        mapView.overlays.add(it)
                    }
                }
                marker.id = markerKey
                activeCount++

                val stopName = if (busStop.denominacion.isNotBlank()) busStop.denominacion else mbStop.denominacion

                if (currentZoom >= 17.8) {
                    val detailedResult = getMergedDetailedMarkerIcon(
                        context = context,
                        stopName = stopName,
                        emtLinesString = busStop.lineas,
                        metrobusLinesString = mbStop.lineas,
                        isDarkMode = isDarkMode,
                        labelPosition = labelPositions[markerKey] ?: "BELOW"
                    )
                    marker.icon = detailedResult.drawable
                    marker.setAnchor(detailedResult.anchorU, detailedResult.anchorV)
                } else if (currentZoom < 15.2) {
                    val dotDrawable = getMergedDotMarkerIcon(context)
                    marker.icon = dotDrawable
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                } else {
                    val compactDrawable = getMergedCompactMarkerIcon(context, isDarkMode)
                    marker.icon = compactDrawable
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }

                marker.infoWindow = null
                marker.closeInfoWindow()
                marker.alpha = markerAlpha
                marker.position = pos
                marker.title = "$stopName (EMT + Metrobús)"
                marker.snippet = "Toca para seleccionar parada"
                marker.subDescription = "LINES_EMT:${busStop.lineas ?: ""}|LINES_MB:${mbStop.lineas ?: ""}"
                marker.isEnabled = true
                marker.setVisible(true)

                marker.setOnMarkerClickListener { _, _ ->
                    onTapHandler.invoke(context, mapView, pos)
                    true
                }
            }
        }

        for (i in activeCount until recycledMergedMarkers.size) {
            recycledMergedMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        return activeCount
    }

    /**
     * Renders EMT Bus stops and clusters depending on zoom LOD.
     */
    fun renderBusStops(
        context: Context,
        mapView: MapView,
        busStopsInViewport: List<GeoportalStopEntity>,
        isFavoritesMode: Boolean,
        isOnlyBusSelected: Boolean = false,
        isDarkMode: Boolean = false,
        currentZoom: Double,
        showBus: Boolean,
        busStopAliases: Map<String, String>,
        selectedMapItem: SelectedMapItem? = null,
        selectedBusLineFilters: Set<String> = emptySet(),
        recycledBusMarkers: MutableList<Marker>,
        recycledClusterMarkers: MutableList<Marker>,
        labelPositions: Map<String, String> = emptyMap(),
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): RenderResult {
        var activeBusCount = 0
        var activeClusterCount = 0

        val busDrawable = getMarkerIcon(context, "BUS", Color.parseColor("#E53935"))
        val busDotDrawable = getCompactDotIcon(context, Color.parseColor("#E53935"))

        val selectedBusStop = (selectedMapItem as? SelectedMapItem.BusStop)?.stop
        val selectedStopId = selectedBusStop?.id_parada
        var selectedStopRendered = false

        val emtHighlight = EmtStationHighlightManager.getHighlightState(selectedMapItem, selectedBusLineFilters)
        val mbHighlight = MetrobusStationHighlightManager.getHighlightState(selectedMapItem, selectedBusLineFilters)

        fun getEmtStopAlpha(stop: GeoportalStopEntity): Float {
            return when {
                selectedStopId != null && stop.id_parada == selectedStopId -> 1.0f
                emtHighlight.isHighlighted -> EmtStationHighlightManager.getBusMarkerAlpha(stop, emtHighlight, 1.0f)
                mbHighlight.isHighlighted -> 0.25f
                selectedMapItem is SelectedMapItem.Metro || selectedMapItem is SelectedMapItem.Cercanias -> 0.25f
                else -> 1.0f
            }
        }

        if (showBus && busStopsInViewport.isNotEmpty()) {
            if (isFavoritesMode) {
                busStopsInViewport.forEach { stop ->
                    val isSelected = selectedStopId != null && stop.id_parada == selectedStopId
                    if (isSelected) selectedStopRendered = true
                    val alpha = getEmtStopAlpha(stop)
                    activeBusCount = renderSingleBusMarker(
                        context = context,
                        mapView = mapView,
                        stop = stop,
                        isSelected = isSelected,
                        isDarkMode = isDarkMode,
                        currentZoom = currentZoom,
                        busDrawable = busDrawable,
                        busDotDrawable = busDotDrawable,
                        useDotIcon = false,
                        busStopAliases = busStopAliases,
                        recycledBusMarkers = recycledBusMarkers,
                        activeBusIndex = activeBusCount,
                        alpha = alpha,
                        labelPosition = labelPositions["BUS_" + stop.id_parada] ?: "BELOW",
                        onSelectItem = onSelectItem,
                        onTapHandler = onTapHandler
                    )
                }
            } else if (emtHighlight.isHighlighted && currentZoom < 14.0) {
                // When an EMT route is highlighted at wide zoom, render all route stops clearly as dots without cluttering clusters
                val routeStops = busStopsInViewport.filter { getEmtStopAlpha(it) == 1.0f }
                routeStops.forEach { stop ->
                    val isSelected = selectedStopId != null && stop.id_parada == selectedStopId
                    if (isSelected) selectedStopRendered = true
                    activeBusCount = renderSingleBusMarker(
                        context = context,
                        mapView = mapView,
                        stop = stop,
                        isSelected = isSelected,
                        isDarkMode = isDarkMode,
                        currentZoom = currentZoom,
                        busDrawable = busDrawable,
                        busDotDrawable = busDotDrawable,
                        useDotIcon = !isSelected,
                        busStopAliases = busStopAliases,
                        recycledBusMarkers = recycledBusMarkers,
                        activeBusIndex = activeBusCount,
                        alpha = 1.0f,
                        labelPosition = labelPositions["BUS_" + stop.id_parada] ?: "BELOW",
                        onSelectItem = onSelectItem,
                        onTapHandler = onTapHandler
                    )
                }
            } else if (mbHighlight.isHighlighted && currentZoom < 14.5) {
                // Metrobús line selected & zoomed out -> suppress EMT markers to emphasize Metrobús route
            } else {
                when {
                    // LOD 1: ZOOM < 13.5 -> Radius Grid Clustering
                    currentZoom < 13.5 -> {
                        val gridSize = if (currentZoom < 11.0) 0.06 else 0.025
                        val (selectedList, nonSelectedStops) = if (selectedStopId != null) {
                            busStopsInViewport.partition { it.id_parada == selectedStopId }
                        } else {
                            Pair(emptyList(), busStopsInViewport)
                        }

                        // Render selected stop first as prominent detailed pin
                        selectedList.forEach { stop ->
                            selectedStopRendered = true
                            activeBusCount = renderSingleBusMarker(
                                context = context,
                                mapView = mapView,
                                stop = stop,
                                isSelected = true,
                                isDarkMode = isDarkMode,
                                currentZoom = currentZoom,
                                busDrawable = busDrawable,
                                busDotDrawable = busDotDrawable,
                                useDotIcon = false,
                                busStopAliases = busStopAliases,
                                recycledBusMarkers = recycledBusMarkers,
                                activeBusIndex = activeBusCount,
                                alpha = 1.0f,
                                labelPosition = labelPositions["BUS_" + stop.id_parada] ?: "BELOW",
                                onSelectItem = onSelectItem,
                                onTapHandler = onTapHandler
                            )
                        }

                        // Only draw cluster circles if no specific transit route is highlighted
                        if (!emtHighlight.isHighlighted && !mbHighlight.isHighlighted) {
                            val clusters = nonSelectedStops.groupBy { stop ->
                                val gridX = (stop.lat / gridSize).toInt()
                                val gridY = (stop.lon / gridSize).toInt()
                                Pair(gridX, gridY)
                            }

                            clusters.values.forEach { group ->
                                val avgLat = group.map { it.lat }.average()
                                val avgLon = group.map { it.lon }.average()
                                val count = group.size

                                val clusterMarker = if (activeClusterCount < recycledClusterMarkers.size) {
                                    recycledClusterMarkers[activeClusterCount]
                                } else {
                                    Marker(mapView).also {
                                        recycledClusterMarkers.add(it)
                                        mapView.overlays.add(it)
                                    }
                                }
                                activeClusterCount++

                                clusterMarker.infoWindow = null
                                clusterMarker.closeInfoWindow()
                                clusterMarker.position = GeoPoint(avgLat, avgLon)
                                clusterMarker.icon = getClusterIcon(context, count, Color.parseColor("#E53935"))
                                clusterMarker.title = "$count Paradas de EMT Bus"
                                clusterMarker.snippet = "Toca para ampliar área"
                                clusterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                clusterMarker.isEnabled = true
                                clusterMarker.setVisible(true)
                                clusterMarker.setOnMarkerClickListener { _, _ ->
                                    if (showBus && clusterMarker.isEnabled) {
                                        mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                                        mapView.controller.zoomTo(currentZoom + 2.2)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                        }
                    }

                    // LOD 2: 13.5 <= ZOOM < 15.2 -> Compact Dot Icons (Selected stop gets full pill)
                    currentZoom in 13.5..<15.2 -> {
                        val stopsToRender = SpatialGridSampler.sampleUniformlyByGrid(
                            items = busStopsInViewport,
                            maxCount = 200,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        ).toMutableList()

                        // Ensure all connected route stops on highlighted route are included
                        if (emtHighlight.isHighlighted) {
                            busStopsInViewport.filter { getEmtStopAlpha(it) == 1.0f }.forEach {
                                if (stopsToRender.none { s -> s.id_parada == it.id_parada }) {
                                    stopsToRender.add(it)
                                }
                            }
                        }

                        // Make sure selected stop is in the rendered set
                        if (selectedBusStop != null && stopsToRender.none { it.id_parada == selectedStopId }) {
                            busStopsInViewport.find { it.id_parada == selectedStopId }?.let { stopsToRender.add(it) }
                        }

                        stopsToRender.forEach { stop ->
                            val isSelected = selectedStopId != null && stop.id_parada == selectedStopId
                            if (isSelected) selectedStopRendered = true
                            val alpha = getEmtStopAlpha(stop)
                            activeBusCount = renderSingleBusMarker(
                                context = context,
                                mapView = mapView,
                                stop = stop,
                                isSelected = isSelected,
                                isDarkMode = isDarkMode,
                                currentZoom = currentZoom,
                                busDrawable = busDrawable,
                                busDotDrawable = busDotDrawable,
                                useDotIcon = !isSelected,
                                busStopAliases = busStopAliases,
                                recycledBusMarkers = recycledBusMarkers,
                                activeBusIndex = activeBusCount,
                                alpha = alpha,
                                labelPosition = labelPositions["BUS_" + stop.id_parada] ?: "BELOW",
                                onSelectItem = onSelectItem,
                                onTapHandler = onTapHandler
                            )
                        }
                    }

                    // LOD 3: ZOOM >= 15.2 -> Full Detailed Pins / Zoom 18+ with Name and Lines
                    else -> {
                        val stopsToRender = SpatialGridSampler.sampleUniformlyByGrid(
                            items = busStopsInViewport,
                            maxCount = 300,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        ).toMutableList()

                        // Ensure all connected route stops on highlighted route are included
                        if (emtHighlight.isHighlighted) {
                            busStopsInViewport.filter { getEmtStopAlpha(it) == 1.0f }.forEach {
                                if (stopsToRender.none { s -> s.id_parada == it.id_parada }) {
                                    stopsToRender.add(it)
                                }
                            }
                        }

                        if (selectedBusStop != null && stopsToRender.none { it.id_parada == selectedStopId }) {
                            busStopsInViewport.find { it.id_parada == selectedStopId }?.let { stopsToRender.add(it) }
                        }

                        stopsToRender.forEach { stop ->
                            val isSelected = selectedStopId != null && stop.id_parada == selectedStopId
                            if (isSelected) selectedStopRendered = true
                            val alpha = getEmtStopAlpha(stop)
                            activeBusCount = renderSingleBusMarker(
                                context = context,
                                mapView = mapView,
                                stop = stop,
                                isSelected = isSelected,
                                isDarkMode = isDarkMode,
                                currentZoom = currentZoom,
                                busDrawable = busDrawable,
                                busDotDrawable = busDotDrawable,
                                useDotIcon = false,
                                busStopAliases = busStopAliases,
                                recycledBusMarkers = recycledBusMarkers,
                                activeBusIndex = activeBusCount,
                                alpha = alpha,
                                labelPosition = labelPositions["BUS_" + stop.id_parada] ?: "BELOW",
                                onSelectItem = onSelectItem,
                                onTapHandler = onTapHandler
                            )
                        }
                    }
                }
            }
        }

        // Always render selected bus stop with large detailed marker even if showBus is false or outside zoom range
        if (!selectedStopRendered && selectedBusStop != null) {
            activeBusCount = renderSingleBusMarker(
                context = context,
                mapView = mapView,
                stop = selectedBusStop,
                isSelected = true,
                isDarkMode = isDarkMode,
                currentZoom = currentZoom,
                busDrawable = busDrawable,
                busDotDrawable = busDotDrawable,
                useDotIcon = false,
                busStopAliases = busStopAliases,
                recycledBusMarkers = recycledBusMarkers,
                activeBusIndex = activeBusCount,
                labelPosition = labelPositions["BUS_" + selectedBusStop.id_parada] ?: "BELOW",
                onSelectItem = onSelectItem,
                onTapHandler = onTapHandler
            )
        }

        // Hide unused recycled bus markers
        for (i in activeBusCount until recycledBusMarkers.size) {
            recycledBusMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        // Hide unused recycled cluster markers
        for (i in activeClusterCount until recycledClusterMarkers.size) {
            recycledClusterMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        return RenderResult(activeBusCount, activeClusterCount)
    }

    /**
     * Renders Metrobús stops and clusters depending on zoom LOD.
     */
    fun renderMetrobusStops(
        context: Context,
        mapView: MapView,
        metrobusStopsInViewport: List<MetrobusStopEntity>,
        isMetrobusFavoritesMode: Boolean,
        isOnlyMetrobusSelected: Boolean = false,
        isDarkMode: Boolean = false,
        currentZoom: Double,
        showMetrobus: Boolean,
        selectedMapItem: SelectedMapItem? = null,
        selectedBusLineFilters: Set<String> = emptySet(),
        recycledMetrobusMarkers: MutableList<Marker>,
        recycledMetrobusClusterMarkers: MutableList<Marker>,
        labelPositions: Map<String, String> = emptyMap(),
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): RenderResult {
        var activeMetrobusCount = 0
        var activeMetrobusClusterCount = 0

        val metrobusDrawable = getMarkerIcon(context, "MB", Color.parseColor("#FFB300"))
        val metrobusDotDrawable = getCompactDotIcon(context, Color.parseColor("#FFB300"))

        val selectedMetrobusStop = (selectedMapItem as? SelectedMapItem.MetrobusStopItem)?.stop
        val selectedMbStopId = selectedMetrobusStop?.id_parada
        var selectedMbStopRendered = false

        val mbHighlight = MetrobusStationHighlightManager.getHighlightState(selectedMapItem, selectedBusLineFilters)
        val emtHighlight = EmtStationHighlightManager.getHighlightState(selectedMapItem, selectedBusLineFilters)

        fun getMbStopAlpha(stop: MetrobusStopEntity): Float {
            return when {
                selectedMbStopId != null && stop.id_parada == selectedMbStopId -> 1.0f
                mbHighlight.isHighlighted -> MetrobusStationHighlightManager.getMetrobusMarkerAlpha(stop, mbHighlight, 1.0f)
                emtHighlight.isHighlighted -> 0.25f
                selectedMapItem is SelectedMapItem.Metro || selectedMapItem is SelectedMapItem.Cercanias -> 0.25f
                else -> 1.0f
            }
        }

        if (showMetrobus && metrobusStopsInViewport.isNotEmpty()) {
            if (isMetrobusFavoritesMode) {
                metrobusStopsInViewport.forEach { stop ->
                    val isSelected = selectedMbStopId != null && stop.id_parada == selectedMbStopId
                    if (isSelected) selectedMbStopRendered = true
                    val alpha = getMbStopAlpha(stop)
                    activeMetrobusCount = renderSingleMetrobusMarker(
                        context = context,
                        mapView = mapView,
                        stop = stop,
                        isSelected = isSelected,
                        isDarkMode = isDarkMode,
                        currentZoom = currentZoom,
                        metrobusDrawable = metrobusDrawable,
                        metrobusDotDrawable = metrobusDotDrawable,
                        useDotIcon = false,
                        recycledMetrobusMarkers = recycledMetrobusMarkers,
                        activeMbIndex = activeMetrobusCount,
                        alpha = alpha,
                        labelPosition = labelPositions["MB_" + stop.id_parada] ?: "BELOW",
                        onSelectItem = onSelectItem,
                        onTapHandler = onTapHandler
                    )
                }
            } else if (mbHighlight.isHighlighted && currentZoom < 14.0) {
                // When a Metrobús route is highlighted at wide zoom, render all route stops clearly as dots without cluttering clusters
                val routeStops = metrobusStopsInViewport.filter { getMbStopAlpha(it) == 1.0f }
                routeStops.forEach { stop ->
                    val isSelected = selectedMbStopId != null && stop.id_parada == selectedMbStopId
                    if (isSelected) selectedMbStopRendered = true
                    activeMetrobusCount = renderSingleMetrobusMarker(
                        context = context,
                        mapView = mapView,
                        stop = stop,
                        isSelected = isSelected,
                        isDarkMode = isDarkMode,
                        currentZoom = currentZoom,
                        metrobusDrawable = metrobusDrawable,
                        metrobusDotDrawable = metrobusDotDrawable,
                        useDotIcon = !isSelected,
                        recycledMetrobusMarkers = recycledMetrobusMarkers,
                        activeMbIndex = activeMetrobusCount,
                        alpha = 1.0f,
                        labelPosition = labelPositions["MB_" + stop.id_parada] ?: "BELOW",
                        onSelectItem = onSelectItem,
                        onTapHandler = onTapHandler
                    )
                }
            } else if (emtHighlight.isHighlighted && currentZoom < 14.5) {
                // EMT line selected & zoomed out -> suppress Metrobús markers to emphasize EMT route
            } else {
                when {
                    // LOD 1: ZOOM < 13.5 -> Radius Grid Clustering
                    currentZoom < 13.5 -> {
                        val gridSize = if (currentZoom < 11.0) 0.06 else 0.025
                        val (selectedList, nonSelectedStops) = if (selectedMbStopId != null) {
                            metrobusStopsInViewport.partition { it.id_parada == selectedMbStopId }
                        } else {
                            Pair(emptyList(), metrobusStopsInViewport)
                        }

                        // Render selected stop first
                        selectedList.forEach { stop ->
                            selectedMbStopRendered = true
                            activeMetrobusCount = renderSingleMetrobusMarker(
                                context = context,
                                mapView = mapView,
                                stop = stop,
                                isSelected = true,
                                isDarkMode = isDarkMode,
                                currentZoom = currentZoom,
                                metrobusDrawable = metrobusDrawable,
                                metrobusDotDrawable = metrobusDotDrawable,
                                useDotIcon = false,
                                recycledMetrobusMarkers = recycledMetrobusMarkers,
                                activeMbIndex = activeMetrobusCount,
                                alpha = 1.0f,
                                labelPosition = labelPositions["MB_" + stop.id_parada] ?: "BELOW",
                                onSelectItem = onSelectItem,
                                onTapHandler = onTapHandler
                            )
                        }

                        // Only draw cluster circles if no specific transit route is highlighted
                        if (!mbHighlight.isHighlighted && !emtHighlight.isHighlighted) {
                            val clusters = nonSelectedStops.groupBy { stop ->
                                val gridX = (stop.lat / gridSize).toInt()
                                val gridY = (stop.lon / gridSize).toInt()
                                Pair(gridX, gridY)
                            }

                            clusters.values.forEach { group ->
                                val avgLat = group.map { it.lat }.average()
                                val avgLon = group.map { it.lon }.average()
                                val count = group.size

                                val clusterMarker = if (activeMetrobusClusterCount < recycledMetrobusClusterMarkers.size) {
                                    recycledMetrobusClusterMarkers[activeMetrobusClusterCount]
                                } else {
                                    Marker(mapView).also {
                                        recycledMetrobusClusterMarkers.add(it)
                                        mapView.overlays.add(it)
                                    }
                                }
                                activeMetrobusClusterCount++

                                clusterMarker.infoWindow = null
                                clusterMarker.closeInfoWindow()
                                clusterMarker.position = GeoPoint(avgLat, avgLon)
                                clusterMarker.icon = getClusterIcon(context, count, Color.parseColor("#FFB300"))
                                clusterMarker.title = "$count Paradas de Metrobús"
                                clusterMarker.snippet = "Toca para ampliar área"
                                clusterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                clusterMarker.isEnabled = true
                                clusterMarker.setVisible(true)
                                clusterMarker.setOnMarkerClickListener { _, _ ->
                                    if (showMetrobus && clusterMarker.isEnabled) {
                                        mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                                        mapView.controller.zoomTo(currentZoom + 2.2)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                        }
                    }

                    // LOD 2: 13.5 <= ZOOM < 15.2 -> Compact Dot Icons
                    currentZoom in 13.5..<15.2 -> {
                        val stopsToRender = SpatialGridSampler.sampleUniformlyByGrid(
                            items = metrobusStopsInViewport,
                            maxCount = 200,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        ).toMutableList()

                        // Ensure all connected route stops on highlighted route are included
                        if (mbHighlight.isHighlighted) {
                            metrobusStopsInViewport.filter { getMbStopAlpha(it) == 1.0f }.forEach {
                                if (stopsToRender.none { s -> s.id_parada == it.id_parada }) {
                                    stopsToRender.add(it)
                                }
                            }
                        }

                        if (selectedMetrobusStop != null && stopsToRender.none { it.id_parada == selectedMbStopId }) {
                            metrobusStopsInViewport.find { it.id_parada == selectedMbStopId }?.let { stopsToRender.add(it) }
                        }

                        stopsToRender.forEach { stop ->
                            val isSelected = selectedMbStopId != null && stop.id_parada == selectedMbStopId
                            if (isSelected) selectedMbStopRendered = true
                            val alpha = getMbStopAlpha(stop)
                            activeMetrobusCount = renderSingleMetrobusMarker(
                                context = context,
                                mapView = mapView,
                                stop = stop,
                                isSelected = isSelected,
                                isDarkMode = isDarkMode,
                                currentZoom = currentZoom,
                                metrobusDrawable = metrobusDrawable,
                                metrobusDotDrawable = metrobusDotDrawable,
                                useDotIcon = !isSelected,
                                recycledMetrobusMarkers = recycledMetrobusMarkers,
                                activeMbIndex = activeMetrobusCount,
                                alpha = alpha,
                                labelPosition = labelPositions["MB_" + stop.id_parada] ?: "BELOW",
                                onSelectItem = onSelectItem,
                                onTapHandler = onTapHandler
                            )
                        }
                    }

                    // LOD 3: ZOOM >= 15.2 -> Full Detailed Pins with Metrobús Logo / Zoom 18+ with Name and Lines
                    else -> {
                        val stopsToRender = SpatialGridSampler.sampleUniformlyByGrid(
                            items = metrobusStopsInViewport,
                            maxCount = 300,
                            getLat = { it.lat },
                            getLon = { it.lon }
                        ).toMutableList()

                        // Ensure all connected route stops on highlighted route are included
                        if (mbHighlight.isHighlighted) {
                            metrobusStopsInViewport.filter { getMbStopAlpha(it) == 1.0f }.forEach {
                                if (stopsToRender.none { s -> s.id_parada == it.id_parada }) {
                                    stopsToRender.add(it)
                                }
                            }
                        }

                        if (selectedMetrobusStop != null && stopsToRender.none { it.id_parada == selectedMbStopId }) {
                            metrobusStopsInViewport.find { it.id_parada == selectedMbStopId }?.let { stopsToRender.add(it) }
                        }

                        stopsToRender.forEach { stop ->
                            val isSelected = selectedMbStopId != null && stop.id_parada == selectedMbStopId
                            if (isSelected) selectedMbStopRendered = true
                            val alpha = getMbStopAlpha(stop)
                            activeMetrobusCount = renderSingleMetrobusMarker(
                                context = context,
                                mapView = mapView,
                                stop = stop,
                                isSelected = isSelected,
                                isDarkMode = isDarkMode,
                                currentZoom = currentZoom,
                                metrobusDrawable = metrobusDrawable,
                                metrobusDotDrawable = metrobusDotDrawable,
                                useDotIcon = false,
                                recycledMetrobusMarkers = recycledMetrobusMarkers,
                                activeMbIndex = activeMetrobusCount,
                                alpha = alpha,
                                labelPosition = labelPositions["MB_" + stop.id_parada] ?: "BELOW",
                                onSelectItem = onSelectItem,
                                onTapHandler = onTapHandler
                            )
                        }
                    }
                }
            }
        }

        // Always render selected metrobus stop with large detailed marker even if showMetrobus is false or outside zoom range
        if (!selectedMbStopRendered && selectedMetrobusStop != null) {
            activeMetrobusCount = renderSingleMetrobusMarker(
                context = context,
                mapView = mapView,
                stop = selectedMetrobusStop,
                isSelected = true,
                isDarkMode = isDarkMode,
                currentZoom = currentZoom,
                metrobusDrawable = metrobusDrawable,
                metrobusDotDrawable = metrobusDotDrawable,
                useDotIcon = false,
                recycledMetrobusMarkers = recycledMetrobusMarkers,
                activeMbIndex = activeMetrobusCount,
                labelPosition = labelPositions["MB_" + selectedMetrobusStop.id_parada] ?: "BELOW",
                onSelectItem = onSelectItem,
                onTapHandler = onTapHandler
            )
        }

        // Hide unused recycled metrobus markers & clusters
        for (i in activeMetrobusCount until recycledMetrobusMarkers.size) {
            recycledMetrobusMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }
        for (i in activeMetrobusClusterCount until recycledMetrobusClusterMarkers.size) {
            recycledMetrobusClusterMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        return RenderResult(activeMetrobusCount, activeMetrobusClusterCount)
    }

    private fun renderSingleBusMarker(
        context: Context,
        mapView: MapView,
        stop: GeoportalStopEntity,
        isSelected: Boolean,
        isDarkMode: Boolean,
        currentZoom: Double,
        busDrawable: Drawable,
        busDotDrawable: Drawable,
        useDotIcon: Boolean,
        busStopAliases: Map<String, String>,
        recycledBusMarkers: MutableList<Marker>,
        activeBusIndex: Int,
        alpha: Float = 1.0f,
        labelPosition: String = "BELOW",
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): Int {
        val marker = if (activeBusIndex < recycledBusMarkers.size) {
            recycledBusMarkers[activeBusIndex]
        } else {
            Marker(mapView).also {
                recycledBusMarkers.add(it)
                mapView.overlays.add(it)
            }
        }
        marker.id = "BUS_${stop.id_parada}"

        marker.infoWindow = null
        marker.closeInfoWindow()
        marker.alpha = alpha
        val alias = busStopAliases[stop.id_parada]
        val displayName = if (!alias.isNullOrBlank()) "$alias (${stop.denominacion})" else stop.denominacion
        marker.position = GeoPoint(stop.lat, stop.lon)

        if (isSelected || currentZoom >= 17.8) {
            val detailedIcon = getBusDetailedMarkerIcon(context, displayName, stop.lineas, isDarkMode, "BUS", labelPosition)
            marker.icon = detailedIcon.drawable
            marker.setAnchor(detailedIcon.anchorU, detailedIcon.anchorV)
        } else if (useDotIcon) {
            marker.icon = busDotDrawable
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        } else {
            marker.icon = busDrawable
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }

        marker.title = displayName
        marker.snippet = "Parada ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
        marker.isEnabled = true
        marker.setVisible(true)
        marker.setOnMarkerClickListener { m, _ ->
            onTapHandler(context, mapView, m.position)
            true
        }
        return activeBusIndex + 1
    }

    private fun renderSingleMetrobusMarker(
        context: Context,
        mapView: MapView,
        stop: MetrobusStopEntity,
        isSelected: Boolean,
        isDarkMode: Boolean,
        currentZoom: Double,
        metrobusDrawable: Drawable,
        metrobusDotDrawable: Drawable,
        useDotIcon: Boolean,
        recycledMetrobusMarkers: MutableList<Marker>,
        activeMbIndex: Int,
        alpha: Float = 1.0f,
        labelPosition: String = "BELOW",
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onTapHandler: (Context, MapView, GeoPoint) -> Boolean
    ): Int {
        val marker = if (activeMbIndex < recycledMetrobusMarkers.size) {
            recycledMetrobusMarkers[activeMbIndex]
        } else {
            Marker(mapView).also {
                recycledMetrobusMarkers.add(it)
                mapView.overlays.add(it)
            }
        }
        marker.id = "MB_${stop.id_parada}"

        marker.infoWindow = null
        marker.closeInfoWindow()
        marker.alpha = alpha
        marker.position = GeoPoint(stop.lat, stop.lon)

        if (isSelected || currentZoom >= 17.8) {
            val detailedIcon = getBusDetailedMarkerIcon(context, stop.denominacion, stop.lineas, isDarkMode, "MB", labelPosition)
            marker.icon = detailedIcon.drawable
            marker.setAnchor(detailedIcon.anchorU, detailedIcon.anchorV)
        } else if (useDotIcon) {
            marker.icon = metrobusDotDrawable
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        } else {
            marker.icon = metrobusDrawable
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }

        marker.title = stop.denominacion
        marker.snippet = "Metrobús ${stop.id_parada} • Líneas: ${stop.lineas ?: "N/A"}"
        marker.isEnabled = true
        marker.setVisible(true)
        marker.setOnMarkerClickListener { m, _ ->
            onTapHandler(context, mapView, m.position)
            true
        }
        return activeMbIndex + 1
    }
}
