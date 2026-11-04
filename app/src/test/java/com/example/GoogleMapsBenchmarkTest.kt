package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.ui.map.components.BusMarkersRenderer
import com.example.ui.map.components.CercaniasMapOverlayLoader
import com.example.ui.map.components.MetroMapOverlayLoader
import com.example.ui.map.components.MetroStationFootprintsOverlayManager
import com.example.ui.map.google.GoogleMapsMarkerCache
import com.example.ui.map.spatial.SpatialGridSampler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleMapsBenchmarkTest {

    @Test
    fun benchmarkOverlayLoadingAndProcessing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        println("=== STARTING GOOGLE MAPS PERFORMANCE BENCHMARK ===")

        // 1. Measure MetroMapOverlayLoader
        val metroTime = measureTimeMillis {
            MetroMapOverlayLoader.ensureLoaded(context)
            var attempts = 0
            while (!MetroMapOverlayLoader.isLoaded && attempts < 100) {
                kotlinx.coroutines.delay(50)
                attempts++
            }
        }
        val metroPolylineCount = MetroMapOverlayLoader.getPolylineSets().highResMedium.size
        println("BENCHMARK [Metro Lines]: $metroTime ms (polylines count = $metroPolylineCount)")

        // 2. Measure Cercanías
        val cercaniasTime = measureTimeMillis {
            CercaniasMapOverlayLoader.ensureLoaded(context)
            var attempts = 0
            while (!CercaniasMapOverlayLoader.isLoaded && attempts < 100) {
                kotlinx.coroutines.delay(50)
                attempts++
            }
        }
        val cercaniasPolylineCount = CercaniasMapOverlayLoader.getPolylineSets().highResMedium.size
        println("BENCHMARK [Cercanías Lines]: $cercaniasTime ms (polylines count = $cercaniasPolylineCount)")

        // 3. Measure Metro Footprints and Accesses
        val footprintsTime = measureTimeMillis {
            val polygons = MetroStationFootprintsOverlayManager.ensureLoaded(context)
            val accesses = MetroStationFootprintsOverlayManager.getLoadedAccessPointsData()
            println("BENCHMARK [Footprints]: polygons = ${polygons.size}, accesses = ${accesses.size}")
        }
        println("BENCHMARK [Footprints Total]: $footprintsTime ms")

        // 4. Measure Stop Filtering and Merging with 2500 EMT stops and 1200 Metrobús stops
        val fakeEmtStops: List<GeoportalStopEntity> = (1..2500).map { i ->
            GeoportalStopEntity(
                id_parada = "$i",
                denominacion = "Parada EMT $i",
                suprimida = 0,
                lat = 39.4699 + (i % 100) * 0.001 - 0.05,
                lon = -0.3763 + (i / 100) * 0.001 - 0.05,
                lineas = "10, 11, 12, 13"
            )
        }
        val fakeMbStops: List<MetrobusStopEntity> = (1..1200).map { i ->
            MetrobusStopEntity(
                id = "$i",
                denominacion = "Parada Metrobús $i",
                municipio = "Valencia",
                latitud = 39.4699 + (i % 80) * 0.0012 - 0.05,
                longitud = -0.3763 + (i / 80) * 0.0012 - 0.05,
                lineas = "150, 160"
            )
        }

        // Test viewport culling
        val minLat = 39.44
        val maxLat = 39.50
        val minLon = -0.42
        val maxLon = -0.32

        val cullingTime = measureTimeMillis {
            val culledBus = fakeEmtStops.filter { it.lat in minLat..maxLat && it.lon in minLon..maxLon }
            val culledMb = fakeMbStops.filter { it.latitud in minLat..maxLat && it.longitud in minLon..maxLon }
            println("BENCHMARK [Viewport Culling]: culledBus = ${culledBus.size}, culledMb = ${culledMb.size}")
        }
        println("BENCHMARK [Culling Time]: $cullingTime ms")

        // Test findMergedStopGroups on culled items
        val culledBus: List<GeoportalStopEntity> = fakeEmtStops.filter { it.lat in minLat..maxLat && it.lon in minLon..maxLon }
        val culledMb: List<MetrobusStopEntity> = fakeMbStops.filter { it.latitud in minLat..maxLat && it.longitud in minLon..maxLon }

        val mergeTime = measureTimeMillis {
            val merged = BusMarkersRenderer.findMergedStopGroups(
                busStopsInViewport = culledBus,
                metrobusStopsInViewport = culledMb,
                selectedMapItem = null,
                showBus = true,
                showMetrobus = true,
                isFavoritesMode = false,
                currentZoom = 15.5
            )
            println("BENCHMARK [Merged Groups]: count = ${merged.size}")
        }
        println("BENCHMARK [FindMergedStopGroups Time]: $mergeTime ms")

        // Test SpatialGridSampler
        val samplingTime = measureTimeMillis {
            val sampled = SpatialGridSampler.sampleUniformlyByGrid(
                items = culledBus,
                maxCount = 200,
                getLat = { it.lat },
                getLon = { it.lon }
            )
            println("BENCHMARK [Sampled Count]: ${sampled.size}")
        }
        println("BENCHMARK [SpatialGridSampler Time]: $samplingTime ms")

        println("=== BENCHMARK COMPLETE ===")
        assertTrue(metroPolylineCount > 0)
    }
}
