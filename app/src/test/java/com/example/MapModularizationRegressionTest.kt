package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.GeoportalStopEntity
import com.example.data.database.MetrobusStopEntity
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.data.model.routing.PlannedItinerary
import com.example.data.model.routing.PlannedLeg
import com.example.data.model.routing.TransitMode
import com.example.ui.map.MapFilter
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.CustomPlaceType
import com.example.ui.map.components.CustomPlacesMarkersRenderer
import com.example.ui.map.components.ItineraryMapRenderer
import com.example.ui.map.components.MapOverlaysComposer
import com.example.ui.map.components.MapTapHandler
import com.example.ui.map.components.UserAndDestinationMarkersRenderer
import com.example.ui.map.components.ValenbisiMarkersRenderer
import com.example.ui.map.components.ValenbisiStation
import com.example.ui.map.components.ViewportTransitFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapModularizationRegressionTest {

    private lateinit var context: Context
    private lateinit var mapView: MapView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Configuration.getInstance().userAgentValue = "TestApp"
        mapView = MapView(context)
    }

    // -------------------------------------------------------------
    // 1. ViewportTransitFilter tests
    // -------------------------------------------------------------
    @Test
    fun testViewportTransitFilter_busStopsAndZoomLimits() {
        val busStops = listOf(
            GeoportalStopEntity(id_parada = "1", denominacion = "Parada 1", suprimida = 0, lat = 39.4699, lon = -0.3763, lineas = "5,6"),
            GeoportalStopEntity(id_parada = "2", denominacion = "Parada 2", suprimida = 0, lat = 39.5000, lon = -0.3000, lineas = "10"),
            GeoportalStopEntity(id_parada = "3", denominacion = "Parada 3", suprimida = 0, lat = 38.0000, lon = -0.3763, lineas = "1")
        )
        val metrobusStops = listOf(
            MetrobusStopEntity(id_parada = "M1", denominacion = "Metrobus 1", lat = 39.4699, lon = -0.3763, lineas = "150")
        )
        val valenbisiStations = listOf(
            ValenbisiStation(
                gid = 1,
                name = "Estacion 1",
                number = 101,
                address = "Calle 1",
                open = true,
                available = 5,
                free = 10,
                total = 15,
                ticket = false,
                latitude = 39.4699,
                longitude = -0.3763
            )
        )

        // Case A: When all modes are active and zoom < 15.2, bus and metrobus stops should be hidden
        val allModesFilter = MapFilter(isFavorites = false, showBus = true, showMetro = true, showCercanias = true, showMetrobus = true, showValenbisi = true)
        val box = BoundingBox(39.48, -0.37, 39.46, -0.38)

        val resultZoomOut = ViewportTransitFilter.filterForViewport(
            boundingBox = box,
            busStops = busStops,
            metrobusStops = metrobusStops,
            valenbisiStations = valenbisiStations,
            mapFilter = allModesFilter,
            currentZoom = 14.0
        )
        assertFalse("Bus stops should be hidden when all modes active and zoom < 15.2", resultZoomOut.showBus)
        assertFalse("Metrobus stops should be hidden when all modes active and zoom < 15.2", resultZoomOut.showMetrobus)
        assertTrue("Valenbisi should be visible", resultZoomOut.showValenbisi)
        assertEquals(1, resultZoomOut.valenbisiStationsInViewport.size)

        // Create a list with > 50 stops to exercise QuadTree indexing (when <= 50, all stops are kept)
        val dummyOutsideStops = (4..60).map { i ->
            GeoportalStopEntity(id_parada = "out_$i", denominacion = "Fuera $i", suprimida = 0, lat = 41.0 + (i * 0.01), lon = 1.0, lineas = "1")
        }
        val allTestBusStops = busStops + dummyOutsideStops

        // Case B: When zoom >= 15.2, bus and metrobus should be active and filtered for viewport
        val resultZoomIn = ViewportTransitFilter.filterForViewport(
            boundingBox = box,
            busStops = allTestBusStops,
            metrobusStops = metrobusStops,
            valenbisiStations = valenbisiStations,
            mapFilter = allModesFilter,
            currentZoom = 15.5
        )
        assertFalse("Should not be favorites mode when busStops.size > 50", resultZoomIn.isFavoritesMode)
        assertTrue("Bus stops should be visible when zoom >= 15.2", resultZoomIn.showBus)
        assertTrue("Metrobus stops should be visible when zoom >= 15.2", resultZoomIn.showMetrobus)
        // Only stops within expanded viewport (39.46..39.48 +/- margin) should be included
        assertTrue(resultZoomIn.busStopsInViewport.any { it.id_parada == "1" })
        assertFalse("Out of range stop should not be included", resultZoomIn.busStopsInViewport.any { it.id_parada == "3" })
        assertFalse("Dummy outside stop should not be included", resultZoomIn.busStopsInViewport.any { it.id_parada == "out_4" })

        // Case C: Favorites mode always shows stops regardless of zoom
        val favFilter = MapFilter(isFavorites = true, showBus = false, showMetrobus = false)
        val resultFav = ViewportTransitFilter.filterForViewport(
            boundingBox = box,
            busStops = busStops,
            metrobusStops = metrobusStops,
            valenbisiStations = valenbisiStations,
            mapFilter = favFilter,
            currentZoom = 12.0
        )
        assertTrue(resultFav.showBus)
        assertTrue(resultFav.isFavoritesMode)
        assertEquals(3, resultFav.busStopsInViewport.size)
    }

    // -------------------------------------------------------------
    // 2. CustomPlacesMarkersRenderer tests
    // -------------------------------------------------------------
    @Test
    fun testCustomPlacesMarkersRenderer_deduplicationAndVisibility() {
        val home = RecentSearch(id = "fav_home", type = "home", title = "Casa", subtitle = "Mi casa", latitude = 39.47, longitude = -0.37, showOnMap = true)
        val work = RecentSearch(id = "fav_work", type = "work", title = "Trabajo", subtitle = "Oficina", latitude = 39.48, longitude = -0.38, showOnMap = true)
        val customFavVisible = RecentSearch(id = "fav_gym", type = "gym", title = "Gimnasio", subtitle = "Calle Gym", latitude = 39.49, longitude = -0.39, showOnMap = true)
        val customFavHidden = RecentSearch(id = "fav_hidden", type = "other", title = "Oculto", subtitle = "No ver", latitude = 39.50, longitude = -0.40, showOnMap = false)
        val duplicateHome = RecentSearch(id = "fav_home2", type = "home", title = "Casa Duplicada", subtitle = "Misma coord", latitude = 39.47, longitude = -0.37, showOnMap = true)

        val recycled = mutableListOf<Marker>()
        var selectedItem: SelectedMapItem? = null

        val activeCount = CustomPlacesMarkersRenderer.renderCustomPlaces(
            context = context,
            mapView = mapView,
            homeLocation = home,
            workLocation = work,
            customFavorites = listOf(customFavVisible, customFavHidden, duplicateHome),
            currentZoom = 15.0,
            isDarkMode = false,
            recycledCustomFavoriteMarkers = recycled,
            onSelectItem = { selectedItem = it }
        )

        // Should render Home, Work, and Gym. Hidden and Duplicate should NOT be rendered
        assertEquals("Should have exactly 3 active custom place markers", 3, activeCount)
        assertEquals(3, recycled.size)

        // Marker 1 is Home
        assertEquals("Casa", recycled[0].title)
        assertEquals("Mi casa", recycled[0].snippet)
        assertEquals(39.47, recycled[0].position.latitude, 0.0001)
        assertTrue(recycled[0].isEnabled)

        // Marker 2 is Work
        assertEquals("Trabajo", recycled[1].title)
        assertEquals("Oficina", recycled[1].snippet)
        assertEquals(39.48, recycled[1].position.latitude, 0.0001)
        assertTrue(recycled[1].isEnabled)

        // Marker 3 is Gym
        assertEquals("Gimnasio", recycled[2].title)
        assertEquals("Calle Gym", recycled[2].snippet)
        assertEquals(39.49, recycled[2].position.latitude, 0.0001)
        assertTrue(recycled[2].isEnabled)

        // Test recycling: re-run with only Home
        val activeCount2 = CustomPlacesMarkersRenderer.renderCustomPlaces(
            context = context,
            mapView = mapView,
            homeLocation = home,
            workLocation = null,
            customFavorites = emptyList(),
            currentZoom = 15.0,
            isDarkMode = false,
            recycledCustomFavoriteMarkers = recycled,
            onSelectItem = {}
        )
        assertEquals(1, activeCount2)
        assertEquals(3, recycled.size)
        assertTrue(recycled[0].isEnabled)
        assertFalse("Unused recycled markers must be disabled", recycled[1].isEnabled)
        assertFalse("Unused recycled markers must be disabled", recycled[2].isEnabled)
    }

    // -------------------------------------------------------------
    // 3. UserAndDestinationMarkersRenderer tests
    // -------------------------------------------------------------
    @Test
    fun testUserAndDestinationMarkersRenderer() {
        val userLoc = GeoPoint(39.4699, -0.3763)
        var userMarker: Marker? = null

        // Initial creation
        userMarker = UserAndDestinationMarkersRenderer.updateUserLocationMarker(
            context = context,
            mapView = mapView,
            userLocation = userLoc,
            existingUserMarker = userMarker
        )
        assertNotNull(userMarker)
        assertEquals("Tu ubicación", userMarker!!.title)
        assertEquals(userLoc.latitude, userMarker!!.position.latitude, 0.0001)

        // Destination Marker
        val destLoc = GeoPoint(39.4750, -0.3700)
        var destMarker: Marker? = null
        var selectedItem: SelectedMapItem? = null

        destMarker = UserAndDestinationMarkersRenderer.updateDestinationMarker(
            context = context,
            mapView = mapView,
            destinationLocation = destLoc,
            destinationTitle = "Plaza del Ayuntamiento",
            selectedItinerary = null,
            isDarkMode = false,
            existingDestinationMarker = destMarker,
            onSelectItem = { selectedItem = it }
        )
        assertNotNull(destMarker)
        assertTrue(destMarker!!.isEnabled)
        assertEquals("Plaza del Ayuntamiento", destMarker!!.title)
        assertEquals(destLoc.latitude, destMarker!!.position.latitude, 0.0001)

        // When active itinerary is present, destination marker is disabled/hidden
        val mockItinerary = PlannedItinerary(
            id = "itin_1",
            totalDurationSeconds = 600,
            startTime = "10:00",
            endTime = "10:10",
            recommendedStartTime = "10:00",
            formattedDuration = "10 min",
            formattedDepartureTime = "10:00",
            formattedArrivalTime = "10:10",
            transfersCount = 0,
            legs = emptyList(),
            viability = com.example.data.model.routing.ItineraryViability.VIABLE_ON_TIME
        )
        destMarker = UserAndDestinationMarkersRenderer.updateDestinationMarker(
            context = context,
            mapView = mapView,
            destinationLocation = destLoc,
            destinationTitle = "Plaza del Ayuntamiento",
            selectedItinerary = mockItinerary,
            isDarkMode = false,
            existingDestinationMarker = destMarker,
            onSelectItem = {}
        )
        assertFalse("Destination marker must be disabled when an itinerary is active", destMarker!!.isEnabled)
    }

    // -------------------------------------------------------------
    // 4. ItineraryMapRenderer tests
    // -------------------------------------------------------------
    @Test
    fun testItineraryMapRenderer_renderingAndDirectTransferDetection() {
        val legWalk = PlannedLeg(
            mode = TransitMode.WALK,
            fromName = "Origen A",
            toName = "Estación Metro",
            fromLat = 39.469,
            fromLon = -0.376,
            toLat = 39.470,
            toLon = -0.375,
            durationSeconds = 120L,
            distanceMeters = 100.0,
            formattedDuration = "2 min",
            startTime = "12:00",
            endTime = "12:02",
            formattedStartTime = "12:00",
            formattedEndTime = "12:02",
            agencyName = null,
            routeShortName = null,
            routeLongName = null,
            headsign = null,
            routeColorHex = "#64748B",
            fromStopId = null,
            toStopId = null,
            geometry = listOf(GeoPoint(39.469, -0.376), GeoPoint(39.470, -0.375))
        )
        val legMetro = PlannedLeg(
            mode = TransitMode.SUBWAY,
            routeShortName = "3",
            fromName = "Estación Metro",
            toName = "Estación Transbordo",
            fromLat = 39.470,
            fromLon = -0.375,
            toLat = 39.480,
            toLon = -0.370,
            durationSeconds = 300L,
            distanceMeters = 1200.0,
            formattedDuration = "5 min",
            startTime = "12:02",
            endTime = "12:07",
            formattedStartTime = "12:02",
            formattedEndTime = "12:07",
            agencyName = "Metrovalencia",
            routeLongName = "Línea 3",
            headsign = "Rafelbunyol",
            routeColorHex = "#E30613",
            fromStopId = "ST1",
            toStopId = "ST2",
            geometry = listOf(GeoPoint(39.470, -0.375), GeoPoint(39.480, -0.370))
        )
        val legMetro2 = PlannedLeg(
            mode = TransitMode.SUBWAY,
            routeShortName = "5",
            fromName = "Estación Transbordo",
            toName = "Destino Final",
            fromLat = 39.480,
            fromLon = -0.370,
            toLat = 39.490,
            toLon = -0.360,
            durationSeconds = 240L,
            distanceMeters = 1000.0,
            formattedDuration = "4 min",
            startTime = "12:07",
            endTime = "12:11",
            formattedStartTime = "12:07",
            formattedEndTime = "12:11",
            agencyName = "Metrovalencia",
            routeLongName = "Línea 5",
            headsign = "Marítim",
            routeColorHex = "#008144",
            fromStopId = "ST2",
            toStopId = "ST3",
            geometry = listOf(GeoPoint(39.480, -0.370), GeoPoint(39.490, -0.360))
        )

        val itinerary = PlannedItinerary(
            id = "itinerary_test_1",
            totalDurationSeconds = 660L,
            startTime = "12:00",
            endTime = "12:11",
            recommendedStartTime = "12:00",
            formattedDuration = "11 min",
            formattedDepartureTime = "12:00",
            formattedArrivalTime = "12:11",
            transfersCount = 1,
            legs = listOf(legWalk, legMetro, legMetro2),
            viability = com.example.data.model.routing.ItineraryViability.VIABLE_ON_TIME
        )

        mapView.overlays.clear()
        val zoomedId = ItineraryMapRenderer.renderItinerary(
            context = context,
            mapView = mapView,
            itinerary = itinerary,
            currentZoom = 14.0,
            isDarkMode = false,
            lastZoomedItineraryId = null
        )

        assertEquals("itinerary_test_1", zoomedId)
        // Overlays must contain polylines and origin/transfer/destination markers
        val polylines = mapView.overlays.filterIsInstance<Polyline>()
        val markers = mapView.overlays.filterIsInstance<Marker>()

        assertEquals("Should have 3 polylines for the 3 legs", 3, polylines.size)
        assertTrue("Should have markers rendered", markers.isNotEmpty())

        // Verify origin marker
        val originMarker = markers.firstOrNull { it.title == "Origen A" }
        assertNotNull("Origin marker must be rendered", originMarker)

        // Verify direct transfer detection between L3 and L5 at 'Estación Transbordo'
        val transferMarker = markers.firstOrNull { it.title?.contains("➔") == true }
        assertNotNull("Direct transfer marker with transition arrow must be detected", transferMarker)
        assertTrue(transferMarker!!.title!!.contains("L3"))
        assertTrue(transferMarker.title!!.contains("L5"))

        // Verify destination marker
        val destMarker = markers.firstOrNull { it.title == "Destino Final" }
        assertNotNull("Destination marker must be rendered", destMarker)
    }

    // -------------------------------------------------------------
    // 5. MapOverlaysComposer layer order test
    // -------------------------------------------------------------
    @Test
    fun testMapOverlaysComposer_layerOrdering() {
        val mapEventsOverlay = MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        val busMarker = Marker(mapView).apply { title = "Bus" }
        val metroMarker = Marker(mapView).apply { title = "Metro" }
        val customFavMarker = Marker(mapView).apply { title = "Casa" }
        val destMarker = Marker(mapView).apply { title = "Destino" }
        val userMarker = Marker(mapView).apply { title = "Tu ubicación" }

        val params = MapOverlaysComposer.ComposeParams(
            context = context,
            mapView = mapView,
            selectedItinerary = null,
            showMetro = true,
            showCercanias = false,
            showMetrobus = false,
            showValenbisi = false,
            currentZoom = 15.0,
            isDarkMode = false,
            mapEventsOverlay = mapEventsOverlay,
            destinationMarker = destMarker,
            destinationLocation = GeoPoint(39.47, -0.37),
            userMarker = userMarker,
            userLocation = GeoPoint(39.46, -0.37),
            activeBusCount = 1,
            activeClusterCount = 0,
            recycledBusMarkers = listOf(busMarker),
            recycledClusterMarkers = emptyList(),
            activeMetrobusCount = 0,
            activeMetrobusClusterCount = 0,
            recycledMetrobusMarkers = emptyList(),
            recycledMetrobusClusterMarkers = emptyList(),
            activeValenbisiCount = 0,
            activeValenbisiClusterCount = 0,
            recycledValenbisiMarkers = emptyList(),
            recycledValenbisiClusterMarkers = emptyList(),
            validMetroStationsCount = 1,
            recycledMetroMarkers = listOf(metroMarker),
            validCercaniasStationsCount = 0,
            recycledCercaniasMarkers = emptyList(),
            activeCustomFavCount = 1,
            recycledCustomFavoriteMarkers = listOf(customFavMarker),
            lastZoomedItineraryId = null
        )

        MapOverlaysComposer.composeOverlays(params)

        val overlays = mapView.overlays
        assertTrue(overlays.isNotEmpty())
        assertEquals("First overlay must be MapEventsOverlay", mapEventsOverlay, overlays.first())
        assertEquals("Last overlay must be User Marker", userMarker, overlays.last())

        val busIdx = overlays.indexOf(busMarker)
        val metroIdx = overlays.indexOf(metroMarker)
        val favIdx = overlays.indexOf(customFavMarker)
        val destIdx = overlays.indexOf(destMarker)
        val userIdx = overlays.indexOf(userMarker)

        assertTrue("Metro and Bus stops must be below Custom Favorites", busIdx < favIdx && metroIdx < favIdx)
        assertTrue("Custom Favorites must be below Destination", favIdx < destIdx)
        assertTrue("Destination must be below User Marker", destIdx < userIdx)
    }

    // -------------------------------------------------------------
    // 6. MapTapHandler disambiguation test
    // -------------------------------------------------------------
    @Test
    fun testMapTapHandler_disambiguationMenu() {
        // Prepare two close items at identical location
        val station = com.example.data.model.MetroStation(
            id = "st_1",
            name = "Xàtiva",
            lines = listOf("3", "5", "9"),
            description = "Estación de Metro Xàtiva",
            latitude = 39.468,
            longitude = -0.377
        )
        val stop = GeoportalStopEntity(
            id_parada = "2001",
            denominacion = "Estació del Nord",
            suprimida = 0,
            lat = 39.468,
            lon = -0.377,
            lineas = "5,6,7"
        )

        // Fake tap point
        val tapPoint = GeoPoint(39.468, -0.377)

        var disambiguationList: List<SelectedMapItem>? = null
        var singleSelected: SelectedMapItem? = null
        var mapClicked = false

        val tapContext = MapTapHandler.TapContext(
            showMetro = true,
            showCercanias = false,
            showBus = true,
            showMetrobus = false,
            showValenbisi = false,
            currentZoomLevel = 16.0,
            isOnlyMetroSelected = false,
            isOnlyCercaniasSelected = false,
            currentMetroStations = listOf(station),
            currentCercaniasStations = emptyList(),
            currentBusStopsInViewport = listOf(stop),
            currentMetrobusStopsInViewport = emptyList(),
            currentValenbisiStations = emptyList(),
            currentMetroPositions = mapOf(station.name to GeoPoint(station.latitude!!, station.longitude!!)),
            currentCercaniasPositions = emptyMap(),
            currentDestinationLocation = null,
            currentDestinationTitle = null,
            currentCustomFavorites = emptyList(),
            currentOnSelectItem = { singleSelected = it },
            currentOnMapClick = { mapClicked = true },
            currentOnShowDisambiguationMenu = { disambiguationList = it }
        )

        MapTapHandler.handleTapAtGeoPoint(context, mapView, tapPoint, tapContext)

        assertNotNull("Disambiguation menu should be triggered when multiple items are tapped together", disambiguationList)
        assertEquals(2, disambiguationList!!.size)
        assertTrue(disambiguationList!!.any { it is SelectedMapItem.Metro })
        assertTrue(disambiguationList!!.any { it is SelectedMapItem.BusStop })
        assertFalse("Map click should NOT be called", mapClicked)
    }
}
