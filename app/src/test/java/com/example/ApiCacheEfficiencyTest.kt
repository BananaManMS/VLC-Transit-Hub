package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.TransitCardEntity
import com.example.data.model.WeatherService
import com.example.data.repository.MetroAlertsRepository
import com.example.data.repository.MetroCardRepository
import com.example.data.repository.MetrobusRepository
import com.example.data.repository.RealTimeTransitRepository
import com.example.data.repository.RemoteAnnouncementRepository
import com.example.data.repository.ValenbisiRepository
import com.example.data.repository.renfe.GtfsCacheManager
import com.example.data.repository.renfe.RenfeAlertsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiCacheEfficiencyTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        RealTimeTransitRepository.clearAllCaches()
        MetroAlertsRepository.clearCacheForTesting()
        RenfeAlertsRepository.clearCacheForTesting()
        ValenbisiRepository.clearCacheForTesting()
        MetrobusRepository.clearAllCaches()
        WeatherService.clearCacheForTesting()
        RemoteAnnouncementRepository.resetSessionCacheForTesting()
        MetroCardRepository.resetSessionStateForTesting()
    }

    @After
    fun tearDown() {
        RealTimeTransitRepository.overrideHttpClientForTesting = null
        RealTimeTransitRepository.clearAllCaches()
        WeatherService.overrideHttpClientForTesting = null
        WeatherService.clearCacheForTesting()
        MetroAlertsRepository.clearCacheForTesting()
        RenfeAlertsRepository.clearCacheForTesting()
        ValenbisiRepository.clearCacheForTesting()
        MetrobusRepository.clearAllCaches()
        RemoteAnnouncementRepository.resetSessionCacheForTesting()
        MetroCardRepository.resetSessionStateForTesting()
        db.close()
    }

    private fun buildMockClient(handler: (url: String, callIndex: Int) -> Pair<Int, String>): Pair<OkHttpClient, AtomicInteger> {
        val counter = AtomicInteger(0)
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val idx = counter.incrementAndGet()
            val (code, body) = handler(request.url.toString(), idx)
            val mediaType = when {
                body.trimStart().startsWith("<") -> "application/xml"
                else -> "application/json"
            }.toMediaType()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(body.toResponseBody(mediaType))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build() to counter
    }

    @Test
    fun `Metro real-time departures cache blocks redundant calls and survives empty HTTP 200 without caching errors`() = runBlocking {
        var shouldFailNext = true
        val validJson = """
            {
              "success": true,
              "data": {
                "trenes": [
                  {"linea": 3, "destino": "Aeroport", "minutos": 4, "segundos": 240}
                ]
              }
            }
        """.trimIndent()
        val (client, calls) = buildMockClient { _, _ ->
            if (shouldFailNext) 500 to "Server error"
            else 200 to validJson
        }
        RealTimeTransitRepository.overrideHttpClientForTesting = client

        // 1. Error must NOT poison cache
        val failedResult = RealTimeTransitRepository.getMetroLiveArrivals("15", forceRefresh = false)
        assertTrue(failedResult.isEmpty())
        assertEquals(1, calls.get())
        assertEquals(0L, RealTimeTransitRepository.getMetroCacheRemainingMs("15"))

        // 2. Retry succeeds and populates 30s cache
        shouldFailNext = false
        val firstOk = RealTimeTransitRepository.getMetroLiveArrivals("15", forceRefresh = false)
        assertEquals(1, firstOk.size)
        assertEquals(2, calls.get())
        assertTrue(RealTimeTransitRepository.getMetroCacheRemainingMs("15") > 0L)

        // 3. Simulated pull-to-refresh within 30s cooldown (forceRefresh = false) hits cache (0 HTTP calls)
        val secondCached = RealTimeTransitRepository.getMetroLiveArrivals("15", forceRefresh = false)
        assertEquals(1, secondCached.size)
        assertEquals(2, calls.get())

        // 4. Valid empty HTTP 200 JSON (e.g. night hours) is also cached cleanly
        RealTimeTransitRepository.clearAllCaches()
        val (emptyClient, emptyCalls) = buildMockClient { _, _ ->
            200 to """{"success": true, "data": {"trenes": []}}"""
        }
        RealTimeTransitRepository.overrideHttpClientForTesting = emptyClient
        val empty1 = RealTimeTransitRepository.getMetroLiveArrivals("15", forceRefresh = false)
        val empty2 = RealTimeTransitRepository.getMetroLiveArrivals("15", forceRefresh = false)
        assertTrue(empty1.isEmpty())
        assertTrue(empty2.isEmpty())
        assertEquals(1, emptyCalls.get())
    }

    @Test
    fun `EMT live arrivals cache respects 30s TTL and skips redundant requests`() = runBlocking {
        val validXml = """
            <buses>
              <bus><linea>19</linea><destino>MALVARROSA</destino><minutos>5</minutos></bus>
            </buses>
        """.trimIndent()
        val (client, calls) = buildMockClient { _, _ -> 200 to validXml }
        RealTimeTransitRepository.overrideHttpClientForTesting = client

        val first = RealTimeTransitRepository.getEmtLiveArrivals("100", forceRefresh = false, includeScheduled = false)
        assertTrue(RealTimeTransitRepository.getEmtCacheRemainingMs("100") > 0L)
        val second = RealTimeTransitRepository.getEmtLiveArrivals("100", forceRefresh = false, includeScheduled = false)

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(1, calls.get())
    }

    @Test
    fun `Metrobus and Cercanias real-time caches are unified at 30s and do not poison on HTTP 500`() = runBlocking {
        assertEquals("Metrobus RT TTL must be 30s", 30_000L, MetrobusRepository.RT_CACHE_TTL_MS)
        assertEquals("Cercanias GTFS-RT TTL must be 30s", 30_000L, GtfsCacheManager.CACHE_TTL_MS)

        var fail = true
        val validJson = """
            {
              "success": true,
              "data": [
                {"line": "160", "route": "València", "estimations": [{"minutesToArrival": 6, "vehicleId": "V1"}]}
              ]
            }
        """.trimIndent()
        val (client, calls) = buildMockClient { _, _ ->
            if (fail) 500 to "err" else 200 to validJson
        }
        val repo = MetrobusRepository(db, client, context)

        val errRes = repo.fetchRealTimeEstimations("4410", forceRefresh = false)
        assertTrue(errRes.isEmpty())
        assertEquals(1, calls.get())

        fail = false
        val okRes1 = repo.fetchRealTimeEstimations("4410", forceRefresh = false)
        assertTrue(MetrobusRepository.getRtCacheRemainingMs("4410") > 0L)
        val okRes2 = repo.fetchRealTimeEstimations("4410", forceRefresh = false)
        assertEquals(1, okRes1.size)
        assertEquals(1, okRes2.size)
        assertEquals(2, calls.get())
    }

    @Test
    fun `MetroAlertsRepository keeps 15min alerts TTL and 6h news TTL and ignores HTTP errors`() = runBlocking {
        assertEquals("MetroAlerts TTL must be 15 min", 15 * 60 * 1000L, MetroAlertsRepository.CACHE_DURATION_MS)
        assertEquals("MetroNews TTL must be 6 hours", 6 * 60 * 60 * 1000L, MetroAlertsRepository.NEWS_CACHE_DURATION_MS)

        var avisosCalls = 0
        var noticiasCalls = 0
        var failAvisos = true
        val (client, _) = buildMockClient { url, _ ->
            when {
                url.contains("/avisos") -> {
                    avisosCalls++
                    if (failAvisos) 503 to "Service Unavailable"
                    else 200 to """{"success":true,"data":{"accesibilidad":[],"prioritarios":[],"avisos":[]}}"""
                }
                url.contains("/noticias") -> {
                    noticiasCalls++
                    200 to """{"success":true,"data":{"noticias":[{"id":1,"titulo":"Aviso","descripcion":"Detalle"}]}}"""
                }
                else -> 404 to "{}"
            }
        }
        val repo = MetroAlertsRepository(client)

        // 1. Failed fetch must not set valid cache
        repo.fetchAllAlerts(force = false, includeNews = false)
        assertFalse(MetroAlertsRepository.hasValidCache())
        assertEquals(1, avisosCalls)

        // 2. Retry populates 15-min alerts cache (news not yet requested)
        failAvisos = false
        repo.fetchAllAlerts(force = false, includeNews = false)
        assertTrue(MetroAlertsRepository.hasValidCache())
        assertFalse(MetroAlertsRepository.hasValidNewsCache())
        assertEquals(2, avisosCalls)
        assertEquals(0, noticiasCalls)

        // 3. Opening Avisos tab within 15 min reuses alerts cache (0 calls) and fetches news once
        repo.fetchAllAlerts(force = false, includeNews = true)
        assertTrue(MetroAlertsRepository.hasValidNewsCache())
        assertEquals(2, avisosCalls)
        assertEquals(1, noticiasCalls)

        // 4. Pulling to refresh Avisos tab again within 15 min makes 0 HTTP calls for either endpoint
        repo.fetchAllAlerts(force = false, includeNews = true)
        assertEquals(2, avisosCalls)
        assertEquals(1, noticiasCalls)
    }

    @Test
    fun `RenfeAlertsRepository keeps unified 15min TTL and caches empty Valencia results cleanly`() = runBlocking {
        assertEquals("RenfeAlerts TTL must be 15 min", 15 * 60 * 1000L, RenfeAlertsRepository.CACHE_DURATION_MS)

        val (client, calls) = buildMockClient { _, _ ->
            200 to """{"header":{"timestamp":1700000000},"entity":[]}"""
        }
        val repo = RenfeAlertsRepository(context, db, client)

        val first = repo.fetchActiveAlerts(force = false)
        assertTrue(first.isEmpty())
        assertTrue(RenfeAlertsRepository.hasValidCache())
        assertEquals(1, calls.get())

        val second = repo.fetchActiveAlerts(force = false)
        assertTrue(second.isEmpty())
        assertEquals(1, calls.get())
    }

    @Test
    fun `ValenbisiRepository keeps 10min TTL and offline fallback never poisons cache`() = runBlocking {
        assertEquals("Valenbisi TTL must be 10 min", 10 * 60 * 1000L, ValenbisiRepository.CACHE_DURATION_MS)

        var failAll = true
        val cityBikesJson = """
            {
              "network": {
                "stations": [
                  {"name":"001_PLAZA","free_bikes":5,"empty_slots":10,"latitude":39.47,"longitude":-0.37,"extra":{"number":1,"slots":15}}
                ]
              }
            }
        """.trimIndent()

        val (client, calls) = buildMockClient { url, _ ->
            if (failAll) 500 to "offline"
            else if (url.contains("citybik.es")) 200 to cityBikesJson
            else 500 to "unused"
        }
        val repo = ValenbisiRepository(context, client)

        // 1. Offline attempt returns fallback landmarks WITHOUT poisoning cache
        val offlineList = repo.fetchStations(force = false)
        assertTrue(offlineList.isNotEmpty())
        assertFalse("Offline fallback must not mark cache valid", ValenbisiRepository.hasValidCache())
        val callsAfterOffline = calls.get()

        // 2. When connectivity returns, next fetch hits CityBikes and establishes 10-min valid cache
        failAll = false
        val onlineList = repo.fetchStations(force = false)
        assertEquals(1, onlineList.size)
        assertTrue(ValenbisiRepository.hasValidCache())
        assertTrue(ValenbisiRepository.getCacheRemainingMs() > 0L)
        val callsAfterOnline = calls.get()
        assertEquals(callsAfterOffline + 1, callsAfterOnline)

        // 3. Subsequent refresh within 15 min makes 0 HTTP calls
        val cachedList = repo.fetchStations(force = false)
        assertEquals(1, cachedList.size)
        assertEquals(callsAfterOnline, calls.get())
    }

    @Test
    fun `RemoteAnnouncementRepository caches within session and refetches on app restart with offline disk fallback`() = runBlocking {
        var offline = false
        val jsonV1 = """
            [
              {"id":"av1","activo":true,"tipo":"azul","titulo_es":"Hola","mensaje_es":"Mundo"}
            ]
        """.trimIndent()
        val jsonV2 = """
            [
              {"id":"av1","activo":true,"tipo":"azul","titulo_es":"Hola","mensaje_es":"Mundo"},
              {"id":"av2","activo":true,"tipo":"verde","titulo_es":"Nuevo","mensaje_es":"Aviso"}
            ]
        """.trimIndent()
        var currentJson = jsonV1
        val (client, calls) = buildMockClient { _, _ ->
            if (offline) 500 to "offline" else 200 to currentJson
        }
        val repo = RemoteAnnouncementRepository(context, client)
        repo.clearCacheForTesting()

        // 1. First fetch downloads JSON
        assertFalse(RemoteAnnouncementRepository.hasValidSessionCache())
        val (active1, all1) = repo.fetchActiveAndAllAnnouncements(force = false)
        assertTrue(RemoteAnnouncementRepository.hasValidSessionCache())
        assertEquals(1, active1.size)
        assertEquals(1, all1.size)
        assertEquals(1, calls.get())

        // 2. Repeated fetch within the same app session hits in-memory cache (0 HTTP calls)
        val (active2, all2) = repo.fetchActiveAndAllAnnouncements(force = false)
        assertEquals(1, active2.size)
        assertEquals(1, all2.size)
        assertEquals(1, calls.get())

        // 3. Simulate closing and reopening the app: session cache clears while disk file remains
        RemoteAnnouncementRepository.resetSessionCacheForTesting()
        assertFalse(RemoteAnnouncementRepository.hasValidSessionCache())
        currentJson = jsonV2
        val (active3, all3) = repo.fetchActiveAndAllAnnouncements(force = false)
        assertEquals(2, active3.size)
        assertEquals(2, all3.size)
        assertEquals(2, calls.get())

        // 4. Simulate reopening while offline: falls back to disk JSON cleanly
        RemoteAnnouncementRepository.resetSessionCacheForTesting()
        offline = true
        val (activeOffline, _) = repo.fetchActiveAndAllAnnouncements(force = false)
        assertEquals(2, activeOffline.size)
        assertEquals(3, calls.get())
    }

    @Test
    fun `MetroCardRepository refreshes once on app open, skips repeated auto-refreshes, and allows manual reload`() = runBlocking {
        val cardJson = """
            {"titulo":"SUMA 10","clase":"MULTIVIAJE","saldo":"7 viajes"}
        """.trimIndent()
        val (client, calls) = buildMockClient { _, _ -> 200 to cardJson }
        val repo = MetroCardRepository(context, db, client)

        // Seed one saved card
        repo.saveCard(
            TransitCardEntity(
                cardNumber = "1234567890",
                assignedName = "Mi SUMA",
                defaultName = "SUMA 10",
                cardType = "MULTIVIAJE",
                remainingValue = "8 viajes",
                lastUpdated = System.currentTimeMillis(),
                detailsJson = "{}",
                customOrder = 0,
                showOnHome = true,
                isManuallyInactive = false
            )
        )

        // 1. First app-open refresh hits API once
        assertTrue(repo.refreshTransitCardsOnAppOpen())
        assertEquals(1, calls.get())

        // 2. Subsequent auto-refresh calls in the same session do nothing (0 HTTP calls)
        assertFalse(repo.refreshTransitCardsOnAppOpen())
        assertFalse(repo.refreshTransitCardsIfNeeded())
        assertEquals(1, calls.get())

        // 3. Explicit manual user reload refreshes immediately
        repo.refreshTransitCards(minCooldownMs = 0L)
        assertEquals(2, calls.get())

        // 4. Simulated app close & reopen allows app-open refresh once again
        MetroCardRepository.resetSessionStateForTesting()
        assertTrue(repo.refreshTransitCardsOnAppOpen())
        assertEquals(3, calls.get())
    }

    @Test
    fun `WeatherService tolerates GPS coordinate jitter and returns null offline instead of dummy 22C`() = runBlocking {
        var fail = true
        val weatherJson = """
            {
              "current_weather": {"temperature": 18.4, "weathercode": 0, "windspeed": 9.0, "time": "2026-11-22T14:00"},
              "hourly": {
                "time": ["2026-11-22T14:00"],
                "temperature_2m": [18.4],
                "weathercode": [0],
                "precipitation_probability": [5]
              }
            }
        """.trimIndent()
        val (client, calls) = buildMockClient { _, _ ->
            if (fail) 500 to "offline" else 200 to weatherJson
        }
        WeatherService.overrideHttpClientForTesting = client

        // 1. Offline returns null (does not overwrite caller's persisted weather with dummy 22C)
        val offline = WeatherService.fetchWeatherDataByCoordsOrNull(39.46975, -0.37639, "Valencia")
        assertNull(offline)
        assertEquals(1, calls.get())

        // 2. Online succeeds
        fail = false
        val online = WeatherService.fetchWeatherDataByCoordsOrNull(39.46975, -0.37639, "Valencia")
        assertEquals(18, online?.currentTempCelsius)
        assertEquals(2, calls.get())

        // 3. Slight GPS jitter rounds to same 2-decimal bucket and hits 15-min cache (0 HTTP calls)
        val jittered = WeatherService.fetchWeatherDataByCoordsOrNull(39.47010, -0.37590, "Valencia")
        assertEquals(18, jittered?.currentTempCelsius)
        assertEquals(2, calls.get())
    }

    @Test
    fun `All viatgem-proxy endpoints and NetworkModule interceptor send X-App-Secret header`() = runBlocking {
        val expectedSecret = BuildConfig.METROVALENCIA_API_SECRET.trim()
        val capturedHeaders = mutableMapOf<String, List<String>>()

        val captureClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedHeaders[req.url.encodedPath] = req.headers("X-App-Secret")
                val body = when {
                    req.url.encodedPath.contains("/avisos") ->
                        """{"success":true,"data":{"accesibilidad":[],"prioritarios":[],"avisos":[]}}"""
                    req.url.encodedPath.contains("/noticias") ->
                        """{"success":true,"data":{"noticias":[]}}"""
                    req.url.encodedPath.contains("/llegadas") ->
                        """{"success":true,"data":{"trenes":[]}}"""
                    req.url.encodedPath.contains("/tarjetas/consultar") ->
                        """{"success":true,"data":{"encontrada":true,"tarjetas":[{"titulo":"SUMA 10","saldo":5}]}}"""
                    req.url.encodedPath.contains("/metrobus/estimacion/") ->
                        """{"success":true,"data":[]}"""
                    else -> """{"ok":true}"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        // 1. Metrovalencia avisos & noticias
        val alertsRepo = MetroAlertsRepository(captureClient)
        alertsRepo.fetchAllAlerts(force = true, includeNews = true)
        assertEquals(listOf(expectedSecret), capturedHeaders["/v1/avisos"])
        assertEquals(listOf(expectedSecret), capturedHeaders["/v1/noticias"])

        // 2. Metrovalencia salidas
        RealTimeTransitRepository.overrideHttpClientForTesting = captureClient
        RealTimeTransitRepository.getMetroLiveArrivals("15", forceRefresh = true)
        assertEquals(listOf(expectedSecret), capturedHeaders["/v1/estaciones/15/llegadas"])

        // 3. Metrovalencia tarjetas
        com.example.ui.metro.MetroCardNetworkSource.fetchCardFromNetwork("1234567890", captureClient)
        assertEquals(listOf(expectedSecret), capturedHeaders["/v1/tarjetas/consultar"])

        // 4. Metrobús salidas
        val metrobusRepo = MetrobusRepository(db, captureClient, context)
        metrobusRepo.fetchRealTimeEstimations("4410", forceRefresh = true)
        assertEquals(listOf(expectedSecret), capturedHeaders["/v1/metrobus/estimacion/4410"])

        // 5. NetworkModule.okHttpClient interceptor injects X-App-Secret on viatgem-proxy and omits it on external hosts
        var proxyInterceptorHeaders: List<String>? = null
        var externalInterceptorHeaders: List<String>? = null
        val networkClientWithMockTail = com.example.data.network.NetworkModule.okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.host == com.example.data.network.NetworkModule.VIATGEM_PROXY_HOST) {
                    proxyInterceptorHeaders = req.headers("X-App-Secret")
                } else {
                    externalInterceptorHeaders = req.headers("X-App-Secret")
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        networkClientWithMockTail.newCall(
            okhttp3.Request.Builder()
                .url("${com.example.data.network.NetworkModule.VIATGEM_PROXY_BASE_URL}/v1/avisos")
                .build()
        ).execute().close()
        assertEquals(listOf(expectedSecret), proxyInterceptorHeaders)

        networkClientWithMockTail.newCall(
            okhttp3.Request.Builder()
                .url("https://example.com/public")
                .build()
        ).execute().close()
        assertTrue(externalInterceptorHeaders.orEmpty().isEmpty())
    }
}
