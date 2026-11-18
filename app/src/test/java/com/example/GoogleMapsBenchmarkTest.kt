package com.example

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.CercaniasStationEntity
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.MetroStation
import com.example.ui.map.MapFilter
import com.example.ui.map.components.BusMarkersRenderer
import com.example.ui.map.components.CercaniasMapOverlayLoader
import com.example.ui.map.components.EmtMapOverlayLoader
import com.example.ui.map.components.MetroMapOverlayLoader
import com.example.ui.map.components.MetroStationFootprintsOverlayManager
import com.example.ui.map.components.ValenbisiStation
import com.example.ui.map.components.ViewportTransitFilter
import com.example.ui.map.components.createCercaniasMarkerIcon
import com.example.ui.map.components.createMetroMarkerIcon
import com.example.ui.map.components.getBusDetailedMarkerIcon
import com.example.ui.map.components.getValenbisiMarkerIcon
import com.example.ui.map.components.SheetState
import com.example.ui.map.components.computeMonotonicDismissReportedHeightPx
import com.example.ui.map.components.computeNearbyRestingHeightPx
import com.example.ui.map.google.GoogleMapPolyline
import com.example.ui.map.google.GoogleMapTransitItem
import com.example.ui.map.google.estimateViewportBoundingBox
import com.example.ui.map.google.isValidMapTarget
import com.example.ui.map.google.isValidMapZoom
import com.example.ui.map.google.resolveInitialCameraPosition
import com.example.ui.map.google.samplePolylineDirectionArrows
import com.example.ui.map.google.shouldRetargetInFlightCamera
import com.example.ui.map.google.shouldSkipRedundantAnimation
import com.example.ui.map.google.shouldSyncFocusAfterProgrammaticAnimation
import com.example.ui.map.spatial.BoundingBox2D
import com.example.ui.map.spatial.SpatialGridSampler
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.BoundingBox
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleMapsBenchmarkTest {

    @Test
    fun benchmarkOverlayLoadingAndProcessing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        println("=== STARTING GOOGLE MAPS PERFORMANCE BENCHMARK ===")

        // 1. Measure MetroMapOverlayLoader (Cold)
        val metroTime = measureTimeMillis {
            MetroMapOverlayLoader.ensureLoaded(context)
            var attempts = 0
            while (!MetroMapOverlayLoader.isLoaded && attempts < 300) {
                kotlinx.coroutines.delay(10)
                attempts++
            }
        }
        val metroSets = MetroMapOverlayLoader.getPolylineSets()
        val metroVertsFar = metroSets.highResFar.sumOf { it.points.size }
        val metroVertsMed = metroSets.highResMedium.sumOf { it.points.size }
        val metroVertsClose = metroSets.highResClose.sumOf { it.points.size }
        val metroLowVertsMed = metroSets.lowResMedium.sumOf { it.points.size }
        println("BENCHMARK [Metro Lines Load]: $metroTime ms (polylines=${metroSets.highResMedium.size}, vertsFar=$metroVertsFar, vertsMed=$metroVertsMed, vertsClose=$metroVertsClose, lowVertsMed=$metroLowVertsMed)")

        val metroConvertMs = measureNanoTime {
            metroSets.highResMedium.map { raw ->
                GoogleMapPolyline(
                    points = raw.points.map { LatLng(it.latitude, it.longitude) },
                    color = Color(raw.color),
                    width = raw.strokeWidth,
                    lineRef = raw.lineRef
                )
            }
        } / 1_000_000.0
        println("BENCHMARK [Metro GeoPoint->LatLng]: ${String.format("%.2f", metroConvertMs)} ms")

        // 2. Measure Cercanías (Cold)
        val cercaniasTime = measureTimeMillis {
            CercaniasMapOverlayLoader.ensureLoaded(context)
            var attempts = 0
            while (!CercaniasMapOverlayLoader.isLoaded && attempts < 300) {
                kotlinx.coroutines.delay(10)
                attempts++
            }
        }
        val cercSets = CercaniasMapOverlayLoader.getPolylineSets()
        val cercVertsMed = cercSets.highResMedium.sumOf { it.points.size }
        println("BENCHMARK [Cercanias Lines Load]: $cercaniasTime ms (polylines=${cercSets.highResMedium.size}, vertsMed=$cercVertsMed)")

        // 3. Measure EMT Shapes Loader (Cold) + Lookup + Chevrons
        val emtLoadTime = measureTimeMillis {
            EmtMapOverlayLoader.ensureLoaded(context)
            var attempts = 0
            while (!EmtMapOverlayLoader.isLoaded && attempts < 300) {
                kotlinx.coroutines.delay(10)
                attempts++
            }
        }
        val sampleShapes = EmtMapOverlayLoader.getShapesForLine("4", null, null)
        val emtGooglePolys = sampleShapes.map { shape ->
            GoogleMapPolyline(
                points = shape.points.map { LatLng(it.latitude, it.longitude) },
                color = Color(AndroidColor.parseColor("#E52320")),
                width = 9f,
                lineRef = "4"
            )
        }
        var arrowCount = 0
        val chevronMs = measureNanoTime {
            repeat(10) {
                arrowCount = samplePolylineDirectionArrows(emtGooglePolys, intervalMeters = 380.0, isMetrobus = false).size
            }
        } / 1_000_000.0
        println("BENCHMARK [EMT Shapes Load]: $emtLoadTime ms (line4Shapes=${sampleShapes.size}), [Chevrons x10]: ${String.format("%.2f", chevronMs)} ms (arrows=$arrowCount)")

        // 4. Measure Metro Footprints and Accesses
        var fpPolyCount = 0
        var fpAccessCount = 0
        val footprintsTime = measureTimeMillis {
            val polygons = MetroStationFootprintsOverlayManager.ensureLoaded(context)
            val accesses = MetroStationFootprintsOverlayManager.getLoadedAccessPointsData()
            fpPolyCount = polygons.size
            fpAccessCount = accesses.size
        }
        println("BENCHMARK [Footprints Total]: $footprintsTime ms (polygons=$fpPolyCount, accesses=$fpAccessCount)")

        // 5. Realistic Valencia datasets
        val fakeEmtStops = (1..2500).map { i ->
            GeoportalStopEntity(
                id_parada = "$i",
                denominacion = "Parada EMT $i",
                suprimida = 0,
                lat = 39.4699 + (i % 100) * 0.001 - 0.05,
                lon = -0.3763 + (i / 100) * 0.001 - 0.05,
                lineas = "10, 11, 12, 13"
            )
        }
        val fakeMbStops = (1..1200).map { i ->
            MetrobusStopEntity(
                id = "$i",
                denominacion = "Parada EMT $i",
                municipio = "Valencia",
                latitud = 39.4699 + (i % 80) * 0.0012 - 0.05,
                longitud = -0.3763 + (i / 80) * 0.0012 - 0.05,
                lineas = "150, 160"
            )
        }
        val fakeValenbisi = (1..276).map { i ->
            ValenbisiStation(
                gid = i,
                name = "Valenbisi $i",
                number = i,
                address = "Calle $i",
                open = true,
                available = i % 20,
                free = (i * 3) % 25,
                total = 25,
                ticket = false,
                latitude = 39.4699 + (i % 30) * 0.002 - 0.03,
                longitude = -0.3763 + (i / 30) * 0.002 - 0.015
            )
        }
        val fakeMetro = (1..146).map { i ->
            MetroStation(
                id = "$i",
                name = "Estacio Metro $i",
                lines = listOf("L1", "L3", "L5"),
                zone = "A",
                latitude = 39.4699 + (i % 20) * 0.004 - 0.04,
                longitude = -0.3763 + (i / 20) * 0.004 - 0.02
            )
        }
        val fakeCercanias = (1..65).map { i ->
            CercaniasStationEntity(
                stop_id = "C$i",
                nombre = "Estacion Cercanias $i",
                lat = 39.4699 + (i % 15) * 0.01 - 0.05,
                lon = -0.3763 + (i / 15) * 0.01 - 0.03,
                lineas = listOf("C1", "C2", "C6")
            )
        }

        // ViewportFilter benchmark (cold + 30 pan steps)
        val busOnlyFilter = MapFilter(isFavorites = false, showBus = true, showMetrobus = true, showMetro = false, showCercanias = false, showValenbisi = false)
        var culledBus: List<GeoportalStopEntity> = emptyList()
        var culledMb: List<MetrobusStopEntity> = emptyList()

        val filterColdMs = measureNanoTime {
            val res = ViewportTransitFilter.filterForViewport(
                boundingBox = BoundingBox(39.49, -0.34, 39.45, -0.40),
                busStops = fakeEmtStops,
                metrobusStops = fakeMbStops,
                valenbisiStations = fakeValenbisi,
                mapFilter = busOnlyFilter,
                currentZoom = 15.5
            )
            culledBus = res.busStopsInViewport
            culledMb = res.metrobusStopsInViewport
        } / 1_000_000.0

        val filterWarmMs = measureNanoTime {
            for (step in 1..30) {
                val d = step * 0.0008
                ViewportTransitFilter.filterForViewport(
                    boundingBox = BoundingBox(39.49 + d, -0.34 + d, 39.45 + d, -0.40 + d),
                    busStops = fakeEmtStops,
                    metrobusStops = fakeMbStops,
                    valenbisiStations = fakeValenbisi,
                    mapFilter = busOnlyFilter,
                    currentZoom = 15.5
                )
            }
        } / 1_000_000.0
        println("BENCHMARK [ViewportFilter Cold]: ${String.format("%.2f", filterColdMs)} ms, [Warm 30 pans]: ${String.format("%.2f", filterWarmMs)} ms (culledBus=${culledBus.size}, culledMb=${culledMb.size})")

        // findMergedStopGroups x 10
        var mergedCount = 0
        val mergeMs = measureNanoTime {
            repeat(10) {
                mergedCount = BusMarkersRenderer.findMergedStopGroups(
                    busStopsInViewport = culledBus,
                    metrobusStopsInViewport = culledMb,
                    selectedMapItem = null,
                    showBus = true,
                    showMetrobus = true,
                    isFavoritesMode = false,
                    currentZoom = 15.5
                ).size
            }
        } / 1_000_000.0
        println("BENCHMARK [FindMergedStopGroups x10]: ${String.format("%.2f", mergeMs)} ms (merged=$mergedCount)")

        // SpatialGridSampler x 20
        var sampledCount = 0
        val sampleMs = measureNanoTime {
            repeat(20) {
                sampledCount = SpatialGridSampler.sampleUniformlyByGrid(
                    items = culledBus,
                    maxCount = 200,
                    getLat = { it.lat },
                    getLon = { it.lon }
                ).size
            }
        } / 1_000_000.0
        println("BENCHMARK [SpatialGridSampler 200 x20]: ${String.format("%.2f", sampleMs)} ms (sampled=$sampledCount)")

        // Clustering baseline x 20 (legacy Pair + average pattern) vs optimized primitive accumulator
        val clusterMs = measureNanoTime {
            repeat(20) {
                val gridSize = 0.025
                fakeEmtStops.groupBy { stop ->
                    Pair((stop.lat / gridSize).toInt(), (stop.lon / gridSize).toInt())
                }.values.map { group ->
                    GoogleMapTransitItem.Cluster(
                        count = group.size,
                        centerLat = group.map { it.lat }.average(),
                        centerLon = group.map { it.lon }.average(),
                        isMetrobus = false
                    )
                }
            }
        } / 1_000_000.0
        val clusterOptMs = measureNanoTime {
            repeat(20) {
                val gridSize = 0.025
                val buckets = HashMap<Long, DoubleArray>(64)
                for (i in 0 until fakeEmtStops.size) {
                    val stop = fakeEmtStops[i]
                    val key = (((stop.lat / gridSize).toInt()).toLong() shl 32) or (((stop.lon / gridSize).toInt()).toLong() and 0xFFFFFFFFL)
                    val acc = buckets[key]
                    if (acc == null) {
                        buckets[key] = doubleArrayOf(stop.lat, stop.lon, 1.0)
                    } else {
                        acc[0] += stop.lat
                        acc[1] += stop.lon
                        acc[2] += 1.0
                    }
                }
                val out = ArrayList<GoogleMapTransitItem.Cluster>(buckets.size)
                for (acc in buckets.values) {
                    val c = acc[2].toInt()
                    out.add(GoogleMapTransitItem.Cluster(count = c, centerLat = acc[0] / c, centerLon = acc[1] / c, isMetrobus = false))
                }
            }
        } / 1_000_000.0
        println("BENCHMARK [Cluster Grid 2500 x20]: legacy=${String.format("%.2f", clusterMs)} ms vs primitive=${String.format("%.2f", clusterOptMs)} ms")

        // Tap Disambiguation linear scan baseline vs QuadTree O(log N) query x 20 taps
        val thresholdSq = 0.00035 * 0.00035
        var tapHits = 0
        val tapLinearMs = measureNanoTime {
            repeat(20) { t ->
                val lat = 39.4699 + t * 0.0005
                val lon = -0.3763 + t * 0.0005
                fakeMetro.forEach { s ->
                    val dLat = s.latitude - lat; val dLon = s.longitude - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) tapHits++
                }
                fakeCercanias.forEach { s ->
                    val dLat = s.lat - lat; val dLon = s.lon - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) tapHits++
                }
                fakeEmtStops.forEach { s ->
                    val dLat = s.lat - lat; val dLon = s.lon - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) tapHits++
                }
                fakeMbStops.forEach { s ->
                    val dLat = s.latitud - lat; val dLon = s.longitud - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) tapHits++
                }
                fakeValenbisi.forEach { s ->
                    val dLat = s.latitude - lat; val dLon = s.longitude - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) tapHits++
                }
            }
        } / 1_000_000.0
        // Warm QuadTree caches first, then measure 20 tap queries
        val tapDelta = 0.00035
        val warmBox = BoundingBox2D(minLat = 39.46, maxLat = 39.47, minLon = -0.38, maxLon = -0.37)
        ViewportTransitFilter.queryBusStopsInBox(fakeEmtStops, warmBox)
        ViewportTransitFilter.queryMetrobusStopsInBox(fakeMbStops, warmBox)
        ViewportTransitFilter.queryValenbisiInBox(fakeValenbisi, warmBox)
        var quadHits = 0
        val tapQuadMs = measureNanoTime {
            repeat(20) { t ->
                val lat = 39.4699 + t * 0.0005
                val lon = -0.3763 + t * 0.0005
                val box = BoundingBox2D(
                    minLat = lat - tapDelta,
                    maxLat = lat + tapDelta,
                    minLon = lon - tapDelta,
                    maxLon = lon + tapDelta
                )
                ViewportTransitFilter.queryBusStopsInBox(fakeEmtStops, box).forEach { s ->
                    val dLat = s.lat - lat; val dLon = s.lon - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) quadHits++
                }
                ViewportTransitFilter.queryMetrobusStopsInBox(fakeMbStops, box).forEach { s ->
                    val dLat = s.latitud - lat; val dLon = s.longitud - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) quadHits++
                }
                ViewportTransitFilter.queryValenbisiInBox(fakeValenbisi, box).forEach { s ->
                    val dLat = s.latitude - lat; val dLon = s.longitude - lon
                    if (dLat * dLat + dLon * dLon < thresholdSq) quadHits++
                }
            }
        } / 1_000_000.0
        println("BENCHMARK [Tap Scan Linear 4187 x20]: ${String.format("%.2f", tapLinearMs)} ms (hits=$tapHits), [Tap QuadTree Bus/MB/Val x20]: ${String.format("%.2f", tapQuadMs)} ms (hits=$quadHits)")

        // Icon Generation Benchmarks (Cold + Cache lookup)
        val metroIconMs = measureNanoTime {
            fakeMetro.forEach { s ->
                createMetroMarkerIcon(context, s.name, s.lines, isDarkMode = false, showPill = true)
            }
        } / 1_000_000.0
        val cercIconMs = measureNanoTime {
            fakeCercanias.forEach { s ->
                createCercaniasMarkerIcon(context, s.nombre, s.lineas, isDarkMode = false, showPill = true)
            }
        } / 1_000_000.0
        val busIconMs = measureNanoTime {
            fakeEmtStops.take(120).forEach { s ->
                getBusDetailedMarkerIcon(context, s.denominacion, s.lineas, isDarkMode = false, busType = "BUS")
            }
        } / 1_000_000.0
        val valenbisiIconMs = measureNanoTime {
            fakeValenbisi.forEach { s ->
                getValenbisiMarkerIcon(context, s.available, s.free, isDarkMode = false, showPill = true)
            }
        } / 1_000_000.0
        println("BENCHMARK [Icons Metro(146)]: ${String.format("%.2f", metroIconMs)} ms, [Cerc(65)]: ${String.format("%.2f", cercIconMs)} ms, [Bus120]: ${String.format("%.2f", busIconMs)} ms, [Valenbisi276]: ${String.format("%.2f", valenbisiIconMs)} ms")

        println("=== BENCHMARK COMPLETE ===")
        assertTrue(metroSets.highResMedium.isNotEmpty())
        assertTrue(cercSets.highResMedium.isNotEmpty())
        assertTrue(fpPolyCount > 0)
    }

    @Test
    fun verifyViewportQuadTreeExactMatch() {
        val emt = (1..1500).map { i ->
            GeoportalStopEntity(
                id_parada = "E$i",
                denominacion = "Parada $i",
                suprimida = 0,
                lat = 39.42 + (i % 60) * 0.0015,
                lon = -0.43 + (i / 60) * 0.0025,
                lineas = "10, 12"
            )
        }
        val mb = (1..900).map { i ->
            MetrobusStopEntity(
                id = "M$i",
                denominacion = "Parada MB $i",
                municipio = "Valencia",
                latitud = 39.42 + (i % 50) * 0.0018,
                longitud = -0.43 + (i / 50) * 0.003,
                lineas = "150"
            )
        }
        val vb = (1..276).map { i ->
            ValenbisiStation(
                gid = i,
                name = "VB $i",
                number = i,
                address = "Dir $i",
                open = true,
                available = i % 15,
                free = 10,
                total = 25,
                ticket = false,
                latitude = 39.44 + (i % 25) * 0.002,
                longitude = -0.40 + (i / 25) * 0.003
            )
        }

        val minLat = 39.455
        val maxLat = 39.485
        val minLon = -0.395
        val maxLon = -0.355
        val box = BoundingBox2D(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon)

        val expectedEmt = emt.filter { it.lat in minLat..maxLat && it.lon in minLon..maxLon }.map { it.id_parada }.toSet()
        val actualEmt = ViewportTransitFilter.queryBusStopsInBox(emt, box).map { it.id_parada }.toSet()
        org.junit.Assert.assertEquals(expectedEmt, actualEmt)

        val expectedMb = mb.filter { it.lat in minLat..maxLat && it.lon in minLon..maxLon }.map { it.id }.toSet()
        val actualMb = ViewportTransitFilter.queryMetrobusStopsInBox(mb, box).map { it.id }.toSet()
        org.junit.Assert.assertEquals(expectedMb, actualMb)

        val expectedVb = vb.filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }.map { it.gid }.toSet()
        val actualVb = ViewportTransitFilter.queryValenbisiInBox(vb, box).map { it.gid }.toSet()
        org.junit.Assert.assertEquals(expectedVb, actualVb)
    }

    @Test
    fun verifyTightHighZoomViewportCullingDoesNotOverscanCity() {
        ViewportTransitFilter.clearCache()
        // 2 visible stops inside a zoomed-in 120m x 90m viewport, surrounded by 160 dense nearby stops within ~2 km
        val denseEmtStops = mutableListOf(
            GeoportalStopEntity(id_parada = "V1", denominacion = "Visible 1", suprimida = 0, lat = 39.4700, lon = -0.3760, lineas = "4, 6"),
            GeoportalStopEntity(id_parada = "V2", denominacion = "Visible 2", suprimida = 0, lat = 39.4703, lon = -0.3758, lineas = "8")
        )
        for (i in 1..160) {
            val angle = i * 0.4
            val dist = 0.002 + (i % 10) * 0.0012 // 220m .. 1.4km away (inside legacy 0.015+margin box!)
            denseEmtStops.add(
                GeoportalStopEntity(
                    id_parada = "OFF_$i",
                    denominacion = "Lejana $i",
                    suprimida = 0,
                    lat = 39.4700 + kotlin.math.sin(angle) * dist,
                    lon = -0.3760 + kotlin.math.cos(angle) * dist,
                    lineas = "10"
                )
            )
        }

        val tightZoomBox = BoundingBox(39.4706, -0.3755, 39.4694, -0.3765)
        val filter = MapFilter(isFavorites = false, showBus = true, showMetrobus = true, showMetro = true, showCercanias = true, showValenbisi = false)
        val filtered = ViewportTransitFilter.filterForViewport(
            boundingBox = tightZoomBox,
            busStops = denseEmtStops,
            metrobusStops = emptyList(),
            valenbisiStations = emptyList(),
            mapFilter = filter,
            currentZoom = 19.5
        )

        val returnedIds = filtered.busStopsInViewport.map { it.id_parada }.toSet()
        assertTrue(returnedIds.contains("V1"))
        assertTrue(returnedIds.contains("V2"))
        org.junit.Assert.assertEquals(2, filtered.busStopsInViewport.size)
    }

    @Test
    fun verifyValenbisiVisualCacheParityAndMergedStopGroups() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val iconA = getValenbisiMarkerIcon(context, available = 7, free = 2, isDarkMode = false, showPill = true)
        val iconB = getValenbisiMarkerIcon(context, available = 7, free = 24, isDarkMode = false, showPill = true)
        val bmpA = (iconA.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        val bmpB = (iconB.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        org.junit.Assert.assertEquals(bmpA.width, bmpB.width)
        org.junit.Assert.assertEquals(bmpA.height, bmpB.height)
        assertTrue(bmpA.sameAs(bmpB))

        // Verify BusMarkersRenderer.findMergedStopGroups deterministic matching with caches
        val bus = listOf(
            GeoportalStopEntity(id_parada = "100", denominacion = "Av. del Cid, 45", suprimida = 0, lat = 39.4698, lon = -0.3768, lineas = "4, 6, 8"),
            GeoportalStopEntity(id_parada = "101", denominacion = "Otra Lejana", suprimida = 0, lat = 39.5000, lon = -0.4200, lineas = "9")
        )
        val mb = listOf(
            MetrobusStopEntity(id = "MB1", denominacion = "Avenida del Cid - 45", municipio = "Valencia", latitud = 39.46985, longitud = -0.37682, lineas = "150")
        )
        val groupsFirst = BusMarkersRenderer.findMergedStopGroups(bus, mb, null, showBus = true, showMetrobus = true, isFavoritesMode = false, currentZoom = 16.0)
        val groupsSecond = BusMarkersRenderer.findMergedStopGroups(bus, mb, null, showBus = true, showMetrobus = true, isFavoritesMode = false, currentZoom = 16.0)
        org.junit.Assert.assertEquals(1, groupsFirst.size)
        org.junit.Assert.assertEquals("100", groupsFirst[0].busStop.id_parada)
        org.junit.Assert.assertEquals("MB1", groupsFirst[0].mbStop.id)
        org.junit.Assert.assertEquals(groupsFirst, groupsSecond)
    }

    @Test
    fun verifyStartupCameraInitializationAndBounds() {
        val userPoint = GeoPoint(39.4752, -0.3701)
        val (resolvedTarget, resolvedZoom) = resolveInitialCameraPosition(userPoint, 15.5)
        org.junit.Assert.assertEquals(39.4752, resolvedTarget.latitude, 1e-6)
        org.junit.Assert.assertEquals(-0.3701, resolvedTarget.longitude, 1e-6)
        org.junit.Assert.assertEquals(15.5f, resolvedZoom, 0.001f)

        // Fallback when target is uninitialized (0, 0) or zoom is invalid
        val (fallbackTarget, fallbackZoom) = resolveInitialCameraPosition(GeoPoint(0.0, 0.0), 0.0)
        org.junit.Assert.assertEquals(39.4699, fallbackTarget.latitude, 1e-4)
        org.junit.Assert.assertEquals(-0.3763, fallbackTarget.longitude, 1e-4)
        org.junit.Assert.assertEquals(15.5f, fallbackZoom, 0.001f)

        // Clamped below MIN_ZOOM
        val (_, clampedMin) = resolveInitialCameraPosition(userPoint, 2.0)
        org.junit.Assert.assertEquals(9.0f, clampedMin, 0.001f)

        // Zoom & target guards against uninitialized native GMS 0f/2f frames
        org.junit.Assert.assertFalse(isValidMapZoom(0f))
        org.junit.Assert.assertFalse(isValidMapZoom(2f))
        org.junit.Assert.assertFalse(isValidMapZoom(Float.NaN))
        assertTrue(isValidMapZoom(9.0f))
        assertTrue(isValidMapZoom(15.5f))

        org.junit.Assert.assertFalse(isValidMapTarget(LatLng(0.0, 0.0)))
        assertTrue(isValidMapTarget(LatLng(39.4752, -0.3701)))

        // Redundant startup animation guard
        assertTrue(shouldSkipRedundantAnimation(39.4752, -0.3701, 15.5f, 39.475201, -0.370101, 15.5f))
        org.junit.Assert.assertFalse(shouldSkipRedundantAnimation(39.4699, -0.3763, 15.5f, 39.4752, -0.3701, 15.5f))
        org.junit.Assert.assertFalse(shouldSkipRedundantAnimation(39.4752, -0.3701, 13.5f, 39.4752, -0.3701, 15.5f))

        // Tight local bounding box at startup zoom 15.5 (<< legacy 0.20 x 0.25 degree Valencia-wide fallback)
        val tightBox = estimateViewportBoundingBox(39.4752, -0.3701, 15.5f)
        val latSpan = tightBox.latNorth - tightBox.latSouth
        val lonSpan = tightBox.lonEast - tightBox.lonWest
        assertTrue("Expected tight latSpan < 0.03, got $latSpan", latSpan < 0.03)
        assertTrue("Expected tight lonSpan < 0.03, got $lonSpan", lonSpan < 0.03)
        assertTrue(39.4752 in tightBox.latSouth..tightBox.latNorth)
        assertTrue(-0.3701 in tightBox.lonWest..tightBox.lonEast)
    }

    @Test
    fun verifyBottomSheetRecenterAndDismissTransitions() {
        // 1. Nearby sheet resting height calculation matches state & search visibility
        org.junit.Assert.assertEquals(
            0f,
            computeNearbyRestingHeightPx(isSearchActive = true, SheetState.COLLAPSED, 720f, 1680f),
            0.01f
        )
        org.junit.Assert.assertEquals(
            0f,
            computeNearbyRestingHeightPx(isSearchActive = false, SheetState.MINIMIZED, 720f, 1680f),
            0.01f
        )
        org.junit.Assert.assertEquals(
            720f,
            computeNearbyRestingHeightPx(isSearchActive = false, SheetState.COLLAPSED, 720f, 1680f),
            0.01f
        )
        org.junit.Assert.assertEquals(
            1680f,
            computeNearbyRestingHeightPx(isSearchActive = false, SheetState.EXPANDED, 720f, 1680f),
            0.01f
        )

        // 2. Monotonic reported height when dismissing taller sheet (1020px -> 720px)
        org.junit.Assert.assertEquals(1020f, computeMonotonicDismissReportedHeightPx(1020f, 1020f, 720f), 0.01f)
        org.junit.Assert.assertEquals(870f, computeMonotonicDismissReportedHeightPx(870f, 1020f, 720f), 0.01f)
        org.junit.Assert.assertEquals(720f, computeMonotonicDismissReportedHeightPx(720f, 1020f, 720f), 0.01f)

        // 3. Monotonic reported height when dismissing compact sheet (< target, e.g. Address 510px -> Nearby 720px)
        // Must never dip down toward 0f even though visual card shrinks 510 -> 0!
        val startReport = computeMonotonicDismissReportedHeightPx(510f, 510f, 720f)
        val midReport = computeMonotonicDismissReportedHeightPx(255f, 510f, 720f)
        val endReport = computeMonotonicDismissReportedHeightPx(0f, 510f, 720f)
        org.junit.Assert.assertEquals(510f, startReport, 0.01f)
        org.junit.Assert.assertEquals(615f, midReport, 0.01f)
        org.junit.Assert.assertEquals(720f, endReport, 0.01f)

        // 4. Mid-flight retargeting when sheet padding updates during active station flight
        assertTrue(shouldRetargetInFlightCamera(isProgrammaticAnimating = true, hasActiveFlightTarget = true, paddingDeltaDp = 50f, remainingFlightMs = 350L))
        org.junit.Assert.assertFalse(shouldRetargetInFlightCamera(isProgrammaticAnimating = false, hasActiveFlightTarget = true, paddingDeltaDp = 50f, remainingFlightMs = 350L))
        org.junit.Assert.assertFalse(shouldRetargetInFlightCamera(isProgrammaticAnimating = true, hasActiveFlightTarget = false, paddingDeltaDp = 50f, remainingFlightMs = 350L))
        org.junit.Assert.assertFalse(shouldRetargetInFlightCamera(isProgrammaticAnimating = true, hasActiveFlightTarget = true, paddingDeltaDp = 0.5f, remainingFlightMs = 350L))
        org.junit.Assert.assertFalse(shouldRetargetInFlightCamera(isProgrammaticAnimating = true, hasActiveFlightTarget = true, paddingDeltaDp = 50f, remainingFlightMs = 0L))

        // 5. Post-flight padding sync triggers only once flight & gesture finish with pending update
        org.junit.Assert.assertFalse(shouldSyncFocusAfterProgrammaticAnimation(pendingPaddingSync = true, isProgrammaticAnimating = true, isUserInteracting = false))
        org.junit.Assert.assertFalse(shouldSyncFocusAfterProgrammaticAnimation(pendingPaddingSync = true, isProgrammaticAnimating = false, isUserInteracting = true))
        org.junit.Assert.assertFalse(shouldSyncFocusAfterProgrammaticAnimation(pendingPaddingSync = false, isProgrammaticAnimating = false, isUserInteracting = false))
        assertTrue(shouldSyncFocusAfterProgrammaticAnimation(pendingPaddingSync = true, isProgrammaticAnimating = false, isUserInteracting = false))
    }
}
