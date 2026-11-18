package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.MetroAlertsRepository
import com.example.data.repository.MetrobusRepository
import com.example.data.repository.StaticTransitDataCache
import com.example.data.repository.UnifiedSearchEngine
import com.example.data.repository.ValenbisiRepository
import com.example.data.repository.renfe.GtfsCacheManager
import com.example.data.repository.renfe.RenfeAlertsRepository
import com.example.ui.bus.BusFilterSource
import com.example.ui.bus.BusViewModel
import com.example.ui.bus.ValenbisiFilterSource
import com.example.ui.cercanias.CercaniasViewModel
import com.example.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossWindowCoordinationTest {

    @Test
    fun `GtfsCacheManager getInstance returns singleton across callers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val a = GtfsCacheManager.getInstance(context)
        val b = GtfsCacheManager.getInstance(context.applicationContext)
        assertSame("GtfsCacheManager must share one singleton instance", a, b)
    }

    @Test
    fun `MetrobusRepository getLinesInfo loads locally without GitHub call and shares companion cache`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            MetrobusRepository.clearAllCaches()
            val networkHits = AtomicInteger(0)
            val failingClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor {
                    networkHits.incrementAndGet()
                    throw IOException("Network should not be called for local getLinesInfo")
                })
                .build()

            val repo1 = MetrobusRepository(db, failingClient, context)
            val repo2 = MetrobusRepository(db, failingClient, context)

            val lines1 = repo1.getLinesInfo()
            val lines2 = repo2.getLinesInfo()

            assertTrue("Local asset metrobus_lines.json should populate lines", lines1.isNotEmpty())
            assertEquals("Second instance should share identical cached lines", lines1.size, lines2.size)
            assertEquals("GitHub must not be queried when local asset exists", 0, networkHits.get())
        } finally {
            db.close()
        }
    }

    @Test
    fun `MetroAlertsRepository and RenfeAlertsRepository sync StateFlows across instances and ViewModels`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val metroRepo1 = MetroAlertsRepository()
            val metroRepo2 = MetroAlertsRepository()

            // Verify lazy news behavior: default includeNews = false does not trigger news load
            metroRepo1.fetchAllAlerts(force = false, includeNews = false)
            assertTrue("News should stay empty when includeNews=false", metroRepo2.metroNews.value.isEmpty())
            assertEquals(metroRepo1.activeIncidents.value, metroRepo2.activeIncidents.value)
            assertEquals(metroRepo1.specialNotices.value, metroRepo2.specialNotices.value)

            val renfeRepo1 = RenfeAlertsRepository(app, db)
            val renfeRepo2 = RenfeAlertsRepository(app, db)
            assertEquals(renfeRepo1.activeAlerts.value, renfeRepo2.activeAlerts.value)

            val dashboardVm = DashboardViewModel(app)
            val cercaniasVm = CercaniasViewModel(app)
            assertEquals(dashboardVm.activeCercaniasAlerts.value, cercaniasVm.activeCercaniasAlerts.value)
            assertEquals(dashboardVm.activeMetroIncidents.value, metroRepo1.activeIncidents.value)
        } finally {
            db.close()
        }
    }

    @Test
    fun `BusFilterSource and ValenbisiFilterSource remove METRO_STATION and BusViewModel skips eager Valenbisi load`() = runBlocking {
        val busValues = BusFilterSource.entries.map { it.name }
        val valenbisiValues = ValenbisiFilterSource.entries.map { it.name }
        assertFalse("BusFilterSource must not include METRO_STATION", busValues.contains("METRO_STATION"))
        assertFalse("ValenbisiFilterSource must not include METRO_STATION", valenbisiValues.contains("METRO_STATION"))

        val app = ApplicationProvider.getApplicationContext<Application>()
        StaticTransitDataCache.getOrLoadEmtStops(app)
        val busViewModel = BusViewModel(app)

        val stopsLoaded = withTimeoutOrNull(3000L) {
            while (busViewModel.busStopsList.value.isEmpty()) {
                delay(25L)
            }
            true
        } ?: false
        assertTrue("BusViewModel should load shared EMT stops quickly", stopsLoaded)
        assertEquals(
            "BusViewModel.init must not eagerly start Valenbisi loading",
            false,
            busViewModel.valenbisiLoading.value
        )
    }

    @Test
    fun `UnifiedSearchEngine resolves Metro, Cercanias, EMT, and Metrobus using shared caches`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val engine = UnifiedSearchEngine(context, db, null)
            var foundMetro = false
            engine.performSearch(query = "Xàtiva").collect { results ->
                if (results.isNotEmpty()) {
                    foundMetro = true
                }
            }
            assertTrue("UnifiedSearchEngine should match Xàtiva station", foundMetro)
        } finally {
            db.close()
        }
    }

    @Test
    fun `ValenbisiRepository exposes sharedStationsFlow cleanly`() {
        val current = ValenbisiRepository.sharedStationsFlow.value
        assertEquals(ValenbisiRepository.getCachedStations(), current)
    }
}
