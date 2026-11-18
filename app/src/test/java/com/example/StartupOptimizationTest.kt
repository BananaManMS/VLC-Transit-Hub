package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.StaticTransitDataCache
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupOptimizationTest {

    @Test
    fun `shared Renfe in-memory schedule cache works across instances`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo1 = RenfeRepository(context, db)
            val repo2 = RenfeRepository(context, db)

            repo1.initDatabaseFromAssetsIfNeeded()
            repo1.syncManager.ensureScheduleLoadedInMemory()

            val horariosFromRepo2 = repo2.syncManager.getHorariosForStation("65000")
            assertTrue(
                "Shared companion cache should populate repo2 immediately without re-parsing",
                horariosFromRepo2.isNotEmpty()
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `StaticTransitDataCache concurrent initialize succeeds cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val results = listOf(
            async { StaticTransitDataCache.getOrLoadEmtStops(context) },
            async { StaticTransitDataCache.getOrLoadEmtStops(context) },
            async { StaticTransitDataCache.getOrLoadMetrobusStops(context) },
            async { StaticTransitDataCache.getOrLoadMetrobusStops(context) }
        ).awaitAll()

        assertTrue("EMT stops must load", results[0].isNotEmpty())
        assertTrue("Concurrent callers must receive identical cached EMT size", results[0].size == results[1].size)
        assertTrue("Metrobus stops must load", results[2].isNotEmpty())
        assertTrue("Concurrent callers must receive identical cached Metrobus size", results[2].size == results[3].size)
    }

    @Test
    fun `DashboardViewModel unlocks UI immediately and completes Phase 1 nearby context`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = DashboardViewModel(app)

        assertTrue("UI must be immediately ready at frame 0", vm.isUiReady.value)

        val readyReached = withTimeoutOrNull(4000L) {
            while (!vm.isNearbyContextReady.value) {
                delay(25L)
            }
            true
        } ?: false

        assertTrue("Phase 1 stations/stops/location context should mark ready cleanly", readyReached)
    }
}
