package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class MainApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.util.StartupProfiler.log("MainApp", "onCreate starting: AppDatabase & RealTimeTransitRepository")
        AppDatabase.getDatabase(this)
        RealTimeTransitRepository.init(this)

        // Defer disk cleanup & WorkManager scheduling completely off the main startup path
        applicationScope.launch(Dispatchers.IO) {
            com.example.util.AppUpdateManager.cleanOldUpdateApks(this@MainApplication)
            scheduleDailyUpdateWorker()
        }

        // Staged background warmup after Inicio frame 0 & live widget requests settle (~1.6s)
        applicationScope.launch(Dispatchers.IO) {
            try {
                delay(1600L)
                com.example.util.StartupProfiler.log("MainApp", "Stage 1: sequential local schedule/stop cache warmup")

                // 1. Pre-warm Metro schedules, Renfe schedules, and static bus stop caches sequentially with yield()
                try {
                    com.example.data.repository.MetroScheduleRepository.getInstance(this@MainApplication).ensureLoaded()
                } catch (_: Exception) {}
                yield()

                try {
                    com.example.data.repository.renfe.RenfeRepository(
                        this@MainApplication,
                        AppDatabase.getDatabase(this@MainApplication)
                    ).syncManager.ensureScheduleLoadedInMemory()
                } catch (_: Exception) {}
                yield()

                try {
                    com.example.data.repository.StaticTransitDataCache.initialize(this@MainApplication)
                } catch (_: Exception) {}
                yield()

                // 2. Always warm tiny map theme JSONs
                try {
                    com.example.ui.map.google.GoogleMapStyleCache.preload(this@MainApplication)
                } catch (_: Exception) {}

                // 3. RAM-aware Map GeoJSON & MapsInitializer warmup:
                // Skip heavy eager GeoJSON parsing on low-RAM / <=192MB heap devices to avoid GC stutter & OOM;
                // those devices load map layers cleanly on demand when the user opens Mapa.
                if (!isLowEndDevice()) {
                    delay(400L)
                    com.example.util.StartupProfiler.log("MainApp", "Stage 2: pre-warming Map GeoJSONs & MapsInitializer")
                    try { com.example.ui.map.components.MetroMapOverlayLoader.ensureLoaded(this@MainApplication, this) } catch (_: Exception) {}
                    delay(80L)
                    try { com.example.ui.map.components.CercaniasMapOverlayLoader.ensureLoaded(this@MainApplication, this) } catch (_: Exception) {}
                    delay(80L)
                    try { com.example.ui.map.components.EmtMapOverlayLoader.ensureLoaded(this@MainApplication, this) } catch (_: Exception) {}
                    delay(80L)
                    try { com.example.ui.map.components.MetroStationFootprintsOverlayManager.ensureLoaded(this@MainApplication) } catch (_: Exception) {}
                    launch(Dispatchers.Main) {
                        try {
                            com.google.android.gms.maps.MapsInitializer.initialize(
                                this@MainApplication,
                                com.google.android.gms.maps.MapsInitializer.Renderer.LATEST
                            ) { }
                        } catch (_: Throwable) {}
                    }
                } else {
                    com.example.util.StartupProfiler.log("MainApp", "Stage 2: low-RAM device detected, deferring heavy map GeoJSON warmup until Mapa tab")
                }
            } catch (_: Exception) {
                // Non-critical background warmup
            }
        }
    }

    private fun isLowEndDevice(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val isLowRam = am?.isLowRamDevice == true
            val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
            isLowRam || maxHeapMb <= 192L
        } catch (_: Exception) {
            false
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
