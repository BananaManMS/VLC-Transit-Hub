package com.example

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.database.AppDatabase
import com.example.data.repository.RealTimeTransitRepository
import com.example.worker.ScheduleUpdateWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.util.StartupProfiler.log("MainApp", "onCreate starting: AppDatabase.getDatabase & RealTimeTransitRepository.init")
        AppDatabase.getDatabase(this)
        RealTimeTransitRepository.init(this)
        
        // Clean any leftover update APK files from previous installs
        com.example.util.AppUpdateManager.cleanOldUpdateApks(this)
        
        applicationScope.launch(Dispatchers.IO) {
            com.example.util.StartupProfiler.log("MainApp", "scheduleDailyUpdateWorker")
            scheduleDailyUpdateWorker()
        }
        
        // Trigger an initial async sync on startup
        applicationScope.launch {
            com.example.util.StartupProfiler.log("MainApp", "MetroScheduleRepository.syncScheduleFromRemoteIfNeeded() starting")
            try {
                com.example.data.repository.MetroScheduleRepository.getInstance(this@MainApplication)
                    .syncScheduleFromRemoteIfNeeded()
                com.example.util.StartupProfiler.log("MainApp", "MetroScheduleRepository.syncScheduleFromRemoteIfNeeded() completed")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Proactively pre-warm GeoJSON map line overlays and station footprints in background (deferred by 4s to prioritize initial UI)
        applicationScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(4000L)
                com.example.ui.map.components.MetroMapOverlayLoader.ensureLoaded(this@MainApplication)
                com.example.ui.map.components.CercaniasMapOverlayLoader.ensureLoaded(this@MainApplication)
                com.example.ui.map.components.MetroStationFootprintsOverlayManager.preloadGeoJson(this@MainApplication)
            } catch (e: Exception) {
                // Non-critical background warmup
            }
        }

        // Deferred Google Maps SDK warmup (3-second delay to preserve cold-start speed under 300ms)
        applicationScope.launch {
            try {
                kotlinx.coroutines.delay(3000)
                com.google.android.gms.maps.MapsInitializer.initialize(this@MainApplication, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST) { }
            } catch (_: Throwable) {
                // Ignore background warmup exceptions
            }
        }
    }

    private fun scheduleDailyUpdateWorker() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ScheduleUpdateWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Throwable) {
            android.util.Log.w("MainApplication", "WorkManager schedule skipped: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}
